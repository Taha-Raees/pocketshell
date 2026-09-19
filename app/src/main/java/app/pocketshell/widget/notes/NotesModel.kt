package app.pocketshell.widget.notes

import kotlinx.serialization.Serializable

/**
 * M8.4 — one sticky note: a short-lived developer note (a command, a
 * reminder, an idea, a snippet). Plain text only, by design — v1 renders
 * no markdown (the monospace body IS the fidelity; parsing user prose
 * into markup invites rendering bugs inside a 172-208dp card for zero
 * quick-capture value). [pinned] notes sort first; the timestamps are the
 * caller's clock (the pure layer owns no time).
 */
@Serializable
data class StickyNote(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
)

/**
 * The pure notes core (JVM-tested in NotesOpsTest): the display sort, the
 * search filter and the list mutations the card performs. Data in, data
 * out — no Android, no IO, no clock — the same testability seam as the
 * M8.3 parsers (GitStatusParser, SshConfigParser).
 */
object NoteOps {

    /** Sanity caps: quick-capture notes, not a second note app. */
    const val MAX_NOTES = 100
    const val MAX_TITLE = 200
    const val MAX_BODY = 10_000

    /** A blank note is never stored — the honest absence of content. */
    fun isBlank(note: StickyNote): Boolean = note.title.isBlank() && note.body.isBlank()

    /** The factory: trims/caps like every stored write, stamps once. */
    fun newNote(id: String, nowMs: Long, title: String, body: String, pinned: Boolean = false): StickyNote =
        StickyNote(
            id = id,
            title = title.trim().take(MAX_TITLE),
            body = body.take(MAX_BODY),
            pinned = pinned,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
        )

    /** Replace by id, or append when the id is new. */
    fun upsert(notes: List<StickyNote>, note: StickyNote): List<StickyNote> {
        val index = notes.indexOfFirst { it.id == note.id }
        return if (index < 0) {
            notes + note
        } else {
            notes.toMutableList().also { it[index] = note }
        }
    }

    fun remove(notes: List<StickyNote>, id: String): List<StickyNote> =
        notes.filterNot { it.id == id }

    /**
     * The display order: pinned first, then most-recently-touched first,
     * then newest-created, then id — a total, deterministic order (equal
     * inputs always render identically).
     */
    fun sortedForDisplay(notes: List<StickyNote>): List<StickyNote> =
        notes.sortedWith(
            compareByDescending<StickyNote> { it.pinned }
                .thenByDescending { it.updatedAtMs }
                .thenByDescending { it.createdAtMs }
                .thenBy { it.id },
        )

    /** Case-insensitive substring match over title + body; a blank query matches all. */
    fun matches(note: StickyNote, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        val lower = needle.lowercase()
        return note.title.lowercase().contains(lower) || note.body.lowercase().contains(lower)
    }

    fun filtered(notes: List<StickyNote>, query: String): List<StickyNote> =
        if (query.isBlank()) notes else notes.filter { matches(it, query) }

    /** The one-line list preview: the first non-blank body line. */
    fun previewLine(body: String): String =
        body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(160)
            .orEmpty()
}
