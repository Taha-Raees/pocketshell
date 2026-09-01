package app.pocketshell.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.pocketshell.cliapps.CliApp
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeStorage
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-scoped owner of every terminal session (brief §14/§24).
 *
 * - Each entry owns its own PTY, shell process, environment, cwd, terminal
 *   state and scrollback (upstream TerminalSession/TerminalEmulator instance).
 * - UI lifecycle never destroys sessions: Activities/ViewModels re-attach to
 *   this manager. State does not leak between sessions by construction.
 * - Finished sessions remain visible (marked, never faked) until the user
 *   closes their tab.
 */
object TerminalSessionManager {

    /**
     * Hook the visible terminal view installs so it can repaint on session
     * output. Called on the main thread with the id of the session whose
     * screen changed.
     *
     * Upstream contract: TerminalView does not observe session data itself —
     * the host (Termux: TermuxTerminalSessionClient) calls
     * [com.termux.view.TerminalView.onScreenUpdated] from this hook. Missing
     * wiring here = output invisible until a layout pass forces a repaint.
     */
    @Volatile
    var onScreenUpdateListener: ((sessionId: Long) -> Unit)? = null

    data class SessionEntry(
        val id: Long,
        val session: TerminalSession,
        /** Fallback label ("Terminal 2", or CLI app name). */
        val label: String,
        /** Title from terminal escape sequences, when the running program sets one. */
        val title: String?,
        val isFinished: Boolean,
    ) {
        val displayLabel: String get() = title ?: label
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sessions = MutableStateFlow<List<SessionEntry>>(emptyList())
    val sessions: StateFlow<List<SessionEntry>> = _sessions.asStateFlow()

    private var nextId: Long = 1L

    /** True while a creation call is in flight (process spawn takes a moment). */
    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    /**
     * Create a real session: real PTY via libtermux JNI, real /system/bin/sh,
     * per-session environment and working directory.
     *
     * @param initialCommand optional command executed by the shell (CLI app launch).
     */
    fun createSession(
        context: Context,
        label: String? = null,
        initialCommand: String? = null,
        environmentExtras: Map<String, String> = emptyMap(),
        workingDirectory: String? = null,
    ): SessionEntry {
        val appContext = context.applicationContext
        ShellEnvironment.ensureDirs(appContext)
        val args = if (initialCommand != null) {
            arrayOf("-c", initialCommand)
        } else {
            arrayOf("-l")
        }
        val env = if (environmentExtras.isEmpty()) {
            ShellEnvironment.environment(appContext)
        } else {
            ShellEnvironment.environment(appContext) +
                environmentExtras.map { (k, v) -> "$k=$v" }
        }
        return spawn(
            context = appContext,
            label = label,
            command = ShellEnvironment.SHELL_PATH,
            workingDirectory = workingDirectory
                ?: ShellEnvironment.homeDir(appContext).absolutePath,
            args = args,
            env = env,
        )
    }

    /**
     * Enter the installed Alpine guest through proot (M2.3): SAME PTY, SAME
     * session machinery — only the spawned process differs. Refuses honestly
     * unless the runtime is READY; nothing is ever faked.
     */
    fun createLinuxSession(context: Context): SessionEntry {
        val appContext = context.applicationContext
        val state = RuntimeManager.state.value
        check(RuntimeProcessLauncher.canEnterLinuxShell(state)) {
            "Linux shell requires runtime READY (current state: $state) — refusing to fake one."
        }
        ShellEnvironment.ensureDirs(appContext)
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        val prootTmp = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() }
        val spec = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = ShellEnvironment.homeDir(appContext),
            prootTmpDir = prootTmp,
        )
        return spawn(
            context = appContext,
            label = spec.guestLabel,
            command = spec.executable,
            workingDirectory = spec.workingDirectory,
            args = spec.arguments.toTypedArray(),
            env = spec.environment.toTypedArray(),
        )
    }

    /** Single real-session factory: real PTY, real process, real environment. */
    private fun spawn(
        context: Context,
        label: String?,
        command: String,
        workingDirectory: String,
        args: Array<String>,
        env: Array<String>,
    ): SessionEntry {
        _creating.value = true
        val id = nextId++
        val client = PocketShellSessionClient(
            context = context,
            onTitleChanged = { mainHandler.post { refreshTitle(id) } },
            onSessionFinished = { mainHandler.post { markFinished(id) } },
            // Already on the main thread (TerminalSession MainThreadHandler);
            // invoke the visible view's refresh hook directly.
            onScreenUpdate = { onScreenUpdateListener?.invoke(id) },
        )
        val session = TerminalSession(
            command,
            workingDirectory,
            args,
            env,
            ShellEnvironment.TRANSCRIPT_ROWS,
            client,
        )
        val entry = SessionEntry(
            id = id,
            session = session,
            label = label ?: "Terminal $id",
            title = null,
            isFinished = false,
        )
        _sessions.update { it + entry }
        _creating.value = false
        syncService(context)
        return entry
    }

    /** Convenience: launch a registered CLI app (real executable, real session). */
    fun createSessionForApp(context: Context, app: CliApp): SessionEntry {
        val resolved = ShellEnvironment.resolveExecutable(
            app.executable,
            ShellEnvironment.shellPathDirs(),
        ) ?: throw IllegalStateException(
            "Executable '${app.executable}' for CLI app '${app.name}' not found — refusing to fake a session."
        )
        val command = buildString {
            append(resolved)
            app.arguments.forEach { append(' ').append(it) }
        }
        return createSession(
            context = context,
            label = app.name,
            initialCommand = command,
            environmentExtras = app.environment,
            workingDirectory = app.workingDirectory,
        )
    }

    /** Kill the session's process and remove its entry. */
    fun closeSession(id: Long) {
        mainHandler.post {
            _sessions.update { list ->
                val entry = list.firstOrNull { it.id == id } ?: return@update list
                entry.session.finishIfRunning()
                list - entry
            }
            _lastContext?.let { syncService(it) }
        }
    }

    private fun refreshTitle(id: Long) {
        _sessions.update { list ->
            list.map { entry ->
                if (entry.id == id) entry.copy(title = entry.session.getTitle()) else entry
            }
        }
    }

    private fun markFinished(id: Long) {
        _sessions.update { list ->
            list.map { entry ->
                if (entry.id == id && !entry.isFinished) entry.copy(isFinished = true) else entry
            }
        }
        // Keep the FGS honest: a finished session is no longer "running".
        _lastContext?.let { syncService(it) }
    }

    private var _lastContext: Context? = null

    private fun syncService(context: Context) {
        _lastContext = context
        TerminalService.syncWithSessionState(context)
    }
}
