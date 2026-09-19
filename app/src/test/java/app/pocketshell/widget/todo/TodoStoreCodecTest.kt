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

    // ------------------------------------------- list migration (M8.4.3)

    @Test
    fun `a task missing listId belongs to the default list - old stores keep working`() {
        val raw = """[{"id":"a","text":"legacy","createdAt":5}]"""
        val tasks = TodoStoreCodec.decode(raw, setOf(TodoList.DEFAULT_LIST_ID))
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID), tasks.map { it.listId })
    }

    @Test
    fun `a task with an unknown listId belongs to the default list`() {
        val raw = """[{"id":"a","text":"home","createdAt":1,"listId":"main"},{"id":"b","text":"ghost","createdAt":2,"listId":"gone"}]"""
        val tasks = TodoStoreCodec.decode(raw, knownListIds = setOf("main", "work"))
        assertEquals(listOf("main", "main"), tasks.map { it.listId })
    }

    @Test
    fun `an unknown listId is also migrated when no known ids are supplied`() {
        val raw = """[{"id":"a","text":"ghost","createdAt":1,"listId":"gone"}]"""
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID), TodoStoreCodec.decode(raw).map { it.listId })
    }

    @Test
    fun `an unknown priority decodes as NORMAL - only H N L are valid`() {
        val raw = """[
            {"id":"a","text":"high","createdAt":1,"priority":"H"},
            {"id":"b","text":"weird","createdAt":2,"priority":"urgent"},
            {"id":"c","text":"low","createdAt":3,"priority":"L"}
        ]""".trimIndent()
        val tasks = TodoStoreCodec.decode(raw)
        assertEquals(
            listOf(TodoTask.PRIORITY_HIGH, TodoTask.PRIORITY_NORMAL, TodoTask.PRIORITY_LOW),
            tasks.map { it.priority },
        )
    }

    @Test
    fun `lists round-trip field for field`() {
        val lists = listOf(
            TodoList.defaultList(),
            TodoList(id = "work", name = "Work", createdAt = 7),
        )
        assertEquals(lists, TodoStoreCodec.decodeLists(TodoStoreCodec.encodeLists(lists)))
    }

    @Test
    fun `absent or corrupt list record decodes to just the default list`() {
        val lists = TodoStoreCodec.decodeLists(null)
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID), lists.map { it.id })
        assertEquals(TodoList.DEFAULT_LIST_NAME, lists.single().name)
        assertEquals(lists, TodoStoreCodec.decodeLists("not json"))
    }

    @Test
    fun `the default list is synthesized at the front when the record lacks it`() {
        val raw = """[{"id":"work","name":"Work","createdAt":1}]"""
        val lists = TodoStoreCodec.decodeLists(raw)
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID, "work"), lists.map { it.id })
    }

    @Test
    fun `list names are trimmed and capped - blank ids and names are dropped`() {
        val raw = """
            [
              {"id":"a","name":"  spaced  ","createdAt":1},
              {"id":"b","name":"${"x".repeat(99)}","createdAt":2},
              {"id":"","name":"no id","createdAt":3},
              {"id":"c","name":"   ","createdAt":4},
              {"id":"a","name":"duplicate","createdAt":5}
            ]
        """.trimIndent()
        val lists = TodoStoreCodec.decodeLists(raw)
        assertEquals(listOf(TodoList.DEFAULT_LIST_ID, "a", "b"), lists.map { it.id })
        assertEquals("spaced", lists.first { it.id == "a" }.name) // duplicate collapsed, first wins
        assertEquals(TodoList.MAX_NAME, lists.first { it.id == "b" }.name.length)
    }

    @Test
    fun `the list cap holds and never evicts the default list`() {
        val custom = (1..TodoList.MAX_LISTS).map { TodoList("l$it", "L$it", createdAt = it.toLong()) }
        val decoded = TodoStoreCodec.decodeLists(TodoStoreCodec.encodeLists(custom))
        assertEquals(TodoList.MAX_LISTS, decoded.size)
        assertEquals(TodoList.DEFAULT_LIST_ID, decoded.first().id)
    }

    @Test
    fun `the task cap is per list - one full list never retires another's tasks`() {
        val listA = (1..TodoTasks.MAX_TASKS + 10).map {
            TodoTask("a$it", "task a$it", createdAt = it.toLong(), listId = "a")
        }
        val listB = listOf(TodoTask("b1", "task b1", createdAt = 1L, listId = "b"))
        val decoded = TodoStoreCodec.decode(TodoStoreCodec.encode(listA + listB), setOf("a", "b"))
        assertEquals(TodoTasks.MAX_TASKS, decoded.count { it.listId == "a" })
        assertEquals(1, decoded.count { it.listId == "b" })
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
