package app.pocketshell.files.editor

import app.pocketshell.files.FsEntry
import app.pocketshell.files.ReadResult
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * M7.0.0 Phase 6 — the quick text editor's PURE decision model.
 *
 * The editor is a QUICK TEXT VIEWER/EDITOR, not a mini IDE, and its honesty
 * rules are byte-level:
 *
 *  - SIZE CAP: files above [MAX_QUICK_EDIT_BYTES] (1 MiB) are refused with
 *    their real size — the whole file must fit in memory as one editable
 *    string, and pretending otherwise would hang the serial worker.
 *  - BINARY SNIFF: any NUL (0x00) byte in the raw content marks the file as
 *    binary — the standard quick-editor heuristic. A binary file is never
 *    decoded, never shown garbled, never rewritten.
 *  - STRICT UTF-8: the decode uses REPORT for malformed input. A lossy
 *    decode would silently replace bytes with U+FFFD and the first save
 *    would corrupt the file — so content that is not valid UTF-8 is refused
 *    honestly instead. Valid UTF-8 round-trips byte-exactly: decoding and
 *    re-encoding a never-edited document reproduces the original bytes
 *    (including a leading BOM, which decodes to U+FEFF and re-encodes to
 *    the same three bytes — no special casing anywhere).
 *  - LINE ENDINGS ARE NEVER NORMALIZED: CRLF, CR, LF and mixed content are
 *    ordinary characters in the buffer and round-trip untouched.
 *
 *  - SAVE GATE (the Phase 1 audit's concurrency answer — risk item 4):
 *    before any save the file is re-statted and compared against the
 *    (size, mtime) snapshot captured at load/last-save. A file that was
 *    changed or deleted OUTSIDE the editor (a running guest shell, another
 *    app, another device writing the SAF provider) can only be overwritten
 *    after the user explicitly confirms it — the editor never wins a race
 *    silently. Everything here is pure and JVM-tested; the ViewModel only
 *    shuttles results between this model and the StorageArea on its serial
 *    worker.
 */
object TextDocument {

    /** The honest size ceiling of the quick editor (1 MiB). */
    const val MAX_QUICK_EDIT_BYTES: Int = 1 shl 20

    // ------------------------------------------------------------ open path

    /**
     * Fold a bounded [readBytes] result into an honest open decision.
     * [name] is used verbatim in refusal messages (never a faked path).
     */
    fun decideOpen(name: String, read: ReadResult): OpenDecision = when (read) {
        is ReadResult.Error -> OpenDecision.Failed(read.reason)
        is ReadResult.TooLarge -> OpenDecision.TooLarge(read.sizeBytes)
        is ReadResult.Ok -> when {
            hasNulByte(read.bytes) -> OpenDecision.Binary
            !isValidUtf8(read.bytes) -> OpenDecision.NotUtf8
            else -> OpenDecision.Text(
                content = String(read.bytes, StandardCharsets.UTF_8),
            )
        }
    }

    /** The standard binary heuristic: a NUL byte means "not text". */
    fun hasNulByte(bytes: ByteArray): Boolean {
        for (b in bytes) if (b == 0.toByte()) return true
        return false
    }

    /**
     * Strict UTF-8 check: true only when the bytes decode with malformed
     * input REPORTED (never replaced). Empty input is valid (empty file).
     */
    fun isValidUtf8(bytes: ByteArray): Boolean = try {
        strictDecoder().decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    /**
     * The inverse of the open path: encode the buffer for
     * [app.pocketshell.files.StorageArea.writeBytesAtomic]. UTF-8 round-trips
     * byte-exactly for valid input, which the open path already guaranteed.
     */
    fun encode(content: String): ByteArray = content.toByteArray(StandardCharsets.UTF_8)

    private fun strictDecoder() = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    // ----------------------------------------------------------- save gate

    /**
     * Compare the live file (fresh [stat], may be null = gone) against the
     * snapshot the editor holds (null before the first load — treated as
     * "changed" so a blind save can never happen without an answer).
     *
     * The comparison uses (sizeBytes, modifiedAtMillis) exactly as the Phase 1
     * audit planned: cheap, available on every area (SAF included), and honest
     * — a same-millisecond same-size write from outside is a race the editor
     * cannot see; the confirm dialog still puts the decision with the user
     * for every change it CAN see.
     */
    fun saveGate(snapshot: FsEntry?, current: FsEntry?): SaveGate = when {
        current == null -> SaveGate.Missing
        snapshot == null -> SaveGate.ChangedExternally
        snapshot.sizeBytes != current.sizeBytes ||
            snapshot.modifiedAtMillis != current.modifiedAtMillis -> SaveGate.ChangedExternally
        else -> SaveGate.Clear
    }

    /**
     * A display fact for the header/status line: the human size of the file
     * being edited ("1.2 KB"), or null when unknown.
     */
    fun sizeLabel(bytes: Long?): String? {
        if (bytes == null || bytes < 0) return null
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}

/** The honest result of trying to open a file in the quick editor. */
sealed interface OpenDecision {
    /** Decoded, validated UTF-8 text — the editor may show it. */
    data class Text(val content: String) : OpenDecision

    /** The file exceeds the quick-editor cap; [sizeBytes] is the real size. */
    data class TooLarge(val sizeBytes: Long) : OpenDecision

    /** A NUL byte was found — treated as binary, never decoded. */
    data object Binary : OpenDecision

    /** The bytes are not valid UTF-8 — refused instead of silently corrupted. */
    data object NotUtf8 : OpenDecision

    /** The read itself failed; [reason] is the area's verbatim message. */
    data class Failed(val reason: String) : OpenDecision
}

/** What a save means after comparing the live file against the snapshot. */
sealed interface SaveGate {
    /** The file is exactly as the editor last saw it — save directly. */
    data object Clear : SaveGate

    /**
     * The file changed (or appeared) outside the editor since it was loaded —
     * saving overwrites those external changes, so the user must confirm.
     */
    data object ChangedExternally : SaveGate

    /** The file no longer exists — saving would recreate it; user confirms. */
    data object Missing : SaveGate
}
