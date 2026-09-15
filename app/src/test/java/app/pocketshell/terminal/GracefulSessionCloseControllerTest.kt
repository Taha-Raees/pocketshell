package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 J1 contract pins (docs/PHASE-4-TERMINAL-AUDIT.md §J1) for
 * [GracefulSessionCloseController] — the pure decision logic behind
 * TerminalSessionManager.closeSession. Device gates for the same contract
 * ran on SM-T870 (report §M-result); these tests pin the LOGIC.
 */
class GracefulSessionCloseControllerTest {

    /** Deterministic scheduler: runnables fire only when the test fires them. */
    private class FakeScheduler : GracefulSessionCloseController.Scheduler {
        val scheduled = mutableListOf<Pair<Runnable, Long>>()

        override fun postDelayed(runnable: Runnable, delayMs: Long): GracefulSessionCloseController.Cancellation {
            scheduled += runnable to delayMs
            return GracefulSessionCloseController.Cancellation {
                scheduled.removeAll { it.first === runnable }
            }
        }

        fun fireAll() {
            val runnables = scheduled.map { it.first }
            scheduled.clear()
            runnables.forEach { it.run() }
        }
    }

    private class FakeTarget(
        private val pgrp: Int,
        private val childAlive: () -> Boolean = { true },
    ) : GracefulSessionCloseController.Target {
        var forceFinishes = 0
        override fun foregroundProcessGroup(): Int = pgrp
        override fun isDirectChildAlive(): Boolean = childAlive()
        override fun forceFinishDirectChild() {
            forceFinishes++
        }
    }

    private class Rig(
        val graceMs: Long = 1_000L,
        signalerThrows: Boolean = false,
    ) {
        val signals = mutableListOf<Pair<Int, Int>>() // (pid, signal) — pid negative for groups
        val scheduler = FakeScheduler()
        val controller = GracefulSessionCloseController(
            graceMs = graceMs,
            signaler = { pid, signal ->
                if (pid == Int.MIN_VALUE) throw IllegalStateException("boom") // test hook
                signals += pid to signal
                if (signalerThrows) throw SecurityException("signaler failed")
            },
            scheduler = scheduler,
        )

        fun close(id: Long = 1L, pgrp: Int = 4242, childAlive: () -> Boolean = { true }): Boolean =
            controller.requestClose(id, FakeTarget(pgrp, childAlive))
    }

    @Test
    fun `graceful close signals SIGCONT then SIGHUP to the foreground group and schedules the fallback`() {
        val rig = Rig()
        val delivered = rig.close(pgrp = 4242)
        assertTrue(delivered)
        assertEquals(
            listOf(-4242 to GracefulSessionCloseController.Signals.SIGCONT, -4242 to GracefulSessionCloseController.Signals.SIGHUP),
            rig.signals,
        )
        assertEquals(1, rig.scheduler.scheduled.size)
        assertEquals(rig.graceMs, rig.scheduler.scheduled[0].second)
        assertEquals(1, rig.controller.pendingCount())
    }

    @Test
    fun `unresolvable foreground group means NO graceful signal and no fallback`() {
        val rig = Rig()
        assertFalse(rig.close(pgrp = 0))
        assertFalse(rig.close(id = 2L, pgrp = -5))
        assertTrue(rig.signals.isEmpty())
        assertEquals(0, rig.scheduler.scheduled.size)
        // The caller runs today's immediate finishIfRunning() in this case —
        // asserted by the return value contract above.
    }

    @Test
    fun `signaler failure aborts the graceful path`() {
        val rig = Rig(signalerThrows = false)
        // First signal succeeds, second throws — simulate via a target whose
        // group resolves but a throwing second signal:
        val throwing = Rig()
        throwing.controller.requestClose(1, object : GracefulSessionCloseController.Target {
            override fun foregroundProcessGroup(): Int = 99
            override fun isDirectChildAlive(): Boolean = true
            override fun forceFinishDirectChild() {}
        })
        // Now a controller whose signaler always throws:
        val alwaysThrows = Rig(signalerThrows = true)
        val delivered = alwaysThrows.close(pgrp = 99)
        assertFalse(delivered)
        assertEquals(0, alwaysThrows.scheduler.scheduled.size)
        assertFalse(throwing.signals.isEmpty()) // sanity: normal rig does signal
    }

    @Test
    fun `timeout with a still-alive direct child fires the SIGKILL fallback exactly once`() {
        val rig = Rig()
        val target = FakeTarget(pgrp = 4242, childAlive = { true })
        assertTrue(rig.controller.requestClose(1L, target))
        rig.scheduler.fireAll()
        assertEquals(1, target.forceFinishes)
        assertEquals(0, rig.controller.pendingCount())
        // Firing again (a stale second call) must not re-kill:
        rig.scheduler.fireAll()
        assertEquals(1, target.forceFinishes)
    }

    @Test
    fun `timeout with an already-exited direct child does not force`() {
        val rig = Rig()
        val target = FakeTarget(pgrp = 4242, childAlive = { false })
        assertTrue(rig.controller.requestClose(1L, target))
        rig.scheduler.fireAll()
        assertEquals(0, target.forceFinishes)
    }

    @Test
    fun `process exit inside the grace window cancels the fallback`() {
        val rig = Rig()
        val target = FakeTarget(pgrp = 4242, childAlive = { true })
        assertTrue(rig.controller.requestClose(1L, target))
        rig.controller.onProcessExited(1L)
        assertEquals(0, rig.controller.pendingCount())
        rig.scheduler.fireAll()
        assertEquals(0, target.forceFinishes)
    }

    @Test
    fun `a second close of the same id replaces the pending fallback (no double fire)`() {
        val rig = Rig()
        val first = FakeTarget(pgrp = 4242)
        val second = FakeTarget(pgrp = 4243)
        assertTrue(rig.controller.requestClose(1L, first))
        assertTrue(rig.controller.requestClose(1L, second))
        assertEquals(1, rig.controller.pendingCount())
        rig.scheduler.fireAll()
        assertEquals(0, first.forceFinishes) // first pending was cancelled
        assertEquals(1, second.forceFinishes)
    }

    @Test
    fun `process exit for one session never cancels another session's fallback`() {
        val rig = Rig()
        val a = FakeTarget(pgrp = 4242)
        val b = FakeTarget(pgrp = 4243)
        assertTrue(rig.controller.requestClose(1L, a))
        assertTrue(rig.controller.requestClose(2L, b))
        rig.controller.onProcessExited(1L)
        rig.scheduler.fireAll()
        assertEquals(0, a.forceFinishes)
        assertEquals(1, b.forceFinishes) // isolation: B's close unaffected by A's exit
    }

    @Test
    fun `POLICY PIN - the policy only ever signals negative group pids, never pids or well-known groups`() {
        val rig = Rig()
        rig.close(id = 1L, pgrp = 4242)
        rig.close(id = 2L, pgrp = 77)
        for ((pid, signal) in rig.signals) {
            assertTrue(
                "policy must signal process GROUPS (negative pids) only, saw $pid",
                pid < -1, // excludes 0 (caller's group), -1?? kill(-1) hits everything — must never happen
            )
            assertTrue(
                "policy must only send SIGCONT/SIGHUP, saw $signal",
                signal == GracefulSessionCloseController.Signals.SIGCONT ||
                    signal == GracefulSessionCloseController.Signals.SIGHUP,
            )
        }
        assertEquals(4, rig.signals.size) // exactly two signals per close — never a sweep
    }
}
