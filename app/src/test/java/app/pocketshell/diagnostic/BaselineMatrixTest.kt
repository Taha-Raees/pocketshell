package app.pocketshell.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4.0.9 — pins for the rendering-reset control experiment's pure
 * specification. The WebView itself is never faked (testing contract);
 * these pins guard the experiment's SCIENTIFIC integrity instead: the
 * single-variable invariant, the matrix cycling, and the status block.
 */
class BaselineMatrixTest {

    @Test
    fun `the baseline is pure Android defaults`() {
        val b = BaselineMatrix.BASELINE
        assertFalse(b.chromeUa)
        assertFalse(b.forcedLight)
        assertFalse(b.midnightBg)
        assertFalse(b.loadBeforeAttach)
        assertFalse(b.wideViewport)
        assertTrue(BaselineMatrix.isSingleVariable(b))
    }

    @Test
    fun `m4_0_11 the declared winner is the baseline and stays single-variable`() {
        // The device sweep (2026-09-05) rendered complete pages on BASELINE
        // (all four gate sites, user recording), +CHROME UA and +MIDNIGHT BG
        // (device screenshots). The winner is the zero-delta control — the
        // most stable and least invasive by construction — and the copy
        // source for the production renderer (CompanionRenderContract).
        assertEquals(BaselineMatrix.BASELINE, BaselineMatrix.WINNER)
        assertEquals(0, BaselineMatrix.VARIANTS.indexOfFirst { it.key == BaselineMatrix.WINNER.key })
        assertTrue(BaselineMatrix.isSingleVariable(BaselineMatrix.WINNER))
    }

    @Test
    fun `every other variant differs from the baseline by exactly one variable`() {
        // The A/B discipline: a device result can name the breaking layer
        // only if no variant confounds two variables.
        BaselineMatrix.VARIANTS.filter { it.key != BaselineMatrix.BASELINE.key }
            .forEach { v ->
                assertTrue("variant ${v.key} must be single-variable", BaselineMatrix.isSingleVariable(v))
            }
        // And every Companion suspect from the brief is covered exactly once.
        val flags = BaselineMatrix.VARIANTS.filter { it.key != BaselineMatrix.BASELINE.key }
            .flatMap {
                listOfNotNull(
                    if (it.chromeUa) "chromeUa" else null,
                    if (it.forcedLight) "forcedLight" else null,
                    if (it.midnightBg) "midnightBg" else null,
                    if (it.loadBeforeAttach) "loadBeforeAttach" else null,
                    if (it.wideViewport) "wideViewport" else null,
                )
            }
        assertEquals(listOf("chromeUa", "forcedLight", "midnightBg", "loadBeforeAttach", "wideViewport"), flags)
    }

    @Test
    fun `url and variant cycles wrap without dead ends`() {
        var url = BaselineMatrix.URLS.first()
        repeat(BaselineMatrix.URLS.size) { url = BaselineMatrix.nextUrl(url) }
        assertEquals(BaselineMatrix.URLS.first(), url)

        var key = BaselineMatrix.VARIANTS.first().key
        repeat(BaselineMatrix.VARIANTS.size) { key = BaselineMatrix.nextVariant(key).key }
        assertEquals(BaselineMatrix.VARIANTS.first().key, key)

        // Unknown inputs join the cycle at its head instead of crashing.
        assertEquals(BaselineMatrix.URLS[0], BaselineMatrix.nextUrl("not-a-url"))
        assertEquals(BaselineMatrix.VARIANTS[0], BaselineMatrix.nextVariant("not-a-key"))
    }

    @Test
    fun `the test matrix carries the mandated gate URLs in order`() {
        assertEquals(
            listOf(
                "https://example.com/",
                "https://www.wikipedia.org/",
                "https://chatgpt.com/",
                "https://chat.z.ai/",
            ),
            BaselineMatrix.URLS,
        )
    }

    @Test
    fun `status carries the exact config and view truth`() {
        val text = BaselineMatrix.status(
            variant = BaselineMatrix.VARIANTS.first { it.key == "chrome-ua" },
            url = "https://chatgpt.com/",
            webviewVersion = "151.0.7922.199",
            userAgent = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/151 Mobile Safari/537.36",
            view = BaselineMatrix.ViewFacts(
                attached = true,
                width = 912,
                height = 1420,
                visibleLeft = 0,
                visibleTop = 118,
                visibleRight = 912,
                visibleBottom = 1538,
                layerType = "none",
            ),
            page = null,
        )
        assertTrue(text.contains("Render baseline — +CHROME UA"))
        assertTrue(text.contains("url: https://chatgpt.com/"))
        assertTrue(text.contains("webview: 151.0.7922.199"))
        assertTrue(text.contains("attached=true 912x1420px"))
        assertTrue(text.contains("visible=0,118-912,1538"))
        assertTrue(text.contains("layer=none"))
        // Without INSPECT there is no page line — never invented.
        assertFalse(text.contains("page:"))
        assertTrue(text.length <= BaselineMatrix.MAX_STATUS_LENGTH)
    }

    @Test
    fun `status carries the opt-in page metrics and an honest missing-view state`() {
        val withPage = BaselineMatrix.status(
            variant = BaselineMatrix.BASELINE,
            url = "https://example.com/",
            webviewVersion = "151.0.7922.199",
            userAgent = "Mozilla/5.0",
            view = null,
            page = BaselineMatrix.PageFacts(
                title = "Example Domain",
                innerWidth = 912,
                innerHeight = 0,
                devicePixelRatio = 2.625,
                visualWidth = 912,
                visualHeight = 1300,
            ),
        )
        assertTrue(withPage.contains("view: not created"))
        assertTrue(withPage.contains("page: title=\"Example Domain\" viewport=912x0 dpr=2.625"))
        assertTrue(withPage.length <= BaselineMatrix.MAX_STATUS_LENGTH)
        // A flooded status is capped, never truncated into a lie about length.
        val flooded = BaselineMatrix.status(
            variant = BaselineMatrix.BASELINE,
            url = "https://example.com/",
            webviewVersion = "x".repeat(2000),
            userAgent = "",
            view = null,
            page = null,
        )
        assertEquals(BaselineMatrix.MAX_STATUS_LENGTH, flooded.length)
    }

    @Test
    fun `layer types are named`() {
        assertEquals("none", BaselineMatrix.layerTypeName(android.view.View.LAYER_TYPE_NONE))
        assertEquals("software", BaselineMatrix.layerTypeName(android.view.View.LAYER_TYPE_SOFTWARE))
        assertEquals("hardware", BaselineMatrix.layerTypeName(android.view.View.LAYER_TYPE_HARDWARE))
    }

    @Test
    fun `page metrics probe is read-only json with safe fallback`() {
        val js = BaselineMatrix.PAGE_METRICS_JS
        // The probe must never mutate the page — no assignment, no fetch.
        assertFalse(js.contains("innerHTML"))
        assertFalse(js.contains("fetch("))
        assertTrue(js.contains("innerWidth"))
        assertTrue(js.contains("visualViewport"))
        assertTrue(js.contains("catch(e)"))
    }
}
