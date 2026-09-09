package app.pocketshell.terminal

import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * M7.2 P3b — the runtime agent-detection SEAM (the evidence provider).
 *
 * Relationship to the ONE lifecycle authority (PART I): this object is an
 * OBSERVER of [TerminalSessionManager]. It reads the manager's
 * authoritative `sessions` StateFlow, extracts the sessions whose launch
 * identity is exactly [LaunchIdentity.KnownAgent], scans real /proc for
 * their correlated process trees, and publishes graded evidence. It NEVER
 * mutates manager state, never drives lifecycle transitions, never posts
 * notifications, and never touches the terminal emulator. The manager
 * wakes it (spawn -> ensureStarted) purely so the scanner exists while
 * sessions exist — the wake carries no data and grants no authority.
 *
 * The scan contract, end to end:
 *
 *   TerminalSessionManager.sessions  (authoritative lifecycle + identity)
 *        |  observe (read-only)
 *        v
 *   eligibility: LIVE session && LaunchIdentity.KnownAgent  (PART K/L:
 *        plain shells, KnownNonAgentTool, CustomOrUnknown never activate
 *        the scanner — no identity, no scan, no claim)
 *        v
 *   HostProcfsReader.snapshot()  — one atomic /proc pass over the app's
 *        own-UID processes (hidepid=2 makes foreign processes invisible;
 *        the getCwd() precedent already proves same-UID procfs reads)
 *        v
 *   AgentRuntimeDetection.compute  (PURE: correlation + matching + the
 *        four-state contract)  ->  observations StateFlow
 *        v
 *   AgentActivityRepository.runtimeActivities  (the derived read model)
 *
 * Polling discipline (PART H): a 2-second tick that exists ONLY while at
 * least one eligible session exists — the monitor parks (no timers armed,
 * no scanning) when the eligibility set is empty and wakes whenever it
 * becomes non-empty again. No AlarmManager, no WakeLock, no work while
 * backgrounded beyond the app's own process lifetime (the terminal FGS
 * already keeps the process alive while sessions exist). One tick costs a
 * readdir of the (hidepid-truncated) /proc plus a handful of small file
 * reads per app-owned process — microseconds of CPU, no device wakes.
 */
object RuntimeAgentDetector {

    /** Tick interval. One /proc pass per tick; only while eligible sessions exist. */
    const val SCAN_INTERVAL_MS: Long = 2_000L

    private const val LOG_TAG = "AgentRuntimeDetector"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    @Volatile
    private var procfsReader: ProcfsReader = HostProcfsReader()

    private val _observations =
        MutableStateFlow<Map<Long, AgentRuntimeDetection.Observation>>(emptyMap())

    /**
     * The authoritative runtime-evidence map: sessionId -> observation.
     * Every value was produced by the pure [AgentRuntimeDetection.compute]
     * step from a real /proc snapshot; keys exist exactly for sessions
     * currently eligible for scanning. Consumers reconcile from this flow
     * (no second truth source anywhere).
     */
    val observations: StateFlow<Map<Long, AgentRuntimeDetection.Observation>> =
        _observations.asStateFlow()

    /**
     * Start the monitor exactly once per process (idempotent; safe to call
     * from every spawn). Does nothing on a second call.
     */
    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { monitor() }
        Log.i(LOG_TAG, "runtime agent detector started (interval ${SCAN_INTERVAL_MS}ms)")
    }

    /** Test seam: replace the /proc reader (JVM tests inject snapshots; never used in production paths). */
    internal fun installReaderForTesting(reader: ProcfsReader) {
        procfsReader = reader
    }

    /** Forcibly stop the monitor (test isolation only; production never stops it). */
    internal fun stopForTesting() {
        started.set(false)
        scope.coroutineContext.cancelChildren()
        _observations.value = emptyMap()
    }

    /**
     * The monitor: react to eligibility changes; tick ONLY while at least
     * one eligible session exists. `collectLatest` cancels the previous
     * tick loop whenever eligibility changes (session added/removed/
     * finished), so the scan set is always the current one and an empty
     * set parks the monitor with zero scheduled work.
     */
    private suspend fun monitor() {
        TerminalSessionManager.sessions
            .map { list -> eligibleSessions(list) }
            .distinctUntilChanged()
            .collectLatest { eligible ->
                pruneTo(eligible)
                if (eligible.isEmpty()) return@collectLatest // park: nothing to scan
                while (coroutineContext.isActive) {
                    scanOnce(eligible)
                    delay(SCAN_INTERVAL_MS)
                }
            }
    }

    private fun pruneTo(eligible: List<AgentRuntimeDetection.EligibleAgentSession>) {
        val keep = eligible.map { it.sessionId }.toSet()
        val pruned = AgentRuntimeDetection.prune(_observations.value, keep)
        if (pruned.keys != _observations.value.keys) {
            _observations.value = pruned
        }
    }

    /** One scan: read /proc once, read the launch channels, fold through the pure step, publish. */
    internal suspend fun scanOnce(eligible: List<AgentRuntimeDetection.EligibleAgentSession>) {
        if (eligible.isEmpty()) return
        // File I/O stays off the caller; the pure step is synchronous. The
        // P9 launch-record reads ride the SAME single IO hop as the /proc
        // snapshot — one small app-private file per eligible session that
        // carries a channel (a few hundred bytes at most), still
        // microseconds of work per tick.
        val (snapshot, records) = runInterruptible(Dispatchers.IO) {
            val snap = procfsReader.snapshot()
            val recs = eligible.associate { session ->
                session.sessionId to (session.recordPath?.let(AgentLaunchRecords::readFile)
                    ?: AgentLaunchRecords.EMPTY)
            }
            snap to recs
        }
        val next = AgentRuntimeDetection.compute(
            eligible = eligible,
            previous = _observations.value,
            snapshot = snapshot,
            nowMs = System.currentTimeMillis(),
            records = records,
        )
        publishChanged(next)
    }

    private fun publishChanged(next: Map<Long, AgentRuntimeDetection.Observation>) {
        if (next == _observations.value) return
        for ((id, observation) in next) {
            val before = _observations.value[id]
            if (before?.state != observation.state) {
                // The evidence log (Part M: existing logs as the diagnostic
                // channel — no UI is added for this in P3b).
                Log.d(
                    LOG_TAG,
                    "session $id agent state ${before?.state ?: "(none)"} -> ${observation.state}" +
                        (observation.evidence?.let { " (pids=${it.pids}, grade=${it.grade})" } ?: ""),
                )
            }
        }
        _observations.value = next
    }

    /**
     * Eligibility extraction from the manager's authoritative entries.
     *
     * M7.2 P9 — TWO classes (the Part-B root-cause fix), both LIVE
     * (not FINISHED) sessions:
     *
     *   - PREDICTED: the launch identity resolves exactly
     *     [LaunchIdentity.KnownAgent] — the registry-launcher sessions,
     *     scanned for their OWN token (spawn truth), plus their launch
     *     record channel when the launch produced one.
     *   - DISCOVERED: every other LIVE GUEST session (plain Linux shells,
     *     Files' Open-Terminal-Here, catalog tools, custom tools) — the
     *     tree is scanned against the whole registry token set. This is
     *     the line the old gate got wrong: a plain Terminal session typing
     *     `kilo` is a REAL agent run and now states exactly that.
     *
     * Excluded, structurally: FINISHED sessions (their process tree is
     * gone) and host-side plain shells ([SpawnOrigin.Shell] — no guest
     * tree, no agent binaries live there). The scanner still never
     * claims anything for a process outside the session's own
     * correlation domain.
     */
    internal fun eligibleSessions(
        entries: List<TerminalSessionManager.SessionEntry>,
    ): List<AgentRuntimeDetection.EligibleAgentSession> =
        entries.mapNotNull { entry ->
            if (entry.isFinished) return@mapNotNull null
            if (entry.origin == SpawnOrigin.Shell) return@mapNotNull null
            val identity = LaunchIdentity.of(entry.origin, entry.agent) as? LaunchIdentity.KnownAgent
            AgentRuntimeDetection.EligibleAgentSession(
                sessionId = entry.id,
                rootPid = entry.shellPid,
                token = identity?.command?.let { AgentProcessMatcher.commandToken(it) } ?: "",
                predetermined = identity,
                discovery = identity == null,
                recordPath = entry.launchRecordPath,
            )
        }
}

/**
 * The production /proc reader — real host procfs as the app's own UID sees
 * it (the SAME view the guest gets through its /proc bind, and the same
 * access class as the in-tree TerminalSession.getCwd() precedent).
 *
 * Per process: /proc/<pid>/stat (comm-aware: fields are parsed AFTER the
 * last ')' so comm entries containing spaces or parens cannot shift the
 * indices), /proc/<pid>/cmdline (NUL-split argv; empty for zombies and
 * pre-exec pids), /proc/<pid>/exe (readlink; null when unreadable).
 * Unreadable is DATA (null), never a guess — the pure layer decides with
 * exactly the evidence it was given.
 */
class HostProcfsReader(private val procRoot: String = "/proc") : ProcfsReader {

    override fun snapshot(): ProcfsSnapshot? {
        val dir = File(procRoot)
        val pidNames = try {
            dir.list { _, name -> name.all { it in '0'..'9' } }?.filter { it.isNotEmpty() }
        } catch (_: SecurityException) {
            null
        } ?: return null // /proc itself unreadable: scanner unavailable
        val processes = ArrayList<ProcfsProcess>(pidNames.size)
        for (name in pidNames) {
            val pid = name.toIntOrNull() ?: continue
            val process = readProcess(pid) ?: continue // vanished mid-scan: fine
            processes += process
        }
        return ProcfsSnapshot(processes)
    }

    private fun readProcess(pid: Int): ProcfsProcess? {
        val stat = readStat(pid) ?: return null // a pid without stat is not observable
        val argv = readArgv(pid)
        val exe = readExe(pid)
        return ProcfsProcess(
            pid = pid,
            ppid = stat.second,
            pgrp = stat.third,
            state = stat.first,
            argv = argv,
            exe = exe,
            startTime = stat.fourth,
        )
    }

    /**
     * /proc/<pid>/stat -> (state, ppid, pgrp, starttime), comm-aware:
     * everything before the LAST ')' is "pid (comm"; the remaining fields
     * are state(1) ppid(2) pgrp(3) … starttime(20) per procfs(5) — immune
     * to comm strings containing spaces or parentheses. (M7.2 P9 added the
     * starttime: the launch-record anchor's pid-reuse guard.)
     */
    private fun readStat(pid: Int): Quadruple<Char, Int, Int, Long>? {
        val text = try {
            File("$procRoot/$pid/stat").readText()
        } catch (_: Exception) {
            return null
        }
        val close = text.lastIndexOf(')')
        if (close < 0 || close == text.length) return null
        val tail = text.substring(close + 1).trim().split(Regex("\\s+"))
        if (tail.size < 20) return null
        val state = tail[0].firstOrNull() ?: return null
        val ppid = tail[1].toIntOrNull() ?: return null
        val pgrp = tail[2].toIntOrNull() ?: return null
        val start = tail[19].toLongOrNull() ?: return null
        return Quadruple(state, ppid, pgrp, start)
    }

    /** Minimal 4-component value carrier (the stdlib has Triple but no Quadruple). */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    /** /proc/<pid>/cmdline as argv; empty list for zombies / pre-exec pids; null when unreadable. */
    private fun readArgv(pid: Int): List<String>? {
        val bytes = try {
            File("$procRoot/$pid/cmdline").readBytes()
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return emptyList()
        val text = String(bytes, Charsets.UTF_8)
        return text.split('\u0000').let { parts ->
            // A trailing NUL yields one empty tail element — drop exactly that.
            if (parts.isNotEmpty() && parts.last().isEmpty()) parts.dropLast(1) else parts
        }
    }

    /**
     * readlink /proc/<pid>/exe via the kernel magic symlink. java.io
     * canonicalization would RESOLVE the whole chain; the app needs the
     * literal target, which getCanonicalPath returns for /proc magic
     * links on Linux (and null-on-failure is the honest unreadable case).
     */
    private fun readExe(pid: Int): String? = try {
        val target = File("$procRoot/$pid/exe").canonicalPath
        if (target == "$procRoot/$pid/exe") null else target // unresolved == unreadable
    } catch (_: Exception) {
        null
    }
}
