package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P3b — the runtime-detection truth matrix (pure tests over
 * [AgentProcessMatcher], [AgentDescendantCorrelator] and
 * [AgentRuntimeDetection.compute]).
 *
 * These tests are the executable form of the phase mandate:
 *
 *   - identity boundaries (PART N): only [LaunchIdentity.KnownAgent]
 *     reaches the scanner — enforced structurally (AgentRuntimeDetection-
 *     IntegrationTest pins the eligibility filter); the pure layer never
 *     sees a non-agent session at all;
 *   - runtime evidence: process found -> RUNNING; proven disappearance ->
 *     NOT_RUNNING; scanner unavailable / ambiguous -> UNKNOWN (never
 *     RUNNING);
 *   - correlation (PART F): an unrelated same-name process NEVER produces
 *     RUNNING for a PocketShell session; attribution only via the
 *     session's fork-proven root (ppid chain OR process group);
 *   - matching safety (PART G): no substring matching — "codex" must not
 *     match "my-codex-wrapper"/"codex-helper"/"something-codex"; the
 *     chain shell's whole-element command line cannot match;
 *   - scanner lifecycle: pruning, purity, and the no-crash-on-disappearance
 *     contract.
 *
 * All shapes below were proved against REAL Linux procfs by the controlled
 * experiments (scripts/procfs_experiments_p3b.sh, E1-E5).
 */
class AgentRuntimeDetectionTest {

    // ------------------------------------------------------------ helpers

    private fun process(
        pid: Int,
        ppid: Int,
        pgrp: Int = ppid,
        state: Char? = 'S',
        argv: List<String>? = null,
        exe: String? = null,
    ) = ProcfsProcess(pid = pid, ppid = ppid, pgrp = pgrp, state = state, argv = argv, exe = exe)

    private fun snap(vararg processes: ProcfsProcess) = ProcfsSnapshot(processes.toList())

    private fun eligible(
        sessionId: Long = 1L,
        rootPid: Int,
        token: String,
    ) = AgentRuntimeDetection.EligibleAgentSession(sessionId, rootPid, token)

    // ------------------------------------------------- matching safety (G)

    @Test
    fun `exact argv0 token matches as PROCFS_CMDLINE`() {
        val grade = AgentProcessMatcher.match(
            "kilo",
            process(pid = 100, ppid = 50, argv = listOf("kilo", "--yolo")),
        )
        assertEquals(AgentMatchedBy.PROCFS_CMDLINE, grade)
    }

    @Test
    fun `argv0 as a path matches by basename`() {
        val grade = AgentProcessMatcher.match(
            "codex",
            process(pid = 100, ppid = 50, argv = listOf("/usr/local/bin/codex", "serve")),
        )
        assertEquals(AgentMatchedBy.PROCFS_CMDLINE, grade)
    }

    @Test
    fun `exe image basename matches as PROCFS_EXE`() {
        val grade = AgentProcessMatcher.match(
            "kilo",
            process(pid = 100, ppid = 50, argv = listOf("/tmp/bin/kilo"), exe = "/usr/bin/kilo"),
        )
        assertEquals(AgentMatchedBy.PROCFS_EXE, grade)
    }

    @Test
    fun `exe grade outranks cmdline grade when both match`() {
        val grade = AgentProcessMatcher.match(
            "kilo",
            process(pid = 100, ppid = 50, argv = listOf("kilo"), exe = "/usr/bin/kilo"),
        )
        assertEquals(AgentMatchedBy.PROCFS_EXE, grade)
    }

    @Test
    fun `shebang shape - the kernel puts the script path at argv1 and it matches`() {
        // Experiment E3a: execve of a shebang script yields
        // [interpreter, scriptPath, ...] — the invoked name rides argv[1].
        val grade = AgentProcessMatcher.match(
            "claude",
            process(pid = 100, ppid = 50, argv = listOf("node", "/usr/local/bin/claude")),
        )
        assertEquals(AgentMatchedBy.PROCFS_CMDLINE, grade)
    }

    @Test
    fun `interpreter-hosted exe does NOT match by exe - the cmdline grade carries it`() {
        // Experiment E3a: exe reads the interpreter, not the script — so the
        // AGENT token can only be claimed through the cmdline grade.
        val process = process(
            pid = 100,
            ppid = 50,
            argv = listOf("node", "/usr/local/bin/claude"),
            exe = "/usr/local/bin/node",
        )
        assertEquals(AgentMatchedBy.PROCFS_CMDLINE, AgentProcessMatcher.match("claude", process))
        // And the exe grade belongs to the INTERPRETER's own name — which is
        // exactly why the agent claim must not rest on the exe field here.
        assertEquals(AgentMatchedBy.PROCFS_EXE, AgentProcessMatcher.match("node", process))
    }

    @Test
    fun `no substring matching - the mandated negatives never match codex`() {
        for (argv0 in listOf("my-codex-wrapper", "codex-helper", "something-codex", "codexer")) {
            assertNull(
                "argv0='$argv0' must not match token 'codex'",
                AgentProcessMatcher.match("codex", process(pid = 100, ppid = 50, argv = listOf(argv0))),
            )
        }
    }

    @Test
    fun `no substring matching - the chain shell's single-element command line cannot match`() {
        // Experiment E2: sh -l -c carries the whole chain as ONE argv element.
        val chainShell = process(
            pid = 60,
            ppid = 50,
            argv = listOf("sh", "-l", "-c", "kilo; exec sh -l"),
        )
        assertNull(AgentProcessMatcher.match("kilo", chainShell))
    }

    @Test
    fun `argv1 without a path separator is treated as an argument, not a script`() {
        // `npm exec kilo`-style bare words at argv[1] are arguments; only the
        // kernel shebang contract (a PATH at argv[1]) is accepted.
        assertNull(
            AgentProcessMatcher.match(
                "kilo",
                process(pid = 100, ppid = 50, argv = listOf("npm", "exec", "kilo")),
            ),
        )
    }

    @Test
    fun `blank token never matches`() {
        assertNull(
            AgentProcessMatcher.match(
                "  ",
                process(pid = 100, ppid = 50, argv = listOf("kilo"), exe = "/usr/bin/kilo"),
            ),
        )
    }

    @Test
    fun `empty argv matches nothing`() {
        assertNull(AgentProcessMatcher.match("kilo", process(pid = 100, ppid = 50, argv = emptyList())))
    }

    @Test
    fun `unreadable argv and exe claim nothing`() {
        assertNull(AgentProcessMatcher.match("kilo", process(pid = 100, ppid = 50)))
    }

    @Test
    fun `basename and commandToken helpers`() {
        assertEquals("c", AgentProcessMatcher.basename("/a/b/c"))
        assertEquals("c", AgentProcessMatcher.basename("c"))
        assertEquals("kilo", AgentProcessMatcher.commandToken("kilo --yolo"))
        assertEquals("kilo", AgentProcessMatcher.commandToken("  kilo  "))
        assertEquals("kilo", AgentProcessMatcher.commandToken("kilo"))
    }

    // ---------------------------------------------------- correlation (F)

    @Test
    fun `direct descendant correlates through the ppid chain`() {
        // proot(root=10) -> sh(11) -> agent(12)
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10, argv = listOf("proot")),
            process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("sh", "-l", "-c", "kilo; exec sh -l")),
            process(pid = 12, ppid = 11, pgrp = 10, argv = listOf("kilo")),
        )
        val correlated = AgentDescendantCorrelator.correlatedPids(s, 10)
        assertEquals(setOf(11, 12), correlated)
    }

    @Test
    fun `deep descendant correlates`() {
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 11, ppid = 10, pgrp = 10),
            process(pid = 12, ppid = 11, pgrp = 10),
            process(pid = 13, ppid = 12, pgrp = 10),
            process(pid = 14, ppid = 13, pgrp = 12), // worker pool regrouped itself
        )
        assertEquals(setOf(11, 12, 13, 14), AgentDescendantCorrelator.correlatedPids(s, 10))
    }

    @Test
    fun `the correlation root itself is never attributed`() {
        val s = snap(process(pid = 10, ppid = 1, pgrp = 10, argv = listOf("proot")))
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, 10).isEmpty())
    }

    @Test
    fun `orphaned child reparents to init but its process group still correlates it`() {
        // Experiment E4a: the parent exited; ppid is 1 (chain lost) but the
        // group survives — domain 2 attributes it.
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 99, ppid = 1, pgrp = 10),
        )
        assertEquals(setOf(99), AgentDescendantCorrelator.correlatedPids(s, 10))
    }

    @Test
    fun `a double-escaped process (reparented AND regrouped) is honestly invisible`() {
        // Experiment E4b: setsid'd + orphaned — outside both domains. The
        // documented blind spot: no claim is made in either direction.
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 77, ppid = 1, pgrp = 77),
        )
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, 10).isEmpty())
    }

    @Test
    fun `an unrelated same-name process can never correlate`() {
        // Part N negative control: another tree elsewhere on the device runs
        // the same token — different chain, different group, no attribution.
        val sessionTree = snap(
            process(pid = 10, ppid = 1, pgrp = 10), // session A root
            process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("sh", "-l", "-c", "codex; exec sh -l")),
        )
        val world = sessionTree.copy(
            processes = sessionTree.processes + listOf(
                process(pid = 500, ppid = 1, pgrp = 500, argv = listOf("codex")), // unrelated
                process(pid = 501, ppid = 500, pgrp = 500, argv = listOf("codex-worker")),
            ),
        )
        val correlated = AgentDescendantCorrelator.correlatedPids(world, 10)
        assertEquals(setOf(11), correlated)
    }

    @Test
    fun `a second session's agent tree attributes to that session only`() {
        val world = snap(
            process(pid = 10, ppid = 1, pgrp = 10), // session A root
            process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("kilo")),
            process(pid = 20, ppid = 1, pgrp = 20), // session B root
            process(pid = 21, ppid = 20, pgrp = 20, argv = listOf("kilo")),
        )
        assertEquals(setOf(11), AgentDescendantCorrelator.correlatedPids(world, 10))
        assertEquals(setOf(21), AgentDescendantCorrelator.correlatedPids(world, 20))
    }

    @Test
    fun `non-positive root correlates nothing`() {
        val s = snap(process(pid = 11, ppid = 0, pgrp = 0))
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, 0).isEmpty())
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, -1).isEmpty())
    }

    @Test
    fun `a cycle in the ancestry cannot hang or misattribute`() {
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 30, ppid = 31, pgrp = 30),
            process(pid = 31, ppid = 30, pgrp = 30), // cycle 30 <-> 31
        )
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, 10).isEmpty())
    }

    @Test
    fun `a parent outside the snapshot is a dead end`() {
        val s = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 40, ppid = 999, pgrp = 999), // parent invisible
        )
        assertTrue(AgentDescendantCorrelator.correlatedPids(s, 10).isEmpty())
    }

    // -------------------------------------- runtime evidence states (E/N)

    @Test
    fun `first scan with no match is UNKNOWN - never NOT_RUNNING`() {
        // The agent may not have started yet, or may already have exited —
        // a scanner that never saw it cannot tell. UNKNOWN is the only
        // honest answer.
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("sh", "-l", "-c", "kilo; exec sh -l")),
            ),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, obs[1L]!!.state)
        assertNull(obs[1L]!!.evidence)
    }

    @Test
    fun `a correlated matching process yields RUNNING with exact evidence`() {
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("sh", "-l", "-c", "kilo; exec sh -l")),
                process(pid = 12, ppid = 11, pgrp = 10, argv = listOf("kilo")),
            ),
            nowMs = 1_000L,
        )
        val observation = obs[1L]!!
        assertEquals(AgentRuntimeState.RUNNING, observation.state)
        assertEquals(listOf(12), observation.evidence!!.pids)
        assertEquals(AgentMatchedBy.PROCFS_CMDLINE, observation.evidence!!.grade)
        assertTrue(observation.everObservedRunning)
    }

    @Test
    fun `proven disappearance is NOT_RUNNING - and never a completion claim`() {
        val running = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo")),
            ),
            nowMs = 1_000L,
        )
        val gone = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = running,
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("sh", "-l")),
            ),
            nowMs = 2_000L,
        )
        assertEquals(AgentRuntimeState.NOT_RUNNING, gone[1L]!!.state)
        assertNull(gone[1L]!!.evidence)
    }

    @Test
    fun `a failed scan yields UNKNOWN even from RUNNING - missing evidence is not absence`() {
        val running = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo"))),
            nowMs = 1_000L,
        )
        val failed = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = running,
            snapshot = null,
            nowMs = 2_000L,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, failed[1L]!!.state)
        // history preserved for explanation
        assertTrue(failed[1L]!!.everObservedRunning)
        assertEquals(running[1L]!!.evidence, failed[1L]!!.evidence)
    }

    @Test
    fun `an alive previously-matched process that no longer matches is ambiguous UNKNOWN`() {
        // The agent pid still exists in the correlated set, but its argv/exe
        // became unreadable — claiming NOT_RUNNING would be false, RUNNING
        // unproven. UNKNOWN is the honest state.
        val running = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo"))),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.RUNNING, running[1L]!!.state)
        val ambiguous = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = running,
            snapshot = snap(process(pid = 12, ppid = 10, pgrp = 10, state = 'S', argv = null, exe = null)),
            nowMs = 2_000L,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, ambiguous[1L]!!.state)
    }

    @Test
    fun `a zombie previously-matched process is proven absence - NOT_RUNNING`() {
        val running = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo"))),
            nowMs = 1_000L,
        )
        val zombied = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = running,
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 12, ppid = 10, pgrp = 10, state = 'Z', argv = emptyList()),
            ),
            nowMs = 2_000L,
        )
        assertEquals(AgentRuntimeState.NOT_RUNNING, zombied[1L]!!.state)
    }

    @Test
    fun `a zombie never matches even with a token-carrying shape`() {
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 12, ppid = 10, pgrp = 10, state = 'Z', argv = emptyList(), exe = "/usr/bin/kilo"),
            ),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, obs[1L]!!.state)
    }

    @Test
    fun `a session that has not forked yet is UNKNOWN`() {
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 0, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(process(pid = 12, ppid = 1, pgrp = 12, argv = listOf("kilo"))),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.UNKNOWN, obs[1L]!!.state)
    }

    @Test
    fun `relaunch inside the session returns to RUNNING`() {
        val running = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo"))),
            nowMs = 1_000L,
        )
        val gone = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = running,
            snapshot = snap(process(pid = 10, ppid = 1, pgrp = 10)),
            nowMs = 2_000L,
        )
        assertEquals(AgentRuntimeState.NOT_RUNNING, gone[1L]!!.state)
        // The user typed `kilo` again at the prompt — a new correlated match.
        val again = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = gone,
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 30, ppid = 10, pgrp = 10, argv = listOf("kilo")),
            ),
            nowMs = 3_000L,
        )
        assertEquals(AgentRuntimeState.RUNNING, again[1L]!!.state)
        assertEquals(listOf(30), again[1L]!!.evidence!!.pids)
    }

    @Test
    fun `multiple matched processes - evidence carries the strongest grade`() {
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(eligible(rootPid = 10, token = "kilo")),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("node", "/usr/local/bin/kilo")),
                process(pid = 13, ppid = 12, pgrp = 10, exe = "/usr/bin/kilo"),
            ),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.RUNNING, obs[1L]!!.state)
        assertEquals(listOf(12, 13), obs[1L]!!.evidence!!.pids)
        assertEquals(AgentMatchedBy.PROCFS_EXE, obs[1L]!!.evidence!!.grade)
    }

    @Test
    fun `two sessions of the same agent are evaluated independently`() {
        val obs = AgentRuntimeDetection.compute(
            eligible = listOf(
                eligible(sessionId = 1L, rootPid = 10, token = "kilo"),
                eligible(sessionId = 2L, rootPid = 20, token = "kilo"),
            ),
            previous = emptyMap(),
            snapshot = snap(
                process(pid = 10, ppid = 1, pgrp = 10),
                process(pid = 11, ppid = 10, pgrp = 10, argv = listOf("kilo")),
                process(pid = 20, ppid = 1, pgrp = 20),
                process(pid = 21, ppid = 20, pgrp = 20, argv = listOf("sh", "-l")),
            ),
            nowMs = 1_000L,
        )
        assertEquals(AgentRuntimeState.RUNNING, obs[1L]!!.state)
        assertEquals(AgentRuntimeState.UNKNOWN, obs[2L]!!.state)
    }

    // --------------------------------------------- purity and lifecycle

    @Test
    fun `compute is pure - identical inputs produce identical outputs`() {
        val eligible = listOf(eligible(rootPid = 10, token = "kilo"))
        val snapshot = snap(
            process(pid = 10, ppid = 1, pgrp = 10),
            process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo")),
        )
        val first = AgentRuntimeDetection.compute(eligible, emptyMap(), snapshot, 1_000L)
        val second = AgentRuntimeDetection.compute(eligible, emptyMap(), snapshot, 1_000L)
        assertEquals(first, second)
    }

    @Test
    fun `prune drops observations whose sessions left eligibility`() {
        val observations = mapOf(
            1L to AgentRuntimeDetection.Observation(1L, AgentRuntimeState.RUNNING, null, true, 1L),
            2L to AgentRuntimeDetection.Observation(2L, AgentRuntimeState.UNKNOWN, null, false, 1L),
        )
        val pruned = AgentRuntimeDetection.prune(observations, setOf(1L))
        assertEquals(setOf(1L), pruned.keys)
    }

    @Test
    fun `process disappearance between scans never throws - the shape changes freely`() {
        // Every step of the lifecycle of a scan must survive arbitrary
        // snapshot shapes: empty world, vanished root, vanished everything.
        val eligible = listOf(eligible(rootPid = 10, token = "kilo"))
        val empty = AgentRuntimeDetection.compute(eligible, emptyMap(), snap(), 1L)
        val withRoot = AgentRuntimeDetection.compute(eligible, empty, snap(process(pid = 10, ppid = 1, pgrp = 10)), 2L)
        val none = AgentRuntimeDetection.compute(eligible, withRoot, snap(), 3L)
        val again = AgentRuntimeDetection.compute(eligible, none, snap(process(pid = 12, ppid = 10, pgrp = 10, argv = listOf("kilo"))), 4L)
        assertEquals(AgentRuntimeState.UNKNOWN, empty[1L]!!.state)
        assertEquals(AgentRuntimeState.UNKNOWN, withRoot[1L]!!.state)
        assertEquals(AgentRuntimeState.UNKNOWN, none[1L]!!.state)
        assertEquals(AgentRuntimeState.RUNNING, again[1L]!!.state)
        assertNotEquals(empty, again)
    }

    @Test
    fun `the four-state contract is exactly the mandated vocabulary`() {
        // PART E: the model distinguishes exactly NOT_APPLICABLE / UNKNOWN /
        // NOT_RUNNING / RUNNING — and adds no certainty of its own.
        assertEquals(
            listOf("NOT_APPLICABLE", "UNKNOWN", "NOT_RUNNING", "RUNNING"),
            AgentRuntimeState.entries.map { it.name },
        )
    }
}
