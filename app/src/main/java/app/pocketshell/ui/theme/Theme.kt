package app.pocketshell.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.pocketshell.settings.ThemeMode
import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.themeModeIsDark

/**
 * PocketShell theme composition — Control Center edition.
 *
 *   THEME = visual identity from [ThemeCatalog] (every identity ships a
 *           complete Light + Dark palette).
 *   MODE  = System / Light / Dark — which variant of the identity is shown.
 *           AMOLED is no longer a mode (a persisted "AMOLED" value degrades
 *           to DARK in the settings parser).
 *
 * This composable is ALSO the sync point for the chrome token vocabulary —
 * [TerminalTheme.applyTheme] runs during composition, before any child
 * reads a token, so a theme/mode selection (persisted or just chosen) never
 * flashes the wrong palette first (the Phase 5 no-flash contract).
 *
 * [textScale] is the Control Center's UI text scaling: a multiplier on the
 * SYSTEM font scale applied through [LocalDensity] for the whole app (the
 * terminal's own default font size stays a separate, terminal-scoped
 * setting). Dynamic color requires Android 12+ and only replaces the M3
 * layer — the chrome tokens always follow the selected identity.
 */
@Composable
fun PocketShellTheme(
    theme: AppTheme = AppTheme.AURORA,
    mode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    textScale: Float = TextScaleDefault,
    content: @Composable () -> Unit,
) {
    val dark = themeModeIsDark(mode, isSystemInDarkTheme())
    // Token sync BEFORE content composes — no first-frame flash.
    androidx.compose.runtime.remember(theme, mode, dark) {
        TerminalTheme.applyTheme(theme, dark.not())
        true
    }
    val context = LocalContext.current
    val variant = ThemeCatalog.variant(theme, dark)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> variant.toDarkColorScheme()
        else -> variant.toLightColorScheme()
    }
    val density = LocalDensity.current
    val scaledDensity = if (textScale == TextScaleDefault) density else Density(
        density = density.density,
        fontScale = density.fontScale * textScale,
    )
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** [TextScale.DEFAULT]'s factor — the "no scaling" identity value. */
const val TextScaleDefault = 1.0f

/**
 * The Material 3 scheme for a palette. Structure mapping (a slot → the
 * chrome token it mirrors) is uniform across themes; only identity-specific
 * M3 overrides (PocketShell's green primary) come from the palette fields.
 * The historical Daylight container-slot values are reproduced exactly by
 * the derivation (key → lowest/highest, chrome → low/container,
 * chromeGradientTop → high).
 */
private fun ThemeVariant.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = Color(m3Primary ?: accent),
    onPrimary = Color(m3OnPrimary ?: enterGlyph),
    primaryContainer = Color(m3PrimaryContainer ?: accentDeep),
    onPrimaryContainer = Color(m3OnPrimaryContainer ?: textPrimary),
    surface = Color(chrome),
    onSurface = Color(textPrimary),
    surfaceContainerLowest = Color(key),
    surfaceContainerLow = Color(chrome),
    surfaceContainer = Color(chrome),
    surfaceContainerHigh = Color(chromeGradientTop),
    surfaceContainerHighest = Color(key),
    onSurfaceVariant = Color(textDim),
    background = Color(screenBg),
    onBackground = Color(textPrimary),
    outline = Color(divider),
    outlineVariant = Color(divider),
)

private fun ThemeVariant.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = Color(m3Primary ?: accent),
    onPrimary = Color(m3OnPrimary ?: enterGlyph),
    primaryContainer = Color(m3PrimaryContainer ?: accentDeep),
    onPrimaryContainer = Color(m3OnPrimaryContainer ?: textPrimary),
    surface = Color(chrome),
    onSurface = Color(textPrimary),
    surfaceContainerLowest = Color(canvas),
    surfaceContainerLow = Color(chrome),
    surfaceContainer = Color(chrome),
    surfaceContainerHigh = Color(chromeGradientTop),
    surfaceContainerHighest = Color(keyAlt),
    onSurfaceVariant = Color(textDim),
    background = Color(screenBg),
    onBackground = Color(textPrimary),
    outline = Color(divider),
    outlineVariant = Color(divider),
)
