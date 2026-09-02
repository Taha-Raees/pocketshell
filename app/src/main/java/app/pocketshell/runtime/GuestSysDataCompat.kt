package app.pocketshell.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

/**
 * M2.6.12 — selective /proc sysdata overlay (docs/M2.6-RESEARCH.md §7).
 *
 * Android's SELinux policy denies untrusted apps read access to several
 * STANDARD /proc files (proc_stat, proc_uptime, proc_loadavg, proc_version,
 * proc_vmstat — AOSP system/sepolicy private/app.te neverallows; device-
 * confirmed 2026-09-02 on SM-F711B for /proc/version). Inside an interactive
 * session the guest sees the REAL host procfs through the M2.6 bind, so those
 * files fail with EACCES in the guest too — distro tools that read them
 * (top, htop, uptime, boot parsers) hit the same wall.
 *
 * Architecture ADAPTED from Termux PRoot-Distro (proot_distro/sysdata.py,
 * setup_fake_sysdata() + fake_sysdata_bindings(), GPL-3.0 — studied, not
 * copied; see docs/M2.6-RESEARCH.md §7 for the adaptation table and
 * THIRD_PARTY.md for attribution):
 *
 *  - PROBE FIRST, REAL WINS (the core honesty mechanism, kept 1:1): before
 *    any overlay is bound, the REAL host file is probed with one byte read.
 *    A file the kernel allows is NEVER overlaid — real data always wins.
 *    Only genuinely-denied files get a compatibility file, bound file-over-
 *    file with proot ON TOP of the real /proc bind.
 *  - SCOPE deliberately narrower than upstream's: exactly the five standard
 *    files this app's kernels deny. Upstream's sysctl entries
 *    (cap_last_cap, inotify max_user_watches, overflowuid/gid) and the
 *    /sys/fs/selinux empty-dir bind are NOT shipped — no guest workload has
 *    needed them; they can be added later under the same rules.
 *  - HONEST CONTENT, adapted from upstream's static constants: this app's
 *    no-fake discipline requires deriving every field from a real host
 *    source where one is reachable and marking the file itself as an
 *    overlay where parsers allow it:
 *      version -> android.system.Os.uname() (real release + build tail via
 *                 the uname(2) syscall, which SELinux does not restrict),
 *                 with an explicit attribution marker in place of the
 *                 compiler/builder parentheticals (not recoverable via any
 *                 allowed interface). This SUPERSEDES v0.6.1's refusal to
 *                 synthesize /proc/version: the project owner directed the
 *                 probe-gated overlay model, and the marker keeps it
 *                 honest — anyone reading the file sees it is an overlay,
 *                 not the kernel's own file.
 *      uptime  -> field 1 from SystemClock.elapsedRealtime() (same clock
 *                 semantics as the kernel file); field 2 (idle) is a
 *                 documented 0.00 placeholder (not exposed to apps).
 *      stat    -> per-cpu lines for the REAL core count
 *                 (availableProcessors) and a REAL btime
 *                 (epoch now - elapsed realtime); the jiffies counters are
 *                 documented placeholders (global counters are denied), so
 *                 CPU% deltas in top read 0 — process ROWS stay real.
 *      loadavg -> fields 1-3 documented placeholders (no allowed source);
 *                 the process-count tail is REAL from the hidepid-filtered
 *                 readable pid set.
 *      vmstat  -> standard kernel counter-name skeleton, zero values
 *                 (names are kernel facts; values are honest placeholders).
 *  - WRITE HARDENING, proportionate port of upstream's (dir_fd, name) +
 *    O_NOFOLLOW + st_nlink==1 discipline: our sysdata directory lives in
 *    the app's private storage and is NEVER bound read-write into the
 *    guest, so the Termux planting threat does not exist here — the
 *    checks are kept as defense in depth anyway: entries are validated
 *    (regular file, not a symlink, exactly one link) before use, planted
 *    or wrong-type entries are dropped and remade, and every write is
 *    verified by content round-trip before it may be bound.
 *  - FRESHNESS: unlike upstream's write-if-missing, files are REFRESHED on
 *    every session spawn — as fresh as the honest sources allow.
 *
 * Failure posture is best-effort per entry, never fatal: a spawn never
 * fails because an overlay could not be written; the entry is simply not
 * bound and Diagnostics reports it. [bindArgs] output is empty unless every
 * listed overlay was written AND verified.
 */
object GuestSysDataCompat {

    /** App-private directory holding the overlay files (sibling of rootfs — upstream layout). */
    const val DIR_NAME = "sysdata"

    /**
     * The five standard /proc files Android SELinux denies untrusted apps,
     * in the stable order used for generation, bind args and reports.
     */
    val GUEST_PATHS: List<String> = listOf(
        "/proc/stat",
        "/proc/uptime",
        "/proc/loadavg",
        "/proc/version",
        "/proc/vmstat",
    )

    /**
     * Attribution marker embedded in the /proc/version overlay. Parsers only
     * need the "Linux version <release>" head; the parenthetical is where the
     * kernel's compiler/builder strings would be and is where we say plainly
     * what this file is.
     */
    const val VERSION_ATTRIBUTION =
        "(PocketShell sysdata overlay: kernel identity via uname(2); " +
            "the kernel's own file is denied to apps by Android SELinux)"

    /**
     * Real host data the generators need. Plain JVM types so the generators
     * and [prepare] stay unit-testable; [device] is the only android-touching
     * constructor and is called exclusively from device wiring.
     */
    data class Sources(
        val unameRelease: String?,
        val unameVersionTail: String?,
        val elapsedRealtimeSeconds: Double,
        val currentTimeSeconds: Long,
        val processorCount: Int,
        val readablePidCount: Int,
        val maxReadablePid: Long,
    ) {
        companion object {
            /** Real host sources; called only from device wiring (PackageGateway). */
            fun device(): Sources {
                val uts = runCatching { android.system.Os.uname() }.getOrNull()
                val (count, maxPid) = scanReadablePids()
                return Sources(
                    unameRelease = uts?.release,
                    unameVersionTail = uts?.version,
                    elapsedRealtimeSeconds = runCatching {
                        android.os.SystemClock.elapsedRealtime() / 1000.0
                    }.getOrDefault(0.0),
                    currentTimeSeconds = System.currentTimeMillis() / 1000L,
                    processorCount = Runtime.getRuntime().availableProcessors(),
                    readablePidCount = count,
                    maxReadablePid = maxPid,
                )
            }

            /**
             * The hidepid-filtered readable pid set — the app's own real
             * process tree (docs/M2.6-RESEARCH.md §4.3). Used for the
             * loadavg tail; a denied or empty /proc yields zeros.
             */
            fun scanReadablePids(procDir: File = File("/proc")): Pair<Int, Long> = runCatching {
                var count = 0
                var maxPid = 0L
                procDir.listFiles()?.forEach { entry ->
                    val name = entry.name
                    if (name.isNotEmpty() && name.all { it.isDigit() }) {
                        count++
                        val pid = name.toLongOrNull() ?: 0L
                        if (pid > maxPid) maxPid = pid
                    }
                }
                count to maxPid
            }.getOrDefault(0 to 0L)
        }
    }

    /** What [statFact] reports about one candidate overlay file. */
    data class FileFact(
        val exists: Boolean,
        val isRegularFile: Boolean,
        val isSymbolicLink: Boolean,
        val nlink: Long,
    ) {
        /** Upstream's _is_own_file: a plain file with exactly one link. */
        val isOwnFile: Boolean get() = exists && isRegularFile && !isSymbolicLink && nlink == 1L
    }

    /**
     * Outcome for one [GUEST_PATHS] entry.
     * Exactly one of [realReadable] / [overlaid] / [note] is the truth.
     */
    data class EntryOutcome(
        val guestPath: String,
        val realReadable: Boolean,
        val overlaid: Boolean,
        val hostFile: File? = null,
        val note: String? = null,
    )

    data class Result(
        val dir: File?,
        val outcomes: List<EntryOutcome>,
        val error: String? = null,
    ) {
        /** Verified overlays only — the sole input to [bindArgs]. */
        val overlays: List<Pair<String, File>>
            get() = outcomes.filter { it.overlaid && it.hostFile != null }
                .map { it.guestPath to it.hostFile!! }

        /** proot file-over-file binds, stable order, verified entries only. */
        fun bindArgs(): List<String> =
            overlays.map { (guest, host) -> "--bind=${host.absolutePath}:$guest" }

        /** One-line honest summary for Diagnostics. */
        fun summary(): String {
            if (error != null) return "probe failed: $error"
            if (outcomes.isEmpty()) return "no probe performed"
            val readable = outcomes.filter { it.realReadable }.map { it.guestPath.substringAfterLast('/') }
            val overlaid = outcomes.filter { it.overlaid }.map { it.guestPath.substringAfterLast('/') }
            val failed = outcomes.filter { !it.realReadable && !it.overlaid }.map { it.guestPath.substringAfterLast('/') }
            return buildString {
                append("real (kernel-readable): ")
                append(if (readable.isEmpty()) "none" else readable.joinToString(", "))
                append("; overlaid (kernel-denied): ")
                append(if (overlaid.isEmpty()) "none" else overlaid.joinToString(", "))
                if (failed.isNotEmpty()) {
                    append("; overlay FAILED: ")
                    append(failed.joinToString(", "))
                }
            }
        }
    }

    /**
     * Probe every standard file and write verified overlays for the denied
     * ones. Never throws; per-entry failures degrade to unbound entries with
     * a note, never to a fake success.
     *
     * [probeReadable] probes the REAL host file (one byte read). [statFact]
     * reports the pre/post-write file facts (device default uses
     * android.system.Os.lstat for the real st_nlink; tests inject).
     */
    fun prepare(
        sysdataDir: File,
        sources: Sources,
        probeReadable: (String) -> Boolean = ::defaultProbeReadable,
        statFact: (File) -> FileFact? = ::platformFileFact,
    ): Result {
        val outcomes = mutableListOf<EntryOutcome>()
        val dirReady = runCatching {
            if (!sysdataDir.isDirectory) {
                // refuse to write through a planted non-directory
                if (sysdataDir.exists()) {
                    if (!sysdataDir.delete()) throw java.io.IOException("cannot remove stale ${sysdataDir.name}")
                }
                if (!sysdataDir.mkdirs() && !sysdataDir.isDirectory) {
                    throw java.io.IOException("cannot create ${sysdataDir.absolutePath}")
                }
            }
            true
        }.getOrElse {
            outcomes += GUEST_PATHS.map {
                EntryOutcome(it, realReadable = false, overlaid = false, note = "sysdata dir unavailable")
            }
            return Result(dir = null, outcomes = outcomes, error = it.message)
        }

        for (guestPath in GUEST_PATHS) {
            val readable = runCatching { probeReadable(guestPath) }.getOrDefault(false)
            if (readable) {
                // REAL WINS — the core honesty rule, upstream-kept.
                outcomes += EntryOutcome(guestPath, realReadable = true, overlaid = false)
                continue
            }
            val name = guestPath.substringAfterLast('/')
            val content = generateContent(name, sources)
            if (content == null) {
                outcomes += EntryOutcome(
                    guestPath,
                    realReadable = false,
                    overlaid = false,
                    note = "no honest source available for this file on this device",
                )
                continue
            }
            val target = File(sysdataDir, name)
            val written = runCatching {
                writeVerified(target, content, statFact)
            }.getOrElse { false }
            outcomes += if (written) {
                EntryOutcome(guestPath, realReadable = false, overlaid = true, hostFile = target)
            } else {
                EntryOutcome(
                    guestPath,
                    realReadable = false,
                    overlaid = false,
                    note = "overlay write or verify failed — file not bound",
                )
            }
        }
        return Result(dir = sysdataDir, outcomes = outcomes)
    }

    /**
     * Read-only probe for the Diagnostics button (NEVER writes — the button
     * must stay read-mostly): which of the five real files does this kernel
     * allow the app to read?
     */
    fun probeReport(probeReadable: (String) -> Boolean = ::defaultProbeReadable): Map<String, Boolean> =
        GUEST_PATHS.associateWith { path -> runCatching { probeReadable(path) }.getOrDefault(false) }

    // ------------------------------------------------------------ generators

    /** Dispatch by file name; null = no honest source (entry degrades, unbound). */
    internal fun generateContent(name: String, sources: Sources): String? = when (name) {
        "version" -> versionContent(sources.unameRelease, sources.unameVersionTail)
        "uptime" -> uptimeContent(sources.elapsedRealtimeSeconds)
        "loadavg" -> loadavgContent(sources.readablePidCount, sources.maxReadablePid)
        "stat" -> statContent(sources.processorCount, btimeSeconds(sources))
        "vmstat" -> vmstatContent()
        else -> null
    }

    /**
     * /proc/version from the REAL uname(2) identity + explicit attribution.
     * Null when the kernel identity itself is unavailable (nothing honest to
     * write — the entry stays unbound).
     */
    internal fun versionContent(release: String?, versionTail: String?): String? {
        if (release.isNullOrBlank() || versionTail == null) return null
        return "Linux version $release $VERSION_ATTRIBUTION $versionTail\n"
    }

    /** Field 1 real (elapsedRealtime ≡ kernel uptime semantics); idle is a documented placeholder. */
    internal fun uptimeContent(elapsedRealtimeSeconds: Double): String {
        val up = if (elapsedRealtimeSeconds.isFinite() && elapsedRealtimeSeconds > 0) elapsedRealtimeSeconds else 0.0
        return "%.2f 0.00\n".format(up)
    }

    /** Load placeholders are documented; the pid tail is the real hidepid-filtered subset. */
    internal fun loadavgContent(readablePidCount: Int, maxReadablePid: Long): String =
        "0.00 0.00 0.00 0/$readablePidCount $maxReadablePid\n"

    /** REAL btime (epoch boot time); jiffies counters are documented placeholders. */
    internal fun btimeSeconds(sources: Sources): Long =
        sources.currentTimeSeconds - sources.elapsedRealtimeSeconds.toLong()

    internal fun statContent(cpuCount: Int, btime: Long): String {
        val cpus = if (cpuCount < 1) 1 else cpuCount
        return buildString {
            append("cpu  0 0 0 0 0 0 0 0 0 0\n")
            repeat(cpus) { n -> append("cpu$n 0 0 0 0 0 0 0 0 0 0\n") }
            append("intr 0\n")
            append("ctxt 0\n")
            append("btime $btime\n")
            append("processes 0\n")
            append("procs_running 0\n")
            append("procs_blocked 0\n")
            append("softirq 0\n")
        }
    }

    /**
     * Standard kernel counter-name skeleton, zero values. Names are kernel
     * facts (5.x /proc/vmstat shape); every value is an honest placeholder —
     * the real counters are not readable from an app domain.
     */
    internal fun vmstatContent(): String {
        val names = listOf(
            "nr_free_pages", "nr_zone_inactive_anon", "nr_zone_active_anon",
            "nr_zone_inactive_file", "nr_zone_active_file", "nr_zone_unevictable",
            "nr_zone_write_pending", "nr_mlock", "nr_bounce", "nr_zspages",
            "nr_free_cma", "numa_hit", "numa_miss", "numa_foreign",
            "numa_interleave", "numa_local", "numa_other",
            "nr_inactive_anon", "nr_active_anon", "nr_inactive_file",
            "nr_active_file", "nr_unevictable", "nr_slab_reclaimable",
            "nr_slab_unreclaimable", "nr_isolated_anon", "nr_isolated_file",
            "nr_anon_pages", "nr_mapped", "nr_file_pages", "nr_dirty",
            "nr_writeback", "nr_shmem", "nr_shmem_hugepages",
            "nr_file_hugepages", "nr_anon_transparent_hugepages",
            "nr_vmscan_write", "nr_dirtied", "nr_written",
            "nr_kernel_stack", "nr_page_table_pages", "nr_swapcached",
            "pgpgin", "pgpgout", "pswpin", "pswpout",
            "pgfree", "pgactivate", "pgdeactivate", "pgfault", "pgmajfault",
            "pgreuse", "pgsteal_kswapd", "pgsteal_direct",
            "pgscan_kswapd", "pgscan_direct", "oom_kill",
            "nr_dirty_threshold", "nr_dirty_background_threshold",
            "pgpromote_success", "pgpromote_candidate",
            "thp_fault_alloc", "thp_collapse_alloc",
            "unevictable_pgs_culled", "unevictable_pgs_scanned",
            "unevictable_pgs_rescued", "unevictable_pgs_mlocked",
            "unevictable_pgs_munlocked", "unevictable_pgs_cleared",
            "unevictable_pgs_stranded",
        )
        return names.joinToString("") { "$it 0\n" }
    }

    // ------------------------------------------------------- write + verify

    /**
     * Proportionate port of upstream's hardening: validate-or-drop the
     * existing entry (regular file, not a symlink, exactly one link —
     * anything else was planted), write fresh, then VERIFY by re-stat and
     * content round-trip before the caller may bind it. Upstream keeps
     * stale content (write-if-missing); we deliberately refresh per spawn.
     */
    private fun writeVerified(target: File, content: String, statFact: (File) -> FileFact?): Boolean {
        val fact = statFact(target)
        if (fact != null && fact.exists) {
            if (fact.isOwnFile) {
                // refresh in place — same inode policy: drop and remake keeps
                // one code path and re-runs the exclusivity checks below
                if (!target.delete()) return false
            } else {
                // planted or wrong type (symlink, hardlink, dir): drop ONLY
                // this name; upstream rule — nothing legitimate is lost
                if (!target.delete()) return false
            }
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        return try {
            // CREATE_NEW + NOFOLLOW: the bytes go into a NEW inode in THIS
            // directory and nowhere else; a symlink placed in a race window
            // is refused, not followed.
            Files.write(
                target.toPath(), bytes,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS,
            )
            val after = statFact(target)
            after != null && after.isOwnFile && target.readBytes().contentEquals(bytes)
        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------- device defaults

    /** One-byte read of the REAL host file; any failure (EACCES) = denied. */
    private fun defaultProbeReadable(guestPath: String): Boolean = try {
        File(guestPath).inputStream().use { it.read() }
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Device file facts via android.system.Os.lstat (real st_nlink; lstat
     * never follows symlinks). Null when even lstat fails oddly — callers
     * then write fresh with CREATE_NEW exclusivity.
     */
    private fun platformFileFact(file: File): FileFact? = runCatching {
        val st = android.system.Os.lstat(file.absolutePath)
        val type = st.st_mode and android.system.OsConstants.S_IFMT
        FileFact(
            exists = true,
            isRegularFile = type == android.system.OsConstants.S_IFREG,
            isSymbolicLink = type == android.system.OsConstants.S_IFLNK,
            nlink = st.st_nlink,
        )
    }.getOrElse {
        if (!file.exists()) {
            FileFact(exists = false, isRegularFile = false, isSymbolicLink = false, nlink = 0)
        } else {
            null
        }
    }
}
