package app.pocketshell

import android.app.Application
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
    }
}
