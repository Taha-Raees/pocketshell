package app.pocketshell.widget.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the store round-trip: what the card writes, it reads back
 * unchanged, through every operation (add / complete / star / archive /
 * reorder) — and a corrupt record degrades honestly to EMPTY, never to
 * invented tasks.
 */
class TodoStoreCodecTest {

    private fun roundTrip(tasks: List<TodoTask>): List<TodoTask> =
        TodoStoreCodec.decode(TodoStoreCodec.encode(tasks))

    // ------------------------------------------------------------ round trip

    @Test
    fun `add survives the round trip`() {
        val afterAdd = TodoTasks.add(emptyList(), "write the report", id = "a", now = 1_000)
        val restored = roundTrip(afterAdd)
        assertEquals(afterAdd, restored)
        assertEquals("write the report", restored.single().text)
        assertEquals(1_000L, restored.single().createdAt)
    }

    @Test
    fun `complete star archive and reorder all persist`() {
        var tasks = TodoTasks.add(emptyList(), "first", id = "a", now = 1)
        tasks = TodoTasks.add(tasks, "second", id = "b", now = 2)

        tasks = TodoTasks.toggleDone(tasks, "b")
        // Stored order after the two adds is [b, a] (newest first).
        assertEquals(listOf(true, false), roundTrip(tasks).map { it.done })

        tasks = TodoTasks.toggleStar(tasks, "a")
        assertTrue(roundTrip(tasks).first { it.id == "a" }.starred)

        tasks = TodoTasks.setArchived(tasks, "a", archived = true)
        assertTrue(roundTrip(tasks).first { it.id == "a" }.archived)

        tasks = TodoTasks.reorder(tasks, listOf("b", "a"))
        assertEquals(listOf("b", "a"), roundTrip(tasks).map { it.id })

        // A full state after every operation, verbatim.
        assertEquals(tasks, roundTrip(tasks))
    }

    @Test
    fun `defaults survive - flags absent from old records read as false`() {
        val raw = """[{"id":"a","text":"legacy","createdAt":5}]"""
        val tasks = TodoStoreCodec.decode(raw)
        assertEquals(1, tasks.size)
        assertFalse(tasks[0].done)
        assertFalse(tasks[0].archived)
        assertFalse(tasks[0].starred)
    }

    // --------------------------------------------------------------- honesty

    @Test
    fun `absent record decodes to empty - no invented tasks`() {
        assertEquals(0, TodoStoreCodec.decode(null).size)
        assertEquals(0, TodoStoreCodec.decode("").size)
    }

    @Test
    fun `corrupt record decodes to empty - never a crash, never fake data`() {
        assertEquals(0, TodoStoreCodec.decode("not json at all").size)
        assertEquals(0, TodoStoreCodec.decode("""[{"id":}]""").size)
        assertEquals(0, TodoStoreCodec.decode("""{"not":"a list"}""").size)
    }

    @Test
    fun `dirty but well-shaped records are sanitized`() {
        val raw = """
            [
              {"id":"a","text":"  spaced  ","createdAt":1},
              {"id":"a","text":"duplicate id","createdAt":2},
              {"id":"b","text":"   ","createdAt":3},
              {"id":"c","text":"kept","createdAt":4,"unknownFutureField":true}
            ]
        """.trimIndent()
        val tasks = TodoStoreCodec.decode(raw)
        assertEquals(listOf("a", "c"), tasks.map { it.id }) // blank dropped, dup deduped (first wins)
        assertEquals("spaced", tasks[0].text) // trimmed
    }

    @Test
    fun `decode caps an oversized store`() {
        val oversized = (1..TodoTasks.MAX_TASKS + 50).map { TodoTask("t$it", "task $it", createdAt = it.toLong()) }
        assertEquals(TodoTasks.MAX_TASKS, TodoStoreCodec.decode(TodoStoreCodec.encode(oversized)).size)
    }
}
