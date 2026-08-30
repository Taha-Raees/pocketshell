package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.cliapps.CliApp
import app.pocketshell.cliapps.CliAppLauncher
import app.pocketshell.cliapps.CliAppRegistry
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

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId = _selectedId.asStateFlow()

    /** Open the terminal: reuse the newest live session, else create a real one. */
    fun openTerminal() {
        val existing = sessions.value.lastOrNull { !it.isFinished }
        _selectedId.value = existing?.id ?: createSession()
    }

    fun newSession() {
        _selectedId.value = createSession()
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
    fun launchApp(app: CliApp) {
        val result = CliAppLauncher.launch(getApplication<Application>(), registry, app)
        if (result is CliAppLauncher.LaunchResult.Launched) {
            _selectedId.value = result.sessionId
        }
        // ExecutableNotFound / Error are surfaced by the caller (M2 UI);
        // nothing is faked meanwhile.
    }

    private fun createSession(): Long =
        TerminalSessionManager.createSession(getApplication()).id
}
