package app.pocketshell.launchers

import java.io.File
import java.io.InputStream

/**
 * M7.1 Phase 1 — launcher icon storage (PART D).
 *
 * A picked image is COPIED into PocketShell-controlled storage the moment
 * the user confirms it: the launcher record references the stored FILENAME,
 * never the picker URI, so moving or deleting the original image can never
 * break a launcher icon. Format-agnostic bytes (`.icon` blob) — decoding
 * belongs to the UI layer (BitmapFactory), storage stays pure java.io and
 * JVM-test-pinned.
 *
 * Missing file, deleted import, invalid bytes: every failure path degrades
 * to the text badge — the UI treats "icon unreadable" exactly like "no icon".
 */
object LauncherIconStore {

    private const val DIR_NAME = "launcher_icons"

    /** Total bytes accepted from a picker stream (imports are tiny by design). */
    const val MAX_IMPORT_BYTES = 8 * 1024 * 1024

    fun directory(baseDir: File): File = File(baseDir, DIR_NAME)

    /**
     * Copy [bytes] into storage as the icon of [launcherId]. Returns the
     * stored filename, or null when the launcher id is unsafe or the stream
     * yields no bytes. The write is temp-file + rename: a failed import can
     * never leave a half-written icon behind.
     */
    fun import(baseDir: File, launcherId: String, bytes: InputStream): String? {
        if (!safeId(launcherId)) return null
        val dir = directory(baseDir).apply { mkdirs() }
        val target = File(dir, "$launcherId.icon")
        val tmp = File(dir, "$launcherId.icon.tmp")
        try {
            var count = 0L
            tmp.outputStream().use { out ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = bytes.read(buf)
                    if (read < 0) break
                    count += read
                    if (count > MAX_IMPORT_BYTES) return null
                    out.write(buf, 0, read)
                }
            }
            if (count == 0L) return null
            if (!tmp.renameTo(target)) return null
            return target.name
        } catch (_: Exception) {
            tmp.delete()
            return null
        }
    }

    /** The stored icon file for [filename], or null when it does not exist. */
    fun file(baseDir: File, filename: String): File? {
        if (filename.isBlank() || filename.contains('/') || filename.contains('\\') ||
            filename.contains("..")
        ) {
            return null
        }
        val file = File(directory(baseDir), filename)
        return if (file.isFile && file.length() > 0) file else null
    }

    /** Delete the icon of [launcherId] (missing file = already gone = fine). */
    fun remove(baseDir: File, launcherId: String) {
        if (!safeId(launcherId)) return
        File(directory(baseDir), "$launcherId.icon").delete()
    }

    /** Ids become filenames — only the exact character class is ever written. */
    private fun safeId(launcherId: String): Boolean =
        launcherId.matches(Regex("[A-Za-z0-9._-]+"))
}
