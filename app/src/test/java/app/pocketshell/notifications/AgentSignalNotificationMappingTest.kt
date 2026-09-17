package app.pocketshell.notifications

import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentRuntimeState
import app.pocketshell.terminal.AgentSignalBridge
import app.pocketshell.terminal.ExitStatus
import app.pocketshell.terminal.LaunchIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P10 — the attention axis of the notification TRUTH CONTRACT.
 *
 * The wording rules (the documented P10 revision of the P7 ban lists):
 * an attention surface may say EXACTLY what the accepted agent signal
 * proves — "requesting permission" for permission evidence, "needs your
 * input" for input evidence, "ended its turn" for turn evidence — and
 * NEVER any completion/success/failure claim. A withdrawal cancels; it
 * never replaces. All PURE.
 */
class AgentSignalNotificationMappingTest {

    private fun agent(name: String = "Claude Code") =
        LaunchIdentity.KnownAgent(launcherId = "claude", displayName = name, command = "claude")

    private fun signal(data: String = "", kind: AgentSignalBridge.AgentSignalKind = AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST) =
        AgentSignalBridge.SignalRecord(
            agent = "claude", kind = kind, seq = 1,
            parentPid = 500, parentStartTicks = 9000, data = data, lineIndex = 0,
        )

    private fun raised(
        sessionId: Long = 1L,
        attention: AgentSignalBridge.AgentAttentionPhase = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST,
        data: String = "",
    ): AgentRuntimeEvent.AgentAttentionRaised =
        AgentRuntimeEvent.AgentAttentionRaised(sessionId, agent(), attention, signal(data), 5_000L)

    // ------------------------------------------------------------ raising

    @Test
    fun `a permission signal posts the narrow permission wording`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            raised(data = "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"rm -rf /tmp/x\"}}"),
        )
        assertTrue(action is AgentRuntimeNotificationMapping.Action.ShowAttention)
        action as AgentRuntimeNotificationMapping.Action.ShowAttention
        assertEquals("Claude Code is requesting permission", action.title)
        assertEquals(
            "The agent asked for your approval to use Bash in terminal session 1",
            action.text,
        )
        assertEquals(
            AgentRuntimeNotificationMapping.AttentionPosted.PERMISSION,
            memory.attentionPosted[1L],
        )
    }

    @Test
    fun `a permission signal without tool metadata degrades to the generic true line`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            raised(data = "{}"),
        )
        action as AgentRuntimeNotificationMapping.Action.ShowAttention
        assertEquals("Claude Code is requesting permission", action.title)
        assertEquals("The agent asked for your approval in terminal session 1", action.text)
    }

    @Test
    fun `an input signal says needs your input - exactly the proven claim`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            raised(attention = AgentSignalBridge.AgentAttentionPhase.INPUT_REQUIRED),
        )
        action as AgentRuntimeNotificationMapping.Action.ShowAttention
        assertEquals("Claude Code needs your input", action.title)
    }

    @Test
    fun `a turn signal posts the turn surface - ended its turn, never finished`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            AgentRuntimeEvent.AgentTurnSignalled(
                1L, agent(),
                signal(kind = AgentSignalBridge.AgentSignalKind.TURN_COMPLETE), 5_000L,
            ),
        )
        assertTrue(action is AgentRuntimeNotificationMapping.Action.ShowAttention)
        action as AgentRuntimeNotificationMapping.Action.ShowAttention
        assertEquals("Claude Code ended its turn", action.title)
        assertEquals(
            AgentRuntimeNotificationMapping.AttentionPosted.TURN,
            memory.attentionPosted[1L],
        )
    }

    // ---------------------------------------------------------- withdrawing

    @Test
    fun `a cleared attention cancels the attention surface - no replacement`() {
        val (memoryAfterRaise, _) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            raised(),
        )
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            memoryAfterRaise,
            AgentRuntimeEvent.AgentAttentionCleared(
                1L, agent(),
                AgentRuntimeEvent.AgentAttentionCleared.Cause.RESUMED_WORK, 6_000L,
            ),
        )
        assertTrue(action is AgentRuntimeNotificationMapping.Action.CancelAttention)
        assertFalse(memory.attentionPosted.containsKey(1L))
    }

    @Test
    fun `a cleared attention with no posted surface is a no-op`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            AgentRuntimeEvent.AgentAttentionCleared(
                7L, agent(),
                AgentRuntimeEvent.AgentAttentionCleared.Cause.RESUMED_WORK, 6_000L,
            ),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
    }

    @Test
    fun `a session tombstone clears the attention record - late events cannot resurrect it`() {
        val (memoryAfterRaise, _) = AgentRuntimeNotificationMapping.reduce(
            AgentRuntimeNotificationMapping.Memory(),
            raised(),
        )
        val (memory, _) = AgentRuntimeNotificationMapping.reduce(
            memoryAfterRaise,
            AgentRuntimeEvent.SessionEnded(
                1L, agent(),
                AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
                ExitStatus.Exited(0), AgentRuntimeState.RUNNING, 7_000L,
            ),
        )
        assertFalse(memory.attentionPosted.containsKey(1L))
        // and a late attention event for the ended session does nothing
        val (_, late) = AgentRuntimeNotificationMapping.reduce(
            memory,
            raised(),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.None, late)
    }

    // -------------------------------------------------------- the two axes

    @Test
    fun `the attention surface coexists with the runtime surface - two truths, two ids`() {
        val memory = AgentRuntimeNotificationMapping.Memory(
            posted = mapOf(1L to AgentRuntimeNotificationMapping.PostedKind.RUNNING),
        )
        val (next, action) = AgentRuntimeNotificationMapping.reduce(memory, raised())
        assertTrue(action is AgentRuntimeNotificationMapping.Action.ShowAttention)
        // the runtime surface was NOT touched
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            next.posted[1L],
        )
    }

    @Test
    fun `attention ids live on their own space and can never collide with runtime ids`() {
        val runtimeId = NotificationIds.agentRuntime(1L)
        val attentionId = NotificationIds.agentAttention(1L)
        assertFalse(runtimeId == attentionId)
        assertEquals(NotificationIds.AGENT_ATTENTION_BASE + 1, attentionId)
    }

    // ------------------------------------------------- the wording boundary

    @Test
    fun `no attention wording claims completion success or failure`() {
        val wordings = listOf(
            AgentRuntimeNotificationMapping.attentionTitle("X", AgentRuntimeNotificationMapping.AttentionPosted.PERMISSION),
            AgentRuntimeNotificationMapping.attentionTitle("X", AgentRuntimeNotificationMapping.AttentionPosted.INPUT),
            AgentRuntimeNotificationMapping.attentionTitle("X", AgentRuntimeNotificationMapping.AttentionPosted.TURN),
            AgentRuntimeNotificationMapping.turnTitle("X"),
            AgentRuntimeNotificationMapping.turnText(3L),
        )
        for (wording in wordings) {
            for (banned in listOf("complet", "success", "succeed", "finished", "failed", "failed")) {
                assertFalse(
                    "attention wording must never claim '$banned' (got: \"$wording\")",
                    wording.lowercase().contains(banned),
                )
            }
        }
    }

    @Test
    fun `the permission wording is the narrow claim P7 mandated - never a generic needs-input`() {
        val title = AgentRuntimeNotificationMapping.attentionTitle(
            "X",
            AgentRuntimeNotificationMapping.AttentionPosted.PERMISSION,
        )
        assertEquals("X is requesting permission", title)
    }
}
