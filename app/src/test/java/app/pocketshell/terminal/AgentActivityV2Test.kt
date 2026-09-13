package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P6 — the AGENT ACTIVITY v2 pure-layer tests: the record-backed
 * terminal states (detection), their event derivation (reducer), and the
 * PTY activity surface (bell / output recency). Everything here is the
 * pure fold — no Android, no clock, no I/O (docs/M7.2-P6-AGENT-ACTIVITY-V2.md).
 */
class AgentActivityV2Test {

    // ---------------------------------------------------------- fixtures

    private fun agent(name: String = "Kilo") = LaunchIdentity.KnownAgent(
        launcherId = "kilo",
        displayName = name,
        command = "kilo",
    )

    private fun eligible(sessionId: Long = 7L) = AgentRuntimeDetection.EligibleAgentSession(
        sessionId = sessionId,
        rootPid = 100,
        token = "kilo",
        predetermined = agent(),
    )

    private fun emptySnapshot() = ProcfsSnapshot(emptyList())

    private fun records(exitStatus: Int?): Map<Long, AgentLaunchRecords.SessionRecords> =
        mapOf(
            7L to AgentLaunchRecords.SessionRecords(
                launch = AgentLaunchRecords.LaunchRecord(pid = 200, pgrp = 200, startTicks = 42, agent = "kilo"),
                exits = exitStatus?.let { listOf(AgentLaunchRecords.ExitRecord(status = it, agent = "kilo")) }
                    ?: emptyList(),
            ),
        )

    private fun observe(
        previous: Map<Long, AgentRuntimeDetection.Observation> = emptyMap(),
        exitStatus: Int? = null,
        snapshot: ProcfsSnapshot? = emptySnapshot(),
    ): AgentRuntimeDetection.Observation =
        AgentRuntimeDetection.compute(
            eligible = listOf(eligible()),
            previous = previous,
            snapshot = snapshot,
            nowMs = 1_000L,
            records = records(exitStatus),
        ).getValue(7L)

    private fun trackedRunning(
        sessionId: Long = 7L,
        activity: AgentRuntimeEventMemory.ActivityAnnouncement? = null,
    ) = AgentRuntimeEventMemory(
        tracked = mapOf(
            sessionId to AgentRuntimeEventMemory.TrackedAgentSession(
                agent = agent(),
                lastState = AgentRuntimeState.RUNNING,
                lastEvidence = AgentRuntimeDetection.ProcessEvidence(
                    pids = listOf(200),
                    grade = AgentMatchedBy.LAUNCH_ANCHOR,
                    observedAtMs = 900L,
                ),
                activity = activity,
            ),
        ),
    )

    private fun reduceObservations(
        memory: AgentRuntimeEventMemory,
        state: AgentRuntimeState,
        exitStatus: Int? = null,
    ): AgentRuntimeTransitions.Outcome {
        val observation = AgentRuntimeDetection.Observation(
            sessionId = 7L,
            state = state,
            evidence = null,
            everObservedRunning = true,
            updatedAtMs = 1_000L,
            agent = agent(),
            lastExit = exitStatus?.let { AgentLaunchRecords.ExitRecord(it, "kilo") },
        )
        return AgentRuntimeTransitions.reduce(
            memory = memory,
            input = AgentEventInput.ObservationsChanged(mapOf(7L to observation)),
            nowMs = 1_000L,
        )
    }

    // ------------------------------------------- detection: record-backed exits

    @Test
    fun `exit record zero yields EXITED_SUCCESS`() {
        assertEquals(AgentRuntimeState.EXITED_SUCCESS, observe(exitStatus = 0).state)
    }

    @Test
    fun `exit record nonzero yields EXITED_FAILED`() {
        assertEquals(AgentRuntimeState.EXITED_FAILED, observe(exitStatus = 1).state)
    }

    @Test
    fun `exit record signal encoding yields EXITED_STOPPED`() {
        assertEquals(AgentRuntimeState.EXITED_STOPPED, observe(exitStatus = 130).state)
        assertEquals(AgentRuntimeState.EXITED_STOPPED, observe(exitStatus = 143).state)
    }

    @Test
    fun `terminal state is sticky across ticks with and without a snapshot`() {
        val exited = observe(exitStatus = 0)
        val again = observe(previous = mapOf(7L to exited), exitStatus = 0)
        assertEquals(AgentRuntimeState.EXITED_SUCCESS, again.state)
        val noScan = AgentRuntimeDetection.compute(
            eligible = listOf(eligible()),
            previous = mapOf(7L to exited),
            snapshot = null,
            nowMs = 2_000L,
            records = records(0),
        ).getValue(7L)
        assertEquals("a failed /proc scan never un-exits a recorded agent", AgentRuntimeState.EXITED_SUCCESS, noScan.state)
    }

    @Test
    fun `exit record without a launch record is ignored as channel drift`() {
        val drift = mapOf(
            7L to AgentLaunchRecords.SessionRecords(
                launch = null,
                exits = listOf(AgentLaunchRecords.ExitRecord(status = 0, agent = "kilo")),
            ),
        )
        val observation = AgentRuntimeDetection.compute(
            eligible = listOf(eligible()),
            previous = emptyMap(),
            snapshot = emptySnapshot(),
            nowMs = 1_000L,
            records = drift,
        ).getValue(7L)
        assertTrue(
            "no launch -> no exit claim (state=${observation.state})",
            !observation.state.isExitedTerminal,
        )
    }

    // --------------------------------------- reducer: the AgentExited event

    @Test
    fun `EXITED observation emits AgentExited exactly once with the real status`() {
        val first = reduceObservations(trackedRunning(), AgentRuntimeState.EXITED_SUCCESS, exitStatus = 0)
        val exited = first.events.filterIsInstance<AgentRuntimeEvent.AgentExited>()
        assertEquals(listOf(0), exited.map { it.exitStatus })
        assertEquals(AgentRuntimeState.RUNNING, exited.single().from)

        // Re-delivered identical observation: no second event.
        val second = reduceObservations(first.memory, AgentRuntimeState.EXITED_SUCCESS, exitStatus = 0)
        assertTrue(second.events.none { it is AgentRuntimeEvent.AgentExited })
    }

    // ------------------------------- reducer: the PTY activity surface

    @Test
    fun `bell after output announces attention`() {
        val outcome = AgentRuntimeTransitions.reduce(
            memory = trackedRunning(),
            input = AgentEventInput.ActivityChanged(
                mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 500, bellAtMs = 800)),
            ),
            nowMs = 900L,
        )
        val working = outcome.events.filterIsInstance<AgentRuntimeEvent.WorkingChanged>().single()
        assertTrue(working.attentionRequested)
        assertTrue(!working.active)
    }

    @Test
    fun `output after the bell clears attention and announces working`() {
        val bell = AgentRuntimeTransitions.reduce(
            memory = trackedRunning(),
            input = AgentEventInput.ActivityChanged(
                mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 500, bellAtMs = 800)),
            ),
            nowMs = 900L,
        )
        val clear = AgentRuntimeTransitions.reduce(
            memory = bell.memory,
            input = AgentEventInput.ActivityChanged(
                mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 1_100, bellAtMs = 800)),
            ),
            nowMs = 1_200L,
        )
        val working = clear.events.filterIsInstance<AgentRuntimeEvent.WorkingChanged>().single()
        assertTrue(working.active)
        assertTrue(!working.attentionRequested)
    }

    @Test
    fun `output within the window announces working - stale output announces quiet`() {
        val busy = AgentRuntimeTransitions.reduce(
            memory = trackedRunning(),
            input = AgentEventInput.ActivityChanged(
                mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 4_900)),
            ),
            nowMs = 5_000L,
        )
        assertTrue(busy.events.filterIsInstance<AgentRuntimeEvent.WorkingChanged>().single().active)

        val quiet = AgentRuntimeTransitions.reduce(
            memory = busy.memory,
            input = AgentEventInput.ActivityChanged(
                mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 4_900)),
            ),
            nowMs = 11_000L,
        )
        val quietEvent = quiet.events.filterIsInstance<AgentRuntimeEvent.WorkingChanged>().single()
        assertTrue(!quietEvent.active)
        assertTrue(!quietEvent.attentionRequested)
    }

    @Test
    fun `identical pulses derive nothing (edge-only)`() {
        val pulse = mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 4_900))
        val first = AgentRuntimeTransitions.reduce(
            memory = trackedRunning(),
            input = AgentEventInput.ActivityChanged(pulse),
            nowMs = 5_000L,
        )
        val second = AgentRuntimeTransitions.reduce(
            memory = first.memory,
            input = AgentEventInput.ActivityChanged(pulse),
            nowMs = 5_400L,
        )
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun `activity is ignored for sessions without an announced running claim`() {
        for (memory in listOf(
            AgentRuntimeEventMemory(), // never tracked
            trackedRunning().copy(
                tracked = mapOf(
                    7L to AgentRuntimeEventMemory.TrackedAgentSession(
                        agent = agent(),
                        lastState = AgentRuntimeState.EXITED_SUCCESS,
                        lastEvidence = null,
                    ),
                ),
            ),
        )) {
            val outcome = AgentRuntimeTransitions.reduce(
                memory = memory,
                input = AgentEventInput.ActivityChanged(
                    mapOf(7L to SessionActivityPulse(sessionId = 7, lastOutputAtMs = 5_000, bellAtMs = 5_100)),
                ),
                nowMs = 5_200L,
            )
            assertTrue(outcome.events.isEmpty())
        }
    }

    @Test
    fun `transitions out of RUNNING clear the activity announcement`() {
        val announced = trackedRunning(
            activity = AgentRuntimeEventMemory.ActivityAnnouncement(active = true, attention = false),
        )
        val outcome = reduceObservations(announced, AgentRuntimeState.UNKNOWN)
        assertTrue(outcome.memory.tracked.getValue(7L).activity == null)
    }
}