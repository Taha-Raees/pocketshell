package app.pocketshell.keyboard

import android.view.KeyEvent

/** What a key does when pressed (Phase 3.1 key model — docs/PHASE-3.1-DESIGN.md §5). */
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
    /**
     * Held-key action (Phase 3.1: the Fn layer). Long-pressing a key with a
     * [longPress] shows its [popupLabel] bubble after the hold threshold and
     * commits the action on release — this replaces the dedicated Fn key
     * (brief §18) and, because PocketShell owns the keyboard, works
     * reliably with no IME limitations.
     */
    val longPress: KeyAction? = null,
    /** Bubble text shown above the key while its [longPress] is armed ("F1"). */
    val popupLabel: String? = null,
)

/**
 * Modifier keys are state keys rendered by the keyboard; never dispatched as
 * keycodes. Phase 3.1 removed the dedicated FN modifier (brief §18): F-keys
 * live on the number-row long-press layer instead.
 */
data class ModifierSlot(val key: ModifierKey, val label: String, val weight: Float = 1f)

enum class KeyboardPage { ALPHA, SYMBOL }

/**
 * Keyboard layout definitions (Phase 3.1 — docs/PHASE-3.1-DESIGN.md §5.2).
 *
 * The deck is composed in three layers around the QWERTY body:
 *   top accessory row   Esc · Tab · arrow cluster (see [topRowSpec])
 *   QWERTY body         the pages below
 *   bottom accessory    toggle · CTRL · ALT · Space · SHIFT · Enter
 *
 * Modifiers (CTRL/ALT/SHIFT) are NOT part of the row data — the composable
 * inserts [ModifierSlot]s into the bottom row so their visual state comes
 * from [KeyboardState].
 */
object KeyLayouts {

    // ---- shared: page toggle sentinel ----------------------------------------

    /** Sentinel keycode for the ALPHA/SYMBOL page toggle (handled by the UI, never dispatched). */
    const val KEYCODE_PAGE_TOGGLE = Int.MIN_VALUE

    // ---- Phone: ALPHA page (portrait, 5 rows) ---------------------------------

    /**
     * Number row. Long-press arms the Fn layer: hold "3" → "F3" bubble, release
     * sends F3. Digits deliberately do NOT auto-repeat (their hold gesture is
     * the Fn layer); F11/F12 ride on -/= of the TABLET rows (see [tabletR1]).
     */
    val phoneDigitRow: List<KeyboardKey> = "1234567890".mapIndexed { index, ch ->
        val shifted = digitShift(ch)
        KeyboardKey(
            action = KeyAction.Text(ch, shifted),
            label = ch.toString(),
            shiftedLabel = shifted?.toString(),
            longPress = KeyAction.Code(KeyEvent.KEYCODE_F1 + index),
            popupLabel = "F${index + 1}",
        )
    }

    val phoneRowQ: List<KeyboardKey> = "qwertyuiop".map { letterKey(it) }
    val phoneRowA: List<KeyboardKey> = "asdfghjkl".map { letterKey(it) }

    /** Z row with page toggle (left) and backspace (right); Shift lives in the bottom row. */
    val phoneRowZ: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KEYCODE_PAGE_TOGGLE), "?123", weight = 1.3f),
        *"zxcvbnm".map { letterKey(it) }.toTypedArray(),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DEL, repeatable = true), "⌫", weight = 1.3f),
    )

    /**
     * Terminal punctuation row — flags (-/), paths (/ .), redirections and
     * quoting (' "), variables ($) in one terminal-first row.
     */
    val phonePunctRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Text('-', '_'), "-", "_", longPress = KeyAction.Text('_'), popupLabel = "_"),
        KeyboardKey(KeyAction.Text('/'), "/"),
        KeyboardKey(KeyAction.Text(':'), ":"),
        KeyboardKey(KeyAction.Text(';'), ";"),
        KeyboardKey(KeyAction.Text(','), ","),
        KeyboardKey(KeyAction.Text('.'), "."),
        KeyboardKey(KeyAction.Text('$'), "$"),
        KeyboardKey(KeyAction.Text('\''), "'"),
        KeyboardKey(KeyAction.Text('"'), "\""),
        KeyboardKey(KeyAction.Text('@'), "@"),
    )

    // ---- Phone: SYMBOL page (everything the punct row does not carry) ---------

    val phoneSymbolRow1: List<KeyboardKey> = "!@#$%^&*()".map { ch ->
        KeyboardKey(KeyAction.Text(ch), ch.toString())
    }

    val phoneSymbolRow2: List<KeyboardKey> = listOf('~', '`', '{', '}', '[', ']', '\\', '|', '=', '+')
        .map { ch -> KeyboardKey(KeyAction.Text(ch), ch.toString()) }

    /** Wide keys + the extended terminal keys (no regression from v0.6.2). */
    val phoneSymbolRow3: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Text('<'), "<", weight = 1.4f),
        KeyboardKey(KeyAction.Text('>'), ">", weight = 1.4f),
        KeyboardKey(KeyAction.Text('?'), "?", weight = 1.4f),
        KeyboardKey(KeyAction.Text('_'), "_", weight = 1.4f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_INSERT), "INS", weight = 1.4f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL), "DEL", weight = 1.4f),
    )

    val phoneSymbolBottomRow: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KEYCODE_PAGE_TOGGLE), "ABC", weight = 1.4f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_HOME), "HOME", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_MOVE_END), "END", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_UP, repeatable = true), "PGUP", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true), "PGDN", weight = 1f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DEL, repeatable = true), "⌫", weight = 1.1f),
    )

    // ---- Tablet (≥600dp width): 14/15-column rows, same identity --------------

    val tabletR1: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Text('`', '~'), "`", "~"),
        *("1234567890".mapIndexed { index, ch ->
            val shifted = digitShift(ch)
            KeyboardKey(
                action = KeyAction.Text(ch, shifted),
                label = ch.toString(),
                shiftedLabel = shifted?.toString(),
                longPress = KeyAction.Code(KeyEvent.KEYCODE_F1 + index),
                popupLabel = "F${index + 1}",
            )
        }).toTypedArray(),
        KeyboardKey(KeyAction.Text('-', '_'), "-", "_", longPress = KeyAction.Code(KeyEvent.KEYCODE_F11), popupLabel = "F11"),
        KeyboardKey(KeyAction.Text('=', '+'), "=", "+", longPress = KeyAction.Code(KeyEvent.KEYCODE_F12), popupLabel = "F12"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DEL, repeatable = true), "⌫", weight = 1.2f),
    )

    val tabletR2: List<KeyboardKey> = "qwertyuiop".map { letterKey(it) } + listOf(
        KeyboardKey(KeyAction.Text('[', '{'), "[", "{"),
        KeyboardKey(KeyAction.Text(']', '}'), "]", "}"),
        KeyboardKey(KeyAction.Text('\\', '|'), "\\", "|"),
    )

    val tabletR3: List<KeyboardKey> = "asdfghjkl".map { letterKey(it) } + listOf(
        KeyboardKey(KeyAction.Text(';', ':'), ";", ":"),
        KeyboardKey(KeyAction.Text('\'', '"'), "'", "\""),
    )

    val tabletR4: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KEYCODE_PAGE_TOGGLE), "?123", weight = 1.3f),
        *"zxcvbnm".map { letterKey(it) }.toTypedArray(),
        KeyboardKey(KeyAction.Text(',', '<'), ",", "<"),
        KeyboardKey(KeyAction.Text('.', '>'), ".", ">"),
        KeyboardKey(KeyAction.Text('/', '?'), "/", "?"),
    )

    // ---- Top accessory row spec (deck layer, brief §11) ------------------------

    /**
     * Esc + Tab on the left; the arrow cluster is rendered as a grouped panel
     * on the right. Kept as data so tests pin the exact top-row contract.
     */
    val topRowSpec: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE), "Esc"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_TAB), "Tab"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true), "←"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_UP, repeatable = true), "↑"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true), "↓"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true), "→"),
    )

    // ---- Modifiers (bottom accessory row, brief §13 order after Space/Shift) ---

    val modifierSlots: List<ModifierSlot> = listOf(
        ModifierSlot(ModifierKey.CTRL, "Ctrl"),
        ModifierSlot(ModifierKey.ALT, "Alt"),
        ModifierSlot(ModifierKey.SHIFT, "Shift"),
    )

    // ---- helpers ------------------------------------------------------------

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
}
