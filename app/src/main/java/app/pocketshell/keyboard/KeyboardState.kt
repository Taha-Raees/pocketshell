package app.pocketshell.keyboard

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Modifier states for the PocketShell keyboard (docs/PHASE-3.1-DESIGN.md §5.3).
 *
 * OFF      — modifier inactive.
 * ONE_SHOT — applies to the next dispatched key only, then auto-clears.
 * LOCKED   — persists until the modifier key is tapped again; always visible.
 */
enum class ModifierState { OFF, ONE_SHOT, LOCKED }

/** Phase 3.1: the dedicated FN modifier was removed — F-keys live on the number-row long-press layer. */
enum class ModifierKey { CTRL, ALT, SHIFT }

/**
 * Single source of truth for keyboard modifier state (docs/ARCHITECTURE.md §4).
 *
 * Consumption rules — deliberately asymmetric (see docs/ARCHITECTURE.md §4):
 *  - [readControlKey] / [readAltKey] / [readShiftKey] are *peeks*:
 *    the vendored TerminalView calls them both for key processing AND for touch
 *    gestures (e.g. shift-extend selection), so they must never mutate state.
 *  - One-shot consumption happens exactly once per dispatched key, from the
 *    dispatch layer ([TerminalKeyDispatcher]), after the key event has been
 *    handed to the TerminalView.
 */
class KeyboardState {

    private val _modifiers = MutableStateFlow(mapOf<ModifierKey, ModifierState>())
    val modifiers: StateFlow<Map<ModifierKey, ModifierState>> = _modifiers.asStateFlow()

    /** Tap cycles OFF → ONE_SHOT → LOCKED → OFF. */
    fun tap(key: ModifierKey) {
        _modifiers.update { current ->
            val next = when (current[key] ?: ModifierState.OFF) {
                ModifierState.OFF -> ModifierState.ONE_SHOT
                ModifierState.ONE_SHOT -> ModifierState.LOCKED
                ModifierState.LOCKED -> ModifierState.OFF
            }
            current + (key to next)
        }
    }

    /** Clear every ONE_SHOT modifier; LOCKED modifiers persist. */
    fun clearOneShots() {
        _modifiers.update { current ->
            current.mapValues { (_, state) -> if (state == ModifierState.ONE_SHOT) ModifierState.OFF else state }
        }
    }

    /** Clear everything (used on session/tab switch so state never leaks). */
    fun clearAll() {
        _modifiers.update { current ->
            current.mapValues { (_, _) -> ModifierState.OFF }
        }
    }

    fun isActive(key: ModifierKey): Boolean =
        (_modifiers.value[key] ?: ModifierState.OFF) != ModifierState.OFF

    val shiftActive: Boolean get() = isActive(ModifierKey.SHIFT)

    // TerminalViewClient hooks — peek only, never consume (see class kdoc).
    fun readControlKey(): Boolean = isActive(ModifierKey.CTRL)
    fun readAltKey(): Boolean = isActive(ModifierKey.ALT)
    fun readShiftKey(): Boolean = isActive(ModifierKey.SHIFT)

    fun anySticky(): Boolean = _modifiers.value.values.any { it != ModifierState.OFF }
}
