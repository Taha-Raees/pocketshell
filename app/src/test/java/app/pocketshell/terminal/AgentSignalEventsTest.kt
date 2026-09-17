package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P10 — the engine's signal-edge fold (the attention axis of the
 * transition matrix): attention raised exactly once per new signal,
 * withdrawal on every clearing kind, the turn story (standalone vs
 * attention-ending), session-terminal withdrawal, re-delivery immunity,
 * and multi-session isolation. All PURE.
 */
class AgentSignalEventsTest {

    // ------------------------------------------------------------ fixtures

    private fun agent(name: String = "Claude Code", id: String = "claude") =
        LaunchIdentity.KnownAgent(launcherId = id, displayName = name, command = id)

    private fun signal(
        lineIndex: Int,
        kind: AgentSignalBridge.AgentSignalKind,
        agent: String = "claude",
        data: String = "",
    ) = AgentSignalBridge.SignalRecord(
        agent = agent, kind = kind, seq = lineIndex,
        parentPid = 500, parentStartTicks = 9000, data = data, lineIndex = lineIndex,
    )

    private fun observation(
        sessionId: Long,
        state: AgentRuntimeState = AgentRuntimeState.RUNNING,
        newSignals: List<AgentSignalBridge.SignalRecord> = emptyList(),
        signalCount: Int = newSignals.size,
        attention: AgentSignalBridge.AgentAttentionPhase =
            AgentSignalBridge.AgentAttentionPhase.NONE,
        identity: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeDetection.Observation(
        sessionId = sessionId,
        state = state,
        evidence = if (state == AgentRuntimeState.RUNNING) {
            AgentRuntimeDetection.ProcessEvidence(listOf(500), AgentMatchedBy.LAUNCH_ANCHOR, 1_000L)
        } else null,
        everObservedRunning = true,
        updatedAtMs = 1_000L,
        newSignals = newSignals,
        signalCount = signalCount,
        attention = attention,
        agent = identity,
    )

    private fun tracked(
        sessionId: Long = 1L,
        lastState: AgentRuntimeState? = AgentRuntimeState.RUNNING,
        attention: AgentSignalBridge.AgentAttentionPhase =
            AgentSignalBridge.AgentAttentionPhase.NONE,
        attentionSignalIndex: Int = -1,
        lastSignalIndex: Int = -1,
    ) = AgentRuntimeEventMemory(
        tracked = mapOf(
            sessionId to AgentRuntimeEventMemory.TrackedAgentSession(
                agent = agent(), lastState = lastState, lastEvidence = null,
                attention = attention, attentionSignalIndex = attentionSignalIndex,
                lastSignalIndex = lastSignalIndex,
            ),
        ),
    )

    private fun reduce(
        memory: AgentRuntimeEventMemory,
        input: AgentEventInput,
        nowMs: Long = 5_000L,
    ): AgentRuntimeTransitions.Outcome = AgentRuntimeTransitions.reduce(memory, input, nowMs)

    private fun raised(events: List<AgentRuntimeEvent>) =
        events.filterIsInstance<AgentRuntimeEvent.AgentAttentionRaised>()

    private fun cleared(events: List<AgentRuntimeEvent>) =
        events.filterIsInstance<AgentRuntimeEvent.AgentAttentionCleared>()

    private fun turns(events: List<AgentRuntimeEvent>) =
        events.filterIsInstance<AgentRuntimeEvent.AgentTurnSignalled>()

    // ------------------------------------------------------------- the fold

    @Test
    fun `an attention signal raises exactly once per new prompt`() {
        val outcome = reduce(
            memory = tracked(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)))),
            ),
        )
        assertEquals(1, raised(outcome.events).size)
        assertEquals(
            AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST,
            raised(outcome.events).first().attention,
        )
        // re-delivery of the SAME tail emits nothing
        val again = reduce(
            memory = outcome.memory,
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)))),
            ),
        )
        assertEquals(0, raised(again.events).size)
    }

    @Test
    fun `a second distinct permission prompt re-raises - new signal, new story`() {
        val first = reduce(
            memory = tracked(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)))),
            ),
        )
        val second = reduce(
            memory = first.memory,
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(1, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)))),
            ),
        )
        assertEquals(1, raised(second.events).size)
    }

    @Test
    fun `working withdraws a pending attention with RESUMED_WORK`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, attentionSignalIndex = 0),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(1, AgentSignalBridge.AgentSignalKind.WORKING)))),
            ),
        )
        assertEquals(1, cleared(outcome.events).size)
        assertEquals(
            AgentRuntimeEvent.AgentAttentionCleared.Cause.RESUMED_WORK,
            cleared(outcome.events).first().cause,
        )
        assertEquals(0, raised(outcome.events).size)
    }

    @Test
    fun `a turn that ends a pending attention emits the withdrawal ONLY - no double surface`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, attentionSignalIndex = 0),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(1, AgentSignalBridge.AgentSignalKind.TURN_COMPLETE)))),
            ),
        )
        assertEquals(1, cleared(outcome.events).size)
        assertEquals(
            AgentRuntimeEvent.AgentAttentionCleared.Cause.TURN_ENDED,
            cleared(outcome.events).first().cause,
        )
        assertEquals(0, turns(outcome.events).size)
    }

    @Test
    fun `a turn with no pending attention stands alone as the turn fact`() {
        val outcome = reduce(
            memory = tracked(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.TURN_COMPLETE)))),
            ),
        )
        assertEquals(1, turns(outcome.events).size)
        assertEquals(0, cleared(outcome.events).size)
    }

    @Test
    fun `signals fold in append order within one tick`() {
        // permission raised, then cleared by working, then a turn: exactly
        // Raised -> Cleared(RESUMED_WORK) -> Turn, in that order.
        val outcome = reduce(
            memory = tracked(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(
                    1L to observation(
                        1L,
                        newSignals = listOf(
                            signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST),
                            signal(1, AgentSignalBridge.AgentSignalKind.WORKING),
                            signal(2, AgentSignalBridge.AgentSignalKind.TURN_COMPLETE),
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf("raised", "cleared", "turn"), outcome.events.map {
            when (it) {
                is AgentRuntimeEvent.AgentAttentionRaised -> "raised"
                is AgentRuntimeEvent.AgentAttentionCleared -> "cleared"
                is AgentRuntimeEvent.AgentTurnSignalled -> "turn"
                else -> "other"
            }
        })
    }

    @Test
    fun `a session terminal edge withdraws pending attention before SessionEnded`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.INPUT_REQUIRED, attentionSignalIndex = 0),
            input = AgentEventInput.SessionsChanged(
                live = emptyList(),
                finished = mapOf(1L to FinishedAgentLaunch(1L, agent(), ExitStatus.Exited(0))),
            ),
        )
        assertEquals(1, cleared(outcome.events).size)
        assertEquals(
            AgentRuntimeEvent.AgentAttentionCleared.Cause.SESSION_TERMINATED,
            cleared(outcome.events).first().cause,
        )
        assertTrue(outcome.events.last() is AgentRuntimeEvent.SessionEnded)
    }

    @Test
    fun `NOT_RUNNING withdraws pending attention - a dead agent waits on no one`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, attentionSignalIndex = 0),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, state = AgentRuntimeState.NOT_RUNNING)),
            ),
        )
        val clear = cleared(outcome.events)
        assertEquals(1, clear.size)
        assertEquals(
            AgentRuntimeEvent.AgentAttentionCleared.Cause.SESSION_TERMINATED,
            clear.first().cause,
        )
        assertEquals(1, outcome.events.filterIsInstance<AgentRuntimeEvent.NoLongerDetected>().size)
    }

    @Test
    fun `attention stays across an ambiguous UNKNOWN - uncertainty is not withdrawal`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, attentionSignalIndex = 0),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, state = AgentRuntimeState.UNKNOWN)),
            ),
        )
        assertEquals(0, cleared(outcome.events).size)
    }

    @Test
    fun `signals never disturb the four-state dedup - same state, new signal, no runtime event`() {
        val outcome = reduce(
            memory = tracked(),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, state = AgentRuntimeState.RUNNING, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.WORKING)))),
            ),
        )
        assertEquals(0, outcome.events.size) // working with no pending attention is silent
    }

    @Test
    fun `multi-session isolation - session 2's permission never raises for session 1`() {
        val memory = AgentRuntimeEventMemory(
            tracked = mapOf(
                1L to AgentRuntimeEventMemory.TrackedAgentSession(agent("A", "a"), AgentRuntimeState.RUNNING, null),
                2L to AgentRuntimeEventMemory.TrackedAgentSession(agent("B", "b"), AgentRuntimeState.RUNNING, null),
            ),
        )
        val outcome = reduce(
            memory = memory,
            input = AgentEventInput.ObservationsChanged(
                mapOf(
                    2L to observation(
                        2L,
                        identity = agent("B", "b"),
                        newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)),
                    ),
                ),
            ),
        )
        assertEquals(1, raised(outcome.events).size)
        assertEquals(2L, raised(outcome.events).first().sessionId)
        assertEquals("B", raised(outcome.events).first().agent.displayName)
    }

    @Test
    fun `an agent-declared session_end withdraws attention with AGENT_SESSION_ENDED`() {
        val outcome = reduce(
            memory = tracked(attention = AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST, attentionSignalIndex = 0),
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(1, AgentSignalBridge.AgentSignalKind.SESSION_END)))),
            ),
        )
        assertEquals(1, cleared(outcome.events).size)
        assertEquals(
            AgentRuntimeEvent.AgentAttentionCleared.Cause.AGENT_SESSION_ENDED,
            cleared(outcome.events).first().cause,
        )
    }

    @Test
    fun `attention survives the state arms - the fold precedes and composes with them`() {
        // UNKNOWN -> RUNNING with an attention signal in the same tick: both
        // stories fire (ConfirmedRunning AND the raise), in fold order.
        val memory = tracked(lastState = AgentRuntimeState.UNKNOWN)
        val outcome = reduce(
            memory = memory,
            input = AgentEventInput.ObservationsChanged(
                mapOf(1L to observation(1L, newSignals = listOf(signal(0, AgentSignalBridge.AgentSignalKind.PERMISSION_REQUEST)))),
            ),
        )
        assertEquals(1, outcome.events.filterIsInstance<AgentRuntimeEvent.ConfirmedRunning>().size)
        assertEquals(1, raised(outcome.events).size)
    }
}
