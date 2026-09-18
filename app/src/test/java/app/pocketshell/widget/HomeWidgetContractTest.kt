package app.pocketshell.widget

import app.pocketshell.widget.external.WidgetManifestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8.2 — the HOME APPLICATION structural contract (source-reading pins
 * over the shipped sources; the established technique — the JVM suite has
 * no device runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. THE ONE-CARD ARCHITECTURE: the two-slot hero era is GONE —
 *      HomeScreen hosts exactly ONE HomeApplicationHost, no slot rows, no
 *      slot persistence, no per-widget hero wiring.
 *   2. HomeScreen stays a LIFECYCLE-AWARE, PROBE-FREE host (the M7.2 P8
 *      pin, extended to M8.2): probing lives in the widget package only.
 *   3. THEME INDEPENDENCE: the Servers application uses ONLY the shared
 *      PocketShell theme tokens — no hardcoded colors, no Aurora literals;
 *      the aurora edge rides the theme-gated shared phase like every
 *      other Home surface.
 *   4. IN-CARD NAVIGATION: the application owns its detail state and its
 *      own back handler (the user never leaves Home to see a detail).
 *   5. Persistence: ONE key ("home_app_id") in the home_widgets store,
 *      absent/corrupt → default; unknown ids render the Missing card.
 *   6. The Agents summary (retained pure logic for the future Agents
 *      application) keeps the M7.2 vocabulary honesty ban list.
 *   7. The external-widget layer carries no execution surface; the
 *      example catalog validates against the implementation.
 *   8. The fd probe reads symlinks with readSymbolicLink (canonicalPath
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

    private fun mainSource(relative: String): String =
        readSource("app/src/main/java/$relative")

    private fun widgetSource(name: String): String =
        mainSource("app/pocketshell/widget/$name")

    // --------------------------------- 1. the one-card architecture

    @Test
    fun `Home hosts exactly ONE application card - the two-slot era is gone`() {
        val home = stripCommentsAndStrings(
            mainSource("app/pocketshell/ui/home/HomeScreen.kt"),
        )
        assertTrue(
            "Home must host the ONE application card",
            home.contains("HomeApplicationHost("),
        )
        val banned = listOf(
            "WidgetHeroRow", "WidgetSlotEntry", "WidgetRegistry",
            "WidgetSlotsCodec", "TerminalWidget", "LinuxWidget",
            "StorageWidget", "AgentsWidget", "weight(1.25f)",
        )
        val found = banned.filter { home.contains(it) }
        assertTrue("two-slot remnants must be gone; found: $found", found.isEmpty())
    }

    @Test
    fun `the host owns the card and resolves through the ONE registry`() {
        val host = stripCommentsAndStrings(
            mainSource("app/pocketshell/ui/home/HomeApplicationHost.kt"),
        )
        assertTrue(host.contains("HomeApplications.resolve"))
        assertTrue(host.contains("MissingApplicationCard"))
        // The card dimensions come from the existing layout's derived
        // token, never a hardcoded fake size.
        assertTrue(host.contains("HomeTokens.homeAppCardHeight"))
        val widgetDir = File("app/src/main/java/app/pocketshell/widget")
            .takeIf { it.isDirectory }
            ?: File("../app/src/main/java/app/pocketshell/widget")
        assumeTrue("widget dir not found on this runner", widgetDir.isDirectory)
        val registryUsages = widgetDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.readText() }
            .filter { stripCommentsAndStrings(it).contains("weight(1.25f)") }
            .toList()
        assertTrue("no two-card weight geometry may survive", registryUsages.isEmpty())
    }

    // --------------------------------- 2. the host is probe-free

    @Test
    fun `HomeScreen hosts applications but never probes - the M7_2 pin extended`() {
        val code = stripCommentsAndStrings(
            mainSource("app/pocketshell/ui/home/HomeScreen.kt"),
        )
        val banned = listOf(
            "/proc", "delay(", "while (true)", "Thread(", "Timer(",
            "fixedRateTimer", "postDelayed", "PortProbe", "ServerProbe",
            "StorageScan", "HostProcfsReader", "RuntimeAgentDetector",
            "ProcessBuilder",
        )
        val found = banned.filter { code.contains(it) }
        assertTrue("HomeScreen must stay probe-free; found: $found", found.isEmpty())
        // The claims seam the Sessions section rides stays the ViewModel's
        // lifecycle-aware projection (unchanged M7.2 P8 contract).
        assertTrue(code.contains("collectAsStateWithLifecycle"))
    }

    // --------------------------------- 3. theme independence

    @Test
    fun `the Servers application follows the shared theme - no private theme`() {
        val code = stripCommentsAndStrings(widgetSource("ServersApp.kt"))
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        // The old hero-tone pair (pinned-dark canvas text) is a two-card
        // relic; the application card is a chrome-surface citizen.
        assertFalse("no canvas-tone text pair", code.contains("onHero"))
        val hardcodedColor = Regex("""Color\(0x""")
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            hardcodedColor.containsMatchIn(code),
        )
        val literals = stringLiterals(widgetSource("ServersApp.kt"))
        assertFalse(
            "no theme-name literals (Aurora is ONE theme, never a dependency)",
            literals.any { it.contains("Aurora", ignoreCase = true) },
        )
    }

    // --------------------------------- 4. in-card navigation

    @Test
    fun `the application owns its detail state and its own back`() {
        val code = stripCommentsAndStrings(widgetSource("ServersApp.kt"))
        assertTrue(
            "detail selection must be saveable (rotation, round-trips)",
            code.contains("rememberSaveable"),
        )
        assertTrue(
            "back inside the card returns to the overview before leaving Home",
            code.contains("BackHandler(enabled = selected != null)"),
        )
        // The application acts ONLY through the navigation seam.
        assertTrue(code.contains("nav.openTerminal()"))
        assertTrue(code.contains("nav.openCompanion("))
    }

    // ------------------------------------- 5. slot/app persistence

    @Test
    fun `application persistence is ONE key with an honest default`() {
        val repo = widgetSource("HomeApplicationRepository.kt")
        assertTrue(repo.contains("preferencesDataStore(name = \"home_widgets\")"))
        assertTrue(repo.contains("\"home_app_id\""))
        assertTrue(
            "the two-slot record key must be gone",
            !repo.contains("slot_widget_ids"),
        )
        assertTrue(repo.contains("HomeAppIdCodec.decode"))
        // Registry sanity at the contract level.
        assertEquals(listOf("servers"), HomeApplications.all.map { it.spec.id })
        assertEquals(HomeApplications.SERVERS_ID, HomeApplications.DEFAULT_ID)
    }

    // --------------------------------- 6. the Agents summary's retained contract

    @Test
    fun `the Agents summary keeps the M7_2 honesty ban list`() {
        val literals = stringLiterals(widgetSource("AgentActivitySummary.kt"))
        val banned = listOf(
            "completed", "completion", "success", "succeed", "finish",
            "failed", "failure", "waiting for input", "needs input", "needs attention",
        )
        val violations = literals.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue("honesty sweep violations: $violations", violations.isEmpty())
        val summaryCode = stripCommentsAndStrings(widgetSource("AgentActivitySummary.kt"))
        val bannedTokens = listOf(
            "RuntimeAgentDetector", "TerminalSessionManager", "MutableStateFlow",
            "/proc", "DataStore",
        )
        val found = bannedTokens.filter { summaryCode.contains(it) }
        assertTrue("the summary stays a pure projection consumer; found: $found", found.isEmpty())
    }

    // ------------------------------------- 7. external widgets are data-only

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

    // ------------------------------------- 8. the probe reads links the safe way

    @Test
    fun `the fd probe uses readSymbolicLink - canonicalPath throws on socket links`() {
        val code = stripCommentsAndStrings(widgetSource("probe/ServerProbe.kt"))
        // The FD-SCAN path must read links raw (canonicalPath THROWS on
        // socket:[…] links — lab-verified). The cwd magic symlink is the
        // one place canonicalPath is the correct call (it resolves), so
        // the pin is scoped to the fd-scan region.
        val start = code.indexOf("private fun readSocketInodes")
        assumeTrue("readSocketInodes not found on this runner", start >= 0)
        val end = code.indexOf("private fun", start + 10).let { if (it < 0) code.length else it }
        val region = code.substring(start, end)
        assertTrue(region.contains("readSymbolicLink"))
        assertFalse(
            "canonicalPath must not be used for fd links (lab-verified failure)",
            region.contains("canonicalPath"),
        )
    }

    // ------------------------------------- 9. routes + wiring

    @Test
    fun `the Control Center routes the Home application page`() {
        val main = readSource("app/src/main/java/app/pocketshell/MainActivity.kt")
        assertTrue(
            "MainActivity must route the HomeWidgetsScreen",
            main.contains("\"homeWidgets\" -> app.pocketshell.ui.settings.HomeWidgetsScreen("),
        )
        assertTrue(
            "Home must receive the persisted application id",
            main.contains("homeAppId = homeAppId,"),
        )
        assertFalse(
            "the two-slot parameter must be gone",
            main.contains("widgetSlots"),
        )
        val settings = mainSource("app/pocketshell/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("ControlCenterRow(\n                title = \"Home widgets\""))
    }
}
