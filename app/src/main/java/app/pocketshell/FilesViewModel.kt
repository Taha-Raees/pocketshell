package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.PathSafety
import app.pocketshell.files.StorageAreas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M7.0.0 Phase 3 — the Files screen's state holder.
 *
 * THIN by design: every decision lives in the pure [ExplorerCore] (JVM-tested);
 * this class only (a) builds the concrete areas from the Phase 2 factories on
 * the application context, (b) dispatches every core call to Dispatchers.IO —
 * all [StorageArea] methods block — and (c) exposes the state as a StateFlow.
 * The UI performs ZERO filesystem operations and never sees a [StorageArea].
 *
 * Like the other process-scoped ViewModels, the instance survives Activity
 * recreation (single-Activity app + configChanges), so the user's location in
 * the explorer survives rotation and Home↔Files round-trips.
 *
 * Linux-first: the guest area is first in the switcher and the landing area.
 * When the guest runtime is not installed yet, [guestUnavailable] is true and
 * the screen says so honestly (with the path to Diagnostics) while the shelf
 * remains usable.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * All core mutations run on ONE confined worker, strictly in submission
     * order: [app.pocketshell.files.StorageArea] methods are blocking and
     * non-cooperative, so a superseded listing must be allowed to finish its
     * turn on the serial queue rather than race the next request. The core's
     * state is therefore never touched from two threads.
     */
    private val explorerIo = Dispatchers.IO.limitedParallelism(1)

    private val core: ExplorerCore

    /** Honest static fact: the PocketShell Linux rootfs is not present yet. */
    val guestUnavailable: Boolean

    private val _state = MutableStateFlow(ExplorerCore.State.initialLoading())
    val state: StateFlow<ExplorerCore.State> = _state.asStateFlow()

    init {
        val handles = buildList {
            StorageAreas.guest(application)?.let { area ->
                add(
                    ExplorerCore.AreaHandle(
                        area = area,
                        startPath = PathSafety.validatePath("/root")!!,
                        shortLabel = "Linux",
                    ),
                )
            }
            StorageAreas.androidShelf(application)?.let { area ->
                add(
                    ExplorerCore.AreaHandle(
                        area = area,
                        startPath = PathSafety.validatePath("/")!!,
                        shortLabel = "Downloads",
                    ),
                )
            }
        }
        guestUnavailable = handles.none { it.area.id.kind == AreaKind.GUEST_LINUX }
        core = ExplorerCore(handles)
        viewModelScope.launch {
            _state.value = withContext(explorerIo) { core.initial() }
        }
    }

    /** Open a directory entry by its listing name (the ONLY navigation input the UI has). */
    fun openChild(name: String) = dispatch { core.openChild(name) }

    /** Back to the parent directory; at the area root the core no-ops (back then exits the screen). */
    fun navigateUp() = dispatch { core.navigateUp() }

    /** Switch storage area (the header chip). */
    fun switchArea(id: AreaId) = dispatch { core.switchArea(id) }

    /** Re-list the current location (the error banner's Retry). */
    fun refresh() = dispatch { core.refresh() }

    private var dispatchJob: kotlinx.coroutines.Job? = null

    /**
     * One serialized core call. The pure loading stage is posted first so the
     * UI reacts on the same frame; a superseded dispatch is cancelled, which
     * skips its result publication (the newest navigation always wins) — the
     * in-flight listing itself merely runs out its turn on the serial queue.
     */
    private fun dispatch(call: () -> ExplorerCore.State) {
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            _state.value = withContext(explorerIo) { core.stageLoading() }
            _state.value = withContext(explorerIo) { call() }
        }
    }
}
