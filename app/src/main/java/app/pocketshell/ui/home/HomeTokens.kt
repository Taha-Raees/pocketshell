package app.pocketshell.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 3.2 "Home / OS Launcher" — home-specific tokens layered on the shared
 * Midnight Sapphire system (docs/PHASE-3.2-DESIGN.md §1). Base surfaces, text,
 * accent and hairlines come from TerminalTheme (the Midnight Sapphire token
 * object); only values Home needs beyond it live here.
 *
 * NO pure black. NO gradients on Home — depth comes from surface steps,
 * hairlines and restrained shadows. ONE accent (Sapphire) across the page;
 * [runningGreen] appears ONLY on a dot that really means "process running"
 * (same value as the terminal's ANSI green — one palette everywhere).
 */
object HomeTokens {

    // ---- surfaces (TerminalTheme layers, named for their Home role) ----------
    val surfaceHero = app.pocketshell.ui.theme.TerminalTheme.canvas      // Terminal tile
    val surfaceEnv = app.pocketshell.ui.theme.TerminalTheme.chrome       // Linux tile
    val surfaceApp = app.pocketshell.ui.theme.TerminalTheme.keyAlt       // app icon tiles
    val surfaceRaised = app.pocketshell.ui.theme.TerminalTheme.deck      // FAB cluster chips
    val surfaceBanner = app.pocketshell.ui.theme.TerminalTheme.tabStrip  // honest banners

    // ---- lines & text (re-exported for home-local readability) --------------
    val hairline = app.pocketshell.ui.theme.TerminalTheme.divider
    val textPrimary = app.pocketshell.ui.theme.TerminalTheme.textPrimary
    val textDim = app.pocketshell.ui.theme.TerminalTheme.textDim

    // ---- the one accent -------------------------------------------------------
    val accent = app.pocketshell.ui.theme.TerminalTheme.accent
    val accentBright = app.pocketshell.ui.theme.TerminalTheme.accentBright
    val accentDeep = app.pocketshell.ui.theme.TerminalTheme.accentDeep
    val onAccent = app.pocketshell.ui.theme.TerminalTheme.enterGlyph

    /** ONLY on a real running-process indicator. */
    val runningGreen = Color(0xFF5FB572)

    /** Launch-error banner (restrained, recognizably a warning). */
    val danger = app.pocketshell.ui.theme.TerminalTheme.danger

    // ---- geometry --------------------------------------------------------------
    val heroRadius: Dp = 20.dp
    val appTileRadius: Dp = 16.dp
    val chipRadius: Dp = 14.dp

    /** Launcher content max width on tablets (centered, never stretched). */
    val contentMaxWidth: Dp = 720.dp
}
