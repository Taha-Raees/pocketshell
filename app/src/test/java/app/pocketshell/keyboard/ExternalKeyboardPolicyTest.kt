package app.pocketshell.keyboard

import app.pocketshell.keyboard.ExternalKeyboardPolicyAction.NONE
import app.pocketshell.keyboard.ExternalKeyboardPolicyAction.RESTORE
import app.pocketshell.keyboard.ExternalKeyboardPolicyAction.SUPPRESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1 P3 — the effective on-screen keyboard logic (spec PART E).
 *
 * The decision table: user preference (auto mode) + hardware state →
 * SUPPRESS / RESTORE / NONE. The scenario tests then walk the FULL cycle the
 * spec pins, using exactly the state model the root composable holds:
 *
 *   connected + auto     — hardware state   (detector flow + DataStore)
 *   deckVisible          — the user's manual visibility state (root owner)
 *   preExternalExpanded  — the suppression memory (null = not suppressed)
 *
 * The critical property: the user's manual preference is never destroyed by
 * a connect — only overlaid — and a disconnect hands exactly that state back.
 */
class ExternalKeyboardPolicyTest {

    private fun resolve(connected: Boolean, auto: Boolean, suppressed: Boolean) =
        ExternalKeyboardPolicy.resolve(connected, auto, suppressed)

    @Test
    fun `the decision table`() {
        // steady, no keyboard
        assertEquals(NONE, resolve(connected = false, auto = true, suppressed = false))
        // keyboard arrives with auto ON
        assertEquals(SUPPRESS, resolve(connected = true, auto = true, suppressed = false))
        // keyboard leaves while suppressed
        assertEquals(RESTORE, resolve(connected = false, auto = true, suppressed = true))
        // the toggle is switched OFF mid-suppression — immediate effect
        assertEquals(RESTORE, resolve(connected = true, auto = false, suppressed = true))
        // already suppressed and still connected
        assertEquals(NONE, resolve(connected = true, auto = true, suppressed = true))
        // auto OFF, never suppressed — the keyboard never touches the deck
        assertEquals(NONE, resolve(connected = true, auto = false, suppressed = false))
        assertEquals(NONE, resolve(connected = false, auto = false, suppressed = false))
        // suppression cleared but no keyboard (post-restore)
        assertEquals(NONE, resolve(connected = false, auto = true, suppressed = false))
    }

    /** The exact root state machine, mirrored from PocketShellRoot. */
    private class RootModel {
        var connected = false
        var auto = true
        var deckVisible = true
        var pre: Boolean? = null

        fun applyPolicy() {
            when (ExternalKeyboardPolicy.resolve(connected, auto, pre != null)) {
                SUPPRESS -> {
                    pre = deckVisible
                    deckVisible = false
                }
                RESTORE -> {
                    deckVisible = pre ?: true
                    pre = null
                }
                NONE -> {}
            }
        }

        fun openManually(open: Boolean) {
            if (open) pre = null   // user override cancels suppression
            deckVisible = open
        }
    }

    @Test
    fun `auto ON - the spec cycle`() {
        val m = RootModel()
        m.applyPolicy()
        assertTrue("no keyboard → deck behaves normally", m.deckVisible)

        m.connected = true; m.applyPolicy()
        assertFalse("keyboard connected → deck hidden", m.deckVisible)

        m.connected = false; m.applyPolicy()
        assertTrue("keyboard disconnected → deck restored", m.deckVisible)
        assertEquals(null, m.pre)
    }

    @Test
    fun `a disconnect restores the user's MANUAL HIDDEN state, not blind open`() {
        val m = RootModel()
        m.openManually(false)          // the user hid the deck themselves
        m.connected = true; m.applyPolicy()
        assertFalse(m.deckVisible)
        m.connected = false; m.applyPolicy()
        assertFalse("restore returns to the pre-connect manual state", m.deckVisible)
    }

    @Test
    fun `auto OFF - connecting never changes the deck`() {
        val m = RootModel()
        m.auto = false
        m.connected = true; m.applyPolicy()
        assertTrue("manual behavior stays under the user's control", m.deckVisible)

        // and the OFF toggle mid-suppression restores immediately
        val s = RootModel()
        s.connected = true; s.applyPolicy()
        assertFalse(s.deckVisible)
        s.auto = false; s.applyPolicy()
        assertTrue("toggle takes effect immediately", s.deckVisible)
        assertEquals(null, s.pre)
    }

    @Test
    fun `manual override during suppression wins over the later disconnect`() {
        val m = RootModel()
        m.connected = true; m.applyPolicy()       // suppressed (pre = true)
        assertFalse(m.deckVisible)

        m.openManually(true)                      // the user explicitly reopens
        assertTrue(m.deckVisible)
        assertEquals(null, m.pre)

        m.connected = false; m.applyPolicy()      // the disconnect must NOT fight them
        assertTrue("manual state preserved", m.deckVisible)
    }

    @Test
    fun `reconnect after a manual override re-applies suppression`() {
        val m = RootModel()
        m.connected = true; m.applyPolicy()
        m.openManually(true)                      // override
        m.connected = false; m.applyPolicy()      // no-op (manual open)
        m.connected = true; m.applyPolicy()       // a NEW connect suppresses again
        assertFalse(m.deckVisible)
        assertEquals(true, m.pre)
        m.connected = false; m.applyPolicy()
        assertTrue(m.deckVisible)
    }

    @Test
    fun `setting auto ON mid-connection applies immediately`() {
        val m = RootModel()
        m.auto = false
        m.connected = true; m.applyPolicy()
        assertTrue(m.deckVisible)
        m.auto = true; m.applyPolicy()
        assertFalse("the enabled toggle suppresses at once", m.deckVisible)
    }
}
