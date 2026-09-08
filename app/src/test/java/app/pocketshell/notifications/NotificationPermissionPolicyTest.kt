package app.pocketshell.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P1 — the full truth table of the ONE runtime-permission decision
 * function. The policy is the anti-nag contract:
 *
 *   - pre-Android 13: never (the runtime permission does not exist);
 *   - granted: never (also covers rotation/recreation/restart after grant);
 *   - already requested by this install: never (the flag is written BEFORE
 *     the dialog opens, so recreation/process death mid-dialog cannot
 *     re-arm it);
 *   - the system's own implicit prompt already denied: never (respect it);
 *   - otherwise: exactly once.
 */
class NotificationPermissionPolicyTest {

    @Test
    fun `pre-13 SDKs never request - regardless of every other input`() {
        for (sdk in intArrayOf(26, 27, 28, 29, 30, 31, 32)) {
            assertFalse(
                "sdk=$sdk granted=false asked=false sysAsked=false",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = false, systemAlreadyAsked = false),
            )
            assertFalse(
                "sdk=$sdk granted=true",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = true, requestedBefore = false, systemAlreadyAsked = false),
            )
            assertFalse(
                "sdk=$sdk asked-before=true",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = true, systemAlreadyAsked = false),
            )
            assertFalse(
                "sdk=$sdk systemAlreadyAsked=true",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = false, systemAlreadyAsked = true),
            )
        }
    }

    @Test
    fun `granted never requests again`() {
        for (sdk in intArrayOf(33, 34, 35, 36)) {
            assertFalse(
                NotificationPermissionPolicy.shouldRequest(sdk, granted = true, requestedBefore = false, systemAlreadyAsked = false),
            )
            assertFalse(
                NotificationPermissionPolicy.shouldRequest(sdk, granted = true, requestedBefore = true, systemAlreadyAsked = true),
            )
        }
    }

    @Test
    fun `once requested by this install - never again whatever the answer was`() {
        for (sdk in intArrayOf(33, 34, 35, 36)) {
            assertFalse(
                "denied then flag set",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = true, systemAlreadyAsked = false),
            )
            assertFalse(
                "dismissed then flag set",
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = true, systemAlreadyAsked = true),
            )
        }
    }

    @Test
    fun `the system's own implicit prompt already denied - never request on top of it`() {
        for (sdk in intArrayOf(33, 34, 35, 36)) {
            assertFalse(
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = false, systemAlreadyAsked = true),
            )
        }
    }

    @Test
    fun `the one ask - 13 or newer, not granted, nobody has asked yet`() {
        for (sdk in intArrayOf(33, 34, 35, 36)) {
            assertTrue(
                NotificationPermissionPolicy.shouldRequest(sdk, granted = false, requestedBefore = false, systemAlreadyAsked = false),
            )
        }
    }

    @Test
    fun `the runtime-permission boundary is Android 13 (sdk 33)`() {
        assertFalse(
            NotificationPermissionPolicy.shouldRequest(32, granted = false, requestedBefore = false, systemAlreadyAsked = false),
        )
        assertTrue(
            NotificationPermissionPolicy.shouldRequest(33, granted = false, requestedBefore = false, systemAlreadyAsked = false),
        )
    }
}
