package app.pocketshell.ui.theme

import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.ThemeMode

/**
 * Control Center theme system (Settings/Control Center task §2/§3/§4).
 *
 * The core separation the brief demands:
 *
 *   THEME = visual identity (PocketShell, Nord, Dracula, …) — a PAIR of
 *           complete palettes, one Light and one Dark. No theme is
 *           "dark-only" and no theme is modeled as an independent mode.
 *   MODE  = System / Light / Dark — which VARIANT of the chosen theme is
 *           shown. AMOLED is no longer a mode (removed from the selector;
 *           a persisted "AMOLED" value degrades honestly to DARK).
 *
 * The identity/mode vocabulary itself ([AppTheme], [ThemeMode] and their
 * parsers) lives in the settings layer beside the persistence — the same
 * home the historical ThemeMode always had — so `ui.theme` stays the
 * palette/application layer and settings stays the single source of truth
 * for user-facing vocabulary.
 *
 * Every value here is a plain 0xAARRGGBB [Long] — pure JVM data, so the
 * readability contract (ThemeCatalogContrastTest: text/surface contrast
 * ratios in EVERY theme × variant) is enforced without an Android device.
 *
 * Palette provenance: established developer palettes are referenced for
 * their publicly documented hex values only (color values are not
 * copyrightable); each carries an attribution comment. Light variants are
 * derived per theme (marked) — never copied from unrelated projects.
 * Where an official light sibling exists (Solarized, Rosé Pine Dawn, One
 * Light, Gruvbox Light) it IS used.
 *
 * The application layer reads this catalog ONLY through
 * [TerminalTheme.applyTheme] (chrome tokens) and [toLightColorScheme] /
 * [toDarkColorScheme] (Theme.kt, the Material 3 layer), both invoked from
 * PocketShellTheme before any child composes — the Phase 5 no-flash
 * contract, unchanged.
 */

/**
 * One complete palette (a theme × a variant). The field set is EXACTLY the
 * Midnight/Daylight chrome vocabulary [TerminalTheme] publishes plus the
 * Material 3 slots that historically leaked through (dialogs, menus) —
 * derived from the chrome tokens unless a theme overrides them.
 */
data class ThemeVariant(
    // chrome surface stack (outer → inner)
    val screenBg: Long,
    val chrome: Long,
    val chromeGradientTop: Long,
    val chromeGradientBottom: Long,
    val tabStrip: Long,
    val deck: Long,
    // the CONTENT surface (terminal canvas / Companion backing). MUST stay
    // byte-identical to the terminal background handed to TerminalPalette.
    val canvas: Long,
    // keys
    val key: Long,
    val keyAlt: Long,
    val keyPressed: Long,
    val keyActive: Long,
    // lines & text
    val divider: Long,
    val textPrimary: Long,
    val textDim: Long,
    // the one accent trio + text that rides it
    val accent: Long,
    val accentBright: Long,
    val accentDeep: Long,
    val enterGlyph: Long,
    val onAccentDeep: Long,
    // text on canvas-tone surfaces
    val onCanvas: Long,
    val onCanvasDim: Long,
    // honest state colors
    val danger: Long,
    val runningGreen: Long,
    // ---- Material 3 slots (historically distinct for PocketShell; derived otherwise)
    val m3Primary: Long? = null,
    val m3OnPrimary: Long? = null,
    val m3PrimaryContainer: Long? = null,
    val m3OnPrimaryContainer: Long? = null,
    val m3SurfaceLowest: Long? = null,
    val m3SurfaceHigh: Long? = null,
    val m3SurfaceHighest: Long? = null,
)

// ---------------------------------------------------------------------------
// POCKETSHELL — the historical Midnight / Daylight Sapphire, byte-for-byte.
// Default identity; the baseline every other theme is measured against.
// ---------------------------------------------------------------------------
private val PocketShellDark = ThemeVariant(
    screenBg = 0xFF0B1424, chrome = 0xFF101B30,
    chromeGradientTop = 0xFF111C33, chromeGradientBottom = 0xFF0E1730,
    tabStrip = 0xFF0D1730, deck = 0xFF131F38, canvas = 0xFF080F1D,
    key = 0xFF1B2947, keyAlt = 0xFF16233F, keyPressed = 0xFF26365B, keyActive = 0xFF24406E,
    divider = 0xFF1D2C4A, textPrimary = 0xFFDCE6F8, textDim = 0xFF7C8DB0,
    accent = 0xFF7FA3EF, accentBright = 0xFFA5C0FF, accentDeep = 0xFF3D5A96,
    enterGlyph = 0xFF071120, onAccentDeep = 0xFFA5C0FF,
    onCanvas = 0xFFDCE6F8, onCanvasDim = 0xFF7C8DB0,
    danger = 0xFFE37993, runningGreen = 0xFF5FB572,
    // the restrained phosphor-green M3 primary — historical values kept
    m3Primary = 0xFF8FD694, m3OnPrimary = 0xFF003911,
    m3PrimaryContainer = 0xFF1D5326, m3OnPrimaryContainer = 0xFFB9F0BA,
)
private val PocketShellLight = ThemeVariant(
    screenBg = 0xFFEEF2F8, chrome = 0xFFF7F9FC,
    chromeGradientTop = 0xFFFBFCFE, chromeGradientBottom = 0xFFF1F4F9,
    tabStrip = 0xFFE6EBF3, deck = 0xFFF2F5FA, canvas = 0xFFF7F9FC,
    key = 0xFFFFFFFF, keyAlt = 0xFFEDF1F7, keyPressed = 0xFFDCE4F0, keyActive = 0xFFD6E2F8,
    divider = 0xFFD3DCE9, textPrimary = 0xFF17233B, textDim = 0xFF5D6E8C,
    accent = 0xFF3D5A96, accentBright = 0xFF24406E, accentDeep = 0xFF2C4478,
    enterGlyph = 0xFFF2F5FA, onAccentDeep = 0xFFEAF0FB,
    onCanvas = 0xFF17233B, onCanvasDim = 0xFF5D6E8C,
    danger = 0xFFB3384E, runningGreen = 0xFF3E8F52,
    m3Primary = 0xFF2E6B34, m3OnPrimary = 0xFFFFFFFF,
    m3PrimaryContainer = 0xFFB9F0BA, m3OnPrimaryContainer = 0xFF002105,
)

// ---------------------------------------------------------------------------
// NORD — nordtheme.com (MIT). Dark uses the documented Polar Night /
// Snow Storm / Frost / Aurora values. Light ("Snow Day") is DERIVED from the
// same palette: Snow Storm surfaces, Frost accents deepened for paper.
// ---------------------------------------------------------------------------
private val NordDark = ThemeVariant(
    screenBg = 0xFF242933, chrome = 0xFF2E3440,
    chromeGradientTop = 0xFF333A47, chromeGradientBottom = 0xFF2B323E,
    tabStrip = 0xFF29303C, deck = 0xFF353C4A, canvas = 0xFF272C36,
    key = 0xFF3B4252, keyAlt = 0xFF353C4A, keyPressed = 0xFF434C5E, keyActive = 0xFF4C566A,
    divider = 0xFF3F4856, textPrimary = 0xFFECEFF4, textDim = 0xFF9BA8BC,
    accent = 0xFF88C0D0, accentBright = 0xFF8FBCBB, accentDeep = 0xFF4F709B,
    enterGlyph = 0xFF242933, onAccentDeep = 0xFFECEFF4,
    onCanvas = 0xFFECEFF4, onCanvasDim = 0xFF9BA8BC,
    danger = 0xFFBF616A, runningGreen = 0xFFA3BE8C,
)
private val NordLight = ThemeVariant(
    screenBg = 0xFFE7ECF2, chrome = 0xFFF2F5F9,
    chromeGradientTop = 0xFFF8FAFC, chromeGradientBottom = 0xFFE9EEF4,
    tabStrip = 0xFFE2E8F0, deck = 0xFFEDF1F6, canvas = 0xFFF6F8FA,
    key = 0xFFFFFFFF, keyAlt = 0xFFF0F3F7, keyPressed = 0xFFDDE4EC, keyActive = 0xFFD7E4EE,
    divider = 0xFFD3DCE5, textPrimary = 0xFF2E3440, textDim = 0xFF5C6A7E,
    accent = 0xFF37708F, accentBright = 0xFF2F5F80, accentDeep = 0xFF3D6591,
    enterGlyph = 0xFFF2F5F9, onAccentDeep = 0xFFECEFF4,
    onCanvas = 0xFF2E3440, onCanvasDim = 0xFF5C6A7E,
    danger = 0xFFA6444C, runningGreen = 0xFF5F7F3F,
)

// ---------------------------------------------------------------------------
// DRACULA — draculatheme.com (MIT license, documented palette). Light
// ("Lavender Paper") is DERIVED: the same hues deepened onto pale
// lavender-gray surfaces — never the paid Dracula Pro assets.
// ---------------------------------------------------------------------------
private val DraculaDark = ThemeVariant(
    screenBg = 0xFF22212C, chrome = 0xFF282A36,
    chromeGradientTop = 0xFF2C2E3B, chromeGradientBottom = 0xFF262834,
    tabStrip = 0xFF252631, deck = 0xFF313342, canvas = 0xFF24252F,
    key = 0xFF3A3D4D, keyAlt = 0xFF343746,
    keyPressed = 0xFF44475A, keyActive = 0xFF4B4E63,
    divider = 0xFF3E4152, textPrimary = 0xFFF8F8F2, textDim = 0xFF9299B8,
    accent = 0xFFBD93F9, accentBright = 0xFF8BE9FD, accentDeep = 0xFF6272A4,
    enterGlyph = 0xFF22212C, onAccentDeep = 0xFFF8F8F2,
    onCanvas = 0xFFF8F8F2, onCanvasDim = 0xFF9299B8,
    danger = 0xFFFF5555, runningGreen = 0xFF50FA7B,
)
private val DraculaLight = ThemeVariant(
    screenBg = 0xFFE9E8F0, chrome = 0xFFF5F4FA,
    chromeGradientTop = 0xFFFBFAFE, chromeGradientBottom = 0xFFE9E7F1,
    tabStrip = 0xFFE4E2EE, deck = 0xFFF2F0F8, canvas = 0xFFF9F8FC,
    key = 0xFFFFFFFF, keyAlt = 0xFFF3F1F9, keyPressed = 0xFFE2DFEE, keyActive = 0xFFE2DEF8,
    divider = 0xFFD8D5E4, textPrimary = 0xFF2B2B38, textDim = 0xFF62658A,
    accent = 0xFF6F4DC4, accentBright = 0xFF5A3FA6, accentDeep = 0xFF4B5A87,
    enterGlyph = 0xFFF5F4FA, onAccentDeep = 0xFFF8F8F2,
    onCanvas = 0xFF2B2B38, onCanvasDim = 0xFF62658A,
    danger = 0xFFC4114B, runningGreen = 0xFF1E8A46,
)

// ---------------------------------------------------------------------------
// GRUVBOX — github.com/morhetz/gruvbox (MIT). Both official variants.
// ---------------------------------------------------------------------------
private val GruvboxDark = ThemeVariant(
    screenBg = 0xFF1D2021, chrome = 0xFF282828,
    chromeGradientTop = 0xFF2C2C28, chromeGradientBottom = 0xFF262624,
    tabStrip = 0xFF242827, deck = 0xFF32302F, canvas = 0xFF1D2021,
    key = 0xFF3C3836, keyAlt = 0xFF32302F, keyPressed = 0xFF504945, keyActive = 0xFF665C54,
    divider = 0xFF3F3B37, textPrimary = 0xFFFBF1C7, textDim = 0xFFA89984,
    accent = 0xFF83A598, accentBright = 0xFF8EC07C, accentDeep = 0xFF458588,
    enterGlyph = 0xFF1D2021, onAccentDeep = 0xFFFBF1C7,
    onCanvas = 0xFFFBF1C7, onCanvasDim = 0xFFA89984,
    danger = 0xFFFB4934, runningGreen = 0xFFB8BB26,
)
private val GruvboxLight = ThemeVariant(
    screenBg = 0xFFEBDBB2, chrome = 0xFFFBF1C7,
    chromeGradientTop = 0xFFFEF7E2, chromeGradientBottom = 0xFFE9D8AE,
    tabStrip = 0xFFE6D6A8, deck = 0xFFF4E8BE, canvas = 0xFFFBF1C7,
    key = 0xFFFFFFFF, keyAlt = 0xFFF7EBCE, keyPressed = 0xFFE7D8B4, keyActive = 0xFFDDDFC6,
    divider = 0xFFD5C69A, textPrimary = 0xFF3C3836, textDim = 0xFF7C6F64,
    accent = 0xFF076678, accentBright = 0xFF427B58, accentDeep = 0xFF458588,
    enterGlyph = 0xFFFBF1C7, onAccentDeep = 0xFFFBF1C7,
    onCanvas = 0xFF3C3836, onCanvasDim = 0xFF7C6F64,
    danger = 0xFF9D0006, runningGreen = 0xFF79740E,
)

// ---------------------------------------------------------------------------
// SOLARIZED — ethanschoonover.com/solarized (MIT). Both OFFICIAL variants.
// ---------------------------------------------------------------------------
private val SolarizedDark = ThemeVariant(
    screenBg = 0xFF00212B, chrome = 0xFF002B36,
    chromeGradientTop = 0xFF01323D, chromeGradientBottom = 0xFF002731,
    tabStrip = 0xFF012833, deck = 0xFF073642, canvas = 0xFF00212B,
    key = 0xFF073642, keyAlt = 0xFF052D38, keyPressed = 0xFF0B4453, keyActive = 0xFF10505F,
    divider = 0xFF0A3D4A, textPrimary = 0xFFEEE8D5, textDim = 0xFF839496,
    accent = 0xFF268BD2, accentBright = 0xFF2AA198, accentDeep = 0xFF1A6BA8,
    enterGlyph = 0xFF00212B, onAccentDeep = 0xFFEEE8D5,
    onCanvas = 0xFFEEE8D5, onCanvasDim = 0xFF839496,
    danger = 0xFFDC322F, runningGreen = 0xFF859900,
)
private val SolarizedLight = ThemeVariant(
    screenBg = 0xFFEEE8D5, chrome = 0xFFFDF6E3,
    chromeGradientTop = 0xFFFFFBE8, chromeGradientBottom = 0xFFEAE2CC,
    tabStrip = 0xFFE7DFC6, deck = 0xFFF5EED8, canvas = 0xFFFDF6E3,
    key = 0xFFFFFFFF, keyAlt = 0xFFF8F0DC, keyPressed = 0xFFE9E1C8, keyActive = 0xFFDDE8E3,
    divider = 0xFFDDD6C0, textPrimary = 0xFF073642, textDim = 0xFF657B83,
    accent = 0xFF1A6BA8, accentBright = 0xFF0F5687, accentDeep = 0xFF15568C,
    enterGlyph = 0xFFFDF6E3, onAccentDeep = 0xFFFDF6E3,
    onCanvas = 0xFF073642, onCanvasDim = 0xFF657B83,
    danger = 0xFFDC322F, runningGreen = 0xFF859900,
)

// ---------------------------------------------------------------------------
// ONE DARK / ONE LIGHT — the Atom "one-" syntax families (MIT). Both
// official palettes.
// ---------------------------------------------------------------------------
private val OneDarkDark = ThemeVariant(
    screenBg = 0xFF21252B, chrome = 0xFF282C34,
    chromeGradientTop = 0xFF2C313A, chromeGradientBottom = 0xFF262A32,
    tabStrip = 0xFF242932, deck = 0xFF2F343E, canvas = 0xFF21252B,
    key = 0xFF3A3F4B, keyAlt = 0xFF333844, keyPressed = 0xFF454B57, keyActive = 0xFF528BFF33,
    divider = 0xFF3B4048, textPrimary = 0xFFDCDFE4, textDim = 0xFF9DA5B4,
    accent = 0xFF61AFEF, accentBright = 0xFF56B6C2, accentDeep = 0xFF3E699C,
    enterGlyph = 0xFF21252B, onAccentDeep = 0xFFDCDFE4,
    onCanvas = 0xFFDCDFE4, onCanvasDim = 0xFF9DA5B4,
    danger = 0xFFE06C75, runningGreen = 0xFF98C379,
)
private val OneDarkLight = ThemeVariant(
    screenBg = 0xFFEAEAEC, chrome = 0xFFFAFAFA,
    chromeGradientTop = 0xFFFFFEFF, chromeGradientBottom = 0xFFEDEDEF,
    tabStrip = 0xFFE6E6E8, deck = 0xFFF2F2F3, canvas = 0xFFFAFAFA,
    key = 0xFFFFFFFF, keyAlt = 0xFFF4F4F5, keyPressed = 0xFFE3E4E6, keyActive = 0xFFD8E4F6,
    divider = 0xFFD8D8DB, textPrimary = 0xFF383A42, textDim = 0xFF6B7280,
    accent = 0xFF2A5FD6, accentBright = 0xFF0184BC, accentDeep = 0xFF3B5BB8,
    enterGlyph = 0xFFFAFAFA, onAccentDeep = 0xFFFAFAFA,
    onCanvas = 0xFF383A42, onCanvasDim = 0xFF6B7280,
    danger = 0xFFE45649, runningGreen = 0xFF50A14F,
)

// ---------------------------------------------------------------------------
// MONOKAI — the classic Wimer/"Monokai" palette (widely reimplemented;
// color values treated as public). Light ("Creme") is DERIVED: warm paper,
// the same hues deepened.
// ---------------------------------------------------------------------------
private val MonokaiDark = ThemeVariant(
    screenBg = 0xFF211F1C, chrome = 0xFF272822,
    chromeGradientTop = 0xFF2B2C26, chromeGradientBottom = 0xFF252620,
    tabStrip = 0xFF24251F, deck = 0xFF30312B, canvas = 0xFF211F1C,
    key = 0xFF3E3D36, keyAlt = 0xFF34352F, keyPressed = 0xFF4A4B42, keyActive = 0xFF75715E,
    divider = 0xFF3B3C34, textPrimary = 0xFFF8F8F2, textDim = 0xFFA2A291,
    accent = 0xFFA6E22E, accentBright = 0xFFE6DB74, accentDeep = 0xFF75715E,
    enterGlyph = 0xFF211F1C, onAccentDeep = 0xFFF8F8F2,
    onCanvas = 0xFFF8F8F2, onCanvasDim = 0xFFA2A291,
    danger = 0xFFF92672, runningGreen = 0xFFA6E22E,
)
private val MonokaiLight = ThemeVariant(
    screenBg = 0xFFEDEAE3, chrome = 0xFFF9F8F4,
    chromeGradientTop = 0xFFFFFEFB, chromeGradientBottom = 0xFFEFEDE5,
    tabStrip = 0xFFEAE7DE, deck = 0xFFF4F2EA, canvas = 0xFFF9F8F4,
    key = 0xFFFFFFFF, keyAlt = 0xFFF5F3EC, keyPressed = 0xFFE8E5DB, keyActive = 0xFFE4E9C8,
    divider = 0xFFDBD8CC, textPrimary = 0xFF2D2C25, textDim = 0xFF6E6D5E,
    accent = 0xFF5C7A00, accentBright = 0xFF8A6D00, accentDeep = 0xFF6D8034,
    enterGlyph = 0xFFF9F8F4, onAccentDeep = 0xFFF9F8F4,
    onCanvas = 0xFF2D2C25, onCanvasDim = 0xFF6E6D5E,
    danger = 0xFFC4114B, runningGreen = 0xFF5C7A00,
)

// ---------------------------------------------------------------------------
// ROSÉ PINE — rosepinetheme.com (MIT). Both OFFICIAL variants (main + Dawn).
// ---------------------------------------------------------------------------
private val RosePineDark = ThemeVariant(
    screenBg = 0xFF191724, chrome = 0xFF1F1D2E,
    chromeGradientTop = 0xFF221F35, chromeGradientBottom = 0xFF1D1B2A,
    tabStrip = 0xFF1C1A2A, deck = 0xFF26233A, canvas = 0xFF191724,
    key = 0xFF26233A, keyAlt = 0xFF221F35, keyPressed = 0xFF312E48, keyActive = 0xFF403D64,
    divider = 0xFF2E2B44, textPrimary = 0xFFE0DEF4, textDim = 0xFF908CAA,
    accent = 0xFFC4A7E7, accentBright = 0xFF9CCFD8, accentDeep = 0xFF6E6A86,
    enterGlyph = 0xFF191724, onAccentDeep = 0xFFE0DEF4,
    onCanvas = 0xFFE0DEF4, onCanvasDim = 0xFF908CAA,
    danger = 0xFFEB6F92, runningGreen = 0xFF95B1AC,
)
private val RosePineLight = ThemeVariant(
    screenBg = 0xFFEFEDEA, chrome = 0xFFFAF4ED,
    chromeGradientTop = 0xFFFFFAF3, chromeGradientBottom = 0xFFEDE7DE,
    tabStrip = 0xFFEBE4DA, deck = 0xFFF6F0E6, canvas = 0xFFFAF4ED,
    key = 0xFFFFFFFF, keyAlt = 0xFFF7F0E5, keyPressed = 0xFFEDE4D6, keyActive = 0xFFDFE7EA,
    divider = 0xFFDDD5C9, textPrimary = 0xFF4C476E, textDim = 0xFF797593,
    accent = 0xFF286983, accentBright = 0xFF6B5E93, accentDeep = 0xFF1F5470,
    enterGlyph = 0xFFFAF4ED, onAccentDeep = 0xFFFAF4ED,
    onCanvas = 0xFF4C476E, onCanvasDim = 0xFF797593,
    danger = 0xFFB4637A, runningGreen = 0xFF286983,
)

// ---------------------------------------------------------------------------
// CYBER — original "restrained neon" identity designed for PocketShell.
// Cool graphite surfaces, ONE cyan accent, magenta/danger kept honest.
// Light ("Ice") is DERIVED: pale ice surfaces, deep teal accent.
// ---------------------------------------------------------------------------
private val CyberDark = ThemeVariant(
    screenBg = 0xFF0B0F14, chrome = 0xFF10161D,
    chromeGradientTop = 0xFF121922, chromeGradientBottom = 0xFF0E141B,
    tabStrip = 0xFF0F151C, deck = 0xFF151D26, canvas = 0xFF0A0E13,
    key = 0xFF1B2530, keyAlt = 0xFF16202A, keyPressed = 0xFF24313E, keyActive = 0xFF1E4A55,
    divider = 0xFF1E2A35, textPrimary = 0xFFD9E7EE, textDim = 0xFF7C93A3,
    accent = 0xFF2AD4C8, accentBright = 0xFF7FE7DF, accentDeep = 0xFF146B6B,
    enterGlyph = 0xFF071013, onAccentDeep = 0xFFD9FBF7,
    onCanvas = 0xFFD9E7EE, onCanvasDim = 0xFF7C93A3,
    danger = 0xFFFF4D6D, runningGreen = 0xFF3DDC97,
)
private val CyberLight = ThemeVariant(
    screenBg = 0xFFE4EBEE, chrome = 0xFFF2F7F9,
    chromeGradientTop = 0xFFFAFDFE, chromeGradientBottom = 0xFFE9EFF2,
    tabStrip = 0xFFDFE7EB, deck = 0xFFEDF3F5, canvas = 0xFFF7FBFC,
    key = 0xFFFFFFFF, keyAlt = 0xFFF0F5F7, keyPressed = 0xFFDFE8EC, keyActive = 0xFFD3EAE8,
    divider = 0xFFD2DDE2, textPrimary = 0xFF152229, textDim = 0xFF54707F,
    accent = 0xFF0B7C74, accentBright = 0xFF095F5A, accentDeep = 0xFF0E6B64,
    enterGlyph = 0xFFF2F7F9, onAccentDeep = 0xFFE8FAF8,
    onCanvas = 0xFF152229, onCanvasDim = 0xFF54707F,
    danger = 0xFFC42B4B, runningGreen = 0xFF12805C,
)

// ---------------------------------------------------------------------------
// AURORA — original identity (task §4): deep polar night with the aurora's
// green-teal/indigo sweep as the accent family. The ONLY theme with
// [AppTheme.aurora] = true; its stops drive the shared animated layer.
// Light ("Polar Dawn") is DERIVED: cold paper with deepened boreal accents.
// ---------------------------------------------------------------------------
private val AuroraDark = ThemeVariant(
    screenBg = 0xFF0A1017, chrome = 0xFF0F1721,
    chromeGradientTop = 0xFF111B27, chromeGradientBottom = 0xFF0D151F,
    tabStrip = 0xFF0E1620, deck = 0xFF141E2B, canvas = 0xFF090F16,
    key = 0xFF1B2A38, keyAlt = 0xFF16232F, keyPressed = 0xFF24384A, keyActive = 0xFF1E4D4F,
    divider = 0xFF1C2A38, textPrimary = 0xFFDCE9F2, textDim = 0xFF7E96A8,
    accent = 0xFF4FD1B0, accentBright = 0xFF8FE7D4, accentDeep = 0xFF1F6E62,
    enterGlyph = 0xFF081210, onAccentDeep = 0xFFD6F7EE,
    onCanvas = 0xFFDCE9F2, onCanvasDim = 0xFF7E96A8,
    danger = 0xFFE96D87, runningGreen = 0xFF57C99B,
)
private val AuroraLight = ThemeVariant(
    screenBg = 0xFFE5EDEA, chrome = 0xFFF3F8F6,
    chromeGradientTop = 0xFFFAFDFC, chromeGradientBottom = 0xFFE9F0ED,
    tabStrip = 0xFFDFE9E5, deck = 0xFFEEF4F1, canvas = 0xFFF6FAF8,
    key = 0xFFFFFFFF, keyAlt = 0xFFF0F6F3, keyPressed = 0xFFDFE9E4, keyActive = 0xFFD5EBE4,
    divider = 0xFFD3DFDA, textPrimary = 0xFF152A2E, textDim = 0xFF567379,
    accent = 0xFF147A64, accentBright = 0xFF0E5F4F, accentDeep = 0xFF116B58,
    enterGlyph = 0xFFF3F8F6, onAccentDeep = 0xFFE2F6F0,
    onCanvas = 0xFF152A2E, onCanvasDim = 0xFF567379,
    danger = 0xFFB3384E, runningGreen = 0xFF237B57,
)

/** The catalog: every identity MUST carry both variants (pinned by test). */
object ThemeCatalog {
    fun definition(theme: AppTheme): Pair<ThemeVariant, ThemeVariant> = when (theme) {
        AppTheme.POCKETSHELL -> PocketShellDark to PocketShellLight
        AppTheme.NORD -> NordDark to NordLight
        AppTheme.DRACULA -> DraculaDark to DraculaLight
        AppTheme.GRUVBOX -> GruvboxDark to GruvboxLight
        AppTheme.SOLARIZED -> SolarizedDark to SolarizedLight
        AppTheme.ONE_DARK -> OneDarkDark to OneDarkLight
        AppTheme.MONOKAI -> MonokaiDark to MonokaiLight
        AppTheme.ROSE_PINE -> RosePineDark to RosePineLight
        AppTheme.CYBER -> CyberDark to CyberLight
        AppTheme.AURORA -> AuroraDark to AuroraLight
    }

    fun variant(theme: AppTheme, light: Boolean): ThemeVariant =
        if (light) definition(theme).second else definition(theme).first

    /**
     * The aurora's animated color stops (backdrop blobs + accent glows),
     * shared by dark and light so the motion language never forks.
     */
    val auroraStops: List<Long> = listOf(
        0xFF4FD1B0, // boreal green (the accent family)
        0xFF6C8CFF, // polar indigo
        0xFF9D6BFF, // dusk violet
        0xFF35E0C8, // ice teal
    )
}
