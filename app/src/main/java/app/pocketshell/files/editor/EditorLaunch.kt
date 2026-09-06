package app.pocketshell.files.editor

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaPath
import app.pocketshell.files.StorageArea

/**
 * M7.0.0 Phase 6 — everything the quick text editor needs to open ONE file.
 *
 * Produced synchronously by `FilesViewModel.editorLaunch(name)` (validate +
 * resolve, no I/O) and consumed by `EditorViewModel.open(launch)`. The
 * [area] reference is the SAME instance the Files screen operates on — the
 * editor never constructs areas of its own and never bypasses the Phase 2
 * storage abstraction. If the underlying folder later leaves the explorer
 * (a removed SAF grant), the reference stays valid and its operations
 * report the truth honestly (revocation errors), which is exactly the
 * honesty contract the rest of the explorer runs on.
 *
 * A launch always names a REGULAR FILE entry from a listing ([app.pocketshell.files.EntryKind.FILE]
 * only — symlinks and directories are never launched into the editor; the
 * action sheet does not offer Open for them).
 */
data class EditorLaunch(
    val area: StorageArea,
    val areaId: AreaId,
    /** Compact area label for the editor header ("Linux", "Downloads", …). */
    val areaLabel: String,
    /** The validated file path inside the area (area-native — never faked). */
    val path: AreaPath,
    /** The listing name the file was opened from (= last path component). */
    val name: String,
)
