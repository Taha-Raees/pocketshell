package app.pocketshell.widget.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the pure task-list operations: every transition the card offers,
 * the section filters and the presentation order, deterministically.
 */
class TodoTasksTest {

    private fun task(
        id: String,
        text: String = "task $id",
        done: Boolean = false,
        archived: Boolean = false,
        starred: Boolean = false,
        createdAt: Long = id.hashCode().toLong(),
        listId: String = TodoList.DEFAULT_LIST_ID,
        priority: String = TodoTask.PRIORITY_NORMAL,
    ) = TodoTask(
        id = id,
        text = text,
        done = done,
        archived = archived,
        starred = starred,
        createdAt = createdAt,
        listId = listId,
        priority = priority,
    )

    // ---------------------------------------------------------------- add

    @Test
    fun `add enters at the front newest first`() {
        val tasks = listOf(task("a", createdAt = 100))
        val out = TodoTasks.add(tasks, "new one", id = "b", now = 200)
        assertEquals(listOf("b", "a"), out.map { it.id })
        assertEquals("new one", out.first().text)
    }

    @Test
    fun `add trims and ignores blank text`() {
        assertEquals(0, TodoTasks.add(emptyList(), "   ", id = "a", now = 1).size)
        assertEquals(0, TodoTasks.add(emptyList(), "\n\t", id = "a", now = 1).size)
        val out = TodoTasks.add(emptyList(), "  real  ", id = "a", now = 1)
        assertEquals("real", out.single().text)
    }

    @Test
    fun `add respects the store cap - oldest entry retires`() {
        // The store's convention is newest-first, so the seed list is too.
        var tasks = (TodoTasks.MAX_TASKS downTo 1).map { task("t$it", createdAt = it.toLong()) }
        tasks = TodoTasks.add(tasks, "newest", id = "new", now = 10_000)
        assertEquals(TodoTasks.MAX_TASKS, tasks.size)
        assertEquals("new", tasks.first().id)
        assertFalse(tasks.any { it.id == "t1" }) // the list tail retired
        assertTrue(tasks.any { it.id == "t2" })
    }

    // --------------------------------------------------- toggles + archive

    @Test
    fun `toggleDone flips exactly the named task`() {
        val tasks = listOf(task("a"), task("b", done = true))
        val out = TodoTasks.toggleDone(tasks, "a")
        assertTrue(out.first { it.id == "a" }.done)
        assertTrue(out.first { it.id == "b" }.done)
    }

    @Test
    fun `toggleDone is an uncomplete too`() {
        val out = TodoTasks.toggleDone(listOf(task("a", done = true)), "a")
        assertFalse(out.single().done)
    }

    @Test
    fun `toggleStar flips exactly the named task`() {
        val out = TodoTasks.toggleStar(listOf(task("a"), task("b")), "b")
        assertFalse(out.first { it.id == "a" }.starred)
        assertTrue(out.first { it.id == "b" }.starred)
    }

    @Test
    fun `archive hides the task and restore brings it back to its prior state`() {
        val doneTask = task("d", done = true)
        val archivedList = TodoTasks.setArchived(listOf(doneTask), "d", archived = true)
        assertTrue(archivedList.single().archived)
        assertTrue(archivedList.single().done) // the done flag survives archiving
        val restored = TodoTasks.setArchived(archivedList, "d", archived = false)
        assertFalse(restored.single().archived)
        assertTrue(restored.single().done) // RESTORE returns it to where it was
    }

    @Test
    fun `toggles on an unknown id change nothing`() {
        val tasks = listOf(task("a"))
        assertEquals(tasks, TodoTasks.toggleDone(tasks, "zz"))
        assertEquals(tasks, TodoTasks.toggleStar(tasks, "zz"))
        assertEquals(tasks, TodoTasks.setArchived(tasks, "zz", archived = true))
    }

    // ----------------------------------------------------- delete (M8.4.2)

    @Test
    fun `delete removes exactly the named task - archive keeps it`() {
        val tasks = listOf(task("a"), task("b", done = true), task("c", archived = true))
        val deleted = TodoTasks.delete(tasks, "b")
        assertEquals(listOf("a", "c"), deleted.map { it.id })
        // The soft path still exists beside it and keeps history.
        val archived = TodoTasks.setArchived(tasks, "b", archived = true)
        assertEquals(3, archived.size)
    }

    @Test
    fun `delete on an unknown id changes nothing`() {
        val tasks = listOf(task("a"))
        assertEquals(tasks, TodoTasks.delete(tasks, "zz"))
    }

    // -------------------------------------------------------------- reorder

    @Test
    fun `reorder persists the given order and keeps unmentioned tasks after`() {
        val tasks = listOf(task("a"), task("b"), task("c"))
        val out = TodoTasks.reorder(tasks, listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), out.map { it.id })
    }

    @Test
    fun `reorder skips unknown ids and handles duplicates`() {
        val tasks = listOf(task("a"), task("b"))
        val out = TodoTasks.reorder(tasks, listOf("b", "zz", "b"))
        assertEquals(listOf("b", "a"), out.map { it.id })
    }

    // ------------------------------------------------------------- sections

    @Test
    fun `sections partition the list - today done archived`() {
        val tasks = listOf(
            task("open", createdAt = 4),
            task("finished", done = true, createdAt = 3),
            task("history", done = true, archived = true, createdAt = 1),
            task("shelved", archived = true, createdAt = 2),
        )
        assertEquals(listOf("open"), TodoTasks.today(tasks).map { it.id })
        assertEquals(listOf("finished"), TodoTasks.done(tasks).map { it.id })
        assertEquals(listOf("shelved", "history"), TodoTasks.archived(tasks).map { it.id })
    }

    @Test
    fun `today sorts starred first then newest first`() {
        val tasks = listOf(
            task("old", createdAt = 1),
            task("star", createdAt = 2, starred = true),
            task("new", createdAt = 3),
        )
        assertEquals(listOf("star", "new", "old"), TodoTasks.today(tasks).map { it.id })
    }

    @Test
    fun `done and archived use the same deterministic order`() {
        val tasks = listOf(
            task("d1", done = true, createdAt = 1),
            task("d2", done = true, createdAt = 2, starred = true),
            task("d3", done = true, createdAt = 3),
        )
        assertEquals(listOf("d2", "d3", "d1"), TodoTasks.done(tasks).map { it.id })
        assertEquals(listOf("d2", "d3", "d1"), TodoTasks.archived(tasks.map { it.copy(archived = true, done = false) }).map { it.id })
    }

    // ------------------------------------------------ priority (M8.4.3)

    @Test
    fun `today sorts starred first then HIGH priority then newest first`() {
        val tasks = listOf(
            task("new", createdAt = 5),
            task("high", createdAt = 4, priority = TodoTask.PRIORITY_HIGH),
            task("star", createdAt = 3, starred = true),
            task("low", createdAt = 2, priority = TodoTask.PRIORITY_LOW),
            task("old", createdAt = 1),
        )
        assertEquals(
            listOf("star", "high", "new", "low", "old"),
            TodoTasks.today(tasks).map { it.id },
        )
    }

    @Test
    fun `only HIGH outranks recency - LOW sorts as normal`() {
        val tasks = listOf(
            task("old", createdAt = 1),
            task("freshLow", createdAt = 9, priority = TodoTask.PRIORITY_LOW),
        )
        assertEquals(listOf("freshLow", "old"), TodoTasks.today(tasks).map { it.id })
        val withHigh = listOf(
            task("old", createdAt = 1),
            task("freshLow", createdAt = 9, priority = TodoTask.PRIORITY_LOW),
            task("oldHigh", createdAt = 2, priority = TodoTask.PRIORITY_HIGH),
        )
        assertEquals(listOf("oldHigh", "freshLow", "old"), TodoTasks.today(withHigh).map { it.id })
    }

    @Test
    fun `cyclePriority walks HIGH to NORMAL to LOW to HIGH`() {
        var tasks = listOf(task("a", priority = TodoTask.PRIORITY_HIGH))
        tasks = TodoTasks.cyclePriority(tasks, "a")
        assertEquals(TodoTask.PRIORITY_NORMAL, tasks.single().priority)
        tasks = TodoTasks.cyclePriority(tasks, "a")
        assertEquals(TodoTask.PRIORITY_LOW, tasks.single().priority)
        tasks = TodoTasks.cyclePriority(tasks, "a")
        assertEquals(TodoTask.PRIORITY_HIGH, tasks.single().priority)
    }

    @Test
    fun `setPriority sanitizes and touches only the named task`() {
        val tasks = listOf(task("a", priority = TodoTask.PRIORITY_NORMAL), task("b"))
        val out = TodoTasks.setPriority(tasks, "a", TodoTask.PRIORITY_HIGH)
        assertEquals(TodoTask.PRIORITY_HIGH, out.first { it.id == "a" }.priority)
        assertEquals(TodoTask.PRIORITY_NORMAL, out.first { it.id == "b" }.priority)
        assertEquals(TodoTask.PRIORITY_NORMAL, TodoTasks.setPriority(tasks, "a", "urgent").first { it.id == "a" }.priority)
    }

    // -------------------------------------------------- text edit (M8.4.3)

    @Test
    fun `setText trims and replaces the text of exactly the named task`() {
        val tasks = listOf(task("a", text = "old"), task("b", text = "keep"))
        val out = TodoTasks.setText(tasks, "a", "  new  ")
        assertEquals("new", out.first { it.id == "a" }.text)
        assertEquals("keep", out.first { it.id == "b" }.text)
    }

    @Test
    fun `an emptied edit deletes the task`() {
        val tasks = listOf(task("a"), task("b"))
        assertEquals(listOf("b"), TodoTasks.setText(tasks, "a", "   ").map { it.id })
        assertEquals(emptyList<String>(), TodoTasks.setText(listOf(task("only")), "only", "").map { it.id })
    }

    @Test
    fun `setText on an unknown id with blank text changes nothing`() {
        val tasks = listOf(task("a"))
        assertEquals(tasks, TodoTasks.setText(tasks, "zz", ""))
    }

    // ------------------------------------------------- list scoping (M8.4.3)

    @Test
    fun `inList scopes the tasks to one list`() {
        val tasks = listOf(
            task("a", listId = TodoList.DEFAULT_LIST_ID),
            task("b", listId = "work"),
            task("c", listId = "work"),
        )
        assertEquals(listOf("a"), TodoTasks.inList(tasks, TodoList.DEFAULT_LIST_ID).map { it.id })
        assertEquals(listOf("b", "c"), TodoTasks.inList(tasks, "work").map { it.id })
        assertEquals(emptyList<String>(), TodoTasks.inList(tasks, "missing").map { it.id })
    }

    @Test
    fun `add lands in the named list and the per-list cap retires only its own oldest`() {
        val out = TodoTasks.add(emptyList(), "new", id = "n", now = 10, listId = "work")
        assertEquals("work", out.single().listId)

        val work = (TodoTasks.MAX_TASKS downTo 1).map {
            task("w$it", createdAt = it.toLong(), listId = "work")
        }
        val main = listOf(task("m1", createdAt = 1, listId = TodoList.DEFAULT_LIST_ID))
        val after = TodoTasks.add(work + main, "newest", id = "new", now = 10_000, listId = "work")
        assertEquals(TodoTasks.MAX_TASKS, after.count { it.listId == "work" })
        assertFalse(after.any { it.id == "w1" }) // work's own oldest retired
        assertTrue(after.any { it.id == "m1" }) // the other list is untouched
    }

    // ----------------------------------------------------- list ops (M8.4.3)

    @Test
    fun `addList appends a trimmed capped name and ignores blanks`() {
        var lists = listOf(TodoList.defaultList())
        lists = TodoTasks.addList(lists, "  Work  ", id = "work", now = 5)
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID, "work"), lists.map { it.id })
        assertEquals("Work", lists.last().name)
        assertEquals(5L, lists.last().createdAt)
        assertEquals(lists, TodoTasks.addList(lists, "   ", id = "blank", now = 6))
    }

    @Test
    fun `addList respects the cap of eight lists`() {
        var lists = listOf(TodoList.defaultList())
        (1..TodoList.MAX_LISTS).forEach { i ->
            lists = TodoTasks.addList(lists, "L$i", id = "l$i", now = i.toLong())
        }
        assertEquals(TodoList.MAX_LISTS, lists.size)
        val capped = TodoTasks.addList(lists, "one too many", id = "extra", now = 99)
        assertEquals(lists, capped)
    }

    @Test
    fun `renameList trims and caps - a blank name changes nothing`() {
        val lists = listOf(TodoList.defaultList(), TodoList("work", "Work", createdAt = 1))
        val renamed = TodoTasks.renameList(lists, "work", "  Deep ${"x".repeat(60)}  ")
        assertEquals(TodoList.MAX_NAME, renamed.first { it.id == "work" }.name.length)
        assertTrue(renamed.first { it.id == "work" }.name.startsWith("Deep"))
        assertEquals(lists, TodoTasks.renameList(lists, "work", "   "))
        assertEquals(lists, TodoTasks.renameList(lists, "missing", "whatever"))
    }

    @Test
    fun `removeList deletes a named list but never the default`() {
        val lists = listOf(TodoList.defaultList(), TodoList("work", "Work", createdAt = 1))
        val kept = TodoTasks.removeList(lists, "work")
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID), kept.map { it.id })
        assertEquals(lists, TodoTasks.removeList(lists, TodoList.DEFAULT_LIST_ID))
        assertEquals(lists, TodoTasks.removeList(lists, "missing"))
    }

    @Test
    fun `removing a list takes its tasks and only its tasks`() {
        val tasks = listOf(
            task("a", listId = "work"),
            task("b", listId = TodoList.DEFAULT_LIST_ID),
            task("c", listId = "work"),
        )
        assertEquals(listOf("b"), TodoTasks.removeTasksOfList(tasks, "work").map { it.id })
    }
}
