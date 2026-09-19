package app.pocketshell.widget.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8.4 — the SYNC/BACKUP application's structural contract, read from the
 * sources that ship (the established technique: the JVM suite has no
 * device runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. THEME: only the shared HomeTokens/TerminalTheme — no hardcoded
 *      colors, no theme-name literals.
 *   2. IN-CARD NAVIGATION: rememberSaveable state + the card's own
 *      BackHandler; actions through the WidgetNav seam only.
 *   3. EXEC DISCIPLINE: the sanctioned guest-exec path only; user specs
 *      ride as argv/positional parameters — never spliced into a shell
 *      string; dry-run flags present; no PTY session spawn.
 *   4. SECRET SAFETY: no password/key literals anywhere in the package;
 *      the persisted model has no credential field.
 *   5. HONESTY: the UI vocabulary never claims user data is backed up,
 *      protected or verified — a profile is a record, not a backup; the
 *      card has no real-run control in v1.
 */
class SyncAppContractTest {

    // ------------------------------------------------------------ helpers

    private fun syncDir(): File =
        File("app/src/main/java/app/pocketshell/widget/sync")
            .takeIf { it.isDirectory }
            ?: File("../app/src/main/java/app/pocketshell/widget/sync")

    private fun syncSources(): List<Pair<String, String>> {
        val dir = syncDir()
        assumeTrue("sync source dir not found on this runner", dir.isDirectory)
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.name to it.readText() }
            .toList()
    }

    private fun source(name: String): String =
        syncSources().firstOrNull { it.first == name }
            ?.second
            ?: error("source not found on this runner: $name")

    /** Comments + string CONTENTS stripped — structural tokens only. */
    private fun stripCommentsAndStrings(code: String): String {
        val out = StringBuilder(code.length)
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '/' && i + 1 < code.length && code[i + 1] == '*' -> {
                    i = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }
                }
                c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                    while (i < code.length && code[i] != '\n') i++
                }
                c == '"' -> {
                    i++
                    while (i < code.length && code[i] != '"') {
                        if (code[i] == '\\') i++
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

    private fun stringLiterals(code: String): List<String> {
        val literals = mutableListOf<String>()
        var i = 0
        while (i < code.length) {
            if (code[i] == '"') {
                val start = i + 1
                var j = start
                while (j < code.length && code[j] != '"') {
                    if (code[j] == '\\') j++
                    j++
                }
                literals += code.substring(start, j.coerceAtMost(code.length))
                i = j + 1
            } else {
                i++
            }
        }
        return literals
    }

    // ------------------------------------------------------- 1. theme only

    @Test
    fun `the Sync application follows the shared theme - no private palette`() {
        val code = stripCommentsAndStrings(source("SyncApp.kt"))
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (a theme is ONE option, never a dependency)",
            stringLiterals(source("SyncApp.kt")).any { it.contains("Aurora", ignoreCase = true) },
        )
    }

    // ------------------------------------------------- 2. in-card navigation

    @Test
    fun `detail and form state are saveable and the card owns its back`() {
        val code = stripCommentsAndStrings(source("SyncApp.kt"))
        assertTrue(
            "navigation state must be saveable (rotation, round-trips)",
            code.contains("rememberSaveable"),
        )
        assertTrue(
            "back inside the card returns to the overview before leaving Home",
            Regex("""BackHandler\(enabled = """).containsMatchIn(code),
        )
        assertTrue("the terminal is where real runs happen", code.contains("nav.openTerminal()"))
        assertTrue("the guest is where sync tools live", code.contains("nav.openLinuxShell()"))
    }

    // ---------------------------------------------------- 3. exec discipline

    @Test
    fun `the only exec path is the sanctioned non-PTY guest runner`() {
        val all = syncSources().joinToString("\n") { it.second }
        val code = stripCommentsAndStrings(all)
        assertTrue(
            "guest commands go through the sanctioned launcher",
            code.contains("RuntimeProcessLauncher.buildLaunchSpec"),
        )
        assertTrue(
            "guest commands run through the background guest runner",
            code.contains("ProcessBuilderGuestCommandRunner"),
        )
        val banned = listOf(
            "Runtime.getRuntime", "libproot", "su ", "/dev/ptmx", "openpty", "ptySession",
        )
        val found = banned.filter { code.contains(it) }
        assertTrue("no second exec surface; found: $found", found.isEmpty())
    }

    @Test
    fun `user specs ride as argv - never spliced into a shell string`() {
        // These pins read the RAW source: the string-literal contents are
        // the point here (the stripper would blank them).
        val raw = source("SyncProbe.kt")
        // The script is a fixed constant passed with an argv placeholder arg.
        assertTrue(raw.contains("\"/bin/sh\""))
        assertTrue(raw.contains("\"-c\""))
        assertTrue(raw.contains("PROBE_SCRIPT"))
        // Positional parameters in the raw template (${D}1 / ${D}2 in source).
        assertTrue("specs must arrive as \$1/\$2 positional parameters", raw.contains("{D}1"))
        assertTrue(raw.contains("{D}2"))
        // No Kotlin string-building of shell command lines from user data:
        // no quote-adjacent + concatenation anywhere in the package.
        syncSources().forEach { (name, text) ->
            val violations = Regex("""["']\s*\+\s*\w+\s*\+""").findAll(text).toList()
            assertTrue(
                "$name must not build shell strings by concatenation; found: " +
                    violations.map { it.value },
                violations.isEmpty(),
            )
        }
    }

    @Test
    fun `the dry-run flags are structural - v1 cannot perform a real run`() {
        val raw = source("SyncProbe.kt")
        assertTrue("rsync previews use -n", raw.contains("\"-n\""))
        assertTrue("rsync previews itemize", raw.contains("\"--itemize-changes\""))
        assertTrue("rclone previews dry-run", raw.contains("\"--dry-run\""))
        assertTrue("rclone previews report to stdout", raw.contains("\"--combined\""))
        // And the UI names the action honestly.
        val app = source("SyncApp.kt")
        assertTrue(app.contains("DRY RUN"))
        assertFalse(
            "no Run-now control exists in v1",
            app.contains("\"RUN NOW\""),
        )
    }

    // ------------------------------------------------------ 4. secret safety

    @Test
    fun `no source carries password or key literals`() {
        syncSources().forEach { (name, text) ->
            val banned = listOf(
                "password", "passwd", "secret", "api_key", "apikey",
                "token", "private_key", "id_rsa", "id_ed25519", "id_ecdsa",
            )
            val found = stringLiterals(text)
                .flatMap { literal -> banned.filter { literal.lowercase().contains(it) } }
            assertTrue("$name must never carry credential literals; found: $found", found.isEmpty())
        }
    }

    @Test
    fun `the persisted model has no credential field to fill`() {
        val model = stripCommentsAndStrings(source("SyncProfile.kt"))
        assertTrue(model.contains("val source"))
        assertTrue(model.contains("val destination"))
        val banned = listOf("password", "secret", "keyPath", "credential", "token")
        val found = banned.filter { model.lowercase().contains(it) }
        assertTrue("the profile model must have no credential surface; found: $found", found.isEmpty())
    }

    // ------------------------------------------------------------ 5. honesty

    @Test
    fun `the UI never claims user data is backed up or safe`() {
        val banned = listOf(
            "backed up", "backup complete", "is safe", "protected",
            "verified backup", "backup succeeded",
        )
        syncSources().forEach { (name, text) ->
            val literals = stringLiterals(text).map { it.lowercase() }
            val found = literals.flatMap { literal -> banned.filter { literal.contains(it) } }
            assertTrue("$name invented a backup claim: $found", found.isEmpty())
        }
    }

    @Test
    fun `the run fields default to never-run and the model documents why`() {
        val p = SyncProfile("id", SyncBackend.RSYNC, "/a", "/b", createdAtMs = 1L)
        assertNull(p.lastRunMs)
        assertNull(p.lastRunSummary)
    }

    // -------------------------------------------------------------- spec

    @Test
    fun `the spec carries the registry identity`() {
        assertEquals(SyncApp.SYNC_ID, SyncApp.spec.id)
        assertEquals("sync", SyncApp.spec.id)
        assertEquals("Sync", SyncApp.spec.name)
        assertTrue(SyncApp.spec.summary.isNotBlank())
    }
}
