package app.pocketshell.files

import java.security.MessageDigest

/**
 * M7.0.0 Phase 2 — cross-domain transfers.
 *
 * Guest storage and Android storage are different domains with different
 * filesystem semantics, so a cross-domain MOVE is NEVER a rename:
 *
 *     verified copy  ->  successful verification  ->  delete source
 *
 * Verification is sha-256 of the copied bytes against a read-back of the
 * committed target (plus size equality) — the same checksum discipline the
 * runtime layer applies to its pinned artifacts. A failed verification leaves
 * the SOURCE untouched and removes the bad copy.
 *
 * Same-domain operations do NOT go through here: the areas' own copy/move use
 * native rename/recursion, which is correct and cheap inside one volume.
 */
object CrossArea {

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Copy [sourcePath] from [sourceArea] to [targetPath] in [targetArea].
     * Files stream (no size caps — ZIPs are multi-MB by nature); directories
     * recurse.
     *
     * Symlink policy for cross-domain copies: a TOP-LEVEL symlink source
     * copies as its resolved content when the source area judges that safe
     * (containment rule) — with a warning noting the conversion; symlink
     * entries nested inside a copied directory are SKIPPED with a warning.
     * No dangling or escaping links are ever created outside the guest, and a
     * target domain that cannot represent symlinks never needs to pretend it
     * can.
     */
    fun copy(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
    ): CrossResult {
        if (sourceArea.id == targetArea.id) {
            return CrossResult(
                success = false,
                reason = "source and target are the same storage area — use the area's own copy/move",
            )
        }
        val warnings = mutableListOf<String>()
        var filesCopied = 0
        val result = copyNode(
            sourceArea, sourcePath,
            targetArea, targetPath,
            warnings, topLevel = true, onFile = { filesCopied++ },
        )
        return CrossResult(
            success = result == null,
            reason = result,
            filesCopied = filesCopied,
            warnings = warnings,
        )
    }

    /**
     * Cross-domain move: [copy], and only a fully successful verified copy
     * deletes the source. If the source delete fails the result reports the
     * honest state ("copied but not moved") — data is never lost silently.
     */
    fun move(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
    ): CrossResult {
        val copied = copy(sourceArea, sourcePath, targetArea, targetPath)
        if (!copied.success) return copied
        val deleted = sourceArea.delete(sourcePath)
        if (!deleted.success) {
            return copied.copy(
                success = false,
                reason = "copied and verified, but the source could not be deleted: " +
                    "${deleted.reason ?: "unknown error"} — the source is intact",
            )
        }
        return copied
    }

    /**
     * Returns null on success, or the failure reason (fail-fast; the caller
     * surfaces partial progress via filesCopied + warnings).
     */
    private fun copyNode(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
        warnings: MutableList<String>,
        topLevel: Boolean,
        onFile: () -> Unit,
    ): String? {
        val sourceStat = sourceArea.stat(sourcePath)
            ?: return "source not found or not safely accessible: ${sourcePath.value}"
        return when (sourceStat.kind) {
            EntryKind.FILE -> {
                val error = copyFileAndVerify(sourceArea, sourcePath, targetArea, targetPath)
                if (error == null) onFile()
                error
            }

            EntryKind.SYMLINK -> {
                if (!topLevel) {
                    warnings.add(
                        "skipped symlink '${sourceStat.name}' — cross-domain copies " +
                            "never carry symlinks",
                    )
                    return null
                }
                // Top-level symlink source: openRead applies the source area's
                // containment rule; a resolvable one copies as content.
                when (val error = tryCopyTopLevelSymlink(
                    sourceArea, sourcePath, targetArea, targetPath,
                )) {
                    null -> {
                        onFile()
                        warnings.add(
                            "copied symlink '${sourceStat.name}' as its resolved content",
                        )
                        null
                    }
                    else -> {
                        warnings.add("skipped symlink '${sourceStat.name}': $error")
                        null
                    }
                }
            }

            EntryKind.DIRECTORY -> copyDirectory(
                sourceArea, sourcePath, targetArea, targetPath, warnings, onFile,
            )

            EntryKind.OTHER -> "skipped special file '${sourceStat.name}'"
        }
    }

    private fun copyDirectory(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
        warnings: MutableList<String>,
        onFile: () -> Unit,
    ): String? {
        // Target directory: reuse an existing directory (paste-into), refuse a
        // file collision, create otherwise.
        when (val existing = targetArea.stat(targetPath)) {
            null -> {
                val created = targetArea.createDirectory(targetPath)
                if (!created.success) {
                    return "could not create directory ${targetPath.value}: ${created.reason ?: "unknown"}"
                }
            }
            else -> if (existing.kind != EntryKind.DIRECTORY) {
                return "${targetPath.value} exists and is not a directory"
            }
        }
        when (val listing = sourceArea.list(sourcePath)) {
            is ListResult.Error -> return "could not list ${sourcePath.value}: ${listing.reason}"
            is ListResult.Ok -> {
                for (entry in listing.entries) {
                    val childSource = child(sourcePath, entry.name)
                        ?: return "invalid entry name '${entry.name}' in ${sourcePath.value}"
                    val childTarget = child(targetPath, entry.name)
                        ?: return "invalid entry name '${entry.name}' for ${targetPath.value}"
                    val failure = copyNode(
                        sourceArea, childSource, targetArea, childTarget,
                        warnings, topLevel = false, onFile = onFile,
                    )
                    if (failure != null) return failure
                }
            }
        }
        return null
    }

    private fun tryCopyTopLevelSymlink(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
    ): String? {
        // openRead refuses when the target is a directory or escapes; a real
        // file streams through the normal verified path. The probe stream is
        // closed immediately — copyFileAndVerify opens its own.
        return when (val probe = sourceArea.openRead(sourcePath)) {
            is StreamRead.Ok -> {
                runCatching { probe.stream.close() }
                copyFileAndVerify(sourceArea, sourcePath, targetArea, targetPath)
            }
            is StreamRead.Error -> "target is not a safely readable file inside the source storage"
        }
    }

    /**
     * One verified file copy: stream source -> atomic target write with a
     * running sha-256 of the source bytes; then read the committed target
     * back and compare digest + size. Mismatch removes the copy and fails.
     */
    private fun copyFileAndVerify(
        sourceArea: StorageArea,
        sourcePath: AreaPath,
        targetArea: StorageArea,
        targetPath: AreaPath,
    ): String? {
        val sourceStream = when (val opened = sourceArea.openRead(sourcePath)) {
            is StreamRead.Ok -> opened.stream
            is StreamRead.Error -> return opened.reason
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        val writeError: String? = try {
            when (val opened = targetArea.openWriteAtomic(targetPath)) {
                is StreamWriteOpen.Error -> opened.reason
                is StreamWriteOpen.Ok -> opened.session.use { session ->
                    try {
                        val buffer = ByteArray(BUFFER_SIZE)
                        sourceStream.use { input ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                digest.update(buffer, 0, n)
                                session.stream.write(buffer, 0, n)
                                bytesWritten += n
                            }
                        }
                        val commit = session.commit()
                        if (commit.success) null else commit.reason ?: "commit failed"
                    } catch (e: Exception) {
                        session.abort()
                        e.message ?: e.javaClass.simpleName
                    }
                }
            }
        } finally {
            runCatching { sourceStream.close() }
        }
        if (writeError != null) return writeError

        // Verify: read the committed target back and compare sha-256 + size.
        val verifyDigest = MessageDigest.getInstance("SHA-256")
        var verifiedBytes = 0L
        return try {
            when (val opened = targetArea.openRead(targetPath)) {
                is StreamRead.Error -> "copied file could not be re-opened for verification: ${opened.reason}"
                is StreamRead.Ok -> opened.stream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        verifyDigest.update(buffer, 0, n)
                        verifiedBytes += n
                    }
                    when {
                        verifiedBytes != bytesWritten -> {
                            removeFailedCopy(targetArea, targetPath)
                            "verification failed for ${targetPath.value} " +
                                "($verifiedBytes of $bytesWritten bytes) — copy removed, source intact"
                        }
                        !digest.digest().contentEquals(verifyDigest.digest()) -> {
                            removeFailedCopy(targetArea, targetPath)
                            "verification failed for ${targetPath.value} " +
                                "(checksum mismatch) — copy removed, source intact"
                        }
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            removeFailedCopy(targetArea, targetPath)
            "verification could not run for ${targetPath.value}: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun removeFailedCopy(targetArea: StorageArea, targetPath: AreaPath) {
        val existing = targetArea.stat(targetPath)
        if (existing != null && existing.kind == EntryKind.FILE) {
            targetArea.delete(targetPath)
        }
    }

    private fun child(parent: AreaPath, name: String): AreaPath? {
        if (PathSafety.validateName(name) == null) return null
        return PathSafety.validatePath(
            if (parent.value == "/") "/$name" else "${parent.value}/$name",
        )
    }
}
