package app.pocketshell.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
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
import kotlin.io.path.absolutePathString

/**
 * The PocketShell glibc runtime layer inside the guest rootfs
 * (docs/runtime/DUAL_LIBC.md): REAL glibc (Debian trixie arm64) at canonical
 * multiarch paths — `/lib/ld-linux-aarch64.so.1` (real loader),
 * `/lib/aarch64-linux-gnu` + `/usr/lib/aarch64-linux-gnu` (libraries),
 * `/etc/nsswitch.conf`, `pocketshell-exec`/`pocketshell-doctor` in
 * `/usr/local/bin`.
 *
 * Delivery is the GuestApkCompat pattern: idempotent, self-healing, and
 * best-effort — called from the per-session preparation seam
 * ([app.pocketshell.packages.PackageGateway.prepareGuestForSession]) so every
 * install path (fresh rootfs, rootfs installed by an older PocketShell build)
 * converges on the same pinned layer without any user action and without ever
 * blocking or breaking a musl session. The marker file is written LAST; a
 * partial extraction is simply re-extracted on the next call.
 *
 * m6.0.1 (device-gate lesson): best-effort must still be OBSERVABLE. Every
 * ensure outcome is mirrored to a guest-visible status file
 * ([STATUS_RELATIVE]) so the on-device suite, pocketshell-doctor and any
 * human in a terminal can see WHY a layer is (not) present — the failure
 * mode found at the m6.0.0 device gate was a silently swallowed
 * [Result.Failed], indistinguishable from "app too old" from inside the
 * guest. The status file is a diagnostic ONLY — the marker stays the sole
 * completeness contract.
 *
 * musl is untouched by construction: the loader name, SONAMEs and directories
 * of the layer are disjoint from musl's (see DUAL_LIBC.md §3).
 */
object GuestGlibcRuntime {

    sealed interface Result {
        /** Marker present and matching the pin — nothing to do (fast path). */
        data object Current : Result

        /** Layer extracted (or re-extracted) — [entries] tar entries written. */
        data class Installed(val entries: Int) : Result

        /** Best-effort failure — musl sessions continue regardless. */
        data class Failed(val reason: String) : Result
    }

    /**
     * Guest-relative LAST-OUTCOME diagnostic file (m6.0.1). Machine-parseable
     * single line, one of:
     *   state=OK source=extractor entries=<n> ts=<epoch-ms>
     *   state=OK source=fastpath ts=<epoch-ms>
     *   state=FAILED reason=<one-line> ts=<epoch-ms>
     * Written best-effort on every ensure call; never masks the real result.
     */
    const val STATUS_RELATIVE = "etc/pocketshell/glibc-runtime.status"

    /** Cheap completeness probe: exact marker content, read from the rootfs. */
    fun isCurrent(rootfsDir: File): Boolean = try {
        val marker = File(rootfsDir, GlibcRuntimePin.MARKER_RELATIVE)
        marker.isFile && marker.readText() == GlibcRuntimePin.markerContent()
    } catch (_: Exception) {
        false
    }

    /**
     * Ensure the pinned layer is present in [rootfsDir]. [openArtifact] yields
     * the pinned tar.gz (production: `context.assets.open(ASSET_PATH)`;
     * tests: an in-memory archive). Never throws.
     */
    fun ensureInstalled(rootfsDir: File, openArtifact: () -> InputStream): Result {
        val result: Result = try {
            if (isCurrent(rootfsDir)) {
                Result.Current
            } else {
                Result.Installed(extract(rootfsDir, openArtifact))
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        // m6.0.1: best-effort observability — the device gate proved a silent
        // Failed is indistinguishable from "app too old" from inside the
        // guest. Write the outcome where the guest can read it. Never throws,
        // never changes the returned result.
        writeStatus(rootfsDir, result)
        return result
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

    // ---------------------------------------------------------------- extraction

    private fun extract(rootfsDir: File, openArtifact: () -> InputStream): Int {
        var count = 0
        TarArchiveInputStream(GZIPInputStream(openArtifact().buffered())).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val target = resolveSecure(rootfsDir, entry.name)
                when {
                    entry.isDirectory -> {
                        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                            Files.createDirectories(target)
                        }
                    }

                    entry.isSymbolicLink -> replaceSymlink(target, entry.linkName)

                    entry.isLink -> {
                        // The layer ships no hardlinks today, but the tar
                        // format allows them — resolve inside the rootfs and
                        // link (f2fs allows it; the Android SELinux denial
                        // from the M2.6.13 lessons does not apply to a plain
                        // Files.createLink on app-private storage because the
                        // SESSION-side proot translation is not involved —
                        // still, fall back to a copy for exotic filesystems).
                        val source = resolveSecure(rootfsDir, entry.linkName)
                        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                            try {
                                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                                    Files.delete(target)
                                }
                                Files.createLink(target, source)
                            } catch (_: Exception) {
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                                applyMode(target, entry.mode)
                            }
                        }
                    }

                    entry.isFile -> {
                        target.parent?.toFile()?.mkdirs()
                        Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING)
                        applyMode(target, entry.mode)
                    }

                    else -> Unit // devices/fifos: not creatable unprivileged; proot supplies them
                }
                count++
            }
        }
        writeMarker(rootfsDir)
        return count
    }

    /**
     * The marker is the completeness contract: written LAST, exact content.
     * A crash mid-extraction leaves no valid marker → the next call
     * re-extracts everything (idempotent).
     */
    private fun writeMarker(rootfsDir: File) {
        val marker = File(rootfsDir, GlibcRuntimePin.MARKER_RELATIVE)
        marker.parentFile?.mkdirs()
        val tmp = File(marker.parentFile, marker.name + ".write")
        tmp.writeText(GlibcRuntimePin.markerContent())
        if (!tmp.renameTo(marker)) {
            tmp.delete()
            throw IOException("glibc layer marker rename failed: ${marker.path}")
        }
    }

    private fun replaceSymlink(target: Path, linkName: String) {
        target.parent?.toFile()?.mkdirs()
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(target)
        ) {
            deleteRecursivelyPath(target)
        }
        Files.createSymbolicLink(target, Path(linkName))
    }

    /** Lexical path-containment guard (zip-slip) — same contract as RuntimeInstaller. */
    private fun resolveSecure(rootfsDir: File, entryName: String): Path {
        if (Path(entryName).isAbsolute) {
            throw IOException("absolute path in glibc layer archive: $entryName")
        }
        val root = Path(rootfsDir.absolutePath).normalize()
        val resolved = root.resolve(entryName).normalize()
        if (!resolved.startsWith(root)) {
            throw IOException("path traversal in glibc layer archive: $entryName")
        }
        return resolved
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
            // Mode preservation is best-effort on exotic filesystems; on f2fs
            // (the device) it applies. Existence is the load-bearing part.
        }
    }

    private fun deleteRecursivelyPath(target: Path) {
        val file = target.toFile()
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}
