package app.pocketshell.files

/**
 * M7.2-A — clickable breadcrumb derivation.
 *
 * PURE: a validated [AreaPath] in, an ordered list of crumbs out. The UI
 * renders one crumb per ancestor of the current location (root first,
 * current location last); tapping a crumb navigates through
 * [ExplorerCore.openDirectory] with the crumb's OWN validated [AreaPath] —
 * the navigation input is never a string reassembled from rendered labels,
 * so a crumb tap has exactly the safety surface of any other core
 * navigation.
 *
 * The root crumb is labeled with the AREA's short label ("Linux",
 * "Downloads", the granted folder's name) rather than "/" — that is the
 * honest boundary of the logical area (the core never navigates above it)
 * and it keeps SAF areas free of fake POSIX roots. Deeper crumbs are the
 * path's own components: for the guest Linux area that is the real POSIX
 * spine; for SAF areas it is the document-tree spine of display names —
 * each is exactly what the area's own listing resolves.
 */
data class Breadcrumb(
    /** Display label of one ancestor (a single name, or the area root label). */
    val name: String,
    /** The validated location this crumb navigates to. */
    val path: AreaPath,
    /** True for the LAST crumb — the current location (rendered, not a button). */
    val isCurrent: Boolean,
)

object Breadcrumbs {

    /**
     * The crumbs for [path], root first, current location last. [rootLabel]
     * names the area root ("/" → "Linux" / "Downloads" / the folder name).
     * A list with exactly one entry means the user is AT the area root.
     */
    fun of(path: AreaPath, rootLabel: String): List<Breadcrumb> {
        val components = path.components
        val crumbs = ArrayList<Breadcrumb>(components.size + 1)
        crumbs += Breadcrumb(
            name = rootLabel.ifBlank { "/" },
            path = AreaPath.unchecked("/"),
            isCurrent = components.isEmpty(),
        )
        var prefix = ""
        for ((index, component) in components.withIndex()) {
            prefix = if (prefix == "/") "/$component" else "$prefix/$component"
            crumbs += Breadcrumb(
                name = component,
                path = AreaPath.unchecked(PathSafety.validatePath(prefix)!!.value),
                isCurrent = index == components.lastIndex,
            )
        }
        return crumbs
    }
}
