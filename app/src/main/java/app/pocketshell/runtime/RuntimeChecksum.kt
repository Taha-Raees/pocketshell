package app.pocketshell.runtime

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Streaming SHA-256 for rootfs archive verification.
 * Constant-time-free hex output, lowercase — matches Alpine's published sums.
 */
object RuntimeChecksum {

    private const val BUFFER_SIZE = 64 * 1024

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** Streaming hash — safe for multi-megabyte archives. */
    fun sha256Hex(file: File): String = file.inputStream().buffered().use { sha256Hex(it) }

    fun sha256Hex(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    /** Constant-shape comparison (no early exit on length-independent secret paths). */
    fun matches(actualHex: String, expectedHex: String): Boolean {
        if (actualHex.length != expectedHex.length) return false
        var result = 0
        for (i in actualHex.indices) {
            result = result or (actualHex[i].lowercaseChar().code xor expectedHex[i].lowercaseChar().code)
        }
        return result == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
