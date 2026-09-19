package app.pocketshell.widget.external

import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageSubtree
import app.pocketshell.widget.ssh.SshArgvTarget
import app.pocketshell.widget.ssh.SshClientProcess
import app.pocketshell.widget.ssh.SshHostEntry
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

    // ------------------------------------------------------- ssh.guest (M8.4.4)

    private val ssh = WidgetManifest(
        id = "ssh", name = "SSH", version = "1.0.0",
        capabilities = listOf("guest.ready"),
        probe = WidgetManifest.Probe("ssh.guest"),
        card = WidgetManifest.Card(headline = "SSH", emptyLine = "No hosts yet", itemTemplate = "{text}", maxLines = 3),
    )

    private val client = SshClientProcess(
        pid = 42,
        processName = "ssh",
        target = SshArgvTarget(user = "root", host = "web1", port = 2222, destinationRaw = "root@web1"),
    )

    private val host = SshHostEntry(
        patterns = listOf("web"),
        hostName = "web.example.com",
        user = "root",
        port = 22,
        identityFiles = emptyList(),
        wildcard = false,
    )

    private val hostWithoutName = SshHostEntry(
        patterns = listOf("db"),
        hostName = null,
        user = null,
        port = null,
        identityFiles = emptyList(),
        wildcard = false,
    )

    @Test
    fun `ssh guest rows list running clients then saved hosts`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(processes = listOf(client), hosts = listOf(host)),
        )
        assertEquals(listOf("→ root@web1", "web"), card.rows)
        assertEquals("2 hosts", card.countLine)
        assertTrue(!card.empty)
        assertTrue(!card.unavailable)
    }

    @Test
    fun `ssh guest templates substitute target and name fields`() {
        val templated = ssh.copy(card = ssh.card.copy(itemTemplate = "{target} ({name})"))
        val card = DeclarativeWidgetRenderer.render(
            templated,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(
                processes = listOf(client),
                hosts = listOf(host, hostWithoutName),
            ),
        )
        // An absent HostName renders as the placeholder — never a guess.
        assertEquals(listOf("root@web1 (ssh)", "web (web.example.com)", "db (—)"), card.rows)
    }

    @Test
    fun `an empty ssh guest renders the manifest empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(processes = emptyList(), hosts = emptyList()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `ssh guest maxLines caps across processes and hosts`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(
                processes = listOf(client, client.copy(pid = 43)),
                hosts = listOf(host, hostWithoutName),
            ),
        )
        assertEquals(3, card.rows.size)
        assertEquals("4 hosts", card.countLine)
    }

    @Test
    fun `ssh guest unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }
}
