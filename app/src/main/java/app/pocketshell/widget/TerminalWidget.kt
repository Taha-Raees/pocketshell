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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.home.TerminalMark
import app.pocketshell.ui.theme.TerminalTheme

/**
 * M8 — the Terminal card AS a widget (the default slot-1 core; its visuals
 * are the historical TerminalTile, unchanged). Real session counts only.
 */
object TerminalWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = WidgetRegistry.TERMINAL_ID,
        name = "Terminal",
        summary = "The native shell — open it, see running sessions",
        isCore = true,
        tone = WidgetTone.Canvas,
        widthWeight = 1.25f,
    )

    override fun tapLabel(context: WidgetContentContext): String = "Open the Terminal"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = { context.nav.openTerminal() }

    @Composable
    override fun Content(context: WidgetContentContext) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TerminalMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (context.runningSessions > 0) {
                    // Real session counts only — plain mono text, no chip box.
                    Text(
                        text = "${context.runningSessions} running",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.onHeroDim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.onHero,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Native shell",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.onHeroDim,
                maxLines = 1,
            )
        }
    }
}
