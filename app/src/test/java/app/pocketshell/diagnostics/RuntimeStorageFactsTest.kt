package app.pocketshell.diagnostics

import app.pocketshell.runtime.RuntimeMetadata
import app.pocketshell.runtime.RuntimeStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Regression tests for the Diagnostics crash mechanism (user report: the
 * Diagnostics page degrades and ANR-crashes as the app-owned runtime storage
 * grows). The old screen code ran two full, unbounded `File.walkTopDown()`
 * passes over the whole runtime tree inline during composition — main thread
 * — so cost grew without bound with installed packages. [RuntimeStorageFacts]
 * replaces it with one budget-capped NOFOLLOW walk; these tests pin its
 * correctness against fixture ground truth, its honesty under truncation,
 * and its cost against the old collection on a 40k-file fixture.
 */
class RuntimeStorageFactsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fileBytes = 512

    /** Bulk file layout: `dirs` directories of `perDir` files, plus usr/bin tools. */
    private fun makeRootfs(rootfs: Path, dirs: Int, perDir: Int): Long {
        val usrBin = Files.createDirectories(rootfs.resolve("usr/bin"))
        var fileCount = 0L
        repeat(dirs) { d ->
            val dir = Files.createDirectories(rootfs.resolve("home/user/pkg$d"))
            repeat(perDir) { i ->
                Files.write(dir.resolve("obj$i.so"), ByteArray(fileBytes))
                fileCount++
            }
            Files.write(usrBin.resolve("tool$d"), ByteArray(fileBytes))
            fileCount++
        }
        return fileCount
    }

    /** Alpine merged-usr shape: top-level DIRECTORY symlinks into usr/. */
    private fun addMergedUsrSymlinks(rootfs: Path) {
        Files.createDirectories(rootfs.resolve("usr/lib"))
        Files.createSymbolicLink(rootfs.resolve("bin"), rootfs.resolve("usr/bin"))
        Files.createSymbolicLink(rootfs.resolve("lib"), rootfs.resolve("usr/lib"))
        Files.createSymbolicLink(rootfs.resolve("sbin"), rootfs.resolve("usr/sbin"))
        Files.createSymbolicLink(rootfs.resolve("usr/bin/sh"), rootfs.resolve("usr/bin/tool0"))
    }

    private fun writeRuntimeMetadata(base: File) {
        RuntimeMetadata.write(
            File(File(base, "runtime"), "runtime.json"),
            RuntimeMetadata(
                distribution = "Alpine",
                distributionVersion = "3.24.1",
                architecture = "aarch64",
                installedAtEpochMs = 1_700_000_000_000,
                rootfsSha256 = "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259",
                state = "READY",
            ),
        )
    }

    /** Ground truth: NOFOLLOW walk over rootDir (size) + rootfs regular count. */
    private fun groundTruth(rootDir: File, rootfsDir: File): Pair<Long, Long> {
        var bytes = 0L
        var rootfsFiles = 0L
        Files.walkFileTree(
            rootDir.toPath(),
            java.util.EnumSet.noneOf(java.nio.file.FileVisitOption::class.java),
            Int.MAX_VALUE,
            object : java.nio.file.SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: java.nio.file.attribute.BasicFileAttributes,
                ): java.nio.file.FileVisitResult {
                    if (attrs.isRegularFile) {
                        bytes += attrs.size()
                        if (file.startsWith(rootfsDir.toPath())) rootfsFiles++
                    }
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
        return bytes to rootfsFiles
    }

    @Test
    fun `sizes and counts match fixture ground truth with merged-usr symlinks`() {
        val base = tmp.newFolder("base")
        val rootfs = Files.createDirectories(File(base, "runtime/rootfs").toPath())
        val created = makeRootfs(rootfs, dirs = 3, perDir = 5) // 18 files
        addMergedUsrSymlinks(rootfs)
        writeRuntimeMetadata(base)
        val storage = RuntimeStorage(base)

        val (truthBytes, truthCount) = groundTruth(storage.rootDir, storage.rootfsDir)
        assertEquals(18L, truthCount) // fixture sanity: symlinks are not files
        assertEquals(18L * fileBytes + metadataBytes(base), truthBytes)

        val facts = RuntimeStorageFacts.collect(
            storage,
            volumeFreeBytes = { 12_345L },
        )

        assertEquals(truthBytes, facts.runtimeSizeBytes)
        assertEquals(truthCount, facts.rootfsFileCount!!.toLong())
        assertEquals("Alpine", facts.metadata?.distribution)
        assertEquals(12_345L, facts.freeBytes)
        assertFalse(facts.truncated)
    }

    @Test
    fun `directory symlinks are not double counted unlike walkTopDown`() {
        val base = tmp.newFolder("base")
        val rootfs = Files.createDirectories(File(base, "runtime/rootfs").toPath())
        makeRootfs(rootfs, dirs = 3, perDir = 5)
        addMergedUsrSymlinks(rootfs)
        val storage = RuntimeStorage(base)

        val facts = RuntimeStorageFacts.collect(storage, volumeFreeBytes = { null })

        // usr/bin and its /bin alias are the same 3+3 files: counted once.
        assertEquals(18, facts.rootfsFileCount)
        // The old collection FOLLOWS the alias and double counts:
        val oldCount = storage.rootfsDir.walkTopDown().filter { it.isFile }.count()
        assertTrue("old walkTopDown inflates via merged-usr aliases", oldCount > facts.rootfsFileCount!!)
    }

    @Test
    fun `cycle directory symlink terminates without hanging`() {
        val base = tmp.newFolder("base")
        val rootfs = Files.createDirectories(File(base, "runtime/rootfs").toPath())
        makeRootfs(rootfs, dirs = 2, perDir = 3) // 2*3 obj + 2 tool = 8 files
        Files.createSymbolicLink(rootfs.resolve("loop"), rootfs.parent)
        val storage = RuntimeStorage(base)

        val facts = RuntimeStorageFacts.collect(storage, volumeFreeBytes = { null })

        assertEquals(8, facts.rootfsFileCount)
        assertFalse(facts.truncated)
    }

    @Test
    fun `budget caps the walk and flags truncation honestly`() {
        val base = tmp.newFolder("base")
        val rootfs = Files.createDirectories(File(base, "runtime/rootfs").toPath())
        makeRootfs(rootfs, dirs = 10, perDir = 10) // 110 files
        val storage = RuntimeStorage(base)

        val facts = RuntimeStorageFacts.collect(
            storage,
            maxFiles = 7L,
            volumeFreeBytes = { null },
        )

        assertTrue(facts.truncated)
        assertTrue(facts.rootfsFileCount!! in 1..8) // budget may overshoot by one visit
        assertTrue(facts.runtimeSizeBytes!! > 0) // but what it saw is counted, never faked
    }

    @Test
    fun `absent runtime yields null facts without throwing`() {
        val base = tmp.newFolder("base") // no runtime/ at all
        val storage = RuntimeStorage(base)

        val facts = RuntimeStorageFacts.collect(storage, volumeFreeBytes = { 42L })

        assertNull(facts.metadata)
        assertNull(facts.runtimeSizeBytes)
        assertNull(facts.rootfsFileCount)
        assertEquals(42L, facts.freeBytes)
        assertFalse(facts.truncated)
    }

    @Test
    fun `unreadable metadata surfaces as null, not a crash`() {
        val base = tmp.newFolder("base")
        Files.createDirectories(File(base, "runtime/rootfs").toPath())
        File(base, "runtime/runtime.json").writeText("{ this is not json !!!")
        val storage = RuntimeStorage(base)

        val facts = RuntimeStorageFacts.collect(storage, volumeFreeBytes = { null })

        assertNull(facts.metadata)
        assertFalse(facts.truncated)
    }

    /**
     * MEASUREMENT: the old collection (verbatim from RuntimeDiagnostics.report
     * — two unbounded walkTopDown passes, which the screen ran on the main
     * thread during composition) vs [RuntimeStorageFacts.collect] on a
     * 40k-file rootfs shaped like months of apk installs. Prints both so the
     * fix's delta is a measured number, and pins the new collector's exact
     * correctness against fixture ground truth.
     */
    @Test
    fun `40k-file rootfs - bounded collector correct and measured against old walkTopDown`() {
        val base = tmp.newFolder("base")
        val rootfs = Files.createDirectories(File(base, "runtime/rootfs").toPath())
        val files = makeRootfs(rootfs, dirs = 4_000, perDir = 10) // 44_000 files
        addMergedUsrSymlinks(rootfs)
        val storage = RuntimeStorage(base)
        val (truthBytes, truthCount) = groundTruth(storage.rootDir, storage.rootfsDir)
        assertEquals(44_000L, truthCount)

        // OLD collection, verbatim from RuntimeDiagnostics.report().
        val t0 = System.nanoTime()
        val oldSize = storage.rootDir
            .walkTopDown().filter { it.isFile }.fold(0L) { acc, f -> acc + f.length() }
        val t1 = System.nanoTime()
        val oldCount = storage.rootfsDir
            .walkTopDown().filter { it.isFile }.count()
        val t2 = System.nanoTime()

        // NEW collection (what the fixed screen runs on Dispatchers.IO).
        val t3 = System.nanoTime()
        val facts = RuntimeStorageFacts.collect(storage, volumeFreeBytes = { 12_345L })
        val t4 = System.nanoTime()

        println(
            "DIAGNOSTICS-BASELINE 44k-file fixture: truth=$truthCount files / $truthBytes B | " +
                "old size pass=${(t1 - t0) / 1_000_000} ms (size=$oldSize), " +
                "old count pass=${(t2 - t1) / 1_000_000} ms (count=$oldCount) " +
                "-> old total=${(t2 - t0) / 1_000_000} ms | " +
                "new single NOFOLLOW pass=${(t4 - t3) / 1_000_000} ms",
        )

        // The old walk FOLLOWS the merged-usr aliases: inflated numbers, 2x+
        // the stat work on usr/, and unbounded — no budget, no truncation.
        assertTrue(oldCount > truthCount)
        assertTrue(oldSize > truthBytes)

        // The new collector is exact here (budget never hit) and honest.
        assertEquals(truthCount, facts.rootfsFileCount!!.toLong())
        assertEquals(truthBytes, facts.runtimeSizeBytes)
        assertFalse(facts.truncated)
        assertEquals(12_345L, facts.freeBytes)
        assertEquals(files, truthCount)
    }

    /** runtime.json is part of runtime/ size (not the rootfs count). */
    private fun metadataBytes(base: File): Long =
        File(base, "runtime/runtime.json").length()
}
