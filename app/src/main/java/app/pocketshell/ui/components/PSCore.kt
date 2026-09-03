package app.pocketshell.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.theme.LogoGradientEnd
import app.pocketshell.ui.theme.LogoGradientStart
import app.pocketshell.ui.theme.OkGreen
import app.pocketshell.ui.theme.PSSpacing

/**
 * The PocketShell brand mark (docs/UI-REDESIGN.md §3.4): gradient rounded
 * square, `>_` glyph and a thin terminal-cursor bar. Drawn — not an image
 * asset — so it scales cleanly from drawer header to Home hero.
 */
@Composable
fun PSLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val corner = size * (20f / 64f)
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.linearGradient(listOf(LogoGradientStart, LogoGradientEnd)),
                shape = RoundedCornerShape(corner),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = ">",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp,
            )
            Spacer(Modifier.width(size * 0.06f))
            Box(
                modifier = Modifier
                    .padding(bottom = size * 0.13f)
                    .size(width = size * 0.11f, height = size * 0.2f)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp)),
            )
        }
    }
}

/** Small-caps section label (docs/UI-REDESIGN.md §3.7). */
@Composable
fun PSSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = PSSpacing.xs, vertical = PSSpacing.sm),
    )
}

/** Small round status dot (session state, runtime state). */
@Composable
fun PSStatusDot(active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

/**
 * Compact runtime/status pill (READY, exited, …). Honest text only — the
 * label is the real state name, never a decoration.
 */
@Composable
fun PSStatusPill(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (ok) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PSSpacing.md, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        if (ok) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (ok) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Launcher grid tile (docs/UI-REDESIGN.md §5): icon disc, title, honest
 * supporting line. 48dp+ touch target via the tile's own size.
 */
@Composable
fun PSActionTile(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescriptionText: String? = null,
) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics {
            contentDescriptionText?.let { this.contentDescription = it }
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(PSSpacing.xl)) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(PSSpacing.md))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 2,
            )
        }
    }
}

/**
 * Row card: leading slot, title + supporting line, trailing slot. The work
 *horse for apps, sessions, package results.
 */
@Composable
fun PSListCard(
    title: String,
    supporting: String,
    leading: @Composable () -> Unit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PSSpacing.lg, vertical = PSSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(PSSpacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(PSSpacing.sm))
            trailing()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) { body() }
    }
}

/** Icon inside a 40dp tinted disc — leading slot of [PSListCard]. */
@Composable
fun PSIconDisc(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The ONE gradient card in the app (docs/UI-REDESIGN.md §3.1) — the Home
 * Terminal entry. Dark themes get the ink-blue gradient with light content;
 * light themes get the pale periwinkle gradient with dark content.
 */
@Composable
fun PSHeroCard(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = app.pocketshell.ui.theme.isDarkTheme()
    val brush = if (dark) {
        Brush.linearGradient(
            listOf(
                app.pocketshell.ui.theme.HeroGradientDarkStart,
                app.pocketshell.ui.theme.HeroGradientDarkEnd,
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                app.pocketshell.ui.theme.HeroGradientLightStart,
                app.pocketshell.ui.theme.HeroGradientLightEnd,
            ),
        )
    }
    val content = if (dark) Color(0xFFEEF1FF) else Color(0xFF101A45)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        shadowElevation = if (dark) 2.dp else 1.dp,
    ) {
        Box(
            Modifier
                .background(brush)
                .padding(PSSpacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = content.copy(alpha = 0.14f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.width(PSSpacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = content,
                    )
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.78f),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
