package app.pocketshell.notifications

import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.ExitStatus

/**
 * M7.2 P4 — the notification TRUTH CONTRACT (pure layer). M7.2 P6 extends
 * it where NEW authoritative evidence exists (docs/M7.2-P6-AGENT-ACTIVITY-V2.md):
 *
 * This is the ONE place where an [AgentRuntimeEvent] (P3c's deduplicated,
 * honest transition vocabulary) is decided into what the notification shade
 * may say. Everything here is DATA plus PURE functions: no Android APIs, no
 * coroutine, no clock, no I/O — which is what makes the whole honesty
 * surface exhaustively JVM-testable (AgentRuntimeNotificationMappingTest).
 *
 * THE CONTRACT (docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md §2 + P6): a
 * notification is a statement about what P2/P3c/P6 PROVED — nothing more.
 * Every wording below stays inside the evidence:
 *
 *   Launched          -> nothing (a spawn fact makes no runtime claim; the
 *                        foreground-service "Terminal sessions" notification
 *                        already covers session existence)
 *   ConfirmedRunning  -> "<RegistryName> is running"  (the display name is
 *                        the launcher registry's own — never invented; the
 *                        running claim is P3b's correlated process evidence)
 *   WorkingChanged    -> refine an ALREADY-POSTED running surface in place
 *                        ("is working" / "requests attention" / back to
 *                        "is running") — never creates a surface. The two
 *                        facts are PTY truths: output recency and the
 *                        terminal bell. Never posted standalone.
 *   RuntimeUnknown    -> "<Name> runtime unknown" (honest uncertainty in
 *                        place of the running claim — certainty is never
 *                        maintained that the evidence no longer supports)
 *   NoLongerDetected  -> CANCEL the running surface. Runtime disappearance
 *                        is NOT completion, NOT success, NOT "work finished"
 *                        — the honest representation of "no longer detected"
 *                        is the ABSENCE of the running claim, so no
 *                        replacement notification exists for this event.
 *                        (P6: this remains the ONLY story for launches
 *                        without a record channel.)
 *   AgentExited (P6)  -> the launch channel's OWN ExitRecord: the agent's
 *                        real exit status. One-shot exit statement, worded
 *                        by the status: 0 -> "<Name> finished
 *                        successfully"; 128+n -> "<Name> was stopped";
 *                        otherwise -> "<Name> failed (exit code N)". This
 *                        is the AGENT's own fact (P9 bridge evidence) —
 *                        distinct from the session's waitpid fact below.
 *   SessionEnded      -> CANCEL the surface; and, only when this session had
 *                        a runtime surface AND its process actually exited
 *                        (SESSION_FINISHED), one factual exit statement:
 *                        "Session ended" / "Terminal session N exited
 *                        (code X)" / "…was terminated by signal Y". This is
 *                        a statement about the TERMINAL PROCESS/SESSION —
 *                        the direct child's waitpid status — never about an
 *                        agent's task, and exit code 0 is never converted
 *                        into success/completion wording. P6: when the
 *                        AGENT's own exit was already announced for this
 *                        session, the session-exit fact is suppressed (the
 *                        story was already told; a second exit line is noise).
 *
 * THE DEDUPLICATION/SAFETY MEMORY (Part G — defensive, NOT a second event
 * state machine): P3c already emits every state change exactly once, but the
 * notification layer must stay safe against duplicate delivery and its own
 * collector restarts. [Memory] records, per session: which surface kind is
 * currently posted (a repeated identical surface is a no-op, never a repost
 * churn), whether ANY surface was ever posted (the exit-fact gate: a session
 * whose runtime was never announced to the user gets no end-of-story
 * notification either), the tombstones of ended sessions (an event
 * arriving for an ended session can never post anything — the consumer
 * trusts the stream's ordering but does not have to trust it blindly), and
 * (P6) the sessions whose AGENT exit was already announced.
 */
object AgentRuntimeNotificationMapping {

    /**
     * What the notification shade currently carries for one session — the
     * dedup key's value. All kinds share ONE notification id per session
     * ([NotificationIds.agentRuntime]), so a state change replaces the
     * surface in place.
     */
    enum class PostedKind { RUNNING, UNKNOWN, WORKING, ATTENTION }

    /** P6 — the three record-backed agent-exit surfaces (wording selectors). */
    enum class AgentExitSurface { SUCCESS, FAILED, STOPPED }

    /**
     * The consumer's notification-domain memory (Part G). Process-scoped,
     * in-memory, bounded by the number of agent sessions this process saw.
     * It records only what THIS layer showed — never what any session or
     * process IS (that stays with the manager/detector/engine).
     */
    data class Memory(
        /** The surface currently posted per session (empty = none). */
        val posted: Map<Long, PostedKind> = emptyMap(),
        /** Sessions for which ANY surface was posted at some point. */
        val everPosted: Set<Long> = emptySet(),
        /** Ended sessions — tombstones; events for them are ignored. */
        val ended: Set<Long> = emptySet(),
        /**
         * P6 — sessions whose AGENT exit fact was already announced (the
         * record-backed one-shot). Suppresses the later session-exit fact
         * (the story was told; a second exit line is noise) and dedups
         * duplicate AgentExited delivery.
         */
        val agentExitAnnounced: Set<Long> = emptySet(),
    )

    /** What the consumer should do about one event. */
    sealed interface Action {

        /**
         * Post or update the per-session runtime surface (the id is
         * [NotificationIds.agentRuntime]; an existing surface on that id is
         * replaced in place).
         */
        data class ShowRuntime(
            val sessionId: Long,
            val title: String,
            val text: String,
        ) : Action

        /**
         * Post the one-shot factual exit statement (replaces any state
         * surface on the same id; not ongoing — tapping dismisses it).
         */
        data class ShowExitFact(
            val sessionId: Long,
            val title: String,
            val text: String,
        ) : Action

        /** Cancel the per-session notification (safe no-op if none). */
        data class Cancel(val sessionId: Long) : Action

        /** Nothing to do (the honest answer for most events). */
        data object None : Action
    }

    /**
     * Fold one event into the memory and the action it yields. Pure:
     * identical (memory, event) pairs always produce identical outcomes; no
     * clock, no I/O, no Android.
     */
    fun reduce(memory: Memory, event: AgentRuntimeEvent): Pair<Memory, Action> {
        // Tombstones first: a session that reached its terminal edge can
        // never produce another notification from any late or duplicate
        // event (Part G).
        if (event.sessionId in memory.ended) return memory to Action.None

        return when (event) {
            is AgentRuntimeEvent.Launched ->
                // A spawn fact: no runtime claim, no notification. The
                // session's story is announced by its first real evidence.
                memory to Action.None

            is AgentRuntimeEvent.ConfirmedRunning -> {
                if (memory.posted[event.sessionId] == PostedKind.RUNNING) {
                    // The surface already says running: repeated delivery is
                    // a no-op (defensive dedup over P3c's own dedup).
                    memory to Action.None
                } else {
                    val next = memory.copy(
                        posted = memory.posted + (event.sessionId to PostedKind.RUNNING),
                        everPosted = memory.everPosted + event.sessionId,
                    )
                    next to Action.ShowRuntime(
                        sessionId = event.sessionId,
                        title = runningTitle(event),
                        text = runningText(event),
                    )
                }
            }

            is AgentRuntimeEvent.RuntimeUnknown -> {
                if (event.sessionId !in memory.everPosted) {
                    // Defensive: the engine emits RuntimeUnknown only after an
                    // informative state (which would have posted a surface);
                    // if this layer somehow never announced the session, it
                    // stays silent — no uncontextualized uncertainty spam.
                    memory to Action.None
                } else if (memory.posted[event.sessionId] == PostedKind.UNKNOWN) {
                    memory to Action.None
                } else {
                    val next = memory.copy(
                        posted = memory.posted + (event.sessionId to PostedKind.UNKNOWN),
                    )
                    next to Action.ShowRuntime(
                        sessionId = event.sessionId,
                        title = unknownTitle(event),
                        text = unknownText(event),
                    )
                }
            }

            is AgentRuntimeEvent.NoLongerDetected -> {
                if (event.sessionId !in memory.posted) {
                    memory to Action.None
                } else {
                    // Runtime disappearance: the running claim is WITHDRAWN.
                    // No replacement notification — "no longer detected" is
                    // not completion, and the absence IS the honest state.
                    val next = memory.copy(posted = memory.posted - event.sessionId)
                    next to Action.Cancel(event.sessionId)
                }
            }

            is AgentRuntimeEvent.SessionEnded -> {
                var next = memory.copy(ended = memory.ended + event.sessionId)
                val hadSurface = event.sessionId in memory.posted
                if (hadSurface) next = next.copy(posted = next.posted - event.sessionId)
                when {
                    // M7.2 P6: the AGENT's own exit fact was already announced
                    // for this session — the story was told. Withdraw any
                    // remaining surface; never a second exit line.
                    event.sessionId in memory.agentExitAnnounced && hadSurface ->
                        next to Action.Cancel(event.sessionId)
                    event.sessionId in memory.agentExitAnnounced -> next to Action.None

                    // The process actually exited (waitpid-proven) and this
                    // session's runtime was announced: one factual exit
                    // statement replaces the state surface. Session-scoped
                    // wording, exit code preserved verbatim — never an agent
                    // completion claim.
                    event.cause == AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED &&
                        event.sessionExitStatus != null &&
                        event.sessionId in memory.everPosted -> {
                        next to Action.ShowExitFact(
                            sessionId = event.sessionId,
                            title = EXIT_TITLE,
                            text = exitText(event.sessionId, event.sessionExitStatus),
                        )
                    }

                    // A posted surface outlives its session in every other
                    // shape: withdraw it. A user-removed tab or a
                    // never-announced session otherwise stays silent.
                    hadSurface -> next to Action.Cancel(event.sessionId)

                    else -> next to Action.None
                }
            }

            /**
             * M7.2 P6 — the agent's own exit fact (record-backed). One-shot
             * exit statement replaces any surface on the id; the session
             * itself may live on (`; exec sh -l`), so this is NOT a
             * tombstone — a NEW running story in the same session may
             * announce itself again. Duplicate delivery is deduplicated by
             * [Memory.agentExitAnnounced].
             */
            is AgentRuntimeEvent.AgentExited -> {
                if (event.sessionId in memory.agentExitAnnounced) return memory to Action.None
                val surface = surfaceFor(event.exitStatus)
                val next = memory.copy(
                    posted = memory.posted - event.sessionId,
                    everPosted = memory.everPosted + event.sessionId,
                    agentExitAnnounced = memory.agentExitAnnounced + event.sessionId,
                )
                next to Action.ShowExitFact(
                    sessionId = event.sessionId,
                    title = agentExitTitle(event.agent.displayName, surface),
                    text = agentExitText(event.agent.displayName, surface, event.exitStatus, event.sessionId),
                )
            }

            /**
             * M7.2 P6 — refine an ALREADY-POSTED runtime surface in place
             * (never creates one; a surface that says UNKNOWN stays until
             * the evidence story itself corrects it). Edge-only upstream;
             * identical desired surface is a no-op.
             */
            is AgentRuntimeEvent.WorkingChanged -> {
                val current = memory.posted[event.sessionId] ?: return memory to Action.None
                if (current == PostedKind.UNKNOWN) return memory to Action.None
                val desired = when {
                    event.attentionRequested -> PostedKind.ATTENTION
                    event.active -> PostedKind.WORKING
                    else -> PostedKind.RUNNING
                }
                if (current == desired) return memory to Action.None
                val next = memory.copy(posted = memory.posted + (event.sessionId to desired))
                next to Action.ShowRuntime(
                    sessionId = event.sessionId,
                    title = activityTitle(event, desired),
                    text = activityText(event, desired),
                )
            }
        }
    }

    // ---------------------------------------------------------- wording

    /**
     * The running surface's title: the launcher REGISTRY's display name
     * ([app.pocketshell.terminal.LaunchIdentity.KnownAgent.displayName]) —
     * the one name genuinely known for this agent; nothing is invented.
     */
    private fun runningTitle(event: AgentRuntimeEvent.ConfirmedRunning): String =
        "${event.agent.displayName} is running"

    /** The running surface's body: the process-evidence fact, session-scoped. */
    private fun runningText(event: AgentRuntimeEvent.ConfirmedRunning): String =
        "Agent process confirmed in terminal session ${event.sessionId}"

    /** The unknown surface's title: uncertainty stays uncertainty. */
    private fun unknownTitle(event: AgentRuntimeEvent.RuntimeUnknown): String =
        "${event.agent.displayName} runtime unknown"

    /** The unknown surface's body: no certainty is manufactured. */
    private fun unknownText(event: AgentRuntimeEvent.RuntimeUnknown): String =
        "Runtime state cannot be verified right now (session ${event.sessionId})"

    /** The one-shot exit statement's title: session-scoped, never agent-scoped. */
    const val EXIT_TITLE: String = "Session ended"

    /**
     * The exit statement's body: the DIRECT CHILD's waitpid status verbatim —
     * "exited (code N)" / "terminated by signal N". A statement about the
     * terminal session's process, with the exit code preserved uninterpreted.
     */
    fun exitText(sessionId: Long, status: ExitStatus): String = when (status) {
        is ExitStatus.Exited -> "Terminal session $sessionId exited (code ${status.code})"
        is ExitStatus.Signaled ->
            "Terminal session $sessionId was terminated by signal ${status.signal}"
    }

    // ------------------------------------------------- P6 agent-exit wording

    /** P6 — select the record-backed exit surface from the shell-encoded `$?`. */
    fun surfaceFor(status: Int): AgentExitSurface = when {
        status == 0 -> AgentExitSurface.SUCCESS
        status in 128..191 -> AgentExitSurface.STOPPED
        else -> AgentExitSurface.FAILED
    }

    /**
     * P6 — the AGENT exit title. The name is the registry's own; the claim
     * is exactly what the agent's own exit status proves: 0 = the process
     * finished successfully; 128+n = stopped by signal; else failed.
     */
    fun agentExitTitle(displayName: String, surface: AgentExitSurface): String = when (surface) {
        AgentExitSurface.SUCCESS -> "$displayName finished successfully"
        AgentExitSurface.FAILED -> "$displayName failed"
        AgentExitSurface.STOPPED -> "$displayName was stopped"
    }

    /**
     * P6 — the AGENT exit body: the record's status preserved verbatim
     * (session-scoped for tap context). The success/failed/stopped claim
     * lives in the TITLE; the body stays factual.
     */
    fun agentExitText(displayName: String, surface: AgentExitSurface, status: Int, sessionId: Long): String =
        when (surface) {
            AgentExitSurface.SUCCESS -> "Agent process exited cleanly (code 0) in session $sessionId"
            AgentExitSurface.FAILED -> "Agent process exited with code $status in session $sessionId"
            AgentExitSurface.STOPPED ->
                "Agent process was stopped by signal ${status - 128} in session $sessionId"
        }

    // -------------------------------------------- P6 activity-surface wording

    /**
     * P6 — the activity surface title: the running claim's name plus the
     * PTY fact that currently holds. Attention wording is exactly the bell
     * fact ("requests attention") — never an inference about input state.
     */
    private fun activityTitle(
        event: AgentRuntimeEvent.WorkingChanged,
        desired: PostedKind,
    ): String = when (desired) {
        PostedKind.ATTENTION -> "${event.agent.displayName} requests attention"
        PostedKind.WORKING -> "${event.agent.displayName} is working"
        else -> "${event.agent.displayName} is running"
    }

    /** P6 — the activity surface body: session-scoped, factual. */
    private fun activityText(
        event: AgentRuntimeEvent.WorkingChanged,
        desired: PostedKind,
    ): String = when (desired) {
        PostedKind.ATTENTION ->
            "The agent rang the terminal bell — tap to return to session ${event.sessionId}"
        PostedKind.WORKING ->
            "Producing output in terminal session ${event.sessionId}"
        else ->
            "Agent process confirmed in terminal session ${event.sessionId}"
    }
}
