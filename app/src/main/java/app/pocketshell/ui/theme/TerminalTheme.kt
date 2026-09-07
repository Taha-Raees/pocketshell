package app.pocketshell.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketshell.R

/**
 * Phase 3.1 "Midnight Sapphire" design tokens — now Phase 5 theme-aware.
 *
 * The terminal canvas is a dark professional surface and web pages own their
 * own appearance, so the CONTENT surface ([canvas]) is pinned Midnight-dark
 * in every theme; everything that is PocketShell CHROME (page backgrounds,
 * tab strips, decks, keys, text, buttons) follows the selected theme.
 *
 * Phase 5 (docs/ROADMAP §5): the token colors became snapshot state —
 * [applyTheme] swaps the whole vocabulary between Midnight Sapphire (dark,
 * byte-for-byte the historical values) and Daylight Sapphire (light) with
 * ZERO call-site changes: every screen reads the same `TerminalTheme.x`
 * properties and recomposes when the theme flips. Geometry + fonts are
 * theme-independent and stay plain vals.
 *
 * Token pairing contracts (both themes):
 *   text on [canvas]-tone surfaces → [onCanvas] / [onCanvasDim] (pinned light)
 *   text on [accentDeep] fills     → [onAccentDeep]
 *   glyph on [accent] fills        → [enterGlyph]
 */
object TerminalTheme {

    /**
     * M7.1 P2.2 — which vocabulary is currently applied (false = Midnight
     * until the first [applyTheme]). Synced by [PocketShellTheme] BEFORE any
     * child composes; theme-reactive consumers (the launcher icon loader)
     * read this to pick the matching bundled asset variant.
     */
    var isLight by mutableStateOf(false); private set

    // ---- surface stack (outer → inner) --------------------------------------
    var screenBg by mutableStateOf(Color(0xFF0B1424)); private set
    var chrome by mutableStateOf(Color(0xFF101B30)); private set
    var chromeGradientTop by mutableStateOf(Color(0xFF111C33)); private set
    var chromeGradientBottom by mutableStateOf(Color(0xFF0E1730)); private set
    var tabStrip by mutableStateOf(Color(0xFF0D1730)); private set

    /**
     * The CONTENT surface — terminal canvas, Companion web backing, the
     * "this IS a terminal" hero tone. Deliberately NOT theme-swapped:
     * the TerminalView background and the session color scheme are built
     * on it (TerminalPalette), and websites paint themselves.
     */
    var canvas by mutableStateOf(Color(0xFF080F1D)); private set
    var deck by mutableStateOf(Color(0xFF131F38)); private set

    // ---- keys ----------------------------------------------------------------
    var key by mutableStateOf(Color(0xFF1B2947)); private set
    var keyAlt by mutableStateOf(Color(0xFF16233F)); private set
    var keyPressed by mutableStateOf(Color(0xFF26365B)); private set
    var keyActive by mutableStateOf(Color(0xFF24406E)); private set

    // ---- lines & text ----------------------------------------------------------
    var divider by mutableStateOf(Color(0xFF1D2C4A)); private set
    var textPrimary by mutableStateOf(Color(0xFFDCE6F8)); private set
    var textDim by mutableStateOf(Color(0xFF7C8DB0)); private set

    // ---- the one accent ---------------------------------------------------------
    var accent by mutableStateOf(Color(0xFF7FA3EF)); private set
    var accentBright by mutableStateOf(Color(0xFFA5C0FF)); private set
    var accentDeep by mutableStateOf(Color(0xFF3D5A96)); private set

    /** Enter key: the strongest weight on the deck — Sapphire fill, dark glyph. */
    var enterGlyph by mutableStateOf(Color(0xFF071120)); private set

    /** Text on an [accentDeep] fill (the filled-button weight). */
    var onAccentDeep by mutableStateOf(Color(0xFFA5C0FF)); private set

    /**
     * Text on [canvas]-tone surfaces — pinned light in EVERY theme, because
     * the canvas is pinned dark (active tab labels, failure cards, the
     * Terminal hero tile).
     */
    var onCanvas by mutableStateOf(Color(0xFFDCE6F8)); private set
    var onCanvasDim by mutableStateOf(Color(0xFF7C8DB0)); private set

    /** Close-tab affordance; restrained, recognizably destructive. */
    var danger by mutableStateOf(Color(0xFFE37993)); private set

    /**
     * ONLY on a real running-process indicator (the ANSI green). Theme-aware:
     * deepened in Daylight for contrast on the paper surfaces.
     */
    var runningGreen by mutableStateOf(Color(0xFF5FB572)); private set

    // ---- geometry ----------------------------------------------------------------
    val keyRadius: Dp = 8.dp

    /** m5.0 final correction: tightened for the compact IDE-tab language. */
    val tabTopRadius: Dp = 6.dp
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

    /**
     * Phase 5 — swap the whole chrome vocabulary. [light] = Daylight
     * Sapphire; otherwise Midnight Sapphire (the historical values, dark and
     * AMOLED alike — AMOLED's pure-black surfaces live in the M3 scheme,
     * which already supports it).
     *
     * Called synchronously during PocketShellTheme composition — BEFORE any
     * child reads a token — so a theme switch or a cold start in Light never
     * flashes the wrong palette.
     */
    fun applyTheme(light: Boolean) {
        isLight = light
        if (light) {
            // Daylight Sapphire — the same structure, paper surfaces, deepened
            // sapphire accents for contrast on light grounds.
            screenBg = Color(0xFFEEF2F8)
            chrome = Color(0xFFF7F9FC)
            chromeGradientTop = Color(0xFFFBFCFE)
            chromeGradientBottom = Color(0xFFF1F4F9)
            tabStrip = Color(0xFFE6EBF3)
            deck = Color(0xFFF2F5FA)

            key = Color(0xFFFFFFFF)
            keyAlt = Color(0xFFEDF1F7)
            keyPressed = Color(0xFFDCE4F0)
            keyActive = Color(0xFFD6E2F8)

            divider = Color(0xFFD3DCE9)
            textPrimary = Color(0xFF17233B)
            textDim = Color(0xFF5D6E8C)

            accent = Color(0xFF3D5A96)
            accentBright = Color(0xFF24406E)
            accentDeep = Color(0xFF2C4478)
            enterGlyph = Color(0xFFF2F5FA)
            onAccentDeep = Color(0xFFEAF0FB)

            danger = Color(0xFFB3384E)
            runningGreen = Color(0xFF3E8F52)
        } else {
            // Midnight Sapphire — the historical values, unchanged.
            screenBg = Color(0xFF0B1424)
            chrome = Color(0xFF101B30)
            chromeGradientTop = Color(0xFF111C33)
            chromeGradientBottom = Color(0xFF0E1730)
            tabStrip = Color(0xFF0D1730)
            deck = Color(0xFF131F38)

            key = Color(0xFF1B2947)
            keyAlt = Color(0xFF16233F)
            keyPressed = Color(0xFF26365B)
            keyActive = Color(0xFF24406E)

            divider = Color(0xFF1D2C4A)
            textPrimary = Color(0xFFDCE6F8)
            textDim = Color(0xFF7C8DB0)

            accent = Color(0xFF7FA3EF)
            accentBright = Color(0xFFA5C0FF)
            accentDeep = Color(0xFF3D5A96)
            enterGlyph = Color(0xFF071120)
            onAccentDeep = Color(0xFFA5C0FF)

            danger = Color(0xFFE37993)
            runningGreen = Color(0xFF5FB572)
        }
        // onCanvas / onCanvasDim are PINNED (canvas is pinned dark in every
        // theme) — deliberately not touched here.
    }
}
