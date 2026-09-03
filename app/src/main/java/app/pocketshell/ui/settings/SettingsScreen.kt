package app.pocketshell.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import kotlinx.coroutines.launch

/**
 * Settings (brief §25/§M1.3): theme mode, dynamic color, default font size.
 * Persisted via DataStore; changes apply immediately where sensible.
 *
 * Phase 3.4 (docs/PHASE-3.4-DESIGN.md §4): the visual language stays the
 * app-theme Material look (the 3.3 §10 decision), but every selectable row is
 * a whole-row touch target — OS idiom, ≥48dp — not a 24dp radio dot or a
 * 40dp switch floating at the end of an inert label.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    defaultFontSize: Int,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Local draft while the user drags the slider; persisted on change.
    var fontSizeDraft by remember(defaultFontSize) { mutableFloatStateOf(defaultFontSize.toFloat()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        SettingHeader("Theme")
        listOf(
            ThemeMode.SYSTEM to "System (follow device)",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
            ThemeMode.AMOLED to "AMOLED (pure black)",
        ).forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        role = Role.RadioButton,
                        onClickLabel = "Select $label",
                    ) { onThemeMode(mode) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // onClick = null: the ROW is the touch target and carries the
                // RadioButton role; the dot is only the state renderer.
                RadioButton(
                    selected = themeMode == mode,
                    onClick = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingHeader("Dynamic color")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Switch) { onDynamicColor(!dynamicColor) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Use wallpaper-based colors", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Android 12+; falls back to the PocketShell scheme elsewhere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // onClick = null: the row toggles (Role.Switch); the switch only
            // renders the state.
            Switch(
                checked = dynamicColor,
                onCheckedChange = null,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingHeader("Terminal font size")
        Text(
            text = "Default: ${fontSizeDraft.toInt()} pt — pinch the terminal to adjust per session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = fontSizeDraft,
            onValueChange = { fontSizeDraft = it },
            onValueChangeFinished = { onFontSize(fontSizeDraft.toInt()) },
            valueRange = SettingsRepository.MIN_FONT_SIZE.toFloat()..SettingsRepository.MAX_FONT_SIZE.toFloat(),
            steps = (SettingsRepository.MAX_FONT_SIZE - SettingsRepository.MIN_FONT_SIZE) / 2 - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun SettingHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
