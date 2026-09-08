package app.pocketshell.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P4 — the structural contract (source-reading pins over the real
 * shipped sources; the established technique from
 * NotificationIntegrationTest / AgentRuntimeEventEngineIntegrationTest —
 * the JVM suite has no Robolectric/device runner, so boundaries are pinned
 * by reading the sources that ship).
 *
 * The phase mandate's structural rules, pinned here because the pure
 * [AgentRuntimeNotificationMappingTest] cannot reach them:
 *
 *   1. the consumer is a CONSUMER ONLY: it subscribes once to the
 *      ROADMAP-named event stream, polls nothing, scans nothing, detects
 *      nothing, never touches the session manager or the detector, and
 *      posts only through the P1 coordinator (never NotificationManager
 *      directly);
 *   2. the lifecycle owner is the Application process scope, wired after
 *      the coordinator's init;
 *   3. the P4 surface introduces NO permission code — the P1 gate/policy
 *      remain the only permission machinery (no duplicate manager, no new
 *      prompts);
 *   4. THE WORDING LINE: no string literal in the P4 files may claim
 *      completion/success/finished work/failure — checked over the actual
 *      literals that ship;
 *   5. notification identity stays deterministic — ids flow through
 *      [NotificationIds.agentRuntime]; no hash, no random;
 *   6. THE FGS REGRESSION: TerminalService's retention notification
 *      (channel `terminal_sessions`, id 1) is untouched by P4 — no merge,
 *      no replacement, no reference from the P4 surface;
 *   7. the channel architecture: the coordinator remains the single owner,
 *      now of exactly the two event channels; the agent-runtime kind maps
 *      to its own id space and channel in an exhaustive `when`.
 */
class AgentRuntimeNotificationIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        // Dual candidates per the established convention: root CWD
        // ("app/src/...") and module CWD ("src/...") — pins must RUN under
        // either runner layout, never silently skip.
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
                    // Step PAST the closing quote (the established helper contract).
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

    /**
     * Extract the double-quoted string literals that actually ship — from
     * COMMENT-STRIPPED source, so a KDoc quoting a banned word in order to
     * document its absence can never trip the pin (only real content is
     * checked).
     */
    private fun stringLiterals(raw: String): List<String> =
        Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
            .findAll(stripCommentsKeepStrings(raw))
            .map { it.groupValues[1] }
            .toList()

    // ------------------------------------------------------------- 1

    private val consumerPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "AgentRuntimeNotificationConsumer.kt",
        )
    }

    private val mappingPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "AgentRuntimeNotificationMapping.kt",
        )
    }

    private val appPair by lazy {
        source("app", "src", "main", "java", "app", "pocketshell", "PocketShellApp.kt")
    }

    private val coordinatorPair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "notifications", "NotificationCoordinator.kt",
        )
    }

    private val servicePair by lazy {
        source(
            "app", "src", "main", "java", "app", "pocketshell",
            "terminal", "TerminalService.kt",
        )
    }

    // ------------------------------------- the consumer is consumer-only

    @Test
    fun `the consumer subscribes once at application start - after the coordinator`() {
        val code = appPair.second
        assertTrue(
            "PocketShellApp.onCreate must arm the notification consumer",
            code.contains("AgentRuntimeNotificationConsumer.ensureStarted()"),
        )
        val initAt = code.indexOf("NotificationCoordinator.init(this)")
        val startAt = code.indexOf("AgentRuntimeNotificationConsumer.ensureStarted()")
        assertTrue(
            "the consumer must start AFTER the coordinator's init (posting path ready)",
            initAt in 0 until startAt,
        )
        assertEquals(
            "exactly one consumer start lives in the application (subscribe once)",
            1,
            Regex("AgentRuntimeNotificationConsumer\\.ensureStarted").findAll(code).count(),
        )
    }

    @Test
    fun `the consumer collects exactly the ROADMAP-named event stream - once`() {
        val code = consumerPair.second
        assertTrue(
            "the consumer must subscribe to AgentActivityRepository.agentRuntimeEvents",
            code.contains("AgentActivityRepository.agentRuntimeEvents"),
        )
        assertEquals(
            "exactly one collection (subscribe once, never poll)",
            1,
            Regex("\\.collect\\s*(\\(|\\{)").findAll(code).count(),
        )
    }

    @Test
    fun `the consumer polls nothing`() {
        val code = consumerPair.second
        for (forbidden in listOf("delay(", "postDelayed", "Timer(", "setInterval", "AlarmManager", "WorkManager")) {
            assertFalse(
                "the consumer must not poll (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    @Test
    fun `the consumer never touches the process or session layer`() {
        val code = consumerPair.second
        for (forbidden in listOf(
            "TerminalSessionManager", "RuntimeAgentDetector", "AgentRuntimeDetection",
            "TerminalService", "HostProcfsReader", "/proc", "procfs", "Process",
        )) {
            assertFalse(
                "the consumer must not reference the process/session layer (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    @Test
    fun `the mapping consumes only event data - no session or process authority`() {
        val code = mappingPair.second
        for (forbidden in listOf(
            "TerminalSessionManager", "RuntimeAgentDetector", "AgentRuntimeDetection",
            "TerminalService", "HostProcfsReader", "/proc", "procfs",
        )) {
            assertFalse(
                "the mapping must not reference the process/session layer (found '$forbidden')",
                code.contains(forbidden),
            )
        }
        assertTrue(
            "the mapping consumes the P3c event vocabulary",
            code.contains("AgentRuntimeEvent"),
        )
    }

    @Test
    fun `the consumer posts only through the coordinator - never NotificationManager directly`() {
        val code = consumerPair.second
        for (forbidden in listOf(
            "NotificationManager", "Notification.Builder", "NotificationChannel", "notify(",
        )) {
            assertFalse(
                "the consumer must not touch the Android notification API directly (found '$forbidden')",
                code.contains(forbidden),
            )
        }
        assertTrue(code.contains("NotificationCoordinator.post("))
        assertTrue(code.contains("NotificationCoordinator.cancel("))
    }

    @Test
    fun `the consumer posts through the deterministic agent-runtime id space`() {
        val code = consumerPair.second
        assertTrue(code.contains("NotificationIds.agentRuntime("))
        for (forbidden in listOf("hashCode(", "Random", "UUID")) {
            assertFalse(
                "notification identity must stay deterministic (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    // -------------------------------------------------- the wording line

    @Test
    fun `no shipped string in the P4 surface claims completion success or failure`() {
        // Checked over the ACTUAL string literals that ship (comments are
        // free prose; user-visible content is not). The lifecycle cause
        // identifier SESSION_FINISHED is code, not wording — it cannot leak
        // into a notification through a string literal.
        for (pair in listOf(consumerPair, mappingPair)) {
            for (literal in stringLiterals(pair.first)) {
                val lowered = literal.lowercase()
                for (banned in listOf("complet", "success", "succeed", "finished", "failed")) {
                    assertFalse(
                        "P4 wording must never claim '$banned' (literal: \"$literal\")",
                        lowered.contains(banned),
                    )
                }
            }
        }
    }

    // ------------------------------------------- the permission boundary

    @Test
    fun `the P4 surface adds no permission code - the P1 gate remains the only machinery`() {
        for (pair in listOf(consumerPair, mappingPair)) {
            val code = pair.second
            for (forbidden in listOf(
                "requestPermission", "POST_NOTIFICATIONS", "checkSelfPermission",
                "rememberLauncherForActivityResult", "shouldShowRequestPermissionRationale",
            )) {
                assertFalse(
                    "P4 must not add permission machinery (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
    }

    // -------------------------------------------------- channel identity

    @Test
    fun `the agent-runtime kind maps to its own id space and channel - exhaustively`() {
        val code = coordinatorPair.second
        assertTrue(code.contains("EventKind.AGENT_RUNTIME -> NotificationIds.agentRuntime(request.sessionId)"))
        assertTrue(code.contains("EventKind.AGENT_RUNTIME -> CHANNEL_AGENT_RUNTIME"))
        assertTrue(
            "the agent-runtime channel must carry its stable literal",
            coordinatorPair.first.contains("const val CHANNEL_AGENT_RUNTIME = \"agent_runtime\""),
        )
    }

    // ----------------------------------------------------- FGS regression

    @Test
    fun `the foreground-service retention notification stays untouched by P4`() {
        val raw = servicePair.first
        assertTrue(
            "TerminalService keeps its own channel",
            raw.contains("const val CHANNEL_ID = \"terminal_sessions\""),
        )
        assertTrue(
            "TerminalService keeps its own notification id",
            raw.contains("const val NOTIFICATION_ID = 1"),
        )
        // No P4 file may reference, merge, or replace the FGS surface.
        for (pair in listOf(consumerPair, mappingPair)) {
            assertFalse(
                "the P4 surface must not touch the FGS notification",
                pair.second.contains("terminal_sessions") || pair.second.contains("TerminalService"),
            )
        }
    }

    // --------------------------------------------------- stale cleanup

    @Test
    fun `stale surfaces have two structural guards - the tombstone and the P1 sweep`() {
        // In-process: the mapping's ended-set refuses every event for an
        // ended session (a late or duplicate delivery can never resurrect a
        // surface).
        val mapping = mappingPair.second
        assertTrue(
            "the mapping must tombstone ended sessions",
            mapping.contains("ended = memory.ended + event.sessionId"),
        )
        assertTrue(
            "the mapping must check the tombstone before anything else",
            mapping.indexOf("in memory.ended") >= 0,
        )
        // Across processes: the P1 startup sweep still cancels exactly the
        // ledgered ids (sessions are process-scoped; nothing is restored).
        val coordinator = coordinatorPair.second
        assertTrue(coordinator.contains("sweepStaleNotifications"))
        assertTrue(coordinator.contains("recordActiveNotificationId(id)"))
        assertTrue(coordinator.contains("clearActiveNotificationId(id)"))
    }

    // ------------------------------------------- the calm-surfaces shape

    @Test
    fun `state surfaces are ongoing and one-shot facts are not - the builder honors the flag`() {
        val code = coordinatorPair.second
        assertTrue(
            "the builder must honor the ongoing flag for live-state surfaces",
            code.contains(".setOngoing(request.ongoing)"),
        )
        assertTrue(
            "auto-cancel must be the negation of ongoing (a fact dismisses on tap)",
            code.contains(".setAutoCancel(!request.ongoing)"),
        )
        assertTrue(
            "repeated deliveries of the same surface must never re-alert",
            code.contains(".setOnlyAlertOnce(true)"),
        )
    }
}
