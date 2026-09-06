package app.pocketshell.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase 2 pins for the pure path-safety layer: traversal rejection, name
 * validation, containment, and the NOFOLLOW delete primitive.
 */
class PathSafetyTest {

    // ---------------------------------------------------------- validatePath

    @Test
    fun `accepts the area root`() {
        assertEquals("/", PathSafety.validatePath("/")!!.value)
    }

    @Test
    fun `accepts canonical absolute paths with spaces and dotfiles`() {
        assertEquals("/root/projects/My Project", PathSafety.validatePath("/root/projects/My Project")!!.value)
        assertEquals("/root/.env", PathSafety.validatePath("/root/.env")!!.value)
    }

    @Test
    fun `rejects relative and blank input`() {
        assertNull(PathSafety.validatePath(null))
        assertNull(PathSafety.validatePath(""))
        assertNull(PathSafety.validatePath("   "))
        assertNull(PathSafety.validatePath("root"))
        assertNull(PathSafety.validatePath("root/projects"))
    }

    @Test
    fun `rejects path traversal everywhere`() {
        assertNull(PathSafety.validatePath("/root/../etc/passwd"))
        assertNull(PathSafety.validatePath("/.."))
        assertNull(PathSafety.validatePath("/root/./x"))
        assertNull(PathSafety.validatePath("/root/.."))
        assertNull(PathSafety.validatePath("/root/projects/../../etc"))
    }

    @Test
    fun `rejects non-canonical forms and NUL`() {
        assertNull(PathSafety.validatePath("/root//x"))
        assertNull(PathSafety.validatePath("/root/"))
        assertNull(PathSafety.validatePath("//"))
        assertNull(PathSafety.validatePath("/root\u0000x"))
    }

    @Test
    fun `rejects over-long paths`() {
        val long = "/" + "a".repeat(5000)
        assertNull(PathSafety.validatePath(long))
    }

    // ---------------------------------------------------------- validateName

    @Test
    fun `accepts ordinary names including dotfiles and spaces`() {
        assertEquals("file.txt", PathSafety.validateName("file.txt"))
        assertEquals(".env", PathSafety.validateName(".env"))
        assertEquals("My Project", PathSafety.validateName("My Project"))
    }

    @Test
    fun `rejects unsafe names`() {
        assertNull(PathSafety.validateName(null))
        assertNull(PathSafety.validateName(""))
        assertNull(PathSafety.validateName("   "))
        assertNull(PathSafety.validateName("."))
        assertNull(PathSafety.validateName(".."))
        assertNull(PathSafety.validateName("a/b"))
        assertNull(PathSafety.validateName("x\u0000y"))
        assertNull(PathSafety.validateName("a".repeat(256)))
    }

    // ------------------------------------------------------------- isInside

    @Test
    fun `containment is prefix-aware and never fooled by sibling prefixes`() {
        val root = "/data/runtime/rootfs"
        assertTrue(PathSafety.isInside(root, "/data/runtime/rootfs"))
        assertTrue(PathSafety.isInside(root, "/data/runtime/rootfs/root/x"))
        assertFalse(PathSafety.isInside(root, "/data/runtime/rootfs-sibling"))
        assertFalse(PathSafety.isInside(root, "/etc/passwd"))
    }

    // ------------------------------------------------------ deleteTreeNoFollow

    @Test
    fun `deleting a tree deletes symlink nodes but never their targets`() {
        val base = Files.createTempDirectory("pocketshell-pathtest")
        try {
            val dir = base.resolve("tree").toFile()
            dir.mkdirs()
            val target = base.resolve("precious.txt").toFile()
            target.writeText("keep me")
            val inner = File(dir, "inner.txt")
            inner.writeText("data")
            Files.createSymbolicLink(dir.resolve("link-file").toPath(), target.toPath())
            val outsideDir = base.resolve("outside-dir").toFile()
            outsideDir.mkdirs()
            File(outsideDir, "deep.txt").writeText("deep")
            Files.createSymbolicLink(dir.resolve("link-dir").toPath(), outsideDir.toPath())

            assertTrue(PathSafety.deleteTreeNoFollow(dir))

            assertFalse(dir.exists())
            assertTrue("symlink TARGET must survive", target.exists() && target.readText() == "keep me")
            assertTrue("symlinked-DIR target must survive", outsideDir.isDirectory)
            assertTrue(File(outsideDir, "deep.txt").exists())
        } finally {
            base.toFile().deleteRecursively()
        }
    }

    @Test
    fun `deleting an absent tree is a success no-op`() {
        val base = Files.createTempDirectory("pocketshell-pathtest")
        try {
            assertTrue(PathSafety.deleteTreeNoFollow(base.resolve("nothing").toFile()))
        } finally {
            base.toFile().deleteRecursively()
        }
    }
}
