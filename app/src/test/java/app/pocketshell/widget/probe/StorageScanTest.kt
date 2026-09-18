package app.pocketshell.widget.probe

import app.pocketshell.widget.StorageWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M8 — the Storage widget's scan: NOFOLLOW walks, per-subtree sizing, the
 * apk-cache row, the honest truncation flag, and the human byte format.
 */
class StorageScanTest {

    private fun writeFile(dir: File, name: String, bytes: Int): File {
        val f = File(dir, name)
        f.writeBytes(ByteArray(bytes))
        return f
    }

    @Test
    fun `sizes the rootfs per top-level subtree plus the apk cache`() {
        val root = Files.createTempDirectory("rootfs").toFile()
        val usr = File(root, "usr").apply { mkdirs() }
        writeFile(usr, "a", 10); writeFile(usr, "b", 20)
        writeFile(File(usr, "lib").apply { mkdirs() }, "c", 30)
        val varDir = File(root, "var").apply { mkdirs() }
        writeFile(varDir, "log", 5)
        File(root, "root").apply { mkdirs() }
        writeFile(root, "topfile", 7) // a top-level FILE counts too
        val cache = Files.createTempDirectory("apk-cache").toFile()
        writeFile(cache, "pkg.apk", 100)

        val result = StorageScan.scanGuestStorage(root, cache, nowMillis = 1000L)

        assertEquals(10L + 20 + 30 + 5 + 0 + 7 + 100, result.totalBytes)
        assertFalse(result.truncated)
        assertEquals(1000L, result.scannedAtMillis)
        // Descending; nested files land in their subtree, not the total twice.
        assertEquals("package-cache", result.subtrees.first().name)
        assertEquals(100L, result.subtrees.first().bytes)
        assertEquals("usr", result.subtrees[1].name)
        assertEquals(60L, result.subtrees[1].bytes)
    }

    @Test
    fun `symlinks are never followed - links are nodes, not targets`() {
        val root = Files.createTempDirectory("rootfs").toFile()
        val real = File(root, "real").apply { mkdirs() }
        writeFile(real, "big", 1000)
        Files.createSymbolicLink(File(root, "link").toPath(), real.toPath())
        // A symlinked FILE inside a counted subtree contributes its link, not
        // its target size (attr.isRegularFile is false through NOFOLLOW).
        val cache = null

        val result = StorageScan.scanGuestStorage(root, cache)

        // A symlinked top-level entry is skipped outright (it would
        // double-count its target); "real" is counted exactly once.
        assertTrue(result.subtrees.none { it.name == "link" })
        val realBytes = result.subtrees.first { it.name == "real" }.bytes
        assertEquals(1000L, realBytes)
    }

    @Test
    fun `the file budget sets the honest truncation flag`() {
        val root = Files.createTempDirectory("rootfs").toFile()
        val big = File(root, "big").apply { mkdirs() }
        repeat(10) { writeFile(big, "f$it", 1) }

        val result = StorageScan.scanGuestStorage(root, null, maxFiles = 3)

        assertTrue(result.truncated)
        // The numbers are a floor (walk stopped), never silently wrong.
        assertTrue(result.totalBytes < 10L)
    }

    @Test
    fun `an absent rootfs degrades to an honest zero`() {
        val result = StorageScan.scanGuestStorage(File("/nonexistent/rootfs"), null, nowMillis = 5L)
        assertEquals(0L, result.totalBytes)
        assertTrue(result.subtrees.isEmpty())
        assertFalse(result.truncated)
    }

    @Test
    fun `human byte formatting is stable`() {
        assertEquals("0 B", StorageWidget.formatBytes(0))
        assertEquals("512 B", StorageWidget.formatBytes(512))
        assertEquals("1.0 KB", StorageWidget.formatBytes(1024))
        assertEquals("3.5 MB", StorageWidget.formatBytes((3.5 * 1024 * 1024).toLong()))
        assertEquals("38 GB", StorageWidget.formatBytes(38L * 1024 * 1024 * 1024))
    }
}
