package app.pocketshell.gui

import android.view.KeyEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3 deterministic contract tests: guest socket placement, guest env/command
 * shape, and Android→evdev key mapping. Pure JVM — the constants used are
 * compile-time inlined; no Android runtime is involved.
 */
class GuiGuestContractTest {

    private val rootfs = File("/data/user/0/app.pocketshell/files/runtime/rootfs")

    @Test
    fun `socket lives inside the guest namespace at run-gui`() {
        val socket = GuiGuestContract.socketPath(rootfs)
        assertEquals(
            "/data/user/0/app.pocketshell/files/runtime/rootfs/run/gui/wayland-0",
            socket.absolutePath,
        )
        // the guest-relative spelling of the same file
        assertTrue(
            socket.absolutePath.endsWith("${GuiGuestContract.GUEST_GUI_DIR}/${GuiGuestContract.WAYLAND_DISPLAY}"),
        )
    }

    @Test
    fun `guest command wires env and execs through one sh -c`() {
        val cmd = GuiGuestContract.guestCommand("/run/gui/simple-client")
        assertEquals(listOf("/bin/sh", "-l", "-c"), cmd.take(3))
        val inner = cmd[3]
        assertTrue(inner.contains("WAYLAND_DISPLAY=wayland-0"))
        assertTrue(inner.contains("XDG_RUNTIME_DIR=/run/gui"))
        assertTrue(inner.startsWith("WAYLAND_DISPLAY="))
        assertTrue(inner.contains("exec /run/gui/simple-client"))
    }

    @Test
    fun `guest command carries extra env after the base env`() {
        val cmd = GuiGuestContract.guestCommand("/usr/bin/electron", mapOf("ELECTRON_ARGS" to "--x"))
        val inner = cmd[3]
        assertTrue(inner.contains("ELECTRON_ARGS=--x "))
        assertTrue(inner.indexOf("XDG_RUNTIME_DIR") < inner.indexOf("ELECTRON_ARGS"))
    }

    @Test
    fun `letters map to their evdev codes`() {
        assertEquals(30, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_A))
        assertEquals(44, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_Z))
        assertEquals(2, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_1))
        assertEquals(11, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_0))
    }

    @Test
    fun `control keys map to their evdev codes`() {
        assertEquals(28, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_ENTER))
        assertEquals(14, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_DEL))
        assertEquals(15, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_TAB))
        assertEquals(1, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(57, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_SPACE))
        assertEquals(105, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(29, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(42, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_SHIFT_LEFT))
    }

    @Test
    fun `unknown keys report unhandled`() {
        assertEquals(null, GuiGuestContract.keyCodeToEvdev(KeyEvent.KEYCODE_MUTE))
        assertEquals(null, GuiGuestContract.keyCodeToEvdev(-1))
    }

    @Test
    fun `client binary name matches the packaged asset`() {
        assertEquals("simple-client", GuiGuestContract.GUEST_CLIENT)
    }
}
