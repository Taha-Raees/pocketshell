package app.pocketshell.keyboard

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Terminal Input Dispatcher (brief §9).
 *
 * Route: PocketShell keyboard → this dispatcher → vendored TerminalView
 * dispatch pipeline (upstream KeyHandler encoding) → PTY → shell.
 *
 * One input pipeline for everything: character keys are converted to real
 * KeyEvents via the VIRTUAL_KEYBOARD KeyCharacterMap; special keys are
 * synthesized as keycode events. Modifier state is consumed exactly once per
 * press, after the event has been handed downstream (see KeyboardState kdoc).
 */
class TerminalKeyDispatcher(
    private val keyboardState: KeyboardState,
    /** Usually TerminalView::dispatchKeyEvent. */
    private val dispatchEvent: (KeyEvent) -> Unit,
) {

    /** Dispatch one key press from the keyboard. */
    fun press(action: KeyAction) {
        val effective = if (keyboardState.fnActive) KeyLayouts.fnRemap(action) else action

        when (effective) {
            is KeyAction.Text -> {
                val c = resolveChar(effective)
                dispatchChar(c)
            }

            is KeyAction.Code -> {
                dispatchCode(effective.keyCode, repeatCount = 0)
            }
        }

        keyboardState.clearOneShots()
    }

    /** Dispatch a held-key repeat (repeat count carried on the DOWN event). */
    fun repeat(action: KeyAction, repeatCount: Int) {
        val effective = if (keyboardState.fnActive) KeyLayouts.fnRemap(action) else action
        when (effective) {
            is KeyAction.Text -> dispatchChar(resolveChar(effective))
            is KeyAction.Code -> dispatchCode(effective.keyCode, repeatCount)
        }
        keyboardState.clearOneShots()
    }

    private fun resolveChar(action: KeyAction.Text): Char =
        if (keyboardState.shiftActive) action.shifted ?: action.c.uppercaseChar() else action.c

    private val virtualKeyMap: KeyCharacterMap by lazy {
        // load() is the SDK-stub factory (getInstance is not in public stubs).
        KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    }

    private fun dispatchChar(c: Char) {
        virtualKeyMap.getEvents(charArrayOf(c))?.forEach(dispatchEvent)
    }

    private fun dispatchCode(keyCode: Int, repeatCount: Int) {
        val now = SystemClock.uptimeMillis()
        dispatchEvent(keyEvent(now, KeyEvent.ACTION_DOWN, keyCode, repeatCount))
        dispatchEvent(keyEvent(now, KeyEvent.ACTION_UP, keyCode, repeatCount))
    }

    private fun keyEvent(now: Long, action: Int, keyCode: Int, repeat: Int): KeyEvent =
        KeyEvent(
            now, now, action, keyCode, repeat, 0,
            KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
        )
}
