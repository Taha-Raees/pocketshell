package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.EntryKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.ExplorerOps
import app.pocketshell.files.PathSafety
import app.pocketshell.files.PendingTransfer
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StorageAreas
import app.pocketshell.ui.files.DeleteConfirmState
import app.pocketshell.ui.files.FilesOpsSurface
import app.pocketshell.ui.files.NewEntryDialog
import app.pocketshell.ui.files.OpsCommand
import app.pocketshell.ui.files.OpsNotice
import app.pocketshell.ui.files.RenameEntryDialog
import app.pocketshell.ui.files.ReplaceRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M7.0.0 Phase 3 (+ Phase 4 operations) — the Files screen's state holder.
 *
 * THIN by design: every decision lives in the pure [ExplorerCore] (navigation)
 * and [ExplorerOps] (file operations), both JVM-tested; this class only
 * (a) builds the concrete areas from the Phase 2 factories on the application
 * context, (b) dispatches every core/ops call to Dispatchers.IO — all
 * [StorageArea] methods block — and (c) exposes state as StateFlows. The UI
 * performs ZERO filesystem operations and never sees a [StorageArea]: it
 * sends listing names and dialog decisions, and this class composes and
 * validates every path.
 *
 * Phase 4 threading: operations and navigation share ONE serial confined
 * worker ([explorerIo]) and ONE dispatch job slot — the newest user intent
 * wins, an op is always followed by its re-list in the same job, and the
 * core's state is never touched from two threads.
 *
 * Like the other process-scoped ViewModels, the instance survives Activity
 * recreation (single-Activity app + configChanges), so the user's location —
 * and the pending copy/move marker — survive rotation and Home↔Files trips.
 *
 * Linux-first: the guest area is first in the switcher and the landing area.
 * When the guest runtime is not installed yet, [guestUnavailable] is true and
 * the screen says so honestly (with the path to Diagnostics) while the shelf
 * remains usable.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application), FilesOpsSurface {

    /**
     * All core mutations run on ONE confined worker, strictly in submission
     * order: [app.pocketshell.files.StorageArea] methods are blocking and
     * non-cooperative, so a superseded listing must be allowed to finish its
     * turn on the serial queue rather than race the next request. The core's
     * state is therefore never touched from two threads.
     */
    private val explorerIo = Dispatchers.IO.limitedParallelism(1)

    private val core: ExplorerCore

    /** Every available area, by id — operations resolve areas from here. */
    private val areaById: Map<AreaId, StorageArea>

    /** Honest static fact: the PocketShell Linux rootfs is not present yet. */
    val guestUnavailable: Boolean

    private val _state = MutableStateFlow(ExplorerCore.State.initialLoading())
    val state: StateFlow<ExplorerCore.State> = _state.asStateFlow()

    // ------------------------------------------------- Phase 4 op-state flows

    private val _pending = MutableStateFlow<PendingTransfer?>(null)
    override val pending: StateFlow<PendingTransfer?> = _pending.asStateFlow()

    private val _notice = MutableStateFlow<OpsNotice?>(null)
    override val notice: StateFlow<OpsNotice?> = _notice.asStateFlow()

    private val _confirmReplace = MutableStateFlow<ReplaceRequest?>(null)
    override val confirmReplace: StateFlow<ReplaceRequest?> = _confirmReplace.asStateFlow()

    private val _newDialog = MutableStateFlow<NewEntryDialog?>(null)
    override val newDialog: StateFlow<NewEntryDialog?> = _newDialog.asStateFlow()

    private val _renameDialog = MutableStateFlow<RenameEntryDialog?>(null)
    override val renameDialog: StateFlow<RenameEntryDialog?> = _renameDialog.asStateFlow()

    private val _deleteConfirm = MutableStateFlow<DeleteConfirmState?>(null)
    override val deleteConfirm: StateFlow<DeleteConfirmState?> = _deleteConfirm.asStateFlow()

    /** Monotonic notice counter — re-triggers the banner's auto-dismiss. */
    private var noticeSeq = 0L

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
        areaById = handles.associate { it.area.id to it.area }
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

    // ============================================================ Phase 4 ops

    /**
     * One serialized OPERATION, always followed by a re-list of the current
     * location in the same job — the refresh-after-every-operation rule.
     * [op] fully owns its extra side effects (pending marker, dialog errors,
     * confirm requests) and returns null when the outcome was delivered
     * somewhere else (e.g. a collision became a Replace request).
     */
    private fun runOp(op: () -> ExplorerOps.OpOutcome?) {
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                _state.value = core.stageLoading()
                val outcome = try {
                    op()
                } catch (t: Throwable) {
                    ExplorerOps.OpOutcome.fail(
                        "The operation could not run: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
                if (outcome != null) {
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        text = outcome.message,
                        isError = !outcome.success,
                        seq = noticeSeq,
                    )
                }
                _state.value = core.refresh()
            }
        }
    }

    private fun currentAreaOrNull(): StorageArea? {
        val id = _state.value.areaId ?: return null
        return areaById[id]
    }

    // ------------------------------------------------------- pending transfer

    override fun startCopy(name: String) = markPending(name, move = false)

    override fun startMove(name: String) = markPending(name, move = true)

    private fun markPending(name: String, move: Boolean) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        // Names come from listings, but the composed path is re-validated anyway.
        val child = ExplorerOps.composeChild(dir, name) ?: return
        val kind = state.entries.firstOrNull { it.name == name }?.kind ?: EntryKind.FILE
        _pending.value = PendingTransfer(
            areaId = areaId,
            areaLabel = state.areas.firstOrNull { it.selected }?.label ?: "",
            path = child,
            name = name,
            kind = kind,
            move = move,
        )
    }

    override fun cancelPending() {
        _pending.value = null
    }

    override fun pasteHere() {
        val pending = _pending.value ?: return
        val targetArea = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        val sourceArea = areaById[pending.areaId] ?: run {
            _pending.value = null
            return
        }
        runOp {
            when (val check = ExplorerOps.checkPaste(sourceArea, pending, targetArea, dir)) {
                is ExplorerOps.PasteCheck.Invalid -> ExplorerOps.OpOutcome.fail(check.reason)
                ExplorerOps.PasteCheck.SameAsSource -> ExplorerOps.OpOutcome.fail(
                    "Source and destination are the same — open a different folder to paste.",
                )
                is ExplorerOps.PasteCheck.Collision -> {
                    _confirmReplace.value = ReplaceRequest(
                        opLabel = if (pending.move) "Move" else "Copy",
                        name = pending.name,
                        existingKind = check.existing.kind,
                        command = OpsCommand.Paste(
                            pending = pending,
                            targetAreaId = targetArea.id,
                            target = check.target,
                        ),
                    )
                    null
                }
                is ExplorerOps.PasteCheck.Clear -> {
                    val outcome = ExplorerOps.executePaste(
                        sourceArea, pending, targetArea, check.target, replace = false,
                    )
                    if (outcome.success) _pending.value = null
                    outcome
                }
            }
        }
    }

    override fun resolveReplace(replace: Boolean) {
        val request = _confirmReplace.value ?: return
        _confirmReplace.value = null
        if (!replace) return
        when (val command = request.command) {
            is OpsCommand.Paste -> {
                val sourceArea = areaById[command.pending.areaId] ?: return
                val targetArea = areaById[command.targetAreaId] ?: return
                runOp {
                    val outcome = ExplorerOps.executePaste(
                        sourceArea, command.pending, targetArea, command.target, replace = true,
                    )
                    if (outcome.success) _pending.value = null
                    outcome
                }
            }
            is OpsCommand.Rename -> {
                val area = areaById[command.areaId] ?: return
                runOp {
                    ExplorerOps.executeRename(area, command.path, command.newName, replace = true)
                }
            }
        }
    }

    // ----------------------------------------------------------- new / rename

    override fun openNewDialog(folder: Boolean) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        _newDialog.value = NewEntryDialog(areaId = areaId, targetDir = dir, folder = folder)
    }

    override fun openNewDialogInside(folderName: String, folder: Boolean) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        val target = ExplorerOps.composeChild(dir, folderName) ?: return
        _newDialog.value = NewEntryDialog(areaId = areaId, targetDir = target, folder = folder)
    }

    override fun dismissNewDialog() {
        _newDialog.value = null
    }

    override fun submitNewName(name: String) {
        val dialog = _newDialog.value ?: return
        val area = areaById[dialog.areaId] ?: run {
            _newDialog.value = null
            return
        }
        // Lexical feedback without touching the dispatcher; engine re-validates.
        ExplorerOps.nameError(name)?.let {
            _newDialog.value = dialog.copy(error = it)
            return
        }
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                _state.value = core.stageLoading()
                val outcome = if (dialog.folder) {
                    ExplorerOps.executeCreateDirectory(area, dialog.targetDir, name)
                } else {
                    ExplorerOps.executeCreateFile(area, dialog.targetDir, name)
                }
                if (outcome.success) {
                    _newDialog.value = null
                    noticeSeq += 1
                    _notice.value = OpsNotice(outcome.message, isError = false, seq = noticeSeq)
                } else {
                    // The dialog stays open with the honest reason; nothing silent.
                    _newDialog.value = dialog.copy(error = outcome.message)
                }
                _state.value = core.refresh()
            }
        }
    }

    override fun openRenameDialog(name: String) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        val path = ExplorerOps.composeChild(dir, name) ?: return
        _renameDialog.value = RenameEntryDialog(areaId = areaId, path = path, currentName = name)
    }

    override fun dismissRenameDialog() {
        _renameDialog.value = null
    }

    override fun submitRename(newName: String) {
        val dialog = _renameDialog.value ?: return
        val area = areaById[dialog.areaId] ?: run {
            _renameDialog.value = null
            return
        }
        ExplorerOps.nameError(newName)?.let {
            _renameDialog.value = dialog.copy(error = it)
            return
        }
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                _state.value = core.stageLoading()
                when (val check = ExplorerOps.checkRename(area, dialog.path, newName)) {
                    is ExplorerOps.RenameCheck.Invalid ->
                        _renameDialog.value = dialog.copy(error = check.reason)
                    ExplorerOps.RenameCheck.Unchanged ->
                        _renameDialog.value = null
                    is ExplorerOps.RenameCheck.Collision -> {
                        _confirmReplace.value = ReplaceRequest(
                            opLabel = "Rename",
                            name = newName,
                            existingKind = check.existing.kind,
                            command = OpsCommand.Rename(
                                areaId = dialog.areaId,
                                path = dialog.path,
                                newName = newName,
                            ),
                        )
                    }
                    is ExplorerOps.RenameCheck.Clear -> {
                        val outcome = ExplorerOps.executeRename(
                            area, dialog.path, newName, replace = false,
                        )
                        if (outcome.success) {
                            _renameDialog.value = null
                            noticeSeq += 1
                            _notice.value = OpsNotice(outcome.message, isError = false, seq = noticeSeq)
                        } else {
                            _renameDialog.value = dialog.copy(error = outcome.message)
                        }
                    }
                }
                _state.value = core.refresh()
            }
        }
    }

    // ----------------------------------------------------------------- delete

    override fun openDeleteConfirm(name: String) {
        val state = _state.value
        if (state.areaId == null || state.path == null) return
        val kind = state.entries.firstOrNull { it.name == name }?.kind ?: return
        _deleteConfirm.value = DeleteConfirmState(name = name, kind = kind)
    }

    override fun dismissDeleteConfirm() {
        _deleteConfirm.value = null
    }

    override fun confirmDelete() {
        val confirm = _deleteConfirm.value ?: return
        _deleteConfirm.value = null
        val area = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        val path = ExplorerOps.composeChild(dir, confirm.name) ?: return
        runOp { ExplorerOps.executeDelete(area, path) }
    }

    // ----------------------------------------------------------------- notice

    override fun dismissNotice() {
        _notice.value = null
    }
}
