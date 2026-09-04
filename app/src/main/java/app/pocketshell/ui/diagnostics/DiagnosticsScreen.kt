package app.pocketshell.ui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.launch
import app.pocketshell.diagnostics.Diagnostics
import app.pocketshell.runtime.RuntimeDiagnostics
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimePin
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.FactState
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.system.MidnightFactRow
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel

/**
 * Diagnostics (brief §diagnostics): read-only facts about the real runtime.
 * No simulated values, no "everything looks great" decoration.
 *
 * Phase 3.5 (docs/PHASE-3.5-DESIGN.md §4): the page joins the Midnight
 * Sapphire system — mono section labels, hairline dividers, mono fact values
 * with honest state coloring (Sapphire = confirmed-good, danger =
 * confirmed-bad). The 3.4 structure is unchanged: one hierarchy — System /
 * Linux runtime / Package environment — and actions carry exactly two
 * weights: filled Sapphire (install/retry) and quiet hairline (remove/check).
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
    val scope = rememberCoroutineScope()
    var pkgReport by remember { mutableStateOf<app.pocketshell.packages.PackageEnvironmentReport?>(null) }
    var pkgChecking by remember { mutableStateOf(false) }
    var pkgError by remember { mutableStateOf<String?>(null) }

    MidnightPageScaffold(title = "Diagnostics", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- System ----------------------------------------------------
            MidnightSectionLabel("System")
            Spacer(Modifier.height(4.dp))
            rows.forEach { row ->
                MidnightFactRow(
                    label = row.label,
                    value = row.value,
                    valueState = when (row.ok) {
                        true -> FactState.OK
                        false -> FactState.FAIL
                        null -> FactState.NEUTRAL
                    },
                )
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Linux runtime ----------------------------------------------
            MidnightSectionLabel("Linux runtime")
            Spacer(Modifier.height(4.dp))
            MidnightFactRow(
                label = "State",
                value = runtimeState.name,
                valueState = when (runtimeState) {
                    RuntimeState.READY -> FactState.OK
                    RuntimeState.FAILED, RuntimeState.REPAIR_REQUIRED, RuntimeState.UNSUPPORTED_ABI ->
                        FactState.FAIL
                    else -> FactState.NEUTRAL
                },
            )
            MidnightFactRow(
                label = "Distribution",
                value = if (runtimeState.expectsRuntimeOnDisk) {
                    runtimeReport.metadata
                        ?.let { "${it.distribution} ${it.distributionVersion} (${it.architecture})" }
                        ?: "metadata unreadable"
                } else {
                    "${RuntimePin.DISTRIBUTION} ${RuntimePin.DISTRIBUTION_VERSION} (not installed)"
                },
            )
            MidnightFactRow(
                label = "Runtime size",
                value = runtimeReport.runtimeSizeBytes?.let(RuntimeDiagnostics::formatBytes) ?: "—",
            )
            MidnightFactRow("Free space", RuntimeDiagnostics.formatBytes(runtimeReport.freeBytes))
            runtimeReport.rootfsEntryCount?.let {
                MidnightFactRow("Rootfs files", it.toString())
            }
            runtimeEvent?.let { event ->
                MidnightFactRow(
                    label = "Last event",
                    value = describe(event),
                    valueState = if (event is app.pocketshell.runtime.RuntimeInstallEvent.Failed) {
                        FactState.FAIL
                    } else {
                        FactState.NEUTRAL
                    },
                )
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    if (runtimeState == RuntimeState.READY) {
                        MidnightQuietButton(
                            text = label,
                            onClick = { RuntimeManager.remove() },
                            destructive = true,
                        )
                    } else {
                        MidnightFilledButton(
                            text = label,
                            onClick = {
                                if (runtimeState == RuntimeState.REPAIR_REQUIRED) {
                                    RuntimeManager.repair()
                                } else {
                                    RuntimeManager.startInstall()
                                }
                            },
                            enabled = runtimeState != RuntimeState.UNSUPPORTED_ABI,
                        )
                    }
                }
            }
            if (runtimeState == RuntimeState.UNSUPPORTED_ABI) {
                MidnightNote(
                    text = "M2 runtime ships an aarch64 (arm64-v8a) rootfs, " +
                        "which this device does not report among its supported ABIs.",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))

            // ---- Package environment (explicit check only) -------------------
            // Nothing here runs on open: the check execs the guest (`apk
            // --version`) and reads package config files — only because the
            // user pressed the button. No network, no database writes.
            MidnightSectionLabel("Package environment")
            Spacer(Modifier.height(4.dp))
            MidnightNote(
                text = "Nothing is checked automatically — press the button. " +
                    "The check includes one real apk update (network) so failures show their true cause.",
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                MidnightQuietButton(
                    text = if (pkgChecking) "Checking…" else "Check package environment",
                    onClick = {
                        pkgChecking = true
                        pkgError = null
                        scope.launch {
                            pkgReport = try {
                                app.pocketshell.packages.PackageGateway.checkEnvironment()
                            } catch (t: Throwable) {
                                pkgError = t.message ?: t.javaClass.simpleName
                                null
                            } finally {
                                pkgChecking = false
                            }
                        }
                    },
                    enabled = !pkgChecking,
                )
            }
            pkgError?.let {
                MidnightBanner(message = "Check failed: $it", modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
            }
            pkgReport?.let { report ->
                val inColumn = Modifier.padding(horizontal = 20.dp)
                Column(inColumn) {
                    MidnightFactRow(
                        label = "Runtime",
                        value = if (report.runtimeReady) "READY" else "not READY",
                        valueState = if (report.runtimeReady) FactState.OK else FactState.FAIL,
                    )
                    MidnightFactRow("apk", report.apkVersion ?: report.apkError ?: "—")
                    MidnightFactRow("Repositories", report.repositories?.joinToString(", ") ?: "—")
                    MidnightFactRow(
                        "Package database",
                        report.worldPackages?.let { "present ($it packages in world)" } ?: "unreadable",
                    )
                    MidnightFactRow(
                        "Guest DNS",
                        when {
                            report.dnsServers?.isNotEmpty() == true ->
                                report.dnsServers.joinToString(", ") +
                                    " — " + (report.dnsSource ?: "")
                            report.dnsConfigured -> "configured — " + (report.dnsSource ?: "")
                            else -> "missing (repairs on first package operation)"
                        },
                    )
                    MidnightFactRow(
                        label = "Repository fetch",
                        value = when {
                            report.updateProbeOk == null -> "not probed"
                            report.updateProbeOk == true -> "OK — ${(report.updateProbeDetail ?: "").take(120)}"
                            else -> "FAILED — ${(report.updateProbeDetail ?: "unknown error").take(200)}"
                        },
                        valueState = when (report.updateProbeOk) {
                            true -> FactState.OK
                            false -> FactState.FAIL
                            null -> FactState.NEUTRAL
                        },
                    )
                    MidnightFactRow("apk fd-link patch", report.apkFdLinkPatch ?: "—")
                    MidnightFactRow("Interactive /proc", report.guestProcPolicy ?: "—")
                    MidnightFactRow("sysdata overlays", report.sysDataOverlays ?: "—")
                }
                Spacer(Modifier.height(8.dp))
            }
            if (pkgChecking) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = HomeTokens.accent,
                    )
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        text = "Asking the real guest…",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
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
