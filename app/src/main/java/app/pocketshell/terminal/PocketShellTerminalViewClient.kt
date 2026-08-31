package app.pocketshell.terminal

import android.view.KeyEvent
import android.view.MotionEvent
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalViewClient
import app.pocketshell.keyboard.KeyboardState

/**
 * Bridges the vendored TerminalView to PocketShell (docs/ARCHITECTURE.md §4).
 *
 * The modifier hooks are the single integration point for the PocketShell
 * keyboard: TerminalView consults them while encoding keys (upstream
 * KeyHandler) and during gestures. They peek at [KeyboardState] without ever
 * mutating it (one-shot consumption happens in TerminalKeyDispatcher).
 */
class PocketShellTerminalViewClient(
    private val keyboardState: KeyboardState,
    private val onSingleTap: () -> Unit = {},
    private val onScaleGesture: (Float) -> Float = { it },
    /** Invoked when the emulator becomes available (after attachSession →
     *  updateSize). Upstream documents this as the sanctioned moment for the
     *  host to start the cursor blinker for the first session. */
    private val onEmulatorReady: () -> Unit = {},
) : TerminalViewClient {

    // ---- input hooks (peek only) ----------------------------------------------

    override fun readControlKey(): Boolean = keyboardState.readControlKey()
    override fun readAltKey(): Boolean = keyboardState.readAltKey()
    override fun readShiftKey(): Boolean = keyboardState.readShiftKey()
    override fun readFnKey(): Boolean = keyboardState.readFnKey()

    /**
     * Unicode input arriving via the IME/InputConnection path. Our keyboard
     * dispatches KeyEvents instead, but hardware/external input lands here —
     * implement upstream-compatible CTRL transliteration honestly.
     */
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        var cp = codePoint
        if (ctrlDown) {
            cp = when {
                cp in 'a'.code..'z'.code -> cp - 96 // a-z → 1..26
                cp in 'A'.code..'Z'.code -> cp - 64 // A-Z → 1..26
                cp == ' '.code || cp == '@'.code || cp == '^'.code -> 0
                else -> cp
            }
        }
        session.writeCodePoint(false, cp)
        return true
    }

    // ---- gestures / view behaviour ---------------------------------------------

    override fun onScale(scale: Float): Float = onScaleGesture(scale)

    override fun onSingleTapUp(e: MotionEvent) = onSingleTap()

    override fun onLongPress(event: MotionEvent): Boolean = false // upstream starts text selection

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onEmulatorSet() = onEmulatorReady()

    // ---- configuration ------------------------------------------------------------

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false // setting in M1.3

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true

    override fun isTerminalViewSelected(): Boolean = true

    // ---- logging ---------------------------------------------------------------------

    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { android.util.Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { android.util.Log.v(tag, message) }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        android.util.Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        android.util.Log.e(tag, "stack trace", e)
    }
}
