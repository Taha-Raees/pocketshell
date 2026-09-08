package app.pocketshell.terminal

import app.pocketshell.apps.CommandAppCatalog
import app.pocketshell.packages.CliAppCatalog

/**
 * M7.2 P3a — the truthful classification of a session's LAUNCH identity
 * (docs/M7.2-P3A-DETECTION-MATRIX.md): what PocketShell actually knows about
 * WHAT a session was launched as, resolved from the REAL registries — never
 * from names, never inferred, never upgraded by runtime guessing.
 *
 * THE THREE STATEMENTS (the milestone's core safety rule — they are NOT
 * interchangeable, and this type deliberately represents only the first):
 *
 *   1. "PocketShell launched X."          — PROVEN at spawn: the typed
 *      [SpawnOrigin] + [AgentHint] recorded before the first byte flows
 *      (P2), classified here against the real launcher registries.
 *   2. "X is currently running."          — NOT representable in P3a. The
 *      direct child is proot (audit §1.3); the agent binary is a descendant
 *      no session-layer signal can see, the fork signal fires BEFORE the
 *      guest shell has even executed the command line, and the
 *      `<command>; exec <shell>` launch chain keeps the direct child alive
 *      AFTER the agent exits. The only running-truth in the architecture is
 *      [SessionPhase] on the direct child — a SESSION fact, never an agent
 *      fact. (P3b's procfs scanner may add graded evidence later, through
 *      the manager — never through this type.)
 *   3. "X completed."                     — NOT representable in P3a. The
 *      agent's own exit status is consumed and discarded by the
 *      intermediate `sh -l -c` shell (no `$?` capture exists in the chain);
 *      a session's FINISHED + [ExitStatus] is the DIRECT CHILD's exit (the
 *      whole guest session ending), which is NOT the agent's completion and
 *      must never be re-labeled as one.
 *
 * Classification is a PURE function of `(origin, agent)`: it depends on
 * nothing else — not the lifecycle phase, not the clock, not the terminal
 * output. Identical inputs always classify identically, whether the session
 * is STARTING, RUNNING or FINISHED (launch identity is spawn-time truth; it
 * does not change and carries no runtime claim).
 *
 * Ownership (PART H): this type adds METADATA classification only.
 * [TerminalSessionManager] remains the ONE lifecycle authority; nothing
 * here stores state, mutates entries, or creates a parallel lifecycle. The
 * registries consulted below are the app's real, immutable seed lists —
 * membership there IS the honest definition of "known".
 */
sealed class LaunchIdentity {

    /** The launcher's stable id (registry id / catalog id / custom-tool id). */
    abstract val launcherId: String

    /** The display name of the launcher (registry-defined for resolved kinds; the user's own text for custom tools). */
    abstract val displayName: String

    /** The launch command the launcher requested (registry argv joined; the user's verbatim line for custom tools). */
    abstract val command: String

    /**
     * A session launched through a REGISTRY command app — the project's
     * known supported agent-launcher set (`CommandAppCatalog.registry`:
     * hermes, opencode, claude, zcode, kilo, cline, agy, codex, qwen).
     *
     * This classifies the LAUNCH, exactly as P0 PART D Case A describes:
     * "this session was launched by tapping Kilo Code" — a real, proven
     * fact. It does NOT say the agent is running (see the class KDoc: no
     * runtime evidence exists at this layer).
     */
    data class KnownAgent(
        override val launcherId: String,
        override val displayName: String,
        override val command: String,
    ) : LaunchIdentity()

    /**
     * A session launched through a CLI-catalog app — a KNOWN NON-AGENT tool
     * (`CliAppCatalog.entries`: nano, htop, vim, git, python3). The P2 hint
     * records the launch identity for every named launcher; this class
     * keeps the model honest that catalog tools are editors, viewers and
     * runtimes — never agents — while preserving the same launch evidence.
     */
    data class KnownNonAgentTool(
        override val launcherId: String,
        override val displayName: String,
        override val command: String,
    ) : LaunchIdentity()

    /**
     * A session launched through a USER-DEFINED custom tool — or any launch
     * whose id no longer resolves against its registry (honest degradation:
     * "unknown" is the only claim left). A custom launcher is user
     * configuration: its NAME is never evidence ("My Agent" does not make
     * `python foo.py` an agent), its command head is a launch fact only,
     * and NO registry membership is implied even when the id or the name
     * collides with a known agent's.
     */
    data class CustomOrUnknown(
        override val launcherId: String,
        override val displayName: String,
        override val command: String,
    ) : LaunchIdentity()

    companion object {

        /**
         * Classify a session's launch identity from its stored spawn
         * metadata, against the REAL registries.
         *
         * Returns `null` exactly for the plain-shell origins — no launcher
         * named any command for them, so no launch identity (and no agent
         * identity) is claimed.
         *
         * Resolution rules, in order:
         *   - [SpawnOrigin.CommandApp] resolves against the registry;
         *     success → [KnownAgent] carrying the REGISTRY's authoritative
         *     display name and command (the registry, not the hint, defines
         *     what a known launcher is). A stale/unresolvable id degrades
         *     honestly to [CustomOrUnknown] (the SpawnOrigin still records
         *     which path spawned the session).
         *   - [SpawnOrigin.CatalogApp] resolves against the catalog the
         *     same way → [KnownNonAgentTool], or degrades to
         *     [CustomOrUnknown].
         *   - [SpawnOrigin.CustomTool] is NEVER resolved against any
         *     registry — user-defined launchers stay [CustomOrUnknown]
         *     unconditionally.
         *   - Shell / LinuxShell / FilesTerminal → `null`.
         *
         * `agent` (the P2 hint) supplies the recorded name/command only
         * where no registry defines them (custom tools, degradation); every
         * caller passes what the spawn recorded, so a null hint degrades to
         * the raw id / empty command instead of inventing a display name.
         */
        fun of(origin: SpawnOrigin, agent: AgentHint?): LaunchIdentity? =
            when (origin) {
                is SpawnOrigin.CommandApp ->
                    when (val app = CommandAppCatalog.byId(origin.appId)) {
                        null -> CustomOrUnknown(
                            launcherId = origin.appId,
                            displayName = agent?.displayName ?: origin.appId,
                            command = agent?.command ?: "",
                        )
                        else -> KnownAgent(
                            launcherId = app.id,
                            displayName = app.displayName,
                            command = app.launchCommand.joinToString(" "),
                        )
                    }

                is SpawnOrigin.CatalogApp ->
                    when (val entry = CliAppCatalog.byId(origin.entryId)) {
                        null -> CustomOrUnknown(
                            launcherId = origin.entryId,
                            displayName = agent?.displayName ?: origin.entryId,
                            command = agent?.command ?: "",
                        )
                        else -> KnownNonAgentTool(
                            launcherId = entry.id,
                            displayName = entry.name,
                            command = entry.launchCommand.joinToString(" "),
                        )
                    }

                // A custom tool is user configuration: never probed against
                // any registry, never promoted by its name (PART D truth
                // boundary). Its id/name/command are recorded as-is.
                is SpawnOrigin.CustomTool -> CustomOrUnknown(
                    launcherId = origin.toolId,
                    displayName = agent?.displayName ?: origin.toolId,
                    command = agent?.command ?: "",
                )

                SpawnOrigin.Shell, SpawnOrigin.LinuxShell, SpawnOrigin.FilesTerminal -> null
            }
    }
}
