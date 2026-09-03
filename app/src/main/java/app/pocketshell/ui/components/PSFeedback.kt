package app.pocketshell.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.theme.PSSpacing

enum class PSBannerSeverity { INFO, OK, WARN, ERROR }

/**
 * Honest status banner (docs/UI-REDESIGN.md §3.5/§3.7): the real message,
 * an optional real output excerpt, Dismiss + one optional action. This is
 * the single banner language for launch errors, probe failures and package
 * operations — no screen invents its own.
 */
@Composable
fun PSBanner(
    severity: PSBannerSeverity,
    message: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val (container, content) = when (severity) {
        PSBannerSeverity.INFO -> MaterialTheme.colorScheme.surfaceContainerHigh to
            MaterialTheme.colorScheme.onSurface
        PSBannerSeverity.OK -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        PSBannerSeverity.WARN -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        PSBannerSeverity.ERROR -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Column(Modifier.padding(horizontal = PSSpacing.lg, vertical = PSSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(PSSpacing.sm))
                }
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            detail?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(PSSpacing.sm))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onAction != null || onDismiss != null) {
                Spacer(Modifier.height(PSSpacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    onDismiss?.let {
                        TextButton(onClick = it) {
                            Text("Dismiss", color = content)
                        }
                    }
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) {
                            Text(actionLabel, color = content)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state (docs/UI-REDESIGN.md §3.5): tinted icon circle, title, an
 * honest body that says what would appear and why it doesn't, optional
 * action. Never a fake placeholder.
 */
@Composable
fun PSEmptyState(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PSSpacing.xxl, vertical = PSSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(PSSpacing.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(PSSpacing.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PSSpacing.sm),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(PSSpacing.lg))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(actionLabel)
            }
        }
    }
}
