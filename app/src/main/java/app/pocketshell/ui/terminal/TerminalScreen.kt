package app.pocketshell.ui.terminal

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pocketshell.ui.components.PSHamburgerIcon
import app.pocketshell.ui.components.PSEmptyState
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.keyboard.TerminalKeyDispatcher
import app.pocketshell.keyboard.TerminalKeyboard
import app.pocketshell.terminal.PocketShellTerminalViewClient
import app.pocketshell.terminal.TerminalSessionManager
import app.pocketshell.ui.theme.PSSpacing
import app.pocketshell.ui.theme.TerminalCanvas
import com.termux.view.TerminalView

private const val DEFAULT_FONT_SIZE = 28
private const val MIN_FONT_SIZE = 12
private const val MAX_FONT_SIZE = 40

/**
 * Terminal screen (docs/UI-REDESIGN.md §6): session tab pills in the chrome,
 * the real TerminalView in a framed-ink canvas, and the accessory keyboard
 * sandwich — top row, Android IME (toggled), bottom row.
 *
 * The Android IME is toggled ONLY by the keyboard icon in the accessory
 * bottom row (final keyboard spec); tapping the terminal re-shows it when
 * hidden. Sessions are never destroyed by UI navigation.
 */
@Composable
fun TerminalScreen(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    keyboardState: KeyboardState,
    creating: Boolean,
    initialFontSize: Int = DEFAULT_FONT_SIZE,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // IME visibility: default open (Termux convention), survives process
    // death, toggled by the accessory icon or a tap on the terminal.
    var imeVisible by rememberSaveable { mutableStateOf(true) }
    var textSize by rememberSaveable { mutableIntStateOf(initialFontSize) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }
    val context = LocalContext.current

    // Modifier state must never leak across sessions.
    LaunchedEffect(selectedId) { keyboardState.clearAll() }

    // Show/hide the Android IME whenever the state or the focused view
    // changes. TerminalView provides a real InputConnection (upstream).
    LaunchedEffect(imeVisible, selected?.id) {
        val view = terminalViewRef.value ?: return@LaunchedEffect
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (imeVisible) {
            view.post {
                if (view.requestFocus()) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        } else {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    // Upstream contract: TerminalView does not observe session data — the host
    // must call TerminalView#onScreenUpdated() whenever the session screen
    // changes, otherwise output stays invisible until a layout pass forces a
    // repaint. The listener is invoked on the main thread.
    DisposableEffect(Unit) {
        TerminalSessionManager.onScreenUpdateListener = { _ ->
            terminalViewRef.value?.onScreenUpdated()
        }
        onDispose { TerminalSessionManager.onScreenUpdateListener = null }
    }

    // Cursor blinker is a host duty: stop it when the host is not visible,
    // restart on resume. The initial start happens in onEmulatorSet.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    terminalViewRef.value?.setTerminalCursorBlinkerState(true, true)
                Lifecycle.Event.ON_PAUSE ->
                    terminalViewRef.value?.setTerminalCursorBlinkerState(false, false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        SessionChrome(
            sessions = sessions,
            selectedId = selectedId,
            onSelect = onSelect,
            onClose = onClose,
            onNewSession = onNewSession,
            onMenu = onMenu,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = PSSpacing.sm, start = PSSpacing.sm, end = PSSpacing.sm),
        ) {
            if (selected != null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = TerminalCanvas,
                ) {
                    TerminalViewHost(
                        entry = selected,
                        keyboardState = keyboardState,
                        textSize = textSize,
                        onTextSizeChange = { textSize = it },
                        onSingleTap = { if (!imeVisible) imeVisible = true },
                        onViewCreated = { view ->
                            terminalViewRef.value = view
                            if (imeVisible) {
                                val imm = context.getSystemService(
                                    Context.INPUT_METHOD_SERVICE,
                                ) as InputMethodManager
                                view.post {
                                    if (view.requestFocus()) {
                                        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            } else {
                // Empty state on the app surface — never inside the dark
                // terminal canvas (light-theme text would sit on ink).
                EmptyTerminalState(
                    creating = creating,
                    onNewSession = onNewSession,
                )
            }
        }

        // Accessory keyboard: BOTH rows always visible; the Android IME
        // appears between them (system-controlled, at the screen bottom).
        val dispatcher = remember {
            TerminalKeyDispatcher(keyboardState) { event ->
                terminalViewRef.value?.dispatchKeyEvent(event)
            }
        }
        TerminalKeyboard(
            keyboardState = keyboardState,
            dispatcher = dispatcher,
            imeVisible = imeVisible,
            onToggleIme = { imeVisible = !imeVisible },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Chrome row: hamburger · session tab pills · new-session · overflow menu. */
@Composable
private fun SessionChrome(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onMenu: () -> Unit,
) {
    var overflowOpen by remember { mutableStateOf(false) }
    var pendingCloseId by remember { mutableStateOf<Long?>(null) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PSSpacing.sm, vertical = PSSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PSSpacing.xs),
        ) {
            Surface(
                onClick = onMenu,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(end = 2.dp),
            ) {
                Box(Modifier.padding(PSSpacing.md)) {
                    PSHamburgerIcon()
                }
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(sessions, key = { it.id }) { entry ->
                    SessionTab(
                        entry = entry,
                        isSelected = entry.id == selectedId,
                        onSelect = { onSelect(entry.id) },
                        onClose = { pendingCloseId = entry.id },
                    )
                }
            }

            Surface(
                onClick = onNewSession,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(Modifier.padding(PSSpacing.md)) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "New session",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Surface(
                onClick = { overflowOpen = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(PSSpacing.md)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(20.dp),
                    )
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New session") },
                            onClick = {
                                overflowOpen = false
                                onNewSession()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Close session") },
                            onClick = {
                                overflowOpen = false
                                selectedId?.let { pendingCloseId = it }
                            },
                        )
                    }
                }
            }
        }
    }

    // Closing a session is a real process kill — confirm it.
    pendingCloseId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingCloseId = null },
            title = { Text("Close session?") },
            text = { Text("The shell process will be terminated. Its scrollback is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onClose(id)
                    pendingCloseId = null
                }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCloseId = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionTab(
    entry: TerminalSessionManager.SessionEntry,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val container = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        entry.isFinished -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surface
    }
    val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.displayLabel + if (entry.isFinished) " (exited)" else "",
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            if (isSelected) {
                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    Box(Modifier.padding(3.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close session",
                            tint = content,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hosts the vendored TerminalView. The view is created once per selected
 * entry; switching tabs re-attaches the existing TerminalSession — sessions
 * are never destroyed by UI navigation.
 */
@Composable
private fun TerminalViewHost(
    entry: TerminalSessionManager.SessionEntry,
    keyboardState: KeyboardState,
    textSize: Int,
    onTextSizeChange: (Int) -> Unit,
    onSingleTap: () -> Unit,
    onViewCreated: (TerminalView) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pinch font size: accumulate scale until a step threshold is crossed,
    // then change one size step and reset the recognizer.
    val scaleAccum = remember { floatArrayOf(1f) }
    val onScale: (Float) -> Float = { scale ->
        scaleAccum[0] *= scale
        var newSize = textSize
        if (scaleAccum[0] > 1.25f) {
            newSize = (textSize + 2).coerceAtMost(MAX_FONT_SIZE)
        } else if (scaleAccum[0] < 0.8f) {
            newSize = (textSize - 2).coerceAtLeast(MIN_FONT_SIZE)
        }
        if (newSize != textSize) {
            scaleAccum[0] = 1f
            onTextSizeChange(newSize)
        }
        1f // reset gesture scale base
    }
    // Track the size we already applied (renderer fields are package-private upstream).
    val appliedSize = remember { mutableStateOf(Int.MIN_VALUE) }
    AndroidView(
        factory = { ctx ->
            val view = TerminalView(ctx, null)
            view.setTerminalViewClient(
                PocketShellTerminalViewClient(
                    keyboardState = keyboardState,
                    onSingleTap = onSingleTap,
                    onScaleGesture = onScale,
                    // Upstream-documented first-session blinker start: called
                    // once updateSize() has actually created the emulator.
                    onEmulatorReady = { view.setTerminalCursorBlinkerState(true, true) },
                )
            )
            view.attachSession(entry.session)
            view.setTextSize(textSize)
            appliedSize.value = textSize
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            // Focus is required for keyboard input (soft or Bluetooth) to
            // reach the terminal; the post defers until the view is attached.
            view.post { view.requestFocus() }
            onViewCreated(view)
            view
        },
        update = { view ->
            if (view.mTermSession !== entry.session) {
                view.attachSession(entry.session)
            }
            if (appliedSize.value != textSize) {
                // setTextSize → relayout → upstream onSizeChanged → PTY TIOCSWINSZ.
                view.setTextSize(textSize)
                appliedSize.value = textSize
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun EmptyTerminalState(creating: Boolean, onNewSession: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PSEmptyState(
            title = if (creating) "Starting shell…" else "No open sessions",
            body = "Each session is a real shell with its own PTY. " +
                "Open one to start working.",
            icon = Icons.Outlined.Add,
            actionLabel = if (creating) null else "New session",
            onAction = if (creating) null else onNewSession,
        )
    }
}
