package app.pocketshell.widget

import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme

/**
 * M8 — the Home Linux Widget System (docs/M8-WIDGET-SYSTEM.md).
 *
 * Home owns exactly TWO hero-card slots. The slots do not know what a
 * widget IS: they render [HomeWidget] implementations resolved from
 * persisted slot ids. A widget is a small, independently described
 * capability — "what is happening, and what can I do about it" — that
 * earns its card space per docs/M8-WIDGET-RESEARCH.md §3.
 *
 * BUILT-IN widgets are Kotlin objects compiled into the APK. OPTIONAL
 * widgets (a future GitHub catalog, `widgets/` in the repository root)
 * are DATA — a validated manifest rendered by a fixed, restricted
 * renderer — never code. Nothing here executes anything downloaded.
 */

/** The two hero-card surface tones (the existing Midnight surface stack). */
enum class WidgetTone {
    /** The pinned-dark canvas tone (the Terminal tile's surface). */
    Canvas,
    /** The chrome tone, one step lighter than the page (the Linux tile's surface). */
    Chrome,
}

/** The color pair a hero card's content renders with, derived from its tone. */
data class WidgetPalette(
    val title: androidx.compose.ui.graphics.Color,
    val dim: androidx.compose.ui.graphics.Color,
    val accent: androidx.compose.ui.graphics.Color,
) {
    companion object {
        fun of(tone: WidgetTone): WidgetPalette = when (tone) {
            // The canvas tone is pinned dark in every theme, so its text is
            // the pinned-light hero pair (the Terminal tile's contract).
            WidgetTone.Canvas -> WidgetPalette(HomeTokens.onHero, HomeTokens.onHeroDim, HomeTokens.accentBright)
            WidgetTone.Chrome -> WidgetPalette(HomeTokens.textPrimary, HomeTokens.textDim, HomeTokens.accent)
        }
    }
}

/**
 * The widget's identity card. Everything the slot host and the Control
 * Center need WITHOUT rendering the widget.
 *
 * [widthWeight] is the hero-row Row weight: the two DEFAULT widgets carry
 * 1.25 / 1.0, reproducing today's exact hero geometry; anything else is 1.0.
 */
data class WidgetSpec(
    val id: String,
    val name: String,
    val summary: String,
    val isCore: Boolean,
    val tone: WidgetTone = WidgetTone.Chrome,
    val widthWeight: Float = 1f,
)

/** One resolved slot: the widget to render, or the honest Missing state. */
sealed interface WidgetSlotEntry {
    data class Resolved(val widget: HomeWidget) : WidgetSlotEntry
    data class Missing(val widgetId: String) : WidgetSlotEntry
}

/** The stored slot assignment codec: absent/corrupt → the defaults. */
object WidgetSlotsCodec {

    private val pattern = Regex("""[a-z][a-z0-9.-]{1,63}""")

    fun encode(ids: List<String>): String = ids.joinToString("\u0000")

    /**
     * Exactly two slot ids, or the defaults. Unknown-shaped tokens are
     * dropped, not preserved — a corrupted record can never inject a
     * synthetic widget id; resolution renders unknown (well-shaped) ids
     * as the honest Missing card instead.
     */
    fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return WidgetRegistry.DEFAULT_SLOTS
        val ids = raw.split("\u0000")
            .map { it.trim() }
            .filter { it.isNotEmpty() && pattern.matches(it) }
            .distinct()
        if (ids.isEmpty()) return WidgetRegistry.DEFAULT_SLOTS
        return fillToTwo(ids)
    }

    /** Home owns exactly two slots — trim overflow, fill gaps with defaults. */
    fun fillToTwo(ids: List<String>): List<String> {
        val trimmed = ids.take(2).toMutableList()
        for (fallback in WidgetRegistry.DEFAULT_SLOTS) {
            if (trimmed.size >= 2) break
            if (!trimmed.contains(fallback)) trimmed.add(fallback)
        }
        return trimmed.toList()
    }
}
