package app.pocketshell.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.home.HomeTokens

/** P3 notice auto-dismisses — fired once per real connect transition, never spamming. */
const val EXTERNAL_KEYBOARD_NOTICE_AUTO_DISMISS_MS = 4500L

/**
 * M7.1 P3 — the transient in-app notice shown when an external keyboard is
 * detected and the shared deck hides itself (spec PART D).
 *
 * It lives at the app ROOT (not the system notification shade): the detection
 * event is meaningful only while PocketShell is running, and this way the
 * existing permission set stays untouched — no POST_NOTIFICATIONS, no new
 * channel, no framework. Fired exactly once per connect transition by the
 * detector; the root auto-dismisses it and it never re-fires while the
 * keyboard stays connected. "Settings" opens the page that owns the
 * automatic-behavior toggle.
 */
@Composable
fun ExternalKeyboardNoticeBar(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(HomeTokens.chipRadius))
            .background(HomeTokens.surfaceBanner)
            .border(1.dp, HomeTokens.accent.copy(alpha = 0.35f), RoundedCornerShape(HomeTokens.chipRadius))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = null,
                tint = HomeTokens.accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "External keyboard detected",
                style = MaterialTheme.typography.bodyMedium,
                color = HomeTokens.textPrimary,
            )
        }
        Spacer(Modifier.size(2.dp))
        Text(
            text = "The on-screen keyboard has been turned off. You can " +
                "change this in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            modifier = Modifier.padding(start = 26.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = HomeTokens.textDim)
            }
            TextButton(onClick = onOpenSettings) {
                Text("Settings", color = HomeTokens.accent)
            }
        }
    }
}
