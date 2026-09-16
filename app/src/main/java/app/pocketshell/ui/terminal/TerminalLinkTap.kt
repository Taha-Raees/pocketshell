package app.pocketshell.ui.terminal

import android.view.MotionEvent
import app.pocketshell.terminal.TerminalUrlDetector
import com.termux.terminal.TerminalBuffer
import com.termux.view.TerminalView

/**
 * Maps a tap on the terminal canvas to a URL, if any (owner iteration:
 * "terminal links → Companion"). Reads the emulator buffer only — the
 * emulator, renderer and PTY are never touched, and the gesture flow is
 * untouched (this runs inside the already-confirmed single-tap callback;
 * scroll/fling/long-press take their own recognizer paths).
 */
object TerminalLinkTap {

    /** The URL under the tap, or null (tap keeps its previous meaning). */
    fun urlAt(view: TerminalView, event: MotionEvent): String? {
        val emulator = view.mEmulator ?: return null
        // Full-screen apps (vim, htop) own every tap while they report mouse
        // events — never steal one.
        if (emulator.isMouseTrackingActive()) return null
        return runCatching {
            val cell = view.getColumnAndRow(event, true)
            val column = cell[0]
            val row = cell[1]
            val buffer = emulator.screen
            val columns = emulator.mColumns
            val segments = buildList {
                var r = row
                var text = buffer.getSelectedText(0, r, columns, r)
                    ?: return@runCatching null
                add(text)
                // A wrapped line continues on the next buffer row; pull up to
                // two continuation segments so a boundary-split URL is found
                // whole (the detector matches on the joined text).
                while (buffer.getLineWrap(r) && size < 3) {
                    r += 1
                    text = buffer.getSelectedText(0, r, columns, r) ?: break
                    add(text)
                }
            }
            TerminalUrlDetector.findAt(segments, columns, segment = 0, column = column)
                ?: continuationHit(buffer, columns, row, column)
        }.getOrNull()
    }

    /** Tap on a wrapped continuation row: the URL head sits on the row above. */
    private fun continuationHit(buffer: TerminalBuffer, columns: Int, row: Int, column: Int): String? {
        if (row == 0 || !buffer.getLineWrap(row - 1)) return null
        val head = buffer.getSelectedText(0, row - 1, columns, row - 1) ?: return null
        val tail = buffer.getSelectedText(0, row, columns, row) ?: return null
        return TerminalUrlDetector.findAt(listOf(head, tail), columns, segment = 1, column = column)
    }
}
