package app.pocketshell.files

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * M7.0.0 Phase 2 — the ONE file-backed [StorageArea] engine.
 *
 * Both M7.0.0 domains are rooted directories the app process owns outright:
 *
 *  - GUEST_LINUX root = the Alpine rootfs (noBackupFilesDir/runtime/rootfs).
 *    Mutations run through [MutationPolicy.guestRuntime]: every runtime-critical
 *    top-level prefix (bin, etc, lib, usr, …) refuses app-side mutation — the
 *    FROZEN M6 runtime must never be touched by explorer operations; system
 *    administration belongs in the terminal (apk/proot), where the guest's own
 *    tooling already operates safely. `/usr/local` is carved out as user area.
 *  - ANDROID_SHELF root = the app's external downloads directory
 *    (getExternalFilesDir(DIRECTORY_DOWNLOADS)) — the destination the Companion
 *    DownloadManager already writes to. Plain app-owned storage: [MutationPolicy.OPEN].
 *
 * Symlink semantics (the guest rootfs carries ~361 symlinks, some with
 * guest-view ABSOLUTE targets like /bin/busybox that only mean the right thing
 * through proot):
 *
 *  - Entries and stats report symlinks as nodes; nothing ever follows one
 *    implicitly.
 *  - A final-component symlink resolves ONLY for content operations (read /
 *    stream read) and only when the resolved canonical target stays inside the
 *    area root. Absolute targets are interpreted against the area root
 *    (proot-identical); relative targets resolve against the link's parent.
 *  - Intermediate symlink components refuse every operation (fail-closed).
 *  - Deletes and recursive walks are strictly NOFOLLOW: a symlink is deleted
 *    or copied as a node (same target string); its target is never touched.
 */
class FileDirArea private constructor(
    private val root: File,
    override val id: AreaId,
    override val displayName: String,
    override val capabilities: Set<AreaCapability>,
    private val policy: MutationPolicy,
) : StorageArea {

    /**
     * Mutation policy. The guest rootfs uses [guestRuntime]; the shelf uses
     * [OPEN]. Read-only operations are never gated — browsing, stat and reads
     * work everywhere; only MUTATION is policy-checked.
     */
    data class MutationPolicy(
        /** Top-level directory names whose contents refuse app-side mutation. */
        val protectedTopLevel: Set<String>,
        /** Absolute area paths carved OUT of protection (e.g. "/usr/local"). */
        val allowedSubTrees: Set<String>,
    ) {
        companion object {
            val OPEN = MutationPolicy(protectedTopLevel = emptySet(), allowedSubTrees = emptySet())

            /**
             * The frozen M6 runtime prefixes. Anything the rootfs extracted
             * as system territory is on this list; /usr/local is the sanctioned
             * user subtree, /root and /tmp are user areas by construction.
             */
            fun guestRuntime(): MutationPolicy = MutationPolicy(
                protectedTopLevel = setOf(
                    "bin", "dev", "etc", "lib", "lib64", "media", "mnt",
                    "opt", "proc", "run", "sbin", "srv", "sys", "usr", "var", "apks",
                ),
                allowedSubTrees = setOf("/usr/local"),
            )
        }
    }

    // -------------------------------------------------------------- factories

    companion object {
        /**
         * Creates an area rooted at [root], or null when the root does not
         * exist as a directory (guest: runtime not installed; shelf: storage
         * not mounted) — the honest "unavailable", never a half-working area.
         */
        fun create(
            root: File,
            id: AreaId,
            displayName: String,
            policy: MutationPolicy,
            capabilities: Set<AreaCapability> = AreaCapability.entries.toSet(),
        ): FileDirArea? {
            val canonical = root.canonicalFile
            if (!canonical.isDirectory) return null
            return FileDirArea(canonical, id, displayName, capabilities, policy)
        }
    }

    // -------------------------------------------------------------- internals

    /** The host node for a validated path (" maps to the root itself). */
    private fun node(path: AreaPath): File =
        if (path.value == "/") root else File(root, path.value.removePrefix("/"))

    /**
     * Mutation gate: DENIED reason for anything the policy protects, null when
     * the operation may proceed. The area root itself is always protected.
     */
    private fun mutationDenial(path: AreaPath): String? {
        val components = path.components
        if (components.isEmpty()) return "the storage root itself is protected"
        val top = components.first()
        if (top in policy.protectedTopLevel) {
            val carved = policy.allowedSubTrees.any { allow ->
                path.value == allow || path.value.startsWith("$allow/")
            }
            if (carved) return null
            return "${path.value} is inside the protected Linux runtime area — " +
                "use the terminal for system administration"
        }
        return null
    }

    private sealed interface Resolved {
        data class Ok(val file: File) : Resolved
        data class Failed(val reason: String) : Resolved
    }

    /**
     * Resolve the FINAL component for a content operation. Intermediates must
     * already be verified real ([PathSafety.parentsProblem]). A final symlink
     * resolves only when its canonical target stays inside the area root.
     */
    private fun resolveFinal(path: AreaPath): Resolved {
        val node = node(path)
        if (!PathSafety.existsNoFollow(node)) {
            return Resolved.Failed("${path.value} does not exist")
        }
        if (!PathSafety.isSymbolicLink(node)) return Resolved.Ok(node)
        val target = PathSafety.symlinkTarget(node)
            ?: return Resolved.Failed("symlink ${path.value} has an unreadable target")
        val candidate = if (target.startsWith("/")) {
            // Guest-view absolute target — the area-root interpretation is what
            // proot serves inside the guest; a host-absolute target would be a
            // different file and is never used.
            File(root, target.removePrefix("/"))
        } else {
            File(node.parentFile, target)
        }
        val canonical = candidate.canonicalFile
        return if (PathSafety.isInside(root.absolutePath, canonical.absolutePath)) {
            Resolved.Ok(canonical)
        } else {
            Resolved.Failed(
                "symlink ${path.value} resolves outside the accessible storage — " +
                    "refused (PocketShell keeps guest links inside the guest)",
            )
        }
    }

    private fun entryOf(node: File, name: String): FsEntry {
        val kind = PathSafety.kindOf(node)
        val target = if (kind == EntryKind.SYMLINK) PathSafety.symlinkTarget(node) else null
        val size = if (kind == EntryKind.FILE) {
            try {
                Files.size(node.toPath())
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
        val modified = node.lastModified().takeIf { it > 0L }
        return FsEntry(name = name, kind = kind, sizeBytes = size, modifiedAtMillis = modified, symlinkTarget = target)
    }

    private fun validate(raw: String): AreaPath? = PathSafety.validatePath(raw)

    private fun nameCollisionProblem(path: AreaPath): String? =
        if (PathSafety.existsNoFollow(node(path))) "${path.value} already exists" else null

    private fun dirIntoSelfProblem(source: AreaPath, target: AreaPath): String? {
        if (source.value == target.value) return "source and target are the same"
        if (target.value.startsWith("${source.value}/")) {
            return "cannot copy or move a directory into itself"
        }
        return null
    }

    // ------------------------------------------------------------ read-only

    override fun stat(path: AreaPath): FsEntry? {
        val p = validate(path.value) ?: return null
        if (PathSafety.parentsProblem(root, p) != null) return null
        val node = node(p)
        if (!PathSafety.existsNoFollow(node)) return null
        return entryOf(node, if (p.value == "/") "/" else node.name)
    }

    override fun list(path: AreaPath): ListResult {
        val p = validate(path.value) ?: return ListResult.Error("invalid path")
        PathSafety.parentsProblem(root, p)?.let { return ListResult.Error(it) }
        val dir: File = if (p.value == "/") {
            root
        } else {
            val node = node(p)
            when {
                !PathSafety.existsNoFollow(node) ->
                    return ListResult.Error("directory not found: ${p.value}")
                PathSafety.isSymbolicLink(node) ->
                    // A listed symlink-to-directory: resolve it under the same
                    // containment rule as content reads, never follow blindly.
                    when (val resolved = resolveFinal(p)) {
                        is Resolved.Ok -> resolved.file
                        is Resolved.Failed -> return ListResult.Error(resolved.reason)
                    }
                else -> node
            }
        }
        if (!Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            !Files.isDirectory(dir.toPath())
        ) {
            return ListResult.Error("${p.value} is not a directory")
        }
        val children = dir.listFiles()
            ?: return ListResult.Error("could not read directory ${p.value}")
        val entries = children
            .map { entryOf(it, it.name) }
            .sortedWith(
                compareBy({ it.kind != EntryKind.DIRECTORY }, { it.name.lowercase() }, { it.name }),
            )
        return ListResult.Ok(entries)
    }

    override fun readBytes(path: AreaPath, maxBytes: Long): ReadResult {
        val p = validate(path.value) ?: return ReadResult.Error("invalid path")
        PathSafety.parentsProblem(root, p)?.let { return ReadResult.Error(it) }
        val resolved = resolveFinal(p)
        if (resolved is Resolved.Failed) return ReadResult.Error(resolved.reason)
        val file = (resolved as Resolved.Ok).file
        if (!Files.isRegularFile(file.toPath())) {
            return ReadResult.Error("${path.value} is not a regular file")
        }
        val size = try {
            Files.size(file.toPath())
        } catch (e: Exception) {
            return ReadResult.Error("could not stat ${path.value}: ${e.message ?: e.javaClass.simpleName}")
        }
        if (size > maxBytes) return ReadResult.TooLarge(size)
        // Belt and braces: the file may grow between stat and read — cap the
        // read itself at maxBytes + 1 and report TooLarge from the real read.
        return try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                val buffer = ByteArray(64 * 1024)
                val out = java.io.ByteArrayOutputStream(maxOf(0, size).toInt().coerceAtLeast(16))
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return ReadResult.TooLarge(total)
                    out.write(buffer, 0, n)
                }
                ReadResult.Ok(out.toByteArray())
            }
        } catch (e: Exception) {
            ReadResult.Error("could not read ${path.value}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun openRead(path: AreaPath): StreamRead {
        val p = validate(path.value) ?: return StreamRead.Error("invalid path")
        PathSafety.parentsProblem(root, p)?.let { return StreamRead.Error(it) }
        val resolved = resolveFinal(p)
        if (resolved is Resolved.Failed) return StreamRead.Error(resolved.reason)
        val file = (resolved as Resolved.Ok).file
        if (!Files.isRegularFile(file.toPath())) {
            return StreamRead.Error("${path.value} is not a regular file")
        }
        return try {
            StreamRead.Ok(BufferedInputStream(FileInputStream(file)))
        } catch (e: Exception) {
            StreamRead.Error("could not open ${path.value}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ------------------------------------------------------------ mutations

    override fun createFile(path: AreaPath): OpResult {
        val p = validate(path.value) ?: return OpResult.failed("invalid path")
        mutationDenial(p)?.let { return OpResult.denied(it) }
        PathSafety.parentsProblem(root, p)?.let { return OpResult.failed(it) }
        nameCollisionProblem(p)?.let { return OpResult.failed(it) }
        return try {
            if (node(p).createNewFile()) OpResult.success()
            else OpResult.failed("could not create ${path.value}")
        } catch (e: IOException) {
            OpResult.failed("could not create ${path.value}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun createDirectory(path: AreaPath): OpResult {
        val p = validate(path.value) ?: return OpResult.failed("invalid path")
        mutationDenial(p)?.let { return OpResult.denied(it) }
        PathSafety.parentsProblem(root, p)?.let { return OpResult.failed(it) }
        nameCollisionProblem(p)?.let { return OpResult.failed(it) }
        return if (node(p).mkdir()) {
            OpResult.success()
        } else {
            OpResult.failed("could not create directory ${path.value}")
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
        val p = validate(path.value) ?: return StreamWriteOpen.Error("invalid path")
        mutationDenial(p)?.let { return StreamWriteOpen.Error(it, denied = true) }
        PathSafety.parentsProblem(root, p)?.let { return StreamWriteOpen.Error(it) }
        val node = node(p)
        if (PathSafety.isSymbolicLink(node)) {
            return StreamWriteOpen.Error("refusing to write through a symlink at ${path.value}", denied = true)
        }
        if (node.isDirectory) {
            return StreamWriteOpen.Error("${path.value} is a directory")
        }
        val parent = node.parentFile
        if (parent == null || !parent.isDirectory) {
            return StreamWriteOpen.Error("parent directory of ${path.value} is missing")
        }
        val tmp = File(parent, ".${node.name}.pocketshell-tmp-${UUID.randomUUID().toString().take(8)}")
        return try {
            val raw = FileOutputStream(tmp)
            val buffered = BufferedOutputStream(raw)
            StreamWriteOpen.Ok(
                FileAtomicWriteSession(
                    tmpFile = tmp,
                    target = node,
                    raw = raw,
                    buffered = buffered,
                ),
            )
        } catch (e: Exception) {
            tmp.delete()
            StreamWriteOpen.Error("could not open ${path.value} for writing: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * One temp-then-rename session. close() aborts when not committed, so a
     * forgotten session can never leave a half-written target — worst case it
     * leaves a temp file, which the shelf/tmp hygiene of later phases cleans.
     */
    private class FileAtomicWriteSession(
        private val tmpFile: File,
        private val target: File,
        private val raw: FileOutputStream,
        private val buffered: BufferedOutputStream,
    ) : AtomicWriteSession {

        override val stream: java.io.OutputStream get() = buffered
        private var finished = false

        override fun commit(): OpResult {
            if (finished) return OpResult.failed("write session already finished")
            return try {
                buffered.flush()
                try {
                    raw.fd.sync()
                } catch (_: Exception) {
                    // fsync is durability polish; the rename below is the safety net
                }
                buffered.close()
                // Re-check the symlink guard at the last possible moment.
                if (PathSafety.isSymbolicLink(target)) {
                    tmpFile.delete()
                    finished = true
                    return OpResult.denied("refusing to write through a symlink at ${target.name}")
                }
                try {
                    Files.move(
                        tmpFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmpFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                finished = true
                OpResult.success()
            } catch (e: Exception) {
                tmpFile.delete()
                finished = true
                OpResult.failed("could not save ${target.name}: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        override fun abort() {
            if (finished) return
            finished = true
            runCatching { buffered.close() }
            tmpFile.delete()
        }

        override fun close() {
            if (!finished) abort()
        }
    }

    override fun rename(path: AreaPath, newName: String): OpResult {
        val p = validate(path.value) ?: return OpResult.failed("invalid path")
        val name = PathSafety.validateName(newName)
            ?: return OpResult.failed("invalid name '$newName'")
        PathSafety.parentsProblem(root, p)?.let { return OpResult.failed(it) }
        // Rename stays inside the source's parent — both endpoints are policy-checked.
        mutationDenial(p)?.let { return OpResult.denied(it) }
        val source = node(p)
        if (!PathSafety.existsNoFollow(source)) {
            return OpResult.failed("${path.value} does not exist")
        }
        val targetPath = AreaPath.unchecked(
            if (p.value == "/") "/$name" else p.value.substringBeforeLast('/') + "/" + name,
        )
        mutationDenial(targetPath)?.let { return OpResult.denied(it) }
        val target = node(targetPath)
        if (PathSafety.existsNoFollow(target)) {
            return OpResult.failed("'$name' already exists")
        }
        return try {
            // Files.move on a symlink source moves the node itself; a directory
            // source renames the directory (POSIX rename — never follows links).
            Files.move(source.toPath(), target.toPath())
            OpResult.success()
        } catch (e: Exception) {
            OpResult.failed("could not rename ${path.value}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun copy(source: AreaPath, target: AreaPath): OpResult {
        val src = validate(source.value) ?: return OpResult.failed("invalid source path")
        val dst = validate(target.value) ?: return OpResult.failed("invalid target path")
        mutationDenial(dst)?.let { return OpResult.denied(it) }
        PathSafety.parentsProblem(root, src)?.let { return OpResult.failed(it) }
        PathSafety.parentsProblem(root, dst)?.let { return OpResult.failed(it) }
        val srcNode = node(src)
        if (!PathSafety.existsNoFollow(srcNode)) {
            return OpResult.failed("${source.value} does not exist")
        }
        if (PathSafety.kindOf(srcNode) == EntryKind.DIRECTORY) {
            dirIntoSelfProblem(src, dst)?.let { return OpResult.failed(it) }
        }
        nameCollisionProblem(dst)?.let { return OpResult.failed(it) }
        return try {
            copyNode(srcNode, node(dst))
            OpResult.success()
        } catch (e: CopyAbort) {
            OpResult.failed(e.message ?: "copy failed")
        } catch (e: Exception) {
            OpResult.failed("copy failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun move(source: AreaPath, target: AreaPath): OpResult {
        val src = validate(source.value) ?: return OpResult.failed("invalid source path")
        val dst = validate(target.value) ?: return OpResult.failed("invalid target path")
        mutationDenial(src)?.let { return OpResult.denied(it) }
        mutationDenial(dst)?.let { return OpResult.denied(it) }
        PathSafety.parentsProblem(root, src)?.let { return OpResult.failed(it) }
        PathSafety.parentsProblem(root, dst)?.let { return OpResult.failed(it) }
        val srcNode = node(src)
        if (!PathSafety.existsNoFollow(srcNode)) {
            return OpResult.failed("${source.value} does not exist")
        }
        if (PathSafety.kindOf(srcNode) == EntryKind.DIRECTORY) {
            dirIntoSelfProblem(src, dst)?.let { return OpResult.failed(it) }
        }
        nameCollisionProblem(dst)?.let { return OpResult.failed(it) }
        return try {
            // Same root = same volume: a plain rename, atomic by POSIX.
            Files.move(srcNode.toPath(), node(dst).toPath())
            OpResult.success()
        } catch (e: Exception) {
            OpResult.failed("move failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override fun delete(path: AreaPath): OpResult {
        val p = validate(path.value) ?: return OpResult.failed("invalid path")
        mutationDenial(p)?.let { return OpResult.denied(it) }
        val node = node(p)
        if (!PathSafety.existsNoFollow(node)) {
            return OpResult.failed("${path.value} does not exist")
        }
        return when (PathSafety.kindOf(node)) {
            // Symlink: delete the NODE only — the target must survive (pinned).
            EntryKind.SYMLINK, EntryKind.FILE ->
                try {
                    Files.delete(node.toPath())
                    OpResult.success()
                } catch (e: Exception) {
                    OpResult.failed("could not delete ${path.value}: ${e.message ?: e.javaClass.simpleName}")
                }
            EntryKind.DIRECTORY ->
                if (PathSafety.deleteTreeNoFollow(node)) OpResult.success()
                else OpResult.failed("could not fully delete directory ${path.value}")
            EntryKind.OTHER ->
                OpResult.failed("${path.value} is not a regular file, directory, or symlink")
        }
    }

    // ------------------------------------------------------- copy recursion

    /** Control-flow carrier for a failed copy child (unwraps into OpResult). */
    private class CopyAbort(message: String) : RuntimeException(message, null, false, false)

    /**
     * NOFOLLOW recursive copy. Symlinks are recreated as nodes with the SAME
     * target string (faithful, and safe: an identical node adds no new escape);
     * permissions are preserved on files (scripts stay executable); directory
     * walks never follow links (File.listFiles on a real dir yields real
     * children; symlink children are classified by [PathSafety.kindOf]).
     */
    private fun copyNode(src: File, dst: File) {
        when (PathSafety.kindOf(src)) {
            EntryKind.SYMLINK -> {
                val target = PathSafety.symlinkTarget(src)
                    ?: throw CopyAbort("symlink '${src.name}' has an unreadable target")
                try {
                    Files.createSymbolicLink(dst.toPath(), Path.of(target))
                } catch (e: Exception) {
                    throw CopyAbort("could not recreate symlink '${src.name}': ${e.message ?: e.javaClass.simpleName}")
                }
            }
            EntryKind.DIRECTORY -> {
                if (!dst.mkdir() && !dst.isDirectory) {
                    throw CopyAbort("could not create directory '${dst.name}'")
                }
                val children = src.listFiles() ?: throw CopyAbort("could not read directory '${src.name}'")
                for (child in children) {
                    copyNode(child, File(dst, child.name))
                }
            }
            EntryKind.FILE -> {
                try {
                    FileInputStream(src).use { input ->
                        BufferedOutputStream(FileOutputStream(dst)).use { output ->
                            input.copyTo(output, 64 * 1024)
                            output.flush()
                        }
                    }
                    copyPermissions(src, dst)
                } catch (e: Exception) {
                    throw CopyAbort("could not copy '${src.name}': ${e.message ?: e.javaClass.simpleName}")
                }
            }
            EntryKind.OTHER -> throw CopyAbort("skipping special file '${src.name}'")
        }
    }

    /** Preserve POSIX permissions (exec bits matter for scripts). Best-effort. */
    private fun copyPermissions(src: File, dst: File) {
        try {
            val perms = Files.getPosixFilePermissions(src.toPath())
            Files.setPosixFilePermissions(dst.toPath(), perms)
        } catch (_: Exception) {
            // Permission preservation is polish, not correctness — never fatal.
        }
    }
}
