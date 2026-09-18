package app.pocketshell.widget

import app.pocketshell.terminal.AgentHomeSessionClaims

/**
 * M8 — client-side aggregation over the ONE authoritative agent-activity
 * projection (`AgentActivityRepository.homeSessionClaims`). This is NOT a
 * detector, NOT a second truth: it is a pure function of the claims Home
 * already renders (docs/M7.2-P8 §9 discipline — UI reads state, and the
 * projection IS the reconciliation authority).
 *
 * The row wording is character-for-character the Sessions section's parity
 * vocabulary ("— Running" / "— Runtime unknown" / "— Requesting
 * permission" / "— Needs your input"); no completion/success/waiting
 * wording exists here (the M7.2 honesty ban list).
 */
object AgentActivitySummary {

    data class Row(
        val sessionId: Long,
        val line: String,
        val attention: Boolean,
        /** false = the runtime-unknown tier (dim presentation). */
        val proven: Boolean,
    )

    data class Summary(
        /** Claims backed by PROOF of activity (running or an attention signal). */
        val activeCount: Int,
        /** Claims whose runtime truth is unknown (launched, never observed). */
        val unknownCount: Int,
        val rows: List<Row>,
    )

    fun summarize(claims: Map<Long, AgentHomeSessionClaims.SessionClaim>): Summary {
        val rows = claims.entries
            .map { (id, claim) ->
                Triple(
                    id,
                    claim,
                    Row(
                        sessionId = id,
                        line = when (claim.claim) {
                            AgentHomeSessionClaims.Claim.RUNNING ->
                                claim.agentDisplayName + " — Running"
                            AgentHomeSessionClaims.Claim.UNKNOWN ->
                                claim.agentDisplayName + " — Runtime unknown"
                            AgentHomeSessionClaims.Claim.ATTENTION_PERMISSION ->
                                claim.agentDisplayName + " — Requesting permission"
                            AgentHomeSessionClaims.Claim.ATTENTION_INPUT ->
                                claim.agentDisplayName + " — Needs your input"
                        },
                        attention = claim.claim == AgentHomeSessionClaims.Claim.ATTENTION_PERMISSION ||
                            claim.claim == AgentHomeSessionClaims.Claim.ATTENTION_INPUT,
                        proven = claim.claim != AgentHomeSessionClaims.Claim.UNKNOWN,
                    ),
                )
            }
            // Attention first (the card answers "does it need me"), then
            // proven-running, then unknown; stable by session id inside a tier.
            .sortedWith(
                compareBy(
                    { (_, claim, _) -> when (claim.claim) {
                        AgentHomeSessionClaims.Claim.ATTENTION_INPUT -> 0
                        AgentHomeSessionClaims.Claim.ATTENTION_PERMISSION -> 1
                        AgentHomeSessionClaims.Claim.RUNNING -> 2
                        AgentHomeSessionClaims.Claim.UNKNOWN -> 3
                    } },
                    { (id, _, _) -> id },
                ),
            )
            .map { (_, _, row) -> row }

        val unknown = claims.values.count { it.claim == AgentHomeSessionClaims.Claim.UNKNOWN }
        return Summary(
            activeCount = claims.size - unknown,
            unknownCount = unknown,
            rows = rows,
        )
    }

    /** The card's one honest headline line ("what is happening"). */
    fun headline(summary: Summary): String = when {
        summary.rows.isEmpty() -> "No agent sessions"
        summary.unknownCount == 0 -> "${summary.activeCount} active"
        summary.activeCount == 0 -> "${summary.unknownCount} unknown"
        else -> "${summary.activeCount} active · ${summary.unknownCount} unknown"
    }
}
