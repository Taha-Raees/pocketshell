package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P2 — the lifecycle-engine integration contract (structural pins over
 * the real sources; the same honest technique as NotificationIntegrationTest
 * / ExternalKeyboardIntegrationTest — the JVM suite has no Robolectric/device
 * runner, and the manager needs a real PTY to execute).
 *
 * What is pinned here is the glue and the boundaries the pure unit tests
 * cannot reach:
 *
 *   1. SessionEntry stores the TYPED lifecycle state (no stored boolean —
 *      `isFinished` is a derived getter, no second truth inside the entry);
 *   2. spawn() starts every entry in STARTING (the honest pre-fork state);
 *   3. the real fork signal is wired end-to-end (client setTerminalShellPid
 *      → manager markStarted) — the lazy fork is observed, never polled;
 *   4. the real waitpid status is read through the machine, with the
 *      still-running guard preventing invented exit information;
 *   5. closeSession never issues kill(0) (the mShellPid==0 hazard) and
 *      emits Removed with the full identity block;
 *   6. every unexpected delivery is logged — missing sessions, rejected
 *      transitions, dropped events (nothing silently swallowed);
 *   7. exactly one emission per transition kind, only at mutation sites;
 *   8. no polling, no timers, no /proc scanning, no DataStore in the
 *      lifecycle engine (in-memory by design; no persistence added);
 *   9. all five spawn sites carry their structured SpawnOrigin and the
 *      named launchers carry AgentHints (TerminalViewModel);
 *  10. AgentActivityRepository is a pure projection — it stores nothing;
 *  11. the terminal layer does not touch the notifications package and
 *      TerminalService's FGS channel/id remain untouched (P1 boundary).
 *
 * Real process exits, tab closes and app restarts on hardware are the
 * docs/TESTING.md §49 gate — never claimed from a JVM run.
 */
class SessionLifecycleIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        // Dual candidates per the established convention
        // (ExternalKeyboardIntegrationTest): root CWD ("app/src/...") and
        // module CWD ("src/...") — pins must RUN under either runner layout,
        // never silently skip (the P1-path lesson recorded in the P2 fix).
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

    private val managerPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "TerminalSessionManager.kt")
    private val clientPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "PocketShellSessionClient.kt")
    private val viewModelPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "TerminalViewModel.kt")
    private val repositoryPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentActivityRepository.kt")
    private val lifecyclePath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "SessionLifecycle.kt")
    private val servicePath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "TerminalService.kt")

    // ---------------------------------------------------------------- 1 + 2

    @Test
    fun `the entry stores the typed lifecycle state and derives isFinished`() {
        val code = source(*managerPath.toTypedArray()).second
        assertTrue(
            "SessionEntry must carry the typed lifecycleState",
            code.contains("val lifecycleState: SessionLifecycleState"),
        )
        assertTrue(
            "isFinished must be a DERIVED getter, never a stored second boolean",
            code.contains("val isFinished: Boolean get()"),
        )
        assertFalse(
            "SessionEntry must not store a plain isFinished boolean field",
            code.contains("val isFinished: Boolean,"),
        )
        assertTrue(
            "SessionEntry must carry the structured origin",
            code.contains("val origin: SpawnOrigin"),
        )
        assertTrue(
            "SessionEntry must carry the optional agent hint",
            code.contains("val agent: AgentHint?"),
        )
    }

    @Test
    fun `spawn starts every entry in STARTING`() {
        val code = source(*managerPath.toTypedArray()).second
        assertTrue(
            "spawn() must append entries in the pre-fork STARTING state",
            code.contains("lifecycleState = SessionLifecycleState.STARTING"),
        )
        // Exactly one stored state at construction — no other assignment path.
        assertEquals(
            "SessionLifecycleState.STARTING must appear exactly once (spawn) in the manager",
            1,
            Regex("SessionLifecycleState\\.STARTING").findAll(code).count(),
        )
    }

    // ------------------------------------------------------------------- 3

    @Test
    fun `the real fork signal is wired end-to-end`() {
        val client = source(*clientPath.toTypedArray()).second
        assertTrue(
            "setTerminalShellPid must forward the real fork signal",
            client.contains("override fun setTerminalShellPid") && client.contains("onProcessStarted()"),
        )
        val manager = source(*managerPath.toTypedArray()).second
        assertTrue(
            "spawn() must wire onProcessStarted through the main handler into markStarted",
            manager.contains("onProcessStarted = { mainHandler.post { markStarted(id) } }"),
        )
        assertTrue(
            "markStarted must apply the pure machine",
            manager.contains("entry.lifecycleState.onProcessStarted()"),
        )
    }

    // ------------------------------------------------------------------- 4

    @Test
    fun `the real waitpid status is read through the machine with the still-running guard`() {
        val code = source(*managerPath.toTypedArray()).second
        assertTrue(
            "markFinished must pass the session's real exit status into the machine",
            code.contains("entry.lifecycleState.onProcessFinished(entry.session.getExitStatus())"),
        )
        assertTrue(
            "markFinished must refuse to record an exit while the child is still running",
            code.contains("if (entry.session.isRunning())"),
        )
    }

    // ------------------------------------------------------------------- 5

    @Test
    fun `closeSession never issues kill(0) and emits Removed with identity`() {
        val code = source(*managerPath.toTypedArray()).second
        val killGuard = Regex(
            "if \\(removed\\.session\\.getPid\\(\\) > 0\\) \\{\\s*removed\\.session\\.finishIfRunning\\(\\)",
        )
        assertTrue(
            "finishIfRunning (SIGKILL) must be guarded by pid > 0 — the upstream" +
                " isRunning() is true for mShellPid == 0, and kill(0) signals the" +
                " caller's whole process group",
            killGuard.containsMatchIn(code),
        )
        assertTrue(
            "closeSession must emit SessionLifecycleEvent.Removed with the identity block",
            Regex("SessionLifecycleEvent\\.Removed\\(").findAll(code).count() == 1,
        )
        assertTrue(
            "the Removed event must carry the exit status when the session had finished",
            code.contains("exitStatus = removed.exitStatus"),
        )
    }

    // ------------------------------------------------------------------- 6

    @Test
    fun `unexpected lifecycle deliveries are logged - never silently swallowed`() {
        val pair = source(*managerPath.toTypedArray())
        // The honest log lines themselves (string contents — checked on the raw source).
        for (marker in listOf(
            "markStarted(\$id): no such session",
            "markFinished(\$id): no such session",
            "closeSession(\$id): no such session",
            "markStarted(\$id) rejected",
            "markFinished(\$id) rejected",
            "lifecycle event buffer full",
        )) {
            assertTrue(
                "expected an honest log for: $marker",
                pair.first.contains(marker),
            )
        }
        // The logging/emission structure (code level).
        assertTrue(
            "rejected transitions must be logged at the call site",
            pair.second.contains("Log.w(LOG_TAG"),
        )
        assertTrue(
            "event emission must check tryEmit and log failures",
            pair.second.contains("if (!_lifecycleEvents.tryEmit(event))"),
        )
    }

    // ------------------------------------------------------------------- 7

    @Test
    fun `each transition kind is emitted at exactly one site`() {
        val code = source(*managerPath.toTypedArray()).second
        assertEquals(
            "exactly one Started emission (markStarted)",
            1,
            Regex("SessionLifecycleEvent\\.Started\\(").findAll(code).count(),
        )
        assertEquals(
            "exactly one Finished emission (markFinished)",
            1,
            Regex("SessionLifecycleEvent\\.Finished\\(").findAll(code).count(),
        )
        assertEquals(
            "exactly one Removed emission (closeSession)",
            1,
            Regex("SessionLifecycleEvent\\.Removed\\(").findAll(code).count(),
        )
    }

    // ------------------------------------------------------------------- 8

    @Test
    fun `the lifecycle engine has no polling, timers, proc scanning or persistence`() {
        val manager = source(*managerPath.toTypedArray()).second
        val repository = source(*repositoryPath.toTypedArray()).second
        val lifecycle = source(*lifecyclePath.toTypedArray()).second
        for (source in listOf(manager, repository, lifecycle)) {
            for (forbidden in listOf(
                "delay(", "postDelayed(", "Timer(", "Thread.sleep(", "/proc",
                "while (true)", "DataStore", "dataStore",
            )) {
                assertFalse(
                    "the lifecycle engine must stay event-driven and in-memory (found '$forbidden')",
                    source.contains(forbidden),
                )
            }
        }
    }

    // ------------------------------------------------------------------- 9

    @Test
    fun `all five spawn sites carry structured origins and named launchers carry hints`() {
        val code = source(*viewModelPath.toTypedArray()).second
        // One explicit origin per launch path:
        assertTrue(code.contains("origin = SpawnOrigin.Shell,"))
        assertTrue(code.contains("origin = SpawnOrigin.LinuxShell,"))
        assertTrue(code.contains("origin = SpawnOrigin.FilesTerminal,"))
        assertTrue(code.contains("SpawnOrigin.CommandApp(app.id)"))
        assertTrue(code.contains("SpawnOrigin.CustomTool(tool.id)"))
        assertTrue(code.contains("SpawnOrigin.CatalogApp(entry.id)"))
        assertEquals(
            "every origin is set at the spawn sites (no default mislabeling)",
            6,
            Regex("SpawnOrigin\\.(Shell|LinuxShell|FilesTerminal|CommandApp|CustomTool|CatalogApp)").findAll(code).count(),
        )
        // The three named-launcher paths attach agent hints:
        assertEquals(
            "exactly three named-launcher AgentHints (command app, custom tool, catalog app)",
            3,
            Regex("AgentHint\\(").findAll(code).count(),
        )
        assertEquals(
            "every hint is graded LAUNCH_METADATA in P2",
            3,
            Regex("AgentMatchedBy\\.LAUNCH_METADATA").findAll(code).count(),
        )
    }

    // ------------------------------------------------------------------ 10

    @Test
    fun `the repository is a pure projection - it stores nothing`() {
        val code = source(*repositoryPath.toTypedArray()).second
        for (forbidden in listOf(
            "MutableStateFlow", "MutableSharedFlow", "mutableListOf",
            "var ", "stateIn",
        )) {
            assertFalse(
                "AgentActivityRepository must own no state (found '$forbidden')",
                code.contains(forbidden),
            )
        }
        assertTrue(
            "the repository must project the manager's authoritative StateFlow",
            code.contains("TerminalSessionManager.sessions"),
        )
        assertTrue(
            "the repository must project the manager's typed event stream",
            code.contains("TerminalSessionManager.lifecycleEvents"),
        )
    }

    // ------------------------------------------------------------------ 11

    @Test
    fun `the terminal layer does not touch the notifications package`() {
        for (path in listOf(managerPath, clientPath, repositoryPath, lifecyclePath)) {
            val code = source(*path.toTypedArray()).second
            assertFalse(
                "${path.last()} must not reference the notifications package (P2 posts nothing)",
                code.contains("app.pocketshell.notifications"),
            )
            assertFalse(
                "${path.last()} must not reference NotificationCoordinator",
                code.contains("NotificationCoordinator"),
            )
        }
    }

    @Test
    fun `TerminalService keeps its FGS channel and id - P2 changed nothing there`() {
        val pair = source(*servicePath.toTypedArray())
        assertTrue(
            "the FGS channel id literal is untouched",
            pair.first.contains("const val CHANNEL_ID = \"terminal_sessions\""),
        )
        assertTrue(pair.second.contains("NOTIFICATION_ID = 1"))
        assertTrue(
            "the FGS sync policy (runs iff >= 1 entry exists) is unchanged",
            pair.second.contains("sessions.value.isNotEmpty()"),
        )
    }

    // ------------------------------------------------- typed model boundaries

    @Test
    fun `the vocabulary declares the three graded evidence sources after P3b`() {
        val code = source(*lifecyclePath.toTypedArray()).second
        assertEquals(
            "AgentMatchedBy is declared exactly once",
            1,
            Regex("enum class AgentMatchedBy").findAll(code).count(),
        )
        assertTrue(code.contains("LAUNCH_METADATA"))
        // M7.2 P3b: the ROADMAP-named compile-time-forced extension is now
        // real — the two procfs grades exist and are produced ONLY by the
        // P3b scanner (the spawn sites still carry LAUNCH_METADATA only).
        assertTrue(code.contains("PROCFS_EXE"))
        assertTrue(code.contains("PROCFS_CMDLINE"))
    }

    @Test
    fun `the event stream is buffered and drops are observable`() {
        val code = source(*managerPath.toTypedArray()).second
        assertTrue(
            "the lifecycle event flow must be a SharedFlow with an explicit buffer",
            code.contains("MutableSharedFlow<SessionLifecycleEvent>("),
        )
        assertTrue(
            "state truth remains authoritative for event reconciliation",
            code.contains("val lifecycleEvents: SharedFlow<SessionLifecycleEvent>"),
        )
    }
}
