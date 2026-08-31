package app.pocketshell.runtime

import kotlinx.coroutines.CancellationException

/**
 * Crash containment for runtime operations (introduced by the v0.2.1 fix).
 *
 * Regression being pinned: v0.2.0-m2.2-wip crashed the whole app the moment
 * "Install" was tapped, because a SecurityException (missing INTERNET
 * permission) escaped the install coroutine. A pipeline failure must always
 * land in an honest, retryable product state — FAILED for installs,
 * REPAIR_REQUIRED for removes — never in a dead process.
 *
 * Extracted from [RuntimeManager] so the containment is unit-testable on the
 * JVM: tests drive the REAL [RuntimeInstaller] through this guard.
 */
internal object RuntimeCrashGuard {

    /**
     * Runs the full install pipeline. Exceptions are consumed: callers'
     * coroutine survives, the user sees a retryable FAILED state.
     * [CancellationException] still propagates (structured concurrency).
     */
    suspend fun install(
        storage: RuntimeStorage,
        installer: RuntimeInstaller,
        spec: RuntimeInstaller.RootfsSpec,
        onEvent: (RuntimeInstallEvent) -> Unit,
        onState: (RuntimeState) -> Unit,
        forceState: (RuntimeState) -> Unit,
    ) {
        try {
            installer.install(spec, onEvent = onEvent, onState = onState)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // The installer converts its own failures to FAILED + a Failed
            // event, then rethrows. Whatever escaped that net (SecurityException,
            // UnknownHostException, even an Error) must land here — honest and
            // retryable, not fatal.
            runCatching { storage.cleanupTransient() }
            forceState(RuntimeState.FAILED)
            onEvent(
                RuntimeInstallEvent.Failed(
                    RuntimeState.FAILED,
                    t.message ?: "install failed (${t.javaClass.simpleName})",
                ),
            )
        }
    }

    /**
     * Runs the remove pipeline. A partially-removed runtime (files that could
     * not be deleted) lands in REPAIR_REQUIRED instead of pretending the
     * runtime is gone.
     */
    fun remove(
        storage: RuntimeStorage,
        onEvent: (RuntimeInstallEvent) -> Unit,
        onState: (RuntimeState) -> Unit,
        forceState: (RuntimeState) -> Unit,
    ) {
        try {
            val cleared = storage.clearRuntime()
            storage.cleanupTransient()
            if (!cleared) {
                throw java.io.IOException("runtime files could not be deleted")
            }
            onState(RuntimeState.NOT_INSTALLED)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            runCatching { storage.cleanupTransient() }
            forceState(RuntimeState.REPAIR_REQUIRED)
            onEvent(
                RuntimeInstallEvent.Failed(
                    RuntimeState.REPAIR_REQUIRED,
                    "remove failed: ${t.message ?: t.javaClass.simpleName}",
                ),
            )
        }
    }
}
