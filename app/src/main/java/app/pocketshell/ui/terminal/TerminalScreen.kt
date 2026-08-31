package app.pocketshell.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.keyboard.TerminalKeyDispatcher
import app.pocketshell.keyboard.TerminalKeyboard
import app.pocketshell.terminal.PocketShellTerminalViewClient
import app.pocketshell.terminal.TerminalSessionManager
import com.termux.view.TerminalView

private const val DEFAULT_FONT_SIZE = 28
private const val MIN_FONT_SIZE = 12
private const val MAX_FONT_SIZE = 40

/**
 * Terminal screen (brief §13): session tabs on top, the real TerminalView
 * dominating the screen, the PocketShell keyboard at the bottom.
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyboardVisible by remember { mutableStateOf(true) }
    var textSize by rememberSaveable { mutableIntStateOf(initialFontSize) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Modifier state must never leak across sessions (brief §14).
    LaunchedEffect(selectedId) { keyboardState.clearAll() }

    // Upstream contract: TerminalView does not observe session data — the host
    // must call TerminalView#onScreenUpdated() whenever the session screen
    // changes, otherwise output stays invisible until a layout pass forces a
    // repaint (observed on device: typed echo only appeared after toggling
    // the keyboard). The listener is invoked on the main thread.
    DisposableEffect(Unit) {
        TerminalSessionManager.onScreenUpdateListener = { _ ->
            terminalViewRef.value?.onScreenUpdated()
        }
        onDispose { TerminalSessionManager.onScreenUpdateListener = null }
    }

    // Cursor blinker is a host duty (upstream setTerminalCursorBlinkerState
    // docs): stop it when the host is not visible, restart on resume. The
    // initial start happens in onEmulatorSet (TerminalViewHost factory).
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

    Column(modifier = modifier.fillMaxSize()) {
        TabStrip(
            sessions = sessions,
            selectedId = selectedId,
            keyboardVisible = keyboardVisible,
            onSelect = onSelect,
            onClose = onClose,
            onNewSession = onNewSession,
            onToggleKeyboard = { keyboardVisible = !keyboardVisible },
            onBack = onBack,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (selected != null) {
                TerminalViewHost(
                    entry = selected,
                    keyboardState = keyboardState,
                    textSize = textSize,
                    onTextSizeChange = { textSize = it },
                    onSingleTap = { if (!keyboardVisible) keyboardVisible = true },
                    onViewCreated = { terminalViewRef.value = it },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EmptyTerminalState(
                    creating = creating,
                    onNewSession = onNewSession,
                )
            }
        }

        if (keyboardVisible) {
            // Captures the holder, not the view — always dispatches to the live view.
            val dispatcher = remember {
                TerminalKeyDispatcher(keyboardState) { event ->
                    terminalViewRef.value?.dispatchKeyEvent(event)
                }
            }
            TerminalKeyboard(
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TabStrip(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    keyboardVisible: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back to Home")
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(sessions, key = { it.id }) { entry ->
                    val isSelected = entry.id == selectedId
                    AssistChip(
                        onClick = { onSelect(entry.id) },
                        label = {
                            Text(
                                text = entry.displayLabel + if (entry.isFinished) " (exited)" else "",
                                maxLines = 1,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Close session",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onClose(entry.id) },
                            )
                        },
                    )
                }
            }

            IconButton(onClick = onToggleKeyboard) {
                Icon(
                    Icons.Outlined.Keyboard,
                    contentDescription = if (keyboardVisible) "Hide keyboard" else "Show keyboard",
                )
            }
            FilledIconButton(onClick = onNewSession) {
                Icon(Icons.Outlined.Add, contentDescription = "New session")
            }
        }
    }
}

/**
 * Hosts the vendored TerminalView. The view is created once per selected
 * entry; switching tabs re-attaches the existing TerminalSession — sessions
 * are never destroyed by UI navigation (brief §14/§24).
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
    // Pinch font size (brief §15/§30-friendly): accumulate scale until a step
    // threshold is crossed, then change one size step and reset the recognizer.
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
        factory = { context ->
            val view = TerminalView(context, null)
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
            // Focus is required for hardware (e.g. Bluetooth) keyboard input
            // to reach the terminal; the post defers until the view is attached.
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (creating) "Starting shell…" else "No open sessions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Each session is a real shell with its own PTY.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            TextButton(onClick = onNewSession) {
                Text("New session")
            }
        }
    }
}
