package app.pocketshell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P5 — the pure routing-resolution matrix (Part J): every interaction
 * arm of the notification-tap contract, resolved against the session
 * manager's AUTHORITATIVE live-id set. The mandate's required cases:
 *
 *   confirmed-running + valid session / runtime-unknown + valid session /
 *   session-ended + valid (listed) session  -> OpenSession(id)
 *   stale session                            -> OpenApp (no resurrection)
 *   missing session identity                 -> OpenApp (the parse degrade)
 *   malformed launch data                    -> OpenApp (the parse degrade)
 *   multiple sessions                        -> each id resolves to ITSELF
 *   identity collisions                      -> none (id space pinned)
 *
 * Everything here runs on the JVM because the resolution model is pure —
 * the whole point of separating the interaction fact from the Android
 * Intent mechanics (Part J).
 */
class AgentRuntimeNotificationRoutingTest {

    private val live = setOf(1L, 2L, 3L)

    private fun openSession(id: Long) = NotificationRoute.OpenSession(id)

    // --------------------------------------- the mandate's three live arms

    @Test
    fun `confirmed-running tap + valid session routes to that session`() {
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenSession(2L),
            AgentRuntimeNotificationRouting.resolve(openSession(2L), live),
        )
    }

    @Test
    fun `runtime-unknown tap + valid session routes to that session too - uncertainty is not absence`() {
        // The unknown surface carries the SAME session identity; the user
        // lands where the runtime lives and the wording stays honest.
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenSession(3L),
            AgentRuntimeNotificationRouting.resolve(openSession(3L), live),
        )
    }

    @Test
    fun `session-ended tap + still-listed session routes to its tab - the exit fact stays historical`() {
        // A finished-but-listed session is present: its tab honestly shows
        // the end state. Routing shows what IS; it fabricates nothing.
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenSession(1L),
            AgentRuntimeNotificationRouting.resolve(openSession(1L), live),
        )
    }

    // ------------------------------------------------ the stale/missing arms

    @Test
    fun `a stale session id resolves to OpenApp - the notification never resurrects a process`() {
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenApp,
            AgentRuntimeNotificationRouting.resolve(openSession(99L), live),
        )
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenApp,
            AgentRuntimeNotificationRouting.resolve(openSession(2L), emptySet()),
        )
    }

    @Test
    fun `the open-app route passes through unchanged`() {
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenApp,
            AgentRuntimeNotificationRouting.resolve(NotificationRoute.OpenApp, live),
        )
    }

    @Test
    fun `missing or malformed session identity reaches the resolver only as OpenApp`() {
        // The parse layer degrades missing/zero/negative ids to OpenApp
        // (NotificationRouteTest pins that arm); here we pin that the
        // degraded value resolves exactly like a plain open — the resolver
        // never sees a fabricated target.
        val degraded = NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, 0L)
        assertEquals(NotificationRoute.OpenApp, degraded)
        assertEquals(
            AgentRuntimeNotificationRouting.Resolution.OpenApp,
            AgentRuntimeNotificationRouting.resolve(degraded!!, live),
        )
    }

    // ------------------------------------------------------ multiple sessions

    @Test
    fun `multiple sessions each resolve to their own id - never another session`() {
        for (id in live) {
            val resolution = AgentRuntimeNotificationRouting.resolve(openSession(id), live)
            assertEquals(
                "session $id must resolve to itself",
                AgentRuntimeNotificationRouting.Resolution.OpenSession(id),
                resolution,
            )
        }
    }

    @Test
    fun `resolution selects the tapped session - a wrong-session tap is distinguishable`() {
        val first = AgentRuntimeNotificationRouting.resolve(openSession(1L), live)
        val second = AgentRuntimeNotificationRouting.resolve(openSession(2L), live)
        assertTrue(first is AgentRuntimeNotificationRouting.Resolution.OpenSession)
        assertTrue(second is AgentRuntimeNotificationRouting.Resolution.OpenSession)
        assertEquals(1L, (first as AgentRuntimeNotificationRouting.Resolution.OpenSession).sessionId)
        assertEquals(2L, (second as AgentRuntimeNotificationRouting.Resolution.OpenSession).sessionId)
    }

    // ------------------------------------------------- determinism and purity

    @Test
    fun `resolution is deterministic - identical inputs always produce identical outcomes`() {
        for (id in 1L..5L) {
            val route = openSession(id)
            val once = AgentRuntimeNotificationRouting.resolve(route, live)
            val again = AgentRuntimeNotificationRouting.resolve(route, live)
            assertEquals(once, again)
        }
    }

    @Test
    fun `an empty live list degrades every session target to OpenApp`() {
        for (id in 1L..3L) {
            assertEquals(
                AgentRuntimeNotificationRouting.Resolution.OpenApp,
                AgentRuntimeNotificationRouting.resolve(openSession(id), emptySet()),
            )
        }
    }

    // ------------------------------------- identity (Part I: no collisions)

    @Test
    fun `per-session notification identity stays pairwise distinct - no tap can update another session's surface`() {
        // The content-intent request code IS the notification id
        // (coordinator source pin): if these never collide, session A's
        // PendingIntent can never be session B's.
        val a = NotificationIds.agentRuntime(1L)
        val b = NotificationIds.agentRuntime(2L)
        val c = NotificationIds.sessionEvent(1L)
        assertTrue(a != b)
        assertTrue(a != c)
        val all = (1L..500L).map { NotificationIds.agentRuntime(it) }
        assertEquals(500, all.toSet().size)
    }
}
