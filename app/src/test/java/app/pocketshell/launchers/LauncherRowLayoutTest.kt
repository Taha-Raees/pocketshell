package app.pocketshell.launchers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M7.1 P2 — the launcher-row layout contract (PART A/J regression pin).
 *
 * The device screenshot that opened this phase: a settings row whose title
 * collapsed to one character per line ("G/i/t/H/u/b") with a stretched
 * page-level button floating inside it. ROOT CAUSE: MidnightQuietButton /
 * MidnightFilledButton hard-fill their width from the inside
 * (`Box(Modifier.fillMaxWidth())` in MidnightPage.kt) — they are PAGE-level
 * components. Inside an unweighted Row slot such a button consumes the whole
 * row, starving the weighted text column to zero width, and Compose breaks
 * the single word character-by-character.
 *
 * This project's JVM suite has no Robolectric/device instrumentation, so
 * Compose UI tests are not practical here (disclosed honestly — the visual
 * gate is the §42 device checklist). This pin is the structural guard that
 * IS practical:
 *
 *   1. every built-in settings row renders through ONE shared composable
 *      whose text column carries Modifier.weight(1f);
 *   2. no page-level expanding button ever appears inside a Row scope in
 *      the launcher settings screen — row actions are intrinsically-sized
 *      text actions and switches, so the weighted text column can never be
 *      starved again.
 */
class LauncherRowLayoutTest {

    private val source: String by lazy {
        val file = listOf(
            File("app/src/main/java/app/pocketshell/ui/settings/LauncherSettingsScreen.kt"),
            File("src/main/java/app/pocketshell/ui/settings/LauncherSettingsScreen.kt"),
        ).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "launcher settings source not found on this runner",
            file != null,
        )
        file!!.readText()
    }

    /** Source with comments and string literals stripped (structure only). */
    private val code: String by lazy { stripCommentsAndStrings(source) }

    @Test
    fun `the shared row composable weights its text column`() {
        // The ONE row builder must give the text column the remaining width.
        val marker = "fun LauncherSettingRow("
        val start = code.indexOf(marker)
        assertTrue("LauncherSettingRow must exist and be used (the ONE row shape)", start >= 0)
        val body = braceBlock(code, start)
        assertTrue(
            "LauncherSettingRow's text column must carry Modifier.weight(1f) " +
                "(the screenshot bug: an unweighted text column next to an " +
                "expanding control collapses one-char-per-line)",
            body.contains("Column(Modifier.weight(1f))"),
        )
    }

    @Test
    fun `no page-level expanding button sits inside any Row scope`() {
        // Scan every Row( call site; its brace block must not build a
        // fillMaxWidth button component.
        var rows = 0
        var index = code.indexOf("Row(")
        while (index >= 0) {
            val isCall = index == 0 || !code[index - 1].isLetterOrDigit()
            if (isCall) {
                rows++
                val block = braceBlock(code, index)
                for (forbidden in listOf("MidnightQuietButton(", "MidnightFilledButton(")) {
                    assertFalse(
                        "$forbidden is a page-level fillMaxWidth component and must " +
                            "never sit inside a Row scope (M7.1 P2 screenshot root cause) " +
                            "— row slots take RowTextAction/MidnightSwitch only",
                        block.contains(forbidden),
                    )
                }
            }
            index = code.indexOf("Row(", index + 1)
        }
        assertTrue("expected rows to scan in the settings screen", rows > 0)
    }

    // -------------------------------------------------------------- helpers

    /** From the '(' after [start]'s call name, the extent of its brace block. */
    private fun braceBlock(code: String, callStart: Int): String {
        val open = code.indexOf('{', callStart)
        assertTrue("no brace block after offset $callStart", open >= 0)
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

    /**
     * Minimal structural stripper: block comments, line comments, and string
     * literal bodies (the KDoc above intentionally names the forbidden
     * components — it must not trip the scan).
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
