package app.pocketshell.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * M2.6: pins the guest apk fd-link patch installer (docs/M2.6-RESEARCH.md §4).
 *
 * The real sha256 pins live in [GuestApkCompat] (original = the libapk inside
 * the pinned minirootfs, patched = the embedded asset produced by
 * scripts/patch_apk_fdlink.py). The JVM tests here inject a content-addressed
 * fake hasher so every decision branch is exercised without the binaries;
 * the REAL asset + pins are verified by the host rehearsal and the device
 * gate (Diagnostics "apk fd-link patch: applied").
 */
class GuestApkCompatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val originalBytes = "original-alpine-libapk-3.0.6-r0".toByteArray()
    private val patchedBytes = "patched-libapk-one-byte-fdlinkoff".toByteArray()

    /** Content-addressed fake: matches the REAL pins for the known contents. */
    private val fakeSha: (File) -> String? = { file ->
        when {
            !file.isFile -> null
            file.readBytes().contentEquals(originalBytes) -> GuestApkCompat.ORIGINAL_LIBAPK_SHA256
            file.readBytes().contentEquals(patchedBytes) -> GuestApkCompat.PATCHED_LIBAPK_SHA256
            else -> "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        }
    }

    /** Byte-level twin of the file fake (asset checksum verification path). */
    private val fakeShaBytes: (ByteArray) -> String? = { bytes ->
        when {
            bytes.contentEquals(originalBytes) -> GuestApkCompat.ORIGINAL_LIBAPK_SHA256
            bytes.contentEquals(patchedBytes) -> GuestApkCompat.PATCHED_LIBAPK_SHA256
            else -> "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        }
    }

    private fun rootfsWith(content: ByteArray?): File {
        val rootfs = tmp.newFolder("rootfs-${System.nanoTime()}")
        val lib = File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE)
        lib.parentFile.mkdirs()
        content?.let { lib.writeBytes(it) }
        return rootfs
    }

    @Test
    fun `missing library is an honest failure`() {
        val rootfs = rootfsWith(null)
        val result = GuestApkCompat.ensure(rootfs, { patchedBytes }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue(result.toString() + (result as GuestApkCompat.Result.Failed).reason, result.reason.contains("not found"))
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `patched library verifies as Ready without rewriting`() {
        val rootfs = rootfsWith(patchedBytes)
        var assetReads = 0
        val result = GuestApkCompat.ensure(rootfs, { assetReads++; patchedBytes }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertEquals(GuestApkCompat.Result.Ready, result)
        assertEquals("already-patched rootfs must not re-read the asset", 0, assetReads)
        assertTrue(GuestApkCompat.isProcSafe(result))
        assertTrue(rootfs.listFilesRecursively().none { it.name.contains(".tmp") })
    }

    @Test
    fun `original library is replaced by the verified patch`() {
        val rootfs = rootfsWith(originalBytes)
        val result = GuestApkCompat.ensure(rootfs, { patchedBytes }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertEquals(GuestApkCompat.Result.Ready, result)
        val lib = File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE)
        assertTrue(lib.readBytes().contentEquals(patchedBytes))
        assertTrue("library must stay executable", lib.canExecute())
        assertTrue(rootfs.listFilesRecursively().none { it.name.contains(".tmp") })
        assertTrue(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `unknown library is never touched (user-modified rootfs)`() {
        val rootfs = rootfsWith("user-replaced-libapk".toByteArray())
        var assetReads = 0
        val result = GuestApkCompat.ensure(rootfs, { assetReads++; patchedBytes }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertEquals(0, assetReads)
        assertTrue(
            File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes()
                .contentEquals("user-replaced-libapk".toByteArray()),
        )
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `missing asset fails honestly and leaves the original in place`() {
        val rootfs = rootfsWith(originalBytes)
        val result = GuestApkCompat.ensure(rootfs, { null }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue((result as GuestApkCompat.Result.Failed).reason.contains("missing from this build"))
        assertTrue(File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(originalBytes))
    }

    @Test
    fun `corrupt asset fails its own checksum and writes nothing`() {
        val rootfs = rootfsWith(originalBytes)
        val corrupt = "corrupt-not-really-patched".toByteArray()
        val result = GuestApkCompat.ensure(rootfs, { corrupt }, fakeSha, sha256OfBytes = fakeShaBytes)
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue((result as GuestApkCompat.Result.Failed).reason.contains("checksum"))
        assertTrue(File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(originalBytes))
        assertFalse(GuestApkCompat.isProcSafe(result))
    }

    @Test
    fun `status-only probe never installs`() {
        val rootfs = rootfsWith(originalBytes)
        var assetReads = 0
        val result = GuestApkCompat.ensure(rootfs, { assetReads++; patchedBytes }, fakeSha, installIfMissing = false)
        assertTrue(result is GuestApkCompat.Result.NotApplicable)
        assertTrue((result as GuestApkCompat.Result.NotApplicable).reason.contains("not applied yet"))
        assertEquals("status probe must not read or install the asset", 0, assetReads)
        assertTrue(File(rootfs, GuestApkCompat.LIBAPK_GUEST_RELATIVE).readBytes().contentEquals(originalBytes))
    }

    @Test
    fun `unreadable library fails without pretending`() {
        val rootfs = rootfsWith(originalBytes)
        val result = GuestApkCompat.ensure(rootfs, { patchedBytes }, { null })
        assertTrue(result is GuestApkCompat.Result.Failed)
        assertTrue((result as GuestApkCompat.Result.Failed).reason.contains("could not read"))
    }

    @Test
    fun `isProcSafe is true only for Ready`() {
        assertTrue(GuestApkCompat.isProcSafe(GuestApkCompat.Result.Ready))
        assertFalse(GuestApkCompat.isProcSafe(GuestApkCompat.Result.NotApplicable("x")))
        assertFalse(GuestApkCompat.isProcSafe(GuestApkCompat.Result.Failed("x")))
    }

    @Test
    fun `real sha256 helper hashes deterministically`() {
        val file = tmp.newFile("known")
        file.writeBytes(originalBytes)
        assertEquals(
            "ef1c9d8d7337d0a3c30cd0bb795c005d92be5a6ca4fae346fd6155a233780db4".length,
            GuestApkCompat.sha256Of(file)!!.length,
        )
        assertEquals(GuestApkCompat.sha256Of(file), GuestApkCompat.sha256Of(file))
        assertEquals(null, GuestApkCompat.sha256Of(File(tmp.root, "nope")))
    }

    private fun File.listFilesRecursively(): List<File> {
        val out = mutableListOf<File>()
        walkTopDown().forEach { if (it != this) out.add(it) }
        return out
    }
}
