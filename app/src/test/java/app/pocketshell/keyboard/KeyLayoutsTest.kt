package app.pocketshell.keyboard

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 3.1 layout contract tests (docs/PHASE-3.1-DESIGN.md §5). */
class KeyLayoutsTest {

    @Test
    fun `all required symbols are reachable`() {
        val required = "~`!@#\$%^&*()-_+={}[]\\|;:'\",.<>/?".toSet()
        val available = buildSet {
            (KeyLayouts.phonePunctRow + KeyLayouts.phoneSymbolRow1 + KeyLayouts.phoneSymbolRow2 + KeyLayouts.phoneSymbolRow3)
                .forEach { key -> (key.action as? KeyAction.Text)?.let { add(it.c) } }
            // shifted digit-row variants (1 → !, 2 → @, …)
            KeyLayouts.phoneDigitRow.forEach { key ->
                (key.action as KeyAction.Text).shifted?.let { add(it) }
            }
        }
        val missing = required - available
        assertTrue("Missing symbols: $missing", missing.isEmpty())
    }

    @Test
    fun `letters cover a-z on both device classes`() {
        for (rows in listOf(
            listOf(KeyLayouts.phoneRowQ, KeyLayouts.phoneRowA, KeyLayouts.phoneRowZ),
            listOf(KeyLayouts.tabletR2, KeyLayouts.tabletR3, KeyLayouts.tabletR4),
        )) {
            val letters = rows.flatMap { it }
                .mapNotNull { (it.action as? KeyAction.Text)?.c }
                .filter { it in 'a'..'z' }
                .toSet()
            assertEquals(('a'..'z').toSet(), letters)
        }
    }

    @Test
    fun `letter shifted variants are uppercase`() {
        (KeyLayouts.phoneRowQ + KeyLayouts.phoneRowA + KeyLayouts.phoneRowZ).forEach { key ->
            val action = key.action as? KeyAction.Text ?: return@forEach
            if (action.c in 'a'..'z') {
                assertEquals(action.c.uppercaseChar(), action.shifted)
            }
        }
    }

    @Test
    fun `number row long-press arms F1 to F10`() {
        KeyLayouts.phoneDigitRow.forEachIndexed { index, key ->
            assertEquals(KeyEvent.KEYCODE_F1 + index, (key.longPress as KeyAction.Code).keyCode)
            assertEquals("F${index + 1}", key.popupLabel)
        }
    }

    @Test
    fun `tablet - and = long-press arm F11 and F12`() {
        val dash = KeyLayouts.tabletR1.first { (it.action as? KeyAction.Text)?.c == '-' }
        val eq = KeyLayouts.tabletR1.first { (it.action as? KeyAction.Text)?.c == '=' }
        assertEquals(KeyEvent.KEYCODE_F11, (dash.longPress as KeyAction.Code).keyCode)
        assertEquals(KeyEvent.KEYCODE_F12, (eq.longPress as KeyAction.Code).keyCode)
    }

    @Test
    fun `no dedicated Fn key and no Fn modifier remains`() {
        assertEquals(
            listOf(ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.SHIFT),
            KeyLayouts.modifierSlots.map { it.key },
        )
        // No row carries an "FN" label either.
        val allRows = KeyLayouts.topRowSpec + KeyLayouts.phoneDigitRow + KeyLayouts.phoneRowQ +
            KeyLayouts.phoneRowA + KeyLayouts.phoneRowZ + KeyLayouts.phonePunctRow +
            KeyLayouts.phoneSymbolRow1 + KeyLayouts.phoneSymbolRow2 + KeyLayouts.phoneSymbolRow3 +
            KeyLayouts.phoneSymbolBottomRow + KeyLayouts.tabletR1 + KeyLayouts.tabletR2 +
            KeyLayouts.tabletR3 + KeyLayouts.tabletR4
        assertTrue(allRows.none { it.label.equals("FN", ignoreCase = true) })
    }

    @Test
    fun `top row is exactly Esc Tab arrows with repeatable arrows`() {
        val actions = KeyLayouts.topRowSpec.map { it.action as KeyAction.Code }
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_ESCAPE,
                KeyEvent.KEYCODE_TAB,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT,
            ),
            actions.map { it.keyCode },
        )
        assertTrue(actions.drop(2).all { it.repeatable })
    }

    @Test
    fun `extended navigation keys stay reachable on the symbol page`() {
        val codes = (KeyLayouts.phoneSymbolRow3 + KeyLayouts.phoneSymbolBottomRow)
            .mapNotNull { (it.action as? KeyAction.Code)?.keyCode }
        listOf(
            KeyEvent.KEYCODE_INSERT,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
        ).forEach { expected ->
            assertTrue("missing keycode $expected", expected in codes)
        }
    }

    @Test
    fun `backspace is repeatable on every page and device class`() {
        listOf(
            KeyLayouts.phoneRowZ,
            KeyLayouts.phoneSymbolBottomRow,
            KeyLayouts.tabletR1,
        ).forEach { row ->
            val back = row.first { it.label == "⌫" }
            assertEquals(
                KeyEvent.KEYCODE_DEL,
                (back.action as KeyAction.Code).keyCode,
            )
            assertTrue((back.action as KeyAction.Code).repeatable)
        }
    }
}
