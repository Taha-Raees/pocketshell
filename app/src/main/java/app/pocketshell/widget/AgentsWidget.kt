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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme

/**
 * M8 — the Agents widget: the M7.2 activity claims at a glance. It consumes
 * EXACTLY the authoritative projection Home's Sessions section already
 * renders (`AgentActivityRepository.homeSessionClaims`, passed down by the
 * hero host) — no second detector, no /proc, no polling, no new wording.
 * Aggregation is client-side and pure (AgentActivitySummary).
 *
 * The headline counts PROVEN activity only; runtime-unknown sessions are
 * shown as unknown, never folded into "active". Tap → the Terminal, where
 * the agent sessions live.
 */
object AgentsWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = "agents",
        name = "Agents",
        summary = "Coding-agent activity at a glance — running, attention, unknown",
        isCore = false,
    )

    override fun tapLabel(context: WidgetContentContext): String = "Open the Terminal"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = { context.nav.openTerminal() }

    private const val MAX_ROWS = 3

    @Composable
    override fun Content(context: WidgetContentContext) {
        val summary = remember(context.agentClaims) {
            AgentActivitySummary.summarize(context.agentClaims)
        }
        val palette = WidgetPalette.of(spec.tone)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                AgentsMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (summary.rows.isNotEmpty()) {
                    Text(
                        text = "${summary.activeCount} active",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = palette.dim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            summary.rows.take(MAX_ROWS).forEach { row ->
                // The SAME parity vocabulary and color contract as the Sessions
                // rows: worded claims, runningGreen only on proven activity.
                Text(
                    text = row.line,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (row.proven) HomeTokens.runningGreen else palette.dim,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (summary.rows.size > MAX_ROWS) {
                Text(
                    text = "+${summary.rows.size - MAX_ROWS} more in Terminal",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.dim,
                    maxLines = 1,
                )
            }
            if (summary.rows.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                text = "Agents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = AgentActivitySummary.headline(summary),
                style = MaterialTheme.typography.bodySmall,
                color = if (summary.activeCount > 0) HomeTokens.runningGreen else palette.dim,
                maxLines = 1,
            )
        }
    }
}
