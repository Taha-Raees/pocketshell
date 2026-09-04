package app.pocketshell.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightRadioRow
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightSwitch
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Settings (brief §25/§M1.3): theme mode, dynamic color, default font size.
 * Persisted via DataStore; changes apply immediately where sensible.
 *
 * Phase 3.5 (docs/PHASE-3.5-DESIGN.md §3): the page joins the Midnight
 * Sapphire system — the same canvas, mono wayfinding and hairline rhythm as
 * Home — while keeping every 3.4 behavior contract: the whole row is the
 * touch target (≥48dp), the radio dot and switch only render state, and the
 * font slider persists on release.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    defaultFontSize: Int,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onOpenCompanions: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local draft while the user drags the slider; persisted on release.
    var fontSizeDraft by remember(defaultFontSize) { mutableFloatStateOf(defaultFontSize.toFloat()) }

    MidnightPageScaffold(title = "Settings", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            MidnightSectionLabel("Theme")
            Spacer(Modifier.height(4.dp))
            listOf(
                ThemeMode.SYSTEM to "System (follow device)",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
                ThemeMode.AMOLED to "AMOLED (pure black)",
            ).forEach { (mode, label) ->
                MidnightRadioRow(
                    selected = themeMode == mode,
                    label = label,
                    onClick = { onThemeMode(mode) },
                )
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))

            MidnightSectionLabel("Dynamic color")
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Use wallpaper-based colors",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HomeTokens.textPrimary,
                    )
                    Text(
                        "Android 12+; falls back to the PocketShell scheme elsewhere",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
                Spacer(Modifier.padding(4.dp))
                MidnightSwitch(checked = dynamicColor, onCheckedChange = onDynamicColor)
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))

            MidnightSectionLabel("Terminal font size")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Default: ${fontSizeDraft.toInt()} pt",
                fontFamily = TerminalTheme.mono,
                fontSize = 14.sp,
                color = HomeTokens.accent,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Pinch the terminal to adjust per session",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Slider(
                value = fontSizeDraft,
                onValueChange = { fontSizeDraft = it },
                onValueChangeFinished = { onFontSize(fontSizeDraft.toInt()) },
                valueRange = SettingsRepository.MIN_FONT_SIZE.toFloat()..SettingsRepository.MAX_FONT_SIZE.toFloat(),
                steps = (SettingsRepository.MAX_FONT_SIZE - SettingsRepository.MIN_FONT_SIZE) / 2 - 1,
                colors = SliderDefaults.colors(
                    thumbColor = HomeTokens.accent,
                    activeTrackColor = HomeTokens.accent,
                    inactiveTrackColor = HomeTokens.surfaceApp,
                ),
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(12.dp))

            // Phase 4 — Companion definitions (docs/PHASE-4-COMPANION-DESIGN.md).
            MidnightSectionLabel("Companion")
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "Manage Companions") {
                        onOpenCompanions()
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Companion websites",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HomeTokens.textPrimary,
                    )
                    Text(
                        "Your web workspaces — ChatGPT, GitHub, anything",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
                Text(
                    text = "›",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 18.sp,
                    color = HomeTokens.textDim,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
