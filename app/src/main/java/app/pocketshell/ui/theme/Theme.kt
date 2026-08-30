package app.pocketshell.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Restrained phosphor-green accent — serious, terminal-flavoured, not flashy.
private val GreenPrimary = Color(0xFF2E6B34)
private val GreenPrimaryDark = Color(0xFF8FD694)
private val TerminalSurfaceDark = Color(0xFF0E1113)
private val TerminalSurfaceLight = Color(0xFFF7F9F7)

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

/**
 * PocketShell M3 theme. Light/Dark now; AMOLED + Dynamic Color arrive with
 * M1.3 (docs/ROADMAP.md) — kept deliberately restrained either way (brief §25).
 */
@Composable
fun PocketShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
