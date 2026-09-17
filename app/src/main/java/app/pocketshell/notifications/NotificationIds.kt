package app.pocketshell.notifications

/**
 * M7.2 P1 — deterministic notification identity (docs/M7.2-P0-AUDIT.md §8,
 * §11 "stale notifications" risk).
 *
 * The app's notification id spaces:
 *
 *   1                    — TerminalService's foreground-service notification
 *                          (the session-retention surface; owned by
 *                          TerminalService since M2.4, never reused here).
 *   EVENT_BASE ..        — coordinator-owned session-activity notifications
 *                          (P1). Per-session ids are EVENT_BASE + sessionId.
 *   AGENT_RUNTIME_BASE ..— coordinator-owned agent-runtime notifications
 *                          (M7.2 P4: the honest "is running / runtime
 *                          unknown / session ended" surfaces). Per-session
 *                          ids are AGENT_RUNTIME_BASE + sessionId, with the
 *                          same determinism and stability guarantees.
 *
 * Both coordinator spaces are deterministic (same session → same id → easy
 * to update/cancel), stable across coordinator calls, collision-free for
 * every session id this process can realistically allocate (the manager
 * hands out 1, 2, 3, … within one process lifetime), and never reach the
 * FGS id space or each other.
 *
 * Deliberately NOT a random or hash-based scheme (P1 spec §7): hashes collide
 * and randoms cannot be cancelled after process death. The stale-notification
 * startup sweep cancels exactly the ids the ledger recorded — all of which
 * live in these two event spaces, so the FGS notification is untouchable by
 * construction.
 */
object NotificationIds {

    /**
     * First id of the coordinator-owned session-activity event space. Chosen
     * high above the FGS id (1) so the two spaces can never overlap.
     */
    const val EVENT_BASE = 10_000

    /**
     * First id of the coordinator-owned agent-runtime event space (M7.2 P4).
     * Chosen high above [EVENT_BASE] so the two coordinator spaces can never
     * overlap either — a session-activity notification and an agent-runtime
     * notification for the same session id are distinct system notifications.
     */
    const val AGENT_RUNTIME_BASE = 20_000

    /**
     * First id of the coordinator-owned agent-ATTENTION event space
     * (M7.2 P10): the agent-native attention/turn surface ("requesting
     * permission", "needs your input", "finished responding"). Kept separate
     * from [AGENT_RUNTIME_BASE] ON PURPOSE: the runtime surface ("is
     * running") stays a true ongoing fact while the attention surface states
     * what the agent is waiting on — the two coexist as distinct system
     * notifications for the same session.
     */
    const val AGENT_ATTENTION_BASE = 30_000

    /**
     * The notification id for a per-session event notification.
     *
     * @throws IllegalStateException for a session id outside the representable
     *   event space (0/negative, or beyond [Int.MAX_VALUE] - [EVENT_BASE]) —
     *   an impossible value for the session manager's monotonic ids; failing
     *   loudly is the honest alternative to a silent wraparound collision.
     */
    fun sessionEvent(sessionId: Long): Int {
        check(sessionId in 1..(Int.MAX_VALUE - EVENT_BASE).toLong()) {
            "session id $sessionId is outside the notification event space — refusing to wrap silently"
        }
        return EVENT_BASE + sessionId.toInt()
    }

    /**
     * The notification id for a per-session agent-runtime notification
     * (M7.2 P4): the running/unknown state surface and the one-shot
     * session-exit fact share ONE id per session — the exit fact replaces
     * the state surface on the same slot, so a session can never accumulate
     * multiple runtime notifications.
     *
     * @throws IllegalStateException for a session id outside the representable
     *   agent-runtime space (0/negative, or beyond
     *   [Int.MAX_VALUE] - [AGENT_RUNTIME_BASE]) — an impossible value for the
     *   session manager's monotonic ids; failing loudly is the honest
     *   alternative to a silent wraparound collision.
     */
    fun agentRuntime(sessionId: Long): Int {
        check(sessionId in 1..(Int.MAX_VALUE - AGENT_RUNTIME_BASE).toLong()) {
            "session id $sessionId is outside the agent-runtime notification space — refusing to wrap silently"
        }
        return AGENT_RUNTIME_BASE + sessionId.toInt()
    }

    /**
     * The notification id for a per-session agent-ATTENTION notification
     * (M7.2 P10): the attention surface ("requesting permission" / "needs
     * your input") and the turn surface ("finished responding") share ONE id
     * per session — the agent can only be in one of those stories at a time
     * (a turn that ends a pending attention withdraws it), so one slot
     * per session can never accumulate multiple attention notifications.
     *
     * @throws IllegalStateException for a session id outside the representable
     *   agent-attention space — the same loud-failure rule as the other spaces.
     */
    fun agentAttention(sessionId: Long): Int {
        check(sessionId in 1..(Int.MAX_VALUE - AGENT_ATTENTION_BASE).toLong()) {
            "session id $sessionId is outside the agent-attention notification space — refusing to wrap silently"
        }
        return AGENT_ATTENTION_BASE + sessionId.toInt()
    }
}
