package app.pocketshell.runtime

import android.os.StatFs
import java.io.File

/**
 * Read-only runtime facts for the Diagnostics screen (Master Prompt §29):
 * directory, size, free space, state. No decoration, no invented numbers.
 */
object RuntimeDiagnostics {

    data class Report(
        val state: RuntimeState,
        val metadata: RuntimeMetadata?,
        /** Total bytes under runtime/ (null when absent). */
        val runtimeSizeBytes: Long?,
        /** Bytes available on the volume hosting the runtime. */
        val freeBytes: Long,
        val rootfsEntryCount: Int?,
    )

    fun report(storage: RuntimeStorage, state: RuntimeState): Report {
        val size = if (storage.runtimeDirExists()) directorySize(storage.rootDir) else null
        val entries = if (storage.runtimeDirExists()) {
            storage.rootfsDir.walkTopDown().filter { it.isFile }.count()
        } else {
            null
        }
        val free = runCatching {
            StatFs(storage.rootDir.parentFile?.absolutePath).availableBytes
        }.getOrDefault(0L)
        return Report(
            state = state,
            metadata = RuntimeMetadata.read(storage.metadataFile),
            runtimeSizeBytes = size,
            freeBytes = free,
            rootfsEntryCount = entries,
        )
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes B"
    }

    private fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.fold(0L) { acc, f -> acc + f.length() }
}
