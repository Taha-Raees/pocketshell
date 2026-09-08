package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P3c — the event-engine structural contract (source-reading pins over
 * the real shipped sources; the established technique from
 * AgentRuntimeDetectionIntegrationTest — the JVM suite has no Robolectric/
 * device runner, so boundaries are pinned by reading the sources that ship).
 *
 * The phase mandate's structural rules (PART K), pinned here because the
 * pure [AgentRuntimeEventsTest] cannot reach them:
 *
 *   1. NO notifications: the P3c surface posts no notification, creates no
 *      channel, requests no permission (the P1 foundation stays untouched);
 *   2. NO completion vocabulary: the event model adds no
 *      completed/success/finished-success certainty — SessionEnded is the
 *      SESSION's fact (the direct child's waitpid status), never the
 *      agent's completion;
 *   3. the event vocabulary is EXACTLY the five mandated kinds;
 *   4. NO output heuristics: no OSC 133, no terminal-output parsing;
 *   5. /proc stays confined to the P3b HostProcfsReader seam — the event
 *      layer consumes detector results and never scans anything;
 *   6. OBSERVER-ONLY: the engine reads the manager's and the detector's
 *      StateFlows and writes neither; the detector knows nothing of the
 *      event layer (no reverse coupling, no detection redesign);
 *   7. NO own polling: the engine arms no timer — the 2-second tick
 *      remains the detector's, the engine only reacts;
 *   8. the stream is replay-free (Part H: late collectors never re-see old
 *      transitions) with a named buffer constant and logged drops;
 *   9. the start discipline: exactly the two observer wakes in the
 *      manager's spawn path, the engine idempotent;
 *  10. the repository re-exposure still stores nothing;
 *  11. identity over display strings: events carry the typed KnownAgent
 *      identity, not a lookup-by-name;
 *  12. the pure model stays pure: no Android, no coroutines in
 *      AgentRuntimeEvents.kt.
 */
class AgentRuntimeEventEngineIntegrationTest {

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

    private fun source(vararg segments: String): Pair<String, String> {
        val raw = readSource(segments.toList())
        return raw to stripCommentsAndStrings(raw)
    }

    private fun source(segments: List<String>): Pair<String, String> =
        source(*segments.toTypedArray())

    private val eventsPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentRuntimeEvents.kt")
    private val enginePath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentRuntimeEventEngine.kt")
    private val repositoryPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentActivityRepository.kt")
    private val managerPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "TerminalSessionManager.kt")
    private val detectorPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "RuntimeAgentDetector.kt")
    private val detectionPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentRuntimeDetection.kt")

    private val p3cSurface = listOf(eventsPath, enginePath, repositoryPath)

    // ------------------------------------------------- 1. no notifications

    @Test
    fun `no notification APIs anywhere in the P3c surface`() {
        for (path in p3cSurface) {
            val (raw, code) = source(path)
            for (forbidden in listOf(
                "NotificationManager", "NotificationCompat", "notify(", "NotificationChannel",
                "POST_NOTIFICATIONS", "requestPermission",
            )) {
                assertFalse(
                    "${path.last()} must not touch notification APIs (found '$forbidden')",
                    code.contains(forbidden) || raw.contains(forbidden),
                )
            }
            assertFalse(
                "${path.last()} must not import the notifications package (P1 boundary)",
                code.contains("import app.pocketshell.notifications"),
            )
        }
    }

    // ---------------------------------------------- 2. no completion claims

    @Test
    fun `the P3c surface adds no agent-completion vocabulary`() {
        for (path in p3cSurface) {
            val code = source(path).second
            for (forbidden in listOf(
                "agentCompleted", "isAgentComplete", "isAgentFinished",
                "AgentCompleted", "CompletionState", "onAgentFinished",
            )) {
                assertFalse(
                    "${path.last()} must never conclude agent completion (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
    }

    @Test
    fun `the event vocabulary declares exactly the five mandated kinds`() {
        val raw = source(eventsPath).first
        val start = raw.indexOf("sealed class AgentRuntimeEvent")
        val end = raw.indexOf("\n}", start)
        org.junit.Assume.assumeTrue("AgentRuntimeEvent sealed class not found", start >= 0 && end > start)
        val body = stripCommentsAndStrings(raw.substring(start, end))
        for (required in listOf(
            "Launched", "ConfirmedRunning", "NoLongerDetected", "RuntimeUnknown", "SessionEnded",
        )) {
            assertTrue("the event vocabulary must declare $required", body.contains(required))
        }
        for (forbidden in listOf("Completed", "Succeeded", "FinishedSuccessfully", "WaitingForInput", "Idle")) {
            assertFalse(
                "the event vocabulary must add no completion/waiting certainty (found '$forbidden')",
                body.contains(forbidden),
            )
        }
    }

    @Test
    fun `SessionEnded carries the SESSION's exit status - never an agent completion`() {
        val code = source(eventsPath).second
        assertTrue(
            "SessionEnded must model the two terminal causes",
            code.contains("SESSION_FINISHED") && code.contains("SESSION_REMOVED"),
        )
        // The status field is the session's (direct child's) — the field is
        // named sessionExitStatus, and no agent-exit synonym exists.
        assertTrue(code.contains("val sessionExitStatus: ExitStatus?"))
        assertFalse(code.contains("agentExitStatus"))
        assertFalse(code.contains("agentCompletion"))
    }

    // ------------------------------------------------- 3. no output heuristics

    @Test
    fun `no output heuristics in the P3c surface - no OSC 133, no terminal parsing`() {
        for (path in listOf(eventsPath, enginePath)) {
            val (raw, code) = source(path)
            assertFalse(
                "${path.last()} must not reference OSC 133",
                code.contains("OSC") || raw.contains("133"),
            )
            for (forbidden in listOf("TerminalEmulator", "emulator", "onTextChanged", "onScreenUpdate", "transcript")) {
                assertFalse(
                    "${path.last()} must not parse terminal output (found '$forbidden')",
                    code.contains(forbidden),
                )
            }
        }
    }

    // -------------------------------------------------- 4. /proc confinement

    @Test
    fun `the event layer never scans - proc access stays confined to the P3b reader`() {
        for (path in p3cSurface) {
            val code = source(path).second
            assertFalse(
                "${path.last()} must not touch /proc (it consumes P3b results)",
                code.contains("/proc"),
            )
            assertFalse(
                "${path.last()} must not do file I/O (it consumes P3b results)",
                code.contains("java.io"),
            )
        }
        // The one /proc reader in the app remains the P3b seam, untouched.
        val detector = source(detectorPath)
        assertTrue(
            "HostProcfsReader must remain the only procfs reader",
            detector.first.contains("\"/proc\"") && detector.second.contains("class HostProcfsReader"),
        )
    }

    // --------------------------------------------------- 5. observer-only

    @Test
    fun `the engine reads both authorities and mutates neither`() {
        val code = source(enginePath).second
        assertTrue(
            "the engine must consume the manager's authoritative session state",
            code.contains("TerminalSessionManager.sessions"),
        )
        assertTrue(
            "the engine must consume the detector's published observations",
            code.contains("RuntimeAgentDetector.observations"),
        )
        for (forbidden in listOf(
            "_sessions.update", "_sessions.value =",
            "closeSession(", "markStarted(", "markFinished(",
            "_observations", "installReaderForTesting",
        )) {
            assertFalse(
                "the engine must never write upstream state (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    @Test
    fun `the detection layer knows nothing of the event layer - no reverse coupling`() {
        for (path in listOf(detectionPath, detectorPath)) {
            val (raw, code) = source(path)
            for (forbidden in listOf("AgentRuntimeEvent", "AgentRuntimeTransitions", "AgentRuntimeEventEngine")) {
                assertFalse(
                    "${path.last()} must not reference the P3c event layer (found '$forbidden')",
                    code.contains(forbidden) || raw.contains(forbidden),
                )
            }
        }
    }

    // ------------------------------------------------------ 6. no own polling

    @Test
    fun `the engine arms no timer - the only tick remains the detector's`() {
        val code = source(enginePath).second
        for (forbidden in listOf(
            "delay(", "postDelayed(", "Timer(", "setExact", "AlarmManager", "WakeLock", "WorkManager",
            "SCAN_INTERVAL",
        )) {
            assertFalse(
                "the event engine must not poll (found '$forbidden') — it reacts to upstream emissions only",
                code.contains(forbidden),
            )
        }
        // …while the detector keeps its named interval (P3b untouched).
        assertTrue(
            "the detector's scan interval must remain in place",
            source(detectorPath).second.contains("const val SCAN_INTERVAL_MS"),
        )
    }

    // --------------------------------------------------- 7. stream semantics

    @Test
    fun `the event stream is replay-free with a named buffer and logged drops`() {
        val (raw, code) = source(enginePath)
        assertTrue(
            "the stream must carry NO replay (Part H: late collectors never re-fire)",
            code.contains("replay = 0"),
        )
        assertTrue(
            "the buffer capacity must be a named constant",
            code.contains("const val EVENT_BUFFER_CAPACITY"),
        )
        assertTrue(
            "emission must go through tryEmit with a logged drop path",
            code.contains("tryEmit") && code.contains("Log.w"),
        )
        // The repository re-exposure inherits the policy — no second flow.
        assertTrue(
            "the repository must re-expose the engine's stream",
            source(repositoryPath).second.contains("AgentRuntimeEventEngine.events"),
        )
    }

    // --------------------------------------------------- 8. start discipline

    @Test
    fun `the manager wakes exactly the two observers from its spawn path`() {
        val code = source(managerPath).second
        assertEquals(
            "the detector wake must appear exactly once",
            1,
            Regex("RuntimeAgentDetector\\.ensureStarted\\(\\)").findAll(code).count(),
        )
        assertEquals(
            "the event-engine wake must appear exactly once",
            1,
            Regex("AgentRuntimeEventEngine\\.ensureStarted\\(\\)").findAll(code).count(),
        )
        val engine = source(enginePath).second
        assertTrue(
            "ensureStarted must be idempotent",
            engine.contains("compareAndSet"),
        )
    }

    // ------------------------------------------- 9. repository stays pure

    @Test
    fun `the repository re-exposure still stores nothing and decides nothing`() {
        val code = source(repositoryPath).second
        assertFalse(
            "the repository must keep no mutable state (stores nothing)",
            code.contains("MutableStateFlow") || code.contains("MutableSharedFlow"),
        )
        assertTrue(
            "the event projection must be declared on the repository",
            code.contains("val agentRuntimeEvents: SharedFlow<AgentRuntimeEvent>"),
        )
    }

    // ---------------------------------------- 10. identity over display strings

    @Test
    fun `events carry the typed KnownAgent identity - never a lookup by display name`() {
        val code = source(eventsPath).second
        assertTrue(
            "every event must carry the P3a identity snapshot",
            code.contains("abstract val agent: LaunchIdentity.KnownAgent"),
        )
        assertTrue(
            "the stable launcher id must ride the identity",
            code.contains("abstract val sessionId: Long"),
        )
        assertFalse(
            "events must not be built from display strings alone",
            code.contains("data class Launched(val displayName: String"),
        )
    }

    // --------------------------------------------- 11. the pure model stays pure

    @Test
    fun `the pure event model imports no Android and no coroutines`() {
        val raw = source(eventsPath).first
        assertFalse("AgentRuntimeEvents.kt must not import Android", raw.contains("import android"))
        assertFalse("AgentRuntimeEvents.kt must not import coroutines", raw.contains("import kotlinx"))
        assertFalse("AgentRuntimeEvents.kt must not do I/O", raw.contains("import java."))
    }
}
