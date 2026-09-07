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
        // Phase 3.4 expansion (docs/PHASE-3.4-DESIGN.md §2): the widely used
        // terminal AI agents, seeded the same way — each one still only
        // SURFACES when the guest's login shell finds its command. kilo is
        // the Kilo Code CLI (`npm install -g @kilocode/cli`).
        CommandApp(
            id = "kilo",
            displayName = "Kilo Code",
            launchCommand = listOf("kilo"),
            description = "Open-source AI coding agent for the terminal",
            monogram = "K",
        ),
        // M7.1 P1: Cline joins the named built-in launcher set
        // (Kilo Code, Cline, Hermes, Claude Code, Codex).
        CommandApp(
            id = "cline",
            displayName = "Cline",
            launchCommand = listOf("cline"),
            description = "Autonomous coding agent for the terminal",
            monogram = "C",
        ),
        CommandApp(
            id = "gemini",
            displayName = "Gemini CLI",
            launchCommand = listOf("gemini"),
            description = "Google's AI agent for the terminal",
            monogram = "G",
        ),
        CommandApp(
            id = "codex",
            displayName = "Codex",
            launchCommand = listOf("codex"),
            description = "OpenAI's terminal coding agent",
            monogram = "C",
        ),
        CommandApp(
            id = "aider",
            displayName = "Aider",
            launchCommand = listOf("aider"),
            description = "AI pair programming in your terminal",
            monogram = "A",
        ),
        CommandApp(
            id = "qwen",
            displayName = "Qwen Code",
            launchCommand = listOf("qwen"),
            description = "Qwen coding agent for the terminal",
            monogram = "Q",
        ),
    )

    fun byId(id: String): CommandApp? = registry.firstOrNull { it.id == id }
}

/**
 * The launch command's head — the name the guest probe asks about.
 */
fun CommandApp.probeName(): String = launchCommand.first()

/**
 * Phase 3.5 — the ONE guest launch path (v0.7.0-m3.5 regression fix).
 *
 * The command is delivered to the guest shell through its ARGV —
 * `sh -l -c "<command>; exec sh -l"` — never through a PTY write after
 * spawn. The PTY write was a silent no-op: TerminalSession only forks the
 * process when the view first renders it (initializeEmulator ← updateSize),
 * and write() drops bytes while mShellPid == 0. The argv form is
 * deterministic — the login shell reads its profiles and then runs the
 * command itself, exactly what typing it would do, whenever the view
 * attaches. When the app exits, the exec'd login shell takes over and the
 * user lands at a real prompt.
 *
 * Pure and test-pinned: [CommandAppsTest] locks the quoting, the single
 * form, and the trailing `exec` fallback.
 */
fun guestLaunchChain(
    launchCommand: List<String>,
    guestShell: String,
): String {
    val command = launchCommand.joinToString(" ") { token ->
        // launch commands are plain argv tokens (pinned by tests); the quote
        // is defense in depth, never a substitute for validation
        if (token.matches(Regex("[A-Za-z0-9._/+%-]+"))) token else "'$token'"
    }
    return "$command; exec $guestShell -l"
}

/**
 * M7.0.0 Phase 7 — the "Open Terminal Here" launch chain: the SIBLING of
 * [guestLaunchChain], built for arbitrary FILESYSTEM PATHS instead of
 * registry argv tokens.
 *
 * Why a sibling: [guestLaunchChain] quotes with a registry-token allowlist
 * regex — its inputs are plain command names pinned by tests. A directory
 * from the Files explorer is a different input class entirely: valid
 * area-native names may contain spaces, apostrophes, double quotes, `$`,
 * `;`, `&&`, `|`, backticks and newlines (all accepted by the existing
 * path validation). Token allowlisting can never carry those, so this
 * helper quotes the directory as ONE POSIX single-quoted word — the shell
 * cannot reinterpret any character inside it:
 *
 *   - every byte of the path travels inside `'…'`, where POSIX defines the
 *     content as literal data (no expansion, no splitting, no history);
 *   - the one character single quotes cannot hold — `'` — is emitted as
 *     `'\''` (close quote, backslash-escaped quote, reopen quote), the
 *     canonical POSIX escape;
 *   - `cd --` ends option parsing, so a leading-dash path stays a path;
 *   - `&&` (not `;`) means the interactive login shell is exec'd only
 *     after a SUCCESSFUL cd — a vanished directory exits the chain instead
 *     of silently dropping the user somewhere else (usually $HOME);
 *   - `exec /bin/sh -l` (the supplied [guestShell]) replaces the -c shell
 *     with a real interactive login prompt at the new working directory —
 *     the same trailing-exec contract as [guestLaunchChain].
 *
 * The directory value remains DATA end to end: this function never parses,
 * validates or rewrites it (validation happened upstream in the Phase 2
 * [app.pocketshell.files.PathSafety] system) and it only ever WRAPS it.
 * Pure and test-pinned by [CommandAppsTest], including an execution-level
 * fixture that runs the real chain through /bin/sh against a directory
 * whose name contains every metacharacter at once.
 */
fun guestTerminalChain(
    directory: String,
    guestShell: String,
): String = "cd -- ${posixSingleQuoted(directory)} && exec $guestShell -l"

/**
 * POSIX single-quote wrapping for one arbitrary string: the canonical
 * `'` → `'\''` escape, everything else literal inside the quotes.
 * Private: callers must go through [guestTerminalChain] so the cd/exec
 * contract stays in one place.
 */
private fun posixSingleQuoted(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

/**
 * M7.1 Phase 1 — the CUSTOM-TOOL launch chain: the SIBLING of
 * [guestLaunchChain] for USER-CONFIGURED command lines.
 *
 * Why a sibling: [guestLaunchChain] receives registry ARGV TOKENS and
 * quotes each defensively — a custom tool's command is one USER-OWNED
 * shell line ("my-tool --serve --port 8080"), and quoting it as a single
 * token would make the guest search for a binary with spaces in its name.
 * The command travels verbatim into the SAME delivery structure —
 * `sh -l -c "<command>; exec <guestShell>"` — exactly what typing the line
 * at the prompt would run, with the identical trailing-exec contract: the
 * login shell reads its profiles, runs the line, and when it exits the
 * user lands at a real prompt.
 *
 * Safety posture (P1 brief PART H): the command is USER CONFIGURATION,
 * treated as data end to end — this function never parses, validates or
 * rewrites it (hygiene validation lives in CustomToolValidation; existence
 * checking is the tap-time guest probe on the command's head token). The
 * user owns the guest environment; this is the same trust boundary as the
 * terminal itself, reached through the SAME session machinery — never a
 * new shell path.
 *
 * Pure and test-pinned by [CommandAppsTest] (single-line contract,
 * trailing exec, verbatim transport).
 */
fun guestCustomCommandChain(
    command: String,
    guestShell: String,
): String = "$command; exec $guestShell -l"

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
