package app.pocketshell.launchers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * M7.1 Phase 1 — launcher icon storage pins (PART D): imports are COPIES in
 * app-controlled storage, referenced by stored filename — never by the
 * original picker URI. Missing/empty/unsafe inputs fail closed (the UI
 * falls back to the text badge).
 */
class LauncherIconStoreTest {

    private fun tempBase(): File = Files.createTempDirectory("psh-icons").toFile()

    @Test
    fun `import copies the bytes and returns the stored filename`() {
        val base = tempBase()
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val filename = LauncherIconStore.import(base, "tool-abc", ByteArrayInputStream(bytes))
        assertEquals("tool-abc.icon", filename)
        val stored = LauncherIconStore.file(base, filename!!)
        assertNotNull(stored)
        assertTrue(stored!!.readBytes().contentEquals(bytes))
    }

    @Test
    fun `imported icon survives independently of the original source`() {
        // simulate the picker's temporary file: import, then DELETE the source
        val base = tempBase()
        val original = File.createTempFile("picker", ".jpg")
        original.writeBytes(byteArrayOf(9, 8, 7))
        val filename = LauncherIconStore.import(base, "builtin-chatgpt", original.inputStream())
        assertNotNull(filename)
        original.delete()
        // the launcher icon still resolves and still holds the bytes
        val stored = LauncherIconStore.file(base, filename!!)
        assertNotNull(stored)
        assertTrue(stored!!.readBytes().contentEquals(byteArrayOf(9, 8, 7)))
    }

    @Test
    fun `empty stream is rejected`() {
        val base = tempBase()
        assertNull(LauncherIconStore.import(base, "tool-x", ByteArrayInputStream(ByteArray(0))))
        assertNull(LauncherIconStore.file(base, "tool-x.icon"))
    }

    @Test
    fun `unsafe launcher ids are never written`() {
        val base = tempBase()
        assertNull(LauncherIconStore.import(base, "../escape", ByteArrayInputStream(byteArrayOf(1))))
        assertNull(LauncherIconStore.import(base, "", ByteArrayInputStream(byteArrayOf(1))))
        assertNull(LauncherIconStore.import(base, "a/b", ByteArrayInputStream(byteArrayOf(1))))
        assertEquals(0, LauncherIconStore.directory(base).listFiles()?.size ?: 0)
    }

    @Test
    fun `file lookup refuses path traversal and missing files`() {
        val base = tempBase()
        assertNull(LauncherIconStore.file(base, "../secrets"))
        assertNull(LauncherIconStore.file(base, "sub/dir.icon"))
        assertNull(LauncherIconStore.file(base, "missing.icon"))
        // a zero-byte file counts as missing (broken icon → badge fallback)
        LauncherIconStore.directory(base).apply { mkdirs() }
        File(LauncherIconStore.directory(base), "broken.icon").writeBytes(ByteArray(0))
        assertNull(LauncherIconStore.file(base, "broken.icon"))
    }

    @Test
    fun `remove deletes the copy and is idempotent`() {
        val base = tempBase()
        val filename = LauncherIconStore.import(base, "tool-del", ByteArrayInputStream(byteArrayOf(5)))
        assertNotNull(filename)
        LauncherIconStore.remove(base, "tool-del")
        assertNull(LauncherIconStore.file(base, filename!!))
        // removing again (or an unknown id) is a no-op, not a crash
        LauncherIconStore.remove(base, "tool-del")
        LauncherIconStore.remove(base, "never-existed")
        assertFalse(File(LauncherIconStore.directory(base), "tool-del.icon").exists())
    }
}
