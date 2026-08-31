package app.pocketshell.runtime

import android.content.Context
import android.os.Build
import app.pocketshell.runtime.RuntimeState.Companion.canStartInstall
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pinned rootfs artifact — reproducible, checksum-verified (Master Prompt §10).
 * Version bumps happen through app updates, never through loose URLs.
 */
object RuntimePin {
    const val DISTRIBUTION = "Alpine"
    const val DISTRIBUTION_VERSION = "3.24.1"
    const val ARCHITECTURE = "aarch64"
    const val URL =
        "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/" +
            "alpine-minirootfs-3.24.1-aarch64.tar.gz"
    const val SIZE_BYTES = 4_023_732L
    const val SHA256 = "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259"

    val spec: RuntimeInstaller.RootfsSpec = RuntimeInstaller.RootfsSpec(
        url = URL,
        expectedSha256 = SHA256,
        expectedSizeBytes = SIZE_BYTES,
        distribution = DISTRIBUTION,
        distributionVersion = DISTRIBUTION_VERSION,
        architecture = ARCHITECTURE,
    )

    /** The device ABI that can host the pinned rootfs. */
    const val REQUIRED_ABI = "arm64-v8a"

    fun deviceSupported(): Boolean = Build.SUPPORTED_ABIS.any { it == REQUIRED_ABI }
}

/**
 * App-scoped facade for the Linux runtime (docs/M2-ARCHITECTURE §6).
 * The UI only ever sees [state] and [lastEvent]; all runtime logic lives here.
 */
object RuntimeManager {

    /**
     * Crash containment: a runtime pipeline failure is a *product state*
     * (FAILED / REPAIR_REQUIRED), never a process killer. The handler below is
     * the last-resort net; the install/remove coroutines also catch locally so
     * the user always sees an honest retryable state in Diagnostics.
     * (v0.2.1: the M2.2 build crashed here — a SecurityException from the
     * missing INTERNET permission escaped the coroutine and killed the app
     * the moment "Install" was tapped.)
     */
    private val crashGuard = CoroutineExceptionHandler { _, t ->
        runCatching { android.util.Log.e("RuntimeManager", "runtime coroutine escaped containment", t) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)
    private val installMutex = Mutex()

    @Volatile
    private var storage: RuntimeStorage? = null

    private val installer: RuntimeInstaller by lazy {
        RuntimeInstaller(requireNotNull(storage) { "RuntimeManager.init(context) not called" })
    }

    private val _state = MutableStateFlow(RuntimeState.NOT_INSTALLED)

    /** Source of truth for the UI. Derived from disk, updated by real work only. */
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private val _lastEvent = MutableStateFlow<RuntimeInstallEvent?>(null)

    /** Latest pipeline event (progress display; may be null between installs). */
    val lastEvent: StateFlow<RuntimeInstallEvent?> = _lastEvent.asStateFlow()

    /**
     * Called once from [app.pocketshell.PocketShellApp]. Reconciles the
     * initial state from disk (a handful of stat() calls — safe on the main
     * thread; the actual install pipeline never runs here).
     */
    fun init(context: Context) {
        if (storage != null) return
        val appContext = context.applicationContext
        val s = RuntimeStorage(appContext.noBackupFilesDir)
        storage = s
        val honest = if (!RuntimePin.deviceSupported()) {
            RuntimeState.UNSUPPORTED_ABI
        } else {
            s.reconcileInitialState()
        }
        _state.value = honest
    }

    /**
     * Start (or retry/repair) the installation. No-op unless the current
     * state allows it; concurrent calls are serialized.
     */
    fun startInstall(onEvent: (RuntimeInstallEvent) -> Unit = {}): Job? {
        val s = storage ?: return null
        val current = _state.value
        if (!canStartInstall(current)) return null
        if (!RuntimePin.deviceSupported()) {
            _state.value = RuntimeState.UNSUPPORTED_ABI
            return null
        }
        return scope.launch {
            installMutex.withLock {
                RuntimeCrashGuard.install(
                    storage = s,
                    installer = installer,
                    spec = RuntimePin.spec,
                    onEvent = {
                        _lastEvent.value = it
                        onEvent(it)
                    },
                    onState = { transitionTo(it) },
                    forceState = { transitionToLenient(it) },
                )
            }
        }
    }

    /** Explicit user action: delete the runtime and its metadata. */
    fun remove() {
        val s = storage ?: return
        scope.launch {
            installMutex.withLock {
                RuntimeCrashGuard.remove(
                    storage = s,
                    onEvent = { _lastEvent.value = it },
                    onState = { transitionTo(it) },
                    forceState = { transitionToLenient(it) },
                )
            }
        }
    }

    /** Same path as install — the state machine routes REPAIR_REQUIRED → DOWNLOADING. */
    fun repair(onEvent: (RuntimeInstallEvent) -> Unit = {}): Job? = startInstall(onEvent)

    private fun transitionTo(next: RuntimeState) {
        val current = _state.value
        if (current == next) return
        check(current.canTransitionTo(next)) {
            "illegal runtime state transition: $current -> $next"
        }
        _state.value = next
    }

    /**
     * [transitionTo] that can never throw: used only from failure paths.
     * FAILED/REPAIR_REQUIRED are always truthful descriptions after a broken
     * attempt, so forcing them is honest even if the transition table is
     * surprised by an exotic sequence.
     */
    private fun transitionToLenient(next: RuntimeState) {
        try {
            transitionTo(next)
        } catch (_: IllegalStateException) {
            _state.value = next
        }
    }
}
