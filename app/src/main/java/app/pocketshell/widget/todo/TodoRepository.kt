package app.pocketshell.widget.todo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * M8.4 — the TODO application's OWN DataStore file, per the established
 * per-domain pattern (home_widgets, settings, launchers, notifications,
 * companion each own one file). ONE file ("todo_store"), TWO keys:
 * "todo_tasks" (the JSON array of [TodoTask]) and — M8.4.3 —
 * "todo_lists" (the JSON array of [TodoList]).
 *
 * Writes go through DataStore's own IO executor (the `edit` transaction —
 * read-modify-write is atomic; no main-thread IO anywhere). Reads are a
 * cold Flow that emits on change only — no polling. Deleting a LIST
 * rewrites BOTH keys inside ONE `edit` transaction, so a list and its
 * tasks can never be observed half-deleted.
 *
 * Honesty, mirroring [app.pocketshell.widget.HomeAppIdCodec]: a corrupt
 * record decodes to EMPTY, never to invented tasks, and the first user
 * edit rewrites a clean file. There is no default seed — a task list
 * with fake entries would be a lie. The DEFAULT LIST is the one
 * exception, and it is structural, not content: it always exists so a
 * migrated store (tasks predating lists) has somewhere honest to land.
 */
class TodoRepository(private val context: Context) {

    private val tasksKey = stringPreferencesKey("todo_tasks")
    private val listsKey = stringPreferencesKey("todo_lists")

    /** The lists, default list always present, capped and name-cleaned. */
    val lists: Flow<List<TodoList>> =
        context.todoStore.data.map { prefs -> TodoStoreCodec.decodeLists(prefs[listsKey]) }

    /**
     * The tasks, decoded against the stored list ids: a task whose listId
     * names no known list belongs to the default list (the migration rule
     * that keeps pre-list stores readable forever).
     */
    val tasks: Flow<List<TodoTask>> =
        context.todoStore.data.map { prefs ->
            val known = TodoStoreCodec.decodeLists(prefs[listsKey]).mapTo(HashSet()) { it.id }
            TodoStoreCodec.decode(prefs[tasksKey], known)
        }

    suspend fun add(text: String, listId: String = TodoList.DEFAULT_LIST_ID) =
        mutate { TodoTasks.add(it, text, newId(), now(), listId) }

    suspend fun toggleDone(id: String) = mutate { TodoTasks.toggleDone(it, id) }

    suspend fun toggleStar(id: String) = mutate { TodoTasks.toggleStar(it, id) }

    suspend fun setArchived(id: String, archived: Boolean) =
        mutate { TodoTasks.setArchived(it, id, archived) }

    /** M8.4.2 — the explicit delete (archive remains the soft path). */
    suspend fun delete(id: String) = mutate { TodoTasks.delete(it, id) }

    /** M8.4.3 — save an edited label (a blank edit deletes the task). */
    suspend fun setText(id: String, text: String) = mutate { TodoTasks.setText(it, id, text) }

    /** M8.4.3 — HIGH → NORMAL → LOW → HIGH. */
    suspend fun cyclePriority(id: String) = mutate { TodoTasks.cyclePriority(it, id) }

    suspend fun reorder(ids: List<String>) = mutate { TodoTasks.reorder(it, ids) }

    // ------------------------------------------------- lists (M8.4.3)

    suspend fun addList(name: String) = mutateLists { TodoTasks.addList(it, name, newId(), now()) }

    suspend fun renameList(id: String, name: String) =
        mutateLists { TodoTasks.renameList(it, id, name) }

    /**
     * Delete a list AND its tasks in one `edit` transaction — both keys
     * rewrite together or not at all. The default list refuses (it is
     * never deletable; the UI never offers it).
     */
    suspend fun deleteList(id: String) {
        if (id == TodoList.DEFAULT_LIST_ID) return
        context.todoStore.edit { prefs ->
            val lists = TodoStoreCodec.decodeLists(prefs[listsKey])
            val known = lists.mapTo(HashSet()) { it.id }
            prefs[listsKey] = TodoStoreCodec.encodeLists(TodoTasks.removeList(lists, id))
            prefs[tasksKey] = TodoStoreCodec.encode(
                TodoTasks.removeTasksOfList(TodoStoreCodec.decode(prefs[tasksKey], known), id),
            )
        }
    }

    /** Atomic read-modify-write through DataStore's `edit` (IO executor). */
    private suspend fun mutate(op: (List<TodoTask>) -> List<TodoTask>) {
        context.todoStore.edit { prefs ->
            val known = TodoStoreCodec.decodeLists(prefs[listsKey]).mapTo(HashSet()) { it.id }
            prefs[tasksKey] = TodoStoreCodec.encode(op(TodoStoreCodec.decode(prefs[tasksKey], known)))
        }
    }

    private suspend fun mutateLists(op: (List<TodoList>) -> List<TodoList>) {
        context.todoStore.edit { prefs ->
            prefs[listsKey] = TodoStoreCodec.encodeLists(op(TodoStoreCodec.decodeLists(prefs[listsKey])))
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun now(): Long = System.currentTimeMillis()
    }
}

private val Context.todoStore by preferencesDataStore(name = "todo_store")

/**
 * The JSON codec for the two store records — pure String in / List out
 * (the established codec discipline: sanitize, cap, honest degradation).
 */
object TodoStoreCodec {

    private val json = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------- tasks

    fun encode(tasks: List<TodoTask>): String =
        json.encodeToString(ListSerializer(TodoTask.serializer()), capPerList(tasks))

    /**
     * Corrupt/absent → empty. Well-shaped but dirty records are
     * sanitized: blank texts dropped, texts trimmed, duplicate ids
     * deduped (first wins), priorities normalized to H/N/L, capped PER
     * LIST. A task whose listId names no known list (or none at all —
     * an old store, or the field missing entirely) belongs to the
     * DEFAULT list. Unknown JSON keys are ignored, so an older app
     * reading a newer store degrades instead of crashing.
     */
    fun decode(raw: String?, knownListIds: Set<String> = emptySet()): List<TodoTask> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(TodoTask.serializer()), it)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
        return parsed
            .map { task ->
                task.copy(
                    text = task.text.trim(),
                    listId = task.listId.trim(),
                    priority = TodoTask.sanitized(task.priority),
                )
            }
            .map { task ->
                if (task.listId.isEmpty() || task.listId !in knownListIds) {
                    task.copy(listId = TodoList.DEFAULT_LIST_ID)
                } else {
                    task
                }
            }
            .filter { it.text.isNotEmpty() }
            .distinctBy { it.id }
            .let(::capPerList)
    }

    /** The cap is per list: one full list never retires another list's tasks. */
    private fun capPerList(tasks: List<TodoTask>): List<TodoTask> =
        tasks.groupBy { it.listId }.flatMap { (_, group) -> group.take(TodoTasks.MAX_TASKS) }

    // ------------------------------------------------------------- lists

    fun encodeLists(lists: List<TodoList>): String =
        json.encodeToString(ListSerializer(TodoList.serializer()), lists)

    /**
     * Corrupt/absent → just the default list. Names are trimmed and
     * capped, blank ids/names dropped, duplicate ids deduped (first
     * wins), the whole set capped at [TodoList.MAX_LISTS] — and the
     * default list is ALWAYS present, synthesized at the front when the
     * record doesn't carry it.
     */
    fun decodeLists(raw: String?): List<TodoList> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(TodoList.serializer()), it)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
        val cleaned = parsed
            .map { it.copy(name = it.name.trim().take(TodoList.MAX_NAME)) }
            .filter { it.id.isNotBlank() && it.name.isNotEmpty() }
            .distinctBy { it.id }
        val withDefault =
            if (cleaned.any { it.id == TodoList.DEFAULT_LIST_ID }) cleaned
            else listOf(TodoList.defaultList()) + cleaned
        return withDefault.take(TodoList.MAX_LISTS)
    }
}
