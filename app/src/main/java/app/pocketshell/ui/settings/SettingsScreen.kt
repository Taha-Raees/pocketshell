package app.pocketshell.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightSwitch
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.ui.theme.ThemeCatalog

/**
 * Settings — the Control Center hub (Settings/Control Center task §1).
 *
 * The page is five clearly-ranked REGIONS, not a flat preference list:
 *
 *   HOME        → the Home widget slots (M8 widget system)
 *   APPEARANCE  → the Appearance page (mode, identity, density, previews)
 *   COMPANIONS  → the companion websites management area
 *   CLI TOOLS   → the Home launcher management area
 *   SYSTEM      → the existing device-side settings (terminal font size,
 *                 on-screen keyboard), preserved inline
 *
 * Everything persists via DataStore; nothing here is invented — every
 * control is real and wired. Count summaries make the management rows read
 * like a control center, not an entry list.
 */
@Composable
fun SettingsScreen(
    theme: AppTheme,
    themeMode: ThemeMode,
    textScale: app.pocketshell.settings.TextScale,
    defaultFontSize: Int,
    onscreenKeyboardEnabled: Boolean,
    companionCount: Int,
    toolCount: Int,
    hiddenLauncherCount: Int,
    /** M8 — the live slot summary ("Terminal · Servers") for the Home row. */
    homeWidgetsSubtitle: String,
    onOpenHomeWidgets: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCompanions: () -> Unit,
    onOpenLaunchers: () -> Unit,
    onFontSize: (Int) -> Unit,
    onOnscreenKeyboardEnabled: (Boolean) -> Unit,
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
            Spacer(Modifier.height(6.dp))

            // ---- Home (M8 widget system) --------------------------------------
            MidnightSectionLabel("Home")
            Spacer(Modifier.height(4.dp))
            ControlCenterRow(
                title = "Home widgets",
                subtitle = homeWidgetsSubtitle,
                onClick = onOpenHomeWidgets,
                onClickLabel = "Manage Home widgets",
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Appearance ---------------------------------------------------
            MidnightSectionLabel("Appearance")
            Spacer(Modifier.height(4.dp))
            ControlCenterRow(
                title = "Appearance",
                subtitle = "${theme.label} · ${modeLabel(themeMode)} · ${textScale.label} text",
                onClick = onOpenAppearance,
                onClickLabel = "Open Appearance",
                leading = { PaletteStrip(theme, themeModeIsLight(themeMode)) },
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Companions ---------------------------------------------------
            MidnightSectionLabel("Companions")
            Spacer(Modifier.height(4.dp))
            ControlCenterRow(
                title = "Companion websites",
                subtitle = "$companionCount configured — add, edit, set default",
                onClick = onOpenCompanions,
                onClickLabel = "Manage Companions",
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- CLI tools ----------------------------------------------------
            MidnightSectionLabel("CLI tools")
            Spacer(Modifier.height(4.dp))
            ControlCenterRow(
                title = "Home launchers",
                subtitle = "$toolCount tools" +
                    if (hiddenLauncherCount > 0) " · $hiddenLauncherCount hidden" else "",
                onClick = onOpenLaunchers,
                onClickLabel = "Manage Home launchers",
            )

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- System (existing settings, preserved) -------------------------
            MidnightSectionLabel("System")
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Default font size: ${fontSizeDraft.toInt()} pt",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 14.sp,
                        color = HomeTokens.accent,
                    )
                    Text(
                        text = "Terminal text — pinch the terminal to adjust per session",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
            }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "On-screen keyboard",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HomeTokens.textPrimary,
                    )
                    Text(
                        "The PocketShell keyboard, on every screen. While an " +
                            "external keyboard is connected it hides automatically " +
                            "and returns when you unplug it. Off keeps it hidden " +
                            "— it is never forced back on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
                Spacer(Modifier.padding(4.dp))
                MidnightSwitch(
                    checked = onscreenKeyboardEnabled,
                    onCheckedChange = onOnscreenKeyboardEnabled,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun modeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun themeModeIsLight(mode: ThemeMode): Boolean = mode == ThemeMode.LIGHT

/** A live three-swatch strip of the active identity (chrome / key / accent). */
@Composable
private fun PaletteStrip(theme: AppTheme, light: Boolean) {
    val v = ThemeCatalog.variant(theme, light)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(v.chrome, v.key, v.accent).forEach { argb ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(argb)),
            )
        }
    }
}

/**
 * The Control Center entry row: title + honest subtitle, whole-row touch
 * target, mono chevron, optional leading live widget. A row is NOT a card
 * at rest (the 3.3 §4 rule) — the banner tone appears only while pressed
 * would, but we keep rest flat and let the chevron + subtitle carry it.
 */
@Composable
private fun ControlCenterRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onClickLabel: String,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = HomeTokens.textPrimary,
            )
            Text(
                subtitle,
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
}
