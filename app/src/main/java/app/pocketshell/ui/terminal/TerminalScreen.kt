package app.pocketshell.ui.terminal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pocketshell.keyboard.KeyboardInputRouter
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.terminal.PocketShellTerminalViewClient
import app.pocketshell.terminal.TerminalPalette
import app.pocketshell.terminal.TerminalSessionManager
import app.pocketshell.ui.theme.TerminalTheme
import com.termux.view.TerminalView

private const val DEFAULT_FONT_SIZE = 28
private const val MIN_FONT_SIZE = 12
private const val MAX_FONT_SIZE = 40

/**
 * m5.0 final correction — the compact IDE-strip height. The strip IS the
 * workspace's top chrome: the old back+title header row is gone, so the
 * terminal canvas starts directly under the Android status area.
 */
private val WORKSPACE_BAR_HEIGHT = 34.dp
private val TAB_INACTIVE_HEIGHT = 26.dp

/**
 * Terminal screen — Phase 3.1 "Midnight Sapphire" (docs/PHASE-3.1-DESIGN.md).
 *
 * One intentional composition, top to bottom (m5.0 final correction):
 *   workspace bar (back glyph + session tabs + "+", under the status bar —
 *   the separate title/header row was REMOVED; the active session's name
 *   already lives in its tab)
 *   → terminal canvas (deepest blue-black, full-bleed, real TerminalView)
 *
 * m4.0.12: the keyboard deck is NOT part of this screen anymore — it lives
 * at PocketShellRoot, the ONE keyboard over EVERY screen. Its visibility
 * state lives at the root; this screen keeps the terminal canvas registered
 * as a dispatch target and auto-opens the deck on canvas tap.
 *
 * The screen consumes the status-bar inset itself (MainActivity passes the
 * raw modifier for this branch) so the chrome surface extends edge-to-edge;
 * every other screen keeps its Scaffold padding.
 */
@Composable
fun TerminalScreen(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    keyboardState: KeyboardState,
    creating: Boolean,
    initialFontSize: Int = DEFAULT_FONT_SIZE,
    keyboardExpanded: Boolean,
    onKeyboardExpandedChange: (Boolean) -> Unit,
    keyboardBottomInset: Dp,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var textSize by rememberSaveable { mutableIntStateOf(initialFontSize) }
    val selected = sessions.firstOrNull { it.id == selectedId }
    val terminalViewRef = remember { mutableStateOf<TerminalView?>(null) }

    // Modifier state must never leak across sessions.
    LaunchedEffect(selectedId) { keyboardState.clearAll() }

    // The terminal canvas is a dispatch target of the shared deck (m4.0.3);
    // unregister when the screen leaves composition. (The screen-update
    // listener is cleared here too — it is installed in the effect below.)
    DisposableEffect(Unit) {
        onDispose {
            KeyboardInputRouter.terminalTarget = null
            TerminalSessionManager.onScreenUpdateListener = null
        }
    }

    // Upstream contract: TerminalView does not observe session data — the host
    // must call TerminalView#onScreenUpdated() whenever the session screen
    // changes, otherwise output stays invisible until a layout pass forces a
    // repaint. The listener is invoked on the main thread.
    //
    // m5.1 — IDLE-RENDERING RULE: the hook fires for EVERY session's output,
    // but the view shows exactly one session. A background session streaming
    // output (a build, a log tail, `yes`) used to force full repaints of the
    // UNCHANGED visible screen at the background output rate — N running
    // sessions multiplied the repaint load while the user read or typed in
    // the foreground. The repaint now happens only when the producer IS the
    // selected session. Session switches stay correct without this hook:
    // attachSession() nulls the emulator, updateSize() invalidates, and the
    // next frame renders the newly attached session's screen.
    val visibleSessionId by rememberUpdatedState(selectedId)
    DisposableEffect(Unit) {
        TerminalSessionManager.onScreenUpdateListener = { sessionId ->
            if (sessionId == visibleSessionId) {
                terminalViewRef.value?.onScreenUpdated()
            }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.screenBg)
            // m4.0.12: the shared deck lives at the app ROOT now (one keyboard
            // over every screen). While it is up it overlays this screen, so
            // the canvas ends above the deck's measured height (navigation-bar
            // padding included); with the deck hidden the canvas still ends
            // above the gesture bar exactly as before.
            .then(
                if (keyboardBottomInset == 0.dp) Modifier.navigationBarsPadding()
                else Modifier.padding(bottom = keyboardBottomInset),
            ),
    ) {
        WorkspaceBar(
            sessions = sessions,
            selectedId = selectedId,
            onSelect = onSelect,
            onClose = onClose,
            onNewSession = onNewSession,
            onBack = onBack,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = TerminalTheme.canvasBottomRadius,
                        bottomEnd = TerminalTheme.canvasBottomRadius,
                    ),
                )
                .background(TerminalTheme.canvas),
        ) {
            if (selected != null) {
                TerminalViewHost(
                    entry = selected,
                    keyboardState = keyboardState,
                    textSize = textSize,
                    onTextSizeChange = { textSize = it },
                    onSingleTap = { if (!keyboardExpanded) onKeyboardExpandedChange(true) },
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

        // m4.0.12: the deck and its dispatcher live at the app root — the
        // ONE keyboard over EVERY screen (PocketShellRoot). This screen only
        // auto-opens the deck on canvas tap (onSingleTap below) and keeps its
        // dispatch target registered with the shared router.
    }
}

/**
 * m5.0 final correction — the workspace bar IS the top chrome: back at the
 * far LEFT, then the session tabs, then "+" at the right end:
 *
 *   ←   [ Tab ] [ Tab ] [ Tab ]        +
 *
 * The back control is an integrated strip glyph (not a header-sized
 * IconButton), and the strip consumes the status-bar inset itself so the
 * workspace starts directly under the Android status area. Geometry is the
 * compact IDE language: a 34dp strip, active tab 34 / inactive 26, 2dp
 * gaps, 8dp horizontal tab padding, no per-tab borders.
 */
@Composable
private fun WorkspaceBar(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    // Long session lists overflow into a horizontal scroll; whenever a
    // switch lands outside the visible window, bring the ACTIVE tab back
    // into view — one tab must never own the bar, and the active one must
    // never hide off-screen.
    LaunchedEffect(selectedId, sessions.size) {
        val idx = sessions.indexOfFirst { it.id == selectedId }
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
            listState.animateScrollToItem(idx)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalTheme.tabStrip)
            .statusBarsPadding()
            .height(WORKSPACE_BAR_HEIGHT),
    ) {
        // Hairline under the whole strip; the active tab paints over (cuts) it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalTheme.divider),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Back, integrated: a quiet glyph in the exact strip language.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(36.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Home",
                    modifier = Modifier.size(18.dp),
                    tint = TerminalTheme.textDim,
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
                contentPadding = PaddingValues(top = 2.dp),
            ) {
                items(sessions, key = { it.id }) { entry ->
                    SessionTab(
                        entry = entry,
                        isSelected = entry.id == selectedId,
                        onSelect = onSelect,
                        onClose = onClose,
                    )
                }
            }
            NewSessionButton(
                onNewSession = onNewSession,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 4.dp, end = 2.dp),
            )
        }
    }
}

/**
 * Session tabs — editor-style, NOT pills (brief §5): rounded TOP corners,
 * inactive tabs recessed and quiet, active tab in the exact canvas color
 * covering the strip's bottom hairline so it opens into the workspace.
 *
 * m5.0 final correction — significantly compacted IDE language: active tab
 * 34 / inactive 26 (the old 40/30 step felt oversized), 8dp horizontal
 * padding (was 10), tab width 64–136dp (was 84–160), 6dp corner radius
 * (was 10), and a 2dp accent hairline (was 2.5). Readability and touch
 * targets stay intact; the visual language is untouched — only its density
 * changes. Long titles truncate with an ellipsis and the close button
 * always stays reachable.
 */
@Composable
private fun SessionTab(
    entry: TerminalSessionManager.SessionEntry,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
) {
    val height by animateDpAsState(
        targetValue = if (isSelected) WORKSPACE_BAR_HEIGHT else TAB_INACTIVE_HEIGHT,
        animationSpec = tween(140),
        label = "tabHeight",
    )
    val container by animateColorAsState(
        targetValue = if (isSelected) TerminalTheme.canvas else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(140),
        label = "tabContainer",
    )
    val shape = RoundedCornerShape(topStart = TerminalTheme.tabTopRadius, topEnd = TerminalTheme.tabTopRadius)

    Box(
        modifier = Modifier
            .height(height)
            .widthIn(min = 64.dp, max = 136.dp)
            .clip(shape)
            .background(container)
            .clickable { onSelect(entry.id) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.displayLabel + if (entry.isFinished) " (exited)" else "",
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Active tab = canvas tone (pinned dark in every theme) →
                // its label is the pinned-light onCanvas, never theme-swapped.
                color = if (isSelected) TerminalTheme.onCanvas else TerminalTheme.textDim,
                modifier = Modifier.weight(1f, fill = false),
            )
            CloseTabButton(
                selected = isSelected,
                onClose = { onClose(entry.id) },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (!isSelected) {
            // Asymmetric border language: a quiet right separator instead of
            // an outline on every side.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .height(18.dp)
                    .background(TerminalTheme.divider),
            )
        }
        if (isSelected) {
            // The structured top edge: one 2dp Sapphire hairline — subtle,
            // clean, never a heavy border.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TerminalTheme.accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun CloseTabButton(selected: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Close session",
            modifier = Modifier.size(13.dp),
            tint = if (selected) TerminalTheme.onCanvasDim else TerminalTheme.textDim.copy(alpha = 0.6f),
        )
    }
}

/**
 * Phase 5 §7 — the + is INTEGRATED into the strip: a quiet glyph in the
 * exact slot, the same button language as its Companion-strip sibling —
 * no circle plate, no border, no floating-control look:
 *
 *   [ Tab ] [ Tab ] [ Tab ]          +
 */
@Composable
private fun NewSessionButton(onNewSession: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onNewSession() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = "New session",
            modifier = Modifier.size(18.dp),
            tint = TerminalTheme.textDim,
        )
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
    // Pinch font size: accumulate scale until a step threshold is crossed, then
    // change one size step and reset the recognizer.
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
            // Phase 3.1 visual identity: JetBrains Mono NL + the Midnight
            // canvas color as the View background (isOpaque view; the renderer
            // only paints cells whose background differs from the default).
            TerminalPalette.typeface(context)?.let { view.setTypeface(it) }
            view.setBackgroundColor(TerminalTheme.canvas.toArgb())
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            // Focus is required for hardware (e.g. Bluetooth) keyboard input
            // to reach the terminal; the post defers until the view is attached.
            view.post { view.requestFocus() }
            // m4.0.3: register as the deck's terminal dispatch target — the
            // router picks whichever surface (this view or the Companion
            // WebView) currently holds focus.
            KeyboardInputRouter.terminalTarget = view
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
                textAlign = TextAlign.Center,
                color = TerminalTheme.textDim,
            )
            Text(
                text = "Each session is a real shell with its own PTY.",
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalTheme.textDim,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            TextButton(
                onClick = onNewSession,
                colors = ButtonDefaults.textButtonColors(contentColor = TerminalTheme.accent),
            ) {
                Text("New session")
            }
        }
    }
}
