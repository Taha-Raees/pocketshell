package app.pocketshell.widget.storage

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
import androidx.compose.runtime.rememberCoroutineScope
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
        val probe = remember { GuestCacheProbe(guestExec(appContext)) }
        var ui by remember { mutableStateOf<StorageUi>(StorageUi.Measuring) }
        // The application's own navigation state: the category whose page
        // fills the card (null = overview). Saveable → rotation and
        // Home↔Settings round-trips restore the page.
        var pageId by rememberSaveable { mutableStateOf<String?>(null) }
        // Manual refresh: an immediate re-measure (the guest probe self-throttles).
        var refreshTick by remember { mutableStateOf(0) }
        // The explicit two-step clear flow (never auto-run, cancellable).
        var clearState by remember { mutableStateOf<ClearState>(ClearState.Idle) }
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current

        // The ONE host measurement: RuntimeStorageFacts (reused verbatim)
        // plus the two app-owned cache walks — all on Dispatchers.IO,
        // budgeted, and cancelled with the composition that asked for them.
        suspend fun measure(guest: GuestCaches): StorageUi = withContext(Dispatchers.IO) {
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

        // The guest probe, gated (at most one exec per AUTO_RESCAN_MS unless
        // the caller invalidated the gate first).
        suspend fun probeGuest(): GuestCaches = withContext(Dispatchers.IO) {
            val result =
                if (probe.shouldProbe(System.currentTimeMillis())) probe.snapshot()
                else probe.cachedResult
            when (result) {
                null -> GuestCaches.NotProbed
                is GuestCacheProbe.ProbeResult.Done -> GuestCaches.Sizes(result.entries)
                is GuestCacheProbe.ProbeResult.Failed -> GuestCaches.Failed(result.reason)
            }
        }

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                // The host-side cache categories exist regardless — measure
                // them honestly; the guest section states WHY it cannot ask.
                ui = measure(GuestCaches.Unavailable)
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    // One host walk + at most one guest exec per Home visit.
                    val guest = probeGuest()
                    ui = measure(guest)
                }
        }

        LaunchedEffect(refreshTick) {
            if (refreshTick == 0) return@LaunchedEffect
            val ready = context.runtimeState == RuntimeState.READY
            val guest = if (ready) probeGuest() else GuestCaches.Unavailable
            ui = measure(guest)
        }

        // The clear flow: category row → this preview page → CLEAR. Only the
        // two APP-OWNED categories ever reach here (the page offers the
        // button only for clearable ones).
        fun clear(category: StorageCategory) {
            if (clearState == ClearState.Running) return
            val storage = RuntimeStorage(appContext.noBackupFilesDir)
            val dir = when (category) {
                StorageCategory.PACKAGE_CACHE -> PackageGateway.apkCacheDir(storage)
                StorageCategory.SHARE_STAGING ->
                    File(appContext.cacheDir, FileShareOps.STAGING_DIR_NAME)
                else -> return
            }
            clearState = ClearState.Running
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CategoryScan.clearRegularFiles(dir, isCancelled = { !isActive })
                }
                clearState = when {
                    result.stoppedEarly -> ClearState.Stopped
                    !result.succeeded ->
                        ClearState.Failed("${result.failures} item(s) could not be removed")
                    else -> ClearState.Done(result.filesDeleted, result.bytesFreed)
                }
                // Sizes update from a fresh honest measurement, never a guess.
                refreshTick++
            }
        }

        val page = pageId?.let { StorageCategory.byId(it) }
        val snapshot = (ui as? StorageUi.Ready)?.snapshot
        BackHandler(enabled = page != null) { pageId = null }
        // A fresh page starts its flow honestly (a Done from another page
        // must not bleed in).
        LaunchedEffect(pageId) { clearState = ClearState.Idle }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val layout = StorageLayout.from(maxWidth.value, maxHeight.value)
            if (page != null && snapshot != null) {
                CategoryPage(
                    category = page,
                    snapshot = snapshot,
                    clearState = clearState,
                    roomy = layout == StorageLayout.ROOMY,
                    onBack = { pageId = null },
                    onClear = { clear(page) },
                    onOpenDiagnostics = { context.nav.openDiagnostics() },
                    onOpenLinuxShell = { context.nav.openLinuxShell() },
                )
            } else {
                StorageOverview(
                    ui = ui,
                    snapshot = snapshot,
                    layout = layout,
                    onOpenCategory = { pageId = it.id },
                    onRefresh = {
                        probe.invalidate()
                        refreshTick++
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
        // Header — the application's title bar, both densities.
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
            else -> {
                TextButton(onClick = onRefresh, modifier = Modifier.padding(top = 2.dp)) {
                    Text("REFRESH", fontFamily = TerminalTheme.mono, color = HomeTokens.accent)
                }
            }
        }
    }
}

// -------------------------------------------------------- category page

@Composable
private fun CategoryPage(
    category: StorageCategory,
    snapshot: StorageSnapshot,
    clearState: ClearState,
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
                ClearablePage(category, snapshot.apkCache, clearState, onClear)
            StorageCategory.SHARE_STAGING ->
                ClearablePage(category, snapshot.staging, clearState, onClear)
            StorageCategory.GUEST_CACHES ->
                GuestCachesPage(snapshot.guest, roomy, onOpenLinuxShell)
        }
    }
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
    Text(
        text = "This card only measures the runtime. Installing, repairing and " +
            "removing it live in Diagnostics.",
        style = MaterialTheme.typography.bodySmall,
        color = HomeTokens.textDim,
    )
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
    onClear: () -> Unit,
) {
    // THE PREVIEW: exactly what Clear removes and roughly how much — before
    // any button can do anything.
    Text(
        text = previewHeadline(size),
        fontFamily = TerminalTheme.mono,
        fontSize = 12.sp,
        color = HomeTokens.textPrimary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = when (category) {
            StorageCategory.PACKAGE_CACHE ->
                "Clearing removes downloaded package archives (and emptied cache " +
                    "folders) inside the app's own cache. apk re-downloads whatever " +
                    "it needs next time; installed packages are not touched."
            StorageCategory.SHARE_STAGING ->
                "Clearing removes staged share copies inside the app's own cache. " +
                    "The staging area refills on the next share and cleans itself " +
                    "before each one."
            else -> ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = HomeTokens.textDim,
    )
    if (size.truncated) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = TRUNCATION_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
        )
    }
    val stateLine = clearStateLine(clearState)
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
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = "CLEAR",
                fontFamily = TerminalTheme.mono,
                color = if (clearState == ClearState.Running) HomeTokens.textDim else HomeTokens.danger,
            )
        }
    }
}

// ------------------------------------------------------ guest caches page

@Composable
private fun GuestCachesPage(
    guest: GuestCaches,
    roomy: Boolean,
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
            guest.entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            }
            if (roomy) {
                Text(
                    text = "These live inside the runtime total above — this is a " +
                        "breakdown, not extra space.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        else -> Unit
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "This card only measures guest caches — clearing them is terminal " +
            "work (for example: npm cache clean --force, or rm -rf ~/.cache/…).",
        style = MaterialTheme.typography.bodySmall,
        color = HomeTokens.textDim,
    )
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
