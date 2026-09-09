package app.pocketshell.terminal

import java.io.File

/**
 * M7.2 P9 — the session-bound LAUNCH-RECORD channel: the Linux ↔ Android
 * observation bridge (docs/M7.2-P9-AGENT-OBSERVATION-ARCHITECTURE.md).
 *
 * WHAT IT IS. A tiny, append-only JSONL file, one per registry-agent LAUNCH,
 * that the guest's own launch chain writes and the Android detector reads.
 * The file lives INSIDE the app-owned rootfs
 * (`<rootfs>/var/lib/pocketshell-agent/<token>.jsonl`) — the same bytes at
 * the guest path `/var/lib/pocketshell-agent/<token>.jsonl` — so NO proot
 * bind, no daemon, no new process machinery is needed: the app reads its
 * own filesystem directly.
 *
 * WHY IT EXISTS. Two truth gaps the pure /proc scan cannot close:
 *
 *  1. THE ANCHOR. The launch chain records the agent process's EXACT
 *     identity at the moment it is exec'd: a nested `sh -c` prints its own
 *     pid / pgrp / /proc-self starttime (the P9 prototype, E1/E3, proved on
 *     real Linux that exec PRESERVES all three — the recorded values are
 *     the agent's live /proc values) and then execs the real agent. The
 *     detector validates the anchor against a live snapshot
 *     (pid + starttime match ⇒ THE agent, immune to pid reuse; starttime
 *     is the kernel's boot-tick birth stamp). The anchor refines the tree
 *     correlation with exactness — it never REPLACES it: a RUNNING claim
 *     still requires the anchored process to exist in the snapshot.
 *
 *  2. THE EXIT FACT. P3a's truth-loss point 3: the launch chain consumes
 *     and discards the agent's exit status (`kilo; exec sh -l` — no `$?`
 *     capture exists). The chain now records the REAL status after the
 *     nested shell returns. It is a FACT ("the launched process exited
 *     with status N"), never an interpretation: exit 0 is NOT success and
 *     exit 1 is NOT failure (PART P line — no completion mapping).
 *
 * GENERATIONS / STALE STATE (PART O). The file is created FRESH by the
 * Android side for every launch tap and named by a random per-launch token;
 * the file's existence IS the runtime generation. There is no replay
 * surface: a deleted file ends the channel, a fresh file starts a new one,
 * and a late event from a previous run is structurally impossible (it would
 * have to be in a file that no longer exists). A session that closes
 * deletes its file; crash-orphans are swept against live sessions at the
 * next launch (the caller's sweep — this file stays pure).
 *
 * SECURITY BOUNDARY (PART T). The channel is app-private at the host level
 * (under the app's noBackupFilesDir — other apps cannot reach it). Guest
 * processes share the app UID, so a guest process CAN append to its own
 * session's file — the trust boundary is the same one the terminal itself
 * already accepts (the guest IS the user's environment; P7's model). The
 * anchor is validated against live /proc truth (a claimed RUNNING still
 * requires a real, live, matching process in the session's tree), the
 * parser accepts exactly the two record shapes below and nothing else, and
 * the anchor can never claim a process for a DIFFERENT session (the
 * consumer checks the pid against the session's own correlation domain).
 *
 * Everything here is DATA plus PURE functions: no clock, no Android APIs,
 * no coroutine, no state.
 */
object AgentLaunchRecords {

    /** The guest-visible directory (inside the rootfs) holding the per-launch record files. */
    const val GUEST_DIR = "/var/lib/pocketshell-agent"

    /**
     * The guest-visible record file path for one launch token — the SAME
     * bytes the Android side reads at `<rootfs>/var/lib/pocketshell-agent/
     * <token>.jsonl` (the rootfs is the app's own filesystem; no proot bind).
     */
    fun guestFilePath(token: String): String = "$GUEST_DIR/$token.jsonl"

    /** The launch record: the agent's exec-anchored process identity. */
    data class LaunchRecord(
        val pid: Int,
        val pgrp: Int,
        /** /proc/<pid>/stat field 22 — the kernel's boot-tick birth stamp (the pid-reuse guard). */
        val startTicks: Long,
        /** The agent's launch token ("kilo") — must match the session's expected agent. */
        val agent: String,
    )

    /**
     * The exit record: the REAL exit status of the launched process, captured
     * by the chain shell. A FACT about the process — never success/failure.
     */
    data class ExitRecord(
        val status: Int,
        val agent: String,
    )

    /** The parsed content of one launch-record file. */
    data class SessionRecords(
        val launch: LaunchRecord?,
        val exits: List<ExitRecord>,
    )

    val EMPTY: SessionRecords = SessionRecords(launch = null, exits = emptyList())

    /**
     * Strict parser: accepts exactly the two record shapes the chain emits,
     * one JSON object per line, and nothing else. Unknown lines, malformed
     * JSON, wrong types or out-of-domain values are DROPPED (the anchor
     * simply disappears — missing evidence is never upgraded to a guess).
     * A truncated final line (a crash mid-write) degrades to the records
     * before it, which is exactly the honest state.
     */
    fun parse(text: String): SessionRecords {
        var launch: LaunchRecord? = null
        val exits = ArrayList<ExitRecord>(2)
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            parseLaunch(line)?.let { launch = it; continue }
            parseExit(line)?.let { exits += it }
        }
        return SessionRecords(launch = launch, exits = exits)
    }

    /** Read + parse one record file; unreadable == empty (the channel is simply absent). */
    fun readFile(path: String): SessionRecords = try {
        parse(File(path).readText())
    } catch (_: Exception) {
        EMPTY
    }

    /**
     * The launch-record token: the per-launch generation name. Hex only —
     * it travels inside the guest command chain and the file path, so it
     * must be a plain token (the same allowlist class as agent commands).
     */
    fun newToken(randomHex: String): String = "p9" + randomHex.filter { it in '0'..'9' || it in 'a'..'f' }.take(24)

    /**
     * The chain snippets. Composition contract (prototype-proven):
     *
     *   <anchorSnippet> ; <exitSnippet> ; exec <guestShell> -l
     *
     * where <anchorSnippet> is a nested `sh -c '…; exec <agentCommand>'`
     * that records pid/pgrp/start and execs the agent (the recorded pid IS
     * the agent's pid after exec), and <exitSnippet> runs in the OUTER
     * chain shell after the nested shell returns, recording its `$?` — the
     * agent's real exit status. The `|| :` guards keep a failed record
     * write from ever killing the launch or the session.
     *
     * [agentCommand] is the (already allowlist-quoted) full launch command;
     * [agentToken] is its HEAD (the plain registry token recorded in the
     * JSON — the same token the detector's discovery resolves against).
     * The token/file paths are hex or fixed constants. No user-owned text
     * reaches these snippets (custom tools keep the plain chain).
     */
    fun anchorSnippet(agentCommand: String, agentToken: String, guestRecordFile: String): String =
        "sh -c 'PGRP=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f3); " +
            "START=\$(sed \"s/^[0-9]* (.*) //\" /proc/self/stat | cut -d\" \" -f20); " +
            "printf \"{\\\"t\\\":\\\"launch\\\",\\\"pid\\\":%s,\\\"pgrp\\\":%s,\\\"start\\\":%s,\\\"agent\\\":\\\"$agentToken\\\"}\\n\" " +
            "\"\$\$\" \"\$PGRP\" \"\$START\" >> $guestRecordFile || :; " +
            "exec $agentCommand'"

    fun exitSnippet(agentToken: String, guestRecordFile: String): String =
        "printf \"{\\\"t\\\":\\\"exit\\\",\\\"status\\\":%s,\\\"agent\\\":\\\"$agentToken\\\"}\\n\" " +
            "\"\$?\" >> $guestRecordFile || :"

    /**
     * The full record-carrying launch chain: the anchor, the exit record,
     * then the unchanged trailing-exec contract (the interactive login
     * shell takes over when the agent exits).
     */
    fun launchChain(
        agentCommand: String,
        agentToken: String,
        guestShell: String,
        guestRecordFile: String,
    ): String =
        "${anchorSnippet(agentCommand, agentToken, guestRecordFile)} ; " +
            "${exitSnippet(agentToken, guestRecordFile)} ; exec $guestShell -l"

    // ---- strict line parsers (no JSON library: the schema is ours) ----

    private fun intField(line: String, name: String): Int? =
        Regex("\"$name\":(-?[0-9]+)").find(line)?.groupValues?.get(1)?.toIntOrNull()

    private fun longField(line: String, name: String): Long? =
        Regex("\"$name\":(-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLongOrNull()

    private fun stringField(line: String, name: String): String? =
        Regex("\"$name\":\"([a-z0-9._+%-]*)\"").find(line)?.groupValues?.get(1)

    private fun parseLaunch(line: String): LaunchRecord? {
        if (!line.startsWith("{\"t\":\"launch\"")) return null
        val pid = intField(line, "pid") ?: return null
        val pgrp = intField(line, "pgrp") ?: return null
        val start = longField(line, "start") ?: return null
        val agent = stringField(line, "agent") ?: return null
        if (pid <= 0 || pgrp <= 0 || start < 0 || agent.isEmpty()) return null
        return LaunchRecord(pid = pid, pgrp = pgrp, startTicks = start, agent = agent)
    }

    private fun parseExit(line: String): ExitRecord? {
        if (!line.startsWith("{\"t\":\"exit\"")) return null
        val status = intField(line, "status") ?: return null
        val agent = stringField(line, "agent") ?: return null
        if (agent.isEmpty()) return null
        return ExitRecord(status = status, agent = agent)
    }
}
