package app.pocketshell.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Explore CLI Apps (brief §12/§18).
 *
 * HONEST PLACEHOLDER: there is no package manager yet. Per the project brief,
 * PocketShell must never simulate installation — so this screen says exactly
 * what is true today and what arrives with M2. No fake catalog, no fake
 * "Install" buttons.
 */
@Composable
fun ExploreAppsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text(
                text = "Explore CLI Apps",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(
                title = "Nothing to install yet — and that is honest.",
                body = "CLI application installation arrives with the M2 milestone, " +
                    "together with a real Linux userspace and a real package manager. " +
                    "PocketShell does not simulate installation progress or installed " +
                    "state, so until the real installer exists this screen stays empty.",
            )
            InfoCard(
                title = "What is already real today",
                body = "The architecture behind this screen is complete: a data-driven " +
                    "CLI app model, a persistent registry that starts empty, and a " +
                    "launcher that verifies an executable exists before opening a real " +
                    "terminal session for it. When M2 lands, installed apps appear " +
                    "here and on the Home screen automatically.",
            )
            InfoCard(
                title = "What the terminal can already do",
                body = "The Terminal entry gives you a real shell on your device. " +
                    "Everything available in the Android system environment — shell " +
                    "built-ins and toybox utilities — can be used there directly.",
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
