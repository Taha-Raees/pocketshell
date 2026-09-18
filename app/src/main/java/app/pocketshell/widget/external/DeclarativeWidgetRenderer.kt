package app.pocketshell.widget.external

import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageBreakdown

/**
 * M8 — the data-only declarative renderer: manifest + probe result → card
 * lines. Pure substitution over FIXED app code — the one piece a future
 * catalog installer plugs validated manifests into. There is no expression
 * language and no command surface: a template can only re-arrange the
 * fields the built-in probe already produced.
 */
object DeclarativeWidgetRenderer {

    /** The probe results a manifest-rendered card can receive. */
    sealed interface ProbeResult {
        data class Listeners(val sockets: List<ListeningSocket>) : ProbeResult
        data class Storage(val breakdown: StorageBreakdown) : ProbeResult
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
}
