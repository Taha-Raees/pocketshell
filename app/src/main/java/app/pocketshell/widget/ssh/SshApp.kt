package app.pocketshell.widget.ssh

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
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.HomeAppSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * M8.3 — SSH: a PocketShell Home Application answering, in one card, the
 * question "what remote machines do I have and what is their current
 * connection state?" with ONLY honest evidence:
 *
 *   overview (saved hosts from ~/.ssh/config + ACTIVE ssh client
 *             processes from /proc + known_hosts counts)
 *     ↓ tap a host
 *   detail (profile facts; identity file NAMES only; whether an ssh
 *           client process targets this host right now)
 *     ↓ back — the card's OWN back handler (the reference ServersApp
 *       pattern: system back returns detail→overview INSIDE the card)
 *
 * What this card deliberately is NOT: a connection manager. The user runs
 * `ssh` in a terminal — the card never connects, never spawns, and acts
 * only through the [WidgetNav] seam. PocketShell has no SSH session API,
 * so "connection state" means exactly one thing: own-UID `ssh` processes
 * found in /proc (the M8.1 probe discipline). A client at a password
 * password prompt and a healthy session look identical there — the card
 * says 'ssh client process', never 'connected', and never invents latency
 * or health.
 *
 * Security, in one place (pinned by SshAppContractTest): identity keys
 * are shown as NAMES and never opened; known_hosts appears as counts with
 * hashed entries noted; nothing is written to ~/.ssh or persisted.
 */
object SshApp : HomeApplication() {

    const val ID = "ssh"

    override val spec = HomeAppSpec(
        id = ID,
        name = "SSH",
        summary = "Saved SSH hosts in the Linux guest's ~/.ssh/config and live ssh client processes",
    )

    private const val REFRESH_MS = 5_000L

    @Composable
    override fun Content(context: HomeAppContext) {
        // M8.4.2 — snapshot + detail selection live in the process-scoped
        // holder: leaving Home or swiping pages away no longer resets the
        // card to "Looking…" and reopens on the same host.
        val state = remember { context.stateStore.forApp(SshApp.ID) { SshState() } }
        val lifecycleOwner = LocalLifecycleOwner.current
        val appContext = LocalContext.current.applicationContext

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                state.ui = SshUi.Unavailable
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (true) {
                        state.ui = SshUi.Ready(
                            withContext(Dispatchers.IO) { SshFiles.snapshot(appContext) },
                        )
                        delay(REFRESH_MS)
                    }
                }
        }

        val snapshot = (state.ui as? SshUi.Ready)?.snapshot
        val hosts = snapshot?.hosts.orEmpty()
        val processes = snapshot?.processes.orEmpty()
        // A selection whose entry vanished degrades to the overview —
        // never a stale detail page for a removed config block.
        val selected = state.detailKey?.let { key -> hosts.firstOrNull { it.displayName == key } }
        BackHandler(enabled = selected != null) { state.detailKey = null }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = SshLayout.from(maxWidth.value, maxHeight.value)
            if (selected != null) {
                SshDetail(
                    entry = selected,
                    matched = processes.filter { sshEntryMatchesTarget(selected, it.target) },
                    roomy = layout == SshLayout.ROOMY,
                    onBack = { state.detailKey = null },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            } else {
                SshOverview(
                    ui = state.ui,
                    layout = layout,
                    onOpenDetail = { entry -> state.detailKey = entry.displayName },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                )
            }
        }
    }
}

/** The application's screen state (files + /proc, never invented). */
internal sealed interface SshUi {
    data object Probing : SshUi
    data object Unavailable : SshUi
    data class Ready(val snapshot: SshSnapshot) : SshUi
}

/**
 * M8.4.2 — the SSH application's process-scoped state: the last probe
 * snapshot and the open host's display key (null = overview). Owned by
 * the HomeAppStateStore so the card reopens where the user left it.
 */
internal class SshState {
    var ui by mutableStateOf<SshUi>(SshUi.Probing)
    var detailKey by mutableStateOf<String?>(null)
}

/**
 * The responsive contract — the same shape (and the same inner-dp
 * thresholds) as the reference ServersLayout: COMPACT hides sublines;
 * ROOMY adds sublines, the status header and the footer statistics.
 * The rows area scrolls at BOTH densities with no row cap.
 */
internal enum class SshLayout(
    val showsSublines: Boolean,
    val showsStatusHeader: Boolean,
    val showsFooter: Boolean,
) {
    COMPACT(showsSublines = false, showsStatusHeader = false, showsFooter = false),
    ROOMY(showsSublines = true, showsStatusHeader = true, showsFooter = true);

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): SshLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ------------------------------------------------------------- overview

@Composable
private fun SshOverview(
    ui: SshUi,
    layout: SshLayout,
    onOpenDetail: (SshHostEntry) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val snapshot = (ui as? SshUi.Ready)?.snapshot
    val hosts = snapshot?.hosts.orEmpty()
    val processes = snapshot?.processes.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "SSH",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (hosts.isNotEmpty()) {
                Text(
                    text = "${hosts.size} hosts",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }

        // Status area — real running clients are the card's live facts.
        if (layout.showsStatusHeader && processes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(HomeTokens.runningGreen, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = processes.size.let { if (it == 1) "1 SSH CLIENT PROCESS" else "$it SSH CLIENT PROCESSES" },
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.accent,
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // Rows — active clients first (the live state), then saved hosts.
        val total = processes.size + hosts.size
        if (total > 0) {
            val hostShown = hosts
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                processes.forEach { proc ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(HomeTokens.runningGreen, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "ssh → ${proc.targetDisplay}",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (layout.showsSublines) {
                            Text(
                                text = "pid ${proc.pid} · client process (from its argv)",
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
                            )
                        }
                    }
                }
                hostShown.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "SSH host ${entry.displayName}",
                            ) { onOpenDetail(entry) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = entry.displayName,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (layout.showsSublines) {
                            Text(
                                text = entrySubtitle(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    if (index != hostShown.lastIndex) {
                        HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Footer — only what no other line already says: the known-hosts
        // count and the parser's honesty note. Host and process counts live
        // in the header/status header alone.
        if (layout.showsFooter && total > 0) {
            val parts = mutableListOf<String>()
            snapshot?.knownHosts?.let {
                parts += it.entries.let { n -> "$n KNOWN-HOST ${if (n == 1) "ENTRY" else "ENTRIES"}" }
            }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    fontFamily = TerminalTheme.mono,
                    fontSize = 10.sp,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (snapshot != null && (snapshot.includesIgnored > 0 || snapshot.matchBlocksIgnored > 0)) {
                Text(
                    text = "Include/Match directives are not followed",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        val stateLine = when {
            ui is SshUi.Probing -> "Looking…"
            ui is SshUi.Unavailable -> "Linux not ready"
            processes.isNotEmpty() -> "ssh client processes running"
            hosts.isNotEmpty() -> "Saved hosts from ~/.ssh/config"
            snapshot?.configFound == true -> "No hosts in ~/.ssh/config"
            else -> "No ssh configuration found"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (processes.isNotEmpty()) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        if (ui is SshUi.Ready && total == 0) {
            Text(
                text = "Run ssh in a Linux terminal — running clients appear here; hosts come from ~/.ssh/config.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
            TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                Text("Open Linux", color = HomeTokens.accent)
            }
        }
        if (ui is SshUi.Unavailable) {
            TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
                Text("Diagnostics", color = HomeTokens.accent)
            }
        }
    }
}

// --------------------------------------------------------------- detail

@Composable
private fun SshDetail(
    entry: SshHostEntry,
    matched: List<SshClientProcess>,
    roomy: Boolean,
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
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
                .clickable(role = Role.Button, onClickLabel = "Back to SSH") { onBack() },
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
                text = "SSH",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = entry.displayName,
            fontFamily = TerminalTheme.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = entrySubtitle(entry),
            fontFamily = TerminalTheme.mono,
            fontSize = 12.sp,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))
        when {
            matched.isNotEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(HomeTokens.runningGreen, CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ACTIVE",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 10.sp,
                        color = HomeTokens.runningGreen,
                    )
                }
                val pids = matched.take(3).joinToString(", ") { "pid ${it.pid}" } +
                    if (matched.size > 3) " …" else ""
                Text(
                    text = (if (matched.size == 1) "1 ssh process" else "${matched.size} ssh processes") +
                        " ($pids) name this host — shown from the process argv",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            entry.wildcard -> {
                Text(
                    text = "Pattern block — patterns are not matched against running clients",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
            else -> {
                Text(
                    text = "No ssh client process targets this host now",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        // Profile facts — the config's own words, resolved with defaults.
        FactRow("HOSTNAME", entry.hostName ?: "(unset)")
        FactRow("USER", entry.user ?: "(unset)")
        FactRow("PORT", "${entry.port ?: 22}" + if (entry.port == null) " (ssh default)" else "")
        if (entry.identityFiles.isEmpty()) {
            FactRow("IDENTITY", "(none in config)")
        } else {
            entry.identityFiles.forEach { FactRow("IDENTITY", it.substringAfterLast('/')) }
        }
        if (roomy) {
            FactRow("PATTERNS", entry.patterns.joinToString(" "))
        }
        Text(
            text = "Identity names only — key files are never read.",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
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
                onClick = onOpenLinuxShell,
                modifier = Modifier.height(34.dp),
            ) {
                Text("OPEN LINUX", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
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

/**
 * The resolved one-line profile: user@host · port · key name. Every part
 * is stated only when the config (or ssh's documented default of 22)
 * actually provides it; pattern blocks say what they are.
 */
internal fun entrySubtitle(entry: SshHostEntry): String {
    if (entry.wildcard) return "pattern block"
    val parts = mutableListOf<String>()
    val who = listOfNotNull(entry.user, entry.hostName).joinToString("@")
    if (who.isNotEmpty()) parts += who
    parts += "port ${entry.port ?: 22}"
    if (entry.identityFiles.isNotEmpty()) {
        val names = entry.identityFiles.joinToString(", ") { it.substringAfterLast('/') }
        parts += "key $names"
    }
    return parts.joinToString(" · ")
}
