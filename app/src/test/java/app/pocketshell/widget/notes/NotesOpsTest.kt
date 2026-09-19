package app.pocketshell.widget.notes

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the pure notes core: pin-first display ordering, search
 * filtering, the list mutations and (M8.4.3) the relative-time badge.
 * Deterministic: no clock, no IO — time arrives as parameters.
 */
class NotesOpsTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcEpoch(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

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

    // -------------------------------------------- relative time (M8.4.3)

    private val SECOND = 1_000L
    private val MINUTE = 60L * SECOND
    private val HOUR = 60L * MINUTE
    private val DAY = 24L * HOUR
    private val WEEK = 7L * DAY

    @Test
    fun `under a minute reads as now - including clock skew into the future`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("now", NoteOps.relativeTime(now, now, utc))
        assertEquals("now", NoteOps.relativeTime(now, now - 59 * SECOND, utc))
        assertEquals("now", NoteOps.relativeTime(now, now + 5 * MINUTE, utc))
    }

    @Test
    fun `the minute boundary - 60s is 1m through 59m`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("1m", NoteOps.relativeTime(now, now - MINUTE, utc))
        assertEquals("1m", NoteOps.relativeTime(now, now - 119 * SECOND, utc))
        assertEquals("59m", NoteOps.relativeTime(now, now - 59 * MINUTE, utc))
    }

    @Test
    fun `the hour boundary - 60m is 1h through 23h`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("1h", NoteOps.relativeTime(now, now - HOUR, utc))
        assertEquals("23h", NoteOps.relativeTime(now, now - 23 * HOUR, utc))
    }

    @Test
    fun `the day boundary - 24h is 1d through 6d`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("1d", NoteOps.relativeTime(now, now - DAY, utc))
        assertEquals("6d", NoteOps.relativeTime(now, now - 6 * DAY, utc))
    }

    @Test
    fun `the week boundary - 7d is 1w through 3w`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("1w", NoteOps.relativeTime(now, now - WEEK, utc))
        assertEquals("3w", NoteOps.relativeTime(now, now - 3 * WEEK, utc))
    }

    @Test
    fun `four weeks minus one millisecond is the last relative bucket`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("3w", NoteOps.relativeTime(now, now - (4 * WEEK - 1), utc))
    }

    @Test
    fun `at four weeks the badge becomes a short date of the current year`() {
        // Sep 19 2025 - 28d = Aug 22 2025 — same year, so no year suffix.
        val now = utcEpoch(2025, 9, 19)
        assertEquals("Aug 22", NoteOps.relativeTime(now, now - 4 * WEEK, utc))
    }

    @Test
    fun `a note from another year carries the two-digit year`() {
        val now = utcEpoch(2025, 9, 19)
        assertEquals("Jul 15", NoteOps.relativeTime(now, utcEpoch(2025, 7, 15), utc))
        assertEquals("Mar 15 '24", NoteOps.relativeTime(now, utcEpoch(2024, 3, 15), utc))
    }

    @Test
    fun `the new-year edge - a note from the old year still names its own year`() {
        val now = utcEpoch(2025, 1, 2)
        assertEquals("Dec 1 '24", NoteOps.relativeTime(now, utcEpoch(2024, 12, 1), utc))
    }
}
