package app.pocketshell.ui.theme

import androidx.compose.ui.graphics.Color
import app.pocketshell.settings.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Terminal Aurora integration (Control Center II §4): the terminal surface
 * composites over the shared aurora backdrop through a translucent scrim —
 * Aurora only; every other theme keeps the fully opaque canvas. The vendored
 * TerminalView paints NO default background itself (TerminalRenderer draws
 * only non-default cell fills), so the scrim config in ui/theme is the whole
 * story — no renderer change, no PTY change.
 */
class AuroraTerminalIntegrationTest {

    @Test
    fun `aurora dark carries the translucent terminal scrim`() {
        TerminalTheme.applyTheme(AppTheme.AURORA, light = false)
        assertTrue(TerminalTheme.isAurora)
        val scrim = terminalScrimColor(isAurora = true)
        assertEquals(AURORA_TERMINAL_SCRIM_ALPHA, scrim.alpha, 1f / 255f)
        assertEquals(TerminalTheme.canvas.red, scrim.red, 0.0001f)
        assertEquals(TerminalTheme.canvas.green, scrim.green, 0.0001f)
        assertEquals(TerminalTheme.canvas.blue, scrim.blue, 0.0001f)
    }

    @Test
    fun `aurora light gets its own light terminal surface, still scrimmed`() {
        TerminalTheme.applyTheme(AppTheme.AURORA, light = true)
        assertTrue(TerminalTheme.isLight)
        // NOT the dark canvas re-used: a light Aurora terminal on pale paper
        assertEquals(Color(0xFFF5FAF8), TerminalTheme.canvas)
        val scrim = terminalScrimColor(isAurora = true)
        assertEquals(AURORA_TERMINAL_SCRIM_ALPHA, scrim.alpha, 1f / 255f)
    }

    @Test
    fun `non-aurora themes keep the fully opaque canvas`() {
        TerminalTheme.applyTheme(AppTheme.NORD, light = false)
        assertFalse(TerminalTheme.isAurora)
        assertEquals(1f, terminalScrimColor(isAurora = false).alpha, 0.0001f)
        TerminalTheme.applyTheme(AppTheme.POCKETSHELL, light = true)
        assertFalse(TerminalTheme.isAurora)
        assertEquals(1f, terminalScrimColor(isAurora = false).alpha, 0.0001f)
    }

    @Test
    fun `the scrim alpha stays readable - the surface remains mostly opaque`() {
        // the aurora must be SUBTLE behind the terminal: ≥80% opacity keeps
        // default fg/cursor contrast effectively untouched
        assertTrue(AURORA_TERMINAL_SCRIM_ALPHA >= 0.8f)
    }

    @Test
    fun `the light aurora stop set exists, distinct and deepened`() {
        val dark = ThemeCatalog.auroraStops
        val light = ThemeCatalog.auroraStopsLight
        assertEquals(dark.size, light.size)
        assertTrue(light.size >= 4)
        assertTrue(light.toSet().size == light.size)
        assertTrue(light.toSet().intersect(dark.toSet()).isEmpty())
    }

    @Test
    fun `the light aurora palette is readable (contrast contract re-run on Aurora only)`() {
        // full-matrix coverage lives in ThemeCatalogContrastTest; this pins
        // the REDESIGNED light palette explicitly against the ink/accent pairs
        val light = ThemeCatalog.definition(AppTheme.AURORA).second
        fun ratio(a: Long, b: Long): Double {
            fun lum(v: Long): Double {
                fun ch(shift: Int): Double {
                    val c = ((v shr shift) and 0xFF) / 255.0
                    return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
                }
                return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
            }
            val la = lum(a); val lb = lum(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
        assertTrue(ratio(light.textPrimary, light.screenBg) >= 6.5)
        assertTrue(ratio(light.accent, light.screenBg) >= 4.0)
        assertTrue(ratio(light.onCanvas, light.canvas) >= 6.5)
        // and it is genuinely a LIGHT palette, sibling of the same identity
        val dark = ThemeCatalog.definition(AppTheme.AURORA).first
        assertTrue(light.accent != dark.accent)
    }
}
