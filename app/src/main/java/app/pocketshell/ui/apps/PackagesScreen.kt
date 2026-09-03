package app.pocketshell.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.TerminalViewModel
import app.pocketshell.ui.components.PSBanner
import app.pocketshell.ui.components.PSBannerSeverity
import app.pocketshell.ui.components.PSIconDisc
import app.pocketshell.ui.components.PSListCard
import app.pocketshell.ui.components.PSScreenHeader
import app.pocketshell.ui.components.PSSectionLabel
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
import app.pocketshell.ui.theme.PSSpacing
import kotlinx.coroutines.launch

/**
 * Packages (docs/UI-REDESIGN.md §8 — formerly "Explore CLI Apps"): the real
 * apk frontend. Search, install, uninstall, inspect — every honesty rule
 * from v0.4.2–v0.5.0 preserved verbatim; only the skin changed.
 */
@Composable
fun PackagesScreen(
    terminalViewModel: TerminalViewModel,
    onMenu: () -> Unit,
    onOpenedSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    // Search hits join the installed-state probe, so a freshly installed
    // result flips to "Installed · version" immediately (M2.5).
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
            // Honesty rule: a failed probe is NOT "nothing installed".
            // Keep the last real answer and say what actually happened.
            probeError = e.message
        }
    }

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
        PSScreenHeader(
            title = "Packages",
            subtitle = "Alpine repositories, through the real apk",
            onMenu = onMenu,
        )

        if (runtimeState != RuntimeState.READY) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PSSpacing.gutter),
            ) {
                PSBanner(
                    severity = PSBannerSeverity.INFO,
                    message = "The Linux runtime is not installed yet",
                    detail = "Packages are installed with the real Alpine package manager " +
                        "(apk) inside the PocketShell Linux runtime. Install the runtime " +
                        "from Diagnostics first — this screen never pretends otherwise.",
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = PSSpacing.gutter,
                vertical = PSSpacing.xs,
            ),
            verticalArrangement = Arrangement.spacedBy(PSSpacing.md),
        ) {
            // ---- search --------------------------------------------------
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search Alpine packages") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (packageBusy && operation?.state == PackageOperationState.SEARCHING) {
                            Text("…", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PSSpacing.sm),
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
                    if (results.isEmpty()) {
                        Text(
                            "No packages matched (real apk search returned nothing).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = PSSpacing.sm),
                        )
                    }
                    // Name matches first (nodejs for "node"), then the
                    // description-only hits — every hit INSTALLABLE.
                    val ranked = ApkOutputParser.rankSearchHits(results, searchQueryUsed)
                    ranked.take(12).forEach { hit ->
                        val known = CliAppCatalog.entries.firstOrNull { it.apkPackageName == hit.name }
                        val installedVersion = installedVersions[hit.name]
                        Column(Modifier.padding(vertical = PSSpacing.xs)) {
                            PSListCard(
                                title = hit.name,
                                supporting = known?.description
                                    ?: "${hit.version}${hit.release?.let { r -> "-$r" } ?: ""}",
                                leading = { PSIconDisc(Icons.Outlined.Search) },
                                onClick = null,
                                trailing = {
                                    when {
                                        installedVersion != null -> Text(
                                            "Installed · $installedVersion" +
                                                if (known == null) {
                                                    " — run '${hit.name}'"
                                                } else {
                                                    ""
                                                },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                        else -> OutlinedButton(
                                            onClick = { terminalViewModel.installSearchResult(hit.name) },
                                            enabled = !packageBusy,
                                        ) {
                                            Text(
                                                if (packageOperationTargetsCard(
                                                        packageBusy,
                                                        operation,
                                                        hit.name,
                                                    )
                                                ) {
                                                    "Working…"
                                                } else {
                                                    "Install"
                                                },
                                            )
                                        }
                                    }
                                },
                            )
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
                        PSBanner(
                            severity = when (op.state) {
                                PackageOperationState.FAILED -> PSBannerSeverity.ERROR
                                else -> PSBannerSeverity.INFO
                            },
                            message = opLabel(op),
                            detail = buildString {
                                if (op.stdoutTail.isNotBlank()) {
                                    append(
                                        op.stdoutTail.lineSequence()
                                            .lastOrNull { it.isNotBlank() } ?: "",
                                    )
                                }
                                if (op.state == PackageOperationState.FAILED &&
                                    op.stderrTail.isNotBlank() &&
                                    op.stderrTail != op.error
                                ) {
                                    // apk's own stderr — the real failure text
                                    // (DNS / EACCES / HTTP), never summarized
                                    // away. Lines the summary already contains
                                    // are not repeated verbatim (v0.4.2 polish).
                                    if (isNotEmpty()) append("\n")
                                    append(
                                        op.stderrTail.lineSequence()
                                            .filter { it.isNotBlank() }
                                            .filter { line -> op.error?.contains(line) != true }
                                            .take(4)
                                            .joinToString("\n"),
                                    )
                                }
                            },
                            onDismiss = null,
                            actionLabel = when {
                                packageBusy -> "Cancel"
                                op.state == PackageOperationState.FAILED &&
                                    op.kind == app.pocketshell.packages.PackageOperationKind.UPDATE_REPOSITORIES -> "Retry"
                                else -> null
                            },
                            onAction = when {
                                packageBusy -> ({
                                    PackageGateway.operations.cancelCurrent()
                                })
                                op.state == PackageOperationState.FAILED &&
                                    op.kind == app.pocketshell.packages.PackageOperationKind.UPDATE_REPOSITORIES -> ({
                                    terminalViewModel.retryRepositoryUpdate()
                                })
                                else -> null
                            },
                        )
                        if (op.state == PackageOperationState.FAILED) {
                            Text(
                                "Full output stays available in Diagnostics → Check package environment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = PSSpacing.sm),
                            )
                        }
                    }
                }
            }

            // ---- featured catalog ----------------------------------------
            item {
                Spacer(Modifier.height(PSSpacing.sm))
                PSSectionLabel("Featured packages")
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
                        modifier = Modifier.padding(top = PSSpacing.sm),
                    )
                }
            }
            items(CliAppCatalog.entries, key = { it.id }) { entry ->
                CatalogPackageCard(
                    entry = entry,
                    installedVersion = installedVersions[entry.apkPackageName],
                    stateUnknown = probeError != null &&
                        installedVersions[entry.apkPackageName] == null,
                    // "Working…" ONLY on the card the running operation
                    // actually targets (or that is being verified). The
                    // single-flight lock still disables the OTHER cards'
                    // actions — but their labels stay true.
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
                Spacer(Modifier.height(PSSpacing.xl))
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
private fun CatalogPackageCard(
    entry: CliAppCatalogEntry,
    installedVersion: String?,
    stateUnknown: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onOpen: () -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(PSSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PSIconDisc(Icons.Outlined.Search)
                Spacer(Modifier.size(PSSpacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        // Real version from apk info -e -v, an honest "Not
                        // installed", or — when the probe itself failed —
                        // "Installed state unknown" (never a guessed state).
                        when {
                            installedVersion != null -> "Installed · $installedVersion"
                            stateUnknown -> "Installed state unknown"
                            else -> "Not installed"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            installedVersion != null -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(PSSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(PSSpacing.sm)) {
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
