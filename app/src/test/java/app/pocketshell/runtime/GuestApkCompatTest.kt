package app.pocketshell.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * M2.6 → m3.6: pins the guest apk fd-link SELF-REPAIR (docs/PROCFS-CONTRACT.md).
 *
 * The mechanism is byte-driven: scan for the standalone "/proc/self/fd" gate
 * literal (NUL-terminated) that apk's is_proc_fd_ok() probes, plus the
 * "/proc/self/fd/%d" format literal that proves the file is apk's io.c.
 * Safe shape (0 gates + format) → Ready. Repairable (exactly 1 gate +
 * format) → one-byte flip 'd'→'X', staged tmp+rename, re-verified from disk.
 * Everything else is refused without writes. The REAL pinned binaries were
 * re-verified in the sandbox on 2026-09-04: the repair of the pinned
 * minirootfs libapk (sha256 ef1c9d8d…db4) produces BYTE-IDENTICAL output to
 * the shipped asset (sha256 b8cd95e2…de9 == GuestApkCompat.PATCHED_LIBAPK_SHA256).
 */
class GuestApkCompatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // A synthetic "apk library": filler + exactly the two rodata literals the
    // real binary carries (verified against the real libapk on 2026-09-04).
    private val fillerA = "ELF-filler-aarch64-apk-tools-io.c-rodata-region".toByteArray()
    private val fillerB = "tail-filler-nothing-special".toByteArray()
    private val originalBytes = fillerA + GuestApkCompat.GATE_LITERAL + fillerB + "/proc/self/fd/%d".toByteArray() + fillerB
    private val patchedBytes = fillerA + "/proc/self/fX\u0000".toByteArray() + fillerB + "/proc/self/fd/%d".toByteArray() + fillerB

    private fun rootfsWith(vararg libs: Pair<String, ByteArray>): File {
        val rootfs = tmp.newFolder("rootfs-${System.nanoTime()}")
        for ((name, content) in libs) {
            val file = File(rootfs, name)
            file.parentFile.mkdirs()
            file.writeBytes(content)
            file.setExecutable(true, false)
        }
        return rootfs
    }

    private fun rootfsWithPrimary(content: ByteArray?): File =
        rootfsWith(GuestApkCompat.LIBAPK_GUEST_RELATIVE to (content ?: ByteArray(0)))

    @Test
    fun `missing library is an honest failure`() {
        val rootfs = tmp.newFolder("rootfs-empty-${System.nanoTime()}")
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue((result as GuestApkCompat.Result.Failed).reason.contains("not found"))
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `already safe library verifies as Ready without rewriting`() {
        val rootfs = rootfsWithPrimary(patchedBytes)
        val before = File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).lastModified()
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue(result is GuestApkCompat.Result.Ready)
        assertTrue(GuestApkCompat.isProcSafe(result))
        assertEquals(
            "already-safe rootfs must not be rewritten",
            before,
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).lastModified(),
        )
        assertTrue(rootfs.listFilesRecursively().none { it.name.contains(".tmp") })
    }

    @Test
    fun `repairable library is self-healed in place`() {
        val rootfs = rootfsWithPrimary(originalBytes)
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue("repairable shape must end Ready: $result", result is GuestApkCompat.Result.Ready)
        val lib = File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE)
        val onDisk = lib.readBytes()
        assertEquals(
            "repaired bytes must be the deterministic one-byte flip",
            patchedBytes.toList(),
            onDisk.toList(),
        )
        assertTrue("library must stay executable", lib.canExecute())
        assertTrue(rootfs.listFilesRecursively().none { it.name.contains(".tmp") })
        assertTrue(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `status-only probe never repairs`() {
        val rootfs = rootfsWithPrimary(originalBytes)
        val result = GuestApkCompat.ensure(rootfs, installIfMissing = false)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertTrue(
            (result as GuestApkCompat.Result.NotApplicable).reason.contains("self-heals on the next session spawn"),
        )
        assertTrue(
            "status probe must not touch the rootfs",
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(originalBytes),
        )
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `ambiguous gate literals are refused without writing`() {
        val ambiguous = fillerA + GuestApkCompat.GATE_LITERAL + fillerB + GuestApkCompat.GATE_LITERAL + fillerB
        val rootfs = rootfsWithPrimary(ambiguous)
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertTrue(
            (result as GuestApkCompat.Result.NotApplicable).reason.contains("unrecognized apk library layout"),
        )
        assertTrue(
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(ambiguous),
        )
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `file without the apk format literal is refused without writing`() {
        // gate present but no "/proc/self/fd/%d" — NOT recognizable as apk's
        // io.c; patching blind is forbidden
        val alien = fillerA + GuestApkCompat.GATE_LITERAL + fillerB
        val rootfs = rootfsWithPrimary(alien)
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertTrue(
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(alien),
        )
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `junk file is refused without writing`() {
        val junk = "user-replaced-libapk".toByteArray()
        val rootfs = rootfsWithPrimary(junk)
        val result = GuestApkCompat.ensure(rootfs)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertTrue(
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(junk),
        )
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `post-upgrade sibling library is repaired too`() {
        // an in-guest apk upgrade may ship a different libapk file name; the
        // scan tracks every libapk.so.3* the guest actually loads
        val siblingName = "usr/lib/libapk.so.3.0.9"
        val rootfs = rootfsWithPrimary(patchedBytes) to siblingName
        val (root, sibling) = rootfs
        File(root, sibling).parentFile.mkdirs()
        File(root, sibling).writeBytes(originalBytes)
        File(root, sibling).setExecutable(true, false)

        val result = GuestApkCompat.ensure(root)
        assertTrue("sibling repair must end Ready: $result", result is GuestApkCompat.Result.Ready)
        assertTrue(
            "sibling must be repaired",
            File(root, sibling).readBytes().contentEquals(patchedBytes),
        )
        assertTrue(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `unreadable library fails without pretending`() {
        val rootfs = rootfsWithPrimary(originalBytes)
        val lib = File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE)
        lib.setReadable(false)
        val result = try {
            GuestApkCompat.ensure(rootfs)
        } finally {
            lib.setReadable(true) // don't leak an unreadable fixture into tmp cleanup
        }
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue((result as GuestApkCompat.Result.Failed).reason.contains("could not read"))
    }

    @Test
    fun `repairPatch refuses non-repairable shapes and flips exactly one byte`() {
        assertEquals(null, GuestApkCompat.repairPatch(patchedBytes))
        assertEquals(null, GuestApkCompat.repairPatch("junk".toByteArray()))
        val out = GuestApkCompat.repairPatch(originalBytes)!!
        assertEquals(originalBytes.size, out.size)
        assertEquals(1, out.countIndexed { i, b -> i < originalBytes.size && b != originalBytes[i] })
        assertEquals('X'.code.toByte(), out[fillerA.size + GuestApkCompat.GATE_LITERAL.size - 2])
        assertTrue(GuestApkCompat.analyze(out).isSafeShape)
    }

    @Test
    fun `analyze classifies the pinned binary shapes`() {
        assertEquals(1, GuestApkCompat.analyze(originalBytes).gateCount)
        assertEquals(1, GuestApkCompat.analyze(originalBytes).formatCount)
        assertTrue(GuestApkCompat.analyze(originalBytes).isRepairableShape)
        assertTrue(GuestApkCompat.analyze(patchedBytes).isSafeShape)
        assertFalse(GuestApkCompat.analyze(patchedBytes).isRepairableShape)
    }

    @Test
    fun `isProcSafe is true only for Ready`() {
        assertTrue(GuestApkCompat.isProcSafe(GuestApkCompat.Result.Ready("x")))
        assertFalse(GuestApkCompat.isProcSafe(GuestApkCompat.Result.NotApplicable("x")))
        assertFalse(GuestApkCompat.isProcSafe(GuestApkCompat.Result.Failed("x")))
    }

    @Test
    fun `real sha256 helper hashes deterministically`() {
        val file = tmp.newFile("known")
        file.writeBytes(originalBytes)
        assertEquals(
            GuestApkCompat.ORIGINAL_LIBAPK_SHA256.length,
            GuestApkCompat.sha256Of(file)!!.length,
        )
        assertEquals(GuestApkCompat.sha256Of(file), GuestApkCompat.sha256Of(file))
        assertEquals(null, GuestApkCompat.sha256Of(File(tmp.root, "nope")))
    }

    private fun ByteArray.countIndexed(predicate: (Int, Byte) -> Boolean): Int {
        var count = 0
        for (i in indices) if (predicate(i, this[i])) count++
        return count
    }

    private fun File.listFilesRecursively(): List<File> {
        val out = mutableListOf<File>()
        walkTopDown().forEach { if (it != this) out.add(it) }
        return out
    }
}
