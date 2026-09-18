package app.pocketshell.widget

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
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
 * M8.1 — the Servers widget: REAL dev servers running in the Linux guest,
 * discovered and verified without any kernel table access (all of
 * /proc/net, per-pid net included, is SELinux-denied — device-proven).
 *
 * The evidence chain is kernel-fact-grounded at every step:
 *   own-UID cmdline names a port ∪ bounded dev-port canon
 *   → TCP connect to 127.0.0.1:P succeeds (app and guest share the
 *     loopback — proot creates no network namespace)
 *   → the accepting process is identified by the held-connection fd-diff
 *     (or, fallback, exactly one socket-owning pid names the port).
 * Nothing is shown without a verified endpoint; foreign (other-UID) local
 * services are never claimed. See widget/probe/ServerProbe.kt.
 *
 * Refresh: full pipeline only when the pid set changed or servers were
 * present last tick; otherwise the tick is a single /proc readdir.
 * Tap on the card → Terminal; tap a server row → details with the two
 * supported actions (Terminal, Companion). No Stop/Restart: PocketShell
 * does not own these processes, and no fake control pretends otherwise.
 */
object ServersWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = "servers",
        name = "Servers",
        summary = "Dev servers in the Linux guest — verified live endpoints",
        isCore = false,
    )

    override fun tapLabel(context: WidgetContentContext): String = "Open the Terminal"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = { context.nav.openTerminal() }

    private const val REFRESH_MS = 5_000L
    private const val MAX_ROWS = 3

    private sealed interface Ui {
        data object Probing : Ui
        data object Unavailable : Ui
        data object ProbeUnavailable : Ui
        data class Ready(val servers: List<ServerInfo>) : Ui
    }

    @Composable
    override fun Content(context: WidgetContentContext) {
        var ui by remember { mutableStateOf<Ui>(Ui.Probing) }
        var detailFor by remember { mutableStateOf<ServerInfo?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                ui = Ui.Unavailable
                return@LaunchedEffect
            }
            val probe = ServerProbe()
            // Lifecycle-aware tick with the idle gate: while the guest's
            // pid set is unchanged and nothing was listening, a tick costs
            // one /proc readdir.
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (true) {
                        if (probe.shouldFullScan(probe.peekPidSet())) {
                            val servers = withContext(Dispatchers.IO) { probe.snapshot() }
                            ui = when {
                                !probe.lastScanSawProcesses && !probe.tablePathAvailable ->
                                    Ui.ProbeUnavailable
                                else -> Ui.Ready(servers)
                            }
                        }
                        delay(REFRESH_MS)
                    }
                }
        }

        val palette = WidgetPalette.of(spec.tone)
        val servers = (ui as? Ui.Ready)?.servers.orEmpty()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ServersMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (servers.isNotEmpty()) {
                    Text(
                        text = "${servers.size} running",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = palette.dim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            servers.take(MAX_ROWS).forEach { server ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Server details :${server.port}",
                        ) { detailFor = server },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // runningGreen: a real verified endpoint, same contract
                    // as every running indicator on Home.
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(HomeTokens.runningGreen, CircleShape),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = ":${server.port}  ${server.displayName}",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = palette.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (servers.size > MAX_ROWS) {
                Text(
                    text = "+${servers.size - MAX_ROWS} more",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.dim,
                    maxLines = 1,
                )
            }
            if (servers.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                text = "Servers",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Spacer(Modifier.height(2.dp))
            val stateLine = when {
                ui is Ui.Probing -> "Looking…"
                ui is Ui.Unavailable -> "Linux not ready"
                ui is Ui.ProbeUnavailable -> "Probes unavailable"
                servers.isEmpty() -> "Nothing listening"
                else -> "Local listeners"
            }
            val stateColor = if (servers.isNotEmpty()) palette.accent else palette.dim
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor,
                maxLines = 1,
            )
        }

        detailFor?.let { server ->
            ServerDetailDialog(
                server = server,
                onDismiss = { detailFor = null },
                onOpenTerminal = {
                    detailFor = null
                    context.nav.openTerminal()
                },
                onOpenCompanion = {
                    detailFor = null
                    context.nav.openCompanion(ServerProbe.companionUrl(server.port))
                },
            )
        }
    }
}

/**
 * The one detail surface: REAL facts only (verified endpoint, attributed
 * pid, the project directory when resolvable) and the two actions
 * PocketShell actually supports. Stop/Restart are deliberately absent —
 * the app does not own these processes.
 */
@Composable
private fun ServerDetailDialog(
    server: ServerInfo,
    onDismiss: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenCompanion: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${server.displayName} :${server.port}",
                fontFamily = TerminalTheme.mono,
            )
        },
        text = {
            Column {
                Text(
                    text = "Running — verified by connecting to " +
                        "127.0.0.1:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textPrimary,
                )
                server.pid?.let { pid ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "PID $pid " + when (server.attribution) {
                            ServerInfo.Attribution.ACCEPTOR_MATCH ->
                                "(accepted a test connection)"
                            ServerInfo.Attribution.CMDLINE_MATCH ->
                                "(names this port, owns sockets)"
                            ServerInfo.Attribution.TABLE_MATCH ->
                                "(owns the listening socket)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
                server.cwd?.let { cwd ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Directory ${displayCwd(cwd, appContext)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Companion opens http://127.0.0.1:${server.port}/ — " +
                        "use it for HTTP servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenTerminal) { Text("Open in Terminal") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOpenCompanion) { Text("Companion") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/**
 * Host-side cwd → human-readable location: the app-data prefix carries no
 * meaning for a Linux developer; the known anchors map to Linux paths.
 * The guest home is the bound `files/home` (guest ~); rootfs paths map to
 * guest-absolute paths.
 */
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
