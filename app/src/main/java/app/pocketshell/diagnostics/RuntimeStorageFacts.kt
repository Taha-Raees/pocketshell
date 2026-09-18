package app.pocketshell.diagnostics

import android.os.StatFs
import app.pocketshell.runtime.RuntimeMetadata
import app.pocketshell.runtime.RuntimeStorage
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Bounded, honest facts about the runtime directory for the Diagnostics page.
 *
 * Crash fix (user report: "the Diagnostics page won't load and crashes the
 * app as packages download"): the page used to run `RuntimeDiagnostics.report`
 * inline during composition — on the MAIN thread — and that function performs
 * two full, unbounded `File.walkTopDown()` passes over the whole runtime tree
 * (one to sum sizes, one to count files). On a grown rootfs that is tens of
 * thousands of stat syscalls per page open, blocking the main thread long
 * enough to ANR. The cost grows without bound as packages install — exactly
 * the reported degradation.
 *
 * This collector replaces that collection with the widget StorageScan
 * discipline (app/pocketshell/widget/probe/StorageScan.kt):
 *  - ONE pass over `runtime/` (size + count in a single walk), executed by
 *    the caller on Dispatchers.IO — never during composition.
 *  - NOFOLLOW (`walkFileTree` without [FileVisitOption.FOLLOW_LINKS]): the
 *    Alpine merged-usr rootfs ships `/bin -> usr/bin`, `/lib -> usr/lib`,
 *    `/sbin -> usr/sbin` directory symlinks, which `walkTopDown()` DESCENDS —
 *    double-counting every file under usr/ and risking a hang on any cycle.
 *    Symlink entries are budget-counted but never sized or counted as files.
 *  - stat-only sizing (`attrs.size()`); no file content is ever read.
 *  - a file-count budget bounds pathological growth honestly: hitting it
 *    stops the walk and sets [truncated] — [runtimeSizeBytes] and
 *    [rootfsFileCount] are then FLOORS, and the UI must say so.
 */
data class RuntimeStorageFacts(
    /** Parsed `runtime.json`; null when absent/unreadable or no runtime. */
    val metadata: RuntimeMetadata?,
    /** Bytes under `runtime/` (null when no runtime); a floor when truncated. */
    val runtimeSizeBytes: Long?,
    /** Regular files under `rootfs/` (null when no runtime); floor when truncated. */
    val rootfsFileCount: Int?,
    /** Bytes available on the volume hosting the runtime; null when unreadable. */
    val freeBytes: Long?,
    /** True when the file-count budget stopped the walk early. */
    val truncated: Boolean,
) {
    companion object {

        /**
         * Generous by design (parity with StorageScan.DEFAULT_MAX_FILES): a
         * heavy dev rootfs is tens of thousands of files.
         */
        const val DEFAULT_MAX_FILES: Long = 200_000L

        /**
         * Walk [RuntimeStorage.rootDir] once and derive all storage facts.
         *
         * Blocking I/O — call from Dispatchers.IO (the Diagnostics page does).
         * [volumeFreeBytes] is injected so pure-JVM tests can supply a stub;
         * production resolves it with [statFsAvailableBytes].
         */
        fun collect(
            storage: RuntimeStorage,
            maxFiles: Long = DEFAULT_MAX_FILES,
            volumeFreeBytes: (File) -> Long? = ::statFsAvailableBytes,
        ): RuntimeStorageFacts {
            if (!storage.runtimeDirExists()) {
                return RuntimeStorageFacts(
                    metadata = null,
                    runtimeSizeBytes = null,
                    rootfsFileCount = null,
                    freeBytes = volumeFreeBytes(storage.baseDir),
                    truncated = false,
                )
            }

            val rootfsPath = storage.rootfsDir.toPath()
            var counted = 0L
            var sizeBytes = 0L
            var rootfsFiles = 0L

            try {
                Files.walkFileTree(
                    storage.rootDir.toPath(),
                    java.util.EnumSet.noneOf(FileVisitOption::class.java),
                    Int.MAX_VALUE,
                    object : SimpleFileVisitor<Path>() {
                        override fun visitFile(
                            file: Path,
                            attrs: BasicFileAttributes,
                        ): FileVisitResult {
                            counted++
                            if (attrs.isRegularFile) {
                                sizeBytes += attrs.size()
                                if (file.startsWith(rootfsPath)) rootfsFiles++
                            }
                            return if (counted > maxFiles) {
                                FileVisitResult.TERMINATE
                            } else {
                                FileVisitResult.CONTINUE
                            }
                        }

                        override fun visitFileFailed(
                            file: Path,
                            error: IOException,
                        ): FileVisitResult = FileVisitResult.CONTINUE
                    },
                )
            } catch (_: Exception) {
                // A vanished or unreadable subtree contributes what it
                // contributed — the numbers below stay whatever was walked.
            }

            return RuntimeStorageFacts(
                metadata = RuntimeMetadata.read(storage.metadataFile),
                runtimeSizeBytes = sizeBytes,
                rootfsFileCount = rootfsFiles.toInt(),
                freeBytes = volumeFreeBytes(storage.baseDir),
                truncated = counted > maxFiles,
            )
        }

        /** StatFs of the volume hosting [dir]; null when it cannot be read. */
        private fun statFsAvailableBytes(dir: File): Long? = try {
            StatFs(dir.absolutePath).availableBytes
        } catch (_: Exception) {
            null
        }
    }
}
