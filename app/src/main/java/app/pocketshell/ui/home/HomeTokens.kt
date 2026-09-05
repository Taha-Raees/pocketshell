package app.pocketshell.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Phase 3.2 "Home / OS Launcher" — home-specific tokens layered on the shared
 * Midnight Sapphire system (docs/PHASE-3.2-DESIGN.md §1). Base surfaces, text,
 * accent and hairlines come from TerminalTheme (the Midnight Sapphire token
 * object); only values Home needs beyond it live here.
 *
 * Phase 5: every token is a READ-THROUGH accessor into the now-reactive
 * TerminalTheme, so Home recomposes on a theme switch exactly like every
 * other surface — with zero call-site changes.
 *
 * NO pure black. NO gradients on Home — depth comes from surface steps,
 * hairlines and restrained shadows. ONE accent (Sapphire) across the page;
 * [runningGreen] appears ONLY on a dot that really means "process running"
 * (same value as the terminal's ANSI green — one palette everywhere).
 */
object HomeTokens {

    // ---- surfaces (TerminalTheme layers, named for their Home role) ----------
    val surfaceHero: Color get() = TerminalTheme.canvas      // Terminal tile
    val surfaceEnv: Color get() = TerminalTheme.chrome       // Linux tile
    val surfaceApp: Color get() = TerminalTheme.keyAlt       // app icon tiles
    val surfaceRaised: Color get() = TerminalTheme.deck      // raised chips
    val surfaceBanner: Color get() = TerminalTheme.tabStrip  // honest banners

    // ---- lines & text (re-exported for home-local readability) --------------
    val hairline: Color get() = TerminalTheme.divider
    val textPrimary: Color get() = TerminalTheme.textPrimary
    val textDim: Color get() = TerminalTheme.textDim

    /** Text on [surfaceHero] (the pinned-dark canvas tone) — pinned light. */
    val onHero: Color get() = TerminalTheme.onCanvas
    val onHeroDim: Color get() = TerminalTheme.onCanvasDim

    // ---- the one accent -------------------------------------------------------
    val accent: Color get() = TerminalTheme.accent
    val accentBright: Color get() = TerminalTheme.accentBright
    val accentDeep: Color get() = TerminalTheme.accentDeep
    val onAccentDeep: Color get() = TerminalTheme.onAccentDeep
    val onAccent: Color get() = TerminalTheme.enterGlyph

    /** ONLY on a real running-process indicator. */
    val runningGreen: Color get() = TerminalTheme.runningGreen

    /** Launch-error banner (restrained, recognizably a warning). */
    val danger: Color get() = TerminalTheme.danger

    // ---- geometry --------------------------------------------------------------
    // Phase 3.3 radius discipline (docs/PHASE-3.3-DESIGN.md §4): environment
    // tiles and menus at 14dp, app icon plates at 16dp — nothing on Home
    // exceeds 16dp, and no element carries a decorative border at rest.
    val heroRadius: Dp = 14.dp
    val appTileRadius: Dp = 16.dp
    val chipRadius: Dp = 14.dp

    /** Launcher content max width on tablets (centered, never stretched). */
    val contentMaxWidth: Dp = 720.dp
}
