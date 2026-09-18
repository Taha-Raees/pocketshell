package app.pocketshell.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.PortProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * M8 — the Servers widget: the guest's listening ports and what owns them,
 * read straight from the kernel tables the app's UID can see (no proot
 * spawn, no PTY, no guest command — docs/M8-WIDGET-SYSTEM.md §4).
 *
 * Refresh policy: parse-tick every 5s while Home is composed AND resumed
 * (lifecycle-aware; backgrounded the loop is cancelled, leaving Home stops
 * it entirely); the expensive owner scan runs only when the listener inode
 * set changes. The tap opens the Terminal — servers are worked with there.
 */
object ServersWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = "servers",
        name = "Servers",
        summary = "Listening ports and what owns them — dev servers at a glance",
        isCore = false,
    )

    override fun tapLabel(context: WidgetContentContext): String = "Open the Terminal"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = { context.nav.openTerminal() }

    private const val REFRESH_MS = 5_000L
    private const val MAX_ROWS = 3

    private sealed interface Ui {
        data object Probing : Ui
        data object Unavailable : Ui
        data object Unreadable : Ui
        data class Ready(val listeners: List<ListeningSocket>) : Ui
    }

    @Composable
    override fun Content(context: WidgetContentContext) {
        var ui by remember { mutableStateOf<Ui>(Ui.Probing) }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                ui = Ui.Unavailable
                return@LaunchedEffect
            }
            val probe = PortProbe()
            // Lifecycle-aware tick: resumed → probe; otherwise the collector
            // is suspended (no background polling; leaving Home ends the
            // effect outright).
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (true) {
                        val snapshot = withContext(Dispatchers.IO) { probe.snapshot() }
                        ui = when {
                            !snapshot.readable -> Ui.Unreadable
                            else -> Ui.Ready(snapshot.listeners)
                        }
                        delay(REFRESH_MS)
                    }
                }
        }

        val palette = WidgetPalette.of(spec.tone)
        val listeners = (ui as? Ui.Ready)?.listeners.orEmpty()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ServersMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (listeners.isNotEmpty()) {
                    Text(
                        text = "${listeners.size} running",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = palette.dim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            listeners.take(MAX_ROWS).forEach { listener ->
                Text(
                    text = ":${listener.port}  ${listener.processName ?: "unknown"}",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.title,
                    maxLines = 1,
                )
            }
            if (listeners.size > MAX_ROWS) {
                Text(
                    text = "+${listeners.size - MAX_ROWS} more",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.dim,
                    maxLines = 1,
                )
            }
            if (listeners.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                text = "Servers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Spacer(Modifier.height(2.dp))
            val stateLine = when {
                ui is Ui.Probing -> "Looking…"
                ui is Ui.Unavailable -> "Linux not ready"
                ui is Ui.Unreadable -> "Port tables unavailable"
                listeners.isEmpty() -> "Nothing listening"
                else -> "Local listeners"
            }
            val stateColor = if (ui is Ui.Ready && listeners.isNotEmpty()) palette.accent else palette.dim
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor,
                maxLines = 1,
            )
        }
    }
}
