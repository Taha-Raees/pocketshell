package app.pocketshell.widget.todo

/**
 * M8.4 — the PURE task-list operations behind the TODO Home Application.
 * Every state transition the card offers is a function `List → List`
 * here, so the DataStore layer ([TodoRepository]) stays a thin codec
 * shell and every rule is JVM-testable without Android.
 *
 * Semantics:
 *   - ADD: newest task enters at the FRONT of the stored list; the
 *     presentation sort decides where it appears. The cap is PER LIST:
 *     a full list retires its own oldest task, other lists are untouched.
 *   - COMPLETE: `done` flips; the task moves to the DONE section.
 *   - ARCHIVE first, DELETE explicit (M8.4.2, user decision): `archived`
 *     hides the task from Today and Done; hard delete is its own op and
 *     removes the task for good. RESTORE returns a task to the state it
 *     was archived from.
 *   - STAR: the priority flag; starred tasks sort first within Today.
 *   - PRIORITY (M8.4.3): coarse H/N/L; only HIGH is surfaced as a glyph —
 *     the cycle walks HIGH → NORMAL → LOW → HIGH.
 *   - TEXT EDIT (M8.4.3): saving an EMPTY text deletes the task — an
 *     emptied label is a removal, stated in the test suite.
 *   - LISTS (M8.4.3): tasks are scoped by `listId`; the pure layer also
 *     owns the list mutations (add / rename / remove) and the rule that
 *     the default list can never be removed.
 *   - REORDER: the store-level primitive (order persisted in the JSON
 *     list); the card exposes priority through STAR, not dragging.
 *
 * No timestamps are read (no dates in v1) — `createdAt` arrives as a
 * parameter so tests stay deterministic.
 */
object TodoTasks {

    /** Sanity cap: a reasonable lifetime for one list's local scratch tasks. */
    const val MAX_TASKS = 200

    /** A non-blank task enters at the front (newest first); blank is ignored. */
    fun add(
        tasks: List<TodoTask>,
        text: String,
        id: String,
        now: Long,
        listId: String = TodoList.DEFAULT_LIST_ID,
        priority: String = TodoTask.PRIORITY_NORMAL,
    ): List<TodoTask> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return tasks
        val withNew = listOf(
            TodoTask(id = id, text = trimmed, createdAt = now, listId = listId, priority = priority),
        ) + tasks
        val sameList = withNew.filter { it.listId == listId }
        if (sameList.size <= MAX_TASKS) return withNew
        // The list's own oldest entry retires; other lists are untouched.
        val retired = sameList.filter { it.id != id }.minByOrNull { it.createdAt }
        return if (retired == null) withNew else withNew.filterNot { it.id == retired.id }
    }

    fun toggleDone(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(done = !it.done) else it }

    fun toggleStar(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(starred = !it.starred) else it }

    fun setArchived(tasks: List<TodoTask>, id: String, archived: Boolean): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(archived = archived) else it }

    /**
     * M8.4.2 — HARD delete, the user's explicit removal (archive stays the
     * soft path; delete removes the task from the store for good).
     */
    fun delete(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.filterNot { it.id == id }

    /**
     * M8.4.3 — save an edited label: trimmed; a BLANK result deletes the
     * task (an emptied edit is a removal), anything else replaces the text.
     */
    fun setText(tasks: List<TodoTask>, id: String, text: String): List<TodoTask> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return delete(tasks, id)
        return tasks.map { if (it.id == id) it.copy(text = trimmed) else it }
    }

    /** Set an absolute priority; anything but H/L reads as NORMAL. */
    fun setPriority(tasks: List<TodoTask>, id: String, priority: String): List<TodoTask> {
        val sanitized = TodoTask.sanitized(priority)
        return tasks.map { if (it.id == id) it.copy(priority = sanitized) else it }
    }

    /** Walk the cycle HIGH → NORMAL → LOW → HIGH on exactly the named task. */
    fun cyclePriority(tasks: List<TodoTask>, id: String): List<TodoTask> =
        tasks.map { if (it.id == id) it.copy(priority = TodoTask.cycled(it.priority)) else it }

    /** M8.4.3 — the tasks of ONE list (the card shows one list at a time). */
    fun inList(tasks: List<TodoTask>, listId: String): List<TodoTask> =
        tasks.filter { it.listId == listId }

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

    /**
     * The one presentation order: starred first, then HIGH priority
     * (M8.4.3), then newest first. Deterministic and total.
     */
    fun sorted(tasks: List<TodoTask>): List<TodoTask> =
        tasks.sortedWith(
            compareByDescending<TodoTask> { it.starred }
                .thenByDescending { it.priority == TodoTask.PRIORITY_HIGH }
                .thenByDescending { it.createdAt },
        )

    // ---------------------------------------------------- lists (M8.4.3)

    /** A non-blank, capped name appends a list — up to [TodoList.MAX_LISTS]. */
    fun addList(lists: List<TodoList>, name: String, id: String, now: Long): List<TodoList> {
        val trimmed = name.trim().take(TodoList.MAX_NAME)
        if (trimmed.isEmpty()) return lists
        if (lists.size >= TodoList.MAX_LISTS) return lists
        return lists + TodoList(id = id, name = trimmed, createdAt = now)
    }

    /** Rename (same trim/cap rules); unknown or blank-id ops change nothing. */
    fun renameList(lists: List<TodoList>, id: String, name: String): List<TodoList> {
        val trimmed = name.trim().take(TodoList.MAX_NAME)
        if (trimmed.isEmpty()) return lists
        return lists.map { if (it.id == id) it.copy(name = trimmed) else it }
    }

    /** The default list is never deletable; removing an unknown id is a no-op. */
    fun removeList(lists: List<TodoList>, id: String): List<TodoList> =
        if (id == TodoList.DEFAULT_LIST_ID) lists else lists.filterNot { it.id == id }

    /** Deleting a list takes its tasks with it (one store transaction). */
    fun removeTasksOfList(tasks: List<TodoTask>, listId: String): List<TodoTask> =
        tasks.filterNot { it.listId == listId }
}
