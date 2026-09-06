package app.pocketshell.files

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 pins for the open-policy file area — the Android download shelf.
 * Same engine, no protected prefixes: everything inside the shelf root is
 * ordinary app-owned storage.
 */
class ShelfAreaTest {

    private lateinit var base: Path
    private lateinit var area: FileDirArea

    private fun p(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    @Throws(Exception::class)
    private fun withShelf(block: () -> Unit) {
        base = Files.createTempDirectory("pocketshell-shelf")
        area = FileDirArea.create(
            root = base.toFile(),
            id = AreaId(AreaKind.ANDROID_SHELF),
            displayName = "test shelf",
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!
        try {
            block()
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `full operation set works with no protected prefixes`() = withShelf {
        assertEquals(AreaCapability.entries.toSet(), area.capabilities)

        assertTrue(area.writeBytesAtomic(p("/site-12345.zip"), "ZIPBYTES".toByteArray()).success)
        assertTrue(area.createDirectory(p("/imports")).success)
        assertTrue(area.createDirectory(p("/imports/etc")).success) // no protected "etc" in a shelf
        assertTrue(area.writeBytesAtomic(p("/imports/etc/notes.txt"), "n".toByteArray()).success)

        val read = area.readBytes(p("/site-12345.zip"), 1024)
        assertEquals("ZIPBYTES", String((read as ReadResult.Ok).bytes))

        assertTrue(area.rename(p("/site-12345.zip"), "project.zip").success)
        assertNull(area.stat(p("/site-12345.zip")))

        assertTrue(area.copy(p("/project.zip"), p("/imports/project.zip")).success)
        assertTrue(area.move(p("/imports/project.zip"), p("/imports/moved.zip")).success)
        assertFalse(area.stat(p("/imports/project.zip")) != null)

        // delete everywhere in the shelf, recursive
        assertTrue(area.delete(p("/imports/etc")).success)
        assertNull(area.stat(p("/imports/etc")))
        assertTrue(area.delete(p("/project.zip")).success)
    }

    @Test
    fun `shelf listing and streaming round-trip`() = withShelf {
        val payload = ByteArray(200_000) { (it % 251).toByte() }
        assertTrue(area.writeBytesAtomic(p("/a.txt"), "A".toByteArray()).success)
        assertTrue(area.writeBytesAtomic(p("/b.bin"), payload).success)
        val listing = area.list(p("/"))
        assertTrue(listing is ListResult.Ok)
        assertEquals(listOf("a.txt", "b.bin"), (listing as ListResult.Ok).entries.map { it.name })

        when (val opened = area.openRead(p("/b.bin"))) {
            is StreamRead.Ok -> opened.stream.use { input ->
                org.junit.Assert.assertArrayEquals(
                    "stream must deliver byte-identical content",
                    payload,
                    input.readBytes(),
                )
            }
            else -> throw AssertionError("expected stream")
        }
    }

    @Test
    fun `unavailable roots produce no area (honest unavailability)`() {
        val missing = Files.createTempDirectory("pocketshell-missing-root")
        val root = missing.resolve("not-created").toFile()
        assertNull(
            FileDirArea.create(
                root = root,
                id = AreaId(AreaKind.ANDROID_SHELF),
                displayName = "x",
                policy = FileDirArea.MutationPolicy.OPEN,
            ),
        )
        missing.toFile().deleteRecursively()
    }
}
