package app.pocketshell.notifications

/**
 * M7.2 P5 — the PURE routing-resolution model for a notification tap
 * (docs/M7.2-P5-NOTIFICATION-INTERACTION.md §4).
 *
 * This is the ONE place where a tapped [NotificationRoute] is decided into
 * what the app should DO, against the session manager's authoritative live
 * list — and it is pure data-in/data-out (no Android APIs, no coroutine, no
 * clock, no I/O), which is what makes the whole interaction contract
 * exhaustively JVM-testable (AgentRuntimeNotificationRoutingTest).
 *
 * THE INTERACTION CONTRACT (Part D, over the mandate's exact arms):
 *
 *   confirmed-running tap + live session   -> OpenSession(id): land in that
 *                                             session's terminal context
 *   runtime-unknown tap   + live session   -> OpenSession(id): uncertainty
 *                                             is not absence — the user
 *                                             lands where the runtime lives
 *   session-ended tap     + listed session -> OpenSession(id): the tab still
 *                                             exists and honestly shows its
 *                                             finished state; the exit fact
 *                                             stays a historical statement
 *   any of the above      + absent session -> OpenApp: the id is STALE — the
 *                                             app opens normally, nothing
 *                                             is resurrected (Part F: the
 *                                             notification must never become
 *                                             a process-resurrection
 *                                             mechanism)
 *   OpenApp route                          -> OpenApp (P1 behavior, pinned)
 *
 * WHAT THIS LAYER MUST NEVER DECIDE OR DO (Part K, structural-pinned): it
 * does not inspect /proc, does not scan processes, does not match PIDs,
 * does not invoke or reference the runtime detector, does not own session
 * lifecycle (the manager's live list is INPUT here, never mutated), and it
 * re-labels nothing: a routing resolution can never turn "no longer
 * detected" into finished/success, "runtime unknown" into running/stopped,
 * or an exit code into a task outcome — this file produces navigation and
 * nothing else.
 */
object AgentRuntimeNotificationRouting {

    /** What a consumed notification tap should do. */
    sealed interface Resolution {

        /**
         * Select this session in the terminal UI (the id is already proven
         * live against the manager's list). Navigation only — the session is
         * shown exactly as it is, never restored or recreated.
         */
        data class OpenSession(val sessionId: Long) : Resolution

        /**
         * The P1 behavior: the system resume IS the action. This is both the
         * literal OpenApp route and the honest fallback for a stale or
         * malformed session target.
         */
        data object OpenApp : Resolution
    }

    /**
     * Resolve one tapped route against the manager's AUTHORITATIVE live
     * session ids (TerminalSessionManager.sessions — read-only input; a
     * finished-but-listed session counts as present: its tab honestly shows
     * its end state). Pure: identical inputs always produce identical
     * outcomes.
     */
    fun resolve(route: NotificationRoute, liveSessionIds: Set<Long>): Resolution =
        when (route) {
            is NotificationRoute.OpenApp -> Resolution.OpenApp

            is NotificationRoute.OpenSession ->
                if (route.sessionId in liveSessionIds) {
                    Resolution.OpenSession(route.sessionId)
                } else {
                    // Stale identity: the session is gone. Open normally —
                    // never recreate, never respawn, never fake selection.
                    Resolution.OpenApp
                }
        }
}
