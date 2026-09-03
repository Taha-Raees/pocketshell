package app.pocketshell.packages

/**
 * Launchable-app detection (docs/UI-REDESIGN.md §9 — the documented rules).
 *
 * A "launchable app" is a program inside the Linux guest that provides a
 * meaningful interactive application of its own (Hermes, OpenCode, …) — as
 * opposed to ordinary CLI tools (git, python, nano, htop, …) which are
 * infrastructure and stay reachable through Packages search only.
 *
 * Rules (deliberately strict):
 *  1. An entry here is a CLAIM, never a state. The guest must confirm it
 *     live: runtime READY **and** `command -v <executable>` succeeding right
 *     now. A probe failure HIDES the row — a dead tile pretending to be an
 *     app is exactly the fake this project refuses.
 *  2. The list is small and hand-documented; adding an entry is one data row
 *     plus a test pin. No large hardcoded "app store" pretending breadth.
 *  3. Launching goes through the same verify-then-launch flow as catalog
 *     apps (real apk db + `command -v` + a dedicated guest session), so
 *     "Open" can never fake.
 */
data class LaunchableApp(
    val id: String,
    val name: String,
    val description: String,
    /** What `command -v` must find before the app is shown or opened. */
    val executable: String,
    /** The real guest command — no shell wrapping. */
    val launchCommand: List<String>,
)

object LaunchableApps {

    val entries: List<LaunchableApp> = listOf(
        LaunchableApp(
            id = "hermes",
            name = "Hermes",
            description = "Hermes Agent — AI agent with skills and tools",
            executable = "hermes",
            launchCommand = listOf("hermes"),
        ),
        LaunchableApp(
            id = "opencode",
            name = "OpenCode",
            description = "OpenCode — AI coding agent for the terminal",
            executable = "opencode",
            launchCommand = listOf("opencode"),
        ),
    )

    /**
     * The guest-confirmed subset: every returned entry just answered
     * `command -v` with a real path. Anything else stays invisible.
     */
    suspend fun detectInstalled(): List<LaunchableApp> =
        entries.filter { app ->
            PackageGateway.executablePath(app.executable) != null
        }
}
