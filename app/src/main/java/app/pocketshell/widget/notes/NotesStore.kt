package app.pocketshell.widget.notes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * M8.4 — the Notes application's OWN DataStore file ("notes_store"), one
 * key, one JSON array — the HomeApplicationRepository pattern (M8.3)
 * applied to the user's own content. Process death, rotation and carousel
 * swipes all read the same file; only UI selection state lives in
 * rememberSaveable. No cloud, no accounts, no second storage mechanism.
 *
 * The codec NEVER substitutes invented content: absent/corrupt → the
 * honest empty list; a decodable record is only shape-repaired (blank ids
 * dropped, lengths capped, duplicate ids collapsed — first wins), the
 * same absent/corrupt → default discipline as HomeAppIdCodec.
 */
private val Context.notesDataStore by preferencesDataStore(name = "notes_store")

class NotesRepository(private val context: Context) {

    private val notesKey = stringPreferencesKey("notes_json")

    /** The notes, as stored. The single source of truth for the card. */
    val notes: Flow<List<StickyNote>> =
        context.notesDataStore.data.map { prefs -> NotesCodec.decode(prefs[notesKey]) }

    /**
     * The single write path — user actions only (create/edit/pin/delete),
     * never a tick. DataStore performs the file IO; the encode joins it on
     * [Dispatchers.IO] so composition never serializes a large note list.
     */
    suspend fun save(notes: List<StickyNote>) {
        withContext(Dispatchers.IO) {
            context.notesDataStore.edit { prefs -> prefs[notesKey] = NotesCodec.encode(notes) }
        }
    }
}

/** The notes codec: JSON array in, sanitized list out; corrupt → empty. */
object NotesCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(notes: List<StickyNote>): String =
        json.encodeToString(ListSerializer(StickyNote.serializer()), notes)

    fun decode(raw: String?): List<StickyNote> {
        if (raw == null) return emptyList()
        val parsed = try {
            json.decodeFromString(ListSerializer(StickyNote.serializer()), raw)
        } catch (_: Exception) {
            // Corrupt record — stated as empty, never half-parsed, never fatal.
            null
        } ?: return emptyList()
        return sanitize(parsed)
    }

    /** Shape repair, never invention: the honest decode of a decodable record. */
    fun sanitize(parsed: List<StickyNote>): List<StickyNote> =
        parsed.asSequence()
            .filter { it.id.isNotBlank() }
            .map { note ->
                note.copy(
                    title = note.title.trim().take(NoteOps.MAX_TITLE),
                    body = note.body.take(NoteOps.MAX_BODY),
                )
            }
            .distinctBy { it.id }
            .take(NoteOps.MAX_NOTES)
            .toList()
}
