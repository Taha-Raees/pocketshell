package app.pocketshell.notifications

import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.ExitStatus

/**
 * M7.2 P4 — the notification TRUTH CONTRACT (pure layer).
 *
 * This is the ONE place where an [AgentRuntimeEvent] (P3c's deduplicated,
 * honest transition vocabulary) is decided into what the notification shade
 * may say. Everything here is DATA plus PURE functions: no Android APIs, no
 * coroutine, no clock, no I/O — which is what makes the whole honesty
 * surface exhaustively JVM-testable (AgentRuntimeNotificationMappingTest).
 *
 * THE CONTRACT (docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md §2): a notification
 * is a statement about what P2/P3c PROVED — nothing more. Every wording
 * below stays inside the evidence:
 *
 *   Launched          -> nothing (a spawn fact makes no runtime claim; the
 *                        foreground-service "Terminal sessions" notification
 *                        already covers session existence)
 *   ConfirmedRunning  -> "<RegistryName> is running"  (the display name is
 *                        the launcher registry's own — never invented; the
 *                        running claim is P3b's correlated process evidence)
 *   RuntimeUnknown    -> "<Name> runtime unknown" (honest uncertainty in
 *                        place of the running claim — certainty is never
 *                        maintained that the evidence no longer supports)
 *   NoLongerDetected  -> CANCEL the running surface. Runtime disappearance
 *                        is NOT completion, NOT success, NOT "work finished"
 *                        — the honest representation of "no longer detected"
 *                        is the ABSENCE of the running claim, so no
 *                        replacement notification exists for this event.
 *   SessionEnded      -> CANCEL the surface; and, only when this session had
 *                        a runtime surface AND its process actually exited
 *                        (SESSION_FINISHED), one factual exit statement:
 *                        "Session ended" / "Terminal session N exited
 *                        (code X)" / "…was terminated by signal Y". This is
 *                        a statement about the TERMINAL PROCESS/SESSION —
 *                        the direct child's waitpid status — never about an
 *                        agent's task, and exit code 0 is never converted
 *                        into success/completion wording.
 *
 * THE DEDUPLICATION/SAFETY MEMORY (Part G — defensive, NOT a second event
 * state machine): P3c already emits every state change exactly once, but the
 * notification layer must stay safe against duplicate delivery and its own
 * collector restarts. [Memory] records, per session: which surface kind is
 * currently posted (a repeated identical surface is a no-op, never a repost
 * churn), whether ANY surface was ever posted (the exit-fact gate: a session
 * whose runtime was never announced to the user gets no end-of-story
 * notification either), and the tombstones of ended sessions (an event
 * arriving for an ended session can never post anything — the consumer
 * trusts the stream's ordering but does not have to trust it blindly).
 *
 * Deliberately ABSENT from every string this file can produce: "completed",
 * "success", "finished", "failed" — the P0 honesty line is preserved at the
 * wording level, and the tests pin it over the actual produced strings.
 */
object AgentRuntimeNotificationMapping {

    /**
     * What the notification shade currently carries for one session — the
     * dedup key's value. Both kinds share ONE notification id per session
     * ([NotificationIds.agentRuntime]), so a state change replaces the
     * surface in place.
     */
    enum class PostedKind { RUNNING, UNKNOWN }

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
}
