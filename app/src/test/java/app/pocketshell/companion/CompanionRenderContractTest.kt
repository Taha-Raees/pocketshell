package app.pocketshell.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m4.0.11 — pins for the FROZEN RENDER CONTRACT ("Replace Renderer Only").
 * m4.0.12 — finalization pins: the harness retirement (the winner is now
 * pinned by VALUE — the sweep's evidence lives in docs/RENDER-RESET-M4.0.9.md
 * and the git history, not in production code) and the refresh layer.
 *
 * The user's directive froze two things: (1) the winning harness mode —
 * BASELINE, zero deltas from Android defaults, selected by the device
 * sweep — and (2) the copy rule — the production tab content renderer is
 * the exact baseline implementation with the diagnostics stripped, never
 * "simplified", never rewritten, never swapped for another engine.
 *
 * [CompanionWebHost] implements the contract by hand (the testing contract
 * forbids fake WebView tests); the on-device proof is docs/TESTING.md
 * (Gates A–H plus the m4.0.12 finalization gates).
 */
class CompanionRenderContractTest {

    // ---- the winner is the zero-delta control (pinned by VALUE) ------------

    @Test
    fun `the frozen winner is the baseline variant`() {
        assertEquals("baseline", CompanionRenderContract.WINNER_KEY)
    }

    @Test
    fun `the contract's frozen flags are all permanently false`() {
        // Every lever the eight iterations added is dead — by value:
        assertFalse(CompanionRenderContract.CHROME_UA_OVERRIDE)
        assertFalse(CompanionRenderContract.FORCED_LIGHT_CONTEXT)
        assertFalse(CompanionRenderContract.MIDNIGHT_BACKGROUND_OVERRIDE)
        assertFalse(CompanionRenderContract.LOAD_BEFORE_ATTACH)
        assertFalse(CompanionRenderContract.WIDE_VIEWPORT_OVERRIDES)
        assertFalse(CompanionRenderContract.SOFTWARE_LAYER_COMPAT)
    }

    // ---- the settings surface is exactly the baseline + §16 hardening -------

    @Test
    fun `the settings surface is exactly four touches and nothing else`() {
        assertEquals(
            listOf(
                "javaScriptEnabled = true",
                "domStorageEnabled = true",
                "allowFileAccess = false",
                "allowContentAccess = false",
            ),
            CompanionRenderContract.SETTINGS_TOUCHES,
        )
        // No "simplifications", no additions — the directive's hard rule:
        // the baseline's WebView setup is copied, not adjusted.
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("userAgent", ignoreCase = true) })
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("background", ignoreCase = true) })
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("viewport", ignoreCase = true) })
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("layer", ignoreCase = true) })
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("forceDark", ignoreCase = true) })
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("darkening", ignoreCase = true) })
        // m4.0.12: the hard reload's transient cacheMode is NOT part of the
        // creation surface — the four touches above are untouched.
        assertTrue(CompanionRenderContract.SETTINGS_TOUCHES.none { it.contains("cacheMode", ignoreCase = true) })
    }

    // ---- the lifecycle is the proven sequence -------------------------------

    @Test
    fun `the load sequence is create attach layout load`() {
        assertEquals(
            "create -> attach -> first layout -> loadUrl",
            CompanionRenderContract.LOAD_SEQUENCE,
        )
        assertFalse(CompanionRenderContract.LOAD_BEFORE_ATTACH)
    }

    // ---- the structure is the baseline's structure ---------------------------

    @Test
    fun `creation context is the real activity on a plain FrameLayout`() {
        assertEquals("activity", CompanionRenderContract.CREATION_CONTEXT)
        assertEquals("android.widget.FrameLayout", CompanionRenderContract.HOST_CONTAINER)
        assertEquals(
            "native view surgery (removeAllViews + addView), one stable canvas",
            CompanionRenderContract.TAB_SWITCH_MECHANISM,
        )
    }

    // ---- the render path is diagnostic-free (m4.0.12: harness RETIRED) -------

    @Test
    fun `the render path carries zero diagnostics`() {
        assertTrue(CompanionRenderContract.DIAGNOSTICS_IN_RENDER_PATH.isEmpty())
    }

    // ---- m4.0.12: the refresh layer (never the renderer) ---------------------

    @Test
    fun `refresh acts on the active tab only`() {
        assertEquals(
            "active tab only: reload() on one WebView, same URL, same tab",
            CompanionRenderContract.REFRESH_SCOPE,
        )
        // The scope must never widen to a reset or an all-tabs action.
        assertTrue(CompanionRenderContract.REFRESH_SCOPE.contains("active tab only"))
        assertTrue(!CompanionRenderContract.REFRESH_SCOPE.contains("all", ignoreCase = true))
        assertTrue(!CompanionRenderContract.REFRESH_SCOPE.contains("reset", ignoreCase = true))
    }

    @Test
    fun `hard reload is a fresh load, never a data reset`() {
        assertEquals(
            listOf(
                "cacheMode = LOAD_NO_CACHE for this one load only",
                "restored to LOAD_DEFAULT on page finish",
                "cookies and login sessions preserved",
                "other tabs untouched",
            ),
            CompanionRenderContract.HARD_RELOAD,
        )
        // The §4 hard line: sessions survive; nothing wipes user data.
        assertTrue(CompanionRenderContract.HARD_RELOAD.any { it.contains("sessions preserved") })
        assertTrue(CompanionRenderContract.HARD_RELOAD.none { it.contains("clearCookies", ignoreCase = true) })
        assertTrue(CompanionRenderContract.HARD_RELOAD.none { it.contains("clear data", ignoreCase = true) })
    }
}
