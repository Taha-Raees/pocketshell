package app.pocketshell.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.ui.components.PSScreenHeader
import app.pocketshell.ui.components.PSSectionLabel
import app.pocketshell.diagnostics.Diagnostics
import app.pocketshell.runtime.RuntimeDiagnostics
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimePin
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.ui.theme.PSSpacing
import kotlinx.coroutines.launch

/**
 * Diagnostics (docs/UI-REDESIGN.md §11): read-only facts about the real
 * runtime, grouped into design-system cards. No simulated values, no
 * "everything looks great" decoration — every row and every button from the
 * M2.x milestones is preserved verbatim.
 */
@Composable
fun DiagnosticsScreen(onMenu: () -> Unit, modifier: Modifier = Modifier) {
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PSSpacing.gutter),
    ) {
        PSScreenHeader(
            title = "Diagnostics",
            subtitle = "Real runtime facts — read-only",
            onMenu = onMenu,
        )

        // ---- device -----------------------------------------------------
        DiagnosticsSection("Device") {
            rows.forEachIndexed { index, row ->
                FactRow(
                    label = row.label,
                    value = row.value,
                    valueColor = when (row.ok) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }

        // ---- M2.2: Linux runtime facts + install controls ---------------
        DiagnosticsSection("Linux runtime") {
            FactRow("State", runtimeState.name)
            FactRow(
                "Distribution",
                if (runtimeState.expectsRuntimeOnDisk) {
                    runtimeReport.metadata
                        ?.let { "${it.distribution} ${it.distributionVersion} (${it.architecture})" }
                        ?: "metadata unreadable"
                } else {
                    "${RuntimePin.DISTRIBUTION} ${RuntimePin.DISTRIBUTION_VERSION} (not installed)"
                },
            )
            FactRow(
                "Runtime size",
                runtimeReport.runtimeSizeBytes?.let(RuntimeDiagnostics::formatBytes) ?: "—",
            )
            FactRow("Free space", RuntimeDiagnostics.formatBytes(runtimeReport.freeBytes))
            runtimeReport.rootfsEntryCount?.let {
                FactRow("Rootfs files", it.toString())
            }
            runtimeEvent?.let { event ->
                FactRow("Last event", describe(event))
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
                        .padding(top = PSSpacing.md),
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
                    modifier = Modifier.padding(top = PSSpacing.sm),
                )
            }
        }

        // ---- M2.4: package environment (explicit check only) -------------
        // Nothing here runs on open: the check execs the guest (`apk
        // --version`) and reads package config files — only because the user
        // pressed the button. No network, no database writes.
        DiagnosticsSection("Package environment") {
            Text(
                text = "Nothing is checked automatically — press the button. " +
                    "The check includes one real apk update (network) so failures " +
                    "show their true cause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val scope = rememberCoroutineScope()
            var pkgReport by remember {
                mutableStateOf<app.pocketshell.packages.PackageEnvironmentReport?>(null)
            }
            var pkgChecking by remember { mutableStateOf(false) }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PSSpacing.md),
            ) {
                Text(if (pkgChecking) "Checking…" else "Check package environment")
            }
            pkgReport?.let { report ->
                Spacer(Modifier.height(PSSpacing.sm))
                FactRow("Runtime", if (report.runtimeReady) "READY" else "not READY")
                FactRow("apk", report.apkVersion ?: report.apkError ?: "—")
                FactRow(
                    "Repositories",
                    report.repositories?.joinToString(", ") ?: "—",
                )
                FactRow(
                    "Package database",
                    report.worldPackages?.let { "present ($it packages in world)" } ?: "unreadable",
                )
                FactRow(
                    "Guest DNS",
                    when {
                        report.dnsServers?.isNotEmpty() == true ->
                            report.dnsServers.joinToString(", ") +
                                " — " + (report.dnsSource ?: "")
                        report.dnsConfigured -> "configured — " + (report.dnsSource ?: "")
                        else -> "missing (repairs on first package operation)"
                    },
                )
                FactRow(
                    "Repository fetch",
                    when {
                        report.updateProbeOk == null -> "not probed"
                        report.updateProbeOk == true ->
                            "OK — ${(report.updateProbeDetail ?: "").take(120)}"
                        else -> "FAILED — ${(report.updateProbeDetail ?: "unknown error").take(200)}"
                    },
                )
                FactRow("apk fd-link patch", report.apkFdLinkPatch ?: "—")
                FactRow("Interactive /proc", report.guestProcPolicy ?: "—")
                FactRow("sysdata overlays", report.sysDataOverlays ?: "—")
            }
        }

        Spacer(Modifier.height(PSSpacing.xl))
    }
}

/** A diagnostics card: section label + bordered surface of honest facts. */
@Composable
private fun DiagnosticsSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(bottom = PSSpacing.lg)) {
        PSSectionLabel(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(PSSpacing.lg),
                content = content,
            )
        }
    }
}

@Composable
private fun FactRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
            color = valueColor,
            modifier = Modifier.weight(0.58f),
        )
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
