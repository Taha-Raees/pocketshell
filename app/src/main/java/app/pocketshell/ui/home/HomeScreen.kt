package app.pocketshell.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.TerminalViewModel
import app.pocketshell.apps.CommandApp
import app.pocketshell.packages.PackageOperationState
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.terminal.TerminalSessionManager
import app.pocketshell.ui.theme.TerminalTheme

/**
 * PocketShell Home — the launcher of the PocketShell environment
 * (docs/PHASE-3.2-DESIGN.md). An OS home screen, not a dashboard:
 *
 *   TOP      PocketShell identity · minimal system actions
 *   CENTER   the two foundations — Terminal and Linux
 *   BELOW    command-launchable apps (launcher grid) or the honest empty state
 *   QUIET    compact running-session continuation area
 *   FLOATING custom quick-action control (no bottom navigation bar)
 *
 * Packages are infrastructure and stay on the Packages screen; only apps the
 * guest's login shell confirms appear here. Fixed Midnight Sapphire identity
 * in every app theme — Home belongs beside the Phase 3.1 terminal.
 */
@Composable
fun HomeScreen(
    terminalViewModel: TerminalViewModel,
    activeSessions: List<TerminalSessionManager.SessionEntry>,
    runtimeState: RuntimeState,
    launchError: String?,
    onDismissLaunchError: () -> Unit,
    onOpenTerminal: () -> Unit,
    onNewTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenCommandApp: (CommandApp) -> Unit,
    onExplorePackages: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commandApps by terminalViewModel.commandApps.collectAsStateWithLifecycle()
    val verifyingApp by terminalViewModel.verifyingApp.collectAsStateWithLifecycle()
    val operation by terminalViewModel.packageOperation.collectAsStateWithLifecycle()
    val creating by terminalViewModel.creating.collectAsStateWithLifecycle()

    // Probe availability whenever Home is visible with a READY runtime …
    LaunchedEffect(runtimeState) {
        if (runtimeState == RuntimeState.READY) terminalViewModel.refreshCommandApps()
    }
    // … and after any package operation lands (a fresh install may add apps).
    LaunchedEffect(operation?.id, operation?.state) {
        val state = operation?.state
        if (state == PackageOperationState.SUCCESS || state == PackageOperationState.FAILED) {
            if (runtimeState == RuntimeState.READY) terminalViewModel.refreshCommandApps()
        }
    }

    val quickActions = remember(
        commandApps.apps, runtimeState, creating, onNewTerminal, onOpenLinuxShell, onOpenCommandApp,
    ) {
        buildList {
            commandApps.apps.take(4).forEach { app ->
                add(
                    QuickAction(
                        id = "app-${app.id}",
                        label = app.displayName,
                        glyph = QuickActionGlyph.APP,
                        monogram = app.monogram,
                        enabled = !creating,
                    ) { onOpenCommandApp(app) },
                )
            }
            if (runtimeState == RuntimeState.READY) {
                add(
                    QuickAction(
                        id = "new-linux",
                        label = "New Linux session",
                        glyph = QuickActionGlyph.LINUX,
                        enabled = !creating,
                    ) { onOpenLinuxShell() },
                )
            }
            add(
                QuickAction(
                    id = "new-terminal",
                    label = "New Terminal",
                    glyph = QuickActionGlyph.TERMINAL,
                    enabled = !creating,
                ) { onNewTerminal() },
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalTheme.screenBg),
    ) {
        val columns = when {
            maxWidth >= 840.dp -> 6
            maxWidth >= 600.dp -> 4
            else -> 3
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = HomeTokens.contentMaxWidth)
                    .align(Alignment.CenterHorizontally),
            ) {
                Spacer(Modifier.height(12.dp))
                BrandHeader(onOpenDiagnostics, onOpenSettings)
                Spacer(Modifier.height(28.dp))

                if (launchError != null) {
                    LaunchErrorBanner(
                        message = launchError,
                        onDismiss = onDismissLaunchError,
                        onOpenDiagnostics = { onDismissLaunchError(); onOpenDiagnostics() },
                    )
                    Spacer(Modifier.height(20.dp))
                }

                EnvironmentLaunchers(
                    runtimeState = runtimeState,
                    runningSessions = activeSessions.count { !it.isFinished },
                    onOpenTerminal = onOpenTerminal,
                    onOpenLinuxShell = onOpenLinuxShell,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
                Spacer(Modifier.height(32.dp))

                CommandAppsArea(
                    state = commandApps,
                    verifyingApp = verifyingApp,
                    runtimeReady = runtimeState == RuntimeState.READY,
                    columns = columns,
                    onOpenCommandApp = onOpenCommandApp,
                    onExplorePackages = onExplorePackages,
                )

                if (activeSessions.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    SessionsContinuationArea(activeSessions, onOpenSession)
                }

                Spacer(Modifier.height(16.dp))
                PackagesFooterLink(onExplorePackages)
                // clearance for the floating quick-action control
                Spacer(Modifier.height(140.dp))
            }
        }

        QuickActionsOverlay(actions = quickActions)
    }
}

// ----------------------------------------------------------------- brand header

@Composable
private fun BrandHeader(onOpenDiagnostics: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "PocketShell",
                fontFamily = TerminalTheme.mono,
                fontWeight = FontWeight.Medium,
                fontSize = 21.sp,
                letterSpacing = 0.3.sp,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Your Linux workspace on Android",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
        }
        HeaderIconButton(Icons.Outlined.Info, "Diagnostics", onOpenDiagnostics)
        Spacer(Modifier.width(4.dp))
        HeaderIconButton(Icons.Outlined.Settings, "Settings", onOpenSettings)
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = HomeTokens.textDim,
            modifier = Modifier.size(21.dp),
        )
    }
}

// ------------------------------------------------------------- honest banners

/** Non-fatal launch failure (v0.3.1 contract) — Midnight restyle, same behavior. */
@Composable
private fun LaunchErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(HomeTokens.chipRadius))
            .background(HomeTokens.surfaceBanner)
            .border(1.dp, HomeTokens.danger.copy(alpha = 0.35f), RoundedCornerShape(HomeTokens.chipRadius))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = HomeTokens.textDim)
            }
            TextButton(onClick = onOpenDiagnostics) {
                Text("Diagnostics", color = HomeTokens.accent)
            }
        }
    }
}

// ------------------------------------------------------ environment launchers

/**
 * The two foundations — asymmetric but balanced. Terminal wears the exact
 * canvas color (it IS the terminal); Linux carries the honest runtime state.
 */
@Composable
private fun EnvironmentLaunchers(
    runtimeState: RuntimeState,
    runningSessions: Int,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TerminalTile(
            runningSessions = runningSessions,
            onClick = onOpenTerminal,
            modifier = Modifier.weight(1.25f),
        )
        LinuxTile(
            runtimeState = runtimeState,
            onClick = { if (runtimeState == RuntimeState.READY) onOpenLinuxShell() else onOpenDiagnostics() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TerminalTile(runningSessions: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PressableScale(onClick = onClick, onClickLabel = "Open the Terminal", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(HomeTokens.heroRadius))
                .background(HomeTokens.surfaceHero)
                .border(1.dp, HomeTokens.accentDeep.copy(alpha = 0.55f), RoundedCornerShape(HomeTokens.heroRadius))
                .padding(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TerminalMark(size = 36.dp)
                Spacer(Modifier.weight(1f))
                if (runningSessions > 0) {
                    RunningChip(count = runningSessions)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Native PocketShell environment",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinuxTile(runtimeState: RuntimeState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Honest gate (M2 architecture): READY enters the guest, everything else
    // routes to Diagnostics where install/retry/repair actually live.
    val stateLine = when (runtimeState) {
        RuntimeState.READY -> "Alpine Linux · ready"
        RuntimeState.NOT_INSTALLED -> "Not installed yet"
        RuntimeState.DOWNLOADING,
        RuntimeState.VERIFYING,
        RuntimeState.EXTRACTING,
        RuntimeState.CONFIGURING,
        -> "Install in progress"
        RuntimeState.FAILED -> "Install failed"
        RuntimeState.REPAIR_REQUIRED -> "Repair needed"
        RuntimeState.UNSUPPORTED_ABI -> "No arm64 CPU"
    }
    val supportLine = when (runtimeState) {
        RuntimeState.READY -> "Enter the guest shell"
        RuntimeState.UNSUPPORTED_ABI -> "Runtime unavailable on this device"
        else -> "Set up or repair from Diagnostics"
    }
    PressableScale(
        onClick = onClick,
        onClickLabel = if (runtimeState == RuntimeState.READY) "Open the Linux environment" else "Open Diagnostics",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(HomeTokens.heroRadius))
                .background(HomeTokens.surfaceEnv)
                .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.heroRadius))
                .padding(18.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                MountainMark(size = 36.dp)
                Spacer(Modifier.weight(1f))
                if (runtimeState == RuntimeState.READY) {
                    ReadyDot()
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Linux",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (runtimeState == RuntimeState.READY) HomeTokens.accent else HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportLine,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "N running" mono chip on the Terminal tile — real session counts only. */
@Composable
private fun RunningChip(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HomeTokens.surfaceEnv)
            .border(1.dp, HomeTokens.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = "$count running",
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = HomeTokens.textDim,
        )
    }
}

/** The Linux tile's quiet readiness cue (state text carries the meaning). */
@Composable
private fun ReadyDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(HomeTokens.accent, androidx.compose.foundation.shape.CircleShape),
    )
}

// ------------------------------------------------------------ command app area

@Composable
private fun CommandAppsArea(
    state: TerminalViewModel.CommandAppsState,
    verifyingApp: String?,
    runtimeReady: Boolean,
    columns: Int,
    onOpenCommandApp: (CommandApp) -> Unit,
    onExplorePackages: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (state.apps.isEmpty() && !state.probeError.isNullOrEmpty()) {
            // Probe failed: the honest "could not check" — never a fake "none".
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HomeTokens.chipRadius))
                    .background(HomeTokens.surfaceBanner)
                    .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
                    .padding(18.dp),
            ) {
                HomeSectionLabel("Command apps")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "App availability could not be checked right now — ${state.probeError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
        } else if (state.apps.isEmpty() && runtimeReady && !state.checked) {
            // Probe in flight: a quiet honest placeholder — the launcher never
            // flashes a fake "none installed" while the guest is being asked.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HomeTokens.chipRadius))
                    .background(HomeTokens.surfaceBanner)
                    .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
                    .padding(18.dp),
            ) {
                HomeSectionLabel("Command apps")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = HomeTokens.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Checking the Linux environment…",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
            }
        } else if (state.apps.isEmpty()) {
            // Real empty answer (or runtime not ready yet): the launcher's
            // beautiful empty state — the body text states which truth applies.
            EmptyAppsState(runtimeReady = runtimeReady, onExplorePackages = onExplorePackages)
        } else {
            HomeSectionLabel("Command apps")
            Spacer(Modifier.height(14.dp))
            state.apps.chunked(columns).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    rowApps.forEach { app ->
                        CommandAppTile(
                            app = app,
                            verifying = verifyingApp == app.displayName,
                            onClick = { onOpenCommandApp(app) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowApps.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (state.probeError != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Availability last checked before an error: ${state.probeError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun CommandAppTile(
    app: CommandApp,
    verifying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(HomeTokens.appTileRadius))
            .clickable(
                role = Role.Button,
                onClickLabel = "Open ${app.displayName}",
            ) { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (verifying) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = HomeTokens.accent,
                )
            }
        } else {
            MonogramTile(monogram = app.monogram, size = 64.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyAppsState(runtimeReady: Boolean, onExplorePackages: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HomeTokens.chipRadius))
            .background(HomeTokens.surfaceBanner)
            .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
            .padding(vertical = 30.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GhostTiles(size = 44.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Your tools will appear here",
            style = MaterialTheme.typography.titleMedium,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (runtimeReady) {
                "Interactive command apps installed in your Linux environment — " +
                    "like Hermes Agent — launch directly from this home screen."
            } else {
                "Install the Linux runtime first — interactive command apps live " +
                    "inside your Linux environment and launch from here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, HomeTokens.hairline, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onExplorePackages() }
                .padding(horizontal = 16.dp, vertical = 9.dp),
        ) {
            Text(
                text = "Explore packages",
                style = MaterialTheme.typography.labelLarge,
                color = HomeTokens.accent,
            )
        }
    }
}

// ------------------------------------------------------------- sessions area

/**
 * Compact continuation area — running sessions stay visible without
 * dominating the launcher (capped; the rest remain in the Terminal's tabs).
 */
@Composable
private fun SessionsContinuationArea(
    sessions: List<TerminalSessionManager.SessionEntry>,
    onOpenSession: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        HomeSectionLabel("Sessions")
        Spacer(Modifier.height(10.dp))
        sessions.take(4).forEach { entry ->
            val finished = entry.isFinished
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(HomeTokens.chipRadius))
                    .background(HomeTokens.surfaceBanner)
                    .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius))
                    .clickable(role = Role.Button, onClickLabel = "Return to session") {
                        onOpenSession(entry.id)
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (finished) HomeTokens.hairline else HomeTokens.runningGreen,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = entry.displayLabel + if (finished) " (exited)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (finished) HomeTokens.textDim else HomeTokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "#${entry.id}",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim.copy(alpha = 0.7f),
                    )
                }
            }
        }
        if (sessions.size > 4) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "+${sessions.size - 4} more in Terminal",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ footer

/** Quiet packages affordance — packages are infrastructure; this is their only Home presence. */
@Composable
private fun PackagesFooterLink(onExplorePackages: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Explore packages",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim.copy(alpha = 0.85f),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { onExplorePackages() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// ------------------------------------------------------------------- helpers

/** Soft press feedback (80ms scale) shared by the launch surfaces. */
@Composable
private fun PressableScale(
    onClick: () -> Unit,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.98f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "homePressScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
            ) { onClick() },
    ) {
        content()
    }
}
