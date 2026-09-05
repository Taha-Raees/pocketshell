package app.pocketshell.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.TerminalViewModel
import app.pocketshell.packages.ApkOutputParser
import app.pocketshell.packages.CliAppCatalog
import app.pocketshell.packages.CliAppCatalogEntry
import app.pocketshell.packages.PackageGateway
import app.pocketshell.packages.PackageOperationState
import app.pocketshell.packages.PackageProbeException
import app.pocketshell.packages.PackageSearchResult
import app.pocketshell.packages.packageOperationTargetsCard
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.system.MidnightCard
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightTextField
import app.pocketshell.ui.theme.TerminalTheme
import kotlinx.coroutines.launch

/**
 * Packages (M2.4) — the real package-management frontend.
 *
 * Everything here is a view over the real Alpine `apk`: install status comes
 * from `apk info -e -v`, search from `apk search`, installs/uninstalls are
 * real apk transactions observed through [PackageGateway.operations]. The
 * catalog below is metadata only; it can never claim an installed state.
 *
 * Phase 3.5 (docs/PHASE-3.5-DESIGN.md §5): the page joins the Midnight
 * Sapphire system. The search field is the quiet chrome plate (a real
 * object — it may be a surface); catalog entries keep their cards (also real
 * objects) in the chrome tone; search hits render as flat mono rows between
 * hairlines; operation progress is the honest banner. Every 3.4 honesty
 * rule is preserved verbatim — a failed probe never reads as "Not
 * installed", the working state lands ONLY on the card it targets.
 */
@Composable
fun ExploreAppsScreen(
    terminalViewModel: TerminalViewModel,
    onBack: () -> Unit,
    onOpenedSession: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val runtimeState by RuntimeManager.state.collectAsStateWithLifecycle()
    val operation by terminalViewModel.packageOperation.collectAsStateWithLifecycle()
    val packageBusy by terminalViewModel.packageBusy.collectAsStateWithLifecycle()
    val verifyingApp by terminalViewModel.verifyingApp.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    var installedVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchQueryUsed by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PackageSearchResult>?>(null) }
    // M2.5: search hits join the installed-state probe, so a freshly
    // installed search result flips to "Installed · version" immediately.
    var probeNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    suspend fun refreshInstalled() {
        try {
            val names = buildList {
                addAll(CliAppCatalog.entries.map { it.apkPackageName })
                addAll(probeNames)
            }.distinct()
            installedVersions = PackageGateway.installedVersions(names)
            probeError = null
        } catch (e: PackageProbeException) {
            // v0.4.4 honesty rule: a failed probe is NOT "nothing installed".
            // Keep the last real answer rendered and say what actually happened
            // — the old silent emptyMap-over-a-dead-probe is how an installed
            // nano got hidden behind "Not installed" cards.
            probeError = e.message
        }
    }

    // Real state on open, and after every operation that reaches a terminal
    // state (SUCCESS or FAILED — after a FAILED apk del the truth may change).
    LaunchedEffect(runtimeState) {
        if (runtimeState == RuntimeState.READY) refreshInstalled()
    }
    LaunchedEffect(operation?.id, operation?.state) {
        val state = operation?.state
        if (state == PackageOperationState.SUCCESS || state == PackageOperationState.FAILED) {
            if (runtimeState == RuntimeState.READY) refreshInstalled()
        }
    }

    MidnightPageScaffold(title = "Packages", onBack = onBack, modifier = modifier) {
        if (runtimeState != RuntimeState.READY) {
            // §9 honesty: an empty/blocked state is quiet text lines on
            // the canvas, never a container. The Diagnostics link is real —
            // that is where the runtime install lives.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = "The Linux runtime is not installed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HomeTokens.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                MidnightNote(
                    text = "Packages are installed with the real Alpine package manager (apk) " +
                        "inside the PocketShell Linux runtime. Install the runtime from " +
                        "Diagnostics first — this screen never pretends otherwise.",
                )
                Text(
                    text = "Open Diagnostics",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 14.sp,
                    color = HomeTokens.accent,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 6.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Open Diagnostics",
                        ) { onOpenDiagnostics() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            return@MidnightPageScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- search --------------------------------------------------
            item {
                // Phase 5 §9 — the field and its action share ONE row: the
                // search control is compact workspace chrome, not a stacked
                // form. The Search button is the quiet hairline weight.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MidnightTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search Alpine packages",
                        enabled = !packageBusy,
                        modifier = Modifier.weight(1f),
                        trailing = {
                            if (packageBusy && operation?.state == PackageOperationState.SEARCHING) {
                                Text(
                                    "…",
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 14.sp,
                                    color = HomeTokens.textDim,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        },
                    )
                    MidnightQuietButton(
                        text = "Search",
                        onClick = {
                            val q = searchQuery.trim()
                            if (q.isNotEmpty()) {
                                searchResults = null
                                searchQueryUsed = q
                                terminalViewModel.searchPackages(q) { results ->
                                    searchResults = results
                                    // probe the hit names too (bounded) so Install
                                    // buttons can show the REAL installed state
                                    probeNames = results.take(12)
                                        .map { it.name }
                                        .toSet()
                                    scope.launch { refreshInstalled() }
                                }
                            }
                        },
                        enabled = !packageBusy,
                        modifier = Modifier.width(108.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                searchResults?.let { results ->
                    if (results.isEmpty()) {
                        Text(
                            "No packages matched (real apk search returned nothing).",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTokens.textDim,
                        )
                    }
                    // M2.5: name matches first (nodejs for "node"), then the
                    // description-only hits apk also returns — and every hit
                    // is INSTALLABLE (real apk add by exact package name).
                    val ranked = ApkOutputParser.rankSearchHits(results, searchQueryUsed)
                    ranked.take(12).forEachIndexed { index, hit ->
                        val known = CliAppCatalog.entries.firstOrNull { it.apkPackageName == hit.name }
                        val installedVersion = installedVersions[hit.name]
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = hit.name,
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = HomeTokens.textPrimary,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = hit.version + (hit.release?.let { "-$it" } ?: ""),
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 12.sp,
                                    color = HomeTokens.textDim,
                                )
                            }
                            known?.let {
                                Text(
                                    it.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HomeTokens.textDim,
                                )
                            }
                            when {
                                installedVersion != null -> Text(
                                    "Installed · $installedVersion" +
                                        if (known == null) {
                                            " — run '${hit.name}' from the shell"
                                        } else {
                                            ""
                                        },
                                    fontFamily = TerminalTheme.mono,
                                    fontSize = 12.sp,
                                    color = HomeTokens.accent,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                else -> Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                ) {
                                    MidnightFilledButton(
                                        text = if (packageOperationTargetsCard(packageBusy, operation, hit.name)) {
                                            "Working…"
                                        } else {
                                            "Install"
                                        },
                                        onClick = { terminalViewModel.installSearchResult(hit.name) },
                                        enabled = !packageBusy,
                                        modifier = Modifier.width(160.dp),
                                    )
                                }
                            }
                        }
                        if (index != minOf(12, ranked.size) - 1) {
                            androidx.compose.material3.HorizontalDivider(
                                color = HomeTokens.hairline.copy(alpha = 0.6f),
                            )
                        }
                    }
                    if (ranked.size > 12) {
                        Text(
                            "…and ${ranked.size - 12} more matches (refine the query to see them).",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTokens.textDim,
                        )
                    }
                }
            }

            // ---- operation progress (honest states only) -----------------
            operation?.let { op ->
                if (packageBusy || op.state == PackageOperationState.FAILED) {
                    item {
                        val failed = op.state == PackageOperationState.FAILED
                        MidnightBanner(
                            message = opLabel(op),
                            failed = failed,
                        ) {
                            if (op.stdoutTail.isNotBlank()) {
                                Text(
                                    op.stdoutTail.lineSequence().lastOrNull { it.isNotBlank() } ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HomeTokens.textDim,
                                )
                            }
                            op.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HomeTokens.danger,
                                )
                            }
                            if (failed &&
                                op.stderrTail.isNotBlank() &&
                                op.stderrTail != op.error
                            ) {
                                // apk's own stderr — the real failure text
                                // (DNS / EACCES / HTTP), never summarized away.
                                // Lines the summary already contains are not
                                // repeated verbatim (v0.4.2 polish).
                                Text(
                                    op.stderrTail.lineSequence()
                                        .filter { it.isNotBlank() }
                                        .filter { line -> op.error?.contains(line) != true }
                                        .take(4)
                                        .joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HomeTokens.textDim,
                                )
                            }
                            if (packageBusy) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    MidnightQuietButton(
                                        text = "Cancel",
                                        onClick = { PackageGateway.operations.cancelCurrent() },
                                        modifier = Modifier.width(120.dp),
                                    )
                                }
                            } else if (failed &&
                                op.kind == app.pocketshell.packages.PackageOperationKind.UPDATE_REPOSITORIES
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    MidnightQuietButton(
                                        text = "Retry",
                                        onClick = { terminalViewModel.retryRepositoryUpdate() },
                                        modifier = Modifier.width(120.dp),
                                    )
                                }
                            }
                            if (failed) {
                                Text(
                                    "Full output stays available in Diagnostics → Check package environment.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HomeTokens.textDim,
                                )
                            }
                        }
                    }
                }
            }

            // ---- featured catalog ----------------------------------------
            item {
                Spacer(Modifier.height(2.dp))
                MidnightSectionLabel("Featured")
                Spacer(Modifier.height(2.dp))
                MidnightNote(
                    text = "Curated entries — installed state always comes from the real apk database.",
                )
                probeError?.let {
                    Text(
                        "Installed state unavailable: $it — cards below may be out of date. " +
                            "\"Check package environment\" in Diagnostics shows the real cause.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            items(CliAppCatalog.entries, key = { it.id }) { entry ->
                CatalogAppCard(
                    entry = entry,
                    installedVersion = installedVersions[entry.apkPackageName],
                    stateUnknown = probeError != null &&
                        installedVersions[entry.apkPackageName] == null,
                    // v0.4.2 honesty: "Working…" ONLY on the card the running
                    // operation actually targets (or that is being verified).
                    // The global mutation lock still disables the OTHER cards'
                    // actions (single-flight) — but their labels stay true.
                    busy = verifyingApp == entry.name ||
                        packageOperationTargetsCard(packageBusy, operation, entry.apkPackageName),
                    enabled = !packageBusy,
                    onInstall = { terminalViewModel.installCatalogApp(entry) },
                    onUninstall = { terminalViewModel.uninstallCatalogApp(entry) },
                    onOpen = {
                        terminalViewModel.openCatalogApp(entry) { onOpenedSession() }
                    },
                )
            }
            item {
                launchError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.danger,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun opLabel(op: app.pocketshell.packages.PackageOperation): String {
    val target = op.packageName ?: op.query ?: ""
    return when (op.state) {
        PackageOperationState.IDLE -> "Preparing…"
        PackageOperationState.UPDATING_REPOSITORIES -> "Updating repositories…"
        PackageOperationState.SEARCHING -> "Searching “${op.query}”…"
        PackageOperationState.INSTALLING -> "Installing $target…"
        PackageOperationState.VERIFYING -> "Verifying installation…"
        PackageOperationState.UNINSTALLING -> "Uninstalling $target…"
        PackageOperationState.SUCCESS -> when (op.kind) {
            app.pocketshell.packages.PackageOperationKind.INSTALL -> "$target installed"
            app.pocketshell.packages.PackageOperationKind.UNINSTALL -> "$target removed"
            app.pocketshell.packages.PackageOperationKind.UPDATE_REPOSITORIES -> "Repositories updated"
            app.pocketshell.packages.PackageOperationKind.SEARCH -> "Search finished"
        }
        PackageOperationState.FAILED -> "Failed — ${op.error ?: "see Diagnostics"}"
    }
}

@Composable
private fun CatalogAppCard(
    entry: CliAppCatalogEntry,
    installedVersion: String?,
    stateUnknown: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onOpen: () -> Unit,
) {
    MidnightCard {
        Column {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Text(
                entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
            Text(
                // real version from apk info -e -v, an honest "Not
                // installed", or — when the probe itself failed —
                // "Installed state unknown" (never a guessed state).
                when {
                    installedVersion != null -> "Installed · $installedVersion"
                    stateUnknown -> "Installed state unknown"
                    else -> "Not installed"
                },
                fontFamily = TerminalTheme.mono,
                fontSize = 12.sp,
                color = when {
                    installedVersion != null -> HomeTokens.accent
                    else -> HomeTokens.textDim
                },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installedVersion == null) {
                // "Install" is honest even when the state is unknown: apk
                // add on an installed package is a real, safe no-op — and
                // the status line above never claims either way.
                MidnightFilledButton(
                    text = if (busy) "Working…" else "Install",
                    onClick = onInstall,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MidnightFilledButton(
                    text = if (busy) "Working…" else "Open",
                    onClick = onOpen,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                MidnightQuietButton(
                    text = "Uninstall",
                    onClick = onUninstall,
                    enabled = enabled,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
