package app.pocketshell.terminal

/**
 * M7.2 P3b — the runtime agent-detection evidence model (PURE layer).
 *
 * P3a established the truth boundary at the type level:
 *
 *   Launched ≠ Running ≠ Completed
 *
 * and P3a could honestly prove only the first statement. THIS file is the
 * next level of truth: the vocabulary and the pure decision logic for the
 * second statement — "PocketShell has RUNTIME PROCESS EVIDENCE that agent X
 * is currently running" — derived from the real /proc architecture (the
 * device-verified scanner contract: docs/M7.2-P3B-RUNTIME-DETECTION.md).
 *
 * Everything in this file is DATA plus PURE functions: no clock, no I/O, no
 * Android APIs, no coroutine, no state of its own, and deliberately no
 * reference to [TerminalSessionManager] (the ONE lifecycle authority — the
 * runtime seam lives in RuntimeAgentDetector.kt and only ever OBSERVES the
 * manager's StateFlow). Purity is what makes the truth boundaries below
 * unit-testable without a device and without fakes of Android internals.
 *
 * THE FOUR-STATE CONTRACT (the phase mandate — nothing invented beyond it):
 *
 *   NOT_APPLICABLE  this session makes NO agent claim at all: plain shell,
 *                   known non-agent tool (nano/htop/vim/git/python3),
 *                   custom/unknown launcher, unsupported launch. The
 *                   scanner never runs for these (PART K/L honesty); the
 *                   repository projection attaches this state so consumers
 *                   never have to guess what "absent" means.
 *   UNKNOWN         PocketShell expected an agent identity but the current
 *                   runtime evidence cannot prove a state: the scanner has
 *                   never yet observed the agent process (it may not have
 *                   started yet — or it may have already exited and the
 *                   post-agent prompt is up; both look identical to a
 *                   scanner that never saw it), or the scan itself failed,
 *                   or the evidence is ambiguous. UNKNOWN must NEVER be
 *                   shown as RUNNING.
 *   NOT_RUNNING     the scanner previously held sufficient evidence to
 *                   track the agent (it OBSERVED a matching correlated
 *                   process) and now finds no matching process. This is
 *                   NOT completion: agent disappearance is not success,
 *                   not failure, not "finished" — it is only absence.
 *   RUNNING         actual runtime process evidence exists RIGHT NOW: at
 *                   least one correlated process matched the agent's
 *                   launch token by [AgentMatchedBy.PROCFS_EXE] or
 *                   [AgentMatchedBy.PROCFS_CMDLINE]. The observation
 *                   carries the exact pids + grade that caused the claim —
 *                   the scanner can always answer "what process caused
 *                   this?".
 *
 * Deliberately ABSENT (the same completion/heuristic line P0 drew):
 * COMPLETED, SUCCESS, FINISHED, waiting-for-input, idle — no runtime
 * signal in this architecture can honestly produce them, and P3b adds none.
 */

/** The runtime evidence state of a supported-agent session (the phase contract above). */
enum class AgentRuntimeState {
    NOT_APPLICABLE,
    UNKNOWN,
    NOT_RUNNING,
    RUNNING,
}

/**
 * One process as the scanner sees it — the exact fields real /proc exposes
 * to a same-UID reader (every field proved readable by the P3b controlled
 * experiments; unreadability is modeled as `null`, never guessed).
 *
 * [argv] is /proc/<pid>/cmdline split on NUL (empty when the process is a
 * zombie or has not exec'd yet); [exe] is the readlink of
 * /proc/<pid>/exe (the kernel magic symlink — the actual binary image);
 * [state] is /proc/<pid>/stat field 3 (Z = zombie: exited, not reaped —
 * a zombie is NOT a running agent); [ppid]/[pgrp] are stat fields 4/5.
 */
data class ProcfsProcess(
    val pid: Int,
    val ppid: Int,
    val pgrp: Int,
    val state: Char? = null,
    val argv: List<String>? = null,
    val exe: String? = null,
)

/** One atomic /proc observation: every visible process at one instant. */
data class ProcfsSnapshot(val processes: List<ProcfsProcess>)

/**
 * The scan seam: where the real filesystem meets the pure model. The
 * production reader (RuntimeAgentDetector's HostProcfsReader) reads real
 * /proc; tests inject snapshots. A null return means "the scanner is
 * unavailable" (procfs unreadable, I/O error) — which must yield UNKNOWN,
 * never a downgrade to NOT_RUNNING: missing evidence is not evidence of
 * absence.
 */
fun interface ProcfsReader {
    fun snapshot(): ProcfsSnapshot?
}

/**
 * Exact-token matching of a correlated process against a known agent's
 * launch command head (PART G — no substring matching, ever).
 *
 * The two graded rules, in confidence order, both proved by the P3b
 * controlled experiments on real procfs:
 *
 *  1. [AgentMatchedBy.PROCFS_EXE] — the executable image's basename equals
 *     the token. Covers single-file binaries (bun-compiled kilo, static
 *     musl agy). For interpreter-hosted CLIs the exe reads the INTERPRETER
 *     (node), so this rule does not fire — rule 2 exists for exactly that.
 *  2. [AgentMatchedBy.PROCFS_CMDLINE] — the token appears as an EXACT argv
 *     element: argv[0] (compared whole, or by basename when it is a path),
 *     or argv[1] when it is a path whose basename is the token — the
 *     kernel shebang contract: execve of `#!/usr/bin/env node` script
 *     produces argv = [interpreter, scriptPath, ...], so the invoked name
 *     rides argv[1] (experiment E3a proved the shape).
 *
 * Safety properties (each pinned by AgentRuntimeDetectionTest):
 *   - substring containment NEVER matches: "codex" does not match
 *     "my-codex-wrapper", "codex-helper", "something-codex";
 *   - the intermediate chain shell's argv carries the whole command line
 *     as ONE element ("kilo; exec sh -l") — whole-element comparison
 *     cannot hit it (experiment E2);
 *   - a blank token can never match anything (no token, no claim).
 *
 * Residual limitation, recorded honestly: a long-lived CORRELATED process
 * whose argv[1] is exactly a path named <token> (e.g. an editor opened on
 * a directory named after the agent) would match rule 2. Correlation
 * already restricts candidates to the agent session's own process tree;
 * the grade records which rule fired so a future phase can tighten.
 */
object AgentProcessMatcher {

    /** Path basename (POSIX): everything after the last '/'. */
    fun basename(path: String): String = path.substringAfterLast('/')

    /** The command HEAD of a launch command line ("kilo --yolo" -> "kilo"). */
    fun commandToken(command: String): String = command.trim().substringBefore(' ')

    /**
     * Match [process] against [token]. Returns the strongest grade or null
     * (no claim). Never throws; unreadable fields simply do not match.
     */
    fun match(token: String, process: ProcfsProcess): AgentMatchedBy? {
        if (token.isBlank()) return null
        // Rule 1 — the actual executable image.
        val exe = process.exe
        if (exe != null && basename(exe) == token) return AgentMatchedBy.PROCFS_EXE
        // Rule 2 — exact argv elements.
        val argv = process.argv ?: return null
        if (argv.isEmpty()) return null
        val argv0 = argv[0]
        if (argv0 == token || (argv0.contains('/') && basename(argv0) == token)) {
            return AgentMatchedBy.PROCFS_CMDLINE
        }
        if (argv.size > 1) {
            val scriptPath = argv[1]
            if (scriptPath.contains('/') && basename(scriptPath) == token) {
                return AgentMatchedBy.PROCFS_CMDLINE
            }
        }
        return null
    }
}

/**
 * Process-tree correlation (PART F — the anti-false-positive core).
 *
 * A process may be attributed to a session ONLY through the session's
 * fork-proven correlation root (the direct child recorded on the real fork
 * signal — proot for every guest session). Two domains, both derived from
 * that root, and both SAFE by kernel semantics:
 *
 *  1. PPID-chain descent: walking parent links from the candidate reaches
 *     the root. Covers descendants that left the original process group
 *     (job control, worker pools that setpgid themselves).
 *  2. Process-group membership: pgrp == rootPid. The root called setsid()
 *     (termux JNI child), so it IS the group leader; membership requires
 *     descending from it or being explicitly moved in — no unrelated
 *     process can hold the group. Covers orphans: a child whose parent
 *     exited reparents to init (its ppid chain is LOST) but KEEPS its
 *     process group (experiment E4a proved both halves).
 *
 * The union is therefore strictly stronger than either domain alone; the
 * only attribution blind spot is a process that BOTH reparented AND left
 * the group (double escape, experiment E4b) — documented, never faked.
 *
 * A random same-name process elsewhere on the device belongs to a
 * different tree (different chain, different group) and can NEVER satisfy
 * either domain — the required "unrelated process must not produce
 * RUNNING" rule holds structurally, not by policy.
 */
object AgentDescendantCorrelator {

    /**
     * All pids attributed to the session rooted at [rootPid] — the root
     * itself EXCLUDED (it is the correlation anchor, not an agent process).
     * A candidate whose ancestry contains a cycle or a dead end simply
     * fails to reach the root and is not attributed.
     */
    fun correlatedPids(snapshot: ProcfsSnapshot, rootPid: Int): Set<Int> {
        if (rootPid <= 0) return emptySet()
        val byPid = snapshot.processes.associateBy { it.pid }
        val result = mutableSetOf<Int>()
        for (process in snapshot.processes) {
            if (process.pid == rootPid) continue
            // Domain 2: group membership under the root's group.
            if (process.pgrp == rootPid) {
                result += process.pid
                continue
            }
            // Domain 1: ppid-chain descent, cycle-guarded.
            var cursor = process.ppid
            var steps = 0
            while (cursor > 0 && steps <= byPid.size) {
                if (cursor == rootPid) {
                    result += process.pid
                    break
                }
                val parent = byPid[cursor] ?: break // parent invisible: dead end
                cursor = parent.ppid
                steps++
            }
        }
        return result
    }
}

/**
 * The pure scan step: fold one [ProcfsSnapshot] plus the previous
 * observations into the next observations, for the eligible sessions.
 *
 * An "eligible" session is a LIVE (not FINISHED) session whose launch
 * identity is exactly [LaunchIdentity.KnownAgent] (PART K/L: the scanner
 * activates for known agents only) with its correlation root. The runtime
 * seam (RuntimeAgentDetector) extracts eligibility from the manager's
 * authoritative state; this function consumes the already-extracted truth.
 *
 * Per-session decision table (pinned by AgentRuntimeDetectionTest):
 *
 *   scan failed (snapshot null)          -> UNKNOWN (history kept: missing
 *                                           evidence is not absence)
 *   rootPid <= 0 (not forked yet)        -> UNKNOWN (nothing to correlate)
 *   a correlated process matched         -> RUNNING (+ evidence pids/grade)
 *   no match, never observed running     -> UNKNOWN (cannot distinguish
 *                                           "not started yet" from
 *                                           "already exited")
 *   no match, previously observed, its
 *   pids all gone (or zombies)           -> NOT_RUNNING (proven absence)
 *   no match, previously observed, its
 *   pids still alive but unmatched
 *   (unreadable/changed shape)           -> UNKNOWN (ambiguous evidence —
 *                                           never a false absence)
 */
object AgentRuntimeDetection {

    /** A session the scanner may observe, extracted from the manager's authoritative state. */
    data class EligibleAgentSession(
        val sessionId: Long,
        /** The fork-proven correlation root (proot for guest sessions); 0 while STARTING. */
        val rootPid: Int,
        /** The known agent's launch command head (the exact match token). */
        val token: String,
    )

    /** WHAT was seen: the pids and the match grade that caused a RUNNING claim. */
    data class ProcessEvidence(
        val pids: List<Int>,
        val grade: AgentMatchedBy,
        val observedAtMs: Long,
    )

    /** One session's runtime observation — the unit published to consumers. */
    data class Observation(
        val sessionId: Long,
        val state: AgentRuntimeState,
        /** Non-null exactly when the CURRENT claim rests on process evidence. */
        val evidence: ProcessEvidence?,
        /** True once ANY scan has proven the agent running in this session. */
        val everObservedRunning: Boolean,
        val updatedAtMs: Long,
    )

    /** Strongest grade across a set of matches (PROCFS_EXE outranks PROCFS_CMDLINE). */
    private fun strongest(grades: Collection<AgentMatchedBy>): AgentMatchedBy =
        if (AgentMatchedBy.PROCFS_EXE in grades) AgentMatchedBy.PROCFS_EXE else AgentMatchedBy.PROCFS_CMDLINE

    /**
     * Compute the next observations. Pure: identical inputs always produce
     * identical outputs; no state is kept anywhere.
     */
    fun compute(
        eligible: List<EligibleAgentSession>,
        previous: Map<Long, Observation>,
        snapshot: ProcfsSnapshot?,
        nowMs: Long,
    ): Map<Long, Observation> {
        if (snapshot == null) {
            // Scanner unavailable: current state is UNKNOWN for every
            // eligible session. Previous history (evidence, everObserved)
            // is PRESERVED for explanation, but the published state must
            // not claim a liveness the failed scan cannot support.
            return eligible.associate { session ->
                val prev = previous[session.sessionId]
                session.sessionId to Observation(
                    sessionId = session.sessionId,
                    state = AgentRuntimeState.UNKNOWN,
                    evidence = prev?.evidence,
                    everObservedRunning = prev?.everObservedRunning ?: false,
                    updatedAtMs = nowMs,
                )
            }
        }
        val byPid = snapshot.processes.associateBy { it.pid }
        return eligible.associate { session ->
            session.sessionId to observeSession(session, previous[session.sessionId], snapshot, byPid, nowMs)
        }
    }

    private fun observeSession(
        session: EligibleAgentSession,
        prev: Observation?,
        snapshot: ProcfsSnapshot,
        byPid: Map<Int, ProcfsProcess>,
        nowMs: Long,
    ): Observation {
        val everObserved = prev?.everObservedRunning ?: false
        // Not forked yet: no correlation root, nothing provable.
        if (session.rootPid <= 0) {
            return Observation(session.sessionId, AgentRuntimeState.UNKNOWN, prev?.evidence, everObserved, nowMs)
        }
        val correlated = AgentDescendantCorrelator.correlatedPids(snapshot, session.rootPid)
        val matches = LinkedHashMap<Int, AgentMatchedBy>()
        for (pid in correlated) {
            val process = byPid[pid] ?: continue
            if (process.state == 'Z') continue // zombie: exited, not a running agent
            val grade = AgentProcessMatcher.match(session.token, process)
            if (grade != null) matches[pid] = grade
        }
        if (matches.isNotEmpty()) {
            val bestGrade = strongest(matches.values)
            return Observation(
                sessionId = session.sessionId,
                state = AgentRuntimeState.RUNNING,
                evidence = ProcessEvidence(
                    pids = matches.keys.toList().sorted(),
                    grade = bestGrade,
                    observedAtMs = nowMs,
                ),
                everObservedRunning = true,
                updatedAtMs = nowMs,
            )
        }
        if (!everObserved) {
            return Observation(session.sessionId, AgentRuntimeState.UNKNOWN, null, false, nowMs)
        }
        // Previously observed running; no match now. Distinguish proven
        // absence (the previously-matched pids are gone — or zombies) from
        // ambiguity (they still exist but can no longer be matched).
        val prevPids = prev?.evidence?.pids.orEmpty().toSet()
        val stillAliveUnmatched = prevPids.any { pid ->
            val process = byPid[pid]
            process != null && process.state != 'Z'
        }
        return if (stillAliveUnmatched) {
            Observation(session.sessionId, AgentRuntimeState.UNKNOWN, prev?.evidence, true, nowMs)
        } else {
            Observation(session.sessionId, AgentRuntimeState.NOT_RUNNING, null, true, nowMs)
        }
    }

    /**
     * Drop observations whose sessions are no longer eligible (removed,
     * finished, or no longer claiming a known-agent identity). A session
     * leaving eligibility makes NO statement about its agent — the session
     * layer's own lifecycle truth (FINISHED/REMOVED) owns that story.
     */
    fun prune(
        observations: Map<Long, Observation>,
        eligibleSessionIds: Set<Long>,
    ): Map<Long, Observation> =
        observations.filterKeys { it in eligibleSessionIds }
}
