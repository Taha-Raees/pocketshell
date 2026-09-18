package app.pocketshell.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — the slot persistence codec: absent/corrupt → defaults, junk-shaped
 * tokens dropped, exactly two slots always (the fill/trim contract).
 */
class WidgetSlotsCodecTest {

    @Test
    fun `absent record decodes to the defaults`() {
        assertEquals(WidgetRegistry.DEFAULT_SLOTS, WidgetSlotsCodec.decode(null))
        assertEquals(WidgetSlotsCodec.decode(""), WidgetRegistry.DEFAULT_SLOTS)
    }

    @Test
    fun `a stored assignment round-trips`() {
        val ids = listOf("servers", "storage")
        assertEquals(ids, WidgetSlotsCodec.decode(WidgetSlotsCodec.encode(ids)))
    }

    @Test
    fun `corrupt tokens are dropped and gaps fall back to the defaults`() {
        // Only one well-shaped token survives → it keeps slot 1, the primary
        // core default (Terminal) fills the remaining slot — never an empty
        // or one-slot Home.
        val decoded = WidgetSlotsCodec.decode("servers\u0000DROP\u0000")
        assertEquals(listOf("servers", WidgetRegistry.TERMINAL_ID), decoded)
    }

    @Test
    fun `all-corrupt input decodes to the defaults`() {
        assertEquals(WidgetRegistry.DEFAULT_SLOTS, WidgetSlotsCodec.decode("A\u0000..\u0000"))
    }

    @Test
    fun `more than two entries is trimmed to the first two`() {
        assertEquals(
            listOf("storage", "agents"),
            WidgetSlotsCodec.fillToTwo(listOf("storage", "agents", "servers")),
        )
    }

    @Test
    fun `fewer than two entries is filled from the defaults`() {
        assertEquals(listOf("agents", "core.terminal"), WidgetSlotsCodec.fillToTwo(listOf("agents")))
        assertEquals(WidgetRegistry.DEFAULT_SLOTS, WidgetSlotsCodec.fillToTwo(emptyList()))
    }

    @Test
    fun `duplicates collapse`() {
        val decoded = WidgetSlotsCodec.decode("servers\u0000servers\u0000storage")
        assertEquals(listOf("servers", "storage"), decoded)
    }

    @Test
    fun `unknown well-shaped ids survive decode and resolve as Missing`() {
        // A catalog widget id that no longer resolves must render the honest
        // Missing card — the codec deliberately does NOT drop it.
        val decoded = WidgetSlotsCodec.decode("tmux\u0000servers")
        val resolved = WidgetRegistry.resolve(decoded)
        assertTrue(resolved[0] is WidgetSlotEntry.Missing)
        assertTrue(resolved[1] is WidgetSlotEntry.Resolved)
    }

    @Test
    fun `every default slot id resolves to a core widget`() {
        val resolved = WidgetRegistry.resolve(WidgetRegistry.DEFAULT_SLOTS)
        assertEquals(2, resolved.size)
        resolved.forEach { entry ->
            val widget = (entry as WidgetSlotEntry.Resolved).widget
            assertTrue(widget.spec.isCore)
        }
    }
}
