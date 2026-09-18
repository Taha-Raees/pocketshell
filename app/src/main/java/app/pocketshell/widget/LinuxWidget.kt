package app.pocketshell.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.home.MountainMark

/**
 * M8 — the Linux card AS a widget (the default slot-2 core; visuals are the
 * historical LinuxTile, unchanged, including the HONEST gate: READY enters
 * the guest, everything else routes to Diagnostics where install/retry/
 * repair actually live).
 */
object LinuxWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = WidgetRegistry.LINUX_ID,
        name = "Linux",
        summary = "The Alpine guest — state, readiness, entry",
        isCore = true,
        tone = WidgetTone.Chrome,
        widthWeight = 1f,
    )

    override fun tapLabel(context: WidgetContentContext): String =
        if (context.runtimeState == RuntimeState.READY) "Open the Linux environment" else "Open Diagnostics"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = {
        if (context.runtimeState == RuntimeState.READY) context.nav.openLinuxShell()
        else context.nav.openDiagnostics()
    }

    @Composable
    override fun Content(context: WidgetContentContext) {
        val stateLine = when (context.runtimeState) {
            RuntimeState.READY -> "Alpine · ready"
            RuntimeState.NOT_INSTALLED -> "Not installed yet"
            RuntimeState.DOWNLOADING,
            RuntimeState.VERIFYING,
            RuntimeState.EXTRACTING,
            RuntimeState.CONFIGURING,
            -> "Installing…"
            RuntimeState.FAILED -> "Install failed"
            RuntimeState.REPAIR_REQUIRED -> "Repair needed"
            RuntimeState.UNSUPPORTED_ABI -> "No arm64 CPU"
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                MountainMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (context.runtimeState == RuntimeState.READY) {
                    // The quiet readiness cue (the state text carries the meaning).
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(HomeTokens.accent, CircleShape),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Linux",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (context.runtimeState == RuntimeState.READY) HomeTokens.accent else HomeTokens.textDim,
                maxLines = 1,
            )
            if (context.runtimeState != RuntimeState.READY && context.runtimeState != RuntimeState.UNSUPPORTED_ABI) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}
