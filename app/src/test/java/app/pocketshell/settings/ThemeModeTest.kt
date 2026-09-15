package app.pocketshell.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control Center THEME × MODE contract (Settings/Control Center task §2/§3):
 * the mode vocabulary is exactly System/Light/Dark — AMOLED is no longer a
 * top-level mode — and the mode→variant decision is total and honest.
 */
class ThemeModeTest {

    @Test
    fun `AMOLED is no longer a top-level mode`() {
        assertFalse("AMOLED must not be a mode", ThemeMode.entries.any { it.name == "AMOLED" })
        assertEquals(3, ThemeMode.entries.size)
        assertEquals(listOf("SYSTEM", "LIGHT", "DARK"), ThemeMode.entries.map { it.name })
    }

    @Test
    fun `a persisted AMOLED value degrades honestly to DARK`() {
        assertEquals(ThemeMode.DARK, parseThemeMode("AMOLED"))
    }

    @Test
    fun `mode parser round-trips every current mode`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, parseThemeMode(mode.name))
        }
    }

    @Test
    fun `mode parser falls back to SYSTEM on junk`() {
        assertEquals(ThemeMode.SYSTEM, parseThemeMode(null))
        assertEquals(ThemeMode.SYSTEM, parseThemeMode(""))
        assertEquals(ThemeMode.SYSTEM, parseThemeMode("NEON"))
    }

    @Test
    fun `system mode follows the device, light forces light, dark forces dark`() {
        assertTrue(themeModeIsDark(ThemeMode.SYSTEM, systemInDark = true))
        assertFalse(themeModeIsDark(ThemeMode.SYSTEM, systemInDark = false))
        assertFalse(themeModeIsDark(ThemeMode.LIGHT, systemInDark = true))
        assertFalse(themeModeIsDark(ThemeMode.LIGHT, systemInDark = false))
        assertTrue(themeModeIsDark(ThemeMode.DARK, systemInDark = true))
        assertTrue(themeModeIsDark(ThemeMode.DARK, systemInDark = false))
    }

    @Test
    fun `the identity list is the curated Control Center set`() {
        assertEquals(
            listOf(
                "POCKETSHELL", "NORD", "DRACULA", "GRUVBOX", "SOLARIZED",
                "ONE_DARK", "MONOKAI", "ROSE_PINE", "CYBER", "AURORA",
            ),
            AppTheme.entries.map { it.name },
        )
    }

    @Test
    fun `identity parser round-trips and falls back to PocketShell`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, parseAppTheme(theme.name))
        }
        assertEquals(AppTheme.POCKETSHELL, parseAppTheme(null))
        assertEquals(AppTheme.POCKETSHELL, parseAppTheme("AMOLED"))
        assertEquals(AppTheme.POCKETSHELL, parseAppTheme("nord"))
    }

    @Test
    fun `aurora is the only animated identity`() {
        assertEquals(listOf(AppTheme.AURORA), AppTheme.entries.filter { it.aurora })
    }

    @Test
    fun `every identity carries a non-blank display label`() {
        AppTheme.entries.forEach { theme ->
            assertTrue(theme.label.isNotBlank())
        }
    }
}
