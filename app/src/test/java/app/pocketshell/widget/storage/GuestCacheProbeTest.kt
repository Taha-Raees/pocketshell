package app.pocketshell.widget.storage

import app.pocketshell.packages.ExecResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guest cache probe's protocol parsing, honest degradation and idle
 * gate — all against a fake exec, no guest needed (the GitProbeTest
 * discipline). The REAL exec path (proot spec + background runner) is
 * wired in StorageApp and pinned by StorageAppContractTest; this suite
 * pins WHAT the probe asks for and HOW it degrades.
 */
class GuestCacheProbeTest {

    // --------------------------------------------------------- fixtures

    private class FakeExec(
        private val result: () -> ExecResult,
    ) : GuestCacheProbe.GuestExec {
        val argvs = mutableListOf<List<String>>()
        var calls = 0

        override fun exec(guestCommand: List<String>): ExecResult {
            calls += 1
            argvs.add(guestCommand)
            return result()
        }
    }

    // -------------------------------------------------- protocol parse

    @Test
    fun `a full probe output parses cache entries`() {
        val parsed = parseCacheOutput("@@CACHE:npm:1024\n@@CACHE:cache:512\n@@CACHE:tmp:5\n@@DONE\n")
        assertTrue(parsed!!.complete)
        assertEquals(3, parsed.entries.size)
        assertEquals(GuestCacheEntry("npm", 1024L), parsed.entries[0])
        assertEquals(GuestCacheEntry("cache", 512L), parsed.entries[1])
        assertEquals(GuestCacheEntry("tmp", 5L), parsed.entries[2])
    }

    @Test
    fun `a du that could not say yields an unknown size, not a zero`() {
        val parsed = parseCacheOutput("@@CACHE:cache:?\n@@DONE\n")
        assertTrue(parsed!!.complete)
        assertNull(parsed.entries.single().kilobytes)
    }

    @Test
    fun `names outside the protocol allowlist are ignored`() {
        val parsed = parseCacheOutput("@@CACHE:evil:999999\n@@CACHE:npm:10\n@@DONE\n")
        assertTrue(parsed!!.complete)
        assertEquals(listOf("npm"), parsed.entries.map { it.name })
    }

    @Test
    fun `output without any marker is not data`() {
        assertNull(parseCacheOutput("du: cannot access\n"))
        assertNull(parseCacheOutput(""))
    }

    @Test
    fun `a stream that ends before DONE is marked incomplete`() {
        val parsed = parseCacheOutput("@@CACHE:npm:10\n")
        assertFalse(parsed!!.complete)
    }

    @Test
    fun `a completed probe with no caches is an honest empty result`() {
        val parsed = parseCacheOutput("@@DONE\n")
        assertTrue(parsed!!.complete)
        assertTrue(parsed.entries.isEmpty())
    }

    // --------------------------------------------------- snapshot path

    @Test
    fun `snapshot asks for exactly the one batched script`() {
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = "@@DONE\n", stderr = "") }
        GuestCacheProbe(fake).snapshot()
        assertEquals(1, fake.calls)
        assertEquals(
            listOf("/bin/sh", "-c", GuestCacheProbe.PROBE_SCRIPT, "sh"),
            fake.argvs.single(),
        )
    }

    @Test
    fun `a healthy probe returns measured sizes`() {
        val probe = GuestCacheProbe(
            FakeExec { ExecResult(exitCode = 0, stdout = "@@CACHE:npm:1024\n@@DONE\n", stderr = "") },
        )
        val result = probe.snapshot()
        val entries = (result as GuestCacheProbe.ProbeResult.Done).entries
        assertEquals(listOf(GuestCacheEntry("npm", 1024L)), entries)
        assertEquals(entries, (probe.cachedResult as GuestCacheProbe.ProbeResult.Done).entries)
    }

    @Test
    fun `unrecognizable stdout is a failed probe`() {
        val probe = GuestCacheProbe(
            FakeExec { ExecResult(exitCode = 0, stdout = "bin/sh: syntax error\n", stderr = "") },
        )
        assertTrue(probe.snapshot() is GuestCacheProbe.ProbeResult.Failed)
    }

    @Test
    fun `a truncated probe is a failure, never partial data`() {
        val probe = GuestCacheProbe(
            FakeExec { ExecResult(exitCode = 0, stdout = "@@CACHE:npm:10\n", stderr = "") },
        )
        val result = probe.snapshot()
        assertTrue(result is GuestCacheProbe.ProbeResult.Failed)
        assertTrue((result as GuestCacheProbe.ProbeResult.Failed).reason.contains("truncated"))
    }

    @Test
    fun `a real exec failure carries the stderr reason`() {
        val probe = GuestCacheProbe(
            FakeExec { ExecResult(exitCode = 137, stdout = "", stderr = "guest process died\n") },
        )
        val result = probe.snapshot()
        assertEquals("guest process died", (result as GuestCacheProbe.ProbeResult.Failed).reason)
    }

    @Test
    fun `a thrown exec becomes a failed probe, never a crash`() {
        val probe = GuestCacheProbe(FakeExec { throw IllegalStateException("proot missing") })
        val result = probe.snapshot()
        assertEquals("proot missing", (result as GuestCacheProbe.ProbeResult.Failed).reason)
    }

    // ------------------------------------------------------- idle gate

    @Test
    fun `the first probe always runs`() {
        var now = 0L
        val probe = GuestCacheProbe(
            FakeExec { ExecResult(exitCode = 0, stdout = "@@DONE\n", stderr = "") },
            clock = { now },
        )
        assertTrue(probe.shouldProbe(now))
    }

    @Test
    fun `a probe right after a scan does nothing`() {
        var now = 1_000L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = "@@DONE\n", stderr = "") }
        val probe = GuestCacheProbe(fake, clock = { now })
        probe.snapshot()
        now += GuestCacheProbe.AUTO_RESCAN_MS - 1
        assertFalse(probe.shouldProbe(now))
        assertEquals(1, fake.calls)
    }

    @Test
    fun `an idle open card re-probes only after the full interval`() {
        var now = 1_000L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = "@@DONE\n", stderr = "") }
        val probe = GuestCacheProbe(fake, clock = { now })
        probe.snapshot()
        now += GuestCacheProbe.AUTO_RESCAN_MS
        assertTrue(probe.shouldProbe(now))
        probe.snapshot()
        assertEquals(2, fake.calls)
    }

    @Test
    fun `invalidate forgets both the gate and the cache - refresh forces a real exec`() {
        var now = 1_000L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = "@@CACHE:npm:1\n@@DONE\n", stderr = "") }
        val probe = GuestCacheProbe(fake, clock = { now })
        probe.snapshot()
        assertEquals(1, fake.calls)
        probe.invalidate()
        assertNull(probe.cachedResult)
        assertTrue(probe.shouldProbe(now))
        probe.snapshot()
        assertEquals(2, fake.calls)
    }

    // --------------------------------------------------- display paths

    @Test
    fun `guest cache paths display for humans`() {
        assertEquals("~/.npm", guestCacheLabel("npm"))
        assertEquals("~/.cache", guestCacheLabel("cache"))
        assertEquals("~/.gradle", guestCacheLabel("gradle"))
        assertEquals("~/.cargo", guestCacheLabel("cargo"))
        assertEquals("~/.m2", guestCacheLabel("m2"))
        assertEquals("/tmp", guestCacheLabel("tmp"))
    }
}
