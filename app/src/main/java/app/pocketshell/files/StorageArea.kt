package app.pocketshell.files

/**
 * M7.0.0 Phase 2 — the storage-area abstraction.
 *
 * One implementation per storage DOMAIN, never one per filesystem pretense:
 * the guest rootfs and the Android download shelf are both file-backed
 * ([FileDirArea]); SAF document trees (Phase 5) will implement this same
 * interface around content URIs with an honest capability subset. Nothing in
 * this interface lets an Android document pose as a Linux POSIX path — paths
 * are area-native, validated, and always interpreted by the area they belong
 * to.
 *
 * THREADING: every method performs blocking I/O. Callers invoke them from
 * Dispatchers.IO (same discipline as the runtime/package layers); the
 * implementations stay deliberately synchronous and JVM-testable.
 *
 * SAFETY CONTRACT (all implementations, pinned by tests):
 *  - Every incoming path is re-validated lexically (traversal rejected).
 *  - Intermediate path components must be REAL directories — a symlinked
 *    parent component refuses the operation fail-closed (the C12/F1 lesson:
 *    a directory symlink must never be traversed on the host side).
 *  - A final-component symlink is resolved ONLY when its resolved canonical
 *    target stays inside the area root; guest-view absolute targets ("/bin/…")
 *    are interpreted against the area root exactly like proot does. Anything
 *    else is refused with an honest reason.
 *  - Deletes and recursive walks NEVER follow symlinks: a symlink is deleted
 *    as a node; its target survives.
 *  - Mutations in protected runtime prefixes are refused (guest policy).
 *  - Writes are temp + fsync + atomic rename; a failed write never damages
 *    an existing file.
 */
interface StorageArea {

    val id: AreaId
    val displayName: String
    val capabilities: Set<AreaCapability>

    // ------------------------------------------------------------ read-only

    /**
     * Stat one path. Symlinks are reported as nodes ([EntryKind.SYMLINK] with
     * the raw target string) — this never follows a symlink. Returns null when
     * the path is invalid, does not exist, or sits behind a symlinked parent
     * component (all three mean "not safely statable").
     */
    fun stat(path: AreaPath): FsEntry?

    /** List a directory. Entries are sorted: directories first, then by name. */
    fun list(path: AreaPath): ListResult

    /**
     * Bounded read of a regular file's bytes (the quick-editor read). Refuses
     * to read through symlinks that do not resolve safely inside the area.
     */
    fun readBytes(path: AreaPath, maxBytes: Long): ReadResult

    /**
     * Uncapped streaming read of a regular file (the cross-area copier's
     * transport). Caller MUST close the returned stream.
     */
    fun openRead(path: AreaPath): StreamRead

    // ------------------------------------------------------------ mutations

    /**
     * Create an empty regular file. Refuses when anything exists at the path
     * (including a dangling symlink — checked NOFOLLOW), when a parent
     * component is a symlink, or when the path is in a protected area.
     */
    fun createFile(path: AreaPath): OpResult

    /** Create ONE directory (no mkdirs — parents must already exist). Same guards as [createFile]. */
    fun createDirectory(path: AreaPath): OpResult

    /**
     * Atomically write (or replace) a REGULAR file's bytes. Refuses to write
     * through a symlink, into a directory, or into a protected area. Overwrite
     * of an existing regular file is deliberate (editor save); collision
     * policies for copy/move live with the caller (UI confirmation), not here.
     */
    fun writeBytesAtomic(path: AreaPath, content: ByteArray): OpResult

    /**
     * Open a temp-then-atomic-rename write session (the streaming form of
     * [writeBytesAtomic]). Caller writes to [AtomicWriteSession.stream] and
     * MUST finish with exactly one of commit()/abort().
     */
    fun openWriteAtomic(path: AreaPath): StreamWriteOpen

    /**
     * Rename within the SAME directory (bare component name — never a path).
     * Refuses when the target name is invalid or exists, or when source or
     * target lies in a protected area. Symlink sources rename the node.
     */
    fun rename(path: AreaPath, newName: String): OpResult

    /**
     * Copy within THIS area. Recurses into directories; symlinks are recreated
     * as symlink nodes with the same target string (never followed); file
     * permissions are preserved. Refuses when the target exists, when a
     * directory would be copied into itself, or when the target is protected.
     */
    fun copy(source: AreaPath, target: AreaPath): OpResult

    /**
     * Move within THIS area — a native rename (atomic, same volume), symlink
     * nodes move as nodes. Refuses when the target exists, when a directory
     * would be moved into itself, or when source/target is protected.
     */
    fun move(source: AreaPath, target: AreaPath): OpResult

    /**
     * Delete a file, symlink (node only — target survives) or directory tree
     * (NOFOLLOW walk). Refuses protected areas and the area root itself.
     */
    fun delete(path: AreaPath): OpResult
}
