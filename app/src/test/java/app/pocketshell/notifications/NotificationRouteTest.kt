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
        assertNull(NotificationRoute.parse("session"))
        assertNull(NotificationRoute.parse("OPEN_APP"))
        assertNull(NotificationRoute.parse("open_app "))
        assertNull(NotificationRoute.parse("null"))
    }

    @Test
    fun `the extra key and route value are stable constants`() {
        // Tap routing is a cross-component contract (coordinator writes,
        // MainActivity reads) — the strings must never drift silently.
        assertEquals("app.pocketshell.notification.ROUTE", NotificationRoute.EXTRA_ROUTE)
        assertEquals("open_app", NotificationRoute.ROUTE_OPEN_APP)
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
