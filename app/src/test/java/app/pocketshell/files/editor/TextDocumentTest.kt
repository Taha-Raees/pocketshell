package app.pocketshell.files.editor

import app.pocketshell.files.EntryKind
import app.pocketshell.files.FsEntry
import app.pocketshell.files.ReadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.0.0 Phase 6 pins — the quick editor's PURE decision model
 * ([TextDocument]). Everything the Android ViewModel does around these
 * calls is dispatch glue; the honesty rules live here and are pinned on
 * the JVM:
 *
 *  - valid UTF-8 round-trips BYTE-EXACTLY (the first-save-corruption guard):
 *    ASCII, multibyte, emoji, CRLF/mixed line endings, a leading BOM;
 *  - NUL bytes mark a file binary (never decoded, never rewritten);
 *  - malformed UTF-8 is REFUSED, never lossily decoded (a lossy decode
 *    would replace bytes with U+FFFD and the first save would corrupt);
 *  - the 1 MiB size cap and the honest TooLarge report;
 *  - the save gate: (size, mtime) snapshot vs fresh stat — clear, changed
 *    externally, missing, and "never a blind save" (null snapshot asks).
 */
class TextDocumentTest {

    // ------------------------------------------------------- round-trip fidelity

    @Test
    fun `max quick-edit cap is 1 MiB`() {
        assertEquals(1 shl 20, TextDocument.MAX_QUICK_EDIT_BYTES)
    }

    @Test
    fun `ascii text round-trips byte-exactly`() {
        val bytes = "hello world\nline two\r\n".toByteArray(Charsets.UTF_8)
        val decoded = TextDocument.encode(decode(bytes))
        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun `multibyte emoji and mixed line endings round-trip byte-exactly`() {
        val text = "café — 中文 🐧\r\nsecond line\n\ttabbed\rmixed\n"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertTrue(bytes.contentEquals(TextDocument.encode(decode(bytes))))
    }

    @Test
    fun `a UTF-8 BOM round-trips byte-exactly without special casing`() {
        // EF BB BF decodes to a leading U+FEFF char and re-encodes to the
        // same three bytes — the editor never silently strips or adds a BOM.
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "header\n".toByteArray(Charsets.UTF_8)
        assertTrue(bytes.contentEquals(TextDocument.encode(decode(bytes))))
    }

    private fun decode(bytes: ByteArray): String =
        TextDocument.decideOpen("f.txt", ReadResult.Ok(bytes)).let {
            (it as OpenDecision.Text).content
        }

    // -------------------------------------------------------------- NUL sniff

    @Test
    fun `nul byte detection`() {
        assertTrue(TextDocument.hasNulByte(byteArrayOf(0x61, 0x00, 0x62)))
        assertFalse(TextDocument.hasNulByte("no nul here".toByteArray(Charsets.UTF_8)))
        assertFalse(TextDocument.hasNulByte(ByteArray(0)))
    }

    // --------------------------------------------------------- strict UTF-8

    @Test
    fun `valid utf-8 multibyte is accepted`() {
        assertTrue(TextDocument.isValidUtf8("café 中文 🐧".toByteArray(Charsets.UTF_8)))
        assertTrue(TextDocument.isValidUtf8(ByteArray(0)))
    }

    @Test
    fun `malformed utf-8 is refused`() {
        // 0xC3 alone (truncated two-byte sequence) followed by ASCII.
        assertFalse(TextDocument.isValidUtf8(byteArrayOf(0xC3.toByte(), 0x28)))
        // Truncated three-byte sequence.
        assertFalse(TextDocument.isValidUtf8(byteArrayOf(0xE2.toByte(), 0x82.toByte())))
        // Lone continuation byte.
        assertFalse(TextDocument.isValidUtf8(byteArrayOf(0x80.toByte())))
        // Overlong encoding of NUL (0xC0 0x80) — refused, not smuggled in.
        assertFalse(TextDocument.isValidUtf8(byteArrayOf(0xC0.toByte(), 0x80.toByte())))
    }

    // ------------------------------------------------------------ decideOpen

    @Test
    fun `decideOpen maps a failed read to Failed with the verbatim reason`() {
        val decision = TextDocument.decideOpen("f.txt", ReadResult.Error("gone"))
        assertEquals(OpenDecision.Failed("gone"), decision)
    }

    @Test
    fun `decideOpen maps TooLarge with the real size`() {
        val decision = TextDocument.decideOpen("f.txt", ReadResult.TooLarge(4_000_000L))
        assertEquals(OpenDecision.TooLarge(4_000_000L), decision)
    }

    @Test
    fun `decideOpen refuses NUL content as Binary before decoding`() {
        val decision = TextDocument.decideOpen(
            "f.bin",
            ReadResult.Ok(byteArrayOf(0x50, 0x4B, 0x00, 0x03)),
        )
        assertEquals(OpenDecision.Binary, decision)
    }

    @Test
    fun `decideOpen refuses malformed utf-8 as NotUtf8`() {
        val decision = TextDocument.decideOpen(
            "f.txt",
            ReadResult.Ok(byteArrayOf(0xC3.toByte(), 0x28)),
        )
        assertEquals(OpenDecision.NotUtf8, decision)
    }

    @Test
    fun `decideOpen keeps CRLF content verbatim`() {
        val decision = TextDocument.decideOpen(
            "f.txt",
            ReadResult.Ok("a\r\nb\rc\n".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(OpenDecision.Text("a\r\nb\rc\n"), decision)
    }

    @Test
    fun `an empty file opens as empty text`() {
        assertEquals(OpenDecision.Text(""), TextDocument.decideOpen("f.txt", ReadResult.Ok(ByteArray(0))))
    }

    // -------------------------------------------------------------- save gate

    private fun entry(size: Long, mtime: Long) = FsEntry(
        name = "f.txt",
        kind = EntryKind.FILE,
        sizeBytes = size,
        modifiedAtMillis = mtime,
    )

    @Test
    fun `save gate is Clear when the file matches the snapshot`() {
        assertEquals(
            SaveGate.Clear,
            TextDocument.saveGate(snapshot = entry(12, 100), current = entry(12, 100)),
        )
    }

    @Test
    fun `save gate asks when the size changed`() {
        assertEquals(
            SaveGate.ChangedExternally,
            TextDocument.saveGate(snapshot = entry(12, 100), current = entry(34, 100)),
        )
    }

    @Test
    fun `save gate asks when the mtime changed`() {
        assertEquals(
            SaveGate.ChangedExternally,
            TextDocument.saveGate(snapshot = entry(12, 100), current = entry(12, 200)),
        )
    }

    @Test
    fun `save gate asks when the file vanished (Missing)`() {
        assertEquals(
            SaveGate.Missing,
            TextDocument.saveGate(snapshot = entry(12, 100), current = null),
        )
    }

    @Test
    fun `save gate never allows a blind save without a snapshot`() {
        // Null snapshot (never loaded / stat failed at load) ALWAYS asks.
        assertEquals(
            SaveGate.ChangedExternally,
            TextDocument.saveGate(snapshot = null, current = entry(12, 100)),
        )
        assertEquals(
            SaveGate.Missing,
            TextDocument.saveGate(snapshot = null, current = null),
        )
    }

    // ---------------------------------------------------------------- encode

    @Test
    fun `encode produces UTF-8 bytes`() {
        assertTrue(
            "中文".toByteArray(Charsets.UTF_8).contentEquals(TextDocument.encode("中文")),
        )
    }

    // -------------------------------------------------------------- sizeLabel

    @Test
    fun `size label formats bytes kb and mb`() {
        assertEquals("0 B", TextDocument.sizeLabel(0))
        assertEquals("512 B", TextDocument.sizeLabel(512))
        assertEquals("1.5 KB", TextDocument.sizeLabel(1536))
        assertEquals("2.0 MB", TextDocument.sizeLabel(2L * 1024 * 1024))
        assertEquals(null, TextDocument.sizeLabel(null))
    }
}
