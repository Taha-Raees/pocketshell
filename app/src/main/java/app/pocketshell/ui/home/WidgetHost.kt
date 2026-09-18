package app.pocketshell.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.settings.CardSize
import app.pocketshell.terminal.AgentHomeSessionClaims
import app.pocketshell.ui.theme.LocalAuroraPhase
import app.pocketshell.ui.theme.auroraEdge
import app.pocketshell.widget.HomeWidget
import app.pocketshell.widget.WidgetContentContext
import app.pocketshell.widget.WidgetNav
import app.pocketshell.widget.WidgetRegistry
import app.pocketshell.widget.WidgetSlotEntry
import app.pocketshell.widget.WidgetTone

/**
 * M8 — the hero-row HOST. Home owns exactly two widget slots; this file is
 * the entire host implementation: it resolves the persisted slot ids
 * through [WidgetRegistry], draws the standard hero chrome (clip → surface
 * tone → aurora edge → 16dp padding, the historical two-card recipe), and
 * delegates everything else to the widget. Adding widget #N never touches
 * this code (docs/M8-WIDGET-SYSTEM.md §2).
 *
 * Host discipline: no probing, no polling, no detector access, no PTY
 * writes — the widgets' data concerns live in the widget package, and the
 * only state the host forwards is the reactive state Home already holds.
 */
@Composable
fun WidgetHeroRow(
    slotIds: List<String>,
    cardSize: CardSize,
    runtimeState: RuntimeState,
    runningSessions: Int,
    agentClaims: Map<Long, AgentHomeSessionClaims.SessionClaim>,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGuestFiles: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Control Center "Card size": the card weight scales the hero tiles'
    // height around the historical 160dp default (unchanged).
    val heroHeight = (160 * cardSize.scale).dp
    val auroraPhase = LocalAuroraPhase.current
    val nav = remember(onOpenTerminal, onOpenLinuxShell, onOpenDiagnostics, onOpenGuestFiles, onOpenWidgetSettings) {
        object : WidgetNav {
            override fun openTerminal() = onOpenTerminal()
            override fun openLinuxShell() = onOpenLinuxShell()
            override fun openDiagnostics() = onOpenDiagnostics()
            override fun openGuestFiles() = onOpenGuestFiles()
            override fun openWidgetSettings() = onOpenWidgetSettings()
        }
    }
    val entries = WidgetRegistry.resolve(
        if (slotIds.isEmpty()) WidgetRegistry.DEFAULT_SLOTS else slotIds,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.take(2).forEach { entry ->
            when (entry) {
                is WidgetSlotEntry.Resolved -> HeroWidgetCard(
                    widget = entry.widget,
                    context = WidgetContentContext(
                        heightDp = heroHeight,
                        auroraPhase = auroraPhase,
                        runtimeState = runtimeState,
                        runningSessions = runningSessions,
                        agentClaims = agentClaims,
                        nav = nav,
                    ),
                    modifier = Modifier.weight(entry.widget.spec.widthWeight),
                )
                is WidgetSlotEntry.Missing -> MissingWidgetCard(
                    widgetId = entry.widgetId,
                    heightDp = heroHeight,
                    auroraPhase = auroraPhase,
                    onClick = onOpenWidgetSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HeroWidgetCard(
    widget: HomeWidget,
    context: WidgetContentContext,
    modifier: Modifier = Modifier,
) {
    val action = widget.tapAction(context)
    val chrome: @Composable () -> Unit = {
        HeroChrome(surface = surfaceFor(widget.spec.tone), heightDp = context.heightDp, auroraPhase = context.auroraPhase) {
            widget.Content(context)
        }
    }
    if (action != null) {
        PressableScale(onClick = action, onClickLabel = widget.tapLabel(context), modifier = modifier, content = chrome)
    } else {
        Box(modifier = modifier) { chrome() }
    }
}

/** A slot whose widget id no longer resolves — stated, never faked. */
@Composable
private fun MissingWidgetCard(
    widgetId: String,
    heightDp: Dp,
    auroraPhase: State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableScale(onClick = onClick, onClickLabel = "Open Home widget settings", modifier = modifier) {
        HeroChrome(surface = HomeTokens.surfaceEnv, heightDp = heightDp, auroraPhase = auroraPhase) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Missing widget",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTokens.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage in Settings → Home widgets",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                )
                Text(
                    text = widgetId,
                    fontFamily = app.pocketshell.ui.theme.TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeroChrome(
    surface: Color,
    heightDp: Dp,
    auroraPhase: State<Float>,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .clip(RoundedCornerShape(HomeTokens.heroRadius))
            .background(surface)
            // Aurora identity: the hero cards are the page's important
            // surfaces — they carry the circulating glow edge.
            .auroraEdge(auroraPhase, HomeTokens.heroRadius)
            .padding(16.dp),
    ) {
        content()
    }
}

private fun surfaceFor(tone: WidgetTone): Color = when (tone) {
    WidgetTone.Canvas -> HomeTokens.surfaceHero
    WidgetTone.Chrome -> HomeTokens.surfaceEnv
}

/** Soft press feedback (80ms scale) shared by the launch surfaces. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PressableScale(
    onClick: () -> Unit,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.98f,
    onLongPress: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "homePressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongPress != null) "Launcher options" else null,
                onClick = { onClick() },
                onLongClick = onLongPress,
            ),
    ) {
        content()
    }
}
