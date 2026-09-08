package app.pocketshell.notifications

import app.pocketshell.terminal.AgentMatchedBy
import app.pocketshell.terminal.AgentRuntimeDetection
import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentRuntimeState
import app.pocketshell.terminal.ExitStatus
import app.pocketshell.terminal.LaunchIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P4 — the notification TRUTH CONTRACT, pinned exhaustively over the
 * pure [AgentRuntimeNotificationMapping] (no Android, no coroutine, no
 * clock — the same purity discipline as AgentRuntimeEventsTest).
 *
 * What is pinned here:
 *   1. the event → notification matrix, every event kind, every honest
 *      wording (the running surface, the unknown surface, the one-shot exit
 *      statement, the cancellations, the silences);
 *   2. THE HONESTY LINE: no produced string ever claims completion,
 *      success, finished work or failure; "no longer detected" never
 *      becomes anything but the WITHDRAWAL of the running claim; the exit
 *      statement stays session/process-scoped with the exit code preserved
 *      verbatim (exit 0 is never "success");
 *   3. display names come from the launcher registry identity only —
 *      nothing is invented;
 *   4. deduplication (Part G): identical surfaces do not repost; the
 *      tombstone refuses every event for an ended session;
 *   5. multi-session isolation and determinism (purity).
 */
class AgentRuntimeNotificationMappingTest {

    // ---------------------------------------------------------- fixtures

    private fun agent(
        id: String = "claude",
        name: String = "Claude Code",
    ) = LaunchIdentity.KnownAgent(launcherId = id, displayName = name, command = "$id --run")

    private fun evidence(pids: List<Int> = listOf(4242)) =
        AgentRuntimeDetection.ProcessEvidence(
            pids = pids,
            grade = AgentMatchedBy.PROCFS_CMDLINE,
            observedAtMs = 1_000L,
        )

    private fun launched(
        sessionId: Long = 7L,
        knownAgent: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeEvent.Launched(sessionId, knownAgent, occurredAtMs = 1_000L)

    private fun confirmedRunning(
        sessionId: Long = 7L,
        from: AgentRuntimeState? = AgentRuntimeState.UNKNOWN,
        knownAgent: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeEvent.ConfirmedRunning(
        sessionId, knownAgent, from, evidence(), occurredAtMs = 2_000L,
    )

    private fun noLongerDetected(
        sessionId: Long = 7L,
        knownAgent: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeEvent.NoLongerDetected(
        sessionId, knownAgent, AgentRuntimeState.RUNNING, evidence(), occurredAtMs = 3_000L,
    )

    private fun runtimeUnknown(
        sessionId: Long = 7L,
        knownAgent: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeEvent.RuntimeUnknown(
        sessionId, knownAgent, AgentRuntimeState.RUNNING, occurredAtMs = 3_500L,
    )

    private fun sessionEnded(
        sessionId: Long = 7L,
        cause: AgentRuntimeEvent.SessionEnded.Cause =
            AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
        exitStatus: ExitStatus? = ExitStatus.Exited(0),
        knownAgent: LaunchIdentity.KnownAgent = agent(),
    ) = AgentRuntimeEvent.SessionEnded(
        sessionId, knownAgent, cause, exitStatus, AgentRuntimeState.RUNNING, occurredAtMs = 4_000L,
    )

    private val empty = AgentRuntimeNotificationMapping.Memory()

    private val postedRunning = AgentRuntimeNotificationMapping.Memory(
        posted = mapOf(7L to AgentRuntimeNotificationMapping.PostedKind.RUNNING),
        everPosted = setOf(7L),
    )

    private fun show(action: AgentRuntimeNotificationMapping.Action): AgentRuntimeNotificationMapping.Action.ShowRuntime {
        assertTrue("expected ShowRuntime, was $action", action is AgentRuntimeNotificationMapping.Action.ShowRuntime)
        return action as AgentRuntimeNotificationMapping.Action.ShowRuntime
    }

    // ------------------------------------------------- 1. the silent arms

    @Test
    fun `Launched produces nothing - a spawn fact makes no runtime claim`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(empty, launched())
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertEquals(empty, memory)
    }

    @Test
    fun `NoLongerDetected never yields a notification - only the withdrawal`() {
        // The hard honesty line: runtime disappearance is not completion, so
        // the ONLY correct action is cancelling the running surface.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(postedRunning, noLongerDetected())
        assertEquals(AgentRuntimeNotificationMapping.Action.Cancel(7L), action)
        assertTrue(memory.posted.isEmpty())
        assertTrue(7L in memory.everPosted) // the ever-posted fact survives
    }

    @Test
    fun `NoLongerDetected with no posted surface is silent`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(empty, noLongerDetected())
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertEquals(empty, memory)
    }

    // --------------------------------------- 2. the running surface (honest)

    @Test
    fun `first ConfirmedRunning shows the running surface with the registry display name`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(empty, confirmedRunning())
        val show = show(action)
        assertEquals(7L, show.sessionId)
        assertEquals("Claude Code is running", show.title)
        assertTrue("body must be session-scoped", show.text.contains("session 7"))
        assertTrue("body must name the confirmation shape", show.text.contains("confirmed"))
    }

    @Test
    fun `the running title uses the launcher registry name - never invented`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            empty,
            confirmedRunning(knownAgent = agent(id = "kilo", name = "Kilo Code")),
        )
        assertEquals("Kilo Code is running", show(action).title)
    }

    @Test
    fun `repeated ConfirmedRunning does not repost - the surface already says running`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(postedRunning, confirmedRunning())
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertEquals(postedRunning, memory)
    }

    @Test
    fun `the running surface updates from unknown in place`() {
        val unknown = AgentRuntimeNotificationMapping.Memory(
            posted = mapOf(7L to AgentRuntimeNotificationMapping.PostedKind.UNKNOWN),
            everPosted = setOf(7L),
        )
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(unknown, confirmedRunning())
        val show = show(action)
        assertEquals("Claude Code is running", show.title)
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[7L],
        )
    }

    // -------------------------------- 3. the unknown surface (uncertainty)

    @Test
    fun `RuntimeUnknown replaces the running claim with honest uncertainty`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(postedRunning, runtimeUnknown())
        val show = show(action)
        assertEquals(7L, show.sessionId)
        assertEquals("Claude Code runtime unknown", show.title)
        assertTrue(
            "the body must state the uncertainty, never a state claim",
            show.text.contains("cannot be verified"),
        )
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.UNKNOWN,
            memory.posted[7L],
        )
    }

    @Test
    fun `repeated RuntimeUnknown does not repost`() {
        val unknown = AgentRuntimeNotificationMapping.Memory(
            posted = mapOf(7L to AgentRuntimeNotificationMapping.PostedKind.UNKNOWN),
            everPosted = setOf(7L),
        )
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(unknown, runtimeUnknown())
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertEquals(unknown, memory)
    }

    @Test
    fun `RuntimeUnknown for a never-announced session stays silent (defensive arm)`() {
        // Structurally impossible per the engine's contract (RuntimeUnknown
        // requires an informative prior state, which would have posted a
        // surface) — pinned as the defensive silence it must remain.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(empty, runtimeUnknown())
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertEquals(empty, memory)
    }

    // ------------------------------------ 4. the exit fact (session truth)

    @Test
    fun `SESSION_FINISHED after a running surface replaces it with the factual exit statement`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedRunning,
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        val exit = action as? AgentRuntimeNotificationMapping.Action.ShowExitFact
            ?: throw AssertionError("expected ShowExitFact, was $action")
        assertEquals(7L, exit.sessionId)
        assertEquals("Session ended", exit.title)
        assertEquals("Terminal session 7 exited (code 0)", exit.text)
        assertTrue(memory.posted.isEmpty())
        assertTrue(7L in memory.ended) // tombstoned
    }

    @Test
    fun `the exit code is preserved verbatim - including non-zero`() {
        val text = AgentRuntimeNotificationMapping.exitText(3L, ExitStatus.Exited(3))
        assertEquals("Terminal session 3 exited (code 3)", text)
    }

    @Test
    fun `a signaled exit states the signal - still session-scoped`() {
        val text = AgentRuntimeNotificationMapping.exitText(3L, ExitStatus.Signaled(9))
        assertEquals("Terminal session 3 was terminated by signal 9", text)
    }

    @Test
    fun `SESSION_FINISHED exit code 0 is never worded as success or completion`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            postedRunning,
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        val exit = action as AgentRuntimeNotificationMapping.Action.ShowExitFact
        for (banned in listOf("complet", "success", "finish", "failed", "agent")) {
            assertNotEquals(
                "exit wording must not contain '$banned'",
                true,
                exit.text.lowercase().contains(banned) || exit.title.lowercase().contains(banned),
            )
        }
    }

    @Test
    fun `SESSION_REMOVED after a running surface only cancels - the user closed the tab`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedRunning,
            sessionEnded(cause = AgentRuntimeEvent.SessionEnded.Cause.SESSION_REMOVED, exitStatus = null),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.Cancel(7L), action)
        assertTrue(memory.posted.isEmpty())
        assertTrue(7L in memory.ended)
    }

    @Test
    fun `SESSION_FINISHED for a never-announced session stays silent`() {
        // No surface was ever posted: no end-of-story notification either.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            empty,
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.None, action)
        assertTrue(7L in memory.ended)
    }

    @Test
    fun `the exit fact still fires after a NoLongerDetected withdrawal - the walk-away story`() {
        // running -> confirmed -> no longer detected (surface withdrawn) ->
        // session exits with code 0: the session's exit fact is the one
        // honest terminal statement, gated on the session having been
        // announced at some point (everPosted survives the withdrawal).
        val withdrawn = AgentRuntimeNotificationMapping.Memory(
            posted = emptyMap(),
            everPosted = setOf(7L),
        )
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            withdrawn,
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        assertTrue(action is AgentRuntimeNotificationMapping.Action.ShowExitFact)
    }

    // ------------------------------------------- 5. the tombstone (Part J)

    @Test
    fun `an ended session refuses every later event - stale deliveries can never resurrect a surface`() {
        val ended = AgentRuntimeNotificationMapping.Memory(ended = setOf(7L))
        for (event in listOf(
            confirmedRunning(),
            runtimeUnknown(),
            noLongerDetected(),
            sessionEnded(), // a duplicate terminal edge
            launched(),
        )) {
            val (memory, action) = AgentRuntimeNotificationMapping.reduce(ended, event)
            assertEquals("event ${event::class.simpleName} after the tombstone must be silent", AgentRuntimeNotificationMapping.Action.None, action)
            assertEquals(ended, memory)
        }
    }

    // -------------------------------- 6. multi-session isolation (Part G)

    @Test
    fun `sessions are isolated - one session's surface never leaks into another's`() {
        val sessionOneRunning = AgentRuntimeNotificationMapping.Memory(
            posted = mapOf(1L to AgentRuntimeNotificationMapping.PostedKind.RUNNING),
            everPosted = setOf(1L),
        )
        // Session 2's confirmation does not touch session 1's surface.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            sessionOneRunning,
            confirmedRunning(sessionId = 2L, knownAgent = agent(id = "kilo", name = "Kilo Code")),
        )
        assertEquals("Kilo Code is running", show(action).title)
        assertEquals(2L, show(action).sessionId)
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[1L],
        )
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[2L],
        )
        // Session 1's ending does not touch session 2's surface.
        val (_, endAction) = AgentRuntimeNotificationMapping.reduce(
            memory,
            sessionEnded(sessionId = 1L),
        )
        assertTrue(endAction is AgentRuntimeNotificationMapping.Action.ShowExitFact)
        assertEquals(1L, (endAction as AgentRuntimeNotificationMapping.Action.ShowExitFact).sessionId)
    }

    // ------------------------------------------------ 7. purity (Part L)

    @Test
    fun `reduce is deterministic - identical inputs always produce identical outputs`() {
        val event = confirmedRunning()
        val first = AgentRuntimeNotificationMapping.reduce(empty, event)
        val second = AgentRuntimeNotificationMapping.reduce(empty, event)
        assertEquals(first, second)

        val endFirst = AgentRuntimeNotificationMapping.reduce(postedRunning, sessionEnded())
        val endSecond = AgentRuntimeNotificationMapping.reduce(postedRunning, sessionEnded())
        assertEquals(endFirst, endSecond)
    }

    @Test
    fun `reduce never mutates the memory it is given`() {
        val before = postedRunning
        AgentRuntimeNotificationMapping.reduce(before, runtimeUnknown())
        AgentRuntimeNotificationMapping.reduce(before, noLongerDetected())
        AgentRuntimeNotificationMapping.reduce(before, sessionEnded())
        assertEquals(postedRunning, before)
    }

    // ------------------------------------- 8. the honesty sweep (the line)

    @Test
    fun `honesty sweep - no produced wording over a full session story claims completion`() {
        // The full lifecycle story of one session, folded in order, with
        // every produced string checked against the banned vocabulary.
        var memory = empty
        val produced = mutableListOf<Pair<String, String>>()
        val story = listOf(
            launched(),
            confirmedRunning(), // birth UNKNOWN -> RUNNING
            confirmedRunning(), // repeat: no-op
            runtimeUnknown(), // RUNNING -> UNKNOWN
            confirmedRunning(from = AgentRuntimeState.UNKNOWN), // recovery
            noLongerDetected(), // RUNNING -> NOT_RUNNING
            confirmedRunning(from = AgentRuntimeState.NOT_RUNNING), // reappearance
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        for (event in story) {
            val (next, action) = AgentRuntimeNotificationMapping.reduce(memory, event)
            memory = next
            when (action) {
                is AgentRuntimeNotificationMapping.Action.ShowRuntime ->
                    produced += action.title to action.text
                is AgentRuntimeNotificationMapping.Action.ShowExitFact ->
                    produced += action.title to action.text
                else -> Unit
            }
        }
        // The story produced surfaces (the matrix is not trivially silent).
        assertTrue(produced.isNotEmpty())
        for ((title, text) in produced) {
            val wording = "${title.lowercase()} ${text.lowercase()}"
            for (banned in listOf("complet", "success", "succeed", "finish", "failed", "task done", "work done")) {
                assertTrue(
                    "produced wording must never claim '$banned': \"$title\" / \"$text\"",
                    !wording.contains(banned),
                )
            }
        }
        // And the final produced surface is the factual exit statement.
        assertEquals("Session ended", produced.last().first)
        assertEquals("Terminal session 7 exited (code 0)", produced.last().second)
    }

    @Test
    fun `the exit title is a session statement - the constant is pinned`() {
        assertEquals("Session ended", AgentRuntimeNotificationMapping.EXIT_TITLE)
    }
}
