package app.pocketshell.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1.1 — the ONE authoritative keyboard-state system
 * ([ExternalKeyboardVisibilityModel]), the pure decision core the real
 * device runs: User Preference + Real Physical Keyboard State → Effective
 * On-Screen Keyboard State.
 *
 * The full matrix + the lifecycle rules the spec pins:
 *   - baseline: shouldShow = userEnabled && !externalConnected;
 *   - connect hides, disconnect re-evaluates the PREFERENCE (matrix #3) —
 *     an Off preference is never force-reopened;
 *   - the user's explicit request always wins ([⌨] / deck toggle) and never
 *     touches the preference ("detection is a temporary runtime override
 *     and must not silently overwrite the user's preference");
 *   - hardware transitions and preference changes clear the manual request
 *     — every real-world change re-evaluates from the fresh pair;
 *   - the terminal-canvas tap is GATED while an external keyboard is
 *     connected (the M7.1 real-device failure) and preserved otherwise.
 */
class ExternalKeyboardVisibilityModelTest {

    // ---- the decision matrix ----------------------------------------------

    @Test
    fun `the decision matrix - request beats everything else`() {
        val m = ExternalKeyboardVisibilityModel()
        val combos = listOf(false to false, false to true, true to false, true to true)
        for ((enabled, connected) in combos) {
            val base = ExternalKeyboardVisibilityModel(
                initialUserEnabled = enabled,
                initialConnected = connected,
            )
            val expectedBaseline = enabled && !connected
            assertTrue(
                "($enabled, $connected, request=null) → baseline",
                base.shouldShowOnscreenKeyboard == expectedBaseline && base.manualRequest == null,
            )
            for (request in listOf(true, false)) {
                base.requestShow(request)
                assertTrue(
                    "($enabled, $connected, request=$request) → the request rules",
                    base.shouldShowOnscreenKeyboard == request,
                )
            }
        }
    }

    @Test
    fun `defaults - preference on, no keyboard, deck shows`() {
        val m = ExternalKeyboardVisibilityModel()
        assertTrue(m.userEnabled)
        assertFalse(m.externalConnected)
        assertNull(m.manualRequest)
        assertTrue(m.shouldShowOnscreenKeyboard)
    }

    // ---- the spec's four transitions ----------------------------------------

    @Test
    fun `matrix 1 - app starts with no external keyboard - behaves normally`() {
        val m = ExternalKeyboardVisibilityModel()
        assertTrue(m.shouldShowOnscreenKeyboard)
        m.onHardwareChanged(false)      // noise, not a transition
        assertTrue(m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `matrix 2 - connect while app is open - deck hides`() {
        val m = ExternalKeyboardVisibilityModel()
        m.onHardwareChanged(true)
        assertTrue(m.externalConnected)
        assertFalse("the automatic hide is unconditional", m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `matrix 3 - disconnect restores the user PREFERENCE, not a memory`() {
        val on = ExternalKeyboardVisibilityModel()
        on.onHardwareChanged(true)
        assertFalse(on.shouldShowOnscreenKeyboard)
        on.onHardwareChanged(false)
        assertTrue("preference On → the deck returns", on.shouldShowOnscreenKeyboard)

        val off = ExternalKeyboardVisibilityModel()
        off.onUserPreferenceChanged(false)   // the user disabled it in Settings
        off.onHardwareChanged(true)
        assertFalse(off.shouldShowOnscreenKeyboard)
        off.onHardwareChanged(false)
        assertFalse(
            "preference Off → the disconnect does NOT force it back on",
            off.shouldShowOnscreenKeyboard,
        )
    }

    @Test
    fun `matrix 4 - app starts with the keyboard already attached - deck disabled immediately`() {
        val m = ExternalKeyboardVisibilityModel(initialConnected = true)
        assertFalse(m.shouldShowOnscreenKeyboard)
        // and the launch-scan re-report of the same truth is a no-op
        m.onHardwareChanged(true)
        assertFalse(m.shouldShowOnscreenKeyboard)
    }

    // ---- the manual request lifecycle ----------------------------------------

    @Test
    fun `explicit open while connected wins immediately and survives the disconnect`() {
        val m = ExternalKeyboardVisibilityModel()
        m.onHardwareChanged(true)
        assertFalse(m.shouldShowOnscreenKeyboard)

        m.requestShow(true)              // the [⌨] floating icon
        assertTrue("the user's explicit open beats the override", m.shouldShowOnscreenKeyboard)

        m.onHardwareChanged(false)       // the disconnect must NOT fight them
        assertTrue(m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `explicit close stays until the next real-world change`() {
        val m = ExternalKeyboardVisibilityModel()
        m.requestShow(false)             // the deck's collapse toggle
        assertFalse(m.shouldShowOnscreenKeyboard)

        m.onHardwareChanged(true)        // a hardware transition clears it…
        assertNull(m.manualRequest)
        assertFalse("…and the connect keeps the deck hidden", m.shouldShowOnscreenKeyboard)

        m.onHardwareChanged(false)       // …so the disconnect re-evaluates the preference
        assertTrue("preference On → restored", m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `a same-value hardware report never clears the manual request`() {
        val m = ExternalKeyboardVisibilityModel()
        m.requestShow(false)
        m.onHardwareChanged(false)       // no transition — just an event
        assertFalse("request intact", m.shouldShowOnscreenKeyboard)
        m.requestShow(true)
        m.onHardwareChanged(false)
        assertTrue(m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `the settings toggle takes effect immediately and clears any request`() {
        val m = ExternalKeyboardVisibilityModel(initialConnected = true)
        m.requestShow(true)              // user opened it over the override
        assertTrue(m.shouldShowOnscreenKeyboard)

        m.onUserPreferenceChanged(false) // Settings Off while connected
        assertFalse("the Off applies at once", m.shouldShowOnscreenKeyboard)
        assertNull(m.manualRequest)

        m.onUserPreferenceChanged(true)  // back On while still connected
        assertFalse("the hardware override rules again", m.shouldShowOnscreenKeyboard)
        m.onHardwareChanged(false)
        assertTrue(m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `a same-value preference change never clears the manual request`() {
        val m = ExternalKeyboardVisibilityModel()
        m.requestShow(false)
        m.onUserPreferenceChanged(true)  // same value as the default
        assertFalse(m.shouldShowOnscreenKeyboard)
    }

    @Test
    fun `the preference is never overwritten by detection or requests`() {
        val m = ExternalKeyboardVisibilityModel()
        m.onHardwareChanged(true)
        m.requestShow(true)
        m.onHardwareChanged(false)
        m.requestShow(false)
        m.onHardwareChanged(true)
        assertTrue(
            "through every transition the persisted preference stays On",
            m.userEnabled,
        )
    }

    // ---- the terminal-canvas tap gate (the M7.1 real-device failure) ---------

    @Test
    fun `canvas tap does NOT reopen while an external keyboard is connected`() {
        val m = ExternalKeyboardVisibilityModel()
        m.onHardwareChanged(true)
        assertTrue("the deck is suppressed by the connect", !m.shouldShowOnscreenKeyboard)
        val opened = m.canvasTapReopen()
        assertFalse("the gate refused the tap", opened)
        assertNull("no manual request was recorded", m.manualRequest)
        assertFalse(
            "the auto-hide survives the most-used gesture in a terminal app",
            m.shouldShowOnscreenKeyboard,
        )
    }

    @Test
    fun `canvas tap reopens when no external keyboard is connected`() {
        val m = ExternalKeyboardVisibilityModel()
        m.requestShow(false)             // collapsed manually, no keyboard
        val opened = m.canvasTapReopen()
        assertTrue(opened)
        assertTrue(m.shouldShowOnscreenKeyboard)
    }
}
