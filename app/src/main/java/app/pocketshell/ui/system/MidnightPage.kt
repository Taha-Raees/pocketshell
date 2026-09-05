package app.pocketshell.ui.system

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Phase 3.5 — the Midnight Sapphire page kit for the system pages
 * (Diagnostics, Packages, Settings), closing the design gap the 3.3/3.4
 * phases left: those pages still wore the old app-theme Material look while
 * Home and Terminal had moved to the fixed Midnight identity.
 *
 * One shared scaffold + one widget vocabulary, so all three pages read as the
 * same OS as Home:
 *
 *   SCAFFOLD   screenBg canvas, edge-to-edge, 720dp centered, mono page title
 *   WAYFINDING mono section labels + hairline dividers (never boxes)
 *   FACTS      label/value rows, mono values, honest state coloring
 *   ACTIONS    two weights only — filled Sapphire, quiet hairline
 *   SURFACES   cards ONLY for real objects (an installable package, a
 *              search field), 14dp radius, borderless at rest
 *
 * NO pure black, NO gradients, ONE accent (Sapphire) per page; danger appears
 * only on real failure/destructive state.
 */

/** The shared page frame: Midnight canvas + back header + centered column. */
@Composable
fun MidnightPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.screenBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = HomeTokens.contentMaxWidth)
                    .align(Alignment.CenterHorizontally),
            ) {
                MidnightPageHeader(title = title, onBack = onBack)
                content()
            }
        }
    }
}

/** Back chevron + mono page title — the header every system page shares. */
@Composable
fun MidnightPageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
            text = title,
            fontFamily = TerminalTheme.mono,
            fontWeight = FontWeight.Medium,
            fontSize = 21.sp,
            letterSpacing = 0.3.sp,
            color = HomeTokens.textPrimary,
        )
    }
}

/** Section label — mono, dim, letterspaced (the workspace's wayfinding). */
@Composable
fun MidnightSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = TerminalTheme.mono,
        fontSize = 13.sp,
        letterSpacing = 1.6.sp,
        color = HomeTokens.textDim,
    )
}

/** The quiet way a Midnight page separates regions — a hairline, never a box. */
@Composable
fun MidnightSectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 20.dp),
        color = HomeTokens.hairline,
    )
}

/** Honest value coloring shared by every fact row. */
enum class FactState { OK, FAIL, NEUTRAL }

/**
 * One fact: dim label, mono value. The 3.4 rhythm (label 42% / value 58%)
 * carried into the Midnight language; state colors are honest — Sapphire for
 * confirmed-good, danger for confirmed-bad, quiet text otherwise.
 */
@Composable
fun MidnightFactRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueState: FactState = FactState.NEUTRAL,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HomeTokens.textDim,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            fontFamily = TerminalTheme.mono,
            fontSize = 13.sp,
            color = when (valueState) {
                FactState.OK -> HomeTokens.accent
                FactState.FAIL -> HomeTokens.danger
                FactState.NEUTRAL -> HomeTokens.textPrimary
            },
            modifier = Modifier.weight(0.58f),
        )
    }
}

/** Free text on the canvas (explainers, notes) — dim, never a container. */
@Composable
fun MidnightNote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HomeTokens.textDim,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

// ------------------------------------------------------------------ buttons

/** The strong action: Sapphire-deep fill, bright text, 44dp, soft press. */
@Composable
fun MidnightFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PressFeedback(modifier = modifier, enabled = enabled, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) HomeTokens.accentDeep else HomeTokens.surfaceApp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                // Text rides the dedicated on-accentDeep pair token so the
                // fill/text contract holds in BOTH themes.
                color = if (enabled) HomeTokens.onAccentDeep else HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * The quiet action: transparent at rest, hairline border, primary text.
 * [destructive] swaps the tone to danger (border 35%, danger text) — the
 * one destructive weight the kit allows.
 */
@Composable
fun MidnightQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val tone = if (destructive) HomeTokens.danger else HomeTokens.textPrimary
    PressFeedback(modifier = modifier, enabled = enabled, onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (destructive) tone.copy(alpha = 0.35f) else HomeTokens.hairline,
                    RoundedCornerShape(12.dp),
                )
                .background(if (enabled) Color.Transparent else HomeTokens.surfaceApp.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = if (!enabled) HomeTokens.textDim else tone,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** Shared soft-scale press feedback (the Home language, kit-local). */
@Composable
private fun PressFeedback(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.985f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "midnightPressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) { onClick() },
    ) {
        content()
    }
}

// ------------------------------------------------------------------ banner

/**
 * The honest banner (the Home LaunchErrorBanner's system-page sibling):
 * surfaceBanner tone, danger border only when it really is a failure.
 */
@Composable
fun MidnightBanner(
    message: String,
    modifier: Modifier = Modifier,
    failed: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HomeTokens.chipRadius))
            .background(HomeTokens.surfaceBanner)
            .border(
                1.dp,
                if (failed) HomeTokens.danger.copy(alpha = 0.35f) else HomeTokens.hairline,
                RoundedCornerShape(HomeTokens.chipRadius),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textPrimary,
        )
        actions?.let { action ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) { action() }
        }
    }
}

// ------------------------------------------------------------------ card

/**
 * A surface for a REAL object only (an installable package, the search
 * field): chrome tone, 14dp radius, borderless at rest — the 3.3 §4 rule.
 */
@Composable
fun MidnightCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(HomeTokens.chipRadius),
        color = HomeTokens.surfaceEnv,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

// ------------------------------------------------------------------ text field

/**
 * The Midnight search/input field: chrome tone plate, hairline border that
 * wakes to Sapphire on focus, mono text, dim placeholder. A real object —
 * it may be a surface.
 */
@Composable
fun MidnightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(HomeTokens.chipRadius))
            .background(HomeTokens.surfaceEnv)
            .border(
                1.dp,
                if (focused) HomeTokens.accent.copy(alpha = 0.55f) else HomeTokens.hairline,
                RoundedCornerShape(HomeTokens.chipRadius),
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = TerminalTheme.mono,
            fontSize = 14.sp,
            color = HomeTokens.textPrimary,
        ),
        cursorBrush = SolidColor(HomeTokens.accent),
        visualTransformation = VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 14.sp,
                            color = HomeTokens.textDim.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}

// ------------------------------------------------------------------ controls

/** The Midnight switch: Sapphire track at rest, dark thumb (the Enter-key weight). */
@Composable
fun MidnightSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = HomeTokens.accent,
            checkedThumbColor = HomeTokens.onAccent,
            checkedBorderColor = HomeTokens.accent,
            checkedIconColor = HomeTokens.onAccent,
            uncheckedTrackColor = HomeTokens.surfaceApp,
            uncheckedThumbColor = HomeTokens.textDim,
            uncheckedBorderColor = HomeTokens.hairline,
            uncheckedIconColor = HomeTokens.textDim,
        ),
    )
}

/**
 * The Midnight radio row: whole-row touch target (≥48dp, the 3.4 OS-idiom
 * rule), flat at rest, banner tone only while pressed; the radio renders as
 * a quiet ring + Sapphire dot — never a Material checkbox dropped on Midnight.
 */
@Composable
fun MidnightRadioRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) HomeTokens.surfaceBanner else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClickLabel = "Select $label",
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) HomeTokens.accent else HomeTokens.hairline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(HomeTokens.accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = HomeTokens.textPrimary,
            )
            sublabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
        }
    }
}
