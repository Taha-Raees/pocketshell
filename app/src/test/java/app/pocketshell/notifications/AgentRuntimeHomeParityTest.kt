package app.pocketshell.notifications

import app.pocketshell.terminal.AgentEventInput
import app.pocketshell.terminal.AgentHomeSessionClaims
import app.pocketshell.terminal.AgentRuntimeDetection
import app.pocketshell.terminal.AgentRuntimeEvent
import app.pocketshell.terminal.AgentRuntimeEventMemory
import app.pocketshell.terminal.AgentRuntimeState
import app.pocketshell.terminal.AgentRuntimeTransitions
import app.pocketshell.terminal.ExitStatus
import app.pocketshell.terminal.FinishedAgentLaunch
import app.pocketshell.terminal.LaunchIdentity
import app.pocketshell.terminal.TrackedAgentLaunch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.2 P8 — the NOTIFICATION ⇄ HOME PARITY fold (the phase mandate's
 * PART N test 10): given the SAME authoritative input sequence — the
 * manager's session world (live / finished) and the detector's observation
 * snapshots — the notification layer (P3c engine → P4 mapping, folded
 * exactly as the shipped consumer folds it) and the Home Sessions claims
 * ([AgentHomeSessionClaims.present], the projection the new UI consumes)
 * must AGREE about whether an agent is currently running, unknown, or
 * absent — for every session, at every step of every story.
 *
 * Parity, precisely: for each session id,
 *
 *   notification memory posts RUNNING  ⟺  Home claims RUNNING
 *   notification memory posts UNKNOWN  ⟺  Home claims UNKNOWN
 *   notification memory posts nothing  ⟺  Home claims nothing
 *
 * The end-of-story shapes agree too and are asserted where they occur: a
 * finished announced session gets the one-shot factual exit statement in
 * the shade while the row already shows its session-level "(exited)" — both
 * sides drop the runtime claim; a never-announced session gets nothing on
 * either side.
 *
 * All PURE: no clock, no I/O, no Android, no coroutine. This is the test
 * that makes it structurally dishonest for Home to ever drift from the
 * shade (upgrade an UNKNOWN, keep a running claim past a withdrawal, or
 * show a claim the shade would not).
 */
class AgentRuntimeHomeParityTest {

    // ------------------------------------------------------------ fixtures

    private fun agent(name: String, id: String) = LaunchIdentity.KnownAgent(
        launcherId = id,
        displayName = name,
        command = "$id --run",
    )

    private val kilo = agent("Kilo Code", "kilo")
    private val agy = agent("Antigravity", "agy")
    private val claude = agent("Claude Code", "claude")

    private fun tracked(sessionId: Long, agent: LaunchIdentity.KnownAgent) =
        TrackedAgentLaunch(sessionId, agent)

    private fun homeSession(sessionId: Long, identity: LaunchIdentity?) =
        AgentHomeSessionClaims.HomeSessionInput(sessionId, identity)

    private fun observation(
        sessionId: Long,
        state: AgentRuntimeState,
        everObservedRunning: Boolean = state == AgentRuntimeState.RUNNING ||
            state == AgentRuntimeState.NOT_RUNNING,
    ) = AgentRuntimeDetection.Observation(
        sessionId = sessionId,
        state = state,
        evidence = if (state == AgentRuntimeState.RUNNING) {
            AgentRuntimeDetection.ProcessEvidence(
                pids = listOf(200 + sessionId.toInt()),
                grade = app.pocketshell.terminal.AgentMatchedBy.PROCFS_EXE,
                observedAtMs = 1_000L,
            )
        } else {
            null
        },
        everObservedRunning = everObservedRunning,
        updatedAtMs = 1_000L,
    )

    /**
     * One fold of the authoritative world into BOTH consumers, with the
     * parity assertion enforced after every step (any drift fails the story
     * at the exact step that produced it).
     */
    private class Harness {
        var engineMemory = AgentRuntimeEventMemory()
            private set
        var notificationMemory = AgentRuntimeNotificationMapping.Memory()
            private set
        val actions = mutableListOf<AgentRuntimeNotificationMapping.Action>()

        /**
         * @param live        the LIVE KnownAgent sessions (the engine view)
         * @param homeLive    ALL live sessions incl. plain shells (the Home view)
         * @param finished    the FINISHED KnownAgent sessions with their exit status
         * @param observations the detector's current snapshot (null = not re-delivered this step)
         */
        fun step(
            live: List<TrackedAgentLaunch> = emptyList(),
            homeLive: List<AgentHomeSessionClaims.HomeSessionInput> = emptyList(),
            finished: Map<Long, FinishedAgentLaunch> = emptyMap(),
            observations: Map<Long, AgentRuntimeDetection.Observation>? = null,
            nowMs: Long = 5_000L,
        ) {
            // 1. the engine's session-world fold (the manager's StateFlow emission)
            val sessionsOutcome = AgentRuntimeTransitions.reduce(
                engineMemory,
                AgentEventInput.SessionsChanged(live, finished),
                nowMs,
            )
            engineMemory = sessionsOutcome.memory
            var events: List<AgentRuntimeEvent> = sessionsOutcome.events

            // 2. the engine's evidence fold (the detector's StateFlow emission)
            if (observations != null) {
                val obsOutcome = AgentRuntimeTransitions.reduce(
                    engineMemory,
                    AgentEventInput.ObservationsChanged(observations),
                    nowMs,
                )
                engineMemory = obsOutcome.memory
                events = events + obsOutcome.events
            }

            // 3. the notification consumer's fold (one event at a time, exactly
            //    like AgentRuntimeNotificationConsumer.ensureStarted)
            for (event in events) {
                val (memory, action) = AgentRuntimeNotificationMapping.reduce(notificationMemory, event)
                notificationMemory = memory
                actions += action
            }

            // 4. the Home fold (the repository projection the UI collects)
            val homeClaims = AgentHomeSessionClaims.present(
                homeLive,
                observations ?: emptyMap(),
            )

            // 5. THE PARITY CONTRACT, session by session.
            val ids = notificationMemory.posted.keys + homeClaims.keys
            for (id in ids) {
                val posted = notificationMemory.posted[id]
                val homeClaim = homeClaims[id]?.claim
                when (posted) {
                    AgentRuntimeNotificationMapping.PostedKind.RUNNING ->
                        assertEquals(
                            "session $id: shade says running, Home must too",
                            AgentHomeSessionClaims.Claim.RUNNING,
                            homeClaim,
                        )
                    AgentRuntimeNotificationMapping.PostedKind.UNKNOWN ->
                        assertEquals(
                            "session $id: shade says unknown, Home must too",
                            AgentHomeSessionClaims.Claim.UNKNOWN,
                            homeClaim,
                        )
                    null ->
                        assertNull(
                            "session $id: shade is silent, Home must claim nothing",
                            homeClaim,
                        )
                }
            }
        }
    }

    // ------------------------------------------------ the §55 device story

    @Test
    fun `the running-withdrawal-reappearance-exit story keeps both views in agreement`() {
        val h = Harness()

        // Spawn: plain shell (1) + a Kilo Code launch (2). The shade is
        // silent (Launched posts nothing); Home claims nothing yet either.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
        )

        // Birth UNKNOWN (no evidence yet): still silent on both sides.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.UNKNOWN, everObservedRunning = false)),
        )

        // Confirmed running: the shade says "Kilo Code is running"; the row
        // claims the same agent, by the same registry name.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        assertEquals(
            AgentRuntimeNotificationMapping.PostedKind.RUNNING,
            h.notificationMemory.posted[2L],
        )

        // Evidence turns ambiguous: the shade downgrades to "<Name> runtime
        // unknown"; Home shows the SAME uncertainty (never a silent upgrade
        // back to running).
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.UNKNOWN, everObservedRunning = true)),
        )
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.UNKNOWN, h.notificationMemory.posted[2L])

        // Proven absence: the shade CANCELS; Home withdraws the claim too.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.NOT_RUNNING)),
        )
        assertTrue(2L !in h.notificationMemory.posted)

        // Reappearance: both sides state running again.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(1, null), homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[2L])

        // The session's process exits (waitpid-proven, exit 3): the runtime
        // claim drops on BOTH sides; the shade gets the one factual exit
        // statement and the row shows its own session-level end state.
        h.step(
            live = emptyList(),
            homeLive = listOf(homeSession(1, null)), // finished sessions leave the live list
            finished = mapOf(
                2L to FinishedAgentLaunch(2L, kilo, ExitStatus.Exited(3)),
            ),
        )
        assertTrue(2L !in h.notificationMemory.posted)

        // The exit action was the honest session-scoped statement.
        val exit = h.actions.filterIsInstance<AgentRuntimeNotificationMapping.Action.ShowExitFact>().single()
        assertEquals(2L, exit.sessionId)
        assertEquals("Session ended", exit.title)
        assertEquals("Terminal session 2 exited (code 3)", exit.text)

        // The tab is removed: nothing new on either side.
        h.step(live = emptyList(), homeLive = emptyList())
    }

    // ------------------------------------------ multi-session + mid removal

    @Test
    fun `three agent sessions agree independently and closing the middle one corrupts nothing`() {
        val h = Harness()
        val world = { obs2Unknown: Boolean ->
            Triple(
                listOf(tracked(1, kilo), tracked(2, agy), tracked(3, claude)),
                listOf(homeSession(1, kilo), homeSession(2, agy), homeSession(3, claude)),
                buildMap<Long, AgentRuntimeDetection.Observation> {
                    put(1L, observation(1, AgentRuntimeState.RUNNING))
                    put(
                        2L,
                        if (obs2Unknown) {
                            observation(2, AgentRuntimeState.UNKNOWN, everObservedRunning = true)
                        } else {
                            observation(2, AgentRuntimeState.RUNNING)
                        },
                    )
                    // session 3 not yet observed at all
                },
            )
        }

        h.step(live = world(false).first, homeLive = world(false).second)

        // Mixed states: 1 running, 2 running, 3 birth-silent.
        val (live, homeLive, obs) = world(false)
        h.step(live = live, homeLive = homeLive, observations = obs)
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[1L])
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[2L])
        assertTrue(3L !in h.notificationMemory.posted)

        // Session 2's evidence turns ambiguous.
        val (liveU, homeLiveU, obsU) = world(true)
        h.step(live = liveU, homeLive = homeLiveU, observations = obsU)
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.UNKNOWN, h.notificationMemory.posted[2L])

        // The MIDDLE session is closed: its claim drops everywhere, the
        // neighbors are untouched.
        h.step(
            live = listOf(tracked(1, kilo), tracked(3, claude)),
            homeLive = listOf(homeSession(1, kilo), homeSession(3, claude)),
            observations = mapOf(
                1L to observation(1, AgentRuntimeState.RUNNING),
                3L to observation(3, AgentRuntimeState.RUNNING),
            ),
        )
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[1L])
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[3L])
        assertTrue(2L !in h.notificationMemory.posted)
    }

    // ------------------------------------------------- the flapping storm

    @Test
    fun `runtime flapping R-U-R-U-R keeps both views aligned and never accumulates`() {
        val h = Harness()
        val live = listOf(tracked(2, kilo))
        val homeLive = listOf(homeSession(1, null), homeSession(2, kilo))

        val sequence = listOf(
            AgentRuntimeState.RUNNING,
            AgentRuntimeState.UNKNOWN,
            AgentRuntimeState.RUNNING,
            AgentRuntimeState.UNKNOWN,
            AgentRuntimeState.RUNNING,
        )
        for (state in sequence) {
            h.step(
                live = live,
                homeLive = homeLive,
                observations = mapOf(
                    2L to observation(
                        2,
                        state,
                        everObservedRunning = true, // the storm starts after proven presence
                    ),
                ),
            )
        }

        // Final state: exactly ONE running surface exists — the fold never
        // accumulates surfaces or tombstones, and Home matches it.
        assertEquals(AgentRuntimeNotificationMapping.PostedKind.RUNNING, h.notificationMemory.posted[2L])
        assertEquals(setOf(2L), h.notificationMemory.posted.keys)
    }

    // --------------------------------- the never-confirmed session's ending

    @Test
    fun `a session that never reached running ends silently on both sides`() {
        val h = Harness()

        // Launch + birth UNKNOWN, then the process exits before any scan
        // could confirm the agent: no surface was ever posted, so the shade
        // stays silent at the end (no uncontextualized exit statement), and
        // Home never claimed anything either.
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.UNKNOWN, everObservedRunning = false)),
        )
        h.step(
            live = emptyList(),
            homeLive = emptyList(),
            finished = mapOf(
                2L to FinishedAgentLaunch(2L, kilo, ExitStatus.Exited(0)),
            ),
        )
        assertTrue(h.notificationMemory.posted.isEmpty())
        assertTrue(2L !in h.notificationMemory.everPosted)
        assertTrue(
            h.actions.none { it is AgentRuntimeNotificationMapping.Action.ShowRuntime } &&
                h.actions.none { it is AgentRuntimeNotificationMapping.Action.ShowExitFact },
        )
    }

    // ------------------------------------------- the exit-0 honesty pairing

    @Test
    fun `exit code zero still ends in the factual session wording on both views`() {
        val h = Harness()

        // Reach running first (the exit fact needs an announced story).
        h.step(
            live = listOf(tracked(2, kilo)),
            homeLive = listOf(homeSession(2, kilo)),
            observations = mapOf(2L to observation(2, AgentRuntimeState.RUNNING)),
        )
        // Then finish with the direct child's exit 0.
        h.step(
            live = emptyList(),
            homeLive = emptyList(),
            finished = mapOf(
                2L to FinishedAgentLaunch(2L, kilo, ExitStatus.Exited(0)),
            ),
        )
        val exit = h.actions.filterIsInstance<AgentRuntimeNotificationMapping.Action.ShowExitFact>().single()
        // "exited (code 0)" — never success/completion wording.
        assertEquals("Terminal session 2 exited (code 0)", exit.text)
        assertTrue(2L !in h.notificationMemory.posted)
    }
}
