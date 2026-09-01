package app.pocketshell.packages

import app.pocketshell.runtime.RuntimeProcessLauncher
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Executes a proot [RuntimeProcessLauncher.LaunchSpec] as a dedicated
 * background guest process (M2.4 Option A) — NOT a PTY session:
 *
 * - package operations must never touch (or type into) a user-visible
 *   terminal session;
 * - apk is non-interactive when stdin is not a tty, and its plain text
 *   progress goes to stdout/stderr, which we capture;
 * - the SAME proot binary, loader, argv shape and environment contract as the
 *   M2.3 Linux Shell are reused (single exec infrastructure — no duplicate
 *   proot logic).
 *
 * [GuestCommandRunner.start] must not run on the main thread (process + pipe IO).
 */
interface GuestCommandRunner {
    fun start(spec: RuntimeProcessLauncher.LaunchSpec): GuestProcess
}

/** A started guest process: wait for completion or destroy it on cancel. */
interface GuestProcess {
    /**
     * Blocks until exit. [timeoutMs] = null means no timeout; on timeout the
     * process is destroyed and the result reports exitCode = null with a
     * timeout error. Both captured streams are drained either way.
     */
    fun waitFor(timeoutMs: Long?): ExecResult

    /** Forcefully terminate (user cancel / timeout). Idempotent. */
    fun destroy()
}

data class ExecResult(
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val error: String? = null,
) {
    val success: Boolean get() = exitCode == 0 && error == null
}

/** Real implementation over java.lang.ProcessBuilder (fork/exec of proot). */
class ProcessBuilderGuestCommandRunner : GuestCommandRunner {

    override fun start(spec: RuntimeProcessLauncher.LaunchSpec): GuestProcess {
        val process = ProcessBuilder().apply {
            directory(File(spec.workingDirectory))
            environment().clear()
            for (entry in spec.environment) {
                val i = entry.indexOf('=')
                require(i > 0) { "malformed environment entry: $entry" }
                environment().put(entry.substring(0, i), entry.substring(i + 1))
            }
            // argv[0] is already part of the spec contract; ProcessBuilder
            // wants the full command with the executable first — identical.
            command(listOf(spec.executable) + spec.arguments.drop(1))
            // stdin closed: apk must never wait for input it cannot get
            redirectInput(File("/dev/null"))
        }.start()
        return ProcessBuilderGuestProcess(process)
    }
}

private class ProcessBuilderGuestProcess(
    private val process: Process,
) : GuestProcess {

    @Volatile private var destroyed = false

    override fun waitFor(timeoutMs: Long?): ExecResult {
        // Drain both pipes on worker threads — they fill independently and a
        // full pipe would deadlock the child.
        val stdoutResult = FutureTask { runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("") }
        val stderrResult = FutureTask { runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("") }
        // Daemon: a cancelled process may leave an orphan holding the pipe —
        // the reader must never keep a JVM alive for it.
        Thread(stdoutResult, "pkg-stdout").apply { isDaemon = true }.start()
        Thread(stderrResult, "pkg-stderr").apply { isDaemon = true }.start()

        val deadlineMs = timeoutMs?.let { System.currentTimeMillis() + it }
        // Poll instead of a bare blocking waitFor(): a destroy() from another
        // thread (user cancel / timeout) must unblock this loop promptly, and
        // a blocking waitFor() in one thread is NOT reliably woken when the
        // child is reaped by a concurrent waitFor elsewhere on this JVM
        // (observed on sandbox JDK 21: the waiter hung the full `sleep` even
        // though destroy + reaping had completed milliseconds earlier).
        while (process.isAlive()) {
            if (destroyed) break
            if (deadlineMs != null && System.currentTimeMillis() >= deadlineMs) break
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        val timedOut = deadlineMs != null &&
            System.currentTimeMillis() >= deadlineMs && process.isAlive()
        if (timedOut) destroy()
        // On cancel/timeout the killed process may have orphaned children that
        // inherited the pipe FDs — and a close() does NOT wake a thread
        // blocked in read() (POSIX close-vs-read race). Never block on EOF
        // then: take whatever drained within a small grace and report the
        // real failure. Normal completion has no orphans → full EOF read.
        val stdout = drained(stdoutResult, if (destroyed) CANCELLED_DRAIN_GRACE_MS else null)
        val stderr = drained(stderrResult, if (destroyed) CANCELLED_DRAIN_GRACE_MS else null)
        val exitCode: Int? = if (process.isAlive()) null else runCatching { process.exitValue() }.getOrNull()
        return if (timedOut) {
            ExecResult(
                exitCode = null,
                stdout = stdout,
                stderr = stderr,
                error = "guest process did not finish within ${timeoutMs}ms and was terminated",
            )
        } else {
            ExecResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        }
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        runCatching { process.destroy() }
        // Belt-and-braces escalation after a grace period; the waitFor poll
        // loop observes termination either way. Daemon: must never block JVM exit.
        Thread {
            runCatching {
                Thread.sleep(DESTROY_GRACE_MS)
                if (process.isAlive()) process.destroyForcibly()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun drained(future: FutureTask<String>, graceMs: Long?): String = try {
        if (graceMs != null) future.get(graceMs, TimeUnit.MILLISECONDS) else future.get()
    } catch (_: Exception) {
        // TimeoutException (orphan holds the pipe) / partial read on cancel —
        // the result still carries the honest error + exit state.
        ""
    }

    companion object {
        private const val POLL_INTERVAL_MS = 50L
        private const val DESTROY_GRACE_MS = 3_000L
        private const val CANCELLED_DRAIN_GRACE_MS = 500L
    }
}
