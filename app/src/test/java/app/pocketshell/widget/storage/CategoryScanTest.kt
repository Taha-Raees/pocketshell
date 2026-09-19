package app.pocketshell.widget.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The STORAGE application's sizing + clearing primitive, against real
 * fixture trees. Pinned here:
 *   - budget + truncation honesty (numbers become floors, stated);
 *   - cancellation stops the walk and reports it;
 *   - SYMLINK SAFETY (sizing): links are nodes — never sized, never
 *     descended into;
 *   - CLEAR SAFETY: only regular files inside the cache dir are removed;
 *     symlink nodes survive with their targets untouched; emptied
 *     subdirectories go; the cache root itself always survives; nothing
 *     outside the cache dir is ever touched.
 *   - measured cost on a >=20k-file fixture (printed for the record).
 */
class CategoryScanTest {

    // --------------------------------------------------------- fixtures

    private fun writeFile(dir: File, name: String, bytes: Int): File {
        val f = File(dir, name)
        f.writeBytes(ByteArray(bytes))
        return f
    }

    // ---------------------------------------------------------- sizing

    @Test
    fun `sizes a fixture tree - regular files only`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val etc = File(root, "etc").apply { mkdirs() }
        val varDir = File(root, "var").apply { mkdirs() }
        writeFile(etc, "index", 30)
        writeFile(varDir, "pkg.apk", 100)
        writeFile(root, "top", 7)

        val result = CategoryScan.size(root)

        assertTrue(result.exists)
        assertFalse(result.truncated)
        assertEquals(3, result.files)
        assertEquals(137L, result.bytes)
        root.deleteRecursively()
    }

    @Test
    fun `an absent dir is an honest non-existence`() {
        val result = CategoryScan.size(File("/nonexistent/cache-dir"))
        assertFalse(result.exists)
        assertEquals(0L, result.bytes)
        assertEquals(0, result.files)
        assertFalse(result.truncated)
    }

    @Test
    fun `the file budget stops the walk - numbers are floors`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        repeat(10) { writeFile(root, "f$it", 1) }

        val result = CategoryScan.size(root, maxFiles = 3)

        assertTrue(result.truncated)
        assertTrue("floor, never the full count", result.files < 10)
        assertTrue(result.bytes < 10L)
        root.deleteRecursively()
    }

    @Test
    fun `cancellation stops the walk and reports floors`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val sub = File(root, "sub").apply { mkdirs() }
        repeat(300) { writeFile(sub, "f$it", 1) }

        // The cancel poll fires every 128 walked entries — a constantly-true
        // check must stop the walk at the first poll, well before 300.
        val result = CategoryScan.size(root, isCancelled = { true })

        assertTrue(result.truncated)
        assertTrue(result.files < 300)
        assertTrue(result.bytes < 300L)
        root.deleteRecursively()
    }

    @Test
    fun `symlinks are never sized or descended into`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        val outsideDir = File(outside, "realdir").apply { mkdirs() }
        writeFile(outside, "big.bin", 1000)
        writeFile(outsideDir, "kept.txt", 7)
        writeFile(File(root, "sub").apply { mkdirs() }, "inner.apk", 50)
        Files.createSymbolicLink(File(root, "link.apk").toPath(), File(outside, "big.bin").toPath())
        Files.createSymbolicLink(File(File(root, "sub"), "dirlink").toPath(), outsideDir.toPath())

        val result = CategoryScan.size(root)

        // The link NODES count against the budget but contribute no bytes;
        // the symlinked DIRECTORY is never entered (its 1000+7 stay out).
        assertEquals(1, result.files)
        assertEquals(50L, result.bytes)
        assertFalse(result.truncated)
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    // ---------------------------------------------------------- clearing

    @Test
    fun `clear removes only regular files - links survive, targets untouched, root survives`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val outside = Files.createTempDirectory("outside").toFile()
        val outsideDir = File(outside, "realdir").apply { mkdirs() }
        val bigTarget = writeFile(outside, "big.bin", 1000)
        val keptTarget = writeFile(outsideDir, "kept.txt", 7)
        val sub = File(root, "sub").apply { mkdirs() }
        val inner = writeFile(sub, "inner.apk", 50)
        val real = writeFile(root, "real.apk", 100)
        val fileLink = Files.createSymbolicLink(
            File(root, "link.apk").toPath(),
            bigTarget.toPath(),
        )
        val dirLink = Files.createSymbolicLink(
            File(File(root, "sub"), "dirlink").toPath(),
            outsideDir.toPath(),
        )

        val result = CategoryScan.clearRegularFiles(root)

        // Both regular files inside the cache were removed, with their bytes.
        assertEquals(2, result.filesDeleted)
        assertEquals(150L, result.bytesFreed)
        assertTrue(result.succeeded)
        assertFalse(result.stoppedEarly)

        // The symlink NODES survive (never deleted, never followed)…
        assertTrue(Files.isSymbolicLink(fileLink))
        assertTrue(Files.isSymbolicLink(dirLink))
        // …and their TARGETS outside the cache are byte-for-byte intact.
        assertEquals(1000L, bigTarget.length())
        assertEquals(7L, keptTarget.length())
        assertTrue(bigTarget.isFile)
        assertTrue(keptTarget.isFile)

        // The cache root itself always survives (the owner wiring keeps it).
        assertTrue(root.isDirectory)
        // "sub" still holds the protected dirlink node, so it stays — that
        // is retention BY DESIGN (regular files only), not a failure.
        assertTrue(File(root, "sub").isDirectory)
        assertEquals(0, result.directoriesRemoved)
        assertEquals(0, result.failures)
        assertFalse(inner.exists())
        assertFalse(real.exists())
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    @Test
    fun `clear removes emptied subdirectories but never the root`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val a = File(root, "a").apply { mkdirs() }
        val b = File(a, "b").apply { mkdirs() }
        writeFile(b, "pkg.apk", 10)
        writeFile(root, "loose", 5)

        val result = CategoryScan.clearRegularFiles(root)

        assertEquals(2, result.filesDeleted)
        assertEquals(15L, result.bytesFreed)
        assertEquals(2, result.directoriesRemoved)
        assertEquals(0, result.failures)
        assertFalse(b.exists())
        assertFalse(a.exists())
        assertTrue(root.isDirectory)
        root.deleteRecursively()
    }

    @Test
    fun `an outside sibling file is never touched`() {
        val parent = Files.createTempDirectory("base").toFile()
        val cache = File(parent, "apk-cache").apply { mkdirs() }
        writeFile(cache, "pkg.apk", 10)
        val sibling = writeFile(parent, "sibling.txt", 99)

        CategoryScan.clearRegularFiles(cache)

        assertTrue(sibling.isFile)
        assertEquals(99L, sibling.length())
        parent.deleteRecursively()
    }

    @Test
    fun `clear of an absent dir is a no-op`() {
        val result = CategoryScan.clearRegularFiles(File("/nonexistent/cache-dir"))
        assertEquals(0, result.filesDeleted)
        assertEquals(0L, result.bytesFreed)
        assertTrue(result.succeeded)
    }

    @Test
    fun `a cancelled clear stops early and says so`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val sub = File(root, "sub").apply { mkdirs() }
        repeat(300) { writeFile(sub, "f$it", 1) }

        val result = CategoryScan.clearRegularFiles(root, isCancelled = { true })

        assertTrue(result.stoppedEarly)
        assertTrue("partial by design, honestly reported", result.filesDeleted < 300)
        assertTrue(root.isDirectory)
        root.deleteRecursively()
    }

    @Test
    fun `a cleared cache re-measures to zero`() {
        val root = Files.createTempDirectory("apk-cache").toFile()
        val sub = File(root, "etc").apply { mkdirs() }
        writeFile(sub, "index", 40)

        CategoryScan.clearRegularFiles(root)
        val after = CategoryScan.size(root)

        assertTrue(after.exists)
        assertEquals(0, after.files)
        assertEquals(0L, after.bytes)
        assertFalse(after.truncated)
        root.deleteRecursively()
    }

    // ---------------------------------------- measured cost (20k files)

    /**
     * The performance contract, measured (not just asserted): a 20 000-file
     * fixture must size and clear in bounded time with a bounded number of
     * syscalls-worthy entries, honestly and completely. The timings are
     * printed for the report; the assertions pin CORRECTNESS at scale
     * (every file accounted for, no truncation at the default budget).
     */
    @Test
    fun `measured cost on a 20k-file fixture`() {
        val root = Files.createTempDirectory("apk-cache-20k").toFile()
        val dirs = 100
        val perDir = 200
        repeat(dirs) { d ->
            val dir = File(root, "pkg-$d").apply { mkdirs() }
            repeat(perDir) { f -> writeFile(dir, "file-$f", 1) }
        }
        val total = dirs * perDir
        assertTrue("fixture really has >=20k files", total >= 20_000)

        val sizeStart = System.nanoTime()
        val sized = CategoryScan.size(root)
        val sizeMs = (System.nanoTime() - sizeStart) / 1_000_000

        assertNotNull(sized)
        assertEquals(total, sized.files)
        assertEquals(total.toLong(), sized.bytes)
        assertFalse(sized.truncated)

        val clearStart = System.nanoTime()
        val cleared = CategoryScan.clearRegularFiles(root)
        val clearMs = (System.nanoTime() - clearStart) / 1_000_000

        assertEquals(total, cleared.filesDeleted)
        assertEquals(total.toLong(), cleared.bytesFreed)
        assertEquals(0, cleared.failures)
        assertEquals(dirs, cleared.directoriesRemoved)
        assertTrue(root.isDirectory)

        println(
            "CategoryScan 20k-file fixture: size=$sizeMs ms (${sized.files} files, " +
                "${sized.bytes} B), clear=$clearMs ms (${cleared.filesDeleted} deleted, " +
                "${cleared.directoriesRemoved} dirs removed)",
        )
        root.deleteRecursively()
    }
}
