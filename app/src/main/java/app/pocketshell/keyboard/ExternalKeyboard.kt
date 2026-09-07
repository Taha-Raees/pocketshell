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
 * M7.1 P3 — live external-keyboard detection.
 *
 * PocketShell owns ONE keyboard (the deck at the app root, m4.0.12). When a
 * physical external keyboard (USB / Bluetooth / dock / DeX) is attached,
 * Android reports it as a hardware InputDevice — and the deck should get out
 * of the way; when it is detached, the deck should return. This file is the
 * detection layer ONLY: it observes Android input-device changes and exposes
 * one state — [ExternalKeyboardDetector.connected]. The VISIBILITY decision
 * lives with the state owner (PocketShellRoot) via
 * [ExternalKeyboardPolicy], so there is exactly one keyboard state system.
 *
 * Architecture (docs/TESTING.md §45; spec PART B):
 *
 *   user preference (Settings: auto-hide ON/OFF)
 *   + hardware state (ExternalKeyboardDetector.connected)
 *   ↓
 *   effective on-screen keyboard visibility (PocketShellRoot)
 *
 * Detection is event-driven: InputManager.InputDeviceListener → a short
 * stability window (coalesces duplicate add/remove bursts and Bluetooth
 * flapping) → one fresh device scan → at most ONE state transition. No
 * polling loops; a rescan hook serves the resume-after-background path.
 * No permissions are required for any of these APIs.
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

/** One connect-transition notice (spec PART D: fire once per transition, never spam). */
data class ExternalKeyboardNotice(val seq: Long)

/**
 * The detector. Pure coroutine logic over an injected [queryConnected] scan
 * (JVM-testable with kotlinx-coroutines-test; the Android glue — the
 * InputDeviceListener that calls [onInputDevicesChanged] — lives in
 * ExternalKeyboardViewModel).
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
 * Notice policy: every stable false→true transition (including the launch
 * scan finding a keyboard already attached — "once per relevant session")
 * emits one [ExternalKeyboardNotice]; disconnects restore silently.
 */
class ExternalKeyboardDetector(
    private val scope: CoroutineScope,
    private val queryConnected: () -> Boolean,
    private val stabilizeMs: Long = DEFAULT_STABILIZE_MS,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _notice = MutableStateFlow<ExternalKeyboardNotice?>(null)
    val notice: StateFlow<ExternalKeyboardNotice?> = _notice.asStateFlow()

    private var started = false
    private var confirmJob: Job? = null
    private var nextNoticeSeq = 1L

    /** Begin observing: one initial scan through the same stability window. Idempotent. */
    fun start() {
        if (started) return
        started = true
        onInputDevicesChanged()
    }

    /** InputManager listener hook + the resume/backgrounded re-evaluation path. */
    fun onInputDevicesChanged() {
        if (!started) return
        confirmJob?.cancel()
        confirmJob = scope.launch {
            delay(stabilizeMs)
            val raw = queryConnected()
            if (raw != _connected.value) {
                _connected.value = raw
                if (raw) {
                    _notice.value = ExternalKeyboardNotice(seq = nextNoticeSeq)
                    nextNoticeSeq += 1
                }
            }
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
        started = false
    }

    companion object {
        /**
         * The stability window. Long enough to swallow Bluetooth's duplicate
         * add/remove bursts and re-probe gaps, short enough that USB
         * attach/detach feels immediate (the spec's determinism bar).
         */
        const val DEFAULT_STABILIZE_MS = 400L
    }
}

/**
 * The pure VISIBILITY policy the root applies on every (connected, auto)
 * change (spec PART B: user preference + hardware state → effective
 * visibility, without destroying the user's manual state).
 *
 *   SUPPRESS — external keyboard arrived and auto mode is on: hide the deck,
 *              remembering the user's pre-connect manual state.
 *   RESTORE  — keyboard left (or auto mode turned off) while suppressed:
 *              give the deck back exactly the remembered state.
 *   NONE     — nothing to do (steady states, and suppression already active).
 *
 * JVM-pinned by ExternalKeyboardPolicyTest.
 */
enum class ExternalKeyboardPolicyAction { SUPPRESS, RESTORE, NONE }

object ExternalKeyboardPolicy {
    fun resolve(connected: Boolean, autoEnabled: Boolean, suppressionActive: Boolean): ExternalKeyboardPolicyAction =
        when {
            suppressionActive && (!connected || !autoEnabled) -> ExternalKeyboardPolicyAction.RESTORE
            !suppressionActive && connected && autoEnabled -> ExternalKeyboardPolicyAction.SUPPRESS
            else -> ExternalKeyboardPolicyAction.NONE
        }
}
