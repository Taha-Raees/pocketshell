package app.pocketshell.widget.storage

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.pocketshell.diagnostics.RuntimeStorageFacts
import app.pocketshell.files.saf.FileShareOps
import app.pocketshell.packages.PackageGateway
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
import app.pocketshell.widget.probe.StorageScan
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M8.4 — STORAGE: the Home application that answers "where is my space
 * going, and what can be safely reclaimed?" (ServersApp is the reference;
 * this file copies its discipline).
 *
 * The card IS the screen:
 *
 *   overview (runtime, package cache, share staging, guest caches — each
 *             row a real measured size, free space in the header)
 *     ↓ tap a row
 *   category page — for the two APP-OWNED caches this is the PREVIEW:
 *             exactly what Clear removes and roughly how much, then an
 *             explicit CLEAR the user must tap. Nothing ever deletes
 *             automatically.
 *     ↓ back (the card's OWN back handler — only from the overview does
 *       back reach the rest of Home)
 *
 * Truth sources, none duplicated:
 *   - the runtime row + free space ride [RuntimeStorageFacts] UNCHANGED —
 *     the same single budgeted NOFOLLOW walk Diagnostics uses; this card
 *     never re-walks the runtime tree;
 *   - the two app-owned cache categories are sized by [CategoryScan]
 *     (budgeted, NOFOLLOW, cancellable, honest floors);
 *   - guest caches come from ONE batched read-only `du` exec
 *     ([GuestCacheProbe], the GitProbe pattern). v1 only MEASURES them —
 *     clearing guest-side caches is terminal work, and the card says so.
 *
 * Honest states everywhere: measuring, Linux not ready, truncated scans
 * (floors, stated), nothing reclaimable, empty caches, probe failures with
 * their real reason. NO main-thread I/O ever — the M8.2 Diagnostics ANR
 * was exactly that shortcut.
 *
 * M8.4.2: every mutable piece lives in the process-scoped [StorageState]
 * holder (HomeAppStateStore) — navigation and carousel page disposal
 * destroy only the composition, never the state. A measured card
 * re-renders its snapshot instantly; the only automatic scans are the
 * first load and the runtime arriving READY without a usable snapshot.
 * Refresh is the explicit header action.
 *
 * M8.4.3: the card reads as a STORAGE ANALYZER — every category page
 * states WHY it exists and the CONSEQUENCE of clearing it ([categoryCopy]);
 * guest caches list per tool, largest first, each row marked terminal-work;
 * the clear preview carries the walk's census (count + largest file); and
 * the clear flow shows its whole arc: freed → re-measuring → cache now.
 */
object StorageApp : HomeApplication() {

    /** The registry id (HomeApplications registers this application). */
    const val STORAGE_ID = "storage"

    override val spec = HomeAppSpec(
        id = STORAGE_ID,
        name = "Storage",
        summary = "Where your space goes — and what can be safely reclaimed",
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        val appContext = LocalContext.current.applicationContext
        // M8.4.2: the ONE process-scoped holder — the last snapshot, the
        // guest probe (its idle gate IS part of the cache), the in-card
        // page, the clear flow and the refresh tick. Leaving Home or the
        // carousel swiping a page away disposes only this composition.
        val state = remember {
            context.stateStore.forApp(STORAGE_ID) {
                StorageState(GuestCacheProbe(guestExec(appContext)))
            }
        }
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current

        // The ONE host measurement: RuntimeStorageFacts (reused verbatim)
        // plus the two app-owned cache walks — all on Dispatchers.IO,
        // budgeted, and cancelled with the composition that asked for them.
        // The measuring flag brackets the scan so the clear arc can say
        // "re-measuring…" honestly instead of showing a stale size.
        suspend fun measure(guest: GuestCaches): StorageUi {
            state.measuring = true
            try {
                return withContext(Dispatchers.IO) {
                    val storage = RuntimeStorage(appContext.noBackupFilesDir)
                    val facts = RuntimeStorageFacts.collect(storage)
                    val apkCache = CategoryScan.size(
                        PackageGateway.apkCacheDir(storage),
                        isCancelled = { !isActive },
                    )
                    val staging = CategoryScan.size(
                        File(appContext.cacheDir, FileShareOps.STAGING_DIR_NAME),
                        isCancelled = { !isActive },
                    )
                    StorageUi.Ready(
                        StorageSnapshot(
                            runtime = RuntimeFacts(
                                present = facts.runtimeSizeBytes != null,
                                bytes = facts.runtimeSizeBytes,
                                fileCount = facts.rootfsFileCount,
                                truncated = facts.truncated,
                            ),
                            apkCache = apkCache,
                            staging = staging,
                            guest = guest,
                            freeBytes = facts.freeBytes,
                            hostTruncated = facts.truncated || apkCache.truncated || staging.truncated,
                            scannedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                state.measuring = false
            }
        }

        // The guest probe, gated (at most one exec per AUTO_RESCAN_MS unless
        // the caller invalidated the gate first).
        suspend fun probeGuest(): GuestCaches = withContext(Dispatchers.IO) {
            val result =
                if (state.probe.shouldProbe(System.currentTimeMillis())) state.probe.snapshot()
                else state.probe.cachedResult
            when (result) {
                null -> GuestCaches.NotProbed
                is GuestCacheProbe.ProbeResult.Done -> GuestCaches.Sizes(result.entries)
                is GuestCacheProbe.ProbeResult.Failed -> GuestCaches.Failed(result.reason)
            }
        }

        // The automatic scan triggers — and only these (M8.4.2): a first
        // load with no cached snapshot, or the runtime arriving READY
        // without one. A holder that already holds a Ready snapshot taken
        // with the runtime present re-renders it: no "Measuring…" flash,
        // no rescan on re-entry, and no fire while the user merely reads
        // (these run on composition ticks of a visible, resumed card only).
        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                if (state.ui is StorageUi.Ready) return@LaunchedEffect
                // The host-side cache categories exist regardless — measure
                // them honestly; the guest section states WHY it cannot ask.
                state.ui = measure(GuestCaches.Unavailable)
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    // A snapshot that already saw the runtime present is the
                    // cache; one taken before it appeared (or none) is the
                    // one honest re-measure. The probe's gate still bounds
                    // the guest exec cost.
                    val cached =
                        (state.ui as? StorageUi.Ready)?.snapshot?.runtime?.present == true
                    if (cached) return@collectLatest
                    val guest = probeGuest()
                    state.ui = measure(guest)
                }
        }

        // The explicit re-measure (header refresh; also the post-clear
        // follow-up). The tick is consumed only after its scan finishes, so
        // a re-entry never re-fires a completed refresh — and a refresh
        // interrupted by navigation finishes on return, because the user
        // asked for it.
        LaunchedEffect(state.refreshTick) {
            if (state.refreshTick == 0) return@LaunchedEffect
            val tick = state.refreshTick
            val ready = context.runtimeState == RuntimeState.READY
            val guest = if (ready) probeGuest() else GuestCaches.Unavailable
            state.ui = measure(guest)
            if (state.refreshTick == tick) state.refreshTick = 0
        }

        // The clear flow: category row → this preview page → CLEAR. Only the
        // two APP-OWNED categories ever reach here (the page offers the
        // button only for clearable ones).
        fun clear(category: StorageCategory) {
            if (state.clearState == ClearState.Running) return
            val storage = RuntimeStorage(appContext.noBackupFilesDir)
            val dir = when (category) {
                StorageCategory.PACKAGE_CACHE -> PackageGateway.apkCacheDir(storage)
                StorageCategory.SHARE_STAGING ->
                    File(appContext.cacheDir, FileShareOps.STAGING_DIR_NAME)
                else -> return
            }
            state.clearState = ClearState.Running
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CategoryScan.clearRegularFiles(dir, isCancelled = { !isActive })
                }
                state.clearState = when {
                    result.stoppedEarly -> ClearState.Stopped
                    !result.succeeded ->
                        ClearState.Failed("${result.failures} item(s) could not be removed")
                    else -> ClearState.Done(result.filesDeleted, result.bytesFreed)
                }
                if (state.clearState is ClearState.Done || state.clearState is ClearState.Stopped) {
                    // The AFTER number is pending until the re-measure lands —
                    // never render the stale pre-clear size as "cache now".
                    state.measuring = true
                }
                // Sizes update from a fresh honest measurement, never a guess.
                state.refreshTick++
            }
        }

        val page = state.pageId?.let { StorageCategory.byId(it) }
        val snapshot = (state.ui as? StorageUi.Ready)?.snapshot
        BackHandler(enabled = page != null) { state.pageId = null }
        // A fresh page starts its flow honestly (a Done from another page
        // must not bleed in).
        LaunchedEffect(state.pageId) { state.clearState = ClearState.Idle }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = StorageLayout.from(maxWidth.value, maxHeight.value)
            if (page != null && snapshot != null) {
                CategoryPage(
                    category = page,
                    snapshot = snapshot,
                    clearState = state.clearState,
                    measuring = state.measuring,
                    roomy = layout == StorageLayout.ROOMY,
                    onBack = { state.pageId = null },
                    onClear = { clear(page) },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            } else {
                StorageOverview(
                    ui = state.ui,
                    snapshot = snapshot,
                    layout = layout,
                    onOpenCategory = { state.pageId = it.id },
                    onRefresh = {
                        // THE manual refresh: the probe's gate is bypassed
                        // for exactly this request, then a real re-measure.
                        state.probe.invalidate()
                        state.refreshTick++
                    },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            }
        }
    }

    /**
     * The real exec: the sanctioned non-PTY guest path — the SAME
     * buildLaunchSpec + background-runner pairing the Git application and
     * AlpinePackageManager use (single exec infrastructure, no duplicate
     * proot logic). The minimal PACKAGE_OPERATION profile is the
     * device-proven safe mount configuration for an offline read-only
     * probe. No DNS/workspace repair: this probe never mutates the rootfs.
     */
    private fun guestExec(appContext: Context): GuestCacheProbe.GuestExec =
        GuestCacheProbe.GuestExec { argv ->
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
                process.waitFor(GuestCacheProbe.SCAN_TIMEOUT_MS)
            } catch (t: Throwable) {
                process.destroy()
                throw t
            }
        }
}

/**
 * M8.4.2 — the STORAGE application's process-scoped state, owned by the
 * HomeAppStateStore: the last measurement, the in-card page, the clear
 * flow, the refresh tick and the guest probe itself (its shouldProbe idle
 * gate is part of the cached measurement). Leaving Home or swiping the
 * card away disposes only the composition — the next visit re-renders
 * this state instead of re-measuring.
 */
internal class StorageState(val probe: GuestCacheProbe) {
    var ui by mutableStateOf<StorageUi>(StorageUi.Measuring)
    var pageId by mutableStateOf<String?>(null)
    var refreshTick by mutableStateOf(0)
    var clearState by mutableStateOf<ClearState>(ClearState.Idle)

    /**
     * True while a measurement is in flight or pending — the AFTER half of
     * the clear arc renders "re-measuring…" instead of a stale size until
     * the fresh snapshot lands.
     */
    var measuring by mutableStateOf(false)
}

// ------------------------------------------------------------- overview

@Composable
private fun StorageOverview(
    ui: StorageUi,
    snapshot: StorageSnapshot?,
    layout: StorageLayout,
    onOpenCategory: (StorageCategory) -> Unit,
    onRefresh: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenLinuxShell: () -> Unit,
) {
    val reclaimable = snapshot?.let(::reclaimableBytes) ?: 0L
    Column(modifier = Modifier.fillMaxSize()) {
        // Header — the application's title bar, both densities; the refresh
        // action (M8.4.2) lives here, compact and always reachable.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            snapshot?.freeBytes?.let { free ->
                Text(
                    text = "${StorageScan.formatBytes(free)} free",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim,
                )
            }
            Spacer(Modifier.width(6.dp))
            RefreshAction(onClick = onRefresh)
        }

        // Status area — the at-a-glance answer, roomy cards only.
        if (layout.showsStatusHeader && snapshot != null && reclaimable > 0L) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${StorageScan.formatBytes(totalHostBytes(snapshot))} USED · " +
                    "${StorageScan.formatBytes(reclaimable)} RECLAIMABLE",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = HomeTokens.accent,
            )
        }
        Spacer(Modifier.height(6.dp))

        // Rows — the four real categories; scrolling so nothing clips.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StorageCategory.entries.forEachIndexed { index, category ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "${category.label} details",
                        ) { onOpenCategory(category) }
                        .padding(vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.label,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = snapshot?.let { categoryValue(category, it) } ?: "…",
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textDim,
                        )
                    }
                    // M8.4.3: the row's secondary fact — a count the snapshot
                    // already holds. No new scanning, no invented numbers.
                    snapshot?.let { current ->
                        categoryDetail(category, current)?.let { detail ->
                            Text(
                                text = detail,
                                fontFamily = TerminalTheme.mono,
                                fontSize = 10.sp,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (index != StorageCategory.entries.lastIndex) {
                    HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                }
            }
            if (snapshot?.hostTruncated == true) {
                Text(
                    text = TRUNCATION_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        // The honest state line, every density, every theme.
        Text(
            text = overviewStateLine(ui),
            style = MaterialTheme.typography.bodySmall,
            color = if (overviewStateAccented(ui)) HomeTokens.accent else HomeTokens.textDim,
            maxLines = 1,
        )
        val ready = ui as? StorageUi.Ready
        when {
            ready == null -> Unit
            !ready.snapshot.runtime.present &&
                ready.snapshot.guest == GuestCaches.Unavailable -> {
                TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Diagnostics", color = HomeTokens.accent)
                }
            }
            reclaimable > 0L -> {
                Text(
                    text = "Open the Package cache or Share staging page to clear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
                    Text("Open Linux", color = HomeTokens.accent)
                }
            }
            // Nothing reclaimable: the header refresh stays the ONE
            // re-measure affordance — no duplicate action row here.
            else -> Unit
        }
    }
}

/** The compact header refresh (TodoApp's IconAction shape, 28dp hit area). */
@Composable
private fun RefreshAction(onClick: () -> Unit) {
    Icon(
        imageVector = Icons.Outlined.Refresh,
        contentDescription = "Refresh storage",
        tint = HomeTokens.accent,
        modifier = Modifier
            .size(28.dp)
            .clickable(role = Role.Button, onClickLabel = "Refresh storage") { onClick() }
            .padding(3.dp),
    )
}

// -------------------------------------------------------- category page

@Composable
private fun CategoryPage(
    category: StorageCategory,
    snapshot: StorageSnapshot,
    clearState: ClearState,
    measuring: Boolean,
    roomy: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onOpenDiagnostics: () -> Unit,
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
                .clickable(role = Role.Button, onClickLabel = "Back to Storage") { onBack() },
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
                text = "Storage",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                letterSpacing = 1.6.sp,
                color = HomeTokens.textDim,
            )
        }
        Text(
            text = category.label,
            fontFamily = TerminalTheme.mono,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = HomeTokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))

        when (category) {
            StorageCategory.RUNTIME -> RuntimePage(snapshot, roomy, onOpenDiagnostics)
            StorageCategory.PACKAGE_CACHE ->
                ClearablePage(category, snapshot.apkCache, clearState, measuring, onClear)
            StorageCategory.SHARE_STAGING ->
                ClearablePage(category, snapshot.staging, clearState, measuring, onClear)
            StorageCategory.GUEST_CACHES ->
                GuestCachesPage(snapshot.guest, onOpenLinuxShell)
        }
    }
}

/**
 * The analyzer block every category page carries: WHY the category exists,
 * then the CONSEQUENCE of clearing it. Two dim lines, one place.
 */
@Composable
private fun CategoryCopyBlock(category: StorageCategory) {
    val copy = categoryCopy(category)
    Text(
        text = copy.why,
        style = MaterialTheme.typography.bodySmall,
        color = HomeTokens.textDim,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = copy.consequence,
        style = MaterialTheme.typography.bodySmall,
        color = HomeTokens.textDim,
    )
}

// ------------------------------------------------------ runtime page

@Composable
private fun RuntimePage(
    snapshot: StorageSnapshot,
    roomy: Boolean,
    onOpenDiagnostics: () -> Unit,
) {
    val runtime = snapshot.runtime
    FactRow("SIZE", runtime.bytes?.let(StorageScan::formatBytes) ?: "not installed")
    if (roomy) {
        FactRow("FILES", runtime.fileCount?.toString() ?: "—")
        FactRow("FREE", snapshot.freeBytes?.let(StorageScan::formatBytes) ?: "—")
    }
    if (runtime.truncated) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = TRUNCATION_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
        )
    }
    Spacer(Modifier.height(4.dp))
    CategoryCopyBlock(StorageCategory.RUNTIME)
    TextButton(onClick = onOpenDiagnostics, modifier = Modifier.padding(top = 2.dp)) {
        Text("Diagnostics", color = HomeTokens.accent)
    }
}

// ------------------------------------------------------ clearable pages

@Composable
private fun ClearablePage(
    category: StorageCategory,
    size: CategoryScan.SizeResult,
    clearState: ClearState,
    measuring: Boolean,
    onClear: () -> Unit,
) {
    // THE PREVIEW: exactly what Clear removes and roughly how much — before
    // any button can do anything. The census (largest single file) rides the
    // headline when the walk saw one.
    Text(
        text = previewHeadline(size),
        fontFamily = TerminalTheme.mono,
        fontSize = 12.sp,
        color = HomeTokens.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    // The analyzer copy: WHY the category exists + the CONSEQUENCE of clearing.
    CategoryCopyBlock(category)
    if (size.truncated) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = TRUNCATION_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
        )
    }
    val stateLine = clearStateLine(clearState, size, measuring)
    if (stateLine.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stateLine,
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = when (clearState) {
                is ClearState.Done -> HomeTokens.accent
                is ClearState.Failed -> HomeTokens.danger
                else -> HomeTokens.textDim
            },
        )
    }
    if (size.exists && size.files > 0) {
        TextButton(
            onClick = onClear,
            enabled = clearState != ClearState.Running,
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(
                text = "CLEAR",
                fontFamily = TerminalTheme.mono,
                fontSize = 11.sp,
                color = if (clearState == ClearState.Running) HomeTokens.textDim else HomeTokens.danger,
            )
        }
    }
}

// ------------------------------------------------------ guest caches page

/**
 * The guest caches BREAKDOWN: one row per probed tool cache, largest first
 * ([sortedGuestCaches]), each row honestly stating its measured size (or
 * "unknown" when du could not say) and that clearing it is terminal work —
 * this card never deletes guest files.
 */
@Composable
private fun GuestCachesPage(
    guest: GuestCaches,
    onOpenLinuxShell: () -> Unit,
) {
    Text(
        text = guestStateLine(guest),
        fontFamily = TerminalTheme.mono,
        fontSize = 11.sp,
        color = HomeTokens.textDim,
    )
    Spacer(Modifier.height(4.dp))
    when (guest) {
        is GuestCaches.Failed -> Text(
            text = guest.reason,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        is GuestCaches.Sizes -> {
            sortedGuestCaches(guest.entries).forEach { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = guestCacheLabel(entry.name),
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = entry.kilobytes?.let { StorageScan.formatBytes(it * 1024) }
                                ?: "unknown",
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textDim,
                        )
                    }
                    // The per-row note: clearing this cache is terminal work.
                    Text(
                        text = "cleared from a terminal",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
            }
        }
        else -> Unit
    }
    Spacer(Modifier.height(4.dp))
    // The analyzer copy: WHY the section exists + the clearing consequence.
    CategoryCopyBlock(StorageCategory.GUEST_CACHES)
    if (guest !is GuestCaches.Unavailable) {
        TextButton(onClick = onOpenLinuxShell, modifier = Modifier.padding(top = 2.dp)) {
            Text("Open Linux", color = HomeTokens.accent)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
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
