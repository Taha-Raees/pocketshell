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
     *   - plain shells (identity `null`) are absent, exactly as in
     *     [classifiedLaunches] — no launcher named a command, no claim;
     *   - [LaunchIdentity.KnownAgent] -> the detector's current observation,
     *     or UNKNOWN before the first scan (never invented running);
     *   - [LaunchIdentity.KnownNonAgentTool] and
     *     [LaunchIdentity.CustomOrUnknown] -> NOT_APPLICABLE: the scanner
     *     does not run for them, and this state says so explicitly;
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
                    ?: return@mapNotNull null // plain shell: no claim, absent
                val state = when (identity) {
                    is LaunchIdentity.KnownAgent ->
                        observations[entry.id]?.state ?: AgentRuntimeState.UNKNOWN
                    is LaunchIdentity.KnownNonAgentTool -> AgentRuntimeState.NOT_APPLICABLE
                    is LaunchIdentity.CustomOrUnknown -> AgentRuntimeState.NOT_APPLICABLE
                }
                AgentRuntimeActivity(
                    sessionId = entry.id,
                    label = entry.displayLabel,
                    identity = identity,
                    state = state,
                )
            }
        }
        .distinctUntilChanged()
}
