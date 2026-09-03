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
    }
}

