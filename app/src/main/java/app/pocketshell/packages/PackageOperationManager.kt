package app.pocketshell.packages

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of package operations (M2.4) — the package sibling of
 * [app.pocketshell.terminal.TerminalSessionManager]:
 *
 * - ONE mutating operation at a time (apk's database lock + honest UI). A
 *   second request while one runs fails IMMEDIATELY with a readable reason —
 *   never queued silently, never overlapping (atomic guard, not a queue).
 * - Operations run in THIS object's scope, not a ViewModel's: Activity
 *   recreation cannot cancel a real apk transaction. Only an explicit
 *   [cancelCurrent] (or the app process dying) stops one — the guest process
 *   is destroyed for real, and apk's own journalling keeps the database
 *   consistent across the interruption.
 * - Every state shown to the user maps to real work: UPDATING_REPOSITORIES
 *   only while `apk update` runs, VERIFYING only while `apk info -e` /
 *   `command -v` answer, SUCCESS only after the guest confirmed. No percent,
 *   no delays, no invented progress.
 */
class PackageOperationManager(
    runner: GuestCommandRunner,
    private val packagesFactory: (GuestCommandRunner) -> PackageManager,
    private val runtimeReady: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val tailLimit: Int = 4000,
) {

    /**
     * The exec layer routes every guest process through [activeProcess] so
     * [cancelCurrent] can destroy the REAL process (a coroutine cancel alone
     * would never interrupt the blocking waitFor inside the exec layer).
     */
    @Volatile private var activeProcess: GuestProcess? = null

    private val wrappedRunner = object : GuestCommandRunner {
        override fun start(spec: app.pocketshell.runtime.RuntimeProcessLauncher.LaunchSpec): GuestProcess =
            runner.start(spec).also { activeProcess = it }
    }

    private val packages: PackageManager = packagesFactory(wrappedRunner)

    private val crashGuard = CoroutineExceptionHandler { _, t ->
        runCatching { android.util.Log.e("PackageOperationManager", "operation escaped containment", t) }
        val op = _current.value
        if (op != null && op.state != PackageOperationState.SUCCESS && op.state != PackageOperationState.FAILED) {
            _current.value = op.copy(
                state = PackageOperationState.FAILED,
                endTimeEpochMs = clock(),
                error = "internal error: ${t.message ?: t.javaClass.simpleName}",
            )
        }
        _busy.value = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)

    private val singleFlight = AtomicBoolean(false)

    private val _current = MutableStateFlow<PackageOperation?>(null)
    /** The latest operation snapshot (running or finished) for the UI. */
    val current: StateFlow<PackageOperation?> = _current.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** True while a MUTATING operation (update/install/uninstall) is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var nextId: Long = 1L
    private var runningJob: Job? = null

    // ------------------------------------------------------------- operations

    /** `apk update` only. Returns false when refused (honest reason in [current]). */
    fun updateRepositories(): Boolean = startOperation(PackageOperationKind.UPDATE_REPOSITORIES) { operation ->
        update(operation, PackageOperationState.UPDATING_REPOSITORIES)
        val result = packages.updateRepositories()
        settle(operation, result)
    }

    /**
     * Real install flow (rehearsed order): `apk update` → `apk add` → verify
     * via `apk info -e -v` → verify executable via `command -v`. SUCCESS is
     * only ever emitted after BOTH verifications passed in the real guest.
     */
    fun install(entry: CliAppCatalogEntry): Boolean =
        startOperation(PackageOperationKind.INSTALL, entry.apkPackageName) { operation ->
            update(operation, PackageOperationState.UPDATING_REPOSITORIES)
            val updateResult = packages.updateRepositories()
            if (!updateResult.success) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    exitCode = updateResult.exitCode,
                    stdout = updateResult.stdout,
                    stderr = updateResult.stderr,
                    error = "repository update failed: ${describe(updateResult)}",
                )
                return@startOperation
            }
            update(operation, PackageOperationState.INSTALLING)
            val installResult = packages.install(entry.apkPackageName)
            if (!installResult.success) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    exitCode = installResult.exitCode,
                    stdout = installResult.stdout,
                    stderr = installResult.stderr,
                    error = "apk add failed: ${describe(installResult)}",
                )
                return@startOperation
            }
            update(operation, PackageOperationState.VERIFYING)
            val info = packages.getPackageInfo(entry.apkPackageName)
            if (!info.installed) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    error = "apk reported success but '${entry.apkPackageName}' is not in the package " +
                        "database — refusing to claim installation",
                )
                return@startOperation
            }
            val execPath = packages.guestExecutablePath(entry.executable)
            if (execPath == null) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    error = "package installed, but executable '${entry.executable}' was not found via " +
                        "command -v — refusing to claim installation",
                )
                return@startOperation
            }
            settle(operation, PackageOperationState.SUCCESS, stdout = execPath)
        }

    /** Real uninstall: `apk del`, then verify absence in the real database. */
    fun uninstall(entry: CliAppCatalogEntry): Boolean =
        startOperation(PackageOperationKind.UNINSTALL, entry.apkPackageName) { operation ->
            update(operation, PackageOperationState.UNINSTALLING)
            val result = packages.uninstall(entry.apkPackageName)
            if (!result.success) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    exitCode = result.exitCode,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    error = "apk del failed: ${describe(result)}",
                )
                return@startOperation
            }
            update(operation, PackageOperationState.VERIFYING)
            val info = packages.getPackageInfo(entry.apkPackageName)
            if (info.installed) {
                settle(
                    operation,
                    PackageOperationState.FAILED,
                    error = "apk del reported success but '${entry.apkPackageName}' is still in the " +
                        "package database — refusing to claim removal",
                )
                return@startOperation
            }
            settle(operation, PackageOperationState.SUCCESS)
        }

    /**
     * Real search. Read-only: does NOT take the mutating single-flight guard
     * (apk index reads are safe during installs), but still reports honest
     * SEARCHING activity through [current].
     */
    fun search(query: String, onResult: (List<PackageSearchResult>) -> Unit): Job {
        val id = nextId++
        _current.value = PackageOperation(
            id = id,
            kind = PackageOperationKind.SEARCH,
            query = query,
            state = PackageOperationState.SEARCHING,
            startTimeEpochMs = clock(),
        )
        return scope.launch {
            val started = _current.value
            val results = try {
                packages.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _current.value = started?.copy(
                    state = PackageOperationState.FAILED,
                    endTimeEpochMs = clock(),
                    error = "search failed: ${t.message ?: t.javaClass.simpleName}",
                )
                emptyList()
            }
            _current.value = _current.value?.copy(
                state = PackageOperationState.SUCCESS,
                endTimeEpochMs = clock(),
            )
            onResult(results)
        }
    }

    /**
     * Cancel the running mutating operation: destroy the real guest process,
     * then cancel the job. The operation lands in FAILED("cancelled").
     */
    fun cancelCurrent() {
        activeProcess?.destroy()
        runningJob?.cancel()
    }

    // ---------------------------------------------------------------- plumbing

    private fun startOperation(
        kind: PackageOperationKind,
        packageName: String? = null,
        body: suspend (PackageOperation) -> Unit,
    ): Boolean {
        if (!runtimeReady()) {
            _current.value = PackageOperation(
                id = nextId++,
                kind = kind,
                packageName = packageName,
                state = PackageOperationState.FAILED,
                startTimeEpochMs = clock(),
                endTimeEpochMs = clock(),
                error = "the Linux runtime is not READY — install or repair it from Diagnostics first",
            )
            return false
        }
        // Atomic single-flight: fail fast, never queue, never overlap.
        if (!singleFlight.compareAndSet(false, true)) {
            _current.value = PackageOperation(
                id = nextId++,
                kind = kind,
                packageName = packageName,
                state = PackageOperationState.FAILED,
                startTimeEpochMs = clock(),
                endTimeEpochMs = clock(),
                error = "another package operation is already running — wait for it to finish",
            )
            return false
        }
        _busy.value = true
        val id = nextId++
        runningJob = scope.launch {
            try {
                body(
                    PackageOperation(
                        id = id,
                        kind = kind,
                        packageName = packageName,
                        state = PackageOperationState.IDLE,
                        startTimeEpochMs = clock(),
                    ),
                )
            } catch (e: CancellationException) {
                val op = _current.value
                if (op != null && op.state != PackageOperationState.SUCCESS && op.state != PackageOperationState.FAILED) {
                    _current.value = op.copy(
                        state = PackageOperationState.FAILED,
                        endTimeEpochMs = clock(),
                        error = "cancelled",
                    )
                }
                throw e
            } catch (t: Throwable) {
                val op = _current.value
                if (op != null && op.state != PackageOperationState.SUCCESS && op.state != PackageOperationState.FAILED) {
                    _current.value = op.copy(
                        state = PackageOperationState.FAILED,
                        endTimeEpochMs = clock(),
                        error = "internal error: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
            } finally {
                activeProcess = null
                runningJob = null
                _busy.value = false
                singleFlight.set(false)
            }
        }
        return true
    }

    private fun update(operation: PackageOperation, state: PackageOperationState) {
        _current.value = operation.copy(state = state)
    }

    private fun settle(operation: PackageOperation, result: PackageResult) {
        settle(
            operation,
            if (result.success) PackageOperationState.SUCCESS else PackageOperationState.FAILED,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            error = result.error,
        )
    }

    private fun settle(
        operation: PackageOperation,
        state: PackageOperationState,
        exitCode: Int? = null,
        stdout: String = "",
        stderr: String = "",
        error: String? = null,
    ) {
        _current.value = operation.copy(
            state = state,
            endTimeEpochMs = clock(),
            exitCode = exitCode,
            stdoutTail = stdout.takeLast(tailLimit),
            stderrTail = stderr.takeLast(tailLimit),
            error = error,
        )
    }

    private fun describe(result: PackageResult): String =
        result.error
            ?: result.stderr.lineSequence().lastOrNull { it.isNotBlank() }
            ?: "apk exited with code ${result.exitCode}"
}
