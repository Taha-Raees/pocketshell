package app.pocketshell.files

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 pins for cross-domain transfers: verified copy, verified-copy-then-
 * delete move, honest symlink handling, and the never-lose-source guarantees.
 */
class CrossAreaTest {

    private lateinit var guestBase: Path
    private lateinit var shelfBase: Path
    private lateinit var guest: FileDirArea
    private lateinit var shelf: FileDirArea

    private fun p(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    @Throws(Exception::class)
    private fun withAreas(block: () -> Unit) {
        guestBase = Files.createTempDirectory("pocketshell-xguest")
        shelfBase = Files.createTempDirectory("pocketshell-xshelf")
        guest = FileDirArea.create(
            root = guestBase.toFile(),
            id = AreaId(AreaKind.GUEST_LINUX),
            displayName = "guest",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!
        shelf = FileDirArea.create(
            root = shelfBase.toFile(),
            id = AreaId(AreaKind.ANDROID_SHELF),
            displayName = "shelf",
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!
        Files.createDirectories(guestBase.resolve("root"))
        try {
            block()
        } finally {
            guestBase.toFile().deleteRecursively()
            shelfBase.toFile().deleteRecursively()
        }
    }

    @Test
    fun `copying a file across domains is byte-exact and counted`() = withAreas {
        Files.write(guestBase.resolve("root/app.js"), "console.log('export me')".toByteArray())
        val result = CrossArea.copy(guest, p("/root/app.js"), shelf, p("/app.js"))
        assertTrue("expected success, got: ${result.reason}", result.success)
        assertEquals(1, result.filesCopied)
        assertEquals("console.log('export me')", shelfBase.resolve("app.js").toFile().readText())
        assertTrue(guestBase.resolve("root/app.js").toFile().exists())
    }

    @Test
    fun `importing a multi-block file is verified byte-exact`() = withAreas {
        val payload = ByteArray(700_000) { ((it * 31 + 7) % 256).toByte() }
        shelfBase.resolve("project.zip").toFile().writeBytes(payload)
        val result = CrossArea.copy(shelf, p("/project.zip"), guest, p("/root/project.zip"))
        assertTrue("expected success, got: ${result.reason}", result.success)
        assertEquals(1, result.filesCopied)
        assertTrue(payload.contentEquals(guestBase.resolve("root/project.zip").toFile().readBytes()))
        // no temp files left behind in the guest
        val leftovers = guestBase.resolve("root").toFile().list { _, n -> n.contains(".pocketshell-tmp-") }
        assertTrue(leftovers!!.isEmpty())
    }

    @Test
    fun `cross-domain move deletes the source only after verified copy`() = withAreas {
        Files.write(shelfBase.resolve("bundle.zip"), "BUNDLE".toByteArray())
        val result = CrossArea.move(shelf, p("/bundle.zip"), guest, p("/root/bundle.zip"))
        assertTrue("expected success, got: ${result.reason}", result.success)
        assertFalse("source must be gone after a verified move", shelfBase.resolve("bundle.zip").toFile().exists())
        assertEquals("BUNDLE", guestBase.resolve("root/bundle.zip").toFile().readText())
    }

    @Test
    fun `a denied target leaves the source fully intact`() = withAreas {
        Files.write(guestBase.resolve("root/data.txt"), "keep".toByteArray())
        val result = CrossArea.copy(guest, p("/root/data.txt"), shelf, p("/imports/data.txt"))
        assertFalse(result.success) // /imports does not exist and create is single-level
        assertTrue("failed copy must keep the source", guestBase.resolve("root/data.txt").toFile().exists())
        assertNull(shelf.stat(p("/imports")))
    }

    @Test
    fun `copying into a protected guest prefix is denied and keeps the source`() = withAreas {
        Files.write(shelfBase.resolve("evil.txt"), "no".toByteArray())
        val result = CrossArea.copy(shelf, p("/evil.txt"), guest, p("/etc/evil.txt"))
        assertFalse(result.success)
        assertTrue("source must survive a denied copy", shelfBase.resolve("evil.txt").toFile().exists())
        assertFalse(guestBase.resolve("etc/evil.txt").toFile().exists())
    }

    @Test
    fun `move whose source delete is denied reports copied-not-moved honestly`() = withAreas {
        Files.createDirectories(guestBase.resolve("usr/bin"))
        Files.write(guestBase.resolve("usr/bin/tool"), "TOOL".toByteArray())
        val result = CrossArea.move(guest, p("/usr/bin/tool"), shelf, p("/tool"))
        assertFalse("copy succeeded but source delete was denied — not a move", result.success)
        assertTrue(result.reason!!.contains("source could not be deleted"))
        assertEquals(1, result.filesCopied)
        assertEquals("TOOL", shelfBase.resolve("tool").toFile().readText())
        assertTrue("source must be intact", guestBase.resolve("usr/bin/tool").toFile().exists())
    }

    @Test
    fun `directory copy skips nested symlinks with warnings and copies the rest`() = withAreas {
        Files.createDirectories(guestBase.resolve("root/project/assets"))
        Files.write(guestBase.resolve("root/project/main.sh"), "#!/bin/sh".toByteArray())
        Files.write(guestBase.resolve("root/project/assets/logo.txt"), "LOGO".toByteArray())
        Files.createSymbolicLink(
            guestBase.resolve("root/project/busybox-link"),
            guestBase.fileSystem.getPath("/bin/busybox"),
        )
        val result = CrossArea.copy(guest, p("/root/project"), shelf, p("/project"))
        assertTrue("expected success, got: ${result.reason}", result.success)
        assertEquals(2, result.filesCopied)
        assertTrue(result.warnings.any { it.contains("skipped symlink") })
        assertEquals("#!/bin/sh", shelfBase.resolve("project/main.sh").toFile().readText())
        assertEquals("LOGO", shelfBase.resolve("project/assets/logo.txt").toFile().readText())
        assertFalse("no busybox content may appear in the copy", shelfBase.resolve("project/busybox-link").toFile().exists())
    }

    @Test
    fun `a top-level symlink source copies as resolved content with a warning`() = withAreas {
        Files.createDirectories(guestBase.resolve("bin"))
        Files.write(guestBase.resolve("bin/busybox"), "BB".toByteArray())
        Files.createSymbolicLink(
            guestBase.resolve("root/bin-link"),
            guestBase.fileSystem.getPath("/bin/busybox"),
        )
        val result = CrossArea.copy(guest, p("/root/bin-link"), shelf, p("/busybox-content"))
        assertTrue("expected success, got: ${result.reason}", result.success)
        assertTrue(result.warnings.any { it.contains("resolved content") })
        assertEquals("BB", shelfBase.resolve("busybox-content").toFile().readText())
    }

    @Test
    fun `an escaping top-level symlink source is refused with a warning, not a copy`() = withAreas {
        Files.createSymbolicLink(
            guestBase.resolve("root/out-link"),
            guestBase.fileSystem.getPath("../../../../etc/evil"),
        )
        val result = CrossArea.copy(guest, p("/root/out-link"), shelf, p("/evil"))
        assertTrue(result.warnings.any { it.contains("skipped symlink") })
        assertFalse(shelfBase.resolve("evil").toFile().exists())
    }

    @Test
    fun `same-area transfers are refused — use the area's own native ops`() = withAreas {
        Files.write(guestBase.resolve("root/a.txt"), "a".toByteArray())
        val result = CrossArea.copy(guest, p("/root/a.txt"), guest, p("/root/b.txt"))
        assertFalse(result.success)
        assertTrue(result.reason!!.contains("same storage area"))
    }
}
