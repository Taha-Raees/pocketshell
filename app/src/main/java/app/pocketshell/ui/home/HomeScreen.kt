package app.pocketshell.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.R
import app.pocketshell.TerminalViewModel
import app.pocketshell.packages.InstalledCatalogApp
import app.pocketshell.packages.PackageOperationState
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.terminal.TerminalSessionManager

/**
 * Home screen (brief §12) — strict, honest hierarchy:
 *   1. Terminal (primary, always)
 *   2. Installed CLI Apps (only what the REAL apk database confirms; empty
 *      until a real install; never pre-populated — v0.4.4: the list comes
 *      from `apk info -e -v` probes, not the M1-era DataStore registry that
 *      nothing wrote and which kept claiming "No apps installed yet" over a
 *      genuinely installed nano)
 *   3. Explore CLI Apps
 *   4. Active sessions (only when they actually exist)
 *
 * System commands are NEVER shown as installed apps (brief §2/§35).
 */
@Composable
fun HomeScreen(
    terminalViewModel: TerminalViewModel,
    activeSessions: List<TerminalSessionManager.SessionEntry>,
    runtimeState: RuntimeState,
    launchError: String?,
    onDismissLaunchError: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenedSession: () -> Unit,
    onExploreApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val installedApps by terminalViewModel.installedCatalogApps.collectAsStateWithLifecycle()
    val probeError by terminalViewModel.installedProbeError.collectAsStateWithLifecycle()
    val operation by terminalViewModel.packageOperation.collectAsStateWithLifecycle()

    // Real state whenever Home becomes visible with a READY runtime …
    LaunchedEffect(runtimeState) {
        if (runtimeState == RuntimeState.READY) terminalViewModel.refreshInstalledCatalogApps()
    }
    // … and after every package operation lands (install/uninstall from
    // Explore must light this list up without leaving the process).
    LaunchedEffect(operation?.id, operation?.state) {
        val state = operation?.state
        if (state == PackageOperationState.SUCCESS || state == PackageOperationState.FAILED) {
            if (runtimeState == RuntimeState.READY) terminalViewModel.refreshInstalledCatalogApps()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(onOpenSettings, onOpenDiagnostics) }
        item { TerminalCard(onOpenTerminal) }
        item { LinuxShellCard(runtimeState, onOpenLinuxShell, onOpenDiagnostics) }
        if (launchError != null) {
            item {
                LaunchErrorCard(
                    message = launchError,
                    onDismiss = onDismissLaunchError,
                    onOpenDiagnostics = {
                        onDismissLaunchError()
                        onOpenDiagnostics()
                    },
                )
            }
        }
        item { InstalledAppsSection(
            installedApps = installedApps,
            probeError = probeError,
            runtimeReady = runtimeState == RuntimeState.READY,
            onOpenApp = { entry ->
                terminalViewModel.openCatalogApp(entry) { onOpenedSession() }
            },
        ) }
        item { ExploreRow(onExploreApps) }
        if (activeSessions.isNotEmpty()) {
            item { ActiveSessionsSection(activeSessions, onOpenSession) }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * Honest, non-fatal launch failure banner (v0.3.1): a refused session spawn
 * is described here — in the app, on the screen the user tapped — instead of
 * killing the process. Dismiss or jump straight to Diagnostics.
 */
@Composable
private fun LaunchErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onOpenDiagnostics) { Text("Diagnostics") }
            }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit, onOpenDiagnostics: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "PocketShell",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "A real Linux terminal & your CLI apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenDiagnostics) {
            Icon(Icons.Outlined.Info, contentDescription = "Diagnostics")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun TerminalCard(onOpenTerminal: () -> Unit) {
    ElevatedCard(
        onClick = onOpenTerminal,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Terminal",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Open a real Linux shell",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun LinuxShellCard(
    state: RuntimeState,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    // Honest gate (docs/M2-ARCHITECTURE §7): the card shows the real runtime
    // state; only READY enters the guest, everything else routes to the
    // Diagnostics screen where install/retry/repair actually live.
    val ready = state == RuntimeState.READY
    val subtitle = when (state) {
        RuntimeState.READY -> "Enter the installed Alpine guest (proot)"
        RuntimeState.NOT_INSTALLED -> "Not installed yet — install from Diagnostics"
        RuntimeState.DOWNLOADING,
        RuntimeState.VERIFYING,
        RuntimeState.EXTRACTING,
        RuntimeState.CONFIGURING -> "Install in progress — see Diagnostics"
        RuntimeState.FAILED -> "Install failed — retry from Diagnostics"
        RuntimeState.REPAIR_REQUIRED -> "Repair needed — open Diagnostics"
        RuntimeState.UNSUPPORTED_ABI -> "This device has no arm64 CPU — runtime unavailable"
    }
    ElevatedCard(
        onClick = { if (ready) onOpenLinuxShell() else onOpenDiagnostics() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Linux Shell",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun InstalledAppsSection(
    installedApps: List<InstalledCatalogApp>,
    probeError: String?,
    runtimeReady: Boolean,
    onOpenApp: (app.pocketshell.packages.CliAppCatalogEntry) -> Unit,
) {
    Column {
        Text(
            text = "Installed CLI Apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (probeError != null) {
            // Honest probe failure: never render "No apps installed yet" when
            // the truth is "we could not ask right now" (v0.4.4).
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = "Installed state unavailable: $probeError\n" +
                        "The list below may be out of date — check the package environment from Diagnostics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else if (installedApps.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(
                    text = if (runtimeReady) {
                        "No apps installed yet"
                    } else {
                        "Install the Linux runtime to add CLI apps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedApps.forEach { app ->
                    Card(
                        onClick = { onOpenApp(app.entry) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = app.entry.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    // description + the REAL version apk reported
                                    text = app.entry.description +
                                        "  \u00b7 Installed \u00b7 " + app.version,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreRow(onExploreApps: () -> Unit) {
    OutlinedButton(
        onClick = onExploreApps,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Explore CLI Apps")
    }
}

@Composable
private fun ActiveSessionsSection(
    sessions: List<TerminalSessionManager.SessionEntry>,
    onOpenSession: (Long) -> Unit,
) {
    Column {
        Text(
            text = "Active Sessions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        sessions.forEach { entry ->
            Surface(
                onClick = { onOpenSession(entry.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp),
                    ) {
                        Surface(
                            color = if (entry.isFinished) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.primary,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.displayLabel + if (entry.isFinished) " (exited)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
