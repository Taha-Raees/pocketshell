package app.pocketshell.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.widget.HomeApplications

/**
 * M8.3 — Control Center → Home applications: manage the carousel's
 * configured order. The user chooses WHICH applications appear (add from
 * the registry's available set), in WHICH order (up/down), and can remove
 * them or restore the default. The carousel order follows this list.
 */
@Composable
fun HomeWidgetsScreen(
    viewModel: app.pocketshell.widget.HomeApplicationViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appIds by viewModel.homeAppIds.collectAsStateWithLifecycle()
    val externals by viewModel.externalApps.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()

    MidnightPageScaffold(title = "Home applications", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "The large card on Home is a carousel of your Home " +
                    "applications — swipe sideways to change application. " +
                    "Each application is its own screen inside the card.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))

            MidnightSectionLabel("On Home")
            Spacer(Modifier.height(4.dp))
            appIds.forEachIndexed { index, id ->
                val spec = HomeApplications.byId(id)?.spec
                    ?: externals.firstOrNull { it.spec.id == id }?.spec
                ConfiguredAppRow(
                    name = spec?.name ?: "Missing application",
                    summary = spec?.summary ?: "$id — install it from the widget catalog below",
                    missing = spec == null,
                    first = index == 0,
                    last = index == appIds.lastIndex,
                    onMoveUp = { viewModel.moveUp(id) },
                    onMoveDown = { viewModel.moveDown(id) },
                    onRemove = { viewModel.remove(id) },
                )
            }
            if (appIds.isEmpty()) {
                Text(
                    text = "No Home applications — add one below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            MidnightSectionLabel("Available")
            Spacer(Modifier.height(4.dp))
            val available = (HomeApplications.specs + externals.map { it.spec })
                .filter { spec -> spec.id !in appIds }
                .distinctBy { it.id }
            if (available.isEmpty()) {
                Text(
                    text = "Every installed application is already on Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else {
                available.forEach { spec ->
                    AvailableAppRow(
                        name = spec.name,
                        summary = spec.summary,
                        onAdd = { viewModel.add(spec.id) },
                    )
                }
            }
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            TextButton(
                onClick = { viewModel.restoreDefault() },
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                Text("Restore default", color = HomeTokens.accent)
            }
            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // M8.4.4 — the DOWNLOADABLE catalog (ps-widget-repo). Widgets
            // are data manifests (probe + template) rendered by fixed app
            // code — never downloaded code.
            MidnightSectionLabel("Widget catalog")
            Text(
                text = "Downloadable Home Applications from " +
                    "github.com/Taha-Raees/ps-widget-repo — data manifests " +
                    "rendered by the app, never code.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (catalog.fetching) {
                Text(
                    text = "Fetching catalog…",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            } else if (catalog.entries.isEmpty()) {
                TextButton(
                    onClick = { viewModel.fetchCatalog() },
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    Text(
                        if (catalog.error == null) "Fetch catalog" else "Retry fetch",
                        color = HomeTokens.accent,
                    )
                }
            } else {
                val installedIds = externals.map { it.spec.id }.toSet()
                catalog.entries.forEach { entry ->
                    CatalogRow(
                        entry = entry,
                        installed = entry.id in installedIds,
                        installing = catalog.installingId == entry.id,
                        busy = catalog.installingId != null,
                        onInstall = { viewModel.install(entry) },
                        onRemove = { viewModel.removeInstalled(entry.id) },
                    )
                }
            }
            catalog.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.danger,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            catalog.lastResult?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One catalog entry: install (downloads + validates the manifest) or remove. */
@Composable
private fun CatalogRow(
    entry: app.pocketshell.widget.external.WidgetCatalogEntry,
    installed: Boolean,
    installing: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = HomeTokens.textPrimary,
                )
                Text(
                    text = "v" + entry.version + " · " + entry.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
            when {
                installing -> Text(
                    text = "Installing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
                installed -> {
                    Text(
                        text = "Installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.accent,
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onRemove, modifier = Modifier.height(32.dp)) {
                        Text("Remove", color = HomeTokens.danger, fontSize = 11.sp)
                    }
                }
                else -> TextButton(
                    onClick = onInstall,
                    enabled = !busy,
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("Install", color = if (busy) HomeTokens.textDim else HomeTokens.accent)
                }
            }
        }
        if (entry.summary.isNotBlank()) {
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** One configured application: name + summary + reorder + remove. */
@Composable
private fun ConfiguredAppRow(
    name: String,
    summary: String,
    missing: Boolean,
    first: Boolean,
    last: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (missing) HomeTokens.textDim else HomeTokens.textPrimary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
        }
        IconActionButton(
            icon = Icons.Outlined.KeyboardArrowUp,
            label = "Move $name up",
            enabled = !first,
            onClick = onMoveUp,
        )
        IconActionButton(
            icon = Icons.Outlined.KeyboardArrowDown,
            label = "Move $name down",
            enabled = !last,
            onClick = onMoveDown,
        )
        IconActionButton(
            icon = Icons.Outlined.Close,
            label = "Remove $name from Home",
            enabled = true,
            onClick = onRemove,
        )
    }
}

/** One registry application that is not yet on Home. */
@Composable
private fun AvailableAppRow(
    name: String,
    summary: String,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = HomeTokens.textPrimary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
        }
        IconActionButton(
            icon = Icons.Outlined.Add,
            label = "Add $name to Home",
            enabled = true,
            onClick = onAdd,
        )
    }
}

@Composable
private fun IconActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) HomeTokens.textDim else HomeTokens.hairline
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}
