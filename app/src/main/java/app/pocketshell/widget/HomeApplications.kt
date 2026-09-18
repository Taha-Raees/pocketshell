package app.pocketshell.widget

/**
 * M8.2 — the ONE registry of Home applications. The Home Application Card
 * hosts exactly ONE application; adding application #N is one object plus
 * one line here (the Control Center picker renders itself from [specs]).
 *
 * First application: Servers (the reference implementation of the
 * pattern). Future candidates — Storage, Agents, SSH, Git, System — are
 * deliberately NOT built yet (owner direction, M8.2); the registry makes
 * them a one-entry addition when they are.
 */
object HomeApplications {

    const val SERVERS_ID = "servers"

    /** The card's default application on fresh install / restore. */
    const val DEFAULT_ID: String = SERVERS_ID

    val all: List<HomeApplication> = listOf(
        ServersApp,
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
