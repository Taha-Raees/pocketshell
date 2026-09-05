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

// Phase 5 — the M3 schemes now carry the SAME paper/ink structure as the
// Midnight/Daylight token vocabulary, so every Material surface that leaks
// through (Scaffold backdrop, DropdownMenu, dialogs) matches the app chrome
// instead of the stock purple-tinted defaults.
private val PaperBackground = Color(0xFFEEF2F8)
private val PaperSurface = Color(0xFFF7F9FC)
private val PaperInk = Color(0xFF17233B)
private val PaperDim = Color(0xFF5D6E8C)
private val MidnightBackground = Color(0xFF0B1424)
private val MidnightSurface = Color(0xFF101B30)
private val MidnightInk = Color(0xFFDCE6F8)

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0BA),
    onPrimaryContainer = Color(0xFF002105),
    surface = PaperSurface,
    onSurface = PaperInk,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = Color(0xFFFBFCFE),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    onSurfaceVariant = PaperDim,
    background = PaperBackground,
    onBackground = PaperInk,
    outline = Color(0xFFD3DCE9),
    outlineVariant = Color(0xFFD3DCE9),
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = Color(0xFF003911),
    primaryContainer = Color(0xFF1D5326),
    onPrimaryContainer = Color(0xFFB9F0BA),
    surface = MidnightSurface,
    onSurface = MidnightInk,
    onSurfaceVariant = Color(0xFF7C8DB0),
    background = MidnightBackground,
    onBackground = MidnightInk,
    outline = Color(0xFF1D2C4A),
    outlineVariant = Color(0xFF1D2C4A),
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
    onSurface = MidnightInk,
    onBackground = MidnightInk,
    onSurfaceVariant = Color(0xFF7C8DB0),
    outline = Color(0xFF1D2C4A),
    outlineVariant = Color(0xFF1D2C4A),
)

/**
 * PocketShell M3 theme (brief §25): Light / Dark / AMOLED / Dynamic Color on a
 * restrained, serious baseline. Dynamic color requires Android 12+ and falls
 * back to the brand scheme elsewhere.
 *
 * Phase 5: this composable is ALSO the sync point for the Midnight/Daylight
 * token vocabulary — [TerminalTheme.applyTheme] runs during composition,
 * before any child reads a token, so a Light selection (persisted or just
 * chosen) never flashes the dark palette first.
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
    // Token sync BEFORE content composes — no first-frame flash.
    androidx.compose.runtime.remember(themeMode, dark) {
        TerminalTheme.applyTheme(!dark)
        true
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
