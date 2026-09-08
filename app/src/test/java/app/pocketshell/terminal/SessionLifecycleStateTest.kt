package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P2 — the pure lifecycle contract (docs/M7.2-P0-AUDIT.md §10.3): the
 * SAME machine [TerminalSessionManager] applies at its transition sites,
 * exercised here over every reachable and unreachable path.
 *
 * These tests verify the actual transition CONTRACT, not implementation
 * details:
 *
 *   - the only entry point is STARTING (the real spawn-time state);
 *   - STARTING → RUNNING via the real fork signal, exactly once;
 *   - STARTING/RUNNING → FINISHED via the real waitpid delivery, carrying
 *     the structured status (exited code / negated signal mapping);
 *   - FINISHED is terminal: duplicate finish callbacks are REJECTED and can
 *     never corrupt the recorded status (idempotency under racing callbacks);
 *   - rejected transitions return the UNCHANGED state plus a reason (the
 *     manager logs them — nothing is silently swallowed);
 *   - invalid combinations are unrepresentable: FINISHED always carries an
 *     exit status, STARTING/RUNNING never do (private constructor).
 *
 * The wiring that delivers real signals (the manager's callbacks, the
 * client's setTerminalShellPid forward, the spawn-site origins) is pinned
 * structurally in SessionLifecycleIntegrationTest; real process exits on
 * hardware remain the docs/TESTING.md §49 gate.
 */
class SessionLifecycleStateTest {

    // ------------------------------------------------------------- STARTING

    @Test
    fun `the machine's entry point is STARTING without exit information`() {
        val starting = SessionLifecycleState.STARTING
        assertEquals(SessionPhase.STARTING, starting.phase)
        assertNull(starting.exitStatus)
    }

    // ---------------------------------------------------- STARTING → RUNNING

    @Test
    fun `the real fork signal moves STARTING to RUNNING`() {
        val transition = SessionLifecycleState.STARTING.onProcessStarted()
        assertTrue(transition is LifecycleTransition.Accepted)
        assertEquals(SessionPhase.RUNNING, transition.next.phase)
        assertNull(transition.next.exitStatus)
    }

    // --------------------------------------------------- STARTING → FINISHED

    @Test
    fun `a real exit from STARTING is accepted - the exit itself proves the fork`() {
        val transition = SessionLifecycleState.STARTING.onProcessFinished(3)
        assertTrue(transition is LifecycleTransition.Accepted)
        assertEquals(SessionPhase.FINISHED, transition.next.phase)
        assertEquals(ExitStatus.Exited(3), transition.next.exitStatus)
    }

    // ----------------------------------------------------- RUNNING → FINISHED

    @Test
    fun `a zero exit code records a successful exit`() {
        val running = SessionLifecycleState.STARTING.onProcessStarted().next
        val transition = running.onProcessFinished(0)
        assertTrue(transition is LifecycleTransition.Accepted)
        assertEquals(SessionPhase.FINISHED, transition.next.phase)
        assertEquals(ExitStatus.Exited(0), transition.next.exitStatus)
    }

    @Test
    fun `a non-zero exit code records the program's status`() {
        val running = SessionLifecycleState.STARTING.onProcessStarted().next
        val transition = running.onProcessFinished(127)
        assertTrue(transition is LifecycleTransition.Accepted)
        assertEquals(ExitStatus.Exited(127), transition.next.exitStatus)
    }

    @Test
    fun `a negated signal number records signal termination`() {
        val running = SessionLifecycleState.STARTING.onProcessStarted().next
        val transition = running.onProcessFinished(-9)
        assertTrue(transition is LifecycleTransition.Accepted)
        assertEquals(SessionPhase.FINISHED, transition.next.phase)
        assertEquals(ExitStatus.Signaled(9), transition.next.exitStatus)
    }

    // ------------------------------------------------------------- idempotency

    @Test
    fun `a duplicate fork signal is rejected from RUNNING`() {
        val running = SessionLifecycleState.STARTING.onProcessStarted().next
        val transition = running.onProcessStarted()
        assertTrue(transition is LifecycleTransition.Rejected)
        assertEquals(SessionPhase.RUNNING, transition.next.phase)
        assertNull(transition.next.exitStatus)
        assertTrue((transition as LifecycleTransition.Rejected).reason.isNotBlank())
    }

    @Test
    fun `a duplicate exit callback is rejected and never corrupts the recorded status`() {
        val finished = SessionLifecycleState.STARTING.onProcessStarted().next.onProcessFinished(2).next
        val duplicate = finished.onProcessFinished(-15)
        assertTrue(duplicate is LifecycleTransition.Rejected)
        assertEquals(SessionPhase.FINISHED, duplicate.next.phase)
        // The FIRST real status survives the racing duplicate.
        assertEquals(ExitStatus.Exited(2), duplicate.next.exitStatus)
    }

    @Test
    fun `an out-of-order fork signal is rejected from FINISHED`() {
        val finished = SessionLifecycleState.STARTING.onProcessStarted().next.onProcessFinished(1).next
        val transition = finished.onProcessStarted()
        assertTrue(transition is LifecycleTransition.Rejected)
        assertEquals(ExitStatus.Exited(1), transition.next.exitStatus)
    }

    @Test
    fun `rejected transitions return the unchanged current state with a reason`() {
        val running = SessionLifecycleState.STARTING.onProcessStarted().next
        val rejected = running.onProcessStarted() as LifecycleTransition.Rejected
        assertEquals(running, rejected.next)
        assertTrue(rejected.reason.contains("RUNNING"))
    }

    // ------------------------------------------------------ raw waitpid mapping

    @Test
    fun `waitpid raw mapping - positive codes are exits`() {
        assertEquals(ExitStatus.Exited(0), ExitStatus.fromWaitpidRaw(0))
        assertEquals(ExitStatus.Exited(1), ExitStatus.fromWaitpidRaw(1))
        assertEquals(ExitStatus.Exited(255), ExitStatus.fromWaitpidRaw(255))
    }

    @Test
    fun `waitpid raw mapping - negative values are negated signals`() {
        assertEquals(ExitStatus.Signaled(1), ExitStatus.fromWaitpidRaw(-1))   // SIGHUP
        assertEquals(ExitStatus.Signaled(9), ExitStatus.fromWaitpidRaw(-9))   // SIGKILL (the close path)
        assertEquals(ExitStatus.Signaled(15), ExitStatus.fromWaitpidRaw(-15)) // SIGTERM
        assertEquals(ExitStatus.Signaled(64), ExitStatus.fromWaitpidRaw(-64)) // realtime-signal ceiling
    }

    // ---------------------------------------------------- state-object contract

    @Test
    fun `states with the same phase and status are equal`() {
        val a = SessionLifecycleState.STARTING.onProcessStarted().next
        val b = SessionLifecycleState.STARTING.onProcessStarted().next
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, SessionLifecycleState.STARTING)
        assertNotEquals(a, a.onProcessFinished(0).next)
    }

    @Test
    fun `the full provable path keeps the status immutable to the end`() {
        // §10.3: SPAWNING → RUNNING → FINISHED(code), with FINISHED terminal.
        val state = SessionLifecycleState.STARTING
            .onProcessStarted().next
            .onProcessFinished(42).next
        assertEquals(SessionPhase.FINISHED, state.phase)
        assertEquals(ExitStatus.Exited(42), state.exitStatus)
    }

    // -------------------------------------------------------- spawn vocabulary

    @Test
    fun `spawn origins cover exactly the six real launch paths`() {
        val origins: List<SpawnOrigin> = listOf(
            SpawnOrigin.Shell,
            SpawnOrigin.LinuxShell,
            SpawnOrigin.FilesTerminal,
            SpawnOrigin.CommandApp("agy"),
            SpawnOrigin.CatalogApp("nano"),
            SpawnOrigin.CustomTool("tool-1"),
        )
        // Every kind is constructible and carries its stable launcher id.
        assertEquals("agy", (origins[3] as SpawnOrigin.CommandApp).appId)
        assertEquals("nano", (origins[4] as SpawnOrigin.CatalogApp).entryId)
        assertEquals("tool-1", (origins[5] as SpawnOrigin.CustomTool).toolId)
    }

    @Test
    fun `agent hints carry the spawn-time identity with its matching grade`() {
        val hint = AgentHint(
            displayName = "Antigravity",
            command = "agy",
            matchedBy = AgentMatchedBy.LAUNCH_METADATA,
        )
        assertEquals("Antigravity", hint.displayName)
        assertEquals("agy", hint.command)
        assertEquals(AgentMatchedBy.LAUNCH_METADATA, hint.matchedBy)
    }
}
