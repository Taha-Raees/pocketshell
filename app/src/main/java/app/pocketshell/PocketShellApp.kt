package app.pocketshell

import android.app.Application
import app.pocketshell.packages.PackageGateway
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.terminal.ShellEnvironment

class PocketShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
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

