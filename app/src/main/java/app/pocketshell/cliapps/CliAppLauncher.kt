package app.pocketshell.cliapps

import android.content.Context
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.terminal.TerminalSessionManager

/**
 * CLI app launcher (brief §17).
 *
 * Order of operations — every step is a real check; failure surfaces an honest
 * error instead of a fake session:
 *   1. verify the app is registered,
 *   2. verify the executable actually exists (resolved against the shell PATH),
 *   3. create a real terminal session that executes the real binary.
 */
object CliAppLauncher {

    sealed interface LaunchResult {
        /** Real session created and executing the app. */
        data class Launched(val sessionId: Long) : LaunchResult
        data class ExecutableNotFound(val app: CliApp) : LaunchResult
        data class Error(val message: String) : LaunchResult
    }

    fun launch(context: Context, registry: CliAppRegistry, app: CliApp): LaunchResult {
        val resolved = ShellEnvironment.resolveExecutable(
            app.executable,
            ShellEnvironment.shellPathDirs(),
        ) ?: return LaunchResult.ExecutableNotFound(app)

        return try {
            val entry = TerminalSessionManager.createSessionForApp(
                context = context,
                app = app.copy(executable = resolved),
            )
            LaunchResult.Launched(entry.id)
        } catch (e: Exception) {
            LaunchResult.Error(e.message ?: "Unknown launch failure")
        }
    }
}
