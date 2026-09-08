package app.pocketshell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P5 — the structural contract (source-reading pins over the real
 * shipped sources; the established technique from
 * AgentRuntimeNotificationIntegrationTest — the JVM suite has no
 * Robolectric/device runner, so boundaries are pinned by reading the sources
 * that ship).
 *
 * The phase mandate's structural rules, pinned here because the pure
 * routing tests cannot reach them:
 *
 *   1. the P5 interaction surface is NAVIGATION ONLY: no /proc, no process
 *      scanning, no PID matching, no detector reference, no session-manager
 *      reach-through from the tap path — the consumed id goes through the
 *      ViewModel's select (the existing UI seam) and nothing else;
 *   2. the routing model stays PURE: AgentRuntimeNotificationRouting
 *      imports no terminal-layer type and no Android type — data in,
 *      decision out;
 *   3. the coordinator's content intent stays DETERMINISTIC: the request
 *      code is the notification id, the session route carries the same
 *      authoritative session id, and the kind `when` stays exhaustive;
 *   4. NO permission machinery is added by P5 — the P1 gate/policy remain
 *      the only path, and the manifest keeps the inherited 6-permission set;
 *   5. THE WORDING LINE extends to P5: no string literal in the P5 files
 *      claims completion/success/finished work/failure — the tap routes, it
 *      never re-labels the truth;
 *   6. THE FGS REGRESSION: TerminalService's retention notification
 *      (channel `terminal_sessions`, id 1) is untouched and unreferenced by
 *      the P5 surface — notification interaction never manipulates the
 *      foreground service.
 */
class AgentRuntimeNotificationInteractionIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        val joined = relatives.joinToString("/")
        val candidates = if (joined.startsWith("app/")) {
            listOf(File(joined), File(joined.removePrefix("app/")))
        } else {
            listOf(File(joined), File("../$joined"))
        }
        val file = candidates.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("source not found on this runner: $joined", file != null)
        return file!!.readText()
    }

    /** Strip comments while PRESERVING string contents (for the wording pin). */
    private fun stripCommentsKeepStrings(source: String): String {
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
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
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

    private fun source(vararg segments: String): Pair<String, String> {
        val raw = readSource(listOf(segments.joinToString("/")))
        return raw to stripCommentsAndStrings(raw)
    }

    private fun stringLiterals(raw: String): List<String> =
        Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(stripCommentsKeepStrings(raw))
            .map { it.groupValues[1] }
            .toList()

    private val routePair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "NotificationRoute.kt",
        )
    }

    private val routingPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "AgentRuntimeNotificationRouting.kt",
        )
    }

    private val coordinatorPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "NotificationCoordinator.kt",
        )
    }

    private val mainActivityPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell", "MainActivity.kt",
        )
    }

    private val servicePair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "terminal", "TerminalService.kt",
        )
    }

    private val manifestPair by lazy {
        source("app", "src", "main", "AndroidManifest.xml")
    }

    private val p5Files: List<Pair<String, String>> by lazy {
        listOf(routePair, routingPair, coordinatorPair, mainActivityPair)
    }

    // -------------------------------- 1: the tap path is navigation only

    @Test
    fun `the P5 interaction surface never touches proc or process identity`() {
        for ((_, code) in p5Files) {
            for (forbidden in listOf("/proc", "RuntimeAgentDetector", "procfs", "Process(")) {
                assertFalse(
                    "the tap path must never inspect processes (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
    }

    @Test
    fun `the notification layer never reaches the session manager or spawns anything`() {
        // Scoped to the notification files: MainActivity's pre-existing UI
        // seams (the "+" button's newSessionMatchingCurrent, the tab close
        // callback, Files' open-terminal launch) are NOT the tap path and
        // were never touched by P5 — the freeze audit owns that.
        for ((_, code) in listOf(routePair, routingPair, coordinatorPair)) {
            for (forbidden in
                listOf(
                    "TerminalSessionManager", "closeSession(", "openLinuxShell",
                    "newSessionMatchingCurrent", "spawn",
                )) {
                assertFalse(
                    "notification interaction must not own or spawn sessions (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
        // The Activity must reach sessions ONLY through the ViewModel — the
        // manager object is never referenced directly from the UI file.
        assertFalse(
            "MainActivity must not reference TerminalSessionManager directly",
            mainActivityPair.second.contains("TerminalSessionManager"),
        )
    }

    @Test
    fun `the session-targeted tap is consumed through the ViewModel select seam`() {
        val (_, main) = mainActivityPair
        assertTrue(
            "the consumption must go through the pure routing model",
            main.contains("AgentRuntimeNotificationRouting.resolve"),
        )
        assertTrue(
            "a live target must select the session through the ViewModel (the ONE UI seam)",
            main.contains("terminalViewModel.select("),
        )
        assertFalse(
            "the consumption must be one-shot (the pending target is cleared)",
            !main.contains("onConsumeNotificationSessionTarget()"),
        )
    }

    // ------------------------------------------- 2: the model stays pure

    @Test
    fun `the routing model imports no terminal-layer and no Android type`() {
        val (raw, _) = routingPair
        val imports = Regex("^import\\s+(\\S+)", RegexOption.MULTILINE)
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "the pure routing model must stay import-free (data in, decision out)",
            imports.isEmpty(),
        )
    }

    // ------------------------------- 3: deterministic PendingIntent identity

    @Test
    fun `the content intent request code is the notification id - per-session deterministic`() {
        val (_, coordinator) = coordinatorPair
        assertTrue(
            "PendingIntent request code must be the notification id (no collisions across sessions)",
            Regex("PendingIntent\\.getActivity\\(\\s*context,\\s*notificationId,").containsMatchIn(coordinator),
        )
        assertFalse(
            "no hash/random identity anywhere in the coordinator",
            Regex("hashCode\\(\\)|Random|UUID").containsMatchIn(coordinator),
        )
    }

    @Test
    fun `the kind-to-route mapping stays an exhaustive when over both kinds`() {
        val (_, coordinator) = coordinatorPair
        val whenBlock = coordinator
            .substringAfter("Intent(context, MainActivity::class.java)")
            .substringBefore("PendingIntent.FLAG_IMMUTABLE")
        assertTrue(
            "SESSION_ACTIVITY must map to the P1 open-app route (behavior unchanged)",
            whenBlock.contains("EventKind.SESSION_ACTIVITY ->") &&
                whenBlock.contains("NotificationRoute.ROUTE_OPEN_APP"),
        )
        assertTrue(
            "AGENT_RUNTIME must map to the session-targeted route carrying the session id",
            whenBlock.contains("EventKind.AGENT_RUNTIME ->") &&
                whenBlock.contains("NotificationRoute.ROUTE_OPEN_SESSION") &&
                whenBlock.contains("NotificationRoute.EXTRA_SESSION_ID"),
        )
        assertEquals(
            "the when must stay exhaustive (exactly two kinds)",
            2,
            Regex("EventKind\\.(SESSION_ACTIVITY|AGENT_RUNTIME) ->").findAll(whenBlock).count(),
        )
    }

    // ------------------------------------------- 4: permission freeze (Part N)

    @Test
    fun `P5 adds no permission machinery`() {
        for ((name, pair) in listOf(
            "NotificationRoute" to routePair,
            "AgentRuntimeNotificationRouting" to routingPair,
            "NotificationCoordinator" to coordinatorPair,
            "MainActivity" to mainActivityPair,
        )) {
            val (_, code) = pair
            for (forbidden in
                listOf(
                    "requestPermission", "POST_NOTIFICATIONS", "shouldShowRequestPermissionRationale",
                    "registerForActivityResult",
                )) {
                assertFalse(
                    "$name must not add permission machinery (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
    }

    @Test
    fun `the manifest keeps the inherited permission set - zero P5 delta`() {
        val (manifest, _) = manifestPair
        val declared = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        // The SOURCE manifest declares exactly these five (the merged APK's
        // sixth — DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION — is added by the
        // manifest merger at build time and is verified on the built APK in
        // the Part-Q audit). P5 adds none and removes none.
        assertEquals(
            "the permission set is frozen (P5 is navigation only)",
            listOf(
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                "android.permission.INTERNET",
                "android.permission.POST_NOTIFICATIONS",
            ),
            declared.sorted(),
        )
    }

    // --------------------------------------------- 5: the wording line (Part H)

    @Test
    fun `no P5 string claims completion, success, finished work or failure`() {
        val banned = listOf(
            "completed", "completion", "success", "succeeded", "successful",
            "finished", "failed", "failure", "task done", "work done",
            "waiting for input", "needs attention",
        )
        for ((name, pair) in listOf(
            "NotificationRoute" to routePair,
            "AgentRuntimeNotificationRouting" to routingPair,
            "NotificationCoordinator" to coordinatorPair,
            "MainActivity" to mainActivityPair,
        )) {
            val literals = stringLiterals(pair.first)
            for (literal in literals) {
                val lower = literal.lowercase()
                for (word in banned) {
                    assertFalse(
                        "$name ships a string claiming '$word': \"$literal\"",
                        lower.contains(word),
                    )
                }
            }
        }
    }

    // ------------------------------------------- 6: the FGS freeze (Part M)

    @Test
    fun `the FGS retention notification is untouched and unreferenced by the tap path`() {
        val raw = servicePair.first
        // TerminalService still owns its own constants (unchanged baseline,
        // checked over the RAW source — the constant is a string literal).
        assertTrue(
            "TerminalService's retention channel constant must stay",
            raw.contains("const val CHANNEL_ID = \"terminal_sessions\""),
        )
        for ((_, code) in p5Files) {
            assertFalse(
                "the P5 interaction surface must never touch the foreground-service notification",
                code.contains("terminal_sessions") || code.contains("startForeground"),
            )
        }
    }

    @Test
    fun `the coordinator's channel set stays exactly the two event channels plus the FGS ownership line`() {
        val (_, coordinator) = coordinatorPair
        assertTrue(coordinator.contains("CHANNEL_SESSION_EVENTS"))
        assertTrue(coordinator.contains("CHANNEL_AGENT_RUNTIME"))
        assertEquals(
            "no third channel may appear without a genuinely new event class",
            2,
            Regex("\\bNotificationChannel\\(").findAll(coordinator).count(),
        )
    }
}
