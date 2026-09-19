package app.pocketshell.widget.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the responsive contract of the TODO card: pure, measured
 * thresholds (same discipline as ServersLayoutTest / GitLayoutTest).
 * COMPACT (the ~172dp phone card, which carries an input row + tabs its
 * siblings lack) caps rows and states "+N more"; ROOMY scrolls and adds
 * the footer statistics.
 */
class TodoLayoutTest {

    @Test
    fun `phone card is COMPACT - capped rows, no scroll, no footer`() {
        val compact = TodoLayout.from(widthDp = 360f, heightDp = 171f)
        assertEquals(TodoLayout.COMPACT, compact)
        assertFalse(compact.scrollsRows)
        assertFalse(compact.showsFooter)
        assertEquals(2, compact.maxRows)
    }

    @Test
    fun `tablet card is ROOMY at the sibling thresholds - scrolling plus footer`() {
        val roomy = TodoLayout.from(
            widthDp = TodoLayout.ROOMY_MIN_WIDTH_DP,
            heightDp = TodoLayout.ROOMY_MIN_HEIGHT_DP,
        )
        assertEquals(TodoLayout.ROOMY, roomy)
        assertTrue(roomy.scrollsRows)
        assertTrue(roomy.showsFooter)
        assertEquals(Int.MAX_VALUE, roomy.maxRows)
    }

    @Test
    fun `both dimensions must qualify - a wide short card stays COMPACT`() {
        assertEquals(TodoLayout.COMPACT, TodoLayout.from(widthDp = 720f, heightDp = 150f))
        assertEquals(TodoLayout.COMPACT, TodoLayout.from(widthDp = 300f, heightDp = 240f))
    }
}
