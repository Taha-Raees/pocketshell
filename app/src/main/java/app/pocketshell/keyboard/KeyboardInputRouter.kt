package app.pocketshell.keyboard

import android.view.View

/**
 * m4.0.3 — one keyboard, two live surfaces.
 *
 * The PocketShell keyboard deck is the app's only keyboard (no system IME).
 * Since Phase 4 the Companion (WebView) is a second typeable surface next to
 * the terminal, so every deck press must go to the surface the user is
 * actually working in. The rule is focus, not guesswork: the terminal view
 * and the active Companion WebView both register here, and dispatch resolves
 * to whichever view currently holds window focus (last tap wins).
 *
 * The pure decision lives in [pick] so it stays unit-testable without
 * instantiating real views (spec §32: no Android fakes in the test suite).
 */
object KeyboardInputRouter {

    /** The terminal canvas view (registered by TerminalViewHost). */
    @Volatile
    var terminalTarget: View? = null

    /** The active Companion WebView (registered by the host on focus). */
    @Volatile
    var webTarget: View? = null

    /**
     * m4.0.12 — focus-follows-input (§13): invoked on the main thread when
     * a Companion WebView input gains focus. The app root opens the shared
     * deck in response — the same keyboard the user would reach manually,
     * exactly when a typeable target appears. Nullable when no UI is mounted.
     */
    @Volatile
    var onWebFocusGained: (() -> Unit)? = null

    /**
     * The view that should receive the next deck press: the WebView only
     * while it is BOTH focused and attached (a destroyed/evicted tab or a
     * terminal tap hands focus back and routing follows automatically).
     */
    fun resolve(): View? {
        val web = webTarget
        return pick(
            web = web,
            webUsable = web != null && web.isAttachedToWindow && web.isFocused,
            terminal = terminalTarget,
        )
    }

    /** Pure routing decision — unit-pinned (see KeyboardInputRouterTest). */
    fun <T> pick(web: T?, webUsable: Boolean, terminal: T?): T? =
        if (web != null && webUsable) web else terminal

    /**
     * m4.0.12 — the universal dispatch chain: router decision first, then
     * the window's focused view. The fallback serves the surfaces the two
     * registrations cannot know (Compose text fields — the Companion
     * settings Name/URL inputs — and any future app-level typeable view),
     * so ONE keyboard serves EVERY text input in PocketShell (§6/§10).
     * Pure decision — unit-pinned.
     */
    fun <T> pickWithFallback(web: T?, webUsable: Boolean, terminal: T?, focused: T?): T? =
        pick(web, webUsable, terminal) ?: focused
}
