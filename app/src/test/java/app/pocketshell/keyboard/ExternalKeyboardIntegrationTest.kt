package app.pocketshell.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.1 P3 / M7.1.1 — the integration contract (structural pins over the
 * real sources; the same honest technique as HomeLauncherRowsTest — the
 * JVM suite has no Robolectric/device runner).
 *
 * What is pinned here is the glue the unit tests cannot execute:
 *
 *   1. the ROOT observes the ONE authoritative effective state
 *      (shouldShowOnscreenKeyboard) as the deck visibility and holds NO
 *      local copy of it any more (no M7.1 suppression memory);
 *   2. every user-facing keyboard write funnels through the model: explicit
 *      controls via requestShow, the terminal canvas tap via the GATED
 *      canvasTapReopen (the M7.1 real-device failure);
 *   3. the web-focus auto-open is gated while an external keyboard is
 *      connected;
 *   4. the ViewModel registers/unregisters the InputManager listener AND
 *      the Application-level configuration-change cross-check (the M7.1.1
 *      second mechanism) and runs the launch scan — event-driven, no
 *      polling;
 *   5. ON_RESUME rescans — the backgrounded-connect rule;
 *   6. the notice banner is mounted at the root for BOTH directions with
 *      dismiss + Settings;
 *   7. the Settings control exists, persists via the ONE DataStore
 *      repository under the onscreen_keyboard_enabled key, and defaults ON;
 *   8. the detector stays visibility-free — the model in the same file is
 *      the ONE authoritative state system.
 *
 * Real attach/detach behavior on hardware is the docs/TESTING.md §47 gate —
 * never claimed from a JVM run.
 */
class ExternalKeyboardIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        val file = relatives.map { File(it) }.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "source not found on this runner: ${relatives.first()}",
            file != null,
        )
        return file!!.readText()
    }

    /** Comments + string CONTENTS stripped — structural tokens only. */
    private fun stripCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '"' -> {
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                    out.append("\"\"")
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun readBoth(relatives: List<String>): Pair<String, String> {
        val raw = readSource(relatives)
        return raw to stripCommentsAndStrings(raw)
    }

    private val mainPair: Pair<String, String> by lazy {
        readBoth(listOf(
            "app/src/main/java/app/pocketshell/MainActivity.kt",
            "src/main/java/app/pocketshell/MainActivity.kt",
        ))
    }
    private val mainRaw get() = mainPair.first
    private val mainCode get() = mainPair.second

    private val vmPair: Pair<String, String> by lazy {
        readBoth(listOf(
            "app/src/main/java/app/pocketshell/ExternalKeyboardViewModel.kt",
            "src/main/java/app/pocketshell/ExternalKeyboardViewModel.kt",
        ))
    }
    private val vmRaw get() = vmPair.first
    private val vmCode get() = vmPair.second

    private val settingsPair: Pair<String, String> by lazy {
        readBoth(listOf(
            "app/src/main/java/app/pocketshell/settings/SettingsRepository.kt",
            "src/main/java/app/pocketshell/settings/SettingsRepository.kt",
        ))
    }
    private val settingsRaw get() = settingsPair.first
    private val settingsCode get() = settingsPair.second

    private val settingsScreenPair: Pair<String, String> by lazy {
        readBoth(listOf(
            "app/src/main/java/app/pocketshell/ui/settings/SettingsScreen.kt",
            "src/main/java/app/pocketshell/ui/settings/SettingsScreen.kt",
        ))
    }
    private val settingsScreenRaw get() = settingsScreenPair.first
    private val settingsScreenCode get() = settingsScreenPair.second

    private val keyboardFilePair: Pair<String, String> by lazy {
        readBoth(listOf(
            "app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt",
            "src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt",
        ))
    }
    private val keyboardFileRaw get() = keyboardFilePair.first
    private val keyboardFileCode get() = keyboardFilePair.second

    /** The detector CLASS body only (the model shares the file, by design). */
    private val detectorCode: String by lazy {
        val start = keyboardFileCode.indexOf("class ExternalKeyboardDetector")
        val end = keyboardFileCode.indexOf("class ExternalKeyboardVisibilityModel")
        assertTrue("detector/model layout changed", start >= 0 && end > start)
        keyboardFileCode.substring(start, end)
    }

    // ---- root wiring -------------------------------------------------------

    @Test
    fun `the root observes the ONE authoritative effective state as the deck visibility`() {
        assertTrue(
            "PocketShellRoot must derive its deck visibility from the model's shouldShow",
            mainCode.contains(
                "externalKeyboardViewModel.shouldShowOnscreenKeyboard.collectAsStateWithLifecycle()",
            ),
        )
        assertTrue(
            "the M7.1 local suppression memory must be gone (the model owns the state)",
            !mainCode.contains("preExternalExpanded"),
        )
        assertTrue(
            "the M7.1 SUPPRESS/RESTORE policy is retired from the root",
            !mainCode.contains("ExternalKeyboardPolicy"),
        )
        assertTrue(
            "the M7.1 local write-back state must be gone (no remembered visibility copy)",
            !mainCode.contains("var keyboardExpanded by rememberSaveable"),
        )
    }

    @Test
    fun `every user-facing keyboard write funnels through the model`() {
        assertTrue(mainCode.contains("val openKeyboardManually"))
        assertTrue(
            "the explicit funnel is requestShow",
            mainCode.contains("externalKeyboardViewModel.requestShow(open)"),
        )
        assertTrue(
            "the terminal canvas tap rides the GATED reopen",
            mainCode.contains("onKeyboardExpandedChange = onCanvasTapReopen") &&
                mainCode.contains("externalKeyboardViewModel.canvasTapReopen()"),
        )
        assertTrue(
            "the deck's collapse toggle rides the explicit funnel",
            mainCode.contains("onToggleExpanded = { openKeyboardManually(false) }"),
        )
        assertTrue(
            "the floating rebirth icon rides the explicit funnel",
            mainCode.contains("openKeyboardManually(true)"),
        )
    }

    @Test
    fun `web-focus auto-open is gated while an external keyboard is connected`() {
        val effect = mainCode.substringAfter("DisposableEffect(Unit) {").substringBefore("onDispose")
        assertTrue(
            "KeyboardInputRouter.onWebFocusGained must not undo the auto-hide",
            effect.contains("if (!externalKeyboardConnected) openKeyboardManually(true)"),
        )
    }

    @Test
    fun `resume rescans the hardware state`() {
        assertTrue(
            "ON_RESUME must re-evaluate input devices",
            mainCode.contains("Lifecycle.Event.ON_RESUME") && mainCode.contains(".rescan()"),
        )
    }

    @Test
    fun `the notice banner handles BOTH directions with dismiss and a Settings affordance`() {
        assertTrue(mainCode.contains("ExternalKeyboardNoticeBar("))
        val block = mainCode.substringAfter("ExternalKeyboardNoticeBar(")
        assertTrue(
            "the notice direction rides the transition",
            block.contains("pendingExternalKeyboardNotice.direction"),
        )
        assertTrue(
            "the disconnect copy must not lie about the deck (preference-aware)",
            block.contains("keyboardAvailable = keyboardExpanded"),
        )
        assertTrue(
            "the notice must be dismissible through the detector",
            block.contains("externalKeyboardViewModel.dismissNotice()"),
        )
        assertTrue(
            "the Settings action must navigate to the settings page",
            mainRaw.contains("screen = \"settings\""),
        )
        assertTrue(
            "the notice auto-dismisses (never permanent)",
            mainCode.contains("EXTERNAL_KEYBOARD_NOTICE_AUTO_DISMISS_MS"),
        )
    }

    // ---- detection glue ------------------------------------------------------

    @Test
    fun `the ViewModel registers a real InputManager listener and unregisters it`() {
        assertTrue(vmCode.contains("registerInputDeviceListener"))
        assertTrue(vmCode.contains("unregisterInputDeviceListener"))
        assertTrue(vmCode.contains("onCleared()"))
        assertTrue(
            "the launch scan runs from the ViewModel init (no unplug/replug)",
            vmCode.contains("detector.start()"),
        )
        assertTrue(
            "all three listener callbacks funnel the detector",
            listOf("onInputDeviceAdded", "onInputDeviceRemoved", "onInputDeviceChanged")
                .all { vmCode.contains(it) },
        )
    }

    @Test
    fun `the ViewModel wires the configuration-change cross-check - the second mechanism`() {
        assertTrue(
            "M7.1.1: the Application-level ComponentCallbacks2 cross-check must be registered",
            vmCode.contains("registerComponentCallbacks") &&
                vmCode.contains("onConfigurationChanged"),
        )
        assertTrue(
            "the cross-check must be unregistered with the listener",
            vmCode.contains("unregisterComponentCallbacks"),
        )
    }

    @Test
    fun `detection is event-driven - no polling loop anywhere`() {
        assertFalse(
            "the detector must not poll on a timer",
            detectorCode.contains("while (true)"),
        )
        assertTrue(
            "the detector works through the stability window",
            detectorCode.contains("DEFAULT_STABILIZE_MS"),
        )
        assertTrue(
            "M7.1.1: the confirm deadline exists (no unbounded deferral)",
            detectorCode.contains("DEFAULT_CONFIRM_DEADLINE_MS"),
        )
        assertTrue(
            "the predicate filters non-alphabetic and virtual devices",
            keyboardFileCode.contains("KEYBOARD_TYPE_ALPHABETIC") &&
                keyboardFileCode.contains("!isVirtual"),
        )
    }

    // ---- settings --------------------------------------------------------------

    @Test
    fun `the setting persists through the existing DataStore repository`() {
        assertTrue(
            "the preference lives in the ONE settings DataStore",
            settingsRaw.contains("onscreen_keyboard_enabled"),
        )
        assertTrue(
            "default ON: only an explicit false disables",
            settingsRaw.contains("prefs[onscreenKeyboardKey] != \"false\""),
        )
        assertTrue(settingsCode.contains("setOnscreenKeyboardEnabled"))
        assertTrue(
            "the M7.1 auto-hide opt-out key is retired from the repository",
            !settingsCode.contains("auto_hide_keyboard_on_external"),
        )
    }

    @Test
    fun `the Settings control is a real wired toggle`() {
        assertTrue(
            "the Settings page carries the On-screen keyboard control",
            settingsScreenCode.contains("onscreenKeyboardEnabled") &&
                settingsScreenCode.contains("MidnightSwitch(") &&
                settingsScreenCode.contains("onOnscreenKeyboardEnabled"),
        )
        assertTrue(
            "the control must carry the spec's wording",
            settingsScreenRaw.contains("On-screen keyboard") &&
                settingsScreenRaw.contains("external keyboard"),
        )
    }

    @Test
    fun `the detector stays visibility-free - the model is the ONE state system`() {
        assertFalse(
            "the detector must not make visibility decisions",
            detectorCode.contains("shouldShow") || detectorCode.contains("Visibility"),
        )
        assertTrue(
            "the authoritative model exists with exactly the spec's three states",
            keyboardFileCode.contains("class ExternalKeyboardVisibilityModel") &&
                keyboardFileRaw.contains("shouldShowOnscreenKeyboard") &&
                keyboardFileRaw.contains("onscreenKeyboardUserEnabled") &&
                keyboardFileRaw.contains("externalKeyboardConnected"),
        )
        assertTrue(
            "the model exposes the manual-request gate for the canvas tap",
            keyboardFileCode.contains("fun canvasTapReopen(): Boolean"),
        )
    }
}
