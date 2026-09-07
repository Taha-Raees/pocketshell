package app.pocketshell.launchers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.1 P2.2 — the Home launcher-surface contract (regression pin).
 *
 * The user's P2.2 brief: bundled icon colors must follow the app's theme
 * scheme (light/dark), Companions scroll in ONE horizontal row, tools in
 * TWO, both with scroll dots, and the packages affordance must live in the
 * "Your tools" header (a Manage action opening the packages page) instead
 * of a floating footer link mid-screen.
 *
 * This project's JVM suite has no Robolectric/device instrumentation, so
 * Compose UI tests are not practical here (the honest visual gate is the
 * §44 device checklist). This pin is the structural guard that IS
 * practical, over the real sources (comments/strings stripped):
 *
 *   1. HomeScreen: CompanionsSection lays ONE x-scroll row; ToolsSection
 *      lays TWO rows via chunked(2) column stacks; both scroll through the
 *      shared LauncherScroller and its ScrollDots;
 *   2. the PackagesFooterLink footer is GONE and the tools header's action
 *      is the packages callback (onOpenPackages) — the companions header
 *      keeps its own launcher-settings Manage;
 *   3. LauncherTileIcon keys the bundled resolution on TerminalTheme.isLight
 *      — the theme-flip re-render that makes icon colors follow the theme.
 */
class HomeLauncherRowsTest {

    private fun readSource(relatives: List<String>): String {
        val file = relatives.map { File(it) }.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "source not found on this runner: ${relatives.first()}",
            file != null,
        )
        return file!!.readText()
    }

    private val homeCode: String by lazy {
        stripCommentsAndStrings(
            readSource(listOf(
                "app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt",
                "src/main/java/app/pocketshell/ui/home/HomeScreen.kt",
            )),
        )
    }

    private val tileCode: String by lazy {
        stripCommentsAndStrings(
            readSource(listOf(
                "app/src/main/java/app/pocketshell/launchers/LauncherTileIcon.kt",
                "src/main/java/app/pocketshell/launchers/LauncherTileIcon.kt",
            )),
        )
    }

    /** The brace block of the fun starting at [marker]'s offset. */
    private fun funBlock(code: String, marker: String): String {
        val start = code.indexOf(marker)
        assertTrue("$marker must exist", start >= 0)
        val open = code.indexOf('{', start)
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return code.substring(open, i + 1)
                }
            }
        }
        return code.substring(open)
    }

    @Test
    fun `companions lay one x-scrolled row through the shared scroller`() {
        val body = funBlock(homeCode, "fun CompanionsSection(")
        assertTrue(
            "CompanionsSection must render through LauncherScroller",
            body.contains("LauncherScroller("),
        )
        assertFalse(
            "the companions scroller must NOT wrap rows (chunked is the old grid)",
            body.contains("chunked("),
        )
    }

    @Test
    fun `tools lay two rows via column pairs through the shared scroller`() {
        val body = funBlock(homeCode, "fun ToolsSection(")
        assertTrue(
            "ToolsSection must render through LauncherScroller",
            body.contains("LauncherScroller("),
        )
        assertTrue(
            "ToolsSection must build TWO-row columns via chunked(2)",
            body.contains("chunked(2)"),
        )
    }

    @Test
    fun `the shared scroller x-scrolls and carries page dots`() {
        val body = funBlock(homeCode, "fun LauncherScroller(")
        assertTrue(
            "LauncherScroller must scroll horizontally",
            body.contains("horizontalScroll("),
        )
        assertTrue(
            "LauncherScroller must show ScrollDots for multi-page content",
            body.contains("ScrollDots("),
        )
        val dots = funBlock(homeCode, "fun ScrollDots(")
        assertTrue(
            "ScrollDots must derive the active dot from the scroll state",
            dots.contains("state.value") && dots.contains("state.maxValue"),
        )
        // both launcher sections ride the ONE scroller
        assertTrue(homeCode.contains("fun CompanionsSection("))
    }

    @Test
    fun `the packages footer link is gone and Manage opens packages from the tools header`() {
        assertFalse(
            "PackagesFooterLink is retired (the mid-screen packages button)",
            homeCode.contains("PackagesFooterLink"),
        )
        val tools = funBlock(homeCode, "fun ToolsSection(")
        assertTrue(
            "the tools header action must be the packages callback",
            tools.contains("onOpenPackages"),
        )
        assertFalse(
            "the tools header must NOT open launcher settings any more",
            tools.contains("onOpenLauncherSettings"),
        )
        val companions = funBlock(homeCode, "fun CompanionsSection(")
        assertTrue(
            "the companions header keeps its launcher-settings Manage",
            companions.contains("onManage"),
        )
    }

    @Test
    fun `the tile icon resolves the bundled variant from the current theme`() {
        assertTrue(
            "LauncherTileIcon must read TerminalTheme.isLight (theme-reactive)",
            tileCode.contains("TerminalTheme.isLight"),
        )
        assertTrue(
            "the bundled load must pass the theme flag through",
            tileCode.contains("loadBundledIcon(context, launcherId, light)"),
        )
        assertTrue(
            "the produce state must re-run on a theme flip (light is a key)",
            tileCode.contains("iconFile, light"),
        )
    }

    // -------------------------------------------------------------- helpers

    /**
     * Minimal structural stripper: block comments, line comments, and string
     * literal bodies (KDoc intentionally names the components under test —
     * it must not trip the scans).
     */
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
}
