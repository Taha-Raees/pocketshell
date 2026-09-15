package app.pocketshell.terminal

/**
 * J1 graceful-close policy (Phase 4, docs/PHASE-4-TERMINAL-AUDIT.md §J1) —
 * pure decision logic, no Android/process access, so the exact contract is
 * unit-test-pinned. [TerminalSessionManager] wires it to the real session
 * (signals via `Os.kill`, fallback via `finishIfRunning`, scheduling via the
 * main handler).
 *
 * The contract, in order:
 *
 *  1. Resolve the terminal's FOREGROUND process group (kernel state via
 *     TIOCGPGRP on the session's own PTY master — never a pid scan, never
 *     /proc polling).
 *  2. If it is a real group (> 1), send SIGCONT + SIGHUP to it — exactly the
 *     pair the kernel itself delivers when a PTY master closes. This
 *     terminates the terminal's foreground work (the shell, or a foreground
 *     job) through normal terminal semantics.
 *  3. Schedule ONE bounded fallback: after [graceMs], if the session's direct
 *     child (proot) is still alive, force-finish it with the existing
 *     SIGKILL path. Deliberately detached descendants (own session, e.g.
 *     `setsid`) are never enumerated and never signaled — they survive the
 *     SIGKILL of the tracer (device-proven 2026-09-14, SM-F711B + SM-T870).
 *  4. If the direct child exits inside the grace window, the fallback is
 *     cancelled — no second signal is ever sent.
 *
 * Blast-radius invariant (test-pinned): the ONLY signals this policy ever
 * sends go to the resolved foreground process group (as a negative pid).
 * It never signals the direct child, pid 1, itself, or any scanned pid.
 * Multi-session isolation follows from the kernel state of ONE session's
 * OWN terminal: another session's foreground group can never be returned
 * here.
 *
 * NOT the owner of removal: the manager removes the session entry
 * immediately (the UI never blocks); this controller only owns the
 * signal/fallback timeline.
 */
class GracefulSessionCloseController(
    private val graceMs: Long,
    private val signaler: (pid: Int, signal: Int) -> Unit,
    private val scheduler: Scheduler,
) {

    /** The signals a graceful close delivers, in order (test-pinned). */
    object Signals {
        const val SIGCONT = 18
        const val SIGHUP = 1
    }

    /** The one SIGKILL fallback may need to force-finish the target. */
    interface Target {
        /** Kernel fg process group of the session's controlling terminal, or <= 0 if unresolvable. */
        fun foregroundProcessGroup(): Int

        /** Whether the session's direct child is still alive. */
        fun isDirectChildAlive(): Boolean

        /** The existing force-finish path (SIGKILL to the direct child). */
        fun forceFinishDirectChild()
    }

    /** Cancellable delayed-scheduling seam (the manager passes its main handler). */
    interface Scheduler {
        fun postDelayed(runnable: Runnable, delayMs: Long): Cancellation
    }

    fun interface Cancellation {
        fun cancel()
    }

    private val pending = HashMap<Long, Pending>()

    private class Pending(val target: Target, val cancellation: Cancellation)

    /**
     * Begin a graceful close for [id]. Returns true when the graceful signal
     * was delivered (fallback scheduled); false when the terminal's foreground
     * group could not be resolved or signaled, in which case the CALLER must
     * run the existing immediate force-finish (today's behavior) instead.
     */
    fun requestClose(id: Long, target: Target): Boolean {
        cancelPending(id)
        val pgrp = try {
            target.foregroundProcessGroup()
        } catch (_: Exception) {
            -1
        }
        if (pgrp <= 1) return false
        val delivered = try {
            signaler(-pgrp, Signals.SIGCONT) // wake a stopped foreground job so SIGHUP is deliverable
            signaler(-pgrp, Signals.SIGHUP)
            true
        } catch (_: Exception) {
            false
        }
        if (delivered) {
            val fallback = Runnable {
                pending.remove(id) ?: return@Runnable
                if (target.isDirectChildAlive()) target.forceFinishDirectChild()
            }
            pending[id] = Pending(target, scheduler.postDelayed(fallback, graceMs))
        }
        return delivered
    }

    /**
     * The session's direct child exited (the manager's onSessionFinished
     * path) — the fallback must not fire afterwards. Safe to call for
     * unknown ids and multiple times.
     */
    fun onProcessExited(id: Long) {
        cancelPending(id)
    }

    /** Number of closes currently inside their grace window (tests/telemetry). */
    fun pendingCount(): Int = pending.size

    private fun cancelPending(id: Long) {
        pending.remove(id)?.cancellation?.cancel()
    }
}
