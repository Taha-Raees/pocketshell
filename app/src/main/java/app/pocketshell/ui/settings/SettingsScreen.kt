package app.pocketshell.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import kotlinx.coroutines.launch

/**
 * Settings (brief §25/§M1.3): theme mode, dynamic color, default font size.
 * Persisted via DataStore; changes apply immediately where sensible.
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
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = themeMode == mode,
                    onClick = { onThemeMode(mode) },
                )
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SettingHeader("Dynamic color")
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Switch(
                checked = dynamicColor,
                onCheckedChange = { onDynamicColor(it) },
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
