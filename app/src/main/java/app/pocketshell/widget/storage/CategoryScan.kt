package app.pocketshell.widget.storage

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * M8.4 STORAGE — the one sizing + clearing primitive for APP-OWNED cache
 * categories (the apk package cache, the share staging dir).
 *
 * Sizing follows the established budgeted-walk discipline exactly
 * (RuntimeStorageFacts / StorageScan): ONE NOFOLLOW walkFileTree pass,
 * stat-only sizing, a file-count budget that stops the walk honestly
 * (the numbers are then FLOORS, never silently wrong), and a cancellation
 * poll so an in-flight scan dies with its coroutine instead of burning
 * the budget in the background. NO File.walkTopDown — the M8.2 Diagnostics
 * ANR came from exactly that unbounded pattern.
 *
 * Clearing is deliberately narrower than the tree-delete helpers elsewhere
 * in the codebase (RuntimeStorage.deleteTreeNoFollow removes whole trees):
 * a cache category may only lose REGULAR FILES. Symlinks are never
 * followed and never deleted through; the walk physically cannot leave
 * [dir] because every deleted path is one the NOFOLLOW walk yielded inside
 * it; and the directory itself always survives.
 */
object CategoryScan {

    /**
     * Generous by design — parity with StorageScan.DEFAULT_MAX_FILES and
     * RuntimeStorageFacts.DEFAULT_MAX_FILES (a heavy dev rootfs is tens of
     * thousands of files; a package cache is far below that).
     */
    const val DEFAULT_MAX_FILES: Long = 200_000L

    /** Cancellation is polled every this-many walked entries. */
    private const val CANCEL_POLL_ENTRIES = 128L

    /** One category's measured size. [truncated] ⇒ bytes/files are floors. */
    data class SizeResult(
        val bytes: Long,
        val files: Int,
        val truncated: Boolean,
        val exists: Boolean,
    )

    /**
     * Size [dir] with one budgeted NOFOLLOW walk. Blocking I/O — call from
     * Dispatchers.IO. [isCancelled] is polled during the walk; a cancelled
     * walk reports truncated = true (whatever was measured is a floor).
     */
    fun size(
        dir: File,
        maxFiles: Long = DEFAULT_MAX_FILES,
        isCancelled: () -> Boolean = { false },
    ): SizeResult {
        if (!dir.isDirectory) return SizeResult(bytes = 0L, files = 0, truncated = false, exists = false)
        var counted = 0L
        var bytes = 0L
        var files = 0
        var stoppedEarly = false
        try {
            Files.walkFileTree(
                dir.toPath(),
                java.util.EnumSet.noneOf(FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        counted++
                        if (counted % CANCEL_POLL_ENTRIES == 0L && isCancelled()) {
                            stoppedEarly = true
                            return FileVisitResult.TERMINATE
                        }
                        // NOFOLLOW: a symlink arrives here as a NODE — counted
                        // against the budget, never sized, never descended.
                        if (attrs.isRegularFile) {
                            bytes += attrs.size()
                            files++
                        }
                        return if (counted > maxFiles) {
                            stoppedEarly = true
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
            // A vanished or unreadable subtree contributes what it contributed.
        }
        return SizeResult(
            bytes = bytes,
            files = files,
            truncated = stoppedEarly,
            exists = true,
        )
    }

    /** Outcome of a clear — honest counts, never a silent partial. */
    data class ClearResult(
        val filesDeleted: Int,
        val bytesFreed: Long,
        val directoriesRemoved: Int,
        /** True when cancellation stopped the clear before the walk ended. */
        val stoppedEarly: Boolean,
        /**
         * Regular files (or unreadable entries) that could not be removed.
         * A directory that stays because it holds deliberately-protected
         * nodes is retention by design, not a failure.
         */
        val failures: Int,
    ) {
        val succeeded: Boolean get() = failures == 0
    }

    /**
     * Clear [dir] by deleting REGULAR FILES ONLY, NOFOLLOW, then removing
     * the emptied subdirectories (never [dir] itself). Best-effort by
     * contract: a file that cannot be removed is counted in [ClearResult.failures]
     * and left in place. Blocking I/O — call from Dispatchers.IO.
     *
     * Safety argument (each property pinned by CategoryScanTest):
     *   - walkFileTree WITHOUT FileVisitOption.FOLLOW_LINKS: symlinked
     *     files AND symlinked directories arrive at visitFile as nodes with
     *     isRegularFile == false — skipped, never deleted through, never
     *     descended into;
     *   - only paths the walk yields inside [dir] are ever deleted — there
     *     is no path arithmetic and nothing outside can be reached;
     *   - the root [dir] is never deleted (identity check in
     *     postVisitDirectory), so the cache dir and its owner wiring stay;
     *   - non-regular files (sockets, fifos, devices) are skipped as well.
     */
    fun clearRegularFiles(
        dir: File,
        isCancelled: () -> Boolean = { false },
    ): ClearResult {
        if (!dir.isDirectory) return ClearResult(0, 0L, 0, stoppedEarly = false, failures = 0)
        val root: Path = dir.toPath()
        var deleted = 0
        var freed = 0L
        var dirsRemoved = 0
        var failures = 0
        var seen = 0L
        var stoppedEarly = false
        try {
            Files.walkFileTree(
                root,
                java.util.EnumSet.noneOf(FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        seen++
                        if (seen % CANCEL_POLL_ENTRIES == 0L && isCancelled()) {
                            stoppedEarly = true
                            return FileVisitResult.TERMINATE
                        }
                        if (!attrs.isRegularFile) {
                            // Symlink node / fifo / socket / device: never ours
                            // to delete. The node stays exactly as it is.
                            return FileVisitResult.CONTINUE
                        }
                        freed += attrs.size()
                        deleted++
                        try {
                            Files.delete(file)
                        } catch (_: Exception) {
                            deleted--
                            freed -= attrs.size()
                            failures++
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dirPath: Path,
                        exc: IOException?,
                    ): FileVisitResult {
                        if (dirPath == root) {
                            // The cache directory itself always survives.
                            return FileVisitResult.CONTINUE
                        }
                        try {
                            Files.delete(dirPath)
                            dirsRemoved++
                        } catch (_: Exception) {
                            // Non-empty (skipped protected nodes) or in use —
                            // retention by design, NOT a failure: the clear
                            // contract removes regular files only.
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        error: IOException,
                    ): FileVisitResult {
                        failures++
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (_: Exception) {
            // A vanished subtree contributes what it contributed.
        }
        return ClearResult(
            filesDeleted = deleted,
            bytesFreed = freed,
            directoriesRemoved = dirsRemoved,
            stoppedEarly = stoppedEarly,
            failures = failures,
        )
    }
}
