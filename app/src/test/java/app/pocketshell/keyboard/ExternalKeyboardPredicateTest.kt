package app.pocketshell.keyboard

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1 P3 — the external-keyboard predicate (spec "Detection Accuracy").
 *
 * Only actual keyboard-capable hardware may trigger the automatic on-screen
 * keyboard hiding. These pins reject every false positive the spec names:
 * touchscreen, game controller, mouse, stylus-class pointers, virtual/system
 * input devices, and button clusters (power/volume pads) that register
 * keyboard SOURCES without an alphabetic key map. The android.* constants
 * are real static-final values in the JVM suite (no method calls).
 */
class ExternalKeyboardPredicateTest {

    private val kb = InputDevice.SOURCE_KEYBOARD
    private val ts = InputDevice.SOURCE_TOUCHSCREEN
    private val mouse = InputDevice.SOURCE_MOUSE
    private val pad = InputDevice.SOURCE_GAMEPAD
    private val stick = InputDevice.SOURCE_JOYSTICK
    private val dpad = InputDevice.SOURCE_DPAD
    private val full = InputDevice.KEYBOARD_TYPE_ALPHABETIC
    private val partial = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
    private val none = InputDevice.KEYBOARD_TYPE_NONE

    @Test
    fun `a full alphabetic hardware keyboard is external`() {
        assertTrue(isExternalKeyboardDevice(kb, full, isVirtual = false))
    }

    @Test
    fun `a bluetooth keyboard with dpad bells is still external`() {
        assertTrue(isExternalKeyboardDevice(kb or dpad, full, isVirtual = false))
    }

    @Test
    fun `a keyboard-mouse composite device is external`() {
        assertTrue(isExternalKeyboardDevice(kb or mouse, full, isVirtual = false))
    }

    @Test
    fun `a dock and dex style keyboard are external`() {
        assertTrue(isExternalKeyboardDevice(kb or ts, full, isVirtual = false))
    }

    @Test
    fun `touchscreen is never a keyboard`() {
        assertFalse(isExternalKeyboardDevice(ts, full, isVirtual = false))
        assertFalse(isExternalKeyboardDevice(ts or dpad, partial, isVirtual = false))
    }

    @Test
    fun `mouse is never a keyboard`() {
        assertFalse(isExternalKeyboardDevice(mouse, full, isVirtual = false))
    }

    @Test
    fun `game controller is never a keyboard`() {
        assertFalse(isExternalKeyboardDevice(pad or stick, full, isVirtual = false))
    }

    @Test
    fun `virtual system input devices are not external hardware`() {
        assertFalse(isExternalKeyboardDevice(kb, full, isVirtual = true))
    }

    @Test
    fun `button clusters are not keyboards`() {
        // gpio-keys / power / volume style devices: keyboard sources, no alphabet.
        assertFalse(isExternalKeyboardDevice(kb, partial, isVirtual = false))
        assertFalse(isExternalKeyboardDevice(kb or dpad, partial, isVirtual = false))
    }

    @Test
    fun `no key map at all is not a keyboard`() {
        assertFalse(isExternalKeyboardDevice(kb, none, isVirtual = false))
    }

    @Test
    fun `no sources at all is not a keyboard`() {
        assertFalse(isExternalKeyboardDevice(0, full, isVirtual = false))
    }
}
