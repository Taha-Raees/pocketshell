package app.pocketshell.settings

/**
 * Control Center appearance vocabulary (Settings/Control Center task, §5/§10).
 *
 * These are the USER-FACING density knobs, deliberately modeled as small
 * closed enums — discrete presets, not sliders (brief §6: "avoid excessive
 * sliders where discrete presets are clearer") — plus the pure math that
 * turns them into layout values. All of it is PURE and JVM-test-pinned:
 * no Android types, no Compose types.
 *
 * The same vocabulary is designed to be consumable by a future Home widget
 * grid without redefining density per surface (brief §5: the architecture
 * must not block widgets) — widgets would read the SAME enums, not a new
 * parallel system.
 */

/** Icon tile size presets for launcher entries (Home grid + previews). */
enum class IconSize(val label: String, val tileDp: Int) {
    SMALL("Small", 44),
    DEFAULT("Default", 52),
    LARGE("Large", 62),
    EXTRA_LARGE("Extra large", 72),
}

/** Card weight presets for the environment/hero cards and tile plates. */
enum class CardSize(val label: String, val scale: Float) {
    COMPACT("Compact", 0.85f),
    DEFAULT("Default", 1.0f),
    LARGE("Large", 1.18f),
}

/**
 * App UI text scale — a multiplier on the system font scale applied at the
 * theme root (Appearance §"Text scaling"). Discrete presets; the terminal's
 * own default font size stays a separate, terminal-scoped setting.
 */
enum class TextScale(val label: String, val factor: Float) {
    SMALL("Small", 0.85f),
    DEFAULT("Default", 1.0f),
    LARGE("Large", 1.15f),
    EXTRA_LARGE("Extra large", 1.3f),
}

/**
 * Icons per row on Home. [AUTO] keeps the width-derived density (the
 * historical responsive behavior); explicit values are a USER PREFERENCE
 * that the width still caps — a narrow phone never gets squeezed past what
 * fits (brief §5: "only values that make sense for the current device
 * width").
 */
enum class IconColumns(val label: String, val requested: Int) {
    AUTO("Auto", 0),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
}

object HomeGridDensity {

    /** Width-derived column maximums — the historical responsive ladder. */
    fun columnsForWidth(maxWidthDp: Float): Int = when {
        maxWidthDp >= 840f -> 6
        maxWidthDp >= 600f -> 4
        else -> 3
    }

    /**
     * Effective icons per row: the user's preference, capped by what the
     * width sensibly allows, floored at 2. AUTO = pure width-derived value.
     */
    fun effectiveColumns(preference: IconColumns, maxWidthDp: Float): Int {
        val byWidth = columnsForWidth(maxWidthDp)
        if (preference == IconColumns.AUTO) return byWidth
        return preference.requested.coerceIn(2, byWidth)
    }

    /**
     * Launcher-entry width for the x-scroll rows: one page of `columns`
     * entries exactly fills the content width (entries carry their own
     * inner gutters) — the M7.1 P2.2 contract, now parameterized.
     */
    fun entryWidthDp(contentWidthDp: Float, columns: Int): Float =
        (contentWidthDp - 40f) / columns
}

// ---------------------------------------------------------- theme vocabulary
//
// The persisted appearance vocabulary lives in the settings layer (the
// historical home of ThemeMode) so settings stays the single source of
// truth for user-facing state; ui.theme is the palette/application layer.

/** Which visual identity is active. AURORA carries the animated layer. */
enum class AppTheme(val label: String, val aurora: Boolean = false) {
    POCKETSHELL("PocketShell"),
    NORD("Nord"),
    DRACULA("Dracula"),
    GRUVBOX("Gruvbox"),
    SOLARIZED("Solarized"),
    ONE_DARK("One Dark"),
    MONOKAI("Monokai"),
    ROSE_PINE("Rosé Pine"),
    CYBER("Cyber"),
    AURORA("Aurora", aurora = true),
}

/** Which light/dark variant of the identity is shown. AMOLED is NOT a mode. */
enum class ThemeMode(val label: String) {
    SYSTEM("System (follow device)"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** Pure JVM parser for the persisted mode string. "AMOLED" → DARK (honest degradation). */
fun parseThemeMode(raw: String?): ThemeMode = when (raw) {
    "LIGHT" -> ThemeMode.LIGHT
    "DARK", "AMOLED" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

/** Pure JVM parser for the persisted theme string. Unknown → the PocketShell identity. */
fun parseAppTheme(raw: String?): AppTheme =
    AppTheme.entries.firstOrNull { it.name == raw } ?: AppTheme.POCKETSHELL

/** The single ThemeMode→dark decision, shared by the theme layer AND the status bar. */
fun themeModeIsDark(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Parser helpers for the discrete appearance enums (unknown → default). */
fun parseTextScale(raw: String?): TextScale =
    TextScale.entries.firstOrNull { it.name == raw } ?: TextScale.DEFAULT

fun parseIconSize(raw: String?): IconSize =
    IconSize.entries.firstOrNull { it.name == raw } ?: IconSize.DEFAULT

fun parseCardSize(raw: String?): CardSize =
    CardSize.entries.firstOrNull { it.name == raw } ?: CardSize.DEFAULT

fun parseIconColumns(raw: String?): IconColumns =
    IconColumns.entries.firstOrNull { it.name == raw } ?: IconColumns.AUTO
