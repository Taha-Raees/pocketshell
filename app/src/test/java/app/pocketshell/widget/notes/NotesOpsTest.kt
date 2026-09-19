package app.pocketshell.widget.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the pure notes core: pin-first display ordering, search
 * filtering, and the list mutations. Deterministic: no clock, no IO.
 */
class NotesOpsTest {

    private fun note(
        id: String,
        title: String = "",
        body: String = "",
        pinned: Boolean = false,
        updated: Long = 0L,
        created: Long = 0L,
    ) = StickyNote(
        id = id,
        title = title,
        body = body,
        pinned = pinned,
        createdAtMs = created,
        updatedAtMs = updated,
    )

    // ------------------------------------------------- pin-first ordering

    @Test
    fun `pinned notes sort first then most recently touched`() {
        val notes = listOf(
            note("a", updated = 100),
            note("b", pinned = true, updated = 10),
            note("c", updated = 300),
            note("d", pinned = true, updated = 50),
        )
        val sorted = NoteOps.sortedForDisplay(notes)
        assertEquals(listOf("d", "b", "c", "a"), sorted.map { it.id })
    }

    @Test
    fun `the display order is total and deterministic`() {
        // Equal timestamps and equal pin state → the id breaks every tie,
        // so the same input always renders in the same order.
        val notes = listOf(note("z"), note("a"), note("m"))
        val sorted = NoteOps.sortedForDisplay(notes)
        assertEquals(listOf("a", "m", "z"), sorted.map { it.id })
        assertEquals(sorted, NoteOps.sortedForDisplay(notes.asReversed()))
    }

    @Test
    fun `a touched unpinned note cannot overtake a stale pinned note`() {
        val notes = listOf(
            note("pin", pinned = true, updated = 1),
            note("fresh", updated = 999),
        )
        assertEquals("pin", NoteOps.sortedForDisplay(notes).first().id)
    }

    // ------------------------------------------------------ search filter

    @Test
    fun `a blank or blank-ish query matches everything`() {
        val notes = listOf(note("a", title = "tar flags"), note("b", body = "docker ps"))
        assertEquals(notes, NoteOps.filtered(notes, ""))
        assertEquals(notes, NoteOps.filtered(notes, "   "))
    }

    @Test
    fun `the query matches title or body case-insensitively`() {
        val notes = listOf(
            note("a", title = "Tar extract"),
            note("b", body = "DOCKER ps output"),
            note("c", title = "shopping", body = "milk"),
        )
        assertEquals(listOf("a"), NoteOps.filtered(notes, "tar").map { it.id })
        assertEquals(listOf("b"), NoteOps.filtered(notes, "docker").map { it.id })
        // Case-insensitive across either field; input order is preserved.
        assertEquals(listOf("b"), NoteOps.filtered(notes, "OUT").map { it.id })
        assertEquals(listOf("b", "c"), NoteOps.filtered(notes, "O").map { it.id })
        assertEquals(emptyList<String>(), NoteOps.filtered(notes, "zsh").map { it.id })
    }

    @Test
    fun `the query is trimmed before matching`() {
        val notes = listOf(note("a", title = "backup cron"))
        assertEquals(listOf("a"), NoteOps.filtered(notes, "  backup  ").map { it.id })
    }

    // --------------------------------------------------- list mutations

    @Test
    fun `upsert appends a new id and replaces an existing one`() {
        val first = note("1", title = "one", updated = 1)
        var notes = NoteOps.upsert(emptyList(), first)
        assertEquals(listOf(first), notes)

        val edited = first.copy(title = "one edited", updatedAtMs = 2)
        notes = NoteOps.upsert(notes, edited)
        assertEquals(listOf(edited), notes)

        val second = note("2", title = "two", updated = 3)
        notes = NoteOps.upsert(notes, second)
        assertEquals(listOf(edited, second), notes)
    }

    @Test
    fun `remove drops only the matching id`() {
        val notes = listOf(note("1"), note("2"), note("3"))
        assertEquals(listOf("1", "3"), NoteOps.remove(notes, "2").map { it.id })
        assertEquals(notes, NoteOps.remove(notes, "missing"))
    }

    @Test
    fun `newNote trims the title caps both fields and stamps once`() {
        val created = NoteOps.newNote(
            id = "n1",
            nowMs = 42L,
            title = "  spaced  " + "x".repeat(NoteOps.MAX_TITLE),
            body = "y".repeat(NoteOps.MAX_BODY + 500),
            pinned = true,
        )
        assertEquals("n1", created.id)
        assertEquals(NoteOps.MAX_TITLE, created.title.length)
        assertEquals(NoteOps.MAX_BODY, created.body.length)
        assertTrue(created.title.startsWith("spaced"))
        assertTrue(created.pinned)
        assertEquals(42L, created.createdAtMs)
        assertEquals(42L, created.updatedAtMs)
    }

    @Test
    fun `blankness is both fields empty`() {
        assertTrue(NoteOps.isBlank(note("a")))
        assertTrue(NoteOps.isBlank(note("a", title = "   ")))
        assertFalse(NoteOps.isBlank(note("a", body = "\u00b7")))
        assertFalse(NoteOps.isBlank(note("a", title = "t")))
    }

    @Test
    fun `the preview line is the first non-blank body line`() {
        assertEquals("first", NoteOps.previewLine("\n  first  \nsecond\n"))
        assertEquals("", NoteOps.previewLine("   \n\t\n"))
        assertEquals("", NoteOps.previewLine(""))
    }
}
