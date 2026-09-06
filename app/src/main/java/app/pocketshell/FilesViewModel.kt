package app.pocketshell

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
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
import app.pocketshell.files.saf.AndroidDocumentArea
import app.pocketshell.files.saf.FileShareOps
import app.pocketshell.files.saf.SafFolderInfo
import app.pocketshell.files.saf.SafFolderState
import app.pocketshell.files.saf.SafTransfers
import app.pocketshell.ui.files.DeleteConfirmState
import app.pocketshell.ui.files.ExportPrompt
import app.pocketshell.ui.files.FilesOpsSurface
import app.pocketshell.ui.files.NewEntryDialog
import app.pocketshell.ui.files.OpsCommand
import app.pocketshell.ui.files.OpsNotice
import app.pocketshell.ui.files.RenameEntryDialog
import app.pocketshell.ui.files.ReplaceRequest
import app.pocketshell.ui.files.ShareReady
import java.io.File
import java.io.FileNotFoundException
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

    /** Every available area, by id — operations resolve areas from here.
     * Mutated ONLY on the serial worker (SAF folders join/leave at runtime). */
    private val areaById: LinkedHashMap<AreaId, StorageArea> = LinkedHashMap()

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

    // ------------------------------------------- Phase 5 — Android bridge

    private val _safFolders = MutableStateFlow<List<SafFolderInfo>>(emptyList())
    override val safFolders: StateFlow<List<SafFolderInfo>> = _safFolders.asStateFlow()

    private val _shareReady = MutableStateFlow<ShareReady?>(null)
    override val shareReady: StateFlow<ShareReady?> = _shareReady.asStateFlow()

    private val _exportPrompt = MutableStateFlow<ExportPrompt?>(null)
    override val exportPrompt: StateFlow<ExportPrompt?> = _exportPrompt.asStateFlow()

    /** The export whose save dialog is currently open (one at a time). */
    private var exportContext: ExportContext? = null

    private data class ExportContext(val areaId: AreaId, val path: app.pocketshell.files.AreaPath, val name: String)

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
            // Phase 5: every persisted user-granted folder rejoins the
            // switcher after a restart. Revoked grants are NOT silently
            // dropped — they appear with the honest revoked state and the
            // Reconnect/Remove banner.
            safTreeUris(application).forEach { uri ->
                val area = StorageAreas.safTree(
                    context = application,
                    treeUri = uri,
                    onAccessLost = { markSafRevoked(uri) },
                )
                add(
                    ExplorerCore.AreaHandle(
                        area = area,
                        startPath = PathSafety.validatePath("/")!!,
                        shortLabel = area.displayName,
                    ),
                )
                _safFolders.value = _safFolders.value + SafFolderInfo(
                    uri = uri,
                    label = area.displayName,
                    state = if (area.startsAvailable) SafFolderState.AVAILABLE else SafFolderState.REVOKED,
                )
            }
        }
        guestUnavailable = handles.none { it.area.id.kind == AreaKind.GUEST_LINUX }
        handles.forEach { areaById[it.area.id] = it.area }
        core = ExplorerCore(handles)
        viewModelScope.launch {
            _state.value = withContext(explorerIo) { core.initial() }
        }
    }

    /** The persisted tree-grant URIs the OS still holds for us (read grants). */
    private fun safTreeUris(application: Application): List<String> = try {
        application.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
    } catch (_: Exception) {
        emptyList()
    }

    /** The area reported a revoked grant — flip the folder's honest state. */
    private fun markSafRevoked(uri: String) {
        _safFolders.value = _safFolders.value.map {
            if (it.uri == uri) it.copy(state = SafFolderState.REVOKED) else it
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
            is OpsCommand.ImportFile -> {
                val area = areaById[command.targetAreaId] ?: return
                val application = getApplication<Application>()
                val uri = Uri.parse(command.uriString)
                runOp {
                    val result = SafTransfers.importDocument(
                        targetArea = area,
                        target = command.target,
                        openSource = {
                            application.contentResolver.openInputStream(uri)
                                ?: throw FileNotFoundException("the selected file is no longer accessible")
                        },
                        replace = true,
                    )
                    if (result.success) {
                        ExplorerOps.OpOutcome.ok(
                            "Imported \"${command.name}\" — the existing file was replaced (\u2713 verified)",
                        )
                    } else {
                        ExplorerOps.OpOutcome.fail(result.reason ?: "the import failed")
                    }
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

    // ================================================== Phase 5 — SAF folders

    /**
     * The folder picker returned a tree URI (a first add OR a reconnect).
     * The picker callback has already taken the persistable permission; a
     * URI matching a known folder replaces its area with a freshly probed
     * one, otherwise the folder joins the switcher. Runs through the ONE
     * dispatch slot so the switcher/state never races a navigation.
     */
    override fun addSafFolderPicked(uriString: String) {
        val application = getApplication<Application>()
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                val id = AndroidDocumentArea.areaIdFor(uriString)
                val known = _safFolders.value.firstOrNull { it.uri == uriString }
                if (known != null) {
                    // Reconnect: rebuild the area with a fresh access probe.
                    areaById.remove(id)
                    _state.value = core.removeArea(id)
                }
                val area = StorageAreas.safTree(
                    context = application,
                    treeUri = uriString,
                    label = known?.label,
                    onAccessLost = { markSafRevoked(uriString) },
                )
                _state.value = core.addArea(
                    ExplorerCore.AreaHandle(
                        area = area,
                        startPath = PathSafety.validatePath("/")!!,
                        shortLabel = area.displayName,
                    ),
                )
                areaById[area.id] = area
                _safFolders.value = _safFolders.value.filterNot { it.uri == uriString } + SafFolderInfo(
                    uri = uriString,
                    label = area.displayName,
                    state = if (area.startsAvailable) SafFolderState.AVAILABLE else SafFolderState.REVOKED,
                )
                if (!area.startsAvailable) {
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        "\u26a0\ufe0f Access to \"${area.displayName}\" is not working yet — try Reconnect.",
                        isError = true,
                        seq = noticeSeq,
                    )
                }
            }
        }
    }

    /** Drop a user-granted folder: out of the switcher, permission released. */
    override fun removeSafFolder(uriString: String) {
        val application = getApplication<Application>()
        val id = AndroidDocumentArea.areaIdFor(uriString)
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                areaById.remove(id)
                _state.value = core.removeArea(id)
                _safFolders.value = _safFolders.value.filterNot { it.uri == uriString }
            }
        }
        runCatching {
            application.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    // ===================================================== Phase 5 — share

    override fun requestShare(name: String) {
        val area = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        val child = ExplorerOps.composeChild(dir, name) ?: return
        viewModelScope.launch {
            val staged = withContext(explorerIo) {
                FileShareOps.stageForShare(
                    sourceArea = area,
                    source = child,
                    stagingDir = File(getApplication<Application>().cacheDir, FileShareOps.STAGING_DIR_NAME),
                )
            }
            when (staged) {
                is FileShareOps.Staging.Error -> {
                    noticeSeq += 1
                    _notice.value = OpsNotice(staged.reason, isError = true, seq = noticeSeq)
                }
                is FileShareOps.Staging.Ok -> {
                    val uri = FileProvider.getUriForFile(
                        getApplication(),
                        FileShareOps.FILE_PROVIDER_AUTHORITY,
                        staged.file,
                    )
                    _shareReady.value = ShareReady(
                        name = name,
                        uriString = uri.toString(),
                        mimeType = FileShareOps.guessMimeType(name),
                    )
                }
            }
        }
    }

    override fun consumeShareReady() {
        _shareReady.value = null
    }

    // ==================================================== Phase 5 — export

    override fun requestExport(name: String) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        val child = ExplorerOps.composeChild(dir, name) ?: return
        val kind = state.entries.firstOrNull { it.name == name }?.kind
        if (kind == EntryKind.DIRECTORY) {
            noticeSeq += 1
            _notice.value = OpsNotice(
                "Folders cannot be exported — only files.",
                isError = true,
                seq = noticeSeq,
            )
            return
        }
        exportContext = ExportContext(areaId = areaId, path = child, name = name)
        _exportPrompt.value = ExportPrompt(name = name, mimeType = FileShareOps.guessMimeType(name))
    }

    override fun consumeExportPrompt() {
        _exportPrompt.value = null
    }

    override fun exportTargetPicked(uriString: String) {
        val context = exportContext ?: return
        exportContext = null
        val area = areaById[context.areaId] ?: return
        val application = getApplication<Application>()
        val target = Uri.parse(uriString)
        runOp {
            val result = SafTransfers.exportDocument(
                sourceArea = area,
                source = context.path,
                openOutput = {
                    application.contentResolver.openOutputStream(target, "w")
                        ?: throw FileNotFoundException("the save destination refused writing")
                },
                openVerify = {
                    application.contentResolver.openInputStream(target)
                        ?: throw FileNotFoundException("the saved file could not be re-opened")
                },
                onRemoveCreated = {
                    runCatching { DocumentsContract.deleteDocument(application.contentResolver, target) }
                },
            )
            if (result.success) {
                ExplorerOps.OpOutcome.ok("Exported \"${context.name}\" (\u2713 verified)")
            } else {
                ExplorerOps.OpOutcome.fail(result.reason ?: "the export failed")
            }
        }
    }

    // ==================================================== Phase 5 — import

    override fun importPicked(uriString: String) {
        val application = getApplication<Application>()
        val uri = Uri.parse(uriString)
        dispatchJob?.cancel()
        dispatchJob = viewModelScope.launch {
            withContext(explorerIo) {
                val pickedName = app.pocketshell.files.saf.DocumentsContractBackend
                    .queryDisplayName(application.contentResolver, uri)
                if (pickedName == null) {
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        "The selected file could not be read — its name or content is not accessible.",
                        isError = true,
                        seq = noticeSeq,
                    )
                    return@withContext
                }
                val state = _state.value
                val areaId = state.areaId
                val dir = state.path
                val area = areaId?.let { areaById[it] }
                if (area == null || dir == null) {
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        "Open a folder first — the import lands in the folder you are browsing.",
                        isError = true,
                        seq = noticeSeq,
                    )
                    return@withContext
                }
                val target = ExplorerOps.composeChild(dir, pickedName)
                if (target == null) {
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        "The file's name \"$pickedName\" cannot be used inside this storage.",
                        isError = true,
                        seq = noticeSeq,
                    )
                    return@withContext
                }
                val existing = area.stat(target)
                if (existing == null) {
                    val result = SafTransfers.importDocument(
                        targetArea = area,
                        target = target,
                        openSource = {
                            application.contentResolver.openInputStream(uri)
                                ?: throw FileNotFoundException("the selected file is no longer accessible")
                        },
                        replace = false,
                    )
                    noticeSeq += 1
                    _notice.value = OpsNotice(
                        if (result.success) {
                            "Imported \"$pickedName\" into ${area.displayName} (\u2713 verified)"
                        } else {
                            result.reason ?: "the import failed"
                        },
                        isError = !result.success,
                        seq = noticeSeq,
                    )
                    _state.value = core.refresh()
                } else {
                    // The Phase 4 collision system, reused verbatim.
                    _confirmReplace.value = ReplaceRequest(
                        opLabel = "Import",
                        name = pickedName,
                        existingKind = existing.kind,
                        command = OpsCommand.ImportFile(
                            uriString = uriString,
                            targetAreaId = areaId,
                            target = target,
                            name = pickedName,
                        ),
                    )
                }
            }
        }
    }
}
