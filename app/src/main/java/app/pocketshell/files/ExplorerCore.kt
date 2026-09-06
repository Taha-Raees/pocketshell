package app.pocketshell.files

/**
 * M7.0.0 Phase 3 — the explorer's state machine.
 *
 * PURE and SYNCHRONOUS: no Android imports, no coroutines, no dispatchers.
 * Every method performs blocking I/O through [StorageArea] (the Phase 2
 * discipline) and returns the new [State]; the ViewModel wraps calls in
 * Dispatchers.IO and posts the results, so the device gets real loading
 * states while this class stays directly JVM-testable with real temp
 * directories.
 *
 * SAFETY MODEL (pinned by ExplorerCoreTest):
 *  - The UI can NEVER hand the core a raw filesystem path. Navigation into
 *    a directory goes through [openChild], which takes only the entry NAME
 *    shown by the listing; the child path is composed from the validated
 *    current location and re-validated. Up-navigation goes through
 *    [navigateUp], which stops at the area root and can never escape the
 *    logical storage area.
 *  - Every state change lands as a canonical [AreaPath] (PathSafety) or an
 *    honest error — there is no code path that renders an unvalidated path.
 *  - Errors are data, not exceptions: a refused or failed listing produces
 *    a State with [State.error] set and the previous location preserved.
 */
class ExplorerCore(
    /**
     * The areas the user can switch between, in display order (the guest
     * Linux area first — the Phase 3 product decision). Only AVAILABLE areas
     * are passed in; the factories already returned null for the rest, and
     * the ViewModel renders that unavailability honestly on its own.
     */
    private val areaHandles: List<AreaHandle>,
) {

    /** One switchable area: its engine, its landing path, its short chip label. */
    data class AreaHandle(
        val area: StorageArea,
        /** Where navigation lands when entering this area fresh (guest: /root). */
        val startPath: AreaPath,
        /** Compact label for the area-switcher chip ("Linux", "Downloads"). */
        val shortLabel: String,
    )

    /** One row of the area switcher. */
    data class AreaOption(
        val id: AreaId,
        val label: String,
        val selected: Boolean,
    )

    /**
     * The whole explorer UI state. The UI renders exactly what is here and
     * performs no filesystem operations of its own.
     */
    data class State(
        val areas: List<AreaOption> = emptyList(),
        val areaId: AreaId? = null,
        val areaName: String = "",
        val path: AreaPath? = null,
        val entries: List<FsEntry> = emptyList(),
        val loading: Boolean = true,
        val error: String? = null,
    ) {
        /** True when the current location has a parent INSIDE the area. */
        val canNavigateUp: Boolean get() = path != null && path.value != "/"

        companion object {
            /** The state before the first listing has landed (initial spinner). */
            fun initialLoading(): State = State()
        }
    }

    /** Per-area remembered location — switching back lands where you left. */
    private val lastPathByArea = mutableMapOf<AreaId, AreaPath>()

    private var current: State = State.initialLoading()

    // ------------------------------------------------------------- lifecycle

    /**
     * Select the first available area at its landing path (or its remembered
     * location — only possible within one core lifetime) and list it. With no
     * available areas the state stays honestly empty: the UI renders an
     * unavailable surface, never a fake directory.
     */
    fun initial(): State {
        val first = areaHandles.firstOrNull()
        current = if (first == null) {
            State(loading = false, error = "No storage is available right now.")
        } else {
            enter(first, rememberedOrStart(first))
        }
        return publish()
    }

    /**
     * The pure pre-I/O stage the ViewModel posts before dispatching a real
     * operation: loading rises, a stale error clears, the visible location
     * and entries stay exactly as they were.
     */
    fun stageLoading(): State {
        current = current.copy(loading = true, error = null)
        return publish()
    }

    // ------------------------------------------------------------ navigation

    /**
     * Open a child of the CURRENT directory by its listing name. This is the
     * only directory-navigation primitive the UI has: names that are not a
     * single valid component ("..", anything with "/", blank, NUL…) are
     * refused with an honest error and the location does not move.
     */
    fun openChild(name: String): State {
        val area = currentArea() ?: return refatalled("No storage area is open.")
        val path = current.path
            ?: return refatalled("No current location — nothing to open from.")
        if (PathSafety.validateName(name) == null) {
            return refatalled("\"$name\" is not a valid folder name — navigation refused.")
        }
        val child = PathSafety.validatePath("${path.value.trimEnd('/')}/$name")
            ?: return refatalled("\"$name\" does not name a location inside this storage — navigation refused.")
        current = current.copy(loading = true, error = null)
        current = listInto(area, child, keepLocationOnFailure = true)
        return publish()
    }

    /**
     * Move to the parent of the current directory. At the area root this is
     * a no-op: the boundary of the logical area is never crossed (the UI's
     * back handling falls through to leaving the screen instead).
     */
    fun navigateUp(): State {
        val area = currentArea() ?: return current
        val path = current.path ?: return current
        if (path.value == "/") return current
        val parentComponents = path.components.dropLast(1)
        val parentRaw = if (parentComponents.isEmpty()) "/" else "/" + parentComponents.joinToString("/")
        val parent = PathSafety.validatePath(parentRaw) ?: return current
        current = current.copy(loading = true, error = null)
        current = listInto(area, parent, keepLocationOnFailure = true)
        return publish()
    }

    /**
     * Switch to another area. The location you are leaving is remembered for
     * this session; the target opens at ITS remembered location or its
     * landing path. Switching to the area you are already in is a no-op.
     */
    fun switchArea(id: AreaId): State {
        if (current.areaId == id) return current
        val target = areaHandles.firstOrNull { it.area.id == id }
            ?: return refatalled("That storage area is not available.")
        current.path?.let { leaving -> currentAreaIdOrNull()?.let { lastPathByArea[it] = leaving } }
        current = current.copy(loading = true, error = null)
        current = enter(target, rememberedOrStart(target))
        return publish()
    }

    /** Re-list the current location (the error banner's Retry, pull feel). */
    fun refresh(): State {
        val area = currentArea() ?: return current
        val path = current.path ?: return current
        current = current.copy(loading = true, error = null)
        current = listInto(area, path, keepLocationOnFailure = true)
        return publish()
    }

    /** The current state without triggering any operation (tests, diagnostics). */
    fun snapshot(): State = current

    // ------------------------------------------------------------- internals

    /** Enter an area at [path] and list it. Failure leaves the area chosen but the error honest. */
    private fun enter(handle: AreaHandle, path: AreaPath): State = State(
        areas = areaHandles.map { option ->
            AreaOption(id = option.area.id, label = option.shortLabel, selected = option.area.id == handle.area.id)
        },
        areaId = handle.area.id,
        areaName = handle.area.displayName,
        path = path,
        entries = emptyList(),
        loading = true,
        error = null,
    ).let { listInto(handle.area, path, keepLocationOnFailure = false, base = it) }

    /**
     * One listing, folded into [base] (or the current state). When the listing
     * fails and [keepLocationOnFailure] is set, the user STAYS at the failed
     * location with the honest reason visible — the same honesty rule the
     * runtime layer uses for launch failures.
     */
    private fun listInto(
        area: StorageArea,
        path: AreaPath,
        keepLocationOnFailure: Boolean,
        base: State? = null,
    ): State {
        val foundation = (base ?: current).copy(loading = true, error = null)
        return when (val result = area.list(path)) {
            is ListResult.Ok -> foundation.copy(
                path = path,
                entries = result.entries,
                loading = false,
                error = null,
            )
            is ListResult.Error -> {
                val message = "Could not list ${path.value}: ${result.reason}"
                if (keepLocationOnFailure) {
                    foundation.copy(loading = false, error = message)
                } else {
                    // Entering fresh failed: still show the area + attempted
                    // location with the reason — never a silent blank.
                    foundation.copy(path = path, entries = emptyList(), loading = false, error = message)
                }
            }
        }
    }

    private fun rememberedOrStart(handle: AreaHandle): AreaPath =
        lastPathByArea[handle.area.id] ?: handle.startPath

    private fun currentAreaIdOrNull(): AreaId? = current.areaId

    private fun currentArea(): StorageArea? =
        areaHandles.firstOrNull { it.area.id == current.areaId }?.area

    /** Honest failure that keeps the current location visible (never a dead screen). */
    private fun refatalled(message: String): State {
        current = current.copy(loading = false, error = message)
        return publish()
    }

    private fun publish(): State = current
}
