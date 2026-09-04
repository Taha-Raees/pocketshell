package app.pocketshell.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import app.pocketshell.ui.theme.TerminalTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REPEAT_INITIAL_DELAY_MS = 350L
private const val REPEAT_INTERVAL_MS = 60L
private const val HOLD_THRESHOLD_MS = 350L

/** Density profile resolved per pass (brief §23/§24: phones first, then tablets/landscape). */
private data class KeyboardDensity(val rowHeight: Dp, val accessoryHeight: Dp, val compact: Boolean, val tablet: Boolean)

@Composable
private fun keyboardDensity(): KeyboardDensity {
    val configuration = LocalConfiguration.current
    val tablet = configuration.screenWidthDp >= 600
    val compactHeight = configuration.screenHeightDp < 480
    return KeyboardDensity(
        rowHeight = when {
            tablet && compactHeight -> 36.dp
            tablet -> 40.dp
            compactHeight -> 34.dp
            else -> 40.dp
        },
        accessoryHeight = when {
            tablet && compactHeight -> 34.dp
            tablet -> 38.dp
            compactHeight -> 32.dp
            else -> 36.dp
        },
        compact = compactHeight,
        tablet = tablet,
    )
}

/**
 * The PocketShell keyboard deck (Phase 3.1 — docs/PHASE-3.1-DESIGN.md §5).
 *
 * One continuous control system around the collapsible QWERTY body:
 *
 *   Esc  Tab                ←  ↑  ↓  →      top accessory row (always)
 *   … QWERTY body pages …                   collapsible (the [⌨] toggle)
 *   [⌨]  Ctrl  Alt  Space  Shift  ⏎        bottom accessory row (always)
 *
 * Every layer is ours — no system IME is involved anywhere; presses flow
 * through [TerminalKeyDispatcher] into the vendored TerminalView pipeline.
 */
@Composable
fun TerminalKeyboardDeck(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = keyboardDensity()
    var page by remember { mutableStateOf(KeyboardPage.ALPHA) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalTheme.deck)
            // The deck surface extends behind the gesture bar; keys stay above it.
            .navigationBarsPadding()
            .padding(horizontal = 5.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TopAccessoryRow(keyboardState, dispatcher, density)

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(tween(120)),
            exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                when (page) {
                    KeyboardPage.ALPHA -> if (density.tablet) {
                        KeyRow(keyboardState, dispatcher, KeyLayouts.tabletR1, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.tabletR2, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.tabletR3, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.tabletR4, density.rowHeight, onPageToggle = { page = KeyboardPage.SYMBOL })
                    } else {
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneDigitRow, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowQ, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowA, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneRowZ, density.rowHeight, onPageToggle = { page = KeyboardPage.SYMBOL })
                        if (!density.compact) {
                            KeyRow(keyboardState, dispatcher, KeyLayouts.phonePunctRow, density.rowHeight)
                        }
                    }

                    KeyboardPage.SYMBOL -> {
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow1, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow2, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolRow3, density.rowHeight)
                        KeyRow(keyboardState, dispatcher, KeyLayouts.phoneSymbolBottomRow, density.rowHeight, onPageToggle = { page = KeyboardPage.ALPHA })
                    }
                }
            }
        }

        BottomAccessoryRow(keyboardState, dispatcher, density, expanded, onToggleExpanded)
    }
}

/** Esc · Tab on the left, the arrow cluster grouped in an inset panel on the right (brief §11). */
@Composable
private fun TopAccessoryRow(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    density: KeyboardDensity,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PSKey(
                key = KeyLayouts.topRowSpec[0],
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                height = density.accessoryHeight,
                width = 58.dp,
                alt = true,
            )
            PSKey(
                key = KeyLayouts.topRowSpec[1],
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                height = density.accessoryHeight,
                width = 66.dp,
                alt = true,
            )
        }

        // Arrow cluster: deliberately grouped — four keys on one inset panel.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(TerminalTheme.keyAlt.copy(alpha = 0.45f))
                .border(1.dp, TerminalTheme.divider, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Arrows render as Unicode glyphs on the standard key path (one
            // gesture/repeat engine for every key); m4.0.3: wider than tall —
            // easier to hit horizontally while staying in the grouped panel.
            val arrowSize = density.accessoryHeight - 8.dp
            val arrowWidth = arrowSize + 12.dp
            KeyLayouts.topRowSpec.drop(2).forEach { arrow ->
                PSKey(
                    key = arrow,
                    keyboardState = keyboardState,
                    dispatcher = dispatcher,
                    height = arrowSize,
                    width = arrowWidth,
                    alt = true,
                )
            }
        }
    }
}

/** [⌨] · Ctrl · Alt · Space · Shift · Enter — the exact brief §13 order. */
@Composable
private fun BottomAccessoryRow(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    density: KeyboardDensity,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KeyboardToggleButton(expanded, onToggleExpanded, density.accessoryHeight)
        KeyLayouts.modifierSlots.forEachIndexed { index, slot ->
            ModifierButton(
                slot = slot,
                keyboardState = keyboardState,
                height = density.accessoryHeight,
                modifier = Modifier.weight(1.1f),
            )
            if (index == 1) {
                // Space sits between Alt and Shift, weighted wide (brief §16).
                PSKey(
                    key = KeyboardKey(KeyAction.Text(' '), "space"),
                    keyboardState = keyboardState,
                    dispatcher = dispatcher,
                    height = density.accessoryHeight,
                    modifier = Modifier.weight(2.2f),
                    alt = true,
                )
            }
        }
        EnterKey(keyboardState, dispatcher, density.accessoryHeight, Modifier.weight(1.3f))
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { key ->
            val isPageToggle = key.action is KeyAction.Code && key.action.keyCode == KeyLayouts.KEYCODE_PAGE_TOGGLE
            PSKey(
                key = key,
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                height = rowHeight,
                modifier = Modifier.weight(key.weight),
                isPageToggle = isPageToggle,
                onPageToggle = onPageToggle,
            )
        }
    }
}

/**
 * The one composable every dispatching key goes through. Handles:
 *  - immediate dispatch + auto-repeat (letters, symbols, ⌫, space),
 *  - hold-threshold actions (number row → F-keys) with the popup bubble,
 *    committed on release, slide-off cancels,
 *  - the ALPHA/SYMBOL page toggle (never dispatched),
 *  - press scale + surface micro-transitions + haptic ticks.
 */
@Composable
private fun PSKey(
    key: KeyboardKey,
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    height: Dp,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    alt: Boolean = false,
    isPageToggle: Boolean = false,
    onPageToggle: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) } // long-press action armed
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    val container by animateColorAsState(
        targetValue = if (pressed) TerminalTheme.keyPressed else if (alt) TerminalTheme.keyAlt else TerminalTheme.key,
        animationSpec = tween(80),
        label = "keyContainer",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "keyScale",
    )

    fun startPress() {
        pressed = true
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when {
            isPageToggle -> {
                onPageToggle?.invoke()
                return
            }
            key.longPress != null -> {
                // Hold gestures dispatch on RELEASE (tap = fast enough to feel
                // immediate, hold = the Fn layer); no auto-repeat for these keys.
                holdJob = scope.launch {
                    delay(HOLD_THRESHOLD_MS)
                    armed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            else -> {
                dispatcher.press(key.action)
                val repeatable = (key.action as? KeyAction.Code)?.repeatable == true || key.action is KeyAction.Text
                if (repeatable) {
                    repeatJob = scope.launch {
                        delay(REPEAT_INITIAL_DELAY_MS)
                        var count = 0
                        while (true) {
                            dispatcher.repeat(key.action, ++count)
                            delay(REPEAT_INTERVAL_MS)
                        }
                    }
                }
            }
        }
    }

    fun endPress() {
        pressed = false
        holdJob?.cancel()
        holdJob = null
        repeatJob?.cancel()
        repeatJob = null
        if (armed) {
            armed = false
            key.longPress?.let(dispatcher::press)
        } else if (key.longPress != null) {
            // m4.0.3 device fix ("the - key was not working"): a quick tap on
            // a hold-capable key (the digit row, "-", tablet -/=/`) dispatched
            // NOTHING — the primary action only existed on the hold path.
            // The primary action now commits on release for short taps; the
            // hold layer still wins when the threshold is reached.
            dispatcher.press(key.action)
        }
    }

    Box(
        modifier = modifier
            .let { if (width != null) it.width(width) else it }
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(TerminalTheme.keyRadius))
            .background(container)
            .semantics {
                role = Role.Button
                this.contentDescription = if (key.label == "space") "Space" else key.label
            }
            .pointerInput(key) {
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
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.label,
            fontSize = if (key.label.length == 1) 16.sp else 12.sp,
            fontWeight = if (key.label.length == 1) FontWeight.Normal else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            color = TerminalTheme.textPrimary,
        )

        if (armed && key.popupLabel != null) {
            val bubbleOffset = with(LocalDensity.current) {
                IntOffset(0, -(height + 12.dp).roundToPx())
            }
            Popup(
                alignment = Alignment.TopCenter,
                offset = bubbleOffset,
                onDismissRequest = {},
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TerminalTheme.keyPressed)
                        .border(1.dp, TerminalTheme.accentDeep, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = key.popupLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TerminalTheme.accentBright,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardToggleButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    height: Dp,
) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = if (expanded) TerminalTheme.keyActive else TerminalTheme.keyAlt,
        animationSpec = tween(100),
        label = "toggleContainer",
    )
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(height)
            .clip(RoundedCornerShape(TerminalTheme.keyRadius))
            .background(container)
            .border(
                1.dp,
                if (expanded) TerminalTheme.accentDeep else TerminalTheme.divider,
                RoundedCornerShape(TerminalTheme.keyRadius),
            )
            .semantics {
                role = Role.Button
                this.contentDescription = if (expanded) "Hide keyboard" else "Show keyboard"
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Keyboard,
            contentDescription = if (expanded) "Hide keyboard" else "Show keyboard",
            tint = if (expanded) TerminalTheme.accentBright else TerminalTheme.textDim,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ModifierButton(
    slot: ModifierSlot,
    keyboardState: KeyboardState,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val mods by keyboardState.modifiers.collectAsState()
    val state = mods[slot.key] ?: ModifierState.OFF

    val container by animateColorAsState(
        targetValue = when (state) {
            ModifierState.OFF -> TerminalTheme.keyAlt
            ModifierState.ONE_SHOT -> TerminalTheme.keyActive
            ModifierState.LOCKED -> TerminalTheme.accent
        },
        animationSpec = tween(100),
        label = "modifierContainer",
    )
    val contentColor = when (state) {
        ModifierState.OFF -> TerminalTheme.textPrimary
        ModifierState.ONE_SHOT -> TerminalTheme.accentBright
        ModifierState.LOCKED -> TerminalTheme.enterGlyph
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(TerminalTheme.keyRadius))
            .background(container)
            .border(
                1.dp,
                if (state == ModifierState.ONE_SHOT) TerminalTheme.accentDeep else TerminalTheme.divider,
                RoundedCornerShape(TerminalTheme.keyRadius),
            )
            .semantics {
                role = Role.Button
                this.contentDescription = slot.label
                // State is communicated beyond color: fill + outline + dot + text.
                stateDescription = when (state) {
                    ModifierState.OFF -> "inactive"
                    ModifierState.ONE_SHOT -> "next key"
                    ModifierState.LOCKED -> "locked"
                }
            }
            .pointerInput(slot.key) {
                detectTapGestures(onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    keyboardState.tap(slot.key)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = slot.label,
            fontSize = 13.sp,
            fontWeight = if (state == ModifierState.OFF) FontWeight.Medium else FontWeight.SemiBold,
            color = contentColor,
        )
        if (state == ModifierState.LOCKED) {
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

@Composable
private fun EnterKey(
    keyboardState: KeyboardState,
    dispatcher: TerminalKeyDispatcher,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "enterScale",
    )
    val container by animateColorAsState(
        targetValue = if (pressed) TerminalTheme.accentDeep else TerminalTheme.accent,
        animationSpec = tween(80),
        label = "enterContainer",
    )
    val enter = KeyboardKey(KeyAction.Code(android.view.KeyEvent.KEYCODE_ENTER), "Enter")

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(TerminalTheme.keyRadius))
            .background(container)
            .semantics {
                role = Role.Button
                this.contentDescription = "Enter"
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        dispatcher.press(enter.action)
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardReturn,
            contentDescription = "Enter",
            tint = TerminalTheme.enterGlyph,
            modifier = Modifier.size(20.dp),
        )
    }
}
