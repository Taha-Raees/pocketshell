package app.pocketshell.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
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
}
