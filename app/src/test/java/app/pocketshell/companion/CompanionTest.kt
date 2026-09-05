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

    // ---- height math (contract §9, Phase 5: FREE POSITIONING) -----------------

    @Test
    fun `release stays exactly where the user leaves it - no snap points`() {
        // Phase 5 §1: the old HALF/FULL snap windows are retired. Every
        // raised release position settles at itself, at ANY fraction.
        assertEquals(0.30f, CompanionHeights.settled(0.30f))
        assertEquals(0.42f, CompanionHeights.settled(0.42f))
        assertEquals(CompanionHeights.HALF, CompanionHeights.settled(CompanionHeights.HALF))
        assertEquals(CompanionHeights.HALF + 0.04f, CompanionHeights.settled(CompanionHeights.HALF + 0.04f))
        assertEquals(CompanionHeights.FULL, CompanionHeights.settled(CompanionHeights.FULL))
        assertEquals(CompanionHeights.FULL - 0.05f, CompanionHeights.settled(CompanionHeights.FULL - 0.05f))
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

    // ---- m5.0 final correction: near-full tab-bar drag surface ---------------

    @Test
    fun `tab strip becomes a drag surface only at or above ninety percent`() {
        // Below the threshold (everyday heights — HALF, 80%, just under the
        // line): the tab strip is NOT a drag surface; only the handle drags.
        assertFalse(CompanionHeights.tabBarDragSurface(0f))
        assertFalse(CompanionHeights.tabBarDragSurface(CompanionHeights.HALF))
        assertFalse(CompanionHeights.tabBarDragSurface(0.80f))
        assertFalse(CompanionHeights.tabBarDragSurface(0.899f))
        // At/above the threshold (near-fullscreen, up to FULL): it is.
        assertTrue(CompanionHeights.tabBarDragSurface(0.90f))
        assertTrue(CompanionHeights.tabBarDragSurface(0.93f))
        assertTrue(CompanionHeights.tabBarDragSurface(CompanionHeights.FULL))
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
    fun `watchdog failure kinds are retired with the watchdog family`() {
        // m4.1.0: the m4.0.9 control experiment proved the render path the
        // pixel watchdog and boot witness policed was never the problem —
        // their failure kinds no longer exist to be raised.
        listOf("RENDER_STALLED", "APP_NOT_BOOTED").forEach { retired ->
            try {
                CompanionFailureKind.valueOf(retired)
                throw AssertionError("$retired should be retired")
            } catch (_: IllegalArgumentException) {
                // expected — the kind is gone
            }
        }
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


}
