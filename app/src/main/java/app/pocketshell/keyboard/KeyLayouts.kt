package app.pocketshell.keyboard

import android.view.KeyEvent

/** What a key does when pressed (brief §8 key model). */
sealed interface KeyAction {

    /**
     * A printable character with an optional shifted variant.
     * (Named Text, not Char, to avoid resolution ambiguity with kotlin.Char.)
     */
    data class Text(val c: Char, val shifted: Char? = null) : KeyAction

    /**
     * A hardware keycode to dispatch through the vendored TerminalView pipeline,
     * which encodes it via upstream KeyHandler (correct terminal sequences).
     */
    data class Code(val keyCode: Int, val repeatable: Boolean = false) : KeyAction
}

/** A single key definition shown on the keyboard. */
data class KeyboardKey(
    val action: KeyAction,
    val label: String,
    val shiftedLabel: String? = null,
    val weight: Float = 1f,
)

/** Modifier keys are state keys rendered by the keyboard; never dispatched as keycodes. */
data class ModifierSlot(val key: ModifierKey, val label: String, val weight: Float = 1f)

enum class KeyboardPage { ALPHA, SYMBOL }

/**
 * Keyboard layout definitions (brief §8/§11).
 *
 * Modifiers (CTRL/ALT/FN/SHIFT) are NOT part of the row data — the composable
 * inserts [ModifierSlot]s into the control row so their visual state comes from
 * [KeyboardState]. FN-layer remapping ([fnRemap]) is applied by the dispatcher.
 */
object KeyLayouts {

    // ---- shared: control rows (non-modifier keys only) -----------------------

    /** Sentinel keycode for the ALPHA/SYMBOL page toggle (handled by the UI, never dispatched). */
    const val KEYCODE_PAGE_TOGGLE = Int.MIN_VALUE

    // ---- Phone: ALPHA page ---------------------------------------------------

    val phoneDigitRow: List<KeyboardKey> = "1234567890".map { ch ->
        val shifted = digitShift(ch)
        KeyboardKey(KeyAction.Text(ch, shifted), ch.toString(), shifted?.toString())
    }

    val phoneRowQ: List<KeyboardKey> = "qwertyuiop".map { letterKey(it) }
    val phoneRowA: List<KeyboardKey> = "asdfghjkl".map { letterKey(it) }
    val phoneRowZ: List<KeyboardKey> = listOf(
        *("zxcvbnm".map { letterKey(it) }).toTypedArray(),
        KeyboardKey(KeyAction.Text('-', '_'), "-", "_"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DEL, repeatable = true), "⌫", weight = 1.4f),
    )

    /** Phone bottom row: page toggle, punctuation, arrow cluster, space, enter. */
    val phoneBottomRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KEYCODE_PAGE_TOGGLE), "?123", weight = 1.3f),
        KeyboardKey(KeyAction.Text(','), ",", weight = 0.8f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true), "←", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_UP, repeatable = true), "↑", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true), "↓", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true), "→", weight = 1f),
        KeyboardKey(KeyAction.Text(' '), "space", weight = 3f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ENTER), "↵", weight = 1.3f),
    )

    // ---- Phone: SYMBOL page (full §8 symbol coverage in 3 rows) --------------

    val phoneSymbolRow1: List<KeyboardKey> = "!@#$%^&*()".map { ch ->
        KeyboardKey(KeyAction.Text(ch), ch.toString())
    }
    val phoneSymbolRow2: List<KeyboardKey> = "~`[]{}\\|-_".map { ch ->
        KeyboardKey(KeyAction.Text(ch), ch.toString())
    }
    val phoneSymbolRow3: List<KeyboardKey> = ";:'\"<>=+/?".map { ch ->
        KeyboardKey(KeyAction.Text(ch), ch.toString())
    }

    /** Extended terminal keys reachable from the SYMBOL page (phone §8 coverage). */
    val phoneExtendedRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_INSERT), "INS"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL), "DEL"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_HOME), "HOME"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_END), "END"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_UP, repeatable = true), "PGUP"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true), "PGDN"),
    )

    val phoneSymbolBottomRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KEYCODE_PAGE_TOGGLE), "ABC", weight = 1.3f),
        KeyboardKey(KeyAction.Text(','), ",", weight = 0.8f),
        KeyboardKey(KeyAction.Text(' '), "space", weight = 4.2f),
        KeyboardKey(KeyAction.Text('.'), ".", weight = 0.8f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ENTER), "↵", weight = 1.3f),
    )

    // ---- Tablet: full key set exposed directly (brief §11) -------------------

    val tabletTerminalRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_HOME), "HOME"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_END), "END"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_UP, repeatable = true), "PGUP"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true), "PGDN"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_INSERT), "INS"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL), "DEL"),
    )

    val tabletFunctionRow: List<KeyboardKey> = (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).map { code ->
        val n = code - KeyEvent.KEYCODE_F1 + 1
        KeyboardKey(KeyAction.Code(code), "F$n")
    }

    val tabletDigitRow: List<KeyboardKey> = listOf(
        *("1234567890".map { ch -> KeyboardKey(KeyAction.Text(ch, digitShift(ch)), ch.toString(), digitShift(ch)?.toString()) }).toTypedArray(),
        KeyboardKey(KeyAction.Text('-', '_'), "-", "_"),
        KeyboardKey(KeyAction.Text('=', '+'), "=", "+"),
    )

    val tabletRowQ: List<KeyboardKey> = "qwertyuiop".map { letterKey(it) } + listOf(
        KeyboardKey(KeyAction.Text('[', '{'), "[", "{"),
        KeyboardKey(KeyAction.Text(']', '}'), "]", "}"),
        KeyboardKey(KeyAction.Text('\\', '|'), "\\", "|"),
    )

    val tabletRowA: List<KeyboardKey> = "asdfghjkl".map { letterKey(it) } + listOf(
        KeyboardKey(KeyAction.Text(';', ':'), ";", ":"),
        KeyboardKey(KeyAction.Text('\'', '"'), "'", "\""),
    )

    val tabletRowZ: List<KeyboardKey> = "zxcvbnm".map { letterKey(it) } + listOf(
        KeyboardKey(KeyAction.Text(',', '<'), ",", "<"),
        KeyboardKey(KeyAction.Text('.', '>'), ".", ">"),
        KeyboardKey(KeyAction.Text('/', '?'), "/", "?"),
    )

    val tabletBottomRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true), "←", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_UP, repeatable = true), "↑", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true), "↓", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true), "→", weight = 1f),
        KeyboardKey(KeyAction.Text(' '), "space", weight = 5f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DEL, repeatable = true), "⌫", weight = 1.3f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ENTER), "↵", weight = 1.5f),
    )

    // ---- helpers ------------------------------------------------------------

    /** Modifier strip layout for both device classes. */
    val modifierSlots: List<ModifierSlot> = listOf(
        ModifierSlot(ModifierKey.CTRL, "CTRL"),
        ModifierSlot(ModifierKey.ALT, "ALT"),
        ModifierSlot(ModifierKey.FN, "FN"),
        ModifierSlot(ModifierKey.SHIFT, "SHIFT", weight = 1.2f),
    )

    fun letterKey(c: Char): KeyboardKey =
        KeyboardKey(KeyAction.Text(c, c.uppercaseChar()), c.toString(), c.uppercaseChar().toString())

    fun digitShift(digit: Char): Char? = when (digit) {
        '1' -> '!'
        '2' -> '@'
        '3' -> '#'
        '4' -> '$'
        '5' -> '%'
        '6' -> '^'
        '7' -> '&'
        '8' -> '*'
        '9' -> '('
        '0' -> ')'
        else -> null
    }

    /**
     * FN-layer remapping applied by the dispatcher *before* dispatching
     * (brief §8/§11): FN+1..0 → F1..F10, FN+- → F11, FN+= → F12,
     * FN+←/→/↑/↓ → HOME/END/PGUP/PGDN, FN+⌫ → DEL.
     */
    fun fnRemap(action: KeyAction): KeyAction = when (action) {
        is KeyAction.Text -> when (action.c) {
            '1' -> KeyAction.Code(KeyEvent.KEYCODE_F1)
            '2' -> KeyAction.Code(KeyEvent.KEYCODE_F2)
            '3' -> KeyAction.Code(KeyEvent.KEYCODE_F3)
            '4' -> KeyAction.Code(KeyEvent.KEYCODE_F4)
            '5' -> KeyAction.Code(KeyEvent.KEYCODE_F5)
            '6' -> KeyAction.Code(KeyEvent.KEYCODE_F6)
            '7' -> KeyAction.Code(KeyEvent.KEYCODE_F7)
            '8' -> KeyAction.Code(KeyEvent.KEYCODE_F8)
            '9' -> KeyAction.Code(KeyEvent.KEYCODE_F9)
            '0' -> KeyAction.Code(KeyEvent.KEYCODE_F10)
            '-' -> KeyAction.Code(KeyEvent.KEYCODE_F11)
            '=' -> KeyAction.Code(KeyEvent.KEYCODE_F12)
            else -> action
        }
        is KeyAction.Code -> when (action.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> KeyAction.Code(KeyEvent.KEYCODE_MOVE_HOME, action.repeatable)
            KeyEvent.KEYCODE_DPAD_RIGHT -> KeyAction.Code(KeyEvent.KEYCODE_MOVE_END, action.repeatable)
            KeyEvent.KEYCODE_DPAD_UP -> KeyAction.Code(KeyEvent.KEYCODE_PAGE_UP, action.repeatable)
            KeyEvent.KEYCODE_DPAD_DOWN -> KeyAction.Code(KeyEvent.KEYCODE_PAGE_DOWN, action.repeatable)
            KeyEvent.KEYCODE_DEL -> KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL, action.repeatable)
            else -> action
        }
    }
}
