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
        // honest absence: no adapter without a verified mechanism
        assertNull(AgentSignalAdapters.forToken("zcode"))
        assertNull(AgentSignalAdapters.forToken("kilo"))
        assertNull(AgentSignalAdapters.forToken("hermes"))
        assertNull(AgentSignalAdapters.forToken("agy"))
        assertNull(AgentSignalAdapters.forToken("qwen"))
        assertNull(AgentSignalAdapters.forToken("cline"))
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
    fun `the Codex adapter stages a notify-only config - no trust-gated hooks`() {
        val files = AgentSignalAdapters.forToken("codex")!!
            .stagedFiles(staging, record, emit)
        assertEquals(1, files.size)
        assertEquals("codex/config.toml", files[0].relativePath)
        val config = files[0].content
        assertTrue(config.contains("notify = [\"sh\", \"$emit\", \"codex\", \"turn_complete\", \"$record\"]"))
        // the trust gate is exactly why hooks are NOT armed by this adapter
        assertFalse(config.contains("hooks"))
    }

    @Test
    fun `the Codex anchor isolates CODEX_HOME and the prep symlinks real auth`() {
        val adapter = AgentSignalAdapters.forToken("codex")!!
        assertEquals("CODEX_HOME=$staging/codex codex", adapter.anchorCommand(staging))
        val prep = adapter.prepSnippet(staging)!!
        assertTrue(prep.startsWith("ln -sf"))
        assertTrue(prep.contains(".codex/auth.json"))
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
        assertEquals("XDG_CONFIG_HOME=$staging/xdg-config opencode", adapter.anchorCommand(staging))
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
