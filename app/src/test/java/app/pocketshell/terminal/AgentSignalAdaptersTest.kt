package app.pocketshell.terminal

import app.pocketshell.apps.guestLaunchChainWithRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P10 — the STAGING adapters (pure): each proven adapter writes the
 * config that points the agent's OWN notification mechanism at the staged
 * emitter and the session's record file, and composes the anchor command
 * that makes the agent READ that config. The chain composition carries
 * prep + override without changing the P9 anchor/exit contract.
 */
class AgentSignalAdaptersTest {

    private val staging = "/var/lib/pocketshell-agent/p9deadbeef.d"
    private val record = "/var/lib/pocketshell-agent/p9deadbeef.jsonl"
    private val emit = "/var/lib/pocketshell-agent/p9deadbeef.d/emit.sh"

    // ---------------------------------------------------------- the registry

    @Test
    fun `adapters exist exactly for the proven mechanisms`() {
        assertNotNull(AgentSignalAdapters.forToken("claude"))
        assertNotNull(AgentSignalAdapters.forToken("codex"))
        assertNotNull(AgentSignalAdapters.forToken("opencode"))
        // M7.2 P10 (device round): the unofficial CLI's hooks mechanism is
        // verified in the shipped engine + official docs; staging ships,
        // live-fire is the §63 D15 device-gate step.
        assertNotNull(AgentSignalAdapters.forToken("zcode"))
        // honest absence: no adapter without a verified mechanism
        assertNull(AgentSignalAdapters.forToken("kilo"))
        assertNull(AgentSignalAdapters.forToken("hermes"))
        assertNull(AgentSignalAdapters.forToken("agy"))
        assertNull(AgentSignalAdapters.forToken("qwen"))
        assertNull(AgentSignalAdapters.forToken("cline"))
    }

    @Test
    fun `the ZCode adapter stages HOME-isolated hooks with storage pointed back at the real home`() {
        val adapter = AgentSignalAdapters.forToken("zcode")!!
        val files = adapter.stagedFiles(staging, record, emit)
        assertEquals(1, files.size)
        assertEquals("home/.zcode/cli/config.json", files[0].relativePath)
        val config = files[0].content
        // the user's login/credentials/model state stay shared via storage.dir
        assertTrue(config.contains("\"storage\":{\"dir\":\"/root/.zcode\"}"))
        assertTrue(config.contains("\"enabled\":true"))
        // the proven event vocabulary, mapped to the bridge kinds
        for (pair in listOf(
            "SessionStart" to "session_start",
            "UserPromptSubmit" to "working",
            "PreToolUse" to "working",
            "PermissionRequest" to "permission_request",
            "PostToolUse" to "working",
            "Stop" to "turn_complete",
        )) {
            val command = "sh $emit zcode ${pair.second} $record"
            assertTrue(
                "hook ${pair.first} must invoke the emitter with kind ${pair.second}",
                config.contains("\"${pair.first}\":[{\"hooks\":[{\"type\":\"command\",\"command\":\"$command\"}]}]"),
            )
        }
        // PostToolUseFailure is a tool failure, not a bridge kind — not armed
        assertTrue(!config.contains("PostToolUseFailure"))
        // the anchor redirects HOME only; storage.dir keeps state persistent
        assertEquals("env HOME=$staging/home zcode", adapter.anchorCommand(staging))
    }

    // --------------------------------------------------------- Claude Code

    @Test
    fun `the Claude adapter stages merge-only hook settings for every bridged event`() {
        val files = AgentSignalAdapters.forToken("claude")!!
            .stagedFiles(staging, record, emit)
        assertEquals(1, files.size)
        val settings = files[0].content
        val expected = listOf(
            "SessionStart" to "session_start",
            "UserPromptSubmit" to "working",
            "PreToolUse" to "working",
            "PostToolUse" to "working",
            "PermissionRequest" to "permission_request",
            "Notification" to "attention",
            "Stop" to "turn_complete",
            "SessionEnd" to "session_end",
        )
        for ((hookEvent, kind) in expected) {
            val command = "sh $emit claude $kind $record"
            assertTrue(
                "hook $hookEvent must invoke the emitter with kind $kind",
                settings.contains("\"$hookEvent\":[{\"hooks\":[{\"type\":\"command\",\"command\":\"$command\"}]}]"),
            )
        }
    }

    @Test
    fun `the Claude anchor command points --settings at the staged file - non-invasive merge`() {
        val anchor = AgentSignalAdapters.forToken("claude")!!.anchorCommand(staging)
        assertEquals("claude --settings $staging/claude-settings.json", anchor)
        // the user's own config dir is never redirected (auth preserved)
        assertFalse(anchor.contains("CLAUDE_CONFIG_DIR"))
    }

    // ---------------------------------------------------------------- Codex

    @Test
    fun `the Codex adapter launches the real home and arms notify through the user's own config`() {
        val adapter = AgentSignalAdapters.forToken("codex")!!
        // no CODEX_HOME staging: trust/auth/model state persist across launches
        assertTrue(adapter.stagedFiles(staging, record, emit).isEmpty())
        assertEquals("codex", adapter.anchorCommand(staging))
        val prep = adapter.prepSnippet(staging)!!
        // one-time, idempotent append; respects an existing user notify
        assertTrue(prep.contains("grep -q"))
        assertTrue(prep.contains("notify = [\"/var/lib/pocketshell-agent/codex-notify\"]"))
        // hooks.json lands in the real home only when the user has none —
        // the TUI's one-time trust dialog then arms the hook path
        assertTrue(prep.contains("hooks.json"))
        assertTrue(prep.contains("codex-hooks.json"))
        assertTrue(prep.endsWith("|| :"))
    }

    // -------------------------------------------------------------- OpenCode

    @Test
    fun `the OpenCode adapter stages a plugin wired to the record file and declares copy-through`() {
        val adapter = AgentSignalAdapters.forToken("opencode")!!
        val files = adapter.stagedFiles(staging, record, emit)
        assertEquals(1, files.size)
        assertEquals("xdg-config/opencode/plugin/pocketshell-bridge.js", files[0].relativePath)
        val plugin = files[0].content
        assertTrue(plugin.contains("export const PocketShellBridge"))
        assertTrue(plugin.contains(record))
        // the proven event vocabulary of the plugin bridge
        for (event in listOf("session.idle", "permission.asked", "permission.replied", "session.status", "session.created", "session.error")) {
            assertTrue("plugin must bridge $event", plugin.contains("\"$event\""))
        }
        // the staged config dir redirect + the user's global config survives
        assertEquals("env XDG_CONFIG_HOME=$staging/xdg-config opencode", adapter.anchorCommand(staging))
        assertEquals(listOf("root/.config/opencode"), adapter.copyThroughGuestDirs)
    }

    // ------------------------------------------------------------ the chain

    @Test
    fun `the chain carries prep before the anchor and execs the adapter command inside it`() {
        val chain = guestLaunchChainWithRecords(
            launchCommand = listOf("claude"),
            guestShell = "/bin/sh",
            guestRecordFile = record,
            agentCommandOverride = "claude --settings $staging/claude-settings.json",
            prepSnippet = "mkdir -p $staging || :",
        )
        // prep runs FIRST (before the anchor's sh -c)
        val anchorStart = chain.indexOf("sh -c '")
        assertTrue(chain.startsWith("mkdir -p $staging || : ; sh -c '"))
        assertTrue(anchorStart > 0)
        // the OVERRIDE command is what the anchor execs (the agent reads its staged config)
        assertTrue(chain.contains("exec claude --settings $staging/claude-settings.json'"))
        // the P9 contract is intact: anchor -> exit record -> login shell
        // (the chain is shell source — the JSON quotes carry backslashes)
        assertTrue(chain.contains("\\\"t\\\":\\\"launch\\\""))
        assertTrue(chain.contains("\\\"t\\\":\\\"exit\\\""))
        assertTrue(chain.endsWith("exec /bin/sh -l"))
    }

    @Test
    fun `without an adapter the chain is byte-identical to the P9 form`() {
        val withNulls = guestLaunchChainWithRecords(
            launchCommand = listOf("kilo"),
            guestShell = "/bin/sh",
            guestRecordFile = record,
            agentCommandOverride = null,
            prepSnippet = null,
        )
        val plain = guestLaunchChainWithRecords(
            launchCommand = listOf("kilo"),
            guestShell = "/bin/sh",
            guestRecordFile = record,
        )
        assertEquals(plain, withNulls)
    }

    @Test
    fun `no staged path can escape the staging directory`() {
        for (token in listOf("claude", "codex", "opencode")) {
            val adapter = AgentSignalAdapters.forToken(token)!!
            for (file in adapter.stagedFiles(staging, record, emit)) {
                assertFalse(file.relativePath.contains(".."))
                assertFalse(file.relativePath.startsWith("/"))
            }
        }
    }
}
