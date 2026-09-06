package app.pocketshell.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.EntryKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.FileSearch
import app.pocketshell.files.FilesSearchState
import app.pocketshell.files.FsEntry
import app.pocketshell.files.saf.SafFolderState
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.theme.TerminalTheme
import java.util.Locale

/**
 * M7.0.0 Phase 3 + Phase 4 + Phase 5 — the File Explorer.
 *
 * Mobile-first and touch-first, deliberately NOT a desktop file manager:
 * one column, directories first (the Phase 2 engine's order), size as the
 * only per-row metadata, and exactly these interactions —
 *
 *   tap a directory    → open it (never beyond the logical area boundary)
 *   system back        → parent directory, then out of the screen
 *   tap a file         → the action sheet (facts + Copy/Move/Share/Export/…)
 *   long-press / "⋮"   → the same contextual action sheet (never gesture-only)
 *   "+"                → New Folder / New File / Import file… in the current
 *                        directory
 *   area switcher      → Linux / PocketShell Downloads (the app-owned shelf)
 *                        / user-granted Android folders (label = the
 *                        folder's own name, e.g. a real shared "Download")
 *                        (+ "Add Android folder…" through the SYSTEM picker)
 *   search (Phase 8)   → header search icon; the search mode REPLACES the
 *                        listing with a focused name search of the CURRENT
 *                        area only (recursive, literal case-insensitive
 *                        substring — the query is data, never a pattern);
 *                        tapping a result exits search and opens the
 *                        result's PARENT directory with the entry marked
 *   Open Terminal Here → Phase 7: a real launch offered for directory
 *                        entries in the PocketShell Linux area only — it
 *                        opens THE TAPPED FOLDER (p7.1); Android-owned
 *                        areas show the honest boundary note instead
 *
 * The UI performs ZERO filesystem operations: it renders [ExplorerCore.State]
 * plus the [FilesOpsSurface] flows and dispatches intents only. Errors are
 * rendered verbatim — honest, never swallowed, never fatal.
 */
@Composable
fun FilesScreen(
    state: ExplorerCore.State,
    guestUnavailable: Boolean,
    ops: FilesOpsSurface,
    /** Phase 8: the current search publication state (Idle/Running/Done/Failed). */
    search: FilesSearchState,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenChild: (String) -> Unit,
    onSwitchArea: (AreaId) -> Unit,
    onRefresh: () -> Unit,
    /** Phase 8: enter search mode (no scan — the empty field is the state). */
    onSearchOpen: () -> Unit,
    /** Phase 8: run/replace the search for [String] (blank = input state). */
    onSearchQuery: (String) -> Unit,
    /** Phase 8: leave search mode — the walk is cancelled, results dropped. */
    onSearchExit: () -> Unit,
    /** Phase 8: activate a result — exit search, open its PARENT directory. */
    onOpenSearchResult: (FileSearch.SearchResult) -> Unit,
    /** Phase 6: open the listing FILE [name] in the quick text editor. */
    onOpenFile: (String) -> Unit,
    /**
     * Phase 7 (p7.1): open a Linux terminal in the TAPPED directory entry
     * [name] (the launch resolves that folder under the browsed location —
     * never the browsed location itself). The caller resolves the launch
     * through the ops surface and navigates only after the session really
     * exists — this screen never talks to the TerminalViewModel itself.
     */
    onOpenTerminal: (String) -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending by ops.pending.collectAsStateWithLifecycle()
    val notice by ops.notice.collectAsStateWithLifecycle()
    val confirmReplace by ops.confirmReplace.collectAsStateWithLifecycle()
    val newDialog by ops.newDialog.collectAsStateWithLifecycle()
    val renameDialog by ops.renameDialog.collectAsStateWithLifecycle()
    val deleteConfirm by ops.deleteConfirm.collectAsStateWithLifecycle()
    val safFolders by ops.safFolders.collectAsStateWithLifecycle()

    // Phase 8.1: selection mode + the multi surfaces.
    val selectionMode by ops.selectionMode.collectAsStateWithLifecycle()
    val selection by ops.selection.collectAsStateWithLifecycle()
    val multiDeleteConfirm by ops.multiDeleteConfirm.collectAsStateWithLifecycle()
    val pendingCount by ops.pendingCount.collectAsStateWithLifecycle()

    // Phase 5: the Android-side launchers (folder picker, import picker) and
    // the share/save effects — created HERE so this screen keeps owning all
    // its wiring and callers stay unchanged.
    val bridge = rememberFilesBridgeLaunchers(ops)
    FilesBridgeEffects(ops)

    // The tapped / long-pressed entry whose action sheet is open.
    var selected by remember { mutableStateOf<FsEntry?>(null) }

    // Phase 8: search mode is a UI-local rendering switch; the search STATE
    // (walk, results, cancellation) lives in the ViewModel. Opening clears
    // the field; leaving cancels the walk through onSearchExit.
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    fun closeSearch() {
        searchMode = false
        onSearchExit()
    }
    fun openSearchMode() {
        searchQuery = ""
        searchMode = true
        onSearchOpen()
    }

    // The Android folder whose grant is gone WHILE it is the current area —
    // the honest no-fake-empty-folder surface.
    val revokedFolder = if (state.areaId?.kind == AreaKind.ANDROID_DOCUMENT_TREE) {
        safFolders.firstOrNull {
            it.uri == state.areaId?.key && it.state == SafFolderState.REVOKED
        }
    } else {
        null
    }

    // Back = exit search while it is open; otherwise parent directory while
    // there is one INSIDE the area; at the area root the handler releases
    // back to the app router (→ Home). The logical area boundary is never
    // crossed by back navigation.
    BackHandler(enabled = searchMode) { closeSearch() }
    BackHandler(enabled = !searchMode && state.canNavigateUp) { onNavigateUp() }
    // Registered last, so while selection mode is on, back LEAVES selection
    // before it ever navigates up or out of the screen.
    BackHandler(enabled = selectionMode) { ops.exitSelectionMode() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.screenBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = HomeTokens.contentMaxWidth)
                .align(Alignment.TopCenter),
        ) {
            FilesHeader(
                state = state,
                searchMode = searchMode,
                selectionMode = selectionMode,
                onToggleSearch = { if (searchMode) closeSearch() else openSearchMode() },
                onToggleSelect = {
                    if (selectionMode) ops.exitSelectionMode() else ops.enterSelectionMode()
                },
                onBack = onBack,
                onSwitchArea = onSwitchArea,
                onAddSafFolder = bridge.pickFolder,
            )

            if (searchMode) {
                SearchFieldRow(
                    areaName = state.areaName,
                    query = searchQuery,
                    onQuery = {
                        searchQuery = it
                        onSearchQuery(it)
                    },
                    onExit = { closeSearch() },
                )
            } else if (selectionMode) {
                // Phase 8.1: the selection bar replaces the location row —
                // the mode's actions live where navigation normally does.
                SelectionBar(
                    count = selection.size,
                    onExit = ops::exitSelectionMode,
                    onSelectAll = ops::selectAll,
                    onCopy = ops::startCopySelected,
                    onMove = ops::startMoveSelected,
                    onDelete = ops::openMultiDeleteConfirm,
                )
            } else {
                LocationRow(
                    state = state,
                    onNavigateUp = onNavigateUp,
                    onNewFolder = { ops.openNewDialog(folder = true) },
                    onNewFile = { ops.openNewDialog(folder = false) },
                    onImportFile = bridge.pickImportFile,
                )
            }

            pending?.let { marker ->
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PendingBanner(
                        pending = marker,
                        count = pendingCount,
                        canPaste = state.path != null,
                        onPaste = ops::pasteHere,
                        onCancel = ops::cancelPending,
                    )
                }
            }

            notice?.let { item ->
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OpsNoticeBanner(notice = item, onDismiss = ops::dismissNotice)
                }
            }

            if (guestUnavailable && state.areaId != null) {
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    MidnightBanner(
                        message = "PocketShell Linux is not installed yet — Linux files appear here after the runtime is installed.",
                        failed = false,
                        actions = {
                            TextButton(onClick = onOpenDiagnostics) {
                                Text(
                                    "Diagnostics",
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 13.sp,
                                    color = HomeTokens.accent,
                                )
                            }
                        },
                    )
                }
            }

            revokedFolder?.let { folder ->
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SafRevokedBanner(
                        folder = folder,
                        onReconnect = bridge.pickFolder,
                        onRemove = { ops.removeSafFolder(folder.uri) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (searchMode) {
                // Phase 8: the search mode REPLACES the listing — the
                // explorer's own listing/error/loading surfaces are fully
                // hidden while the field is open.
                SearchBody(
                    search = search,
                    areaName = state.areaName,
                    onOpenResult = { result ->
                        searchMode = false
                        onOpenSearchResult(result)
                    },
                )
            } else when {
                // No area at all: honest dead-end with the real way out.
                state.areaId == null && !state.loading -> NoStorageState(onOpenDiagnostics)

                state.loading && state.entries.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = HomeTokens.accent,
                    )
                }

                state.error != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    MidnightBanner(
                        message = state.error ?: "",
                        failed = true,
                        actions = {
                            TextButton(onClick = onRefresh) {
                                Text(
                                    "Retry",
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 13.sp,
                                    color = HomeTokens.accent,
                                )
                            }
                        },
                    )
                }

                state.entries.isEmpty() && state.path != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Empty directory",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTokens.textDim,
                    )
                }

                else -> Listing(
                    entries = state.entries,
                    highlight = state.highlight,
                    selectionMode = selectionMode,
                    selected = selection,
                    onOpenChild = onOpenChild,
                    onSelect = { selected = it },
                    onToggle = ops::toggleSelected,
                )
            }
        }
    }

    // ------------------------------------------------- Phase 4 op surfaces

    selected?.let { entry ->
        EntryActionSheet(
            entry = entry,
            onDismiss = { selected = null },
            handlers = EntryActionHandlers(
                onOpen = when (entry.kind) {
                    EntryKind.DIRECTORY -> {
                        { selected = null; onOpenChild(entry.name) }
                    }
                    EntryKind.FILE -> {
                        { selected = null; onOpenFile(entry.name) }
                    }
                    else -> null
                },
                onNewFolder = if (entry.kind == EntryKind.DIRECTORY) {
                    { selected = null; ops.openNewDialogInside(entry.name, folder = true) }
                } else {
                    null
                },
                onNewFile = if (entry.kind == EntryKind.DIRECTORY) {
                    { selected = null; ops.openNewDialogInside(entry.name, folder = false) }
                } else {
                    null
                },
                onCopy = { selected = null; ops.startCopy(entry.name) },
                onMove = { selected = null; ops.startMove(entry.name) },
                onShare = if (entry.kind == EntryKind.FILE) {
                    { selected = null; ops.requestShare(entry.name) }
                } else {
                    null
                },
                onExport = if (entry.kind == EntryKind.FILE) {
                    { selected = null; ops.requestExport(entry.name) }
                } else {
                    null
                },
                onRename = { selected = null; ops.openRenameDialog(entry.name) },
                onDelete = { selected = null; ops.openDeleteConfirm(entry.name) },
                onEdit = if (entry.kind == EntryKind.FILE) {
                    { selected = null; onOpenFile(entry.name) }
                } else {
                    null
                },
                // Phase 7: a REAL terminal launch for directories inside the
                // PocketShell Linux area. Android-owned areas keep the
                // handler null — the sheet then shows the honest boundary
                // note instead of an actionable launch. p7.1: the tapped
                // entry's NAME travels with the intent so the launch opens
                // THIS folder.
                onTerminal = if (
                    entry.kind == EntryKind.DIRECTORY &&
                    state.areaId?.kind == AreaKind.GUEST_LINUX
                ) {
                    { selected = null; onOpenTerminal(entry.name) }
                } else {
                    null
                },
            ),
        )
    }

    confirmReplace?.let { request ->
        ConfirmReplaceDialog(
            request = request,
            onReplace = { ops.resolveReplace(replace = true) },
            onCancel = { ops.resolveReplace(replace = false) },
        )
    }

    deleteConfirm?.let { confirm ->
        ConfirmDeleteDialog(
            state = confirm,
            onDelete = ops::confirmDelete,
            onCancel = ops::dismissDeleteConfirm,
        )
    }

    multiDeleteConfirm?.let { confirm ->
        ConfirmMultiDeleteDialog(
            state = confirm,
            onDelete = ops::confirmMultiDelete,
            onCancel = ops::dismissMultiDeleteConfirm,
        )
    }

    renameDialog?.let { dialog ->
        NamePromptDialog(
            title = "Rename \"${dialog.currentName}\"",
            initial = dialog.currentName,
            confirmLabel = "Rename",
            error = dialog.error,
            onConfirm = ops::submitRename,
            onDismiss = ops::dismissRenameDialog,
        )
    }

    newDialog?.let { dialog ->
        NamePromptDialog(
            title = if (dialog.folder) {
                "New folder in ${dialog.targetDir.value}"
            } else {
                "New file in ${dialog.targetDir.value}"
            },
            initial = "",
            confirmLabel = if (dialog.folder) "Create" else "Create",
            error = dialog.error,
            onConfirm = ops::submitNewName,
            onDismiss = ops::dismissNewDialog,
        )
    }
}

// ------------------------------------------------------------------- header

/**
 * Back chevron + mono page title + the area switcher chip. The chip only
 * appears when more than one area exists (Linux-first Phase 3 usually has
 * two: Linux + Downloads); the current one carries a check. Phase 5: the
 * switcher ends with "Add Android folder…" — the only way in, always
 * through the system picker.
 */
@Composable
private fun FilesHeader(
    state: ExplorerCore.State,
    searchMode: Boolean,
    selectionMode: Boolean,
    onToggleSearch: () -> Unit,
    onToggleSelect: () -> Unit,
    onBack: () -> Unit,
    onSwitchArea: (AreaId) -> Unit,
    onAddSafFolder: () -> Unit,
) {
    var switcherOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Back") { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = HomeTokens.textDim,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Files",
            fontFamily = TerminalTheme.mono,
            fontWeight = FontWeight.Medium,
            fontSize = 21.sp,
            letterSpacing = 0.3.sp,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.weight(1f))

        // Phase 8.1: the select affordance lives beside search; while the
        // selection mode is open it becomes the exit. Hidden during search
        // mode (the two modes never mix) and for empty locations.
        if (!searchMode && state.entries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = if (selectionMode) "Exit selection" else "Select items") {
                        onToggleSelect()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DoneAll,
                    contentDescription = if (selectionMode) "Exit selection" else "Select items",
                    tint = if (selectionMode) HomeTokens.accent else HomeTokens.textDim,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Phase 8: the search affordance lives in the header; while the
        // search mode is open it becomes the exit (the field row carries
        // its own clear/close too). The area chip is hidden in search mode
        // so the scope stays unambiguous — the field names the area.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = if (searchMode) "Close search" else "Search files") {
                    onToggleSearch()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (searchMode) Icons.Outlined.Close else Icons.Outlined.Search,
                contentDescription = if (searchMode) "Close search" else "Search files",
                tint = if (searchMode) HomeTokens.accent else HomeTokens.textDim,
                modifier = Modifier.size(22.dp),
            )
        }

        if (state.areas.size > 1 && !searchMode) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(HomeTokens.chipRadius))
                        .background(HomeTokens.surfaceEnv)
                        .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
                        .clickable(role = Role.Button, onClickLabel = "Switch storage area") {
                            switcherOpen = true
                        }
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.areas.firstOrNull { it.selected }?.label ?: "Area",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 13.sp,
                        color = HomeTokens.textPrimary,
                        maxLines = 1,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        tint = HomeTokens.textDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = switcherOpen,
                    onDismissRequest = { switcherOpen = false },
                ) {
                    state.areas.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 14.sp,
                                    color = HomeTokens.textPrimary,
                                )
                            },
                            trailingIcon = {
                                if (option.selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = HomeTokens.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            onClick = {
                                switcherOpen = false
                                if (!option.selected) onSwitchArea(option.id)
                            },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Add Android folder…",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 14.sp,
                                color = HomeTokens.accent,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = null,
                                tint = HomeTokens.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            switcherOpen = false
                            onAddSafFolder()
                        },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------- location row

/**
 * The up-to-parent affordance, the current path, and the "+" that makes
 * New Folder / New File / Import file… reachable in the CURRENT directory
 * (the only way creation and import work in an empty directory).
 */
@Composable
private fun LocationRow(
    state: ExplorerCore.State,
    onNavigateUp: () -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
    onImportFile: () -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.canNavigateUp) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Up one level") { onNavigateUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = "Up one level",
                    tint = HomeTokens.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(40.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = state.path?.value ?: "",
            fontFamily = TerminalTheme.mono,
            fontSize = 13.sp,
            color = HomeTokens.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )
        if (state.path != null) {
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "New folder or file") {
                            createOpen = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "New folder or file",
                        tint = HomeTokens.textDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = createOpen,
                    onDismissRequest = { createOpen = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "New folder",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 14.sp,
                                color = HomeTokens.textPrimary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.CreateNewFolder,
                                contentDescription = null,
                                tint = HomeTokens.textDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            createOpen = false
                            onNewFolder()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "New file",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 14.sp,
                                color = HomeTokens.textPrimary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.NoteAdd,
                                contentDescription = null,
                                tint = HomeTokens.textDim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            createOpen = false
                            onNewFile()
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Import file…",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 14.sp,
                                color = HomeTokens.accent,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                                tint = HomeTokens.accent,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            createOpen = false
                            onImportFile()
                        },
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

// ------------------------------------------------------- selection (P8.1)

/**
 * The selection-mode action bar: replaces the location row while selecting.
 * Copy/Move/Delete act on the selection (disabled while nothing is picked);
 * Select all grabs the whole listing; the X leaves the mode. It performs
 * ZERO filesystem work — every button dispatches an intent.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Exit selection") { onExit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Exit selection",
                tint = HomeTokens.textDim,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count selected",
            fontFamily = TerminalTheme.mono,
            fontSize = 13.sp,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) {
            Text(
                "All",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = HomeTokens.accent,
            )
        }
        TextButton(onClick = onCopy, enabled = count > 0) {
            Text(
                "Copy",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = if (count > 0) HomeTokens.accent else HomeTokens.textDim,
            )
        }
        TextButton(onClick = onMove, enabled = count > 0) {
            Text(
                "Move",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = if (count > 0) HomeTokens.accent else HomeTokens.textDim,
            )
        }
        TextButton(onClick = onDelete, enabled = count > 0) {
            Text(
                "Delete",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = if (count > 0) HomeTokens.danger else HomeTokens.textDim,
            )
        }
    }
}

// -------------------------------------------------------------- search (P8)

/**
 * The search-mode field row: a focused single-line text field with the
 * scope named honestly ("Search in <area>"), an in-field clear affordance,
 * and a close affordance. Typing dispatches [onQuery] for every change —
 * each call supersedes the previous walk in the ViewModel; a BLANK query
 * is the input state and never scans.
 */
@Composable
private fun SearchFieldRow(
    areaName: String,
    query: String,
    onQuery: (String) -> Unit,
    onExit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(HomeTokens.surfaceEnv)
                .border(1.dp, HomeTokens.hairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = HomeTokens.textPrimary,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(HomeTokens.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search in $areaName",
                                style = MaterialTheme.typography.bodyLarge,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Clear search") { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear search",
                    tint = HomeTokens.textDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Close search") { onExit() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = "Close search",
                tint = HomeTokens.textDim,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * The search-mode body: ONLY renders the search publication state — running
 * spinner, honest limit/skip disclosures, no-results, results — and sends
 * activations. It never sees a [app.pocketshell.files.StorageArea] and never
 * decides what a match is.
 */
@Composable
private fun SearchBody(
    search: FilesSearchState,
    areaName: String,
    onOpenResult: (FileSearch.SearchResult) -> Unit,
) {
    when (val s = search) {
        FilesSearchState.Idle -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 40.dp),
        ) {
            MidnightNote(
                text = "Type to search folder and file NAMES inside $areaName — " +
                    "matching is a simple case-insensitive part of the name, " +
                    "and the search never leaves this area.",
            )
        }

        is FilesSearchState.Running -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = HomeTokens.accent,
            )
        }

        is FilesSearchState.Failed -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            MidnightBanner(message = s.reason, failed = true)
        }

        is FilesSearchState.Done -> {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Honest disclosures — limits and partial failures are
                // never silent. Exact wording mirrors the core's flags.
                if (s.outcome.truncated || s.outcome.scanTruncated ||
                    s.outcome.skippedCount > 0
                ) {
                    val notes = buildList {
                        if (s.outcome.truncated) {
                            add("Stopped at the first ${s.outcome.results.size} matches — refine the query to narrow the search.")
                        }
                        if (s.outcome.scanTruncated) {
                            add("Stopped early — only part of $areaName was searched, so results may be incomplete.")
                        }
                        if (s.outcome.skippedCount > 0) {
                            add("${s.outcome.skippedCount} folder(s) could not be searched: ${s.outcome.firstError ?: "listing failed"}")
                        }
                    }
                    MidnightBanner(message = notes.joinToString(" "), failed = false)
                    Spacer(Modifier.height(4.dp))
                }
                if (s.outcome.results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No matches for \"${s.outcome.query}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HomeTokens.textDim,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(s.outcome.results, key = { it.parent.value + "/" + it.entry.name }) { result ->
                            SearchRow(
                                result = result,
                                areaName = areaName,
                                onClick = { onOpenResult(result) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One search result: kind icon, the name, and the relative location that
 * distinguishes duplicates (the area's own navigation spine — for SAF
 * areas this is the honest document-tree spine, never a fake POSIX path). */
@Composable
private fun SearchRow(
    result: FileSearch.SearchResult,
    areaName: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${result.entry.name}") { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (result.entry.kind) {
                EntryKind.DIRECTORY -> Icons.Outlined.Folder
                EntryKind.SYMLINK -> Icons.Outlined.Link
                else -> Icons.Outlined.InsertDriveFile
            },
            contentDescription = null,
            tint = HomeTokens.textDim,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = result.entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (result.entry.kind == EntryKind.DIRECTORY) FontWeight.Medium else FontWeight.Normal,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (result.relativeLocation.isEmpty()) areaName else result.relativeLocation,
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ listing

/** One quiet column of entries — directories first (the engine's order). */
@Composable
private fun Listing(
    entries: List<FsEntry>,
    highlight: String?,
    selectionMode: Boolean,
    selected: Set<String>,
    onOpenChild: (String) -> Unit,
    onSelect: (FsEntry) -> Unit,
    onToggle: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(entries, key = { it.name }) { entry ->
            EntryRow(
                entry = entry,
                highlighted = entry.name == highlight,
                onClick = {
                    when (entry.kind) {
                        EntryKind.DIRECTORY -> onOpenChild(entry.name)
                        else -> onSelect(entry)
                    }
                },
                onActions = { onSelect(entry) },
                selecting = selectionMode,
                selectedNow = entry.name in selected,
                onToggle = { onToggle(entry.name) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: FsEntry,
    onClick: () -> Unit,
    onActions: () -> Unit,
    highlighted: Boolean = false,
    selecting: Boolean = false,
    selectedNow: Boolean = false,
    onToggle: () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    pressed -> HomeTokens.surfaceBanner
                    selectedNow -> HomeTokens.surfaceEnv
                    highlighted -> HomeTokens.surfaceEnv
                    else -> Color.Transparent
                },
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (selecting) {
                    if (selectedNow) "Deselect ${entry.name}" else "Select ${entry.name}"
                } else if (entry.kind == EntryKind.DIRECTORY) "Open ${entry.name}" else "Details of ${entry.name}",
                onLongClickLabel = if (selecting) null else "Actions for ${entry.name}",
                onClick = if (selecting) onToggle else onClick,
                onLongClick = if (selecting) onToggle else onActions,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when {
                selecting ->
                    if (selectedNow) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank
                entry.kind == EntryKind.DIRECTORY -> Icons.Outlined.Folder
                entry.kind == EntryKind.SYMLINK -> Icons.Outlined.Link
                else -> Icons.Outlined.InsertDriveFile
            },
            contentDescription = if (selecting) {
                if (selectedNow) "Selected" else "Not selected"
            } else {
                null
            },
            tint = if (selecting && selectedNow) HomeTokens.accent else HomeTokens.textDim,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.kind == EntryKind.DIRECTORY) FontWeight.Medium else FontWeight.Normal,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.kind == EntryKind.SYMLINK && entry.symlinkTarget != null) {
                Text(
                    text = "→ ${entry.symlinkTarget}",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.kind == EntryKind.FILE && entry.sizeBytes != null) {
            Text(
                text = formatBytes(entry.sizeBytes),
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textDim,
            )
            Spacer(Modifier.width(10.dp))
        }
        // The always-visible affordance — actions are never gesture-only.
        // Hidden while selecting: the row itself toggles in this mode.
        if (!selecting) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Actions for ${entry.name}") { onActions() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Actions for ${entry.name}",
                    tint = HomeTokens.textDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------- states

@Composable
private fun NoStorageState(onOpenDiagnostics: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No storage is available right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.height(8.dp))
        MidnightNote(text = "Check Diagnostics for the runtime and storage state.")
        Spacer(Modifier.height(16.dp))
        MidnightQuietButton("Open Diagnostics", onClick = onOpenDiagnostics)
    }
}

// ------------------------------------------------------------------ helpers

/** Minimal human byte size — the only per-row metadata the listing shows. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return compact(kb) + " KB"
    val mb = kb / 1024.0
    if (mb < 1024) return compact(mb) + " MB"
    val gb = mb / 1024.0
    return compact(gb) + " GB"
}

private fun compact(value: Double): String =
    if (value < 10) String.format(Locale.US, "%.1f", value) else value.toLong().toString()
