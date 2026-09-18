package app.pocketshell.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.files.EntryKind
import app.pocketshell.files.ExplorerOps
import app.pocketshell.files.MultiSelectOps
import app.pocketshell.files.FsEntry
import app.pocketshell.files.PendingTransfer
import app.pocketshell.files.TerminalLaunchSupport
import app.pocketshell.files.ZipArchiveOps
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.system.MidnightFactRow
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightTextField
import app.pocketshell.ui.theme.TerminalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M7.0.0 Phase 4 — the operation surfaces of the Files screen, extracted so
 * FilesScreen stays a listing+navigation screen rather than growing into a
 * monolith. PRESENTATION ONLY: every composable here renders state from
 * [FilesOpsSurface] and dispatches intents; none of them touch the filesystem.
 */

// ------------------------------------------------------------ pending banner

/** The pending copy/move marker with its Paste here action. Phase 8.1:
 * [count] > 1 discloses that the clipboard holds a whole selection. */
@Composable
fun PendingBanner(
    pending: PendingTransfer,
    count: Int,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    MidnightBanner(
        message = MultiSelectOps.bannerText(pending, count),
        failed = false,
        actions = {
            TextButton(onClick = onCancel) {
                Text(
                    "Cancel",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    color = HomeTokens.textDim,
                )
            }
            TextButton(onClick = onPaste, enabled = canPaste) {
                Text(
                    "Paste here",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canPaste) HomeTokens.accent else HomeTokens.textDim,
                )
            }
        },
    )
}

// ------------------------------------------------------------- notice banner

/** The honest outcome of the last operation. Errors persist; successes clear. */
@Composable
fun OpsNoticeBanner(notice: OpsNotice, onDismiss: () -> Unit) {
    var visibleSeq by remember { mutableStateOf<Long?>(null) }

    // Successes auto-clear after a moment; errors stay until dismissed.
    LaunchedEffect(notice.seq) {
        visibleSeq = notice.seq
        if (!notice.isError) {
            kotlinx.coroutines.delay(4_000)
            if (visibleSeq == notice.seq) onDismiss()
        }
    }

    MidnightBanner(
        message = notice.text,
        failed = notice.isError,
        actions = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Dismiss",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    color = if (notice.isError) HomeTokens.accent else HomeTokens.textDim,
                )
            }
        },
    )
}

// --------------------------------------------------------------- action sheet

/** The callbacks one row of the action sheet can carry (null = not offered). */
data class EntryActionHandlers(
    val onOpen: (() -> Unit)? = null,
    val onNewFolder: (() -> Unit)? = null,
    val onNewFile: (() -> Unit)? = null,
    /**
     * M7.0.0 Phase 7: open a real Linux terminal session in the directory
     * the explorer is browsing. Offered ONLY for directories inside the
     * PocketShell Linux area; Android-owned areas keep the honest boundary
     * note instead ([TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE]) —
     * never a broken actionable launch.
     */
    val onTerminal: (() -> Unit)? = null,
    val onCopy: () -> Unit,
    val onMove: () -> Unit,
    /** Phase 5: share this file via the Android share sheet (files only). */
    val onShare: (() -> Unit)? = null,
    /** Phase 5: export this file via the system save dialog (files only). */
    val onExport: (() -> Unit)? = null,
    /** Phase 6: open this file in the quick text editor (files only). */
    val onEdit: (() -> Unit)? = null,
    /**
     * M7.2-A: open this file with an installed Android app via ACTION_VIEW
     * over a FileProvider copy (files only). The caller resolves the real
     * handler situation; the sheet only offers what is genuinely possible.
     */
    val onOpenWith: (() -> Unit)? = null,
    /**
     * M8.3: install this .apk via the SYSTEM package installer (files
     * only; the caller offers it only for .apk names). The installer's
     * own confirmation screen runs — nothing installs silently.
     */
    val onInstallApk: (() -> Unit)? = null,
    /** M7.2-A: compress this entry into a .zip (files and folders). */
    val onCompress: (() -> Unit)? = null,
    /** M7.2-A: extract this .zip into a folder of the current directory. */
    val onExtract: (() -> Unit)? = null,
    /** Owner iteration: bookmark / unbookmark this directory (folders only). */
    val onToggleBookmark: (() -> Unit)? = null,
    val onRename: () -> Unit,
    val onDelete: () -> Unit,
)

/**
 * The contextual action sheet — the mobile-first replacement for a desktop
 * right-click. Reachable three ways (tap a file, long-press any row, or the
 * row's "⋮"), so no action hides behind a gesture-only path. The sheet is
 * exactly the product vision: no multi-select, no batch, no preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryActionSheet(
    entry: FsEntry,
    /** Owner iteration: current bookmark state — flips the folder action label. */
    bookmarked: Boolean = false,
    onDismiss: () -> Unit,
    handlers: EntryActionHandlers,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = TerminalTheme.chrome,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = entry.name,
                fontFamily = TerminalTheme.mono,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = HomeTokens.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            MidnightFactRow(
                label = "Kind",
                value = when (entry.kind) {
                    EntryKind.DIRECTORY -> "Folder"
                    EntryKind.SYMLINK -> "Symlink"
                    EntryKind.FILE -> "File"
                    EntryKind.OTHER -> "Special"
                },
            )
            if (entry.kind == EntryKind.FILE && entry.sizeBytes != null) {
                MidnightFactRow("Size", formatBytes(entry.sizeBytes))
            }
            entry.modifiedAtMillis?.let { modified ->
                MidnightFactRow("Modified", timestampFormat().format(Date(modified)))
            }
            entry.symlinkTarget?.let { target ->
                MidnightFactRow("Target", target)
            }
            Spacer(Modifier.height(8.dp))

            if (entry.kind == EntryKind.DIRECTORY) {
                SheetAction("Open", Icons.Outlined.FolderOpen, onClick = { handlers.onOpen?.invoke() })
                // Phase 7: the real action — a launchable terminal session in
                // THIS folder (p7.1: the tapped entry's name rides the intent;
                // PocketShell Linux areas only; the caller wires the handler,
                // the sheet never fabricates one).
                if (handlers.onTerminal != null) {
                    val terminal = handlers.onTerminal
                    SheetAction("Open Terminal Here", Icons.Outlined.Terminal, onClick = { terminal?.invoke() })
                }
                // Owner iteration: persist this folder for the Home "Folders"
                // list — the label reflects the current state.
                if (handlers.onToggleBookmark != null) {
                    val toggleBookmark = handlers.onToggleBookmark
                    SheetAction(
                        if (bookmarked) "Remove Bookmark" else "Bookmark",
                        if (bookmarked) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                        onClick = { toggleBookmark?.invoke() },
                    )
                }
                SheetAction("New folder", Icons.Outlined.CreateNewFolder, onClick = { handlers.onNewFolder?.invoke() })
                SheetAction("New file", Icons.Outlined.NoteAdd, onClick = { handlers.onNewFile?.invoke() })
                SheetDivider()
            }
            if (entry.kind == EntryKind.FILE && handlers.onEdit != null) {
                val edit = handlers.onEdit
                SheetAction("Open", Icons.Outlined.Description, onClick = { edit?.invoke() })
            }
            if (entry.kind == EntryKind.FILE && handlers.onInstallApk != null) {
                val installApk = handlers.onInstallApk
                SheetAction("Install", Icons.Outlined.SystemUpdate, onClick = { installApk?.invoke() })
            }
            if (entry.kind == EntryKind.FILE && handlers.onOpenWith != null) {
                val openWith = handlers.onOpenWith
                SheetAction("Open in Android app", Icons.Outlined.OpenInNew, onClick = { openWith?.invoke() })
            }
            SheetAction("Copy", Icons.Outlined.ContentCopy, onClick = handlers.onCopy)
            SheetAction("Move", Icons.Outlined.DriveFileMove, onClick = handlers.onMove)
            if (entry.kind == EntryKind.FILE && handlers.onShare != null) {
                val share = handlers.onShare
                SheetAction("Share", Icons.Outlined.Share, onClick = { share?.invoke() })
            }
            if (entry.kind == EntryKind.FILE && handlers.onExport != null) {
                val export = handlers.onExport
                SheetAction("Export", Icons.Outlined.FileUpload, onClick = { export?.invoke() })
            }
            if (handlers.onCompress != null) {
                val compress = handlers.onCompress
                SheetAction("Compress", Icons.Outlined.Archive, onClick = { compress?.invoke() })
            }
            if (handlers.onExtract != null) {
                val extract = handlers.onExtract
                SheetAction("Extract…", Icons.Outlined.FolderZip, onClick = { extract?.invoke() })
            }
            SheetAction("Rename", Icons.Outlined.Edit, onClick = handlers.onRename)
            SheetAction("Delete", Icons.Outlined.Delete, danger = true, onClick = handlers.onDelete)

            Spacer(Modifier.height(10.dp))
            MidnightNote(
                text = when {
                    entry.kind == EntryKind.DIRECTORY && handlers.onTerminal != null ->
                        "Opens a new Linux terminal in this folder."
                    entry.kind == EntryKind.DIRECTORY ->
                        // The honest Android-boundary explanation — replaces
                        // the old Phase 4 deferral note. Never a fake launch.
                        TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE
                    entry.kind == EntryKind.FILE && handlers.onExtract != null ->
                        "The archive is extracted into a folder of this directory; nothing is overwritten without your choice."
                    entry.kind == EntryKind.FILE && handlers.onInstallApk != null ->
                        "Android's installer opens and asks for your confirmation — the original stays here."
                    entry.kind == EntryKind.FILE && handlers.onOpenWith != null ->
                        "A copy is handed to the Android app you pick — the original stays here."
                    else ->
                        "Open and Edit arrive in a later update."
                },
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) HomeTokens.danger else HomeTokens.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) HomeTokens.danger else HomeTokens.textDim,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = TerminalTheme.mono,
            fontSize = 14.sp,
            color = tint,
        )
    }
}

@Composable
private fun SheetDivider() {
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HomeTokens.hairline),
        ) {}
    }
    Spacer(Modifier.height(6.dp))
}

// -------------------------------------------------------------------- dialogs

/** Replace / Cancel — the only overwrite path, always user-confirmed. */
@Composable
fun ConfirmReplaceDialog(
    request: ReplaceRequest,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    val existing = when (request.existingKind) {
        EntryKind.DIRECTORY -> "folder"
        EntryKind.SYMLINK -> "link"
        else -> "file"
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = "Replace \"${request.name}\"?",
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Text(
                text = "A ${existing} named \"${request.name}\" already exists here. " +
                    "${request.opLabel} will overwrite it. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onReplace) {
                Text(
                    "Replace",
                    fontFamily = TerminalTheme.mono,
                    color = HomeTokens.danger,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/** Delete confirmation — shows the real name and the honest per-kind warning. */
@Composable
fun ConfirmDeleteDialog(
    state: DeleteConfirmState,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val warning = ExplorerOps.deleteWarning(state.kind)
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = "Delete \"${state.name}\"?",
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Text(
                    text = "This permanently deletes \"${state.name}\".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (warning != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTokens.textPrimary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", fontFamily = TerminalTheme.mono, color = HomeTokens.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/** Phase 8.1: the multi-delete confirmation — the honest count, the named
 * items (up to a handful, then "…"), and the per-kind warning lines. */
@Composable
fun ConfirmMultiDeleteDialog(
    state: MultiDeleteConfirm,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val shown = state.names.take(3)
    val rest = state.names.size - shown.size
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = "Delete ${state.names.size} items?",
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Column {
                Text(
                    text = "This permanently deletes " +
                        shown.joinToString(", ") { "\"${it}\"" } +
                        if (rest > 0) " and $rest more…" else ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.warnings.forEach { warning ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HomeTokens.textPrimary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", fontFamily = TerminalTheme.mono, color = HomeTokens.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/**
 * The one text prompt used by Rename and New Folder / New File. Errors from
 * the ViewModel (invalid name, already exists, refused) render inline; the
 * dialog never closes itself on a failure.
 */
@Composable
fun NamePromptDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = title,
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Column {
                MidnightTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Name",
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(confirmLabel, fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/** Terminal-flavoured deterministic timestamp for fact rows. */
internal fun timestampFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

// ------------------------------------------------- M7.2-A — archive dialogs

/**
 * The "Create archive" prompt — the same round-trip discipline as
 * [NamePromptDialog]: errors from the ViewModel render inline and the
 * dialog never closes itself on a failure.
 */
@Composable
fun CompressDialog(
    title: String,
    initial: String,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = title,
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Column {
                MidnightTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "archive name",
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Create", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/**
 * The "Extract archive" prompt: the destination folder (inside the current
 * directory; created when missing) plus the EXPLICIT overwrite choice —
 * existing files are never replaced silently.
 */
@Composable
fun ExtractDialog(
    zipName: String,
    suggestedFolder: String,
    replace: Boolean,
    error: String?,
    onReplace: (Boolean) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(suggestedFolder) { mutableStateOf(suggestedFolder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = "Extract \"$zipName\"",
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Text(
                    text = "Extract into a folder of the current directory. It is created when missing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                MidnightTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "folder name",
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Checkbox) { onReplace(!replace) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (replace) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = if (replace) "Replace existing files" else "Keep existing files",
                        tint = if (replace) HomeTokens.accent else HomeTokens.textDim,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Replace existing files",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 13.sp,
                        color = HomeTokens.textPrimary,
                    )
                }
                if (replace) {
                    Text(
                        text = "Existing files with the same name will be overwritten.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                    )
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Extract", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        },
    )
}

/**
 * The live compress/extract progress banner: what is running, the current
 * entry, honest bytes (determinate only when a total is really known), and
 * the one cancel affordance.
 */
@Composable
fun ZipProgressBanner(
    progress: ZipProgress,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HomeTokens.surfaceEnv)
            .border(1.dp, HomeTokens.hairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (progress.kind == ZipProgress.ZipKind.COMPRESS) "Creating archive" else "Extracting archive",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) {
                Text(
                    "Cancel",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    color = HomeTokens.danger,
                )
            }
        }
        progress.currentName?.let { name ->
            Text(
                text = name,
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        val total = progress.totalBytes
        if (total != null && total > 0) {
            LinearProgressIndicator(
                progress = { (progress.bytesDone.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = HomeTokens.accent,
                trackColor = HomeTokens.hairline,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = HomeTokens.accent,
                trackColor = HomeTokens.hairline,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("${progress.entriesDone} entr")
                append(if (progress.entriesDone == 1) "y" else "ies")
                append(" · ${ZipArchiveOps.humanBytes(progress.bytesDone)}")
                if (total != null && total > 0) {
                    append(" of ${ZipArchiveOps.humanBytes(total)}")
                }
            },
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = HomeTokens.textDim,
        )
    }
}
