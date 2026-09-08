package app.pocketshell.keyboard

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * M7.1 P3 / M7.1.1 — live external-keyboard detection and the ONE
 * authoritative on-screen keyboard state system.
 *
 * PocketShell owns ONE keyboard (the deck at the app root, m4.0.12). When a
 * physical external keyboard (USB / Bluetooth / dock / DeX) is attached,
 * Android reports it as a hardware InputDevice — and the deck should get out
 * of the way; when it is detached, the deck should return. This file holds
 * both halves of that contract:
 *
 *   - [ExternalKeyboardDetector] — the detection layer: it observes Android
 *     input-device changes (plus the M7.1.1 confirm deadline) and exposes
 *     the hardware state [ExternalKeyboardDetector.connected] with one
 *     notice per real transition;
 *   - [ExternalKeyboardVisibilityModel] — the M7.1.1 authoritative state
 *     model: user preference + hardware state (+ the user's explicit
 *     requests) → shouldShowOnscreenKeyboard. The root composable observes
 *     it through ExternalKeyboardViewModel; nothing else owns visibility.
 *
 * Architecture (docs/TESTING.md §45/§47; spec PART B):
 *
 *   user preference (Settings: On-screen keyboard On/Off)
 *   + hardware state (ExternalKeyboardDetector.connected)
 *   ↓
 *   effective on-screen keyboard visibility (the model → the root)
 *
 * Detection is event-driven: InputManager.InputDeviceListener + the
 * configuration-change cross-check → a short stability window (coalesces
 * duplicate add/remove bursts and Bluetooth flapping) → one fresh device
 * scan → at most ONE state transition. No polling loops; a rescan hook
 * serves the resume-after-background path. No permissions are required for
 * any of these APIs.
 */

/** True when [sources]/[keyboardType]/[isVirtual] describe a real external keyboard.
 *
 *  The predicate rejects the false positives the spec names:
 *   - touchscreens, mice, gamepads, joysticks, stylus — no SOURCE_KEYBOARD bit;
 *   - virtual/system-synthesized input devices (isVirtual) — not hardware;
 *   - button clusters (gpio-keys / power / volume pads) — physical keyboard
 *     SOURCES but NOT alphabetic (KEYBOARD_TYPE_NON_ALPHABETIC).
 *  A combined keyboard+mouse composite device still has the keyboard bit → true.
 *  Pure and JVM-pinned (ExternalKeyboardPredicateTest). */
fun isExternalKeyboardDevice(sources: Int, keyboardType: Int, isVirtual: Boolean): Boolean =
    (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
        keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        !isVirtual

/**
 * The Android-facing scan: does the system CURRENTLY expose at least one
 * external keyboard device? Called by the detector after every stability
 * window — cheap (a device-id loop), never polled on a timer.
 */
object ExternalKeyboardScanner {
    fun hasExternalKeyboard(context: Context): Boolean {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            ?: return false
        for (id in im.inputDeviceIds) {
            val device = im.getInputDevice(id) ?: continue
            if (isExternalKeyboardDevice(device.sources, device.keyboardType, device.isVirtual)) {
                return true
            }
        }
        return false
    }
}

/**
 * One transition notice (M7.1.1: BOTH directions — the spec's connect text
 * and the "External keyboard disconnected." message). Emitted exactly once
 * per confirmed transition; duplicate events coalesce upstream and never
 * re-emit. The root picks the copy from [direction].
 */
data class ExternalKeyboardNotice(val seq: Long, val direction: Direction) {
    enum class Direction { CONNECTED, DISCONNECTED }
}

/**
 * The detector. Pure coroutine logic over an injected [queryConnected] scan
 * (JVM-testable with kotlinx-coroutines-test; the Android glue — the
 * InputDeviceListener and the configuration-change cross-check that call
 * [onInputDevicesChanged] — lives in ExternalKeyboardViewModel).
 *
 * Transition model (spec: deterministic, debounced, no flicker):
 *
 *   NO_EXTERNAL_KEYBOARD ⇄ EXTERNAL_KEYBOARD_CONNECTED
 *
 * Every input-device event (re)starts a [stabilizeMs] window; only when the
 * scan is still unchanged after the quiet window does the state flip — once.
 * Duplicate connect events cancel and reschedule each other, so they can
 * never produce repeated transitions. The window is short enough that a
 * normal USB attach/detach stays perceptually instant.
 *
 * M7.1.1 REAL-DEVICE FIX — the confirm deadline: the M7.1 design restarted
 * the stability window on EVERY event with no ceiling, so a device stack
 * that emits periodic input-device events (Bluetooth LE HID re-announcing
 * itself — LED/battery/layout config events are OEM reality) could starve
 * the confirmation FOREVER and detection would never fire. The deadline
 * ([DEFAULT_CONFIRM_DEADLINE_MS], anchored at the first unconfirmed event
 * of a burst) guarantees a fresh scan and at most one transition even under
 * an endless event storm, while sub-window flaps still never flicker.
 *
 * Notice policy (M7.1.1): every confirmed transition emits ONE
 * [ExternalKeyboardNotice] — connect AND disconnect (the spec's disconnect
 * message) — never spam: duplicates coalesce into a single transition.
 */
class ExternalKeyboardDetector(
    private val scope: CoroutineScope,
    private val queryConnected: () -> Boolean,
    private val stabilizeMs: Long = DEFAULT_STABILIZE_MS,
    private val confirmDeadlineMs: Long = DEFAULT_CONFIRM_DEADLINE_MS,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notice = MutableStateFlow<ExternalKeyboardNotice?>(null)
    val notice: StateFlow<ExternalKeyboardNotice?> = _notice.asStateFlow()

    private var started = false
    private var confirmJob: Job? = null
    private var deadlineJob: Job? = null
    private var nextNoticeSeq = 1L

    /** Begin observing: one initial scan through the same stability window. Idempotent. */
    fun start() {
        if (started) return
        started = true
        onInputDevicesChanged()
    }

    /**
     * The event funnels: InputManager.InputDeviceListener (all three
     * callbacks), the Application-level configuration-change cross-check,
     * and the resume/backgrounded re-evaluation path.
     *
     * Each event (re)starts the stability window; the FIRST event of a
     * burst also arms the hard confirm deadline, which no event storm can
     * push back — one of the two always runs [confirm].
     */
    fun onInputDevicesChanged() {
        if (!started) return
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(stabilizeMs)
            confirm()
        }
        if (deadlineJob == null) {
            deadlineJob = scope.launch {
                delay(confirmDeadlineMs)
                confirm()
            }
        }
    }

    /** The one scan-and-flip: whichever of window/deadline fires first wins. */
    private fun confirm() {
        deadlineJob?.cancel()
        deadlineJob = null
        confirmJob = null
        val raw = queryConnected()
        if (raw != _connected.value) {
            _connected.value = raw
            val direction = if (raw) ExternalKeyboardNotice.Direction.CONNECTED
            else ExternalKeyboardNotice.Direction.DISCONNECTED
            _notice.value = ExternalKeyboardNotice(seq = nextNoticeSeq, direction = direction)
            nextNoticeSeq += 1
        }
    }

    /** The UI acknowledged the notice (auto-dismiss timeout or user tap). */
    fun dismissNotice() {
        _notice.value = null
    }

    /** Stop observing (owner cleared): pending confirmations are cancelled. */
    fun stop() {
        confirmJob?.cancel()
        confirmJob = null
        deadlineJob?.cancel()
        deadlineJob = null
        started = false
    }

    companion object {
        /**
         * The stability window. Long enough to swallow Bluetooth's duplicate
         * add/remove bursts and re-probe gaps, short enough that USB
         * attach/detach feels immediate (the spec's determinism bar).
         */
        const val DEFAULT_STABILIZE_MS = 400L

        /**
         * M7.1.1 — the confirm deadline. The ceiling on how long an endless
         * event stream can defer the scan: long enough that ordinary BT
         * attach bursts (which the window coalesces) never feel it, short
         * enough that a pathological event source still lands within seconds.
         */
        const val DEFAULT_CONFIRM_DEADLINE_MS = 2_000L
    }
}

/**
 * M7.1.1 — the ONE authoritative keyboard-state system (spec: "Create one
 * authoritative keyboard-state system... User Preference + Real Physical
 * Keyboard State → Effective Onscreen Keyboard State"). Replaces the M7.1
 * SUPPRESS/RESTORE memory-overlay policy, whose restore semantics could
 * fight the user and whose state lived scattered across the root composable.
 *
 * The three exposed states (exactly the spec's model):
 *
 *   externalKeyboardConnected     — hardware truth (from the detector).
 *   onscreenKeyboardUserEnabled   — the PERSISTENT user preference
 *                                   (Settings "On-screen keyboard" On/Off;
 *                                   DataStore; detection NEVER writes it).
 *   shouldShowOnscreenKeyboard    — the effective visibility the UI observes.
 *
 * Derivation:
 *
 *   shouldShow = manualRequest ?: (userEnabled && !externalConnected)
 *
 *   - Baseline: the deck shows exactly when the user's preference says so
 *     and no external keyboard is connected. A connect therefore hides it
 *     (the spec's required automatic hide), a disconnect restores it "when
 *     appropriate" — and if the user disabled the on-screen keyboard in
 *     Settings, the disconnect does NOT force it back on.
 *   - manualRequest is the user's LAST EXPLICIT ACTION ([⌨] floating icon,
 *     deck collapse toggle): the user always wins over the machine, in both
 *     directions, without the preference being touched ("external keyboard
 *     detection is a temporary runtime override and must not silently
 *     overwrite the user's preference" — and neither does any other
 *     automatic path).
 *   - manualRequest is CLEARED on hardware transitions and preference
 *     changes: every real-world state change re-evaluates from the fresh
 *     (preference, hardware) pair — the spec's disconnect rule ("re-evaluate
 *     the user's preference"), and the reason a pre-connect manual state can
 *     never leak across a hardware event.
 *
 * Pure and synchronous — JVM-pinned by ExternalKeyboardVisibilityModelTest;
 * the Android glue (detector → onHardwareChanged, DataStore →
 * onUserPreferenceChanged, taps → requestShow) lives in
 * ExternalKeyboardViewModel, the only other writer of this state.
 */
class ExternalKeyboardVisibilityModel(
    initialUserEnabled: Boolean = true,
    initialConnected: Boolean = false,
) {
    private var userEnabledField = initialUserEnabled
    private var connectedField = initialConnected
    private var manualRequestField: Boolean? = null

    /** The persistent user preference (Settings). Detection never writes it. */
    var userEnabled: Boolean
        get() = userEnabledField
        private set(value) { userEnabledField = value }

    /** Hardware truth: is an external keyboard connected right now? */
    var externalConnected: Boolean
        get() = connectedField
        private set(value) { connectedField = value }

    /** The user's last explicit request, or null when the baseline rules. */
    val manualRequest: Boolean?
        get() = manualRequestField

    /** The effective on-screen keyboard visibility the UI observes. */
    val shouldShowOnscreenKeyboard: Boolean
        get() = manualRequestField ?: (userEnabledField && !connectedField)

    /**
     * A hardware transition (connect OR disconnect — edge-triggered: same
     * value is a no-op). Clears the manual request so the fresh
     * (preference, hardware) pair rules again.
     */
    fun onHardwareChanged(connected: Boolean) {
        if (connected == connectedField) return
        connectedField = connected
        manualRequestField = null
    }

    /**
     * The persisted preference changed (Settings toggle, process start).
     * Clears the manual request — a preference change is a real-world state
     * change and re-evaluates immediately (the M7.1 "toggle takes effect at
     * once" behavior, now over the whole model).
     */
    fun onUserPreferenceChanged(enabled: Boolean) {
        if (enabled == userEnabledField) return
        userEnabledField = enabled
        manualRequestField = null
    }

    /**
     * The user's explicit visibility request ([⌨] floating icon, deck
     * collapse toggle). Wins over everything until the next hardware or
     * preference change.
     */
    fun requestShow(open: Boolean) {
        manualRequestField = open
    }

    /**
     * The terminal-canvas tap-to-reopen affordance. M7.1 REAL-DEVICE FIX:
     * the canvas tap used to ride the same funnel as an explicit [⌨] open
     * and CANCELLED the suppression — on a terminal app the canvas tap is
     * the most-used gesture, so the deck popped back up immediately after
     * every connect and the auto-hide looked broken. The gate lives HERE,
     * in the one authoritative system: while an external keyboard is
     * connected the canvas tap does not reopen the deck (the hardware
     * keyboard is typing into that surface); with no external keyboard the
     * m4.0.12 tap-to-reopen behavior is preserved exactly.
     */
    fun canvasTapReopen(): Boolean {
        if (connectedField) return false
        requestShow(true)
        return true
    }
}
