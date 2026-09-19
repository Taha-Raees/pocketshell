package app.pocketshell.widget

/**
 * M8.4.2 — process-scoped state ownership for Home Applications.
 *
 * THE PROBLEM IT SOLVES: screens switch by composition (`when (screen)`),
 * so leaving Home DISPOSES the whole carousel — and the pager disposes
 * pages beyond its bounds while swiping. Any application state that lived
 * in `remember` was therefore destroyed by the user merely navigating:
 * probe instances lost their idle-gate memory (Storage re-measured and
 * Git re-probed on every visit), scans re-ran, drafts and selections
 * reset. That was refresh-on-navigation, not refresh-on-purpose.
 *
 * THE CONTRACT: each application owns ONE state holder object, stored
 * here under its registry id. The store is owned by the process-scoped
 * [HomeApplicationViewModel], so holders survive page disposal, leaving
 * Home, and configuration changes. Applications reach it through
 * [HomeAppContext] — they never hold this store themselves.
 *
 * What belongs in a holder: cached backend snapshots (+ their loading/
 * error/last-refresh state), the probe/scanner instances (their idle
 * gates are part of the cache), transient UI state (selected detail,
 * draft, section). What does NOT: anything that must survive process
 * death — that stays in the per-domain DataStore stores as before.
 */
class HomeAppStateStore {

    private val holders = HashMap<String, Any>()

    /** The application's one holder, created on first need, kept forever. */
    fun <T : Any> forApp(appId: String, factory: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        return holders.getOrPut(appId) { factory() } as T
    }
}
