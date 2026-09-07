package app.pocketshell.launchers

import kotlinx.serialization.Serializable

/**
 * M7.1 Phase 1 — Home launchers (the user's P1 brief).
 *
 * The one design rule this file encodes:
 *
 *   A launcher is not an app.
 *
 * A launcher is a small, honest convenience record: a name, a command or a
 * website, an optional icon, and a Home-visibility flag. Companions remain
 * simple websites launched through the EXISTING Companion implementation;
 * CLI tools remain commands launched through the EXISTING terminal/session
 * architecture (`TerminalViewModel.openCommandApp` / the new
 * `openCustomTool`). Nothing here launches anything, resolves packages, or
 * claims installation state — tap-time verification stays where it has
 * always lived (the guest-shell probe).
 *
 * This domain is PURE and JVM-test-pinned. The repositories and UI layers
 * around it stay thin.
 */

// ----------------------------------------------------------------- custom tools

/**
 * A user-defined CLI launcher. The command is USER CONFIGURATION — stored
 * verbatim (after hygiene validation) and handed to the existing launch
 * chain as data; this layer never parses or reinterprets it.
 */
@Serializable
data class CustomTool(
    val id: String,
    val name: String,
    val command: String,
)

object CustomToolValidation {

    const val MAX_NAME_LENGTH = 40
    const val MAX_COMMAND_LENGTH = 256

    /**
     * Trimmed display name or null (non-blank, length-capped) — the same
     * contract the Companion name validation has used since Phase 4.
     */
    fun validateName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return null
        return trimmed
    }

    /**
     * Hygiene-validated command line or null. This is NOT shell parsing:
     * the rules exist so one user field can never break the persisted JSON,
     * the DataStore record, or the single-line `sh -l -c` launch shape —
     * newlines/NULs would silently corrupt all three. Every other character
     * is the user's own configuration and travels verbatim.
     */
    fun validateCommand(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_COMMAND_LENGTH) return null
        if (trimmed.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        return trimmed
    }
}

/**
 * The command's head — the first whitespace-separated token, the name the
 * tap-time guest probe asks about (`npm install -g foo` probes `npm`).
 * Only meaningful for a VALIDATED command (never blank).
 */
fun CustomTool.commandHead(): String =
    command.trim().split(Regex("\\s+")).first()

// ------------------------------------------------- built-in companion seeds

/**
 * The preinstalled companion websites (P1 brief PART A: ChatGPT, Claude,
 * Z.ai, GitHub — the intended built-in set; there was never a three-slot
 * limit, only quick-add TEMPLATES, which stay untouched).
 *
 * Reconciliation with the M6 companion freeze: these seeds are injected
 * through the CompanionRepository's PUBLIC API as ordinary definitions with
 * FIXED ids (`builtin-*`), once per install — the frozen companion package
 * gains zero file changes, while Home/Settings treat them exactly like any
 * other definition (tabs, WebView pool, web data: all existing machinery).
 * Removing one from Home only HIDES it (the definition and its web data
 * survive); Settings can restore a deleted seed by its fixed id.
 */
object BuiltInCompanions {

    data class Seed(val id: String, val name: String, val host: String)

    val SEEDS: List<Seed> = listOf(
        Seed(id = "builtin-chatgpt", name = "ChatGPT", host = "chatgpt.com"),
        Seed(id = "builtin-claude", name = "Claude", host = "claude.ai"),
        Seed(id = "builtin-zai", name = "Z.ai", host = "z.ai"),
        Seed(id = "builtin-github", name = "GitHub", host = "github.com"),
    )

    val SEED_IDS: Set<String> = SEEDS.map { it.id }.toSet()
}

// ------------------------------------------------------------ launcher badges

/**
 * The deterministic text-badge fallback (P1 brief PART D). For each visible
 * launcher name, the SHORTEST meaningful prefix that avoids collisions
 * among the currently visible launchers — computed in display order, so
 * the examples from the brief reproduce exactly:
 *
 *   companions: ChatGPT→C, Claude→Cl, Z.ai→Z, GitHub→G
 *   tools:      Kilo→K, Cline→C, Hermes→H, Claude Code→Cl, Codex→Co
 *               (greedy in display order: Cline claims C first)
 *
 * Pure, index-aligned (duplicate display names each get their own slot),
 * and cheap enough to recompute on every recomposition — no icon engine,
 * no bitmaps, no state.
 */
object LauncherBadges {

    /**
     * @return one badge per input name, in input order. Names with no
     *   letters/digits at all degrade to "?" deterministically.
     */
    fun assign(names: List<String>): List<String> {
        val used = HashSet<String>()
        val badges = ArrayList<String>(names.size)
        for (name in names) {
            val letters = name.filter { it.isLetterOrDigit() }
            var badge = if (letters.isEmpty()) "?" else letters.take(1)
            // Extend only while the current prefix is taken. Case-insensitive
            // collisions ("claude" vs "Claude") must resolve the same way.
            var n = 1
            while (badge.uppercase() in used && n < letters.length) {
                n += 1
                badge = letters.take(n)
            }
            badges += badge.replaceFirstChar { it.uppercaseChar() }
            used += badge.uppercase()
        }
        return badges
    }
}

// ---------------------------------------------------- visible-launcher math

/** One "Your tools" grid entry, regardless of origin. */
sealed interface ToolLauncher {
    val id: String
    val label: String

    /** A registry command app (the honest tap-time probe still applies). */
    data class Builtin(val app: app.pocketshell.apps.CommandApp) : ToolLauncher {
        override val id: String = app.id
        override val label: String = app.displayName
    }

    /** A user-defined launcher (launched verbatim through the same chain). */
    data class Custom(val tool: CustomTool) : ToolLauncher {
        override val id: String = tool.id
        override val label: String = tool.name
    }
}

/** Home-visible companions: the full definitions minus the hidden ids. */
fun visibleCompanions(
    defs: List<app.pocketshell.companion.CompanionDef>,
    hiddenIds: Set<String>,
): List<app.pocketshell.companion.CompanionDef> =
    defs.filter { it.id !in hiddenIds }

/**
 * Home-visible tools: the built-in registry entries first (registry order),
 * then the user's custom tools, minus every hidden id. Hiding NEVER deletes:
 * built-ins stay in the registry, customs stay persisted — only the Home
 * grid omits them.
 */
fun visibleTools(
    registry: List<app.pocketshell.apps.CommandApp>,
    customTools: List<CustomTool>,
    hiddenIds: Set<String>,
): List<ToolLauncher> =
    registry.filter { it.id !in hiddenIds }.map { ToolLauncher.Builtin(it) } +
        customTools.filter { it.id !in hiddenIds }.map { ToolLauncher.Custom(it) }

/**
 * The one-shot built-in companion seed merge: existing definitions keep
 * their order and content untouched (a user-edited seed URL survives);
 * missing seeds append in seed order with their URLs normalized through
 * the EXISTING companion validation (fail-closed — an invalid seed would
 * be skipped, never persisted broken).
 */
fun mergeBuiltInCompanionSeeds(
    existing: List<app.pocketshell.companion.CompanionDef>,
): List<app.pocketshell.companion.CompanionDef> =
    existing + BuiltInCompanions.SEEDS
        .filter { seed -> existing.none { it.id == seed.id } }
        .mapNotNull { seed ->
            val url = app.pocketshell.companion.CompanionValidation.normalizeUrl(seed.host)
                ?: return@mapNotNull null
            app.pocketshell.companion.CompanionDef(id = seed.id, name = seed.name, url = url)
        }
