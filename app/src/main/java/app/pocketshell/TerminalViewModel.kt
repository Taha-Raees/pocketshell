package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.cliapps.CliApp
import app.pocketshell.cliapps.CliAppLauncher
import app.pocketshell.cliapps.CliAppRegistry
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.terminal.TerminalSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * UI-side state holder. Sessions themselves live in the process-scoped
 * [TerminalSessionManager]; this ViewModel only tracks selection and provides
 * flows — so Activity recreation can never destroy sessions (brief §24).
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    val sessions = TerminalSessionManager.sessions
    val creating = TerminalSessionManager.creating

    private val registry = CliAppRegistry(application)

    val installedApps = registry.apps.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Runtime state (M2) — Home shows the honest Linux Shell availability. */
    val runtimeState = RuntimeManager.state

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId = _selectedId.asStateFlow()

    /**
     * Honest, non-fatal launch failure surface (v0.3.1 regression fix): any
     * session-spawn failure is reported HERE — as a message the Home screen
     * renders — instead of escaping a click handler and killing the process.
     */
    private val _launchError = MutableStateFlow<String?>(null)
    val launchError = _launchError.asStateFlow()

    fun dismissLaunchError() {
        _launchError.value = null
    }

    /** Open the terminal: reuse the newest live session, else create a real one. */
    fun openTerminal(): Boolean {
        val existing = sessions.value.lastOrNull { !it.isFinished }
        if (existing != null) {
            _selectedId.value = existing.id
            return true
        }
        return safeSpawn("Terminal") { createSession() }
    }

    fun newSession(): Boolean = safeSpawn("Terminal") { createSession() }

    /**
     * Enter the installed Alpine guest (M2.3). Caller must only invoke this
     * when [runtimeState] is READY (Home routes otherwise). Returns true when
     * a real session was created and selected; false + [launchError] when the
     * launch was refused — the app must NEVER die from a refused launch.
     */
    fun openLinuxShell(): Boolean {
        val application = getApplication<Application>()
        // Preflight for an honest, specific message before any side effects.
        val preflight = RuntimeProcessLauncher.preconditionProblem(
            nativeLibraryDir = application.applicationInfo.nativeLibraryDir,
            rootfsDir = RuntimeStorage(application.noBackupFilesDir).rootfsDir,
        )
        if (preflight != null) {
            _launchError.value = preflight
            return false
        }
        return safeSpawn("Linux shell") {
            TerminalSessionManager.createLinuxSession(application).id
        }
    }

    /** Single no-crash boundary around every process spawn. */
    private inline fun safeSpawn(what: String, block: () -> Long): Boolean {
        _launchError.value = null
        return try {
            _selectedId.value = block()
            true
        } catch (t: Throwable) {
            // Containment, not concealment: the real cause is shown, the
            // process stays alive, and the user keeps a working app.
            _launchError.value = "$what could not start: ${t.message ?: t.javaClass.simpleName}"
            false
        }
    }

    fun select(id: Long) {
        _selectedId.value = id
    }

    fun closeSession(id: Long) {
        TerminalSessionManager.closeSession(id)
        if (_selectedId.value == id) {
            _selectedId.value = sessions.value
                .filter { it.id != id && !it.isFinished }
                .lastOrNull()?.id
        }
    }

    /** Launch a registered CLI app into a real session (brief §17). */
    fun launchApp(app: CliApp): Boolean {
        val result = CliAppLauncher.launch(getApplication<Application>(), registry, app)
        return when (result) {
            is CliAppLauncher.LaunchResult.Launched -> {
                _selectedId.value = result.sessionId
                true
            }
            is CliAppLauncher.LaunchResult.ExecutableNotFound ->
                safeFailure("${app.name} could not start: executable '${app.executable}' not found on this device")
            is CliAppLauncher.LaunchResult.Error ->
                safeFailure("${app.name} could not start: ${result.message}")
        }
    }

    private fun safeFailure(message: String): Boolean {
        _launchError.value = message
        return false
    }

    private fun createSession(): Long =
        TerminalSessionManager.createSession(getApplication()).id
}
