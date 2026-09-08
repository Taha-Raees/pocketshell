package app.pocketshell.terminal

/**
 * M7.2 P2 — the typed session-lifecycle vocabulary (docs/M7.2-P0-AUDIT.md
 * §10.3 + PART I: the provable state machine, nothing without a signal).
 *
 * Everything in this file is DATA plus pure transition logic. It owns no
 * process, no thread, no clock and no storage; the ONE writer of real
 * lifecycle truth is [TerminalSessionManager] (the M7.1.1 single-model
 * lesson — no second source). UI, [AgentActivityRepository] and any future
 * consumer observe; they never redefine.
 *
 * The per-session machine, exactly as provable from real signals:
 *
 * ```text
 * STARTING   entry exists, the PTY child is NOT forked yet
 *            (TerminalSession forks lazily at the first updateSize —
 *            audit §1.3 fact 1; real signal: setTerminalShellPid)
 *   → RUNNING    the fork signal arrived (real callback, not polling)
 *   → FINISHED   the waiter's real waitpid delivered an exit (audit §1.2);
 *                the exit status is recorded as a structured [ExitStatus]
 * RUNNING    the direct child is alive
 *   → FINISHED   as above
 *   → REMOVED    the user closed the tab — modeled by the entry LEAVING the
 *                manager's list plus a [SessionLifecycleEvent.Removed]
 *                emission; there is no stored "removed" state to corrupt
 * FINISHED   terminal for the entry; a second finish callback is rejected
 *            (idempotent) — the entry stays visible until explicit removal
 * ```
 *
 * Deliberately ABSENT (the audit's honest capability line, Part B/F):
 * command-level running, agent-level completion, waiting-for-input, any
 * idle/inactivity notion. Those have no real signal in this architecture
 * and enter only via future phases with real evidence (P3 scanner /
 * integrations) — never by inference here.
 *
 * Concurrency: every state object is immutable; transitions are pure
 * functions returning [LifecycleTransition.Accepted] or
 * [LifecycleTransition.Rejected] (with the reason), so the manager can log
 * every unexpected delivery instead of silently swallowing it.
 */

/** The observable phase of a session's DIRECT child process (audit §1.3: for guest sessions that is proot). */
enum class SessionPhase {
    /** The entry exists; the PTY child has not been forked yet (lazy fork at the first [com.termux.terminal.TerminalSession.updateSize]). */
    STARTING,

    /** The direct child process is alive. Proven by the fork callback, never inferred. */
    RUNNING,

    /** The direct child exited; the waitpid-proven exit status is recorded in the entry. */
    FINISHED,
}

/**
 * Structured exit information of the direct child process, exactly as the
 * underlying PTY implementation provides it (TerminalSession → JNI.waitFor
 * → waitpid(2), terminal-emulator/src/main/jni/termux.c):
 *
 * - `WIFEXITED(status)`  → `WEXITSTATUS(status)`      → [Exited]
 * - `WIFSIGNALED(status)` → `-WTERMSIG(status)`       → [Signaled]
 *
 * There is deliberately NO "unknown" variant: a FINISHED state can only be
 * produced from a real waitpid delivery, and the JNI contract always yields
 * one of the two shapes above. (The JNI's impossible third branch returns 0
 * — indistinguishable from a genuine exit 0 by the upstream API; documented
 * as an upstream limitation, not modeled as fake information.)
 */
sealed class ExitStatus {

    /** The child exited normally with [code] (0 = success; non-zero per the running program). */
    data class Exited(val code: Int) : ExitStatus()

    /** The child was terminated by signal number [signal] (e.g. 9 = SIGKILL, 15 = SIGTERM). */
    data class Signaled(val signal: Int) : ExitStatus()

    companion object {
        /**
         * Map the raw `JNI.waitFor` result (the waitpid status already folded
         * to a positive exit code or a NEGATED signal number) into the typed
         * value. `raw < 0` encodes `-WTERMSIG`; the kernel's signal domain
         * (1..64) makes the negation overflow-free.
         */
        fun fromWaitpidRaw(raw: Int): ExitStatus =
            if (raw >= 0) Exited(raw) else Signaled(-raw)
    }
}

/**
 * The lifecycle phase + exit status of one session as ONE immutable value —
 * the shape stored on [TerminalSessionManager.SessionEntry].
 *
 * The private constructor makes invalid combinations unrepresentable:
 * a [SessionPhase.FINISHED] state always carries a non-null [exitStatus],
 * and STARTING/RUNNING states can never carry one. There is no stored
 * `isFinished` boolean anywhere in the model; the entry derives it from
 * the phase.
 */
class SessionLifecycleState private constructor(
    val phase: SessionPhase,
    val exitStatus: ExitStatus?,
) {

    /**
     * Apply a real "the PTY child was forked" signal (the upstream
     * `setTerminalShellPid` callback). Valid only from STARTING; the fork
     * happens exactly once per session, so RUNNING/FINISHED sources are
     * rejected with a reason (the manager logs them — never silent).
     */
    fun onProcessStarted(): LifecycleTransition =
        when (phase) {
            SessionPhase.STARTING ->
                LifecycleTransition.Accepted(started())
            SessionPhase.RUNNING ->
                LifecycleTransition.Rejected(this, "process-started signal for an already RUNNING session (duplicate callback)")
            SessionPhase.FINISHED ->
                LifecycleTransition.Rejected(this, "process-started signal for a FINISHED session (out-of-order callback)")
        }

    /**
     * Apply a real process-exit signal: [raw] is the `JNI.waitFor` result
     * (positive = exit code, negative = negated signal).
     *
     * Accepted from RUNNING (the normal path) and also from STARTING — the
     * exit itself proves the fork happened, so a lost/late fork callback
     * must not discard the real exit status (the manager additionally
     * verifies the underlying session reports `!isRunning()` before calling
     * this, so the status is only ever a real waitpid value). A second
     * finish for an already FINISHED session is rejected (idempotent:
     * duplicate callbacks can never corrupt the recorded status).
     */
    fun onProcessFinished(raw: Int): LifecycleTransition =
        when (phase) {
            SessionPhase.RUNNING, SessionPhase.STARTING ->
                LifecycleTransition.Accepted(finished(raw))
            SessionPhase.FINISHED ->
                LifecycleTransition.Rejected(this, "process-exit signal for an already FINISHED session (duplicate callback)")
        }

    override fun equals(other: Any?): Boolean =
        other is SessionLifecycleState && other.phase == phase && other.exitStatus == exitStatus

    override fun hashCode(): Int = 31 * phase.hashCode() + (exitStatus?.hashCode() ?: 0)

    override fun toString(): String =
        "SessionLifecycleState(phase=$phase, exitStatus=$exitStatus)"

    companion object {
        /** The state of a freshly appended entry (no fork yet, no exit info). */
        val STARTING: SessionLifecycleState = SessionLifecycleState(SessionPhase.STARTING, null)
    }

    private fun started(): SessionLifecycleState = SessionLifecycleState(SessionPhase.RUNNING, null)

    private fun finished(raw: Int): SessionLifecycleState =
        SessionLifecycleState(SessionPhase.FINISHED, ExitStatus.fromWaitpidRaw(raw))
}

/**
 * The outcome of a pure lifecycle transition request — [Accepted] carries
 * the next state to store, [Rejected] carries the UNCHANGED current state
 * plus the reason (the manager logs rejections; nothing is swallowed).
 */
sealed class LifecycleTransition {
    abstract val next: SessionLifecycleState

    data class Accepted(override val next: SessionLifecycleState) : LifecycleTransition()

    data class Rejected(
        override val next: SessionLifecycleState,
        val reason: String,
    ) : LifecycleTransition()
}

/**
 * Structured launch identity: WHERE a session's spawn came from. Attached
 * at the real spawn path (every launch path converges on the manager's
 * single factory — audit §3.3) and carried on the entry for its whole
 * process-scoped life. The six kinds are exactly the launch paths that
 * exist in the app today; a future launch path is a compile-time-forced
 * extension (sealed).
 */
sealed class SpawnOrigin {
    /** Host Android shell (`createSession` — Terminal / "+" / terminal-tab defaults). */
    data object Shell : SpawnOrigin()

    /** Plain Alpine guest login shell (`openLinuxShell` — Home "Linux Shell"). */
    data object LinuxShell : SpawnOrigin()

    /** Files' "Open Terminal Here" (`openLinuxShellAt` — guest shell at a directory). */
    data object FilesTerminal : SpawnOrigin()

    /** A registry command app launched from Home (`openCommandApp`), identified by its stable [appId]. */
    data class CommandApp(val appId: String) : SpawnOrigin()

    /** An installed CLI catalog app opened via "Open" (`openCatalogApp`), identified by its stable [entryId]. */
    data class CatalogApp(val entryId: String) : SpawnOrigin()

    /** A user-defined custom tool launched from Home (`openCustomTool`), identified by its stable [toolId]. */
    data class CustomTool(val toolId: String) : SpawnOrigin()
}

/**
 * How an identity claim was established. P2 carried exactly ONE value: the
 * spawn-time launcher metadata. M7.2 P3b adds the two procfs grades as the
 * compile-time-forced extension the ROADMAP names (the P3a structural pin
 * "no PROCFS grades pre-invented" evolves WITH this phase — they are now
 * real, device-verified-shape evidence produced only by the P3b scanner,
 * never by spawn metadata):
 *
 *   - [PROCFS_EXE]     — /proc/<pid>/exe resolved basename equals the
 *     agent's launch token (the strongest process shape: the actual
 *     executable image — single-file binaries, bun-compiled CLIs).
 *   - [PROCFS_CMDLINE] — /proc/<pid>/cmdline carries the token as an exact
 *     argv element (argv[0], or the kernel shebang contract's script path
 *     at argv[1]) — the shape interpreter-hosted CLIs produce (exe reads
 *     the interpreter; the script path rides argv[1]).
 *
 * Confidence is DATA (P0 audit Case C): consumers can see HOW a claim was
 * established and weight it accordingly. Nothing here ever upgrades a
 * LAUNCH_METADATA claim into a process claim or vice versa.
 */
enum class AgentMatchedBy {
    /** The launcher tap itself named the command (registry/custom-tool/catalog metadata at spawn). */
    LAUNCH_METADATA,

    /** The actual executable image matched (readlink /proc/<pid>/exe basename == token). */
    PROCFS_EXE,

    /** The command line matched by exact argv element (argv[0] or the shebang script path at argv[1]). */
    PROCFS_CMDLINE,
}

/**
 * Spawn-time agent identity for sessions launched through a NAMED launcher
 * (command app / custom tool / catalog app). Plain shell sessions carry
 * `null` — no launcher named any command for them.
 *
 * HONESTY BOUNDARY (the audit's Tier-1 line, Part E): this is what the
 * session was LAUNCHED AS — real spawn metadata, not a process observation.
 * It says nothing about which descendant process is actually running inside
 * the guest right now (the direct child is proot, audit §1.3); proving an
 * agent process is the P3b scanner's territory, graded by [AgentMatchedBy].
 */
data class AgentHint(
    /** The launcher's display name (e.g. "Antigravity"). */
    val displayName: String,
    /** The command the launcher requested (single token, or the user's full custom line). */
    val command: String,
    /** How this identity was established. */
    val matchedBy: AgentMatchedBy,
)

/**
 * Typed lifecycle transition events emitted by [TerminalSessionManager]
 * (the ONE owner) exactly at the state-mutation sites. Edge-triggered
 * convenience for downstream consumers ([AgentActivityRepository] and
 * future notification policy); the manager's `sessions` StateFlow remains
 * the authoritative STATE truth — a consumer that misses an edge can always
 * reconcile from it, and every event carries the full identity block so no
 * consumer needs a by-id re-lookup (which could race a concurrent removal).
 */
sealed class SessionLifecycleEvent {
    abstract val sessionId: Long
    abstract val label: String
    abstract val origin: SpawnOrigin
    abstract val agent: AgentHint?

    /** STARTING → RUNNING: the real fork signal was applied. */
    data class Started(
        override val sessionId: Long,
        override val label: String,
        override val origin: SpawnOrigin,
        override val agent: AgentHint?,
    ) : SessionLifecycleEvent()

    /** → FINISHED: the real waitpid exit was applied with its structured status. */
    data class Finished(
        override val sessionId: Long,
        override val label: String,
        override val origin: SpawnOrigin,
        override val agent: AgentHint?,
        val exitStatus: ExitStatus,
    ) : SessionLifecycleEvent()

    /** → REMOVED: the user explicitly closed the tab; [exitStatus] is the recorded status when the session had finished before removal, else null. */
    data class Removed(
        override val sessionId: Long,
        override val label: String,
        override val origin: SpawnOrigin,
        override val agent: AgentHint?,
        val exitStatus: ExitStatus?,
    ) : SessionLifecycleEvent()
}
