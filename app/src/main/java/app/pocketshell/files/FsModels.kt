package app.pocketshell.files

/**
 * M7.0.0 Phase 2 — storage models (docs record: worklog Task 11).
 *
 * The File Explorer exposes exactly two storage domains:
 *
 *  - GUEST_LINUX: the PocketShell-owned Alpine rootfs, accessed directly with
 *    File IO by the app process (same UID owns every byte). Guest "/" maps to
 *    the rootfs directory; guest /root is the user's home.
 *  - ANDROID_SHELF: the app's own external downloads directory (the existing
 *    Companion DownloadManager destination) — plain app-owned storage.
 *  - ANDROID_DOCUMENT_TREE: reserved (Phase 5) for SAF user-granted trees.
 *    SAF document URIs are NEVER rendered as POSIX paths; the abstraction
 *    below is capability-based so a SAF implementation can join without
 *    pretending both systems have identical filesystem semantics.
 *
 * Domains never merge: a guest path and an Android path are the same lexical
 * shape (validated [AreaPath]) but always travel with the [StorageArea] they
 * belong to. Cross-domain transfers go through [CrossArea] — verified copy,
 * then (for moves) delete of the source.
 */

/** Which physical storage domain an area represents. Domains never merge. */
enum class AreaKind {
    GUEST_LINUX,
    ANDROID_SHELF,
    ANDROID_DOCUMENT_TREE,
}

/**
 * Stable identity of one concrete area. Singleton areas (the guest rootfs,
 * the app download shelf) use the default key; future SAF tree grants use
 * one instance per grant with a grant-derived key.
 */
data class AreaId(val kind: AreaKind, val key: String = "default")

/**
 * A validated, canonical, absolute path INSIDE one storage area ("/" is the
 * area root). Construction goes exclusively through [PathSafety.validatePath],
 * which rejects path traversal ("." / ".." components), empty components,
 * NUL bytes, and any non-canonical absolute form — the UI never sees or passes
 * unvalidated filesystem paths.
 */
data class AreaPath private constructor(val value: String) {

    /** Path components without the leading "/" (empty for the area root). */
    val components: List<String> get() = value.split('/').filter { it.isNotEmpty() }

    companion object {
        /** Internal: build from an already-validated canonical string. */
        internal fun unchecked(value: String): AreaPath = AreaPath(value)
    }
}

/** What kind of node a directory entry is. */
enum class EntryKind { FILE, DIRECTORY, SYMLINK, OTHER }

/**
 * One directory-listing entry (also the stat model). Symlinks are reported
 * AS NODES — [symlinkTarget] carries the raw target string for display; the
 * area decides whether that target is safely resolvable when an operation
 * actually needs content (never implicitly).
 */
data class FsEntry(
    val name: String,
    val kind: EntryKind,
    /** Regular-file size in bytes; null for directories, symlinks, others. */
    val sizeBytes: Long?,
    /** Last-modified epoch millis; null when the filesystem does not supply one. */
    val modifiedAtMillis: Long?,
    /** Raw symlink target string (only for [EntryKind.SYMLINK]). */
    val symlinkTarget: String? = null,
)

/**
 * Capabilities an area advertises. File-backed areas (guest, shelf) support
 * everything; the Phase 5 SAF tree implementation will advertise the subset
 * the underlying document provider actually supports. Operations dispatch on
 * these instead of assuming identical semantics across domains.
 */
enum class AreaCapability {
    LIST,
    READ,
    WRITE,
    CREATE_FILE,
    CREATE_DIR,
    RENAME,
    COPY,
    MOVE,
    DELETE,
}

// --------------------------------------------------------------- result types

/**
 * Uniform operation result. [Kind.DENIED] means a policy refusal (protected
 * runtime area, path safety) — distinct from [Kind.FAILED] (I/O or state
 * problem), because the UI renders these differently (refused vs. error).
 */
data class OpResult(
    val kind: Kind,
    val reason: String? = null,
) {
    enum class Kind { SUCCESS, DENIED, FAILED }

    val success: Boolean get() = kind == Kind.SUCCESS

    companion object {
        fun success(): OpResult = OpResult(Kind.SUCCESS)
        fun denied(reason: String): OpResult = OpResult(Kind.DENIED, reason)
        fun failed(reason: String): OpResult = OpResult(Kind.FAILED, reason)
    }
}

/** Result of [StorageArea.list]. */
sealed interface ListResult {
    data class Ok(val entries: List<FsEntry>) : ListResult
    data class Error(val reason: String) : ListResult
}

/** Result of [StorageArea.readBytes] — the bounded read the quick editor uses. */
sealed interface ReadResult {
    data class Ok(val bytes: ByteArray) : ReadResult
    /** The file exists but exceeds the requested cap; [sizeBytes] is the real size. */
    data class TooLarge(val sizeBytes: Long) : ReadResult
    data class Error(val reason: String) : ReadResult
}

/**
 * Result of [StorageArea.openRead] — an uncapped streaming read used by the
 * cross-area copier (files can exceed any editor cap). The caller MUST close
 * the stream.
 */
sealed interface StreamRead {
    data class Ok(val stream: java.io.InputStream) : StreamRead
    data class Error(val reason: String) : StreamRead
}

/** Result of [StorageArea.openWriteAtomic]. A committed write lands via
 * temp-file + fsync + atomic rename — a failed/interrupted write can never
 * corrupt an existing file (the same discipline the runtime layer uses for
 * its marker).
 */
sealed interface StreamWriteOpen {
    data class Ok(val session: AtomicWriteSession) : StreamWriteOpen

    /**
     * [denied] marks a POLICY refusal (protected area, symlink write-through)
     * as opposed to an I/O/state failure — writeBytesAtomic maps this onto
     * [OpResult.Kind.DENIED] so the UI can render "refused" honestly.
     */
    data class Error(val reason: String, val denied: Boolean = false) : StreamWriteOpen
}

/** One temp-then-rename write session. Caller: write to [stream], then commit(). */
interface AtomicWriteSession : AutoCloseable {
    val stream: java.io.OutputStream

    /** Flush + fsync + atomic rename into place. Exactly one of commit/abort. */
    fun commit(): OpResult

    /** Discard the temp file. Idempotent; also the [close] default. */
    fun abort()
}

/**
 * Result of a cross-domain copy/move ([CrossArea]). [warnings] carries honest
 * per-item notes (e.g. a symlink that was skipped because its target is not
 * safely resolvable) without failing the whole operation.
 */
data class CrossResult(
    val success: Boolean,
    val reason: String? = null,
    val filesCopied: Int = 0,
    val warnings: List<String> = emptyList(),
)
