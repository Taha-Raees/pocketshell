package app.pocketshell.ui.theme

import app.pocketshell.settings.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Control Center readability contract: EVERY theme × EVERY variant must
 * keep the chrome token pairs readable (WCAG-style contrast ratios on the
 * 0xAARRGGBB palette values — pure JVM, no device needed).
 *
 * These thresholds are the product bar for adding a theme: if a new palette
 * fails here, the palette is wrong — not the test.
 */
class ThemeCatalogContrastTest {

    private fun channel(value: Long, shift: Int): Double {
        val c = ((value shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    private fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertReadable(theme: AppTheme, light: Boolean, v: ThemeVariant) {
        val id = "${theme.name}/${if (light) "light" else "dark"}"
        val checks = listOf(
            "textPrimary on screenBg" to (ratio(v.textPrimary, v.screenBg) to 6.5),
            "textDim on screenBg" to (ratio(v.textDim, v.screenBg) to 3.2),
            "textPrimary on chrome" to (ratio(v.textPrimary, v.chrome) to 6.0),
            "textPrimary on deck" to (ratio(v.textPrimary, v.deck) to 4.5),
            "textPrimary on key" to (ratio(v.textPrimary, v.key) to 4.5),
            "textPrimary on keyAlt" to (ratio(v.textPrimary, v.keyAlt) to 4.5),
            "onCanvas on canvas" to (ratio(v.onCanvas, v.canvas) to 6.5),
            "onCanvasDim on canvas" to (ratio(v.onCanvasDim, v.canvas) to 3.2),
            "accent as text on screenBg" to (ratio(v.accent, v.screenBg) to 4.0),
            "accentBright on screenBg" to (ratio(v.accentBright, v.screenBg) to 3.0),
            // floor 3.5: the historical Midnight filled-button pair measures
            // 3.74 and is preserved byte-for-byte; NEW themes should aim 4.5+
            "enterGlyph on accent" to (ratio(v.enterGlyph, v.accent) to 4.5),
            "onAccentDeep on accentDeep" to (ratio(v.onAccentDeep, v.accentDeep) to 3.5),
            "danger on screenBg" to (ratio(v.danger, v.screenBg) to 3.0),
            "runningGreen on screenBg" to (ratio(v.runningGreen, v.screenBg) to 2.5),
            "divider visible on screenBg" to (ratio(v.divider, v.screenBg) to 1.12),
        )
        checks.forEach { (name, pair) ->
            val (actual, minRatio) = pair
            assertTrue(
                "$id: $name ratio ${"%.2f".format(actual)} < $minRatio",
                actual >= minRatio,
            )
        }
    }

    @Test
    fun `every theme is readable in both variants`() {
        AppTheme.entries.forEach { theme ->
            val (dark, light) = ThemeCatalog.definition(theme)
            assertReadable(theme, light = false, v = dark)
            assertReadable(theme, light = true, v = light)
        }
    }

    @Test
    fun `every theme has a light and a dark variant with distinct surfaces`() {
        AppTheme.entries.forEach { theme ->
            val (dark, light) = ThemeCatalog.definition(theme)
            // a light variant must actually be light and the dark one dark —
            // measured, not assumed: relative luminance ordering.
            assertTrue(
                "${theme.name}: light screenBg must out-luminate dark screenBg",
                luminance(light.screenBg) > luminance(dark.screenBg),
            )
            // the surface stack must not collapse into one flat color
            assertTrue(
                "${theme.name}: dark chrome must differ from screenBg",
                dark.chrome != dark.screenBg,
            )
            assertTrue(
                "${theme.name}: light chrome must differ from screenBg",
                light.chrome != light.screenBg,
            )
        }
    }

    @Test
    fun `the PocketShell identity is byte-for-byte the historical Midnight and Daylight values`() {
        val (dark, light) = ThemeCatalog.definition(AppTheme.POCKETSHELL)
        // Midnight Sapphire (spot-check the identity-carrying tokens)
        assertTrue(dark.screenBg == 0xFF0B1424)
        assertTrue(dark.canvas == 0xFF080F1D)
        assertTrue(dark.accent == 0xFF7FA3EF)
        assertTrue(dark.textPrimary == 0xFFDCE6F8)
        assertTrue(dark.m3Primary == 0xFF8FD694)
        // Daylight Sapphire
        assertTrue(light.screenBg == 0xFFEEF2F8)
        assertTrue(light.canvas == 0xFFF7F9FC)
        assertTrue(light.accent == 0xFF3D5A96)
        assertTrue(light.textPrimary == 0xFF17233B)
        assertTrue(light.m3Primary == 0xFF2E6B34)
    }

    @Test
    fun `aurora stops exist for the animated layer`() {
        assertTrue(ThemeCatalog.auroraStops.size >= 3)
        assertTrue(ThemeCatalog.auroraStops.all { it in 0xFF000000..0xFFFFFFFF })
    }
}
