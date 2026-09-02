package app.pocketshell.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.pocketshell.packages.PackageGateway
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
    fun createLinuxSession(context: Context): SessionEntry = createLinuxSessionInternal(
        context,
        guestCommand = listOf(ShellEnvironment.SHELL_PATH_GUEST, "-l"),
        label = "Alpine Linux",
    )

    /**
     * Open an installed catalog CLI app inside a NEW dedicated guest session
     * (M2.4): the proot spec is identical to the Linux Shell's (same builder,
     * same guest shell) and the app's launch command is written into THAT NEW
     * session's PTY only. This is not injection into a user session — the
     * session exists solely for this app launch, the typed command stays
     * visible in its scrollback, and exiting the app returns to the guest
     * shell prompt (real nano → Ctrl+X → real shell).
     */
    fun createLinuxAppSession(context: Context, entry: app.pocketshell.packages.CliAppCatalogEntry): SessionEntry {
        val shellEntry = createLinuxSessionInternal(
            context,
            guestCommand = listOf(ShellEnvironment.SHELL_PATH_GUEST, "-l"),
            label = entry.name,
        )
        val command = entry.launchCommand.joinToString(" ") { token ->
            // catalog launch commands are plain names (pinned by tests); the
            // quote is defense in depth, never a substitute for validation
            if (token.matches(Regex("[A-Za-z0-9._/+%-]+"))) token else "'$token'"
        }
        val bytes = (command + "\n").toByteArray(Charsets.UTF_8)
        shellEntry.session.write(bytes, 0, bytes.size)
        return shellEntry
    }

    private fun createLinuxSessionInternal(
        context: Context,
        guestCommand: List<String>,
        label: String,
    ): SessionEntry {
        val appContext = context.applicationContext
        val state = RuntimeManager.state.value
        check(RuntimeProcessLauncher.canEnterLinuxShell(state)) {
            "Linux shell requires runtime READY (current state: $state) — refusing to fake one."
        }
        ShellEnvironment.ensureDirs(appContext)
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        // v0.5.0: refresh the guest's DNS/apk-workspace best-effort, then spawn
        // with the APK-CAPABLE session spec (no /proc + shared apk cache binds
        // — see RuntimeProcessLauncher.buildSessionSpec): manual `apk update` /
        // `apk add` inside this session now works and shares ONE cache with
        // the app-side package operations.
        PackageGateway.prepareGuestForSession(appContext, storage.rootfsDir)
        val prootTmp = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() }
        val spec = RuntimeProcessLauncher.buildSessionSpec(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = ShellEnvironment.homeDir(appContext),
            prootTmpDir = prootTmp,
            guestCommand = guestCommand,
            apkCacheDir = PackageGateway.apkCacheDir(storage),
        )
        return spawn(
            context = appContext,
            label = label,
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
        try {
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
            syncService(context)
            return entry
        } finally {
            // Never leave the UI stuck in "creating…" if the PTY/process
            // construction throws (v0.3.1: failures must be observable, not
            // fatal or sticky). The exception propagates to the caller's
            // no-crash boundary (TerminalViewModel.safeSpawn).
            _creating.value = false
        }
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
