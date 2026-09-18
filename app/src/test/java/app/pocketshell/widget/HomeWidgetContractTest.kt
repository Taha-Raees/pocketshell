package app.pocketshell.widget

import app.pocketshell.widget.external.WidgetManifestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8 — the Home widget system's structural contract (source-reading pins
 * over the shipped sources; the established technique — the JVM suite has
 * no device runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. HomeScreen stays a LIFECYCLE-AWARE, PROBE-FREE host: the M7.2 P8
 *      "Home never polls" pin is extended — no probing tokens may appear
 *      in HomeScreen.kt at all now that data widgets exist (probing lives
 *      in the widget package).
 *   2. The hero row resolves through the ONE registry and renders the
 *      honest Missing card for unknown ids — never a substitution.
 *   3. The Agents widget is a claims-projection consumer ONLY: no /proc,
 *      no detector access, no own state, and the M7.2 vocabulary ban list.
 *   4. Slot persistence is ONE DataStore file with ONE key.
 *   5. External manifests are data-only: the validator and renderer carry
 *      no execution surface; the example catalog in widgets/ validates
 *      against the implementation (published format drift guard).
 *   6. The fd probe reads symlinks with readSymbolicLink (canonicalPath
 *      throws on socket:[…] links — lab-verified), never a substitute.
 */
class HomeWidgetContractTest {

    // ------------------------------------------------------------ helpers

    private fun readSource(relative: String): String {
        val file = listOf(relative, "../$relative")
            .map { File(it) }
            .firstOrNull { it.isFile }
        assumeTrue("source not found on this runner: $relative", file != null)
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

    private fun stringLiterals(source: String): List<String> {
        val literals = mutableListOf<String>()
        var i = 0
        while (i < source.length) {
            if (source[i] == '"') {
                val start = i + 1
                var j = start
                while (j < source.length && source[j] != '"') {
                    if (source[j] == '\\') j++
                    j++
                }
                literals += source.substring(start, j.coerceAtMost(source.length))
                i = j + 1
            } else {
                i++
            }
        }
        return literals
    }

    private fun widgetSource(name: String): String =
        readSource("app/src/main/java/app/pocketshell/widget/$name")

    // ------------------------------------- 1. the host is probe-free

    @Test
    fun `HomeScreen hosts widgets but never probes - the M7_2 pin extended`() {
        val code = stripCommentsAndStrings(
            readSource("app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt"),
        )
        val banned = listOf(
            "/proc", "delay(", "while (true)", "Thread(", "Timer(",
            "fixedRateTimer", "postDelayed", "PortProbe", "StorageScan",
            "HostProcfsReader", "RuntimeAgentDetector", "ProcessBuilder",
        )
        val found = banned.filter { code.contains(it) }
        assertTrue("HomeScreen must stay probe-free; found: $found", found.isEmpty())
        // The claims seam the Sessions section rides stays the ViewModel's
        // lifecycle-aware projection (unchanged M7.2 P8 contract).
        assertTrue(code.contains("collectAsStateWithLifecycle"))
    }

    // ------------------------------------- 2. resolution through the ONE registry

    @Test
    fun `the hero host resolves through the registry and states missing widgets`() {
        val code = stripCommentsAndStrings(
            readSource("app/src/main/java/app/pocketshell/ui/home/WidgetHost.kt"),
        )
        assertTrue(code.contains("WidgetRegistry.resolve"))
        assertTrue(code.contains("MissingWidgetCard"))
        assertTrue(code.contains("WidgetRegistry.DEFAULT_SLOTS"))
    }

    // ------------------------------------- 3. the Agents widget's consumer contract

    @Test
    fun `the Agents widget consumes only the claims projection`() {
        val code = stripCommentsAndStrings(widgetSource("AgentsWidget.kt"))
        assertTrue(
            "the widget must read the claims Home already holds",
            code.contains("context.agentClaims"),
        )
        val banned = listOf(
            "RuntimeAgentDetector", "TerminalSessionManager", "MutableStateFlow",
            "MutableSharedFlow", "/proc", "delay(", "AgentProcessMatcher",
            "NotificationManager", "DataStore",
        )
        val found = banned.filter { code.contains(it) }
        assertTrue("Agents widget must not own or discover state; found: $found", found.isEmpty())
    }

    @Test
    fun `the Agents widget literal vocabulary passes the M7_2 honesty ban list`() {
        val literals = stringLiterals(widgetSource("AgentsWidget.kt")) +
            stringLiterals(widgetSource("AgentActivitySummary.kt"))
        val banned = listOf(
            "completed", "completion", "success", "succeed", "finish",
            "failed", "failure", "waiting for input", "needs input", "needs attention",
        )
        val violations = literals.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue("honesty sweep violations: $violations", violations.isEmpty())
    }

    // ------------------------------------- 4. slot persistence discipline

    @Test
    fun `slot persistence is ONE DataStore file with ONE key`() {
        val repo = widgetSource("HomeWidgetSlotsRepository.kt")
        assertTrue(repo.contains("preferencesDataStore(name = \"home_widgets\")"))
        assertTrue(repo.contains("\"slot_widget_ids\""))
        // The absent/corrupt path is the codec's honest default.
        assertTrue(repo.contains("WidgetSlotsCodec.decode"))
    }

    // ------------------------------------- 5. external widgets are data-only

    @Test
    fun `the external-widget layer carries no execution surface`() {
        listOf("external/WidgetManifestValidator.kt", "external/DeclarativeWidgetRenderer.kt")
            .forEach { name ->
                val code = stripCommentsAndStrings(widgetSource(name))
                val banned = listOf(
                    "ProcessBuilder", "Runtime.getRuntime", ".exec(",
                    "Runtime.exec", "Shell(", "su ", "libproot",
                )
                val found = banned.filter { code.contains(it) }
                assertTrue("$name must never execute anything; found: $found", found.isEmpty())
            }
    }

    @Test
    fun `the example catalog validates against the implementation`() {
        // The published-format drift guard: the repository's widgets/ tree is
        // the future distribution catalog's shape — it must stay valid.
        listOf("servers", "storage").forEach { id ->
            val text = listOf("widgets/$id/manifest.json", "../widgets/$id/manifest.json")
                .map { File(it) }
                .firstOrNull { it.isFile }
            assumeTrue("widgets/$id/manifest.json not found on this runner", text != null)
            val result = WidgetManifestValidator.parse(text!!.readText())
            assertTrue(
                "widgets/$id/manifest.json must validate; got $result",
                result is WidgetManifestValidator.Result.Valid,
            )
        }
    }

    // ------------------------------------- 6. the probe reads links the safe way

    @Test
    fun `the fd probe uses readSymbolicLink - canonicalPath throws on socket links`() {
        val code = stripCommentsAndStrings(widgetSource("probe/PortProbe.kt"))
        assertTrue(code.contains("readSymbolicLink"))
        assertTrue(
            "canonicalPath must not be used for fd links (lab-verified failure)",
            !code.contains("canonicalPath"),
        )
    }

    // ------------------------------------- 7. routes + wiring

    @Test
    fun `the Control Center routes the Home widgets page`() {
        val main = readSource("app/src/main/java/app/pocketshell/MainActivity.kt")
        assertTrue(
            "MainActivity must route the HomeWidgetsScreen",
            main.contains("\"homeWidgets\" -> app.pocketshell.ui.settings.HomeWidgetsScreen("),
        )
        assertTrue(
            "Home must receive the persisted slot assignment",
            main.contains("widgetSlots = widgetSlots"),
        )
        val settings = readSource("app/src/main/java/app/pocketshell/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("ControlCenterRow(\n                title = \"Home widgets\""))
    }

    // ------------------------------------- 8. registry sanity at the contract level

    @Test
    fun `the default slots are exactly the two preserved core cards`() {
        assertEquals(listOf("core.terminal", "core.linux"), WidgetRegistry.DEFAULT_SLOTS)
    }
}
