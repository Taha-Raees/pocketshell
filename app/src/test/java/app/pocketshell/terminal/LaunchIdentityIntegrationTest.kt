package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.2 P3a — the launch-identity integration contract (structural pins over
 * the real sources; the same honest technique as SessionLifecycleIntegrationTest
 * — the JVM suite has no Robolectric/device runner, so the glue and the
 * boundaries are pinned by reading the shipped sources).
 *
 * What is pinned here is everything the pure [LaunchIdentityTest] cannot
 * reach:
 *
 *   1. ownership (PART H): the classification adds METADATA only —
 *      TerminalSessionManager stays the ONE lifecycle authority, unchanged;
 *      the SessionEntry stores origin + agent exactly as P2 shipped (no
 *      stored classification — identity is derived, never a second truth);
 *   2. the classifier resolves against the REAL registry/catalog objects
 *      and is EXHAUSTIVE over the sealed SpawnOrigin kinds;
 *   3. the never-promote rule for custom tools is structural: the
 *      CustomTool branch consults no registry;
 *   4. no "agent running/completed" API exists anywhere in the app's main
 *      sources (the false-running and completion boundaries hold by
 *      construction, not by comment);
 *   5. the P3a surface touches no notifications, no /proc, no polling, no
 *      persistence (the P1/P2 boundaries carry through);
 *   6. the P2 vocabulary is not mutated: SpawnOrigin stays sealed with the
 *      six launch kinds, AgentMatchedBy still declares exactly
 *      LAUNCH_METADATA (P3b's procfs grades are not pre-invented).
 *
 * Real launch/exit behavior on hardware remains the docs/TESTING.md gates
 * (§47/§48/§49) — P3a adds no device-visible behavior and no new gate.
 */
class LaunchIdentityIntegrationTest {

    private fun readSource(relatives: List<String>): String {
        // Dual candidates per the established convention
        // (ExternalKeyboardIntegrationTest / SessionLifecycleIntegrationTest):
        // root CWD ("app/src/...") and module CWD ("src/...") — pins must
        // RUN under either runner layout, never silently skip.
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
                    // Step PAST the closing quote — without this, the closing
                    // quote re-enters this branch and consumes real code up
                    // to the next string (the P2 helper's exact contract).
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

    private val identityPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "LaunchIdentity.kt")
    private val managerPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "TerminalSessionManager.kt")
    private val repositoryPath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "AgentActivityRepository.kt")
    private val lifecyclePath =
        listOf("app", "src", "main", "java", "app", "pocketshell", "terminal", "SessionLifecycle.kt")

    // ------------------------------------------------------------ ownership

    @Test
    fun `the manager's lifecycle authority is untouched by P3a - no classification stored`() {
        val (raw, code) = source(*managerPath.toTypedArray())
        // The entry still stores exactly the P2 metadata: typed state, origin, agent hint.
        assertTrue(code.contains("val lifecycleState: SessionLifecycleState"))
        assertTrue(code.contains("val origin: SpawnOrigin"))
        assertTrue(code.contains("val agent: AgentHint?"))
        // No stored/derived classification field on the entry: identity is a
        // projection, never a second stored truth inside the manager.
        assertFalse(
            "SessionEntry must not store a LaunchIdentity (it is derived, PART H)",
            code.contains("LaunchIdentity"),
        )
        // The manager still owns transitions exactly as P2 shipped — the pure
        // machine is the only path, on the main handler.
        assertTrue(code.contains("lifecycleState.onProcessStarted()"))
        assertTrue(code.contains("lifecycleState.onProcessFinished("))
    }

    @Test
    fun `the lifecycle owner consults no launcher registry - classification is not its job`() {
        val (raw, code) = source(*managerPath.toTypedArray())
        assertFalse(
            "TerminalSessionManager must not import the apps registry",
            code.contains("CommandAppCatalog"),
        )
        assertFalse(
            "TerminalSessionManager must not import the CLI catalog",
            code.contains("CliAppCatalog"),
        )
        assertFalse(
            "TerminalSessionManager must not reference LaunchIdentity",
            code.contains("LaunchIdentity"),
        )
    }

    // ------------------------------------------------- the classifier's shape

    @Test
    fun `the classifier resolves against the real registry and catalog objects`() {
        val code = source(*identityPath.toTypedArray()).second
        assertTrue(
            "KnownAgent must be gated on the REAL registry lookup",
            code.contains("CommandAppCatalog.byId("),
        )
        assertTrue(
            "KnownNonAgentTool must be gated on the REAL catalog lookup",
            code.contains("CliAppCatalog.byId("),
        )
    }

    @Test
    fun `the classifier is exhaustive over the sealed SpawnOrigin kinds`() {
        val code = source(*identityPath.toTypedArray()).second
        assertTrue(code.contains("is SpawnOrigin.CommandApp ->"))
        assertTrue(code.contains("is SpawnOrigin.CatalogApp ->"))
        assertTrue(code.contains("is SpawnOrigin.CustomTool ->"))
        assertTrue(
            "plain-shell origins must claim no identity",
            code.contains("SpawnOrigin.Shell, SpawnOrigin.LinuxShell, SpawnOrigin.FilesTerminal -> null"),
        )
    }

    @Test
    fun `the custom-tool branch consults no registry - the never-promote rule is structural`() {
        val raw = source(*identityPath.toTypedArray()).first
        // Slice the CustomTool branch out of the RAW source (between its
        // label and the plain-shell fallthrough) and assert it never names
        // a registry lookup.
        val start = raw.indexOf("is SpawnOrigin.CustomTool ->")
        val end = raw.indexOf("SpawnOrigin.Shell, SpawnOrigin.LinuxShell")
        org.junit.Assume.assumeTrue("custom-tool branch not found", start >= 0 && end > start)
        val branch = raw.substring(start, end)
        assertFalse(
            "the CustomTool branch must never resolve against any registry",
            branch.contains("byId"),
        )
        assertTrue(
            "the CustomTool branch must classify CustomOrUnknown unconditionally",
            branch.contains("CustomOrUnknown"),
        )
    }

    // ------------------------------------- the false-running / completion rules

    @Test
    fun `no agent completion API exists anywhere - and the running-state vocabulary is confined to the P3b seam`() {
        val root = File(rootCandidate("app/src/main/java/app/pocketshell"))
        org.junit.Assume.assumeTrue("main source root not found", root.isDirectory)
        // (a) COMPLETION claims are still banned EVERYWHERE (P3a rule, now
        // stronger: P3b's runtime model explicitly stops at NOT_RUNNING).
        val completionTokens = listOf(
            "agentCompleted", "isAgentComplete", "isAgentFinished",
            "AgentCompleted", "CompletionState", "onAgentFinished",
        )
        // (b) the RUNNING-state vocabulary is the P3b deliverable — but it
        // may exist ONLY in the evidence seam (detection model, detector,
        // repository projection) and, since M7.2 P3c, in the event layer
        // that CONSUMES it (the ROADMAP-authorized consumer boundary: the
        // event engine folds the detector's published observations into
        // transitions — it performs no detection of its own). Since M7.2 P8,
        // also in the pure HOME presentation claim, the third authorized
        // consumer: it reads the detector's published observations to decide
        // what the Home Sessions row may say — it performs no detection of
        // its own either (the P8 parity contract keeps it aligned with the
        // notification layer state-for-state). Every other file stays free
        // of it.
        val runningTokens = listOf("AgentRuntimeState", "AgentRuntimeDetection")
        val seamFiles = setOf(
            "AgentRuntimeDetection.kt", "RuntimeAgentDetector.kt",
            "AgentActivityRepository.kt", "SessionLifecycle.kt",
            "AgentRuntimeEvents.kt", "AgentRuntimeEventEngine.kt",
            "AgentHomeSessionClaims.kt",
        )
        val completionOffenders = mutableListOf<String>()
        val runningOffenders = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                completionTokens.filter { text.contains(it) }.forEach {
                    completionOffenders += "${file.name}:$it"
                }
                if (file.name !in seamFiles) {
                    runningTokens.filter { text.contains(it) }.forEach {
                        runningOffenders += "${file.name}:$it"
                    }
                }
            }
        assertTrue(
            "no agent completion API may exist anywhere (the truth boundary holds by construction): $completionOffenders",
            completionOffenders.isEmpty(),
        )
        assertTrue(
            "the running-state vocabulary must stay confined to the P3b evidence seam: $runningOffenders",
            runningOffenders.isEmpty(),
        )
    }

    private fun rootCandidate(relative: String): String {
        val direct = File(relative)
        if (direct.isDirectory) return direct.path
        val parent = File("../$relative")
        if (parent.isDirectory) return parent.path
        return relative
    }

    // ------------------------------------------------------- P1/P2 boundaries

    @Test
    fun `the P3a surface touches no notifications, no proc, no polling, no persistence`() {
        val identity = source(*identityPath.toTypedArray())
        val repository = source(*repositoryPath.toTypedArray())
        for ((name, pair) in listOf("LaunchIdentity.kt" to identity, "AgentActivityRepository.kt" to repository)) {
            val code = pair.second
            assertFalse(
                "$name must not reference the notifications package (P1 boundary)",
                code.contains("notifications"),
            )
            assertFalse("$name must not scan /proc", code.contains("/proc"))
            assertFalse("$name must not poll", Regex("\\bpostDelayed\\b|\\bTimer\\b|\\bsleep\\(").containsMatchIn(code))
            assertFalse(
                "$name must not persist (in-memory by design)",
                code.contains("DataStore") || code.contains("preferencesDataStore"),
            )
            assertFalse("$name must not parse terminal output (OSC 133 territory)", code.contains("OSC"))
        }
    }

    @Test
    fun `the P2 vocabulary is not mutated by P3b beyond the compile-time-forced procfs grades`() {
        val lifecycle = source(*lifecyclePath.toTypedArray())
        val (rawLifecycle, code) = lifecycle
        // SpawnOrigin stays sealed with exactly the six P2 launch kinds.
        assertTrue(rawLifecycle.contains("sealed class SpawnOrigin"))
        for (kind in listOf("Shell", "LinuxShell", "FilesTerminal", "CommandApp", "CatalogApp", "CustomTool")) {
            assertTrue("SpawnOrigin must keep the $kind kind", code.contains(kind))
        }
        // AgentMatchedBy: P3a shipped exactly LAUNCH_METADATA; the ROADMAP
        // named P3b's two procfs grades as the compile-time-forced extension.
        // The enum now declares EXACTLY those three values — no others.
        val enumStart = rawLifecycle.indexOf("enum class AgentMatchedBy")
        val enumEnd = rawLifecycle.indexOf("}", enumStart)
        val enumBody = stripCommentsAndStrings(rawLifecycle.substring(enumStart, enumEnd))
        for (grade in listOf("LAUNCH_METADATA", "PROCFS_EXE", "PROCFS_CMDLINE")) {
            assertTrue("AgentMatchedBy must declare $grade", enumBody.contains(grade))
        }
        val declaredValues = Regex("[A-Z_]+,").findAll(enumBody).map { it.value.dropLast(1) }.toSet()
        assertEquals(
            "AgentMatchedBy must declare exactly the three graded evidence sources",
            setOf("LAUNCH_METADATA", "PROCFS_EXE", "PROCFS_CMDLINE"),
            declaredValues,
        )
    }

    @Test
    fun `the repository projection derives through the classifier and stores nothing`() {
        val code = source(*repositoryPath.toTypedArray()).second
        assertTrue(
            "classifiedLaunches must derive via LaunchIdentity.of",
            code.contains("LaunchIdentity.of("),
        )
        assertFalse(
            "the repository must keep no mutable state (stores nothing, decides nothing)",
            code.contains("MutableStateFlow") || code.contains("MutableSharedFlow"),
        )
    }
}
