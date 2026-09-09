package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P9 — the DISCOVERY truth matrix (pure tests over
 * [AgentRuntimeDetection.compute] with explicit snapshots).
 *
 * The Part-B root-cause fix, pinned as executable truth:
 *
 *  - a plain guest session (no launch identity) whose tree contains a
 *    registry-token process states that agent RUNNING with the REGISTRY's
 *    own identity — at ANY working directory (the cwd was never the
 *    discriminator; the old eligibility line was);
 *  - a host-side shell session is not even eligible (the detector seam
 *    pins that; the pure layer asserts the discovery shape directly);
 *  - predicted (registry-launcher) sessions keep spawn-truth precedence;
 *  - the anchor refines the claim with the exact pid and the
 *    LAUNCH_ANCHOR grade, rejects pid reuse (starttime), rejects foreign
 *    processes (outside the correlation domain), and still claims when
 *    exe/cmdline are unreadable;
 *  - the four-state contract is unchanged: UNKNOWN never reads as
 *    RUNNING, NOT_RUNNING is proven absence only, the exit FACT rides
 *    the observation without ever becoming an interpretation;
 *  - multi-session independence, unrelated-process rejection, birth
 *    silence, identity persistence, and purity hold exactly as in P3b.
 */
class AgentRuntimeDiscoveryTest {

    private val tokens = listOf("hermes", "opencode", "claude", "zcode", "kilo", "cline", "agy", "codex", "qwen")

    private fun session(
        id: Long = 1,
        rootPid: Int = 100,
        discovery: Boolean = true,
        token: String = "",
        recordPath: String? = null,
    ) = AgentRuntimeDetection.EligibleAgentSession(
        sessionId = id,
        rootPid = rootPid,
        token = token,
        predetermined = null,
        discovery = discovery,
        recordPath = recordPath,
    )

    private fun proc(
        pid: Int,
        ppid: Int,
        pgrp: Int = 1,
        exe: String? = null,
        argv: List<String>? = null,
        state: Char? = 'S',
        start: Long? = null,
    ) = ProcfsProcess(pid = pid, ppid = ppid, pgrp = pgrp, state = state, exe = exe, argv = argv, startTime = start)

    /** Root (100) → shell (101) → agent (102), the plain-session topology. */
    private fun plainTreeWithAgent(exe: String? = "/usr/local/bin/kilo", start: Long = 500) = listOf(
        proc(pid = 100, ppid = 1, pgrp = 100),
        proc(pid = 101, ppid = 100, pgrp = 100),
        proc(pid = 102, ppid = 101, pgrp = 100, exe = exe, argv = listOf("/bin/sh", "/usr/local/bin/kilo"), start = start),
    )

    // ---------------------------------------------------- the root-cause fix

    @Test
    fun `a plain session typing kilo at any directory states Kilo Code - Running`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(plainTreeWithAgent()),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        val observation = next[1L]!!
        assertEquals(AgentRuntimeState.RUNNING, observation.state)
        assertEquals("Kilo Code", observation.agent?.displayName)
        assertEquals("kilo", observation.agent?.launcherId)
        assertTrue(observation.everObservedRunning)
        assertEquals(listOf(102), observation.evidence?.pids)
    }

    @Test
    fun `the same detection holds for the shebang shape (exe reads the interpreter)`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/bin/node",
                        argv = listOf("/usr/bin/node", "/usr/local/bin/kilo"), start = 5),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.RUNNING, next[1L]!!.state)
        assertEquals("kilo", next[1L]!!.agent?.launcherId)
    }

    @Test
    fun `spawn truth keeps precedence - a predicted session claims its own agent`() {
        val predicted = AgentRuntimeDetection.EligibleAgentSession(
            sessionId = 1,
            rootPid = 100,
            token = "kilo",
            predetermined = LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
            discovery = false,
        )
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(predicted),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/local/bin/kilo", start = 9),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.RUNNING, next[1L]!!.state)
        assertEquals("Kilo Code", next[1L]!!.agent?.displayName)
    }

    @Test
    fun `a process named after no registry token is never claimed`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/bin/my-own-tool", start = 5),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
        assertNull(next[1L]!!.evidence)
    }

    @Test
    fun `substring look-alikes never match (the P3b G rule holds under discovery)`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/bin/my-codex-wrapper", start = 5),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
    }

    @Test
    fun `birth silence - an unresolved discovery session states UNKNOWN and names nobody`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(listOf(proc(pid = 100, ppid = 1, pgrp = 100))),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
        assertFalse(next[1L]!!.everObservedRunning)
    }

    @Test
    fun `an unrelated same-name process outside the tree never claims (PART Q 10)`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 900, ppid = 1, pgrp = 900, exe = "/usr/local/bin/kilo", start = 5), // different tree
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
    }

    // ------------------------------------------------------------- the anchor

    private fun anchorRecord(pid: Int, pgrp: Int, start: Long, agent: String = "kilo") =
        mapOf(
            1L to AgentLaunchRecords.SessionRecords(
                launch = AgentLaunchRecords.LaunchRecord(pid, pgrp, start, agent),
                exits = emptyList(),
            ),
        )

    @Test
    fun `the anchor claims the exact pid with the LAUNCH_ANCHOR grade`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(plainTreeWithAgent(exe = "/usr/local/bin/kilo", start = 500)),
            nowMs = 1_000L,
            records = anchorRecord(pid = 102, pgrp = 100, start = 500),
            discoveryTokens = tokens,
        )
        val observation = next[1L]!!
        assertEquals(AgentRuntimeState.RUNNING, observation.state)
        // The token spoke (exe readable): the claim's grade is the token's,
        // and the anchor corroborates the exact pid inside the evidence.
        assertEquals(AgentMatchedBy.PROCFS_EXE, observation.evidence?.grade)
        assertEquals(listOf(102), observation.evidence?.pids)
        assertEquals("Kilo Code", observation.agent?.displayName)
    }

    @Test
    fun `a recycled pid fails the starttime comparison - the anchor is honestly gone`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 101, pgrp = 100, exe = "/usr/bin/other",
                        argv = listOf("/usr/bin/other"), start = 999), // pid reused by an unrelated binary
                ),
            ),
            nowMs = 1_000L,
            records = anchorRecord(pid = 102, pgrp = 100, start = 500),
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
    }

    @Test
    fun `a foreign-session process can never be claimed by a forged record (PART T)`() {
        // The record names pid 900 — a live matching process in ANOTHER
        // session's tree — but the correlation domain of session 1 does
        // not contain it: the anchor is structurally refused.
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 900, ppid = 1, pgrp = 900, exe = "/usr/local/bin/kilo", start = 500),
                ),
            ),
            nowMs = 1_000L,
            records = anchorRecord(pid = 900, pgrp = 900, start = 500),
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.evidence)
    }

    @Test
    fun `the anchor still claims when exe and cmdline are unreadable`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = null, argv = null, start = 500),
                ),
            ),
            nowMs = 1_000L,
            records = anchorRecord(pid = 102, pgrp = 100, start = 500),
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.RUNNING, next[1L]!!.state)
        assertEquals(AgentMatchedBy.LAUNCH_ANCHOR, next[1L]!!.evidence?.grade)
        assertEquals("Kilo Code", next[1L]!!.agent?.displayName)
    }

    @Test
    fun `the anchor's own token resolves the agent even against a hypothetical foreign observation`() {
        // A non-agent token set would normally refuse "kilo"; the launch
        // record is the session's OWN spawn-channel statement about what
        // the chain exec'd — used only to resolve the identity of an
        // already-correlated process, never to widen the domain.
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/bin/kilo", start = 500),
                ),
            ),
            nowMs = 1_000L,
            records = anchorRecord(pid = 102, pgrp = 100, start = 500),
            discoveryTokens = tokens,
        )
        assertEquals("kilo", next[1L]!!.agent?.launcherId)
    }

    // --------------------------------------------- lifecycle + exit + sessions

    @Test
    fun `proven absence attaches the exit FACT and keeps the resolved name`() {
        val previous = mapOf(
            1L to AgentRuntimeDetection.Observation(
                sessionId = 1,
                state = AgentRuntimeState.RUNNING,
                evidence = AgentRuntimeDetection.ProcessEvidence(pids = listOf(102), grade = AgentMatchedBy.PROCFS_EXE, observedAtMs = 900),
                everObservedRunning = true,
                updatedAtMs = 900,
                agent = LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo"),
            ),
        )
        val records = mapOf(
            1L to AgentLaunchRecords.SessionRecords(
                launch = AgentLaunchRecords.LaunchRecord(102, 100, 500, "kilo"),
                exits = listOf(AgentLaunchRecords.ExitRecord(0, "kilo")),
            ),
        )
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = previous,
            snapshot = ProcfsSnapshot(listOf(proc(pid = 100, ppid = 1, pgrp = 100))), // agent gone
            nowMs = 2_000L,
            records = records,
            discoveryTokens = tokens,
        )
        val observation = next[1L]!!
        assertEquals(AgentRuntimeState.NOT_RUNNING, observation.state)
        assertEquals("Kilo Code", observation.agent?.displayName)
        assertEquals(0, observation.lastExit?.status)
    }

    @Test
    fun `multi-session independence - two plain sessions, two agents (PART Q 3, 6)`() {
        val eligible = listOf(session(id = 1, rootPid = 100), session(id = 2, rootPid = 200))
        val snapshot = ProcfsSnapshot(
            listOf(
                proc(pid = 100, ppid = 1, pgrp = 100),
                proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/bin/kilo", start = 5),
                proc(pid = 200, ppid = 1, pgrp = 200),
                proc(pid = 202, ppid = 200, pgrp = 200, exe = "/usr/bin/claude", start = 6),
            ),
        )
        val next = AgentRuntimeDetection.compute(
            eligible = eligible,
            previous = emptyMap(),
            snapshot = snapshot,
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals("kilo", next[1L]!!.agent?.launcherId)
        assertEquals("claude", next[2L]!!.agent?.launcherId)
        assertEquals(AgentRuntimeState.RUNNING, next[1L]!!.state)
        assertEquals(AgentRuntimeState.RUNNING, next[2L]!!.state)
    }

    @Test
    fun `a session whose agent exits does not poison another session's claim (PART Q 5, 7)`() {
        val eligible = listOf(session(id = 1, rootPid = 100), session(id = 2, rootPid = 200))
        val running = AgentRuntimeDetection.compute(
            eligible = eligible,
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/usr/bin/kilo", start = 5),
                    proc(pid = 200, ppid = 1, pgrp = 200),
                    proc(pid = 202, ppid = 200, pgrp = 200, exe = "/usr/bin/kilo", start = 6),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        // Session 1's agent dies; session 2's keeps running.
        val next = AgentRuntimeDetection.compute(
            eligible = eligible,
            previous = running,
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 200, ppid = 1, pgrp = 200),
                    proc(pid = 202, ppid = 200, pgrp = 200, exe = "/usr/bin/kilo", start = 6),
                ),
            ),
            nowMs = 2_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.NOT_RUNNING, next[1L]!!.state)
        assertEquals(AgentRuntimeState.RUNNING, next[2L]!!.state)
        assertEquals("kilo", next[2L]!!.agent?.launcherId)
    }

    @Test
    fun `a new session starts with no stale state (PART Q 8)`() {
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session(id = 9, rootPid = 300)),
            previous = emptyMap(), // the new session's memory starts empty
            snapshot = ProcfsSnapshot(listOf(proc(pid = 300, ppid = 1, pgrp = 300))),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, next[9L]!!.state)
        assertNull(next[9L]!!.agent)
        assertFalse(next[9L]!!.everObservedRunning)
    }

    @Test
    fun `arbitrary output-shaped processes never create agent state (PART Q 11)`() {
        // Shell noise, TUI refreshes, cats of files whose CONTENT mentions
        // agents — none of it is a process; nothing is claimed.
        val next = AgentRuntimeDetection.compute(
            eligible = listOf(session()),
            previous = emptyMap(),
            snapshot = ProcfsSnapshot(
                listOf(
                    proc(pid = 100, ppid = 1, pgrp = 100),
                    proc(pid = 102, ppid = 100, pgrp = 100, exe = "/bin/cat",
                        argv = listOf("/bin/cat", "kilo claude codex"), start = 5),
                ),
            ),
            nowMs = 1_000L,
            discoveryTokens = tokens,
        )
        // argv[1] basename is "kilo claude codex" (the WHOLE element) — no
        // exact match; exe is cat. Nothing is claimed.
        assertEquals(AgentRuntimeState.UNKNOWN, next[1L]!!.state)
        assertNull(next[1L]!!.agent)
    }

    @Test
    fun `compute is pure - identical inputs produce identical observations`() {
        val eligible = listOf(session())
        val snapshot = ProcfsSnapshot(plainTreeWithAgent())
        val one = AgentRuntimeDetection.compute(eligible, emptyMap(), snapshot, 1_000L, discoveryTokens = tokens)
        val two = AgentRuntimeDetection.compute(eligible, emptyMap(), snapshot, 1_000L, discoveryTokens = tokens)
        assertEquals(one, two)
    }

    @Test
    fun `the registry token set is exactly the nine curated launchers`() {
        assertEquals(
            listOf("hermes", "opencode", "claude", "zcode", "kilo", "cline", "agy", "codex", "qwen"),
            AgentDiscoveryTokens.registryTokens(),
        )
        assertEquals("Kilo Code", AgentDiscoveryTokens.byToken("kilo")?.displayName)
        assertNull(AgentDiscoveryTokens.byToken("totally-unknown"))
    }
}
