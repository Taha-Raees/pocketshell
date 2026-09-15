package app.pocketshell.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control Center density contract (task §5): discrete presets, width-capped
 * column math, and honest parsers for the persisted values.
 */
class AppearanceDensityTest {

    // ---- responsive columns (Control Center II model) ----------------------

    @Test
    fun `AUTO gives phones the historical 3 and fills tablets instead of idling`() {
        // phone (411dp window → 371dp usable): ~110dp cells → 3, unchanged
        assertEquals(3, HomeGridDensity.autoColumns(411f, 52))
        // large phone / small foldable (500dp → 460 usable): 4 comfortable cells
        assertEquals(4, HomeGridDensity.autoColumns(500f, 52))
        // foldable inner / small tablet (600dp): 5 — no more dead space
        assertEquals(5, HomeGridDensity.autoColumns(600f, 52))
        // tablet content width (720dp cap → 680 usable): 6
        assertEquals(6, HomeGridDensity.autoColumns(840f, 52))
    }

    @Test
    fun `AUTO re-derives for the chosen icon size`() {
        // extra-large icons need room: a phone drops to 2 comfortable columns
        assertEquals(2, HomeGridDensity.autoColumns(411f, 72))
        // small icons on the same phone stay comfortable at 3+
        assertTrue(HomeGridDensity.autoColumns(411f, 44) >= 3)
    }

    @Test
    fun `an explicit request that fits MUST be honored (no hidden max 3)`() {
        // THE regression the task names: 4 columns on a phone that can carry
        // 4 must actually render 4
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.FOUR, 411f, 52))
        assertEquals(5, HomeGridDensity.effectiveColumns(IconColumns.FIVE, 500f, 52))
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.FOUR, 411f, 44))
    }

    @Test
    fun `a request denser than fits clamps at the fit, never below 2`() {
        // 6 on a phone: cells would drop under the minimum → clamp to fit
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.SIX, 411f, 52))
        // 2 always fits something
        assertEquals(2, HomeGridDensity.effectiveColumns(IconColumns.TWO, 240f, 72))
    }

    @Test
    fun `icon size and columns interact - larger icons cap the fit`() {
        val smallFit = HomeGridDensity.fitByWidth(411f, 44)
        val largeFit = HomeGridDensity.fitByWidth(411f, 72)
        assertTrue(largeFit < smallFit)
        // 4 columns + Large icons on a tablet: no overlap, both fit
        assertEquals(4, HomeGridDensity.effectiveColumns(IconColumns.FOUR, 840f, 62))
    }

    @Test
    fun `tablet width carries every explicit request up to six`() {
        for (pref in listOf(IconColumns.TWO, IconColumns.THREE, IconColumns.FOUR, IconColumns.FIVE, IconColumns.SIX)) {
            assertEquals(
                pref.requested,
                HomeGridDensity.effectiveColumns(pref, 840f, 52),
            )
        }
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
