package app.pocketshell.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * M7.2 P2 — the ONE derived read model for "what was launched and how its
 * session is doing" (docs/M7.2-P0-AUDIT.md PART J). Every value here is a
 * PURE PROJECTION of [TerminalSessionManager]'s authoritative StateFlow and
 * its typed lifecycle-event stream: this object stores NOTHING, owns
 * NOTHING, decides NOTHING — it can never become a second source of truth
 * (the M7.1.1 lesson, and the P1 coordinator's boundary preserved one layer
 * up).
 *
 * HONESTY BOUNDARY (the audit's Tier-1 line, Part E): "launched" below means
 * the session carries spawn-time [AgentHint] metadata — what the session was
 * LAUNCHED AS, a real fact recorded at spawn. Whether a specific agent
 * PROCESS is actually running inside the guest is NOT knowable from the
 * session layer (the direct child is proot, audit §1.3); that evidence is
 * the P3b scanner's territory and will arrive as graded procfs facts through
 * the manager, never through inference here. P2 posts no notifications and
 * touches no notification policy — the P1 coordinator stays untouched.
 */
object AgentActivityRepository {

    /**
     * A named-launcher session whose lifecycle reached FINISHED: "the session
     * launched as X ended with this structured status". The identity block is
     * snapshotted into the event by the manager, so this projection needs no
     * by-id re-lookup (which could race a concurrent tab removal).
     */
    data class FinishedLaunch(
        val sessionId: Long,
        val label: String,
        val origin: SpawnOrigin,
        val agent: AgentHint,
        val exitStatus: ExitStatus,
    )

    /** The manager's typed lifecycle edges, re-exposed for downstream consumers. */
    val lifecycleEvents: SharedFlow<SessionLifecycleEvent> =
        TerminalSessionManager.lifecycleEvents

    /**
     * M7.2 P3c — the deduplicated runtime-transition event stream,
     * re-exposed for downstream consumers exactly like [lifecycleEvents]:
     * this projection stores nothing, decides nothing and produces nothing
     * — [AgentRuntimeEventEngine] is the only producer, folding the P3b
     * detector's observations and this manager's authoritative state
     * through the pure [AgentRuntimeTransitions] step. Every emission IS a
     * transition (Launched / ConfirmedRunning / NoLongerDetected /
     * RuntimeUnknown / SessionEnded); identical-state re-observations never
     * appear, and there is NO replay for late collectors (a future
     * notification consumer must never re-fire on activity recreation or
     * collector restart).
     */
    val agentRuntimeEvents: SharedFlow<AgentRuntimeEvent> =
        AgentRuntimeEventEngine.events

    /**
     * Sessions launched through a NAMED launcher (agent hint present) whose
     * direct child has not exited yet (STARTING or RUNNING). "Running" is
     * session-level truth (the direct child); it makes no claim about any
     * descendant process inside the guest.
     */
    val runningLaunchedSessions: Flow<List<TerminalSessionManager.SessionEntry>> =
        TerminalSessionManager.sessions
            .map { list -> list.filter { it.agent != null && !it.isFinished } }
            .distinctUntilChanged()

    /**
     * Finished edges for named-launcher sessions only — "launched as X has
     * ended, with exit status Y". Plain shells exit too; they carry no agent
     * identity and are deliberately absent from this projection (consume
     * [lifecycleEvents] directly for session-level edges).
     */
    val finishedLaunchedSessions: Flow<FinishedLaunch> =
        lifecycleEvents
            .filterIsInstance<SessionLifecycleEvent.Finished>()
            .mapNotNull { event ->
                event.agent?.let {
                    FinishedLaunch(
                        sessionId = event.sessionId,
                        label = event.label,
                        origin = event.origin,
                        agent = it,
                        exitStatus = event.exitStatus,
                    )
                }
            }

    /**
     * M7.2 P3a — the classified launch identity per live session: WHAT each
     * session was launched as, resolved against the real registries
     * ([LaunchIdentity]). A pure derived view of the manager's authoritative
     * state — computed per emission, stored NOWHERE, and carrying NO runtime
     * claim (the identity is the launch evidence; the only running-truth
     * remains the entry's [SessionLifecycleState] on the direct child).
     * Plain shells (identity `null`) are absent, exactly as they are absent
     * from [runningLaunchedSessions].
     */
    data class ClassifiedLaunch(
        val sessionId: Long,
        val label: String,
        val identity: LaunchIdentity,
    )

    val classifiedLaunches: Flow<List<ClassifiedLaunch>> =
        TerminalSessionManager.sessions
            .map { list ->
                list.mapNotNull { entry ->
                    LaunchIdentity.of(entry.origin, entry.agent)?.let { identity ->
                        ClassifiedLaunch(
                            sessionId = entry.id,
                            label = entry.displayLabel,
                            identity = identity,
                        )
                    }
                }
            }
            .distinctUntilChanged()

    /**
     * M7.2 P3b — the runtime-evidence projection: WHAT each live session
     * claims at the process level, with the four-state honesty contract
     * ([AgentRuntimeState]). A pure derived view over the manager's
     * authoritative state PLUS the [RuntimeAgentDetector]'s graded procfs
     * evidence — this projection still stores nothing and decides nothing;
     * the detector is the only evidence producer, the manager the only
     * lifecycle authority.
     *
     * Mapping (each arm pinned structurally):
     *   - a session with NO launch identity and NO resolved observation is
     *     absent: nothing is known, nothing is claimed (M7.2 P9: a plain
     *     session whose runtime evidence RESOLVED an agent appears with
     *     that agent — the discovery class, the Part-B root-cause fix);
     *   - [LaunchIdentity.KnownAgent] -> the detector's current observation,
     *     or UNKNOWN before the first scan (never invented running);
     *   - [LaunchIdentity.KnownNonAgentTool] and
     *     [LaunchIdentity.CustomOrUnknown] -> NOT_APPLICABLE — exactly
     *     while the scanner's discovery has not RESOLVED an agent in the
     *     session's tree (P9: the process evidence upgrades the story; a
     *     nano session whose user typed `kilo` afterwards states kilo);
     *   - a FINISHED session is absent: its process tree is gone and the
     *     session layer's own waitpid-proven lifecycle (FINISHED +
     *     [ExitStatus]) is the truth that story — the runtime projection
     *     adds no completion claim to it, ever.
     *
     * There is deliberately NO completed/success state in this vocabulary
     * (P0 audit line, Part E of the P3b mandate): process disappearance is
     * at most NOT_RUNNING, and session ending is the session layer's fact.
     */
    data class AgentRuntimeActivity(
        val sessionId: Long,
        val label: String,
        val identity: LaunchIdentity,
        val state: AgentRuntimeState,
    )

    val runtimeActivities: Flow<List<AgentRuntimeActivity>> =
        combine(
            TerminalSessionManager.sessions,
            RuntimeAgentDetector.observations,
        ) { list, observations ->
            list.mapNotNull { entry ->
                if (entry.isFinished) return@mapNotNull null
                val identity = LaunchIdentity.of(entry.origin, entry.agent)
                val observation = observations[entry.id]
                when {
                    identity is LaunchIdentity.KnownAgent ->
                        AgentRuntimeActivity(
                            sessionId = entry.id,
                            label = entry.displayLabel,
                            identity = identity,
                            state = observation?.state ?: AgentRuntimeState.UNKNOWN,
                        )
                    identity != null ->
                        // Known non-agent tool / custom launcher: claimed
                        // NOT_APPLICABLE exactly while discovery has not
                        // resolved an agent in the session's tree.
                        if (observation?.agent != null) {
                            AgentRuntimeActivity(
                                sessionId = entry.id,
                                label = entry.displayLabel,
                                identity = observation.agent!!, // resolved by process evidence
                                state = observation.state,
                            )
                        } else {
                            AgentRuntimeActivity(
                                sessionId = entry.id,
                                label = entry.displayLabel,
                                identity = identity,
                                state = AgentRuntimeState.NOT_APPLICABLE,
                            )
                        }
                    observation?.agent != null ->
                        // Plain guest session, discovery resolved an agent:
                        // the claim names the registry entry the EVIDENCE
                        // found, never the session's label or title.
                        AgentRuntimeActivity(
                            sessionId = entry.id,
                            label = entry.displayLabel,
                            identity = observation.agent!!, // resolved by process evidence
                            state = observation.state,
                        )
                    else -> null // plain session, nothing discovered: absent, no claim
                }
            }
        }
        .distinctUntilChanged()

    /**
     * M7.2 P8 — the HOME presentation claim per session: what the EXISTING
     * Home Sessions row may honestly say about agent work in that session,
     * so Home and the notification shade state the SAME truth. Still a pure
     * projection of the SAME two authorities (the manager's authoritative
     * session list + the detector's graded observations) — this projection
     * adds no scan, no polling and no vocabulary of its own; the decision
     * lives in the pure [AgentHomeSessionClaims] step (see its KDoc for the
     * claim vocabulary and the P4-parity contract it mirrors state-for-state:
     * birth UNKNOWN silent, RUNNING claimed, informed UNKNOWN uncertain,
     * NOT_RUNNING withdrawn, non-agents and finished sessions absent).
     */
    val homeSessionClaims: Flow<Map<Long, AgentHomeSessionClaims.SessionClaim>> =
        combine(
            TerminalSessionManager.sessions,
            RuntimeAgentDetector.observations,
        ) { list, observations ->
            AgentHomeSessionClaims.present(
                live = list.filter { !it.isFinished }.map { entry ->
                    AgentHomeSessionClaims.HomeSessionInput(
                        sessionId = entry.id,
                        identity = LaunchIdentity.of(entry.origin, entry.agent),
                    )
                },
                observations = observations,
            )
        }
        .distinctUntilChanged()
}
