package app.pocketshell.widget

import app.pocketshell.widget.notes.NotesApp
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

    /**
     * M8.4.4 — the default application on fresh install / restore. Servers
     * moved to the DOWNLOADABLE catalog (ps-widget-repo), so the default is
     * the local-first app that never needs the guest or the network.
     */
    const val DEFAULT_ID: String = TODO_ID

    /**
     * The BUILT-IN applications. Servers and SSH are no longer here: they
     * are the pilot DOWNLOADABLE widgets — installed from the widget
     * repository (catalog.json) and resolved through the installed-external
     * store before this builtin list is consulted. M8.4.5 — Git, Sync and
     * Storage joined them: BUILTIN = Todo + Notes only; everything
     * guest-side is a catalog widget, updatable from the repo.
     */
    val all: List<HomeApplication> = listOf(
        TodoApp,
        NotesApp,
    )

    /** The Control Center picker listing (registry order = display order). */
    val specs: List<HomeAppSpec> get() = all.map { it.spec }

    fun byId(id: String): HomeApplication? = all.firstOrNull { it.spec.id == id }

    /**
     * The persisted application id → the application, or Missing (an id
     * that no longer resolves — stated, never substituted; identical
     * discipline to every persisted id in this codebase).
     *
     * Installed EXTERNAL widgets resolve first (M8.4.4): the host passes
     * the installed-manifest applications it owns; a builtin id can never
     * be shadowed by an external one because install ids outside the
     * builtin vocabulary are rejected at install time.
     */
    sealed interface Resolved {
        data class Found(val application: HomeApplication) : Resolved
        data class Missing(val id: String) : Resolved
    }

    fun resolve(id: String, externals: List<HomeApplication> = emptyList()): Resolved =
        (externals.firstOrNull { it.spec.id == id } ?: byId(id))
            ?.let { Resolved.Found(it) }
            ?: Resolved.Missing(id)
}
