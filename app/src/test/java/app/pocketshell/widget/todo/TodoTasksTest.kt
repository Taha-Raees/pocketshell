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
    ) = TodoTask(id = id, text = text, done = done, archived = archived, starred = starred, createdAt = createdAt)

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
}
