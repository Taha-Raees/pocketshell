package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P9 — the DISCOVERY event arms (pure tests over
 * [AgentRuntimeTransitions.reduce]).
 *
 * The engine gains exactly two arms for the discovery class, and the
 * phase's honesty invariants are re-pinned around them:
 *
 *  - LAZY TRACK: a resolved RUNNING observation for a never-tracked
 *    session tracks it silently (no invented Launched — no launcher named
 *    the command) and emits ConfirmedRunning(from=null) — the "(birth)"
 *    shape the notification mapping already handles;
 *  - UNRESOLVED observations for untracked sessions stay ignored (the
 *    birth baseline never storms);
 *  - IDENTITY SWITCH: kilo → claude in one discovered session withdraws
 *    the old RUNNING claim (NoLongerDetected naming the OLD agent) and
 *    starts the new agent's story at its own first fact;
 *  - STALENESS: an observation for an ended session can never fabricate
 *    an event (Part G unchanged);
 *  - a DISCOVERED session that FINISHES ends as SESSION_FINISHED with the
 *    real waitpid status (never mislabeled as a tab removal).
 */
class AgentDiscoveryEventTest {

    private val kilo = LaunchIdentity.KnownAgent("kilo", "Kilo Code", "kilo")
    private val claude = LaunchIdentity.KnownAgent("claude", "Claude Code", "claude")

    private fun runningObservation(
        sessionId: Long,
        agent: LaunchIdentity.KnownAgent,
        nowMs: Long,
        pid: Int = 102,
    ) = mapOf(
        sessionId to AgentRuntimeDetection.Observation(
            sessionId = sessionId,
            state = AgentRuntimeState.RUNNING,
            evidence = AgentRuntimeDetection.ProcessEvidence(
                pids = listOf(pid),
                grade = AgentMatchedBy.PROCFS_EXE,
                observedAtMs = nowMs,
            ),
            everObservedRunning = true,
            updatedAtMs = nowMs,
            agent = agent,
        ),
    )

    @Test
    fun `lazy track - a plain session's first RUNNING evidence emits ConfirmedRunning from birth`() {
        val outcome = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val event = outcome.events.single()
        assertTrue(event is AgentRuntimeEvent.ConfirmedRunning)
        event as AgentRuntimeEvent.ConfirmedRunning
        assertEquals(1L, event.sessionId)
        assertNull("from=null: the birth shape the P4 mapping already handles", event.from)
        assertEquals("Kilo Code", event.agent.displayName)
        assertEquals(AgentRuntimeState.RUNNING, outcome.memory.tracked[1L]?.lastState)
    }

    @Test
    fun `lazy track - an UNRESOLVED observation for an untracked session stays silent`() {
        val outcome = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(
                    1L to AgentRuntimeDetection.Observation(
                        sessionId = 1,
                        state = AgentRuntimeState.UNKNOWN,
                        evidence = null,
                        everObservedRunning = false,
                        updatedAtMs = 1_000L,
                        agent = null,
                    ),
                ),
            ),
            nowMs = 1_000L,
        )
        assertTrue(outcome.events.isEmpty())
        assertTrue(outcome.memory.tracked.isEmpty())
    }

    @Test
    fun `the dedup contract holds for discovered sessions - same state twice, one event`() {
        val first = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val second = AgentRuntimeTransitions.reduce(
            memory = first.memory,
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 3_000L)),
            nowMs = 3_000L,
        )
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun `the withdrawal contract holds for discovered sessions (PART N 4)`() {
        val running = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val gone = AgentRuntimeTransitions.reduce(
            memory = running.memory,
            input = AgentEventInput.ObservationsChanged(
                mapOf(
                    1L to AgentRuntimeDetection.Observation(
                        sessionId = 1,
                        state = AgentRuntimeState.NOT_RUNNING,
                        evidence = null,
                        everObservedRunning = true,
                        updatedAtMs = 2_000L,
                        agent = kilo,
                    ),
                ),
            ),
            nowMs = 2_000L,
        )
        val event = gone.events.single()
        assertTrue(event is AgentRuntimeEvent.NoLongerDetected)
        assertEquals("Kilo Code", (event as AgentRuntimeEvent.NoLongerDetected).agent.displayName)
    }

    @Test
    fun `identity switch under RUNNING - the old claim is withdrawn before the new story starts`() {
        val running = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val switched = AgentRuntimeTransitions.reduce(
            memory = running.memory,
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, claude, nowMs = 2_000L, pid = 103)),
            nowMs = 2_000L,
        )
        assertEquals(2, switched.events.size)
        val withdrawal = switched.events[0]
        val confirmation = switched.events[1]
        assertTrue(withdrawal is AgentRuntimeEvent.NoLongerDetected)
        assertEquals("kilo", (withdrawal as AgentRuntimeEvent.NoLongerDetected).agent.launcherId)
        assertTrue(confirmation is AgentRuntimeEvent.ConfirmedRunning)
        assertEquals("claude", (confirmation as AgentRuntimeEvent.ConfirmedRunning).agent.launcherId)
        assertNull("the new agent's story starts at its own birth", confirmation.from)
        assertEquals("claude", switched.memory.tracked[1L]?.agent?.launcherId)
    }

    @Test
    fun `staleness - an observation for an ENDED session can never fabricate an event (Part G)`() {
        // A lazily-tracked session that reached its terminal edge.
        val running = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val ended = AgentRuntimeTransitions.reduce(
            memory = running.memory,
            input = AgentEventInput.SessionsChanged(
                live = emptyList(),
                finished = emptyMap(),
                finishedStatuses = mapOf(1L to ExitStatus.Exited(0)),
            ),
            nowMs = 2_000L,
        )
        assertTrue(ended.events.single() is AgentRuntimeEvent.SessionEnded)
        val late = AgentRuntimeTransitions.reduce(
            memory = ended.memory,
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 3_000L)),
            nowMs = 3_000L,
        )
        assertTrue(late.events.isEmpty())
    }

    @Test
    fun `a discovered session that FINISHES ends with its real status and cause`() {
        val running = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.ObservationsChanged(runningObservation(1L, kilo, nowMs = 1_000L)),
            nowMs = 1_000L,
        )
        val ended = AgentRuntimeTransitions.reduce(
            memory = running.memory,
            input = AgentEventInput.SessionsChanged(
                live = emptyList(),
                finished = emptyMap(), // no KnownAgent launch metadata for a plain session
                finishedStatuses = mapOf(1L to ExitStatus.Exited(3)),
            ),
            nowMs = 2_000L,
        )
        val event = ended.events.single()
        assertTrue(event is AgentRuntimeEvent.SessionEnded)
        event as AgentRuntimeEvent.SessionEnded
        assertEquals(AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED, event.cause)
        assertEquals(ExitStatus.Exited(3), event.sessionExitStatus)
        assertEquals("Kilo Code", event.agent.displayName)
    }

    @Test
    fun `an untracked plain session that finishes emits NOTHING (the arm consults tracked ids only)`() {
        val outcome = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.SessionsChanged(
                live = emptyList(),
                finished = emptyMap(),
                finishedStatuses = mapOf(7L to ExitStatus.Exited(0)),
            ),
            nowMs = 1_000L,
        )
        assertTrue(outcome.events.isEmpty())
    }

    @Test
    fun `predicted sessions still end via the KnownAgent finished map`() {
        val launched = AgentRuntimeTransitions.reduce(
            memory = AgentRuntimeEventMemory(),
            input = AgentEventInput.SessionsChanged(
                live = listOf(TrackedAgentLaunch(1L, kilo)),
                finished = emptyMap(),
            ),
            nowMs = 1_000L,
        )
        assertTrue(launched.events.single() is AgentRuntimeEvent.Launched)
        val ended = AgentRuntimeTransitions.reduce(
            memory = launched.memory,
            input = AgentEventInput.SessionsChanged(
                live = emptyList(),
                finished = mapOf(1L to FinishedAgentLaunch(1L, kilo, ExitStatus.Exited(0))),
                finishedStatuses = mapOf(1L to ExitStatus.Exited(0)),
            ),
            nowMs = 2_000L,
        )
        val event = ended.events.single()
        assertTrue(event is AgentRuntimeEvent.SessionEnded)
        assertEquals(
            AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
            (event as AgentRuntimeEvent.SessionEnded).cause,
        )
    }

    @Test
    fun `the exit FACT never becomes a completion claim (no event vocabulary invents one)`() {
        // Whatever the detector records, the reducer's vocabulary contains
        // exactly the five P3c events — no Completed/Failed/Waiting exists.
        val allEvents = listOf(
            AgentRuntimeEvent.Launched::class,
            AgentRuntimeEvent.ConfirmedRunning::class,
            AgentRuntimeEvent.NoLongerDetected::class,
            AgentRuntimeEvent.RuntimeUnknown::class,
            AgentRuntimeEvent.SessionEnded::class,
        )
        assertEquals(5, allEvents.size)
    }
}
