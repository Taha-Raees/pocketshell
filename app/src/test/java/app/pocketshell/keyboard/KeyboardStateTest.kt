package app.pocketshell.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Modifier state machine tests (brief §10):
 * tap = one-shot, second tap = lock, third tap = unlock; one-shots are
 * consumable exactly once; locked state persists; clearAll resets everything.
 */
class KeyboardStateTest {

    private lateinit var state: KeyboardState

    @Before
    fun setUp() {
        state = KeyboardState()
    }

    @Test
    fun `initial state is all off`() {
        ModifierKey.entries.forEach { key ->
            assertEquals(ModifierState.OFF, state.modifiers.value[key] ?: ModifierState.OFF)
        }
        assertFalse(state.anySticky())
    }

    @Test
    fun `tap activates one-shot`() {
        state.tap(ModifierKey.CTRL)
        assertEquals(ModifierState.ONE_SHOT, state.modifiers.value[ModifierKey.CTRL])
        assertTrue(state.readControlKey())
    }

    @Test
    fun `second tap locks`() {
        state.tap(ModifierKey.SHIFT)
        state.tap(ModifierKey.SHIFT)
        assertEquals(ModifierState.LOCKED, state.modifiers.value[ModifierKey.SHIFT])
        assertTrue(state.readShiftKey())
    }

    @Test
    fun `third tap unlocks`() {
        state.tap(ModifierKey.ALT)
        state.tap(ModifierKey.ALT)
        state.tap(ModifierKey.ALT)
        assertEquals(ModifierState.OFF, state.modifiers.value[ModifierKey.ALT])
        assertFalse(state.readAltKey())
    }

    @Test
    fun `clearOneShots consumes one-shot but keeps locked`() {
        state.tap(ModifierKey.CTRL)
        state.tap(ModifierKey.ALT)
        state.tap(ModifierKey.ALT)

        state.clearOneShots()

        assertEquals(ModifierState.OFF, state.modifiers.value[ModifierKey.CTRL])
        assertEquals(ModifierState.LOCKED, state.modifiers.value[ModifierKey.ALT])
    }

    @Test
    fun `clearAll resets locked too`() {
        state.tap(ModifierKey.FN)
        state.tap(ModifierKey.FN)
        state.clearAll()
        assertFalse(state.anySticky())
        assertFalse(state.fnActive)
    }

    @Test
    fun `modifiers combine independently`() {
        state.tap(ModifierKey.CTRL)
        state.tap(ModifierKey.SHIFT)
        assertTrue(state.readControlKey())
        assertTrue(state.readShiftKey())
        assertFalse(state.readAltKey())
        assertFalse(state.readFnKey())
    }

    @Test
    fun `read hooks never mutate state`() {
        state.tap(ModifierKey.CTRL)
        repeat(5) { state.readControlKey() }
        assertEquals(ModifierState.ONE_SHOT, state.modifiers.value[ModifierKey.CTRL])
    }
}
