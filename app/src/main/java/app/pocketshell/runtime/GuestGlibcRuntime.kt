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
 * m6.0.2 (device-gate root cause,vc40/vc41): the shipped APK carried the
 * asset under a different name than the pin declared (AGP decompresses *.gz
 * assets on merge), so every spawn failed at [android.content.res.AssetManager]
 * open BEFORE touching the rootfs. Hardenings shipped here:
 *  - the archive stream is FORMAT-SNIFFED (gzip magic 0x1f8b) — both the
 *    plain-tar APK form and a gzipped form extract identically;
 *  - the asset bytes are sha-256 verified against [GlibcRuntimePin.ASSET_SHA256]
 *    BEFORE extraction (a corrupt/foreign asset is a FAILED result, not a
 *    half-extraction);
 *  - every outcome is logged (logcat, tag [TAG]) as well as mirrored to the
 *    guest status file — visible from `adb logcat` AND from inside the guest.
 *
 * musl is untouched by construction: the loader name, SONAMEs and directories
 * of the layer are disjoint from musl's (see DUAL_LIBC.md §3).
 *
 * M6 PHASE-C ADVERSARIAL CLOSURE AUDIT (three hardenings, none behavioral for
 * a healthy layer):
 *  - C2.2/C2.3/C2.6 INTEGRITY PROBE: the marker is the completeness contract,
 *    but a text marker cannot see a clobbered filesystem (the proven case:
 *    Alpine's gcompat package owns /lib/ld-linux-aarch64.so.1 and ships a real
 *    ELF shim there — `apk fix/reinstall/upgrade gcompat` can reclaim the
 *    loader path behind a perfectly valid marker). [isCurrent] therefore also
 *    runs [structuralIntegrityPasses]: the loader symlink must still resolve
 *    to the canonical REAL Debian loader, and the load-bearing files (core
 *    libs, guest tools) must exist. A handful of stat() calls — the fast path
 *    stays fast (C10/C11); a probe failure falls through to the normal
 *    idempotent re-extraction (self-healing, musl untouched).
 *  - C3 SINGLE-FLIGHT: ensure is serialized by an object monitor with the
 *    marker+probe fast path INSIDE the lock. Today the only caller is
 *    main-thread-serialized session prep; the lock keeps the guarantee under
 *    any future IO-thread caller, so two concurrent ensures can never
 *    interleave extractions or both run a full extraction.
 *  - C12 EXTRACTOR HARDENING: (a) an existing SYMLINK at an entry target is
 *    replaced by deleting the link NODE itself — File.deleteRecursively
 *    FOLLOWS directory symlinks (stdlib FileTreeWalk walks the TARGET), and
 *    the layer legitimately ships lib/aarch64-linux-gnu -> ../usr/lib/
 *    aarch64-linux-gnu, so a naïve recursive delete wiped the whole multiarch
 *    directory mid-re-extraction; (b) recursive deletes never descend through
 *    symlinks (NOFOLLOW tree walk); (c) a file/symlink/dir entry whose parent
 *    chain contains a symlink is REFUSED (fail closed) — a pinned-archive
 *    attacker cannot route writes outside the guest root through its own
 *    earlier symlink entries.
 */
object GuestGlibcRuntime {

    /** logcat tag — the adb-side half of the m6.0.1/m6.0.2 observability contract. */
    private const val TAG = "GuestGlibcRuntime"

    /**
     * C3: single-flight guard around [ensureInstalled] (fast path included —
     * it is a handful of stats; extraction holds the lock for its duration,
     * which is exactly the serialization a concurrent second session wants).
     */
    private val ensureLock = Any()

    /** Guest-relative loader path — the structural probe's anchor. */
    const val LOADER_RELATIVE = "lib/ld-linux-aarch64.so.1"

    /** The symlink target the REAL Debian loader must sit at. */
    const val LOADER_CANONICAL_TARGET = "/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"

    /** Guest-relative multiarch library directory (the layer's real payload). */
    const val MULTARCH_LIBS_RELATIVE = "usr/lib/aarch64-linux-gnu"

    /**
     * Load-bearing files of the layer: the Cline DT_NEEDED set (the shape the
     * device gate actually exercises), the C++ runtime, and the two guest
     * tools. NOT an inventory of all 103 entries — a corruption probe must be
     * cheap enough to ride the fast path; the marker rev covers payload
     * changes, this covers destruction.
     */
    private val CORE_MULTARCH_FILES = listOf(
        "ld-linux-aarch64.so.1",
        "libc.so.6",
        "libm.so.6",
        "libpthread.so.0",
        "libdl.so.2",
        "libstdc++.so.6",
    )

    private val GUEST_TOOLS_RELATIVE = listOf(
        "usr/local/bin/pocketshell-doctor",
        "usr/local/bin/pocketshell-exec",
    )

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

    /** Cheap completeness probe: exact marker content AND structural integrity. */
    fun isCurrent(rootfsDir: File): Boolean = try {
        val marker = File(rootfsDir, GlibcRuntimePin.MARKER_RELATIVE)
        marker.isFile && marker.readText() == GlibcRuntimePin.markerContent() &&
            structuralIntegrityPasses(rootfsDir)
    } catch (_: Exception) {
        false
    }

    /**
     * C2.2/C2.3/C2.6 structural probe (Phase-C audit): the marker TEXT alone
     * cannot detect a clobbered runtime. This checks — with stat/readlink
     * only, no bytes read — that the real loader symlink is intact and
     * canonical, and that every load-bearing file still exists. Any mismatch
     * means the layer is NOT trustworthy regardless of the marker; the caller
     * re-extracts idempotently (musl is never touched).
     */
    fun structuralIntegrityPasses(rootfsDir: File): Boolean = try {
        val loader = File(rootfsDir, LOADER_RELATIVE).toPath()
        if (!Files.isSymbolicLink(loader)) {
            false // missing, or a regular file (the historical gcompat shim shape)
        } else if (Files.readSymbolicLink(loader).toString() != LOADER_CANONICAL_TARGET) {
            false // reclaimed/re-pointed (gcompat reclaim = a shim ELF at this path)
        } else {
            val multarch = File(rootfsDir, MULTARCH_LIBS_RELATIVE)
            multarch.isDirectory &&
                CORE_MULTARCH_FILES.all { file ->
                    // Final-component symlinks are legitimate layer shapes
                    // (SONAME aliases like libstdc++.so.6 -> libstdc++.so.6.0.33):
                    // FOLLOW them here — the question is "does the loader's
                    // resolution land on a real file", not "is this node a file".
                    Files.isRegularFile(File(multarch, file).toPath())
                } &&
                GUEST_TOOLS_RELATIVE.all { tool ->
                    Files.isRegularFile(File(rootfsDir, tool).toPath())
                }
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Ensure the pinned layer is present in [rootfsDir]. [openArtifact] (the
     * trailing lambda) yields the pinned archive (production:
     * `context.assets.open(ASSET_PATH)` — the PLAIN tar form AGP packages;
     * tests: in-memory archives of either form). [assetSha256] — when
     * non-null, the artifact bytes are verified BEFORE extraction (m6.0.2; a
     * mismatch is a [Result.Failed], never a half-extraction). Never throws.
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
        // m6.0.1: best-effort observability — the device gate proved a silent
        // Failed is indistinguishable from "app too old" from inside the
        // guest. Write the outcome where the guest can read it (and, m6.0.2,
        // where adb logcat can see it too). Never throws, never changes the
        // returned result.
        writeStatus(rootfsDir, result)
        logOutcome(result)
        result
    }

    private fun logOutcome(result: Result) {
        try {
            when (result) {
                is Result.Current ->
                    android.util.Log.i(TAG, "glibc layer current (marker fast path)")
                is Result.Installed ->
                    android.util.Log.i(TAG, "glibc layer installed (${result.entries} entries)")
                is Result.Failed ->
                    android.util.Log.w(TAG, "glibc layer ensure FAILED: ${result.reason}")
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

    // ---------------------------------------------------------------- extraction

    /**
     * m6.0.2: read the archive fully (≤ ~18 MB), verify the pin, then FORMAT-
     * SNIFF: gzip magic (0x1f 0x8b) → GZIP stream, anything else → plain tar.
     * Both forms carry identical tar entries; the sniff makes the extractor
     * immune to which form AGP happens to package (the vc40/vc41 defect).
     */
    private fun extract(rootfsDir: File, openArtifact: () -> InputStream, assetSha256: String?): Int {
        val bytes = openArtifact().use { it.readBytes() }
        if (assetSha256 != null) {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
            if (!digest.equals(assetSha256, ignoreCase = true)) {
                throw IOException(
                    "glibc layer asset sha mismatch: expected $assetSha256, got $digest " +
                        "(${bytes.size} bytes) — asset/package drift",
                )
            }
        }
        val stream: InputStream = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream().buffered())
        } else {
            bytes.inputStream().buffered()
        }
        var count = 0
        TarArchiveInputStream(stream).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val target = resolveSecure(rootfsDir, entry.name)
                // C12: refuse any entry whose parent chain routes through a
                // symlink — an earlier archive entry must not be able to
                // redirect a later entry's writes outside the guest root.
                refuseSymlinkParents(rootfsDir, target, entry.name)
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
        if (Files.isSymbolicLink(target)) {
            // Replace the link NODE itself. File.deleteRecursively would
            // FOLLOW a directory symlink and wipe its real target — the
            // Phase-C audit proved this for lib/aarch64-linux-gnu ->
            // ../usr/lib/aarch64-linux-gnu (every re-extraction erased the
            // whole multiarch directory before rewriting it).
            Files.delete(target)
        } else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            // A real file/directory from an older revision: clear it, but a
            // NOFOLLOW tree walk — never descend through any symlink.
            deleteRecursivelyNoFollow(target)
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

    /**
     * C12: fail closed when any component between the rootfs and [target] is
     * a symlink — writes must never be redirected outside the guest root by
     * an earlier archive entry. Pinned archives never trigger this (verified:
     * the layer's only directory symlink is a LEAF, and no file entry lives
     * under it); a hostile archive does.
     */
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
            // Mode preservation is best-effort on exotic filesystems; on f2fs
            // (the device) it applies. Existence is the load-bearing part.
        }
    }

    /**
     * NOFOLLOW recursive delete (C12): deletes files and symlink NODES as
     * they are, never descending into a symlinked directory. Replaces
     * File.deleteRecursively, whose walk follows directory symlinks and
     * would delete through them (the Phase-C audit finding).
     */
    private fun deleteRecursivelyNoFollow(target: Path) {
        Files.walkFileTree(
            target,
            java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java),
            Int.MAX_VALUE,
            object : java.nio.file.SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: java.nio.file.attribute.BasicFileAttributes): java.nio.file.FileVisitResult {
                    Files.delete(file) // symlink nodes land here too (no follow)
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): java.nio.file.FileVisitResult {
                    Files.delete(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException?): java.nio.file.FileVisitResult {
                    try {
                        Files.delete(file)
                    } catch (_: Exception) {
                        // best-effort on exotic fs — the parent dir delete will surface it
                    }
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
    }
}
