package app.pocketshell.widget.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4.4 — the installed-manifests codec: what install writes, decode
 * reads back unchanged; anything that no longer passes the validator in
 * full is dropped; ids dedupe first-wins; the cap holds on BOTH paths;
 * a corrupt record degrades honestly to EMPTY.
 */
class WidgetInstallStoreTest {

    private fun manifest(
        id: String,
        minAppVersion: String = "0.0.0",
        name: String = "Widget",
    ) = WidgetManifest(
        id = id,
        name = name,
        version = "1.0.0",
        minAppVersion = minAppVersion,
        capabilities = listOf("proc.net"),
        probe = WidgetManifest.Probe("proc.net.listen"),
        card = WidgetManifest.Card(emptyLine = "Nothing yet"),
    )

    @Test
    fun `installed manifests round trip through the codec`() {
        val installed = listOf(manifest("servers"), manifest("ssh", name = "SSH"))
        assertEquals(installed, WidgetInstallCodec.decode(WidgetInstallCodec.encode(installed)))
    }

    @Test
    fun `manifests that no longer validate are dropped on decode`() {
        val encoded = WidgetInstallCodec.encode(listOf(manifest(id = "Servers")))
        assertEquals(emptyList<WidgetManifest>(), WidgetInstallCodec.decode(encoded))
    }

    @Test
    fun `duplicate ids keep the first manifest`() {
        val duplicated = listOf(manifest("servers", name = "First"), manifest("ssh")) +
            listOf(manifest("servers", name = "Second"))
        val decoded = WidgetInstallCodec.decode(WidgetInstallCodec.encode(duplicated))
        assertEquals(listOf("servers", "ssh"), decoded.map { it.id })
        assertEquals("First", decoded.first { it.id == "servers" }.name)
    }

    @Test
    fun `a corrupt record decodes to empty - never partially trusted`() {
        assertEquals(emptyList<WidgetManifest>(), WidgetInstallCodec.decode("{broken"))
        assertEquals(emptyList<WidgetManifest>(), WidgetInstallCodec.decode(null))
        assertEquals(emptyList<WidgetManifest>(), WidgetInstallCodec.decode("[]"))
    }

    @Test
    fun `the decode path is capped at twelve widgets`() {
        val thirteen = (1..13).map { index -> manifest("widget-$index") }
        assertEquals(12, WidgetInstallCodec.decode(WidgetInstallCodec.encode(thirteen)).size)
    }

    @Test
    fun `install overwrites the same id and keeps order`() {
        val current = WidgetInstallCodec.install(
            listOf(manifest("a"), manifest("b")),
            manifest("a", name = "Updated"),
        )
        assertEquals(listOf("b", "a"), current.map { it.id })
        assertEquals("Updated", current.single { it.id == "a" }.name)
    }

    @Test
    fun `install into a full store refuses loudly instead of evicting`() {
        val full = (1..WidgetInstallCodec.MAX_INSTALLED).map { index -> manifest("widget-$index") }
        try {
            WidgetInstallCodec.install(full, manifest("one-more"))
            throw AssertionError("install past the cap must refuse")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("limit"))
        }
        // Re-installing an EXISTING id never hits the cap (it replaces).
        assertEquals(
            full.size,
            WidgetInstallCodec.install(full, manifest("widget-1", name = "Replaced")).size,
        )
    }
}
