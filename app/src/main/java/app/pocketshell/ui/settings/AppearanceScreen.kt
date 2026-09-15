package app.pocketshell.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.launchers.LauncherTileIcon
import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.CardSize
import app.pocketshell.settings.HomeGridDensity
import app.pocketshell.settings.IconColumns
import app.pocketshell.settings.IconSize
import app.pocketshell.settings.ThemeMode
import app.pocketshell.settings.TextScale
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightPageScaffold
import app.pocketshell.ui.system.MidnightChipRow
import app.pocketshell.ui.system.MidnightRadioRow
import app.pocketshell.ui.system.MidnightSectionDivider
import app.pocketshell.ui.system.MidnightSectionLabel
import app.pocketshell.ui.system.MidnightSwitch
import app.pocketshell.ui.theme.LocalAuroraPhase
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.ui.theme.ThemeCatalog
import app.pocketshell.ui.theme.auroraEdge

/**
 * Appearance (Control Center task §2/§5/§6) — the THEME × MODE page.
 *
 *   MODE   System / Light / Dark — the top-level selector; AMOLED is gone
 *          by design. Every identity resolves through the SAME mode.
 *   THEME  the visual identity, one preview card per identity — always
 *          previewed in the CURRENT mode so the pair reads as one choice.
 *   DENSITY text / icons / cards / icons-per-row, each a chip row of
 *          discrete presets with a live preview beside it.
 *
 * Everything here persists through the one Settings DataStore and applies
 * immediately (the token swap runs before the next frame).
 */
@Composable
fun AppearanceScreen(
    theme: AppTheme,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    textScale: TextScale,
    iconSize: IconSize,
    cardSize: CardSize,
    iconColumns: IconColumns,
    onTheme: (AppTheme) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onTextScale: (TextScale) -> Unit,
    onIconSize: (IconSize) -> Unit,
    onCardSize: (CardSize) -> Unit,
    onIconColumns: (IconColumns) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MidnightPageScaffold(title = "Appearance", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(6.dp))

            // ---- Mode: System / Light / Dark (AMOLED is not a mode) --------
            MidnightSectionLabel("Mode")
            Spacer(Modifier.height(2.dp))
            ThemeMode.entries.forEach { mode ->
                MidnightRadioRow(
                    selected = themeMode == mode,
                    label = mode.label,
                    onClick = { onThemeMode(mode) },
                )
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Theme identities (previewed in the CURRENT mode) ----------
            MidnightSectionLabel("Theme")
            Spacer(Modifier.height(8.dp))
            val light = themeModeIsLight(themeMode)
            AppTheme.entries.chunked(2).forEach { rowThemes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowThemes.forEach { identity ->
                        ThemePreviewCard(
                            theme = identity,
                            selected = theme == identity,
                            light = light,
                            modifier = Modifier.weight(1f),
                            onClick = { onTheme(identity) },
                        )
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Text scaling ----------------------------------------------
            MidnightSectionLabel("Text")
            Spacer(Modifier.height(8.dp))
            MidnightChipRow(
                options = TextScale.entries.toList(),
                label = { it.label },
                selected = textScale,
                onSelect = onTextScale,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextScale.entries.forEach { scale ->
                val active = scale == textScale
                Text(
                    text = "Terminal-ready — ${scale.label} text on the workstation",
                    fontSize = (13 * scale.factor).sp,
                    color = if (active) HomeTokens.textPrimary else HomeTokens.textDim,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Icon size ---------------------------------------------------
            MidnightSectionLabel("Icons")
            Spacer(Modifier.height(8.dp))
            MidnightChipRow(
                options = IconSize.entries.toList(),
                label = { it.label },
                selected = iconSize,
                onSelect = onIconSize,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconSize.entries.forEach { size ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LauncherTileIcon(
                            launcherId = "appearance-preview",
                            iconFile = null,
                            badge = "PS",
                            size = size.tileDp.dp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${size.tileDp}dp",
                            fontFamily = TerminalTheme.mono,
                            fontSize = 10.sp,
                            color = if (size == iconSize) HomeTokens.accent else HomeTokens.textDim,
                        )
                    }
                }
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Card size ---------------------------------------------------
            MidnightSectionLabel("Cards")
            Spacer(Modifier.height(8.dp))
            MidnightChipRow(
                options = CardSize.entries.toList(),
                label = { it.label },
                selected = cardSize,
                onSelect = onCardSize,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                CardSize.entries.forEach { size ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height((56 * size.scale).dp)
                                .clip(RoundedCornerShape(HomeTokens.chipRadius))
                                .background(HomeTokens.surfaceEnv)
                                .border(1.dp, HomeTokens.hairline, RoundedCornerShape(HomeTokens.chipRadius)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Preview",
                                fontFamily = TerminalTheme.mono,
                                fontSize = 11.sp,
                                color = if (size == cardSize) HomeTokens.accent else HomeTokens.textDim,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = size.label,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 10.sp,
                            color = if (size == cardSize) HomeTokens.accent else HomeTokens.textDim,
                        )
                    }
                }
            }

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Layout density: icons per row --------------------------------
            MidnightSectionLabel("Layout")
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Icons per row — Auto fills your screen comfortably; " +
                        "a chosen 2–6 is used whenever it fits and clamped when " +
                        "it can't.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
            }
            Spacer(Modifier.height(4.dp))
            MidnightChipRow(
                options = IconColumns.entries.toList(),
                label = { it.label },
                selected = iconColumns,
                onSelect = onIconColumns,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            GridPreview(iconColumns)

            MidnightSectionDivider()
            Spacer(Modifier.height(10.dp))

            // ---- Dynamic color (existing setting, appearance-scoped) ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Dynamic color",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HomeTokens.textPrimary,
                    )
                    Text(
                        "Wallpaper-based colors, Android 12+ — replaces the app " +
                            "palette but never the terminal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HomeTokens.textDim,
                    )
                }
                Spacer(Modifier.padding(4.dp))
                MidnightSwitch(checked = dynamicColor, onCheckedChange = onDynamicColor)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun themeModeIsLight(mode: ThemeMode): Boolean = mode == ThemeMode.LIGHT

/**
 * One theme identity card: a LIVE palette preview (the identity's actual
 * screen/chrome/accent/text values for the current mode) + its name.
 * Selected = the accent hairline; the Aurora identity additionally carries
 * the circulating aurora edge — a preview of exactly what the theme does.
 */
@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    selected: Boolean,
    light: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val v = ThemeCatalog.variant(theme, light)
    val screenBg = Color(v.screenBg)
    val chrome = Color(v.chrome)
    val key = Color(v.key)
    val accent = Color(v.accent)
    val text = Color(v.textPrimary)
    val dim = Color(v.textDim)
    val auroraPhase = LocalAuroraPhase.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HomeTokens.surfaceEnv)
            .then(
                if (selected) {
                    Modifier.border(1.dp, HomeTokens.accent, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .auroraEdge(
                enabled = selected && theme.aurora,
                phase = auroraPhase,
                cornerRadius = 14.dp,
            )
            .clickable(role = Role.Button, onClickLabel = "Theme ${theme.label}") { onClick() }
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(screenBg)
                .padding(8.dp),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwatchDot(chrome)
                    SwatchDot(key)
                    SwatchDot(accent)
                }
                Spacer(Modifier.height(8.dp))
                Text("Ag →_", fontFamily = TerminalTheme.mono, fontSize = 15.sp, color = text)
                Spacer(Modifier.height(2.dp))
                Text("workstation", fontFamily = TerminalTheme.mono, fontSize = 10.sp, color = dim)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            fontFamily = TerminalTheme.mono,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) HomeTokens.accent else HomeTokens.textPrimary,
        )
    }
}

@Composable
private fun SwatchDot(color: Color) {
    Box(
        modifier = Modifier
            .height(12.dp)
            .width(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

/** The icons-per-row preview: the effective count, drawn as launcher tiles. */
@Composable
private fun GridPreview(preference: IconColumns) {
    val columns = HomeGridDensity.effectiveColumns(preference, availableWidthDp = 380f, iconTileDp = 34)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(columns) { index ->
            LauncherTileIcon(
                launcherId = "appearance-preview",
                iconFile = null,
                badge = "${index + 1}",
                size = 34.dp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$columns per row" + if (preference == IconColumns.AUTO) " (auto)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = HomeTokens.textDim,
        )
    }
}
