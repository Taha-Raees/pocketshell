package app.pocketshell.widget.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the responsive contract of the TODO card: pure, measured
 * thresholds (same discipline as ServersLayoutTest / GitLayoutTest).
 * The task rows scroll at BOTH densities with no cap — a task must never
 * be unreachable inside the card.
 */
class TodoLayoutTest {

    @Test
    fun `phone card is COMPACT`() {
        val compact = TodoLayout.from(widthDp = 360f, heightDp = 171f)
        assertEquals(TodoLayout.COMPACT, compact)
    }

    @Test
    fun `tablet card is ROOMY at the sibling thresholds - scrolling`() {
        val roomy = TodoLayout.from(
            widthDp = TodoLayout.ROOMY_MIN_WIDTH_DP,
            heightDp = TodoLayout.ROOMY_MIN_HEIGHT_DP,
        )
        assertEquals(TodoLayout.ROOMY, roomy)
    }

    @Test
    fun `both dimensions must qualify - a wide short card stays COMPACT`() {
        assertEquals(TodoLayout.COMPACT, TodoLayout.from(widthDp = 720f, heightDp = 150f))
        assertEquals(TodoLayout.COMPACT, TodoLayout.from(widthDp = 300f, heightDp = 240f))
    }
}
