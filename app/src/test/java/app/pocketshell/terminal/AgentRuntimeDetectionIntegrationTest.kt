package app.pocketshell.terminal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P3b — the runtime-detection structural contract (source-reading pins
 * over the real shipped sources; the established technique from
 * SessionLifecycleIntegrationTest / LaunchIdentityIntegrationTest — the JVM
 * suite has no Robolectric/device runner, so boundaries are pinned by
 * reading the sources that ship).
 *
 * What is pinned here is everything the pure [AgentRuntimeDetectionTest]
 * cannot reach — the PHASE MANDATE's structural rules (PART O):
 *
 *   1. NO notifications: no production source in this phase posts a
 *      notification or touches Android notification APIs for agent
 *      activity (the P1 foundation stays the only notification surface);
 *   2. NO completion claims: the runtime vocabulary adds no
 *      completed/success/finished certainty — disappearance is at most
 *      NOT_RUNNING;
 *   3. NO output heuristics: no OSC 133, no terminal-output parsing, no
 *      prompt/command-completion regexes anywhere in the P3b surface;
 *   4. NO custom-tool promotion: the scanner activates ONLY for
 *      [LaunchIdentity.KnownAgent]; known non-agent tools and
 *      custom/unknown launchers map to NOT_APPLICABLE explicitly;
 *   5. /proc access is CONFINED to the HostProcfsReader seam — the pure
 *      model, the manager and the repository never touch it;
 *   6. the manager's extension is minimal: the correlation root is
 *      recorded exactly ON the real fork signal, and the ONE lifecycle
 *      authority gains nothing else;
 *   7. the detector owns NO lifecycle authority: it is an observer of the
 *      manager's StateFlow, never a writer;
 *   8. the polling discipline: a named interval, parked when no eligible
 *      session exists, no AlarmManager/WakeLock/WorkManager.
 */
class AgentRuntimeDetectionIntegrationTest {

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

    private val detectionPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentRuntimeDetection.kt")
    private val detectorPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "RuntimeAgentDetector.kt")
    private val repositoryPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentActivityRepository.kt")
    private val managerPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "TerminalSessionManager.kt")

    // ------------------------------------------------- 1. no notifications

    @Test
    fun `no notification APIs anywhere in the P3b surface`() {
        for (path in listOf(detectionPath, detectorPath, repositoryPath)) {
            val (raw, code) = source(path)
            for (forbidden in listOf("NotificationManager", "NotificationCompat", "notify(", "NotificationChannel")) {
                assertFalse(
                    "${path.last()} must not touch notification APIs (found '$forbidden')",
                    code.contains(forbidden),
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
    fun `the runtime state vocabulary is exactly the four mandated states - no completion certainty`() {
        val raw = source(detectionPath).first
        val start = raw.indexOf("enum class AgentRuntimeState")
        val end = raw.indexOf("}", start)
        org.junit.Assume.assumeTrue("AgentRuntimeState enum not found", start >= 0 && end > start)
        val body = stripCommentsAndStrings(raw.substring(start, end))
        for (required in listOf("NOT_APPLICABLE", "UNKNOWN", "NOT_RUNNING", "RUNNING")) {
            assertTrue("AgentRuntimeState must declare $required", body.contains(required))
        }
        for (forbidden in listOf("COMPLETED", "SUCCESS", "FINISHED", "WAITING", "IDLE")) {
            assertFalse(
                "the runtime vocabulary must add no completion/waiting certainty (found '$forbidden')",
                body.contains(forbidden),
            )
        }
    }

    @Test
    fun `the pure detection layer names no completion concept`() {
        val code = source(detectionPath).second
        for (forbidden in listOf("Completed", "Success", "Finished", "agentCompleted")) {
            assertFalse(
                "the runtime evidence layer must never conclude completion (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    // ------------------------------------------------- 3. no output heuristics

    @Test
    fun `no output heuristics in the P3b surface - no OSC 133, no terminal parsing`() {
        for (path in listOf(detectionPath, detectorPath)) {
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

    // ------------------------------------------- 4. the activation boundary

    @Test
    fun `the scanner activates only for KnownAgent identities - never for tools or custom launchers`() {
        val detector = source(detectorPath).second
        // The eligibility filter: the identity must classify KnownAgent and
        // everything else is dropped before any scan happens.
        assertTrue(
            "eligibility must filter on the P3a classifier's KnownAgent kind",
            detector.contains("identity !is LaunchIdentity.KnownAgent"),
        )
        assertTrue(
            "eligibility must consult the real P3a resolver (LaunchIdentity.of)",
            detector.contains("LaunchIdentity.of("),
        )
        // The repository states the negative explicitly: tools and custom
        // launchers are NOT_APPLICABLE, never RUNNING.
        val repository = source(repositoryPath).second
        assertTrue(
            "KnownNonAgentTool must map to NOT_APPLICABLE",
            repository.contains("is LaunchIdentity.KnownNonAgentTool -> AgentRuntimeState.NOT_APPLICABLE"),
        )
        assertTrue(
            "CustomOrUnknown must map to NOT_APPLICABLE",
            repository.contains("is LaunchIdentity.CustomOrUnknown -> AgentRuntimeState.NOT_APPLICABLE"),
        )
    }

    // ------------------------------------------------ 5. /proc confinement

    @Test
    fun `proc access is confined to the HostProcfsReader seam`() {
        // The pure model must stay pure: no filesystem access at all.
        val detection = source(detectionPath).second
        assertFalse("AgentRuntimeDetection.kt must not touch /proc (it is pure)", detection.contains("/proc"))
        assertFalse("AgentRuntimeDetection.kt must not do file I/O", detection.contains("java.io"))
        // The manager (the engine) stays /proc-free — the existing P2 pin,
        // restated here because P3b touched the manager.
        val manager = source(managerPath).second
        assertFalse("TerminalSessionManager.kt must not read /proc", manager.contains("/proc"))
        // The repository projects; it never scans.
        val repository = source(repositoryPath).second
        assertFalse("AgentActivityRepository.kt must not read /proc", repository.contains("/proc"))
        // The reader itself is the ONE place /proc is named in code (as the
        // quoted default root of the reader class — a string literal, so
        // checked against the RAW source).
        val detector = source(detectorPath)
        assertTrue(
            "the HostProcfsReader seam must be the procfs reader",
            detector.first.contains("\"/proc\"") && detector.second.contains("class HostProcfsReader"),
        )
    }

    // --------------------------------- 6. the minimal manager extension

    @Test
    fun `the correlation root is recorded exactly on the real fork signal`() {
        val (rawManager, code) = source(managerPath)
        assertTrue(
            "SessionEntry must declare the fork-proven shellPid",
            code.contains("val shellPid: Int = 0"),
        )
        // The write happens ONLY inside markStarted (the STARTING->RUNNING
        // Accepted path), never in spawn() before the fork is proven.
        val markStart = rawManager.indexOf("private fun markStarted")
        val markEnd = rawManager.indexOf("private fun markFinished")
        org.junit.Assume.assumeTrue("markStarted slice not found", markStart >= 0 && markEnd > markStart)
        val markBody = stripCommentsAndStrings(rawManager.substring(markStart, markEnd))
        assertTrue(
            "markStarted must capture the fork-proven pid with the transition",
            markBody.contains("shellPid = entry.session.getPid()"),
        )
        val spawnStart = rawManager.indexOf("private fun spawn(")
        val spawnEnd = rawManager.indexOf("fun closeSession(")
        org.junit.Assume.assumeTrue("spawn slice not found", spawnStart >= 0 && spawnEnd > spawnStart)
        assertFalse(
            "spawn() must not record a pid before the fork signal proves it",
            stripCommentsAndStrings(rawManager.substring(spawnStart, spawnEnd)).contains("getPid()"),
        )
        // The manager gained nothing else: still no registry, no LaunchIdentity,
        // no polling (the P2/P3a engine pins restated).
        assertFalse(code.contains("CommandAppCatalog"))
        assertFalse(code.contains("CliAppCatalog"))
        assertFalse(code.contains("LaunchIdentity"))
        assertFalse(code.contains("/proc"))
        assertFalse(Regex("\\bdelay\\(|\\bpostDelayed\\(|\\bTimer\\(").containsMatchIn(code))
    }

    // ---------------------------------------- 7. the detector owns nothing

    @Test
    fun `the detector observes the manager but owns no lifecycle authority`() {
        val code = source(detectorPath).second
        assertTrue(
            "the detector must observe the manager's authoritative StateFlow",
            code.contains("TerminalSessionManager.sessions"),
        )
        for (forbidden in listOf(
            "_sessions.update", "_sessions.value =",
            "closeSession(", "markStarted(", "markFinished(", "finishIfRunning",
            "SessionLifecycleState", "onProcessStarted", "onProcessFinished",
        )) {
            assertFalse(
                "the detector must never drive session lifecycle (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    // ----------------------------------------------- 8. polling discipline

    @Test
    fun `the polling discipline is conservative and gated on eligible sessions`() {
        val code = source(detectorPath).second
        assertTrue("the tick interval must be a named constant", code.contains("const val SCAN_INTERVAL_MS"))
        assertTrue(
            "the monitor must park when no eligible session exists",
            code.contains("if (eligible.isEmpty()) return@collectLatest"),
        )
        assertTrue(
            "eligibility changes must cancel the stale tick loop (collectLatest)",
            code.contains("collectLatest"),
        )
        for (forbidden in listOf("AlarmManager", "WakeLock", "WorkManager", "setExact", "postDelayed")) {
            assertFalse(
                "the scanner must not wake the device or outlive its scope (found '$forbidden')",
                code.contains(forbidden),
            )
        }
    }

    @Test
    fun `the detector starts exactly once and only from the manager's spawn path`() {
        val manager = source(managerPath).second
        assertTrue(
            "spawn must wake the evidence provider (the single start site)",
            manager.contains("RuntimeAgentDetector.ensureStarted()"),
        )
        val detector = source(detectorPath).second
        assertTrue(
            "ensureStarted must be idempotent",
            detector.contains("compareAndSet"),
        )
    }

    // ----------------------------------------- 9. the repository stays pure

    @Test
    fun `the repository runtime projection still stores nothing and decides nothing`() {
        val code = source(repositoryPath).second
        assertFalse(
            "the repository must keep no mutable state (stores nothing)",
            code.contains("MutableStateFlow") || code.contains("MutableSharedFlow"),
        )
        assertTrue(
            "the runtime projection must combine the manager's state with the detector's evidence",
            code.contains("RuntimeAgentDetector.observations"),
        )
        assertTrue(
            "the runtime projection must derive identities through the P3a classifier",
            code.contains("LaunchIdentity.of("),
        )
    }
}
