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
 * companion each own one file). ONE file ("todo_store"), ONE key
 * ("todo_tasks"): a JSON array of [TodoTask].
 *
 * Writes go through DataStore's own IO executor (the `edit` transaction —
 * read-modify-write is atomic; no main-thread IO anywhere). Reads are a
 * cold Flow that emits on change only — no polling.
 *
 * Honesty, mirroring [app.pocketshell.widget.HomeAppIdCodec]: a corrupt
 * record decodes to EMPTY, never to invented tasks, and the first user
 * edit rewrites a clean file. There is no default seed — a task list
 * with fake entries would be a lie.
 */
class TodoRepository(private val context: Context) {

    private val tasksKey = stringPreferencesKey("todo_tasks")

    val tasks: Flow<List<TodoTask>> =
        context.todoStore.data.map { prefs -> TodoStoreCodec.decode(prefs[tasksKey]) }

    suspend fun add(text: String) = mutate { TodoTasks.add(it, text, newId(), now()) }

    suspend fun toggleDone(id: String) = mutate { TodoTasks.toggleDone(it, id) }

    suspend fun toggleStar(id: String) = mutate { TodoTasks.toggleStar(it, id) }

    suspend fun setArchived(id: String, archived: Boolean) =
        mutate { TodoTasks.setArchived(it, id, archived) }

    /** M8.4.2 — the explicit delete (archive remains the soft path). */
    suspend fun delete(id: String) = mutate { TodoTasks.delete(it, id) }

    suspend fun reorder(ids: List<String>) = mutate { TodoTasks.reorder(it, ids) }

    /** Atomic read-modify-write through DataStore's `edit` (IO executor). */
    private suspend fun mutate(op: (List<TodoTask>) -> List<TodoTask>) {
        context.todoStore.edit { prefs ->
            prefs[tasksKey] = TodoStoreCodec.encode(op(TodoStoreCodec.decode(prefs[tasksKey])))
        }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun now(): Long = System.currentTimeMillis()
    }
}

private val Context.todoStore by preferencesDataStore(name = "todo_store")

/**
 * The JSON codec for the task list — pure String in / List out (the
 * established codec discipline: sanitize, cap, honest degradation).
 */
object TodoStoreCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(tasks: List<TodoTask>): String =
        json.encodeToString(ListSerializer(TodoTask.serializer()), tasks.take(TodoTasks.MAX_TASKS))

    /**
     * Corrupt/absent → empty. Well-shaped but dirty records are
     * sanitized: blank texts dropped, texts trimmed, duplicate ids
     * deduped (first wins), capped. Unknown JSON keys are ignored, so an
     * older app reading a newer store degrades instead of crashing.
     */
    fun decode(raw: String?): List<TodoTask> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(TodoTask.serializer()), it)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
        return parsed
            .map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
            .distinctBy { it.id }
            .take(TodoTasks.MAX_TASKS)
    }
}
