package app.pocketshell.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.files.AreaId
import app.pocketshell.files.EntryKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.FsEntry
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.system.MidnightFactRow
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.theme.TerminalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M7.0.0 Phase 3 — the File Explorer (EXPLORER FOUNDATION ONLY).
 *
 * Mobile-first and touch-first, deliberately NOT a desktop file manager:
 * one column, directories first (the Phase 2 engine's order), size as the
 * only per-row metadata, and exactly three interactions —
 *
 *   tap a directory  → open it (never beyond the logical area boundary)
 *   system back      → parent directory, then out of the screen
 *   tap a file       → a minimal info surface (real actions = Phase 4)
 *
 * The UI performs ZERO filesystem operations: it renders [ExplorerCore.State]
 * and sends intentions (names, up, area switch, refresh) to the ViewModel.
 * Errors are rendered verbatim — honest, never swallowed, never fatal.
 */
@Composable
fun FilesScreen(
    state: ExplorerCore.State,
    guestUnavailable: Boolean,
    onBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenChild: (String) -> Unit,
    onSwitchArea: (AreaId) -> Unit,
    onRefresh: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The minimal file surface (Phase 3): metadata + an honest "actions are
    // coming" note. Phase 4 replaces this with the real operations menu.
    var selected by remember { mutableStateOf<FsEntry?>(null) }

    // Back = parent directory while there is one INSIDE the area; at the area
    // root the handler releases back to the app router (→ Home). The logical
    // area boundary is never crossed by back navigation.
    BackHandler(enabled = state.canNavigateUp) { onNavigateUp() }

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
            FilesHeader(state = state, onBack = onBack, onSwitchArea = onSwitchArea)

            LocationRow(state = state, onNavigateUp = onNavigateUp)

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

            Spacer(Modifier.height(8.dp))

            when {
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
                    onOpenChild = onOpenChild,
                    onSelectFile = { selected = it },
                )
            }
        }
    }

    selected?.let { entry ->
        FileSurface(entry = entry, onDismiss = { selected = null })
    }
}

// ------------------------------------------------------------------- header

/**
 * Back chevron + mono page title + the area switcher chip. The chip only
 * appears when more than one area exists (Linux-first Phase 3 usually has
 * two: Linux + Downloads); the current one carries a check.
 */
@Composable
private fun FilesHeader(
    state: ExplorerCore.State,
    onBack: () -> Unit,
    onSwitchArea: (AreaId) -> Unit,
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

        if (state.areas.size > 1) {
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
                }
            }
        }
    }
}

// -------------------------------------------------------------- location row

/** The up-to-parent affordance and the current path, mono and dim. */
@Composable
private fun LocationRow(state: ExplorerCore.State, onNavigateUp: () -> Unit) {
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
                .padding(end = 20.dp),
        )
    }
}

// ------------------------------------------------------------------ listing

/** One quiet column of entries — directories first (the engine's order). */
@Composable
private fun Listing(
    entries: List<FsEntry>,
    onOpenChild: (String) -> Unit,
    onSelectFile: (FsEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(entries, key = { it.name }) { entry ->
            EntryRow(
                entry = entry,
                onClick = {
                    when (entry.kind) {
                        EntryKind.DIRECTORY -> onOpenChild(entry.name)
                        else -> onSelectFile(entry)
                    }
                },
            )
        }
    }
}

@Composable
private fun EntryRow(entry: FsEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) HomeTokens.surfaceBanner else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (entry.kind == EntryKind.DIRECTORY) "Open ${entry.name}" else "Details of ${entry.name}",
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (entry.kind) {
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
        }
    }
}

// -------------------------------------------------------------- file surface

/**
 * The Phase 3 file surface: real metadata, honest note, no fake buttons.
 * (Open / Edit / Share / Rename / Delete / Move arrive with Phase 4.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSurface(entry: FsEntry, onDismiss: () -> Unit) {
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
                MidnightFactRow("Modified", dateFormat().format(Date(modified)))
            }
            entry.symlinkTarget?.let { target ->
                MidnightFactRow("Target", target)
            }
            Spacer(Modifier.height(12.dp))
            MidnightNote(
                text = "File actions — open, edit, share, rename, delete — arrive in the next update.",
            )
            Spacer(Modifier.height(16.dp))
            MidnightQuietButton("Close", onClick = onDismiss)
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

/** Terminal-flavoured deterministic timestamp for the file surface. */
private fun dateFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
