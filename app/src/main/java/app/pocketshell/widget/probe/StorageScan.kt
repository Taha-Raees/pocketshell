package app.pocketshell.widget.probe

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * M8 — Linux-side storage scan for the Storage widget.
 *
 * The guest rootfs (and the bound apk cache) live in APP-OWNED host
 * directories (`RuntimeStorage`, docs/PROCFS-CONTRACT.md §1): every byte
 * is the app's own — a direct, permission-free, NOFOLLOW walk is the
 * honest way to size them (the Files domain's own note: a folder size is
 * "unknowable without a full walk"). This is EXPENSIVE by nature, so it
 * is on-demand + cached by contract (the widget refresh policy), never a
 * periodic tick.
 */

/** One sized subtree of the Linux storage world. */
data class StorageSubtree(val name: String, val bytes: Long)

data class StorageBreakdown(
    val totalBytes: Long,
    /** Descending by bytes; the renderer caps the displayed rows. */
    val subtrees: List<StorageSubtree>,
    val truncated: Boolean,
    val scannedAtMillis: Long,
)

object StorageScan {

    /**
     * Size the guest rootfs per TOP-LEVEL subtree plus the bound apk cache.
     * A file budget bounds pathological growth honestly: hitting it stops
     * the walk and sets [StorageBreakdown.truncated] — the card says the
     * numbers are a floor, never silently wrong.
     */
    fun scanGuestStorage(
        rootfsDir: File,
        apkCacheDir: File?,
        maxFiles: Long = DEFAULT_MAX_FILES,
        nowMillis: Long = System.currentTimeMillis(),
    ): StorageBreakdown {
        var truncated = false
        var total = 0L
        val subtrees = ArrayList<StorageSubtree>()

        val children = rootfsDir.listFiles()
            ?.sortedBy { it.name }
            ?: return StorageBreakdown(0L, emptyList(), false, nowMillis)

        for (child in children) {
            if (truncated) break
            // A symlinked top-level entry would double-count its target.
            if (Files.isSymbolicLink(child.toPath())) continue
            val budget = Budget(maxFiles)
            val bytes = if (child.isDirectory) walkSize(child, budget) else fileSize(child)
            truncated = truncated || budget.exceeded
            total += bytes
            subtrees.add(StorageSubtree(child.name, bytes))
        }

        apkCacheDir?.let { cache ->
            if (!truncated) {
                val budget = Budget(maxFiles)
                val bytes = walkSize(cache, budget)
                truncated = truncated || budget.exceeded
                total += bytes
                subtrees.add(StorageSubtree(APK_CACHE_NAME, bytes))
            }
        }

        return StorageBreakdown(
            totalBytes = total,
            subtrees = subtrees.sortedByDescending { it.bytes },
            truncated = truncated,
            scannedAtMillis = nowMillis,
        )
    }

    /** The apk-cache row's display name (the one non-rootfs line). */
    const val APK_CACHE_NAME = "package-cache"

    /** Generous by design: a heavy dev rootfs is tens of thousands of files. */
    const val DEFAULT_MAX_FILES: Long = 200_000

    /** Human bytes: one decimal under 10 units, whole otherwise, KiB base. */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kib = bytes / 1024.0
        if (kib < 1024) return number(kib) + " KB"
        val mib = kib / 1024.0
        if (mib < 1024) return number(mib) + " MB"
        val gib = mib / 1024.0
        return number(gib) + " GB"
    }

    private fun number(value: Double): String =
        if (value < 10) String.format(java.util.Locale.US, "%.1f", value)
        else value.toLong().toString()

    private class Budget(val maxFiles: Long) {
        var counted = 0L
        val exceeded: Boolean get() = counted > maxFiles
    }

    private fun fileSize(file: File): Long = try {
        if (file.isFile) file.length() else 0L
    } catch (_: Exception) {
        0L
    }

    /** NOFOLLOW walk: symlinked entries are counted as links, not targets. */
    private fun walkSize(dir: File, budget: Budget): Long {
        var total = 0L
        try {
            Files.walkFileTree(
                dir.toPath(),
                java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java),
                Integer.MAX_VALUE,
                object : SimpleFileVisitor<java.nio.file.Path>() {
                    override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                        budget.counted++
                        if (attrs.isRegularFile) total += attrs.size()
                        return if (budget.exceeded) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                    }

                    override fun preVisitDirectory(dir: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                        // A symlinked directory would re-enter its target.
                        if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: java.nio.file.Path, error: IOException): FileVisitResult =
                        FileVisitResult.CONTINUE
                },
            )
        } catch (_: Exception) {
            // A vanished or unreadable subtree contributes what it contributed.
        }
        return total
    }
}
