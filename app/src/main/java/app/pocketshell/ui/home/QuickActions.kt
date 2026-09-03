package app.pocketshell.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Phase 3.2 — the floating quick-action system (docs/PHASE-3.2-DESIGN.md §8).
 *
 * ONE custom control near the bottom-right (NOT a Material FAB, NOT a bottom
 * navigation bar): tapping rotates + → ×, dims the page behind a scrim, and
 * an upward stack of labeled chips emerges from the control — soft, layered,
 * tactile, 150–220ms, nothing bouncy, nothing looping.
 *
 * The action list is DATA: future capabilities (Files, SSH, Containers, …)
 * become new [QuickAction] entries — no redesign. Fake actions are never
 * rendered; callers only pass features that really exist.
 */
enum class QuickActionGlyph { TERMINAL, LINUX, APP }

data class QuickAction(
    val id: String,
    val label: String,
    val glyph: QuickActionGlyph,
    /** Command-app monogram (glyph == APP). */
    val monogram: String? = null,
    val enabled: Boolean = true,
    val onRun: () -> Unit,
)

@Composable
fun QuickActionsOverlay(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    // ---- scrim ---------------------------------------------------------------
    val scrimAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "quickActionScrim",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(scrimAlpha)
            .background(Color.Black.copy(alpha = 0.42f))
            .then(
                if (expanded) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { expanded = false }
                    }
                } else {
                    Modifier
                },
            ),
    ) {}

    // ---- control column (chips emerge upward from the main control) -----------
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 96.dp, end = 22.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.forEachIndexed { index, action ->
                var chipVisible by remember(action.id) { mutableStateOf(false) }
                LaunchedEffect(expanded, action.id) {
                    if (expanded) {
                        kotlinx.coroutines.delay(30L * (actions.size - 1 - index).coerceAtLeast(0))
                        chipVisible = true
                    } else {
                        chipVisible = false
                    }
                }
                AnimatedVisibility(
                    visible = chipVisible,
                    enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            tween(180, easing = FastOutSlowInEasing),
                        ) { it / 3 },
                    exit = fadeOut(tween(90)),
                ) {
                    QuickActionChip(action = action, onRun = { expanded = false; action.onRun() })
                }
            }

            QuickActionButton(expanded = expanded, onToggle = { expanded = !expanded })
        }
    }
}

/** The main control: 56dp Sapphire circle, + rotates to ×. Position never moves. */
@Composable
private fun QuickActionButton(expanded: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "quickActionRotate",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "quickActionPress",
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(HomeTokens.accent, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = if (expanded) "Close quick actions" else "Open quick actions",
            ) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        PlusGlyph(
            size = 22.dp,
            color = HomeTokens.onAccent,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/** A labeled chip: icon circle (deck tone, hairline) + label pill. */
@Composable
private fun QuickActionChip(action: QuickAction, onRun: () -> Unit) {
    val shape = RoundedCornerShape(HomeTokens.chipRadius)
    Row(
        modifier = Modifier
            .alpha(if (action.enabled) 1f else 0.45f)
            .shadow(3.dp, shape, spotColor = Color.Black)
            .background(HomeTokens.surfaceRaised, shape)
            .border(1.dp, HomeTokens.hairline, shape)
            .then(
                if (action.enabled) {
                    Modifier.clickable(role = Role.Button) { onRun() }
                } else {
                    Modifier
                },
            )
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickActionIconCircle(action)
        Spacer(Modifier.width(10.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.width(2.dp))
    }
}

@Composable
private fun QuickActionIconCircle(action: QuickAction) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(HomeTokens.surfaceApp, CircleShape)
            .border(1.dp, HomeTokens.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (action.glyph) {
            QuickActionGlyph.TERMINAL -> TerminalMark(size = 16.dp)
            QuickActionGlyph.LINUX -> MountainMark(size = 17.dp)
            QuickActionGlyph.APP -> Text(
                text = action.monogram ?: "?",
                fontFamily = TerminalTheme.mono,
                fontSize = 13.sp,
                color = HomeTokens.accentBright,
            )
        }
    }
}
