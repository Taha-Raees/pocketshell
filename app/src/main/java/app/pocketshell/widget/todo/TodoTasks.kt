package app.pocketshell.widget.todo

/**
 * M8.4 — the PURE task-list operations behind the TODO Home Application.
 * Every state transition the card offers is a function `List → List`
 * here, so the DataStore layer ([TodoRepository]) stays a thin codec
 * shell and every rule is JVM-testable without Android.
 *
 * Semantics (v1, deliberately small):
 *   - ADD: newest task enters at the FRONT of the stored list; the
 *     presentation sort decides where it appears.
 *   - COMPLETE: `done` flips; the task moves to the DONE section.
 *   - ARCHIVE (the chosen alternative to delete — history survives):
 *     `archived` hides the task from Today and Done. There is NO hard
 *     delete in v1; ARCHIVED is the store's history, capped like the
 *     rest of the list. RESTORE returns a task to the state it was
 *     archived from (`archived = false`; the `done` flag is untouched).
 *   - STAR: the priority flag; starred tasks sort first within Today.
 *   - REORDER: the store-level primitive (order persisted in the JSON
 *     list); the v1 card exposes priority through STAR, not dragging.
 *
 * No timestamps are read (no dates in v1) — `createdAt` arrives as a
 * parameter so tests stay deterministic.
 */
object TodoTasks {

    /** Sanity cap: a reasonable lifetime for a local scratch list. */
    const val MAX_TASKS = 200

    /** A non-blank task enters at the front (newest first); blank is ignored. */
    fun add(tasks: List<TodoTask>, text: String, id: String, now: Long): List<TodoTask> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return tasks
        return (listOf(TodoTask(id = id, text = trimmed, createdAt = now)) + tasks)
            .take(MAX_TASKS)
    }

    fun toggleDone(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(done = !it.done) else it }

    fun toggleStar(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(starred = !it.starred) else it }

    fun setArchived(tasks: List<TodoTask>, id: String, archived: Boolean): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(archived = archived) else it }

    /**
     * The mentioned ids first, in the given order (unknown ids skipped,
     * duplicate ids collapsed); unmentioned tasks keep their existing
     * order after them.
     */
    fun reorder(tasks: List<TodoTask>, ids: List<String>): List<TodoTask> {
        val byId = tasks.associateBy { it.id }
        val mentioned = ids.distinct().mapNotNull(byId::get)
        val mentionedIds = mentioned.mapTo(HashSet()) { it.id }
        return mentioned + tasks.filter { it.id !in mentionedIds }
    }

    // ------------------------------------------------- sections (v1 views)

    /** TODAY — the default view: open, not archived. */
    fun today(tasks: List<TodoTask>): List<TodoTask> =
        sorted(tasks.filter { !it.done && !it.archived })

    /** DONE — completed, not yet archived. */
    fun done(tasks: List<TodoTask>): List<TodoTask> =
        sorted(tasks.filter { it.done && !it.archived })

    /** ARCHIVED — history; survives until the list cap retires it. */
    fun archived(tasks: List<TodoTask>): List<TodoTask> =
        sorted(tasks.filter { it.archived })

    /** The one presentation order: starred first, then newest first. */
    fun sorted(tasks: List<TodoTask>): List<TodoTask> =
        tasks.sortedWith(
            compareByDescending<TodoTask> { it.starred }.thenByDescending { it.createdAt },
        )
}
