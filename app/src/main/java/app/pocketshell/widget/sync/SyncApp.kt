package app.pocketshell.widget.sync

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M8.4 — SYNC/BACKUP: the PocketShell Home Application that answers, in
 * one card, "what backup/sync do I have, and when did it actually last
 * run — verified?" — and the honest answer structures the whole design:
 *
 *   overview (N profiles; each row: backend, source → destination, last
 *             run stated as NEVER RUN until a real run record exists)
 *     ↓ tap a row
 *   detail (full specs, probed backend availability, path visibility,
 *             DRY-RUN preview via one bounded guest exec, TERMINAL/LINUX)
 *     ↓ back (the card's OWN back handler — only from the overview does
 *       back reach the rest of Home)
 *
 * TRUTHFULNESS CONTRACT (pinned by the sync test suite):
 *   - a profile is a RECORD OF INTENT, not a backup; the card never
 *     states that user data was backed up, that it is protected, or that
 *     a backup was verified;
 *   - v1 performs DRY RUNS ONLY — `rsync -n --itemize-changes` and
 *     `rclone sync --dry-run --combined -` never copy or delete anything;
 *     there is deliberately NO real-run control (a real run from a Home
 *     card without progress/confirm UX is risk without product; deferred,
 *     not faked);
 *   - the run record fields exist and stay null: runs the user starts in
 *     a terminal are invisible here, and the card says so;
 *   - backends are probed with `command -v` — an absent rsync/rclone is
 *     the honest "not installed" state with the real install hint, never
 *     a silent skip;
 *   - no credential is ever stored or asked for (ssh remotes reference
 *     the user's existing guest ~/.ssh; rclone remotes the user's own
 *     rclone.conf), and no size is ever fabricated (neither dry-run
 *     format reports sizes; the card reports COUNTS of parsed entries).
 *
 * Performance: the overview probe is ONE batched guest exec (GitProbe's
 * batching discipline), idle-gated at AUTO_RESCAN_MS, lifecycle-gated to
 * RESUMED, on Dispatchers.IO; the dry run is a manual-only bounded exec.
 */
object SyncApp : HomeApplication() {

    /** The registry id (HomeApplications registers this application). */
    const val SYNC_ID = "sync"

    override val spec = HomeAppSpec(
        id = SYNC_ID,
        name = "Sync",
        summary = "Backup/sync profiles for the Linux guest — probed backends, dry-run previews",
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        val appContext = LocalContext.current.applicationContext
        val repository = remember { SyncRepository(appContext) }
        val probe = remember { SyncProbe(guestExec(appContext)) }
        // null until DataStore's first emission — the honest loading state.
        val profilesState by repository.profiles.collectAsState(initial = null)
        var ui by remember { mutableStateOf<SyncUi>(SyncUi.Loading) }
        // The application's own navigation state: which profile's detail
        // page fills the card, and whether the new-profile form is open.
        // Saveable → rotation and Home↔Settings round-trips restore them.
        var detailId by rememberSaveable { mutableStateOf<String?>(null) }
        var formOpen by rememberSaveable { mutableStateOf(false) }
        // Manual dry run: one tick = one bounded guest exec.
        var previewTick by remember { mutableStateOf(0) }
        var previewUi by remember { mutableStateOf<PreviewUi>(PreviewUi.Idle) }
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current

        // A live mirror the lifecycle loop reads at scan time (a captured
        // parameter would go stale between ticks).
        val profilesRef = remember { mutableStateOf<List<SyncProfile>>(emptyList()) }
        val scannedIds = remember { mutableStateOf<List<String>>(emptyList()) }
        var scannedOnce by remember { mutableStateOf(false) }

        suspend fun runScan(profiles: List<SyncProfile>) {
            scannedIds.value = profiles.map { it.id }
            scannedOnce = true
            val result = withContext(Dispatchers.IO) { probe.snapshot(profiles) }
            ui = when (result) {
                is ProbeResult.Failed -> SyncUi.ProbeFailed(result.reason)
                is ProbeResult.Done -> SyncUi.Ready(result.snapshot, profiles)
            }
        }

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                ui = SyncUi.Unavailable
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    // Returning to Home refreshes immediately.
                    runScan(profilesRef.value)
                    while (true) {
                        delay(SyncProbe.TICK_MS)
                        // The idle gate: a tick that fires too soon after
                        // the last scan does NOTHING — no guest exec while
                        // the card sits open and idle.
                        if (!probe.shouldFullScan(System.currentTimeMillis())) continue
                        runScan(profilesRef.value)
                    }
                }
        }

        // Profile edits rescan immediately (a fresh profile's visibility is
        // the first thing the user wants to know); the first emission is
        // skipped when a scan already covered it — the open-race dedupe.
        LaunchedEffect(profilesState, context.runtimeState) {
            val list = profilesState ?: return@LaunchedEffect
            profilesRef.value = list
            if (context.runtimeState != RuntimeState.READY) return@LaunchedEffect
            val ids = list.map { it.id }
            if (ids == scannedIds.value) return@LaunchedEffect
            if (scannedOnce) runScan(list)
        }

        val ready = ui as? SyncUi.Ready
        val profiles = ready?.profiles.orEmpty()

        // A selection whose profile was deleted degrades to the overview —
        // never a stale detail page.
        val selected = detailId?.let { id -> profiles.firstOrNull { it.id == id } }
        BackHandler(enabled = formOpen || selected != null) {
            if (formOpen) formOpen = false else detailId = null
        }

        // A fresh detail page starts with a clean preview slate.
        LaunchedEffect(detailId) {
            previewUi = PreviewUi.Idle
            previewTick = 0
        }

        // The manual dry run — one tick, one bounded exec, result assigned
        // only if this effect is still the current one.
        LaunchedEffect(previewTick) {
            val profile = detailId?.let { id -> profilesRef.value.firstOrNull { it.id == id } }
                ?: return@LaunchedEffect
            val snapshot = (ui as? SyncUi.Ready)?.snapshot ?: return@LaunchedEffect
            previewUi = PreviewUi.Running
            val result = withContext(Dispatchers.IO) {
                probe.dryRun(profile, snapshot.rsync.path, snapshot.rclone.path)
            }
            previewUi = PreviewUi.Done(result)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = SyncLayout.from(maxWidth.value, maxHeight.value)
            when {
                formOpen -> SyncForm(
                    onCancel = { formOpen = false },
                    onSave = { backend, source, destination ->
                        formOpen = false
                        scope.launch {
                            repository.add(
                                SyncProfile(
                                    id = SyncRepository.newId(),
                                    backend = backend,
                                    source = source,
                                    destination = destination,
                                    createdAtMs = SyncRepository.now(),
                                ),
                            )
                        }
                    },
                )
                selected != null -> SyncDetail(
                    profile = selected,
                    status = ready?.snapshot?.pairs?.getOrNull(profiles.indexOf(selected)),
                    snapshot = ready?.snapshot,
                    previewUi = previewUi,
                    roomy = layout == SyncLayout.ROOMY,
                    maxEntries = if (layout == SyncLayout.ROOMY) {
                        SyncLayout.DETAIL_MAX_ENTRIES_ROOMY
                    } else {
                        SyncLayout.DETAIL_MAX_ENTRIES_COMPACT
                    },
                    onBack = { detailId = null },
                    onPreview = { previewTick++ },
                    onDelete = {
                        scope.launch {
                            repository.remove(selected.id)
                            detailId = null
                        }
                    },
                    onOpenTerminal = { context.nav.openTerminal() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
                else -> SyncOverview(
                    ui = ui,
                    profiles = profiles,
                    snapshot = ready?.snapshot,
                    layout = layout,
                    onOpenDetail = { detailId = it.id },
                    onOpenForm = { formOpen = true },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                )
            }
        }
    }

    /**
     * The real exec: the sanctioned non-PTY guest path — the SAME
     * buildLaunchSpec + background-runner pairing GitApp uses (single exec
     * infrastructure, no duplicate proot logic). The minimal
     * PACKAGE_OPERATION profile is the right mount configuration for a
     * read-only probe and a dry run (the tools write nothing); DNS/network
     * quirks surface as the honest failure text of a remote attempt.
     */
    private fun guestExec(appContext: Context): SyncProbe.GuestExec =
        SyncProbe.GuestExec { argv, timeoutMs ->
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
                process.waitFor(timeoutMs)
            } catch (t: Throwable) {
                process.destroy()
                throw t
            }
        }
}

/** The application's screen state (probe-driven, never invented). */
internal sealed interface SyncUi {
    data object Loading : SyncUi
    data object Unavailable : SyncUi
    data class ProbeFailed(val reason: String) : SyncUi
    data class Ready(val snapshot: SyncSnapshot, val profiles: List<SyncProfile>) : SyncUi
}

/** The detail page's dry-run state. */
internal sealed interface PreviewUi {
    data object Idle : PreviewUi
    data object Running : PreviewUi
    data class Done(val result: DryRunResult) : PreviewUi
}

/**
 * The responsive contract — same geometry as GitLayout (the card
 * dimensions are identical, so the device-derived thresholds carry over):
 * COMPACT keeps one-line rows; ROOMY adds the status header, per-profile
 * sublines, the footer and scrolling rows. Pure + JVM-tested.
 */
internal enum class SyncLayout(
    val showsSubline: Boolean,
    val showsStatusHeader: Boolean,
    val showsFooter: Boolean,
    val scrollsRows: Boolean,
) {
    COMPACT(showsSubline = false, showsStatusHeader = false, showsFooter = false, scrollsRows = false),
    ROOMY(showsSubline = true, showsStatusHeader = true, showsFooter = true, scrollsRows = true);

    val maxRows: Int get() = if (this == ROOMY) Int.MAX_VALUE else 3

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f
        const val DETAIL_MAX_ENTRIES_COMPACT = 6
        const val DETAIL_MAX_ENTRIES_ROOMY = 20
        const val MAX_NOTICES_SHOWN = 2

        fun from(widthDp: Float, heightDp: Float): SyncLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ------------------------------------------------------------- overview

@Composable
private fun SyncOverview(
    ui: SyncUi,
    profiles: List<SyncProfile>,
    snapshot: SyncSnapshot?,
    layout: SyncLayout,
    onOpenDetail: (SyncProfile) -> Unit,
    onOpenForm: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val ready = ui as? SyncUi.Ready
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Sync",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            if (profiles.isNotEmpty()) {
                Text(
                    text = "${profiles.size} profiles",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
        }

        // Status area — backend availability, roomy cards only.
        if (layout.showsStatusHeader && profiles.isNotEmpty() && ready != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = backendHeaderText(ready.snapshot),
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.accent,
            )
        }
        Spacer(Modifier.height(6.dp))

        // Rows — the user's profiles; scroll where useful, cap where not.
        val visible = profiles.take(layout.maxRows)
        if (visible.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (layout.scrollsRows) Modifier.verticalScroll(rememberScrollState())
                        else Modifier,
                    ),
            ) {
                visible.forEachIndexed { index, profile ->
                    val status = ready?.snapshot?.pairs?.getOrNull(profiles.indexOf(profile))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Sync profile ${profile.source}",
                            ) { onOpenDetail(profile) }
                            .padding(vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (status?.sourceExists == false) "!" else "·",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 11.sp,
                                color = if (status?.sourceExists == false) {
                                    HomeTokens.danger
                                } else {
                                    HomeTokens.textDim
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = SyncProfiles.line(profile),
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (layout.showsSubline) {
                            Text(
                                text = profileSubline(profile, status),
                                style = MaterialTheme.typography.bodySmall,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 14.dp, top = 1.dp),
                            )
                        }
                    }
                    if (index != visible.lastIndex) {
                        HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                    }
                }
                if (profiles.size > visible.size) {
                    Text(
                        text = "+${profiles.size - visible.size} more",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Footer — roomy cards only.
        if (layout.showsFooter && profiles.isNotEmpty()) {
            Text(
                text = "${profiles.size} PROFILES · NEVER RUN",
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        val bothMissing = ready != null &&
            ready.snapshot.rsync.path == null &&
            ready.snapshot.rclone.path == null
        val stateLine = when {
            ui is SyncUi.Loading -> "…"
            ui is SyncUi.Unavailable -> "Linux not ready"
            ui is SyncUi.ProbeFailed -> "Could not probe"
            ready != null && profiles.isEmpty() -> "No profiles yet"
            ready != null && bothMissing -> "No sync backend installed"
            ready != null -> "Profiles only — no runs recorded"
            else -> "…"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (profiles.isNotEmpty() && ready != null) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        when {
            ui is SyncUi.Unavailable -> {
                TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Diagnostics", color = HomeTokens.accent)
                }
            }
            ui is SyncUi.ProbeFailed -> {
                Text(
                    text = ui.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ready != null && profiles.isEmpty() -> {
                Text(
                    text = "A profile records a source, a destination and a backend — it is a plan, not a backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ready != null && bothMissing -> {
                Text(
                    text = "Install rsync (main repo) or rclone (community repo) from the Linux Shell.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Open Linux", color = HomeTokens.accent)
                }
            }
        }
        if (ui !is SyncUi.Unavailable) {
            TextButton(onClick = onOpenForm, modifier = Modifier.padding(top = 2.dp)) {
                Text("+ NEW PROFILE", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
        }
    }
}

// --------------------------------------------------------------- detail

@Composable
private fun SyncDetail(
    profile: SyncProfile,
    status: PathPairStatus?,
    snapshot: SyncSnapshot?,
    previewUi: PreviewUi,
    roomy: Boolean,
    maxEntries: Int,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
) {
    // DELETE arms on the first tap — one accidental tap must not erase a
    // profile; the confirm is in-card, no dialog, no screen.
    var deleteArmed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // In-card back header: the ONLY back is the application's own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Back to Sync") { onBack() },
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
                text = "Sync",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = profile.backend.name.lowercase(),
            fontFamily = TerminalTheme.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = SyncProfiles.line(profile),
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = HomeTokens.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))
        SyncRow("SOURCE", SyncProfiles.displayPath(profile.source))
        SyncRow("DEST", SyncProfiles.displayPath(profile.destination))
        SyncRow(
            "SRC STATE",
            pathStateText(remote = status?.sourceRemote ?: SyncProfiles.isRemote(profile.source), exists = status?.sourceExists),
        )
        SyncRow(
            "DEST STATE",
            pathStateText(remote = status?.destinationRemote ?: SyncProfiles.isRemote(profile.destination), exists = status?.destinationExists),
        )
        SyncRow("LAST RUN", lastRunText(profile))

        // Backend availability — probed, never assumed.
        val backendStatus = when (profile.backend) {
            SyncBackend.RSYNC -> snapshot?.rsync
            SyncBackend.RCLONE -> snapshot?.rclone
        }
        if (backendStatus?.path == null && snapshot != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (profile.backend) {
                    SyncBackend.RSYNC ->
                        "rsync is not installed in the guest — install it from the Linux Shell (apk add rsync)."
                    SyncBackend.RCLONE ->
                        "rclone is not installed in the guest — install it from the Linux Shell " +
                            "(apk add rclone; it lives in the community repository)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // The dry-run preview area — the one action this card performs.
        Spacer(Modifier.height(4.dp))
        when (val p = previewUi) {
            PreviewUi.Idle -> if (backendStatus?.path != null) {
                TextButton(onClick = onPreview, modifier = Modifier.height(34.dp)) {
                    Text("PREVIEW (DRY RUN)", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
                }
            }
            PreviewUi.Running -> Text(
                text = "Running dry run — nothing is copied…",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
            is PreviewUi.Done -> when (val result = p.result) {
                is DryRunResult.Failed -> {
                    Text(
                        text = result.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onPreview, modifier = Modifier.padding(top = 2.dp)) {
                        Text("RETRY", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
                    }
                }
                is DryRunResult.Done -> PreviewList(
                    preview = result.preview,
                    maxEntries = maxEntries,
                    roomy = roomy,
                    onRetry = onPreview,
                )
            }
        }

        // The same dry run, typed out for the user's own terminal — where
        // real runs (the same command without the dry-run flag) belong.
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (profile.backend) {
                SyncBackend.RSYNC ->
                    "shell: rsync -n --itemize-changes -- ${profile.source} ${profile.destination}"
                SyncBackend.RCLONE ->
                    "shell: rclone sync ${profile.source} ${profile.destination} --dry-run --combined -"
            },
            fontFamily = TerminalTheme.mono,
            fontSize = 10.sp,
            color = HomeTokens.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(onClick = onOpenTerminal, modifier = Modifier.height(34.dp)) {
                Text("TERMINAL", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenLinuxShell, modifier = Modifier.height(34.dp)) {
                Text("LINUX", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    if (deleteArmed) onDelete() else deleteArmed = true
                },
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = if (deleteArmed) "CONFIRM DELETE" else "DELETE",
                    fontFamily = TerminalTheme.mono,
                    color = if (deleteArmed) HomeTokens.danger else HomeTokens.textDim,
                )
            }
        }
    }
}

@Composable
private fun PreviewList(
    preview: SyncPreview,
    maxEntries: Int,
    roomy: Boolean,
    onRetry: () -> Unit,
) {
    Column {
        if (preview.isEmpty) {
            Text(
                text = "Nothing to transfer — destination already in sync.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.accent,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            Text(
                text = previewCountsLine(preview),
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = if (preview.errorCount > 0) HomeTokens.danger else HomeTokens.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            val shown = preview.entries.take(maxEntries)
            shown.forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 1.dp)) {
                    Text(
                        text = entry.kind.glyph,
                        fontFamily = TerminalTheme.mono,
                        fontSize = 10.sp,
                        color = HomeTokens.textDim,
                        modifier = Modifier.width(16.dp),
                    )
                    Text(
                        text = entry.path,
                        fontFamily = TerminalTheme.mono,
                        fontSize = 10.sp,
                        color = HomeTokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (preview.entries.size > shown.size) {
                Text(
                    text = "+${preview.entries.size - shown.size} more",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 10.sp,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (roomy && preview.summary.isNotEmpty()) {
            Text(
                text = preview.summary.last(),
                fontFamily = TerminalTheme.mono,
                fontSize = 10.sp,
                color = HomeTokens.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = 2.dp)) {
            Text("REFRESH PREVIEW", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
        }
    }
}

// ----------------------------------------------------------------- form

@Composable
private fun SyncForm(
    onCancel: () -> Unit,
    onSave: (SyncBackend, String, String) -> Unit,
) {
    var backendName by rememberSaveable { mutableStateOf(SyncBackend.RSYNC.name) }
    var source by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    val backend = SyncBackend.entries.firstOrNull { it.name == backendName } ?: SyncBackend.RSYNC
    val problem = SyncProfiles.validate(source, destination)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // In-card back header: the ONLY back is the application's own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Back to Sync") { onCancel() },
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
                text = "New profile",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }

        Row {
            SyncBackend.entries.forEach { candidate ->
                TextButton(
                    onClick = { backendName = candidate.name },
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        text = candidate.name.lowercase(),
                        fontFamily = TerminalTheme.mono,
                        color = if (candidate == backend) HomeTokens.accent else HomeTokens.textDim,
                    )
                }
            }
        }
        FormField(
            label = "SOURCE",
            value = source,
            onValue = { source = it },
            placeholder = "guest path, e.g. /root/project",
        )
        FormField(
            label = "DEST",
            value = destination,
            onValue = { destination = it },
            placeholder = when (backend) {
                SyncBackend.RSYNC -> "/mnt/backup or user@host:/path"
                SyncBackend.RCLONE -> "/mnt/backup or remote:path"
            },
        )
        if (problem != null) {
            Text(
                text = problem,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.danger,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = "No credentials are stored here — ssh remotes use the guest's own ~/.ssh, " +
                "rclone remotes the guest's own rclone.conf. The card previews; it never copies.",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row {
            TextButton(
                onClick = { onSave(backend, source.trim(), destination.trim()) },
                enabled = problem == null,
                modifier = Modifier.height(34.dp),
            ) {
                Text("SAVE", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCancel, modifier = Modifier.height(34.dp)) {
                Text("CANCEL", fontFamily = TerminalTheme.mono, color = HomeTokens.textDim)
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = TerminalTheme.mono,
            fontSize = 10.sp,
            color = HomeTokens.textDim,
            modifier = Modifier.width(64.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = HomeTokens.textPrimary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { }),
            cursorBrush = SolidColor(HomeTokens.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Column {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 11.sp,
                            color = HomeTokens.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

// -------------------------------------------------------------- helpers

@Composable
private fun SyncRow(label: String, value: String) {
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

private fun backendHeaderText(snapshot: SyncSnapshot): String = listOf(
    snapshot.rsync.path?.let { "rsync ${snapshot.rsync.version ?: ""}".trim() } ?: "rsync missing",
    snapshot.rclone.path?.let { "rclone ${snapshot.rclone.version ?: ""}".trim() } ?: "rclone missing",
).joinToString(" · ")

/** The overview subline: path facts + the honest run fact, in one line. */
private fun profileSubline(profile: SyncProfile, status: PathPairStatus?): String {
    val src = pathStateText(
        remote = status?.sourceRemote ?: SyncProfiles.isRemote(profile.source),
        exists = status?.sourceExists,
    )
    val dst = pathStateText(
        remote = status?.destinationRemote ?: SyncProfiles.isRemote(profile.destination),
        exists = status?.destinationExists,
    )
    return "$src · $dst · never run"
}

private fun pathStateText(remote: Boolean, exists: Boolean?): String = when {
    remote -> "remote"
    exists == null -> "unchecked"
    exists -> "visible"
    else -> "missing"
}

private fun lastRunText(profile: SyncProfile): String {
    val ms = profile.lastRunMs ?: return "never run (this card only previews)"
    val summary = profile.lastRunSummary ?: return "ran at $ms"
    return "ran at $ms · $summary"
}

internal fun previewCountsLine(preview: SyncPreview): String {
    val parts = listOf(
        "${preview.newCount} new",
        "${preview.changedCount} changed",
        "${preview.deletedCount} deleted",
    ) + (if (preview.unchangedCount > 0) listOf("${preview.unchangedCount} unchanged") else emptyList()) +
        (if (preview.errorCount > 0) listOf("${preview.errorCount} errors") else emptyList())
    return parts.joinToString(" · ")
}
