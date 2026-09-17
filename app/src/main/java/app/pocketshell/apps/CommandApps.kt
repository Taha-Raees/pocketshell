package app.pocketshell.apps

import app.pocketshell.terminal.AgentLaunchRecords

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
        // M7.1 P2: Antigravity REPLACES Gemini CLI in the curated default
        // launcher set. The command is the official binary name (`agy`) —
        // named by Google's own installer (docs/ANTIGRAVITY-PLATFORM.md §1,
        // antigravity.google/cli/install.sh). Upstream ships no musl build
        // today, so on many installs the honest tap-time probe answers
        // "absent" — the launcher is a launcher, never an install claim.
        CommandApp(
            id = "agy",
            displayName = "Antigravity",
            launchCommand = listOf("agy"),
            description = "Google's Antigravity coding agent for the terminal",
            monogram = "A",
        ),
        CommandApp(
            id = "codex",
            displayName = "Codex",
            launchCommand = listOf("codex"),
            description = "OpenAI's terminal coding agent",
            monogram = "C",
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
 * M7.2 P9 — the RECORD-CARRYING launch chain: [guestLaunchChain]'s sibling
 * for REGISTRY command apps, adding the session-bound launch-record anchor
 * (docs/M7.2-P9-AGENT-OBSERVATION-ARCHITECTURE.md). The behavioral contract
 * is IDENTICAL to the plain chain — the same agent runs with the same argv,
 * the same terminal, the same trailing-exec login shell — with TWO pure
 * additions, both sandbox-prototype-proven on real Linux (E1/E3):
 *
 *   1. THE ANCHOR: a nested `sh -c` records its own pid / pgrp /
 *      /proc-self starttime into the session's record file and then EXECS
 *      the real agent — exec preserves all three, so the record names THE
 *      agent's exact live process identity (the pid-reuse-proof anchor the
 *      detector validates against a live /proc snapshot).
 *   2. THE EXIT FACT: the outer chain records the nested shell's `$?` —
 *      the agent's REAL exit status — which the plain chain structurally
 *      discards (P3a truth-loss point 3). A fact, never an interpretation.
 *
 * The command tokens are the registry's own argv tokens (the same
 * allowlist/quoting [guestLaunchChain] applies). The record file path is
 * composed by the Android side from a random per-launch token (the runtime
 * generation). Custom tools DO NOT take this path (arbitrary user shell
 * lines cannot be exec'd by the anchor) — they keep the plain chain.
 *
 * Pure and test-pinned: the single chain form, the anchor placement, the
 * trailing exec, and (in the CommandAppsTest execution fixture) the real
 * end-to-end behavior through /bin/sh.
 */
fun guestLaunchChainWithRecords(
    launchCommand: List<String>,
    guestShell: String,
    guestRecordFile: String,
    agentCommandOverride: String? = null,
    prepSnippet: String? = null,
): String {
    // Registry launch commands are plain single tokens today; apply the
    // same allowlist/quoting as the plain chain for defense in depth.
    val command = launchCommand.joinToString(" ") { token ->
        if (token.matches(Regex("[A-Za-z0-9._/+%-]+"))) token else "'$token'"
    }
    val token = launchCommand.first()
    return AgentLaunchRecords.launchChain(
        agentCommand = command,
        agentToken = token,
        guestShell = guestShell,
        guestRecordFile = guestRecordFile,
        agentCommandOverride = agentCommandOverride,
        prepSnippet = prepSnippet,
    )
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

// ================================================================ one-click install
//
// Owner iteration 4: every Home tool installs with ONE tap. When the
// tap-time guest probe answers "absent" and a spec below carries a real
// installer, PocketShell opens a NORMAL guest terminal session running that
// exact line — the user watches everything that happens in their
// environment (curl|sh is a trust decision; hiding it would be the
// dishonest kind of convenience). Every command was verified against the
// project's own first-party documentation and the vendors' official
// installers (2026-09-15):
//
//   claude   npm @anthropic-ai/claude-code   (support.claude.com)
//   opencode npm opencode-ai                 (opencode.ai/download)
//   codex    npm @openai/codex               (musl arm64 assets ship in the
//                                             package — GitHub releases)
//   qwen     npm @qwen-code/qwen-code        (github.com/QwenLM/qwen-code)
//   kilo     npm @kilocode/cli               (kilo.ai/docs CLI page)
//   cline    npm cline                       (cline.bot/cli)
//   hermes   official curl installer         (NousResearch/hermes-agent
//            install.sh; UV_LINK_MODE=copy is REQUIRED in the PocketShell
//            guest — device-validated, docs/M2.6-RESEARCH.md §8.5)
//   zcode    NONE — no official command-line installer exists (owner).
//   agy      UNSUPPORTED — upstream ships no musl build for ARM64 and the
//            glibc binary cannot run under musl; both executed and
//            documented in docs/ANTIGRAVITY-PLATFORM.md (§2/§3). The
//            install.sh's own musl manifest 404s — a curl run would fail
//            at the release server, so we refuse honestly up front.
//
// The npm specs self-install their prerequisites (`apk add nodejs npm`)
// because the guest does not guarantee node; the guest runs as root, so
// no sudo appears anywhere.

/** How a registry tool's one-click install runs in the guest. */
enum class InstallMethod { NPM, SCRIPT }

/**
 * The honest install story for one registry tool. Exactly one of [command]
 * (a full guest shell line, strict charset) or [script] (a multi-line
 * first-party POSIX script, transported base64-encoded and executed with
 * `sh -x` so every line is traced visibly) — pinned by
 * [ToolInstallCatalogTest]. [attribution] is the honest provenance line the
 * install session echoes BEFORE anything runs (unofficial/forked installers
 * must say so). [note] is reserved for tools with no installer at all.
 */
data class ToolInstallSpec(
    val method: InstallMethod,
    val command: String? = null,
    val script: String? = null,
    val attribution: String? = null,
    val note: String? = null,
)

object ToolInstallCatalog {

    /**
     * Hygiene for a one-click install line — the same shape as
     * CustomToolValidation (one line, bounded, no NUL), plus a STRICT
     * allowlist: letters, digits, space and `._/@%+|<>=&:-` only. The
     * catalog's own commands all pass; anything a future spec adds that
     * needs quotes/backticks/`$` must not silently reach the guest — it
     * fails the pin test instead.
     */
    private val commandCharset = Regex("[A-Za-z0-9 ._/@%+|<>=&;:-]+")

    fun validateCommand(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > 512) return null
        if (trimmed.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        if (!commandCharset.matches(trimmed)) return null
        return trimmed
    }

    /**
     * Hygiene for a first-party install SCRIPT: content trust comes from it
     * being a reviewed repo constant executed VISIBLY (sh -x traces every
     * line); this check guards transport — bounded, single NUL/CR-free
     * block, no backticks (command substitution goes through $( ) which is
     * at least greppable in review).
     */
    fun validateScript(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > 8192) return null
        if (trimmed.any { it == '\r' || it == '\u0000' || it == '`' }) return null
        return trimmed
    }

    fun validateAttribution(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > 200) return null
        if (trimmed.any { it == '"' || it == '`' || it == '\n' }) return null
        return trimmed
    }

    private fun npmLine(vararg packages: String): String =
        "command -v npm >/dev/null 2>&1 || apk add --no-cache nodejs npm; " +
            "npm install -g " + packages.joinToString(" ")

    private val specs: Map<String, ToolInstallSpec> = mapOf(
        "claude" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("@anthropic-ai/claude-code")),
        ),
        "opencode" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("opencode-ai")),
        ),
        "codex" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("@openai/codex")),
        ),
        "qwen" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("@qwen-code/qwen-code@latest")),
        ),
        "kilo" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("@kilocode/cli")),
        ),
        "cline" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("cline")),
        ),
        "hermes" to ToolInstallSpec(
            method = InstallMethod.SCRIPT,
            command = validateCommand(
                "command -v curl >/dev/null 2>&1 || apk add --no-cache curl xz git; " +
                    "export UV_LINK_MODE=copy; " +
                    "curl -fsSL " +
                    "https://raw.githubusercontent.com/NousResearch/hermes-agent/main/scripts/install.sh" +
                    " | bash",
            ),
        ),
        // Owner iteration 4b: the UNOFFICIAL community client — a Node
        // launcher (bin `zcode`) wrapping the OFFICIAL ZCode Desktop agent
        // runtime (kingsword09/zcode-cli; npm registry verified 2026-09-15:
        // bin zcode, engines node >=22.19). Not affiliated with Z.ai — the
        // attribution line says so in the install session.
        "zcode" to ToolInstallSpec(
            method = InstallMethod.NPM,
            command = validateCommand(npmLine("zcode-app-cli")),
            attribution = validateAttribution(
                "Unofficial client - wraps the official ZCode runtime - " +
                    "not affiliated with Z.ai",
            ),
        ),
        // Owner iteration 4b — ANTIGRAVITY INSTALLS AFTER ALL: the owner
        // runs `agy` on this very phone. ANTIGRAVITY-PLATFORM.md's "cannot
        // run" verdict (2026-09-04) predates the M6.0 dual-libc layer — the
        // rootfs now carries the REAL Debian loader at canonical paths
        // (docs/runtime/DUAL_LIBC.md), and the glibc arm64 binary is
        // exactly what that layer serves (the same doc's qemu ladder
        // proved the binary itself is valid under real glibc). The official
        // install.sh still 404s on its musl manifest, so this spec installs
        // DIRECTLY from the official release manifest: fetch, sha512-
        // verify, extract, install as `agy`, prove with --version.
        "agy" to ToolInstallSpec(
            method = InstallMethod.SCRIPT,
            script = validateScript(
                """set -e
                |command -v curl >/dev/null 2>&1 || apk add --no-cache curl tar
                |M=https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64.json
                |curl -fsSL "${'$'}M" -o /tmp/agy-manifest.json
                |U=${'$'}(sed -n 's/.*"url" *: *"\([^"]*\)".*/\1/p' /tmp/agy-manifest.json | head -n 1)
                |S=${'$'}(sed -n 's/.*"sha512" *: *"\([^"]*\)".*/\1/p' /tmp/agy-manifest.json | head -n 1)
                |[ -n "${'$'}U" ] || { echo "manifest: no url field"; exit 1; }
                |[ -n "${'$'}S" ] || { echo "manifest: no sha512 field"; exit 1; }
                |curl -fsSL "${'$'}U" -o /tmp/agy.tar.gz
                |echo "${'$'}S  /tmp/agy.tar.gz" | sha512sum -c -
                |R=/tmp/agy-install
                |rm -rf "${'$'}R"
                |mkdir -p "${'$'}R" /usr/local/bin
                |tar -xzf /tmp/agy.tar.gz -C "${'$'}R"
                |B=${'$'}(find "${'$'}R" -type f \( -name antigravity -o -name agy \) | head -n 1)
                |[ -n "${'$'}B" ] || { echo "archive: antigravity binary not found"; exit 1; }
                |install -m 0755 "${'$'}B" /usr/local/bin/agy
                |rm -rf "${'$'}R" /tmp/agy.tar.gz /tmp/agy-manifest.json
                |agy --version
                """.trimMargin(),
            ),
            attribution = validateAttribution(
                "Official Google CLI binary - direct manifest install - " +
                    "runs via the PocketShell glibc layer",
            ),
        ),
    )

    fun specFor(id: String): ToolInstallSpec? = specs[id]

    /** The covered ids — visible for the completeness pin test. */
    fun specsKeys(): Set<String> = specs.keys
}

/**
 * The one-click install chain: the same `sh -l -c "<line>; exec <shell>"`
 * delivery every launch uses, wrapped in an honest echo of the exact line
 * (the user must be able to read what ran in their guest) and a closing
 * instruction. Pure and test-pinned: single line, verbatim command
 * transport, trailing exec.
 */
fun guestInstallChain(
    displayName: String,
    installCommand: String,
    guestShell: String,
    attribution: String? = null,
): String {
    val attributionEcho = attribution?.let { "echo \"note: $it\"; " } ?: ""
    return "echo \"PocketShell · installing $displayName\"; " +
        attributionEcho +
        "echo \"\$ $installCommand\"; " +
        "$installCommand; " +
        "echo; echo \"Install step finished — if it succeeded, tap the $displayName tile again to launch.\"; " +
        "exec $guestShell -l"
}

/**
 * The SCRIPT install chain (multi-line first-party scripts): the script is
 * transported BASE64-ENCODED — keeping the delivered chain itself inside the
 * strict transport charset — decoded in the guest, and executed with
 * `sh -x`, which TRACES EVERY LINE the script runs. Visible execution is the
 * honesty contract: the user watches the whole script. [attribution] (when
 * present) is echoed BEFORE anything runs. Pure and test-pinned: the
 * decoded payload round-trips byte-for-byte.
 */
fun guestInstallScriptChain(
    displayName: String,
    installScript: String,
    guestShell: String,
    attribution: String? = null,
): String {
    val payload = java.util.Base64.getEncoder().withoutPadding().encodeToString(
        installScript.toByteArray(Charsets.UTF_8),
    )
    val attributionEcho = attribution?.let { "echo \"note: $it\"; " } ?: ""
    return "echo \"PocketShell · installing $displayName\"; " +
        attributionEcho +
        "printf %s $payload | base64 -d | sh -x; " +
        "echo; echo \"Install step finished — if it succeeded, tap the $displayName tile again to launch.\"; " +
        "exec $guestShell -l"
}

/** Decode side of [guestInstallScriptChain] — used by the round-trip pin test. */
fun decodeInstallScriptPayload(chain: String): String? {
    val marker = "printf %s "
    val start = chain.indexOf(marker) + marker.length
    val end = chain.indexOf(" | base64 -d", start)
    if (start < marker.length || end <= start) return null
    return String(java.util.Base64.getDecoder().decode(chain.substring(start, end)), Charsets.UTF_8)
}
