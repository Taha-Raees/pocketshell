package app.pocketshell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P1 — deterministic notification identity. Pins the P1 spec §7
 * requirements: deterministic, collision-resistant, easy to cancel, stable
 * across calls, and — the stale-sweep safety property — never reaching the
 * TerminalService foreground-service notification id (1).
 */
class NotificationIdsTest {

    @Test
    fun `identity is deterministic - same session id maps to the same notification id`() {
        assertEquals(NotificationIds.sessionEvent(1), NotificationIds.sessionEvent(1))
        assertEquals(NotificationIds.sessionEvent(42), NotificationIds.sessionEvent(42))
        assertEquals(NotificationIds.sessionEvent(1_000_000), NotificationIds.sessionEvent(1_000_000))
    }

    @Test
    fun `distinct session ids map to distinct notification ids`() {
        val ids = (1L..500L).map { NotificationIds.sessionEvent(it) }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the event space never reaches the foreground-service notification id 1`() {
        // TerminalService owns id 1; the coordinator's ids all live at or
        // above EVENT_BASE — the sweep can therefore never touch the FGS
        // notification by construction.
        assertTrue(NotificationIds.EVENT_BASE > 1)
        for (sessionId in 1L..200L) {
            assertTrue(NotificationIds.sessionEvent(sessionId) >= NotificationIds.EVENT_BASE)
            assertNotEquals(1, NotificationIds.sessionEvent(sessionId))
        }
    }

    @Test
    fun `the first session lands exactly on EVENT_BASE + 1`() {
        assertEquals(NotificationIds.EVENT_BASE + 1, NotificationIds.sessionEvent(1))
    }

    @Test
    fun `ids stay inside the Int range for realistic session ids`() {
        // 10_000 + sessionId must not overflow Int for the session manager's
        // monotonic ids.
        assertTrue(NotificationIds.sessionEvent(1_000_000L) in 1..Int.MAX_VALUE)
    }

    @Test
    fun `zero and negative session ids are refused - no silent wraparound`() {
        assertThrows(IllegalStateException::class.java) { NotificationIds.sessionEvent(0L) }
        assertThrows(IllegalStateException::class.java) { NotificationIds.sessionEvent(-1L) }
        assertThrows(IllegalStateException::class.java) { NotificationIds.sessionEvent(Long.MIN_VALUE) }
    }

    @Test
    fun `session ids beyond the event space are refused - no silent wraparound`() {
        val topOfSpace = (Int.MAX_VALUE - NotificationIds.EVENT_BASE).toLong()
        assertEquals(Int.MAX_VALUE, NotificationIds.sessionEvent(topOfSpace))
        assertThrows(IllegalStateException::class.java) { NotificationIds.sessionEvent(topOfSpace + 1) }
        assertThrows(IllegalStateException::class.java) { NotificationIds.sessionEvent(Int.MAX_VALUE.toLong()) }
    }
}
