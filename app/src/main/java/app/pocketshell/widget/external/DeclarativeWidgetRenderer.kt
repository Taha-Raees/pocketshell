package app.pocketshell.widget.external

import app.pocketshell.widget.git.RepoSnapshot
import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageBreakdown
import app.pocketshell.widget.ssh.SshClientProcess
import app.pocketshell.widget.ssh.SshHostEntry
import app.pocketshell.widget.sync.SyncProfile
import app.pocketshell.widget.sync.SyncProfiles

/**
 * M8 — the data-only declarative renderer: manifest + probe result → card
 * lines. Pure substitution over FIXED app code — the one piece a future
 * catalog installer plugs validated manifests into. There is no expression
 * language and no command surface: a template can only re-arrange the
 * fields the built-in probe already produced. Nothing here executes,
 * connects, opens a file, or reads a manifest-declared path: every branch
 * consumes the output of its own built-in probe, and a new probe kind can
 * only ever be added as fixed app code in THIS file.
 */
object DeclarativeWidgetRenderer {

    /** The probe results a manifest-rendered card can receive. */
    internal sealed interface ProbeResult {
        data class Listeners(val sockets: List<ListeningSocket>) : ProbeResult
        data class Storage(val breakdown: StorageBreakdown) : ProbeResult

        /**
         * M8.4.4 — the `ssh.guest` primitive's result: live own-UID `ssh`
         * client processes (argv facts) plus the saved `~/.ssh/config`
         * hosts. Template fields per row: `target` (destination as typed /
         * the host alias), `name` (process name / resolved HostName when
         * the config states one), and `text` (the canonical row: "→ target"
         * for a running client, the alias for a saved host) — so the schema
         * default template "{text}" and "{target}" both render honestly.
         */
        data class SshGuest(
            val processes: List<SshClientProcess>,
            val hosts: List<SshHostEntry>,
        ) : ProbeResult

        /**
         * M8.4.4 — the `git.overview` primitive's result: the compiled
         * GitApp probe's repository snapshots, one row each. Template
         * fields per row: `name`, `branch` (the porcelain head line's
         * branch — "detached"/"unknown" when git said nothing), `status`
         * ("OK"/"DIRTY", "ERROR" for a repository git could not read —
         * the compiled card's dirty derivation, mirrored), `dirty`
         * ("dirty"/"clean"), and `ahead`/`behind` (integers as strings,
         * present only when the head line carries a real divergence — an
         * absent field renders as "—", never a guessed 0). Internal
         * because [RepoSnapshot] is the git package's own type.
         */
        data class GitOverview(
            val repos: List<RepoSnapshot>,
        ) : ProbeResult

        /**
         * M8.4.4 — the `sync.overview` primitive's result: the user's own
         * sync profiles (the SyncRepository flow), one row each, with the
         * render clock so [SyncProfiles.statusLine] stays a pure call.
         * Template fields per row: `source` and `destination` (guest-home
         * paths as "~"-displayed, the compiled card's rule), `backend`
         * ("rsync"/"rclone"), and `status` (the profile's real run facts:
         * "never run" or "2h ago · OK (exit 0)").
         */
        data class SyncOverview(
            val profiles: List<SyncProfile>,
            val nowMs: Long,
        ) : ProbeResult

        data object Unavailable : ProbeResult
    }

    /** The card's data: headline, up to maxLines rows, or the empty line. */
    data class CardData(
        val headline: String,
        val countLine: String?,
        val rows: List<String>,
        val empty: Boolean,
        val unavailable: Boolean,
    )

    internal fun render(manifest: WidgetManifest, result: ProbeResult): CardData {
        val headline = manifest.card.headline.ifEmpty { manifest.name }
        return when (result) {
            is ProbeResult.Unavailable -> CardData(
                headline = headline,
                countLine = null,
                rows = emptyList(),
                empty = false,
                unavailable = true,
            )
            is ProbeResult.Listeners -> {
                val rows = result.sockets
                    .take(manifest.card.maxLines)
                    .map { substitute(manifest.card.itemTemplate, listenerFields(it)) }
                CardData(
                    headline = headline,
                    countLine = if (result.sockets.isEmpty()) null else "${result.sockets.size} running",
                    rows = rows,
                    empty = result.sockets.isEmpty(),
                    unavailable = false,
                )
            }
            is ProbeResult.Storage -> {
                val rows = result.breakdown.subtrees
                    .take(manifest.card.maxLines)
                    .map { substitute(manifest.card.itemTemplate, storageFields(it)) }
                CardData(
                    headline = headline,
                    countLine = if (result.breakdown.totalBytes == 0L) null else {
                        StorageScan.formatBytes(result.breakdown.totalBytes)
                    },
                    rows = rows,
                    empty = result.breakdown.totalBytes == 0L,
                    unavailable = false,
                )
            }
            is ProbeResult.SshGuest -> {
                // Live clients first (the connection state), then saved
                // hosts — the compiled SshApp overview's ordering, as data.
                val total = result.processes.size + result.hosts.size
                val rows = (
                    result.processes.map { process ->
                        substitute(manifest.card.itemTemplate, processFields(process))
                    } + result.hosts.map { entry ->
                        substitute(manifest.card.itemTemplate, hostFields(entry))
                    }
                    ).take(manifest.card.maxLines)
                CardData(
                    headline = headline,
                    countLine = if (total == 0) null else "$total hosts",
                    rows = rows,
                    empty = total == 0,
                    unavailable = false,
                )
            }
            is ProbeResult.GitOverview -> {
                // One row per discovered repository, discovery order (the
                // probe's deterministic sort). A git-less guest scans Done
                // with NO repositories — the manifest's empty line, never
                // a fabricated row.
                val rows = result.repos
                    .take(manifest.card.maxLines)
                    .map { substitute(manifest.card.itemTemplate, gitFields(it)) }
                CardData(
                    headline = headline,
                    countLine = if (result.repos.isEmpty()) null else "${result.repos.size} repos",
                    rows = rows,
                    empty = result.repos.isEmpty(),
                    unavailable = false,
                )
            }
            is ProbeResult.SyncOverview -> {
                // One row per profile, the repository's own order (the
                // store's insertion order). An empty profile store is the
                // manifest's empty line.
                val rows = result.profiles
                    .take(manifest.card.maxLines)
                    .map { substitute(manifest.card.itemTemplate, syncFields(it, result.nowMs)) }
                CardData(
                    headline = headline,
                    countLine = if (result.profiles.isEmpty()) null else "${result.profiles.size} profiles",
                    rows = rows,
                    empty = result.profiles.isEmpty(),
                    unavailable = false,
                )
            }
        }
    }

    /** {field} substitution; unknown fields render as "—", never as code. */
    internal fun substitute(template: String, fields: Map<String, String>): String =
        Regex("""\{([a-z]+)\}""").replace(template) { match ->
            fields[match.groupValues[1]] ?: "—"
        }

    private fun listenerFields(socket: ListeningSocket): Map<String, String> = mapOf(
        "port" to socket.port.toString(),
        "process" to (socket.processName ?: "unknown"),
        "text" to ":${socket.port}  ${socket.processName ?: "unknown"}",
    )

    private fun storageFields(subtree: app.pocketshell.widget.probe.StorageSubtree): Map<String, String> {
        val label = if (subtree.name == app.pocketshell.widget.probe.StorageScan.APK_CACHE_NAME) {
            "Package cache"
        } else {
            subtree.name
        }
        val size = StorageScan.formatBytes(subtree.bytes)
        return mapOf(
            "name" to label,
            "size" to size,
            "text" to "$label  $size",
        )
    }

    /** What an ssh client's argv literally says — never enriched. */
    private fun processFields(process: SshClientProcess): Map<String, String> = mapOf(
        "target" to process.targetDisplay,
        "name" to process.processName,
        "text" to "→ ${process.targetDisplay}",
    )

    /**
     * A saved host: `target` is the alias the user invokes ssh with;
     * `name` (the resolved HostName) is present only when the config
     * actually states one — an absent field renders as "—", not a guess.
     */
    private fun hostFields(entry: SshHostEntry): Map<String, String> = buildMap {
        put("target", entry.displayName)
        entry.hostName?.let { put("name", it) }
        put("text", entry.displayName)
    }

    // ------------------------------------------------ git.overview fields

    /**
     * One repository's row fields. The branch and dirty derivations mirror
     * the compiled GitApp's pure helpers (the porcelain head line, never a
     * guess): a repository git could not read keeps its name and reports
     * the ERROR status — it degrades alone, it does not silently pose as
     * clean. ahead/behind ride only when git reported a real divergence
     * (the ssh host `name` rule: an absent field renders as "—").
     */
    private fun gitFields(repo: RepoSnapshot): Map<String, String> = buildMap {
        put("name", repo.name)
        put("branch", gitBranchText(repo.status))
        put("status", gitStatusText(repo))
        put("dirty", if (repo.status?.dirty == true) "dirty" else "clean")
        repo.status?.ahead?.let { put("ahead", it.toString()) }
        repo.status?.behind?.let { put("behind", it.toString()) }
    }

    /** The porcelain head line's branch, the compiled card's vocabulary. */
    private fun gitBranchText(status: app.pocketshell.widget.git.GitStatusParser.RepoStatus?): String =
        when {
            status == null -> "unknown"
            status.detached -> "detached"
            else -> status.branch ?: "unknown"
        }

    /** The row's verdict: "OK", "DIRTY", or "ERROR" when git gave nothing. */
    private fun gitStatusText(repo: RepoSnapshot): String = when {
        repo.error != null || repo.status == null -> "ERROR"
        repo.status.dirty -> "DIRTY"
        else -> "OK"
    }

    // ------------------------------------------------ sync.overview fields

    /**
     * One profile's row fields — the profile's own facts, displayed the
     * way the compiled SyncApp displays them: "~"-mapped paths and the
     * run-status line ("never run" / "2h ago · OK (exit 0)"). Nothing is
     * enriched: a profile that never ran says exactly that.
     */
    private fun syncFields(profile: SyncProfile, nowMs: Long): Map<String, String> = mapOf(
        "source" to SyncProfiles.displayPath(profile.source),
        "destination" to SyncProfiles.displayPath(profile.destination),
        "backend" to profile.backend.name.lowercase(),
        "status" to SyncProfiles.statusLine(profile, nowMs),
    )
}
