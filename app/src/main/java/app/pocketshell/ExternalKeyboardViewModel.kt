package app.pocketshell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.keyboard.ExternalKeyboardDetector
import app.pocketshell.keyboard.ExternalKeyboardNotice
import app.pocketshell.keyboard.ExternalKeyboardScanner
import app.pocketshell.keyboard.ExternalKeyboardVisibilityModel
import app.pocketshell.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * M7.1.1 — the Android glue around [ExternalKeyboardDetector] and
 * [ExternalKeyboardVisibilityModel]: the ONE authoritative keyboard-state
 * system lives in the model; this class owns its Android wiring.
 *
 * DETECTION (multi-mechanism — the M7.1 real-device lesson: a single
 * mechanism is how the feature passed JVM tests and still failed on
 * hardware):
 *
 *   1. InputManager.InputDeviceListener — the primary event source, all
 *      three callbacks (added/removed/changed) funnel the detector. No
 *      polling, no permissions.
 *   2. Application-level ComponentCallbacks2.onConfigurationChanged — the
 *      independent cross-check. Plugging/unplugging a keyboard is a system
 *      configuration change (keyboard/keyboardHidden/navigation — the
 *      manifest already declares them, so the activity is NOT recreated and
 *      the callback fires in place). Some OEM stacks can miss listener
 *      callbacks for Bluetooth HID (re)connection flows; the config path is
 *      a different system route and re-arms the same detector (whose
 *      stability window + confirm deadline coalesce the duplicate triggers).
 *   3. The launch scan at init — "app started with the keyboard already
 *      attached" (no unplug/replug required).
 *   4. ON_RESUME rescan — reconnects that happened while backgrounded.
 *
 * STATE: the model owns (preference, hardware, manualRequest) and derives
 * shouldShowOnscreenKeyboard; this class mirrors it into StateFlows for
 * Compose and wires the writers: detector transitions, the persisted
 * DataStore preference, and the user's explicit requests. Being a
 * root-scoped ViewModel, everything survives configuration changes; process
 * death re-evaluates reality from the same launch scan.
 */
class ExternalKeyboardViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = ExternalKeyboardDetector(
        scope = viewModelScope,
        queryConnected = { ExternalKeyboardScanner.hasExternalKeyboard(application) },
    )

    private val visibility = ExternalKeyboardVisibilityModel()

    private val repo = SettingsRepository(application)

    private val _connected = MutableStateFlow(false)
    private val _userEnabled = MutableStateFlow(true)
    private val _shouldShow = MutableStateFlow(true)

    /** Hardware state: is an external keyboard connected right now? */
    val externalKeyboardConnected: StateFlow<Boolean> = _connected

    /** The persistent user preference (Settings "On-screen keyboard"). */
    val onscreenKeyboardUserEnabled: StateFlow<Boolean> = _userEnabled

    /**
     * The effective on-screen keyboard visibility — the ONE state the UI
     * observes for the deck (spec: externalKeyboardConnected +
     * onscreenKeyboardUserEnabled → shouldShowOnscreenKeyboard).
     */
    val shouldShowOnscreenKeyboard: StateFlow<Boolean> = _shouldShow

    /** One-shot transition notice consumed by the root banner (both directions). */
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

    /**
     * Mechanism 2 — the configuration-change cross-check. Registered on the
     * APPLICATION (not the activity) so it is independent of composition;
     * fires for keyboard/keyboardHidden/navigation plus every other config
     * change, all of which simply re-arm the detector's window (cheap scan,
     * coalesced by the stability window + deadline — no polling).
     */
    private val configCallbacks = object : android.content.ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            detector.onInputDevicesChanged()
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}

        override fun onTrimMemory(level: Int) {}
    }

    private val inputManager =
        application.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        inputManager.registerInputDeviceListener(inputListener, mainHandler)
        application.registerComponentCallbacks(configCallbacks)
        // The launch scan: covers "app launched with the keyboard already
        // attached" — no unplug/replug required.
        detector.start()
        // Hardware transitions flow into the one authoritative model…
        viewModelScope.launch {
            detector.connected.collect { raw ->
                _connected.value = raw
                visibility.onHardwareChanged(raw)
                _shouldShow.value = visibility.shouldShowOnscreenKeyboard
            }
        }
        // …and so does the persisted preference (the model never writes it).
        viewModelScope.launch {
            repo.onscreenKeyboardEnabled.collect { enabled ->
                _userEnabled.value = enabled
                visibility.onUserPreferenceChanged(enabled)
                _shouldShow.value = visibility.shouldShowOnscreenKeyboard
            }
        }
    }

    /**
     * The user's explicit visibility request ([⌨] floating icon, deck
     * collapse toggle). Always wins — never gated, never written to the
     * preference.
     */
    fun requestShow(open: Boolean) {
        visibility.requestShow(open)
        _shouldShow.value = visibility.shouldShowOnscreenKeyboard
    }

    /**
     * The terminal-canvas tap-to-reopen. GATED by the authoritative model:
     * while an external keyboard is connected it does nothing (the deck
     * stays out of the hardware keyboard's way); otherwise it reopens the
     * deck exactly like before. Returns whether it took effect.
     */
    fun canvasTapReopen(): Boolean {
        val opened = visibility.canvasTapReopen()
        _shouldShow.value = visibility.shouldShowOnscreenKeyboard
        return opened
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
        getApplication<Application>().unregisterComponentCallbacks(configCallbacks)
        inputManager.unregisterInputDeviceListener(inputListener)
    }
}
