package app.pocketshell.widget

/**
 * The seam a Home application uses to act (navigation ONLY — a Home
 * application never spawns, writes to a PTY, posts notifications or owns
 * state). Implemented once at the MainActivity wiring so every action
 * stays the app's existing navigation path; there is no second
 * navigation mechanism. (M8.2: carried over from the M8 widget seam,
 * unchanged in spirit — now serving Home applications.)
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

    /** The Control Center's Home-application management page. */
    fun openWidgetSettings()

    /** Open a local URL in the existing Companion browser. */
    fun openCompanion(url: String)
}
