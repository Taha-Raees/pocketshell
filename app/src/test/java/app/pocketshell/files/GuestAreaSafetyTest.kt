package app.pocketshell.files

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 safety battery for the guest-shaped area (protected runtime policy):
 * the M6 runtime freeze expressed as engine pins — symlink containment,
 * NOFOLLOW deletes, protected prefixes, collision rules.
 */
class GuestAreaSafetyTest {

    private lateinit var base: Path
    private lateinit var area: FileDirArea

    /** A miniature rootfs with the shapes that matter (incl. real symlinks). */
    private fun buildGuestFixture() {
        base = Files.createTempDirectory("pocketshell-guearea")
        val root = base.toFile()
        File(root, "bin").mkdirs(); File(root, "bin/busybox").writeText("BB")
        File(root, "etc").mkdirs(); File(root, "etc/passwd").writeText("pw")
        File(root, "usr/bin").mkdirs()
        val tool = File(root, "usr/bin/tool")
        tool.writeText("TOOL")
        Files.setPosixFilePermissions(tool.toPath(), PosixFilePermissions.fromString("rwx------"))
        File(root, "usr/local/work").mkdirs(); File(root, "usr/local/work/file.txt").writeText("hello local")
        File(root, "root/projects/demo").mkdirs(); File(root, "root/projects/demo/app.js").writeText("console.log(1)")
        File(root, "root").let { File(it, ".env").writeText("SECRET=1") }
        File(root, "tmp").mkdirs()
        File(root, "root/sub").mkdirs(); File(root, "root/sub/inner.txt").writeText("inner")
        // symlinks under /root
        Files.createSymbolicLink(root.resolve("root/bin-link").toPath(), base.fileSystem.getPath("/bin/busybox"))
        Files.createSymbolicLink(root.resolve("root/out-link").toPath(), base.fileSystem.getPath("../../../../etc/evil"))
        Files.createSymbolicLink(root.resolve("root/dangling").toPath(), base.fileSystem.getPath("missing-target"))
        Files.createSymbolicLink(root.resolve("root/dir-sym").toPath(), base.fileSystem.getPath("sub"))
    }

    private fun freshArea(policy: FileDirArea.MutationPolicy): FileDirArea =
        FileDirArea.create(
            root = base.toFile(),
            id = AreaId(AreaKind.GUEST_LINUX),
            displayName = "test guest",
            policy = policy,
        )!!

    private fun p(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    @Throws(Exception::class)
    private fun withGuestArea(block: () -> Unit) {
        buildGuestFixture()
        area = freshArea(FileDirArea.MutationPolicy.guestRuntime())
        try {
            block()
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ stat

    @Test
    fun `stat reports kinds and symlink target strings without following`() = withGuestArea {
        val file = area.stat(p("/root/.env"))!!
        assertEquals(EntryKind.FILE, file.kind)
        assertEquals("SECRET=1".length.toLong(), file.sizeBytes)

        val link = area.stat(p("/root/bin-link"))!!
        assertEquals(EntryKind.SYMLINK, link.kind)
        assertEquals("/bin/busybox", link.symlinkTarget)
        assertNull(link.sizeBytes)

        assertEquals(EntryKind.DIRECTORY, area.stat(p("/root/projects"))!!.kind)
        assertEquals(EntryKind.DIRECTORY, area.stat(p("/"))!!.kind)
        assertNull(area.stat(p("/root/nothing")))
        // Stat through a symlinked parent component is refused (not followed).
        assertNull(area.stat(p("/root/dir-sym/inner.txt")))
    }

    // ------------------------------------------------------------------ list

    @Test
    fun `listing sorts directories first and includes dotfiles and symlinks`() = withGuestArea {
        val listing = area.list(p("/root"))
        assertTrue(listing is ListResult.Ok)
        val names = (listing as ListResult.Ok).entries.map { it.name }
        // directories first (projects, sub), then others alphabetically
        assertEquals(listOf("projects", "sub", ".env", "bin-link", "dangling", "dir-sym", "out-link"), names)
    }

    @Test
    fun `listing resolves a final symlink-to-directory under containment rules`() = withGuestArea {
        val listing = area.list(p("/root/dir-sym"))
        assertTrue(listing is ListResult.Ok)
        assertEquals(listOf("inner.txt"), (listing as ListResult.Ok).entries.map { it.name })
    }

    @Test
    fun `listing the guest root works (browsing is unrestricted)`() = withGuestArea {
        val listing = area.list(p("/"))
        assertTrue(listing is ListResult.Ok)
        val names = (listing as ListResult.Ok).entries.map { it.name }
        assertTrue(names.containsAll(listOf("bin", "etc", "usr", "root", "tmp")))
    }

    @Test
    fun `listing a file fails honestly`() = withGuestArea {
        assertTrue(area.list(p("/root/.env")) is ListResult.Error)
    }

    // ------------------------------------------------------------------ read

    @Test
    fun `reading through a contained guest-view absolute symlink resolves inside the root`() = withGuestArea {
        val read = area.readBytes(p("/root/bin-link"), 1024)
        assertTrue(read is ReadResult.Ok)
        assertEquals("BB", String((read as ReadResult.Ok).bytes))
    }

    @Test
    fun `reading through an escaping symlink is refused`() = withGuestArea {
        val read = area.readBytes(p("/root/out-link"), 1024)
        assertTrue(read is ReadResult.Error)
    }

    @Test
    fun `reading a dangling symlink fails honestly`() = withGuestArea {
        assertTrue(area.readBytes(p("/root/dangling"), 1024) is ReadResult.Error)
    }

    @Test
    fun `reading through a symlinked parent component is refused`() = withGuestArea {
        assertTrue(area.readBytes(p("/root/dir-sym/inner.txt"), 1024) is ReadResult.Error)
    }

    @Test
    fun `bounded read reports TooLarge with the real size`() = withGuestArea {
        val read = area.readBytes(p("/root/.env"), 4)
        assertTrue(read is ReadResult.TooLarge)
        assertEquals("SECRET=1".length.toLong(), (read as ReadResult.TooLarge).sizeBytes)
    }

    // ----------------------------------------------------------------- write

    @Test
    fun `atomic write creates and overwrites regular files`() = withGuestArea {
        assertTrue(area.writeBytesAtomic(p("/root/new.txt"), "one".toByteArray()).success)
        assertEquals("one", String((area.readBytes(p("/root/new.txt"), 100) as ReadResult.Ok).bytes))
        assertTrue(area.writeBytesAtomic(p("/root/new.txt"), "two".toByteArray()).success)
        assertEquals("two", String((area.readBytes(p("/root/new.txt"), 100) as ReadResult.Ok).bytes))
    }

    @Test
    fun `aborted write session leaves target and directory clean`() = withGuestArea {
        assertTrue(area.writeBytesAtomic(p("/root/keep.txt"), "old".toByteArray()).success)
        when (val opened = area.openWriteAtomic(p("/root/keep.txt"))) {
            is StreamWriteOpen.Ok -> opened.session.use { session ->
                session.stream.write("new".toByteArray())
                session.abort()
            }
            else -> throw AssertionError("expected a session")
        }
        assertEquals("old", String((area.readBytes(p("/root/keep.txt"), 100) as ReadResult.Ok).bytes))
        val leftovers = base.resolve("root").toFile().list { _, name -> name.contains(".pocketshell-tmp-") }
        assertTrue("temp file must be cleaned on abort", leftovers!!.isEmpty())
    }

    @Test
    fun `writing through a symlink is refused`() = withGuestArea {
        val result = area.writeBytesAtomic(p("/root/bin-link"), "EVIL".toByteArray())
        assertEquals(OpResult.Kind.DENIED, result.kind)
        assertEquals("BB", base.resolve("bin/busybox").toFile().readText())
    }

    @Test
    fun `writing into a protected runtime prefix is denied, usr-local is allowed`() = withGuestArea {
        assertEquals(OpResult.Kind.DENIED, area.writeBytesAtomic(p("/etc/hosts"), "x".toByteArray()).kind)
        assertEquals(OpResult.Kind.DENIED, area.writeBytesAtomic(p("/usr/bin/newbin"), "x".toByteArray()).kind)
        assertEquals(OpResult.Kind.DENIED, area.writeBytesAtomic(p("/bin/newbin"), "x".toByteArray()).kind)
        assertTrue(area.writeBytesAtomic(p("/usr/local/work/new.txt"), "local".toByteArray()).success)
        assertTrue(area.writeBytesAtomic(p("/root/notes.md"), "home".toByteArray()).success)
        assertTrue(area.writeBytesAtomic(p("/tmp/scratch.txt"), "tmp".toByteArray()).success)
    }

    // ---------------------------------------------------------- create + rename

    @Test
    fun `create file and directory obey collisions, missing parents, and protection`() = withGuestArea {
        assertTrue(area.createFile(p("/root/created.txt")).success)
        assertEquals(OpResult.Kind.FAILED, area.createFile(p("/root/created.txt")).kind)
        // A dangling symlink at the target is an existing node (NOFOLLOW).
        assertEquals(OpResult.Kind.FAILED, area.createFile(p("/root/dangling")).kind)
        assertEquals(OpResult.Kind.FAILED, area.createDirectory(p("/root/a/b")).kind)
        assertEquals(OpResult.Kind.DENIED, area.createDirectory(p("/etc/newdir")).kind)
        assertEquals(OpResult.Kind.DENIED, area.createFile(p("/var/log/x")).kind)
        assertTrue(area.createDirectory(p("/root/newdir")).success)
        assertTrue(area.createDirectory(p("/usr/local/newdir")).success)
    }

    @Test
    fun `rename works within a directory and refuses collisions and protection`() = withGuestArea {
        assertTrue(area.rename(p("/root/projects/demo/app.js"), "main.js").success)
        assertNull(area.stat(p("/root/projects/demo/app.js")))
        assertEquals(EntryKind.FILE, area.stat(p("/root/projects/demo/main.js"))!!.kind)

        assertEquals(OpResult.Kind.FAILED, area.rename(p("/root/.env"), "projects").kind)
        assertEquals(OpResult.Kind.FAILED, area.rename(p("/root/.env"), "../x").kind)
        assertEquals(OpResult.Kind.DENIED, area.rename(p("/etc/passwd"), "motd").kind)
        assertEquals(OpResult.Kind.FAILED, area.rename(p("/root/nothing"), "x").kind)
    }

    @Test
    fun `renaming a symlink moves the node, not the target`() = withGuestArea {
        assertTrue(area.rename(p("/root/bin-link"), "renamed-link").success)
        val moved = area.stat(p("/root/renamed-link"))!!
        assertEquals(EntryKind.SYMLINK, moved.kind)
        assertEquals("/bin/busybox", moved.symlinkTarget)
        assertEquals("BB", base.resolve("bin/busybox").toFile().readText())
    }

    // ------------------------------------------------------------------ copy

    @Test
    fun `copy preserves bytes and executable permissions`() = withGuestArea {
        assertTrue(area.copy(p("/usr/bin/tool"), p("/root/tool")).success)
        val copied = base.resolve("root/tool").toFile()
        assertEquals("TOOL", copied.readText())
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(copied.toPath())))
    }

    @Test
    fun `copying a directory recreates symlinks as nodes and never follows them`() = withGuestArea {
        // a project dir with an internal guest-view symlink
        File(base.toFile(), "root/projects/demo/tools").mkdirs()
        Files.createSymbolicLink(
            base.resolve("root/projects/demo/tools/sh"),
            base.fileSystem.getPath("/bin/busybox"),
        )
        assertTrue(area.copy(p("/root/projects/demo"), p("/root/demo-copy")).success)
        val link = area.stat(p("/root/demo-copy/tools/sh"))!!
        assertEquals(EntryKind.SYMLINK, link.kind)
        assertEquals("/bin/busybox", link.symlinkTarget)
        // the symlink was NOT followed: no busybox copy exists in the copy
        assertTrue(!base.resolve("root/demo-copy/tools/busybox").toFile().exists())
        assertEquals("console.log(1)", base.resolve("root/demo-copy/app.js").toFile().readText())
    }

    @Test
    fun `copy refuses collisions, dir-into-itself, and protected targets`() = withGuestArea {
        assertEquals(OpResult.Kind.FAILED, area.copy(p("/root/.env"), p("/root/sub")).kind)
        assertEquals(OpResult.Kind.FAILED, area.copy(p("/root/projects"), p("/root/projects/demo")).kind)
        assertEquals(OpResult.Kind.DENIED, area.copy(p("/root/.env"), p("/etc/evil")).kind)
        // read-only source from a protected area is fine
        assertTrue(area.copy(p("/usr/bin/tool"), p("/root/tool-from-usr")).success)
    }

    // ------------------------------------------------------------------ move

    @Test
    fun `move renames within the area and refuses dir-into-itself and collisions`() = withGuestArea {
        assertTrue(area.move(p("/root/projects/demo"), p("/root/demo-moved")).success)
        assertNull(area.stat(p("/root/projects/demo")))
        assertEquals(EntryKind.DIRECTORY, area.stat(p("/root/demo-moved"))!!.kind)

        assertEquals(OpResult.Kind.FAILED, area.move(p("/root/sub"), p("/root/sub/inner")).kind)
        assertEquals(OpResult.Kind.FAILED, area.move(p("/root/.env"), p("/root/sub")).kind)
    }

    @Test
    fun `moving out of a protected area is denied`() = withGuestArea {
        assertEquals(OpResult.Kind.DENIED, area.move(p("/usr/bin/tool"), p("/root/tool")).kind)
        assertEquals(OpResult.Kind.DENIED, area.move(p("/etc/passwd"), p("/root/passwd")).kind)
    }

    // ---------------------------------------------------------------- delete

    @Test
    fun `deleting a symlink removes only the node — the target survives`() = withGuestArea {
        assertTrue(area.delete(p("/root/bin-link")).success)
        assertNull(area.stat(p("/root/bin-link")))
        assertEquals("BB", base.resolve("bin/busybox").toFile().readText())
    }

    @Test
    fun `deleting a tree with escaping symlinks never touches the targets`() = withGuestArea {
        File(base.toFile(), "root/delme").mkdirs()
        File(base.toFile(), "root/delme/data.txt").writeText("data")
        Files.createSymbolicLink(
            base.resolve("root/delme/escape"),
            base.fileSystem.getPath("/etc/passwd"),
        )
        assertTrue(area.delete(p("/root/delme")).success)
        assertNull(area.stat(p("/root/delme")))
        assertEquals("pw", base.resolve("etc/passwd").toFile().readText())
    }

    @Test
    fun `deleting protected areas and the root is denied`() = withGuestArea {
        assertEquals(OpResult.Kind.DENIED, area.delete(p("/etc")).kind)
        assertEquals(OpResult.Kind.DENIED, area.delete(p("/usr")).kind)
        assertEquals(OpResult.Kind.DENIED, area.delete(p("/")).kind)
        assertEquals(OpResult.Kind.FAILED, area.delete(p("/root/nothing")).kind)
        assertTrue(area.delete(p("/root/sub")).success)
    }
}
