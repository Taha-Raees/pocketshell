package app.pocketshell.files

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

/**
 * M7.2-A — ZIP compress/extract for the File Explorer.
 *
 * PURE and SYNCHRONOUS like [ExplorerOps]: no Android imports, no
 * coroutines. Every byte moves through the Phase 2 [StorageArea]
 * abstraction — [StorageArea.openRead] in, [StorageArea.openWriteAtomic] /
 * [StorageArea.createDirectory] out — so the guest Linux area, the Android
 * shelf and SAF document trees all behave identically, and each area's own
 * safety engine (traversal rejection, symlink discipline, atomic writes)
 * applies to every written file. NOTHING here touches java.io.File.
 *
 * Uses the archive implementation the repo already ships (Apache
 * commons-compress — the same library the runtime layer's audited tar.gz
 * extraction uses). Compression streams file-by-file and extraction
 * streams entry-by-entry with an explicit cumulative byte cap: an archive
 * is never held in memory, and a hostile "zip bomb" cannot exhaust the
 * device.
 *
 * SAFETY CONTRACT (pinned by ZipArchiveOpsTest):
 *  - Entry names are validated BEFORE anything is created: absolute names,
 *    ".."/"." components, empty components and NUL bytes REFUSE the whole
 *    operation with the offending entry named — the classic Zip-Slip
 *    traversal can never escape [destinationDir], not even one file.
 *  - Existing files are NEVER silently overwritten: without replace they
 *    are skipped and reported; with replace (an explicit user choice) the
 *    existing node is deleted through the area first.
 *  - Symlinks are never archived and never extracted-as: source-tree
 *    symlinks are skipped with warnings, and a zip has no honest symlink
 *    representation here.
 *  - The compressed output is written through the area's atomic write
 *    session — a failed or cancelled compress can never leave a
 *    half-written .zip behind.
 *  - Cancel is cooperative and honest: the partial result is described,
 *    not hidden.
 */
object ZipArchiveOps {

    /** Production defaults: 2 GiB extracted, 100k entries. */
    const val DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 2L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES: Int = 100_000

    /** Anti-zip-bomb caps. Constructor-injectable so tests can shrink them. */
    data class Limits(
        val maxTotalUncompressedBytes: Long = DEFAULT_MAX_TOTAL_UNCOMPRESSED_BYTES,
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    )

    /** One progress tick. [totalBytes] null = indeterminate (honest). */
    data class Progress(
        val currentName: String?,
        val entriesDone: Int,
        val bytesDone: Long,
        val totalBytes: Long?,
    )

    /** The honest outcome of one archive operation. */
    data class ZipResult(
        val success: Boolean,
        val cancelled: Boolean,
        val message: String,
        val warnings: List<String> = emptyList(),
        val entriesDone: Int = 0,
        val bytesDone: Long = 0,
    )

    /** True when [name] looks like a ZIP archive the extractor accepts. */
    fun isZipName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() == "zip"

    /**
     * Where zip entry [rawName] must land underneath [destinationDir], or
     * null when the name is REJECTED (absolute, traversal, NUL, empty or
     * invalid components). This is the ONLY way an entry name becomes a
     * path; the composition goes through [PathSafety.validatePath] so the
     * result can never leave the destination subtree.
     */
    fun entryTarget(destination: AreaPath, rawName: String): AreaPath? {
        if (rawName.isEmpty()) return null
        if (rawName.indexOf('\u0000') >= 0) return null
        var name = rawName
        while (name.endsWith("/")) name = name.dropLast(1) // trailing "/" marks a directory
        if (name.isEmpty()) return null
        if (name.startsWith("/")) return null
        val components = name.split('/')
        if (components.any { it.isEmpty() || it == "." || it == ".." }) return null
        return PathSafety.validatePath(
            if (destination.value == "/") "/$name" else "${destination.value}/$name",
        )
    }

    // ------------------------------------------------------------- compress

    /**
     * Compress [sources] (validated paths inside ONE directory — the browsed
     * location) into [target]. Directory sources recurse preserving their
     * structure under their own name; plain files keep their name. Refuses
     * when the target already exists (the caller owns the Replace flow), or
     * when the target sits INSIDE a source directory (the archive would try
     * to contain itself). Symlink sources/children are skipped with
     * warnings. Progress [Progress.totalBytes] is known only when every
     * top-level source is a regular file.
     */
    fun compress(
        area: StorageArea,
        sources: List<AreaPath>,
        target: AreaPath,
        limits: Limits = Limits(),
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
    ): ZipResult {
        if (sources.isEmpty()) {
            return ZipResult(false, false, "Nothing was selected to compress.")
        }
        if (area.stat(target) != null) {
            return ZipResult(false, false, "${target.value} already exists — nothing was changed.")
        }
        for (source in sources) {
            if (source.value == target.value || target.value.startsWith("${source.value}/")) {
                return ZipResult(
                    false,
                    false,
                    "The archive cannot be created inside \"${source.value}\" — nothing was changed.",
                )
            }
        }

        val stats = sources.map { source ->
            area.stat(source)
                ?: return ZipResult(false, false, "${source.value} does not exist or is not accessible.")
        }
        val warnings = ArrayList<String>(0)
        val totalBytes: Long? = if (stats.all { it.kind == EntryKind.FILE }) {
            stats.sumOf { it.sizeBytes ?: 0L }
        } else {
            null // a folder's future archive size is unknowable without a full walk
        }

        val counter = ProgressCounter(totalBytes, onProgress)
        val session = when (val opened = area.openWriteAtomic(target)) {
            is StreamWriteOpen.Error -> return ZipResult(false, false, opened.reason)
            is StreamWriteOpen.Ok -> opened.session
        }
        var entries = 0
        var cancelled = false
        try {
            // The zip writer flushes into a counting, NON-closing wrapper:
            // only commit()/abort() may finish the area's write session.
            val written = CountingNonClosingOutput(session.stream)
            ZipArchiveOutputStream(written).use { zip ->
                zip.setEncoding("UTF-8")
                // Every entry carries its real sizes in the header (files:
                // size set from the stat; directories: STORED zero-size), so
                // the writer never emits a data descriptor — the most
                // compatible layout, and ZipArchiveInputStream's backward
                // end-of-archive scan never mistakes a trailing DD
                // ("PK\x07\x08") for a split-archive marker.
                loop@ for ((source, stat) in sources.zip(stats)) {
                    if (isCancelled()) {
                        cancelled = true
                        break@loop
                    }
                    if (stat.kind == EntryKind.SYMLINK) {
                        warnings += "Skipped \"${stat.name}\" (symlink)."
                        continue@loop
                    }
                    val writtenEntries = writeNode(
                        area, source, stat, stat.name, zip, counter, warnings, isCancelled,
                    )
                    if (writtenEntries == null) {
                        cancelled = true
                        break@loop
                    }
                    entries += writtenEntries
                }
                // finish() BEFORE any abort: the writer must close cleanly
                // while the session is still alive (close order matters);
                // the result is discarded either way when cancelled.
                if (!cancelled) zip.finish()
            }
            if (cancelled) {
                session.abort()
                return ZipResult(false, true, "Compressing cancelled — no archive was kept.")
            }
            val commit = session.commit()
            if (!commit.success) {
                return ZipResult(false, false, commit.reason ?: "the archive could not be saved")
            }
            // Verify the committed size equals what this operation wrote.
            val committed = area.stat(target)?.sizeBytes
            if (committed != null && committed != written.count) {
                area.delete(target)
                return ZipResult(
                    false,
                    false,
                    "Verification of \"${target.value}\" failed (size mismatch) — the bad archive was removed.",
                    warnings = warnings,
                )
            }
            counter.finish()
            val sizeText = committed?.let { " (${humanBytes(it)})" } ?: ""
            return ZipResult(
                success = true,
                cancelled = false,
                message = "Created ${target.value} — $entries " +
                    if (entries == 1) "entry$sizeText." else "entries$sizeText.",
                warnings = warnings,
                entriesDone = entries,
                bytesDone = counter.bytesDone,
            )
        } catch (t: Throwable) {
            session.abort()
            return ZipResult(
                false,
                false,
                "Compressing failed: ${t.message ?: t.javaClass.simpleName}",
                warnings = warnings,
            )
        }
    }

    /** Recursively write one node; returns entries written, or null on cancel. */
    private fun writeNode(
        area: StorageArea,
        path: AreaPath,
        stat: FsEntry,
        entryName: String,
        zip: ZipArchiveOutputStream,
        counter: ProgressCounter,
        warnings: MutableList<String>,
        isCancelled: () -> Boolean,
    ): Int? {
        when (stat.kind) {
            EntryKind.DIRECTORY -> {
                // Classic stored directory entry: known zero sizes + CRC, so
                // no data descriptor is ever needed for it.
                zip.putArchiveEntry(
                    ZipArchiveEntry("$entryName/").apply {
                        method = ZipArchiveEntry.STORED
                        size = 0
                        compressedSize = 0
                        crc = 0
                        time = stat.modifiedAtMillis ?: System.currentTimeMillis()
                    },
                )
                zip.closeArchiveEntry()
                counter.onEntry(entryName, 0)
                var written = 1
                when (val listing = area.list(path)) {
                    is ListResult.Error ->
                        throw IOException("could not read ${path.value}: ${listing.reason}")
                    is ListResult.Ok -> {
                        for (child in listing.entries) {
                            if (isCancelled()) return null
                            if (child.kind == EntryKind.SYMLINK) {
                                warnings += "Skipped \"${child.name}\" (symlink)."
                                continue
                            }
                            val childPath = ExplorerOps.composeChild(path, child.name)
                            if (childPath == null) {
                                warnings += "Skipped \"${child.name}\" (not addressable here)."
                                continue
                            }
                            val writtenEntries = writeNode(
                                area, childPath, child, "$entryName/${child.name}",
                                zip, counter, warnings, isCancelled,
                            )
                            if (writtenEntries == null) return null
                            written += writtenEntries
                        }
                    }
                }
                return written
            }
            EntryKind.FILE -> {
                val entry = ZipArchiveEntry(entryName).apply {
                    time = stat.modifiedAtMillis ?: System.currentTimeMillis()
                    stat.sizeBytes?.let { size = it }
                }
                zip.putArchiveEntry(entry)
                when (val opened = area.openRead(path)) {
                    is StreamRead.Error ->
                        throw IOException("could not read ${path.value}: ${opened.reason}")
                    is StreamRead.Ok -> opened.stream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (isCancelled()) return null
                            val n = input.read(buffer)
                            if (n < 0) break
                            zip.write(buffer, 0, n)
                            counter.onBytes(n)
                        }
                    }
                }
                zip.closeArchiveEntry()
                counter.onEntry(entryName, 1)
                return 1
            }
            else -> return 0 // OTHER nodes cannot be archived honestly
        }
    }

    // --------------------------------------------------------------- extract

    /**
     * Extract the archive at [zipPath] into [destinationDir] (must already
     * exist as a directory — the caller composes and creates it through the
     * area). Structure is preserved; missing intermediate directories are
     * created (not every zip stores explicit directory entries). Existing
     * targets are skipped and reported unless [replace].
     */
    fun extract(
        area: StorageArea,
        zipPath: AreaPath,
        destinationDir: AreaPath,
        limits: Limits = Limits(),
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
        replace: Boolean = false,
    ): ZipResult {
        val zipStat = area.stat(zipPath)
            ?: return ZipResult(false, false, "${zipPath.value} does not exist or is not accessible.")
        if (zipStat.kind != EntryKind.FILE) {
            return ZipResult(false, false, "${zipPath.value} is not a file — nothing was extracted.")
        }
        val destinationStat = area.stat(destinationDir)
        if (destinationStat == null || destinationStat.kind != EntryKind.DIRECTORY) {
            return ZipResult(false, false, "${destinationDir.value} is not a folder — nothing was extracted.")
        }

        val counter = ProgressCounter(null, onProgress)
        val warnings = ArrayList<String>(0)
        val madeDirectories = HashSet<String>()
        var entries = 0
        var skipped = 0

        val opened = area.openRead(zipPath)
        if (opened is StreamRead.Error) {
            return ZipResult(false, false, opened.reason)
        }
        // Honest "not a zip" detection BEFORE extracting: a streaming
        // reader cannot distinguish garbage from an empty archive, so the
        // ZIP magic is checked up front ("PK\x03\x04" local header, or the
        // "PK\x05\x06" end-of-central-directory of an EMPTY archive).
        val input = (opened as StreamRead.Ok).stream
        val pushed = java.io.PushbackInputStream(input, 4)
        val signature = ByteArray(4)
        try {
            readSignature(pushed, signature)
        } catch (e: Exception) {
            runCatching { pushed.close() }
            return ZipResult(false, false, "Extraction failed: the archive could not be read (${e.message ?: e.javaClass.simpleName}).")
        }
        if (!isZipSignature(signature)) {
            runCatching { pushed.close() }
            return ZipResult(
                false,
                false,
                "\"${zipPath.value}\" is not a ZIP archive — nothing was extracted.",
            )
        }
        pushed.unread(signature)
        try {
            ZipArchiveInputStream(pushed.buffered(64 * 1024), "UTF-8").use { zip ->
                while (true) {
                    val entry = zip.nextZipEntry ?: break
                    if (isCancelled()) {
                        return cancelledExtract(entries, counter.bytesDone, destinationDir, warnings)
                    }
                    val rawName = entry.name
                    val target = entryTarget(destinationDir, rawName)
                    if (target == null) {
                        val extracted = if (entries == 1) "entry was" else "entries were"
                        return ZipResult(
                            false,
                            false,
                            "Extraction REFUSED: archive entry \"$rawName\" is not a safe path " +
                                "(path traversal). $entries $extracted extracted before the refusal; " +
                                "nothing was written outside ${destinationDir.value}.",
                            warnings = warnings,
                            entriesDone = entries,
                            bytesDone = counter.bytesDone,
                        )
                    }
                    if (entries + skipped + 1 > limits.maxEntries) {
                        return ZipResult(
                            false,
                            false,
                            "Extraction stopped: the archive holds more than ${limits.maxEntries} " +
                                "entries — refusing as a possible archive bomb. " +
                                "$entries entries were extracted.",
                            warnings = warnings,
                            entriesDone = entries,
                            bytesDone = counter.bytesDone,
                        )
                    }
                    if (counter.bytesDone > limits.maxTotalUncompressedBytes) {
                        return ZipResult(
                            false,
                            false,
                            "Extraction stopped: more than " +
                                "${humanBytes(limits.maxTotalUncompressedBytes)} would be written — " +
                                "refusing as a possible archive bomb. $entries entries were extracted.",
                            warnings = warnings,
                            entriesDone = entries,
                            bytesDone = counter.bytesDone,
                        )
                    }

                    if (entry.isDirectory) {
                        if (ensureDirectory(area, target, madeDirectories, replace, warnings)) {
                            entries += 1
                        } else {
                            skipped += 1
                        }
                        counter.onEntry(target.value, 1)
                        continue
                    }

                    // Regular file: create implied parent directories first.
                    if (!ensureParents(area, target, destinationDir, madeDirectories, warnings)) {
                        skipped += 1
                        drain(zip)
                        continue
                    }
                    val existing = area.stat(target)
                    if (existing != null) {
                        if (!replace) {
                            warnings += "Skipped \"${target.value}\" (already exists — Replace to overwrite)."
                            skipped += 1
                            drain(zip)
                            continue
                        }
                        val removed = area.delete(target)
                        if (!removed.success) {
                            warnings += "Skipped \"${target.value}\" " +
                                "(could not replace: ${removed.reason ?: "delete failed"})."
                            skipped += 1
                            drain(zip)
                            continue
                        }
                    }

                    val session = when (val openedTarget = area.openWriteAtomic(target)) {
                        is StreamWriteOpen.Error -> {
                            warnings += "Skipped \"${target.value}\" (${openedTarget.reason})."
                            skipped += 1
                            drain(zip)
                            continue
                        }
                        is StreamWriteOpen.Ok -> openedTarget.session
                    }
                    val writeFailure = try {
                        session.use { s ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                if (isCancelled()) {
                                    return cancelledExtract(entries, counter.bytesDone, destinationDir, warnings)
                                }
                                val n = zip.read(buffer)
                                if (n < 0) break
                                s.stream.write(buffer, 0, n)
                                counter.onBytes(n)
                                // The cap is enforced MID-ENTRY too: a single
                                // huge (or infinitely padded) entry cannot
                                // blow past it. Returning from inside use
                                // aborts the session (the temp is removed).
                                if (counter.bytesDone > limits.maxTotalUncompressedBytes) {
                                    return ZipResult(
                                        false,
                                        false,
                                        "Extraction stopped: more than " +
                                            "${humanBytes(limits.maxTotalUncompressedBytes)} would be written — " +
                                            "refusing as a possible archive bomb. $entries entries were extracted.",
                                        warnings = warnings,
                                        entriesDone = entries,
                                        bytesDone = counter.bytesDone,
                                    )
                                }
                            }
                            val commit = s.commit()
                            if (!commit.success) commit.reason ?: "commit failed" else null
                        }
                    } catch (t: Throwable) {
                        t.message ?: t.javaClass.simpleName
                    }
                    if (writeFailure != null) {
                        return ZipResult(
                            false,
                            false,
                            "Extraction failed at \"${target.value}\": $writeFailure. " +
                                "$entries entries were extracted before the failure.",
                            warnings = warnings,
                            entriesDone = entries,
                            bytesDone = counter.bytesDone,
                        )
                    }
                    entries += 1
                    counter.onEntry(target.value, 1)
                }
            }
        } catch (t: Throwable) {
            return ZipResult(
                false,
                false,
                "Extraction failed: the archive could not be read " +
                    "(${t.message ?: t.javaClass.simpleName}). $entries entries were extracted.",
                warnings = warnings,
                entriesDone = entries,
                bytesDone = counter.bytesDone,
            )
        }

        val skipText = if (skipped > 0) " — $skipped existing skipped (use Replace to overwrite)" else ""
        return ZipResult(
            success = true,
            cancelled = false,
            message = "Extracted $entries " +
                (if (entries == 1) "entry" else "entries") +
                " (${humanBytes(counter.bytesDone)}) into ${destinationDir.value}$skipText.",
            warnings = warnings,
            entriesDone = entries,
            bytesDone = counter.bytesDone,
        )
    }

    // ------------------------------------------------------------- internals

    private fun ensureDirectory(
        area: StorageArea,
        target: AreaPath,
        madeDirectories: MutableSet<String>,
        replace: Boolean,
        warnings: MutableList<String>,
    ): Boolean {
        val existing = area.stat(target)
        if (existing?.kind == EntryKind.DIRECTORY) return true
        if (existing != null) {
            if (!replace) {
                warnings += "Skipped folder \"${target.value}\" (a file with that name exists)."
                return false
            }
            val removed = area.delete(target)
            if (!removed.success) {
                warnings += "Skipped folder \"${target.value}\" (${removed.reason ?: "delete failed"})."
                return false
            }
        }
        val created = area.createDirectory(target)
        if (!created.success) {
            warnings += "Skipped folder \"${target.value}\" (${created.reason ?: "creation failed"})."
            return false
        }
        madeDirectories.add(target.value)
        return true
    }

    /** Create every missing directory between [destinationDir] and [target]'s parent. */
    private fun ensureParents(
        area: StorageArea,
        target: AreaPath,
        destinationDir: AreaPath,
        madeDirectories: MutableSet<String>,
        warnings: MutableList<String>,
    ): Boolean {
        val components = target.components
        val depthOfDestination = destinationDir.components.size
        var current = destinationDir
        for (depth in depthOfDestination until components.size - 1) {
            current = ExplorerOps.composeChild(current, components[depth]) ?: return false
            if (current.value in madeDirectories) continue
            if (area.stat(current) == null) {
                val created = area.createDirectory(current)
                if (!created.success) {
                    warnings += "Skipped \"${target.value}\" (${created.reason ?: "folder creation failed"})."
                    return false
                }
            }
            madeDirectories.add(current.value)
        }
        return true
    }

    /** Drain the current entry's bytes so the stream can continue. */
    private fun drain(zip: ZipArchiveInputStream) {
        val sink = ByteArray(64 * 1024)
        while (true) {
            val n = zip.read(sink)
            if (n < 0) break
        }
    }

    /** The two legal first-4-bytes of a ZIP stream (local header / EOCD). */
    private fun isZipSignature(bytes: ByteArray): Boolean {
        if (bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
        // Little-endian: "PK\x03\x04" → 0x0403, "PK\x05\x06" → 0x0605.
        val marker = (bytes[2].toInt() and 0xFF) or ((bytes[3].toInt() and 0xFF) shl 8)
        return marker == 0x0403 || marker == 0x0605
    }

    /** Read exactly [buffer.size] bytes; throws on a premature end. */
    private fun readSignature(input: java.io.InputStream, buffer: ByteArray) {
        var filled = 0
        while (filled < buffer.size) {
            val n = input.read(buffer, filled, buffer.size - filled)
            if (n < 0) break
            filled += n
        }
        if (filled < buffer.size) {
            throw java.io.IOException("unexpected end of archive")
        }
    }

    private fun cancelledExtract(
        entries: Int,
        bytes: Long,
        destinationDir: AreaPath,
        warnings: List<String>,
    ): ZipResult = ZipResult(
        success = false,
        cancelled = true,
        message = "Extraction cancelled — $entries " +
            (if (entries == 1) "entry" else "entries") +
            " (${humanBytes(bytes)}) landed in ${destinationDir.value}; " +
            "the archive was not fully extracted.",
        warnings = warnings,
        entriesDone = entries,
        bytesDone = bytes,
    )

    /** Shared progress plumbing; throttling belongs to the caller. */
    private class ProgressCounter(
        val totalBytes: Long?,
        val sink: (Progress) -> Unit,
    ) {
        var bytesDone = 0L
            private set
        private var entriesDone = 0
        private var currentName: String? = null

        fun onBytes(n: Int) {
            bytesDone += n
            emit()
        }

        fun onEntry(name: String, count: Int) {
            currentName = name
            entriesDone += count
            emit()
        }

        fun finish() {
            emit()
        }

        private fun emit() {
            sink(Progress(currentName, entriesDone, bytesDone, totalBytes))
        }
    }

    /**
     * Counts every byte handed to the zip writer and shields the area's
     * atomic write session: close() flushes but NEVER closes the session
     * stream — exactly one of commit()/abort() finishes the session.
     */
    private class CountingNonClosingOutput(private val inner: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            inner.write(b)
            count += 1
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            inner.write(b, off, len)
            count += len
        }

        override fun flush() = inner.flush()

        override fun close() = inner.flush()
    }

    /** Small honest byte formatter for result messages. */
    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }
}
