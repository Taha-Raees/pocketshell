package app.pocketshell.widget

import app.pocketshell.terminal.AgentHomeSessionClaims.Claim
import app.pocketshell.terminal.AgentHomeSessionClaims.SessionClaim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — the Agents widget's pure aggregation over the ONE authoritative
 * claims projection. The wording pins mirror the Sessions section's parity
 * vocabulary (the M7.2 honesty contract), and the headline counts PROVEN
 * activity only.
 */
class AgentActivitySummaryTest {

    @Test
    fun `rows use the exact sessions-section parity wording`() {
        val summary = AgentActivitySummary.summarize(
            mapOf(
                1L to SessionClaim(1, "Kilo Code", Claim.RUNNING),
                2L to SessionClaim(2, "Cline", Claim.UNKNOWN),
                3L to SessionClaim(3, "Hermes", Claim.ATTENTION_PERMISSION),
                4L to SessionClaim(4, "Agy", Claim.ATTENTION_INPUT),
            ),
        )
        val lines = summary.rows.map { it.line }
        assertTrue(lines.contains("Kilo Code — Running"))
        assertTrue(lines.contains("Cline — Runtime unknown"))
        assertTrue(lines.contains("Hermes — Requesting permission"))
        assertTrue(lines.contains("Agy — Needs your input"))
    }

    @Test
    fun `attention sorts first, then running, then unknown`() {
        val summary = AgentActivitySummary.summarize(
            mapOf(
                1L to SessionClaim(1, "A", Claim.UNKNOWN),
                2L to SessionClaim(2, "B", Claim.RUNNING),
                3L to SessionClaim(3, "C", Claim.ATTENTION_INPUT),
                4L to SessionClaim(4, "D", Claim.ATTENTION_PERMISSION),
                5L to SessionClaim(5, "E", Claim.RUNNING),
            ),
        )
        assertEquals(
            listOf("C", "D", "B", "E", "A"),
            summary.rows.map { it.line.substringBefore(" —") },
        )
    }

    @Test
    fun `the headline counts proven activity and keeps unknowns honest`() {
        assertEquals(
            "No agent sessions",
            AgentActivitySummary.headline(AgentActivitySummary.summarize(emptyMap())),
        )
        val allRunning = AgentActivitySummary.summarize(
            mapOf(1L to SessionClaim(1, "A", Claim.RUNNING), 2L to SessionClaim(2, "B", Claim.RUNNING)),
        )
        assertEquals("2 active", AgentActivitySummary.headline(allRunning))
        val mixed = AgentActivitySummary.summarize(
            mapOf(1L to SessionClaim(1, "A", Claim.RUNNING), 2L to SessionClaim(2, "B", Claim.UNKNOWN)),
        )
        assertEquals("1 active · 1 unknown", AgentActivitySummary.headline(mixed))
        val onlyUnknown = AgentActivitySummary.summarize(
            mapOf(1L to SessionClaim(1, "A", Claim.UNKNOWN)),
        )
        assertEquals("1 unknown", AgentActivitySummary.headline(onlyUnknown))
    }

    @Test
    fun `proven and attention flags carry the presentation contract`() {
        val summary = AgentActivitySummary.summarize(
            mapOf(
                1L to SessionClaim(1, "A", Claim.RUNNING),
                2L to SessionClaim(2, "B", Claim.UNKNOWN),
                3L to SessionClaim(3, "C", Claim.ATTENTION_INPUT),
            ),
        )
        val byId = summary.rows.associateBy { it.sessionId }
        assertTrue(byId[1]!!.proven && !byId[1]!!.attention)
        assertFalse(byId[2]!!.proven)
        assertTrue(byId[3]!!.proven && byId[3]!!.attention)
    }

    @Test
    fun `no completion success failure or waiting wording can ship`() {
        // The M7.2 honesty ban list, over every literal the widget domain
        // produces (wording is carried by summarize + headline only).
        val banned = listOf(
            "completed", "completion", "success", "succeed", "finish",
            "failed", "failure", "waiting for input", "needs input", "needs attention",
        )
        val claims: Map<Long, SessionClaim> = Claim.entries.associate { claim ->
            claim.ordinal.toLong() to SessionClaim(claim.ordinal.toLong(), "X", claim)
        }
        val produced = buildList {
            AgentActivitySummary.summarize(claims).rows.forEach { add(it.line) }
            add(AgentActivitySummary.headline(AgentActivitySummary.summarize(claims)))
        }
        val violations = produced.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue("honesty sweep violations: $violations", violations.isEmpty())
    }
}
