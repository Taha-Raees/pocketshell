package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P9 — the OBSERVATION TOPOLOGY test: the Part-B reproduction of the
 * real-device discrepancy, pinned permanently against REAL processes and
 * REAL /proc on the test JVM's Linux.
 *
 * The user's report: Kilo launched from the Home launcher was detected;
 * Kilo launched inside a plain Terminal session (at any working directory)
 * was not — only the generic "Terminal sessions running" FGS. The Part-B
 * audit proved the discriminator was the session's SPAWN ORIGIN (the old
 * eligibility line scanned only registry-launcher sessions), never the
 * directory. THIS test reproduces the exact topologies with real process
 * trees and pins the FIXED behavior:
 *
 *   topology 1 — the launcher chain (setsid root → sh -c chain → agent):
 *                detected as PREDICTED (spawn truth), any cwd, both match
 *                grades, and the launch-record anchor names the exact pid;
 *   topology 2 — the plain Terminal session (setsid root → sh -l → the
 *                user types the agent): detected as DISCOVERED — the fix;
 *   topology 3 — a subdirectory cwd produces the SAME detection quality
 *                (the §58-B regression target, reproduced here on Linux);
 *   topology 4 — an unrelated same-name process in a foreign tree claims
 *                nothing (the anti-false-positive core).
 *
 * Everything runs through the SHIPPED seam: the real [HostProcfsReader]
 * (not a fixture) reads the real /proc; the pure [AgentRuntimeDetection.compute]
 * makes the decision. Process spawning in unit tests is the established
 * repo precedent (CommandAppsTest's execution fixture); every process is
 * reaped and every temp dir removed.
 */
class AgentObservationTopologyTest {

    /** A fake "agent" binary: real exec, real exit status, cwd-independent. */
    private fun writeAgent(dir: File, name: String, exitCode: Int): File {
        val f = File(dir, name).apply {
            writeText("#!/bin/sh\nprintf '%s\\n' \"agent-at-\$(pwd)\"\nsleep 1\nexit $exitCode\n")
            setExecutable(true)
        }
        return f
    }

    /** Spawn `setsid sh -c <chain>`; the shell records its own pid (the session root) in <work>/root.pid. */
    private fun spawnSession(work: File, chain: String): Int {
        val pidFile = File(work, "root-${System.nanoTime()}.pid")
        ProcessBuilder("setsid", "sh", "-c", "printf '%s\\n' \"\$\$\" > '${pidFile.absolutePath}'; $chain").start()
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            val text = pidFile.absoluteFile.takeIf { it.exists() }?.readText()?.trim()
            if (!text.isNullOrEmpty()) return text.toInt()
            Thread.sleep(10)
        }
        error("the session root never recorded its pid")
    }

    private fun awaitRecord(record: File, timeoutMs: Long = 5_000): AgentLaunchRecords.SessionRecords? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (record.length() > 0) {
                val parsed = AgentLaunchRecords.readFile(record.absolutePath)
                if (parsed.launch != null) return parsed
            }
            Thread.sleep(25)
        }
        return null
    }

    @Test
    fun `topology 1+3 - the launcher chain is detected with the anchor at root and in a subdirectory`() {
        val work = java.nio.file.Files.createTempDirectory("p9topo").toFile()
        try {
            val agent = writeAgent(work, "kilo", exitCode = 0)
            val record = File(work, "gen.jsonl").apply { createNewFile() }
            val chain = AgentLaunchRecords.launchChain(
                agentCommand = agent.absolutePath,
                agentToken = "kilo",
                guestShell = "/bin/sh",
                guestRecordFile = record.absolutePath,
            ).removeSuffix("; exec /bin/sh -l") // the fixture omits the trailing login shell
            for (cwd in listOf("/", work.absolutePath)) { // root vs subdirectory
                val rootPid = spawnSession(work, "cd '$cwd' 2>/dev/null; $chain")
                val parsed = awaitRecord(record)
                assertNotNull("the launch record arrived (cwd=$cwd)", parsed)
                val launch = parsed!!.launch!!
                // The agent is alive right now (it sleeps 1s): scan it.
                val reader = HostProcfsReader()
                val snapshot = reader.snapshot()
                assertNotNull(snapshot)
                val predicted = AgentRuntimeDetection.EligibleAgentSession(
                    sessionId = 1,
                    rootPid = rootPid,
                    token = "kilo",
                    predetermined = LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
                    discovery = false,
                    recordPath = record.absolutePath,
                )
                val next = AgentRuntimeDetection.compute(
                    eligible = listOf(predicted),
                    previous = emptyMap(),
                    snapshot = snapshot,
                    nowMs = System.currentTimeMillis(),
                    records = mapOf(1L to parsed),
                )
                val observation = next[1L]!!
                assertEquals("detected at cwd=$cwd (the §58-B regression target)", AgentRuntimeState.RUNNING, observation.state)
                assertEquals("Kilo Code", observation.agent?.displayName)
                assertTrue(
                    "the anchor names the exact pid",
                    observation.evidence?.pids?.contains(launch.pid) == true,
                )
                rootJoin(rootPid)
                record.writeText("") // reset for the second leg
            }
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `topology 2+3 - the plain Terminal session is DISCOVERED at root and in a subdirectory (the fix)`() {
        val work = java.nio.file.Files.createTempDirectory("p9topo").toFile()
        try {
            val agent = writeAgent(work, "kilo", exitCode = 0)
            for (cwd in listOf("/", work.absolutePath)) {
                // The plain-shell shape: a login shell the user typed the
                // command into — no launch identity, no record channel.
                // The agent starts immediately and sleeps 1s; the session
                // root stays alive beside it.
                val rootPid = spawnSession(
                    work,
                    "cd '$cwd' 2>/dev/null; ${agent.absolutePath} >/dev/null 2>&1 & sleep 1.5",
                )
                val reader = HostProcfsReader()
                // Poll a few times — the agent exists from ~0.0s (the & job
                // starts immediately after cd); we just need one snapshot
                // while it sleeps.
                var detected: AgentRuntimeDetection.Observation? = null
                val deadline = System.currentTimeMillis() + 4_000
                while (System.currentTimeMillis() < deadline && detected == null) {
                    val snapshot = reader.snapshot()
                    if (snapshot != null) {
                        val discovered = AgentRuntimeDetection.EligibleAgentSession(
                            sessionId = 7,
                            rootPid = rootPid,
                            token = "",
                            predetermined = null,
                            discovery = true,
                        )
                        val next = AgentRuntimeDetection.compute(
                            eligible = listOf(discovered),
                            previous = emptyMap(),
                            snapshot = snapshot,
                            nowMs = System.currentTimeMillis(),
                        )
                        detected = next[7L]?.takeIf { it.state == AgentRuntimeState.RUNNING }
                    }
                    if (detected == null) Thread.sleep(50)
                }
                assertNotNull("the plain session's agent was DISCOVERED (cwd=$cwd)", detected)
                assertEquals("Kilo Code", detected!!.agent?.displayName)
                rootJoin(rootPid)
            }
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `topology 4 - a foreign same-name process in an unrelated tree claims nothing`() {
        val work = java.nio.file.Files.createTempDirectory("p9topo").toFile()
        try {
            val agent = writeAgent(work, "kilo", exitCode = 0)
            // Session root: an innocent sleeper (no agent in ITS tree).
            val rootPid = spawnSession(work, "sleep 2")
            // An UNRELATED agent process outside the session's tree.
            spawnSession(work, "cd /; ${agent.absolutePath} >/dev/null 2>&1; exit 0")
            Thread.sleep(300) // let the foreign agent start
            val reader = HostProcfsReader()
            val snapshot = reader.snapshot()
            assertNotNull(snapshot)
            val discovered = AgentRuntimeDetection.EligibleAgentSession(
                sessionId = 1,
                rootPid = rootPid,
                token = "",
                predetermined = null,
                discovery = true,
            )
            val next = AgentRuntimeDetection.compute(
                eligible = listOf(discovered),
                previous = emptyMap(),
                snapshot = snapshot,
                nowMs = System.currentTimeMillis(),
            )
            assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
            assertNull(next[1L]!!.agent)
            assertNull(next[1L]!!.evidence)
            rootJoin(rootPid)
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `the shipped HostProcfsReader reads the real stat fields the anchor needs`() {
        val sleeper = ProcessBuilder("sleep", "3").start()
        try {
            val reader = HostProcfsReader()
            val snapshot = reader.snapshot()
            assertNotNull(snapshot)
            // The test JVM's own pid, straight from /proc/self (the android
            // jar's Process stubs are irrelevant here — this is real Linux).
            val selfPid = File("/proc/self/stat").readText().substringBefore(' ').toInt()
            val me = snapshot!!.processes.firstOrNull { it.pid == selfPid }
            assertNotNull("the test JVM's own process is visible to the app-UID reader", me)
            assertNotNull(me!!.startTime)
            assertTrue("starttime is a positive boot-tick count", me.startTime!! > 0)
            // The correlation inputs are present for a real process.
            assertTrue(me.ppid >= 0 && me.pgrp > 0)
        } finally {
            sleeper.destroy()
            sleeper.waitFor()
        }
    }

    /** Wait for a session root to exit, bounded (the chains are all self-terminating). */
    private fun rootJoin(rootPid: Int) {
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            if (!File("/proc/$rootPid").exists()) return
            Thread.sleep(50)
        }
        // Kill it honestly rather than leak it.
        ProcessBuilder("kill", "-9", rootPid.toString()).start().waitFor()
    }
}
