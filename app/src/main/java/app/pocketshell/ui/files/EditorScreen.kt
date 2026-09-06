package app.pocketshell.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.files.editor.TextDocument
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.theme.TerminalTheme
import kotlinx.coroutines.delay

/**
 * M7.0.0 Phase 6 — the quick text editor screen.
 *
 * PRESENTATION ONLY: renders [EditorState] verbatim and dispatches to
 * [EditorSurface]. The scope is the product rule: a QUICK TEXT VIEWER and
 * EDITOR — one file, one buffer, one Save. No syntax highlighting, no line
 * numbers, no search, no undo history. Every refusal (too large, binary,
 * not UTF-8, unreadable) is an honest full-screen state; the file is never
 * shown garbled and never changed by a refused open.
 *
 * INPUT PATH (no system IME anywhere in PocketShell): the text area is a
 * multiline [BasicTextField] — the exact surface class the app's other text
 * inputs use — so the ONE keyboard deck at the app root serves it through
 * the existing focused-view dispatch chain (real KeyEvents). The body sits
 * above the deck via [keyboardBottomInset] (the TerminalScreen pattern).
 * Cursor/selection behaviour of the deck's arrow keys is a documented
 * device-check item (docs/TESTING.md).
 */
@Composable
fun EditorScreen(
    state: EditorState,
    surface: EditorSurface,
    keyboardBottomInset: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back semantics, innermost first (Compose gives the LAST enabled
    // handler the event): an open guard dialog backs out to "keep editing";
    // a dirty buffer raises the guard; otherwise back falls through to the
    // app router (Home) — the document stays in the process-scoped holder.
    BackHandler(enabled = state.backGuard) { surface.keepEditing() }
    BackHandler(enabled = state.readable && state.dirty && !state.saving) {
        surface.requestBackGuard()
    }

    // Leave-after-save: the guard's Save choice completes asynchronously
    // (possibly through a confirmation); when the buffer is finally clean
    // the screen performs the back navigation itself.
    LaunchedEffect(state.pendingLeave, state.dirty, state.saving, state.confirmSave) {
        if (state.pendingLeave && !state.dirty && !state.saving && state.confirmSave == null) {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.screenBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = HomeTokens.contentMaxWidth)
                .align(Alignment.TopCenter)
                .then(
                    if (keyboardBottomInset == 0.dp) Modifier.navigationBarsPadding()
                    else Modifier.padding(bottom = keyboardBottomInset),
                ),
        ) {
            EditorHeader(state = state, surface = surface, onBack = onBack)

            if (state.readable) {
                StatusLine(state = state)
            }

            state.saveError?.let { error ->
                Spacer(Modifier.height(6.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    MidnightBanner(
                        message = error,
                        failed = state.saveDenied,
                        actions = {
                            TextButton(onClick = surface::dismissSaveError) {
                                Text(
                                    "Dismiss",
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 13.sp,
                                    color = if (state.saveDenied) HomeTokens.accent else HomeTokens.textDim,
                                )
                            }
                        },
                    )
                }
            }

            when {
                !state.hasDocument -> Unit

                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = HomeTokens.accent,
                    )
                }

                state.tooLargeBytes != null -> RefusalState(
                    title = "\"${state.fileName}\" is too large for the quick editor",
                    body = "This file is ${TextDocument.sizeLabel(state.tooLargeBytes)} — " +
                        "the quick editor opens text files up to 1 MB. " +
                        "Copy, move, share and export still work for it in Files.",
                )

                state.binaryRefused -> RefusalState(
                    title = "\"${state.fileName}\" is not a text file",
                    body = "The quick editor opens UTF-8 text only, so this file was not " +
                        "opened — and it was not changed.",
                )

                state.notUtf8Refused -> RefusalState(
                    title = "\"${state.fileName}\" is not UTF-8 text",
                    body = "The quick editor refuses content it could not save back byte-exactly. " +
                        "The file was not opened and not changed.",
                )

                state.loadError != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    MidnightBanner(
                        message = state.loadError ?: "",
                        failed = true,
                        actions = {
                            TextButton(onClick = surface::retryLoad) {
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

                state.readable -> TextBuffer(state = state, surface = surface)
            }
        }
    }

    state.confirmSave?.let { confirm ->
        SaveAnywayDialog(
            state = state,
            missing = confirm.missing,
            onSaveAnyway = { surface.resolveSaveConfirm(saveAnyway = true) },
            onCancel = { surface.resolveSaveConfirm(saveAnyway = false) },
        )
    }

    if (state.backGuard) {
        BackGuardDialog(
            state = state,
            onSave = surface::saveFromGuard,
            onDiscard = {
                surface.discardAndLeave()
                onBack()
            },
            onKeepEditing = surface::keepEditing,
        )
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun EditorHeader(
    state: EditorState,
    surface: EditorSurface,
    onBack: () -> Unit,
) {
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
        Column(Modifier.weight(1f)) {
            Text(
                text = state.fileName,
                fontFamily = TerminalTheme.mono,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.areaLabel.ifEmpty { state.pathDisplay },
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = surface::save,
            enabled = state.readable && state.dirty && !state.saving,
        ) {
            Text(
                text = if (state.saving) "Saving…" else "Save",
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = when {
                    state.saving -> HomeTokens.textDim
                    state.readable && state.dirty -> HomeTokens.accent
                    else -> HomeTokens.textDim
                },
            )
        }
    }
}

@Composable
private fun StatusLine(state: EditorState) {
    var savedFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.saveNoticeSeq) {
        if (state.saveNoticeSeq > 0L) {
            savedFlash = true
            delay(2_000)
            savedFlash = false
        }
    }
    val label = when {
        state.saving -> "Saving…"
        savedFlash -> "Saved \u2713"
        state.dirty -> "Unsaved changes"
        else -> TextDocument.sizeLabel(state.sizeBytes) ?: ""
    }
    if (label.isEmpty()) return
    Text(
        text = label,
        fontFamily = TerminalTheme.mono,
        fontSize = 11.sp,
        color = when {
            state.saving -> HomeTokens.textDim
            savedFlash -> HomeTokens.runningGreen
            state.dirty -> HomeTokens.accent
            else -> HomeTokens.textDim
        },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

// ------------------------------------------------------------- text buffer

@Composable
private fun TextBuffer(state: EditorState, surface: EditorSurface) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.readable) {
        if (state.readable) {
            // One quiet focus so deck keys flow immediately; never fatal if
            // the node is not attached yet.
            runCatching { focusRequester.requestFocus() }
        }
    }
    BasicTextField(
        value = state.text,
        onValueChange = surface::editText,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .focusRequester(focusRequester)
            .semantics { contentDescription = "Text editor" },
        enabled = !state.saving,
        readOnly = false,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = TerminalTheme.mono,
            fontSize = 14.sp,
            color = HomeTokens.textPrimary,
        ),
        cursorBrush = SolidColor(HomeTokens.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        onTextLayout = {},
    )
}

// ------------------------------------------------------------ refusal states

@Composable
private fun RefusalState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            fontFamily = TerminalTheme.mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = HomeTokens.textPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = HomeTokens.textDim,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ----------------------------------------------------------------- dialogs

@Composable
private fun SaveAnywayDialog(
    state: EditorState,
    missing: Boolean,
    onSaveAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = if (missing) {
                    "\"${state.fileName}\" no longer exists"
                } else {
                    "\"${state.fileName}\" changed outside the editor"
                },
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Text(
                text = if (missing) {
                    "The file was deleted since you opened it. " +
                        "Saving now creates it again with your content."
                } else {
                    "The file was changed since you opened it — saving now " +
                        "overwrites those changes. This cannot be undone."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onSaveAnyway) {
                Text(
                    "Save anyway",
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

@Composable
private fun BackGuardDialog(
    state: EditorState,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        containerColor = TerminalTheme.chrome,
        titleContentColor = HomeTokens.textPrimary,
        textContentColor = HomeTokens.textDim,
        title = {
            Text(
                text = "Unsaved changes",
                fontFamily = TerminalTheme.mono,
                fontSize = 17.sp,
            )
        },
        text = {
            Text(
                text = "Your edits to \"${state.fileName}\" are not saved. " +
                    "Discarding them now cannot be undone, and unsaved content " +
                    "does not survive closing the app.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("Save", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) {
                    Text("Discard", fontFamily = TerminalTheme.mono, color = HomeTokens.danger)
                }
                TextButton(onClick = onKeepEditing) {
                    Text("Keep editing", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
                }
            }
        },
    )
}
