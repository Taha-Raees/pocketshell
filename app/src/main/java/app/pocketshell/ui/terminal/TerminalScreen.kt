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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.keyboard.TerminalKeyDispatcher
import app.pocketshell.keyboard.TerminalKeyboard
import app.pocketshell.terminal.PocketShellTerminalViewClient
import app.pocketshell.terminal.TerminalSessionManager
import com.termux.view.TerminalView

private const val DEFAULT_FONT_SIZE = 28

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
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyboardVisible by remember { mutableStateOf(true) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Modifier state must never leak across sessions (brief §14).
    LaunchedEffect(selectedId) { keyboardState.clearAll() }

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
    onSingleTap: () -> Unit,
    onViewCreated: (TerminalView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            TerminalView(context, null).apply {
                setTerminalViewClient(
                    PocketShellTerminalViewClient(
                        keyboardState = keyboardState,
                        onSingleTap = onSingleTap,
                    )
                )
                attachSession(entry.session)
                setTextSize(DEFAULT_FONT_SIZE)
                isFocusable = true
                isFocusableInTouchMode = true
                onViewCreated(this)
            }
        },
        update = { view ->
            if (view.mTermSession !== entry.session) {
                view.attachSession(entry.session)
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
