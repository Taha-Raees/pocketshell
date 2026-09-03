package app.pocketshell.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Phase 3.3 "Home & System UI" — the PocketShell workspace
 * (docs/PHASE-3.3-DESIGN.md). An OS home screen drawn on a canvas, NOT a
 * dashboard of rounded boxes:
 *
 *   IDENTITY    PocketShell mark + wordmark + tagline · Info / Settings
 *   FOUNDATION  the two environments — Terminal and Linux — as borderless
 *               tone-step surfaces (§8)
 *   TOOLS       "Your tools" — command apps as icon + label launcher entries
 *               or the lightweight inline empty state (§6/§9)
 *   SESSIONS    flat continuation rows between hairline dividers (§2a)
 *   FLOATING    the single-purpose create control (§7)
 *
 * Surface rules (§3/§4): a surface is drawn ONLY for a real object — an
 * environment, the CLI Apps menu, the floating control, an actionable banner,
 * or a pressed row/tile. Text groupings are separated by spacing, section
 * labels and hairline dividers, never containers. Packages are infrastructure
 * and keep exactly ONE quiet affordance on this page in every state.
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

    // The floating control creates sessions — nothing else (§7). Command apps
    // launch from the tools grid and the CLI Apps menu, never from here.
    val quickActions = remember(runtimeState, creating, onNewTerminal, onOpenLinuxShell) {
        buildList {
            if (runtimeState == RuntimeState.READY) {
                add(
                    QuickAction(
                        id = "new-linux",
                        label = "New Linux session",
                        enabled = !creating,
                    ) { onOpenLinuxShell() },
                )
            }
            add(
                QuickAction(
                    id = "new-terminal",
                    label = "New Terminal",
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
                Spacer(Modifier.height(24.dp))

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

                // ONE CLI control in the header area (§5) — rendered only when
                // the guest actually confirmed apps; never a dead button.
                if (commandApps.apps.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    CliAppsMenu(
                        apps = commandApps.apps,
                        onOpenCommandApp = onOpenCommandApp,
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(24.dp))
                }
                SectionDivider()
                Spacer(Modifier.height(16.dp))

                ToolsSection(
                    state = commandApps,
                    verifyingApp = verifyingApp,
                    runtimeReady = runtimeState == RuntimeState.READY,
                    columns = columns,
                    onOpenCommandApp = onOpenCommandApp,
                    onExplorePackages = onExplorePackages,
                )

                if (activeSessions.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SectionDivider()
                    Spacer(Modifier.height(12.dp))
                    SessionsSection(activeSessions, onOpenSession)
                }

                if (commandApps.apps.isNotEmpty()) {
                    // Exactly ONE packages affordance when tools exist — the
                    // empty state carries it when they don't (§9).
                    Spacer(Modifier.height(20.dp))
                    PackagesFooterLink(onExplorePackages)
                }

                // clearance for the floating create control
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
    icon: ImageVector,
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

// ------------------------------------------------------------- honest banner

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

// ----------------------------------------------------------------- dividers

/** The quiet way a workspace separates regions — a hairline, never a box (§3). */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = HomeTokens.hairline,
    )
}

// ------------------------------------------------------ environment launchers

/**
 * The two foundations (§8) — borderless tone-step surfaces. Terminal wears the
 * canvas tone (it IS a terminal); Linux wears the chrome tone, one step
 * lighter than the page. No borders, no shadows: depth comes from the
 * Midnight Sapphire surface stack alone.
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
                .height(160.dp)
                .clip(RoundedCornerShape(HomeTokens.heroRadius))
                .background(HomeTokens.surfaceHero)
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                TerminalMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (runningSessions > 0) {
                    // Real session counts only — plain mono text, no chip box.
                    Text(
                        text = "$runningSessions running",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim,
                    )
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
                text = "Native shell",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LinuxTile(runtimeState: RuntimeState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Honest gate (M2 architecture): READY enters the guest, everything else
    // routes to Diagnostics where install/retry/repair actually live.
    val stateLine = when (runtimeState) {
        RuntimeState.READY -> "Alpine · ready"
        RuntimeState.NOT_INSTALLED -> "Not installed yet"
        RuntimeState.DOWNLOADING,
        RuntimeState.VERIFYING,
        RuntimeState.EXTRACTING,
        RuntimeState.CONFIGURING,
        -> "Installing…"
        RuntimeState.FAILED -> "Install failed"
        RuntimeState.REPAIR_REQUIRED -> "Repair needed"
        RuntimeState.UNSUPPORTED_ABI -> "No arm64 CPU"
    }
    PressableScale(
        onClick = onClick,
        onClickLabel = if (runtimeState == RuntimeState.READY) "Open the Linux environment" else "Open Diagnostics",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(HomeTokens.heroRadius))
                .background(HomeTokens.surfaceEnv)
                .padding(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                MountainMark(size = 32.dp)
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
            )
            if (runtimeState != RuntimeState.READY && runtimeState != RuntimeState.UNSUPPORTED_ABI) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The Linux tile's quiet readiness cue (the state text carries the meaning). */
@Composable
private fun ReadyDot() {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(HomeTokens.accent, CircleShape),
    )
}

// --------------------------------------------------------------- CLI Apps menu

/**
 * The ONE CLI control (§5): a quiet "CLI Apps ▾" trigger in the header area
 * opening a compact launcher menu of the command apps the guest actually
 * confirmed — the Phase 3.2 discovery state, presented as an OS menu (no
 * dialog, no logos): monogram plate + name, launch command dim and secondary.
 * A row tap runs the exact Phase 3.2 launch pipeline (fresh verify →
 * dedicated guest session → focus).
 */
@Composable
private fun CliAppsMenu(
    apps: List<CommandApp>,
    onOpenCommandApp: (CommandApp) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "cliAppsChevron",
    )
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (expanded) "Close the CLI Apps menu" else "Open the CLI Apps menu",
                    ) { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CLI Apps",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp,
                    color = HomeTokens.textDim,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = HomeTokens.textDim,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 236.dp, max = 320.dp),
                shape = RoundedCornerShape(HomeTokens.chipRadius),
                containerColor = HomeTokens.surfaceEnv,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, HomeTokens.hairline),
            ) {
                apps.forEach { app ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = app.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HomeTokens.textPrimary,
                            )
                        },
                        onClick = { expanded = false; onOpenCommandApp(app) },
                        leadingIcon = {
                            MonogramTile(
                                monogram = app.monogram,
                                size = 28.dp,
                                radius = 9.dp,
                                fontSizeScale = 0.42f,
                            )
                        },
                        trailingIcon = {
                            Text(
                                text = app.launchCommand.joinToString(" "),
                                fontFamily = TerminalTheme.mono,
                                fontSize = 11.sp,
                                color = HomeTokens.textDim,
                            )
                        },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- tools section

/**
 * "Your tools" (§6) — command apps as launcher entries on the canvas, or the
 * lightweight honest states (§9). No container is drawn around any of this.
 */
@Composable
private fun ToolsSection(
    state: TerminalViewModel.CommandAppsState,
    verifyingApp: String?,
    runtimeReady: Boolean,
    columns: Int,
    onOpenCommandApp: (CommandApp) -> Unit,
    onExplorePackages: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        HomeSectionLabel("Your tools")
        Spacer(Modifier.height(14.dp))
        when {
            state.apps.isEmpty() && !state.probeError.isNullOrEmpty() -> {
                // Probe failed, nothing previously confirmed: the honest
                // "could not check" — never a fake "none" (v0.4.4 rule).
                Text(
                    text = "App availability could not be checked — ${state.probeError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
            state.apps.isEmpty() && runtimeReady && !state.checked -> {
                // Probe in flight: one quiet line, the launcher never flashes
                // a fake "none installed" while the guest is being asked.
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
            state.apps.isEmpty() -> {
                EmptyToolsState(runtimeReady = runtimeReady, onExplorePackages = onExplorePackages)
            }
            else -> {
                state.apps.chunked(columns).forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    ) {
                        rowApps.forEach { app ->
                            CommandAppEntry(
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
}

/**
 * One launcher entry: icon + label — an application on an OS home screen, not
 * a card (§6). The monogram plate is a borderless tone step; press feedback
 * is the soft scale, nothing draws a box.
 */
@Composable
private fun CommandAppEntry(
    app: CommandApp,
    verifying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableScale(
        onClick = onClick,
        onClickLabel = "Open ${app.displayName}",
        modifier = modifier,
        pressedScale = 0.96f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (verifying) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = HomeTokens.accent,
                    )
                }
            } else {
                MonogramTile(monogram = app.monogram, size = 52.dp)
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
}

/**
 * The empty launcher (§9): three quiet text lines directly on the canvas —
 * no container, no placeholder icons, nothing that dominates. This carries
 * the page's ONLY "Explore packages" affordance in this state.
 */
@Composable
private fun EmptyToolsState(runtimeReady: Boolean, onExplorePackages: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "No CLI apps yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = HomeTokens.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (runtimeReady) {
                "Install an interactive command application and it will appear here."
            } else {
                "Install the Linux runtime first — interactive command apps live " +
                    "inside your Linux environment and launch from here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
        )
        Text(
            text = "Explore packages",
            style = MaterialTheme.typography.labelLarge,
            color = HomeTokens.accent,
            modifier = Modifier
                .padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp)
                .clickable(role = Role.Button) { onExplorePackages() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- sessions

/**
 * Flat continuation rows (§2a) — running sessions stay visible without
 * dominating: dot + label + mono id between hairline dividers. No boxes at
 * rest; a pressed row is the only surface this section ever draws. Capped;
 * the rest remain in the Terminal's tabs.
 */
@Composable
private fun SessionsSection(
    sessions: List<TerminalSessionManager.SessionEntry>,
    onOpenSession: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        HomeSectionLabel("Sessions")
        Spacer(Modifier.height(6.dp))
        val visible = sessions.take(4)
        visible.forEachIndexed { index, entry ->
            val finished = entry.isFinished
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (pressed) HomeTokens.surfaceBanner else Color.Transparent)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = "Return to session",
                    ) { onOpenSession(entry.id) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (finished) HomeTokens.hairline else HomeTokens.runningGreen,
                            CircleShape,
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
            if (index != visible.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 30.dp),
                    color = HomeTokens.hairline.copy(alpha = 0.6f),
                )
            }
        }
        if (sessions.size > 4) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "+${sessions.size - 4} more in Terminal",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ footer

/**
 * The single quiet packages affordance for the tools-present state (§9) —
 * when the empty state is shown, ITS link is the only one on the page.
 */
@Composable
private fun PackagesFooterLink(onExplorePackages: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Packages",
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
