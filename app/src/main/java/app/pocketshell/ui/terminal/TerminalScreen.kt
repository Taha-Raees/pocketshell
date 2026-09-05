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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
 * Terminal screen — Phase 3.1 "Midnight Sapphire" (docs/PHASE-3.1-DESIGN.md).
 *
 * One intentional composition, top to bottom:
 *   chrome (back + live session title, under the status bar)
 *   → session tabs (editor-style; the active tab merges into the canvas)
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
        ChromeHeader(title = selected?.displayLabel ?: "Terminal", onBack = onBack)

        TabStrip(
            sessions = sessions,
            selectedId = selectedId,
            onSelect = onSelect,
            onClose = onClose,
            onNewSession = onNewSession,
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

/** Top chrome: back + the live session title, under a barely-there depth gradient. */
@Composable
private fun ChromeHeader(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(TerminalTheme.chromeGradientTop, TerminalTheme.chromeGradientBottom),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = TerminalTheme.textPrimary,
                )
            }
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TerminalTheme.textPrimary,
                modifier = Modifier
                    .padding(start = 2.dp)
                    .weight(1f),
            )
            // Honest expansion slot — deliberately empty, never a fake control.
            Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * Session tabs — editor-style, NOT pills (brief §5): rounded TOP corners,
 * inactive tabs recessed and quiet, active tab in the exact canvas color
 * covering the strip's bottom hairline so it opens into the workspace.
 *
 * Phase 5 §6 — compacted: a 40dp strip (was 44), tighter gaps and paddings,
 * narrower minimum tab width; the active label rides the pinned-light
 * onCanvas token (the active tab is canvas-dark in every theme).
 */
@Composable
private fun TabStrip(
    sessions: List<TerminalSessionManager.SessionEntry>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewSession: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(TerminalTheme.tabStrip),
    ) {
        // Hairline under the whole strip; the active tab paints over (cuts) it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalTheme.divider),
        )
        Row(modifier = Modifier.fillMaxSize().padding(start = 6.dp)) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                contentPadding = PaddingValues(top = 4.dp),
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
                    .padding(start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun SessionTab(
    entry: TerminalSessionManager.SessionEntry,
    isSelected: Boolean,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
) {
    val height by animateDpAsState(
        targetValue = if (isSelected) 40.dp else 30.dp,
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
            .widthIn(min = 84.dp, max = 160.dp)
            .clip(shape)
            .background(container)
            .clickable { onSelect(entry.id) }
            .padding(horizontal = 10.dp),
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
            // The structured top edge: one 2.5dp Sapphire hairline.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(TerminalTheme.accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun CloseTabButton(selected: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Close session",
            modifier = Modifier.size(14.dp),
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
