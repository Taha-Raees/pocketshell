package app.pocketshell.runtime

import java.io.File
import java.security.MessageDigest

/**
 * M2.6 — guest apk fd-link compatibility (docs/M2.6-RESEARCH.md).
 *
 * apk-tools 3.0.x picks its download-commit strategy with
 * `is_proc_fd_ok()` = `access("/proc/self/fd", F_OK) == 0` (src/io.c). With
 * /proc visible that enables the anonymous-O_TMPFILE + `linkat("/proc/self/fd/N", …)`
 * commit, which AOSP sepolicy neverallows for untrusted apps
 * (`neverallow all_untrusted_apps file_type:file link`) — the commit fails
 * with EACCES and apk cancels the whole download (no fallback, device-proven
 * on SM-F711B across v0.4.0–v0.5.0). Without /proc, apk uses its
 * named-tmpfile + `renameat` commit — plain create/rename, fully allowed.
 *
 * The fix shipped here is a ONE-BYTE, checksum-pinned patch to Alpine's OWN
 * `usr/lib/libapk.so.3.0.0` (apk-tools 3.0.6-r0 from the pinned 3.24.1
 * minirootfs): the standalone `"/proc/self/fd"` rodata literal that
 * is_proc_fd_ok() probes becomes `"/proc/self/fX"`, so access() always fails
 * with ENOENT and the fd-link path is never taken. The `"/proc/self/fd/%d"`
 * format string (package script execution) is a separate literal and stays
 * untouched. Everything else is stock apk: same version banner, same real
 * downloads/commits/exit codes. Reproducible via scripts/patch_apk_fdlink.py;
 * the full evidence chain (two literals, exactly one code reference per
 * binary, disassembly) lives in docs/M2.6-RESEARCH.md §2/§4.1.
 *
 * With the patched library verified, interactive sessions can bind a REAL
 * /proc again (`ps`, `top`, `htop` work) while apk keeps working — the M2.6
 * goal of not trading one Linux feature for another.
 *
 * Honest degradation: any state that is not [Result.Ready] means the session
 * spawn falls back to the v0.5.0 shape (no /proc; apk still works) and
 * Diagnostics explains why. A user-modified rootfs (e.g. an in-guest
 * `apk upgrade` replaced libapk) is NEVER touched.
 */
object GuestApkCompat {

    /** Asset carrying the patched library (aarch64 — the only shipped guest arch). */
    const val ASSET_DIR = "guest"
    const val ASSET_NAME = "libapk.so.3.0.0.fdlinkoff.aarch64"

    /** Guest-relative location of the library inside the pinned rootfs. */
    const val LIBAPK_GUEST_RELATIVE = "usr/lib/libapk.so.3.0.0"

    /**
     * sha256 of the library as Alpine ships it in the pinned minirootfs
     * (alpine-minirootfs-3.24.1-aarch64.tar.gz, RuntimePin.SHA256-verified;
     * apk-tools 3.0.6-r0). The ONLY on-disk state the patch will replace.
     */
    const val ORIGINAL_LIBAPK_SHA256 =
        "ef1c9d8d7337d0a3c30cd0bb795c005d92be5a6ca4fae346fd6155a233780db4"

    /**
     * sha256 of the patched library embedded as [ASSET_NAME]
     * (scripts/patch_apk_fdlink.py output; verified at install time BEFORE
     * anything is written into the rootfs).
     */
    const val PATCHED_LIBAPK_SHA256 =
        "b8cd95e2c59735594011654dbb5032bbe4fbc14500aa36ec020d7871c0846de9"

    /**
     * Outcome of an ensure()/status() run. [isProcSafe] is the ONLY thing
     * callers may derive a /proc bind from.
     */
    sealed class Result {
        /** Patched library verified in the rootfs — interactive /proc is safe. */
        object Ready : Result() {
            override fun toString(): String = "Ready"
        }

        /**
         * The rootfs's apk library is not the pinned original (user-modified
         * runtime, e.g. an in-guest apk upgrade). Nothing was written;
         * interactive sessions run without /proc until the runtime is
         * reinstalled from Diagnostics.
         */
        data class NotApplicable(val reason: String) : Result()

        /** The patch could not be installed (asset missing/corrupt, I/O error). */
        data class Failed(val reason: String) : Result()
    }

    /** True only for [Result.Ready] — the sole license to bind /proc into a session. */
    fun isProcSafe(result: Result): Boolean = result is Result.Ready

    /**
     * Verify — and, when [installIfMissing] and the on-disk library is the
     * pinned original, install — the patched library. Idempotent, hash-driven,
     * never throws: failures come back as [Result.Failed]/[Result.NotApplicable].
     *
     * [readAsset] returns the embedded patched library bytes (null when the
     * asset is missing); [sha256Of] hashes a file. Injected for purity.
     */
    fun ensure(
        rootfsDir: File,
        readAsset: (String) -> ByteArray?,
        sha256Of: (File) -> String? = ::sha256Of,
        installIfMissing: Boolean = true,
        sha256OfBytes: (ByteArray) -> String? = ::sha256OfBytes,
    ): Result {
        val target = File(rootfsDir, LIBAPK_GUEST_RELATIVE)
        if (!target.isFile) {
            return Result.Failed(
                "$LIBAPK_GUEST_RELATIVE not found in the guest rootfs — install or repair the runtime from Diagnostics",
            )
        }
        val current = sha256Of(target)
            ?: return Result.Failed("could not read $LIBAPK_GUEST_RELATIVE to verify its state")
        if (current == PATCHED_LIBAPK_SHA256) return Result.Ready
        if (current != ORIGINAL_LIBAPK_SHA256) {
            return Result.NotApplicable(
                "the guest's apk library is not the pinned Alpine build " +
                    "(sha256 ${current.take(12)}…) — apk fd-link patch not applied; " +
                    "interactive sessions run without /proc; reinstall the runtime " +
                    "from Diagnostics to restore the pinned rootfs",
            )
        }
        if (!installIfMissing) {
            return Result.NotApplicable(
                "the pinned Alpine apk is present but the fd-link patch is not applied yet " +
                    "(it installs on the next interactive session or package operation)",
            )
        }
        val patched = readAsset("$ASSET_DIR/$ASSET_NAME")
            ?: return Result.Failed(
                "the patched guest apk library is missing from this build — please report the APK version",
            )
        val patchedHash = sha256OfBytes(patched)
        if (patchedHash != PATCHED_LIBAPK_SHA256) {
            return Result.Failed(
                "the embedded apk fd-link patch failed its own checksum " +
                    "(${patchedHash?.take(12) ?: "unreadable"}…) — nothing was written; " +
                    "please report the APK version",
            )
        }
        return try {
            val tmp = File(target.parentFile, "${target.name}.patch-${System.nanoTime()}.tmp")
            tmp.writeBytes(patched)
            try {
                check(sha256Of(tmp) == PATCHED_LIBAPK_SHA256) { "staged patch hash mismatch" }
                if (!tmp.renameTo(target)) {
                    // rename across the same dir only fails on exotic mounts —
                    // fall back to a full copy, still verified afterwards
                    target.outputStream().use { out -> out.write(patched) }
                    tmp.delete()
                }
            } catch (t: Throwable) {
                tmp.delete()
                throw t
            }
            target.setExecutable(true, false)
            if (sha256Of(target) == PATCHED_LIBAPK_SHA256) {
                Result.Ready
            } else {
                Result.Failed("patched library did not verify after install — nothing was claimed")
            }
        } catch (t: Throwable) {
            Result.Failed(
                "could not install the apk fd-link patch: ${t.message ?: t.javaClass.simpleName}",
            )
        }
    }

    /** sha256 of raw bytes, null when hashing itself fails. */
    fun sha256OfBytes(data: ByteArray): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.digest(data).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    /** sha256 of a file, null when unreadable. */
    fun sha256Of(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }
}
