package app.pocketshell.runtime

import java.io.File

/**
 * Path layout + atomic-promotion primitives for the runtime directory
 * (docs/M2-ARCHITECTURE §3).
 *
 * Takes a plain base [File] (not a Context) so every rule is unit-testable
 * against a temporary folder.
 */
class RuntimeStorage(baseDir: File) {

    /** The layout base (app noBackupFilesDir) — also hosts the apk cache dir. */
    val baseDir: File = baseDir

    val rootDir: File = File(baseDir, DIR_RUNTIME)
    val rootfsDir: File = File(rootDir, DIR_ROOTFS)
    val metadataFile: File = File(rootDir, FILE_METADATA)

    /** In-flight download target (sibling of runtime/, same volume ⇒ atomic rename). */
    val downloadTmp: File = File(baseDir, TMP_DOWNLOAD)

    /** Staging extraction root; promoted to [rootDir] by rename. */
    val extractTmp: File = File(baseDir, TMP_EXTRACT)

    /** True when a final runtime directory exists. */
    fun runtimeDirExists(): Boolean = rootDir.isDirectory

    /**
     * Delete transient staging artifacts (idempotent, safe when absent).
     * C12 (Phase-C audit): an interrupted extraction leaves staging dirs
     * containing symlinks (the minirootfs ships etc/mtab -> ../proc/mounts
     * and 304 /bin/busybox applet links); the cleanup walk must never follow
     * them — File.deleteRecursively follows directory links, so staging is
     * cleared with a NOFOLLOW tree walk (symlink nodes are deleted as nodes).
     */
    fun cleanupTransient(): Boolean {
        val a = deleteTreeNoFollow(downloadTmp.toPath())
        val b = deleteTreeNoFollow(extractTmp.toPath())
        return a && b
    }

    private fun deleteTreeNoFollow(target: java.nio.file.Path): Boolean {
        if (!java.nio.file.Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return true
        return try {
            java.nio.file.Files.walkFileTree(
                target,
                java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                    override fun visitFile(
                        file: java.nio.file.Path,
                        attrs: java.nio.file.attribute.BasicFileAttributes,
                    ): java.nio.file.FileVisitResult {
                        java.nio.file.Files.delete(file) // symlink nodes land here too
                        return java.nio.file.FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dir: java.nio.file.Path,
                        exc: java.io.IOException?,
                    ): java.nio.file.FileVisitResult {
                        java.nio.file.Files.delete(dir)
                        return java.nio.file.FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: java.nio.file.Path,
                        exc: java.io.IOException?,
                    ): java.nio.file.FileVisitResult {
                        try {
                            java.nio.file.Files.delete(file)
                        } catch (_: Exception) {
                            // best-effort; the overall result reports honestly
                        }
                        return java.nio.file.FileVisitResult.CONTINUE
                    }
                },
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Atomic promotion of the staged extraction to the final runtime dir.
     * Preconditions: staging exists, final dir does NOT exist (a failed run
     * must never overwrite an existing runtime — repair removes it first).
     */
    fun promoteStagedToRuntime() {
        check(extractTmp.isDirectory) { "no staged extraction to promote" }
        check(!rootDir.exists()) { "runtime dir already exists; refusing to overwrite" }
        if (!extractTmp.renameTo(rootDir)) {
            throw java.io.IOException("atomic promotion rename failed: ${extractTmp.path} -> ${rootDir.path}")
        }
    }

    /**
     * Remove the entire runtime (user-invoked remove/repair). Idempotent.
     * C12: NOFOLLOW tree walk — the rootfs contains hundreds of symlinks and
     * may contain user-installed directory symlinks; the remove walk must
     * never delete THROUGH any of them.
     */
    fun clearRuntime(): Boolean =
        if (!rootDir.exists()) true else deleteTreeNoFollow(rootDir.toPath())

    /**
     * Startup reconciliation (docs/M2-ARCHITECTURE §4 "persistence"):
     * derive the honest state from what is actually on disk.
     */
    fun reconcileInitialState(): RuntimeState {
        val runtimeExists = runtimeDirExists()
        val metadata = if (runtimeExists) RuntimeMetadata.read(metadataFile) else null

        return when {
            // Orphaned tmp artifacts without a finished runtime: a previous run
            // was interrupted — clean them, report the truth.
            !runtimeExists -> {
                cleanupTransient()
                RuntimeState.NOT_INSTALLED
            }

            // Runtime present but metadata unreadable/missing: do not silently
            // delete user data — surface it as repairable.
            metadata == null -> RuntimeState.REPAIR_REQUIRED

            // Runtime present: trust structural state recorded at install time,
            // downgraded to REPAIR_REQUIRED if key parts vanished afterwards.
            else -> {
                if (structuralProbePasses()) {
                    RuntimeState.entries.firstOrNull { it.name == metadata.state }
                        ?: RuntimeState.REPAIR_REQUIRED
                } else {
                    RuntimeState.REPAIR_REQUIRED
                }
            }
        }
    }

    /** Cheap structural probe of the extracted rootfs (no process execution). */
    fun structuralProbePasses(): Boolean =
        File(rootfsDir, "etc/alpine-release").isFile &&
            (File(rootfsDir, "bin/busybox").isFile || File(rootfsDir, "usr/bin/busybox").isFile)

    companion object {
        const val DIR_RUNTIME = "runtime"
        const val DIR_ROOTFS = "rootfs"
        const val FILE_METADATA = "runtime.json"
        const val TMP_DOWNLOAD = "runtime-download.tmp"
        const val TMP_EXTRACT = "runtime-extract.tmp"
    }
}
