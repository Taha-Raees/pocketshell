package app.pocketshell.files

/**
 * The recently-browsed folder (owner iteration: "recent open folder on Home").
 *
 * ONE record — the last directory the user actually navigated INTO, recorded
 * by [app.pocketshell.FilesViewModel] from the explorer's successful listing
 * state and persisted through the settings DataStore as three plain strings
 * (kind name / area key / path value). [AreaPath] reconstruction goes back
 * through [PathSafety.validatePath] — the stored value is NEVER trusted as a
 * path without revalidation, so a corrupted or hand-edited record degrades
 * to "no recent folder", never to an unvalidated path.
 */
data class RecentFolder(
    val areaKind: AreaKind,
    val areaKey: String,
    val path: AreaPath,
) {
    /** Last path component; area-root fallbacks use the area's own name. */
    val name: String
        get() = path.components.lastOrNull() ?: when (areaKind) {
            AreaKind.GUEST_LINUX -> "Linux"
            AreaKind.ANDROID_SHELF -> "Downloads"
            AreaKind.ANDROID_DOCUMENT_TREE -> "Folder"
        }

    /** The honest storage label under the name (same worlds the explorer uses). */
    val storageLabel: String
        get() = when (areaKind) {
            AreaKind.GUEST_LINUX -> "PocketShell Linux"
            AreaKind.ANDROID_SHELF -> "Android · Downloads"
            AreaKind.ANDROID_DOCUMENT_TREE -> "Android folder"
        }
}

object RecentFolderStore {

    /** The settings-DataStore keys the record rides on (ONE source of truth). */
    const val KEY_AREA_KIND = "recent_folder_area_kind"
    const val KEY_AREA_KEY = "recent_folder_area_key"
    const val KEY_PATH = "recent_folder_path"

    fun serialize(folder: RecentFolder): Map<String, String> = mapOf(
        KEY_AREA_KIND to folder.areaKind.name,
        KEY_AREA_KEY to folder.areaKey,
        KEY_PATH to folder.path.value,
    )

    /** Absent/unknown kind or an unvalidatable path → null (no recent folder). */
    fun deserialize(kindRaw: String?, areaKeyRaw: String?, pathRaw: String?): RecentFolder? {
        if (kindRaw == null || pathRaw == null) return null
        val kind = AreaKind.entries.firstOrNull { it.name == kindRaw } ?: return null
        val path = PathSafety.validatePath(pathRaw) ?: return null
        return RecentFolder(kind, areaKeyRaw ?: "default", path)
    }
}
