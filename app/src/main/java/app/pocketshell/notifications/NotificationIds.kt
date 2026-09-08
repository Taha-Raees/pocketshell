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
 *   EVENT_BASE ..        — coordinator-owned event notifications. Per-session
 *                          event ids are EVENT_BASE + sessionId, so they are
 *                          deterministic (same session → same id → easy to
 *                          update/cancel), stable across coordinator calls,
 *                          collision-free for every session id this process
 *                          can realistically allocate (the manager hands out
 *                          1, 2, 3, … within one process lifetime), and never
 *                          reach the FGS id space.
 *
 * Deliberately NOT a random or hash-based scheme (P1 spec §7): hashes collide
 * and randoms cannot be cancelled after process death. The stale-notification
 * startup sweep cancels exactly the ids the ledger recorded — all of which
 * live in this event space, so the FGS notification is untouchable by
 * construction.
 */
object NotificationIds {

    /**
     * First id of the coordinator-owned event space. Chosen high above the
     * FGS id (1) so the two spaces can never overlap.
     */
    const val EVENT_BASE = 10_000

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
}
