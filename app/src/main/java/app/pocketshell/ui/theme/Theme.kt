package app.pocketshell.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.pocketshell.settings.ThemeMode

/**
 * PocketShell "Quiet Aurora" theme (docs/UI-REDESIGN.md §3).
 *
 * Light / Dark / AMOLED brand schemes + optional Material You dynamic color
 * (Android 12+, honest fallback to the brand scheme elsewhere). Terminal
 * surfaces intentionally do not follow dynamic color: the terminal canvas is
 * always the framed-ink pair below.
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
        themeMode == ThemeMode.AMOLED -> QuietAuroraAmoled
        dark -> QuietAuroraDark
        else -> QuietAuroraLight
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PSTypography,
        shapes = PSShapes,
        content = content,
    )
}

/** True when the active theme is dark-flavoured (Dark/AMOLED/dark system). */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background == AmoledBg ||
    MaterialTheme.colorScheme.background == InkBg
