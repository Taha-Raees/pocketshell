package app.pocketshell.widget

import app.pocketshell.widget.git.GitApp
import app.pocketshell.widget.notes.NotesApp
import app.pocketshell.widget.storage.StorageApp
import app.pocketshell.widget.sync.SyncApp
import app.pocketshell.widget.ssh.SshApp
import app.pocketshell.widget.todo.TodoApp

/**
 * M8.3 — the ONE registry of Home applications. The Home Application
 * carousel hosts the user's configured subset, in their configured order;
 * adding application #N is one object + one line here (the Control Center
 * picker renders itself from [specs]).
 *
 * Servers is the reference implementation of the pattern; Git (repository
 * state at a glance, read-only) and SSH (saved hosts + live client
 * evidence) follow. Future candidates — Storage, Agents, System — use the
 * same one-entry path.
 */
object HomeApplications {

    const val SERVERS_ID = "servers"
    const val GIT_ID = "git"
    const val SSH_ID = "ssh"
    const val TODO_ID = "todo"

    /** The card's default application on fresh install / restore. */
    const val DEFAULT_ID: String = SERVERS_ID

    val all: List<HomeApplication> = listOf(
        ServersApp,
        GitApp,
        SshApp,
        TodoApp,
        NotesApp,
        StorageApp,
        SyncApp,
    )

    /** The Control Center picker listing (registry order = display order). */
    val specs: List<HomeAppSpec> get() = all.map { it.spec }

    fun byId(id: String): HomeApplication? = all.firstOrNull { it.spec.id == id }

    /**
     * The persisted application id → the application, or Missing (an id
     * that no longer resolves — stated, never substituted; identical
     * discipline to every persisted id in this codebase).
     */
    sealed interface Resolved {
        data class Found(val application: HomeApplication) : Resolved
        data class Missing(val id: String) : Resolved
    }

    fun resolve(id: String): Resolved =
        byId(id)?.let { Resolved.Found(it) } ?: Resolved.Missing(id)
}
