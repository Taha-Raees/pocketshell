package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P3c — the pure transition matrix of [AgentRuntimeTransitions]
 * (the phase mandate's PART J, arm 1): every mandated transition, the
 * deduplication guarantees, the reappearance story, the unknown-safety
 * story, session closure with stale-observation rejection, the non-agent
 * silence, and multi-session isolation. All PURE: no clock (nowMs is a
 * parameter), no I/O, no Android, no coroutine.
 *
 * The engine's structural rules (no notifications, no /proc, observer-only,
 * replay-free stream, no own polling) are pinned separately in
 * AgentRuntimeEventEngineIntegrationTest, per the established
 * source-reading technique.
 */
class AgentRuntimeEventsTest {

    // ------------------------------------------------------------ fixtures

    private fun agent(name: String = "Kilo Code", id: String = "kilo") =
        LaunchIdentity.KnownAgent(
            launcherId = id,
            displayName = name,
            command = "kilo --yolo",
        )

    private fun live(sessionId: Long, agent: LaunchIdentity.KnownAgent = agent()) =
        TrackedAgentLaunch(sessionId, agent)

    private fun finished(
        sessionId: Long,
        agent: LaunchIdentity.KnownAgent = agent(),
        code: Int = 0,
    ) = sessionId to FinishedAgentLaunch(sessionId, agent, ExitStatus.Exited(code))

    private fun evidence(vararg pids: Int) = AgentRuntimeDetection.ProcessEvidence(
        pids = pids.toList(),
        grade = AgentMatchedBy.PROCFS_EXE,
        observedAtMs = 1_000L,
    )

    private fun observation(
        sessionId: Long,
        state: AgentRuntimeState,
        processEvidence: AgentRuntimeDetection.ProcessEvidence? = null,
    ) = AgentRuntimeDetection.Observation(
        sessionId = sessionId,
        state = state,
        evidence = processEvidence,
        everObservedRunning = state == AgentRuntimeState.RUNNING ||
            state == AgentRuntimeState.NOT_RUNNING,
        updatedAtMs = 1_000L,
    )

    private fun sessionsInput(
        live: List<TrackedAgentLaunch>,
        finished: List<Pair<Long, FinishedAgentLaunch>> = emptyList(),
    ) = AgentEventInput.SessionsChanged(live, finished.toMap())

    private fun observationsInput(
        vararg observations: AgentRuntimeDetection.Observation,
    ) = AgentEventInput.ObservationsChanged(
        observations.associateBy { it.sessionId },
    )

    private fun reduce(
        memory: AgentRuntimeEventMemory = AgentRuntimeEventMemory(),
        input: AgentEventInput,
        nowMs: Long = 5_000L,
    ): AgentRuntimeTransitions.Outcome = AgentRuntimeTransitions.reduce(memory, input, nowMs)

    // -------------------------------------------------- basic transitions

    @Test
    fun `a new known-agent launch emits exactly one Launched event`() {
        val outcome = reduce(input = sessionsInput(live = listOf(live(1))))
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.Launched
        assertEquals(1L, event.sessionId)
        assertEquals("kilo", event.agent.launcherId)
        assertEquals("Kilo Code", event.agent.displayName)
        assertEquals(5_000L, event.occurredAtMs)
        assertEquals(1L, outcome.memory.tracked.keys.single())
    }

    @Test
    fun `a re-delivered identical sessions input emits nothing - state is not event`() {
        val first = reduce(input = sessionsInput(live = listOf(live(1))))
        val second = reduce(first.memory, sessionsInput(live = listOf(live(1))))
        assertTrue(second.events.isEmpty())
        assertEquals(first.memory, second.memory)
    }

    @Test
    fun `the first UNKNOWN observation emits nothing - the birth baseline is silent`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(launch.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        assertTrue(outcome.events.isEmpty())
        // The birth UNKNOWN derived no event, so the memory's last-event
        // state stays null — "no runtime story told yet" (and therefore no
        // NOT_RUNNING can ever be derived from it).
        assertEquals(null, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `UNKNOWN to RUNNING emits exactly one ConfirmedRunning carrying the evidence`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val unknown = reduce(launch.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        val outcome = reduce(
            unknown.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(42, 43))),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.ConfirmedRunning
        assertEquals(1L, event.sessionId)
        // from == null: no runtime EVENT preceded the confirmation (the
        // silent birth-UNKNOWN ticks are not a story the consumer ever saw).
        assertEquals(null, event.from)
        assertEquals(listOf(42, 43), event.evidence.pids)
        assertEquals(AgentMatchedBy.PROCFS_EXE, event.evidence.grade)
        assertEquals(AgentRuntimeState.RUNNING, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `the very first observation may already be RUNNING - from birth, one ConfirmedRunning`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.ConfirmedRunning
        assertEquals(null, event.from)
        assertEquals(listOf(7), event.evidence.pids)
    }

    @Test
    fun `RUNNING re-observed four times emits exactly one event - the deduplication core`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        assertEquals(1, confirmed.events.size)
        for (tick in 2..4) {
            val again = reduce(
                confirmed.memory,
                observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
            )
            assertTrue("tick $tick must not re-emit while RUNNING continues", again.events.isEmpty())
        }
    }

    @Test
    fun `RUNNING to NOT_RUNNING emits NoLongerDetected carrying the last evidence`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7, 9))),
        )
        val outcome = reduce(
            confirmed.memory,
            observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.NoLongerDetected
        assertEquals(1L, event.sessionId)
        assertEquals(AgentRuntimeState.RUNNING, event.from)
        assertEquals(listOf(7, 9), event.lastEvidence?.pids)
        assertEquals(AgentRuntimeState.NOT_RUNNING, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `NOT_RUNNING re-observed emits nothing`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val gone = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        val stillGone = reduce(gone.memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        assertTrue(stillGone.events.isEmpty())
    }

    @Test
    fun `RUNNING to UNKNOWN emits RuntimeUnknown - honest uncertainty, no fake completion`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val outcome = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.RuntimeUnknown
        assertEquals(1L, event.sessionId)
        assertEquals(AgentRuntimeState.RUNNING, event.from)
        assertEquals(AgentRuntimeState.UNKNOWN, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `UNKNOWN re-observed emits nothing - no event storm from detector uncertainty`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val unknown = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        assertEquals(1, unknown.events.size)
        val again = reduce(unknown.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        val andAgain = reduce(again.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        assertTrue(andAgain.events.isEmpty())
    }

    @Test
    fun `NOT_RUNNING to UNKNOWN emits RuntimeUnknown - evidence turned ambiguous after proven absence`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val gone = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        val outcome = reduce(gone.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        assertEquals(1, outcome.events.size)
        assertTrue(outcome.events.single() is AgentRuntimeEvent.RuntimeUnknown)
    }

    @Test
    fun `UNKNOWN to NOT_RUNNING emits NoLongerDetected - the evidence-holding UNKNOWN flavor`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val unknown = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        val outcome = reduce(unknown.memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.NoLongerDetected
        assertEquals(AgentRuntimeState.UNKNOWN, event.from)
        assertEquals(listOf(7), event.lastEvidence?.pids)
    }

    // ------------------------------------------------------- reappearance

    @Test
    fun `UNKNOWN RUNNING NOT_RUNNING RUNNING yields the three meaningful events of a restart`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        var memory = launch.memory
        val seen = mutableListOf<AgentRuntimeEvent>()

        val first = reduce(memory, observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))))
        memory = first.memory; seen += first.events
        val second = reduce(memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        memory = second.memory; seen += second.events
        val third = reduce(memory, observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(11))))
        memory = third.memory; seen += third.events

        assertEquals(3, seen.size)
        assertTrue(seen[0] is AgentRuntimeEvent.ConfirmedRunning)
        assertEquals(listOf(7), (seen[0] as AgentRuntimeEvent.ConfirmedRunning).evidence.pids)
        assertTrue(seen[1] is AgentRuntimeEvent.NoLongerDetected)
        assertTrue(seen[2] is AgentRuntimeEvent.ConfirmedRunning)
        assertEquals(AgentRuntimeState.NOT_RUNNING, (seen[2] as AgentRuntimeEvent.ConfirmedRunning).from)
        assertEquals(listOf(11), (seen[2] as AgentRuntimeEvent.ConfirmedRunning).evidence.pids)
    }

    @Test
    fun `UNKNOWN to RUNNING after an unknown stretch works exactly like the first confirmation`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val unknown = reduce(confirmed.memory, observationsInput(observation(1, AgentRuntimeState.UNKNOWN)))
        val outcome = reduce(
            unknown.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(15))),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.ConfirmedRunning
        assertEquals(AgentRuntimeState.UNKNOWN, event.from)
        assertEquals(listOf(15), event.evidence.pids)
    }

    // --------------------------------------------------- contract guards

    @Test
    fun `a RUNNING observation without evidence is rejected - no claim without a process`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(launch.memory, observationsInput(observation(1, AgentRuntimeState.RUNNING, null)))
        assertTrue(outcome.events.isEmpty())
        assertEquals(null, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `a first NOT_RUNNING observation is rejected - no absence without proven presence`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(launch.memory, observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)))
        assertTrue(outcome.events.isEmpty())
        assertEquals(null, outcome.memory.tracked.getValue(1L).lastState)
    }

    @Test
    fun `a NOT_APPLICABLE observation is ignored - never published for eligible sessions`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(launch.memory, observationsInput(observation(1, AgentRuntimeState.NOT_APPLICABLE)))
        assertTrue(outcome.events.isEmpty())
        assertEquals(null, outcome.memory.tracked.getValue(1L).lastState)
    }

    // ---------------------------------------------------- session closure

    @Test
    fun `a finished agent session emits SessionEnded with the session's own exit status`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val outcome = reduce(
            confirmed.memory,
            sessionsInput(live = emptyList(), finished = listOf(finished(1, code = 3))),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.SessionEnded
        assertEquals(1L, event.sessionId)
        assertEquals(AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED, event.cause)
        assertEquals(ExitStatus.Exited(3), event.sessionExitStatus)
        assertEquals(AgentRuntimeState.RUNNING, event.lastState)
        assertTrue(outcome.memory.tracked.isEmpty())
        assertEquals(setOf(1L), outcome.memory.ended)
    }

    @Test
    fun `a removed-while-running agent session emits SessionEnded with the REMOVED cause`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val outcome = reduce(confirmed.memory, sessionsInput(live = emptyList()))
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.SessionEnded
        assertEquals(AgentRuntimeEvent.SessionEnded.Cause.SESSION_REMOVED, event.cause)
        assertEquals(null, event.sessionExitStatus)
        assertEquals(AgentRuntimeState.RUNNING, event.lastState)
    }

    @Test
    fun `a session that ends before any observation still gets Launched then SessionEnded`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val outcome = reduce(
            launch.memory,
            sessionsInput(live = emptyList(), finished = listOf(finished(1, code = 0))),
        )
        assertEquals(1, outcome.events.size)
        val event = outcome.events.single() as AgentRuntimeEvent.SessionEnded
        assertEquals(AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED, event.cause)
        assertEquals(null, event.lastState)
    }

    @Test
    fun `finish followed by removal emits exactly one SessionEnded`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val done = reduce(
            launch.memory,
            sessionsInput(live = emptyList(), finished = listOf(finished(1, code = 0))),
        )
        val removed = reduce(done.memory, sessionsInput(live = emptyList()))
        assertTrue(removed.events.isEmpty())
        assertEquals(setOf(1L), removed.memory.ended)
    }

    @Test
    fun `a stale detector observation after closure produces no event - the Part G rule`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val ended = reduce(confirmed.memory, sessionsInput(live = emptyList()))
        // The forbidden sequence: session closed, THEN a stale RUNNING
        // result arrives -> must NOT fabricate "confirmed running".
        val stale = reduce(
            ended.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(99))),
        )
        assertTrue(stale.events.isEmpty())
        assertTrue(stale.memory.tracked.isEmpty())
    }

    @Test
    fun `a stale detector observation after REMOVAL produces no event`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))))
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
        )
        val removed = reduce(confirmed.memory, sessionsInput(live = emptyList(), finished = emptyList()))
        val stale = reduce(
            removed.memory,
            observationsInput(observation(1, AgentRuntimeState.NOT_RUNNING)),
        )
        assertTrue(stale.events.isEmpty())
    }

    // -------------------------------------------------- non-agent silence

    @Test
    fun `non-agent launches never reach the event layer - plain shell, tool, custom tool`() {
        // The seam extracts ONLY KnownAgent launches; at the pure level a
        // sessions input without them is indistinguishable from "nothing
        // happened" — and observations for never-tracked ids are rejected.
        val nothing = reduce(input = sessionsInput(live = emptyList()))
        assertTrue(nothing.events.isEmpty())

        val foreignObservation = reduce(
            AgentRuntimeEventMemory(),
            observationsInput(observation(77, AgentRuntimeState.RUNNING, evidence(5))),
        )
        assertTrue(foreignObservation.events.isEmpty())
        assertTrue(foreignObservation.memory.tracked.isEmpty())
    }

    // -------------------------------------------------- multiple sessions

    @Test
    fun `two agent sessions transition independently - no event from A may affect B`() {
        val agentA = agent(name = "Claude Code", id = "claude")
        val agentB = agent(name = "Codex CLI", id = "codex")
        val launch = reduce(
            input = sessionsInput(live = listOf(live(1, agentA), live(2, agentB))),
        )
        assertEquals(
            listOf(1L, 2L),
            launch.events.map { (it as AgentRuntimeEvent.Launched).sessionId },
        )
        // B's first observation stays silent while A confirms running.
        val aRuns = reduce(
            launch.memory,
            observationsInput(
                observation(1, AgentRuntimeState.RUNNING, evidence(10)),
                observation(2, AgentRuntimeState.UNKNOWN),
            ),
        )
        assertEquals(1, aRuns.events.size)
        assertEquals(1L, (aRuns.events.single() as AgentRuntimeEvent.ConfirmedRunning).sessionId)
        // A stops; B starts — exactly one event each, no cross-talk.
        val mixed = reduce(
            aRuns.memory,
            observationsInput(
                observation(1, AgentRuntimeState.NOT_RUNNING),
                observation(2, AgentRuntimeState.RUNNING, evidence(20)),
            ),
        )
        assertEquals(2, mixed.events.size)
        assertTrue(mixed.events[0] is AgentRuntimeEvent.NoLongerDetected)
        assertEquals(1L, (mixed.events[0] as AgentRuntimeEvent.NoLongerDetected).sessionId)
        assertTrue(mixed.events[1] is AgentRuntimeEvent.ConfirmedRunning)
        assertEquals(2L, (mixed.events[1] as AgentRuntimeEvent.ConfirmedRunning).sessionId)
        assertEquals("codex", (mixed.events[1] as AgentRuntimeEvent.ConfirmedRunning).agent.launcherId)
    }

    @Test
    fun `ending session A leaves session B tracked and emitting`() {
        val agentA = agent(name = "Claude Code", id = "claude")
        val agentB = agent(name = "Codex CLI", id = "codex")
        val launch = reduce(input = sessionsInput(live = listOf(live(1, agentA), live(2, agentB))))
        val ended = reduce(
            launch.memory,
            sessionsInput(live = listOf(live(2, agentB)), finished = listOf(finished(1, agentA))),
        )
        assertEquals(1, ended.events.size)
        assertTrue(ended.events.single() is AgentRuntimeEvent.SessionEnded)
        assertEquals(setOf(2L), ended.memory.tracked.keys)
        val bRuns = reduce(
            ended.memory,
            observationsInput(observation(2, AgentRuntimeState.RUNNING, evidence(20))),
        )
        assertEquals(1, bRuns.events.size)
    }

    // -------------------------------------------- determinism and purity

    @Test
    fun `events within one input are ordered by session id`() {
        val agentA = agent(name = "Claude Code", id = "claude")
        val agentB = agent(name = "Codex CLI", id = "codex")
        val outcome = reduce(
            input = sessionsInput(live = listOf(live(9, agentB), live(4, agentA))),
        )
        assertEquals(listOf(4L, 9L), outcome.events.map { it.sessionId })
    }

    @Test
    fun `occurredAtMs always equals the nowMs parameter - no clock inside the reducer`() {
        val launch = reduce(input = sessionsInput(live = listOf(live(1))), nowMs = 123_456L)
        assertEquals(123_456L, launch.events.single().occurredAtMs)
        val confirmed = reduce(
            launch.memory,
            observationsInput(observation(1, AgentRuntimeState.RUNNING, evidence(7))),
            nowMs = 234_567L,
        )
        assertEquals(234_567L, confirmed.events.single().occurredAtMs)
    }

    @Test
    fun `reduce is pure - identical inputs produce identical outcomes and never mutate the input memory`() {
        val input = sessionsInput(live = listOf(live(1)))
        val first = AgentRuntimeTransitions.reduce(AgentRuntimeEventMemory(), input, 1L)
        val second = AgentRuntimeTransitions.reduce(AgentRuntimeEventMemory(), input, 1L)
        assertEquals(first, second)
        // distinct memory instances per call — the reducer allocates fresh
        // memory instead of mutating (data-class equality still holds).
        assertTrue(first.memory !== second.memory)

        val base = AgentRuntimeEventMemory()
        val snapshot = base.copy()
        AgentRuntimeTransitions.reduce(base, input, 1L)
        assertEquals(snapshot, base)
    }
}
