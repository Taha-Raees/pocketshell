package app.pocketshell.widget.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M8.4 — the responsive contract's thresholds, pinned exactly like the
 * M8.3 application layouts (ServersLayoutTest, GitLayoutTest): the SAME
 * inner-dp boundaries — 420 wide AND 200 tall — every application shares.
 */
class NotesLayoutTest {

    @Test
    fun `phone-class inner size is COMPACT`() {
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(300f, 172f))
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(390f, 199f))
    }

    @Test
    fun `tablet-class inner size is ROOMY`() {
        assertEquals(NotesLayout.ROOMY, NotesLayout.from(780f, 208f))
        assertEquals(NotesLayout.ROOMY, NotesLayout.from(500f, 300f))
    }

    @Test
    fun `the shared threshold is inclusive`() {
        assertEquals(NotesLayout.ROOMY, NotesLayout.from(420f, 200f))
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(419f, 200f))
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(420f, 199f))
    }

    @Test
    fun `width alone or height alone never reaches ROOMY`() {
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(1000f, 150f))
        assertEquals(NotesLayout.COMPACT, NotesLayout.from(100f, 1000f))
    }
}
