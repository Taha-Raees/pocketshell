package app.pocketshell.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import kotlin.io.path.Path

/**
 * The PocketShell dev-workstation toolset inside the guest rootfs
 * (docs/runtime/DEV_WORKSTATION.md): `pocketshell-dev-bootstrap`,
 * `manifest.conf` (pinned upstream artifacts), `pocketshell-adb`,
 * `build-minimal-apk.sh` and the provenance template — installed at
 * `/usr/local/lib/pocketshell-dev` with `/usr/local/bin` entry points.
 *
 * Delivery follows the GuestGlibcRuntime contract exactly (idempotent,
 * self-healing, best-effort, called from the per-session preparation seam):
 * marker file written LAST is the completeness contract; every outcome is
 * mirrored to a guest-visible status file; the artifact bytes are sha-256
 * verified BEFORE extraction; format-sniffing makes the extractor immune to
 * AGP's `*.gz` asset recompression. The payload is plain files with exec
 * bits (no symlinks, no hardlinks) so this extractor stays small — but the
 * zip-slip and symlink-parent refusals are identical to the glibc layer's.
 */
object GuestDevTools {
    private const val TAG = "GuestDevTools"
    private val ensureLock = Any()

    /** Pinned asset (the plain-tar form AGP packages; see GlibcRuntimePin note). */
    const val ASSET_PATH = "guest/pocketshell-devtools-1.tar"

    /** SHA-256 of the packaged asset bytes (pocketshell-devtools-1.tar). */
    const val ASSET_SHA256 = "2ed16f0b8d8bd3efeaff7c4d75c5c501d1a59ca303578b5e1de3dce04b6ec26b"

    /** Version label + marker contract. */
    const val VERSION = "devtools-1"

    const val MARKER_RELATIVE = "etc/pocketshell/devtools"
    const val STATUS_RELATIVE = "etc/pocketshell/devtools.status"

    /** lib dir (guest-relative) + the load-bearing file probe set. */
    const val LIB_DIR_RELATIVE = "usr/local/lib/pocketshell-dev"
    const val BIN_DIR_RELATIVE = "usr/local/bin"

    private val LOAD_BEARING_FILES = listOf(
        "$LIB_DIR_RELATIVE/manifest.conf",
        "$LIB_DIR_RELATIVE/pocketshell-dev-bootstrap",
        "$LIB_DIR_RELATIVE/build-minimal-apk.sh",
        "$LIB_DIR_RELATIVE/pocketshell-adb",
        "$BIN_DIR_RELATIVE/pocketshell-dev-bootstrap",
        "$BIN_DIR_RELATIVE/pocketshell-adb",
    )

    private val EXEC_BIT_FILES = setOf(
        "$LIB_DIR_RELATIVE/pocketshell-dev-bootstrap",
        "$LIB_DIR_RELATIVE/build-minimal-apk.sh",
        "$LIB_DIR_RELATIVE/pocketshell-adb",
        "$BIN_DIR_RELATIVE/pocketshell-dev-bootstrap",
        "$BIN_DIR_RELATIVE/pocketshell-adb",
    )

    sealed interface Result {
        data object Current : Result
        data class Installed(val entries: Int) : Result
        data class Failed(val reason: String) : Result
    }

    fun markerContent(): String = "$VERSION\n"

    /** Cheap completeness probe: exact marker content AND structural integrity. */
    fun isCurrent(rootfsDir: File): Boolean = try {
        val marker = File(rootfsDir, MARKER_RELATIVE)
        marker.isFile && marker.readText() == markerContent() && structuralIntegrityPasses(rootfsDir)
    } catch (_: Exception) {
        false
    }

    /** stat-only probe: payload files exist and the bin/lib exec entries are executable. */
    fun structuralIntegrityPasses(rootfsDir: File): Boolean = try {
        LOAD_BEARING_FILES.all { rel ->
            val f = File(rootfsDir, rel).toPath()
            Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) &&
                (rel !in EXEC_BIT_FILES || Files.isExecutable(f))
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Ensure the pinned toolset is present in [rootfsDir]. [openArtifact]
     * yields the packaged archive; [assetSha256] (when non-null) is verified
     * before any byte is written. Never throws; best-effort by contract.
     */
    fun ensureInstalled(
        rootfsDir: File,
        assetSha256: String? = null,
        openArtifact: () -> InputStream,
    ): Result = synchronized(ensureLock) {
        val result: Result = try {
            if (isCurrent(rootfsDir)) {
                Result.Current
            } else {
                Result.Installed(extract(rootfsDir, openArtifact, assetSha256))
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        writeStatus(rootfsDir, result)
        logOutcome(result)
        result
    }

    private fun logOutcome(result: Result) {
        try {
            when (result) {
                is Result.Current ->
                    android.util.Log.i(TAG, "devtools current (marker fast path)")
                is Result.Installed ->
                    android.util.Log.i(TAG, "devtools installed (${result.entries} entries)")
                is Result.Failed ->
                    android.util.Log.w(TAG, "devtools ensure FAILED: ${result.reason}")
            }
        } catch (_: Throwable) {
            // Diagnostics must never become a failure source (JVM tests).
        }
    }

    private fun writeStatus(rootfsDir: File, result: Result) {
        try {
            val status = File(rootfsDir, STATUS_RELATIVE)
            status.parentFile?.mkdirs()
            val body = when (result) {
                is Result.Current ->
                    "state=OK source=fastpath ts=${System.currentTimeMillis()}\n"
                is Result.Installed ->
                    "state=OK source=extractor entries=${result.entries} ts=${System.currentTimeMillis()}\n"
                is Result.Failed ->
                    "state=FAILED reason=${result.reason.replace('\n', ' ').take(200)} ts=${System.currentTimeMillis()}\n"
            }
            val tmp = File(status.parentFile, status.name + ".write")
            tmp.writeText(body)
            if (!tmp.renameTo(status)) tmp.delete()
        } catch (_: Exception) {
            // Diagnostics must never become a failure source.
        }
    }

    /** Format-sniff (gzip magic → GZIP, else plain tar), sha-verify, extract. */
    private fun extract(rootfsDir: File, openArtifact: () -> InputStream, assetSha256: String?): Int {
        val bytes = openArtifact().use { it.readBytes() }
        if (assetSha256 != null) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
            if (!digest.equals(assetSha256, ignoreCase = true)) {
                throw IOException(
                    "devtools asset sha mismatch: expected $assetSha256, got $digest " +
                        "(${bytes.size} bytes) — asset/package drift",
                )
            }
        }
        val stream: InputStream =
            if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(bytes.inputStream().buffered())
            } else {
                bytes.inputStream().buffered()
            }
        var count = 0
        TarArchiveInputStream(stream).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val target = resolveSecure(rootfsDir, entry.name)
                refuseSymlinkParents(rootfsDir, target, entry.name)
                when {
                    entry.isDirectory ->
                        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                            Files.createDirectories(target)
                        }

                    entry.isSymbolicLink || entry.isLink ->
                        // The pinned payload is plain files by contract; a
                        // link entry here means artifact drift — fail closed.
                        throw IOException("unexpected link entry in devtools archive: ${entry.name}")

                    entry.isFile -> {
                        target.parent?.toFile()?.mkdirs()
                        Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING)
                        applyMode(target, entry.mode)
                    }

                    else -> Unit // devices/fifos: not creatable unprivileged
                }
                count++
            }
        }
        writeMarker(rootfsDir)
        return count
    }

    private fun writeMarker(rootfsDir: File) {
        val marker = File(rootfsDir, MARKER_RELATIVE)
        marker.parentFile?.mkdirs()
        val tmp = File(marker.parentFile, marker.name + ".write")
        tmp.writeText(markerContent())
        if (!tmp.renameTo(marker)) {
            tmp.delete()
            throw IOException("devtools marker rename failed: ${marker.path}")
        }
    }

    /** Lexical path-containment guard (zip-slip) — same contract as RuntimeInstaller. */
    private fun resolveSecure(rootfsDir: File, entryName: String): Path {
        if (Path(entryName).isAbsolute) {
            throw IOException("absolute path in devtools archive: $entryName")
        }
        val root = Path(rootfsDir.absolutePath).normalize()
        val resolved = root.resolve(entryName).normalize()
        if (!resolved.startsWith(root)) {
            throw IOException("path traversal in devtools archive: $entryName")
        }
        return resolved
    }

    /** C12: never write through a symlinked parent (fail closed). */
    private fun refuseSymlinkParents(rootfsDir: File, target: Path, entryName: String) {
        val root = Path(rootfsDir.absolutePath).normalize()
        var node = target.parent?.normalize() ?: return
        while (node != null && node != root) {
            if (Files.isSymbolicLink(node)) {
                throw IOException(
                    "archive entry \"$entryName\" resolves through a symlink (" +
                        "${node.fileName}) — refusing to write through it",
                )
            }
            node = node.parent?.normalize() ?: break
        }
    }

    private fun applyMode(target: Path, mode: Int) {
        try {
            val perms = HashSet<java.nio.file.attribute.PosixFilePermission>()
            if (mode and 0b100_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ)
            if (mode and 0b010_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)
            if (mode and 0b001_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
            if (mode and 0b000_100_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
            if (mode and 0b000_010_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
            if (mode and 0b000_001_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
            if (mode and 0b000_000_100 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
            if (mode and 0b000_000_010 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
            if (mode and 0b000_000_001 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(target, perms)
        } catch (_: Exception) {
            // Mode is best-effort on exotic filesystems; on f2fs it applies.
        }
    }
}
