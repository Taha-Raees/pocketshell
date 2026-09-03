package app.pocketshell.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

/**
 * Packages (M2.4) — the real package-management frontend.
 *
 * Everything here is a view over the real Alpine `apk`: install status comes
 * from `apk info -e -v`, search from `apk search`, installs/uninstalls are
 * real apk transactions observed through [PackageGateway.operations]. The
 * catalog below is metadata only; it can never claim an installed state.
 *
 * Phase 3.4 (docs/PHASE-3.4-DESIGN.md §3): the not-ready state is inline
 * text on the canvas — never a container (the 3.3 §9 rule) — and carries a
 * working `Open Diagnostics` affordance because that is where the runtime is
 * installed. Per-package surfaces stay: they are real objects (an installable
 * package with actions), which the 3.3 §4 card rules explicitly allow.
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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text("Packages", style = MaterialTheme.typography.titleLarge)
        }

        if (runtimeState != RuntimeState.READY) {
            // §9 honesty: an empty/blocked state is three quiet text lines on
            // the canvas, never a container. The Diagnostics link is real —
            // that is where the runtime install lives.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "The Linux runtime is not installed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Packages are installed with the real Alpine package manager (apk) " +
                        "inside the PocketShell Linux runtime. Install the runtime from " +
                        "Diagnostics first — this screen never pretends otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Open Diagnostics",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Open Diagnostics",
                        ) { onOpenDiagnostics() }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- search --------------------------------------------------
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search Alpine packages") },
                    trailingIcon = {
                        if (packageBusy && operation?.state == PackageOperationState.SEARCHING) {
                            Text("…", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
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
                    ) { Text("Search") }
                }
                searchResults?.let { results ->
                    val msg = if (results.isEmpty()) {
                        "No packages matched (real apk search returned nothing)."
                    } else {
                        null
                    }
                    msg?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // M2.5: name matches first (nodejs for "node"), then the
                    // description-only hits apk also returns — and every hit
                    // is INSTALLABLE (real apk add by exact package name).
                    val ranked = ApkOutputParser.rankSearchHits(results, searchQueryUsed)
                    ranked.take(12).forEach { hit ->
                        val known = CliAppCatalog.entries.firstOrNull { it.apkPackageName == hit.name }
                        val installedVersion = installedVersions[hit.name]
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${hit.name}  ${hit.version}${hit.release?.let { "-$it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            known?.let {
                                Text(
                                    it.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                else -> Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    OutlinedButton(
                                        onClick = { terminalViewModel.installSearchResult(hit.name) },
                                        enabled = !packageBusy,
                                    ) {
                                        Text(
                                            if (packageOperationTargetsCard(packageBusy, operation, hit.name)) {
                                                "Working…"
                                            } else {
                                                "Install"
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (ranked.size > 12) {
                        Text(
                            "…and ${ranked.size - 12} more matches (refine the query to see them).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- operation progress (honest states only) -----------------
            operation?.let { op ->
                if (packageBusy || op.state == PackageOperationState.FAILED) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (op.state == PackageOperationState.FAILED) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = opLabel(op),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (op.stdoutTail.isNotBlank()) {
                                    Text(
                                        op.stdoutTail.lineSequence().lastOrNull { it.isNotBlank() } ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                op.error?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                if (op.state == PackageOperationState.FAILED &&
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (packageBusy) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        OutlinedButton(
                                            onClick = { PackageGateway.operations.cancelCurrent() },
                                        ) { Text("Cancel") }
                                    }
                                } else if (op.state == PackageOperationState.FAILED &&
                                    op.kind == app.pocketshell.packages.PackageOperationKind.UPDATE_REPOSITORIES
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        OutlinedButton(
                                            onClick = { terminalViewModel.retryRepositoryUpdate() },
                                        ) { Text("Retry") }
                                    }
                                }
                                if (op.state == PackageOperationState.FAILED) {
                                    Text(
                                        "Full output stays available in Diagnostics → Check package environment.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- featured catalog ----------------------------------------
            item {
                Text(
                    "Featured",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Curated entries — installed state always comes from the real apk database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                probeError?.let {
                    Text(
                        "Installed state unavailable: $it — cards below may be out of date. " +
                            "\"Check package environment\" in Diagnostics shows the real cause.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                        color = MaterialTheme.colorScheme.error,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            installedVersion != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (installedVersion == null) {
                    // "Install" is honest even when the state is unknown: apk
                    // add on an installed package is a real, safe no-op — and
                    // the status line above never claims either way.
                    Button(onClick = onInstall, enabled = enabled) {
                        Text(if (busy) "Working…" else "Install")
                    }
                } else {
                    Button(onClick = onOpen, enabled = enabled) {
                        Text(if (busy) "Working…" else "Open")
                    }
                    OutlinedButton(onClick = onUninstall, enabled = enabled) {
                        Text("Uninstall")
                    }
                }
            }
        }
    }
}
