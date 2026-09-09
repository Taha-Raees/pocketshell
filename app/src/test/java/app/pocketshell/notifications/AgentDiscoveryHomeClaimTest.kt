package app.pocketshell.notifications

import app.pocketshell.terminal.AgentHomeSessionClaims
import app.pocketshell.terminal.AgentMatchedBy
import app.pocketshell.terminal.AgentRuntimeDetection
import app.pocketshell.terminal.AgentRuntimeState
import app.pocketshell.terminal.LaunchIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P9 — the Home-claim parity for DISCOVERED sessions: the shade and
 * the Home Sessions row must agree state-for-state for a plain Terminal
 * session whose agent was discovered by process evidence, exactly as P8
 * pinned for launcher-born sessions.
 *
 * The claim name comes from the RESOLVED registry identity (the process
 * evidence's own registry entry) — never the session label, never the
 * terminal title. Spawn truth keeps precedence: a session WITH a launch
 * identity claims its own agent name even if the observation also carries
 * a resolved agent.
 */
class AgentDiscoveryHomeClaimTest {

    private val kilo = LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo")

    private fun input(sessionId: Long, identity: LaunchIdentity?) =
        AgentHomeSessionClaims.HomeSessionInput(sessionId = sessionId, identity = identity)

    private fun runningObservation(sessionId: Long, agent: LaunchIdentity.KnownAgent) =
        AgentRuntimeDetection.Observation(
            sessionId = sessionId,
            state = AgentRuntimeState.RUNNING,
            evidence = AgentRuntimeDetection.ProcessEvidence(
                pids = listOf(102),
                grade = AgentMatchedBy.PROCFS_EXE,
                observedAtMs = 1_000L,
            ),
            everObservedRunning = true,
            updatedAtMs = 1_000L,
            agent = agent,
        )

    @Test
    fun `a plain session with discovered RUNNING evidence claims the registry name`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = mapOf(1L to runningObservation(1L, kilo)),
        )
        val claim = claims[1L]
        assertTrue(claim != null)
        assertEquals(AgentHomeSessionClaims.Claim.RUNNING, claim!!.claim)
        assertEquals("Kilo Code", claim.agentDisplayName)
    }

    @Test
    fun `a plain session with nothing discovered claims nothing (birth silence)`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = mapOf(
                1L to AgentRuntimeDetection.Observation(
                    sessionId = 1,
                    state = AgentRuntimeState.UNKNOWN,
                    evidence = null,
                    everObservedRunning = false,
                    updatedAtMs = 1_000L,
                    agent = null,
                ),
            ),
        )
        assertNull(claims[1L])
    }

    @Test
    fun `a plain session with no observation at all claims nothing`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = emptyMap(),
        )
        assertNull(claims[1L])
    }

    @Test
    fun `spawn truth wins - a launcher session claims its own agent name`() {
        val claude = LaunchIdentity.KnownAgent("claude", "Claude Code", "claude")
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, claude)),
            observations = mapOf(1L to runningObservation(1L, kilo)),
        )
        assertEquals("Claude Code", claims[1L]!!.agentDisplayName)
    }

    @Test
    fun `the discovered claim withdraws on proven absence (parity with the shade cancel)`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = mapOf(
                1L to AgentRuntimeDetection.Observation(
                    sessionId = 1,
                    state = AgentRuntimeState.NOT_RUNNING,
                    evidence = null,
                    everObservedRunning = true,
                    updatedAtMs = 2_000L,
                    agent = kilo,
                ),
            ),
        )
        assertNull(claims[1L])
    }

    @Test
    fun `the discovered claim states uncertainty after an announced story (parity)`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = mapOf(
                1L to AgentRuntimeDetection.Observation(
                    sessionId = 1,
                    state = AgentRuntimeState.UNKNOWN,
                    evidence = null,
                    everObservedRunning = true,
                    updatedAtMs = 2_000L,
                    agent = kilo,
                ),
            ),
        )
        assertEquals(AgentHomeSessionClaims.Claim.UNKNOWN, claims[1L]!!.claim)
        assertEquals("Kilo Code", claims[1L]!!.agentDisplayName)
    }

    @Test
    fun `an unresolved uncertainty never upgrades to a claim (UNKNOWN != RUNNING)`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(input(1L, null)),
            observations = mapOf(
                1L to AgentRuntimeDetection.Observation(
                    sessionId = 1,
                    state = AgentRuntimeState.UNKNOWN,
                    evidence = null,
                    everObservedRunning = false,
                    updatedAtMs = 2_000L,
                    agent = kilo, // resolved earlier but currently uncertain, never observed running
                ),
            ),
        )
        assertNull(claims[1L])
    }
}
