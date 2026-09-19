package app.pocketshell.widget.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8.4 — the Notes application's structural contract, read from the
 * sources that ship (the established technique: the JVM suite has no
 * device runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. THEME: only the shared HomeTokens/TerminalTheme — no hardcoded
 *      colors, no theme-name literals, no canvas-tone text pair.
 *   2. IN-CARD NAVIGATION: rememberSaveable editor/draft/search state and
 *      the card's OWN BackHandler (back commits and returns to the list
 *      before it ever reaches the rest of Home).
 *   3. PERSISTENCE: the application's OWN notes_store DataStore file,
 *      writes on IO, the collected store flow as the single truth.
 *   4. NO POLLING: no tick, no delay loop — writes happen only when the
 *      user commits (structural, not a comment).
 *   5. NO EXECUTION / NO NETWORK: nothing spawned, nothing fetched —
 *      "no cloud, no accounts" is structural.
 *   6. PLAIN TEXT: the UI copy never promises markdown or rich rendering.
 */
class NotesAppContractTest {

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

    private fun notesDir(): File =
        File("app/src/main/java/app/pocketshell/widget/notes")
            .takeIf { it.isDirectory }
            ?: File("../app/src/main/java/app/pocketshell/widget/notes")

    private fun notesSources(): List<Pair<String, String>> {
        val dir = notesDir()
        assumeTrue("notes source dir not found on this runner", dir.isDirectory)
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.name to it.readText() }
            .toList()
    }

    private fun source(name: String): String =
        readSource("app/src/main/java/app/pocketshell/widget/notes/$name")

    // ------------------------------------------------------- 1. theme only

    @Test
    fun `the Notes application follows the shared theme - no private palette`() {
        val raw = source("NotesApp.kt")
        val code = stripCommentsAndStrings(raw)
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue(
            "the body must be monospace — TerminalTheme.mono",
            code.contains("TerminalTheme.mono"),
        )
        assertFalse("no canvas-tone text pair", code.contains("onHero"))
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (a theme is ONE option, never a dependency)",
            stringLiterals(raw).any { it.contains("Aurora", ignoreCase = true) },
        )
        notesSources().forEach { (name, text) ->
            assertFalse(
                "$name must not hardcode colors",
                Regex("""Color\(0x""").containsMatchIn(stripCommentsAndStrings(text)),
            )
        }
    }

    // ------------------------------------------------- 2. in-card navigation

    @Test
    fun `editor and draft state are saveable and the card owns its back`() {
        val code = stripCommentsAndStrings(source("NotesApp.kt"))
        assertTrue(
            "editor/draft/search state must be saveable (rotation, round-trips)",
            code.contains("rememberSaveable"),
        )
        assertTrue(
            "back inside the card commits and returns to the list before leaving Home",
            code.contains(
                "BackHandler(enabled = editorOpen && (editorId.isEmpty() || editorNote != null))",
            ),
        )
    }

    // ------------------------------------------------------- 3. persistence

    @Test
    fun `notes persist in the application's OWN DataStore file`() {
        val store = source("NotesStore.kt")
        assertTrue(
            "the store file must be the notes_store DataStore",
            store.contains("""preferencesDataStore(name = "notes_store")"""),
        )
        assertTrue(
            "the record is ONE JSON array under ONE key",
            store.contains("stringPreferencesKey") && store.contains("ListSerializer"),
        )
        assertTrue(
            "writes must happen on IO",
            stripCommentsAndStrings(store).contains("Dispatchers.IO"),
        )
        assertTrue(
            "the card reads the store's flow lifecycle-aware — the single truth",
            stripCommentsAndStrings(source("NotesApp.kt")).contains("collectAsStateWithLifecycle"),
        )
        assertTrue(
            "corrupt records decode honestly empty",
            stripCommentsAndStrings(source("NotesStore.kt")).contains("catch"),
        )
    }

    // -------------------------------------------------------- 4. no polling

    @Test
    fun `the application never polls - writes happen only on user action`() {
        notesSources().forEach { (name, text) ->
            val code = stripCommentsAndStrings(text)
            val banned = listOf("delay(", "while (true)", "while(true)", "REFRESH")
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must not poll; found: $found", found.isEmpty())
        }
    }

    // ------------------------------------------- 5. no execution, no network

    @Test
    fun `the application never executes anything`() {
        notesSources().forEach { (name, text) ->
            val code = stripCommentsAndStrings(text)
            val banned = listOf(
                "ProcessBuilder", "Runtime.getRuntime", ".exec(",
                "Runtime.exec", "libproot", "su ",
            )
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must never execute anything; found: $found", found.isEmpty())
        }
    }

    @Test
    fun `no cloud and no accounts - nothing addresses a network`() {
        notesSources().forEach { (name, text) ->
            val literals = stringLiterals(text)
            val found = literals.filter {
                it.contains("http", ignoreCase = true) || it.contains("ftp")
            }
            assertTrue("$name must not carry network endpoints; found: $found", found.isEmpty())
        }
    }

    // ------------------------------------------------------------ 6. honesty

    @Test
    fun `the card never claims markdown or rich rendering`() {
        val literals = stringLiterals(source("NotesApp.kt"))
        val banned = listOf("markdown", "rich text", "formatted", "syntax")
        val violations = literals.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue("over-promising UI copy: $violations", violations.isEmpty())
    }

    // -------------------------------------------------------------- spec

    @Test
    fun `the spec carries the registry identity`() {
        assertEquals("notes", NotesApp.spec.id)
        assertEquals("Notes", NotesApp.spec.name)
        assertTrue(NotesApp.spec.summary.isNotBlank())
    }
}
