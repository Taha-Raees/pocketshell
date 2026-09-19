package app.pocketshell.widget.git

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.pocketshell.packages.ProcessBuilderGuestCommandRunner
import app.pocketshell.runtime.GuestExecutionProfile
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.HomeAppSpec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * M8.3 — GIT: the second PocketShell Home Application (ServersApp is the
 * reference; this file copies its discipline). The card IS the screen:
 *
 *   overview ("N repos" + repository rows: name, branch, dirty badge)
 *     ↓ tap a row
 *   detail (branch/upstream/diverge table, porcelain rows, TERMINAL / LINUX)
 *     ↓ back (the card's OWN back handler — only from the overview does
 *       back reach the rest of Home)
 *
 * The one question the card answers: "what is happening in my repositories
 * right now?" — answered with REAL guest data only. One batched read-only
 * guest exec per refresh ([GitProbe]) reports the git binary, repositories
 * under the guest home and each repo's `status --porcelain=v1 -b`. There
 * are deliberately NO staging/commit/push/checkout controls: the app does
 * not own the repositories, a terminal is where git work happens, and the
 * only actions offered are the existing navigation seams (open the
 * terminal, enter the Linux guest).
 *
 * Honest degradation everywhere: runtime not READY → "Linux not ready";
 * git absent in the guest → "Git unavailable" + how to get it; no repos
 * → "No repositories" + where they would appear; a failed exec → the real
 * reason, never "no repositories"; one unreadable repo degrades alone.
 * Refresh cost is idle-gated ([GitProbe.shouldFullScan]) — an open, idle
 * card execs at most once per AUTO_RESCAN_MS, plus a manual REFRESH.
 */
object GitApp : HomeApplication() {

    /** The registry id (HomeApplications registers this application). */
    const val GIT_ID = "git"

    override val spec = HomeAppSpec(
        id = GIT_ID,
        name = "Git",
        summary = "Repositories in the Linux guest — branch, dirty state, ahead/behind",
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        val appContext = LocalContext.current.applicationContext
        val probe = remember { GitProbe(guestExec(appContext)) }
        var ui by remember { mutableStateOf<GitUi>(GitUi.Probing) }
        // The application's own navigation state: the path of the repo whose
        // detail page fills the card (null = overview). Saveable → rotation
        // and Home↔Settings round-trips restore the page.
        var detailPath by rememberSaveable { mutableStateOf<String?>(null) }
        // Manual refresh: an immediate scan; the probe self-throttles.
        var refreshTick by remember { mutableStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                ui = GitUi.Unavailable
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    // Returning to Home refreshes immediately.
                    ui = scanToUi(withContext(Dispatchers.IO) { probe.snapshot() })
                    while (true) {
                        delay(GitProbe.TICK_MS)
                        // The idle gate: a tick that fires too soon after the
                        // last scan does NOTHING — no guest exec while idle.
                        if (!probe.shouldFullScan(System.currentTimeMillis())) continue
                        ui = scanToUi(withContext(Dispatchers.IO) { probe.snapshot() })
                    }
                }
        }

        LaunchedEffect(refreshTick) {
            if (refreshTick == 0 || context.runtimeState != RuntimeState.READY) return@LaunchedEffect
            ui = scanToUi(withContext(Dispatchers.IO) { probe.snapshot() })
        }

        val repos = (ui as? GitUi.Ready)?.snapshot?.repos.orEmpty()
        // A selection whose repo vanished degrades to the overview — never
        // a stale detail page for a deleted directory.
        val selected = detailPath?.let { path -> repos.firstOrNull { it.path == path } }
        BackHandler(enabled = selected != null) { detailPath = null }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = GitLayout.from(maxWidth.value, maxHeight.value)
            if (selected != null) {
                GitDetail(
                    repo = selected,
                    roomy = layout == GitLayout.ROOMY,
                    onBack = { detailPath = null },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            } else {
                GitOverview(
                    ui = ui,
                    repos = repos,
                    layout = layout,
                    onRefresh = { refreshTick++ },
                    onOpenDetail = { detailPath = it.path },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                )
            }
        }
    }

    /**
     * The real exec: the sanctioned non-PTY guest path — the SAME
     * buildLaunchSpec + background-runner pairing AlpinePackageManager.runApk
     * uses (single exec infrastructure, no duplicate proot logic). The
     * minimal PACKAGE_OPERATION profile is the right mount configuration for
     * an offline read-only probe, and it is the device-proven safe one. No
     * DNS/workspace repair: this probe is read-only and never mutates the
     * rootfs from Home.
     */
    private fun guestExec(appContext: Context): GitProbe.GuestExec = GitProbe.GuestExec { argv ->
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        val spec = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = ShellEnvironment.homeDir(appContext),
            prootTmpDir = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() },
            guestCommand = argv,
            profile = GuestExecutionProfile.PACKAGE_OPERATION,
        )
        val process = ProcessBuilderGuestCommandRunner().start(spec)
        try {
            process.waitFor(GitProbe.SCAN_TIMEOUT_MS)
        } catch (t: Throwable) {
            process.destroy()
            throw t
        }
    }
}

/** The application's screen state (probe-driven, never invented). */
internal sealed interface GitUi {
    data object Probing : GitUi
    data object Unavailable : GitUi
    data class ProbeFailed(val reason: String) : GitUi
    data class Ready(val snapshot: GitSnapshot) : GitUi
}

internal fun scanToUi(result: ScanResult): GitUi = when (result) {
    is ScanResult.Done -> GitUi.Ready(result.snapshot)
    is ScanResult.Failed -> GitUi.ProbeFailed(result.reason)
}

/**
 * The responsive contract — same geometry as ServersLayout (the card
 * dimensions are identical, so the device-derived thresholds carry over):
 * COMPACT keeps one-line rows; ROOMY adds the status header, per-repo path
 * sublines, the footer statistics and scrolling rows. Pure + JVM-tested.
 */
internal enum class GitLayout(
    val showsPath: Boolean,
    val showsStatusHeader: Boolean,
) {
    COMPACT(showsPath = false, showsStatusHeader = false),
    ROOMY(showsPath = true, showsStatusHeader = true);

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f
        const val DETAIL_MAX_ENTRIES_COMPACT = 8
        const val DETAIL_MAX_ENTRIES_ROOMY = 24

        fun from(widthDp: Float, heightDp: Float): GitLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ------------------------------------------------------------- overview

@Composable
private fun GitOverview(
    ui: GitUi,
    repos: List<RepoSnapshot>,
    layout: GitLayout,
    onRefresh: () -> Unit,
    onOpenDetail: (RepoSnapshot) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val ready = ui as? GitUi.Ready
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Git",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (repos.isNotEmpty() && layout == GitLayout.COMPACT) {
                Text(
                    text = "${repos.size} repos",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }

        // Status area — the at-a-glance answer, roomy cards only. No green
        // dot: repositories are not running processes (that token is
        // reserved), the dirty count is the real state.
        if (layout.showsStatusHeader && repos.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${repos.size} REPOS · ${ready?.snapshot?.dirtyRepos ?: 0} DIRTY",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.accent,
            )
        }
        Spacer(Modifier.height(6.dp))

        // Rows — discovered repositories only; always scrolling, never capped.
        if (repos.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                repos.forEachIndexed { index, repo ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Git details ${repo.name}",
                            ) { onOpenDetail(repo) }
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = repoBadge(repo),
                                fontFamily = TerminalTheme.mono,
                                fontSize = 11.sp,
                                color = if (repo.status?.dirty == true) HomeTokens.accent else HomeTokens.textDim,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = repoLine(repo),
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (layout.showsPath) {
                            Text(
                                text = repoSubline(repo),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 26.dp, top = 1.dp),
                            )
                        }
                    }
                    if (index != repos.lastIndex) {
                        HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Footer statistics — roomy cards only.
                Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        val stateLine = when {
            ui is GitUi.Probing -> "Looking…"
            ui is GitUi.Unavailable -> "Linux not ready"
            ui is GitUi.ProbeFailed -> "Could not probe git"
            ready != null && !ready.snapshot.hasGit -> "Git unavailable"
            ready != null && repos.isEmpty() -> "No repositories"
            else -> "Live from the guest"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (repos.isNotEmpty()) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        when {
            ui is GitUi.Unavailable -> {
                TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Diagnostics", color = HomeTokens.accent)
                }
            }
            ui is GitUi.ProbeFailed -> {
                Text(
                    text = ui.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 2.dp)) {
                    Text("REFRESH", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
                }
            }
            ready != null && !ready.snapshot.hasGit -> {
                Text(
                    text = "Git is not installed in the guest — install it from the Linux Shell (apk add git).",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Open Linux", color = HomeTokens.accent)
                }
            }
            ready != null && repos.isEmpty() -> {
                Text(
                    text = "Clone or create a repository under ~/ or ~/Projects — it appears here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Open Linux", color = HomeTokens.accent)
                }
            }
            ready != null -> {
                // The refresh cadence is deliberately slow (idle-gated); a
                // manual refresh makes "right now" honest and immediate.
                TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 2.dp)) {
                    Text("REFRESH", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
                }
            }
        }
    }
}

// --------------------------------------------------------------- detail

@Composable
private fun GitDetail(
    repo: RepoSnapshot,
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
                .clickable(role = Role.Button, onClickLabel = "Back to Git") { onBack() },
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
                text = "Git",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = repo.name,
            fontFamily = TerminalTheme.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = displayGuestRepoPath(repo.path),
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))
        if (repo.error != null) {
            // One unreadable repository degrades alone — stated, never hidden.
            Text(
                text = "git could not read this repository (${repo.error})",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.danger,
            )
        }
        val status = repo.status
        GitRow("BRANCH", branchText(status))
        GitRow("UPSTREAM", upstreamText(status))
        if (status != null) {
            GitRow("STATUS", status.summary())
        }

        // The porcelain rows — the real `git status` facts, XY and all.
        if (status != null && status.entries.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val cap = if (roomy) GitLayout.DETAIL_MAX_ENTRIES_ROOMY else GitLayout.DETAIL_MAX_ENTRIES_COMPACT
            val shown = status.entries.take(cap)
            shown.forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = "${entry.x}${entry.y}",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 10.sp,
                        color = HomeTokens.textDim,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = if (entry.origPath != null) "${entry.origPath} -> ${entry.path}" else entry.path,
                        fontFamily = TerminalTheme.mono,
                        fontSize = 10.sp,
                        color = HomeTokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (status.entries.size > shown.size) {
                Text(
                    text = "+${status.entries.size - shown.size} more",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 10.sp,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
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
                Text("LINUX", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        }
    }
}

@Composable
private fun GitRow(label: String, value: String) {
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// -------------------------------------------------------------- helpers

/**
 * The row's state badge. Glyphs stay Latin-1-safe; "±N" = N pending
 * changes, "·" = clean, "!" = unreadable. Nothing here implies a running
 * process — those semantics belong to ServersApp.
 */
private fun repoBadge(repo: RepoSnapshot): String = when {
    repo.error != null -> "!"
    repo.status?.dirty == true -> "±${repo.status.totalChanges}"
    else -> "·"
}

/** "name  branch ↑a↓b" — the one-line answer for the row. */
private fun repoLine(repo: RepoSnapshot): String {
    val status = repo.status
    val branch = when {
        repo.error != null -> "unreadable"
        status == null -> ""
        status.noCommits -> "${status.branch ?: "?"} (new)"
        status.detached -> "detached"
        else -> status.branch ?: "?"
    }
    val diverge = status?.let {
        if (it.ahead != null || it.behind != null) " ↑${it.ahead ?: 0}↓${it.behind ?: 0}" else ""
    } ?: ""
    return "${repo.name}  $branch$diverge".trimEnd()
}

/** Roomy subline: the mapped path plus the honest dirty summary. */
private fun repoSubline(repo: RepoSnapshot): String {
    val summary = when {
        repo.error != null -> repo.error!!
        else -> repo.status?.summary() ?: ""
    }
    return "${displayGuestRepoPath(repo.path)} · $summary"
}

private fun branchText(status: GitStatusParser.RepoStatus?): String = when {
    status == null -> "unknown"
    status.noCommits -> "${status.branch ?: "main"} (no commits yet)"
    status.detached -> "detached"
    else -> status.branch ?: "unknown"
}

private fun upstreamText(status: GitStatusParser.RepoStatus?): String {
    if (status == null) return "unknown"
    val upstream = status.upstream ?: return "none set"
    val gone = if (status.upstreamGone) " (gone)" else ""
    val diverge = when {
        status.ahead == null && status.behind == null -> ""
        else -> " · ↑${status.ahead ?: 0} ↓${status.behind ?: 0}"
    }
    return "$upstream$gone$diverge"
}
