package app.pocketshell.terminal

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * M7.2 P3c — the runtime transition/event SEAM.
 *
 * Relationship to the TWO upstream authorities (Parts G/I — ownership is
 * everything in this phase):
 *
 *   TerminalSessionManager   the ONE lifecycle authority — this engine only
 *                            READS its authoritative `sessions` StateFlow;
 *   RuntimeAgentDetector     the ONLY runtime evidence producer — this
 *                            engine only READS its `observations` StateFlow.
 *
 * This engine is an OBSERVER of both. It detects nothing (no /proc, no
 * matching, no correlation — those details stay behind P3b), owns no
 * lifecycle transitions, mutates no upstream state, adds NO polling of its
 * own (it reacts to upstream emissions — the 2-second tick remains the
 * detector's), and posts no notifications. Its single job: fold the
 * observed state changes through the PURE [AgentRuntimeTransitions] step
 * and publish the resulting DEDUPLICATED events on a replay-free
 * [SharedFlow] — so a future notification consumer subscribes to
 * transitions ("confirmed running", "no longer detected", "session ended")
 * and never re-fires on activity recreation or collector restart (Part H:
 * state is not event; late collectors get no replay).
 *
 * Race safety (Part G): ONE collector coroutine processes BOTH inputs
 * sequentially, so a stale detector observation can never interleave with
 * the terminal edge that invalidates it — a session that has ended is
 * removed from the tracked memory in the SAME reduction chain, and any
 * observation arriving for it afterwards is rejected by the reducer's
 * staleness gate. StateFlow inputs additionally make the engine
 * restart-safe: its first collection delivers the CURRENT authoritative
 * state (no synthetic events for transitions it never observed — the
 * memory starts empty and only NEW transitions derive events).
 *
 * Observability (Part I — consistent with P3b's existing-logs channel):
 * every EMITTED event logs one structured Log.d line (session, agent,
 * transition, evidence). No per-tick logging exists — the reducer emits
 * only on transitions, so silence IS the no-news signal. No debugging UI.
 */
object AgentRuntimeEventEngine {

    private const val LOG_TAG = "AgentRuntimeEvents"

    /**
     * The event buffer size, mirroring the manager's lifecycle-event
     * discipline: replay stays ZERO (a late collector must never re-see old
     * transitions — the future notification layer must not re-fire merely
     * because a collector restarted), and a pathological storm while no
     * collector keeps up would drop only beyond this bound, with every drop
     * logged (nothing silently swallowed).
     */
    const val EVENT_BUFFER_CAPACITY: Int = 64

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    // The replay/overflow policy in exactly one place: replay stays ZERO
    // (Part H — late collectors never re-see old transitions), the buffer
    // absorbs bursts, and overflow SUSPENDs the emitter rather than losing
    // events silently (the producer here is the single sequential monitor).
    private val _events = MutableSharedFlow<AgentRuntimeEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /**
     * The deduplicated transition-event stream (Part H). Edge-triggered:
     * every emission IS a transition; identical-state re-observations
     * (the detector's 2s tick re-delivering RUNNING) never appear here.
     * No replay for late collectors.
     */
    val events: SharedFlow<AgentRuntimeEvent> = _events.asSharedFlow()

    /**
     * Start the engine exactly once per process (idempotent; safe to call
     * from the manager's spawn path). Does nothing on a second call.
     */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { monitor() }
        Log.i(LOG_TAG, "agent runtime event engine started (consumes P3b evidence + session state; posts no notifications)")
    }

    /** Forcibly stop the engine (test isolation only; production never stops it). */
    internal fun stopForTesting() {
        started.set(false)
        scope.coroutineContext.cancelChildren()
    }

    /**
     * The monitor: ONE sequential collector over the manager's
     * authoritative session state and the detector's observations. Every
     * emission of either flow folds BOTH current inputs through the pure
     * reducer (the reduction is idempotent for unchanged inputs — the
     * memory makes re-delivery a no-op), so ordering between the two
     * sources can never fabricate or duplicate a transition.
     */
    private suspend fun monitor() {
        var memory = AgentRuntimeEventMemory()
        combine(
            TerminalSessionManager.sessions,
            RuntimeAgentDetector.observations,
        ) { entries, observations -> entries to observations }
            .collect { (entries, observations) ->
                val nowMs = System.currentTimeMillis()
                val sessionsOutcome = AgentRuntimeTransitions.reduce(
                    memory = memory,
                    input = AgentEventInput.SessionsChanged(
                        live = liveAgentLaunches(entries),
                        finished = finishedAgentLaunches(entries),
                    ),
                    nowMs = nowMs,
                )
                val observationsOutcome = AgentRuntimeTransitions.reduce(
                    memory = sessionsOutcome.memory,
                    input = AgentEventInput.ObservationsChanged(observations),
                    nowMs = nowMs,
                )
                memory = observationsOutcome.memory
                (sessionsOutcome.events + observationsOutcome.events).forEach(::emitEvent)
            }
    }

    /**
     * The LIVE sessions classified exactly [LaunchIdentity.KnownAgent] —
     * the same eligibility line as the P3b scanner, extracted through the
     * same P3a resolver. Plain shells (identity null), known non-agent
     * tools and custom/unknown launchers never reach the event layer.
     */
    private fun liveAgentLaunches(
        entries: List<TerminalSessionManager.SessionEntry>,
    ): List<TrackedAgentLaunch> =
        entries.mapNotNull { entry ->
            if (entry.isFinished) return@mapNotNull null
            val identity = LaunchIdentity.of(entry.origin, entry.agent)
            if (identity !is LaunchIdentity.KnownAgent) return@mapNotNull null
            TrackedAgentLaunch(sessionId = entry.id, agent = identity)
        }

    /**
     * The FINISHED sessions that carried a known-agent launch identity,
     * with their waitpid-proven DIRECT-child exit status (the session's
     * structured status — never the agent's completion).
     */
    private fun finishedAgentLaunches(
        entries: List<TerminalSessionManager.SessionEntry>,
    ): Map<Long, FinishedAgentLaunch> =
        entries.mapNotNull { entry ->
            if (!entry.isFinished) return@mapNotNull null
            val identity = LaunchIdentity.of(entry.origin, entry.agent)
            if (identity !is LaunchIdentity.KnownAgent) return@mapNotNull null
            // The P2 invariant: a FINISHED lifecycle state always carries
            // its exit status (private-constructor guarantee).
            FinishedAgentLaunch(
                sessionId = entry.id,
                agent = identity,
                exitStatus = entry.exitStatus ?: return@mapNotNull null,
            )
        }.associateBy { it.sessionId }

    /** Publish one event; every drop is logged (nothing silently swallowed). */
    private fun emitEvent(event: AgentRuntimeEvent) {
        if (!_events.tryEmit(event)) {
            Log.w(LOG_TAG, "event dropped (buffer overflow, no collector keeping up): ${describe(event)}")
            return
        }
        Log.d(LOG_TAG, describe(event))
    }

    /** The one-line structured diagnostic for an event (Part I). */
    private fun describe(event: AgentRuntimeEvent): String = when (event) {
        is AgentRuntimeEvent.Launched ->
            "session ${event.sessionId} LAUNCHED agent=${event.agent.displayName} (launcher=${event.agent.launcherId})"
        is AgentRuntimeEvent.ConfirmedRunning ->
            "session ${event.sessionId} CONFIRMED_RUNNING agent=${event.agent.displayName} " +
                "from=${event.from ?: "(birth)"} (pids=${event.evidence.pids}, grade=${event.evidence.grade})"
        is AgentRuntimeEvent.NoLongerDetected ->
            "session ${event.sessionId} NO_LONGER_DETECTED agent=${event.agent.displayName} " +
                "from=${event.from} (last pids=${event.lastEvidence?.pids ?: "none"}) — disappearance is not completion"
        is AgentRuntimeEvent.RuntimeUnknown ->
            "session ${event.sessionId} RUNTIME_UNKNOWN agent=${event.agent.displayName} from=${event.from}"
        is AgentRuntimeEvent.SessionEnded ->
            "session ${event.sessionId} SESSION_ENDED agent=${event.agent.displayName} " +
                "cause=${event.cause} lastState=${event.lastState ?: "(never observed)"}"
    }
}
