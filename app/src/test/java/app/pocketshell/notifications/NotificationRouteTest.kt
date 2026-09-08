package app.pocketshell.notifications

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * M7.2 P1 — the tap-routing parser. The pure core ([NotificationRoute.parse])
 * is pinned here; the Android adapter ([NotificationRoute.fromIntent]) over a
 * real Intent is JVM-limited (unitTests.returnDefaultValues makes
 * Intent.getStringExtra answer null, so only the null/no-route path is
 * executable here) — the real tap paths are the docs/TESTING.md §48 gate.
 */
class NotificationRouteTest {

    @Test
    fun `the open-app route value parses to OpenApp`() {
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_APP))
    }

    @Test
    fun `no extra at all parses to null - plain opens stay un-routed`() {
        assertNull(NotificationRoute.parse(null))
    }

    @Test
    fun `empty extra parses to null`() {
        assertNull(NotificationRoute.parse(""))
    }

    @Test
    fun `unknown route values parse to null - honestly unrecognized`() {
        assertNull(NotificationRoute.parse("session", null))
        assertNull(NotificationRoute.parse("session", 2L))
        assertNull(NotificationRoute.parse("OPEN_APP", null))
        assertNull(NotificationRoute.parse("open_app ", null))
        assertNull(NotificationRoute.parse("null", null))
    }

    @Test
    fun `the extra key and route value are stable constants`() {
        // Tap routing is a cross-component contract (coordinator writes,
        // MainActivity reads) — the strings must never drift silently.
        assertEquals("app.pocketshell.notification.ROUTE", NotificationRoute.EXTRA_ROUTE)
        assertEquals("open_app", NotificationRoute.ROUTE_OPEN_APP)
    }

    // ------------------------------------------------ M7.2 P5 (session route)

    @Test
    fun `the session route value and id extra are stable constants`() {
        assertEquals("open_session", NotificationRoute.ROUTE_OPEN_SESSION)
        assertEquals("app.pocketshell.notification.SESSION_ID", NotificationRoute.EXTRA_SESSION_ID)
        // The three extra keys must stay distinct — one contract surface.
        assertEquals(
            3,
            setOf(
                NotificationRoute.EXTRA_ROUTE,
                NotificationRoute.ROUTE_OPEN_SESSION,
                NotificationRoute.EXTRA_SESSION_ID,
            ).size,
        )
    }

    @Test
    fun `a session route with a positive id parses to OpenSession carrying that exact id`() {
        assertEquals(
            NotificationRoute.OpenSession(2L),
            NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, 2L),
        )
        assertEquals(
            NotificationRoute.OpenSession(Long.MAX_VALUE),
            NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, Long.MAX_VALUE),
        )
    }

    @Test
    fun `a session route with a missing or malformed id degrades to OpenApp - never a crash`() {
        // Part F: the honest fallback IS a real route (open normally).
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, null))
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, 0L))
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, -1L))
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_SESSION, Long.MIN_VALUE))
    }

    @Test
    fun `the open-app route ignores the session id extra entirely`() {
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_APP, null))
        assertEquals(NotificationRoute.OpenApp, NotificationRoute.parse(NotificationRoute.ROUTE_OPEN_APP, 7L))
    }

    @Test
    fun `fromIntent over the JVM intent shell still parses null (no extras on the JVM)`() {
        // unitTests.returnDefaultValues: getStringExtra answers null — the
        // no-route path is the JVM-executable one; extras-carrying taps are
        // device-verified (docs/TESTING.md §54).
        assertNull(NotificationRoute.fromIntent(Intent()))
    }

    @Test
    fun `fromIntent is null-safe`() {
        assertNull(NotificationRoute.fromIntent(null))
    }

    @Test
    fun `fromIntent over an intent without extras stays null (JVM-executable path)`() {
        // On the JVM the Intent shell answers getStringExtra() with null
        // (unitTests.returnDefaultValues) — this pins exactly the
        // no-route path; extras-carrying intents are verified on device.
        assertNull(NotificationRoute.fromIntent(Intent()))
    }
}
