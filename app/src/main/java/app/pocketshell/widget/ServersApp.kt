package app.pocketshell.widget

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.probe.ServerInfo
import app.pocketshell.widget.probe.ServerProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * M8.2 — SERVERS: the first PocketShell Home Application and the REFERENCE
 * implementation of the one-card pattern (docs/M8-WIDGET-SYSTEM.md §9).
 *
 * The card IS this application's screen. The user never leaves Home:
 *
 *   overview ("N running" + verified server rows [+ cwd, + footer stats
 *             when the card is roomy])
 *     ↓ tap a row
 *   detail (endpoint, directory, PID/PROCESS, TERMINAL / COMPANION)
 *     ↓ back (the card's OWN back handler — the system back returns to
 *       the overview INSIDE the card; only from the overview does back
 *       reach the rest of Home)
 *
 * Every visual token comes from the shared PocketShell theme system
 * (HomeTokens → TerminalTheme): the card follows whatever theme the user
 * selected, and the aurora edge appears only when the selected theme
 * provides it (the shared root phase is theme-gated). No Servers-specific
 * theme exists.
 *
 * Information hierarchy adapts to the available card space
 * ([ServersLayout]) instead of scaling: COMPACT keeps one-line rows;
 * ROOMY adds the status header, per-server directory lines and the
 * footer statistics. Truthfulness is inherited from [ServerProbe]
 * unchanged (M8.1): verified endpoints only, real attribution, honest
 * empty/loading/unavailable states, no invented controls.
 */
object ServersApp : HomeApplication() {

    override val spec = HomeAppSpec(
        id = HomeApplications.SERVERS_ID,
        name = "Servers",
        summary = "Dev servers in the Linux guest — verified live endpoints",
    )

    private const val REFRESH_MS = 5_000L

    @Composable
    override fun Content(context: HomeAppContext) {
        // M8.4.2 — snapshot, detail selection AND the probe instance live
        // in the process-scoped holder: the probe's pid-set gate memory
        // survives navigation, so re-entering Home shows the cached
        // snapshot instead of re-scanning, and the detail page the user
        // had open reopens as it was.
        val state = remember {
            context.stateStore.forApp(HomeApplications.SERVERS_ID) { ServersState() }
        }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                state.ui = ServersUi.Unavailable
                return@LaunchedEffect
            }
            val probe = state.probe
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (true) {
                        if (probe.shouldFullScan(probe.peekPidSet())) {
                            val servers = withContext(Dispatchers.IO) { probe.snapshot() }
                            state.ui = when {
                                !probe.lastScanSawProcesses && !probe.tablePathAvailable ->
                                    ServersUi.ProbeUnavailable
                                else -> ServersUi.Ready(servers)
                            }
                        }
                        delay(REFRESH_MS)
                    }
                }
        }

        val servers = (state.ui as? ServersUi.Ready)?.servers.orEmpty()
        // A selection whose server vanished degrades to the overview —
        // never a stale detail page for a dead endpoint.
        val selected = state.detailPort?.let { port -> servers.firstOrNull { it.port == port } }
        BackHandler(enabled = selected != null) { state.detailPort = null }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = ServersLayout.from(maxWidth.value, maxHeight.value)
            if (selected != null) {
                ServersDetail(
                    server = selected,
                    roomy = layout == ServersLayout.ROOMY,
                    onBack = { state.detailPort = null },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenCompanion = { context.nav.openCompanion(ServerProbe.companionUrl(selected.port)) },
                )
            } else {
                ServersOverview(
                    ui = state.ui,
                    servers = servers,
                    layout = layout,
                    onOpenDetail = { state.detailPort = it.port },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                )
            }
        }
    }
}

/** The application's screen state (probe-driven, never invented). */
internal sealed interface ServersUi {
    data object Probing : ServersUi
    data object Unavailable : ServersUi
    data object ProbeUnavailable : ServersUi
    data class Ready(val servers: List<ServerInfo>) : ServersUi
}

/**
 * M8.4.2 — the Servers application's process-scoped state: the last
 * probe snapshot, the open endpoint's port (null = overview) and the
 * probe instance itself. Owned by the HomeAppStateStore.
 */
internal class ServersState {
    var ui by mutableStateOf<ServersUi>(ServersUi.Probing)
    var detailPort by mutableStateOf<Int?>(null)
    val probe = ServerProbe()
}

/**
 * The responsive contract: which information the application shows at the
 * available card size. Derived from real constraints (the card width is
 * the content width — phone ≈300-390dp, tablet up to 720dp−gutters — and
 * the height is HomeTokens.homeAppCardHeight), never by scaling.
 * Pure + JVM-tested (ServersLayoutTest).
 */
internal enum class ServersLayout(
    val showsCwd: Boolean,
    val showsStatusHeader: Boolean,
) {
    COMPACT(showsCwd = false, showsStatusHeader = false),
    ROOMY(showsCwd = true, showsStatusHeader = true);

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): ServersLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ------------------------------------------------------------- overview

@Composable
private fun ServersOverview(
    ui: ServersUi,
    servers: List<ServerInfo>,
    layout: ServersLayout,
    onOpenDetail: (ServerInfo) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Servers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (servers.isNotEmpty() && layout == ServersLayout.COMPACT) {
                Text(
                    text = "${servers.size} running",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }

        // Status area — the at-a-glance answer, roomy cards only.
        if (layout.showsStatusHeader && servers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(HomeTokens.runningGreen, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${servers.size} RUNNING",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.accent,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // Rows — verified servers only; always scrolling, never capped.
        if (servers.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                servers.forEachIndexed { index, server ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Server details :${server.port}",
                            ) { onOpenDetail(server) }
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(HomeTokens.runningGreen, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = ":${server.port}  ${server.displayName}",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (layout.showsCwd && server.cwd != null) {
                            Text(
                                text = rememberDisplayCwd(server),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
                            )
                        }
                    }
                    if (index != servers.lastIndex) {
                        HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        val stateLine = when {
            ui is ServersUi.Probing -> "Looking…"
            ui is ServersUi.Unavailable -> "Linux not ready"
            ui is ServersUi.ProbeUnavailable -> "Probes unavailable"
            servers.isEmpty() -> "Nothing listening"
            else -> "Local listeners"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (servers.isNotEmpty()) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        if (ui is ServersUi.Ready && servers.isEmpty()) {
            Text(
                text = "Start a server in a terminal — it appears here when it listens.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
            TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                Text("Open Linux", color = HomeTokens.accent)
            }
        }
        if (ui is ServersUi.Unavailable || ui is ServersUi.ProbeUnavailable) {
            TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
                Text("Diagnostics", color = HomeTokens.accent)
            }
        }
    }
}

// --------------------------------------------------------------- detail

@Composable
private fun ServersDetail(
    server: ServerInfo,
    roomy: Boolean,
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenCompanion: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // In-card back header: the ONLY back is the application's own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Back to Servers") { onBack() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = HomeTokens.accent,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Servers",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = "${server.displayName} :${server.port}",
            fontFamily = TerminalTheme.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(HomeTokens.runningGreen, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "RUNNING",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.runningGreen,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "127.0.0.1:${server.port}",
            fontFamily = TerminalTheme.mono,
            fontSize = 12.sp,
            color = HomeTokens.textPrimary,
        )
        if (server.cwd != null) {
            Text(
                text = rememberDisplayCwd(server),
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(4.dp))
        if (roomy) {
            // The process table, roomy cards only.
            ProcessRow("PID", server.pid?.toString() ?: "unknown")
            ProcessRow("PROCESS", server.processName ?: "unknown")
        } else if (server.pid != null) {
            Text(
                text = "PID ${server.pid} · ${server.processName ?: "unknown"}",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = when (server.attribution) {
                ServerInfo.Attribution.ACCEPTOR_MATCH ->
                    "verified by connecting; this process accepted the connection"
                ServerInfo.Attribution.CMDLINE_MATCH ->
                    "verified by connecting; this process names the port and owns sockets"
                ServerInfo.Attribution.TABLE_MATCH ->
                    "verified by connecting; this process owns the listening socket"
            },
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )

        Row {
            TextButton(
                onClick = onOpenTerminal,
                modifier = Modifier.height(34.dp),
            ) {
                Text("TERMINAL", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenCompanion,
                modifier = Modifier.height(34.dp),
            ) {
                Text("COMPANION", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        }
    }
}

@Composable
private fun ProcessRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            fontFamily = TerminalTheme.mono,
            fontSize = 10.sp,
            color = HomeTokens.textDim,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            fontFamily = TerminalTheme.mono,
            fontSize = 10.sp,
            color = HomeTokens.textPrimary,
        )
    }
}

// --------------------------------------------------------------- helpers

/** The cached cwd mapping, computed once per server per composition. */
@Composable
private fun rememberDisplayCwd(server: ServerInfo): String {
    val appContext = LocalContext.current.applicationContext
    return remember(server.port, server.cwd) {
        displayCwd(cwd = server.cwd ?: "", context = appContext)
    }
}

internal fun displayCwd(cwd: String, context: Context): String {
    val rootfsPath = try {
        RuntimeStorage(context.applicationContext.noBackupFilesDir).rootfsDir.path
    } catch (_: Exception) {
        null
    }
    return displayCwdPaths(
        cwd = cwd,
        dataDir = context.applicationInfo.dataDir ?: "",
        rootfsPath = rootfsPath,
    )
}

/** The pure path-mapping core (JVM-testable): host anchors → Linux paths. */
internal fun displayCwdPaths(cwd: String, dataDir: String, rootfsPath: String?): String {
    val dataDirNorm = dataDir.removeSuffix("/")
    val rootfs = rootfsPath?.removeSuffix("/")
    if (!rootfs.isNullOrEmpty() && cwd.startsWith("$rootfs/")) {
        val guestPath = "/" + cwd.removePrefix("$rootfs/")
        return "$guestPath (guest)"
    }
    if (!rootfs.isNullOrEmpty() && cwd == rootfs) return "/ (guest root)"
    val home = "$dataDirNorm/files/home"
    return when {
        cwd == home -> "~ (Linux home)"
        cwd.startsWith("$home/") -> "~/" + cwd.removePrefix("$home/")
        else -> cwd.removePrefix("$dataDirNorm/")
    }
}
