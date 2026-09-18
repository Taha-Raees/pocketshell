package app.pocketshell.widget.git

import app.pocketshell.packages.ExecResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe's protocol parsing, honest degradation and idle gate — all
 * against a fake exec, no guest needed. The REAL exec path (proot spec +
 * background runner) is wired in GitApp and pinned by GitAppContractTest;
 * this suite pins WHAT the probe asks for and HOW it degrades.
 */
class GitProbeTest {

    // --------------------------------------------------------- fixtures

    private val fullOutput = """
        @@GIT:2.34.1
        @@REPO:/root/project
        ## main...origin/main [ahead 1, behind 2]
         M src/a.kt
        ?? notes.txt
        @@RC:0
        @@REPO:/root/broken
        @@RC:128
        @@DONE
    """.trimIndent() + "\n"

    private class FakeExec(
        private val result: () -> ExecResult,
    ) : GitProbe.GuestExec {
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
    fun `a full probe output parses version and repo blocks`() {
        val parsed = parseProbeOutput(fullOutput)
        assertNotNull(parsed)
        assertEquals("2.34.1", parsed!!.gitVersion)
        assertTrue(parsed.complete)
        assertEquals(2, parsed.repos.size)
        assertEquals("/root/project", parsed.repos[0].path)
        assertEquals(listOf("## main...origin/main [ahead 1, behind 2]", " M src/a.kt", "?? notes.txt"), parsed.repos[0].lines)
        assertEquals(0, parsed.repos[0].rc)
        assertEquals("/root/broken", parsed.repos[1].path)
        assertEquals(128, parsed.repos[1].rc)
    }

    @Test
    fun `an absent git binary is the empty version marker`() {
        val parsed = parseProbeOutput("@@GIT:\n@@DONE\n")
        assertNotNull(parsed)
        assertNull(parsed!!.gitVersion)
        assertTrue(parsed.repos.isEmpty())
        assertTrue(parsed.complete)
    }

    @Test
    fun `output without the version marker is not data`() {
        assertNull(parseProbeOutput("some random stdout\n"))
        assertNull(parseProbeOutput(""))
    }

    @Test
    fun `a stream that ends before DONE is marked incomplete`() {
        val parsed = parseProbeOutput("@@GIT:2.34.1\n@@REPO:/root/x\n@@RC:0\n")
        assertNotNull(parsed)
        assertFalse(parsed!!.complete)
    }

    @Test
    fun `a repo block cut short before its exit code has a null rc`() {
        val parsed = parseProbeOutput("@@GIT:2.34.1\n@@REPO:/root/x\n## main\n@@DONE\n")
        assertNotNull(parsed)
        assertNull(parsed!!.repos.single().rc)
    }

    // --------------------------------------------------- snapshot path

    @Test
    fun `snapshot asks for exactly the one batched script`() {
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") }
        GitProbe(fake).snapshot()
        assertEquals(1, fake.calls)
        assertEquals(listOf("/bin/sh", "-c", GitProbe.PROBE_SCRIPT, "sh"), fake.argvs.single())
    }

    @Test
    fun `a healthy scan renders repositories with parsed status`() {
        val probe = GitProbe(FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") })
        val result = probe.snapshot()
        val snapshot = (result as ScanResult.Done).snapshot
        assertEquals("2.34.1", snapshot.gitVersion)
        assertTrue(snapshot.hasGit)
        assertEquals(2, snapshot.repos.size)

        val project = snapshot.repos[0]
        assertEquals("project", project.name)
        assertNull(project.error)
        assertEquals("main", project.status!!.branch)
        assertEquals(1, project.status!!.ahead)
        assertEquals(2, project.status!!.behind)
        // one worktree change + one untracked path
        assertEquals(2, project.status!!.totalChanges)
        assertTrue(project.status!!.dirty)

        val broken = snapshot.repos[1]
        assertNull(broken.status)
        assertEquals("git exited with 128", broken.error)
    }

    @Test
    fun `git absent degrades honestly - not to an empty repo list`() {
        val probe = GitProbe(FakeExec { ExecResult(exitCode = 0, stdout = "@@GIT:\n@@DONE\n", stderr = "") })
        val snapshot = (probe.snapshot() as ScanResult.Done).snapshot
        assertFalse(snapshot.hasGit)
        assertTrue(snapshot.repos.isEmpty())
    }

    @Test
    fun `a truncated scan is a failed probe, not partial data`() {
        val probe = GitProbe(FakeExec { ExecResult(exitCode = 0, stdout = "@@GIT:2.34.1\n@@REPO:/root/x\n", stderr = "") })
        val result = probe.snapshot()
        assertTrue(result is ScanResult.Failed)
        assertTrue((result as ScanResult.Failed).reason.contains("truncated"))
    }

    @Test
    fun `unrecognizable stdout is a failed probe`() {
        val probe = GitProbe(FakeExec { ExecResult(exitCode = 0, stdout = "bin/sh: syntax error\n", stderr = "") })
        assertTrue(probe.snapshot() is ScanResult.Failed)
    }

    @Test
    fun `a real exec failure carries the stderr reason`() {
        val probe = GitProbe(
            FakeExec { ExecResult(exitCode = 137, stdout = "", stderr = "guest process died\n") },
        )
        val result = probe.snapshot()
        assertTrue(result is ScanResult.Failed)
        assertEquals("guest process died", (result as ScanResult.Failed).reason)
    }

    @Test
    fun `a thrown exec becomes a failed probe, never a crash`() {
        val probe = GitProbe(FakeExec { throw IllegalStateException("proot missing") })
        val result = probe.snapshot()
        assertEquals("proot missing", (result as ScanResult.Failed).reason)
    }

    // ------------------------------------------------------- idle gate

    @Test
    fun `the first tick always scans`() {
        var now = 0L
        val probe = GitProbe(FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") }, clock = { now })
        assertTrue(probe.shouldFullScan(now))
    }

    @Test
    fun `a tick right after a scan does nothing`() {
        var now = 1_000L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") }
        val probe = GitProbe(fake, clock = { now })
        probe.snapshot()
        now += GitProbe.AUTO_RESCAN_MS - 1
        assertFalse(probe.shouldFullScan(now))
        assertEquals(1, fake.calls)
    }

    @Test
    fun `an idle open card rescans only after the full interval`() {
        var now = 1_000L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") }
        val probe = GitProbe(fake, clock = { now })
        probe.snapshot()
        now += GitProbe.AUTO_RESCAN_MS
        assertTrue(probe.shouldFullScan(now))
        probe.snapshot()
        assertEquals(2, fake.calls)
    }

    @Test
    fun `the gate is the only thing a cheap tick costs`() {
        var now = 0L
        val fake = FakeExec { ExecResult(exitCode = 0, stdout = fullOutput, stderr = "") }
        val probe = GitProbe(fake, clock = { now })
        probe.snapshot()
        repeat(10) {
            now += 1_999 // ticks that stay inside the idle window
            assertFalse(probe.shouldFullScan(now))
        }
        assertEquals(1, fake.calls)
    }

    // --------------------------------------------------- display paths

    @Test
    fun `guest home paths display with a tilde`() {
        assertEquals("~/Projects/app", displayGuestRepoPath("/root/Projects/app"))
        assertEquals("~/repo", displayGuestRepoPath("/root/repo"))
        assertEquals("~", displayGuestRepoPath("/root"))
    }

    @Test
    fun `paths outside the guest home pass through verbatim`() {
        assertEquals("/srv/repo", displayGuestRepoPath("/srv/repo"))
    }
}
