package app.pocketshell.widget.git

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
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
 * reference; this file copies its discipline). The card IS the screen,
 * with the information hierarchy of the mature terminal TUIs (lazygit,
 * tig) borrowed, not their UI:
 *
 *   overview (repo chips when several — selection swaps the pane IN
 *     PLACE; the selected repo's pane: branch + tracking glyphs + worktree
 *     marker, then the changed files grouped STAGED / UNSTAGED)
 *     ↓ tap the pane
 *   detail (branch/upstream/diverge table, the full porcelain rows,
 *     TERMINAL / LINUX / REFRESH as compact TEXT buttons)
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
 * terminal, enter the Linux guest) plus refresh.
 *
 * Honest degradation everywhere: runtime not READY → "Linux not ready";
 * git absent in the guest → "Git unavailable" + how to get it; no repos
 * → "No repositories" + where they would appear; a failed exec → the real
 * reason, never "no repositories"; one unreadable repo degrades alone.
 * M8.4.2: the probe instance, the last scan's ui, the detail page and the
 * selected repo live in the process-scoped holder ([GitState] via
 * stateStore.forApp) — returning to the card renders the cached snapshot
 * instantly, and the probe's idle gate stays the only periodic scan path.
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
        // M8.4.2 — the ONE holder: the probe (its idle gate IS the cache),
        // the last scan's ui, the open detail page and the selected repo.
        // Leaving Home, swiping the page away, or rotating never resets
        // them; the exec closure is built once, on first need.
        val state = remember {
            context.stateStore.forApp(GIT_ID) { GitState(GitProbe(guestExec(appContext))) }
        }
        val lifecycleOwner = LocalLifecycleOwner.current

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                state.ui = GitUi.Unavailable
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    // Re-entry execs NOTHING on top of a usable cache: the
                    // SAME staleness policy as the tick loop decides. Only
                    // a cache-less ui (Probing, a failed probe — hasCache
                    // false) or a cache older than the idle gate re-scans;
                    // swiping away and back re-renders, never re-probes.
                    val hasCache = state.ui is GitUi.Ready
                    if (!hasCache || state.probe.shouldFullScan(System.currentTimeMillis())) {
                        state.ui = scanToUi(withContext(Dispatchers.IO) { state.probe.snapshot() })
                    }
                    while (true) {
                        delay(GitProbe.TICK_MS)
                        // The idle gate: a tick that fires too soon after the
                        // last scan does NOTHING — no guest exec while idle.
                        if (!state.probe.shouldFullScan(System.currentTimeMillis())) continue
                        state.ui = scanToUi(withContext(Dispatchers.IO) { state.probe.snapshot() })
                    }
                }
        }

        LaunchedEffect(state.refreshTick) {
            if (state.refreshTick == 0 || context.runtimeState != RuntimeState.READY) return@LaunchedEffect
            state.ui = scanToUi(withContext(Dispatchers.IO) { state.probe.snapshot() })
        }

        val repos = (state.ui as? GitUi.Ready)?.snapshot?.repos.orEmpty()
        // The pane's repository: the chip selection, defaulting to — and
        // degrading to — the first repo; a vanished selection never leaves
        // a stale pane.
        val paneRepo = repos.firstOrNull { it.path == state.selectedPath } ?: repos.firstOrNull()
        // A detail selection whose repo vanished degrades to the overview —
        // never a stale detail page for a deleted directory.
        val selected = state.detailPath?.let { path -> repos.firstOrNull { it.path == path } }
        BackHandler(enabled = selected != null) { state.detailPath = null }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = GitLayout.from(maxWidth.value, maxHeight.value)
            if (selected != null) {
                GitDetail(
                    repo = selected,
                    roomy = layout == GitLayout.ROOMY,
                    onBack = { state.detailPath = null },
                    onRefresh = { state.refreshTick++ },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            } else {
                GitOverview(
                    ui = state.ui,
                    repos = repos,
                    paneRepo = paneRepo,
                    layout = layout,
                    onRefresh = { state.refreshTick++ },
                    onSelectRepo = { state.selectedPath = it.path },
                    onOpenDetail = { state.detailPath = it.path },
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

/**
 * M8.4.2 — the GIT application's process-scoped state, owned by the
 * HomeAppStateStore: the probe instance — its idle gate and last snapshot
 * ARE the cache — the last scan's ui, the open detail page, the selected
 * repository and the manual-refresh counter. Nothing here needs to
 * survive process death (a fresh process re-probes honestly), so no
 * DataStore is involved.
 */
internal class GitState(val probe: GitProbe) {
    var ui by mutableStateOf<GitUi>(GitUi.Probing)
    var detailPath by mutableStateOf<String?>(null)
    var selectedPath by mutableStateOf<String?>(null)
    var refreshTick by mutableStateOf(0)
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
    paneRepo: RepoSnapshot?,
    layout: GitLayout,
    onRefresh: () -> Unit,
    onSelectRepo: (RepoSnapshot) -> Unit,
    onOpenDetail: (RepoSnapshot) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val ready = ui as? GitUi.Ready
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — title + the ONE refresh control (icon-first, named),
        // both densities. The repo count is a COMPACT-only fact: ROOMY's
        // status line carries other facts, so no count is ever duplicated.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.width(8.dp))
            }
            RefreshButton(onRefresh = onRefresh)
        }

        // Repo selector — several repositories pick from ONE horizontally-
        // scrollable chip row; selecting swaps the pane below IN PLACE
        // (never a page navigation). A single repository needs no selector.
        if (repos.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repos.forEach { repo ->
                    val active = repo.path == paneRepo?.path
                    Text(
                        text = repo.name,
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = if (active) HomeTokens.accent else HomeTokens.textDim,
                        maxLines = 1,
                        modifier = Modifier
                            .border(
                                1.dp,
                                if (active) HomeTokens.accent else HomeTokens.hairline,
                                RoundedCornerShape(HomeTokens.chipRadius),
                            )
                            .clickable(
                                role = Role.Tab,
                                onClickLabel = "Show repository ${repo.name}",
                            ) { onSelectRepo(repo) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Status area — ROOMY cards only, and deliberately NOT the repo
        // count (the header owns counts in COMPACT, and the chips already
        // show the set): the git binary's real version + the dirty-repo
        // count — facts shown nowhere else.
        if (layout.showsStatusHeader && repos.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val version = ready?.snapshot?.gitVersion
            val dirtyRepos = ready?.snapshot?.dirtyRepos ?: 0
            Text(
                text = listOfNotNull(
                    version?.let { "git $it" },
                    "$dirtyRepos DIRTY",
                ).joinToString(" · "),
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.accent,
            )
        }
        Spacer(Modifier.height(6.dp))

        // The selected repository's pane — the one-glance answer: branch +
        // tracking glyphs + worktree marker on the first line, the mapped
        // path under it (roomy cards), then the changed files grouped the
        // way git's index/worktree split sees them. Tapping the pane opens
        // the repo's detail page.
        if (paneRepo != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Git details ${paneRepo.name}",
                        ) { onOpenDetail(paneRepo) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = branchText(paneRepo.status),
                            fontFamily = TerminalTheme.mono,
                            fontSize = 13.sp,
                            color = HomeTokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        val glyphs = GitPresentation.trackingGlyphs(
                            ahead = paneRepo.status?.ahead,
                            behind = paneRepo.status?.behind,
                        )
                        if (glyphs.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = glyphs,
                                fontFamily = TerminalTheme.mono,
                                fontSize = 11.sp,
                                // rendered only when nonzero — see
                                // GitPresentation.trackingGlyphs
                                color = HomeTokens.accent,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        val dirty = paneRepo.status?.dirty == true
                        Text(
                            text = GitPresentation.worktreeGlyph(dirty).toString(),
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = if (dirty) HomeTokens.accent else HomeTokens.textDim,
                        )
                    }
                    if (layout.showsPath) {
                        Text(
                            text = displayGuestRepoPath(paneRepo.path),
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTokens.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    if (paneRepo.error != null) {
                        // One unreadable repository degrades alone —
                        // stated, never hidden.
                        Text(
                            text = "git could not read this repository (${paneRepo.error})",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTokens.danger,
                        )
                    }
                }

                // The changed files, grouped. Empty groups render nothing;
                // both empty → the one honest line. Rows stay parser-faithful:
                // the single status letter + the path (renames arrowed).
                val status = paneRepo.status
                if (status != null) {
                    val staged = GitPresentation.stagedRows(status.entries)
                    val unstaged = GitPresentation.unstagedRows(status.entries)
                    if (staged.isEmpty() && unstaged.isEmpty()) {
                        Text(
                            text = "Working tree clean",
                            fontFamily = TerminalTheme.mono,
                            fontSize = 11.sp,
                            color = HomeTokens.textDim,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        if (staged.isNotEmpty()) {
                            GitSection("STAGED")
                            staged.forEach { GitFileRow(it) }
                        }
                        if (unstaged.isNotEmpty()) {
                            GitSection("UNSTAGED")
                            unstaged.forEach { GitFileRow(it) }
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // The honest state line, every density, every theme.
        Spacer(Modifier.height(4.dp))
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
                // The reason, verbatim; manual refresh rides the header's
                // refresh button — never a second refresh control.
                Text(
                    text = ui.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
            // Ready with repositories: the state line says it all; the
            // refresh cadence is the probe's idle gate + the header button.
        }
    }
}

/** A changed-files section label — git's index/worktree vocabulary. */
@Composable
private fun GitSection(label: String) {
    Text(
        text = label,
        fontFamily = TerminalTheme.mono,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = HomeTokens.textDim,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** One changed file: the single status letter + the path (renames arrowed). */
@Composable
private fun GitFileRow(row: GitPresentation.EntryRow) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = row.letter.toString(),
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = HomeTokens.textDim,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = row.label,
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one refresh control: compact icon-first, named for accessibility. */
@Composable
private fun RefreshButton(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(role = Role.Button, onClickLabel = "Refresh repositories") { onRefresh() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Refresh repositories",
            tint = HomeTokens.accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

// --------------------------------------------------------------- detail

@Composable
private fun GitDetail(
    repo: RepoSnapshot,
    roomy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
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
        // The detail actions stay compact TEXT buttons — glyphs would be
        // ambiguous here — tightened to the 32dp row the card budget allows.
        Row {
            DetailAction("TERMINAL", onOpenTerminal)
            Spacer(Modifier.width(8.dp))
            DetailAction("LINUX", onOpenLinuxShell)
            Spacer(Modifier.width(8.dp))
            DetailAction("REFRESH", onRefresh)
        }
    }
}

@Composable
private fun DetailAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(32.dp)) {
        Text(label, fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
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
