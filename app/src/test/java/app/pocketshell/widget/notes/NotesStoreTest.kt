package app.pocketshell.widget.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the notes store contract, at the codec boundary (the same
 * technique as the M8.3 persistence tests: the JVM suite has no Android
 * runner, so the DataStore record's encode/decode — the persisted truth —
 * is pinned here, and the repository stays the established thin wrapper).
 *
 * The discipline under test: what the user wrote is what comes back
 * (create/edit/pin/delete round-trips); a corrupt file is honest emptiness,
 * never a crash and never invented content.
 */
class NotesStoreTest {

    private fun note(
        id: String,
        title: String = "",
        body: String = "",
        pinned: Boolean = false,
        updated: Long = 0L,
    ) = StickyNote(
        id = id,
        title = title,
        body = body,
        pinned = pinned,
        createdAtMs = updated - 1000,
        updatedAtMs = updated,
    )

    // ------------------------------------------------------- round-trips

    @Test
    fun `a created note persists - encode then decode returns it`() {
        val created = NoteOps.newNote("n1", nowMs = 10L, title = "port", body = "lsof -i :8080")
        val restored = NotesCodec.decode(NotesCodec.encode(listOf(created)))
        assertEquals(listOf(created), restored)
    }

    @Test
    fun `an edited note persists - the stored record is the new content`() {
        val before = NoteOps.newNote("n1", nowMs = 10L, title = "old", body = "old body")
        val after = before.copy(title = "new", body = "new body", updatedAtMs = 20L)
        val restored = NotesCodec.decode(NotesCodec.encode(NoteOps.upsert(listOf(before), after)))
        assertEquals(listOf(after), restored)
    }

    @Test
    fun `a pin flip persists`() {
        val un = note("n1", title = "t", pinned = false)
        val pinnedList = NoteOps.upsert(listOf(un), un.copy(pinned = true))
        val restored = NotesCodec.decode(NotesCodec.encode(pinnedList))
        assertEquals(pinnedList, restored)
        assertTrue(restored.single().pinned)
    }

    @Test
    fun `a deleted note stays deleted`() {
        val notes = listOf(note("n1"), note("n2"))
        val remaining = NotesCodec.decode(NotesCodec.encode(NoteOps.remove(notes, "n1")))
        assertEquals(listOf("n2"), remaining.map { it.id })
    }

    @Test
    fun `the whole list round-trips field for field`() {
        val notes = listOf(
            NoteOps.newNote("a", 1L, "Title A", "body\nwith lines", pinned = true),
            NoteOps.newNote("b", 2L, "", "\tmixed   spacing  kept"),
            note("c", title = "unicode", body = "echo \u2014 \u00b7 \u2192", updated = 3L),
        )
        assertEquals(notes, NotesCodec.decode(NotesCodec.encode(notes)))
    }

    // ------------------------------------------------- corrupt honesty

    @Test
    fun `absent and corrupt records decode to the honest empty list`() {
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode(null))
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode(""))
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode("not json at all {"))
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode("[{\"id\": 12}]"))
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode("{\"a\":1}"))
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode("[1,2,3]"))
    }

    @Test
    fun `one corrupt record among good data corrupts the record - empty, stated`() {
        // The persisted record is ONE string: a hand-mangled file is the
        // whole record. Decode never half-parses.
        val good = NotesCodec.encode(listOf(note("n1", title = "t")))
        val mangled = good.substring(0, good.length / 2)
        assertEquals(emptyList<StickyNote>(), NotesCodec.decode(mangled))
    }

    @Test
    fun `unknown json fields are ignored not fatal`() {
        val raw = """[{"id":"n1","title":"t","body":"b","futureField":true}]"""
        val decoded = NotesCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("n1", decoded[0].id)
        assertEquals("t", decoded[0].title)
    }

    // --------------------------------------------------- shape repair

    @Test
    fun `blank ids are dropped and duplicate ids collapse to the first`() {
        val decoded = NotesCodec.sanitize(
            listOf(
                note("", title = "no id"),
                note("dup", title = "first"),
                note("dup", title = "second"),
                note("ok", title = "kept"),
            ),
        )
        assertEquals(listOf("dup", "ok"), decoded.map { it.id })
        assertEquals("first", decoded.first { it.id == "dup" }.title)
    }

    @Test
    fun `overlong fields are capped on decode`() {
        val decoded = NotesCodec.sanitize(
            listOf(note("n1", title = " " + "x".repeat(9999), body = "y".repeat(99999))),
        ).single()
        assertEquals(NoteOps.MAX_TITLE, decoded.title.length)
        assertEquals(NoteOps.MAX_BODY, decoded.body.length)
        assertTrue(decoded.title.endsWith("x") && !decoded.title.startsWith(" "))
    }

    @Test
    fun `the list cap is enforced on decode`() {
        val many = (0 until NoteOps.MAX_NOTES + 50).map { note("n$it") }
        val decoded = NotesCodec.sanitize(many)
        assertEquals(NoteOps.MAX_NOTES, decoded.size)
    }
}
