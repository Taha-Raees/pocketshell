package app.pocketshell.runtime

import android.os.StatFs
import java.io.File

/**
 * Read-only runtime facts for the Diagnostics screen (Master Prompt §29):
 * directory, size, free space, state. No decoration, no invented numbers.
 */
object RuntimeDiagnostics {

    /**
     * M8.3: the old `report()`/`directorySize()` (two unbounded, symlink-
     * following walkTopDown passes over the whole runtime tree, run on the
     * MAIN thread during Diagnostics composition) were removed after they
     * were proven to ANR the app as the rootfs/apk-cache grew — the device
     * gate §67 covers the replacement. Storage facts now come from
     * diagnostics/RuntimeStorageFacts.collect(): single-pass, NOFOLLOW,
     * budget-capped, on Dispatchers.IO.
     *
     * Kept: [formatBytes] (the screen's byte formatting).
     */
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.1f KB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes B"
    }
}