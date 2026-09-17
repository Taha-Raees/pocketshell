package app.pocketshell

import android.app.Application
import android.content.res.Configuration
import app.pocketshell.packages.PackageGateway
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import app.pocketshell.settings.themeModeIsDark
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.ui.theme.TerminalTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class PocketShellApp : Application() {

    companion object {
        /**
         * The persisted theme identity, read SYNCHRONOUSLY once in onCreate
         * (owner 2026-09-16 no-glimpse fix): the process-start palette and
         * SettingsViewModel's first-frame StateFlow seeds both come from
         * here, so an open never flashes the fresh-install default before
         * the saved theme lands. Fresh installs read the Aurora × Dark
         * defaults — that feature is unchanged.
         */
        var startupTheme: AppTheme = AppTheme.AURORA
            private set
        var startupThemeMode: ThemeMode = ThemeMode.DARK
            private set
    }

    override fun onCreate() {
        super.onCreate()
        // The terminal palette must be in place BEFORE any TerminalEmulator
        // is constructed (each emulator copies the static scheme defaults at
        // creation — docs/PHASE-3.1-DESIGN.md) — and, per the owner's
        // no-glimpse fix, it must be the SAVED palette, not the fresh-install
        // default: the old hardcoded Aurora start showed Aurora for a glimpse
        // on every open until the DataStore emit re-applied the saved theme.
        // One small blocking read here (a few KB, once per process, guarded
        // by a timeout that falls back to the defaults) replaces the flash;
        // the first PocketShellTheme composition re-applies the SAME values.
        runBlocking {
            withTimeoutOrNull(500L) {
                val repo = SettingsRepository(this@PocketShellApp)
                startupTheme = repo.theme.first()
                startupThemeMode = repo.themeMode.first()
            }
        }
        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        TerminalTheme.applyTheme(
            startupTheme,
            light = themeModeIsDark(startupThemeMode, systemDark).not(),
        )
        // Create the real per-app shell directories (HOME, TMPDIR) once.
        ShellEnvironment.ensureDirs(this)
        // Reconcile Linux runtime state from disk (M2.2) — derives the honest
        // initial RuntimeState; no work is scheduled here.
        RuntimeManager.init(this)
        // M2.4: real apk-backed package layer (process-scoped, like the
        // terminal session manager; nothing runs during init).
        PackageGateway.init(this)
        // Phase 4: Companion web runtime — context handoff ONLY. m4.0.1
        // hotfix (device-reported 2026-09-05): this call runs at EVERY app
        // start, so it must NEVER load the WebView provider — m4.0's eager
        // CookieManager.getInstance() here made a broken/updated WebView
        // package crash every PocketShell launch before any UI. All
        // provider touches now happen lazily inside the web host, guarded;
        // failure degrades only the Companion surface (runtimeFailed).
        // m4.1.0: the host is the REBUILT CompanionWebHost (the proven
        // baseline recipe; docs/RENDER-RESET-M4.0.9.md verdict).
        app.pocketshell.companion.CompanionWebHost.init(this)
        // M7.2 P1: notification foundation — the ONE owner of the event
        // notification channels + the startup stale-notification sweep.
        // Output/integration layer only: it owns no session or agent state
        // and never blocks app start (sweep runs off the main thread).
        app.pocketshell.notifications.NotificationCoordinator.init(this)
        // M7.2 P4: the notification consumer — the ONE subscriber to the
        // P3c agent-runtime event stream (replay-free, transition-only).
        // Application scope is the lifecycle owner: the stream has NO
        // replay, so the subscription must exist before the first session
        // can spawn — and nothing spawns before Application.onCreate
        // returns. Consumer-only: it polls nothing, scans nothing, owns no
        // lifecycle truth; it folds events through the pure truth contract
        // and posts/cancels via the coordinator above.
        app.pocketshell.notifications.AgentRuntimeNotificationConsumer.ensureStarted()
    }
}
