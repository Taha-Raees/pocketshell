package app.pocketshell.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Compact width threshold per Material window size classes (compact < 600dp). */
@Composable
private fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

private const val REPEAT_INITIAL_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 60L

/**
 * The PocketShell built-in terminal keyboard (brief §7/§8/§11).
 *
 * - An in-app composable (NOT an InputMethodService); it is the primary typing
 *   surface inside the terminal.
 * - Modifier taps cycle OFF → ONE_SHOT → LOCKED; the state is always visible
 *   (container color, font weight and a lock dot for LOCKED).
 * - Keys marked repeatable auto-repeat while held; ordinary character keys
 *   repeat as well, matching hardware keyboard behaviour.
 * - Phone layout prioritises terminal controls and a small footprint; the
 *   tablet layout exposes the full key set including F1–F12.
 */
@Composable
fun TerminalKeyboard(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    modifier: Modifier = Modifier,
) {
    val tablet = isTabletWidth()
    var page by remember { mutableStateOf(KeyboardPage.ALPHA) }
    val rowHeight = if (tablet) 38.dp else 44.dp
    val togglePage = {
        page = if (page == KeyboardPage.ALPHA) KeyboardPage.SYMBOL else KeyboardPage.ALPHA
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (tablet) {
                ControlRow(keyboardState, dispatcher, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletFunctionRow, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletTerminalRow, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletDigitRow, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletRowQ, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletRowA, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletRowZ, rowHeight)
                KeyRow(keyboardState, dispatcher, KeyLayouts.tabletBottomRow, rowHeight)
            } else {
                when (page) {
                    KeyboardPage.ALPHA -> {
                        ControlRow(keyboardState, dispatcher, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneDigitRow, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowQ, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowA, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowZ, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneBottomRow, rowHeight, togglePage)
                    }

                    KeyboardPage.SYMBOL -> {
                        ControlRow(keyboardState, dispatcher, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow1, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow2, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow3, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneExtendedRow, rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolBottomRow, rowHeight, togglePage)
                    }
                }
            }
        }
    }
}

/** Control row: ESC + TAB as dispatched keys, then CTRL / ALT / FN / SHIFT slots. */
@Composable
private fun ControlRow(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    rowHeight: Dp,
) {
    val leading = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE), "ESC"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_TAB), "TAB", weight = 1.2f),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        leading.forEach { key ->
            KeyboardKeyButton(
                key = key,
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                rowHeight = rowHeight,
                onPageToggle = null,
                modifier = Modifier.weight(key.weight),
            )
        }
        KeyLayouts.modifierSlots.forEach { slot ->
            ModifierButton(
                slot = slot,
                keyboardState = keyboardState,
                rowHeight = rowHeight,
                modifier = Modifier.weight(slot.weight),
            )
        }
    }
}

@Composable
private fun KeyRow(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    keys: List<KeyboardKey>,
    rowHeight: Dp,
    onPageToggle: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { key ->
            KeyboardKeyButton(
                key = key,
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                rowHeight = rowHeight,
                onPageToggle = onPageToggle,
                modifier = Modifier.weight(key.weight),
            )
        }
    }
}

@Composable
private fun KeyboardKeyButton(
    key: KeyboardKey,
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    rowHeight: Dp,
    onPageToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    val isPageToggle = key.action is KeyAction.Code &&
        (key.action as KeyAction.Code).keyCode == KeyLayouts.KEYCODE_PAGE_TOGGLE

    // While FN is active, show the FN meaning of the key (digits → F-keys).
    val label = if (keyboardState.fnActive) fnLabelFor(key) ?: key.label else key.label

    fun startPress() {
        pressed = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (isPageToggle) {
            onPageToggle?.invoke()
            return
        }
        when (val action = key.action) {
            is KeyAction.Text -> {
                dispatcher.press(action)
                repeatJob = scope.launch {
                    delay(REPEAT_INITIAL_DELAY_MS)
                    var count = 0
                    while (true) {
                        dispatcher.repeat(action, ++count)
                        delay(REPEAT_INTERVAL_MS)
                    }
                }
            }

            is KeyAction.Code -> {
                dispatcher.press(action)
                if (action.repeatable) {
                    repeatJob = scope.launch {
                        delay(REPEAT_INITIAL_DELAY_MS)
                        var count = 0
                        while (true) {
                            dispatcher.repeat(action, ++count)
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                }
            }
        }
    }

    fun endPress() {
        pressed = false
        repeatJob?.cancel()
        repeatJob = null
    }

    KeySurface(
        label = label,
        pressed = pressed,
        locked = false,
        rowHeight = rowHeight,
        modifier = modifier.pointerInput(key.label) {
            detectTapGestures(
                onPress = {
                    startPress()
                    try {
                        awaitRelease()
                    } finally {
                        endPress()
                    }
                },
            )
        },
    )
}

@Composable
private fun ModifierButton(
    slot: ModifierSlot,
    keyboardState: KeyboardState,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val mods by keyboardState.modifiers.collectAsState()
    val state = mods[slot.key] ?: ModifierState.OFF

    KeySurface(
        label = slot.label,
        pressed = state != ModifierState.OFF,
        locked = state == ModifierState.LOCKED,
        rowHeight = rowHeight,
        modifier = modifier.pointerInput(slot.key) {
            detectTapGestures(
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    keyboardState.tap(slot.key)
                },
            )
        },
        bold = state != ModifierState.OFF,
    )
}

@Composable
private fun KeySurface(
    label: String,
    pressed: Boolean,
    locked: Boolean,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val container = when {
        locked -> MaterialTheme.colorScheme.primary
        pressed -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        locked -> MaterialTheme.colorScheme.onPrimary
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .height(rowHeight)
            .scale(if (pressed) 0.96f else 1f),
        shape = RoundedCornerShape(7.dp),
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = contentColor,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(5.dp)
                        .background(contentColor, CircleShape),
                )
            }
            content()
        }
    }
}

/** Labels shown while FN is active (digits → F1..F10, -/= → F11/F12). */
private fun fnLabelFor(key: KeyboardKey): String? {
    val action = key.action as? KeyAction.Text ?: return null
    return when (action.c) {
        '1' -> "F1"
        '2' -> "F2"
        '3' -> "F3"
        '4' -> "F4"
        '5' -> "F5"
        '6' -> "F6"
        '7' -> "F7"
        '8' -> "F8"
        '9' -> "F9"
        '0' -> "F10"
        '-' -> "F11"
        '=' -> "F12"
        else -> null
    }
}
