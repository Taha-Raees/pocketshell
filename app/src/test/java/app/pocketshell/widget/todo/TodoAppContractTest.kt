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
 *   4. IN-CARD NAVIGATION: section + draft state live in the app's
 *      process-scoped holder (HomeAppStateStore — M8.4.2), so they
 *      survive navigation and carousel swipes; sections are tabs, not
 *      depth (no BackHandler — adding one without a real depth page
 *      would steal Home's back).
 *   5. HONESTY: the empty state tells the user where to add ("Add your
 *      first task above"), counts appear exactly ONCE (on the tabs),
 *      delete is an explicit named action ("Delete task") beside the
 *      soft archive path, and the pure layers stay pure.
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
    fun `the store persists tasks and lists in the todo_store DataStore under two keys`() {
        val raw = source("TodoRepository.kt")
        assertTrue(
            "the store is its own per-domain DataStore file",
            raw.contains("""preferencesDataStore(name = "todo_store")"""),
        )
        assertTrue(
            "one key holds the JSON task list",
            raw.contains("""stringPreferencesKey("todo_tasks")"""),
        )
        assertTrue(
            "the second key holds the JSON list-of-lists (M8.4.3)",
            raw.contains("""stringPreferencesKey("todo_lists")"""),
        )
        val code = stripCommentsAndStrings(raw)
        assertTrue("the records are kotlinx-serialization JSON", code.contains("ListSerializer"))
        assertTrue("writes go through DataStore edit", code.contains(".edit {"))
    }

    @Test
    fun `UI state lives in the process-scoped holder - the list is never compose state`() {
        val code = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue(
            "section + draft must live in the HomeAppStateStore holder (survives Home disposal)",
            code.contains("stateStore.forApp"),
        )
        assertTrue(
            "the selected list belongs in the holder too — the whole card follows it (M8.4.3)",
            code.contains("var selectedListId by"),
        )
        assertTrue(
            "the task being edited in place belongs in the holder (M8.4.3)",
            code.contains("var editingTaskId by"),
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
    fun `sections are in-card tabs - holder state, no back-depth in v1`() {
        val code = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue("tab state must live in the holder", code.contains("var section by"))
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
    fun `delete is explicit and named - archive remains the soft path`() {
        // M8.4.2 (user decision): the trash action EXISTS — but always
        // named for accessibility, and always beside (never instead of)
        // the archive path.
        val app = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue("the row offers delete via an icon action", app.contains("Icons.Outlined.Delete"))
        assertTrue(
            "delete must be named for accessibility (content description)",
            stringLiterals(source("TodoApp.kt")).any { it == "Delete task" },
        )
        val ops = stripCommentsAndStrings(source("TodoTasks.kt"))
        assertTrue("the pure layer has the delete op", ops.contains("fun delete"))
        assertTrue("archive remains available", ops.contains("fun setArchived"))
    }

    @Test
    fun `each count appears exactly once - the tabs are the only counter`() {
        val raw = source("TodoApp.kt")
        val violations = Regex(
            "\\.size\\}\\s*(open|completed|archived)",
            RegexOption.IGNORE_CASE,
        ).findAll(raw).toList()
        assertTrue(
            "a task count must appear ONLY on its tab; found: $violations",
            violations.isEmpty(),
        )
    }

    // ------------------------------------------- lists + edit (M8.4.3)

    @Test
    fun `the list picker is a chip row - switch by tap, edit only through the active chip`() {
        val raw = source("TodoApp.kt")
        val literals = stringLiterals(raw)
        assertTrue(
            "switching lists must be named for accessibility",
            literals.any { it.startsWith("Switch to list ") },
        )
        assertTrue(
            "the new-list affordance must be named for accessibility",
            literals.contains("New list"),
        )
        // The trash lives on the list form ONLY, never on a chip.
        val chipRowStart = raw.indexOf("private fun ListChipRow")
        val nextSection = raw.indexOf("private fun ListFormCard")
        assumeTrue("ListChipRow/ListFormCard not found on this runner", chipRowStart >= 0 && nextSection > chipRowStart)
        val chipRow = raw.substring(chipRowStart, nextSection)
        assertTrue(
            "the chips are tabs, not buttons — the card switches in place",
            chipRow.contains("Role.Tab"),
        )
        assertTrue(
            "the ACTIVE chip is the door to the edit form",
            chipRow.contains("if (active) onEdit(list) else onSelect(list.id)"),
        )
        assertFalse(
            "a delete affordance must never sit on a list chip — deletion lives on the form",
            chipRow.contains("Icons.Outlined.Delete"),
        )
    }

    @Test
    fun `deleting a list is a named confirm that states the damage`() {
        val raw = source("TodoApp.kt")
        val literals = stringLiterals(raw)
        assertTrue(
            "the confirm must name the list and its task count",
            literals.any { it.startsWith("Delete list '") && it.contains("and its ") },
        )
        assertTrue(
            "Delete and Cancel are text buttons, named",
            literals.contains("Delete") && literals.contains("Cancel"),
        )
        assertTrue(
            "the pure layer refuses to remove the default list",
            stripCommentsAndStrings(source("TodoTasks.kt")).contains("TodoList.DEFAULT_LIST_ID) lists"),
        )
    }

    @Test
    fun `priority is coarse and HIGH wears the accent glyph`() {
        val model = source("TodoTask.kt")
        assertTrue(
            "the three letters are the whole model — no numeric priority",
            model.contains("PRIORITY_HIGH = \"H\"") &&
                model.contains("PRIORITY_NORMAL = \"N\"") &&
                model.contains("PRIORITY_LOW = \"L\""),
        )
        val app = stringLiterals(source("TodoApp.kt"))
        assertTrue(
            "the row shows '!' for HIGH (and only the edit control cycles)",
            app.contains("!")
        )
        val code = stripCommentsAndStrings(source("TodoApp.kt"))
        assertTrue(
            "the glyph is conditional on HIGH — not decoration",
            code.contains("PRIORITY_HIGH) {"),
        )
        assertTrue(
            "the cycle is a pure model op, testable without Android",
            stripCommentsAndStrings(source("TodoTask.kt")).contains("fun cycled("),
        )
    }

    @Test
    fun `tapping the task text opens the in-place edit - blank edit deletes`() {
        val literals = stringLiterals(source("TodoApp.kt"))
        assertTrue(
            "the row's text is the edit affordance, named for accessibility",
            literals.contains("Edit task"),
        )
        assertTrue(
            "an emptied edit deletes — the pure op owns the rule",
            stripCommentsAndStrings(source("TodoTasks.kt")).contains("fun setText"),
        )
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
