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
 *
 * M7.2 P9: [startTime] is /proc/<pid>/stat field 22 — the kernel's
 * boot-tick birth stamp. It is the pid-reuse guard for the launch-record
 * anchor (AgentLaunchRecords): a recorded pid names THE agent only while
 * its birth stamp matches; a recycled pid fails the comparison and the
 * anchor is honestly gone. Null when unreadable (never guessed).
 */
data class ProcfsProcess(
    val pid: Int,
    val ppid: Int,
    val pgrp: Int,
    val state: Char? = null,
    val argv: List<String>? = null,
    val exe: String? = null,
    val startTime: Long? = null,
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
 * M7.2 P9 EVOLUTION — TWO ELIGIBILITY CLASSES (the Part-B root-cause fix:
 * the cwd-dependent Kilo detection discrepancy was NOT directory-dependent
 * at all — it was the old eligibility line, which scanned ONLY sessions
 * whose SPAWN ORIGIN was a registry command app, leaving every agent a
 * user launched from a plain Terminal session structurally invisible):
 *
 *   - PREDICTED: a LIVE session whose launch identity resolves to
 *     [LaunchIdentity.KnownAgent]. The session's own launch token decides
 *     (spawn truth keeps precedence — the session was launched AS this
 *     agent), exactly as P3b scanned it.
 *   - DISCOVERED: every other LIVE GUEST session (plain Linux shells,
 *     Files' Open-Terminal-Here, catalog tools, custom tools — the
 *     detector's seam owns the exact extraction). Their process tree is
 *     scanned against the WHOLE registry token set; the matched token
 *     RESOLVES the agent (the claim names the registry entry the process
 *     evidence actually found — never the session's name, never a guess).
 *     Host-side plain shells have no guest tree and stay excluded.
 *
 * The four-state contract is UNCHANGED — UNKNOWN must never read as
 * RUNNING, NOT_RUNNING is proven absence only — and so is the
 * per-session decision table (pinned by AgentRuntimeDetectionTest):
 *
 *   scan failed (snapshot null)          -> UNKNOWN (history kept: missing
 *                                           evidence is not absence)
 *   rootPid <= 0 (not forked yet)        -> UNKNOWN (nothing to correlate)
 *   a correlated process matched         -> RUNNING (+ evidence pids/grade)
 *   the launch-record anchor matched     -> RUNNING (+ evidence, grade
 *                                           LAUNCH_ANCHOR — the exact
 *                                           pid the launch recorded,
 *                                           pid-reuse-proofed by the
 *                                           starttime comparison)
 *   no match, never observed running     -> UNKNOWN (cannot distinguish
 *                                           "not started yet" from
 *                                           "already exited")
 *   no match, previously observed, its
 *   pids all gone (or zombies)           -> NOT_RUNNING (proven absence)
 *   no match, previously observed, its
 *   pids still alive but unmatched
 *   (unreadable/changed shape)           -> UNKNOWN (ambiguous evidence —
 *                                           never a false absence)
 *
 * The anchor (AgentLaunchRecords) EXTENDS the correlation domain: the
 * recorded pid is credited even when a same-tick tree walk misses it
 * (fork/exec races), validated by pid + startTime; it can never claim a
 * process for the WRONG session — the consumer binds each record to the
 * session whose launch produced it, and the record's agent token must
 * still match the claim being made.
 */
object AgentRuntimeDetection {

    /**
     * A session the scanner may observe, extracted from the manager's
     * authoritative state.
     *
     * M7.2 P9: [predetermined] carries the spawn-truth identity for
     * registry-launcher sessions (the token is their command head);
     * [discovery] marks the guest sessions with NO launch identity, whose
     * tree is scanned against the whole registry token set. The two are
     * mutually exclusive by construction (the detector's seam guarantees
     * it); [recordPath] is the session's launch-record channel (null when
     * the launch carried no record — plain chains).
     */
    data class EligibleAgentSession(
        val sessionId: Long,
        /** The fork-proven correlation root (proot for guest sessions); 0 while STARTING. */
        val rootPid: Int,
        /** The predetermined match token (registry command head); empty for discovery sessions. */
        val token: String,
        /** The spawn-truth identity (predicted sessions only); null for discovery sessions. */
        val predetermined: LaunchIdentity.KnownAgent? = null,
        /** True when the session has no launch identity and resolves agents by discovery. */
        val discovery: Boolean = false,
        /** The launch-record channel file (host path); null when the launch carried no records. */
        val recordPath: String? = null,
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
        /**
         * M7.2 P9 — the RESOLVED agent identity this observation names: the
         * spawn-truth identity for predicted sessions, the registry entry
         * the process evidence actually matched for discovery sessions
         * (null while nothing has been resolved — no claim names an agent
         * the evidence never named). The engine, repository and Home
         * claims consume THIS as the agent identity, so a discovered
         * session states exactly the registry name it proved.
         */
        val agent: LaunchIdentity.KnownAgent? = null,
        /**
         * M7.2 P9 — the launch channel's recorded EXIT FACT for the
         * current generation (the real exit status of the launched
         * process). A fact, never an interpretation: no consumer may map
         * it to success/failure/completion (PART P).
         */
        val lastExit: AgentLaunchRecords.ExitRecord? = null,
    )

    /**
     * Strongest grade across a set of matches. M7.2 P9 order:
     * LAUNCH_ANCHOR (the launch itself named the exact pid) outranks
     * PROCFS_EXE (the actual executable image), which still outranks
     * PROCFS_CMDLINE (the argv element).
     */
    private fun strongest(grades: Collection<AgentMatchedBy>): AgentMatchedBy =
        when {
            AgentMatchedBy.LAUNCH_ANCHOR in grades -> AgentMatchedBy.LAUNCH_ANCHOR
            AgentMatchedBy.PROCFS_EXE in grades -> AgentMatchedBy.PROCFS_EXE
            else -> AgentMatchedBy.PROCFS_CMDLINE
        }

    /**
     * Compute the next observations. Pure: identical inputs always produce
     * identical outputs; no state is kept anywhere.
     *
     * M7.2 P9: [records] carries each session's parsed launch-record
     * channel (the anchor + exit fact), read by the detector's seam; the
     * discovery token set defaults to the registry's command heads so the
     * function stays parameter-pure for tests.
     */
    fun compute(
        eligible: List<EligibleAgentSession>,
        previous: Map<Long, Observation>,
        snapshot: ProcfsSnapshot?,
        nowMs: Long,
        records: Map<Long, AgentLaunchRecords.SessionRecords> = emptyMap(),
        discoveryTokens: List<String> = AgentDiscoveryTokens.registryTokens(),
    ): Map<Long, Observation> {
        if (snapshot == null) {
            // Scanner unavailable: current state is UNKNOWN for every
            // eligible session. Previous history (evidence, everObserved,
            // the resolved agent, the exit fact) is PRESERVED for
            // explanation, but the published state must not claim a
            // liveness the failed scan cannot support.
            return eligible.associate { session ->
                val prev = previous[session.sessionId]
                session.sessionId to Observation(
                    sessionId = session.sessionId,
                    state = AgentRuntimeState.UNKNOWN,
                    evidence = prev?.evidence,
                    everObservedRunning = prev?.everObservedRunning ?: false,
                    updatedAtMs = nowMs,
                    agent = resolvedAgent(session, prev),
                    lastExit = prev?.lastExit,
                )
            }
        }
        val byPid = snapshot.processes.associateBy { it.pid }
        return eligible.associate { session ->
            session.sessionId to observeSession(
                session = session,
                prev = previous[session.sessionId],
                snapshot = snapshot,
                byPid = byPid,
                nowMs = nowMs,
                record = records[session.sessionId],
                discoveryTokens = discoveryTokens,
            )
        }
    }

    /**
     * The agent identity an observation may name WITHOUT new process
     * evidence: the spawn-truth identity for predicted sessions; for
     * discovery sessions, the agent a PREVIOUS scan resolved (kept for
     * story continuity — a discovery session that stops matching keeps
     * naming the agent it had proven, until the session ends).
     */
    private fun resolvedAgent(
        session: EligibleAgentSession,
        prev: Observation?,
    ): LaunchIdentity.KnownAgent? = session.predetermined ?: prev?.agent

    /**
     * The candidate token set for one session: predicted sessions match
     * their own launch token (spawn truth keeps precedence); discovery
     * sessions match the WHOLE registry token set (the process evidence
     * resolves the agent).
     */
    private fun tokensFor(session: EligibleAgentSession, discoveryTokens: List<String>): List<String> =
        if (!session.discovery && session.token.isNotBlank()) listOf(session.token) else discoveryTokens

    private fun observeSession(
        session: EligibleAgentSession,
        prev: Observation?,
        snapshot: ProcfsSnapshot,
        byPid: Map<Int, ProcfsProcess>,
        nowMs: Long,
        record: AgentLaunchRecords.SessionRecords?,
        discoveryTokens: List<String>,
    ): Observation {
        val everObserved = prev?.everObservedRunning ?: false
        // Not forked yet: no correlation root, nothing provable.
        if (session.rootPid <= 0) {
            return Observation(
                session.sessionId, AgentRuntimeState.UNKNOWN, prev?.evidence, everObserved, nowMs,
                resolvedAgent(session, prev), prev?.lastExit,
            )
        }
        val correlated = AgentDescendantCorrelator.correlatedPids(snapshot, session.rootPid)
        val tokens = tokensFor(session, discoveryTokens)
        val matches = LinkedHashMap<Int, AgentMatchedBy>()
        for (pid in correlated) {
            val process = byPid[pid] ?: continue
            if (process.state == 'Z') continue // zombie: exited, not a running agent
            for (token in tokens) {
                val grade = AgentProcessMatcher.match(token, process)
                if (grade != null) {
                    matches[pid] = grade
                    break // one pid claims once per scan; the strongest token wins below
                }
            }
        }
        // M7.2 P9 — the launch-record ANCHOR: the exact pid the launch
        // recorded, validated against the live snapshot (alive, non-zombie,
        // and the birth stamp matches — a recycled pid fails the startTime
        // comparison and the anchor is honestly gone) AND against the
        // session's own correlation domain (a record can only ever pinpoint
        // a process inside THIS session's tree — a forged or cross-session
        // record cannot claim a foreign process; the PART T boundary). The
        // anchor pinpoints WHICH tree process is the agent and upgrades the
        // grade even when exe/cmdline are unreadable.
        val anchor = record?.launch
        val anchorPid = anchor?.let { a ->
            byPid[a.pid]?.takeIf { p ->
                p.state != 'Z' && p.startTime != null && p.startTime == a.startTicks
            }?.pid?.takeIf { it in correlated }
        }
        if (anchorPid != null && anchorPid !in matches) {
            matches[anchorPid] = AgentMatchedBy.LAUNCH_ANCHOR
        }
        // Deterministic resolution order for discovery: the anchor's own
        // token first (the launch named it), then strongest-grade matches,
        // then lowest pid. Predicted sessions resolve to their own identity.
        if (matches.isNotEmpty()) {
            val bestGrade = strongest(matches.values)
            val resolvedAgent = session.predetermined
                ?: anchor?.let { a -> AgentDiscoveryTokens.byToken(a.agent) }
                ?: matches.entries.firstNotNullOfOrNull { (pid, _) ->
                    byPid[pid]?.let { p -> AgentDiscoveryTokens.match(p) }
                }
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
                agent = resolvedAgent ?: prev?.agent,
                lastExit = prev?.lastExit,
            )
        }
        val prevAgent = resolvedAgent(session, prev)
        if (!everObserved) {
            return Observation(session.sessionId, AgentRuntimeState.UNKNOWN, null, false, nowMs, prevAgent, prev?.lastExit)
        }
        // Previously observed running; no match now. Distinguish proven
        // absence (the previously-matched pids are gone — or zombies) from
        // ambiguity (they still exist but can no longer be matched).
        val prevPids = prev?.evidence?.pids.orEmpty().toSet()
        val stillAliveUnmatched = prevPids.any { pid ->
            val process = byPid[pid]
            process != null && process.state != 'Z'
        }
        // The exit FACT rides the observation when the launch channel
        // recorded it (the real status of the launched process) — attached
        // when the anchor pid is gone, exactly the proven-absence arm.
        val exitFact = if (stillAliveUnmatched) {
            prev?.lastExit
        } else {
            record?.exits?.lastOrNull() ?: prev?.lastExit
        }
        return if (stillAliveUnmatched) {
            Observation(session.sessionId, AgentRuntimeState.UNKNOWN, prev?.evidence, true, nowMs, prevAgent, exitFact)
        } else {
            Observation(session.sessionId, AgentRuntimeState.NOT_RUNNING, null, true, nowMs, prevAgent, exitFact)
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

/**
 * M7.2 P9 — the DISCOVERY token set: the registry's command heads, the
 * only tokens a guest session's process tree may be discovered by.
 *
 * This is the boundary that keeps discovery honest: a plain Terminal
 * session has no launch identity, so the ONLY agent claim its tree can
 * produce is a process whose exe/argv exactly matches a REGISTRY command
 * head (the same curated set the Home launcher itself resolves against).
 * A process named after nothing in the registry is never claimed, the
 * session's own label/title is never consulted, and substring matching
 * stays forbidden (the same exact-token rules as [AgentProcessMatcher]).
 */
object AgentDiscoveryTokens {

    private val apps = app.pocketshell.apps.CommandAppCatalog.registry

    /** The registry's command heads, in registry order (deterministic scan order). */
    fun registryTokens(): List<String> = apps.map { it.launchCommand.first() }

    /** The registry identity a matched token resolves to (null for unknown tokens). */
    fun byToken(token: String): LaunchIdentity.KnownAgent? =
        apps.firstOrNull { it.launchCommand.first() == token }?.let { app ->
            LaunchIdentity.KnownAgent(
                launcherId = app.id,
                displayName = app.displayName,
                command = app.launchCommand.joinToString(" "),
            )
        }

    /**
     * The first registry entry (registry order) whose token matches
     * [process] — the deterministic discovery resolution for a match the
     * anchor did not name.
     */
    fun match(process: ProcfsProcess): LaunchIdentity.KnownAgent? =
        apps.asSequence()
            .map { it to it.launchCommand.first() }
            .firstOrNull { (_, token) -> AgentProcessMatcher.match(token, process) != null }
            ?.let { (app, token) ->
                LaunchIdentity.KnownAgent(
                    launcherId = app.id,
                    displayName = app.displayName,
                    command = app.launchCommand.joinToString(" "),
                )
            }
}
