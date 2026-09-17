package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P10 — the DETECTOR's signal integration (pure [AgentRuntimeDetection]
 * .compute): accepted signals surface exactly once per generation, the
 * attention axis folds in file order, generation breaks reset the story,
 * and unaccepted signals never reach a consumer.
 */
class AgentSignalDetectionTest {

    private val anchor = AgentLaunchRecords.LaunchRecord(pid = 500, pgrp = 500, startTicks = 9000, agent = "claude")

    private fun session(recordPath: String? = "/host/record.jsonl") = AgentRuntimeDetection.EligibleAgentSession(
        sessionId = 1L,
        rootPid = 400,
        token = "claude",
        predetermined = LaunchIdentity.KnownAgent(launcherId = "claude", displayName = "Claude Code", command = "claude"),
        recordPath = recordPath,
    )

    private fun snapshot(vararg pids: Int): ProcfsSnapshot = ProcfsSnapshot(
        pids.map { ProcfsProcess(pid = it, ppid = if (it == 400) 1 else 400, pgrp = 400, state = 'S', startTime = if (it == 500) 9000 else it * 10L) } +
            ProcfsProcess(pid = 400, ppid = 1, pgrp = 400, state = 'S', startTime = 1L),
    )

    private fun signalLine(agent: String = "claude", kind: String, pid: Int = 500, start: Long = 9000, data: String = "") =
        "{\"t\":\"signal\",\"agent\":\"$agent\",\"kind\":\"$kind\",\"seq\":0,\"pid\":$pid,\"start\":$start,\"data\":\"$data\"}"

    private fun records(vararg lines: String) = AgentLaunchRecords.parse(
        "{\"t\":\"launch\",\"pid\":500,\"pgrp\":500,\"start\":9000,\"agent\":\"claude\"}\n" +
            lines.joinToString("\n"),
    )

    @Test
    fun `an accepted permission signal raises attention and is delivered once`() {
        val recs = records(signalLine(kind = "permission_request"))
        val obs1 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, obs1.attention)
        assertEquals(1, obs1.newSignals.size)
        assertEquals(AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST, obs1.newSignals.first().kind)
        assertEquals(1, obs1.signalCount)

        // The SAME records re-delivered: the tail is now empty (exactly once).
        val obs2 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = mapOf(1L to obs1),
            snapshot = snapshot(400, 500), nowMs = 3_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(0, obs2.newSignals.size)
        assertEquals(1, obs2.signalCount)
        assertEquals(AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, obs2.attention)
    }

    @Test
    fun `attention folds in append order - working clears a permission wait`() {
        val recs = records(
            signalLine(kind = "permission_request"),
            signalLine(kind = "working", data = "{\"tool_name\":\"Bash\"}"),
        )
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(AgentSignalBridge.AgentAttentionPhase.NONE, obs.attention)
        assertNull(obs.attentionSignal)
        assertEquals(2, obs.newSignals.size)
    }

    @Test
    fun `a later attention-bearing signal supersedes an earlier one`() {
        val recs = records(
            signalLine(kind = "attention", data = "{\"notification_type\":\"idle_prompt\"}"),
            signalLine(kind = "permission_request"),
        )
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, obs.attention)
    }

    @Test
    fun `a foreign signal is never surfaced - cross-session contamination is structural-impossible`() {
        // parent pid 999 is NOT the anchor and NOT in the session tree
        val recs = records(signalLine(kind = "turn_complete", pid = 999, start = 9990))
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(0, obs.newSignals.size)
        assertEquals(0, obs.signalCount)
    }

    @Test
    fun `a signal naming another agent is never surfaced`() {
        val recs = records(signalLine(agent = "codex", kind = "turn_complete"))
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(0, obs.newSignals.size)
    }

    @Test
    fun `a generation break resets the story - new anchor, signals redeliver`() {
        val recs1 = records(signalLine(kind = "working"))
        val obs1 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs1),
        )[1L]!!
        assertEquals(1, obs1.signalCount)

        // A NEW launch generation: same session, new anchor identity.
        val recs2 = AgentLaunchRecords.parse(
            "{\"t\":\"launch\",\"pid\":700,\"pgrp\":700,\"start\":424242,\"agent\":\"claude\"}\n" +
                signalLine(kind = "working", pid = 700, start = 424242),
        )
        val snap2 = ProcfsSnapshot(
            listOf(
                ProcfsProcess(pid = 400, ppid = 1, pgrp = 400, state = 'S', startTime = 1L),
                ProcfsProcess(pid = 700, ppid = 400, pgrp = 400, state = 'S', startTime = 424242L),
            ),
        )
        val obs2 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = mapOf(1L to obs1),
            snapshot = snap2, nowMs = 2_000, records = mapOf(1L to recs2),
        )[1L]!!
        assertEquals("700:424242", obs2.signalGeneration)
        // the new generation's signal delivers even though the old count was 1
        assertEquals(1, obs2.newSignals.size)
    }

    @Test
    fun `no snapshot keeps the attention story as last proven`() {
        val recs = records(signalLine(kind = "permission_request"))
        val obs1 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        val obs2 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = mapOf(1L to obs1),
            snapshot = null, nowMs = 2_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(AgentRuntimeState.UNKNOWN, obs2.state)
        assertEquals(AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, obs2.attention)
        assertEquals(0, obs2.newSignals.size) // nothing NEW is deliverable without a scan
    }

    @Test
    fun `attention rides an observation even when the runtime state did not change`() {
        // First tick: RUNNING with a working signal — the story starts.
        val recs = records(signalLine(kind = "working"))
        val obs1 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(1L to recs),
        )[1L]!!
        assertEquals(AgentRuntimeState.RUNNING, obs1.state)
        // Second tick: still RUNNING, a NEW signal arrives — the signal edge
        // must fire even though the four-state edge will dedup to nothing.
        val recs2 = records(
            signalLine(kind = "working"),
            signalLine(kind = "permission_request"),
        )
        val obs2 = AgentRuntimeDetection.compute(
            eligible = listOf(session()), previous = mapOf(1L to obs1),
            snapshot = snapshot(400, 500), nowMs = 3_000, records = mapOf(1L to recs2),
        )[1L]!!
        assertEquals(1, obs2.newSignals.size)
        assertEquals(AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, obs2.attention)
    }

    @Test
    fun `discovery sessions accept signals named by their own anchor`() {
        val plain = AgentRuntimeDetection.EligibleAgentSession(
            sessionId = 2L, rootPid = 400, token = "", discovery = true,
            recordPath = "/host/record2.jsonl",
        )
        val recs = records(signalLine(kind = "turn_complete"))
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(plain), previous = emptyMap(),
            snapshot = snapshot(400, 500), nowMs = 1_000, records = mapOf(2L to recs),
        )[2L]!!
        assertEquals(1, obs.newSignals.size)
        assertEquals("Claude Code", obs.agent?.displayName) // the anchor named the token; the registry names the agent
    }
}
