package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P10 — the agent-native signal layer's pure-model pins.
 *
 * Three groups, mirroring the P9 evidence discipline:
 *
 *  1. THE PARSER — the strict single signal shape; anything else is
 *     dropped (missing evidence is never upgraded to a signal).
 *  2. THE ACCEPTANCE GATE — the correlation core: a signal speaks for a
 *     session only when its recorded parent is the session's anchored
 *     agent (pid + birth stamp) or a live, matching process inside the
     * session's own correlation domain. Recycled pids, foreign pids,
 *     zombies, wrong tokens: all rejected, structurally.
 *  3. THE SEMANTICS — the attention axis: only what the agent's own
 *     payload proves, nothing wider.
 */
class AgentSignalBridgeTest {

    // ------------------------------------------------------------- 1. parser

    private fun signal(
        agent: String = "claude",
        kind: String = "permission_request",
        seq: Int = 1,
        pid: Int = 500,
        start: Long = 9000,
        data: String = "",
    ): String =
        "{\"t\":\"signal\",\"agent\":\"$agent\",\"kind\":\"$kind\",\"seq\":$seq,\"pid\":$pid,\"start\":$start,\"data\":\"$data\"}"

    @Test
    fun `a well-formed signal parses with all fields`() {
        val parsed = AgentSignalBridge.parseSignal(signal(seq = 7, pid = 501, start = 9001, data = "x"), 0)
        assertTrue(parsed != null)
        parsed!!
        assertEquals("claude", parsed.agent)
        assertEquals(AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST, parsed.kind)
        assertEquals(7, parsed.seq)
        assertEquals(501, parsed.parentPid)
        assertEquals(9001L, parsed.parentStartTicks)
        assertEquals("x", parsed.data)
        assertEquals(0, parsed.lineIndex)
    }

    @Test
    fun `every vocabulary token maps and round-trips`() {
        for (kind in AgentSignalBridge.AgentSignalKind.entries) {
            assertEquals(kind, AgentSignalBridge.AgentSignalKind.fromToken(kind.token))
        }
    }

    @Test
    fun `unknown kinds are dropped - never invented into a state`() {
        assertNull(AgentSignalBridge.parseSignal(signal(kind = "task_completed_success"), 0))
        assertNull(AgentSignalBridge.parseSignal(signal(kind = "DROP TABLE"), 0))
        assertNull(AgentSignalBridge.parseSignal(signal(kind = ""), 0))
    }

    @Test
    fun `malformed lines and foreign shapes are dropped`() {
        assertNull(AgentSignalBridge.parseSignal("", 0))
        assertNull(AgentSignalBridge.parseSignal("not json at all", 0))
        assertNull(AgentSignalBridge.parseSignal("{\"t\":\"launch\",\"pid\":1,\"pgrp\":1,\"start\":1,\"agent\":\"kilo\"}", 0))
        assertNull(AgentSignalBridge.parseSignal("{\"t\":\"signal\"", 0))
        assertNull(AgentSignalBridge.parseSignal(signal(agent = ""), 0))
        assertNull(AgentSignalBridge.parseSignal(signal(pid = -1), 0))
        assertNull(AgentSignalBridge.parseSignal(signal(start = -1), 0))
        assertNull(AgentSignalBridge.parseSignal(signal(seq = -3), 0))
        assertNull(AgentSignalBridge.parseSignal("{\"t\":\"signal\",\"agent\":\"claude\",\"kind\":\"working\"}", 0))
    }

    @Test
    fun `payload unescaping is left-to-right and single-pass`() {
        assertEquals("", AgentSignalBridge.unescape(""))
        assertEquals("plain", AgentSignalBridge.unescape("plain"))
        assertEquals("a\"b", AgentSignalBridge.unescape("a\\\"b"))
        assertEquals("a\\b", AgentSignalBridge.unescape("a\\\\b"))
        // \\\" decodes to \" — the backslash survives, the quote unescapes once
        assertEquals("a\\\"b", AgentSignalBridge.unescape("a\\\\\\\"b"))
        // unknown escapes keep both characters (forged syntax is data)
        assertEquals("a\\nb", AgentSignalBridge.unescape("a\\nb"))
    }

    @Test
    fun `a real Claude PermissionRequest payload round-trips through the emitter escaping`() {
        val original = "{\"session_id\":\"s1\",\"hook_event_name\":\"PermissionRequest\",\"tool_name\":\"Bash\"}"
        // what the emitter writes: backslashes and quotes escaped
        val escaped = original.replace("\\", "\\\\").replace("\"", "\\\"")
        val parsed = AgentSignalBridge.parseSignal(signal(data = escaped), 0)
        assertTrue(parsed != null)
        assertEquals(original, parsed!!.data)
        assertTrue(parsed.data.contains("\"tool_name\":\"Bash\""))
    }

    @Test
    fun `lineIndex rides through the parser for exactly-once consumption`() {
        val first = AgentSignalBridge.parseSignal(signal(seq = 1), 3)
        val second = AgentSignalBridge.parseSignal(signal(seq = 2), 4)
        assertEquals(3, first!!.lineIndex)
        assertEquals(4, second!!.lineIndex)
    }

    // -------------------------------------------------------- 2. acceptance

    private fun context(
        anchor: AgentLaunchRecords.LaunchRecord? =
            AgentLaunchRecords.LaunchRecord(pid = 500, pgrp = 500, startTicks = 9000, agent = "claude"),
        expectedToken: String = "claude",
        snapshot: ProcfsSnapshot? = null,
        correlated: Set<Int> = emptySet(),
    ): AgentSignalBridge.AcceptanceContext {
        val byPid = snapshot?.processes?.associateBy { it.pid }
        return AgentSignalBridge.AcceptanceContext(
            anchor = anchor,
            expectedAgentToken = expectedToken,
            byPid = byPid,
            correlatedPids = correlated,
        )
    }

    private fun proc(
        pid: Int, ppid: Int = 1, pgrp: Int = pid, state: Char = 'S',
        startTime: Long = 9000,
    ) = ProcfsProcess(pid = pid, ppid = ppid, pgrp = pgrp, state = state, startTime = startTime)

    @Test
    fun `the anchor arm accepts the launch's own agent identity`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 500, start = 9000), 0)!!
        assertTrue(AgentSignalBridge.accept(s, context()))
    }

    @Test
    fun `the anchor arm tolerates the measured proot birth-stamp skew - but nothing wider`() {
        // device-gate finding: two reads of the same process's field 22 can
        // differ by a few ticks under proot (measured: 2). Pid reuse cannot
        // happen inside this window — that is the whole point of the bound.
        val within = AgentSignalBridge.parseSignal(signal(pid = 500, start = 9002), 0)!!
        assertTrue(AgentSignalBridge.accept(within, context()))
        val tooFar = AgentSignalBridge.parseSignal(signal(pid = 500, start = 9000 + AgentRuntimeDetection.STARTTIME_SKEW_TICKS + 1), 0)!!
        assertFalse(AgentSignalBridge.accept(tooFar, context()))
    }

    @Test
    fun `the live-parent arm tolerates the same skew`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100 + 2), 0)!!
        val snap = ProcfsSnapshot(listOf(proc(pid = 610, startTime = 9100)))
        assertTrue(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = setOf(610))))
        val beyond = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100 + AgentRuntimeDetection.STARTTIME_SKEW_TICKS + 1), 0)!!
        assertFalse(AgentSignalBridge.accept(beyond, context(anchor = null, snapshot = snap, correlated = setOf(610))))
    }

    @Test
    fun `a recycled pid with a different birth stamp is rejected - the pid-reuse guard`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 500, start = 777777), 0)!!
        // no anchor, but a LIVE pid 500 with a DIFFERENT start in the tree
        val snap = ProcfsSnapshot(listOf(proc(pid = 500, startTime = 9000)))
        assertFalse(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = setOf(500))))
    }

    @Test
    fun `the live-parent arm accepts a matching process inside the session tree`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100), 0)!!
        val snap = ProcfsSnapshot(listOf(proc(pid = 500, startTime = 9000), proc(pid = 610, startTime = 9100)))
        assertTrue(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = setOf(610))))
    }

    @Test
    fun `a foreign process with a matching shape is rejected - cross-session isolation`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100), 0)!!
        val snap = ProcfsSnapshot(listOf(proc(pid = 610, startTime = 9100)))
        // the process exists and its birth stamp matches — but it is NOT in
        // THIS session's correlation domain
        assertFalse(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = emptySet())))
    }

    @Test
    fun `zombie parents are rejected - an exited process emits no truth`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100), 0)!!
        val snap = ProcfsSnapshot(listOf(proc(pid = 610, state = 'Z', startTime = 9100)))
        assertFalse(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = setOf(610))))
    }

    @Test
    fun `an unreadable birth stamp rejects - missing evidence is never a match`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100), 0)!!
        val snap = ProcfsSnapshot(listOf(ProcfsProcess(pid = 610, ppid = 1, pgrp = 610, state = 'S', startTime = null)))
        assertFalse(AgentSignalBridge.accept(s, context(anchor = null, snapshot = snap, correlated = setOf(610))))
    }

    @Test
    fun `a signal naming a different agent is rejected - identity binding`() {
        val s = AgentSignalBridge.parseSignal(signal(agent = "codex"), 0)!!
        assertFalse(AgentSignalBridge.accept(s, context(expectedToken = "claude")))
    }

    @Test
    fun `an unknown expected token rejects - no identity, no claim`() {
        val s = AgentSignalBridge.parseSignal(signal(), 0)!!
        assertFalse(AgentSignalBridge.accept(s, context(expectedToken = "")))
    }

    @Test
    fun `zero parent identity rejects - a signal without an origin proves nothing`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 0, start = 0), 0)!!
        assertFalse(AgentSignalBridge.accept(s, context()))
    }

    @Test
    fun `no anchor and no snapshot rejects - nothing to validate against`() {
        val s = AgentSignalBridge.parseSignal(signal(pid = 610, start = 9100), 0)!!
        assertFalse(AgentSignalBridge.accept(s, context(anchor = null, snapshot = null)))
    }

    // --------------------------------------------------------- 3. semantics

    @Test
    fun `permission_request proves the permission attention phase`() {
        val s = AgentSignalBridge.parseSignal(signal(kind = "permission_request"), 0)!!
        assertEquals(
            AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST,
            AgentSignalBridge.attentionPhase(s),
        )
    }

    @Test
    fun `attention payloads with permission_prompt prove permission - nothing wider`() {
        val s = AgentSignalBridge.parseSignal(
            signal(kind = "attention", data = "{\"notification_type\":\"permission_prompt\",\"message\":\"Claude needs your permission\"}"),
            0,
        )!!
        assertEquals(
            AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST,
            AgentSignalBridge.attentionPhase(s),
        )
    }

    @Test
    fun `attention payloads without a permission marker are input waits`() {
        val s = AgentSignalBridge.parseSignal(
            signal(kind = "attention", data = "{\"notification_type\":\"idle_prompt\"}"),
            0,
        )!!
        assertEquals(
            AgentSignalBridge.AgentAttentionPhase.INPUT_REQUIRED,
            AgentSignalBridge.attentionPhase(s),
        )
    }

    @Test
    fun `clearing kinds prove NO attention - withdrawal, never a new claim`() {
        for (kind in listOf("working", "turn_complete", "session_end", "session_start")) {
            val s = AgentSignalBridge.parseSignal(signal(kind = kind), 0)!!
            assertEquals(
                "kind $kind must clear",
                AgentSignalBridge.AgentAttentionPhase.NONE,
                AgentSignalBridge.attentionPhase(s),
            )
        }
    }
}
