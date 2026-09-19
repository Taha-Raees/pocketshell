package app.pocketshell.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.2 — the Servers application's responsive contract: the information
 * hierarchy adapts to the available card space (real constraints — the
 * content width and the derived card height), it is never scaled.
 */
class ServersLayoutTest {

    @Test
    fun `a phone card is COMPACT - one-line rows, no extras`() {
        // Phone content width ≈ 320-390dp; INNER card height ≈ 240dp·scale
        // minus the 32dp chrome padding.
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(320f, 208f))
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(390f, 172f)) // COMPACT card size
        val layout = ServersLayout.from(320f, 240f)
        assertFalse(layout.showsCwd)
        assertFalse(layout.showsStatusHeader)
    }

    @Test
    fun `a tablet card is ROOMY - status header, directories, scroll`() {
        // Tablet content width up to 720−40dp; INNER height 240dp·1.0−32.
        assertEquals(ServersLayout.ROOMY, ServersLayout.from(680f, 208f))
        assertEquals(ServersLayout.ROOMY, ServersLayout.from(420f, 251f)) // LARGE card size
        val layout = ServersLayout.ROOMY
        assertTrue(layout.showsCwd)
        assertTrue(layout.showsStatusHeader)
    }

    @Test
    fun `the roomy threshold requires BOTH width and height`() {
        // Wide but short (landscape phone) → COMPACT hierarchy.
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(680f, 180f))
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(680f, 199.9f))
        // Tall but narrow (portrait phone) → COMPACT hierarchy.
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(390f, 300f))
    }

    @Test
    fun `the thresholds sit exactly at the documented boundaries`() {
        assertEquals(ServersLayout.ROOMY, ServersLayout.from(420f, 200f))
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(419.9f, 200f))
        assertEquals(ServersLayout.COMPACT, ServersLayout.from(420f, 199.9f))
    }
}
