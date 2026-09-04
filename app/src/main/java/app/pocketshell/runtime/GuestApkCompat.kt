package app.pocketshell.runtime

import java.io.File
import java.security.MessageDigest

/**
 * M2.6 — guest apk fd-link compatibility; SELF-HEALING since v0.7.0-m3.6.
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
 * The fix is a ONE-BYTE patch to the guest's own apk library: the standalone
 * `"/proc/self/fd"` rodata literal that is_proc_fd_ok() probes becomes
 * `"/proc/self/fX"`, so access() always fails with ENOENT and the fd-link
 * path is never taken. The `"/proc/self/fd/%d"` format string (package
 * script execution) is a separate literal and stays untouched. Everything
 * else is stock apk: same version banner, same real downloads/commits/exit
 * codes. Evidence chain (two literals, exactly one code reference per
 * binary, disassembly) lives in docs/M2.6-RESEARCH.md §2/§4.1;
 * scripts/patch_apk_fdlink.py produced the original asset.
 *
 * M2.6–m3.5 shipped this patch as a checksum-pinned ASSET: the rootfs's
 * usr/lib/libapk.so.3.0.0 was replaced only when its sha256 matched the
 * pinned original. That created the m3.6 device regression (2026-09-04): an
 * in-guest `apk upgrade` replaced the library with a NEW apk-tools build,
 * the pin no longer matched, [Result.NotApplicable] degraded every session
 * to no /proc (see RuntimeProcessLauncher) and Bun-compiled CLIs — Kilo
 * Code — died on realpath() of existing paths (aarch64 has no realpath
 * syscall; Bun resolves via /proc/self/fd).
 *
 * The mechanism is now PATTERN-BASED and version-independent:
 *
 *  1. Scan the library bytes for the standalone gate literal
 *     (`/proc/self/fd` NUL-terminated) and the `/proc/self/fd/%d` format
 *     literal (proof this is an apk-tools io.c build).
 *  2. Safe shape (0 gate + format present) → Ready, nothing written.
 *  3. Repairable shape (exactly 1 gate + format present) → flip the gate's
 *     last byte ('d'→'X'), stage tmp+rename, re-verify from disk → Ready.
 *     For the pinned 3.0.6 original this output is BYTE-IDENTICAL to the
 *     shipped asset (sha256 b8cd95e2…de9 — re-proven 2026-09-04 from the
 *     pinned minirootfs tarball); the literal layout is identical in
 *     apk-tools 3.0.8 (scripts/patch_apk_fdlink.py evidence header), so a
 *     post-upgrade library self-heals on the next session spawn.
 *  4. Any other shape (ambiguous literals, no format literal, junk) →
 *     [Result.NotApplicable], NOTHING is written — a user-modified rootfs is
 *     never touched blindly. NOTE: since m3.6 this no longer strips /proc
 *     from interactive sessions (that policy is absolute); it only means the
 *     manual in-guest apk keeps its fd-link path and may hit the SELinux
 *     linkat denial — Diagnostics reports it honestly.
 *
 * Scanned set: the pinned path (usr/lib/libapk.so.3.0.0) plus any
 * `libapk.so.3*` sibling the guest currently ships (a library version bump
 * moves the file), so the repair tracks what the DYNAMIC LOADER actually
 * resolves. Every scanned file must end in the safe shape for [Result.Ready].
 *
 * Idempotent by construction: a safe shape is detected without writes, so
 * running ensure() on every session spawn is free.
 */
object GuestApkCompat {

    /**
     * The original embedded asset (aarch64) that carried this patch for
     * M2.6–m3.5, kept as provenance: scripts/patch_apk_fdlink.py output,
     * sha256 [PATCHED_LIBAPK_SHA256]. The runtime self-repair no longer
     * reads it — the pattern-based repair provably produces the same bytes —
     * but the file and its hash stay as the regression anchor.
     */
    const val ASSET_DIR = "guest"
    const val ASSET_NAME = "libapk.so.3.0.0.fdlinkoff.aarch64"

    /** Guest-relative location of the library inside the pinned rootfs. */
    const val LIBAPK_GUEST_RELATIVE = "usr/lib/libapk.so.3.0.0"

    /** Guest usr/lib prefix shared by every libapk soname variant. */
    private const val LIBAPK_SIBLING_PREFIX = "libapk.so.3"

    /**
     * sha256 of the library as Alpine ships it in the pinned minirootfs
     * (alpine-minirootfs-3.24.1-aarch64.tar.gz; apk-tools 3.0.6-r0).
     * Regression anchor: the self-repair of THIS content must produce
     * [PATCHED_LIBAPK_SHA256] (re-proven 2026-09-04 in the sandbox).
     */
    const val ORIGINAL_LIBAPK_SHA256 =
        "ef1c9d8d7337d0a3c30cd0bb795c005d92be5a6ca4fae346fd6155a233780db4"

    /**
     * sha256 of the patched library (scripts/patch_apk_fdlink.py output,
     * embedded as [ASSET_NAME]). A rootfs whose library already hashes to
     * this is trivially in the safe shape; the byte scan below detects the
     * same shape in ANY apk-tools build.
     */
    const val PATCHED_LIBAPK_SHA256 =
        "b8cd95e2c59735594011654dbb5032bbe4fbc14500aa36ec020d7871c0846de9"

    /** The standalone NUL-terminated gate literal is_proc_fd_ok() probes. */
    val GATE_LITERAL = "/proc/self/fd\u0000".toByteArray()

    /** Same-length replacement: access() on it can never succeed. */
    private val PATCHED_LITERAL = "/proc/self/fX\u0000".toByteArray()

    /** apk-tools' fd-name format literal — separate rodata, never touched. */
    private val FORMAT_LITERAL = "/proc/self/fd/%d".toByteArray()

    /** Byte-level classification of one library file. */
    data class Analysis(val gateCount: Int, val formatCount: Int) {
        /** No gate probe left, but the file is recognizably apk's io.c. */
        val isSafeShape: Boolean get() = gateCount == 0 && formatCount >= 1

        /** Exactly the shape the one-byte repair handles unambiguously. */
        val isRepairableShape: Boolean get() = gateCount == 1 && formatCount >= 1
    }

    fun analyze(bytes: ByteArray): Analysis =
        Analysis(gateCount = countOf(bytes, GATE_LITERAL), formatCount = countOf(bytes, FORMAT_LITERAL))

    /**
     * The one-byte repair: flip the gate literal's trailing 'd' to 'X'.
     * Returns null unless the bytes are exactly the repairable shape —
     * ambiguity is refused, never guessed through.
     */
    fun repairPatch(bytes: ByteArray): ByteArray? {
        val analysis = analyze(bytes)
        if (!analysis.isRepairableShape) return null
        val offset = indexOf(bytes, GATE_LITERAL)
        val out = bytes.copyOf()
        out[offset + GATE_LITERAL.size - 2] = 0x58 // 'd' -> 'X'
        return out
    }

    /**
     * Outcome of an ensure()/status() run. [isProcSafe] reports whether the
     * guest apk library is fd-link-safe; since m3.6 it NO LONGER governs the
     * /proc bind (that policy is absolute in RuntimeProcessLauncher) — it
     * only feeds Diagnostics and the honest risk report.
     */
    sealed class Result {
        /** Guest apk library is fd-link-safe; [detail] says how it got there. */
        data class Ready(val detail: String) : Result()

        /**
         * The library is not in a shape this patcher understands (user-modified
         * rootfs, unknown apk-tools layout). Nothing was written; interactive
         * sessions still bind /proc (absolute policy), but manual in-guest apk
         * keeps its fd-link commit path and may hit the SELinux linkat denial.
         */
        data class NotApplicable(val reason: String) : Result()

        /** The repair was attempted but could not be completed (I/O error). */
        data class Failed(val reason: String) : Result()
    }

    /** True only for [Result.Ready] — apk fd-link safety, NOT the /proc license. */
    fun isProcSafe(result: Result): Boolean = result is Result.Ready

    /**
     * Verify — and, when [installIfMissing], self-repair — every libapk
     * library the guest currently ships. Idempotent, byte-driven, never
     * throws: failures come back as [Result.Failed]/[Result.NotApplicable].
     * With [installIfMissing] = false the run is a pure read-only probe
     * (the Diagnostics button) that never mutates the rootfs.
     */
    fun ensure(rootfsDir: File, installIfMissing: Boolean = true): Result {
        val target = File(rootfsDir, LIBAPK_GUEST_RELATIVE)
        if (!target.isFile) {
            return Result.Failed(
                "$LIBAPK_GUEST_RELATIVE not found in the guest rootfs — install or repair the runtime from Diagnostics",
            )
        }
        // The pinned path first, then any post-upgrade sibling the guest
        // actually loads (a lib version bump renames the file).
        val candidates = LinkedHashMap<File, String>()
        candidates[target] = LIBAPK_GUEST_RELATIVE
        target.parentFile
            ?.listFiles { file -> file.isFile && file.name.startsWith(LIBAPK_SIBLING_PREFIX) }
            ?.filter { it.absolutePath != target.absolutePath }
            ?.sortedBy { it.name }
            ?.forEach { candidates[it] = "usr/lib/${it.name}" }

        val repairs: MutableList<String> = mutableListOf()
        val problems: MutableList<String> = mutableListOf()
        for ((file, label) in candidates) {
            val bytes = try {
                file.readBytes()
            } catch (t: Throwable) {
                return Result.Failed("could not read $label to verify its state: ${t.message ?: t.javaClass.simpleName}")
            }
            val analysis = analyze(bytes)
            when {
                analysis.isSafeShape -> {
                    // Fast path covers the pinned patched asset hash; the
                    // scan covers every future self-repaired build.
                    if (label == LIBAPK_GUEST_RELATIVE && sha256OfBytes(bytes) == PATCHED_LIBAPK_SHA256) {
                        repairs.add("$label: patched library verified (asset hash match)")
                    } else {
                        repairs.add("$label: fd-link gate absent — library already safe")
                    }
                }
                analysis.isRepairableShape && installIfMissing -> {
                    when (val failure = repairInPlace(file, bytes, label)) {
                        null -> repairs.add("$label: fd-link gate disabled (one-byte self-repair applied)")
                        else -> return failure
                    }
                }
                analysis.isRepairableShape -> {
                    problems.add(
                        "$label: the fd-link gate is present but the patch is not applied yet " +
                            "(it self-heals on the next session spawn)",
                    )
                }
                else -> {
                    problems.add(
                        "$label: unrecognized apk library layout " +
                            "(gate literal ×${analysis.gateCount}, fd format literal ×${analysis.formatCount}) — " +
                            "nothing written; manual in-guest apk keeps its fd-link commit path " +
                            "and may hit the SELinux linkat denial while /proc is bound",
                    )
                }
            }
        }
        return when {
            problems.isEmpty() -> Result.Ready(
                repairs.joinToString("; ").ifEmpty { "guest apk library fd-link-safe" },
            )
            else -> Result.NotApplicable(problems.joinToString("; "))
        }
    }

    /**
     * Stage [patched] bytes via tmp+rename (the running library's old inode
     * stays alive for any process still using it), re-verify from DISK, and
     * restore the executable bit. Returns null on success or a [Result.Failed]
     * describing the failure; nothing is ever claimed unverified.
     */
    private fun repairInPlace(target: File, original: ByteArray, label: String): Result.Failed? {
        val patched = repairPatch(original) ?: return Result.Failed("$label: repair shape vanished mid-flight")
        return try {
            val tmp = File(target.parentFile, "${target.name}.patch-${System.nanoTime()}.tmp")
            tmp.writeBytes(patched)
            try {
                val staged = tmp.readBytes()
                check(analyze(staged).isSafeShape) { "staged patch still contains the gate literal" }
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
            val onDisk = target.readBytes()
            if (analyze(onDisk).isSafeShape && onDisk.size == patched.size) {
                null
            } else {
                Result.Failed("$label: patched library did not verify after install — nothing was claimed")
            }
        } catch (t: Throwable) {
            Result.Failed("$label: could not install the apk fd-link repair: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    /** Count of non-overlapping occurrences of [needle] in [haystack]. */
    private fun countOf(haystack: ByteArray, needle: ByteArray): Int {
        var count = 0
        var i = 0
        while (i <= haystack.size - needle.size) {
            if (indexOfAt(haystack, needle, i)) {
                count++
                i += needle.size
            } else {
                i++
            }
        }
        return count
    }

    /** First index of [needle] in [haystack], or -1. */
    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        var i = 0
        while (i <= haystack.size - needle.size) {
            if (indexOfAt(haystack, needle, i)) return i
            i++
        }
        return -1
    }

    private fun indexOfAt(haystack: ByteArray, needle: ByteArray, start: Int): Boolean {
        for (j in needle.indices) {
            if (haystack[start + j] != needle[j]) return false
        }
        return true
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
