package app.pocketshell.gui

import android.view.KeyEvent
import java.io.File

/**
 * Pure contract between the app-side compositor and the guest-side client
 * (P3). Everything here is JVM-static so the socket placement, guest env and
 * key mapping are unit-testable without Android.
 *
 * Topology (see docs/M8-P2-GUI-RUNTIME.md §3):
 *
 *     guest client (proot, app UID)
 *         │  XDG_RUNTIME_DIR=/run/gui  WAYLAND_DISPLAY=wayland-0
 *         ▼
 *     <rootfs>/run/gui/wayland-0  (plain unix socket file, app-UID created)
 *         ▼
 *     mc compositor (Android side, in-process, same UID)
 */
object GuiGuestContract {

    const val WAYLAND_DISPLAY = "wayland-0"
    const val GUEST_XDG_RUNTIME_DIR = "/run/gui"

    /** Guest-side relative dir (under the rootfs) owning the socket. */
    const val GUEST_GUI_DIR = "$GUEST_XDG_RUNTIME_DIR"

    /** Deterministic v1 test client shipped as libguiclient.so (static bionic). */
    const val GUEST_CLIENT = "simple-client"

    /** Host-side socket path: INSIDE the guest rootfs so the guest namespace
     *  sees the very same file at /run/gui — no proot bind changes needed. */
    fun socketPath(rootfsDir: File): File =
        File(rootfsDir, "run/gui/$WAYLAND_DISPLAY")

    fun guiDir(rootfsDir: File): File = File(rootfsDir, "run/gui")

    /** The guest command that runs [program] wired to the compositor.
     *  Env rides the shell command (one -c string) — the launcher contract
     *  stays byte-identical to M6. */
    fun guestCommand(program: String, extraEnv: Map<String, String> = emptyMap()): List<String> {
        val env = buildString {
            append("WAYLAND_DISPLAY=$WAYLAND_DISPLAY ")
            append("XDG_RUNTIME_DIR=$GUEST_XDG_RUNTIME_DIR ")
            extraEnv.forEach { (k, v) -> append("$k=$v ") }
            append("exec ")
            append(program)
        }
        return listOf("/bin/sh", "-l", "-c", env)
    }

    /** Android KeyEvent.keyCode → Linux evdev code (deck/hardware fallback
     *  when KeyEvent.getScanCode() is 0). Covers the terminal deck surface:
     *  letters, digits, control keys and modifiers. */
    // Android KEYCODE_A..Z are alphabetical; evdev follows physical QWERTY —
    // an explicit table (a=30 … z=44 with the QWERTY gaps) is the only
    // correct mapping.
    private val LETTER_TO_EVDEV = mapOf(
        KeyEvent.KEYCODE_A to 30, KeyEvent.KEYCODE_B to 48,
        KeyEvent.KEYCODE_C to 46, KeyEvent.KEYCODE_D to 32,
        KeyEvent.KEYCODE_E to 18, KeyEvent.KEYCODE_F to 33,
        KeyEvent.KEYCODE_G to 34, KeyEvent.KEYCODE_H to 35,
        KeyEvent.KEYCODE_I to 23, KeyEvent.KEYCODE_J to 36,
        KeyEvent.KEYCODE_K to 37, KeyEvent.KEYCODE_L to 38,
        KeyEvent.KEYCODE_M to 50, KeyEvent.KEYCODE_N to 49,
        KeyEvent.KEYCODE_O to 24, KeyEvent.KEYCODE_P to 25,
        KeyEvent.KEYCODE_Q to 16, KeyEvent.KEYCODE_R to 19,
        KeyEvent.KEYCODE_S to 31, KeyEvent.KEYCODE_T to 20,
        KeyEvent.KEYCODE_U to 22, KeyEvent.KEYCODE_V to 47,
        KeyEvent.KEYCODE_W to 17, KeyEvent.KEYCODE_X to 45,
        KeyEvent.KEYCODE_Y to 21, KeyEvent.KEYCODE_Z to 44,
    )

    fun keyCodeToEvdev(keyCode: Int): Int? {
        LETTER_TO_EVDEV[keyCode]?.let { return it }
        return when (keyCode) {
        KeyEvent.KEYCODE_0 -> 11
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> 2 + (keyCode - KeyEvent.KEYCODE_1)
        KeyEvent.KEYCODE_ENTER -> 28
        KeyEvent.KEYCODE_NUMPAD_ENTER -> 96
        KeyEvent.KEYCODE_DEL -> 14            // backspace
        KeyEvent.KEYCODE_FORWARD_DEL -> 111   // delete
        KeyEvent.KEYCODE_TAB -> 15
        KeyEvent.KEYCODE_ESCAPE -> 1
        KeyEvent.KEYCODE_SPACE -> 57
        KeyEvent.KEYCODE_DPAD_LEFT -> 105
        KeyEvent.KEYCODE_DPAD_RIGHT -> 106
        KeyEvent.KEYCODE_DPAD_UP -> 103
        KeyEvent.KEYCODE_DPAD_DOWN -> 108
        KeyEvent.KEYCODE_SHIFT_LEFT -> 42
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 54
        KeyEvent.KEYCODE_CTRL_LEFT -> 29
        KeyEvent.KEYCODE_CTRL_RIGHT -> 97
        KeyEvent.KEYCODE_ALT_LEFT -> 56
        KeyEvent.KEYCODE_ALT_RIGHT -> 100
        KeyEvent.KEYCODE_COMMA -> 51
        KeyEvent.KEYCODE_PERIOD -> 52
        KeyEvent.KEYCODE_SLASH -> 53
        KeyEvent.KEYCODE_SEMICOLON -> 39
        KeyEvent.KEYCODE_APOSTROPHE -> 40
        KeyEvent.KEYCODE_LEFT_BRACKET -> 26
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 27
        KeyEvent.KEYCODE_MINUS -> 12
        KeyEvent.KEYCODE_EQUALS -> 13
        KeyEvent.KEYCODE_GRAVE -> 41
        KeyEvent.KEYCODE_BACKSLASH -> 43
        KeyEvent.KEYCODE_PAGE_UP -> 104
        KeyEvent.KEYCODE_PAGE_DOWN -> 109
        KeyEvent.KEYCODE_F1 -> 59
        KeyEvent.KEYCODE_F2 -> 60
        KeyEvent.KEYCODE_F3 -> 61
        KeyEvent.KEYCODE_F4 -> 62
        KeyEvent.KEYCODE_F5 -> 63
        KeyEvent.KEYCODE_F6 -> 64
        KeyEvent.KEYCODE_F7 -> 65
        KeyEvent.KEYCODE_F8 -> 66
        KeyEvent.KEYCODE_F9 -> 67
        KeyEvent.KEYCODE_F10 -> 68
        KeyEvent.KEYCODE_F11 -> 87
        KeyEvent.KEYCODE_F12 -> 88
        else -> null
        }
    }
}
