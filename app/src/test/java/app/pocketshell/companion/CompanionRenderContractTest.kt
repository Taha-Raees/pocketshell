package app.pocketshell.companion

import app.pocketshell.diagnostic.BaselineMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m4.0.11 — pins for the FROZEN RENDER CONTRACT ("Replace Renderer Only").
 *
 * The user's directive froze two things: (1) the winning harness mode —
 * BASELINE, zero deltas from Android defaults, selected by the device
 * sweep — and (2) the copy rule — the production tab content renderer is
 * the exact baseline implementation with the diagnostics stripped, never
 * "simplified", never rewritten, never swapped for another engine.
 *
 * These pins hold the contract to both. [CompanionWebHost] implements the
 * contract by hand (the testing contract forbids fake WebView tests);
 * the on-device proof is docs/TESTING.md §28 (Gates A–H).
 */
class CompanionRenderContractTest {

    // ---- the winner is the zero-delta control -------------------------------

    @Test
    fun `the frozen winner is the baseline variant`() {
        assertEquals(BaselineMatrix.BASELINE, BaselineMatrix.WINNER)
        assertEquals("baseline", CompanionRenderContract.WINNER_KEY)
        assertTrue(BaselineMatrix.isSingleVariable(BaselineMatrix.WINNER))
    }

    @Test
    fun `the contract's frozen flags are exactly the baseline's flags`() {
        val w = BaselineMatrix.WINNER
        assertEquals(w.chromeUa, CompanionRenderContract.CHROME_UA_OVERRIDE)
        assertEquals(w.forcedLight, CompanionRenderContract.FORCED_LIGHT_CONTEXT)
        assertEquals(w.midnightBg, CompanionRenderContract.MIDNIGHT_BACKGROUND_OVERRIDE)
        assertEquals(w.loadBeforeAttach, CompanionRenderContract.LOAD_BEFORE_ATTACH)
        assertEquals(w.wideViewport, CompanionRenderContract.WIDE_VIEWPORT_OVERRIDES)
        // And the levers the iterations added stay dead:
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

    // ---- the render path is diagnostic-free ----------------------------------

    @Test
    fun `the render path carries zero diagnostics`() {
        assertTrue(CompanionRenderContract.DIAGNOSTICS_IN_RENDER_PATH.isEmpty())
    }
}
