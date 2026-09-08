package app.pocketshell.notifications

import android.content.Intent

/**
 * M7.2 P1 — the notification tap-routing foundation (P0 audit §8.4/§11:
 * MainActivity is `singleTask`, ignores intent extras entirely, and a tap on
 * a notification while the app is alive arrives as `onNewIntent`, which
 * nothing handled before P1).
 *
 * P1 deliberately defined only the smallest useful route vocabulary —
 * [OpenApp] — and processed intents through one exhaustive `when` in
 * MainActivity. Adding a route is a compile-time-forced extension here AND
 * in that `when`, so an unrecognized extra can never silently fall through
 * the routing chain.
 *
 * M7.2 P5 — the session-targeted route [OpenSession]: "open the app INTO the
 * terminal context of THIS session" (the agent-runtime notifications' tap
 * action). The carried identity is the SESSION ID — the authoritative key
 * the P3c event already carried, and the only id the interaction layer may
 * carry (no PID, no process name, no token, no display string — Part D/E).
 * A session-targeted tap never rediscovers anything: whether the session
 * still exists is resolved against the manager's authoritative live list at
 * consumption time ([AgentRuntimeNotificationRouting]), never here and never
 * from the notification.
 *
 * HONEST DEGRADATION (Part F): a session-targeted route whose session-id
 * extra is missing/malformed degrades to [OpenApp] — the app opens normally,
 * nothing is fabricated, nothing crashes. The parser core is pure ([parse])
 * and JVM-tested; [fromIntent] is the thin Android adapter.
 */
sealed interface NotificationRoute {

    /**
     * "Open the app." No in-app action is required — for a `singleTask`
     * activity the system resume IS the action, both from cold start
     * (onCreate) and for an existing instance (onNewIntent). It exists so
     * the routing chain has a real, recognized value to carry. It is also
     * the P5 stale/missing-session fallback: an unresolvable session target
     * degrades to exactly this (PocketShell opens normally — never a fake
     * restoration).
     */
    data object OpenApp : NotificationRoute

    /**
     * M7.2 P5 — "open the app into session [sessionId]'s terminal context,
     * if that context still exists." The id is the session manager's
     * authoritative session id, verbatim from the P3c/P4 notification
     * identity — not re-derived, not matched, not looked up in /proc.
     * Existence/staleness is decided by the pure resolution model at
     * consumption time against the manager's live list; this route only
     * CARRIES the identity.
     */
    data class OpenSession(val sessionId: Long) : NotificationRoute

    companion object {
        const val EXTRA_ROUTE = "app.pocketshell.notification.ROUTE"
        const val ROUTE_OPEN_APP = "open_app"

        /** M7.2 P5 — the session-targeted route value and its id extra. */
        const val ROUTE_OPEN_SESSION = "open_session"
        const val EXTRA_SESSION_ID = "app.pocketshell.notification.SESSION_ID"

        /**
         * Pure core: map the raw extra values to a route. Unknown route
         * values are honestly null (a plain launch / an unrecognized extra).
         * A session route with a missing or malformed id (non-positive)
         * degrades to [OpenApp] — the honest safe fallback, never a crash
         * and never a fabricated target.
         */
        fun parse(routeValue: String?, sessionIdValue: Long? = null): NotificationRoute? =
            when (routeValue) {
                ROUTE_OPEN_APP -> OpenApp
                ROUTE_OPEN_SESSION ->
                    if (sessionIdValue != null && sessionIdValue >= 1) {
                        OpenSession(sessionIdValue)
                    } else {
                        // Malformed/missing session identity: open the app
                        // normally (Part F) — the fallback IS a real route.
                        OpenApp
                    }
                else -> null
            }

        /**
         * Android adapter: read the routing extras off a real intent
         * (null-safe). On the JVM the Intent shell answers the extra getters
         * with null/0 (unitTests.returnDefaultValues), so only the
         * null/no-route paths are executable there; extras-carrying taps are
         * device-verified (docs/TESTING.md §48/§54).
         */
        fun fromIntent(intent: Intent?): NotificationRoute? =
            parse(
                intent?.getStringExtra(EXTRA_ROUTE),
                intent?.getLongExtra(EXTRA_SESSION_ID, 0L),
            )
    }
}
