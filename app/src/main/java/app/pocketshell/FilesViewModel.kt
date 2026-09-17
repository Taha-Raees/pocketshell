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
import app.pocketshell.files.BookmarkStore
import app.pocketshell.files.EntryKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.ExplorerOps
import app.pocketshell.files.FileSearch
import app.pocketshell.files.MultiSelectOps
import app.pocketshell.files.FilesSearchState
import app.pocketshell.files.FsEntry
import app.pocketshell.files.PathSafety
import app.pocketshell.files.RecentFolder
import app.pocketshell.files.RecentFolderStore
import app.pocketshell.files.isSameTarget
import app.pocketshell.files.PendingTransfer
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StorageAreas
import app.pocketshell.files.TerminalLaunch
import app.pocketshell.files.terminalLaunchDirectory
import app.pocketshell.files.TerminalLaunchResolution
import app.pocketshell.files.TerminalLaunchSupport
import app.pocketshell.files.openTerminalHereProblem
import app.pocketshell.files.ZipArchiveOps
import app.pocketshell.files.saf.AndroidDocumentArea
import app.pocketshell.files.saf.FileShareOps
import app.pocketshell.files.saf.SafFolderInfo
import app.pocketshell.files.saf.SafFolderState
import app.pocketshell.files.saf.SafTransfers
import app.pocketshell.files.editor.EditorLaunch
import app.pocketshell.ui.files.DeleteConfirmState
import app.pocketshell.ui.files.MultiDeleteConfirm
import app.pocketshell.ui.files.ExportPrompt
import app.pocketshell.ui.files.FilesOpsSurface
import app.pocketshell.ui.files.NewEntryDialog
import app.pocketshell.ui.files.OpsCommand
import app.pocketshell.ui.files.OpsNotice
import app.pocketshell.ui.files.RenameEntryDialog
import app.pocketshell.ui.files.ReplaceRequest
import app.pocketshell.ui.files.ShareReady
import app.pocketshell.ui.files.CompressDialogState
import app.pocketshell.ui.files.ExtractDialogState
import app.pocketshell.ui.files.OpenWithReady
import app.pocketshell.ui.files.ZipProgress
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    // The recently-browsed folder (Home "Recent" row): recorded from the
    // explorer's SUCCESSFUL listing state, persisted through the settings
    // DataStore (files/RecentFolder.kt owns the typed model + revalidation).
    private val settingsRepository = app.pocketshell.settings.SettingsRepository(application)

    /** The last folder the user navigated INTO (survives process death), or null. */
    val recentFolder: StateFlow<RecentFolder?> = settingsRepository.recentFolderRecord
        .map { (kind, areaKey, path) -> RecentFolderStore.deserialize(kind, areaKey, path) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------- Phase 4 op-state flows

    private val _pending = MutableStateFlow<PendingTransfer?>(null)
    override val pending: StateFlow<PendingTransfer?> = _pending.asStateFlow()

    /** Phase 8.1: how many items the pending marker holds (1 = single). */
    private val _pendingCount = MutableStateFlow(1)
    override val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

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

    // ------------------------------------------- Phase 8.1 — multi-select

    private val _selectionMode = MutableStateFlow(false)
    override val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    override val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _multiDeleteConfirm = MutableStateFlow<MultiDeleteConfirm?>(null)
    override val multiDeleteConfirm: StateFlow<MultiDeleteConfirm?> = _multiDeleteConfirm.asStateFlow()

    /**
     * The multi clipboard: fully validated single transfers, all from one
     * source area, in listing order. It exists only while a paste banner is
     * up; every path in it was composed at marking time and is re-checked
     * at execution time — nothing is ever re-derived from raw names.
     */
    private var multiQueue: List<PendingTransfer>? = null

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
                        shortLabel = StorageAreas.Labels.SHELF_SHORT_LABEL,
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
        // Record every USER navigation into a folder: state.path only moves
        // on a successful listing, and drop(1) skips the initial landing
        // area — a restart must never overwrite the stored recent with the
        // default start path before the user has opened anything.
        viewModelScope.launch {
            state
                .map { it.areaId to it.path }
                .distinctUntilChanged()
                .drop(1)
                .collect { (area, path) ->
                    if (area != null && path != null) {
                        settingsRepository.setRecentFolderRecord(
                            kind = area.kind.name,
                            areaKey = area.key,
                            path = path.value,
                        )
                    }
                }
        }
    }

    /**
     * Open the Home "Recent" row's folder: switch to its area (no-op when it
     * is already current) and list the stored, revalidated path. An area
     * that no longer exists (a revoked SAF grant) fails honestly through the
     * core's normal error state — never a fake navigation.
     */
    fun openRecentFolder(folder: RecentFolder) {
        exitSearch()
        exitSelection()
        val area = AreaId(folder.areaKind, folder.areaKey)
        dispatch {
            if (areaById[area] != null && _state.value.areaId != area) {
                core.switchArea(area)
            }
            core.openDirectory(folder.path)
        }
    }

    /** The Recent row's "Remove from Home": clears the persisted record. */
    fun clearRecentFolder() {
        viewModelScope.launch { settingsRepository.setRecentFolderRecord(null, null, null) }
    }

    // --------------------------------- folder bookmarks (owner iteration)

    /** The user's bookmarked folders (newest last); every read revalidates. */
    override val bookmarks: StateFlow<List<RecentFolder>> = settingsRepository.folderBookmarksRecord
        .map { BookmarkStore.deserialize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Bookmark / Remove Bookmark for the ⋯ sheet: the DIRECTORY entry is
     * resolved against the CURRENT listing (area + path + name), so a sheet
     * left open over an old directory can never bookmark the wrong target.
     * Persisted through the settings DataStore in one atomic rewrite.
     */
    override fun toggleBookmark(entry: FsEntry) {
        if (entry.kind != EntryKind.DIRECTORY) return
        val area = _state.value.areaId ?: return
        val parent = _state.value.path ?: return
        val target = PathSafety.validatePath("${parent.value}/${entry.name}") ?: return
        viewModelScope.launch { toggleBookmarkInternal(area, target) }
    }

    /** Header action (owner round 2): bookmark / unbookmark the BROWSED folder. */
    override fun toggleBookmarkCurrent() {
        val area = _state.value.areaId ?: return
        val path = _state.value.path ?: return
        viewModelScope.launch { toggleBookmarkInternal(area, path) }
    }

    private suspend fun toggleBookmarkInternal(area: AreaId, target: app.pocketshell.files.AreaPath) {
        val current = BookmarkStore.deserialize(
            settingsRepository.folderBookmarksRecord.first(),
        )
        fun RecentFolder.matchesTarget() = isSameTarget(area.kind, area.key, target)
        val updated = if (current.any { it.matchesTarget() }) {
            current.filterNot { it.matchesTarget() }
        } else {
            current + RecentFolder(area.kind, area.key, target)
        }
        settingsRepository.setFolderBookmarks(BookmarkStore.serialize(updated))
    }

    /** Is [entry] in the CURRENT listing bookmarked? (Sheet label + state.) */
    override fun bookmarked(entry: FsEntry, bookmarks: List<RecentFolder>): Boolean {
        val area = _state.value.areaId ?: return false
        val parent = _state.value.path ?: return false
        val target = PathSafety.validatePath("${parent.value}/${entry.name}") ?: return false
        return bookmarks.any { it.isSameTarget(area.kind, area.key, target) }
    }

    /** Is the BROWSED folder bookmarked? (Header button state.) */
    override fun bookmarkedCurrent(bookmarks: List<RecentFolder>): Boolean {
        val area = _state.value.areaId ?: return false
        val path = _state.value.path ?: return false
        return bookmarks.any { it.isSameTarget(area.kind, area.key, path) }
    }

    /** Home row action: remove ONE bookmark by its exact identity. */
    fun removeBookmark(folder: RecentFolder) {
        viewModelScope.launch {
            val current = BookmarkStore.deserialize(
                settingsRepository.folderBookmarksRecord.first(),
            )
            settingsRepository.setFolderBookmarks(
                BookmarkStore.serialize(current.filterNot { it == folder }),
            )
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
    fun openChild(name: String): Unit {
        exitSelection() // a selection never survives leaving its directory
        dispatch { core.openChild(name) }
    }

    /** Back to the parent directory; at the area root the core no-ops (back then exits the screen). */
    fun navigateUp(): Unit {
        exitSelection() // a selection never survives leaving its directory
        dispatch { core.navigateUp() }
    }

    /** Switch storage area (the header chip). An open search is invalidated
     * first — results from one area must never appear in another. A
     * selection is dropped for the same reason. */
    fun switchArea(id: AreaId) {
        exitSearch()
        exitSelection()
        dispatch { core.switchArea(id) }
    }

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

    // ======================================================= Phase 8 search

    /**
     * Search runs on its OWN serial worker — a long walk must never delay
     * navigation (which owns [explorerIo]) — and never touches the core's
     * state: the only shared objects are read-only [StorageArea.list] calls
     * and the volatile generation below. StorageArea references are resolved
     * on the main thread before the walk starts (the established pattern).
     */
    private val searchIo = Dispatchers.IO.limitedParallelism(1)

    private val _search = MutableStateFlow<FilesSearchState>(FilesSearchState.Idle)
    val search: StateFlow<FilesSearchState> = _search.asStateFlow()

    /**
     * Monotonic generation guard: every new query / exit / area-set change
     * bumps it. A walk checks it between directories (cooperative cancel —
     * blocking I/O never observes job cancellation on its own) and again at
     * publication, so a slow stale walk can NEVER overwrite newer state.
     */
    @Volatile private var searchGen = 0L

    private var searchJob: kotlinx.coroutines.Job? = null

    /** Enter search mode. No scan happens — the empty field is the state. */
    fun openSearch() {
        exitSelection() // the search UI replaces the listing; selection is stale
        searchJob?.cancel()
        searchGen += 1
        _search.value = FilesSearchState.Idle
    }

    /**
     * Search the CURRENT storage area for [raw] (name substring,
     * case-insensitive — the query is data, never a path or pattern). A
     * blank query is the input state and never scans. Each call supersedes
     * the previous walk (cancel + generation bump); results publish only
     * while the generation is still current.
     */
    fun search(raw: String) {
        if (raw.isBlank()) {
            searchJob?.cancel()
            searchGen += 1
            _search.value = FilesSearchState.Idle
            return
        }
        val area = currentAreaOrNull() ?: return
        val gen = searchGen + 1
        searchGen = gen
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _search.value = FilesSearchState.Running(raw)
            val outcome = withContext(searchIo) {
                FileSearch.search(
                    area = area,
                    root = PathSafety.validatePath("/")!!,
                    query = raw,
                    isCancelled = { searchGen != gen },
                )
            }
            if (searchGen != gen) return@launch // superseded — stale never wins
            _search.value = when (outcome) {
                null -> FilesSearchState.Idle // cancelled mid-walk (defensive)
                is FileSearch.SearchOutcome.Idle -> FilesSearchState.Idle
                is FileSearch.SearchOutcome.Error -> FilesSearchState.Failed(raw, outcome.reason)
                is FileSearch.SearchOutcome.Matches -> FilesSearchState.Done(outcome)
            }
        }
    }

    /** Leave search mode: the walk is cancelled and its results discarded. */
    fun exitSearch() {
        searchJob?.cancel()
        searchGen += 1
        _search.value = FilesSearchState.Idle
    }

    /**
     * Activate a result: leave search mode, then open the result's PARENT
     * directory (the required behavior) with the tapped entry marked for
     * display. The parent is an [app.pocketshell.files.AreaPath] this
     * search composed inside the selected area — the core re-lists it and
     * fails honestly if it vanished since. It can never name a location
     * outside the area: the type has no representation for that.
     */
    fun openSearchResult(result: FileSearch.SearchResult) {
        exitSearch()
        exitSelection() // landing in the parent directory drops the selection
        dispatch { core.openDirectory(result.parent, result.entry.name) }
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
        // A single mark replaces a multi marker — one operation at a time.
        multiQueue = null
        _pendingCount.value = 1
        _pending.value = PendingTransfer(
            areaId = areaId,
            areaLabel = state.areas.firstOrNull { it.selected }?.label ?: "",
            path = child,
            name = name,
            kind = kind,
            move = move,
        )
    }

    override fun cancelPending() = dropPending()

    /** Drop the whole pending marker — single OR multi — without doing anything. */
    private fun dropPending() {
        multiQueue = null
        _pendingCount.value = 1
        _pending.value = null
    }

    // ------------------------------------------- Phase 8.1 — multi-select

    /** Leave selection mode; the selection and any multi-delete dialog die with it. */
    private fun exitSelection() {
        _selectionMode.value = false
        _selection.value = emptySet()
        _multiDeleteConfirm.value = null
    }

    override fun enterSelectionMode() {
        if (_state.value.entries.isEmpty()) return
        _selectionMode.value = true
    }

    override fun toggleSelected(name: String) {
        if (!_selectionMode.value) return
        _selection.value = if (name in _selection.value) {
            _selection.value - name
        } else {
            _selection.value + name
        }
    }

    override fun selectAll() {
        if (!_selectionMode.value) return
        _selection.value = _state.value.entries.mapTo(HashSet()) { it.name }
    }

    override fun exitSelectionMode() = exitSelection()

    override fun startCopySelected() = startSelectedTransfer(move = false)

    override fun startMoveSelected() = startSelectedTransfer(move = true)

    private fun startSelectedTransfer(move: Boolean) {
        val state = _state.value
        val areaId = state.areaId ?: return
        val dir = state.path ?: return
        val (queue, outcome) = MultiSelectOps.buildTransfers(
            areaId = areaId,
            areaLabel = state.areas.firstOrNull { it.selected }?.label ?: "",
            dir = dir,
            entries = state.entries,
            selected = _selection.value,
            move = move,
        )
        exitSelection() // the clipboard takes over; rows navigate again
        if (queue.isEmpty()) {
            noticeSeq += 1
            _notice.value = OpsNotice(
                text = "Nothing to ${if (move) "move" else "copy"} — the selection no longer matches this folder" +
                    if (outcome.failed.isEmpty()) {
                        "."
                    } else {
                        ": " + outcome.failed.joinToString("; ") { "\"${it.name}\" (${it.reason})" }
                    },
                isError = true,
                seq = noticeSeq,
            )
            return
        }
        multiQueue = queue
        _pendingCount.value = queue.size
        _pending.value = queue.first()
    }

    override fun openMultiDeleteConfirm() {
        val state = _state.value
        val names = state.entries.map { it.name }.filter { it in _selection.value }
        if (names.isEmpty()) return
        _multiDeleteConfirm.value = MultiDeleteConfirm(
            names = names,
            warnings = MultiSelectOps.deleteWarnings(state.entries, names),
        )
    }

    override fun dismissMultiDeleteConfirm() {
        _multiDeleteConfirm.value = null
    }

    override fun confirmMultiDelete() {
        val confirm = _multiDeleteConfirm.value ?: return
        _multiDeleteConfirm.value = null
        val area = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        runOp {
            val outcome = MultiSelectOps.deleteAll(area, dir, confirm.names)
            exitSelection() // the selection is consumed
            ExplorerOps.OpOutcome(
                success = outcome.failed.isEmpty(),
                refused = false,
                message = MultiSelectOps.summary("Deleted", outcome),
            )
        }
    }

    /** Uniform honest banner mapping for a finished multi outcome. */
    private fun multiOutcomeToOpOutcome(
        verb: String,
        outcome: MultiSelectOps.MultiOutcome,
        cancelledAt: String?,
    ): ExplorerOps.OpOutcome = ExplorerOps.OpOutcome(
        success = outcome.failed.isEmpty(),
        refused = false,
        message = MultiSelectOps.summary(verb, outcome, cancelledAt),
    )

    override fun pasteHere() {
        val targetArea = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        val queue = multiQueue
        if (queue != null) {
            // Phase 8.1: the multi clipboard — items run in order through the
            // SAME check/execute engine as the single paste; the first
            // collision interrupts for the same Replace/Cancel question.
            val sourceArea = areaById[queue.first().areaId] ?: run {
                dropPending()
                return
            }
            runOp {
                when (
                    val pass = MultiSelectOps.pastePass(
                        sourceArea = sourceArea,
                        queue = queue,
                        targetArea = targetArea,
                        targetDir = dir,
                    )
                ) {
                    is MultiSelectOps.PastePassResult.Completed -> {
                        dropPending()
                        multiOutcomeToOpOutcome(
                            if (queue.first().move) "Moved" else "Copied",
                            pass.outcome,
                            cancelledAt = null,
                        )
                    }
                    is MultiSelectOps.PastePassResult.NeedsReplace -> {
                        _confirmReplace.value = ReplaceRequest(
                            opLabel = if (pass.current.move) "Move" else "Copy",
                            name = pass.current.name,
                            existingKind = pass.existingKind,
                            command = OpsCommand.PasteMulti(
                                current = pass.current,
                                target = pass.target,
                                targetDir = dir,
                                targetAreaId = targetArea.id,
                                rest = pass.rest,
                                done = pass.done,
                            ),
                        )
                        null // the outcome continues after the user answers
                    }
                }
            }
            return
        }
        val pending = _pending.value ?: return
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
        if (!replace) {
            // Phase 8.1: cancelling a MULTI paste still reports what already
            // landed — a partial execution must never go silent. A single
            // paste cancel stays silent (nothing ever happened).
            val command = request.command
            if (command is OpsCommand.PasteMulti) {
                val verb = if (command.current.move) "Moved" else "Copied"
                runOp {
                    dropPending()
                    multiOutcomeToOpOutcome(verb, command.done, cancelledAt = command.current.name)
                }
            }
            return
        }
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
            is OpsCommand.PasteMulti -> {
                val sourceArea = areaById[command.current.areaId] ?: return
                val targetArea = areaById[command.targetAreaId] ?: return
                runOp {
                    // The confirmed replacement — the SAME delete-then-perform
                    // composition the single paste has always used.
                    val first = ExplorerOps.executePaste(
                        sourceArea, command.current, targetArea, command.target, replace = true,
                    )
                    var outcome = if (first.success) {
                        command.done.copy(ok = command.done.ok + 1)
                    } else {
                        command.done.copy(
                            failed = command.done.failed +
                                MultiSelectOps.MultiFailure(command.current.name, first.message),
                        )
                    }
                    // Continue the rest against the PINNED target directory.
                    if (command.rest.isNotEmpty()) {
                        when (
                            val pass = MultiSelectOps.pastePass(
                                sourceArea = sourceArea,
                                queue = command.rest,
                                targetArea = targetArea,
                                targetDir = command.targetDir,
                            )
                        ) {
                            is MultiSelectOps.PastePassResult.Completed ->
                                outcome = outcome + pass.outcome
                            is MultiSelectOps.PastePassResult.NeedsReplace -> {
                                outcome = outcome + pass.done
                                _confirmReplace.value = ReplaceRequest(
                                    opLabel = if (pass.current.move) "Move" else "Copy",
                                    name = pass.current.name,
                                    existingKind = pass.existingKind,
                                    command = OpsCommand.PasteMulti(
                                        current = pass.current,
                                        target = pass.target,
                                        targetDir = command.targetDir,
                                        targetAreaId = command.targetAreaId,
                                        rest = pass.rest,
                                        done = outcome,
                                    ),
                                )
                                return@runOp null // chained on the next answer
                            }
                        }
                    }
                    dropPending()
                    multiOutcomeToOpOutcome(
                        if (command.current.move) "Moved" else "Copied",
                        outcome,
                        cancelledAt = null,
                    )
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
            is OpsCommand.CompressZip -> {
                val area = areaById[command.areaId] ?: return
                startZipJob(ZipProgress.ZipKind.COMPRESS) { isCancelled ->
                    // Confirmed Replace = delete the old archive, then run.
                    val removed = area.delete(command.target)
                    if (!removed.success) {
                        return@startZipJob ZipArchiveOps.ZipResult(
                            false,
                            false,
                            "Could not replace ${command.target.value}: " +
                                "${removed.reason ?: "delete failed"} — nothing was changed.",
                        )
                    }
                    ZipArchiveOps.compress(
                        area = area,
                        sources = command.sources,
                        target = command.target,
                        isCancelled = isCancelled,
                        onProgress = ::onZipProgress,
                    )
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

    // ================================================ Phase 6 — quick editor

    /**
     * Resolve a listing FILE into a launch for the quick text editor:
     * validate + resolve ONLY — no I/O, no state change (the same discipline
     * as every path composition in this class). Null when the current state
     * cannot launch (no area/location, the name is not in the listing, it is
     * not a regular file, or the path does not validate) — the router simply
     * does not navigate. Regular files only: symlinks are never opened in
     * the editor (a safe-target read would work, but a write-through-symlink
     * save is refused by the engine, so the honest surface is: not offered).
     *
     * The returned launch carries the SAME StorageArea instance this class
     * operates on; the editor runs on its own serial worker and relies on
     * the area's atomic writes plus its own (size, mtime) save gate.
     */
    fun editorLaunch(name: String): EditorLaunch? {
        val state = _state.value
        val areaId = state.areaId ?: return null
        val dir = state.path ?: return null
        val kind = state.entries.firstOrNull { it.name == name }?.kind ?: return null
        if (kind != EntryKind.FILE) return null
        val area = areaById[areaId] ?: return null
        val child = ExplorerOps.composeChild(dir, name) ?: return null
        return EditorLaunch(
            area = area,
            areaId = areaId,
            areaLabel = state.areas.firstOrNull { it.selected }?.label ?: "",
            path = child,
            name = name,
        )
    }

    // ============================================ Phase 7 — Open Terminal Here

    /**
     * Resolve the TAPPED directory entry ([selectedEntry], from the action
     * sheet) into an "Open Terminal Here" launch — validate + resolve ONLY
     * (the [editorLaunch] discipline; no I/O, no state change, no session
     * creation: this class resolves intent, the TerminalViewModel owns
     * sessions).
     *
     * p7.1 — the launch opens THE TAPPED FOLDER, not the browsed location
     * (the p7.0 device report: tapping the action on a folder landed the
     * terminal in the browsed parent instead). The resolution is the pure
     * [terminalLaunchDirectory] — the same listing the user tapped from,
     * the same DIRECTORY check the sheet applies, and the ONE validated
     * child composition ([ExplorerOps.composeChild]); a stale sheet (the
     * entry no longer in the listing) resolves to an honest refusal, never
     * a somewhere-else launch.
     *
     * The gate is the pure [openTerminalHereProblem]: only the guest Linux
     * area can ever launch. For an Android area the honest boundary message
     * ([TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE]) is surfaced here as
     * this class's standard notice and returned as [TerminalLaunchResolution.NotSupported]
     * — the UI stays in Files and no fake launch can happen. The
     * [TerminalLaunchResolution.Ready] payload carries the validated child
     * [AreaPath]; no second validation, no second path representation.
     */
    override fun terminalLaunch(selectedEntry: String): TerminalLaunchResolution {
        val state = _state.value
        val areaId = state.areaId ?: return TerminalLaunchResolution.NotSupported(
            "Open a folder first — Terminal opens in the folder you choose.",
        )
        val dir = state.path ?: return TerminalLaunchResolution.NotSupported(
            "Open a folder first — Terminal opens in the folder you choose.",
        )
        val problem = openTerminalHereProblem(areaId.kind)
        if (problem != null) {
            noticeSeq += 1
            _notice.value = OpsNotice(problem, isError = true, seq = noticeSeq)
            return TerminalLaunchResolution.NotSupported(problem)
        }
        val directory = terminalLaunchDirectory(dir, state.entries, selectedEntry)
            ?: return TerminalLaunchResolution.NotSupported(
                "That folder is no longer in the listing — refresh and try again.",
            )
        return TerminalLaunchResolution.Ready(
            TerminalLaunch(
                areaId = areaId,
                kind = areaId.kind,
                directory = directory,
            ),
        )
    }

    /**
     * The TOOLBAR "open the folder I am browsing" launch (owner iteration):
     * unlike [terminalLaunch] — which resolves a TAPPED child (p7.1) — this
     * IS the browsed location, so no listing lookup is needed; the directory
     * is the explorer's current validated [AreaPath] verbatim. Same pure
     * area-kind gate, same honest NotSupported (the toolbar hides itself in
     * Android areas, so the refusal here is defensive only — no notice).
     */
    fun terminalLaunchHere(): TerminalLaunchResolution {
        val state = _state.value
        val areaId = state.areaId ?: return TerminalLaunchResolution.NotSupported(
            "Open a folder first — Terminal opens in the folder you choose.",
        )
        val dir = state.path ?: return TerminalLaunchResolution.NotSupported(
            "Open a folder first — Terminal opens in the folder you choose.",
        )
        val problem = openTerminalHereProblem(areaId.kind)
        if (problem != null) return TerminalLaunchResolution.NotSupported(problem)
        return TerminalLaunchResolution.Ready(
            TerminalLaunch(areaId = areaId, kind = areaId.kind, directory = dir),
        )
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
        exitSearch() // the area set is about to change — a walk is stale
        exitSelection() // the area set is about to change — a selection is stale
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
        exitSearch() // the area set is about to change — a walk is stale
        exitSelection() // the area set is about to change — a selection is stale
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

    // ============================================ M7.2-A — breadcrumb navigation

    /**
     * Navigate to an ancestor/folder location of the CURRENT area (a crumb
     * tap). [target] is a validated [AreaPath] derived from the location the
     * listing itself reports — the same safety surface as [openChild]
     * ([ExplorerCore.openDirectory] re-lists it and fails honestly if it
     * vanished). A selection never survives leaving its directory.
     */
    fun openBreadcrumb(target: app.pocketshell.files.AreaPath) {
        exitSelection()
        dispatch { core.openDirectory(target) }
    }

    // ================================= M7.2-A — archives (ZIP) + Open With engine

    /**
     * Archive work gets its OWN serial worker (the [searchIo] discipline):
     * a long compress/extract must never delay navigation, which owns
     * [explorerIo]. One archive operation at a time — a second request
     * while one runs is refused honestly.
     */
    private val zipIo = Dispatchers.IO.limitedParallelism(1)

    private val _zipProgress = MutableStateFlow<ZipProgress?>(null)
    override val zipProgress: StateFlow<ZipProgress?> = _zipProgress.asStateFlow()

    private val _compressDialog = MutableStateFlow<CompressDialogState?>(null)
    override val compressDialog: StateFlow<CompressDialogState?> = _compressDialog.asStateFlow()

    private val _extractDialog = MutableStateFlow<ExtractDialogState?>(null)
    override val extractDialog: StateFlow<ExtractDialogState?> = _extractDialog.asStateFlow()

    private val _openWithReady = MutableStateFlow<OpenWithReady?>(null)
    override val openWithReady: StateFlow<OpenWithReady?> = _openWithReady.asStateFlow()

    /**
     * Monotonic generation guard (the [searchGen] discipline): [cancelZip]
     * bumps it, the running operation observes the change between entries
     * (cooperative cancel — blocking I/O never sees job cancellation) and
     * stops with an honest partial result.
     */
    @Volatile private var zipGen = 0L

    /** Throttle bookkeeping for [onZipProgress] (written from [zipIo]). */
    @Volatile private var lastProgressPublishMillis = 0L

    private fun postNotice(text: String, isError: Boolean) {
        noticeSeq += 1
        _notice.value = OpsNotice(text = text, isError = isError, seq = noticeSeq)
    }

    private fun notice(text: String) = postNotice(text, isError = true)

    // ------------------------------------------------------------- compress

    override fun requestCompress(names: List<String>) {
        val state = _state.value
        val dir = state.path
        if (dir == null || names.isEmpty()) return
        if (_zipProgress.value != null) {
            notice("An archive operation is already running — cancel it first.")
            return
        }
        // Only names the CURRENT listing genuinely shows are compressible;
        // symlinks are refused up front (they are never archived).
        val valid = names.filter { name ->
            state.entries.firstOrNull { it.name == name }?.let {
                it.kind == EntryKind.FILE || it.kind == EntryKind.DIRECTORY
            } == true
        }
        if (valid.isEmpty()) {
            notice("Nothing compressible is selected — symlinks and special files are not archived.")
            return
        }
        val suggested = if (valid.size == 1) "${valid.first()}.zip" else "archive.zip"
        _compressDialog.value = CompressDialogState(
            targetDir = dir,
            names = valid,
            suggestedName = suggested,
        )
    }

    override fun dismissCompressDialog() {
        _compressDialog.value = null
    }

    override fun submitCompress(archiveName: String) {
        val dialog = _compressDialog.value ?: return
        ExplorerOps.nameError(archiveName)?.let {
            _compressDialog.value = dialog.copy(error = it)
            return
        }
        if (ZipArchiveOps.isZipName(archiveName).not()) {
            _compressDialog.value = dialog.copy(error = "The archive name must end with \".zip\".")
            return
        }
        val area = currentAreaOrNull() ?: return
        val target = ExplorerOps.composeChild(dialog.targetDir, archiveName) ?: run {
            _compressDialog.value = dialog.copy(error = "\"$archiveName\" is not a valid archive name here.")
            return
        }
        val sources = dialog.names.mapNotNull { ExplorerOps.composeChild(dialog.targetDir, it) }
        if (sources.size != dialog.names.size) {
            _compressDialog.value = dialog.copy(error = "A selection is no longer addressable — reopen the dialog.")
            return
        }
        _compressDialog.value = null
        // The collision probe is a blocking area call — it belongs on IO
        // (the established discipline), never on the main thread.
        viewModelScope.launch {
            val existing = withContext(explorerIo) { area.stat(target) }
            if (existing != null) {
                _confirmReplace.value = ReplaceRequest(
                    opLabel = "Compress",
                    name = archiveName,
                    existingKind = existing.kind,
                    command = OpsCommand.CompressZip(
                        areaId = area.id,
                        targetDir = dialog.targetDir,
                        sources = sources,
                        target = target,
                    ),
                )
                return@launch
            }
            startZipJob(ZipProgress.ZipKind.COMPRESS) { isCancelled ->
                ZipArchiveOps.compress(
                    area = area,
                    sources = sources,
                    target = target,
                    isCancelled = isCancelled,
                    onProgress = ::onZipProgress,
                )
            }
        }
    }

    // -------------------------------------------------------------- extract

    override fun requestExtract(name: String) {
        val state = _state.value
        if (state.path == null) return
        val entry = state.entries.firstOrNull { it.name == name }
        if (entry?.kind != EntryKind.FILE || !ZipArchiveOps.isZipName(name)) return
        if (_zipProgress.value != null) {
            notice("An archive operation is already running — cancel it first.")
            return
        }
        _extractDialog.value = ExtractDialogState(
            zipName = name,
            suggestedFolder = name.removeSuffix(".zip").ifBlank { "extracted" },
        )
    }

    override fun setExtractReplace(replace: Boolean) {
        val dialog = _extractDialog.value ?: return
        _extractDialog.value = dialog.copy(replace = replace)
    }

    override fun dismissExtractDialog() {
        _extractDialog.value = null
    }

    override fun submitExtract(folderName: String) {
        val dialog = _extractDialog.value ?: return
        val area = currentAreaOrNull() ?: return
        val dir = _state.value.path ?: return
        ExplorerOps.nameError(folderName)?.let {
            _extractDialog.value = dialog.copy(error = it)
            return
        }
        val destination = ExplorerOps.composeChild(dir, folderName) ?: run {
            _extractDialog.value = dialog.copy(error = "\"$folderName\" is not a valid folder name here.")
            return
        }
        val zipPath = ExplorerOps.composeChild(dir, dialog.zipName) ?: run {
            _extractDialog.value = dialog.copy(error = "The archive is no longer addressable here.")
            return
        }
        _extractDialog.value = null
        startZipJob(ZipProgress.ZipKind.EXTRACT) { isCancelled ->
            // The destination folder is created through the area itself when
            // missing; extract refuses anything that is not a directory.
            if (area.stat(destination) == null) {
                val created = area.createDirectory(destination)
                if (!created.success) {
                    return@startZipJob ZipArchiveOps.ZipResult(
                        false,
                        false,
                        "Could not create ${destination.value}: ${created.reason ?: "creation failed"}.",
                    )
                }
            }
            ZipArchiveOps.extract(
                area = area,
                zipPath = zipPath,
                destinationDir = destination,
                isCancelled = isCancelled,
                onProgress = ::onZipProgress,
                replace = dialog.replace,
            )
        }
    }

    override fun cancelZip() {
        zipGen += 1
    }

    // ------------------------------------------------------- shared zip job

    private fun startZipJob(kind: ZipProgress.ZipKind, work: (isCancelled: () -> Boolean) -> ZipArchiveOps.ZipResult) {
        if (_zipProgress.value != null) {
            notice("An archive operation is already running — cancel it first.")
            return
        }
        zipGen += 1
        val gen = zipGen
        viewModelScope.launch {
            _zipProgress.value = ZipProgress(kind, null, 0, 0, null)
            val result = withContext(zipIo) {
                work { zipGen != gen }
            }
            _zipProgress.value = null
            postNotice(
                buildString {
                    append(result.message)
                    val shown = result.warnings.take(3)
                    if (shown.isNotEmpty()) {
                        append(" ")
                        append(shown.joinToString(" "))
                        val rest = result.warnings.size - shown.size
                        if (rest > 0) append(" And $rest more.")
                    }
                },
                isError = !result.success,
            )
            // The listing must show what landed — refresh through the ONE
            // serial dispatch slot (cheap: one area.list of the current dir).
            dispatch { core.refresh() }
        }
    }

    /**
     * Progress ticks arrive from [zipIo]; the StateFlow itself is
     * thread-safe, but per-64KiB-chunk updates would recompose the banner
     * hundreds of times a second — throttled to ~4 Hz plus entry changes.
     */
    private fun onZipProgress(progress: ZipArchiveOps.Progress) {
        val kind = _zipProgress.value?.kind ?: return
        val now = System.currentTimeMillis()
        val last = _zipProgress.value
        val entryChanged = last?.currentName != progress.currentName ||
            last?.entriesDone != progress.entriesDone
        if (!entryChanged &&
            now - lastProgressPublishMillis < 250 &&
            progress.bytesDone - (last?.bytesDone ?: 0) < 512 * 1024
        ) {
            return
        }
        lastProgressPublishMillis = now
        _zipProgress.value = ZipProgress(
            kind = kind,
            currentName = progress.currentName,
            entriesDone = progress.entriesDone,
            bytesDone = progress.bytesDone,
            totalBytes = progress.totalBytes,
        )
    }

    // ---------------------------------------------------------- open with

    override fun requestOpenWith(name: String) {
        val state = _state.value
        val area = currentAreaOrNull() ?: return
        val dir = state.path ?: return
        val kind = state.entries.firstOrNull { it.name == name }?.kind
        if (kind != EntryKind.FILE) {
            notice("Only files can be opened in an Android app.")
            return
        }
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
                is FileShareOps.Staging.Error -> notice(staged.reason)
                is FileShareOps.Staging.Ok -> {
                    val application = getApplication<Application>()
                    val mimeType = FileShareOps.guessMimeType(name)
                    // Honest no-handler detection BEFORE launching: resolve
                    // ACTION_VIEW for the staged content URI's type. (The
                    // app targets SDK 28, so Android 11+ package filtering
                    // does not hide resolvers from this probe.)
                    val probe = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(
                            Uri.parse("content://${FileShareOps.FILE_PROVIDER_AUTHORITY}/staging"),
                            mimeType,
                        )
                    val handler = runCatching { probe.resolveActivity(application.packageManager) }
                        .getOrNull()
                    if (handler == null) {
                        notice(
                            "No installed Android app can open \"$name\" ($mimeType).",
                        )
                        return@launch
                    }
                    _openWithReady.value = OpenWithReady(
                        name = name,
                        uriString = FileProvider.getUriForFile(
                            application,
                            FileShareOps.FILE_PROVIDER_AUTHORITY,
                            staged.file,
                        ).toString(),
                        mimeType = mimeType,
                    )
                }
            }
        }
    }

    override fun consumeOpenWithReady() {
        _openWithReady.value = null
    }

    override fun openWithLaunchFailed(reason: String) {
        notice(reason)
    }
}
