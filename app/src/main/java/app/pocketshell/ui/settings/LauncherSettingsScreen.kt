package app.pocketshell.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.apps.CommandAppCatalog
import app.pocketshell.companion.CompanionDef
import app.pocketshell.launchers.BuiltInCompanions
import app.pocketshell.launchers.CustomTool
import app.pocketshell.launchers.CustomToolValidation
import app.pocketshell.launchers.LauncherBadges
import app.pocketshell.launchers.LauncherTileIcon
import app.pocketshell.launchers.LauncherViewModel
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightCard
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightSwitch
import app.pocketshell.ui.system.MidnightTextField
import app.pocketshell.ui.theme.TerminalTheme
import kotlinx.coroutines.launch

/**
 * M7.1 P1 — Settings → Home launchers (PART F: ONE management surface).
 *
 * Everything the Home launcher grids need to be manageable lives here:
 *
 *   COMPANIONS  the built-in seeds (show-on-Home switches, restore after
 *               deletion) and the user's custom companions (show/hide +
 *               optional icon); add/edit/delete of companion websites stays
 *               in the EXISTING Companion settings screen (frozen UI, zero
 *               changes) — this page links to it.
 *   CLI TOOLS   the built-in registry launchers (show-on-Home switches)
 *               and the user's custom tools (add / edit / remove / icon).
 *
 * The honesty rule is stated on the page itself: a launcher on Home is not
 * an install claim — PocketShell verifies with the real guest shell when
 * the user taps it.
 */
@Composable
fun LauncherSettingsScreen(
    launcherViewModel: LauncherViewModel,
    companionDefs: List<CompanionDef>,
    onOpenCompanionSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenIds by launcherViewModel.hiddenIds.collectAsStateWithLifecycle()
    val customTools by launcherViewModel.customTools.collectAsStateWithLifecycle()
    val companionIcons by launcherViewModel.companionIcons.collectAsStateWithLifecycle()
    val toolIcons by launcherViewModel.toolIcons.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // null = list mode; "" = adding a new custom tool; otherwise editing id.
    var editingId by remember { mutableStateOf<String?>(null) }
    // Icon picked for the NEW-tool editor (imported once the tool exists).
    var newToolIconUri by remember { mutableStateOf<Uri?>(null) }
    // Icon picker target: (launcherId or null-for-the-new-editor, isCompanion).
    var iconTarget by remember { mutableStateOf<Pair<String?, Boolean>?>(null) }

    val iconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        val target = iconTarget
        iconTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val (id, companion) = target
        if (id == null) {
            newToolIconUri = uri
        } else {
            launcherViewModel.importIcon(id, uri, companion)
        }
    }

    MidnightPageScaffold(title = "Home launchers", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ---- Companions -------------------------------------------------
            MidnightSectionLabel("Companions")
            Spacer(Modifier.height(4.dp))
            MidnightNote(
                "Companion websites open in PocketShell's built-in web " +
                    "canvas. Toggles control the Home grid only — nothing " +
                    "is deleted by hiding.",
            )
            Spacer(Modifier.height(6.dp))
            companionSettingsRows(
                defs = companionDefs,
                hiddenIds = hiddenIds,
                icons = companionIcons,
                onShowChanged = { id, show ->
                    if (show) launcherViewModel.restoreToHome(id) else launcherViewModel.hideFromHome(id)
                },
                onRestore = { seedId ->
                    launcherViewModel.restoreBuiltInCompanion(seedId)
                    launcherViewModel.restoreToHome(seedId)
                },
                onSetIcon = { id -> iconTarget = id to true },
                onClearIcon = { id -> launcherViewModel.clearIcon(id, companion = true) },
            )
            Spacer(Modifier.height(10.dp))
            MidnightQuietButton(
                text = "Add or edit companion websites",
                onClick = onOpenCompanionSettings,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- CLI tools --------------------------------------------------
            MidnightSectionLabel("CLI tools")
            Spacer(Modifier.height(4.dp))
            MidnightNote(
                "Launchers run their command in a real Linux terminal " +
                    "session. A launcher on Home is not an install claim — " +
                    "PocketShell checks with the real guest shell when you " +
                    "tap it.",
            )
            Spacer(Modifier.height(6.dp))

            if (editingId == null) {
                builtInToolRows(
                    hiddenIds = hiddenIds,
                    icons = toolIcons,
                    onShowChanged = { id, show ->
                        if (show) launcherViewModel.restoreToHome(id) else launcherViewModel.hideFromHome(id)
                    },
                )
                customToolRows(
                    tools = customTools,
                    hiddenIds = hiddenIds,
                    icons = toolIcons,
                    onShowChanged = { id, show ->
                        if (show) launcherViewModel.restoreToHome(id) else launcherViewModel.hideFromHome(id)
                    },
                    onEdit = { editingId = it },
                    onRemove = { launcherViewModel.removeCustomTool(it) },
                    onSetIcon = { id -> iconTarget = id to false },
                    onClearIcon = { id -> launcherViewModel.clearIcon(id, companion = false) },
                )
                Spacer(Modifier.height(10.dp))
                MidnightFilledButton(
                    text = "+ Add Custom Tool",
                    onClick = { editingId = "" },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                CustomToolEditor(
                    editing = customTools.firstOrNull { it.id == editingId },
                    pendingIconUri = newToolIconUri.takeIf { editingId.isNullOrEmpty() },
                    onPickIcon = { iconTarget = (editingId?.takeIf { it.isNotEmpty() }) to false },
                    onClearPendingIcon = { newToolIconUri = null },
                    onSave = { name, command, done ->
                        scope.launch {
                            if (editingId.isNullOrEmpty()) {
                                val tool = launcherViewModel.addCustomTool(name, command)
                                if (tool != null) {
                                    newToolIconUri?.let { uri ->
                                        launcherViewModel.importIcon(tool.id, uri, companion = false)
                                        newToolIconUri = null
                                    }
                                    editingId = null
                                    done(true)
                                } else {
                                    done(false)
                                }
                            } else {
                                val ok = launcherViewModel.updateCustomTool(editingId ?: "", name, command)
                                if (ok) editingId = null
                                done(ok)
                            }
                        }
                    },
                    onCancel = {
                        editingId = null
                        newToolIconUri = null
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------ rows

/**
 * THE launcher settings row (the P2 brief's slot contract, ONE shared
 * composable so it cannot drift):
 *
 *   Row
 *   ├── icon (fixed size)
 *   ├── Spacer
 *   ├── text column — Modifier.weight(1f), ALWAYS the remaining width
 *   │     ├── title
 *   │     └── subtitle (wraps naturally when genuinely necessary)
 *   ├── optional action slot (intrinsically sized, e.g. a text action)
 *   └── optional control slot (fixed content size, e.g. the switch)
 *
 * The M7.1 P2 screenshot bug is structurally impossible through this
 * composable: page-level expanding buttons (MidnightQuietButton and
 * MidnightFilledButton hard-fill their width from the inside) never sit in
 * a row slot here, so the weighted text column can never be starved to a
 * one-character-per-line column. LauncherRowLayoutTest pins that rule.
 */
@Composable
private fun LauncherSettingRow(
    launcherId: String,
    iconFile: String?,
    badge: String,
    title: String,
    subtitle: String,
    subtitleMono: Boolean = false,
    dimmed: Boolean = false,
    action: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    control: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LauncherTileIcon(launcherId = launcherId, iconFile = iconFile, badge = badge, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dimmed) HomeTokens.textDim else HomeTokens.textPrimary,
            )
            Text(
                text = subtitle,
                style = if (subtitleMono) {
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = TerminalTheme.mono,
                        fontSize = 12.sp,
                    )
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = HomeTokens.textDim,
            )
        }
        action?.invoke(this)
        control?.invoke(this)
    }
}

@Composable
private fun companionSettingsRows(
    defs: List<CompanionDef>,
    hiddenIds: Set<String>,
    icons: Map<String, String>,
    onShowChanged: (String, Boolean) -> Unit,
    onRestore: (String) -> Unit,
    onSetIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    val missingSeeds = remember(defs) {
        BuiltInCompanions.SEEDS.filter { seed -> defs.none { it.id == seed.id } }
    }
    // ONE badge pass over every name this section can show — def rows AND
    // deleted-seed restore rows share one collision space, so a seed whose
    // definition was deleted can never render the same badge as an existing
    // definition (the P1 behavior assigned the restore row in isolation).
    val badges = remember(defs, missingSeeds) {
        LauncherBadges.assign(defs.map { it.name } + missingSeeds.map { it.name })
    }
    val byId = remember(defs) { defs.associateBy { it.id } }
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        BuiltInCompanions.SEEDS.forEach { seed ->
            val def = byId[seed.id]
            if (def == null) {
                // The seed definition was deleted — offer the honest restore
                // as a compact text action (P2 fix: never a page-level button
                // inside a row).
                val seedIndex = missingSeeds.indexOfFirst { it.id == seed.id }
                LauncherSettingRow(
                    launcherId = seed.id,
                    iconFile = icons[seed.id],
                    badge = badges[defs.size + seedIndex],
                    title = seed.name,
                    subtitle = "Not on this install",
                    dimmed = true,
                    action = {
                        RowTextAction(text = "Restore", onClick = { onRestore(seed.id) })
                        Spacer(Modifier.width(8.dp))
                    },
                )
            } else {
                CompanionRow(
                    def = def,
                    badge = badges[defs.indexOf(def)],
                    iconFile = icons[def.id],
                    visible = def.id !in hiddenIds,
                    onShowChanged = onShowChanged,
                    onSetIcon = onSetIcon,
                    onClearIcon = onClearIcon,
                )
            }
        }
        defs.filter { it.id !in BuiltInCompanions.SEED_IDS }.forEach { def ->
            CompanionRow(
                def = def,
                badge = badges[defs.indexOf(def)],
                iconFile = icons[def.id],
                visible = def.id !in hiddenIds,
                onShowChanged = onShowChanged,
                onSetIcon = onSetIcon,
                onClearIcon = onClearIcon,
            )
        }
    }
}

@Composable
private fun CompanionRow(
    def: CompanionDef,
    badge: String,
    iconFile: String?,
    visible: Boolean,
    onShowChanged: (String, Boolean) -> Unit,
    onSetIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    LauncherSettingRow(
        launcherId = def.id,
        iconFile = iconFile,
        badge = badge,
        title = def.name,
        subtitle = def.url,
        action = {
            RowTextAction(
                text = if (iconFile == null) "Icon" else "Clear icon",
                onClick = { if (iconFile == null) onSetIcon(def.id) else onClearIcon(def.id) },
            )
            Spacer(Modifier.width(8.dp))
        },
        control = {
            MidnightSwitch(checked = visible, onCheckedChange = { onShowChanged(def.id, it) })
        },
    )
}

@Composable
private fun builtInToolRows(
    hiddenIds: Set<String>,
    icons: Map<String, String>,
    onShowChanged: (String, Boolean) -> Unit,
) {
    val badges = remember {
        LauncherBadges.assign(CommandAppCatalog.registry.map { it.displayName })
    }
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        CommandAppCatalog.registry.forEachIndexed { index, app ->
            LauncherSettingRow(
                launcherId = app.id,
                iconFile = icons[app.id],
                badge = badges[index],
                title = app.displayName,
                subtitle = app.launchCommand.joinToString(" "),
                subtitleMono = true,
                control = {
                    MidnightSwitch(
                        checked = app.id !in hiddenIds,
                        onCheckedChange = { onShowChanged(app.id, it) },
                    )
                },
            )
        }
    }
}

@Composable
private fun customToolRows(
    tools: List<CustomTool>,
    hiddenIds: Set<String>,
    icons: Map<String, String>,
    onShowChanged: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSetIcon: (String) -> Unit,
    onClearIcon: (String) -> Unit,
) {
    if (tools.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        tools.forEach { tool ->
            MidnightCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LauncherTileIcon(
                            launcherId = tool.id,
                            iconFile = icons[tool.id],
                            badge = tool.name.take(1),
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = HomeTokens.textPrimary,
                            )
                            Text(
                                text = tool.command,
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textDim,
                            )
                        }
                        MidnightSwitch(checked = tool.id !in hiddenIds, onCheckedChange = { onShowChanged(tool.id, it) })
                    }
                    Row(modifier = Modifier.padding(top = 2.dp)) {
                        RowTextAction("Icon", onClick = { onSetIcon(tool.id) })
                        if (icons[tool.id] != null) {
                            RowTextAction("Clear icon", onClick = { onClearIcon(tool.id) })
                        }
                        RowTextAction("Edit", onClick = { onEdit(tool.id) })
                        RowTextAction("Remove", onClick = { onRemove(tool.id) }, destructive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowTextAction(text: String, onClick: () -> Unit, destructive: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (destructive) HomeTokens.danger else HomeTokens.accent,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------------- editor

@Composable
private fun CustomToolEditor(
    editing: CustomTool?,
    pendingIconUri: Uri?,
    onPickIcon: () -> Unit,
    onClearPendingIcon: () -> Unit,
    onSave: (String, String, (Boolean) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var command by remember(editing?.id) { mutableStateOf(editing?.command ?: "") }
    var error by remember(editing?.id) { mutableStateOf(false) }

    MidnightSectionLabel(if (editing == null) "New custom tool" else "Edit custom tool")
    Spacer(Modifier.height(8.dp))
    MidnightTextField(
        value = name,
        onValueChange = { name = it; error = false },
        placeholder = "Name — e.g. My Tool",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(8.dp))
    MidnightTextField(
        value = command,
        onValueChange = { command = it; error = false },
        placeholder = "Command — e.g. my-tool --serve",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Icon (optional)",
                style = MaterialTheme.typography.bodyLarge,
                color = HomeTokens.textPrimary,
            )
            Text(
                text = if (pendingIconUri != null) "Image selected — copied into PocketShell storage on save"
                else "Choose an image from this device; without one a letter badge is used",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
        }
        if (pendingIconUri != null) {
            RowTextAction("Undo", onClick = onClearPendingIcon)
        }
        RowTextAction("Choose from device", onClick = onPickIcon)
    }
    if (error) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "A name and a single-line command are required " +
                "(max ${CustomToolValidation.MAX_COMMAND_LENGTH} characters).",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.danger,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    MidnightFilledButton(
        text = if (editing == null) "Add to Home" else "Save",
        onClick = { onSave(name, command) { ok -> error = !ok } },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(6.dp))
    MidnightQuietButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
