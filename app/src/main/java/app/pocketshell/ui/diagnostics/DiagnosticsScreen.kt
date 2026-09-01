package app.pocketshell.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.pocketshell.diagnostics.Diagnostics
import app.pocketshell.runtime.RuntimeDiagnostics
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimePin
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage

/**
 * Diagnostics (brief §diagnostics): read-only facts about the real runtime.
 * No simulated values, no "everything looks great" decoration.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val rows = remember { Diagnostics.snapshot(context) }
    val runtimeState by RuntimeManager.state.collectAsStateWithLifecycle()
    val runtimeEvent by RuntimeManager.lastEvent.collectAsStateWithLifecycle()
    val storage = remember {
        RuntimeStorage(context.applicationContext.noBackupFilesDir)
    }
    val runtimeReport = remember(runtimeState) {
        RuntimeDiagnostics.report(storage, runtimeState)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text("Diagnostics", style = MaterialTheme.typography.titleLarge)
        }

        rows.forEachIndexed { index, row ->
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (row.ok) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(0.58f),
                    )
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }

        // ---- M2.2: Linux runtime facts + install controls ------------------
        HorizontalDivider(Modifier.padding(top = 10.dp))
        Text(
            text = "Linux runtime",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        RuntimeFactRow("State", runtimeState.name)
        RuntimeFactRow(
            "Distribution",
            if (runtimeState.expectsRuntimeOnDisk) {
                runtimeReport.metadata
                    ?.let { "${it.distribution} ${it.distributionVersion} (${it.architecture})" }
                    ?: "metadata unreadable"
            } else {
                "${RuntimePin.DISTRIBUTION} ${RuntimePin.DISTRIBUTION_VERSION} (not installed)"
            },
        )
        RuntimeFactRow(
            "Runtime size",
            runtimeReport.runtimeSizeBytes?.let(RuntimeDiagnostics::formatBytes) ?: "—",
        )
        RuntimeFactRow("Free space", RuntimeDiagnostics.formatBytes(runtimeReport.freeBytes))
        runtimeReport.rootfsEntryCount?.let {
            RuntimeFactRow("Rootfs files", it.toString())
        }
        runtimeEvent?.let { event ->
            RuntimeFactRow("Last event", describe(event))
        }

        val actionLabel = when {
            runtimeState == RuntimeState.READY -> "Remove runtime"
            runtimeState == RuntimeState.NOT_INSTALLED -> "Install Linux environment"
            runtimeState == RuntimeState.UNSUPPORTED_ABI -> "Unsupported ABI on this device"
            runtimeState == RuntimeState.FAILED ||
                runtimeState == RuntimeState.REPAIR_REQUIRED -> "Retry install"
            else -> null // in-flight states: no action, honest silence
        }
        actionLabel?.let { label ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (runtimeState == RuntimeState.READY) {
                    OutlinedButton(
                        onClick = { RuntimeManager.remove() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                } else {
                    Button(
                        onClick = {
                            if (runtimeState == RuntimeState.REPAIR_REQUIRED) {
                                RuntimeManager.repair()
                            } else {
                                RuntimeManager.startInstall()
                            }
                        },
                        enabled = runtimeState != RuntimeState.UNSUPPORTED_ABI,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label)
                    }
                }
            }
        }
        if (runtimeState == RuntimeState.UNSUPPORTED_ABI) {
            Text(
                text = "M2 runtime ships an aarch64 (arm64-v8a) rootfs, " +
                    "which this device does not report among its supported ABIs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ---- M2.4: package environment (explicit check only) ---------------
        // Nothing here runs on open: the check execs the guest (`apk
        // --version`) and reads package config files — only because the user
        // pressed the button. No network, no database writes.
        HorizontalDivider(Modifier.padding(top = 10.dp))
        Text(
            text = "Package environment",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Text(
            text = "Nothing is checked automatically — press the button. " +
                "The check includes one real apk update (network) so failures show their true cause.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val scope = rememberCoroutineScope()
        var pkgReport by remember { mutableStateOf<app.pocketshell.packages.PackageEnvironmentReport?>(null) }
        var pkgChecking by remember { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    pkgChecking = true
                    scope.launch {
                        pkgReport = try {
                            app.pocketshell.packages.PackageGateway.checkEnvironment()
                        } finally {
                            pkgChecking = false
                        }
                    }
                },
                enabled = !pkgChecking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (pkgChecking) "Checking…" else "Check package environment")
            }
        }
        pkgReport?.let { report ->
            RuntimeFactRow("Runtime", if (report.runtimeReady) "READY" else "not READY")
            RuntimeFactRow(
                "apk",
                report.apkVersion ?: report.apkError ?: "—",
            )
            RuntimeFactRow(
                "Repositories",
                report.repositories?.joinToString(", ") ?: "—",
            )
            RuntimeFactRow(
                "Package database",
                report.worldPackages?.let { "present ($it packages in world)" } ?: "unreadable",
            )
            RuntimeFactRow(
                "Guest DNS",
                when {
                    report.dnsServers?.isNotEmpty() == true ->
                        report.dnsServers.joinToString(", ") +
                            " — " + (report.dnsSource ?: "")
                    report.dnsConfigured -> "configured — " + (report.dnsSource ?: "")
                    else -> "missing (repairs on first package operation)"
                },
            )
            RuntimeFactRow(
                "Repository fetch",
                when {
                    report.updateProbeOk == null -> "not probed"
                    report.updateProbeOk == true -> "OK — ${(report.updateProbeDetail ?: "").take(120)}"
                    else -> "FAILED — ${(report.updateProbeDetail ?: "unknown error").take(200)}"
                },
            )
        }
    }
}

private fun describe(event: app.pocketshell.runtime.RuntimeInstallEvent): String = when (event) {
    is app.pocketshell.runtime.RuntimeInstallEvent.DownloadProgress ->
        "downloading ${RuntimeDiagnostics.formatBytes(event.bytesRead)} / " +
            RuntimeDiagnostics.formatBytes(event.totalBytes)
    app.pocketshell.runtime.RuntimeInstallEvent.Downloaded -> "download complete"
    is app.pocketshell.runtime.RuntimeInstallEvent.VerifyProgress ->
        "verifying ${RuntimeDiagnostics.formatBytes(event.bytesHashed)}"
    is app.pocketshell.runtime.RuntimeInstallEvent.ExtractProgress ->
        "extracting (${event.entriesProcessed} entries)"
    app.pocketshell.runtime.RuntimeInstallEvent.Configured -> "configured, promoting"
    app.pocketshell.runtime.RuntimeInstallEvent.Ready -> "ready"
    is app.pocketshell.runtime.RuntimeInstallEvent.Failed -> "failed: ${event.message}"
}

@Composable
private fun RuntimeFactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}
