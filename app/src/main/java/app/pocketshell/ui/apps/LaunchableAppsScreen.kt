package app.pocketshell.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.TerminalViewModel
import app.pocketshell.ui.components.PSBanner
import app.pocketshell.ui.components.PSBannerSeverity
import app.pocketshell.ui.components.PSIconDisc
import app.pocketshell.ui.components.PSListCard
import app.pocketshell.ui.components.PSEmptyState
import app.pocketshell.ui.components.PSScreenHeader
import app.pocketshell.ui.components.PSSectionLabel
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.ui.theme.PSSpacing

/**
 * Apps (docs/UI-REDESIGN.md §5/§9): the launchable applications detected in
 * the Linux guest. Rows exist ONLY when the guest confirms the executable
 * right now; the empty state says exactly what would appear and why nothing
 * does yet. Ordinary CLI tools never appear here — Packages search reaches
 * them.
 */
@Composable
fun LaunchableAppsScreen(
    terminalViewModel: TerminalViewModel,
    runtimeState: RuntimeState,
    onMenu: () -> Unit,
    onOpenedSession: () -> Unit,
    onOpenPackages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps by terminalViewModel.launchableApps.collectAsStateWithLifecycle()
    val probeError by terminalViewModel.launchableProbeError.collectAsStateWithLifecycle()
    val launching by terminalViewModel.launchingApp.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    // Re-probe whenever the screen becomes visible with a READY runtime —
    // installs and removals happen in the guest, outside this UI.
    LaunchedEffect(runtimeState) {
        terminalViewModel.refreshLaunchableApps()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PSScreenHeader(
            title = "Apps",
            subtitle = "Launchable applications in your Linux environment",
            onMenu = onMenu,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PSSpacing.gutter),
        ) {
            if (runtimeState != RuntimeState.READY) {
                PSBanner(
                    severity = PSBannerSeverity.INFO,
                    message = "The Linux runtime is not ready yet — " +
                        "apps appear here once the environment is installed.",
                    onDismiss = null,
                )
                Spacer(Modifier.height(PSSpacing.xl))
            } else {
                probeError?.let {
                    PSBanner(
                        severity = PSBannerSeverity.WARN,
                        message = "App detection could not run just now",
                        detail = it + " — the list below may be out of date.",
                    )
                    Spacer(Modifier.height(PSSpacing.lg))
                }
                launchError?.let {
                    PSBanner(
                        severity = PSBannerSeverity.ERROR,
                        message = it,
                    )
                    Spacer(Modifier.height(PSSpacing.lg))
                }
                if (apps.isEmpty() && probeError == null) {
                    PSEmptyState(
                        title = "No launchable apps detected",
                        body = "Applications installed inside the Linux guest that provide an " +
                            "interactive command (for example Hermes or OpenCode) appear here " +
                            "automatically. CLI tools live in Packages instead.",
                        icon = Icons.Outlined.SmartToy,
                    )
                } else {
                    PSSectionLabel("Detected in the Linux guest")
                    Spacer(Modifier.height(PSSpacing.sm))
                    apps.forEach { app ->
                        PSListCard(
                            title = app.name,
                            supporting = app.description,
                            leading = { PSIconDisc(Icons.Outlined.Dns) },
                            onClick = {
                                if (launching == null) {
                                    terminalViewModel.openLaunchableApp(app) { onOpenedSession() }
                                }
                            },
                        )
                        Spacer(Modifier.height(PSSpacing.sm))
                    }
                }
            }
            Text(
                text = "Detection rule: an app appears only while the guest confirms " +
                    "its command (command -v) right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = PSSpacing.lg),
            )
        }
    }
}
