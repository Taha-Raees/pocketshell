package app.pocketshell.keyboard

import android.view.KeyEvent

/** What a key does when pressed (key model). */
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
    /** Optional alternate action on long-press (e.g. arrows → HOME/END/PGUP/PGDN). */
    val longPress: KeyAction? = null,
)

/** Modifier keys are state keys rendered by the keyboard; never dispatched as keycodes. */
data class ModifierSlot(val key: ModifierKey, val label: String, val weight: Float = 1f)

/**
 * Accessory-row layout definitions — the FINAL keyboard specification
 * (docs/UI-REDESIGN.md §7):
 *
 *   top row:    Esc · Tab · (spring) · ← ↑ ↓ →
 *   [ Android IME — toggled by the keyboard icon in the bottom row ]
 *   bottom row: [⌨] · Ctrl · Alt · Space · Shift · ↵
 *
 * No dedicated Fn key (removed): arrows long-press to HOME/END/PGUP/PGDN and
 * Esc long-press opens the F1–F12 strip. No digits/symbols in the accessory
 * bar — the Android keyboard provides them (its own long-press behavior).
 */
object KeyLayouts {

    /** Sentinel keycode for the keyboard-visibility toggle (handled by the UI, never dispatched). */
    const val KEYCODE_KEYBOARD_TOGGLE = Int.MIN_VALUE

    /** Top row, left cluster: Esc + Tab. Esc long-press opens the F-key strip. */
    val topRowLeading: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE), "Esc"),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_TAB), "Tab", weight = 1.2f),
    )

    /** Top row, right cluster: the arrow keys, repeatable, long-press to nav keys. */
    val topRowArrows: List<KeyboardKey> = listOf(
        KeyboardKey(
            KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT, repeatable = true), "←",
            longPress = KeyAction.Code(KeyEvent.KEYCODE_MOVE_HOME, repeatable = true),
        ),
        KeyboardKey(
            KeyAction.Code(KeyEvent.KEYCODE_DPAD_UP, repeatable = true), "↑",
            longPress = KeyAction.Code(KeyEvent.KEYCODE_PAGE_UP, repeatable = true),
        ),
        KeyboardKey(
            KeyAction.Code(KeyEvent.KEYCODE_DPAD_DOWN, repeatable = true), "↓",
            longPress = KeyAction.Code(KeyEvent.KEYCODE_PAGE_DOWN, repeatable = true),
        ),
        KeyboardKey(
            KeyAction.Code(KeyEvent.KEYCODE_DPAD_RIGHT, repeatable = true), "→",
            longPress = KeyAction.Code(KeyEvent.KEYCODE_MOVE_END, repeatable = true),
        ),
    )

    /** Bottom row, non-modifier keys: Space (flex) + Enter. */
    val bottomRowKeys: List<KeyboardKey> = listOf(
        KeyboardKey(KeyAction.Text(' '), "space", weight = 3f),
        KeyboardKey(KeyAction.Code(KeyEvent.KEYCODE_ENTER), "↵", weight = 1.2f),
    )

    /** Modifier slots — CTRL / ALT / SHIFT only. The dedicated FN key is removed. */
    val modifierSlots: List<ModifierSlot> = listOf(
        ModifierSlot(ModifierKey.CTRL, "Ctrl"),
        ModifierSlot(ModifierKey.ALT, "Alt"),
        ModifierSlot(ModifierKey.SHIFT, "Shift", weight = 1.2f),
    )

    /** F1–F12 strip (opened by Esc long-press; nothing permanently on the bar). */
    val functionKeys: List<KeyboardKey> = (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).map { code ->
        val n = code - KeyEvent.KEYCODE_F1 + 1
        KeyboardKey(KeyAction.Code(code), "F$n")
    }

    /** Long-press meaning of a key, when one exists (arrows → nav keys). */
    fun longPressFor(action: KeyAction): KeyAction? = topRowArrows
        .plus(topRowLeading)
        .plus(bottomRowKeys)
        .firstOrNull { it.action == action }
        ?.longPress
}
