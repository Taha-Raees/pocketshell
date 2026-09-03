package app.pocketshell.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketshell.R

/**
 * Phase 3.1 "Midnight Sapphire" — design tokens for the TERMINAL SCREEN ONLY
 * (docs/PHASE-3.1-DESIGN.md). The rest of the app keeps the M2.6 Material
 * theme; this page deliberately renders its own fixed blue-dark identity in
 * every app theme (a terminal canvas is a dark professional surface).
 *
 * One accent ([accent]) for the whole page: cursor, active tab hairline,
 * modifier states, Enter key. No pure black anywhere — the darkest surface
 * is deep blue-black [canvas].
 */
object TerminalTheme {

    // ---- surface stack (outer → inner) --------------------------------------
    val screenBg = Color(0xFF0B1424)
    val chrome = Color(0xFF101B30)
    val chromeGradientTop = Color(0xFF111C33)
    val chromeGradientBottom = Color(0xFF0E1730)
    val tabStrip = Color(0xFF0D1730)
    val canvas = Color(0xFF080F1D)
    val deck = Color(0xFF131F38)

    // ---- keys ----------------------------------------------------------------
    val key = Color(0xFF1B2947)
    val keyAlt = Color(0xFF16233F)
    val keyPressed = Color(0xFF26365B)
    val keyActive = Color(0xFF24406E)

    // ---- lines & text ----------------------------------------------------------
    val divider = Color(0xFF1D2C4A)
    val textPrimary = Color(0xFFDCE6F8)
    val textDim = Color(0xFF7C8DB0)

    // ---- the one accent ---------------------------------------------------------
    val accent = Color(0xFF7FA3EF)
    val accentBright = Color(0xFFA5C0FF)
    val accentDeep = Color(0xFF3D5A96)

    /** Enter key: the strongest weight on the deck — Sapphire fill, dark glyph. */
    val enterGlyph = Color(0xFF071120)

    /** Close-tab affordance; restrained, recognizably destructive. */
    val danger = Color(0xFFE37993)

    // ---- geometry ----------------------------------------------------------------
    val keyRadius: Dp = 8.dp
    val tabTopRadius: Dp = 10.dp
    val canvasBottomRadius: Dp = 16.dp

    /**
     * JetBrains Mono NL (no-ligature build, OFL 1.1 — docs/THIRD_PARTY.md).
     * The NL variant keeps terminal output character-exact: a terminal must
     * show the shell's actual bytes, never merged glyph ligatures.
     */
    val mono = FontFamily(
        Font(R.font.jetbrains_mono_nl_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_nl_bold, FontWeight.Bold),
        Font(R.font.jetbrains_mono_nl_italic, FontWeight.Normal, FontStyle.Italic),
    )
}
