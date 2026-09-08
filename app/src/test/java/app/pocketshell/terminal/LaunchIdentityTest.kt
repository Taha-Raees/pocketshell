package app.pocketshell.terminal

import app.pocketshell.apps.CommandAppCatalog
import app.pocketshell.packages.CliAppCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P3a — the launch-identity truth boundary (the pure half of the
 * detection-matrix contract, docs/M7.2-P3A-DETECTION-MATRIX.md).
 *
 * What is pinned here is what PocketShell may honestly CLAIM about a
 * session's launch:
 *
 *   - every REGISTRY launcher classifies KnownAgent — the real 9-entry
 *     list, by its stable ids, resolved against the real registry objects;
 *   - every CATALOG launcher classifies KnownNonAgentTool — the real
 *     5-entry list; catalog tools are known NON-agents (nano is not an
 *     agent) and their launch evidence is preserved without promotion;
 *   - a CUSTOM tool is NEVER promoted — not by its name, not by its
 *     command, not even when its id or name collides with a known agent's;
 *   - plain shells claim NO launch identity at all (no agent identity is
 *     invented for them);
 *   - unresolvable ids degrade honestly to CustomOrUnknown (never to a
 *     false "known" claim);
 *   - classification is a PURE function of (origin, agent): identical
 *     inputs classify identically regardless of the session's lifecycle
 *     phase — spawn metadata carries NO runtime claim, so "the known
 *     launcher was spawned" can never silently become "the agent is
 *     confirmed running" (the false-running rule) and a session's exit
 *     status can never be re-attributed to the agent (the completion
 *     boundary — the type has no completion/exit field to corrupt).
 */
class LaunchIdentityTest {

    // ------------------------------------------------------------ the matrix

    private fun hint(displayName: String, command: String) = AgentHint(
        displayName = displayName,
        command = command,
        matchedBy = AgentMatchedBy.LAUNCH_METADATA,
    )

    /** The exact command each registry entry records (single-token argv joined). */
    private val expectedRegistryCommands = mapOf(
        "hermes" to "hermes",
        "opencode" to "opencode",
        "claude" to "claude",
        "zcode" to "zcode",
        "kilo" to "kilo",
        "cline" to "cline",
        "agy" to "agy",
        "codex" to "codex",
        "qwen" to "qwen",
    )

    @Test
    fun `every registry agent classifies as KnownAgent with the registry's own identity`() {
        for (app in CommandAppCatalog.registry) {
            val identity = LaunchIdentity.of(
                SpawnOrigin.CommandApp(app.id),
                hint(app.displayName, app.launchCommand.joinToString(" ")),
            )
            assertTrue(
                "registry launcher ${app.id} must classify KnownAgent",
                identity is LaunchIdentity.KnownAgent,
            )
            identity as LaunchIdentity.KnownAgent
            assertEquals(app.id, identity.launcherId)
            assertEquals(app.displayName, identity.displayName)
            assertEquals(expectedRegistryCommands[app.id], identity.command)
        }
    }

    @Test
    fun `the registry the classifier resolves against is the real 9-agent seed list`() {
        assertEquals(
            listOf("hermes", "opencode", "claude", "zcode", "kilo", "cline", "agy", "codex", "qwen"),
            CommandAppCatalog.registry.map { it.id },
        )
    }

    @Test
    fun `every catalog tool classifies as KnownNonAgentTool - known non-agents, evidence preserved`() {
        for (entry in CliAppCatalog.entries) {
            val identity = LaunchIdentity.of(
                SpawnOrigin.CatalogApp(entry.id),
                hint(entry.name, entry.launchCommand.joinToString(" ")),
            )
            assertTrue(
                "catalog tool ${entry.id} must classify KnownNonAgentTool (never an agent)",
                identity is LaunchIdentity.KnownNonAgentTool,
            )
            identity as LaunchIdentity.KnownNonAgentTool
            assertEquals(entry.id, identity.launcherId)
            assertEquals(entry.name, identity.displayName)
            assertEquals(entry.launchCommand.joinToString(" "), identity.command)
        }
    }

    @Test
    fun `the catalog the classifier resolves against is the real 5-entry non-agent list`() {
        assertEquals(
            listOf("nano", "htop", "vim", "git", "python3"),
            CliAppCatalog.entries.map { it.id },
        )
    }

    // ------------------------------------------------- the custom boundary

    @Test
    fun `a custom tool named like an agent is still CustomOrUnknown - the name is never evidence`() {
        val identity = LaunchIdentity.of(
            SpawnOrigin.CustomTool("tool-1"),
            hint("My Agent", "python foo.py"),
        )
        assertEquals(
            LaunchIdentity.CustomOrUnknown(
                launcherId = "tool-1",
                displayName = "My Agent",
                command = "python foo.py",
            ),
            identity,
        )
    }

    @Test
    fun `a custom tool whose id collides with a registry id is never promoted`() {
        val identity = LaunchIdentity.of(
            SpawnOrigin.CustomTool("claude"),
            hint("Claude Code", "claude"),
        )
        assertTrue(
            "a custom-tool origin must never resolve into the registry",
            identity is LaunchIdentity.CustomOrUnknown,
        )
    }

    @Test
    fun `a custom tool whose command head is a known agent binary is never promoted`() {
        val identity = LaunchIdentity.of(
            SpawnOrigin.CustomTool("tool-9"),
            hint("wrapper", "kilo --resume"),
        )
        assertTrue(identity is LaunchIdentity.CustomOrUnknown)
        assertEquals("kilo --resume", (identity as LaunchIdentity.CustomOrUnknown).command)
    }

    @Test
    fun `a custom tool with a null hint degrades to the raw id without inventing a name`() {
        val identity = LaunchIdentity.of(SpawnOrigin.CustomTool("tool-2"), null)
        assertEquals(
            LaunchIdentity.CustomOrUnknown(launcherId = "tool-2", displayName = "tool-2", command = ""),
            identity,
        )
    }

    // ------------------------------------------------------- plain shells

    @Test
    fun `plain shells claim no launch identity at all`() {
        assertNull(LaunchIdentity.of(SpawnOrigin.Shell, null))
        assertNull(LaunchIdentity.of(SpawnOrigin.LinuxShell, null))
        assertNull(LaunchIdentity.of(SpawnOrigin.FilesTerminal, null))
    }

    // -------------------------------------------------- honest degradation

    @Test
    fun `an unresolvable registry id degrades to CustomOrUnknown instead of a false known claim`() {
        val identity = LaunchIdentity.of(
            SpawnOrigin.CommandApp("no-such-agent"),
            hint("Ghost", "ghost"),
        )
        assertEquals(
            LaunchIdentity.CustomOrUnknown(launcherId = "no-such-agent", displayName = "Ghost", command = "ghost"),
            identity,
        )
    }

    @Test
    fun `an unresolvable catalog id degrades to CustomOrUnknown as well`() {
        val identity = LaunchIdentity.of(SpawnOrigin.CatalogApp("deleted-tool"), null)
        assertEquals(
            LaunchIdentity.CustomOrUnknown(launcherId = "deleted-tool", displayName = "deleted-tool", command = ""),
            identity,
        )
    }

    // ------------------------------------- purity / the false-running rule

    @Test
    fun `classification is independent of the lifecycle phase - spawn metadata carries no runtime claim`() {
        // The SAME (origin, hint) pair must classify identically no matter
        // when it is asked: before the fork (STARTING), while the direct
        // child runs (RUNNING) or after it exited (FINISHED). The identity
        // sees neither the phase nor any exit status — a KnownAgent launch
        // can never silently read as "confirmed running".
        val origin = SpawnOrigin.CommandApp("kilo")
        val kiloHint = hint("Kilo Code", "kilo")
        val beforeFork = LaunchIdentity.of(origin, kiloHint)
        val afterFork = LaunchIdentity.of(origin, kiloHint)
        val afterExit = LaunchIdentity.of(origin, kiloHint)
        assertEquals(beforeFork, afterFork)
        assertEquals(beforeFork, afterExit)
        assertTrue(beforeFork is LaunchIdentity.KnownAgent)
    }

    @Test
    fun `the identity type carries no exit or completion representation`() {
        // The completion boundary in TYPE form: a LaunchIdentity has no
        // field that could hold an exit status or a completion claim, so
        // the manager's waitpid-proven ExitStatus can never be re-attributed
        // to an agent through this model.
        val identity = LaunchIdentity.of(SpawnOrigin.CommandApp("claude"), null)!!
        assertTrue(identity is LaunchIdentity.KnownAgent)
        val fields = identity.javaClass.declaredFields.map { it.name }.toSet()
        assertTrue(fields.containsAll(listOf("launcherId", "displayName", "command")))
        assertTrue(
            "no exit/completion/running field may exist on a launch identity: $fields",
            fields.none { it.lowercase().contains("exit") || it.lowercase().contains("running") || it.lowercase().contains("complet") },
        )
    }

    @Test
    fun `identity equality is value equality per kind`() {
        assertEquals(
            LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
            LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
        )
        assertNotEquals(
            LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
            LaunchIdentity.CustomOrUnknown("kilo", "Kilo Code", "kilo"),
        )
        assertNotEquals(
            LaunchIdentity.KnownNonAgentTool("nano", "Nano", "nano"),
            LaunchIdentity.KnownAgent("nano", "Nano", "nano"),
        )
    }
}
