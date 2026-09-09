package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P8 — the pure HOME presentation-claim matrix of
 * [AgentHomeSessionClaims] (the phase's PART N arms 1/2/3/4/5/6/8 folded
 * into the pure layer): only the proven states may be claimed, the P4
 * notification parity holds state-for-state (birth UNKNOWN silent,
 * informed UNKNOWN uncertain, NOT_RUNNING withdrawn), non-agents are
 * structurally absent, multi-session isolation is keyed by the manager's
 * authoritative id, and a new session can never inherit a stale claim.
 *
 * All PURE: no clock, no I/O, no Android, no coroutine. The UI wiring and
 * the structural observer rules are pinned separately in
 * HomeSessionIntegrationBoundaryTest (the established source-reading
 * technique), and the notification-side agreement in
 * AgentRuntimeHomeParityTest.
 */
class AgentHomeSessionClaimsTest {

    // ------------------------------------------------------------ fixtures

    private fun agent(name: String = "Kilo Code", id: String = "kilo") =
        LaunchIdentity.KnownAgent(
            launcherId = id,
            displayName = name,
            command = "kilo --yolo",
        )

    private fun session(sessionId: Long, identity: LaunchIdentity?) =
        AgentHomeSessionClaims.HomeSessionInput(
            sessionId = sessionId,
            identity = identity,
        )

    private fun observation(
        sessionId: Long,
        state: AgentRuntimeState,
        everObservedRunning: Boolean = state == AgentRuntimeState.RUNNING ||
            state == AgentRuntimeState.NOT_RUNNING,
    ) = AgentRuntimeDetection.Observation(
        sessionId = sessionId,
        state = state,
        evidence = if (state == AgentRuntimeState.RUNNING) {
            AgentRuntimeDetection.ProcessEvidence(
                pids = listOf(100 + sessionId.toInt()),
                grade = AgentMatchedBy.PROCFS_EXE,
                observedAtMs = 1_000L,
            )
        } else {
            null
        },
        everObservedRunning = everObservedRunning,
        updatedAtMs = 1_000L,
    )

    // ------------------------------------------------- the claim decision

    @Test
    fun `RUNNING is claimed - the proven state the shade announces`() {
        assertEquals(
            AgentHomeSessionClaims.Claim.RUNNING,
            AgentHomeSessionClaims.claimFor(AgentRuntimeState.RUNNING, everObservedRunning = true),
        )
    }

    @Test
    fun `UNKNOWN after proven presence is claimed as uncertainty - never upgraded`() {
        assertEquals(
            AgentHomeSessionClaims.Claim.UNKNOWN,
            AgentHomeSessionClaims.claimFor(AgentRuntimeState.UNKNOWN, everObservedRunning = true),
        )
    }

    @Test
    fun `birth UNKNOWN claims nothing - the shade is silent there too`() {
        assertNull(
            AgentHomeSessionClaims.claimFor(AgentRuntimeState.UNKNOWN, everObservedRunning = false),
        )
    }

    @Test
    fun `NOT_RUNNING claims nothing - the withdrawal arm mirrors the cancelled surface`() {
        assertNull(
            AgentHomeSessionClaims.claimFor(AgentRuntimeState.NOT_RUNNING, everObservedRunning = true),
        )
    }

    @Test
    fun `NOT_APPLICABLE claims nothing`() {
        assertNull(
            AgentHomeSessionClaims.claimFor(AgentRuntimeState.NOT_APPLICABLE, everObservedRunning = false),
        )
    }

    // --------------------------------------------------- the present fold

    @Test
    fun `no agent session - no claims at all (plain shells never gain one)`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(
                session(1, identity = null), // plain shell
            ),
            observations = emptyMap(),
        )
        assertTrue(claims.isEmpty())
    }

    @Test
    fun `a running agent session claims RUNNING with the registry display name`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(2, identity = agent(name = "Kilo Code"))),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        val claim = claims.getValue(2L)
        assertEquals(AgentHomeSessionClaims.Claim.RUNNING, claim.claim)
        assertEquals("Kilo Code", claim.agentDisplayName)
        assertEquals(2L, claim.sessionId)
    }

    @Test
    fun `an agent session before its first scan claims nothing - no invented running`() {
        // No observation exists yet (the scanner has not ticked): the state
        // is UNKNOWN at birth, everObservedRunning=false — the same honest
        // silence the notification layer keeps (Launched posts nothing).
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(2, identity = agent())),
            observations = emptyMap(),
        )
        assertTrue(claims.isEmpty())
    }

    @Test
    fun `informed UNKNOWN renders as Runtime unknown - never as Running`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(2, identity = agent())),
            observations = mapOf(
                2L to observation(2, AgentRuntimeState.UNKNOWN, everObservedRunning = true),
            ),
        )
        assertEquals(
            AgentHomeSessionClaims.Claim.UNKNOWN,
            claims.getValue(2L).claim,
        )
    }

    @Test
    fun `withdrawal - NOT_RUNNING removes the claim the running state had made`() {
        val running = AgentHomeSessionClaims.present(
            live = listOf(session(2, identity = agent())),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        assertTrue(AgentHomeSessionClaims.Claim.RUNNING == running.getValue(2L).claim)

        val withdrawn = AgentHomeSessionClaims.present(
            live = listOf(session(2, identity = agent())),
            observations = mapOf(2L to observation(2, AgentRuntimeState.NOT_RUNNING)),
        )
        assertTrue(withdrawn.isEmpty())
    }

    @Test
    fun `known non-agent tools claim nothing - the scanner never ran for them`() {
        val tool = LaunchIdentity.KnownNonAgentTool(
            launcherId = "nano",
            displayName = "nano",
            command = "nano",
        )
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(3, identity = tool)),
            observations = mapOf(
                3L to observation(3, AgentRuntimeState.RUNNING, everObservedRunning = true),
            ),
        )
        // Even a hypothetical observation for the id cannot mint a claim:
        // the identity gate is structural.
        assertTrue(claims.isEmpty())
    }

    @Test
    fun `custom or unknown launchers claim nothing - names are never evidence`() {
        val custom = LaunchIdentity.CustomOrUnknown(
            launcherId = "my-agent",
            displayName = "My Agent",
            command = "python foo.py",
        )
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(4, identity = custom)),
            observations = mapOf(
                4L to observation(4, AgentRuntimeState.RUNNING, everObservedRunning = true),
            ),
        )
        assertTrue(claims.isEmpty())
    }

    // ------------------------------------------- multi-session isolation

    @Test
    fun `multiple sessions each receive only their own claim`() {
        val claims = AgentHomeSessionClaims.present(
            live = listOf(
                session(1, identity = null), // plain shell
                session(2, identity = agent(name = "Kilo Code", id = "kilo")),
                session(3, identity = agent(name = "Antigravity", id = "agy")),
                session(4, identity = LaunchIdentity.KnownNonAgentTool("nano", "nano", "nano")),
            ),
            observations = mapOf(
                2L to observation(2, AgentRuntimeState.RUNNING),
                3L to observation(3, AgentRuntimeState.UNKNOWN, everObservedRunning = true),
            ),
        )
        assertEquals(setOf(2L, 3L), claims.keys)
        assertEquals(AgentHomeSessionClaims.Claim.RUNNING, claims.getValue(2L).claim)
        assertEquals("Kilo Code", claims.getValue(2L).agentDisplayName)
        assertEquals(AgentHomeSessionClaims.Claim.UNKNOWN, claims.getValue(3L).claim)
        assertEquals("Antigravity", claims.getValue(3L).agentDisplayName)
    }

    @Test
    fun `middle session removal corrupts nothing - the neighbors keep their own claims`() {
        val before = AgentHomeSessionClaims.present(
            live = listOf(
                session(1, identity = agent(name = "Kilo Code", id = "kilo")),
                session(2, identity = agent(name = "Antigravity", id = "agy")),
                session(3, identity = agent(name = "Claude Code", id = "claude")),
            ),
            observations = mapOf(
                1L to observation(1, AgentRuntimeState.RUNNING),
                2L to observation(2, AgentRuntimeState.RUNNING),
                3L to observation(3, AgentRuntimeState.RUNNING),
            ),
        )
        assertEquals(setOf(1L, 2L, 3L), before.keys)

        // Session 2's tab is closed: it leaves the live list entirely.
        val after = AgentHomeSessionClaims.present(
            live = listOf(
                session(1, identity = agent(name = "Kilo Code", id = "kilo")),
                session(3, identity = agent(name = "Claude Code", id = "claude")),
            ),
            observations = mapOf(
                1L to observation(1, AgentRuntimeState.RUNNING),
                // the pruned detector map no longer holds session 2
                3L to observation(3, AgentRuntimeState.RUNNING),
            ),
        )
        assertEquals(setOf(1L, 3L), after.keys)
        assertEquals("Kilo Code", after.getValue(1L).agentDisplayName)
        assertEquals("Claude Code", after.getValue(3L).agentDisplayName)
    }

    @Test
    fun `a finished or removed session cannot hold a claim - no stale agent label`() {
        // The finished agent session is no longer in the live list (the
        // repository filters it): its earlier claim cannot survive.
        val claims = AgentHomeSessionClaims.present(
            live = emptyList(),
            observations = mapOf(
                2L to observation(2, AgentRuntimeState.RUNNING), // stale observation
            ),
        )
        assertTrue(claims.isEmpty())
    }

    @Test
    fun `a new session receives its own identity and inherits no stale claim`() {
        // Session ids are monotonic and never reused (the manager's
        // contract): a NEW session 5 with no observation cannot inherit the
        // old session 2's running claim even if a stale observation for 2
        // somehow lingers.
        val claims = AgentHomeSessionClaims.present(
            live = listOf(session(5, identity = agent(name = "Kilo Code", id = "kilo"))),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        assertTrue(claims.isEmpty())
    }

    // ------------------------------------------------- determinism/purity

    @Test
    fun `present is pure - identical inputs produce identical outputs`() {
        val live = listOf(
            session(1, identity = null),
            session(2, identity = agent()),
            session(3, identity = agent(name = "Claude Code", id = "claude")),
        )
        val observations = mapOf(
            2L to observation(2, AgentRuntimeState.RUNNING),
            3L to observation(3, AgentRuntimeState.UNKNOWN, everObservedRunning = true),
        )
        val first = AgentHomeSessionClaims.present(live, observations)
        val second = AgentHomeSessionClaims.present(live, observations)
        assertEquals(first, second)
    }

    @Test
    fun `the claim vocabulary is exactly the two proven states`() {
        assertEquals(
            setOf("RUNNING", "UNKNOWN"),
            AgentHomeSessionClaims.Claim.entries.map { it.name }.toSet(),
        )
    }
}
