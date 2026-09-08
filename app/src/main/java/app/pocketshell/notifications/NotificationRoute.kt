package app.pocketshell.notifications

import android.content.Intent

/**
 * M7.2 P1 — the notification tap-routing foundation (P0 audit §8.4/§11:
 * MainActivity is `singleTask`, ignores intent extras entirely, and a tap on
 * a notification while the app is alive arrives as `onNewIntent`, which
 * nothing handled before P1).
 *
 * P1 deliberately defines only the smallest useful route vocabulary —
 * [OpenApp] — and processes intents through one exhaustive `when` in
 * MainActivity. Adding a future route (e.g. a session-targeted route for the
 * P8 phase) is a compile-time-forced extension here AND in that `when`, so
 * an unrecognized extra can never silently fall through the routing chain.
 *
 * The parser core is pure ([parse]) and JVM-tested; [fromIntent] is the thin
 * Android adapter.
 */
sealed interface NotificationRoute {

    /**
     * "Open the app." No in-app action is required — for a `singleTask`
     * activity the system resume IS the action, both from cold start
     * (onCreate) and for an existing instance (onNewIntent). It exists so
     * the routing chain has a real, recognized value to carry.
     */
    data object OpenApp : NotificationRoute

    companion object {
        const val EXTRA_ROUTE = "app.pocketshell.notification.ROUTE"
        const val ROUTE_OPEN_APP = "open_app"

        /** Pure core: map a raw extra value to a route; unknown values are honestly null. */
        fun parse(value: String?): NotificationRoute? = when (value) {
            ROUTE_OPEN_APP -> OpenApp
            else -> null
        }

        /** Android adapter: read the route extra off a real intent (null-safe). */
        fun fromIntent(intent: Intent?): NotificationRoute? =
            parse(intent?.getStringExtra(EXTRA_ROUTE))
    }
}
