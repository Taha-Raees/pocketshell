package app.pocketshell

import android.app.Application
import app.pocketshell.packages.PackageGateway
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.terminal.TerminalPalette

class PocketShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 3.1: Midnight Sapphire terminal palette must be in place
        // BEFORE any TerminalEmulator is constructed (each emulator copies
        // the static scheme defaults at creation — docs/PHASE-3.1-DESIGN.md).
        TerminalPalette.applyDefaults()
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
    }
}

