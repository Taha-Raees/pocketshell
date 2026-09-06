package app.pocketshell.files.saf

import app.pocketshell.files.AreaPath
import app.pocketshell.files.EntryKind
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StreamRead
import app.pocketshell.files.StreamWriteOpen
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * M7.0.0 Phase 5 — verified transfers between one-shot Android document URIs
 * (import/export pickers) and PocketShell storage areas.
 *
 * The picked content URI is NOT a storage area — it is a one-shot document
 * outside the [StorageArea] abstraction, so it enters here as an injected
 * stream factory. Everything on the AREA side still goes through the
 * abstraction (openWriteAtomic / openRead / delete): no bypass, and the
 * verification discipline is the SAME one CrossArea uses for cross-domain
 * copies — sha-256 of the copied bytes against a read-back of the committed
 * target, plus size equality. A failed verification removes the copy and
 * reports honestly; a failed export never pretends the file was saved.
 *
 * PURE (java.io streams only) — directly JVM-testable with real temp areas.
 */
object SafTransfers {

    private const val BUFFER_SIZE = 64 * 1024

    /** Uniform honest result of one transfer. */
    data class TransferResult(
        val success: Boolean,
        val reason: String? = null,
        val bytesCopied: Long = 0,
    )

    // --------------------------------------------------------------- import

    /**
     * Import one picked document into [target] (a validated child of the
     * CURRENT directory) in [targetArea]. [openSource] must yield a FRESH
     * stream each call (content URIs are re-openable).
     *
     * [replace] must come ONLY from an explicit user confirmation of a
     * collision: replace = delete-the-existing-then-import, the same uniform
     * Replace composition the Phase 4 ops layer uses. Without replace an
     * existing destination refuses the import — never a silent overwrite.
     */
    fun importDocument(
        targetArea: StorageArea,
        target: AreaPath,
        openSource: () -> InputStream,
        replace: Boolean,
    ): TransferResult {
        val existing = targetArea.stat(target)
        if (existing != null) {
            if (!replace) {
                return TransferResult(
                    success = false,
                    reason = "${target.value} already exists in ${targetArea.displayName} — " +
                        "choose Replace to overwrite it",
                )
            }
            val removed = targetArea.delete(target)
            if (!removed.success) {
                return TransferResult(
                    success = false,
                    reason = "could not replace ${target.value}: ${removed.reason ?: "delete failed"} " +
                        "— nothing was changed",
                )
            }
        }
        val sourceStream = try {
            openSource()
        } catch (e: Exception) {
            return TransferResult(
                success = false,
                reason = "could not open the selected file: ${e.message ?: e.javaClass.simpleName}",
            )
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val writeError: String? = try {
            when (val opened = targetArea.openWriteAtomic(target)) {
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
                                written += n
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
        if (writeError != null) {
            return TransferResult(success = false, reason = writeError, bytesCopied = written)
        }
        return verifyCommitted(
            targetArea = targetArea,
            target = target,
            expectedDigest = digest.digest(),
            expectedSize = written,
            onFailed = { targetArea.delete(target) },
        )
    }

    // --------------------------------------------------------------- export

    /**
     * Export one [source] FILE from [sourceArea] to a create-document target
     * ([openOutput] streams into it; [openVerify] re-opens it for the
     * read-back verification; [onRemoveCreated] best-effort removes the
     * created document when verification fails, so no bad file is left
     * behind pretending to be a save).
     *
     * Directories are refused: file sharing/export only (the product scope).
     */
    fun exportDocument(
        sourceArea: StorageArea,
        source: AreaPath,
        openOutput: () -> OutputStream,
        openVerify: () -> InputStream,
        onRemoveCreated: (() -> Unit)? = null,
    ): TransferResult {
        val stat = sourceArea.stat(source)
            ?: return TransferResult(false, "${source.value} does not exist or is not accessible")
        if (stat.kind == EntryKind.DIRECTORY) {
            return TransferResult(false, "Folders cannot be exported — only files.")
        }
        val sourceStream = when (val opened = sourceArea.openRead(source)) {
            is StreamRead.Error -> return TransferResult(false, opened.reason)
            is StreamRead.Ok -> opened.stream
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        val writeError: String? = try {
            val output = try {
                openOutput()
            } catch (e: Exception) {
                return TransferResult(
                    false,
                    "could not open the save destination: ${e.message ?: e.javaClass.simpleName}",
                )
            }
            output.use { out ->
                sourceStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                        written += n
                    }
                    out.flush()
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        } finally {
            runCatching { sourceStream.close() }
        }
        if (writeError != null) {
            return TransferResult(false, "could not save the file: $writeError", written)
        }
        return verifyCommitted(
            targetArea = null,
            target = source,
            expectedDigest = digest.digest(),
            expectedSize = written,
            readBack = openVerify,
            onFailed = onRemoveCreated,
        )
    }

    // ------------------------------------------------------- shared verify

    /**
     * The shared verified-copy tail: re-open the committed bytes and compare
     * sha-256 + size. [targetArea] set → read back through the area (import);
     * [readBack] set → read back through the injected factory (export).
     */
    private fun verifyCommitted(
        targetArea: StorageArea?,
        target: AreaPath,
        expectedDigest: ByteArray,
        expectedSize: Long,
        readBack: (() -> InputStream)? = null,
        onFailed: (() -> Unit)? = null,
    ): TransferResult {
        val verification: DigestResult = try {
            when {
                targetArea != null -> when (val opened = targetArea.openRead(target)) {
                    is StreamRead.Error ->
                        DigestResult.Failed("the committed copy could not be re-opened: ${opened.reason}")
                    is StreamRead.Ok -> opened.stream.use { input -> digestOf(input) }
                }
                readBack != null -> readBack().use { input -> digestOf(input) }
                else -> DigestResult.Failed("no read path for verification")
            }
        } catch (e: Exception) {
            DigestResult.Failed("verification could not run: ${e.message ?: e.javaClass.simpleName}")
        }
        return when (verification) {
            is DigestResult.Failed -> {
                onFailed?.invoke()
                TransferResult(false, "verification failed for the saved copy — ${verification.reason}")
            }
            is DigestResult.Digest ->
                if (verification.size == expectedSize && verification.digest.contentEquals(expectedDigest)) {
                    TransferResult(success = true, bytesCopied = expectedSize)
                } else {
                    onFailed?.invoke()
                    TransferResult(
                        success = false,
                        reason = "verification failed (checksum or size mismatch) — the bad copy was removed, " +
                            "the source is intact",
                    )
                }
        }
    }

    private sealed interface DigestResult {
        data class Digest(val digest: ByteArray, val size: Long) : DigestResult
        data class Failed(val reason: String) : DigestResult
    }

    private fun digestOf(input: InputStream): DigestResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
            size += n
        }
        return DigestResult.Digest(digest.digest(), size)
    }
}
