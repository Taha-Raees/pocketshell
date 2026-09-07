package app.pocketshell.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.pocketshell.apps.CommandAppCatalog
import app.pocketshell.companion.CompanionDef
import app.pocketshell.launchers.CustomTool
import app.pocketshell.launchers.LauncherBadges
import app.pocketshell.launchers.LauncherTileIcon
import app.pocketshell.launchers.ToolLauncher
import app.pocketshell.launchers.visibleCompanions
import app.pocketshell.launchers.visibleTools
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
 *   FILES       the one quiet Files entry (M7 Phase 3)
 *   COMPANIONS  the companion websites as launcher entries (M7.1 P1) —
 *               taps raise the EXISTING companion sheet
 *   TOOLS       "Your tools" — the built-in CLI launchers + the user's
 *               custom tools as icon + label launcher entries (M7.1 P1);
 *               a launcher is NOT an install claim — availability is
 *               verified honestly at tap time (§6/§9)
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
    onOpenCustomTool: (CustomTool) -> Unit,
    onExplorePackages: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    companions: List<CompanionDef>,
    companionIcons: Map<String, String>,
    customTools: List<CustomTool>,
    toolIcons: Map<String, String>,
    hiddenLauncherIds: Set<String>,
    onOpenCompanion: (String) -> Unit,
    onRemoveFromHome: (String) -> Unit,
    onOpenLauncherSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // M7.1 P1 — the Home launcher model. The old Home-visible guest probe
    // pre-pass is retired: launcher tiles are NOT install claims (a launcher
    // is not an app), so availability is verified honestly at TAP time by
    // the existing verify-then-launch path (guest probe → spawn → honest
    // refusal banner). The spinner below still runs during that verification.
    val verifyingApp by terminalViewModel.verifyingApp.collectAsStateWithLifecycle()

    // Long-press "Remove from Home" confirmation target (id to label).
    var removeTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Phase 5 §2 — the floating create control is GONE. Session creation
    // lives where the sessions live: the Terminal screen's own "+" and its
    // empty state. The two environment tiles below remain the single, clear
    // way INTO each environment; no floating button replaces the FAB.

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
                Spacer(Modifier.height(8.dp))
                BrandHeader(onOpenDiagnostics, onOpenSettings)
                Spacer(Modifier.height(16.dp))

                if (launchError != null) {
                    LaunchErrorBanner(
                        message = launchError,
                        onDismiss = onDismissLaunchError,
                        onOpenDiagnostics = { onDismissLaunchError(); onOpenDiagnostics() },
                    )
                    Spacer(Modifier.height(14.dp))
                }

                EnvironmentLaunchers(
                    runtimeState = runtimeState,
                    runningSessions = activeSessions.count { !it.isFinished },
                    onOpenTerminal = onOpenTerminal,
                    onOpenLinuxShell = onOpenLinuxShell,
                    onOpenDiagnostics = onOpenDiagnostics,
                )

                // M7 Phase 3 — the ONE Files entry point: a quiet launcher
                // surface under the environments, before the tools grid.
                // Home stays uncluttered (one row, no badges, no counters).
                Spacer(Modifier.height(12.dp))
                FilesLauncherRow(onOpenFiles = onOpenFiles)

                // M7.1 P1 — Companions: the preinstalled (seeded) + custom
                // companion websites as launcher entries. A tap raises the
                // EXISTING companion sheet through the existing tab
                // machinery — no new web stack, no companion-specific code.
                val homeCompanions = remember(companions, hiddenLauncherIds) {
                    visibleCompanions(companions, hiddenLauncherIds)
                }
                if (homeCompanions.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    CompanionsSection(
                        companions = homeCompanions,
                        iconFiles = companionIcons,
                        columns = columns,
                        onOpen = onOpenCompanion,
                        onLongPress = { id, label -> removeTarget = id to label },
                        onManage = onOpenLauncherSettings,
                    )
                }

                // Phase 5 §3 — the "CLI Apps ▾" dropdown is retired: it listed
                // exactly the apps the "Your tools" grid below already shows,
                // with the same launch pipeline. ONE clear path remains — the
                // tools grid (and Packages for installing more).
                Spacer(Modifier.height(14.dp))
                SectionDivider()
                Spacer(Modifier.height(12.dp))

                ToolsSection(
                    tools = visibleTools(
                        CommandAppCatalog.registry, customTools, hiddenLauncherIds,
                    ),
                    iconFiles = toolIcons,
                    columns = columns,
                    verifyingApp = verifyingApp,
                    onOpenCommandApp = onOpenCommandApp,
                    onOpenCustomTool = onOpenCustomTool,
                    onLongPress = { id, label -> removeTarget = id to label },
                    onManage = onOpenLauncherSettings,
                )

                if (activeSessions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SectionDivider()
                    Spacer(Modifier.height(8.dp))
                    SessionsSection(activeSessions, onOpenSession)
                }

                // Exactly ONE packages affordance (§9) — now unconditional:
                // the launcher grid is permanent, so the empty state that
                // used to carry this link no longer exists.
                Spacer(Modifier.height(14.dp))
                PackagesFooterLink(onExplorePackages)

                // bottom clearance (the Companion bar zone rides here)
                Spacer(Modifier.height(24.dp))
            }
        }

        // M7.1 P1 — remove-from-Home confirmation (PART C: hide-only, never
        // uninstall/delete; restorable from Settings → Home launchers).
        removeTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { removeTarget = null },
                title = { Text("Remove ${target.second} from Home?") },
                text = {
                    Text(
                        "The launcher stays configured and restorable in " +
                            "Settings → Home launchers. Nothing is uninstalled " +
                            "or deleted.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onRemoveFromHome(target.first)
                        removeTarget = null
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
                },
            )
        }
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
                        color = HomeTokens.onHeroDim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Terminal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.onHero,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Native shell",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.onHeroDim,
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

/**
 * M7 Phase 3 — the Files launcher: one compact full-width surface in the
 * environment-launcher family (icon + name + one honest subtitle). It opens
 * the explorer at PocketShell Linux /root; the Android Downloads shelf is a
 * switch inside Files. Exactly ONE entry point on Home — no clutter.
 */
@Composable
private fun FilesLauncherRow(onOpenFiles: () -> Unit) {
    PressableScale(onClick = onOpenFiles, onClickLabel = "Open Files", modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(HomeTokens.heroRadius))
                .background(HomeTokens.surfaceEnv)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HomeTokens.surfaceApp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = HomeTokens.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTokens.textPrimary,
                )
                Text(
                    text = "Linux files · Downloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                )
            }
        }
    }
}

// --------------------------------------------------------------- tools section

/**
 * "Your tools" (M7.1 P1) — the built-in CLI launchers + the user's custom
 * tools as launcher entries on the canvas. A launcher is NOT an install
 * claim: every tile is visible by default and honesty lives at tap time
 * (the existing verify-then-launch path). No container is drawn around any
 * of this.
 */
@Composable
private fun ToolsSection(
    tools: List<ToolLauncher>,
    iconFiles: Map<String, String>,
    columns: Int,
    verifyingApp: String?,
    onOpenCommandApp: (CommandApp) -> Unit,
    onOpenCustomTool: (CustomTool) -> Unit,
    onLongPress: (id: String, label: String) -> Unit,
    onManage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionHeaderWithAction("Your tools", "Manage", onManage)
        Spacer(Modifier.height(10.dp))
        if (tools.isEmpty()) {
            // Every launcher hidden by the user — one quiet honest line.
            Text(
                text = "All launchers are hidden — restore them from Manage.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
            return@Column
        }
        // Deterministic text badges over the VISIBLE launchers (collision
        // rule: shortest meaningful prefix, greedy in display order).
        val badges = remember(tools) { LauncherBadges.assign(tools.map { it.label }) }
        tools.chunked(columns).forEachIndexed { rowIndex, rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                rowTools.forEachIndexed { colIndex, tool ->
                    LauncherGridEntry(
                        launcherId = tool.id,
                        label = tool.label,
                        badge = badges[rowIndex * columns + colIndex],
                        iconFile = iconFiles[tool.id],
                        verifying = verifyingApp == tool.label,
                        onClick = {
                            when (tool) {
                                is ToolLauncher.Builtin -> onOpenCommandApp(tool.app)
                                is ToolLauncher.Custom -> onOpenCustomTool(tool.tool)
                            }
                        },
                        onLongClick = { onLongPress(tool.id, tool.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowTools.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ----------------------------------------------------------- companions section

/**
 * M7.1 P1 — Companions on Home. The preinstalled (seeded) + custom
 * companion websites as launcher entries. A tap opens the companion
 * through the EXISTING tab/sheet machinery; long-press offers the same
 * hide-only remove-from-Home as the tools.
 */
@Composable
private fun CompanionsSection(
    companions: List<CompanionDef>,
    iconFiles: Map<String, String>,
    columns: Int,
    onOpen: (String) -> Unit,
    onLongPress: (id: String, label: String) -> Unit,
    onManage: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        SectionHeaderWithAction("Companions", "Manage", onManage)
        Spacer(Modifier.height(10.dp))
        val badges = remember(companions) { LauncherBadges.assign(companions.map { it.name }) }
        companions.chunked(columns).forEachIndexed { rowIndex, rowDefs ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                rowDefs.forEachIndexed { colIndex, def ->
                    LauncherGridEntry(
                        launcherId = def.id,
                        label = def.name,
                        badge = badges[rowIndex * columns + colIndex],
                        iconFile = iconFiles[def.id],
                        onClick = { onOpen(def.id) },
                        onLongClick = { onLongPress(def.id, def.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowDefs.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Section label with one quiet trailing action (the workspace's wayfinding). */
@Composable
private fun SectionHeaderWithAction(
    label: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeSectionLabel(label)
        Spacer(Modifier.weight(1f))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = HomeTokens.accent,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button) { onAction() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * One launcher entry: icon (the P2 curated bundled icon, the user's
 * imported copy, or the deterministic text badge) + label — an application
 * on an OS home screen, not a card (§6). The tile is a borderless tone
 * step; press feedback is the soft scale, nothing draws a box. Long-press
 * = remove-from-Home (hide-only).
 */
@Composable
private fun LauncherGridEntry(
    launcherId: String,
    label: String,
    badge: String,
    iconFile: String?,
    verifying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableScale(
        onClick = onClick,
        onClickLabel = "Open $label",
        onLongPress = onLongClick,
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
                LauncherTileIcon(launcherId = launcherId, iconFile = iconFile, badge = badge, size = 52.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = HomeTokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// The launcher tile composable lives in the launchers domain
// (app.pocketshell.launchers.LauncherTileIcon) so the management screen
// shares the ONE icon/badge path.

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PressableScale(
    onClick: () -> Unit,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.98f,
    onLongPress: (() -> Unit)? = null,
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
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongPress != null) "Launcher options" else null,
                onClick = { onClick() },
                onLongClick = onLongPress,
            ),
    ) {
        content()
    }
}
