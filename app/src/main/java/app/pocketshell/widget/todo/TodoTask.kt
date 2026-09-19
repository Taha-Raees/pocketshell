package app.pocketshell.widget.todo

import kotlinx.serialization.Serializable

/**
 * M8.4 — TODO Home Application: one task, persisted as plain JSON in the
 * app-local "todo_store" DataStore (see [TodoRepository]).
 *
 * Deliberately SMALL: a task is text plus three flags and one ordering
 * key. No due dates, no reminders, no projects, no accounts — v1 answers
 * exactly "what do I need to do today?".
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
)
