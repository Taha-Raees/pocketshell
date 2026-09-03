package app.pocketshell.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.pocketshell.settings.ThemeMode

// Restrained phosphor-green accent — serious, terminal-flavoured, not flashy.
private val GreenPrimary = Color(0xFF2E6B34)
private val GreenPrimaryDark = Color(0xFF8FD694)
private val TerminalSurfaceDark = Color(0xFF0E1113)
private val TerminalSurfaceLight = Color(0xFFF7F9F7)
private val AmoledBlack = Color(0xFF000000)
private val AmoledSurface = Color(0xFF050505)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0BA),
    onPrimaryContainer = Color(0xFF002105),
    surface = TerminalSurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF003911),
    primaryContainer = Color(0xFF1D5326),
    onPrimaryContainer = Color(0xFFB9F0BA),
    surface = TerminalSurfaceDark,
)

private val AmoledColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF003911),
    primaryContainer = Color(0xFF14331A),
    onPrimaryContainer = Color(0xFFB9F0BA),
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurface,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = Color(0xFF0C0C0C),
    surfaceContainerHighest = Color(0xFF111111),
)

/**
 * PocketShell M3 theme (brief §25): Light / Dark / AMOLED / Dynamic Color on a
 * restrained, serious baseline. Dynamic color requires Android 12+ and falls
 * back to the brand scheme elsewhere.
 */
@Composable
fun PocketShellTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> AmoledColors
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
