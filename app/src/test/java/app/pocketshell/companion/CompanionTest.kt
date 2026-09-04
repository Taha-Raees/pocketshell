package app.pocketshell.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 — Companion unit pins (docs/PHASE-4-COMPANION-DESIGN.md §18).
 * Pure logic only: validation, tab reducer, back decision, height math,
 * JSON round-trips. No WebView fakes (the project rule) — device behavior
 * is gated manually in docs/TESTING.md §17.
 */
class CompanionTest {

    // ---- URL validation (contract §4) --------------------------------------

    @Test
    fun `scheme-less url gets https`() {
        assertEquals("https://chatgpt.com", CompanionValidation.normalizeUrl("chatgpt.com"))
        assertEquals("https://github.com", CompanionValidation.normalizeUrl("  github.com  "))
    }

    @Test
    fun `explicit https and http survive`() {
        assertEquals("https://chat.deepseek.com", CompanionValidation.normalizeUrl("https://chat.deepseek.com"))
        assertEquals("http://192.168.1.10:8080", CompanionValidation.normalizeUrl("http://192.168.1.10:8080"))
    }

    @Test
    fun `forbidden schemes are rejected`() {
        listOf(
            "javascript:alert(1)",
            "file:///etc/passwd",
            "data:text/html;base64,AAAA",
            "about:blank",
            "intent://x#Intent;scheme=y;end",
            "blob:https://x/y",
        ).forEach { url -> assertNull(url, CompanionValidation.normalizeUrl(url)) }
    }

    @Test
    fun `malformed urls are rejected`() {
        assertNull(CompanionValidation.normalizeUrl(""))
        assertNull(CompanionValidation.normalizeUrl("   "))
        assertNull(CompanionValidation.normalizeUrl("not a url"))
        assertNull(CompanionValidation.normalizeUrl("https://"))
        assertNull(CompanionValidation.normalizeUrl("ftp://files.example.com"))
    }

    @Test
    fun `host without dot is rejected`() {
        // URI parses it; the contract requires a dotted host.
        assertNull(CompanionValidation.normalizeUrl("http://localhost"))
    }

    @Test
    fun `oversized input rejected`() {
        val long = "a".repeat(CompanionValidation.MAX_URL_LENGTH + 1)
        assertNull(CompanionValidation.normalizeUrl(long))
    }

    // ---- name validation ----------------------------------------------------

    @Test
    fun `name is trimmed and capped`() {
        assertEquals("ChatGPT", CompanionValidation.validateName("  ChatGPT "))
        assertNull(CompanionValidation.validateName("   "))
        assertNull(CompanionValidation.validateName("x".repeat(CompanionValidation.MAX_NAME_LENGTH + 1)))
    }

    // ---- tab reducer (contract §6) ------------------------------------------

    private fun tab(defId: String) = TabRecord(defId)

    @Test
    fun `open focuses existing tab instead of duplicating`() {
        val tabs = listOf(tab("a"), tab("b"))
        val result = CompanionTabs.opened(tabs, "a")
        assertEquals(tabs, result)
        assertEquals(0, CompanionTabs.indexAfterOpen(tabs, "a"))
    }

    @Test
    fun `open appends new tab`() {
        val tabs = listOf(tab("a"))
        val result = CompanionTabs.opened(tabs, "b")
        assertEquals(listOf(tab("a"), tab("b")), result)
        assertEquals(1, CompanionTabs.indexAfterOpen(tabs, "b"))
    }

    @Test
    fun `closing inactive tab keeps selection`() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val (surviving, next) = CompanionTabs.closed(tabs, "b", activeDefId = "c")
        assertEquals(listOf(tab("a"), tab("c")), surviving)
        assertEquals("c", next)
    }

    @Test
    fun `closing active tab selects left neighbor then right`() {
        val tabs = listOf(tab("a"), tab("b"), tab("c"))
        val (surviving1, next1) = CompanionTabs.closed(tabs, "b", activeDefId = "b")
        assertEquals("a", next1)
        val (surviving2, next2) = CompanionTabs.closed(listOf(tab("a"), tab("c")), "a", activeDefId = "a")
        assertEquals("c", next2)
        assertEquals(listOf(tab("c")), surviving2)
    }

    @Test
    fun `closing last tab clears selection`() {
        val (surviving, next) = CompanionTabs.closed(listOf(tab("a")), "a", "a")
        assertTrue(surviving.isEmpty())
        assertNull(next)
    }

    @Test
    fun `deleting a definition removes its tabs and repairs selection`() {
        val tabs = listOf(tab("a"), tab("b"))
        val (surviving, next) = CompanionTabs.defsRemoved(tabs, "a", activeDefId = "a")
        assertEquals(listOf(tab("b")), surviving)
        assertEquals("b", next)
    }

    @Test
    fun `resolved active repairs stale selections`() {
        val tabs = listOf(tab("a"), tab("b"))
        assertEquals("b", CompanionTabs.resolvedActive(tabs, "b"))
        assertEquals("a", CompanionTabs.resolvedActive(tabs, "gone"))
        // No explicit selection but tabs exist → first tab (cold-restore UX).
        assertEquals("a", CompanionTabs.resolvedActive(tabs, null))
        // No tabs at all → honest null.
        assertNull(CompanionTabs.resolvedActive(emptyList(), "a"))
    }

    // ---- JSON round-trips (contract §5) --------------------------------------

    @Test
    fun `defs and tabs json round-trip`() {
        val defs = listOf(
            CompanionDef("id1", "ChatGPT", "https://chatgpt.com"),
            CompanionDef("id2", "GitHub", "https://github.com"),
        )
        val encoded = CompanionJson.encodeToString(defs)
        assertEquals(defs, CompanionJson.decodeFromString<List<CompanionDef>>(encoded))

        val tabs = listOf(TabRecord("id1", "https://github.com/pocketshell"))
        assertEquals(tabs, CompanionJson.decodeFromString<List<TabRecord>>(CompanionJson.encodeToString(tabs)))
    }

    // ---- back decision (contract §14) ----------------------------------------

    @Test
    fun `back decision order is web then collapse then passthrough`() {
        assertEquals(CompanionBackAction.PASS_THROUGH, decideBackAction(canGoBack = true, raised = false))
        assertEquals(CompanionBackAction.WEB_BACK, decideBackAction(canGoBack = true, raised = true))
        assertEquals(CompanionBackAction.COLLAPSE, decideBackAction(canGoBack = false, raised = true))
    }

    // ---- height math (contract §9) --------------------------------------------

    @Test
    fun `release snaps only within the gentle window`() {
        assertEquals(CompanionHeights.HALF, CompanionHeights.settled(CompanionHeights.HALF + 0.04f))
        assertEquals(CompanionHeights.FULL, CompanionHeights.settled(CompanionHeights.FULL - 0.05f))
        // Outside the window: stay exactly where released.
        val free = CompanionHeights.HALF + 0.12f
        assertEquals(free, CompanionHeights.settled(free))
    }

    @Test
    fun `release below the collapse threshold collapses`() {
        assertEquals(0f, CompanionHeights.settled(0.07f))
        assertEquals(0.09f, CompanionHeights.settled(0.09f))
    }

    @Test
    fun `fractions clamp into the legal band`() {
        assertEquals(0f, CompanionHeights.clamp(-0.5f))
        assertEquals(CompanionHeights.FULL, CompanionHeights.clamp(1.5f))
        assertEquals(0.4f, CompanionHeights.clamp(0.4f))
    }

    @Test
    fun `raised means at or above the minimum`() {
        assertFalse(CompanionHeights.isRaised(0f))
        assertFalse(CompanionHeights.isRaised(0.01f))
        assertTrue(CompanionHeights.isRaised(CompanionHeights.MIN_RAISED))
        assertTrue(CompanionHeights.isRaised(0.5f))
    }

    // ---- failure surfaces (m4.0.2) ------------------------------------------

    @Test
    fun `failure titles map from kind`() {
        assertEquals(
            "Page didn't load",
            CompanionFailure(CompanionFailureKind.LOAD_ERROR).title,
        )
        assertEquals(
            "Page renderer crashed",
            CompanionFailure(CompanionFailureKind.RENDERER_GONE).title,
        )
        // m4.0.3: the silent-white signature gets its own honest title.
        assertEquals(
            "Page never rendered",
            CompanionFailure(CompanionFailureKind.RENDER_STALLED).title,
        )
    }

    @Test
    fun `failure body joins detail and webview version`() {
        val failure = CompanionFailure(
            CompanionFailureKind.LOAD_ERROR,
            detail = "net::ERR_NAME_NOT_RESOLVED",
            webViewVersion = "109.0.5414.117",
        )
        assertEquals(
            "net::ERR_NAME_NOT_RESOLVED · Android System WebView 109.0.5414.117",
            failure.body,
        )
    }

    @Test
    fun `failure body skips missing parts`() {
        assertEquals(
            "Android System WebView 100.0.0.0",
            CompanionFailure(CompanionFailureKind.RENDERER_GONE, webViewVersion = "100.0.0.0").body,
        )
        assertEquals(
            "net::ERR_TIMED_OUT",
            CompanionFailure(CompanionFailureKind.LOAD_ERROR, detail = "net::ERR_TIMED_OUT").body,
        )
        assertEquals("", CompanionFailure(CompanionFailureKind.LOAD_ERROR).body)
        assertEquals(
            "",
            CompanionFailure(CompanionFailureKind.LOAD_ERROR, detail = "  ", webViewVersion = "").body,
        )
    }

    @Test
    fun `failure hints point at the real fix`() {
        assertTrue(
            CompanionFailure(CompanionFailureKind.LOAD_ERROR).hint.contains("network/VPN"),
        )
        assertTrue(
            CompanionFailure(CompanionFailureKind.RENDERER_GONE).hint.contains("Update or roll it back"),
        )
    }

    @Test
    fun `render-stall failure explains itself after both render modes stalled`() {
        // m4.0.4: the pixel probe's card. The FIRST stall silently retries on
        // the software renderer; this card only appears once BOTH modes gave
        // up — so the hint says exactly that, plus the WebView version.
        val failure = CompanionFailure(
            CompanionFailureKind.RENDER_STALLED,
            webViewVersion = "99.0.0.0",
        )
        assertEquals("Page never rendered", failure.title)
        assertEquals("Android System WebView 99.0.0.0", failure.body)
        assertTrue(failure.hint.contains("never drew a single frame"))
        assertTrue(failure.hint.contains("compatibility renderer"))
    }

    // ---- render probe (m4.0.4) ----------------------------------------------

    @Test
    fun `render probe - flash-guard background is the pool's exact canvas color`() {
        // Single source of truth: createWebView paints THIS value, and the
        // probe reads the SAME value — a canvas uniformly in it never drew.
        assertEquals(0xFF080F1D.toInt(), RenderProbe.WEBVIEW_BACKGROUND)
    }

    @Test
    fun `render probe - uniform flash-guard canvas means nothing painted`() {
        val bg = RenderProbe.WEBVIEW_BACKGROUND
        assertFalse(RenderProbe.hasPainted(IntArray(64) { bg }, bg))
        // An empty sample has no evidence either way — never a false "painted".
        assertFalse(RenderProbe.hasPainted(IntArray(0), bg))
    }

    @Test
    fun `render probe - any differing pixel counts as painted`() {
        val bg = RenderProbe.WEBVIEW_BACKGROUND
        val mostlyBackground = IntArray(96) { bg }.also { it[42] = 0xFFFFFFFF.toInt() }
        assertTrue(RenderProbe.hasPainted(mostlyBackground, bg))
        // A page that painted its OWN solid color counts — even a near
        // identical dark (only the EXACT flash-guard value means nothing).
        assertTrue(RenderProbe.hasPainted(IntArray(64) { 0xFF080F1E.toInt() }, bg))
        assertTrue(RenderProbe.hasPainted(IntArray(64) { 0xFF000000.toInt() }, bg))
    }

    @Test
    fun `render probe - verdict samples the main region, not the banner dock`() {
        // m4.0.4 device lesson (2026-09-05 cookie-banner screenshot): the
        // site's own bottom-docked consent banner painted a few pixels on a
        // dead canvas and the whole-canvas rule stood down. The judged
        // region now excludes that dock — a banner can never vouch for a
        // page whose main content never drew.
        assertEquals(72, RenderProbe.mainRegionRows(96))
        assertEquals(75, RenderProbe.mainRegionRows(100))
        assertEquals(75, RenderProbe.mainRegionRows(101))
        // Degenerate samples still judge at least one row — never zero.
        assertEquals(1, RenderProbe.mainRegionRows(1))
        assertEquals(1, RenderProbe.mainRegionRows(2))
        assertEquals(3, RenderProbe.mainRegionRows(4))
        assertEquals(99, RenderProbe.mainRegionRows(132))
    }

    @Test
    fun `render probe - glass verdict samples the keyboard-free top half`() {
        // m4.0.7: the glass probe reads the PRESENTED surface, where the
        // system keyboard may dock over the canvas's lower part — its
        // Midnight pixels must never vouch for the page. The judged region
        // is the top half: keyboard-free whenever the sheet is raised, and
        // a working page always paints there.
        assertEquals(48, RenderProbe.glassRegionRows(96))
        assertEquals(50, RenderProbe.glassRegionRows(100))
        assertEquals(50, RenderProbe.glassRegionRows(101))
        // Degenerate samples still judge at least one row — never zero.
        assertEquals(1, RenderProbe.glassRegionRows(1))
        assertEquals(1, RenderProbe.glassRegionRows(2))
        assertEquals(1, RenderProbe.glassRegionRows(3))
        // And the glass region is never LARGER than the main region it
        // refines (the banner dock stays excluded in the fallback path).
        assertTrue(RenderProbe.glassRegionRows(200) <= RenderProbe.mainRegionRows(200))
    }

    // ---- embedded-webview compat (m4.0.5) -----------------------------------

    @Test
    fun `web compat - webview UA markers stripped to the device's chrome UA`() {
        val webviewUa =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A.231005.007; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A.231005.007) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36",
            WebCompat.chromeLikeUserAgent(webviewUa),
        )
    }

    @Test
    fun `web compat - chrome UA passes through unchanged (idempotent)`() {
        val chromeUa =
            "Mozilla/5.0 (Linux; Android 14; SM-S928B Build/UP1A.231005.007) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        assertEquals(chromeUa, WebCompat.chromeLikeUserAgent(chromeUa))
        assertEquals(chromeUa, WebCompat.chromeLikeUserAgent(WebCompat.chromeLikeUserAgent(chromeUa)))
        // Degraded provider answers must never become something worse.
        assertEquals("", WebCompat.chromeLikeUserAgent(""))
    }

    // ---- boot witness (m4.0.5) ----------------------------------------------

    @Test
    fun `boot witness - probe scripts carry the trap and the truth read`() {
        // Refactor guards: the injection must arm the error trap, and the
        // probe must read readyState + element count + captured errors.
        assertTrue(BootWitness.BOOT_TRAP_JS.contains("__psBoot"))
        assertTrue(BootWitness.BOOT_TRAP_JS.contains("addEventListener('error'"))
        assertTrue(BootWitness.BOOT_TRAP_JS.contains("unhandledrejection"))
        assertTrue(BootWitness.DOM_TRUTH_JS.contains("readyState"))
        assertTrue(BootWitness.DOM_TRUTH_JS.contains("getElementsByTagName"))
        assertTrue(BootWitness.DOM_TRUTH_JS.contains("__psBoot"))
    }

    @Test
    fun `boot witness - element floor separates a real app from a banner-only page`() {
        fun truth(elements: Int) = BootWitness.Truth("complete", elements, 0, emptyList())
        // Banner-only shell (the device's captured state): never mounted.
        assertFalse(BootWitness.mounted(truth(0)))
        assertFalse(BootWitness.mounted(truth(15)))
        assertFalse(BootWitness.mounted(truth(BootWitness.MOUNT_ELEMENT_FLOOR - 1)))
        // Any real app shell is hundreds of nodes.
        assertTrue(BootWitness.mounted(truth(BootWitness.MOUNT_ELEMENT_FLOOR)))
        assertTrue(BootWitness.mounted(truth(5_000)))
        // readyState alone never vouches: a complete document with a tiny
        // DOM is exactly the dead-shell signature.
        assertFalse(BootWitness.mounted(BootWitness.Truth("complete", 12, 400, emptyList())))
    }

    @Test
    fun `boot witness - parseTruth reads the double-encoded probe answer`() {
        // evaluateJavascript hands back the JS string value ENCODED as a
        // JSON string literal — the probe's own JSON rides inside escaped.
        val inner =
            """{"rs":"complete","n":412,"t":1337,"e":["SyntaxError: Unexpected token '?"]}"""
        val raw = "\"" + inner.replace("\"", "\\\"") + "\""
        val truth = BootWitness.parseTruth(raw)
        assertEquals("complete", truth?.readyState)
        assertEquals(412, truth?.elementCount)
        assertEquals(1337, truth?.textLength)
        assertEquals(listOf("SyntaxError: Unexpected token '?"), truth?.bootErrors)
    }

    @Test
    fun `boot witness - garbage answers never become truth`() {
        assertNull(BootWitness.parseTruth(null))
        assertNull(BootWitness.parseTruth(""))
        assertNull(BootWitness.parseTruth("   "))
        assertNull(BootWitness.parseTruth("null"))
        assertNull(BootWitness.parseTruth("not json at all"))
    }

    @Test
    fun `boot witness - diagnose carries the page's own testimony`() {
        val truth = BootWitness.Truth(
            readyState = "complete",
            elementCount = 23,
            textLength = 40,
            bootErrors = listOf("SyntaxError: Unexpected token"),
        )
        val diagnosis = BootWitness.diagnose(truth, listOf("E: boom @app.js:1"))
        assertTrue(diagnosis.contains("readyState=complete"))
        assertTrue(diagnosis.contains("23 DOM elements"))
        assertTrue(diagnosis.contains("error: SyntaxError: Unexpected token"))
        assertTrue(diagnosis.contains("console: E: boom @app.js:1"))
        // The cap exists so a chatty page can never blow out the card.
        assertTrue(diagnosis.length <= BootWitness.MAX_DIAGNOSIS_LENGTH)
    }

    @Test
    fun `boot witness - a silent page is reported honestly`() {
        val diagnosis = BootWitness.diagnose(null, emptyList())
        assertTrue(diagnosis.contains("never answered"))
        // An unreadable DOM says so instead of inventing a count.
        val unreadable = BootWitness.diagnose(
            BootWitness.Truth("loading", -1, 0, emptyList()),
            emptyList(),
        )
        assertTrue(unreadable.contains("DOM unreadable"))
    }

    @Test
    fun `boot witness - a captured boot error defeats the SSR element count`() {
        // THE m4.0.6 device pin: chatgpt.com's server-rendered shell lands
        // with hundreds of inert nodes BEFORE its app hydrates — under the
        // m4.0.5 floor rule that shell vouched for a page whose canvas was
        // black. With a captured boot error, only REAL visible text may
        // call the page alive.
        val ssrShell = BootWitness.Truth(
            readyState = "complete",
            elementCount = 800,
            textLength = 12,
            bootErrors = listOf("SyntaxError: Unexpected token '?'"),
        )
        assertFalse("SSR node count must not vouch for an erroring page", BootWitness.mounted(ssrShell))
        // The same page once its app actually renders content IS alive.
        val alive = ssrShell.copy(textLength = BootWitness.TEXT_MOUNT_FLOOR + 5)
        assertTrue(BootWitness.mounted(alive))
        // A quiet page keeps the pure element-floor rule.
        assertTrue(BootWitness.mounted(BootWitness.Truth("complete", 60, 0, emptyList())))
        assertFalse(BootWitness.mounted(BootWitness.Truth("complete", 59, 0, emptyList())))
    }

    @Test
    fun `boot witness - the probe reads the interactive-element count`() {
        val inner =
            """{"rs":"complete","n":412,"i":23,"t":1337,"e":[]}"""
        val raw = "\"" + inner.replace("\"", "\\\"") + "\""
        val truth = BootWitness.parseTruth(raw)
        assertEquals(23, truth?.interactiveCount)
        // A degraded answer without the field stays parseable (m4.0.5 shape).
        val legacy = BootWitness.parseTruth("\"{\\\"rs\\\":\\\"loading\\\",\\\"n\\\":9,\\\"t\\\":0,\\\"e\\\":[]}\"")
        assertEquals(-1, legacy?.interactiveCount)
    }

    @Test
    fun `boot witness - the probe carries the page's own voice (title + text)`() {
        // m4.0.8: title and first visible words — the page names its own
        // state (login wall, consent, empty shell) in one pasted line.
        val inner =
            """{"rs":"complete","n":896,"i":88,"t":394,"ti":"ChatGPT","s":"Welcome to ChatGPT Log in Sign up","e":[]}"""
        val raw = "\"" + inner.replace("\"", "\\\"") + "\""
        val truth = BootWitness.parseTruth(raw)
        assertEquals("ChatGPT", truth?.title)
        assertEquals("Welcome to ChatGPT Log in Sign up", truth?.textSample)
        // A legacy answer without the fields keeps parsing (m4.0.6 shape).
        val legacy = BootWitness.parseTruth(
            "\"{\\\"rs\\\":\\\"complete\\\",\\\"n\\\":9,\\\"t\\\":0,\\\"e\\\":[]}\"",
        )
        assertEquals("", legacy?.title)
        assertEquals("", legacy?.textSample)
    }

    // ---- render probe m4.0.8: the color truth -------------------------------

    @Test
    fun `render probe - color truth names what is on the glass`() {
        // The device's killer case: a canvas the old report called
        // "painted" while the user saw black. The color truth NAMES it.
        val flashGuard = RenderProbe.WEBVIEW_BACKGROUND
        val dark = RenderProbe.colorTruth(IntArray(64) { flashGuard })!!
        assertEquals("#080F1D", dark.dominantHex)
        assertEquals(100, dark.nearBlackPct)
        assertEquals(1, dark.distinctColors)
        val black = RenderProbe.colorTruth(IntArray(50) { 0xFF000000.toInt() })!!
        assertEquals("#000000", black.dominantHex)
        assertEquals(100, black.nearBlackPct)
        assertEquals(1, black.distinctColors)
        // A mixed sample: dominant bucket wins, near-black share is exact.
        val mixed = IntArray(100) { if (it < 90) 0xFF000000.toInt() else 0xFFFFFFFE.toInt() }
        val truth = RenderProbe.colorTruth(mixed)!!
        assertEquals("#000000", truth.dominantHex)
        assertEquals(90, truth.nearBlackPct)
        assertEquals(2, truth.distinctColors)
        // Empty sample — no evidence, never invented.
        assertNull(RenderProbe.colorTruth(IntArray(0)))
    }

    @Test
    fun `render probe - findActivity unwraps any context wrapper`() {
        assertNull(RenderProbe.findActivity(null))
        // A wrapper chain that never reaches an Activity: null, never a cast
        // crash — and a wrapper-of-wrapper chain is followed to its base.
        val inner = android.content.ContextWrapper(null)
        val outer = android.content.ContextWrapper(inner)
        assertNull(RenderProbe.findActivity(outer))
        assertNull(RenderProbe.findActivity(android.content.ContextWrapper(null)))
    }

    @Test
    fun `render probe - forced-light uiMode arithmetic is exact`() {
        val yes = android.content.res.Configuration.UI_MODE_NIGHT_YES
        val no = android.content.res.Configuration.UI_MODE_NIGHT_NO
        val undefined = android.content.res.Configuration.UI_MODE_NIGHT_UNDEFINED
        assertEquals(no, RenderProbe.forcedLightUiMode(yes))
        assertEquals(no, RenderProbe.forcedLightUiMode(no))
        assertEquals(no, RenderProbe.forcedLightUiMode(undefined))
        // Non-night bits ride along untouched: TELEVISION stays, night
        // bits land exactly on NIGHT_NO.
        val mixed = yes or android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        assertEquals(
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION or no,
            RenderProbe.forcedLightUiMode(mixed),
        )
    }

    // ---- page-health report (m4.0.6) ----------------------------------------

    @Test
    fun `health report - carries every testimony line in stable order`() {
        val facts = CompanionHealth.Facts(
            name = "ChatGPT",
            url = "https://chatgpt.com/",
            webViewVersion = "124.0.6367.82",
            renderer = "GPU",
            pixels = "painted",
            truth = BootWitness.Truth(
                readyState = "complete",
                elementCount = 800,
                interactiveCount = 23,
                textLength = 12,
                bootErrors = listOf("SyntaxError: Unexpected token '?'"),
                title = "ChatGPT",
                textSample = "Welcome back",
            ),
            console = listOf("Uncaught SyntaxError @main.js:1"),
            userAgent = "Mozilla/5.0 (Linux; Android 14) Chrome/124 Mobile",
            colors = RenderProbe.ColorTruth("#0D0D0D", 97, 3),
            scheme = "forced light",
        )
        val report = CompanionHealth.compose(facts)
        listOf(
            "PocketShell Companion health — ChatGPT",
            "url: https://chatgpt.com/",
            "webview: 124.0.6367.82 · renderer: GPU · pixels: painted",
            "scheme: forced light",
            "glass: dominant #0D0D0D · 97% near-black · 3 colors",
            "readyState=complete · 800 elements · 23 interactive · 12 text chars",
            "page says: \"ChatGPT — Welcome back\"",
            "boot error 1: SyntaxError: Unexpected token '?'",
            "console (last 1):",
            "> Uncaught SyntaxError @main.js:1",
            "ua: Mozilla/5.0",
        ).forEach { line -> assertTrue("missing: $line", report.contains(line)) }
        assertTrue(report.length <= CompanionHealth.MAX_REPORT_LENGTH)
    }

    @Test
    fun `health report - honest empty states and the hard cap`() {
        val bare = CompanionHealth.compose(
            CompanionHealth.Facts(
                name = "Zai",
                url = null,
                webViewVersion = "unknown",
                renderer = "SOFTWARE",
                pixels = "unknown",
                truth = null,
                console = emptyList(),
                userAgent = null,
            ),
        )
        assertTrue(bare.contains("no DOM reading yet"))
        // Console testimony survives even without a DOM reading (m4.0.5's
        // console tail keeps working when the probe can't).
        assertTrue(bare.contains("console: empty"))
        // A chatty page can never blow out the sheet.
        val flooded = CompanionHealth.compose(
            CompanionHealth.Facts(
                name = "Flood",
                url = "https://flood.example",
                webViewVersion = "1",
                renderer = "GPU",
                pixels = "painted",
                truth = null,
                console = List(50) { "e".repeat(160) },
                userAgent = null,
            ),
        )
        assertEquals(CompanionHealth.MAX_REPORT_LENGTH, flooded.length)
    }

    @Test
    fun `console tail - bounded ring keeps the newest lines`() {
        val tail = ConsoleTail(cap = 8)
        repeat(10) { tail.append("line $it") }
        val snapshot = tail.snapshot()
        assertEquals(8, snapshot.size)
        assertEquals("line 2", snapshot.first())
        assertEquals("line 9", snapshot.last())
    }

    @Test
    fun `console tail - lines are cleaned, capped, and clearable`() {
        val tail = ConsoleTail()
        tail.append("  ${"x".repeat(200)}  ")
        assertEquals(160, tail.snapshot().single().length)
        tail.append("\n  padded \n line \n")
        assertEquals("padded   line", tail.snapshot().last())
        tail.clear()
        assertTrue(tail.snapshot().isEmpty())
    }

    @Test
    fun `app-not-booted failure names the real fix and shows the page's testimony`() {
        val failure = CompanionFailure(
            CompanionFailureKind.APP_NOT_BOOTED,
            detail = "readyState=complete · 23 DOM elements · error: SyntaxError",
            webViewVersion = "124.0.6367.82",
        )
        assertEquals("Page won't start", failure.title)
        assertTrue(failure.body.contains("readyState=complete"))
        assertTrue(failure.body.contains("Android System WebView 124.0.6367.82"))
        assertTrue(failure.hint.contains("app never started"))
        assertTrue(failure.hint.contains("Android System WebView"))
        assertTrue(failure.hint.contains("browser"))
    }
}
