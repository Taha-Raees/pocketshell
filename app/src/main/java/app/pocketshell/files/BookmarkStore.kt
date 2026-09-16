package app.pocketshell.files

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * User bookmarks of folders (owner iteration: "user folder bookmarks").
 *
 * ONE JSON document in the settings DataStore — the same store that keeps
 * the recent-folder record. Every read revalidates through
 * [PathSafety.validatePath] exactly like [RecentFolderStore]: a corrupted,
 * hand-edited or half-migrated document degrades to "fewer bookmarks",
 * never to an unvalidated path, and a folder that later disappeared stays
 * in the list (it is re-anchored by navigation, which surfaces an honest
 * listing error rather than silently vanishing).
 *
 * Order = insertion order (newest last). Dedup key = (kind, areaKey, path).
 * No cap: Home applies its own display window; Files shows the full list.
 */
object BookmarkStore {

    const val KEY = "folder_bookmarks"

    @Serializable
    private data class Entry(val kind: String, val areaKey: String, val path: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Entry.serializer())

    fun serialize(bookmarks: List<RecentFolder>): String =
        json.encodeToString(
            serializer,
            bookmarks.map { Entry(it.areaKind.name, it.areaKey, it.path.value) },
        )

    /** Absent/corrupt JSON → empty list; individually invalid entries dropped. */
    fun deserialize(raw: String?): List<RecentFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        val entries = runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
            ?: return emptyList()
        return entries.mapNotNull { entry ->
            val kind = AreaKind.entries.firstOrNull { it.name == entry.kind }
                ?: return@mapNotNull null
            val path = PathSafety.validatePath(entry.path) ?: return@mapNotNull null
            RecentFolder(kind, entry.areaKey, path)
        }
    }
}

/** Membership test for the ⋯ sheet label and Home row rendering. */
fun RecentFolder.isSameTarget(areaKind: AreaKind, areaKey: String, path: AreaPath): Boolean =
    this.areaKind == areaKind && this.areaKey == areaKey && this.path.value == path.value
