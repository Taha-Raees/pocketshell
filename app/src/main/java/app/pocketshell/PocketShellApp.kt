package app.pocketshell

import android.app.Application
import app.pocketshell.terminal.ShellEnvironment

class PocketShellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the real per-app shell directories (HOME, TMPDIR) once.
        ShellEnvironment.ensureDirs(this)
    }
}
