package app.pocketshell.widget.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4.4 — the downloader's PURE decision layer (the HttpsURLConnection
 * wrapper is deliberately thin and exercised only on-device): guarded
 * path resolution against the catalog directory, the version gate, the
 * id-mismatch refusal, and the contractual bounds.
 */
class WidgetCatalogDownloaderTest {

    private val catalogUrl = "https://example.test/repo/main/catalog.json"

    private fun serversManifest(minAppVersion: String = "0.13.0") = WidgetManifest(
        id = "servers",
        name = "Servers",
        version = "1.0.0",
        minAppVersion = minAppVersion,
        capabilities = listOf("proc.net"),
        probe = WidgetManifest.Probe("proc.net.listen"),
        card = WidgetManifest.Card(itemTemplate = ":{port}  {process}"),
    )

    @Test
    fun `manifest paths resolve against the catalog directory`() {
        assertEquals(
            "https://example.test/repo/main/widgets/servers.json",
            resolveManifestUrl(catalogUrl, "widgets/servers.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/Taha-Raees/ps-widget-repo/main/widgets/ssh.json",
            resolveManifestUrl(DEFAULT_CATALOG_URL, "widgets/ssh.json"),
        )
    }

    @Test
    fun `path traversal absolute and backslash paths are refused`() {
        listOf(
            "../etc",
            "widgets/../../etc/passwd",
            "/etc/passwd",
            "a\\b",
            "",
            " ",
            "widgets/a b.json",
        ).forEach { path ->
            assertNull("path must be refused: '$path'", resolveManifestUrl(catalogUrl, path))
        }
    }

    @Test
    fun `non https catalog urls are refused`() {
        assertNull(catalogDirectory("http://example.test/catalog.json"))
        assertNull(resolveManifestUrl("http://example.test/catalog.json", "a.json"))
        assertNull(catalogDirectory("https://"))
    }

    @Test
    fun `the downloader constructor rejects non https urls`() {
        try {
            WidgetCatalogDownloader(catalogUrl = "http://example.test/catalog.json")
            throw AssertionError("constructor must reject cleartext urls")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("https"))
        }
    }

    @Test
    fun `the downloader is constructible without arguments`() {
        // The production call shape (the Control Center's lazy downloader):
        // construction IS the assertion — the default catalog URL passes
        // the constructor's https requirement.
        WidgetCatalogDownloader()
    }

    @Test
    fun `min app version compares as a numeric triple`() {
        listOf(
            Triple("0.13.0", "0.13.0", true),
            Triple("0.13.0-m7.3", "0.13.0", true), // the real versionName shape
            Triple("0.13.0-m7.3", "0.13.1", false),
            Triple("0.14.0", "0.13.0", true),
            Triple("0.9.9", "0.13.0", false),
            Triple("1.0.0", "0.99.99", true),
        ).forEach { (app, min, expected) ->
            assertEquals("app=$app min=$min", expected, appSupports(app, min))
        }
    }

    @Test
    fun `malformed versions refuse instead of guessing`() {
        listOf("latest", "v1.2.3", "", "0.13").forEach { bad ->
            assertTrue("app side must refuse: '$bad'", !appSupports(bad, "0.1.0"))
            assertTrue("min side must refuse: '$bad'", !appSupports("0.13.0", bad))
        }
    }

    @Test
    fun `a fetched manifest must match its catalog id and version gate`() {
        assertNull(
            acceptFetchedManifest(serversManifest(), expectedId = "servers", appVersion = "0.13.0-m7.3"),
        )
        val mismatch = acceptFetchedManifest(serversManifest(), expectedId = "other", appVersion = "0.13.0-m7.3")!!
        assertTrue(mismatch.contains("does not match"))
        val tooOld = acceptFetchedManifest(serversManifest(), expectedId = "servers", appVersion = "0.12.9")!!
        assertTrue(tooOld.contains("requires PocketShell"))
    }

    @Test
    fun `downloader bounds are contractual`() {
        assertEquals(256 * 1024, WidgetCatalogDownloader.MAX_RESPONSE_BYTES)
        assertEquals(10_000, WidgetCatalogDownloader.TIMEOUT_MS)
    }
}
