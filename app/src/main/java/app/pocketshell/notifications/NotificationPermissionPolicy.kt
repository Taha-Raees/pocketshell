package app.pocketshell.notifications

/**
 * M7.2 P1 — the ONE decision function for the controlled POST_NOTIFICATIONS
 * runtime request (the P0 audit's largest ready-made gap: the permission is
 * declared in the manifest but was never requested at runtime).
 *
 * The policy is intentionally pure so the JVM suite can pin the whole truth
 * table; the Android glue ([NotificationPermissionGate]) only reads the real
 * inputs and calls this.
 *
 * Anti-nag rules (P1 spec §4):
 *
 *  - Pre-Android 13 the runtime permission does not exist — never request
 *    (notifications are governed only by the app-level "blocked" toggle).
 *  - Already granted — never request again (rotation, recreation, restart).
 *  - Already requested once by THIS install (the persisted
 *    `permission_requested` flag is set BEFORE the dialog is launched, so a
 *    recreation or process death mid-dialog can never re-arm it) — never
 *    request again, whatever the answer was (granted / denied / dismissed).
 *  - The system's own one-shot prompt (on Android 13 an app with a
 *    pre-13 targetSdk gets an implicit prompt after its first notification
 *    channel is created) was already answered with a denial —
 *    [systemAlreadyAsked] (Activity.shouldShowRequestPermissionRationale)
 *    is true exactly in that state for this install — respect the denial,
 *    never request on top of it.
 *  - Otherwise (13+, not granted, never asked by anyone): ask exactly once.
 *
 * Denial is always safe: notifications are optional output — the terminal,
 * the guest runtime and TerminalService behave identically without the
 * permission (the FGS notification is simply hidden on Android 13+; the
 * service itself is unaffected).
 */
object NotificationPermissionPolicy {

    /** Android 13 (Tiramisu) is the first release gating notifications behind a runtime permission. */
    const val RUNTIME_PERMISSION_SDK = 33

    fun shouldRequest(
        sdkInt: Int,
        granted: Boolean,
        requestedBefore: Boolean,
        systemAlreadyAsked: Boolean,
    ): Boolean {
        if (sdkInt < RUNTIME_PERMISSION_SDK) return false
        if (granted) return false
        if (requestedBefore) return false
        if (systemAlreadyAsked) return false
        return true
    }
}
