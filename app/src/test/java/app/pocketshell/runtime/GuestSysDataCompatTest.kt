package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * M2.6.12 — GuestSysDataCompat pins (docs/M2.6-RESEARCH.md §7).
 *
 * The core honesty rule under test: REAL WINS. A kernel-readable /proc file
 * is never overlaid; only genuinely-denied files get a verified overlay,
 * and every overlay's content is derived from real host sources
 * (uname(2), elapsedRealtime, real core count, hidepid-filtered pid set)
 * with documented placeholders where no honest source exists.
 */
class GuestSysDataCompatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---------------------------------------------------------- fixtures

    private fun sources(
        release: String? = "5.10.157-android13-4-00001-g1234abcd",
        tail: String? = "#1 SMP PREEMPT Thu Sep  3 12:00:00 UTC 2026",
        elapsed: Double = 1200.5,
        now: Long = 1_788_000_000,
        cpus: Int = 8,
        pids: Int = 12,
        maxPid: Long = 34567,
    ) = GuestSysDataCompat.Sources(
        unameRelease = release,
        unameVersionTail = tail,
        elapsedRealtimeSeconds = elapsed,
        currentTimeSeconds = now,
        processorCount = cpus,
        readablePidCount = pids,
        maxReadablePid = maxPid,
    )

    /**
     * JVM stand-in for the device's Os.lstat: real type facts via
     * NOFOLLOW attributes + real nlink via the unix attribute view
     * (tests run on a Linux JVM). Null only when the file cannot be
     * stat'ed at all — matching the device default's contract.
     */
    private fun jvmStat(): (File) -> GuestSysDataCompat.FileFact? = { file ->
        try {
            val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            val nlink = runCatching {
                Files.getAttribute(file.toPath(), "unix:nlink", LinkOption.NOFOLLOW_LINKS) as Long
            }.getOrDefault(1L)
            GuestSysDataCompat.FileFact(
                exists = true,
                isRegularFile = attrs.isRegularFile,
                isSymbolicLink = attrs.isSymbolicLink,
                nlink = nlink,
            )
        } catch (_: NoSuchFileException) {
            GuestSysDataCompat.FileFact(exists = false, isRegularFile = false, isSymbolicLink = false, nlink = 0)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------- the REAL-WINS rule

    @Test
    fun `kernel-readable files are never overlaid`() {
        val dir = tmp.newFolder("sysdata")
        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { true }, // kernel grants everything
            statFact = jvmStat(),
        )
        assertTrue(result.outcomes.all { it.realReadable && !it.overlaid })
        assertTrue(result.bindArgs().isEmpty())
        assertTrue("no overlay files may exist when everything is readable", dir.listFiles().isNullOrEmpty())
        assertTrue(result.summary().contains("real (kernel-readable)"))
    }

    @Test
    fun `kernel-denied files get verified overlays in stable order`() {
        val dir = tmp.newFolder("sysdata")
        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { false }, // kernel denies all five
            statFact = jvmStat(),
        )
        assertEquals(GuestSysDataCompat.GUEST_PATHS, result.outcomes.map { it.guestPath })
        assertTrue(result.outcomes.all { it.overlaid && it.hostFile != null })
        // bind args: file-over-file, stable order, verified entries only
        assertEquals(
            GuestSysDataCompat.GUEST_PATHS.map { "--bind=${File(dir, it.substringAfterLast('/')).absolutePath}:$it" },
            result.bindArgs(),
        )
        // every file exists and is a plain regular file with content
        result.outcomes.forEach { outcome ->
            val f = outcome.hostFile!!
            assertTrue(f.isFile)
            assertTrue(f.readText().endsWith("\n"))
        }
    }

    @Test
    fun `mixed reality — only the denied subset is overlaid`() {
        val dir = tmp.newFolder("sysdata")
        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { it == "/proc/version" }, // only version is real
            statFact = jvmStat(),
        )
        val byPath = result.outcomes.associateBy { it.guestPath }
        assertTrue(byPath.getValue("/proc/version").realReadable)
        assertNull(byPath.getValue("/proc/version").hostFile)
        for (path in GuestSysDataCompat.GUEST_PATHS.filter { it != "/proc/version" }) {
            assertTrue("$path must be overlaid", byPath.getValue(path).overlaid)
        }
        assertFalse(File(dir, "version").exists())
        assertTrue(result.bindArgs().none { it.endsWith(":/proc/version") })
        assertEquals(4, result.bindArgs().size)
    }

    // -------------------------------------------------- content honesty

    @Test
    fun `version overlay carries real uname identity plus explicit attribution`() {
        val content = GuestSysDataCompat.versionContent(
            release = "5.10.157-android13-4",
            versionTail = "#1 SMP PREEMPT Thu Sep 3 12:00:00 UTC 2026",
        )
        assertNotNull(content)
        assertTrue(content!!.startsWith("Linux version 5.10.157-android13-4 "))
        assertTrue("attribution marker required", content.contains("PocketShell sysdata overlay"))
        assertTrue(content.contains("uname(2)"))
        assertTrue(content.endsWith("#1 SMP PREEMPT Thu Sep 3 12:00:00 UTC 2026\n"))
    }

    @Test
    fun `version overlay refuses to write without a real kernel identity`() {
        assertNull(GuestSysDataCompat.versionContent(null, "#1 SMP"))
        assertNull(GuestSysDataCompat.versionContent("", "#1 SMP"))
        // release present but tail missing — nothing honest to write either
        assertNull(GuestSysDataCompat.versionContent("5.10.157", null))

        // and through prepare: the entry degrades with a note, unbound
        val dir = tmp.newFolder("sysdata")
        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(release = null, tail = null),
            probeReadable = { false },
            statFact = jvmStat(),
        )
        val version = result.outcomes.first { it.guestPath == "/proc/version" }
        assertFalse(version.overlaid)
        assertNotNull(version.note)
        assertFalse(result.bindArgs().any { it.endsWith(":/proc/version") })
        // the other four entries are unaffected
        assertEquals(4, result.bindArgs().size)
    }

    @Test
    fun `uptime overlay is real elapsed plus documented idle placeholder`() {
        assertEquals("1200.50 0.00\n", GuestSysDataCompat.uptimeContent(1200.5))
        assertEquals("0.00 0.00\n", GuestSysDataCompat.uptimeContent(-1.0))
        assertEquals("0.00 0.00\n", GuestSysDataCompat.uptimeContent(Double.NaN))
    }

    @Test
    fun `loadavg tail is the real hidepid-filtered pid subset`() {
        assertEquals("0.00 0.00 0.00 0/12 34567\n", GuestSysDataCompat.loadavgContent(12, 34567))
    }

    @Test
    fun `stat overlay has real core count and real btime`() {
        val content = GuestSysDataCompat.statContent(cpuCount = 8, btime = 1_787_999_880)
        val lines = content.trim().lines()
        assertEquals("cpu  0 0 0 0 0 0 0 0 0 0", lines[0])
        assertEquals("one cpuN line per real core", 8, lines.count { it.startsWith("cpu") && it[3].isDigit() })
        assertTrue(lines.contains("btime 1787999880"))
        assertTrue(lines.contains("intr 0"))
        assertTrue(lines.contains("procs_running 0"))
    }

    @Test
    fun `btime is derived from real clocks`() {
        val s = sources(elapsed = 100.5, now = 1_000_000)
        // epoch boot time = now - uptime (elapsedRealtime has the kernel's
        // includes-deep-sleep semantics of /proc/uptime field 1)
        assertEquals(1_000_000L - 100L, GuestSysDataCompat.btimeSeconds(s))
    }

    @Test
    fun `vmstat overlay is a zero-valued standard counter skeleton`() {
        val content = GuestSysDataCompat.vmstatContent()
        assertTrue(content.contains("nr_free_pages 0\n"))
        assertTrue(content.contains("pgfault 0\n"))
        assertTrue(content.contains("oom_kill 0\n"))
        content.trim().lines().forEach { line ->
            assertTrue("non-conforming vmstat line: $line", line.matches(Regex("^[a-z0-9_]+ 0$")))
        }
    }

    // ---------------------------------------------- hardening + failure

    @Test
    fun `a planted symlink is dropped and remade, never followed`() {
        val dir = tmp.newFolder("sysdata")
        val target = File(dir, "stat")
        val outside = tmp.newFolder("outside")
        val hostFile = File(outside, "host-secret")
        hostFile.writeText("host data\n")
        Files.createSymbolicLink(target.toPath(), Paths.get(hostFile.absolutePath))

        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { false },
            statFact = jvmStat(),
        )
        assertTrue(result.outcomes.first { it.guestPath == "/proc/stat" }.overlaid)
        assertFalse("symlink must not survive", Files.isSymbolicLink(target.toPath()))
        assertTrue(target.isFile)
        assertTrue("overlay content must replace the planted link", target.readText().startsWith("cpu  "))
        assertFalse(target.readText().contains("host data"))
    }

    @Test
    fun `a planted hardlink (st_nlink greater than one) is dropped and remade`() {
        val dir = tmp.newFolder("sysdata")
        val target = File(dir, "loadavg")
        val outside = tmp.newFolder("outside")
        val hostFile = File(outside, "host-file")
        hostFile.writeText("host data\n")
        Files.createLink(target.toPath(), hostFile.toPath()) // real nlink == 2

        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { false },
            statFact = jvmStat(),
        )
        assertTrue(result.outcomes.first { it.guestPath == "/proc/loadavg" }.overlaid)
        // the host file keeps its content; only the planted name is replaced
        assertTrue(hostFile.readText() == "host data\n")
        assertTrue(target.readText() == GuestSysDataCompat.loadavgContent(12, 34567))
    }

    @Test
    fun `stale own overlay is refreshed on every spawn`() {
        val dir = tmp.newFolder("sysdata")
        val target = File(dir, "uptime")
        target.writeText("1.00 0.00\n") // last session's value

        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(elapsed = 7200.25),
            probeReadable = { false },
            statFact = jvmStat(),
        )
        assertTrue(result.outcomes.first { it.guestPath == "/proc/uptime" }.overlaid)
        // upstream keeps stale content (write-if-missing); we deliberately
        // refresh — as fresh as the honest sources allow
        assertEquals("7200.25 0.00\n", target.readText())
    }

    @Test
    fun `a failed overlay degrades honestly without blocking the others`() {
        val dir = tmp.newFolder("sysdata")
        // a DIRECTORY named stat: statFact reports a wrong-type entry, the
        // drop fails (non-empty dir), and the entry must NOT be bound
        File(dir, "stat").also { it.mkdirs() }.let { File(it, "keep").writeText("x") }

        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { false },
            statFact = jvmStat(),
        )
        val stat = result.outcomes.first { it.guestPath == "/proc/stat" }
        assertFalse(stat.overlaid)
        assertNotNull(stat.note)
        assertFalse(result.bindArgs().any { it.endsWith(":/proc/stat") })
        // the other four entries still overlay
        assertEquals(4, result.bindArgs().size)
    }

    @Test
    fun `summary separates real, overlaid and failed entries`() {
        val dir = tmp.newFolder("sysdata")
        File(dir, "stat").also { it.mkdirs() }.let { File(it, "keep").writeText("x") }
        val result = GuestSysDataCompat.prepare(
            sysdataDir = dir,
            sources = sources(),
            probeReadable = { it == "/proc/version" },
            statFact = jvmStat(),
        )
        val summary = result.summary()
        assertTrue(summary.contains("real (kernel-readable): version"))
        assertTrue(summary.contains("overlaid (kernel-denied): uptime, loadavg, vmstat"))
        assertTrue(summary.contains("overlay FAILED: stat"))
    }

    @Test
    fun `probeReport is read-only and maps every guest path`() {
        val report = GuestSysDataCompat.probeReport { it != "/proc/vmstat" }
        assertEquals(GuestSysDataCompat.GUEST_PATHS.toSet(), report.keys)
        assertFalse(report.getValue("/proc/vmstat"))
        assertTrue(report.getValue("/proc/stat"))
    }
}
