package app.pocketshell.files.saf

/**
 * M7.0.0 Phase 5 — the user-granted Android folder model (pure, JVM-testable).
 *
 * A SAF folder grant is identified by its tree URI string and is EITHER
 * available (a live persisted permission) OR revoked (Android took the
 * permission back). There is deliberately no third "maybe" state and no fake
 * empty-folder rendering: a revoked folder keeps its place in the area
 * switcher and every operation on it returns an honest error, while the
 * Files screen shows the reconnect/remove banner.
 *
 * SAF URIs are NEVER converted into Linux paths and never rendered as POSIX
 * paths — the label here is a display name only.
 */

/** The access state of one user-granted SAF folder. */
enum class SafFolderState {
    /** A live persisted grant — the area works. */
    AVAILABLE,

    /** Android revoked the grant — honest error + Reconnect/Remove, never a crash. */
    REVOKED,
}

/** One user-granted SAF folder as the UI sees it. */
data class SafFolderInfo(
    /** The tree URI string; also the [app.pocketshell.files.AreaId] key. */
    val uri: String,
    /** Display label (the folder's own name — a display name, not a path). */
    val label: String,
    val state: SafFolderState,
)

object SafFolders {

    /**
     * Derive a display label from a tree URI WITHOUT any provider query —
     * this is what a REVOKED folder can still honestly show. Decodes the URI,
     * takes the tree segment ("primary:Download/MyProject"), strips the
     * storage-root prefix after ':' and keeps the last path component.
     * Falls back to the decoded segment itself when the shape is unusual.
     */
    fun labelFromTreeUri(uri: String): String {
        val decoded = runCatching { java.net.URLDecoder.decode(uri, "UTF-8") }
            .getOrDefault(uri)
        val marker = "/tree/"
        val treeIndex = decoded.lastIndexOf(marker)
        val tail = if (treeIndex >= 0) decoded.substring(treeIndex + marker.length) else decoded
        val afterRoot = tail.substringAfter(':', tail)
        val name = afterRoot.trim('/').substringAfterLast('/')
        return if (name.isEmpty()) "Android folder" else name
    }

    /**
     * Merge the OS-persisted grant list with previously known folder infos:
     * known labels are kept (a provider query is not always possible), unknown
     * grants get a URI-derived label. URIs that are no longer persisted are
     * dropped entirely — they were released by PocketShell itself (Remove) or
     * by the system, and keeping them would fake a grant that no longer
     * exists. States are (re-)decided by the caller's access probe.
     */
    fun reconcile(
        persistedUris: List<String>,
        previous: List<SafFolderInfo>,
    ): List<SafFolderInfo> = persistedUris.map { uri ->
        val known = previous.firstOrNull { it.uri == uri }
        SafFolderInfo(
            uri = uri,
            label = known?.label ?: labelFromTreeUri(uri),
            state = SafFolderState.AVAILABLE,
        )
    }
}
