package app.pocketshell.widget

import androidx.compose.runtime.Composable
import app.pocketshell.runtime.RuntimeState

/**
 * M8.2 — the ONE Home Application Card pattern (docs/M8-WIDGET-SYSTEM.md
 * §9, rewritten for M8.2): a persistent Home surface that hosts one
 * PocketShell-native SINGLE-PAGE application.
 *
 * The contract, established by [ServersApp] as the reference
 * implementation:
 *
 *   - The card IS the application's screen. The user remains on Home;
 *     actions transform the application's state INSIDE the same card
 *     (overview → detail → back), never a shrunken full-screen app and
 *     never a second Activity.
 *   - The application is designed FOR the available card dimensions
 *     (the composition adapts its information hierarchy, it does not
 *     scale a desktop layout).
 *   - Every visual token comes from the shared PocketShell theme system
 *     (TerminalTheme / HomeTokens) — the card follows whatever theme the
 *     user selected; theme-specific effects (the Aurora edge) appear
 *     only when the selected theme provides them (the shared root phase
 *     is theme-gated). No application owns a theme.
 *   - Truthfulness is inherited from the probe layer: real endpoints,
 *     real attribution, honest unknowns, no invented controls.
 */
abstract class HomeApplication {

    abstract val spec: HomeAppSpec

    /**
     * The application's single-page content, rendered inside the standard
     * Home application card chrome. Internal navigation (overview /
     * detail / back) is the application's own state, scoped to its own
     * composition — `rememberSaveable` keeps it across rotation and
     * Home↔Settings round-trips.
     */
    @Composable
    abstract fun Content(context: HomeAppContext)
}

/** The application's identity card: registry id, name, honest summary. */
data class HomeAppSpec(
    val id: String,
    val name: String,
    val summary: String,
)

/** What a Home application may read and act on. Navigation + honest state. */
data class HomeAppContext(
    val nav: WidgetNav,
    val runtimeState: RuntimeState,
)
