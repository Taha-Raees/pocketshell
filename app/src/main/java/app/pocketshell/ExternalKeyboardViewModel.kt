package app.pocketshell

import android.app.Application
import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.keyboard.ExternalKeyboardDetector
import app.pocketshell.keyboard.ExternalKeyboardNotice
import app.pocketshell.keyboard.ExternalKeyboardScanner
import kotlinx.coroutines.flow.StateFlow

/**
 * M7.1 P3 — Android glue around [ExternalKeyboardDetector].
 *
 * The detector is the testable core (pure coroutine logic); this ViewModel
 * owns its Android wiring: the InputManager.InputDeviceListener (a
 * system listener — no polling, no permissions) and the process-lifetime
 * that makes "notify once" honest. Being a root-scoped ViewModel, both
 * survive configuration changes — a rotation neither re-fires the connect
 * notice nor restarts detection; process death re-evaluates reality from
 * the same launch scan.
 *
 * All three listener callbacks funnel into the detector's stability
 * window, so duplicate/burst events coalesce into at most ONE transition.
 */
class ExternalKeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = ExternalKeyboardDetector(
        scope = viewModelScope,
        queryConnected = { ExternalKeyboardScanner.hasExternalKeyboard(application) },
    )

    /** Hardware state: is an external keyboard connected right now? */
    val externalKeyboardConnected: StateFlow<Boolean> = detector.connected

    /** One-shot connect notice consumed by the root banner. */
    val connectNotice: StateFlow<ExternalKeyboardNotice?> = detector.notice

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            detector.onInputDevicesChanged()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            detector.onInputDevicesChanged()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // Some composite/BT devices re-announce themselves (config, LED
            // state) without an add/remove pair — same stability window.
            detector.onInputDevicesChanged()
        }
    }

    private val inputManager =
        application.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        inputManager.registerInputDeviceListener(inputListener, mainHandler)
        // The launch scan: covers "app launched with the keyboard already
        // attached" — no unplug/replug required.
        detector.start()
    }

    /** Lifecycle-safe re-evaluation (ON_RESUME after the app was backgrounded). */
    fun rescan() {
        detector.onInputDevicesChanged()
    }

    fun dismissNotice() {
        detector.dismissNotice()
    }

    override fun onCleared() {
        detector.stop()
        inputManager.unregisterInputDeviceListener(inputListener)
    }
}
