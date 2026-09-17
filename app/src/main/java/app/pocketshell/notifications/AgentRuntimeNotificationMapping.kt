package app.pocketshell.notifications

import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentSignalBridge
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
     * M7.2 P10 — what the separate agent-ATTENTION surface (its own id,
     * [NotificationIds.agentAttention]) currently carries, if anything.
     * The attention surface and the runtime surface COEXIST: "is running"
     * and "is waiting on you" are both true at once.
     */
    enum class AttentionPosted { PERMISSION, INPUT, TURN }

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
        /** M7.2 P10 — the attention surface currently posted per session (empty = none). */
        val attentionPosted: Map<Long, AttentionPosted> = emptyMap(),
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

        /**
         * M7.2 P10 — post or update the per-session ATTENTION surface (the
         * id is [NotificationIds.agentAttention]; distinct from the runtime
         * surface, so the two truths coexist).
         */
        data class ShowAttention(
            val sessionId: Long,
            val title: String,
            val text: String,
        ) : Action

        /** M7.2 P10 — cancel the per-session attention surface (safe no-op if none). */
        data class CancelAttention(val sessionId: Long) : Action

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
                var next = memory.copy(
                    ended = memory.ended + event.sessionId,
                    // M7.2 P10 hygiene: an attention surface state never
                    // outlives its session's tombstone. (The engine emits
                    // the withdrawal BEFORE this edge; this is defense in
                    // depth for the recorded state.)
                    attentionPosted = memory.attentionPosted - event.sessionId,
                )
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

            // --------------------------------------------------------------
            // M7.2 P10 — the agent-native attention axis. These wordings
            // are the "conscious, documented revision" of the P7 wording
            // ban lists (docs/M7.2-P10-AGENT-SIGNAL-BRIDGE.md §8): the
            // claims are only ever as wide as the agent's OWN structured
            // payload that proved them — a permission signal may say
            // "requesting permission", an idle_prompt signal may say
            // "needs your input", a turn-complete signal may say
            // "finished responding". Nothing here upgrades exit codes,
            // absences, or guesses into any of those claims.
            // --------------------------------------------------------------

            is AgentRuntimeEvent.AgentAttentionRaised -> {
                val kind = when (event.attention) {
                    AgentSignalBridge.AgentAttentionPhase.PERMISSION_REQUEST -> AttentionPosted.PERMISSION
                    AgentSignalBridge.AgentAttentionPhase.INPUT_REQUIRED -> AttentionPosted.INPUT
                    AgentSignalBridge.AgentAttentionPhase.NONE -> null
                }
                if (kind == null) {
                    // Structurally impossible from the engine (a Raised
                    // always carries a real phase); stay silent rather
                    // than invent a surface.
                    memory to Action.None
                } else {
                    val next = memory.copy(
                        attentionPosted = memory.attentionPosted + (event.sessionId to kind),
                    )
                    next to Action.ShowAttention(
                        sessionId = event.sessionId,
                        title = attentionTitle(event.agent.displayName, kind),
                        text = attentionText(event, kind),
                    )
                }
            }

            is AgentRuntimeEvent.AgentAttentionCleared -> {
                if (event.sessionId !in memory.attentionPosted) {
                    memory to Action.None
                } else {
                    // The wait is OVER (the agent went back to work, ended
                    // its turn, or died) — the withdrawal is the honest
                    // state: cancel, no replacement ("cleared" is not
                    // completion, and says nothing about success).
                    val next = memory.copy(
                        attentionPosted = memory.attentionPosted - event.sessionId,
                    )
                    next to Action.CancelAttention(event.sessionId)
                }
            }

            is AgentRuntimeEvent.AgentTurnSignalled -> {
                // The agent's OWN turn-end fact: one calm surface per turn
                // (the attention slot updates in place; setOnlyAlertOnce
                // keeps repeats silent). Narrow wording — a turn ended, the
                // agent is back at its prompt; no success/completion claim.
                val next = memory.copy(
                    attentionPosted = memory.attentionPosted + (event.sessionId to AttentionPosted.TURN),
                )
                next to Action.ShowAttention(
                    sessionId = event.sessionId,
                    title = turnTitle(event.agent.displayName),
                    text = turnText(event.sessionId),
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

    // ---------------------------------------------------------- M7.2 P10 wording

    /**
     * The attention surface's title — exactly as wide as the proven claim:
     * a permission signal says "requesting permission" (the narrow claim
     * P7 §10 mandated for permission evidence), an input signal says
     * "needs your input".
     */
    fun attentionTitle(displayName: String, kind: AttentionPosted): String = when (kind) {
        AttentionPosted.PERMISSION -> "$displayName is requesting permission"
        AttentionPosted.INPUT -> "$displayName needs your input"
        AttentionPosted.TURN -> turnTitle(displayName)
    }

    /**
     * The attention surface's body. When the agent's own payload named the
     * tool it wants to run (Claude Code PermissionRequest payloads carry
     * tool_name), the body names it — the agent said it, the bridge
     * relayed it verbatim; nothing is inferred beyond that.
     */
    fun attentionText(event: AgentRuntimeEvent.AgentAttentionRaised, kind: AttentionPosted): String {
        val tool = toolNameFromPayload(event.signal.data)
        val session = "terminal session ${event.sessionId}"
        return when (kind) {
            AttentionPosted.PERMISSION -> when (tool) {
                null -> "The agent asked for your approval in $session"
                else -> "The agent asked for your approval to use $tool in $session"
            }
            AttentionPosted.INPUT -> "The agent is waiting for you in $session"
            AttentionPosted.TURN -> turnText(event.sessionId)
        }
    }

    /**
     * The turn surface's title — the agent's own turn-end fact, narrowly
     * worded ("ended its turn" — exactly what a turn-complete/Stop/idle
     * signal declares; deliberately NOT "finished", which the P4 wording
     * sweep bans and which would read as a task-completion claim).
     */
    fun turnTitle(displayName: String): String = "$displayName ended its turn"

    /** The turn surface's body: turn ended, agent back at its prompt. Nothing more. */
    fun turnText(sessionId: Long): String =
        "The agent ended its turn (terminal session $sessionId)"

    /**
     * Extracts `tool_name` from an accepted signal's payload (the raw JSON
     * the agent's hook/plugin wrote, relayed verbatim). Best-effort BY
     * DESIGN: a payload without the field simply yields null — the wording
     * degrades to the still-true generic line, never to a guess.
     */
    private fun toolNameFromPayload(data: String): String? {
        if (data.isEmpty()) return null
        val match = Regex("\"tool_name\":\"([A-Za-z0-9_.-]+)\"").find(data) ?: return null
        return match.groupValues.get(1).takeIf { it.isNotBlank() }
    }
}
