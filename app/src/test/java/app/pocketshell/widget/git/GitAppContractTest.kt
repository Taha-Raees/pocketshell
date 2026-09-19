package app.pocketshell.widget.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The GIT application's structural contract (source-reading pins in the
 * established HomeWidgetContractTest style — the JVM suite has no device
 * runner, so boundaries are pinned by reading what ships). Scope: the
 * widget/git sources only; shared files belong to the shared contract.
 *
 * Pinned here:
 *   1. THEME INDEPENDENCE: only the shared PocketShell tokens — no
 *      hardcoded colors, no theme-name literals, no canvas-tone text pair.
 *   2. THE GUEST EXEC PATH: probing goes ONLY through the sanctioned
 *      non-PTY launch spec + background runner; no own-hand process-table
 *      reading, no direct process spawning.
 *   3. READ-ONLY GIT: the probe script carries exactly the read commands;
 *      no state-changing git verb can ever reach the guest from this card.
 *   4. IN-CARD NAVIGATION: the application owns its detail state and its
 *      own back handler; actions ride the one WidgetNav seam.
 *   5. The probe/parser/presentation layer stays pure (no android deps).
 *   6. M8.4.2 STATE OWNERSHIP: the probe + screen state live in the
 *      process-scoped store, and the refresh control is a named icon.
 */
class GitAppContractTest {

    // ------------------------------------------------------------ helpers

    private fun gitSource(relative: String): String {
        val file = listOf(
            "app/src/main/java/app/pocketshell/widget/git/$relative",
            "../app/src/main/java/app/pocketshell/widget/git/$relative",
        ).map { File(it) }.firstOrNull { it.isFile }
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

    // -------------------------------------- 1. theme independence

    @Test
    fun `the Git application follows the shared theme - no private theme`() {
        val app = gitSource("GitApp.kt")
        val code = stripCommentsAndStrings(app)
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        assertFalse(
            "no canvas-tone text pair (the card is a chrome-surface citizen)",
            code.contains("onHero"),
        )
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (Aurora is ONE theme, never a dependency)",
            stringLiterals(app).any { it.contains("Aurora", ignoreCase = true) },
        )
        assertFalse(
            "no hardcoded colors in the probe layer either",
            Regex("""Color\(0x""").containsMatchIn(stripCommentsAndStrings(gitSource("GitProbe.kt"))),
        )
    }

    // -------------------------------------- 2. the guest exec path

    @Test
    fun `probing goes only through the sanctioned non-PTY guest exec`() {
        val app = stripCommentsAndStrings(gitSource("GitApp.kt"))
        assertTrue(
            "specs must come from the ONE launch builder",
            app.contains("RuntimeProcessLauncher.buildLaunchSpec"),
        )
        assertTrue(
            "the minimal device-proven profile is the probe's mount configuration",
            app.contains("GuestExecutionProfile.PACKAGE_OPERATION"),
        )
        assertTrue(
            "execution rides the shared background guest runner",
            app.contains("ProcessBuilderGuestCommandRunner"),
        )
        listOf("GitApp.kt", "GitProbe.kt", "GitStatusParser.kt").forEach { name ->
            val code = stripCommentsAndStrings(gitSource(name))
            val banned = listOf(
                "/proc", "cmdline", "Runtime.getRuntime", "java.lang.ProcessBuilder",
                "Runtime.exec", "libproot",
            )
            val found = banned.filter { code.contains(it) }
            assertTrue(
                "$name must stay on the guest exec path only; found: $found",
                found.isEmpty(),
            )
        }
    }

    // -------------------------------------- 3. read-only git

    /**
     * The probe script is a Kotlin RAW string (triple-quoted), which the
     * single-quote literal scanner cannot extract — locate it by its
     * markers instead.
     */
    private fun probeScriptLiteral(): String? {
        val source = gitSource("GitProbe.kt")
        val startMarker = "PROBE_SCRIPT = \"\"\""
        val start = source.indexOf(startMarker)
        if (start < 0) return null
        val bodyStart = start + startMarker.length
        val end = source.indexOf("\"\"\"", bodyStart)
        if (end < 0) return null
        return source.substring(bodyStart, end)
    }

    @Test
    fun `the probe script is strictly read-only git`() {
        val script = probeScriptLiteral()
        assertNotNull("the probe script must ship in GitProbe.kt", script)
        assertTrue("binary presence check", script!!.contains("command -v git"))
        assertTrue("version probe", script.contains("git --version"))
        assertTrue(
            "discovery is a shallow find for real checkouts",
            script.contains("*/.git/HEAD") && script.contains("-maxdepth"),
        )
        assertTrue(
            "status is the stable porcelain form (asked per discovered repo)",
            script.contains("status --porcelain=v1 -b"),
        )
        val banned = listOf(
            " add", " rm ", "commit", "push", "pull", "merge", "rebase",
            "checkout", "reset", "stash", "clean", "clone", "init", "config", " mv ",
        )
        val found = banned.filter { script.contains(it) }
        assertTrue("the card never mutates a repository; found: $found", found.isEmpty())
    }

    // -------------------------------------- 4. in-card navigation

    @Test
    fun `the application owns its detail state and its own back`() {
        val code = stripCommentsAndStrings(gitSource("GitApp.kt"))
        assertTrue(
            "detail + selection state must live in the process-scoped store (M8.4.2)",
            code.contains("stateStore.forApp"),
        )
        assertTrue(
            "back inside the card returns to the overview before leaving Home",
            code.contains("BackHandler(enabled = selected != null)"),
        )
        // Actions ride ONLY the one navigation seam.
        assertTrue(code.contains("nav.openTerminal()"))
        assertTrue(code.contains("nav.openLinuxShell()"))
        assertTrue(code.contains("nav.openDiagnostics()"))
        val banned = listOf("startActivity", "Intent(")
        val found = banned.filter { code.contains(it) }
        assertTrue("no second navigation mechanism; found: $found", found.isEmpty())
    }

    // -------------------------------------- 5. the probe layer stays pure

    @Test
    fun `the probe and parser layers have no android dependencies`() {
        listOf("GitProbe.kt", "GitStatusParser.kt", "GitPresentation.kt").forEach { name ->
            val code = stripCommentsAndStrings(gitSource(name))
            val banned = listOf("android.", "androidx", "Context", "Composable")
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must stay JVM-pure; found: $found", found.isEmpty())
        }
        assertEquals(
            "the registry id must match the application's spec id",
            GitApp.GIT_ID,
            GitApp.spec.id,
        )
    }

    // ------------------- 6. M8.4.2 state ownership + the named refresh

    @Test
    fun `state comes from the process-scoped store and refresh is a named icon`() {
        val code = stripCommentsAndStrings(gitSource("GitApp.kt"))
        assertTrue(
            "the probe and screen state must live in the shared store",
            code.contains("stateStore.forApp"),
        )
        val literals = stringLiterals(gitSource("GitApp.kt"))
        assertTrue(
            "the refresh icon needs its accessibility name for BOTH " +
                "contentDescription and onClickLabel",
            literals.count { it == "Refresh repositories" } >= 2,
        )
    }

    @Test
    fun `returning to the card renders the cache and gates any rescan`() {
        val code = stripCommentsAndStrings(gitSource("GitApp.kt"))
        assertTrue(
            "the resume edge must check for a cached Ready snapshot before scanning",
            code.contains("val hasCache = state.ui is GitUi.Ready"),
        )
        assertTrue(
            "the resume edge scan must be gated by the SAME staleness gate " +
                "as the tick loop (no guest exec on every re-entry)",
            Regex(
                """if \(!hasCache \|\| state\.probe\.shouldFullScan\(System\.currentTimeMillis\(\)\)\)""",
            ).containsMatchIn(code),
        )
    }
}
