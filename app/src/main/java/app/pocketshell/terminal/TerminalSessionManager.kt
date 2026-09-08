package app.pocketshell.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.pocketshell.packages.PackageGateway
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeStorage
import com.termux.terminal.TerminalSession
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-scoped owner of every terminal session (brief §14/§24) — and since
 * M7.2 P2 the ONE authoritative owner of session LIFECYCLE truth
 * (docs/M7.2-P0-AUDIT.md §10.3: extend the manager, never duplicate it).
 *
 * - Each entry owns its own PTY, shell process, environment, cwd, terminal
 *   state and scrollback (upstream TerminalSession/TerminalEmulator instance).
 * - UI lifecycle never destroys sessions: Activities/ViewModels re-attach to
 *   this manager. State does not leak between sessions by construction.
 * - Finished sessions remain visible (marked, never faked, with their real
 *   structured exit status) until the user closes their tab.
 * - Lifecycle transitions are applied ONLY here, ONLY through the pure
 *   [SessionLifecycleState] machine, ONLY on the main handler — no duplicate
 *   transition paths, no inference, no polling. Every transition also emits
 *   a typed [SessionLifecycleEvent] for downstream consumers; the StateFlow
 *   below remains the authoritative STATE truth.
 * - Lifecycle/exit state is intentionally IN-MEMORY ONLY (process-scoped,
 *   like the sessions themselves): process death loses everything honestly
 *   (START_NOT_STICKY, no restoration) and the next launch starts empty.
 *   Nothing is persisted — this is a different concern from P1's
 *   notification-permission DataStore and must stay separate.
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
        /** The typed lifecycle phase + structured exit status (M7.2 P2 — the ONE stored lifecycle truth). */
        val lifecycleState: SessionLifecycleState,
        /** Structured launch identity: which launch path spawned this session. */
        val origin: SpawnOrigin,
        /** Spawn-time agent identity for named-launcher sessions; null for plain shells. */
        val agent: AgentHint?,
    ) {
        val displayLabel: String get() = title ?: label

        /** Derived from the lifecycle state — never a stored second boolean. */
        val lifecycle: SessionPhase get() = lifecycleState.phase

        /** The waitpid-proven exit status; non-null exactly when [lifecycle] is FINISHED. */
        val exitStatus: ExitStatus? get() = lifecycleState.exitStatus

        /** Derived for the historical call sites ("the direct child has exited"). */
        val isFinished: Boolean get() = lifecycleState.phase == SessionPhase.FINISHED
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _sessions = MutableStateFlow<List<SessionEntry>>(emptyList())
    val sessions: StateFlow<List<SessionEntry>> = _sessions.asStateFlow()

    /**
     * Edge-triggered lifecycle transitions, emitted ONLY at the state-mutation
     * sites below. Best-effort edges for downstream consumers — the buffered
     * overflow policy would drop only under a pathological storm of transitions
     * while no collector keeps up, and any consumer that misses an edge
     * reconciles from the authoritative [sessions] StateFlow (every event also
     * carries the full identity block, so consumers never re-look-up by id).
     * Drops are logged — nothing is silently swallowed.
     */
    private val _lifecycleEvents = MutableSharedFlow<SessionLifecycleEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val lifecycleEvents: SharedFlow<SessionLifecycleEvent> = _lifecycleEvents.asSharedFlow()

    private var nextId: Long = 1L

    /** True while a creation call is in flight (process spawn takes a moment). */
    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    /**
     * Create a real session: real PTY via libtermux JNI, real /system/bin/sh,
     * per-session environment and working directory.
     *
     * @param initialCommand optional command executed by the shell (CLI app launch).
     * @param origin the structured launch identity of the calling path (M7.2 P2 —
     *   explicit at every spawn site; no default that could silently mislabel).
     * @param agent spawn-time agent identity, or null for plain shells.
     */
    fun createSession(
        context: Context,
        label: String? = null,
        initialCommand: String? = null,
        environmentExtras: Map<String, String> = emptyMap(),
        workingDirectory: String? = null,
        origin: SpawnOrigin,
        agent: AgentHint? = null,
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
            origin = origin,
            agent = agent,
        )
    }

    /**
     * Enter the installed Alpine guest through proot (M2.3): SAME PTY, SAME
     * session machinery — only the spawned process differs. Refuses honestly
     * unless the runtime is READY; nothing is ever faked.
     *
     * TWO-PHASE since the M6 Phase-C audit (C3/C1.1):
     * [prepareLinuxSession] is the HEAVY half (apk fd-link self-repair, the
     * glibc layer ensure — marker fast path or full re-extraction — sysdata
     * overlays, DNS/apk-workspace repair) and must run OFF the main thread:
     * the layer's re-extraction path reads an ~18 MB asset, sha-verifies it
     * and writes ~103 entries, which used to freeze the UI for the whole
     * duration when session creation ran synchronously in the click handler.
     * [spawnLinuxSession] is the PTY half and keeps the upstream
     * MainThreadHandler contract (main thread only). Callers compose:
     * `withContext(IO) { prepareLinuxSession(ctx) }` then
     * `withContext(Main) { spawnLinuxSession(...) }`.
     */
    fun prepareLinuxSession(context: Context): List<String> {
        val appContext = context.applicationContext
        ShellEnvironment.ensureDirs(appContext)
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        // M2.6 → m3.6: prepare the guest (DNS/apk workspace best-effort + the
        // apk fd-link SELF-REPAIR — docs/PROCFS-CONTRACT.md). Since v0.7.0-m3.6
        // the /proc bind is ABSOLUTE for interactive sessions: the previous
        // gate (bind /proc only when the patched guest apk library verified)
        // silently degraded every session to no /proc once an in-guest
        // `apk upgrade` replaced the library — and Bun-compiled CLIs (Kilo
        // Code) resolve paths via /proc/self/fd on aarch64, so realpath() of
        // EXISTING paths failed with ENOENT. /proc is now always bound (real
        // host procfs, hidepid=2-filtered); the apk fd-link safety that used
        // to gate it is restored here by the self-repair instead, and any
        // residual risk is reported honestly by Diagnostics — never by
        // stripping procfs from the session.
        // M2.6.12: the same prepare phase writes the verified sysdata
        // overlays for kernel-denied standard /proc files (probe-first —
        // real files are never overlaid). They ride every interactive session
        // directly on top of the real /proc bind; the builder refuse-guards
        // the rest.
        // M6 Phase-C: the glibc layer ensure runs inside the SAME prep call
        // (PackageGateway.prepareGuestForSession) — single-flight inside
        // GuestGlibcRuntime, so a concurrent second session waits instead of
        // racing the extraction.
        val prep = PackageGateway.prepareGuestForSession(appContext, storage.rootfsDir)
        return prep.sysData.bindArgs()
    }

    /**
     * Phase 2 of guest session creation — the PTY spawn. MAIN THREAD ONLY
     * (TerminalSession MainThreadHandler contract). Refuses honestly unless
     * the runtime is READY; the sysdata binds come from the completed
     * [prepareLinuxSession] call.
     */
    fun spawnLinuxSession(
        context: Context,
        guestCommand: List<String>,
        label: String,
        sysDataBinds: List<String>,
        origin: SpawnOrigin,
        agent: AgentHint? = null,
    ): SessionEntry {
        val appContext = context.applicationContext
        val state = RuntimeManager.state.value
        check(RuntimeProcessLauncher.canEnterLinuxShell(state)) {
            "Linux shell requires runtime READY (current state: $state) — refusing to fake one."
        }
        ShellEnvironment.ensureDirs(appContext)
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        val prootTmp = File(appContext.cacheDir, "proot-tmp").apply { mkdirs() }
        val spec = RuntimeProcessLauncher.buildSessionSpec(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = ShellEnvironment.homeDir(appContext),
            prootTmpDir = prootTmp,
            guestCommand = guestCommand,
            apkCacheDir = PackageGateway.apkCacheDir(storage),
            sysDataBinds = sysDataBinds,
        )
        return spawn(
            context = appContext,
            label = label,
            command = spec.executable,
            workingDirectory = spec.workingDirectory,
            args = spec.arguments.toTypedArray(),
            env = spec.environment.toTypedArray(),
            origin = origin,
            agent = agent,
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
        origin: SpawnOrigin,
        agent: AgentHint?,
    ): SessionEntry {
        _creating.value = true
        try {
            val id = nextId++
            val client = PocketShellSessionClient(
                context = context,
                onTitleChanged = { mainHandler.post { refreshTitle(id) } },
                onSessionFinished = { mainHandler.post { markFinished(id) } },
                // M7.2 P2: the REAL fork signal (upstream fires this once, on the
                // main thread, right after createSubprocess — before any waiter
                // thread can possibly deliver an exit). Posts like every other
                // transition for uniformity with the manager's main-handler rule.
                onProcessStarted = { mainHandler.post { markStarted(id) } },
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
                // The entry exists; the PTY child is NOT forked yet (upstream forks
                // lazily at the first updateSize — audit §1.3). The real fork
                // signal moves it to RUNNING; a real waitpid delivery moves it to
                // FINISHED. Nothing is inferred anywhere in between.
                lifecycleState = SessionLifecycleState.STARTING,
                origin = origin,
                agent = agent,
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
            // Snapshot BEFORE removal: the Removed event carries the full identity
            // block (consumers must never re-look-up by id — the entry is gone).
            val removed = _sessions.value.firstOrNull { it.id == id }
            if (removed == null) {
                Log.w(LOG_TAG, "closeSession($id): no such session (already removed?)")
                return@post
            }
            // Guard the kill path (M7.2 P2 race audit): the upstream
            // finishIfRunning() only checks isRunning() — which is TRUE for a
            // session whose PTY was never forked (mShellPid == 0) — and would then
            // issue kill(0, SIGKILL): "signal the caller's whole process group",
            // i.e. undefined blast radius far beyond this session. Only a real,
            // forked, still-running child (pid > 0) is killed; a not-yet-started
            // (STARTING) or already-finished (pid == -1) session is just removed.
            if (removed.session.getPid() > 0) {
                removed.session.finishIfRunning()
            }
            _sessions.update { list -> list.filterNot { it.id == id } }
            _lastContext?.let { syncService(it) }
            emit(
                SessionLifecycleEvent.Removed(
                    sessionId = id,
                    label = removed.label,
                    origin = removed.origin,
                    agent = removed.agent,
                    exitStatus = removed.exitStatus,
                ),
            )
        }
    }

    private fun refreshTitle(id: Long) {
        _sessions.update { list ->
            list.map { entry ->
                if (entry.id == id) entry.copy(title = entry.session.getTitle()) else entry
            }
        }
    }

    /**
     * Apply the real fork signal (STARTING → RUNNING) through the pure
     * machine. Rejections (duplicate/out-of-order deliveries) are logged and
     * change nothing — never silently swallowed.
     */
    private fun markStarted(id: Long) {
        // The close race (closeSession vs the fork signal) removes the entry
        // first — an unexpected delivery is logged, never silently swallowed.
        if (_sessions.value.none { it.id == id }) {
            Log.w(LOG_TAG, "markStarted($id): no such session (removed before the fork signal arrived)")
            return
        }
        _sessions.update { list ->
            list.map { entry ->
                if (entry.id != id) return@map entry
                when (val transition = entry.lifecycleState.onProcessStarted()) {
                    is LifecycleTransition.Accepted -> {
                        val updated = entry.copy(lifecycleState = transition.next)
                        emit(
                            SessionLifecycleEvent.Started(
                                sessionId = id,
                                label = updated.label,
                                origin = updated.origin,
                                agent = updated.agent,
                            ),
                        )
                        updated
                    }
                    is LifecycleTransition.Rejected -> {
                        Log.w(LOG_TAG, "markStarted($id) rejected: ${transition.reason}")
                        entry
                    }
                }
            }
        }
    }

    /**
     * Apply the real waitpid exit (→ FINISHED with the structured status)
     * through the pure machine. Runs on the main handler AFTER the upstream
     * handler stored the status in the session (cleanupResources →
     * onSessionFinished ordering), so getExitStatus() here is the real
     * waitpid value, never a guess.
     */
    private fun markFinished(id: Long) {
        // The close race (closeSession vs the waiter's exit delivery) removes
        // the entry first — the exit status of a removed session is honestly
        // gone (nothing to record it on); the delivery is logged, never silent.
        if (_sessions.value.none { it.id == id }) {
            Log.w(LOG_TAG, "markFinished($id): no such session (closed before the exit arrived)")
            return
        }
        _sessions.update { list ->
            list.map { entry ->
                if (entry.id != id) return@map entry
                // Defensive real-signal guard: a finish callback for a session
                // whose direct child is STILL alive carries no waitpid value —
                // recording anything would be invention. Refuse loudly instead.
                if (entry.session.isRunning()) {
                    Log.w(LOG_TAG, "markFinished($id) ignored: session reports still running (no real exit signal)")
                    return@map entry
                }
                when (val transition = entry.lifecycleState.onProcessFinished(entry.session.getExitStatus())) {
                    is LifecycleTransition.Accepted -> {
                        val updated = entry.copy(lifecycleState = transition.next)
                        emit(
                            SessionLifecycleEvent.Finished(
                                sessionId = id,
                                label = updated.label,
                                origin = updated.origin,
                                agent = updated.agent,
                                exitStatus = updated.exitStatus!!, // FINISHED always carries one (typed model)
                            ),
                        )
                        updated
                    }
                    is LifecycleTransition.Rejected -> {
                        Log.w(LOG_TAG, "markFinished($id) rejected: ${transition.reason}")
                        entry
                    }
                }
            }
        }
        // Keep the FGS honest: a finished session is no longer "running".
        _lastContext?.let { syncService(it) }
    }

    /** Emit a lifecycle event; a failed emit is logged, never silent. */
    private fun emit(event: SessionLifecycleEvent) {
        if (!_lifecycleEvents.tryEmit(event)) {
            Log.w(LOG_TAG, "lifecycle event buffer full — dropped ${event::class.simpleName} for session ${event.sessionId} (state truth remains in the sessions StateFlow)")
        }
    }

    private var _lastContext: Context? = null

    private fun syncService(context: Context) {
        _lastContext = context
        TerminalService.syncWithSessionState(context)
    }

    private const val LOG_TAG = "TerminalSessionManager"
}
