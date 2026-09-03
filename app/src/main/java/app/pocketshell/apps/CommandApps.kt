package app.pocketshell.apps

/**
 * Phase 3.2 — command-launchable apps (docs/PHASE-3.2-DESIGN.md §4).
 *
 * The architecture that separates PACKAGES from APPS:
 *
 *   Packages are infrastructure. Apps are experiences.
 *
 * A package being installed (git, nano, python, node, gcc, htop, …) never
 * makes it a launcher app — those are toolchains and terminal utilities used
 * THROUGH the shell, and they stay on the Packages screen. A command-launchable
 * app is a meaningful interactive application the user launches through a
 * dedicated command (`hermes`, `opencode`, …); when the guest confirms the
 * command exists, the app appears on the Home launcher and one tap launches it
 * inside a real Linux session — exactly what typing the command would do.
 *
 * Availability is NEVER metadata: the registry says "this app is known"; the
 * guest's login shell answers "this app exists here". See
 * [availableCommandApps] + the AlpinePackageManager.guestCommandPaths probe.
 */
data class CommandApp(
    /** Stable, unique id (also the registry key). */
    val id: String,
    /** Human display name shown on the launcher tile. */
    val displayName: String,
    /**
     * The guest command that launches the app, argv-style. Plain executable
     * names (single token) are pinned by tests; future entries that need
     * arguments keep them argv-safe the same way the CLI-app catalog does.
     */
    val launchCommand: List<String>,
    /** Short static description (registry metadata — never an install claim). */
    val description: String,
    /**
     * Launcher glyph: the monogram drawn on the app tile. Deliberately NOT a
     * third-party logo (legal + one consistent icon language); a polished
     * neutral monogram treatment is the identity for apps without icons.
     */
    val monogram: String,
)

object CommandAppCatalog {

    /**
     * The seed registry (Phase 3.2 brief). Extensible by adding one entry —
     * no Home redesign needed for future interactive command apps.
     *
     * zcode's launch command is the plain supported name (`zcode`); like every
     * entry here it only SURFACES when the guest's login shell actually finds
     * it — an absent binary can never render a tile.
     */
    val registry: List<CommandApp> = listOf(
        CommandApp(
            id = "hermes",
            displayName = "Hermes Agent",
            launchCommand = listOf("hermes"),
            description = "AI agent toolkit in your Linux environment",
            monogram = "H",
        ),
        CommandApp(
            id = "opencode",
            displayName = "OpenCode",
            launchCommand = listOf("opencode"),
            description = "AI coding agent for the terminal",
            monogram = "O",
        ),
        CommandApp(
            id = "claude",
            displayName = "Claude Code",
            launchCommand = listOf("claude"),
            description = "Anthropic's terminal coding agent",
            monogram = "C",
        ),
        CommandApp(
            id = "zcode",
            displayName = "ZCode",
            launchCommand = listOf("zcode"),
            description = "Interactive command app",
            monogram = "Z",
        ),
    )

    fun byId(id: String): CommandApp? = registry.firstOrNull { it.id == id }
}

/**
 * The launch command's head — the name the guest probe asks about.
 */
fun CommandApp.probeName(): String = launchCommand.first()

/**
 * Classification (pure, test-pinned): the registry subset the REAL guest
 * answer confirms, in registry order. Anything absent from [paths] is absent
 * from the launcher — this function never adds an app the guest did not name,
 * and it carries no default/assumed state (the honesty contract).
 */
fun availableCommandApps(paths: Map<String, String>): List<CommandApp> =
    CommandAppCatalog.registry.filter { app ->
        paths.containsKey(app.launchCommand.first())
    }
