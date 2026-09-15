package app.pocketshell.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control Center density contract (task §5): discrete presets, width-capped
 * column math, and honest parsers for the persisted values.
 */
class AppearanceDensityTest {

    // ---- icons per row -----------------------------------------------------

    @Test
    fun `the width ladder is the historical responsive density`() {
        assertEquals(3, HomeGridDensity.columnsForWidth(360f))
        assertEquals(3, HomeGridDensity.columnsForWidth(411f))
        assertEquals(3, HomeGridDensity.columnsForWidth(599f))
        assertEquals(4, HomeGridDensity.columnsForWidth(600f))
        assertEquals(4, HomeGridDensity.columnsForWidth(720f))
        assertEquals(6, HomeGridDensity.columnsForWidth(840f))
        assertEquals(6, HomeGridDensity.columnsForWidth(1280f))
    }

    @Test
    fun `AUTO follows the width exactly`() {
        assertEquals(3, HomeGridDensity.effectiveColumns(IconColumns.AUTO, 411f))
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.AUTO, 600f))
        assertEquals(6, HomeGridDensity.effectiveColumns(IconColumns.AUTO, 840f))
    }

    @Test
    fun `an explicit preference is capped by the width`() {
        // a phone (≤600dp) never gets squeezed past 3; tablets get what fits
        assertEquals(3, HomeGridDensity.effectiveColumns(IconColumns.FIVE, 411f))
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.SIX, 600f))
        assertEquals(6, HomeGridDensity.effectiveColumns(IconColumns.SIX, 840f))
        assertEquals(5, HomeGridDensity.effectiveColumns(IconColumns.FIVE, 840f))
    }

    @Test
    fun `an explicit preference below the width cap is honored`() {
        assertEquals(2, HomeGridDensity.effectiveColumns(IconColumns.TWO, 1280f))
        assertEquals(3, HomeGridDensity.effectiveColumns(IconColumns.THREE, 840f))
    }

    @Test
    fun `columns never drop below two`() {
        assertEquals(2, HomeGridDensity.effectiveColumns(IconColumns.TWO, 240f))
    }

    @Test
    fun `entry width fills the content width with one page of columns`() {
        assertEquals(320f, HomeGridDensity.entryWidthDp(1000f, 3), 0.001f)
        assertEquals(112f, HomeGridDensity.entryWidthDp(600f, 5), 0.001f)
    }

    // ---- presets are real, distinct, and sensible --------------------------

    @Test
    fun `icon size presets are distinct and ordered`() {
        val dps = IconSize.entries.map { it.tileDp }
        assertEquals(dps, dps.sorted())
        assertTrue(dps.toSet().size == dps.size)
        assertEquals(52, IconSize.DEFAULT.tileDp) // the historical tile size
    }

    @Test
    fun `card size presets bracket the default`() {
        assertTrue(CardSize.COMPACT.scale < CardSize.DEFAULT.scale)
        assertTrue(CardSize.DEFAULT.scale < CardSize.LARGE.scale)
        assertEquals(1.0f, CardSize.DEFAULT.scale, 0.0001f)
    }

    @Test
    fun `text scale presets bracket the default`() {
        assertTrue(TextScale.SMALL.factor < TextScale.DEFAULT.factor)
        assertTrue(TextScale.DEFAULT.factor < TextScale.LARGE.factor)
        assertTrue(TextScale.LARGE.factor < TextScale.EXTRA_LARGE.factor)
        assertEquals(1.0f, TextScale.DEFAULT.factor, 0.0001f)
    }

    // ---- persistence parsers (pure half of the DataStore mapping) ----------

    @Test
    fun `appearance parsers round-trip every preset`() {
        TextScale.entries.forEach { assertEquals(it, parseTextScale(it.name)) }
        IconSize.entries.forEach { assertEquals(it, parseIconSize(it.name)) }
        CardSize.entries.forEach { assertEquals(it, parseCardSize(it.name)) }
        IconColumns.entries.forEach { assertEquals(it, parseIconColumns(it.name)) }
    }

    @Test
    fun `appearance parsers fall back to the defaults on junk`() {
        assertEquals(TextScale.DEFAULT, parseTextScale(null))
        assertEquals(TextScale.DEFAULT, parseTextScale("HUGE"))
        assertEquals(IconSize.DEFAULT, parseIconSize(null))
        assertEquals(IconSize.DEFAULT, parseIconSize("GIGANTIC"))
        assertEquals(CardSize.DEFAULT, parseCardSize(null))
        assertEquals(CardSize.DEFAULT, parseCardSize("massive"))
        assertEquals(IconColumns.AUTO, parseIconColumns(null))
        assertEquals(IconColumns.AUTO, parseIconColumns("ELEVEN"))
    }

    @Test
    fun `icon columns vocabulary carries exactly auto and two through six`() {
        assertEquals(
            listOf(0, 2, 3, 4, 5, 6),
            IconColumns.entries.map { it.requested },
        )
    }
}
