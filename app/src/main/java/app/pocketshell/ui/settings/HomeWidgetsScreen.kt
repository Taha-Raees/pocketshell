package app.pocketshell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightRadioRow
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.widget.HomeApplications

/**
 * M8.2 — Control Center → Home application. The ONE Home Application Card
 * hosts one PocketShell-native single-page application; this page chooses
 * which. Not a marketplace: the registry (HomeApplications) makes future
 * applications (Storage, Agents, …) a one-entry addition — deliberately
 * not built yet (owner direction).
 */
@Composable
fun HomeWidgetsScreen(
    viewModel: app.pocketshell.widget.HomeApplicationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appId by viewModel.homeAppId.collectAsStateWithLifecycle()

    MidnightPageScaffold(title = "Home application", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The large card on Home is ONE application surface. It " +
                    "is the application's screen: actions change the content " +
                    "inside the card while you stay on Home.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))

            MidnightSectionLabel("Application")
            Spacer(Modifier.height(4.dp))
            HomeApplications.specs.forEach { spec ->
                MidnightRadioRow(
                    selected = appId == spec.id,
                    label = spec.name,
                    sublabel = spec.summary,
                    onClick = { viewModel.select(spec.id) },
                )
            }
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = { viewModel.restoreDefault() },
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text("Restore default", color = HomeTokens.accent)
            }
            Text(
                text = "Future Home applications (Storage, Agents, …) will " +
                    "appear here when they ship.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
