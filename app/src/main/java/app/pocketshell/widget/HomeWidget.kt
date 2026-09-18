package app.pocketshell.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.terminal.AgentHomeSessionClaims

/**
 * The seam a widget uses to act (navigation ONLY — a widget never spawns,
 * writes to a PTY, posts notifications or owns state). Implemented at the
 * MainActivity wiring so every action stays the app's existing navigation
 * path; there is no second navigation mechanism.
 */
interface WidgetNav {
    /** Verify-then-open the terminal (navigate only on a real session). */
    fun openTerminal()

    /** Enter the Linux guest (async; navigates when a session exists). */
    fun openLinuxShell()

    /** The honest not-ready destination (install/retry/repair live there). */
    fun openDiagnostics()

    /** The Files explorer at the guest root (the Linux storage surface). */
    fun openGuestFiles()

    /** The Control Center's Home-widgets management page. */
    fun openWidgetSettings()

    /** Open a local URL in the existing Companion browser (M8.1 servers). */
    fun openCompanion(url: String)
}

/**
 * Everything a widget's card content may read. The HERO ROW is the one
 * collector: Home already holds this state (runtime, sessions, claims) as
 * reactive, lifecycle-aware values, and the host passes a read-only view
 * down — widgets never collect a second source of truth on their own.
 */
data class WidgetContentContext(
    val heightDp: Dp,
    val auroraPhase: State<Float>,
    val runtimeState: RuntimeState,
    val runningSessions: Int,
    val agentClaims: Map<Long, AgentHomeSessionClaims.SessionClaim>,
    val nav: WidgetNav,
)

/**
 * A Home widget: [spec] for the host/Control Center, the tap contract for
 * the hero chrome, and the card content itself.
 *
 * Content renders INSIDE the standard hero chrome (clip → surface tone →
 * aurora edge → 16dp padding) at the shared hero height — the widget owns
 * only the content Column. The visual language is the existing two-card
 * one: mark top-left, mono count top-right, title + one honest state line
 * at the bottom, at most three quiet data rows between.
 */
abstract class HomeWidget {

    abstract val spec: WidgetSpec

    /** Accessibility label for the card's tap (null → not clickable). */
    open fun tapLabel(context: WidgetContentContext): String = "Open ${spec.name}"

    /** The card's single action, resolved per state (null → not clickable). */
    abstract fun tapAction(context: WidgetContentContext): (() -> Unit)?

    /** The card's children, laid out inside the hero chrome's padded box. */
    @Composable
    abstract fun Content(context: WidgetContentContext)
}
