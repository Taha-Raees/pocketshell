package app.pocketshell.terminal

/**
 * M7.2 P10 — the AGENT-NATIVE SIGNAL layer (pure model).
 *
 * THE PROBLEM THIS SOLVES. P7's verdict stands untouched: generic terminal
 * observation cannot honestly produce "waiting for input" or "finished".
 * But the AGENTS THEMSELVES expose exactly those moments through their own
 * structured notification mechanisms (Claude Code hooks, Codex notify,
 * OpenCode plugin events, ZCode hooks — each verified experimentally on
 * Linux, docs/M7.2-P10-AGENT-SIGNAL-BRIDGE.md). P9 built the session-bound
 * launch-record channel and left it "an adapter phase away" from carrying
 * agent-native events. THIS file is that phase's pure core.
 *
 * THE SHAPE. An agent signal is a third record in the session's own
 * launch-record file (the P9 JSONL channel — same file, same generation
 * semantics, same app-private host path):
 *
 *   {"t":"signal","agent":"<token>","kind":"<kind>","seq":<n>,
 *    "pid":<parent-pid>,"start":<parent-starttime>,"data":"<payload>"}
 *
 * written by the staged bridge emitter (assets/agentbridge/ps-emit.sh)
 * that the agent's own hook/plugin/notify configuration invokes. The
 * staging is per-launch (the record file's token directory), so a signal
 * is bound to ONE session's ONE generation by construction: the emitter
 * only ever knows the file path that launch staged for it, and a deleted
 * file ends the channel (the P9 PART O model, unchanged).
 *
 * CORRELATION (the PART T boundary, carried over). A signal record claims
 * more than a file write: it claims to come FROM the agent. The emitter
 * records its parent process's pid + /proc starttime; the acceptance rule
 * below credits a signal ONLY when that parent is provably the session's
 * anchored agent (pid + birth stamp — the same pid-reuse-proof identity
 * the launch anchor validated) or a live process inside the session's own
 * correlation domain. A forged, stale, recycled-pid or foreign signal is
 * DROPPED — never interpreted.
 *
 * HONESTY (the P7 wording rules, carried over). A signal is the agent's
 * OWN structured declaration, so states that were never honestly
 * derivable become derivable where — and only where — the agent declares
 * them: a PermissionRequest/notification_type=permission_prompt payload
 * proves "requesting permission"; an idle_prompt payload proves "waiting
 * for input"; a turn-complete/Stop payload proves "the agent ended its
 * turn". Nothing maps exit codes to success/failure (PART P unchanged);
 * no agent-declared state is ever widened beyond what its payload says.
 *
 * Everything here is DATA plus PURE functions: no clock, no I/O, no
 * Android APIs, no state — unit-testable without a device (the phase
 * discipline every M7.2 layer follows).
 */
object AgentSignalBridge {

    // ---------------------------------------------------------------------
    // Vocabulary
    // ---------------------------------------------------------------------

    /**
     * The fixed signal vocabulary the bridge understands. The staged
     * emitters only ever emit these tokens; anything else is dropped by
     * the parser (an unknown kind from a future agent version must never
     * be invented into a state).
     */
    enum class AgentSignalKind(val token: String) {
        /** The agent's session started (its bridge is live). */
        SESSION_START("session_start"),

        /** The agent is doing work (tool call / turn activity). */
        WORKING("working"),

        /** The agent asked for permission (its own permission event). */
        PERMISSION_REQUEST("permission_request"),

        /**
         * The agent declared an attention need whose precise flavor rides
         * in [SignalRecord.data] (e.g. Claude Code's Notification hook:
         * notification_type=permission_prompt vs idle_prompt).
         */
        ATTENTION("attention"),

        /** The agent declared its turn ended (Codex agent-turn-complete, Claude Stop). */
        TURN_COMPLETE("turn_complete"),

        /** The agent declared its own session ended (clears pending attention). */
        SESSION_END("session_end"),

        ;

        companion object {
            /** Exact-token lookup; null for unknown tokens (never guessed). */
            fun fromToken(token: String): AgentSignalKind? =
                entries.firstOrNull { it.token == token }
        }
    }

    /**
     * The attention axis the signal layer feeds — ORTHOGONAL to the P3b
     * four-state runtime contract (RUNNING etc. describe the process; this
     * describes what the agent SAYS it is waiting on). NONE is the absent
     * state; the two attention flavors are exactly the two claims an
     * agent-native payload can prove, and no wider claim exists.
     */
    enum class AgentAttentionPhase {
        NONE,

        /** The agent proved it is asking for permission. */
        PERMISSION_REQUEST,

        /** The agent proved it is waiting for the user's input. */
        INPUT_REQUIRED,
    }

    /**
     * One parsed signal record. [lineIndex] is the record's position in the
     * session's record file — the append order IS the ordering authority
     * ([seq] is the emitter's advisory hint only); the engine consumes
     * signals strictly in line order and remembers the last consumed index
     * (a signal is consumed exactly once per session generation).
     */
    data class SignalRecord(
        val agent: String,
        val kind: AgentSignalKind,
        val seq: Int,
        /** The emitter's parent pid (the agent process) at emit time. */
        val parentPid: Int,
        /** The parent's /proc stat field-22 birth stamp at emit time. */
        val parentStartTicks: Long,
        /** The raw agent payload, single-line escaped; empty when none. */
        val data: String,
        val lineIndex: Int,
    )

    // ---------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------

    /**
     * Strict signal-line parser: accepts exactly the shape the emitter
     * writes and nothing else. Wrong prefix, unknown kind, missing or
     * out-of-domain fields — all drop to null (missing evidence is never
     * upgraded; the same rule as the P9 parsers).
     */
    fun parseSignal(line: String, lineIndex: Int): SignalRecord? {
        if (!line.startsWith("{\"t\":\"signal\"")) return null
        val agent = stringField(line, "agent") ?: return null
        val kindToken = rawStringField(line, "kind") ?: return null
        val kind = AgentSignalKind.fromToken(kindToken) ?: return null
        val seq = intField(line, "seq") ?: return null
        val pid = intField(line, "pid") ?: return null
        val start = longField(line, "start") ?: return null
        val data = unescape(dataField(line))
        if (agent.isEmpty() || seq < 0 || pid < 0 || start < 0L) return null
        return SignalRecord(
            agent = agent,
            kind = kind,
            seq = seq,
            parentPid = pid,
            parentStartTicks = start,
            data = data,
            lineIndex = lineIndex,
        )
    }

    private fun intField(line: String, name: String): Int? =
        Regex("\"$name\":(-?[0-9]+)").find(line)?.groupValues?.get(1)?.toIntOrNull()

    private fun longField(line: String, name: String): Long? =
        Regex("\"$name\":(-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLongOrNull()

    /** Agent tokens are constrained like the P9 record tokens. */
    private fun stringField(line: String, name: String): String? =
        Regex("\"$name\":\"([a-z0-9._+%-]*)\"").find(line)?.groupValues?.get(1)?.let { if (it.isEmpty()) null else it }

    /** Kind tokens are the fixed vocabulary — no empties. */
    private fun rawStringField(line: String, name: String): String? =
        Regex("\"$name\":\"([a-z_]+)\"").find(line)?.groupValues?.get(1)

    /**
     * The data payload is the LAST field before the closing brace and may
     * contain any escaped content — capture greedily to the final quote.
     */
    private fun dataField(line: String): String =
        Regex("\"data\":\"(.*)\"\\}").find(line)?.groupValues?.get(1) ?: ""

    /**
     * Reverse of the emitter's escaping (\\ → \, \" → "), left-to-right so
     * sequences like \\\" decode exactly once. Unknown escapes keep both
     * characters (the emitter emits none; a forged one is data, not syntax).
     */
    fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next == '"' || next == '\\') {
                    out.append(next)
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    // ---------------------------------------------------------------------
    // Acceptance — the correlation gate
    // ---------------------------------------------------------------------

    /**
     * What a signal needs proven about its origin before it may speak for
     * a session. Everything here is validated against LIVE /proc truth and
     * the session's OWN launch anchor — the same trust class the P9 anchor
     * validation uses.
     */
    data class AcceptanceContext(
        /** The session's launch-record ANCHOR (null when the launch recorded none). */
        val anchor: AgentLaunchRecords.LaunchRecord?,
        /** The agent token the signal must name (the session's resolved identity). */
        val expectedAgentToken: String,
        /** The live /proc snapshot (null = scanner unavailable). */
        val byPid: Map<Int, ProcfsProcess>?,
        /** The session's correlation domain (pids) from the same snapshot. */
        val correlatedPids: Set<Int>,
    )

    /**
     * Accept ONE signal for a session only when every claim it makes is
     * provable RIGHT NOW:
     *
     *  1. GENERATION: the caller only ever offers signals parsed from the
     *     session's own record file (structural, enforced by the detector's
     *     seam — a signal has no session id of its own; its file is its
     *     binding).
     *  2. IDENTITY: the named agent token equals the session's expected
     *     agent — a record can never speak for a different agent.
     *  3. ORIGIN: the recorded parent (the process that ran the emitter)
     *     is EITHER the launch anchor itself (pid + birth stamp match —
     *     pid-reuse-proofed, alive or freshly dead) OR a process alive in
     *     the live snapshot with the SAME recorded birth stamp inside the
     *     session's correlation domain. Both arms tie the signal to THE
     *     process tree this session launched; a foreign or recycled pid
     *     fails one of them.
     *
     * Unknown/blank expected token rejects (no identity, no claim).
     */
    fun accept(signal: SignalRecord, context: AcceptanceContext): Boolean {
        if (context.expectedAgentToken.isBlank()) return false
        if (signal.agent != context.expectedAgentToken) return false
        if (signal.parentPid <= 0) return false
        val anchor = context.anchor
        if (anchor != null &&
            signal.parentPid == anchor.pid &&
            signal.parentStartTicks == anchor.startTicks
        ) {
            return true
        }
        val byPid = context.byPid ?: return false // no live truth: the anchorless arm cannot fire
        val parent = byPid[signal.parentPid] ?: return false
        if (parent.state == 'Z') return false
        val liveStart = parent.startTime ?: return false
        if (liveStart != signal.parentStartTicks) return false // recycled pid: not the same process
        return signal.parentPid in context.correlatedPids
    }

    // ---------------------------------------------------------------------
    // Semantics — what a signal PROVES
    // ---------------------------------------------------------------------

    /**
     * The attention phase a signal proves. Attention-bearing kinds derive
     * their precise flavor from their payload; everything else CLEARs a
     * pending attention (the agent went back to work, ended its turn, or
     * ended its session — in all three a pending "come back" claim is no
     * longer true, and dropping it is the honest move).
     */
    fun attentionPhase(signal: SignalRecord): AgentAttentionPhase = when (signal.kind) {
        AgentSignalKind.PERMISSION_REQUEST -> AgentAttentionPhase.PERMISSION_REQUEST
        AgentSignalKind.ATTENTION ->
            if (signal.data.contains("\"notification_type\":\"permission_prompt") ||
                signal.data.contains("needs your permission")
            ) {
                AgentAttentionPhase.PERMISSION_REQUEST
            } else {
                AgentAttentionPhase.INPUT_REQUIRED
            }
        else -> AgentAttentionPhase.NONE
    }
}
