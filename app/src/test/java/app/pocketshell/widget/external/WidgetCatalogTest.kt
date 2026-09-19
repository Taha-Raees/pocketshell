package app.pocketshell.widget.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * M8.4.4 — the catalog listing codec and the fetch bounds. The listing is
 * display data pointing at manifests (the trust boundary), so bad ENTRIES
 * are dropped while a non-catalog BODY is an honest null/failure; the
 * response cap fails a fetch BEFORE any parse can see truncated data.
 */
class WidgetCatalogTest {

    private fun catalog(vararg entries: String): String =
        """{"widgets":[${entries.joinToString(",")}]}"""

    private val entry =
        """{"id":"servers","name":"Servers","summary":"Dev servers","author":"Taha",""" +
            """"version":"1.0.0","minAppVersion":"0.13.0","file":"widgets/servers.json"}"""

    @Test
    fun `a well formed catalog parses with every field intact`() {
        val parsed = WidgetCatalogCodec.parse(catalog(entry))
        val widgets = WidgetCatalogCodec.sanitize(parsed!!).widgets
        assertEquals(1, widgets.size)
        val servers = widgets.single()
        assertEquals("servers", servers.id)
        assertEquals("Servers", servers.name)
        assertEquals("Dev servers", servers.summary)
        assertEquals("Taha", servers.author)
        assertEquals("1.0.0", servers.version)
        assertEquals("0.13.0", servers.minAppVersion)
        assertEquals("widgets/servers.json", servers.file)
    }

    @Test
    fun `missing optional entry fields take their defaults`() {
        val minimal = """{"id":"ssh","name":"SSH","version":"1.0.0","file":"widgets/ssh.json"}"""
        val widgets = WidgetCatalogCodec.sanitize(WidgetCatalogCodec.parse(catalog(minimal))!!).widgets
        assertEquals("", widgets.single().summary)
        assertEquals("0.0.0", widgets.single().minAppVersion)
    }

    @Test
    fun `an empty widgets array and an empty object are valid empty catalogs`() {
        assertEquals(emptyList<String>(), WidgetCatalogCodec.parse(catalog())!!.widgets.map { it.id })
        assertEquals(emptyList<String>(), WidgetCatalogCodec.parse("{}")!!.widgets.map { it.id })
    }

    @Test
    fun `duplicate ids keep the first entry`() {
        val newer = """{"id":"servers","name":"Servers again","version":"2.0.0","file":"widgets/other.json"}"""
        val widgets = WidgetCatalogCodec.sanitize(WidgetCatalogCodec.parse(catalog(entry, newer))!!).widgets
        assertEquals(1, widgets.size)
        assertEquals("1.0.0", widgets.single().version)
    }

    @Test
    fun `entries failing the shape checks are dropped`() {
        listOf(
            """{"id":"Servers","name":"x","version":"1.0.0","file":"a.json"}""", // id shape
            """{"id":"ok-name","name":"","version":"1.0.0","file":"a.json"}""", // empty name
            """{"id":"ok-name","name":"x","version":"1.0","file":"a.json"}""", // version shape
            """{"id":"ok-name","name":"x","version":"1.0.0","minAppVersion":"latest","file":"a.json"}""", // min shape
            """{"id":"ok-name","name":"x","version":"1.0.0","file":"../escape.json"}""", // traversal
            """{"id":"ok-name","name":"x","version":"1.0.0","file":"/abs.json"}""", // absolute
            """{"id":"ok-name","name":"x","version":"1.0.0","file":"a\\b.json"}""", // backslash
        ).forEach { candidate ->
            val widgets = WidgetCatalogCodec.sanitize(WidgetCatalogCodec.parse(catalog(candidate))!!).widgets
            assertTrue("entry must be dropped: $candidate", widgets.isEmpty())
        }
    }

    @Test
    fun `an unparseable catalog is null - never half parsed`() {
        assertNull(WidgetCatalogCodec.parse("not json"))
        assertNull(WidgetCatalogCodec.parse("""{"widgets":"many"}"""))
    }

    @Test
    fun `the response cap fails oversized bodies before any parse`() {
        val small = """{"widgets":[]}"""
        assertEquals(
            small,
            readCapped(ByteArrayInputStream(small.toByteArray()), WidgetCatalogDownloader.MAX_RESPONSE_BYTES),
        )
        val oversized = ByteArray(WidgetCatalogDownloader.MAX_RESPONSE_BYTES + 1) { 0x61 }
        assertNull(readCapped(ByteArrayInputStream(oversized), WidgetCatalogDownloader.MAX_RESPONSE_BYTES))
    }

    @Test
    fun `exactly the cap is not over the cap`() {
        val exact = ByteArray(WidgetCatalogDownloader.MAX_RESPONSE_BYTES) { 0x61 }
        assertEquals(
            String(exact, Charsets.UTF_8),
            readCapped(ByteArrayInputStream(exact), WidgetCatalogDownloader.MAX_RESPONSE_BYTES),
        )
    }

    @Test
    fun `the default catalog url is https`() {
        assertTrue(DEFAULT_CATALOG_URL.startsWith("https://"))
        assertTrue(DEFAULT_CATALOG_URL.endsWith("catalog.json"))
    }
}
