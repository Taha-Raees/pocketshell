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

    /** Home sections' side paddings (2 × 20dp) — the historical constant. */
    const val PAGE_GUTTER_DP = 40f

    /** Home launcher content max width (HomeTokens.contentMaxWidth, mirrored for the pure module). */
    const val CONTENT_MAX_WIDTH_DP = 720f

    /**
     * Per-cell gutter reserved around the icon+label INSIDE an entry
     * (entry paddings + breathing room). Explicit column requests may pack
     * down to this; AUTO wants more comfort.
     */
    const val CELL_PADDING_DP = 24f

    /** AUTO's comfort target on top of the icon (≈110dp cells at default 52dp icons). */
    const val AUTO_CELL_COMFORT_DP = 58f

    /** Column bounds — brief §5: 2..6, whatever the width genuinely fits. */
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 6

    /** Cell width a column of [iconTileDp] icons needs at minimum. */
    fun minCellWidthDp(iconTileDp: Int): Float = iconTileDp + CELL_PADDING_DP

    /** The grid's usable width: content-capped, gutters removed. */
    fun usableWidthDp(availableWidthDp: Float): Float =
        availableWidthDp.coerceAtMost(CONTENT_MAX_WIDTH_DP) - PAGE_GUTTER_DP

    /**
     * requestedColumns → availableWidth → minimum comfortable cell width →
     * actual columns (brief §5). The HARD fit: how many columns of
     * [iconTileDp] icons can share the width without any cell dropping
     * below its minimum.
     */
    fun fitByWidth(availableWidthDp: Float, iconTileDp: Int): Int =
        (usableWidthDp(availableWidthDp) / minCellWidthDp(iconTileDp))
            .toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * The AUTO baseline: comfortable ~110dp cells at default icon size,
     * re-derived for the chosen icon size and clamped to the fit. Phones
     * land at 3 (the historical density); tablets/foldables/DeX fill the
     * width instead of idling at 3-of-6 columns of dead space.
     */
    fun autoColumns(availableWidthDp: Float, iconTileDp: Int): Int =
        (usableWidthDp(availableWidthDp) / (iconTileDp + AUTO_CELL_COMFORT_DP))
            .toInt()
            .coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    /**
     * The ONE column decision for the Home launcher grids.
     *
     * AUTO → the comfortable density for this width × icon size.
     * Explicit 2..6 → honored WHENEVER the width can carry it (a request of
     * 4 on a phone that fits 4 MUST show 4 — the retired behavior capped
     * everything under 600dp to 3); denser-than-fits requests clamp
     * gracefully at the fit instead of breaking the grid.
     */
    fun effectiveColumns(
        preference: IconColumns,
        availableWidthDp: Float,
        iconTileDp: Int = 52,
    ): Int = when (preference) {
        IconColumns.AUTO -> autoColumns(availableWidthDp, iconTileDp)
        else -> preference.requested.coerceIn(MIN_COLUMNS, fitByWidth(availableWidthDp, iconTileDp))
    }

    /**
     * Launcher-entry width for the x-scroll rows: one page of `columns`
     * entries exactly fills the content width (entries carry their own
     * inner gutters) — the M7.1 P2.2 contract, now parameterized.
     */
    fun entryWidthDp(contentWidthDp: Float, columns: Int): Float =
        (contentWidthDp - PAGE_GUTTER_DP) / columns
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
    "SYSTEM" -> ThemeMode.SYSTEM
    "LIGHT" -> ThemeMode.LIGHT
    "DARK", "AMOLED" -> ThemeMode.DARK
    // Control Center II — the fresh-install identity is Aurora × DARK: the
    // ABSENT-key (or unknown-value) fallback is DARK. An explicitly stored
    // mode — System included — is returned verbatim, never overwritten.
    else -> ThemeMode.DARK
}

/** Pure JVM parser for the persisted theme string. Unknown → the Aurora identity. */
fun parseAppTheme(raw: String?): AppTheme =
    AppTheme.entries.firstOrNull { it.name == raw } ?: AppTheme.AURORA

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
