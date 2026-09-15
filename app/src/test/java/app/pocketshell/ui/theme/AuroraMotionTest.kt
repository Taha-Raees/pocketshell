package app.pocketshell.ui.theme

import app.pocketshell.settings.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aurora motion policy (task §4/§11): reduced-motion and low-RAM devices
 * get the STATIC aurora — same palette, no frame loop — everyone else gets
 * the restrained animation.
 */
class AuroraMotionTest {

    @Test
    fun `normal animator scale animates`() {
        assertEquals(AuroraMotionPolicy.ANIMATED, AuroraMotion.resolve(1.0f, isLowRamDevice = false))
        assertEquals(AuroraMotionPolicy.ANIMATED, AuroraMotion.resolve(0.5f, isLowRamDevice = false))
    }

    @Test
    fun `animator duration scale zero (remove animations) forces static`() {
        assertEquals(AuroraMotionPolicy.STATIC, AuroraMotion.resolve(0.0f, isLowRamDevice = false))
    }

    @Test
    fun `negative animator scale is treated as static`() {
        assertEquals(AuroraMotionPolicy.STATIC, AuroraMotion.resolve(-1.0f, isLowRamDevice = false))
    }

    @Test
    fun `low-RAM devices force static regardless of animator scale`() {
        assertEquals(AuroraMotionPolicy.STATIC, AuroraMotion.resolve(1.0f, isLowRamDevice = true))
        assertEquals(AuroraMotionPolicy.STATIC, AuroraMotion.resolve(0.0f, isLowRamDevice = true))
    }

    @Test
    fun `the static aurora is a fixed phase inside one period`() {
        assertTrue(STATIC_AURORA_PHASE in 0f..1f)
        assertTrue(AURORA_PERIOD_SECONDS >= 20f) // slow by design, never flashy
    }

    @Test
    fun `the aurora identity is flagged and carries both variants`() {
        assertTrue(AppTheme.AURORA.aurora)
        val (dark, light) = ThemeCatalog.definition(AppTheme.AURORA)
        val lum = { argb: Long ->
            val ch = { v: Long -> (v / 255.0).coerceIn(0.0, 1.0) }
            0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) +
                0.0722 * ch(argb and 0xFF)
        }
        assertTrue(lum(light.screenBg) > lum(dark.screenBg))
    }
}
