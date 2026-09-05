package app.pocketshell.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * m4.0.3 — the shared-deck routing decision (terminal canvas vs Companion
 * WebView). Pins the pure [KeyboardInputRouter.pick] logic; no Android views
 * are constructed (spec §32).
 */
class KeyboardInputRouterTest {

    private object Surface

    @Test
    fun `focused attached webview wins over terminal`() {
        assertEquals(
            Surface,
            KeyboardInputRouter.pick(
                web = Surface,
                webUsable = true,
                terminal = Any(),
            ),
        )
    }

    @Test
    fun `webview without focus falls back to the terminal`() {
        val terminal = Any()
        assertEquals(
            terminal,
            KeyboardInputRouter.pick(web = Surface, webUsable = false, terminal = terminal),
        )
    }

    @Test
    fun `no webview registered routes to the terminal`() {
        val terminal = Any()
        assertEquals(
            terminal,
            KeyboardInputRouter.pick(web = null, webUsable = false, terminal = terminal),
        )
    }

    @Test
    fun `nothing registered resolves to null (no dispatch)`() {
        assertNull(KeyboardInputRouter.pick<Any>(web = null, webUsable = false, terminal = null))
    }

    @Test
    fun `unusable webview with null terminal resolves to null`() {
        assertNull(
            KeyboardInputRouter.pick(web = Surface, webUsable = false, terminal = null),
        )
    }

    // ---- m4.0.12 — the universal dispatch chain (§6/§10: ONE keyboard
    //      serves every text input, so the router decision falls through to
    //      the window's focused view for surfaces the two registrations
    //      cannot know — e.g. Compose text fields) ----------------------------

    @Test
    fun `fallback keeps the router decision when one exists`() {
        assertEquals(
            Surface,
            KeyboardInputRouter.pickWithFallback(
                web = Surface,
                webUsable = true,
                terminal = Any(),
                focused = Any(),
            ),
        )
        val terminal = Any()
        assertEquals(
            terminal,
            KeyboardInputRouter.pickWithFallback(
                web = Surface,
                webUsable = false,
                terminal = terminal,
                focused = Any(),
            ),
        )
    }

    @Test
    fun `fallback serves the focused view when nothing is registered`() {
        val focused = Any()
        assertEquals(
            focused,
            KeyboardInputRouter.pickWithFallback<Any>(
                web = null,
                webUsable = false,
                terminal = null,
                focused = focused,
            ),
        )
    }

    @Test
    fun `fallback with nothing focused resolves to null (no dispatch)`() {
        assertNull(
            KeyboardInputRouter.pickWithFallback<Any>(
                web = Surface,
                webUsable = false,
                terminal = null,
                focused = null,
            ),
        )
    }
}
