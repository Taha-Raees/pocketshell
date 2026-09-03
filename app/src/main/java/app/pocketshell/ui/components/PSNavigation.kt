package app.pocketshell.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DrawerState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.navigation.Screen
import app.pocketshell.ui.theme.PSMotion
import app.pocketshell.ui.theme.PSSpacing

/**
 * The PocketShell hamburger (docs/UI-REDESIGN.md §4): two lines, the upper
 * one slightly shorter, drawn — subtle by design.
 */
@Composable
fun PSHamburgerIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val y1 = size.height * 0.34f
        val y2 = size.height * 0.66f
        // Upper line: slightly shorter, left-aligned.
        drawLine(
            color = color,
            start = Offset(0f, y1),
            end = Offset(size.width * 0.62f, y1),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Lower line: full width.
        drawLine(
            color = color,
            start = Offset(0f, y2),
            end = Offset(size.width, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The tappable menu button: hamburger inside a 44dp disc, with an
 * accessibility label (icon-only control — brief §16).
 */
@Composable
fun PSMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = "Open menu" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            PSHamburgerIcon()
        }
    }
}

/**
 * Screen header: hamburger + title + optional trailing actions. Every screen
 * uses this — no screen carries its own back-arrow language; back goes Home
 * (system back) and the drawer is always top-left.
 */
@Composable
fun PSScreenHeader(
    title: String,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PSSpacing.lg, vertical = PSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onMenu,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .size(44.dp)
                .semantics { contentDescription = "Open menu" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                PSHamburgerIcon()
            }
        }
        Spacer(Modifier.width(PSSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

/**
 * Navigation drawer content (docs/UI-REDESIGN.md §4): logo header, real
 * destinations only, runtime status footer. Future destinations append a
 * row — nothing else changes. Rendered inside a DismissibleDrawerSheet
 * (M3 1.4's successor of the modal drawer).
 */
@Composable
fun PSNavDrawerContent(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    runtimeStatusLabel: String,
    runtimeStatusOk: Boolean,
    modifier: Modifier = Modifier,
    onLinuxShell: (() -> Unit)? = null,
    drawerState: DrawerState? = null,
) {
    DismissibleDrawerSheet(drawerState = drawerState ?: return) {
        Column(
            Modifier.padding(horizontal = PSSpacing.lg, vertical = PSSpacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PSLogo(size = 40.dp)
                Spacer(Modifier.width(PSSpacing.lg))
                Column {
                    Text(
                        "PocketShell",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Linux, in your pocket",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(PSSpacing.xxl))
            // Real destinations first…
            Screen.entries.forEach { destination ->
                NavigationDrawerItem(
                    label = { Text(destination.label) },
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                        )
                    },
                    selected = destination == current,
                    onClick = { onNavigate(destination) },
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(PSSpacing.xs))
            }
            // …then the one drawer row that spawns a session rather than
            // switching screens (Linux Shell): same honest spawn flow as Home.
            onLinuxShell?.let {
                NavigationDrawerItem(
                    label = { Text("Linux Shell") },
                    icon = {
                        Icon(
                            Icons.Outlined.Computer,
                            contentDescription = null,
                        )
                    },
                    selected = false,
                    onClick = it,
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(PSSpacing.xs))
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.padding(PSSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PSStatusPill(
                    text = runtimeStatusLabel,
                    ok = runtimeStatusOk,
                )
            }
        }
    }
}

/**
 * The assistant floating icon (docs/UI-REDESIGN.md §10): elegant, quiet,
 * bottom-right. Opens the AI Assistant configuration — it never pretends to
 * chat. Scales gently on entrance (once) per the motion spec.
 */
@Composable
fun PSAssistantFab(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "AI Assistant settings" },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/** Press-scale helper for tiles/cards (docs/UI-REDESIGN.md §3.5). */
@Composable
fun pressScale(pressed: Boolean): Float = if (pressed) 0.98f else 1f

/** Animated height used by keyboard strip popups (UI.5). */
@Composable
fun animatedStripHeight(visible: Boolean, height: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val animated by animateDpAsState(
        targetValue = if (visible) height else 0.dp,
        animationSpec = tween(PSMotion.NORMAL_MS),
        label = "stripHeight",
    )
    return animated
}
