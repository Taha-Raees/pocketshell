package app.pocketshell.widget.external

import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageBreakdown
import app.pocketshell.widget.ssh.SshClientProcess
import app.pocketshell.widget.ssh.SshHostEntry

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
    sealed interface ProbeResult {
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

    fun render(manifest: WidgetManifest, result: ProbeResult): CardData {
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
}
