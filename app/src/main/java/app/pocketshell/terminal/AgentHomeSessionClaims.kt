package app.pocketshell.terminal

/**
 * M7.2 P8 — the HOME SESSIONS presentation claim (PURE layer).
 *
 * P3a established WHAT a session was launched as ([LaunchIdentity]), P3b
 * whether the launched agent has runtime process evidence RIGHT NOW
 * ([AgentRuntimeState], published by [RuntimeAgentDetector]), P3c converted
 * evidence changes into deduplicated events, and P4/P5/P6 turned those
 * events into honest notifications. P8 asks one more question of the SAME
 * authorities: what may the EXISTING Home Sessions row say about the agent
 * work in that session — so that Home and the notification shade are two
 * views of one runtime state, never two independent systems.
 *
 * This object is a pure DECISION step, not a source of truth: it stores
 * nothing, owns nothing, scans nothing. Its inputs are exactly the
 * projections the notification layer already consumes
 * ([TerminalSessionManager.sessions] via [AgentActivityRepository],
 * [RuntimeAgentDetector.observations]); it can never become a second
 * detector, and it adds no polling, no parsing and no new vocabulary.
 *
 * THE CLAIM VOCABULARY (PART D — only states M7.2 already proves):
 *
 *   RUNNING  — the P3b detector currently holds correlated process evidence
 *              for this session's known agent. Same truth as the P4
 *              ConfirmedRunning surface ("<Name> is running").
 *   UNKNOWN  — the detector held evidence for this session before and the
 *              current scan cannot prove a state (honest uncertainty). Same
 *              truth as the P4 RuntimeUnknown surface ("<Name> runtime
 *              unknown").
 *
 * NO OTHER CLAIM EXISTS. Notably absent, per the frozen M7.2 truth
 * boundary: no Completed/Success/Failed (process disappearance is at most
 * NOT_RUNNING and the P7 audit proved generic waiting evidence does not
 * exist — so no NeedsInput/Waiting either), and no end-of-story wording
 * beyond what the session layer already shows on the row itself ("(exited)"
 * — the session fact Home has always displayed).
 *
 * THE PARITY CONTRACT (PART I — the reason for the [Observation]
 * .everObservedRunning gate): the P4 notification layer stays SILENT at a
 * session's birth UNKNOWN (no surface exists to contradict), states the
 * running surface exactly when evidence exists, states the unknown surface
 * exactly when uncertainty follows an announced story, and CANCELS (shows
 * nothing) on NoLongerDetected. The Home claim below follows that surface
 * semantics state-for-state:
 *
 *   no observation / UNKNOWN & !everObservedRunning -> no claim   (birth
 *       baseline: the notification is silent here too — the row keeps its
 *       normal presentation; uncertainty is never shown where the shade
 *       would be silent, and NEVER upgraded to running)
 *   RUNNING                                         -> RUNNING    (the shade
 *       says "<Name> is running" — the row says the same thing)
 *   UNKNOWN & everObservedRunning                   -> UNKNOWN    (the shade
 *       says "<Name> runtime unknown" — so does the row)
 *   NOT_RUNNING                                     -> no claim   (the shade
 *       cancels — the running claim is WITHDRAWN, absence is the honest
 *       state; the row returns to its normal presentation)
 *   NOT_APPLICABLE (non-agent tool / custom launcher) -> no claim
 *   plain shell / FINISHED session (not in [live])    -> no claim
 *
 * Purity: identical inputs always produce identical outputs; no clock, no
 * I/O, no Android, no coroutine, no state.
 */
object AgentHomeSessionClaims {

    /**
     * The ONLY two activity claims the Home Sessions row may state about an
     * agent — both already proven surfaces of the P4 notification contract.
     */
    enum class Claim { RUNNING, UNKNOWN }

    /**
     * One session's renderable agent-activity claim: which session it
     * belongs to (the manager's authoritative id — the same id the
     * notification identity and the row use), WHICH agent it names (the
     * launcher REGISTRY's display name — the same source the P4 wordings
     * use; never the session label, never a name invented from terminal
     * text), and the claim itself.
     */
    data class SessionClaim(
        val sessionId: Long,
        val agentDisplayName: String,
        val claim: Claim,
    )

    /**
     * One live session as Home presents it — the minimal projection the
     * repository derives from the manager's authoritative entry (its id and
     * its P3a launch identity; `null` identity = plain shell). Deliberately
     * NO lifecycle copy: liveness is the caller's filter, lifecycle stays
     * the manager's authority.
     */
    data class HomeSessionInput(
        val sessionId: Long,
        val identity: LaunchIdentity?,
    )

    /**
     * The claim decision for one session's current runtime state — the
     * parity mapping documented on this object, pinned exhaustively by
     * AgentHomeSessionClaimsTest. `null` = Home states NO agent activity
     * (the normal session presentation).
     */
    fun claimFor(state: AgentRuntimeState, everObservedRunning: Boolean): Claim? =
        when (state) {
            AgentRuntimeState.RUNNING -> Claim.RUNNING
            AgentRuntimeState.UNKNOWN ->
                if (everObservedRunning) Claim.UNKNOWN else null
            AgentRuntimeState.NOT_RUNNING -> null
            AgentRuntimeState.NOT_APPLICABLE -> null
        }

    /**
     * Present the claims for ALL live sessions in one fold. Sessions with
     * no claim (plain shells, tools, custom launchers, birth-UNKNOWN agent
     * sessions, withdrawn runtimes) are simply ABSENT from the result — the
     * keyed-by-id map can never attach a claim to the wrong session, and a
     * session that leaves [live] (finished or removed) loses its claim
     * structurally: no stale agent label can survive its session.
     */
    fun present(
        live: List<HomeSessionInput>,
        observations: Map<Long, AgentRuntimeDetection.Observation>,
    ): Map<Long, SessionClaim> =
        live.mapNotNull { session ->
            val identity = session.identity as? LaunchIdentity.KnownAgent
                ?: return@mapNotNull null
            val observation = observations[session.sessionId]
            val claim = claimFor(
                state = observation?.state ?: AgentRuntimeState.UNKNOWN,
                everObservedRunning = observation?.everObservedRunning ?: false,
            ) ?: return@mapNotNull null
            SessionClaim(
                sessionId = session.sessionId,
                agentDisplayName = identity.displayName,
                claim = claim,
            )
        }.associateBy { it.sessionId }
}
