package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P8 — the HOME INTEGRATION structural contract (source-reading pins
 * over the real shipped sources; the established technique from
 * AgentRuntimeDetectionIntegrationTest / AgentRuntimeWaitingEvidenceBoundary
 * Test — the JVM suite has no Robolectric/device runner, so boundaries are
 * pinned by reading the sources that ship).
 *
 * P8 is a CONSUMER/UI integration phase (the mandate's PART O). What is
 * pinned here is that the Home Sessions activity display can never become
 * another detector, another lifecycle system, or an interactive writer:
 *
 *   1. the pure claim step is DECISION-ONLY: no /proc, no process matching,
 *      no PID discovery, no inactivity timers, no terminal-output parsing,
 *      no regex detection, no polling loops, no notifications, no
 *      persistence;
 *   2. the repository projection consumes EXACTLY the two existing
 *      authorities (the manager's session StateFlow + the detector's
 *      observation StateFlow) through the pure step — no new scan, no new
 *      source of truth, no own mutable state (the P3b pin re-verified with
 *      the P8 projection present);
 *   3. Home collects the projection LIFECYCLE-AWARE and renders it — no
 *      polling loops, no handlers, no direct detector access;
 *   4. HOME IS AN OBSERVER: the Sessions section gains a status line, not a
 *      write path — no PTY write/paste anywhere in HomeScreen, and the
 *      claim rendering adds NO click target (the row's existing
 *      onOpenSession tap stays the only interaction, PART H);
 *   5. NO SECOND NAVIGATION and NO SECOND LIFECYCLE OWNER: the ViewModel
 *      seam is a read-only pass-through of the repository flow;
 *   6. WORDING HONESTY (the M7.2 truth boundary, carried into Home): the
 *      claim literals are exactly " — Running" / " — Runtime unknown"; no
 *      completion/success/failure/waiting wording can ship from the
 *      Sessions section's literals, and "(exited)" stays the session-level
 *      end state it always was.
 */
class AgentHomeSessionIntegrationBoundaryTest {

    // ------------------------------------------------------------ helpers

    private fun readSource(vararg segments: String): String {
        val joined = segments.joinToString("/")
        val candidates = if (joined.startsWith("app/")) {
            listOf(File(joined), File(joined.removePrefix("app/")))
        } else {
            listOf(File(joined), File("../$joined"))
        }
        val file = candidates.firstOrNull { it.isFile }
        assumeTrue("source not found on this runner: $joined", file != null)
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

    /** Comments stripped, string literal CONTENTS kept — the shipped-words view. */
    private fun stripComments(source: String): String {
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

    private fun stringLiterals(source: String): List<String> {
        val cleaned = stripComments(source)
        val literals = mutableListOf<String>()
        var i = 0
        while (i < cleaned.length) {
            if (cleaned[i] == '"') {
                val start = i + 1
                var j = start
                while (j < cleaned.length && cleaned[j] != '"') {
                    if (cleaned[j] == '\\') j++
                    j++
                }
                literals += cleaned.substring(start, j.coerceAtMost(cleaned.length))
                i = j + 1
            } else {
                i++
            }
        }
        return literals
    }

    /**
     * The source region of one declaration. Brace-matched when the
     * declaration owns a body; otherwise sliced to the first end marker
     * (for brace-less property pass-throughs).
     */
    private fun region(source: String, startMarker: String, endMarkers: List<String>): String {
        val start = source.indexOf(startMarker)
        if (start < 0) return ""
        val bodyStart = source.indexOf('{', start)
        if (bodyStart in start until start + 400) {
            var depth = 0
            var i = bodyStart
            while (i < source.length) {
                when (source[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return source.substring(start, i + 1)
                    }
                }
                i++
            }
            return source.substring(start)
        }
        // Brace-less declaration: slice to the earliest end marker.
        val ends = endMarkers.mapNotNull { marker ->
            source.indexOf(marker, start).takeIf { it > start }
        }
        val end = ends.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private val claimsPath = arrayOf(
        "app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentHomeSessionClaims.kt",
    )
    private val repositoryPath = arrayOf(
        "app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentActivityRepository.kt",
    )
    private val homeScreenPath = arrayOf(
        "app", "src", "main", "java", "app", "pocketshell", "ui", "home", "HomeScreen.kt",
    )
    private val viewModelPath = arrayOf(
        "app", "src", "main", "java", "app", "pocketshell", "TerminalViewModel.kt",
    )

    // --------------------------------- 1. the pure claim step is decision-only

    @Test
    fun `the claim step is decision-only - no detection machinery of any kind`() {
        val code = stripCommentsAndStrings(readSource(*claimsPath))
        val banned = listOf(
            "/proc",
            "ProcfsProcess",
            "HostProcfsReader",
            "ProcfsSnapshot",
            "AgentProcessMatcher",
            "commandToken",
            "shellPid",
            "getPid",
            "AgentDescendantCorrelator",
            "FileObserver",
            "ContentObserver",
            "File(",
            "DataStore",
            "SharedPreferences",
            "TerminalBuffer",
            "getTranscriptText",
            "getSelectedText",
            "Regex(",
            "toRegex",
            "delay(",
            "Thread.sleep",
            "System.currentTimeMillis",
            "Handler(",
            "postDelayed",
            "MutableStateFlow",
            "MutableSharedFlow",
            "NotificationManager",
            "NotificationCompat",
            "createSession",
            "closeSession",
        )
        val found = banned.filter { code.contains(it) }
        assertTrue(
            "the pure claim step must stay decision-only; found: $found",
            found.isEmpty(),
        )
    }

    // --------------------- 2. the repository projection consumes only the two authorities

    @Test
    fun `the home projection consumes exactly the two existing authorities - no new source`() {
        val code = stripCommentsAndStrings(readSource(*repositoryPath))
        val region = region(code, "val homeSessionClaims", emptyList())
        assertTrue("the P8 projection must exist in the repository", region.isNotEmpty())
        assertTrue(
            "the projection must read the manager's authoritative session list",
            region.contains("TerminalSessionManager.sessions"),
        )
        assertTrue(
            "the projection must read the detector's observation flow",
            region.contains("RuntimeAgentDetector.observations"),
        )
        assertTrue(
            "the decision must go through the pure claim step",
            region.contains("AgentHomeSessionClaims.present"),
        )
        assertTrue(
            "identities must be classified by the P3a resolver",
            region.contains("LaunchIdentity.of("),
        )
        val bannedInRegion = listOf(
            "HostProcfsReader",
            "ProcfsSnapshot",
            "AgentProcessMatcher",
            "eligibleSessions",
            "File(",
            "DataStore",
            "scanOnce",
            "delay(",
        )
        val found = bannedInRegion.filter { region.contains(it) }
        assertTrue(
            "the P8 projection must not scan, read files or poll; found: $found",
            found.isEmpty(),
        )
        // The P3b pins still hold with the P8 projection present.
        assertFalse(
            "the repository must keep no mutable state (stores nothing)",
            code.contains("MutableStateFlow") || code.contains("MutableSharedFlow"),
        )
    }

    // ------------------------------- 3+4. Home is a lifecycle-aware observer

    @Test
    fun `Home collects the claims lifecycle-aware and never polls`() {
        val raw = readSource(*homeScreenPath)
        val code = stripCommentsAndStrings(raw)
        assertTrue(
            "the claims must be consumed through the ViewModel seam",
            code.contains("terminalViewModel.homeSessionClaims"),
        )
        assertTrue(
            "the collection must be lifecycle-aware",
            code.contains("collectAsStateWithLifecycle"),
        )
        val polling = listOf(
            "Handler(",
            "postDelayed",
            "Thread(",
            "Thread.sleep",
            "delay(",
            "Timer(",
            "fixedRateTimer",
            "RuntimeAgentDetector",
            "AgentProcessMatcher",
            "/proc",
            "HostProcfsReader",
            "while (true)",
        )
        val found = polling.filter { code.contains(it) }
        assertTrue(
            "Home must never poll or touch the detector directly; found: $found",
            found.isEmpty(),
        )
    }

    @Test
    fun `Home gains a status line - not a write path and not a second interaction`() {
        val raw = readSource(*homeScreenPath)
        val code = stripCommentsAndStrings(raw)
        val sessions = region(code, "private fun SessionsSection", emptyList())
        assertTrue("the Sessions section must exist", sessions.isNotEmpty())

        // NO PTY write path anywhere in Home (HomeScreen has never had one;
        // P8 must not add one): Home is an observer.
        val writeTokens = listOf(".write(", "writeCodePoint", "paste(", "sendTextToTerminal")
        val writeFound = writeTokens.filter { code.contains(it) }
        assertTrue(
            "Home must never write to the terminal; found: $writeFound",
            writeFound.isEmpty(),
        )

        // The claim rendering adds no click target: the row's existing tap is
        // the ONLY interaction in the section, and it is the existing
        // onOpenSession seam (PART H — no second navigation mechanism).
        assertEquals(
            "the Sessions section must keep exactly one clickable (the row)",
            1,
            Regex("\\.clickable\\(").findAll(sessions).count(),
        )
        assertEquals(
            "the row tap must remain the existing session-selection seam",
            1,
            Regex("onOpenSession\\(").findAll(sessions).count(),
        )
    }

    // --------------------------------------------- 5. no second truth owner

    @Test
    fun `the ViewModel seam is a read-only pass-through - no second source of truth`() {
        val code = stripCommentsAndStrings(readSource(*viewModelPath))
        assertTrue(
            "the ViewModel must expose the repository's projection",
            code.contains("AgentActivityRepository.homeSessionClaims"),
        )
        // The seam must not transform the projection into a new state holder.
        // Extracted from the RAW source: the brace-less property has no body
        // to match, so the region is bounded by the next declaration's doc
        // comment (which stripping would remove).
        val rawViewModel = readSource(*viewModelPath)
        val seam = region(rawViewModel, "val homeSessionClaims", listOf("\n    /**"))
        val banned = listOf("MutableStateFlow", "stateIn", "map {", "onEach", "File(")
        val found = banned.filter { seam.contains(it) }
        assertTrue(
            "the seam must stay a pass-through; found: $found",
            found.isEmpty(),
        )
    }

    // ------------------------------------------- 6. the wording truth boundary

    @Test
    fun `the claim wording names exactly the two proven states - never completion or waiting`() {
        val raw = readSource(*homeScreenPath)
        val literals = stringLiterals(raw)

        // The two claim literals exist and name the states the shade names.
        assertTrue(
            "the running claim literal must exist (wording, not color-only)",
            literals.any { it.endsWith("— Running") },
        )
        assertTrue(
            "the unknown claim literal must exist (wording, not color-only)",
            literals.any { it.endsWith("— Runtime unknown") },
        )

        // The M7.2 honesty ban list, over every literal the Sessions section
        // can ship: no completion/success/failure/waiting claim may ride the
        // agent activity display. "(exited)" remains the pre-existing
        // session-level end state — deliberately absent from the ban list.
        val banned = listOf(
            "completed",
            "completion",
            "success",
            "succeed",
            "finish",
            "failed",
            "failure",
            "waiting for input",
            "needs input",
            "needs attention",
        )
        val sessionsRaw = region(raw, "private fun SessionsSection", emptyList())
        val sessionsLiterals = stringLiterals(sessionsRaw)
        val violations = sessionsLiterals.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue(
            "honesty sweep over the Sessions section literals; violations: $violations",
            violations.isEmpty(),
        )
    }
}
