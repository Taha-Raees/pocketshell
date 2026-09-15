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
    fun `mode parser falls back to DARK on absence or junk (fresh-install default)`() {
        // Control Center II: Aurora × Dark is the fresh-install identity —
        // the absent-key fallback is DARK, never a crash and never SYSTEM.
        assertEquals(ThemeMode.DARK, parseThemeMode(null))
        assertEquals(ThemeMode.DARK, parseThemeMode(""))
        assertEquals(ThemeMode.DARK, parseThemeMode("NEON"))
    }

    @Test
    fun `an explicitly saved mode is NEVER overwritten by the default`() {
        // every current value round-trips verbatim — the fallback only
        // applies to keys that are absent/unknown
        assertEquals(ThemeMode.SYSTEM, parseThemeMode("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, parseThemeMode("LIGHT"))
        assertEquals(ThemeMode.DARK, parseThemeMode("DARK"))
    }

    @Test
    fun `fresh-install theme default is AURORA and saved identities are preserved`() {
        assertEquals(AppTheme.AURORA, parseAppTheme(null))
        assertEquals(AppTheme.AURORA, parseAppTheme(""))
        // saved preferences — including the historical default — win
        assertEquals(AppTheme.NORD, parseAppTheme("NORD"))
        assertEquals(AppTheme.POCKETSHELL, parseAppTheme("POCKETSHELL"))
        assertEquals(AppTheme.AURORA, parseAppTheme("AURORA"))
        // unknown junk falls back to the fresh-install identity
        assertEquals(AppTheme.AURORA, parseAppTheme("NEON"))
    }

    @Test
    fun `Aurora x Dark composes - the default pair resolves to a dark aurora`() {
        // the exact fresh-install pair: aurora identity, mode DARK
        assertTrue(AppTheme.AURORA.aurora)
        assertTrue(themeModeIsDark(parseThemeMode(null), systemInDark = false))
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
    fun `identity parser round-trips every identity`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, parseAppTheme(theme.name))
        }
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
