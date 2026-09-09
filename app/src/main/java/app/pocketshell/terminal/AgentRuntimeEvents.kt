package app.pocketshell.terminal

/**
 * M7.2 P3c — the runtime TRANSITION/EVENT model (PURE layer).
 *
 * P3a established WHAT a session was launched as ([LaunchIdentity]).
 * P3b established whether the launched agent has runtime process evidence
 * RIGHT NOW ([AgentRuntimeState], published by [RuntimeAgentDetector]).
 * THIS file is the third level: converting MEANINGFUL CHANGES of that
 * evidence into structured, deduplicated internal events — the vocabulary a
 * future notification system can subscribe to without ever knowing that
 * /proc, process groups, PID ancestry, token matching or polling exist.
 *
 * Everything here is DATA plus PURE functions: no clock, no I/O, no Android
 * APIs, no coroutine, no shared state, no notification surface. The runtime
 * seam lives in AgentRuntimeEventEngine.kt and only ever OBSERVES the
 * manager's authoritative StateFlow and the detector's observations
 * StateFlow. Purity is what makes the transition matrix below exhaustively
 * unit-testable without a device.
 *
 * THE EVENT TRUTH MODEL (the phase mandate, Part C): an event is a FACT
 * ABOUT A TRANSITION, derived from
 *
 *     LaunchIdentity (spawn truth)  +  runtime detection state (P3b)
 *     +  session lifecycle state (P2, the ONE authority)
 *
 * and never from any independent scan: this layer consumes detector
 * results, it does not produce or reinterpret them.
 *
 * THE SEMANTIC DISTINCTIONS (Part C — NOT interchangeable, each separately
 * representable):
 *
 *   Agent launched                  Launched          — spawn truth (P3a)
 *   Agent confirmed running         ConfirmedRunning  — process evidence (P3b)
 *   Agent stopped being detected    NoLongerDetected  — proven absence (P3b)
 *   Agent runtime became unknown    RuntimeUnknown    — honest uncertainty (P3b)
 *   Agent session ended             SessionEnded      — session truth (P2)
 *
 * THE ONE FALSE CONVERSION THIS LAYER CAN NEVER MAKE (Part C, the hard
 * line): RUNNING → NOT_RUNNING is runtime DISAPPEARANCE ONLY. It is not
 * success, not failure, not completion, not "work finished" — the agent's
 * own exit status is structurally discarded by the launch chain (P3a truth
 * loss point 3) and no state in this vocabulary re-invents it. Completion
 * detection belongs to a later phase, on evidence that does not exist yet.
 *
 * Deliberately ABSENT: Completed, Success, FinishedSuccessfully,
 * WaitingForInput, Idle — the P0 audit's rejected tiers stand; no event in
 * this file claims anything the underlying evidence cannot prove.
 */

/**
 * One deduplicated runtime transition. Every event carries the full
 * identity block ([sessionId] + the P3a [agent] identity snapshot) so no
 * consumer ever needs a by-id re-lookup (which could race a concurrent
 * removal) and no display string is ever used as identity ([launcherId]
 * inside [agent] is the stable key). [occurredAtMs] is the wall-clock time
 * the transition was derived — passed INTO the pure reducer, never read
 * from a clock here.
 */
sealed class AgentRuntimeEvent {
    abstract val sessionId: Long
    abstract val agent: LaunchIdentity.KnownAgent
    abstract val occurredAtMs: Long

    /**
     * A session launched through a known-agent launcher appeared in the
     * manager's authoritative state. This is the P3a fact ("PocketShell
     * launched agent X in session N") — it makes NO runtime claim; the
     * runtime story starts at UNKNOWN and is told by the events below.
     */
    data class Launched(
        override val sessionId: Long,
        override val agent: LaunchIdentity.KnownAgent,
        override val occurredAtMs: Long,
    ) : AgentRuntimeEvent()

    /**
     * The detector's evidence for this session entered RUNNING: a
     * correlated process matched the agent's launch token RIGHT NOW.
     * [evidence] is the exact pids + match grade that caused the claim —
     * the event can always answer "what process caused this?". Emitted
     * exactly once per ENTRY into RUNNING (UNKNOWN→RUNNING,
     * NOT_RUNNING→RUNNING (reappearance), or the first scan already seeing
     * the agent); never re-emitted while the state remains RUNNING.
     */
    data class ConfirmedRunning(
        override val sessionId: Long,
        override val agent: LaunchIdentity.KnownAgent,
        /** The runtime state the session transitions OUT of (null = the first observation). */
        val from: AgentRuntimeState?,
        val evidence: AgentRuntimeDetection.ProcessEvidence,
        override val occurredAtMs: Long,
    ) : AgentRuntimeEvent()

    /**
     * The detector's state for this session left RUNNING (or an ambiguous
     * UNKNOWN that had held evidence) and is now NOT_RUNNING: the
     * previously-observed process is GONE. This claims runtime
     * disappearance ONLY — never success, failure, completion, or that any
     * work finished. [lastEvidence] is the most recent evidence the
     * detector had held for this session (what stopped being detected).
     */
    data class NoLongerDetected(
        override val sessionId: Long,
        override val agent: LaunchIdentity.KnownAgent,
        val from: AgentRuntimeState,
        val lastEvidence: AgentRuntimeDetection.ProcessEvidence?,
        override val occurredAtMs: Long,
    ) : AgentRuntimeEvent()

    /**
     * The detector's state for this session became UNKNOWN after having
     * been informative (RUNNING, or an evidence-holding UNKNOWN): the
     * current evidence cannot prove a state — scan failure, or previously
     * matched pids alive but no longer matching. This is honest
     * uncertainty, never dressed up as any other state, and never a
     * completion claim. Repeated UNKNOWN observations do NOT re-emit
     * (deduplication); UNKNOWN at session birth emits nothing at all (the
     * launch event already announced the expectation).
     */
    data class RuntimeUnknown(
        override val sessionId: Long,
        override val agent: LaunchIdentity.KnownAgent,
        val from: AgentRuntimeState,
        override val occurredAtMs: Long,
    ) : AgentRuntimeEvent()

    /**
     * The session that launched this agent reached a terminal lifecycle
     * state — derived from the manager's authoritative state (the ONE
     * lifecycle authority), never from a /proc tick. [cause] distinguishes
     * the two terminal shapes this architecture has:
     *
     *   - [Cause.SESSION_FINISHED]: the waitpid-proven exit of the session's
     *     DIRECT child (proot for guest sessions). [sessionExitStatus] is
     *     that DIRECT CHILD's structured exit status — it is NOT the
     *     agent's completion and must never be re-labeled as one (P3a
     *     truth-loss point 3).
     *   - [Cause.SESSION_REMOVED]: the user closed the tab (the entry left
     *     the manager's list); no exit status exists for this shape.
     *
     * [lastState] is the last runtime state the detector held when the
     * session ended (the runtime story simply stops here — the session
     * layer's fact owns the ending; no NOT_RUNNING is invented for it).
     * Emitted exactly once per session.
     */
    data class SessionEnded(
        override val sessionId: Long,
        override val agent: LaunchIdentity.KnownAgent,
        val cause: Cause,
        val sessionExitStatus: ExitStatus?,
        val lastState: AgentRuntimeState?,
        override val occurredAtMs: Long,
    ) : AgentRuntimeEvent() {
        enum class Cause {
            /** The session's direct child exited (waitpid-proven); [sessionExitStatus] is that child's status. */
            SESSION_FINISHED,

            /** The user closed the session's tab; no exit status exists. */
            SESSION_REMOVED,
        }
    }
}

/**
 * One launch the event engine may track — the LIVE session classified
 * exactly [LaunchIdentity.KnownAgent] by the P3a resolver (the same
 * eligibility line as the P3b scanner: plain shells, known non-agent tools
 * and custom/unknown launchers NEVER enter this layer, so they can never
 * produce an agent event).
 */
data class TrackedAgentLaunch(
    val sessionId: Long,
    val agent: LaunchIdentity.KnownAgent,
)

/**
 * A FINISHED session that had carried a known-agent launch identity, with
 * the waitpid-proven exit status of its DIRECT child ([ExitStatus] — the
 * session's structured status, never the agent's).
 */
data class FinishedAgentLaunch(
    val sessionId: Long,
    val agent: LaunchIdentity.KnownAgent,
    val exitStatus: ExitStatus,
)

/** The engine's inputs — pure projections extracted from the authoritative flows by the runtime seam. */
sealed class AgentEventInput {

    /**
     * The manager's session list changed. [live] lists every LIVE session
     * classified [LaunchIdentity.KnownAgent]; [finished] maps every
     * FINISHED such session to its recorded direct-child exit status. A
     * tracked session missing from BOTH maps was REMOVED (the user closed
     * its tab) — the only two ways a session leaves this layer.
     *
     * M7.2 P9: [finishedStatuses] carries the waitpid-proven exit status of
     * EVERY finished session (not only known-agent launches), so a
     * DISCOVERED session (tracked lazily by the observations arm below)
     * that reaches a real terminal state ends with the honest
     * SESSION_FINISHED cause and its real status — never mislabeled as a
     * tab removal. It changes nothing for sessions this layer never
     * tracked: the reducer consults it only for tracked ids.
     */
    data class SessionsChanged(
        val live: List<TrackedAgentLaunch>,
        val finished: Map<Long, FinishedAgentLaunch>,
        val finishedStatuses: Map<Long, ExitStatus> = emptyMap(),
    ) : AgentEventInput()

    /**
     * The detector's observation map changed. Keys exist exactly for
     * sessions the scanner currently tracks (live + KnownAgent); values
     * carry the four-state contract. Identical-state observations are
     * deduplicated HERE (Part F) — the reducer is the only emission path.
     */
    data class ObservationsChanged(
        val observations: Map<Long, AgentRuntimeDetection.Observation>,
    ) : AgentEventInput()
}

/**
 * Per-session transition memory — the minimum state that guarantees
 * "same state observation = no duplicate event" (Part F) without becoming
 * a database: one entry per LIVE tracked session (the last runtime state
 * an event was derived from, plus the most recent evidence for the
 * NoLongerDetected story), and the set of session ids that already reached
 * a terminal edge (they can never emit again — stale-observation
 * rejection, Part G). Process-scoped, in-memory, bounded by the number of
 * agent sessions that existed in the process lifetime.
 */
data class AgentRuntimeEventMemory(
    val tracked: Map<Long, TrackedAgentSession> = emptyMap(),
    val ended: Set<Long> = emptySet(),
) {
    data class TrackedAgentSession(
        val agent: LaunchIdentity.KnownAgent,
        /**
         * The last runtime state an EVENT was derived from — null until the
         * first runtime event (silent observations, like the birth UNKNOWN,
         * leave it null: a state the consumer never heard about must never
         * become the "from" of a later story, and an absence can never be
         * derived from a state that was never announced).
         */
        val lastState: AgentRuntimeState?,
        /** The most recent process evidence the detector held (null when none so far). */
        val lastEvidence: AgentRuntimeDetection.ProcessEvidence?,
    )
}

/**
 * The pure transition step (Parts C/E/F/G): fold one [AgentEventInput]
 * plus the previous [AgentRuntimeEventMemory] into the next memory and the
 * events that input yields. Pure: identical inputs always produce
 * identical outputs; no clock (nowMs is a parameter), no I/O, no state.
 *
 * THE TRANSITION MATRIX (Parts E/F — pinned exhaustively by
 * AgentRuntimeEventsTest):
 *
 *   input = SessionsChanged:
 *     live KnownAgent id, not tracked, not ended   -> Launched
 *         (once per session; a re-delivered identical list emits nothing)
 *     tracked id now in finished map               -> SessionEnded(SESSION_FINISHED)
 *     tracked id in neither live nor finished      -> SessionEnded(SESSION_REMOVED)
 *     id already ended                             -> nothing, ever
 *
 *   input = ObservationsChanged (only for tracked, not-ended sessions):
 *     state unchanged (RUNNING→RUNNING, UNKNOWN→UNKNOWN,
 *                      NOT_RUNNING→NOT_RUNNING)    -> nothing (Part F dedup;
 *                                                     RUNNING evidence is
 *                                                     refreshed silently)
 *     (none|UNKNOWN|NOT_RUNNING) -> RUNNING        -> ConfirmedRunning(evidence)
 *         (the reappearance arm NOT_RUNNING→RUNNING included — a new
 *          meaningful runtime transition, honestly re-emitted)
 *     X -> NOT_RUNNING, X != null                  -> NoLongerDetected(lastEvidence)
 *         (X == null is structurally impossible: the pure detector produces
 *          NOT_RUNNING only after proven presence; the defensive skip keeps
 *          the event honest even if that contract were ever violated)
 *     X -> UNKNOWN, X != null                      -> RuntimeUnknown
 *         (X == null — UNKNOWN at session birth — emits nothing: the launch
 *          event already announced the expectation; this is the anti-storm
 *          rule that keeps the first tick silent)
 *     -> NOT_APPLICABLE                            -> nothing (never published
 *                                                     for eligible sessions;
 *                                                     defensive skip)
 *
 *   STALENESS (Part G): an observation for a session that is not tracked
 *   (never an agent launch, or already ended) is rejected outright — a
 *   detector result arriving after session closure can never fabricate an
 *   event. The engine processes its inputs SEQUENTIALLY (one collector),
 *   so a stale RUNNING observation can never overtake the terminal edge
 *   that invalidates it.
 */
object AgentRuntimeTransitions {

    /** The memory after one input, plus the events derived from it (in deterministic order). */
    data class Outcome(
        val memory: AgentRuntimeEventMemory,
        val events: List<AgentRuntimeEvent>,
    )

    fun reduce(
        memory: AgentRuntimeEventMemory,
        input: AgentEventInput,
        nowMs: Long,
    ): Outcome = when (input) {
        is AgentEventInput.SessionsChanged -> reduceSessions(memory, input, nowMs)
        is AgentEventInput.ObservationsChanged -> reduceObservations(memory, input, nowMs)
    }

    /**
     * Session edges. Deterministic order: launches first (ascending session
     * id), then endings (ascending session id) — a session id is never
     * reused (the manager's monotonic ids), so the two groups are disjoint.
     */
    private fun reduceSessions(
        memory: AgentRuntimeEventMemory,
        input: AgentEventInput.SessionsChanged,
        nowMs: Long,
    ): Outcome {
        val events = mutableListOf<AgentRuntimeEvent>()
        var tracked = memory.tracked
        val ended = memory.ended.toMutableSet()
        val liveIds = input.live.map { it.sessionId }.toSet()

        // 1. New launches: live KnownAgent sessions this memory has never tracked.
        for (launch in input.live.sortedBy { it.sessionId }) {
            if (launch.sessionId in tracked || launch.sessionId in ended) continue
            tracked = tracked + (
                launch.sessionId to AgentRuntimeEventMemory.TrackedAgentSession(
                    agent = launch.agent,
                    lastState = null,
                    lastEvidence = null,
                )
                )
            events += AgentRuntimeEvent.Launched(launch.sessionId, launch.agent, nowMs)
        }

        // 2. Terminal edges for tracked sessions that left the live set —
        //    READ the session before removing it (each id is processed
        //    exactly once: the manager's session ids are monotonic and
        //    never reused, so the live and finished groups are disjoint).
        //    M7.2 P9: the cause consults [input.finishedStatuses] (EVERY
        //    finished session's real status), so a DISCOVERED session that
        //    finished ends as SESSION_FINISHED with its true status — the
        //    SESSION_REMOVED shape stays reserved for tab closures.
        for (sessionId in tracked.keys.sorted()) {
            if (sessionId in liveIds) continue
            val session = tracked[sessionId] ?: continue
            // The finish signal: the KnownAgent finished map (spawn-truth
            // launches, unchanged contract) OR the P9 all-sessions status
            // map (discovered sessions). The real waitpid status wins.
            val finishedStatus = input.finishedStatuses[sessionId]
                ?: input.finished[sessionId]?.exitStatus
            tracked = tracked - sessionId
            ended += sessionId
            events += if (finishedStatus != null) {
                AgentRuntimeEvent.SessionEnded(
                    sessionId = sessionId,
                    agent = session.agent,
                    cause = AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
                    sessionExitStatus = finishedStatus,
                    lastState = session.lastState,
                    occurredAtMs = nowMs,
                )
            } else {
                AgentRuntimeEvent.SessionEnded(
                    sessionId = sessionId,
                    agent = session.agent,
                    cause = AgentRuntimeEvent.SessionEnded.Cause.SESSION_REMOVED,
                    sessionExitStatus = null,
                    lastState = session.lastState,
                    occurredAtMs = nowMs,
                )
            }
        }

        // 3. A finished-but-untracked KnownAgent session (defensive: in this
        //    architecture the launch edge always precedes the terminal edge —
        //    the engine's inputs are sequential — so this arm never fires).
        for (sessionId in input.finished.keys.sorted()) {
            if (sessionId in ended || tracked.containsKey(sessionId)) continue
            val finished = input.finished[sessionId] ?: continue
            ended += sessionId
            events += AgentRuntimeEvent.SessionEnded(
                sessionId = sessionId,
                agent = finished.agent,
                cause = AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
                sessionExitStatus = finished.exitStatus,
                lastState = null,
                occurredAtMs = nowMs,
            )
        }

        return Outcome(
            memory = AgentRuntimeEventMemory(tracked = tracked, ended = ended),
            events = events,
        )
    }

    /**
     * Runtime edges — the deduplication core (Part F). Only CHANGES of the
     * detector's state derive events; identical re-observations (the 2s
     * polling tick re-delivering the same state) derive nothing.
     *
     * M7.2 P9 — DISCOVERY TRACKING (two new arms, the exact-discovery
     * counterpart of the launch edge):
     *
     *   LAZY TRACK: an observation for a session this memory has never
     *   tracked whose [Observation.agent] is RESOLVED tracks the session
     *   SILENTLY (no Launched event exists to emit — no launcher named the
     *   command; the process evidence IS the first fact) and falls through
     *   to the normal arms, so a first RUNNING observation emits
     *   ConfirmedRunning(from=null) — the "(birth)" arm the notification
     *   mapping already handles. Unresolved observations (UNKNOWN with no
     *   agent, the birth baseline of every discovery session) stay ignored
     *   — the anti-storm rule is unchanged.
     *
     *   IDENTITY SWITCH: a tracked session whose resolved agent CHANGES
     *   (a discovered session's user stopped kilo and started claude) is a
     *   story boundary: if the old story was announced as RUNNING, its
     *   running claim is WITHDRAWN first (NoLongerDetected for the OLD
     *   identity), the memory resets, and the new agent's story starts
     *   from its own first real evidence. The old name is never reused for
     *   the new agent's events.
     */
    private fun reduceObservations(
        memory: AgentRuntimeEventMemory,
        input: AgentEventInput.ObservationsChanged,
        nowMs: Long,
    ): Outcome {
        val events = mutableListOf<AgentRuntimeEvent>()
        var tracked = memory.tracked

        for (sessionId in input.observations.keys.sorted()) {
            val observation = input.observations[sessionId] ?: continue
            var session = tracked[sessionId]
            if (session == null) {
                // M7.2 P9 lazy track: only a RESOLVED agent may open a
                // story (the process evidence named it); anything else for
                // an untracked session is stale/birth-baseline noise.
                val discovered = observation.agent ?: continue
                if (sessionId in memory.ended) continue // defensive: ended sessions never emit
                session = AgentRuntimeEventMemory.TrackedAgentSession(
                    agent = discovered,
                    lastState = null,
                    lastEvidence = null,
                )
                tracked = tracked + (sessionId to session)
            }
            if (sessionId in memory.ended) continue // defensive: ended sessions never emit
            if (observation.state == AgentRuntimeState.NOT_APPLICABLE) continue

            // M7.2 P9 identity switch: the resolved agent changed — close
            // the old story honestly (withdraw a RUNNING claim; an UNKNOWN
            // surface the consumer still holds is corrected by the new
            // story's own next event), then reset and re-track.
            if (observation.agent != null && observation.agent != session.agent) {
                if (session.lastState == AgentRuntimeState.RUNNING) {
                    events += AgentRuntimeEvent.NoLongerDetected(
                        sessionId = sessionId,
                        agent = session.agent,
                        from = AgentRuntimeState.RUNNING,
                        lastEvidence = session.lastEvidence,
                        occurredAtMs = nowMs,
                    )
                }
                session = AgentRuntimeEventMemory.TrackedAgentSession(
                    agent = observation.agent,
                    lastState = null,
                    lastEvidence = null,
                )
                tracked = tracked + (sessionId to session)
            }

            if (observation.state == session.lastState) {
                // Same state re-observed: no event (Part F). A RUNNING
                // observation refreshes the evidence silently — the pids may
                // have changed (relaunch inside the session) while the state
                // sentence stays the same.
                if (observation.state == AgentRuntimeState.RUNNING && observation.evidence != null) {
                    tracked = tracked + (
                        sessionId to session.copy(lastEvidence = observation.evidence)
                        )
                }
                continue
            }

            when (observation.state) {
                AgentRuntimeState.RUNNING -> {
                    val evidence = observation.evidence
                    if (evidence == null) {
                        // Contract violation of the pure detector (RUNNING
                        // always carries evidence). Honest fallback: no
                        // claim at all — never a Running event without a
                        // process behind it.
                        continue
                    }
                    events += AgentRuntimeEvent.ConfirmedRunning(
                        sessionId = sessionId,
                        agent = session.agent,
                        from = session.lastState,
                        evidence = evidence,
                        occurredAtMs = nowMs,
                    )
                    tracked = tracked + (
                        sessionId to session.copy(
                            lastState = AgentRuntimeState.RUNNING,
                            lastEvidence = evidence,
                        )
                        )
                }

                AgentRuntimeState.NOT_RUNNING -> {
                    if (session.lastState == null) continue // structurally impossible; never fake an absence
                    events += AgentRuntimeEvent.NoLongerDetected(
                        sessionId = sessionId,
                        agent = session.agent,
                        from = session.lastState!!,
                        lastEvidence = session.lastEvidence,
                        occurredAtMs = nowMs,
                    )
                    tracked = tracked + (
                        sessionId to session.copy(
                            lastState = AgentRuntimeState.NOT_RUNNING,
                            lastEvidence = null,
                        )
                        )
                }

                AgentRuntimeState.UNKNOWN -> {
                    if (session.lastState == null) continue // birth baseline: the launch event said it all
                    events += AgentRuntimeEvent.RuntimeUnknown(
                        sessionId = sessionId,
                        agent = session.agent,
                        from = session.lastState!!,
                        occurredAtMs = nowMs,
                    )
                    tracked = tracked + (
                        sessionId to session.copy(lastState = AgentRuntimeState.UNKNOWN)
                        )
                }

                AgentRuntimeState.NOT_APPLICABLE -> continue // never published for eligible sessions
            }
        }

        return Outcome(
            memory = AgentRuntimeEventMemory(tracked = tracked, ended = memory.ended),
            events = events,
        )
    }
}
