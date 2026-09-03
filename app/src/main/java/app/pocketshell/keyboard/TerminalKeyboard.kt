package app.pocketshell.keyboard

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Compact width threshold (docs/UI-REDESIGN.md §3.9): larger caps ≥ 600dp. */
@Composable
private fun isTabletWidth(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

private const val REPEAT_INITIAL_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 60L

/**
 * The PocketShell accessory keyboard — the FINAL specification
 * (docs/UI-REDESIGN.md §7, binding):
 *
 *   top row:    Esc · Tab · (spring) · ← ↑ ↓ →
 *   [ Android IME — visible between the rows when toggled on ]
 *   bottom row: [⌨] · Ctrl · Alt · Space · Shift · ↵
 *
 * - The keyboard toggle is ICON-ONLY (no ON/OFF text) and permanently sits
 *   first in the bottom row; it shows/hides the Android IME.
 * - Ctrl/Alt/Shift are real modifier toggles (OFF → ONE_SHOT → LOCKED) with
 *   visual state only: tinted cap, bold label, lock dot.
 * - No dedicated Fn key: arrows long-press to HOME/END/PGUP/PGDN; Esc
 *   long-press opens the F1–F12 strip (nothing permanently on the bar).
 * - The Android keyboard provides digits/symbols (with its own long-press).
 */
@Composable
fun TerminalKeyboard(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    imeVisible: Boolean,
    onToggleIme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fStripVisible by remember { mutableStateOf(false) }
    val capHeight = if (isTabletWidth()) 40.dp else 46.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            // ---- F-key strip (Esc long-press) ---------------------------
            AnimatedVisibility(
                visible = fStripVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KeyLayouts.functionKeys.forEach { key ->
                        AccessoryCap(
                            label = key.label,
                            rowHeight = 34.dp,
                            tap = key.action,
                            dispatcher = dispatcher,
                            modifier = Modifier.weight(1f),
                            afterTap = { fStripVisible = false },
                        )
                    }
                }
            }

            // ---- top row: Esc · Tab · (spring) · ← ↑ ↓ → -----------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KeyLayouts.topRowLeading.forEach { key ->
                    val isEsc = key.action is KeyAction.Code &&
                        key.action.keyCode == KeyEvent.KEYCODE_ESCAPE
                    AccessoryCap(
                        label = key.label,
                        rowHeight = capHeight,
                        tap = key.action,
                        dispatcher = dispatcher,
                        modifier = Modifier.weight(key.weight),
                        // Esc long-press opens/closes the F-key strip.
                        onLongPressUI = if (isEsc) {
                            { fStripVisible = !fStripVisible }
                        } else {
                            null
                        },
                    )
                }
                // Spring: arrows stay right-aligned on the top row.
                Spacer(Modifier.weight(1.2f))
                KeyLayouts.topRowArrows.forEach { key ->
                    AccessoryCap(
                        label = key.label,
                        rowHeight = capHeight,
                        tap = key.action,
                        dispatcher = dispatcher,
                        modifier = Modifier.weight(1f),
                        longPress = key.longPress,
                    )
                }
            }

            // ---- bottom row: [⌨] · Ctrl · Alt · Space · Shift · ↵ --------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToggleImeCap(
                    imeVisible = imeVisible,
                    rowHeight = capHeight,
                    modifier = Modifier.weight(1f),
                    onTap = {
                        fStripVisible = false
                        onToggleIme()
                    },
                )
                KeyLayouts.modifierSlots.forEachIndexed { index, slot ->
                    ModifierCap(
                        slot = slot,
                        keyboardState = keyboardState,
                        rowHeight = capHeight,
                        modifier = Modifier.weight(slot.weight),
                    )
                    // Space lives between Alt and Shift per the final spec.
                    if (index == 1) {
                        val space = KeyLayouts.bottomRowKeys.first()
                        AccessoryCap(
                            label = space.label,
                            rowHeight = capHeight,
                            tap = space.action,
                            dispatcher = dispatcher,
                            modifier = Modifier.weight(space.weight),
                            repeatable = true,
                        )
                    }
                }
                val enter = KeyLayouts.bottomRowKeys.last()
                AccessoryCap(
                    label = enter.label,
                    rowHeight = capHeight,
                    tap = enter.action,
                    dispatcher = dispatcher,
                    modifier = Modifier.weight(enter.weight),
                )
            }
        }
    }
}

/**
 * One accessory key cap, driven by [TerminalKeyDispatcher].
 *
 * - No long-press: the tap action fires immediately on touch and (when
 *   repeatable) auto-repeats while held (Space).
 * - With a long-press action (arrows): a short tap fires the tap action on
 *   release; holding past the long-press threshold fires the long-press
 *   action and (when repeatable) repeats IT while still held
 *   (← → HOME/END, ↑ ↓ PGUP/PGDN).
 */
@Composable
private fun AccessoryCap(
    label: String,
    rowHeight: Dp,
    tap: KeyAction,
    dispatcher: TerminalKeyDispatcher,
    modifier: Modifier = Modifier,
    repeatable: Boolean = false,
    longPress: KeyAction? = null,
    onLongPressUI: (() -> Unit)? = null,
    afterTap: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    fun startRepeat(action: KeyAction) {
        if (action is KeyAction.Code && action.repeatable || action is KeyAction.Text) {
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

    val deferredTap = longPress != null || onLongPressUI != null
    KeyCapSurface(
        label = label,
        pressed = pressed,
        locked = false,
        rowHeight = rowHeight,
        modifier = modifier.pointerInput(tap, longPress, onLongPressUI) {
            detectTapGestures(
                onTap = if (deferredTap) {
                    {
                        dispatcher.press(tap)
                        afterTap?.invoke()
                    }
                } else {
                    null
                },
                onPress = {
                    pressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!deferredTap) {
                        dispatcher.press(tap)
                        if (repeatable) startRepeat(tap)
                    }
                    try {
                        awaitRelease()
                    } finally {
                        pressed = false
                        repeatJob?.cancel()
                        repeatJob = null
                    }
                },
                onLongPress = if (longPress != null || onLongPressUI != null) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longPress?.let { lp ->
                            dispatcher.press(lp)
                            startRepeat(lp)
                        }
                        onLongPressUI?.invoke()
                    }
                } else {
                    null
                },
            )
        },
    )
}

/** The keyboard-visibility toggle: ICON ONLY, permanent bottom-row position. */
@Composable
private fun ToggleImeCap(
    imeVisible: Boolean,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val icon: ImageVector =
        if (imeVisible) Icons.Outlined.KeyboardHide else Icons.Outlined.Keyboard
    KeyCapSurface(
        label = "",
        pressed = false,
        locked = false,
        rowHeight = rowHeight,
        modifier = modifier.pointerInput(imeVisible) {
                detectTapGestures(
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTap()
                    },
                )
            },
        icon = icon,
        iconContentDescription = if (imeVisible) "Hide Android keyboard" else "Show Android keyboard",
    )
}

/** Modifier cap: tap cycles OFF → ONE_SHOT → LOCKED → OFF, visual state only. */
@Composable
private fun ModifierCap(
    slot: ModifierSlot,
    keyboardState: KeyboardState,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val mods by keyboardState.modifiers.collectAsState()
    val state = mods[slot.key] ?: ModifierState.OFF

    KeyCapSurface(
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
    )
}

@Composable
private fun KeyCapSurface(
    label: String,
    pressed: Boolean,
    locked: Boolean,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
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
        shape = RoundedCornerShape(10.dp),
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = iconContentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = contentColor,
                    fontWeight = if (pressed) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            if (locked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(5.dp)
                        .background(contentColor, CircleShape),
                )
            }
        }
    }
}
