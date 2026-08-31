package app.pocketshell.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom

class RuntimeChecksumTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `known vector abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            RuntimeChecksum.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun `known vector empty`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            RuntimeChecksum.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `streaming equals single-shot on multi-buffer input`() {
        val data = ByteArray(256 * 1024 + 7).also { SecureRandom().nextBytes(it) }
        val file = File(tmp.root, "blob.bin").apply { writeBytes(data) }
        assertEquals(RuntimeChecksum.sha256Hex(data), RuntimeChecksum.sha256Hex(file))
    }

    @Test
    fun `matches is case-insensitive and length-sensitive`() {
        val abc = RuntimeChecksum.sha256Hex("abc".toByteArray())
        assertTrue(RuntimeChecksum.matches(abc, abc.uppercase()))
        assertFalse(RuntimeChecksum.matches(abc, abc.dropLast(1)))
        assertFalse(RuntimeChecksum.matches(abc, "ffd".padEnd(64, '0')))
    }
}
