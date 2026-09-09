package app.pocketshell.notifications

import app.pocketshell.terminal.AgentMatchedBy
import app.pocketshell.terminal.AgentRuntimeDetection
import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentRuntimeState
import app.pocketshell.terminal.ExitStatus
import app.pocketshell.terminal.LaunchIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P6 — the DEVICE-VISIBLE TRANSITION MATRIX (pure layer, fold-based).
 *
 * P6 is a verification phase: the P3c event engine and the P4 truth
 * contract ALREADY implement every runtime state transition; the P5 tap
 * routing sits on top unchanged. This file adds the one coverage the P4
 * test did not pin as a CONTINUOUS STORY — that the whole device-visible
 * state flow (running → unknown → running → withdrawn → reappeared →
 * session ended) plays out on ONE stable notification identity per
 * session, with in-place updates, no duplicates, no accumulated surfaces,
 * and honesty-preserving wording at every step:
 *
 *   - the RUNNING → UNKNOWN and UNKNOWN → RUNNING arms update the SAME
 *     id in place ([NotificationIds.agentRuntime] is the one identity —
 *     the coordinator derives it deterministically from the session id
 *     every action carries);
 *   - RUNNING → NOT_RUNNING and UNKNOWN → NOT_RUNNING are CANCELLATIONS
 *     on that same id — no replacement surface ever exists;
 *   - UNKNOWN at session end still yields the one factual exit statement
 *     (an unknown surface is an announced surface; everPosted gates it);
 *   - repeated identical event deliveries (the defensive-dedup layer's
 *     contract — the P3c engine already dedups state re-observations)
 *     repost nothing;
 *   - exit code 0 through the whole story is never worded success or
 *     completion.
 *
 * Everything here is the pure [AgentRuntimeNotificationMapping.reduce]
 * fold — no Android, no coroutine, no clock — so the matrix runs on the
 * JVM while the §55 device gate owns the hardware verification of the
 * naturally reproducible subset.
 */
class AgentRuntimeNotificationTransitionMatrixTest {

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

    private fun launched(sessionId: Long = 7L) =
        AgentRuntimeEvent.Launched(sessionId, agent(), occurredAtMs = 1_000L)

    private fun confirmedRunning(
        sessionId: Long = 7L,
        from: AgentRuntimeState? = AgentRuntimeState.UNKNOWN,
    ) = AgentRuntimeEvent.ConfirmedRunning(
        sessionId, agent(), from, evidence(), occurredAtMs = 2_000L,
    )

    private fun runtimeUnknown(
        sessionId: Long = 7L,
        from: AgentRuntimeState = AgentRuntimeState.RUNNING,
    ) = AgentRuntimeEvent.RuntimeUnknown(sessionId, agent(), from, occurredAtMs = 3_000L)

    private fun noLongerDetected(
        sessionId: Long = 7L,
        from: AgentRuntimeState = AgentRuntimeState.RUNNING,
    ) = AgentRuntimeEvent.NoLongerDetected(sessionId, agent(), from, evidence(), occurredAtMs = 4_000L)

    private fun sessionEnded(
        sessionId: Long = 7L,
        cause: AgentRuntimeEvent.SessionEnded.Cause =
            AgentRuntimeEvent.SessionEnded.Cause.SESSION_FINISHED,
        exitStatus: ExitStatus? = ExitStatus.Exited(0),
    ) = AgentRuntimeEvent.SessionEnded(
        sessionId, agent(), cause, exitStatus, AgentRuntimeState.RUNNING, occurredAtMs = 5_000L,
    )

    private val empty = AgentRuntimeNotificationMapping.Memory()

    private val postedRunning = AgentRuntimeNotificationMapping.Memory(
        posted = mapOf(7L to AgentRuntimeNotificationMapping.PostedKind.RUNNING),
        everPosted = setOf(7L),
    )

    private val postedUnknown = AgentRuntimeNotificationMapping.Memory(
        posted = mapOf(7L to AgentRuntimeNotificationMapping.PostedKind.UNKNOWN),
        everPosted = setOf(7L),
    )

    /** The one notification identity the whole P6 story must land on. */
    private val expectedId: Int = NotificationIds.agentRuntime(7L)

    /** Fold a story in order; return the final memory and every action. */
    private fun fold(
        start: AgentRuntimeNotificationMapping.Memory,
        events: List<AgentRuntimeEvent>,
    ): Pair<AgentRuntimeNotificationMapping.Memory, List<AgentRuntimeNotificationMapping.Action>> {
        var memory = start
        val actions = mutableListOf<AgentRuntimeNotificationMapping.Action>()
        for (event in events) {
            val (next, action) = AgentRuntimeNotificationMapping.reduce(memory, event)
            memory = next
            actions += action
        }
        return memory to actions
    }

    private fun shows(actions: List<AgentRuntimeNotificationMapping.Action>) =
        actions.filterIsInstance<AgentRuntimeNotificationMapping.Action.ShowRuntime>()

    private fun exits(actions: List<AgentRuntimeNotificationMapping.Action>) =
        actions.filterIsInstance<AgentRuntimeNotificationMapping.Action.ShowExitFact>()

    private fun cancels(actions: List<AgentRuntimeNotificationMapping.Action>) =
        actions.filterIsInstance<AgentRuntimeNotificationMapping.Action.Cancel>()

    /**
     * Every notification-bearing action of a story carries the SAME
     * session id — so the coordinator's deterministic derivation lands
     * every one of them on the ONE id ([expectedId]). This is the
     * identity-stability pin across state transitions.
     */
    private fun assertOneIdentity(actions: List<AgentRuntimeNotificationMapping.Action>) {
        val ids = actions.map { action ->
            when (action) {
                is AgentRuntimeNotificationMapping.Action.ShowRuntime ->
                    NotificationIds.agentRuntime(action.sessionId)
                is AgentRuntimeNotificationMapping.Action.ShowExitFact ->
                    NotificationIds.agentRuntime(action.sessionId)
                is AgentRuntimeNotificationMapping.Action.Cancel ->
                    NotificationIds.agentRuntime(action.sessionId)
                AgentRuntimeNotificationMapping.Action.None -> null
            }
        }.filterNotNull()
        assertTrue("the story must touch the notification identity at all", ids.isNotEmpty())
        assertEquals(
            "every notification action must land on the ONE deterministic identity",
            setOf(expectedId),
            ids.toSet(),
        )
    }

    private fun assertNoBannedWording(actions: List<AgentRuntimeNotificationMapping.Action>) {
        val produced = shows(actions).map { it.title to it.text } +
            exits(actions).map { it.title to it.text }
        for ((title, text) in produced) {
            val wording = "${title.lowercase()} ${text.lowercase()}"
            for (banned in listOf(
                "complet", "success", "succeed", "finish", "failed",
                "waiting for input", "needs input",
            )) {
                assertTrue(
                    "produced wording must never claim '$banned': \"$title\" / \"$text\"",
                    !wording.contains(banned),
                )
            }
        }
    }

    // --------------------------------------- A. the in-place update arms

    @Test
    fun `RUNNING to UNKNOWN updates the surface in place on the same identity - uncertainty-only wording`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(postedRunning, runtimeUnknown())
        val show = action as AgentRuntimeNotificationMapping.Action.ShowRuntime
        // The SAME identity: the id the coordinator derives from this
        // action's session id is the id the running surface already used.
        assertEquals(expectedId, NotificationIds.agentRuntime(show.sessionId))
        assertEquals("Claude Code runtime unknown", show.title)
        assertTrue(show.text.contains("cannot be verified"))
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.UNKNOWN,
            memory.posted[7L],
        )
        // Uncertainty stays uncertainty — the update never dresses the
        // unknown as a stopped/finished/failed state.
        val wording = "${show.title.lowercase()} ${show.text.lowercase()}"
        for (banned in listOf("complet", "success", "finish", "failed", "stopped", "not running")) {
            assertTrue("unknown wording must not claim '$banned'", !wording.contains(banned))
        }
    }

    @Test
    fun `UNKNOWN back to RUNNING restores the running surface on the same identity`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedUnknown,
            confirmedRunning(from = AgentRuntimeState.UNKNOWN),
        )
        val show = action as AgentRuntimeNotificationMapping.Action.ShowRuntime
        assertEquals(expectedId, NotificationIds.agentRuntime(show.sessionId))
        assertEquals("Claude Code is running", show.title)
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[7L],
        )
        assertTrue(7L in memory.everPosted)
    }

    @Test
    fun `the RUNNING-UNKNOWN-RUNNING cycle is ONE notification updated in place - no duplicate, no second identity`() {
        val (memory, actions) = fold(
            postedRunning,
            listOf(
                runtimeUnknown(), // RUNNING -> UNKNOWN
                confirmedRunning(from = AgentRuntimeState.UNKNOWN), // UNKNOWN -> RUNNING
            ),
        )
        val shows = shows(actions)
        assertEquals("the cycle yields exactly the two in-place updates", 2, shows.size)
        assertEquals("Claude Code runtime unknown", shows[0].title)
        assertEquals("Claude Code is running", shows[1].title)
        assertTrue("no cancellation rides the cycle", cancels(actions).isEmpty())
        assertOneIdentity(actions)
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[7L],
        )
        assertEquals(setOf(7L), memory.everPosted)
    }

    // --------------------------------- B. the withdrawal arms (cancels)

    @Test
    fun `RUNNING to NOT_RUNNING cancels the identity - no replacement surface exists`() {
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedRunning,
            noLongerDetected(from = AgentRuntimeState.RUNNING),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.Cancel(7L), action)
        assertEquals(expectedId, NotificationIds.agentRuntime(7L))
        assertTrue("the withdrawal posts nothing in its place", memory.posted.isEmpty())
        assertTrue(7L in memory.everPosted)
    }

    @Test
    fun `UNKNOWN to NOT_RUNNING cancels too - uncertainty withdraws without an absence claim`() {
        // The arm P6 adds to the pinned matrix: a session sitting on the
        // unknown surface whose runtime then becomes PROVABLY absent.
        // The honest result is the same withdrawal — the unknown surface
        // is cancelled, nothing replaces it, and no "stopped"/"ended"
        // wording is manufactured.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedUnknown,
            noLongerDetected(from = AgentRuntimeState.UNKNOWN),
        )
        assertEquals(AgentRuntimeNotificationMapping.Action.Cancel(7L), action)
        assertTrue(memory.posted.isEmpty())
        assertTrue(7L in memory.everPosted)
    }

    // --------------------------------- C. unknown at the session's end

    @Test
    fun `UNKNOWN at session end replaces the surface with the factual exit statement`() {
        // The unknown surface was announced (everPosted), so the session's
        // waitpid-proven end is still the one honest terminal statement —
        // exactly as after a running surface.
        val (memory, action) = AgentRuntimeNotificationMapping.reduce(
            postedUnknown,
            sessionEnded(exitStatus = ExitStatus.Exited(0)),
        )
        val exit = action as AgentRuntimeNotificationMapping.Action.ShowExitFact
        assertEquals(expectedId, NotificationIds.agentRuntime(exit.sessionId))
        assertEquals("Session ended", exit.title)
        assertEquals("Terminal session 7 exited (code 0)", exit.text)
        assertTrue(memory.posted.isEmpty())
        assertTrue(7L in memory.ended)
    }

    @Test
    fun `UNKNOWN at a signaled session end states the signal - still session-scoped`() {
        val (_, action) = AgentRuntimeNotificationMapping.reduce(
            postedUnknown,
            sessionEnded(exitStatus = ExitStatus.Signaled(9)),
        )
        val exit = action as AgentRuntimeNotificationMapping.Action.ShowExitFact
        assertEquals("Terminal session 7 was terminated by signal 9", exit.text)
    }

    // ---------------------------------------- D. duplicate delivery (Part G)

    @Test
    fun `duplicate delivery storm - repeated identical events between real transitions repost nothing`() {
        // Models the defensive-dedup contract at the notification layer
        // (the P3c engine already dedups state re-observations upstream):
        // a burst of repeated identical deliveries around the two real
        // transitions must produce exactly one surface per REAL state.
        val (_, actions) = fold(
            empty,
            listOf(
                launched(), // silent
                confirmedRunning(), // the real RUNNING entry
                confirmedRunning(), // duplicate delivery x3
                confirmedRunning(),
                confirmedRunning(),
                runtimeUnknown(), // the real UNKNOWN transition
                runtimeUnknown(), // duplicate delivery x2
                runtimeUnknown(),
                confirmedRunning(from = AgentRuntimeState.UNKNOWN), // recovery
            ),
        )
        val shows = shows(actions)
        assertEquals(
            "exactly three surfaces for three real states (running, unknown, running)",
            3,
            shows.size,
        )
        assertEquals("Claude Code is running", shows[0].title)
        assertEquals("Claude Code runtime unknown", shows[1].title)
        assertEquals("Claude Code is running", shows[2].title)
        assertOneIdentity(actions)
    }

    @Test
    fun `runtime flapping never accumulates notifications - every update lands on the one identity`() {
        // R → U → R → U → R: each flap is a REAL transition (one in-place
        // update each), never a second notification, never a tombstone.
        val (memory, actions) = fold(
            postedRunning,
            listOf(
                runtimeUnknown(),
                confirmedRunning(from = AgentRuntimeState.UNKNOWN),
                runtimeUnknown(),
                confirmedRunning(from = AgentRuntimeState.UNKNOWN),
            ),
        )
        assertEquals(4, shows(actions).size)
        assertTrue(cancels(actions).isEmpty())
        assertTrue(exits(actions).isEmpty())
        assertOneIdentity(actions)
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            memory.posted[7L],
        )
        assertTrue(7L !in memory.ended)
    }

    // --------------------------------- E. the §55 device story, folded

    @Test
    fun `the P6 device story end to end keeps one identity and honest words through exit 3`() {
        // The §55 hardware sequence: spawn → running appears → (ticks)
        // → agent quits inside the live session (withdrawal) → relaunch
        // in the SAME session (reappear) → `exit 3` closes the session
        // (factual exit statement). The fold proves the notification
        // contract for the whole story before the device gate runs it.
        val (memory, actions) = fold(
            empty,
            listOf(
                launched(),
                confirmedRunning(), // birth UNKNOWN -> RUNNING
                noLongerDetected(from = AgentRuntimeState.RUNNING), // agent quits, session lives
                confirmedRunning(from = AgentRuntimeState.NOT_RUNNING), // relaunch in-session
                sessionEnded(exitStatus = ExitStatus.Exited(3)), // `exit 3`
            ),
        )
        assertEquals("two running surfaces (first + reappear)", 2, shows(actions).size)
        assertEquals("exactly one withdrawal", 1, cancels(actions).size)
        val reShow = shows(actions)
        val exit = exits(actions)
        assertEquals("Claude Code is running", reShow[0].title)
        assertEquals("Claude Code is running", reShow[1].title)
        assertEquals("Session ended", exit.single().title)
        assertEquals("Terminal session 7 exited (code 3)", exit.single().text)
        assertOneIdentity(actions)
        assertTrue("the session is tombstoned", 7L in memory.ended)
        assertTrue(memory.posted.isEmpty())
        assertNoBannedWording(actions)
    }

    @Test
    fun `exit code 0 through the device story is never worded success or completion`() {
        val (_, actions) = fold(
            empty,
            listOf(
                launched(),
                confirmedRunning(),
                sessionEnded(exitStatus = ExitStatus.Exited(0)),
            ),
        )
        val exit = exits(actions).single()
        assertEquals("Terminal session 7 exited (code 0)", exit.text)
        assertNoBannedWording(actions)
        val wording = "${exit.title.lowercase()} ${exit.text.lowercase()}"
        for (banned in listOf("complet", "success", "succeed", "finish", "failed")) {
            assertTrue("exit-0 wording must not contain '$banned'", !wording.contains(banned))
        }
    }
}
