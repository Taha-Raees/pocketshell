package app.pocketshell.widget.external

import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageSubtree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — the declarative renderer: templates are PLAIN field substitution
 * over built-in probe output. No expression language, no commands, and an
 * unknown field renders as "—", never as code or a leak.
 */
class DeclarativeWidgetRendererTest {

    private val servers = WidgetManifest(
        id = "servers", name = "Servers", version = "1.0.0",
        capabilities = listOf("proc.net"),
        probe = WidgetManifest.Probe("proc.net.listen"),
        card = WidgetManifest.Card(headline = "Servers", emptyLine = "Nothing listening", itemTemplate = ":{port}  {process}"),
    )

    @Test
    fun `listener rows substitute port and process`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(
                listOf(
                    ListeningSocket(3000, 4, 1, 10, "node"),
                    ListeningSocket(8080, 6, 2, 11, null),
                ),
            ),
        )
        assertEquals("Servers", card.headline)
        assertEquals("2 running", card.countLine)
        assertEquals(listOf(":3000  node", ":8080  unknown"), card.rows)
        assertTrue(!card.empty)
    }

    @Test
    fun `an empty probe renders the manifest's empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(emptyList()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
    }

    @Test
    fun `unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `storage rows substitute name and size with the cache label`() {
        val storage = WidgetManifest(
            id = "storage", name = "Storage", version = "1.0.0",
            capabilities = listOf("storage.rootfs"),
            probe = WidgetManifest.Probe("storage.rootfs"),
            card = WidgetManifest.Card(itemTemplate = "{name}  {size}", maxLines = 3),
        )
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Storage(
                app.pocketshell.widget.probe.StorageBreakdown(
                    totalBytes = 38L * 1024 * 1024 * 1024,
                    subtrees = listOf(
                        StorageSubtree(StorageScan.APK_CACHE_NAME, 33L * 1024 * 1024 * 1024),
                        StorageSubtree("usr", 5L * 1024 * 1024),
                    ),
                    truncated = false,
                    scannedAtMillis = 0L,
                ),
            ),
        )
        assertEquals("38 GB", card.countLine)
        assertEquals(listOf("Package cache  33 GB", "usr  5.0 MB"), card.rows)
    }

    @Test
    fun `unknown template fields render as an em-dash placeholder`() {
        assertEquals(
            "a — b",
            DeclarativeWidgetRenderer.substitute("{known} {mystery} b", mapOf("known" to "a")),
        )
        assertEquals("plain", DeclarativeWidgetRenderer.substitute("plain", emptyMap()))
    }

    @Test
    fun `maxLines bounds the rows`() {
        val capped = servers.copy(card = servers.card.copy(maxLines = 1))
        val card = DeclarativeWidgetRenderer.render(
            capped,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(
                List(4) { ListeningSocket(it, 4, it.toLong(), null, "p") },
            ),
        )
        assertEquals(1, card.rows.size)
    }
}
