package app.pocketshell.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout coverage tests — the full §8 symbol set must be reachable. */
class KeyLayoutsTest {

    @Test
    fun `all required symbols exist on phone symbol page`() {
        val required = "~`!@#\$%^&*()-_+={}[]\\|;:'\",.<>/?".toSet()
        val available = buildSet {
            (KeyLayouts.phoneSymbolRow1 + KeyLayouts.phoneSymbolRow2 + KeyLayouts.phoneSymbolRow3)
                .forEach { key -> (key.action as KeyAction.Text).let { add(it.c) } }
            add(',') // bottom row
            add('.') // bottom row
            // shifted digit row variants
            KeyLayouts.phoneDigitRow.forEach { key ->
                (key.action as KeyAction.Text).shifted?.let { add(it) }
            }
        }
        val missing = required - available
        assertTrue("Missing symbols: $missing", missing.isEmpty())
    }

    @Test
    fun `letters cover a-z`() {
        val letters = (KeyLayouts.phoneRowQ + KeyLayouts.phoneRowA + KeyLayouts.phoneRowZ)
            .mapNotNull { (it.action as? KeyAction.Text)?.c }
            .filter { it in 'a'..'z' }
            .toSet()
        assertEquals(('a'..'z').toSet(), letters)
    }

    @Test
    fun `letter shifted variants are uppercase`() {
        KeyLayouts.phoneRowQ.forEach { key ->
            val action = key.action as KeyAction.Text
            assertEquals(action.c.uppercaseChar(), action.shifted)
        }
    }

    @Test
    fun `fn remap digits to F-keys`() {
        assertEquals(
            KeyEvent.KEYCODE_F1,
            (KeyLayouts.fnRemap(KeyAction.Text('1')) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_F10,
            (KeyLayouts.fnRemap(KeyAction.Text('0')) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_F11,
            (KeyLayouts.fnRemap(KeyAction.Text('-')) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_F12,
            (KeyLayouts.fnRemap(KeyAction.Text('=')) as KeyAction.Code).keyCode,
        )
    }

    @Test
    fun `fn remap navigation keys`() {
        assertEquals(
            KeyEvent.KEYCODE_MOVE_HOME,
            (KeyLayouts.fnRemap(KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT)) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_MOVE_END,
            (KeyLayouts.fnRemap(KeyAction.Code(KeyEvent.KEYCODE_DPAD_RIGHT)) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_PAGE_UP,
            (KeyLayouts.fnRemap(KeyAction.Code(KeyEvent.KEYCODE_DPAD_UP)) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_PAGE_DOWN,
            (KeyLayouts.fnRemap(KeyAction.Code(KeyEvent.KEYCODE_DPAD_DOWN)) as KeyAction.Code).keyCode,
        )
        assertEquals(
            KeyEvent.KEYCODE_FORWARD_DEL,
            (KeyLayouts.fnRemap(KeyAction.Code(KeyEvent.KEYCODE_DEL)) as KeyAction.Code).keyCode,
        )
    }

    @Test
    fun `tablet exposes F1 to F12 directly`() {
        val codes = KeyLayouts.tabletFunctionRow
            .map { (it.action as KeyAction.Code).keyCode }
        assertEquals((KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).toList(), codes)
    }

    @Test
    fun `extended row covers INS DEL HOME END PGUP PGDN`() {
        val codes = KeyLayouts.phoneExtendedRow.map { (it.action as KeyAction.Code).keyCode }
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_INSERT,
                KeyEvent.KEYCODE_FORWARD_DEL,
                KeyEvent.KEYCODE_MOVE_HOME,
                KeyEvent.KEYCODE_MOVE_END,
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_PAGE_DOWN,
            ),
            codes,
        )
    }
}
