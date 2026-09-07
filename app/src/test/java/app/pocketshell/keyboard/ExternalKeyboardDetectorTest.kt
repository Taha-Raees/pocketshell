package app.pocketshell.keyboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1 P3 — the detector's transition machine, on virtual time (spec PART E:
 * detection state logic + transition notification logic, no faked device
 * tests — the Android scan/listener glue is pinned separately by the
 * structural contract test; real attach/detach is the §45 device gate).
 *
 * Pinned here:
 *   - no keyboard → connected stays false, no notice;
 *   - connect → flips true exactly once, after the stability window;
 *   - remove → flips false; reconnect → one NEW notice is allowed;
 *   - duplicate connect events produce ONE transition (no spam);
 *   - a Bluetooth-style flap inside the stability window produces NO
 *     transition (no flicker), a flap beyond it produces both transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExternalKeyboardDetectorTest {

    private val window = ExternalKeyboardDetector.DEFAULT_STABILIZE_MS

    @Test
    fun `no keyboard ever — stays disconnected with no notice`() = runTest {
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { false })
        detector.start()
        advanceTimeBy(10_000); runCurrent()
        assertFalse(detector.connected.value)
        assertNull(detector.notice.value)
    }

    @Test
    fun `connect flips connected after the stability window and emits one notice`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()
        assertFalse(detector.connected.value)

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window - 1); runCurrent()
        assertFalse("inside the stability window the old state holds", detector.connected.value)
        advanceTimeBy(1); runCurrent()
        assertTrue(detector.connected.value)
        assertEquals(1L, detector.notice.value?.seq)
    }

    @Test
    fun `duplicate connect events coalesce into one transition and one notice`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()

        present = true
        // Android fires a burst for one physical attach (BT especially).
        repeat(5) { detector.onInputDevicesChanged(); advanceTimeBy(window - 1); runCurrent() }
        advanceTimeBy(1); runCurrent()

        assertTrue(detector.connected.value)
        assertEquals(1L, detector.notice.value?.seq)
    }

    @Test
    fun `irrelevant device events never flip the state`() = runTest {
        // A mouse / touchscreen plugs and unplugs; the scan keeps answering false.
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { false })
        detector.start()
        advanceTimeBy(1_000); runCurrent()
        repeat(4) {
            detector.onInputDevicesChanged()
            advanceTimeBy(10_000); runCurrent()
        }
        assertFalse(detector.connected.value)
        assertNull(detector.notice.value)
    }

    @Test
    fun `bluetooth flap inside the stability window produces no transition`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(100); runCurrent()
        present = false               // the transient drop…
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1_000); runCurrent()

        assertFalse("a sub-window flap must not flicker the state", detector.connected.value)
        assertNull(detector.notice.value)
    }

    @Test
    fun `flap beyond the stability window produces both transitions`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1_000); runCurrent()
        assertTrue(detector.connected.value)

        present = false
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1_000); runCurrent()
        assertFalse(detector.connected.value)
    }

    @Test
    fun `disconnect restores silently and reconnect emits one new notice`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1); runCurrent()
        assertTrue(detector.connected.value)
        assertEquals(1L, detector.notice.value?.seq)

        present = false
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1); runCurrent()
        assertFalse(detector.connected.value)
        // no NEW notice on disconnect; the connect notice simply persists
        // until the UI dismisses it (no spam on removal).
        assertEquals(1L, detector.notice.value?.seq)

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1); runCurrent()
        assertTrue(detector.connected.value)
        assertEquals("reconnect allows exactly one new notice", 2L, detector.notice.value?.seq ?: -1L)
    }

    @Test
    fun `events before start are ignored`() = runTest {
        var present = true
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.onInputDevicesChanged()
        advanceTimeBy(10_000); runCurrent()
        assertFalse(detector.connected.value)

        detector.start()               // the launch scan sees the keyboard
        advanceTimeBy(window + 1); runCurrent()
        assertTrue(detector.connected.value)
    }

    @Test
    fun `stop cancels pending confirmations`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(1_000); runCurrent()
        present = true
        detector.onInputDevicesChanged()
        detector.stop()
        advanceTimeBy(10_000); runCurrent()
        assertFalse(detector.connected.value)
    }

    @Test
    fun `dismiss clears the notice without touching the connection state`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(10_000); runCurrent()
        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1); runCurrent()
        assertTrue(detector.connected.value)
        assertEquals(1L, detector.notice.value?.seq)

        detector.dismissNotice()
        assertNull(detector.notice.value)
        assertTrue("the hardware state is independent of the notice", detector.connected.value)
    }
}
