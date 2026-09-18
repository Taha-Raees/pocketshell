package app.pocketshell.widget.git

import app.pocketshell.packages.ExecResult

/**
 * The Git application's probe: ONE batched, read-only guest exec answers
 * everything a refresh needs (binary presence, repository discovery under
 * the guest's home, per-repo porcelain status). Never per-field spawns —
 * the getInstalledVersions batching discipline (AlpinePackageManager).
 *
 * The script and its parser are designed as a strict little protocol:
 * marker lines ("@@…") are structural, everything between @@REPO and @@RC
 * is passed to [GitStatusParser] untouched. A repo path would have to
 * start with "@@" to confuse it — and discovery only yields real
 * directories under the guest home.
 *
 * Read-only by contract: `git --version`, `find`, `git status` — nothing
 * that touches the index, the refs or the worktree (the Home application
 * never stages, commits or checks out; a terminal is where git work
 * happens, and the card only LOOKS). No DNS/workspace repair either — the
 * probe is offline and must never mutate the rootfs from Home.
 */

/** One discovered repository's rendered snapshot. */
internal data class RepoSnapshot(
    /** Absolute guest path (the discovery root of truth). */
    val path: String,
    /** Basename for display; the detail page shows the full mapped path. */
    val name: String,
    /** Parsed status; null when git could not read this repository. */
    val status: GitStatusParser.RepoStatus?,
    /** Real failure text (git exit code / truncated output); null = healthy. */
    val error: String?,
)

internal data class GitSnapshot(
    /** "2.34.1"-style version; null = git is not installed (honest state). */
    val gitVersion: String?,
    val repos: List<RepoSnapshot>,
) {
    val hasGit: Boolean get() = gitVersion != null
    val dirtyRepos: Int get() = repos.count { it.status?.dirty == true }
}

internal sealed interface ScanResult {
    data class Done(val snapshot: GitSnapshot) : ScanResult
    data class Failed(val reason: String) : ScanResult
}

/**
 * The stateful snapshotter — a UI-domain cache exactly like PortProbe: it
 * owns no lifecycle truth; the application's lifecycle-aware collect
 * decides WHEN to ask, and [shouldFullScan] is the idle gate that keeps an
 * open, idle Home card from exec'ing into the guest.
 *
 * Unlike ServerProbe there is no cheap kernel signal to gate on (the
 * contract for this application bans own-hand process-table reading — the
 * guest exec path is the ONLY probe), so the gate is time-based and the
 * cost bound is explicit: at most one guest exec per AUTO_RESCAN_MS while
 * the card is open, plus immediate scans on the Home→resume edge and on
 * the manual refresh control.
 */
internal class GitProbe(
    private val exec: GuestExec,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Runs one guest command (argv tail after proot) and waits, bounded. */
    fun interface GuestExec {
        fun exec(guestCommand: List<String>): ExecResult
    }

    private val lock = Any()
    private var hasScanned = false
    private var lastScanAtMs: Long = 0L
    private var lastSnapshot: GitSnapshot? = null

    /**
     * The idle gate (ServerProbe's shouldFullScan shape): a tick that fires
     * too soon after the last full scan does nothing at all — no guest
     * exec, no rootfs reads, no cost. The first tick always scans.
     */
    fun shouldFullScan(nowMs: Long): Boolean = synchronized(lock) {
        !hasScanned || nowMs - lastScanAtMs >= AUTO_RESCAN_MS
    }

    /** The most recent successful snapshot, if any (for instant re-layout). */
    val cached: GitSnapshot? get() = synchronized(lock) { lastSnapshot }

    /**
     * One full probe pass. Blocking (proot spawn + script) — callers wrap
     * in Dispatchers.IO, exactly like ServerProbe.snapshot(). Real exec
     * failures come back as [ScanResult.Failed]; the UI never dresses a
     * dead probe up as "no repositories".
     */
    fun snapshot(): ScanResult = synchronized(lock) {
        hasScanned = true
        lastScanAtMs = clock()
        val result: ScanResult = try {
            val out = exec.exec(
                listOf("/bin/sh", "-c", PROBE_SCRIPT, "sh"),
            )
            when {
                !out.success && out.error != null -> ScanResult.Failed(out.error!!)
                !out.success -> ScanResult.Failed(
                    out.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                        ?: "guest exec exited with ${out.exitCode}",
                )
                else -> {
                    val parsed = parseProbeOutput(out.stdout)
                    when {
                        parsed == null -> ScanResult.Failed("unrecognized probe output")
                        !parsed.complete -> ScanResult.Failed("probe output truncated")
                        else -> ScanResult.Done(toSnapshot(parsed))
                    }
                }
            }
        } catch (t: Throwable) {
            ScanResult.Failed(t.message ?: t.javaClass.simpleName)
        }
        if (result is ScanResult.Done) lastSnapshot = result.snapshot
        result
    }

    private fun toSnapshot(out: ProbeOutput): GitSnapshot {
        if (out.gitVersion == null) return GitSnapshot(gitVersion = null, repos = emptyList())
        return GitSnapshot(
            gitVersion = out.gitVersion,
            repos = out.repos.map { block ->
                RepoSnapshot(
                    path = block.path,
                    name = block.path.substringAfterLast('/').ifEmpty { block.path },
                    status = if (block.rc == 0) GitStatusParser.parse(block.lines.joinToString("\n")) else null,
                    error = when (block.rc) {
                        null -> "probe output truncated"
                        0 -> null
                        else -> "git exited with ${block.rc}"
                    },
                )
            },
        )
    }

    companion object {

        /** Bounded: a full guest round-trip (proot + find + N status calls). */
        const val SCAN_TIMEOUT_MS = 20_000L

        /** Cheap heartbeat tick: decides, spends nothing when the gate says no. */
        const val TICK_MS = 5_000L

        /** Minimum space between full guest execs on an open, watched card. */
        const val AUTO_RESCAN_MS = 20_000L

        /** The lone "$" — the probe script is a shell script, not a template. */
        private const val D = "$"

        /**
         * THE one batched script for a whole refresh (busybox ash, Alpine):
         *
         *   1. binary check — `command -v git`; absent → "@@GIT:" and done
         *      (the UI renders the honest "Git unavailable" state);
         *   2. discovery — one shallow find per well-known root ($HOME,
         *      $HOME/Projects) for any .git/HEAD file at repo depth ≤ 2 (a
         *      .git/HEAD FILE is what makes a directory a real, non-bare
         *      checkout); busybox find supports -maxdepth/-path;
         *   3. per repo — `git status --porcelain=v1 -b` (stable, script-
         *      documented format), its exit code after every block so one
         *      unreadable repository degrades alone;
         *   4. terminal `exit 0` — a completed scan is a successful probe
         *      no matter what git printed (the v0.4.4 mixed-answer lesson).
         *
         * `sort` gives a deterministic repo order; the pipeline's while
         * loop keeps the whole thing ONE exec (discovery and status share
         * the same proot spawn).
         */
        val PROBE_SCRIPT = """
            command -v git >/dev/null 2>&1 || { echo "@@GIT:"; exit 0; }
            echo "@@GIT:${D}(git --version 2>/dev/null | cut -d ' ' -f3)"
            for root in "${D}HOME" "${D}HOME/Projects"; do
              [ -d "${D}root" ] || continue
              find "${D}root" -maxdepth 3 -type f -path '*/.git/HEAD' 2>/dev/null
            done | sort | while IFS= read -r headpath; do
              d=${D}(dirname "${D}(dirname "${D}headpath")")
              echo "@@REPO:${D}d"
              git -C "${D}d" status --porcelain=v1 -b 2>/dev/null
              echo "@@RC:${D}?"
            done
            echo "@@DONE"
            exit 0
        """.trimIndent()
    }
}

// ------------------------------------------------------------ block parsing

/** One @@REPO…@@RC region straight from the probe output. */
internal data class RepoBlock(
    val path: String,
    /** The raw porcelain lines, verbatim (parsing is GitStatusParser's job). */
    val lines: List<String>,
    /** git's exit code; null when the block was cut short (truncated exec). */
    val rc: Int?,
)

internal data class ProbeOutput(
    val gitVersion: String?,
    val repos: List<RepoBlock>,
    /** False = the stream ended before "@@DONE" — output not trusted. */
    val complete: Boolean,
)

private class RepoBlockBuilder(val path: String) {
    val lines = ArrayList<String>()
    var rc: Int? = null

    fun build() = RepoBlock(path, lines.toList(), rc)
}

/**
 * Parse the probe's stdout. Null when no "@@GIT:" marker was seen at all —
 * output the protocol cannot vouch for is a Failed probe, never data.
 */
internal fun parseProbeOutput(stdout: String): ProbeOutput? {
    var sawGitMarker = false
    var gitVersion: String? = null
    var complete = false
    val repos = ArrayList<RepoBlock>()
    var current: RepoBlockBuilder? = null

    fun flush() {
        current?.let { repos.add(it.build()) }
        current = null
    }

    for (line in stdout.lineSequence()) {
        when {
            line.startsWith("@@GIT:") -> {
                sawGitMarker = true
                gitVersion = line.removePrefix("@@GIT:").trim().ifEmpty { null }
            }
            line.startsWith("@@REPO:") -> {
                flush()
                current = RepoBlockBuilder(line.removePrefix("@@REPO:"))
            }
            line.startsWith("@@RC:") -> {
                current?.rc = line.removePrefix("@@RC:").trim().toIntOrNull()
                flush()
            }
            line == "@@DONE" -> {
                complete = true
                flush()
            }
            else -> current?.lines?.add(line)
        }
    }
    flush()
    return if (sawGitMarker) ProbeOutput(gitVersion, repos, complete) else null
}

/**
 * Guest paths for humans: the guest home (where discovery looks) shows as
 * "~", everything else passes through verbatim — never reinterpreted.
 */
internal fun displayGuestRepoPath(path: String, guestHome: String = "/root"): String = when {
    path == guestHome -> "~"
    path.startsWith("$guestHome/") -> "~" + path.removePrefix(guestHome)
    else -> path
}
