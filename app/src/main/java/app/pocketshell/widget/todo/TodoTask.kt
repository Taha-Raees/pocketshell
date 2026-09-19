package app.pocketshell.widget.todo

import kotlinx.serialization.Serializable

/**
 * M8.4 — TODO Home Application: one task, persisted as plain JSON in the
 * app-local "todo_store" DataStore (see [TodoRepository]).
 *
 * Deliberately SMALL: a task is text plus flags and ordering keys. No due
 * dates, no reminders, no accounts — v1 answers exactly "what do I need
 * to do today?".
 *
 * M8.4.3 — the task belongs to a list ([listId]) and carries a coarse
 * [priority] ("H"/"N"/"L"). Both default on decode: a record missing them
 * (an older store) reads as the default list at normal priority — old
 * stores keep working, nothing is rewritten behind the user.
 *
 * Design for a future CLI/agent integration: the store is a plain JSON
 * array of these objects under one DataStore string key — a guest script
 * could serialize the same shape. Nothing about this model is
 * UI-private; no cloud, no sync, ever (v1 scope, structurally honest).
 *
 * `createdAt` (epoch millis) is NOT a displayed date — it is the stable
 * ordering key so the list never reshuffles under the user and the
 * sort is deterministic and testable. It is never rendered.
 */
@Serializable
data class TodoTask(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val archived: Boolean = false,
    val starred: Boolean = false,
    val createdAt: Long,
    val listId: String = TodoList.DEFAULT_LIST_ID,
    val priority: String = PRIORITY_NORMAL,
) {
    companion object {
        const val PRIORITY_HIGH = "H"
        const val PRIORITY_NORMAL = "N"
        const val PRIORITY_LOW = "L"

        /** The cycle the card's priority control walks: HIGH → NORMAL → LOW → HIGH. */
        fun cycled(priority: String): String = when (priority) {
            PRIORITY_HIGH -> PRIORITY_NORMAL
            PRIORITY_LOW -> PRIORITY_HIGH
            else -> PRIORITY_LOW
        }

        /** Only the three letters are valid; anything else decodes as NORMAL. */
        fun sanitized(priority: String): String =
            if (priority == PRIORITY_HIGH || priority == PRIORITY_NORMAL || priority == PRIORITY_LOW) {
                priority
            } else {
                PRIORITY_NORMAL
            }
    }
}

/**
 * M8.4.3 — one task list: a named bucket of [TodoTask]s. Stored as a
 * JSON array under the store's second key ("todo_lists") — the tasks
 * stay under their own key, so an old app reading a new store still
 * decodes the tasks it knows.
 *
 * The default list ("main", "My tasks") is never deletable and always
 * exists: [TodoStoreCodec] synthesizes it on read when absent, so a
 * migrated store (tasks with no "todo_lists" key at all) lands in it.
 */
@Serializable
data class TodoList(
    val id: String,
    val name: String,
    val createdAt: Long,
) {
    companion object {
        const val DEFAULT_LIST_ID = "main"
        const val DEFAULT_LIST_NAME = "My tasks"
        const val MAX_NAME = 40
        const val MAX_LISTS = 8

        /** The always-present first chip — synthesized, never seeded with fake content. */
        fun defaultList(): TodoList = TodoList(DEFAULT_LIST_ID, DEFAULT_LIST_NAME, createdAt = 0L)
    }
}
