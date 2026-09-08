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
 * M7.1 P3 / M7.1.1 — the detector's transition machine, on virtual time
 * (spec PART E: detection state logic + transition notification logic, no
 * faked device tests — the Android scan/listener glue is pinned separately
 * by the structural contract test; real attach/detach is the §47 device
 * gate).
 *
 * Pinned here:
 *   - no keyboard → connected stays false, no notice;
 *   - connect → flips true exactly once, after the stability window, ONE
 *     CONNECTED notice;
 *   - remove → flips false with ONE DISCONNECTED notice (M7.1.1: the spec's
 *     "External keyboard disconnected." message — the M7.1 "silent
 *     disconnect" is retired); reconnect → one NEW notice;
 *   - duplicate connect events coalesce into ONE transition (no spam);
 *   - a Bluetooth-style flap inside the stability window produces NO
 *     transition (no flicker), a flap beyond it produces both transitions;
 *   - M7.1.1 THE REAL-DEVICE FIX: an ENDLESS event storm (periodic
 *     onInputDeviceChanged re-announcements, OEM/LE-HID reality) can no
 *     longer starve the confirmation — the hard confirm deadline runs the
 *     scan and lands the transition no matter how many events arrive;
 *   - launch-scan / events-before-start / stop / dismiss semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExternalKeyboardDetectorTest {

    private val window = ExternalKeyboardDetector.DEFAULT_STABILIZE_MS
    private val deadline = ExternalKeyboardDetector.DEFAULT_CONFIRM_DEADLINE_MS

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
        assertEquals(
            ExternalKeyboardNotice.Direction.CONNECTED,
            detector.notice.value?.direction,
        )
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
        assertEquals(ExternalKeyboardNotice.Direction.CONNECTED, detector.notice.value?.direction)

        present = false
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1_000); runCurrent()
        assertFalse(detector.connected.value)
        assertEquals(
            ExternalKeyboardNotice.Direction.DISCONNECTED,
            detector.notice.value?.direction,
        )
    }

    @Test
    fun `disconnect emits ONE disconnected notice and reconnect one new connect notice`() = runTest {
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
        // M7.1.1: the disconnect is announced exactly once (spec: "Notify
        // the user: External keyboard disconnected.") — never spam.
        assertEquals(2L, detector.notice.value?.seq)
        assertEquals(
            ExternalKeyboardNotice.Direction.DISCONNECTED,
            detector.notice.value?.direction,
        )

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window + 1); runCurrent()
        assertTrue(detector.connected.value)
        assertEquals("reconnect allows exactly one new notice", 3L, detector.notice.value?.seq ?: -1L)
    }

    // ---- M7.1.1 — the confirm deadline (the real-device starvation fix) ----

    @Test
    fun `an endless event storm can no longer starve detection - the deadline confirms`() = runTest {
        // A BT LE stack re-announcing its device every 300ms — each event
        // restarts the 400ms stability window, so the M7.1 design (window
        // only, no ceiling) NEVER confirms. The M7.1.1 deadline must.
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(1_000); runCurrent()

        present = true
        var transitions = 0
        var lastSeq = 0L
        // Events every 300ms — inside the window, forever.
        for (t in 1..8) {
            detector.onInputDevicesChanged()
            advanceTimeBy(300); runCurrent()
            val seq = detector.notice.value?.seq ?: 0L
            if (seq > lastSeq) { transitions++; lastSeq = seq }
        }
        // The deadline (2s from the first event) fired inside the storm:
        assertTrue("the deadline confirms despite the endless storm", detector.connected.value)
        assertEquals("exactly one transition happened", 1, transitions)
        assertEquals(1L, lastSeq)
    }

    @Test
    fun `the deadline fires at the exact ceiling when events never stop`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(1_000); runCurrent()

        present = true
        // Saturate right up to (but not across) the deadline.
        var elapsed = 0L
        while (elapsed + 300 <= deadline) {
            detector.onInputDevicesChanged()
            advanceTimeBy(300)
            elapsed += 300
        }
        runCurrent()
        assertFalse("before the ceiling the M7.1 window semantics hold", detector.connected.value)

        detector.onInputDevicesChanged()   // yet another event at the ceiling
        advanceTimeBy(deadline - elapsed); runCurrent()
        assertTrue("the deadline lands the transition", detector.connected.value)
        assertEquals(1L, detector.notice.value?.seq)

        // And the state is STABLE afterwards: more events, no more transitions.
        repeat(3) {
            detector.onInputDevicesChanged()
            advanceTimeBy(300); runCurrent()
        }
        assertTrue(detector.connected.value)
        assertEquals("no duplicate notices after the storm", 1L, detector.notice.value?.seq)
    }

    @Test
    fun `a single event still confirms through the window - the deadline never delays it`() = runTest {
        var present = false
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(1_000); runCurrent()

        present = true
        detector.onInputDevicesChanged()
        advanceTimeBy(window - 1); runCurrent()
        assertFalse(detector.connected.value)
        advanceTimeBy(1); runCurrent()
        assertTrue("USB attach stays perceptually instant (window, not deadline)", detector.connected.value)
    }

    @Test
    fun `a disconnect under an event storm also lands through the deadline`() = runTest {
        var present = true
        val detector = ExternalKeyboardDetector(backgroundScope, queryConnected = { present })
        detector.start()
        advanceTimeBy(window + 1_000); runCurrent()
        assertTrue(detector.connected.value)
        detector.dismissNotice()

        present = false
        for (t in 1..8) {
            detector.onInputDevicesChanged()
            advanceTimeBy(300); runCurrent()
        }
        assertFalse("the storm must not hide the unplugging", detector.connected.value)
        assertEquals(
            ExternalKeyboardNotice.Direction.DISCONNECTED,
            detector.notice.value?.direction,
        )
    }

    // ---- the M7.1 semantics that must survive ------------------------------

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
