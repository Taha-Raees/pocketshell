package app.pocketshell.ui.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.companion.CompanionDef
import app.pocketshell.companion.CompanionTemplates
import app.pocketshell.companion.CompanionViewModel
import app.pocketshell.companion.CompanionWebPool
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightCard
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.system.MidnightNote
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightQuietButton
import app.pocketshell.ui.system.MidnightRadioRow
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightTextField
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Phase 4 — Settings → Companions (docs/PHASE-4-COMPANION-DESIGN.md §4.2).
 *
 * The user manages Companion definitions here: add / edit / delete /
 * default / clear web data. A Companion is Name + URL — the editor is an
 * INLINE Midnight section (the app has no dialog idiom and needs none):
 * the list gives way to the editor and returns on Save/Cancel.
 *
 * Quick-add templates are Name+URL pre-fills only (contract §4): fully
 * editable, zero runtime special-casing.
 */
@Composable
fun CompanionSettingsScreen(
    viewModel: CompanionViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defs by viewModel.defs.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultId.collectAsStateWithLifecycle()

    // null = list mode; "" = adding a new Companion; otherwise editing id.
    var editingId by remember { mutableStateOf<String?>(null) }
    // Quick-add template pre-fill for the "" (new) editor mode.
    var prefill by remember { mutableStateOf<Pair<String, String>?>(null) }

    MidnightPageScaffold(title = "Companions", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            if (editingId == null) {
                CompanionListSection(
                    defs = defs,
                    onEdit = { editingId = it },
                    onAdd = { editingId = "" },
                    onQuickAdd = { template ->
                        prefill = template.name to template.host
                        editingId = ""
                    },
                )
            } else {
                CompanionEditorSection(
                    editing = defs.firstOrNull { it.id == editingId },
                    prefill = if (editingId.isNullOrEmpty()) prefill else null,
                    onSave = { name, url, done ->
                        val action = if (editingId.isNullOrEmpty()) {
                            viewModel::addCompanion
                        } else {
                            { n: String, u: String, r: (Boolean) -> Unit ->
                                viewModel.updateCompanion(editingId ?: "", n, u, r)
                            }
                        }
                        action(name, url) { ok ->
                            if (ok) editingId = null
                            done(ok)
                        }
                    },
                    onDelete = { id ->
                        viewModel.deleteCompanion(id)
                        editingId = null
                    },
                    onCancel = { editingId = null },
                    showDelete = !editingId.isNullOrEmpty(),
                )
            }

            if (defs.isNotEmpty()) {
                MidnightSectionDivider()
                Spacer(Modifier.height(12.dp))
                MidnightSectionLabel("Default Companion")
                Spacer(Modifier.height(4.dp))
                defs.forEach { def ->
                    MidnightRadioRow(
                        selected = defaultId == def.id,
                        label = def.name,
                        sublabel = def.url,
                        onClick = { viewModel.setDefault(def.id) },
                    )
                }
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))
            MidnightSectionLabel("Web data")
            Spacer(Modifier.height(4.dp))
            MidnightNote(
                "Logins, cookies and site storage live in PocketShell's " +
                    "private web storage. Clearing signs you out everywhere.",
            )
            Spacer(Modifier.height(8.dp))
            MidnightQuietButton(
                text = "Clear web data",
                destructive = true,
                onClick = { viewModel.clearWebData() },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CompanionListSection(
    defs: List<CompanionDef>,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onQuickAdd: (CompanionTemplates.Template) -> Unit,
) {
    MidnightSectionLabel("Your Companions")
    Spacer(Modifier.height(6.dp))
    if (defs.isEmpty()) {
        MidnightNote("Add a website you use while working.")
        Spacer(Modifier.height(10.dp))
        MidnightSectionLabel("Quick add")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CompanionTemplates.ALL.forEach { template ->
                MidnightQuietButton(
                    text = "${template.name}  ·  ${template.host}",
                    onClick = { onQuickAdd(template) },
                    enabled = true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        MidnightFilledButton(
            text = "+ Add Companion",
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            defs.forEach { def ->
                MidnightCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(HomeTokens.chipRadius))
                            .clickable(role = Role.Button, onClickLabel = "Edit ${def.name}") {
                                onEdit(def.id)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = def.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = HomeTokens.textPrimary,
                            )
                            Text(
                                text = def.url,
                                fontFamily = TerminalTheme.mono,
                                fontSize = 12.sp,
                                color = HomeTokens.textDim,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MidnightQuietButton(
            text = "+ Add Companion",
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun CompanionEditorSection(
    editing: CompanionDef?,
    prefill: Pair<String, String>?,
    onSave: (String, String, (Boolean) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: () -> Unit,
    showDelete: Boolean,
) {
    var name by remember(editing?.id, prefill) { mutableStateOf(editing?.name ?: prefill?.first ?: "") }
    var url by remember(editing?.id, prefill) { mutableStateOf(editing?.url ?: prefill?.second ?: "") }
    var error by remember(editing?.id, prefill) { mutableStateOf(false) }

    MidnightSectionLabel(if (editing == null) "New Companion" else "Edit Companion")
    Spacer(Modifier.height(8.dp))
    MidnightTextField(
        value = name,
        onValueChange = { name = it; error = false },
        placeholder = "Name — e.g. ChatGPT",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(8.dp))
    MidnightTextField(
        value = url,
        onValueChange = { url = it; error = false },
        placeholder = "Address — e.g. chatgpt.com",
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    if (error) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "A name and a valid website address are required.",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.danger,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    MidnightFilledButton(
        text = "Save",
        onClick = { onSave(name, url) { ok -> error = !ok } },
        modifier = Modifier.padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(6.dp))
    if (showDelete && editing != null) {
        MidnightQuietButton(
            text = "Delete",
            destructive = true,
            onClick = { onDelete(editing.id) },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
    MidnightQuietButton(
        text = "Cancel",
        onClick = onCancel,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}
