package app.pocketshell.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accessory-row layout tests — the FINAL keyboard specification
 * (docs/UI-REDESIGN.md §7):
 *   top row:    Esc · Tab · (spring) · ← ↑ ↓ →
 *   bottom row: [⌨] · Ctrl · Alt · Space · Shift · ↵
 * Fn key removed; arrows long-press to nav keys; F1–F12 via the Esc
 * long-press strip. Digits/symbols come from the Android keyboard.
 */
class KeyLayoutsTest {

    @Test
    fun `top row leading is exactly Esc then Tab`() {
        val codes = KeyLayouts.topRowLeading.map {
            (it.action as KeyAction.Code).keyCode
        }
        assertEquals(listOf(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB), codes)
    }

    @Test
    fun `top row arrows are the four arrow keys, repeatable, right-cluster order`() {
        val codes = KeyLayouts.topRowArrows.map {
            (it.action as KeyAction.Code).keyCode
        }
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            ),
            codes,
        )
        KeyLayouts.topRowArrows.forEach {
            assertTrue((it.action as KeyAction.Code).repeatable)
        }
    }

    @Test
    fun `arrow long-press remaps to HOME PGUP PGDN END`() {
        val remaps = KeyLayouts.topRowArrows.map { key ->
            (key.longPress as KeyAction.Code).keyCode
        }
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_MOVE_END,
            ),
            remaps,
        )
    }

    @Test
    fun `bottom row keys are Space then Enter only`() {
        val space = KeyLayouts.bottomRowKeys[0].action as KeyAction.Text
        val enter = KeyLayouts.bottomRowKeys[1].action as KeyAction.Code
        assertEquals(' ', space.c)
        assertEquals(KeyEvent.KEYCODE_ENTER, enter.keyCode)
        assertEquals(2, KeyLayouts.bottomRowKeys.size)
    }

    @Test
    fun `modifier slots are exactly Ctrl Alt Shift - no Fn`() {
        assertEquals(
            listOf(ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.SHIFT),
            KeyLayouts.modifierSlots.map { it.key },
        )
    }

    @Test
    fun `F-key strip covers F1 to F12`() {
        val codes = KeyLayouts.functionKeys.map { (it.action as KeyAction.Code).keyCode }
        assertEquals((KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).toList(), codes)
    }

    @Test
    fun `longPressFor resolves arrows and returns null for plain keys`() {
        val left = KeyLayouts.topRowArrows[0].action
        assertEquals(
            KeyEvent.KEYCODE_MOVE_HOME,
            (KeyLayouts.longPressFor(left) as KeyAction.Code).keyCode,
        )
        assertNull(KeyLayouts.longPressFor(KeyAction.Code(KeyEvent.KEYCODE_ESCAPE)))
        assertNull(KeyLayouts.longPressFor(KeyAction.Text(' ')))
    }

    @Test
    fun `accessory bar carries no printable digit or letter keys`() {
        val allKeys = KeyLayouts.topRowLeading +
            KeyLayouts.topRowArrows +
            KeyLayouts.bottomRowKeys +
            KeyLayouts.functionKeys
        val printable = allKeys
            .mapNotNull { it.action as? KeyAction.Text }
            .map { it.c }
        assertEquals(listOf(' '), printable)
    }
}
