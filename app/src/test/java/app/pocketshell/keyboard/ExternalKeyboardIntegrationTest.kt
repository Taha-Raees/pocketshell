package app.pocketshell.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.1 P3 — the integration contract (structural pins over the real
 * sources; the same honest technique as HomeLauncherRowsTest — the JVM
 * suite has no Robolectric/device runner).
 *
 * What is pinned here is the glue the unit tests cannot execute:
 *
 *   1. the ROOT wires the policy effect + suppression memory + the manual
 *      funnel (every user-facing keyboard write goes through it), and the
 *      web-focus auto-open is gated while suppression is active;
 *   2. the ViewModel registers/unregisters the InputManager listener and
 *      runs the launch scan (event-driven, no polling);
 *   3. ON_RESUME rescans — the backgrounded-connect rule;
 *   4. the notice banner is mounted at the root with dismiss + Settings;
 *   5. the Settings control exists, persists via the ONE DataStore
 *      repository, and defaults ON;
 *   6. no competing keyboard-state system appeared (detector/ViewModel
 *      stay visibility-free — the root owns the deck).
 *
 * Real attach/detach behavior on hardware is the docs/TESTING.md §45 gate —
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

    private val detectorCode: String by lazy {
        stripCommentsAndStrings(
            readSource(listOf(
                "app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt",
                "src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt",
            )),
        )
    }

    // ---- root wiring -----------------------------------------------------

    @Test
    fun `the root applies the policy on connected and auto changes`() {
        assertTrue(
            "PocketShellRoot must key the decision on (connected, auto)",
            mainCode.contains(
                "LaunchedEffect(externalKeyboardConnected, autoHideKeyboardOnExternal)",
            ),
        )
        assertTrue(
            "the decision must go through ExternalKeyboardPolicy.resolve",
            mainCode.contains("ExternalKeyboardPolicy.resolve("),
        )
        assertTrue(
            "the suppression memory must survive process death like the deck state",
            mainCode.contains("var preExternalExpanded by rememberSaveable"),
        )
    }

    @Test
    fun `every user-facing keyboard write funnels through the manual override`() {
        assertTrue(mainCode.contains("val openKeyboardManually"))
        assertTrue(
            "terminal canvas tap / deck callback rides the funnel",
            mainCode.contains("onKeyboardExpandedChange = openKeyboardManually"),
        )
        assertTrue(
            "the deck's collapse toggle rides the funnel",
            mainCode.contains("onToggleExpanded = { openKeyboardManually(false) }"),
        )
        assertTrue(
            "the floating rebirth icon rides the funnel",
            mainCode.contains("openKeyboardManually(true)"),
        )
    }

    @Test
    fun `web-focus auto-open is gated while suppression is active`() {
        val effect = mainCode.substringAfter("DisposableEffect(Unit) {").substringBefore("onDispose")
        assertTrue(
            "KeyboardInputRouter.onWebFocusGained must not undo the auto-hide",
            effect.contains("if (preExternalExpanded == null) keyboardExpanded = true"),
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
    fun `the notice banner is mounted with dismiss and a Settings affordance`() {
        assertTrue(mainCode.contains("ExternalKeyboardNoticeBar("))
        val block = mainCode.substringAfter("ExternalKeyboardNoticeBar(")
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

    // ---- detection glue ----------------------------------------------------

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
            "the predicate filters non-alphabetic and virtual devices",
            detectorCode.contains("KEYBOARD_TYPE_ALPHABETIC") &&
                detectorCode.contains("!isVirtual"),
        )
    }

    // ---- settings ----------------------------------------------------------

    @Test
    fun `the setting persists through the existing DataStore repository`() {
        assertTrue(
            "the preference lives in the ONE settings DataStore",
            settingsRaw.contains("auto_hide_keyboard_on_external"),
        )
        assertTrue(
            "default ON: only an explicit false disables",
            settingsRaw.contains("prefs[autoHideKeyboardKey] != \"false\""),
        )
        assertTrue(settingsCode.contains("setAutoHideKeyboardOnExternal"))
    }

    @Test
    fun `the Settings control is a real wired toggle`() {
        assertTrue(
            "the Settings page carries the On-screen keyboard control",
            settingsScreenCode.contains("autoHideKeyboardOnExternal") &&
                settingsScreenCode.contains("MidnightSwitch(") &&
                settingsScreenCode.contains("onAutoHideKeyboardOnExternal"),
        )
        assertTrue(
            "the control must carry the spec's wording",
            settingsScreenRaw.contains("On-screen keyboard") &&
                settingsScreenRaw.contains("external keyboard"),
        )
    }

    @Test
    fun `no competing keyboard state system was introduced`() {
        assertFalse(
            "the detector must stay visibility-free (the root owns the deck)",
            detectorCode.contains("keyboardExpanded"),
        )
        assertFalse(
            "the ViewModel must not mutate visibility either",
            vmCode.contains("keyboardExpanded"),
        )
    }
}
