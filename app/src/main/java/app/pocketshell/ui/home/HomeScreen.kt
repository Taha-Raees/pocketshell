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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pocketshell.TerminalViewModel
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.terminal.TerminalSessionManager
import app.pocketshell.ui.components.PSActionTile
import app.pocketshell.ui.components.PSAssistantFab
import app.pocketshell.ui.components.PSBanner
import app.pocketshell.ui.components.PSBannerSeverity
import app.pocketshell.ui.components.PSMenuButton
import app.pocketshell.ui.components.PSHeroCard
import app.pocketshell.ui.components.PSListCard
import app.pocketshell.ui.components.PSLogo
import app.pocketshell.ui.components.PSStatusDot
import app.pocketshell.ui.components.PSStatusPill
import app.pocketshell.ui.theme.PSSpacing

/**
 * Home (docs/UI-REDESIGN.md §5) — the gateway into the user's Linux
 * environment. Brand block, a clean launcher grid (Terminal / Linux Shell /
 * Apps / Packages), active sessions, honest error banner, assistant FAB.
 *
 * Deliberately absent: CLI-utility launcher cards (they are packages, found
 * via Packages search), project cards, IDE concepts, fake recents.
 */
@Composable
fun HomeScreen(
    terminalViewModel: TerminalViewModel,
    activeSessions: List<TerminalSessionManager.SessionEntry>,
    runtimeState: RuntimeState,
    launchError: String?,
    onDismissLaunchError: () -> Unit,
    onMenu: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenApps: () -> Unit,
    onOpenPackages: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PSSpacing.gutter)
                .padding(bottom = 96.dp),
        ) {
            HomeHeader(runtimeState, onMenu)
            Spacer(Modifier.height(PSSpacing.sm))
            BrandBlock()
            Spacer(Modifier.height(PSSpacing.xxl))

            // ---- launcher grid ------------------------------------------
            PSHeroCard(
                title = "Terminal",
                supporting = "Your shell sessions — the heart of PocketShell",
                icon = Icons.Outlined.Terminal,
                onClick = onOpenTerminal,
            )
            Spacer(Modifier.height(PSSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(PSSpacing.md)) {
                PSActionTile(
                    title = "Linux Shell",
                    supporting = linuxShellSupporting(runtimeState),
                    icon = Icons.Outlined.Computer,
                    onClick = {
                        if (runtimeState == RuntimeState.READY) onOpenLinuxShell()
                        else onOpenDiagnostics()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    contentDescriptionText = if (runtimeState == RuntimeState.READY) {
                        "Open the Linux shell"
                    } else {
                        "Linux shell unavailable: ${linuxShellSupporting(runtimeState)}"
                    },
                )
                PSActionTile(
                    title = "Apps",
                    supporting = "Launchable apps in your Linux environment",
                    icon = Icons.Outlined.SmartToy,
                    onClick = onOpenApps,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(PSSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(PSSpacing.md)) {
                PSActionTile(
                    title = "Packages",
                    supporting = "Search and install Alpine packages",
                    icon = Icons.Outlined.Explore,
                    onClick = onOpenPackages,
                    modifier = Modifier.weight(1f),
                )
                // Fourth slot intentionally empty — a future real destination
                // (File Explorer) takes it when it actually exists. Never a
                // placeholder pretending to work.
                Spacer(Modifier.weight(1f))
            }

            if (launchError != null) {
                Spacer(Modifier.height(PSSpacing.lg))
                PSBanner(
                    severity = PSBannerSeverity.ERROR,
                    message = launchError,
                    icon = Icons.Outlined.ErrorOutline,
                    onDismiss = onDismissLaunchError,
                    actionLabel = "Diagnostics",
                    onAction = {
                        onDismissLaunchError()
                        onOpenDiagnostics()
                    },
                )
            }

            if (activeSessions.isNotEmpty()) {
                Spacer(Modifier.height(PSSpacing.xl))
                SectionLabel("Active sessions")
                Spacer(Modifier.height(PSSpacing.sm))
                activeSessions.forEach { entry ->
                    PSListCard(
                        title = entry.displayLabel + if (entry.isFinished) " (exited)" else "",
                        supporting = if (entry.isFinished) {
                            "Session has ended — its scrollback stays readable"
                        } else {
                            "Live session"
                        },
                        leading = {
                            Box(
                                Modifier.padding(top = 2.dp),
                            ) { PSStatusDot(active = !entry.isFinished) }
                        },
                        onClick = { onOpenSession(entry.id) },
                    )
                    Spacer(Modifier.height(PSSpacing.sm))
                }
            }
        }

        PSAssistantFab(
            onClick = onOpenAiSettings,
            icon = Icons.Outlined.SmartToy,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(PSSpacing.xl),
        )
    }
}

private fun linuxShellSupporting(state: RuntimeState): String = when (state) {
    RuntimeState.READY -> "Enter the installed Alpine guest"
    RuntimeState.NOT_INSTALLED -> "Not installed — set up from Diagnostics"
    RuntimeState.DOWNLOADING,
    RuntimeState.VERIFYING,
    RuntimeState.EXTRACTING,
    RuntimeState.CONFIGURING, -> "Install in progress — see Diagnostics"
    RuntimeState.FAILED -> "Install failed — retry from Diagnostics"
    RuntimeState.REPAIR_REQUIRED -> "Repair needed — open Diagnostics"
    RuntimeState.UNSUPPORTED_ABI -> "No arm64 CPU — runtime unavailable"
}

@Composable
private fun HomeHeader(runtimeState: RuntimeState, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PSSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PSMenuButton(onClick = onMenu)
        Spacer(Modifier.weight(1f))
        PSStatusPill(
            text = when (runtimeState) {
                RuntimeState.READY -> "Linux ready"
                else -> runtimeState.name.lowercase().replace('_', ' ')
            },
            ok = runtimeState == RuntimeState.READY,
        )
    }
}

@Composable
private fun BrandBlock() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PSSpacing.xl, bottom = PSSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PSLogo(size = 64.dp)
        Spacer(Modifier.height(PSSpacing.lg))
        Text(
            text = "PocketShell",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your Linux environment, in your pocket",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
