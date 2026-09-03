package app.pocketshell.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.components.PSScreenHeader
import app.pocketshell.ui.components.PSSectionLabel
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import app.pocketshell.ui.theme.PSSpacing

/**
 * Settings (docs/UI-REDESIGN.md §10/§12): Appearance, AI Assistant
 * (OpenRouter configuration), About. Persisted via DataStore; changes apply
 * immediately where sensible.
 *
 * Honesty rules for the API key: entered once, shown masked afterwards,
 * never logged, never rendered into Diagnostics. The section states plainly
 * that storage is the app's private DataStore and OS-keystore-backed
 * storage is a planned upgrade. The assistant chat itself does not exist
 * yet — the section says so.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    defaultFontSize: Int,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    openRouterApiKey: String? = null,
    openRouterModel: String? = null,
    onSaveApiKey: (String?) -> Unit = {},
    onSaveModel: (String?) -> Unit = {},
) {
    // Local draft while the user drags the slider; persisted on change.
    var fontSizeDraft by remember(defaultFontSize) { mutableFloatStateOf(defaultFontSize.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        PSScreenHeader(
            title = "Settings",
            subtitle = "Make PocketShell yours",
            onMenu = onMenu,
        )

        Column(
            modifier = Modifier.padding(horizontal = PSSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(PSSpacing.lg),
        ) {
            // ---- Appearance --------------------------------------------
            SettingsCard(title = "Appearance") {
                listOf(
                    ThemeMode.SYSTEM to "System (follow device)",
                    ThemeMode.LIGHT to "Light",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.AMOLED to "AMOLED (pure black)",
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeMode(mode) },
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic color", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Wallpaper-based colors on Android 12+; " +
                                "falls back to the PocketShell scheme elsewhere",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { onDynamicColor(it) },
                    )
                }
                Text(
                    text = "Terminal font size: ${fontSizeDraft.toInt()}pt — " +
                        "pinch the terminal to adjust per session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = fontSizeDraft,
                    onValueChange = { fontSizeDraft = it },
                    onValueChangeFinished = { onFontSize(fontSizeDraft.toInt()) },
                    valueRange = SettingsRepository.MIN_FONT_SIZE.toFloat()
                        ..SettingsRepository.MAX_FONT_SIZE.toFloat(),
                    steps = (SettingsRepository.MAX_FONT_SIZE - SettingsRepository.MIN_FONT_SIZE) / 2 - 1,
                )
            }

            // ---- AI Assistant (OpenRouter) ------------------------------
            AiAssistantSection(
                storedKey = openRouterApiKey,
                storedModel = openRouterModel,
                onSaveApiKey = onSaveApiKey,
                onSaveModel = onSaveModel,
            )

            // ---- About ---------------------------------------------------
            SettingsCard(title = "About") {
                SettingFact("App", "PocketShell")
                SettingFact(
                    "Identity",
                    "A polished personal Linux environment on Android",
                )
                SettingFact(
                    "Scope",
                    "Terminal, Linux guest (Alpine via proot), packages, " +
                        "launchable apps — no more, no less",
                )
            }
            Spacer(Modifier.height(PSSpacing.xl))
        }
    }
}

/**
 * OpenRouter configuration (docs/UI-REDESIGN.md §10). Real, working storage;
 * honest statement about what exists and what is still coming. The key field
 * starts empty even when a key is stored (masked reminder only) — the key is
 * never re-rendered in clear text.
 */
@Composable
private fun AiAssistantSection(
    storedKey: String?,
    storedModel: String?,
    onSaveApiKey: (String?) -> Unit,
    onSaveModel: (String?) -> Unit,
) {
    var keyDraft by remember { mutableStateOf("") }
    var modelDraft by remember(storedModel) { mutableStateOf(storedModel ?: "") }
    var reveal by remember { mutableStateOf(false) }

    SettingsCard(title = "AI Assistant (OpenRouter)") {
        Text(
            text = "Configure the built-in assistant ahead of its arrival. " +
                "The assistant chat itself is not part of the app yet — this " +
                "screen stores your configuration for when it lands.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val masked = SettingsRepository.maskKey(storedKey)
        if (masked != null) {
            Text(
                text = "A key is stored ($masked).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            label = { Text(if (masked == null) "OpenRouter API key" else "Replace API key") },
            placeholder = { Text("sk-or-…") },
            visualTransformation = if (reveal) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "Hide" else "Show")
                }
            },
        )
        OutlinedTextField(
            value = modelDraft,
            onValueChange = { modelDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            label = { Text("Model ID (entered manually)") },
            placeholder = { Text("e.g. vendor/model-name") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (masked != null) {
                TextButton(onClick = {
                    keyDraft = ""
                    onSaveApiKey(null)
                }) { Text("Remove key") }
            }
            TextButton(onClick = {
                onSaveApiKey(keyDraft.takeIf { it.isNotBlank() })
                keyDraft = ""
                onSaveModel(modelDraft.takeIf { it.isNotBlank() })
            }) { Text("Save") }
        }
        Text(
            text = "Storage: the app's private settings storage. Not shown in " +
                "Diagnostics, never written to logs. OS-keystore-backed storage " +
                "is a planned upgrade.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column {
        PSSectionLabel(title)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(PSSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PSSpacing.sm),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingFact(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = PSSpacing.md),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
