package app.pocketshell.gui

import android.view.Surface

/**
 * JNI bridge to libmc — the kiosk Wayland compositor running in-process.
 *
 * Threading: [nativeStart] spawns the compositor's event-loop thread and
 * returns immediately; the loop owns all wayland/EGL state. UI threads only
 * push commands (attach/detach/input/stop) through a pipe queue.
 *
 * All natives live in libmc.so (gui-runtime module).
 */
object McGuiRuntime {
    @Volatile
    private var loaded = false

    /** Idempotent; safe to call before any native entry point. */
    @Synchronized
    fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("mc")
            loaded = true
        }
    }

    /** Starts the event-loop thread. 0 = started, negative = -errno. */
    fun start(socketPath: String, xkbRoot: String, width: Int, height: Int): Int {
        ensureLoaded()
        return nativeStart(socketPath, xkbRoot, width, height)
    }

    /** UI thread: attach (or resize) the display surface. */
    fun attachSurface(surface: Surface, width: Int, height: Int) {
        ensureLoaded()
        nativeAttachSurface(surface, width, height)
    }

    /** UI thread: surface gone (backgrounded/destroyed). */
    fun detachSurface() {
        ensureLoaded()
        nativeDetachSurface()
    }

    /** Blocks until the loop thread exits. Returns its status code. */
    fun stop(): Int {
        ensureLoaded()
        return nativeStop()
    }

    fun key(evdevCode: Int, down: Boolean) {
        ensureLoaded(); nativeKey(evdevCode, down)
    }

    fun pointerMotion(x: Int, y: Int) {
        ensureLoaded(); nativePointerMotion(x, y)
    }

    fun pointerButton(evdevBtn: Int, down: Boolean) {
        ensureLoaded(); nativePointerButton(evdevBtn, down)
    }

    fun pointerScroll(steps: Int) {
        ensureLoaded(); nativePointerScroll(steps)
    }

    /** action: 0 = down, 1 = motion, 2 = up. */
    fun touch(action: Int, id: Int, x: Int, y: Int) {
        ensureLoaded(); nativeTouch(action, id, x, y)
    }

    private external fun nativeStart(
        socketPath: String,
        xkbRoot: String,
        width: Int,
        height: Int,
    ): Int

    private external fun nativeAttachSurface(surface: Surface, width: Int, height: Int)
    private external fun nativeDetachSurface()
    private external fun nativeStop(): Int
    private external fun nativeKey(evdevCode: Int, down: Boolean)
    private external fun nativePointerMotion(x: Int, y: Int)
    private external fun nativePointerButton(evdevBtn: Int, down: Boolean)
    private external fun nativePointerScroll(steps: Int)
    private external fun nativeTouch(action: Int, id: Int, x: Int, y: Int)
}
