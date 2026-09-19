package app.pocketshell.widget.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8.4 — the TODO application's structural contract, read from the
 * sources that ship (the established technique: the JVM suite has no
 * device runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. THEME: only the shared HomeTokens/TerminalTheme — no hardcoded
 *      colors, no theme-name literals.
 *   2. PERSISTENCE: the task list lives in the application's OWN
 *      DataStore ("todo_store", one key "todo_tasks", JSON via
 *      kotlinx-serialization) — not in preferences, not in memory.
 *   3. LOCAL-FIRST: no execution surface and no network anywhere in the
 *      package — the card is app-local by construction.
 *   4. IN-CARD NAVIGATION: section state is saveable; sections are tabs,
 *      not depth (no BackHandler is expected in v1 — adding one without
 *      a real depth page would steal Home's back).
 *   5. HONESTY: the empty state tells the user where to add ("Add your
 *      first task above"), the card never deletes (ARCHIVE is the
 *      delete), and the pure layers stay pure.
 */
class TodoAppContractTest {

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

    private fun todoDir(): File =
        File("app/src/main/java/app/pocketshell/widget/todo")
            .takeIf { it.isDirectory }
            ?: File("../app/src/main/java/app/pocketshell/widget/todo")

    private fun todoSources(): List<Pair<String, String>> {
        val dir = todoDir()
        assumeTrue("todo source dir not found on this runner", dir.isDirectory)
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.name to it.readText() }
            .toList()
    }

    private fun source(name: String): String =
        readSource("app/src/main/java/app/pocketshell/widget/todo/$name")

    // ------------------------------------------------------- 1. theme only

    @Test
    fun `the TODO application follows the shared theme - no private palette`() {
        val raw = source("TodoApp.kt")
        val code = stripCommentsAndStrings(raw)
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (a theme is ONE option, never a dependency)",
            stringLiterals(raw).any { it.contains("Aurora", ignoreCase = true) },
        )
    }

    // --------------------------------------------------- 2. own DataStore

    @Test
    fun `the task list persists in the todo_store DataStore under one key`() {
        val raw = source("TodoRepository.kt")
        assertTrue(
            "the store is its own per-domain DataStore file",
            raw.contains("""preferencesDataStore(name = "todo_store")"""),
        )
        assertTrue(
            "one key holds the JSON task list",
            raw.contains("""stringPreferencesKey("todo_tasks")"""),
        )
        val code = stripCommentsAndStrings(raw)
        assertTrue("the list is kotlinx-serialization JSON", code.contains("ListSerializer"))
        assertTrue("writes go through DataStore edit", code.contains(".edit {"))
    }

    @Test
    fun `UI state is saveable but the task list is never compose state`() {
        val code = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue(
            "section + draft survive rotation and carousel swipes",
            code.contains("rememberSaveable"),
        )
        assertFalse(
            "the task list must come from the DataStore flow, not a remember { mutableStateListOf }",
            code.contains("mutableStateListOf"),
        )
    }

    // ------------------------------------------------ 3. local-first purity

    @Test
    fun `the application never executes or reaches the network - local by construction`() {
        todoSources().forEach { (name, text) ->
            val code = stripCommentsAndStrings(text)
            val banned = listOf(
                "ProcessBuilder", "Runtime.getRuntime", ".exec(",
                "HttpURLConnection", "java.net.Socket", "okhttp",
                "URL(", "Firebase", "WorkManager",
            )
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must stay local (no execution, no network, no cloud); found: $found", found.isEmpty())
        }
    }

    @Test
    fun `the pure layers never touch Android`() {
        listOf("TodoTask.kt", "TodoTasks.kt").forEach { name ->
            val code = stripCommentsAndStrings(source(name))
            val banned = listOf("android.", "androidx.", "Context", "File", "readText")
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must stay pure (List in, List out); found: $found", found.isEmpty())
        }
    }

    // -------------------------------------------- 4. in-card navigation

    @Test
    fun `sections are in-card tabs - saveable state, no back-depth in v1`() {
        val code = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue("tab state must be saveable", code.contains("rememberSaveable"))
        assertFalse(
            "tabs are not depth — a BackHandler here would steal Home's back; add one only with a real depth page",
            code.contains("BackHandler"),
        )
    }

    // ---------------------------------------------------------- 5. honesty

    @Test
    fun `the empty state tells the user where to add`() {
        val literals = stringLiterals(source("TodoApp.kt"))
        assertTrue(
            "the first-run hint must exist",
            literals.any { it.contains("Add your first task above") },
        )
    }

    @Test
    fun `the card archives - it never deletes`() {
        todoSources().forEach { (name, text) ->
            val literals = stringLiterals(text)
            val violations = literals.filter { it.lowercase().contains("delete") }
            assertTrue("$name must not offer delete (ARCHIVE is the delete); found: $violations", violations.isEmpty())
        }
    }

    // -------------------------------------------------------------- spec

    @Test
    fun `the spec carries the application identity`() {
        assertEquals("todo", TodoApp.spec.id)
        assertEquals("Todo", TodoApp.spec.name)
        assertTrue(TodoApp.spec.summary.isNotBlank())
        assertEquals(TodoApp.ID, TodoApp.spec.id)
    }
}
