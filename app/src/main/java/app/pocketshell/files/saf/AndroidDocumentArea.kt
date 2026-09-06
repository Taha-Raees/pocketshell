package app.pocketshell.files.saf

import app.pocketshell.files.AreaCapability
import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.AreaPath
import app.pocketshell.files.AtomicWriteSession
import app.pocketshell.files.EntryKind
import app.pocketshell.files.FsEntry
import app.pocketshell.files.ListResult
import app.pocketshell.files.OpResult
import app.pocketshell.files.PathSafety
import app.pocketshell.files.ReadResult
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StreamRead
import app.pocketshell.files.StreamWriteOpen
import java.io.FileNotFoundException
import java.io.OutputStream

/**
 * M7.0.0 Phase 5 — the StorageArea implementation for a USER-GRANTED SAF
 * document tree (an ACTION_OPEN_DOCUMENT_TREE grant).
 *
 * This is an Android STORAGE AREA, never a Linux path: [AreaPath] here is a
 * validated navigation spine whose components are resolved against the
 * provider's display names, one child lookup at a time. A content:// URI is
 * never rendered as or converted into a POSIX path.
 *
 * HONEST CAPABILITY MODEL (pinned by SafAreaTest):
 *  - LIST / READ / WRITE / CREATE_FILE / CREATE_DIR / RENAME / COPY / MOVE /
 *    DELETE are genuinely implemented through the [DocumentBackend] — no
 *    faked support, and every failure surfaces verbatim.
 *  - SAF has NO symlink concept: entries are FILE or DIRECTORY, never
 *    SYMLINK. (Cross-domain copies never carry symlinks — the Phase 2 rule —
 *    so nothing is lost.)
 *  - SAF cannot atomically REPLACE a document. The write path therefore
 *    stages a hidden temp document and swaps on commit: existing → hidden
 *    backup name, temp → final name, backup deleted last. A failed swap
 *    RESTORES the backup — a failed write never damages the existing file
 *    (the honest equivalent of the file-backed area's temp+fsync+rename).
 *  - Document ids are opaque, so every path lookup walks the tree by display
 *    name ([resolve]); depth is bounded by the validated path itself.
 *
 * REVOCATION (the safety rule this area exists for): every backend call may
 * throw [SecurityException] / [FileNotFoundException] when Android revokes
 * the grant or the provider disappears. These become honest OpResult /
 * ListResult / ReadResult errors — never an exception escaping to the UI and
 * never a crash. The first SecurityException also fires [onAccessLost] (at
 * most once per area instance), which the ViewModel uses to flip the folder
 * state to REVOKED so the Files screen can offer Reconnect / Remove.
 *
 * THREADING: blocking, like every StorageArea — callers use Dispatchers.IO.
 */
class AndroidDocumentArea private constructor(
    override val id: AreaId,
    override val displayName: String,
    private val backend: DocumentBackend,
    private val onAccessLost: (() -> Unit)?,
    /** Access-probe result at construction time (false = revoked folder). */
    val startsAvailable: Boolean,
) : StorageArea {

    override val capabilities: Set<AreaCapability> = setOf(
        AreaCapability.LIST,
        AreaCapability.READ,
        AreaCapability.WRITE,
        AreaCapability.CREATE_FILE,
        AreaCapability.CREATE_DIR,
        AreaCapability.RENAME,
        AreaCapability.COPY,
        AreaCapability.MOVE,
        AreaCapability.DELETE,
    )

    /** Set after the first SecurityException — one honest announcement per instance. */
    private var accessLostAnnounced = false

    /** Sticky fact: the grant was lost at least once during this area's life. */
    private var accessLost = false

    private fun announceAccessLost() {
        accessLost = true
        if (!accessLostAnnounced) {
            accessLostAnnounced = true
            onAccessLost?.invoke()
        }
    }

    companion object {
        /** The AreaId key of a SAF area is its tree URI string. */
        fun areaIdFor(treeUri: String): AreaId = AreaId(AreaKind.ANDROID_DOCUMENT_TREE, treeUri)

        /**
         * Probe access once at construction: SecurityException /
         * FileNotFoundException / a malformed URI mark the folder revoked —
         * it still becomes an area (so the switcher shows it and the banner
         * can offer Reconnect / Remove), but every operation returns the
         * honest revoked error. Other probe failures leave the folder
         * available; its first real operation surfaces the truth anyway.
         */
        fun create(
            id: AreaId,
            displayName: String,
            backend: DocumentBackend,
            onAccessLost: (() -> Unit)? = null,
        ): AndroidDocumentArea {
            val available = try {
                backend.root()
                true
            } catch (_: SecurityException) {
                false
            } catch (_: FileNotFoundException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
            return AndroidDocumentArea(id, displayName, backend, onAccessLost, available)
        }

        private const val TEMP_PREFIX = ".pp-tmp-"
        private const val BACKUP_PREFIX = ".pp-old-"
        private const val BUFFER_SIZE = 64 * 1024
        private const val REVOKED_MESSAGE =
            "Access to this folder is no longer available — reconnect or remove it."
    }

    // ------------------------------------------------------------- resolution

    /** The root document, or null when access is gone (revoked/unavailable). */
    private fun rootOrNull(): SafDoc? = try {
        if (startsAvailable) backend.root() else null
    } catch (_: SecurityException) {
        announceAccessLost()
        null
    } catch (_: FileNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /**
     * Resolve an area path against display names, one component at a time.
     * Null when the path does not resolve or access is gone — callers turn
     * that into honest per-operation errors.
     */
    private fun resolve(path: AreaPath): SafDoc? {
        var current = rootOrNull() ?: return null
        for (component in path.components) {
            current = try {
                backend.resolveChild(current, component) ?: return null
            } catch (_: SecurityException) {
                announceAccessLost()
                return null
            } catch (_: FileNotFoundException) {
                return null
            } catch (_: RuntimeException) {
                return null
            }
        }
        return current
    }

    /** Resolve the PARENT directory of a non-root path, or null. */
    private fun resolveParent(path: AreaPath): SafDoc? {
        val components = path.components
        if (components.isEmpty()) return null
        val parentValue = "/" + components.dropLast(1).joinToString("/")
        return resolve(AreaPath.unchecked(parentValue))
    }

    /**
     * Run one backend call, mapping every exception to an honest message.
     * SecurityException additionally announces the lost access exactly once.
     */
    private fun <T> guarded(operation: String, call: () -> T): SafOutcome<T> = try {
        SafOutcome.Ok(call())
    } catch (e: SecurityException) {
        announceAccessLost()
        SafOutcome.Error(REVOKED_MESSAGE)
    } catch (_: FileNotFoundException) {
        SafOutcome.Error("$operation: the document no longer exists or is no longer accessible")
    } catch (e: Exception) {
        SafOutcome.Error("$operation: ${e.message ?: e.javaClass.simpleName}")
    }

    private sealed interface SafOutcome<T> {
        data class Ok<T>(val value: T) : SafOutcome<T>
        data class Error<T>(val reason: String) : SafOutcome<T>
    }

    private fun notAccessible(path: AreaPath): String = when {
        !startsAvailable -> REVOKED_MESSAGE
        accessLost -> REVOKED_MESSAGE
        else -> "${path.value} does not exist or is not accessible"
    }

    // ------------------------------------------------------------------ read

    private fun entryOf(doc: SafDoc): FsEntry = FsEntry(
        name = doc.name,
        kind = if (doc.isDirectory) EntryKind.DIRECTORY else EntryKind.FILE,
        sizeBytes = if (doc.isDirectory) null else doc.sizeBytes,
        modifiedAtMillis = doc.modifiedAtMillis?.takeIf { it > 0 },
        symlinkTarget = null, // SAF has no symlinks — never faked
    )

    override fun stat(path: AreaPath): FsEntry? = resolve(path)?.let { entryOf(it) }

    override fun list(path: AreaPath): ListResult {
        val doc = resolve(path) ?: return ListResult.Error(notAccessible(path))
        if (!doc.isDirectory) return ListResult.Error("${path.value} is not a folder")
        return when (val children = guarded("could not list ${path.value}") {
            backend.listChildren(doc)
        }) {
            is SafOutcome.Error -> ListResult.Error(children.reason)
            is SafOutcome.Ok -> ListResult.Ok(
                children.value.map { entryOf(it) }.sortedWith(
                    compareByDescending<FsEntry> { it.kind == EntryKind.DIRECTORY }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                ),
            )
        }
    }

    override fun readBytes(path: AreaPath, maxBytes: Long): ReadResult {
        val doc = resolve(path) ?: return ReadResult.Error(notAccessible(path))
        if (doc.isDirectory) return ReadResult.Error("${path.value} is a folder, not a file")
        doc.sizeBytes?.takeIf { it > maxBytes }?.let { return ReadResult.TooLarge(it) }
        return when (val opened = openRead(path)) {
            is StreamRead.Error -> ReadResult.Error(opened.reason)
            is StreamRead.Ok -> try {
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                opened.stream.use { input ->
                    while (total <= maxBytes) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                    }
                }
                if (total > maxBytes) ReadResult.TooLarge(total) else ReadResult.Ok(out.toByteArray())
            } catch (e: Exception) {
                ReadResult.Error("could not read ${path.value}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun openRead(path: AreaPath): StreamRead {
        val doc = resolve(path) ?: return StreamRead.Error(notAccessible(path))
        if (doc.isDirectory) return StreamRead.Error("${path.value} is a folder, not a file")
        return when (val opened = guarded("could not open ${path.value}") {
            backend.openInputStream(doc)
        }) {
            is SafOutcome.Error -> StreamRead.Error(opened.reason)
            is SafOutcome.Ok -> StreamRead.Ok(opened.value)
        }
    }

    // ------------------------------------------------------------- mutations

    override fun createFile(path: AreaPath): OpResult = createEntry(path, isDirectory = false)

    override fun createDirectory(path: AreaPath): OpResult = createEntry(path, isDirectory = true)

    private fun createEntry(path: AreaPath, isDirectory: Boolean): OpResult {
        if (path.components.isEmpty()) {
            return OpResult.denied("the storage root itself cannot be created")
        }
        val parentDoc = resolveParent(path) ?: return OpResult.failed(notAccessible(path))
        val name = path.components.last()
        return when (val existing = guarded("could not inspect ${path.value}") {
            backend.resolveChild(parentDoc, name)
        }) {
            is SafOutcome.Error -> OpResult.failed(existing.reason)
            is SafOutcome.Ok -> {
                if (existing.value != null) return OpResult.failed("${path.value} already exists")
                when (val created = guarded("could not create ${path.value}") {
                    if (isDirectory) {
                        backend.createDirectory(parentDoc, name)
                    } else {
                        backend.createFile(parentDoc, name, "application/octet-stream")
                    }
                }) {
                    is SafOutcome.Error -> OpResult.failed(created.reason)
                    is SafOutcome.Ok -> OpResult.success()
                }
            }
        }
    }

    override fun writeBytesAtomic(path: AreaPath, content: ByteArray): OpResult =
        when (val opened = openWriteAtomic(path)) {
            is StreamWriteOpen.Error ->
                if (opened.denied) OpResult.denied(opened.reason)
                else OpResult.failed(opened.reason)
            is StreamWriteOpen.Ok -> opened.session.use { session ->
                try {
                    session.stream.write(content)
                    session.commit()
                } catch (e: Exception) {
                    session.abort()
                    OpResult.failed("could not write ${path.value}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }

    override fun openWriteAtomic(path: AreaPath): StreamWriteOpen {
        if (path.components.isEmpty()) {
            return StreamWriteOpen.Error("the storage root itself is not a file")
        }
        val parentDoc = resolveParent(path) ?: return StreamWriteOpen.Error(notAccessible(path))
        val name = path.components.last()
        return when (val existing = guarded("could not inspect ${path.value}") {
            backend.resolveChild(parentDoc, name)
        }) {
            is SafOutcome.Error -> StreamWriteOpen.Error(existing.reason)
            is SafOutcome.Ok -> when (val found = existing.value) {
                null -> openTempSession(parentDoc, name, existingDocument = null)
                else -> if (found.isDirectory) {
                    StreamWriteOpen.Error("${path.value} is a folder — cannot write a file over it")
                } else {
                    openTempSession(parentDoc, name, existingDocument = found)
                }
            }
        }
    }

    /**
     * The SAF write session: create a hidden temp document, stream into it,
     * and swap it into place on commit (backup-first when a file already
     * exists, so a failed swap restores the old content).
     */
    private fun openTempSession(
        parentDoc: SafDoc,
        finalName: String,
        existingDocument: SafDoc?,
    ): StreamWriteOpen {
        val tempName = freeHiddenName(parentDoc, TEMP_PREFIX, finalName)
        return when (val created = guarded("could not stage a temp file") {
            backend.createFile(parentDoc, tempName, "application/octet-stream")
        }) {
            is SafOutcome.Error -> StreamWriteOpen.Error(created.reason)
            is SafOutcome.Ok -> when (val stream = guarded("could not open the temp file for writing") {
                backend.openOutputStream(created.value, truncate = true)
            }) {
                is SafOutcome.Error -> {
                    runCatching { backend.deleteDocument(created.value) }
                    StreamWriteOpen.Error(stream.reason)
                }
                is SafOutcome.Ok -> StreamWriteOpen.Ok(
                    SafAtomicWriteSession(parentDoc, created.value, finalName, existingDocument, stream.value),
                )
            }
        }
    }

    /** A unique hidden sibling name: "<prefix><name>" then "<prefix><name>.2", … */
    private fun freeHiddenName(parentDoc: SafDoc, prefix: String, finalName: String): String {
        var candidate = "$prefix$finalName"
        var attempt = 1
        while (true) {
            val taken = try {
                backend.resolveChild(parentDoc, candidate) != null
            } catch (_: Exception) {
                false
            }
            if (!taken) return candidate
            attempt += 1
            candidate = "$prefix$finalName.$attempt"
        }
    }

    private inner class SafAtomicWriteSession(
        private val parentDoc: SafDoc,
        private val tempDoc: SafDoc,
        private val finalName: String,
        private val existingDocument: SafDoc?,
        override val stream: OutputStream,
    ) : AtomicWriteSession {

        private var finished = false

        override fun commit(): OpResult {
            if (finished) return OpResult.failed("the write session was already finished")
            finished = true
            try {
                stream.flush()
                stream.close()
            } catch (e: Exception) {
                runCatching { backend.deleteDocument(tempDoc) }
                return OpResult.failed(
                    "could not finish writing \"$finalName\": ${e.message ?: e.javaClass.simpleName}",
                )
            }
            val failure = swapTempIntoPlace() ?: return OpResult.success()
            val restored = if (failure.restored) " — the previous file was restored" else ""
            return OpResult.failed("could not write \"$finalName\": ${failure.reason}$restored")
        }

        /**
         * Swap the temp document into its final place. Returns null on
         * success; otherwise the reason plus whether the previous content
         * was successfully restored.
         */
        private fun swapTempIntoPlace(): SwapFailure? {
            var backupDoc: SafDoc? = null
            if (existingDocument != null) {
                val backupName = freeHiddenName(parentDoc, BACKUP_PREFIX, finalName)
                val backup = guarded("could not stage the previous file") {
                    backend.renameDocument(existingDocument, backupName)
                }
                backupDoc = (backup as? SafOutcome.Ok)?.value
                if (backupDoc == null) {
                    runCatching { backend.deleteDocument(tempDoc) }
                    val reason = (backup as? SafOutcome.Error)?.reason ?: "the provider refused"
                    return SwapFailure(reason, restored = false)
                }
            }
            return when (val renamed = guarded("could not write \"$finalName\"") {
                backend.renameDocument(tempDoc, finalName)
            }) {
                is SafOutcome.Ok -> {
                    if (renamed.value.name != finalName) {
                        // The provider altered the name — undo and report honestly.
                        runCatching { backend.renameDocument(renamed.value, tempDoc.name) }
                        val restored = restoreBackup(backupDoc)
                        return SwapFailure("the provider altered the name", restored = restored)
                    }
                    if (backupDoc != null) {
                        val backup = backupDoc
                        val removed = guarded("could not delete the replaced file") {
                            backend.deleteDocument(backup)
                        }
                        if (removed is SafOutcome.Error) {
                            return SwapFailure(
                                "replaced, but the previous file could not be deleted: ${removed.reason} " +
                                    "(a hidden \".pp-old-…\" copy remains)",
                                restored = false,
                            )
                        }
                    }
                    null
                }
                is SafOutcome.Error -> {
                    runCatching { backend.deleteDocument(tempDoc) }
                    SwapFailure(renamed.reason, restored = restoreBackup(backupDoc))
                }
            }
        }

        /** Rename the backup back to the final name; true when restored. */
        private fun restoreBackup(backupDoc: SafDoc?): Boolean {
            if (backupDoc == null) return false
            return runCatching {
                backend.renameDocument(backupDoc, finalName).name == finalName
            }.getOrDefault(false)
        }

        override fun abort() {
            if (finished) return
            finished = true
            runCatching { stream.close() }
            runCatching { backend.deleteDocument(tempDoc) }
        }

        override fun close() = abort()
    }

    private data class SwapFailure(val reason: String, val restored: Boolean)

    override fun rename(path: AreaPath, newName: String): OpResult {
        val name = PathSafety.validateName(newName)
            ?: return OpResult.failed("invalid name '$newName'")
        if (path.components.isEmpty()) {
            return OpResult.denied("the storage root itself cannot be renamed")
        }
        val parentDoc = resolveParent(path) ?: return OpResult.failed(notAccessible(path))
        return when (val existing = guarded("could not inspect \"$newName\"") {
            backend.resolveChild(parentDoc, name)
        }) {
            is SafOutcome.Error -> OpResult.failed(existing.reason)
            is SafOutcome.Ok -> {
                if (existing.value != null) return OpResult.failed("'$name' already exists")
                val doc = resolve(path) ?: return OpResult.failed(notAccessible(path))
                when (val renamed = guarded("could not rename ${path.value}") {
                    backend.renameDocument(doc, name)
                }) {
                    is SafOutcome.Error -> OpResult.failed(renamed.reason)
                    is SafOutcome.Ok ->
                        if (renamed.value.name == name) {
                            OpResult.success()
                        } else {
                            // The provider altered the name — undo and report.
                            runCatching { backend.renameDocument(renamed.value, doc.name) }
                            OpResult.failed("the provider did not apply the new name \"$name\"")
                        }
                }
            }
        }
    }

    override fun copy(source: AreaPath, target: AreaPath): OpResult {
        sourceAndTargetProblems(source, target)?.let { return it }
        val sourceDoc = resolve(source) ?: return OpResult.failed(notAccessible(source))
        val failure = copyNode(sourceDoc, source, target)
        return if (failure == null) OpResult.success() else OpResult.failed(failure)
    }

    /** Same refusals as the file-backed engine: same path, dir-into-self. */
    private fun sourceAndTargetProblems(source: AreaPath, target: AreaPath): OpResult? {
        if (source.value == target.value) {
            return OpResult.failed("source and target are the same")
        }
        if (target.value.startsWith("${source.value}/")) {
            return OpResult.failed("cannot copy or move a directory into itself")
        }
        return null
    }

    /**
     * Recursive in-area copy. Returns null on success, else the reason.
     * Files are streamed; the written size is verified against the source's
     * reported size when the provider supplies one (a mismatch removes the
     * partial copy). Cross-area copies get the full sha-256 verification in
     * [app.pocketshell.files.CrossArea]; inside one provider this size check
     * is the honest local equivalent.
     */
    private fun copyNode(sourceDoc: SafDoc, source: AreaPath, target: AreaPath): String? {
        if (sourceDoc.isDirectory) {
            val created = guarded("could not create ${target.value}") {
                createDirectoryStrict(target)
            }
            if (created is SafOutcome.Error) return created.reason
            val children = guarded("could not list ${source.value}") {
                backend.listChildren(sourceDoc)
            }
            val listing = when (children) {
                is SafOutcome.Error -> return children.reason
                is SafOutcome.Ok -> children.value
            }
            for (child in listing) {
                val childSource = PathSafety.validatePath("${source.value.trimEnd('/')}/${child.name}")
                    ?: return "entry '${child.name}' cannot be addressed inside this storage"
                val childTarget = PathSafety.validatePath("${target.value.trimEnd('/')}/${child.name}")
                    ?: return "entry '${child.name}' cannot be addressed inside this storage"
                val failure = copyNode(child, childSource, childTarget)
                if (failure != null) return failure
            }
            return null
        }
        // Regular file: stream, then verify the size the provider reported.
        val expectedSize = sourceDoc.sizeBytes
        return when (val staged = stageFileCopy(sourceDoc, source, target)) {
            is SafOutcome.Error -> staged.reason
            is SafOutcome.Ok -> {
                if (expectedSize != null && staged.value != expectedSize) {
                    val partial = resolve(target)
                    if (partial != null) runCatching { backend.deleteDocument(partial) }
                    "size verification failed for ${target.value} — the partial copy was removed"
                } else {
                    null
                }
            }
        }
    }

    /** createDirectory that refuses an existing target (engine parity). */
    private fun createDirectoryStrict(target: AreaPath): SafDoc {
        val parent = resolveParent(target)
            ?: throw IllegalStateException("${target.value} does not exist or is not accessible")
        val name = target.components.last()
        if (backend.resolveChild(parent, name) != null) {
            throw IllegalStateException("${target.value} already exists")
        }
        return backend.createDirectory(parent, name)
    }

    /** Stream one file into a freshly created target document; returns bytes written. */
    private fun stageFileCopy(
        sourceDoc: SafDoc,
        source: AreaPath,
        target: AreaPath,
    ): SafOutcome<Long> {
        val parent = resolveParent(target) ?: return SafOutcome.Error(notAccessible(target))
        val name = target.components.last()
        val created = try {
            if (backend.resolveChild(parent, name) != null) {
                return SafOutcome.Error("${target.value} already exists")
            }
            backend.createFile(parent, name, "application/octet-stream")
        } catch (e: Exception) {
            return SafOutcome.Error("could not create ${target.value}: ${e.message ?: e.javaClass.simpleName}")
        }
        var written = 0L
        return try {
            val input = when (val opened = openRead(source)) {
                is StreamRead.Error -> return SafOutcome.Error(opened.reason)
                is StreamRead.Ok -> opened.stream
            }
            val output = backend.openOutputStream(created, truncate = true)
            input.use { inputStream ->
                output.use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = inputStream.read(buffer)
                        if (n < 0) break
                        outputStream.write(buffer, 0, n)
                        written += n
                    }
                }
            }
            SafOutcome.Ok(written)
        } catch (e: Exception) {
            runCatching { backend.deleteDocument(created) }
            SafOutcome.Error("could not copy ${source.value}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun move(source: AreaPath, target: AreaPath): OpResult {
        sourceAndTargetProblems(source, target)?.let { return it }
        val parentDoc = resolveParent(source) ?: return OpResult.failed(notAccessible(source))
        val sourceDoc = resolve(source) ?: return OpResult.failed(notAccessible(source))
        val targetParent = resolveParent(target) ?: return OpResult.failed(notAccessible(target))
        val name = target.components.last()
        return when (val existing = guarded("could not inspect ${target.value}") {
            backend.resolveChild(targetParent, name)
        }) {
            is SafOutcome.Error -> OpResult.failed(existing.reason)
            is SafOutcome.Ok -> {
                if (existing.value != null) return OpResult.failed("${target.value} already exists")
                val moved = if (parentDoc.id == targetParent.id) {
                    guarded("could not move ${source.value}") { backend.renameDocument(sourceDoc, name) }
                } else {
                    guarded("could not move ${source.value}") {
                        backend.moveDocument(sourceDoc, parentDoc, targetParent)
                    }
                }
                when (moved) {
                    is SafOutcome.Error -> OpResult.failed(moved.reason)
                    is SafOutcome.Ok -> {
                        var placed = moved.value
                        // moveDocument keeps the display name — complete the
                        // move-to-[name] contract with a rename when needed.
                        if (placed.name != name) {
                            when (val renamed = guarded("could not name the moved document") {
                                backend.renameDocument(placed, name)
                            }) {
                                is SafOutcome.Error -> return OpResult.failed(
                                    "moved as \"${placed.name}\" but could not be named \"$name\": ${renamed.reason}",
                                )
                                is SafOutcome.Ok -> placed = renamed.value
                            }
                        }
                        if (placed.name == name) {
                            OpResult.success()
                        } else {
                            OpResult.failed(
                                "the provider did not keep the name \"$name\" — moved as \"${placed.name}\"",
                            )
                        }
                    }
                }
            }
        }
    }

    override fun delete(path: AreaPath): OpResult {
        if (path.components.isEmpty()) {
            return OpResult.denied("the storage root itself cannot be deleted")
        }
        val doc = resolve(path) ?: return OpResult.failed(notAccessible(path))
        return when (val removed = guarded("could not delete ${path.value}") {
            backend.deleteDocument(doc)
        }) {
            is SafOutcome.Error -> OpResult.failed(removed.reason)
            is SafOutcome.Ok -> OpResult.success()
        }
    }
}
