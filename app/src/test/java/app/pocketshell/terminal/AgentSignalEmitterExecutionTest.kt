package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M7.2 P10 — the EMITTER EXECUTION fixture (real /bin/sh on the test
 * JVM's Linux, the same technique as the P9 launch-chain fixture).
 *
 * The REAL staged emitter asset (src/main/assets/agentbridge/ps-emit.sh)
 * runs as a real process against a real record file and real /proc:
 *
 *  - the appended line PARSES through the strict [AgentSignalBridge]
 *     parser (the emitter can never corrupt the channel);
 *  - the recorded parent identity (pid + stat-22 birth stamp) is EXACTLY
 *     the invoking shell's live /proc identity — the correlation truth
 *     the Android acceptance gate consumes;
 *  - payloads arrive through BOTH transports (stdin like Claude Code
 *     hooks, argv like Codex notify) and hostile payloads (quotes,
 *     backslashes, newlines, control bytes, overlong) still yield valid,
 *     bounded records;
 *  - the generation guard: a missing record file produces NOTHING (a
 *     dead generation cannot receive signals) and never fails the agent.
 *
 * This is the deterministic fake-agent harness: any suite can simulate a
 * full agent story (launch -> working -> permission -> working -> turn)
 * by appending through this emitter — no agent installation required.
 */
class AgentSignalEmitterExecutionTest {

    private fun findEmitterAsset(): File? =
        listOf(
            File("src/main/assets/agentbridge/ps-emit.sh"),
            File("app/src/main/assets/agentbridge/ps-emit.sh"),
            File("../app/src/main/assets/agentbridge/ps-emit.sh"),
        ).firstOrNull { it.isFile }

    private fun runEmitter(vararg args: String, stdin: String? = null): Process {
        val command = mutableListOf("sh", findEmitterAsset()!!.absolutePath) + args.toList()
        val process = ProcessBuilder(command).start()
        stdin?.let {
            process.outputStream.write(it.toByteArray())
            process.outputStream.close()
        }
        process.waitFor()
        return process
    }

    private fun readSignals(record: File): List<AgentSignalBridge.SignalRecord> =
        AgentLaunchRecords.parse(record.readText()).signals

    @Test
    fun `the real emitter appends a parseable signal with the invoking shell's true identity`() {
        val emitter = findEmitterAsset() ?: return
        val dir = Files.createTempDirectory("ps-emit").toFile()
        try {
            val record = File(dir, "p9feed.jsonl")
            record.writeText("{\"t\":\"launch\",\"pid\":1,\"pgrp\":1,\"start\":1,\"agent\":\"claude\"}\n")
            val side = File(dir, "identity.txt")
            // The wrapper shell records its OWN pid:start (fields 4/22, the
            // comm-safe parse) — then invokes the emitter; the emitter's
            // parent IS this shell, so the recorded identity must match.
            val script = """
                sed 's/^[0-9]* (.*) //' /proc/${"\$\$"}/stat | cut -d ' ' -f2,20 | tr ' ' ':' > ${side.absolutePath}
                sh ${emitter.absolutePath} claude working ${record.absolutePath} <<'EOF'
                {"hook_event_name":"PreToolUse","tool_name":"Bash"}
                EOF
            """.trimIndent()
            val process = ProcessBuilder("sh", "-c", script).start()
            process.waitFor()
            assertEquals(0, process.exitValue())

            val identity = side.readText().trim() // "pid:start"
            assertTrue(identity.contains(':'))
            val signals = readSignals(record)
            assertEquals(1, signals.size)
            val signal = signals.first()
            assertEquals("claude", signal.agent)
            assertEquals(AgentSignalBridge.AgentSignalKind.WORKING, signal.kind)
            assertEquals(identity, "${signal.parentPid}:${signal.parentStartTicks}")
            assertTrue(signal.data.contains("\"tool_name\":\"Bash\""))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the argv transport works - the Codex notify shape`() {
        val emitter = findEmitterAsset() ?: return
        val dir = Files.createTempDirectory("ps-emit").toFile()
        try {
            val record = File(dir, "p9feed.jsonl")
            record.writeText("")
            val payload = "{\"type\":\"agent-turn-complete\",\"thread-id\":\"t1\",\"last-assistant-message\":\"done\"}"
            val process = runEmitter("codex", "turn_complete", record.absolutePath, payload, stdin = "{}")
            assertEquals(0, process.exitValue())
            val signals = readSignals(record)
            assertEquals(1, signals.size)
            assertEquals(AgentSignalBridge.AgentSignalKind.TURN_COMPLETE, signals.first().kind)
            assertTrue(signals.first().data.contains("agent-turn-complete"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a hostile payload still yields a valid bounded record`() {
        val emitter = findEmitterAsset() ?: return
        val dir = Files.createTempDirectory("ps-emit").toFile()
        try {
            val record = File(dir, "p9feed.jsonl")
            record.writeText("")
            val hostile = "{\"a\":\"back\\\\slash\",\"b\":\"quote\\\"inside\",\"c\":\"tab\tnewline\nctl\u0001\",\"d\":\"" + "x".repeat(3000) + "\"}"
            runEmitter("kilo", "attention", record.absolutePath, stdin = hostile)
            val signals = readSignals(record)
            assertEquals(1, signals.size)
            assertEquals(AgentSignalBridge.AgentSignalKind.ATTENTION, signals.first().kind)
            // bounded: the data never exceeds the emitter's cap (with escape headroom)
            assertTrue(signals.first().data.length <= 900)
            // and the control byte did not survive
            assertTrue(!signals.first().data.contains('\u0001'))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the generation guard - a missing record file receives nothing and never fails`() {
        val emitter = findEmitterAsset() ?: return
        val dir = Files.createTempDirectory("ps-emit").toFile()
        try {
            val missing = File(dir, "absent.jsonl")
            val process = runEmitter("claude", "working", missing.absolutePath, stdin = "{}")
            assertEquals(0, process.exitValue())
            assertTrue(!missing.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a full fake-agent story appends in order and folds to the right attention`() {
        val emitter = findEmitterAsset() ?: return
        val dir = Files.createTempDirectory("ps-emit").toFile()
        try {
            val record = File(dir, "p9feed.jsonl")
            record.writeText("{\"t\":\"launch\",\"pid\":9,\"pgrp\":9,\"start\":99,\"agent\":\"claude\"}\n")
            runEmitter("claude", "session_start", record.absolutePath, stdin = "{}")
            runEmitter("claude", "working", record.absolutePath, stdin = "{}")
            runEmitter("claude", "permission_request", record.absolutePath, stdin = "{\"tool_name\":\"Bash\"}")
            runEmitter("claude", "working", record.absolutePath, stdin = "{}")
            runEmitter("claude", "turn_complete", record.absolutePath, stdin = "{\"last_assistant_message\":\"done\"}")
            val signals = readSignals(record)
            assertEquals(
                listOf(
                    AgentSignalBridge.AgentSignalKind.SESSION_START,
                    AgentSignalBridge.AgentSignalKind.WORKING,
                    AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST,
                    AgentSignalBridge.AgentSignalKind.WORKING,
                    AgentSignalBridge.AgentSignalKind.TURN_COMPLETE,
                ),
                signals.map { it.kind },
            )
            // append order is the ordering authority: line indices are 0..4
            assertEquals(listOf(0, 1, 2, 3, 4), signals.map { it.lineIndex })
        } finally {
            dir.deleteRecursively()
        }
    }
}
