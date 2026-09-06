package app.pocketshell.files.saf

import app.pocketshell.files.AreaPath
import app.pocketshell.files.EntryKind
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StreamRead
import java.io.File

/**
 * M7.0.0 Phase 5 — the Share side-bridge: staging + MIME guessing.
 *
 * A PocketShell file is NEVER shared by exposing its real location (a
 * rootfs path or a document URI). Instead it is STAGED into the app's cache
 * (a plain copy), and the FileProvider hands the share sheet a temporary
 * content:// URI for THAT staged copy with temporary read permission only.
 * The staging directory self-cleans before every share, so nothing lingers.
 *
 * PURE where possible: [guessMimeType] is a plain function; [stageForShare]
 * works on java.io.File + the [StorageArea] seam and is JVM-testable. The
 * single Android call left (FileProvider.getUriForFile) lives with the
 * ViewModel that launches the share sheet.
 */
object FileShareOps {

    /** The FileProvider authority declared in the merged manifest. */
    const val FILE_PROVIDER_AUTHORITY = "app.pocketshell.fileprovider"

    /** The staging directory inside cacheDir, mirrored in res/xml/file_paths.xml. */
    const val STAGING_DIR_NAME = "share"

    /**
     * MIME type by file extension — the small, honest map for the kinds a
     * terminal user actually shares. Unknown extensions share as
     * "application/octet-stream" (honest: the sheet treats it as a binary).
     */
    fun guessMimeType(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt", "log", "conf", "cfg", "ini", "properties", "env" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "text/javascript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            "tgz" -> "application/gzip"
            "apk" -> "application/vnd.android.package-archive"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "sh" -> "application/x-sh"
            "py" -> "text/x-python"
            "kt", "kts" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "c", "h" -> "text/x-c"
            "cpp", "cc", "hpp" -> "text/x-c++src"
            "deb" -> "application/vnd.debian.binary-package"
            else -> "application/octet-stream"
        }
    }

    /** Result of [stageForShare]. */
    sealed interface Staging {
        /** The staged copy, ready for the FileProvider. */
        data class Ok(val file: File) : Staging

        /** Honest failure — surfaced verbatim in the notice banner. */
        data class Error(val reason: String) : Staging
    }

    /**
     * Copy [source] (a FILE) from its area into [stagingDir] for sharing.
     * Refuses directories (file sharing only — the product scope), cleans
     * the staging directory first, and verifies the byte count against the
     * source's reported size when one is known. The staged file carries the
     * entry's real name so the share sheet shows something familiar.
     */
    fun stageForShare(
        sourceArea: StorageArea,
        source: AreaPath,
        stagingDir: File,
    ): Staging {
        val stat = sourceArea.stat(source)
            ?: return Staging.Error("${source.value} does not exist or is not accessible")
        if (stat.kind == EntryKind.DIRECTORY) {
            return Staging.Error("Folders cannot be shared — only files.")
        }
        val cleaned = runCatching {
            stagingDir.mkdirs()
            stagingDir.listFiles()?.forEach { it.delete() }
        }.isSuccess
        if (!cleaned) {
            return Staging.Error("could not prepare the share staging area")
        }
        val staged = File(stagingDir, stat.name)
        return when (val opened = sourceArea.openRead(source)) {
            is StreamRead.Error -> Staging.Error(opened.reason)
            is StreamRead.Ok -> try {
                opened.stream.use { input ->
                    staged.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            total += n
                        }
                        val expected = stat.sizeBytes
                        if (expected != null && total != expected) {
                            staged.delete()
                            return Staging.Error(
                                "share staging failed for \"${stat.name}\" (size mismatch) — " +
                                    "nothing was shared",
                            )
                        }
                    }
                }
                Staging.Ok(staged)
            } catch (e: Exception) {
                runCatching { staged.delete() }
                Staging.Error("could not stage \"${stat.name}\" for sharing: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
