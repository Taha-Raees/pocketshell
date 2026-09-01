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

    /** Delete transient staging artifacts (idempotent, safe when absent). */
    fun cleanupTransient(): Boolean {
        val a = downloadTmp.deleteRecursively()
        val b = extractTmp.deleteRecursively()
        return a && b
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

    /** Remove the entire runtime (user-invoked remove/repair). Idempotent. */
    fun clearRuntime(): Boolean = if (!rootDir.exists()) true else rootDir.deleteRecursively()

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
