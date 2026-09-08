package app.pocketshell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P1 — the integration contract (structural pins over the real
 * sources; the same honest technique as ExternalKeyboardIntegrationTest —
 * the JVM suite has no Robolectric/device runner).
 *
 * What is pinned here is the glue the unit tests cannot execute:
 *
 *   1. PocketShellApp initializes the coordinator once, at process start —
 *      the owner of channel creation + the startup stale sweep;
 *   2. MainActivity processes intents on BOTH entry paths (cold start in
 *      onCreate, existing-instance onNewIntent) through ONE handler;
 *   3. the composition root mounts the once-per-install permission gate
 *      keyed on the first session's existence;
 *   4. the gate asks through the unit-tested policy and marks the request
 *      spent BEFORE launching the dialog (no re-ask on recreation);
 *   5. the coordinator stays an output layer — it never references the
 *      session layer (no TerminalSessionManager/TerminalSession), never
 *      scans /proc, and carries no timing heuristics (no delay/timers) —
 *      the no-second-truth-source boundary;
 *   6. TerminalService's foreground-service channel and id 1 remain
 *      untouched (no merge with the event channel);
 *   7. no new permissions were added to the manifest.
 *
 * Real permission dialogs, taps and sweeps on hardware are the
 * docs/TESTING.md §48 gate — never claimed from a JVM run.
 */
class NotificationIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        // M7.2 P2 fix (found while adding the lifecycle suite): this suite
        // previously resolved ONLY from a project-root working directory, so
        // under the standard module-dir runner every pin silently SKIPPED via
        // Assume — pins that never ran could look green in a totals line. The
        // established convention (ExternalKeyboardIntegrationTest et al.) is
        // BOTH candidates: root CWD ("app/src/...") and module CWD ("src/...").
        val joined = relatives.joinToString("/")
        val candidates = if (joined.startsWith("app/")) {
            listOf(File(joined), File(joined.removePrefix("app/")))
        } else {
            listOf(File(joined), File("../$joined"))
        }
        val file = candidates.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "source not found on this runner: $joined",
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

    private fun source(vararg segments: String): Pair<String, String> =
        readBoth(listOf(segments.joinToString("/")))

    // ---------------------------------------------------------------- 1

    private val appPair by lazy {
        source("app", "src", "main", "java", "app", "pocketshell", "PocketShellApp.kt")
    }

    @Test
    fun `PocketShellApp initializes the coordinator at process start`() {
        val code = appPair.second
        assertTrue(
            "PocketShellApp.onCreate must init NotificationCoordinator",
            code.contains("NotificationCoordinator.init(this)"),
        )
    }

    // ---------------------------------------------------------------- 2

    private val mainPair by lazy {
        source("app", "src", "main", "java", "app", "pocketshell", "MainActivity.kt")
    }

    @Test
    fun `MainActivity processes cold-start notification intents in onCreate`() {
        val code = mainPair.second
        assertTrue(
            "onCreate must route the launch intent through handleNotificationIntent",
            code.contains("handleNotificationIntent(intent)"),
        )
    }

    @Test
    fun `MainActivity handles onNewIntent - the singleTask tap path`() {
        val code = mainPair.second
        assertTrue(
            "onNewIntent must be overridden (the P0 audit's unhandled path)",
            code.contains("override fun onNewIntent(intent: Intent)"),
        )
        val onNewBlock = code.substringAfter("override fun onNewIntent")
            .substringBefore("private fun handleNotificationIntent")
        assertTrue(
            "onNewIntent must forward through the same handler",
            onNewBlock.contains("handleNotificationIntent(intent)"),
        )
    }

    @Test
    fun `the routing handler is an exhaustive when over NotificationRoute`() {
        val code = mainPair.second
        val handler = code.substringAfter("private fun handleNotificationIntent")
            .substringBefore("/**")
        assertTrue(handler.contains("NotificationRoute.fromIntent(intent)"))
        assertTrue(handler.contains("is NotificationRoute.OpenApp"))
        assertTrue(handler.contains("null -> {}"))
    }

    // ---------------------------------------------------------------- 3

    @Test
    fun `the composition root mounts the permission gate keyed on the first session`() {
        val code = mainPair.second
        assertTrue(
            "PocketShellRoot must mount NotificationPermissionGate(hasSessions = sessions.isNotEmpty())",
            code.contains("NotificationPermissionGate(hasSessions = sessions.isNotEmpty())"),
        )
    }

    // ---------------------------------------------------------------- 4

    private val gatePair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "NotificationPermissionGate.kt",
        )
    }

    @Test
    fun `the gate decides through the unit-tested policy`() {
        val code = gatePair.second
        assertTrue(
            code.contains("NotificationPermissionPolicy.shouldRequest("),
        )
        assertTrue(
            code.contains("rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())"),
        )
    }

    @Test
    fun `the flag is written BEFORE the dialog launches - no re-ask on recreation`() {
        val code = gatePair.second
        val effect = code.substringAfter("LaunchedEffect(hasSessions, requestedBefore)")
        val markAt = effect.indexOf("preferences.markPermissionRequested()")
        val launchAt = effect.indexOf("requestPermission.launch(")
        assertTrue("gate must mark the request spent", markAt > 0)
        assertTrue("gate must launch the permission request", launchAt > 0)
        assertTrue(
            "markPermissionRequested must precede launch (pessimistic anti-nag)",
            markAt < launchAt,
        )
    }

    // ---------------------------------------------------------------- 5

    private val coordinatorPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "NotificationCoordinator.kt",
        )
    }

    @Test
    fun `the coordinator never references the session layer - no second truth source`() {
        val code = coordinatorPair.second
        assertFalse(
            "coordinator must not reference TerminalSessionManager",
            code.contains("TerminalSessionManager"),
        )
        assertFalse(
            "coordinator must not reference TerminalSession",
            code.contains("TerminalSession("),
        )
        assertFalse(
            "coordinator must not scan /proc",
            code.contains("/proc"),
        )
    }

    @Test
    fun `the coordinator carries no timing heuristics`() {
        val code = coordinatorPair.second
        assertFalse(
            "no delay-based inference",
            code.contains("delay("),
        )
        assertFalse(
            "no postDelayed timers",
            code.contains("postDelayed"),
        )
        assertFalse(
            "no Timer",
            code.contains("Timer("),
        )
    }

    @Test
    fun `the coordinator is the single owner of exactly one event channel`() {
        val raw = coordinatorPair.first
        val code = coordinatorPair.second
        assertTrue(code.contains("CHANNEL_SESSION_EVENTS"))
        assertEquals(
            "exactly one createNotificationChannel call lives in the coordinator",
            1,
            Regex("createNotificationChannel").findAll(code).count(),
        )
        assertTrue(
            "the event channel id must be a stable literal",
            raw.contains("const val CHANNEL_SESSION_EVENTS = \"session_events\""),
        )
    }

    @Test
    fun `posting records the ledger - the sweep clears exactly it`() {
        val code = coordinatorPair.second
        assertTrue(code.contains("recordActiveNotificationId(id)"))
        assertTrue(code.contains("clearActiveNotificationId(id)"))
        assertTrue(code.contains("prefs.activeNotificationIds.first()"))
    }

    @Test
    fun `event posts use the deterministic id space`() {
        val code = coordinatorPair.second
        assertTrue(code.contains("NotificationIds.sessionEvent(request.sessionId)"))
    }

    // ---------------------------------------------------------------- 6

    private val servicePair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "terminal", "TerminalService.kt",
        )
    }

    @Test
    fun `TerminalService keeps its own channel and id - untouched by P1`() {
        val raw = servicePair.first
        assertTrue(raw.contains("const val CHANNEL_ID = \"terminal_sessions\""))
        assertTrue(raw.contains("const val NOTIFICATION_ID = 1"))
    }

    // ---------------------------------------------------------------- 7

    private val manifestPair by lazy {
        source("app", "src", "main", "AndroidManifest.xml")
    }

    @Test
    fun `no new permissions - the manifest declares exactly the inherited set`() {
        // The declared source set (the merged APK carries one extra
        // dependency-contributed permission — re-verified by aapt2 at build
        // time; the source manifest is what P1 must not grow).
        val raw = manifestPair.first
        val permissions = Regex("uses-permission android:name=\"([^\"]+)\"")
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            listOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.POST_NOTIFICATIONS",
            ),
            permissions,
        )
    }
}
