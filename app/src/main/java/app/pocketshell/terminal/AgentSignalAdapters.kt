package app.pocketshell.terminal

/**
 * M7.2 P10 — the AGENT-SIGNAL ADAPTERS: per-agent knowledge of how to arm
 * the bridge through the agent's OWN notification mechanism, staged fresh
 * into the launch's record directory (`<token>.d/`) at every launch.
 *
 * THE COMMON PATTERN (verified experimentally on Linux x86,
 * docs/M7.2-P10-AGENT-SIGNAL-BRIDGE.md): every major CLI coding agent
 * exposes a local, structured, hook-shaped notification mechanism whose
 * payload is JSON naming the agent session (Claude Code hooks, Codex
 * notify + Claude-compatible hooks.json, OpenCode plugin events, ZCode
 * hooks). The adapter stage is the ONLY agent-specific layer of the
 * bridge: it (a) writes each agent's config files so the agent's own
 * events invoke the staged bridge emitter, and (b) rewrites the anchor's
 * exec command so the agent actually READS that staged config (a CLI
 * flag, an env var). Everything downstream of the record file is
 * agent-agnostic.
 *
 * THE STAGING MODEL. The Android side creates the staging directory
 * (`<rootfs>/var/lib/pocketshell-agent/<token>.d/`), copies the emitter
 * asset into it, writes [AgentSignalAdapter.stagedFiles], and composes the
 * launch chain from [AgentSignalAdapter.anchorCommand] (what the anchor
 * exec's — the agent plus its config-pointing flag/env) and
 * [AgentSignalAdapter.prepSnippet] (an outer-chain step that runs before
 * the anchor — auth symlinks and the like). The record file IS the
 * generation, and the staging dir lives beside it and dies with it: one
 * launch's staged config can never outlive its own channel.
 *
 * THE HONESTY RULE. An adapter exists ONLY where the underlying mechanism
 * was proven (this file documents the proof for each entry). An agent
 * without a verified mechanism gets NO adapter — it keeps the P3b process
 * truth and nothing more. No adapter may claim a state its mechanism
 * cannot prove.
 */
object AgentSignalAdapters {

    /** One file the adapter needs inside the launch's staging directory. */
    data class StagedFile(
        /** Relative path under the staging dir (plain segments, no `..`). */
        val relativePath: String,
        /** Exact file content. */
        val content: String,
        /** Whether the file must be executable in the guest. */
        val executable: Boolean = false,
    )

    /**
     * The per-agent staging contract. Pure: every function derives its
     * output from its inputs only, so the whole staging step is testable
     * on the JVM.
     */
    interface AgentSignalAdapter {
        /** The registry token this adapter arms ("claude", ...). */
        val agentToken: String

        /**
         * The staged config files. [stagingGuestDir] is the guest-visible
         * staging directory; [recordGuestPath] the guest-visible record
         * file (the signal destination); [emitGuestPath] the staged
         * emitter (hooks invoke `sh <emitGuestPath> …`).
         */
        fun stagedFiles(stagingGuestDir: String, recordGuestPath: String, emitGuestPath: String): List<StagedFile>

        /**
         * The command the launch ANCHOR execs: the agent with whatever
         * flag/env points it at the staged config. Composed of the
         * registry token plus adapter-owned additions only (paths carry
         * the hex token — the same safe character class as the record
         * file path).
         */
        fun anchorCommand(stagingGuestDir: String): String

        /**
         * An optional OUTER-chain step that runs before the anchor
         * (auth symlinks etc.). Single line, `|| :`-guarded, POSIX sh.
         * Null when the adapter needs none.
         */
        fun prepSnippet(stagingGuestDir: String): String? = null

        /**
         * Guest-relative directories under the rootfs whose top-level
         * regular files are copied THROUGH into the staged config dir at
         * staging time (the user's own global config for that agent —
         * settings survive staging; staged files win name conflicts).
         * Empty when the adapter does not redirect a config dir.
         */
        val copyThroughGuestDirs: List<String> get() = emptyList()
    }

    // -----------------------------------------------------------------
    // Claude Code — hooks (VERIFIED, docs/M7.2-P10 §2.1).
    //
    // `claude --settings <file>` loads ADDITIONAL settings; hooks arrays
    // MERGE with the user's real configuration (experiment claude-1 run 4:
    // user-config hooks and staged hooks BOTH fired; auth and the rest of
    // the user's config are untouched — non-invasive by construction).
    // Hook payloads arrive as JSON on stdin carrying session_id,
    // hook_event_name and the event specifics; PermissionRequest fired
    // with tool_name/tool_input on a real permission dialog, Notification
    // with notification_type=permission_prompt, Stop with
    // last_assistant_message.
    // -----------------------------------------------------------------
    internal class ClaudeCodeAdapter(override val agentToken: String) : AgentSignalAdapter {

        override fun stagedFiles(stagingGuestDir: String, recordGuestPath: String, emitGuestPath: String): List<StagedFile> {
            fun hook(kind: String) =
                "{\"type\":\"command\",\"command\":\"sh $emitGuestPath $agentToken $kind $recordGuestPath\"}"
            fun event(event: String, kind: String) =
                "\"$event\":[{\"hooks\":[${hook(kind)}]}]"
            val settings = "{\"hooks\":{" +
                event("SessionStart", "session_start") + "," +
                event("UserPromptSubmit", "working") + "," +
                event("PreToolUse", "working") + "," +
                event("PostToolUse", "working") + "," +
                event("PermissionRequest", "permission_request") + "," +
                event("Notification", "attention") + "," +
                event("Stop", "turn_complete") + "," +
                event("SessionEnd", "session_end") +
                "}}"
            return listOf(StagedFile("claude-settings.json", settings + "\n"))
        }

        override fun anchorCommand(stagingGuestDir: String): String =
            "$agentToken --settings $stagingGuestDir/claude-settings.json"
    }

    // -----------------------------------------------------------------
    // Codex — notify (VERIFIED, docs/M7.2-P10 §2.2).
    //
    // `notify = ["cmd"]` in config.toml fires once per completed turn with
    // the JSON payload as ARGV[1] ({"type":"agent-turn-complete",
    // "thread-id":...,"last-assistant-message":...}), with NO trust gate —
    // zero launch friction. The Claude-compatible hooks.json (which also
    // carries PermissionRequest) requires per-hook trust approval on a
    // fresh CODEX_HOME — a prompt on EVERY launch — so hooks are
    // deliberately NOT armed by this adapter (documented deferral).
    //
    // Staging isolates CODEX_HOME; the outer-chain prep symlinks the
    // user's real auth into the staged home so sign-in survives. The
    // notify line is the ONLY content of the staged config.toml (v1: the
    // user's model/provider preferences do not transfer into PocketShell's
    // staged home — documented trade-off).
    // -----------------------------------------------------------------
    internal class CodexAdapter(override val agentToken: String) : AgentSignalAdapter {

        override fun stagedFiles(stagingGuestDir: String, recordGuestPath: String, emitGuestPath: String): List<StagedFile> {
            val config = "notify = [\"sh\", \"$emitGuestPath\", \"$agentToken\", " +
                "\"turn_complete\", \"$recordGuestPath\"]\n"
            return listOf(StagedFile("codex/config.toml", config))
        }

        override fun anchorCommand(stagingGuestDir: String): String =
            "CODEX_HOME=$stagingGuestDir/codex $agentToken"

        override fun prepSnippet(stagingGuestDir: String): String? =
            "ln -sf \"\$HOME/.codex/auth.json\" \"$stagingGuestDir/codex/auth.json\" 2>/dev/null || :"
    }

    // -----------------------------------------------------------------
    // OpenCode — plugin events (VERIFIED, docs/M7.2-P10 §2.3).
    //
    // A plugin in <config-dir>/opencode/plugin/ receives the WHOLE event
    // bus: permission.asked (attention, with the command metadata),
    // permission.replied, session.status busy, session.idle (turn
    // completion), session.created, session.error. The staged plugin
    // writes signal records DIRECTLY (it runs inside the agent's own
    // runtime with fs access) — it records its own process identity
    // (pid + /proc starttime) exactly like the shell emitter, so
    // acceptance treats its records identically.
    //
    // Staging points XDG_CONFIG_HOME at the staged config dir (the DATA
    // dir — auth, sessions — stays shared). The Android staging step
    // copies the user's real global opencode config files through, so
    // settings survive (plugin dir stays ours — the bridge plugin is
    // additive to any project-level plugins the user already has).
    // -----------------------------------------------------------------
    internal class OpenCodeAdapter(override val agentToken: String) : AgentSignalAdapter {

        override fun stagedFiles(stagingGuestDir: String, recordGuestPath: String, emitGuestPath: String): List<StagedFile> {
            val plugin = """
                // PocketShell agent-signal bridge (staged per launch; see
                // docs/M7.2-P10-AGENT-SIGNAL-BRIDGE.md). Relays OpenCode's
                // own event bus into this launch's record file.
                export const PocketShellBridge = async () => {
                  const fs = await import("node:fs")
                  const RECORD = "$recordGuestPath"
                  let parentStart = 0
                  try {
                    const stat = fs.readFileSync(`/proc/${'$'}{process.pid}/stat`, "utf8")
                    const rest = stat.slice(stat.lastIndexOf(")") + 2).split(" ")
                    parentStart = Number(rest[19]) || 0
                  } catch {}
                  let seq = 0
                  const emit = (kind, payload) => {
                    try {
                      let data = ""
                      if (payload !== undefined) {
                        data = JSON.stringify(payload)
                        if (data.length > 800) data = data.slice(0, 799) + "_"
                      }
                      const line = JSON.stringify({
                        t: "signal", agent: "$agentToken", kind: kind,
                        seq: ++seq, pid: process.pid, start: parentStart, data: data,
                      })
                      fs.appendFileSync(RECORD, line + "\n")
                    } catch {}
                  }
                  return { event: async ({ event }) => {
                    try {
                      const t = event && event.type
                      const p = (event && event.properties) || {}
                      if (t === "session.idle") emit("turn_complete", { sessionID: p.sessionID })
                      else if (t === "permission.asked") emit("permission_request", {
                        sessionID: p.sessionID, permission: p.permission,
                        command: p && p.metadata ? p.metadata.command : undefined,
                      })
                      else if (t === "permission.replied") emit("working", { sessionID: p.sessionID, reply: p.reply })
                      else if (t === "session.status" && p.status && p.status.type === "busy") emit("working", { sessionID: p.sessionID })
                      else if (t === "session.created") emit("session_start", { sessionID: p.sessionID || (p.info && p.info.id) })
                      else if (t === "session.error") emit("session_end", { sessionID: p.sessionID, error: String(p.error || "").slice(0, 200) })
                    } catch {}
                  } }
                }
            """.trimIndent() + "\n"
            return listOf(StagedFile("xdg-config/opencode/plugin/pocketshell-bridge.js", plugin))
        }

        override fun anchorCommand(stagingGuestDir: String): String =
            "XDG_CONFIG_HOME=$stagingGuestDir/xdg-config $agentToken"

        override val copyThroughGuestDirs: List<String> = listOf("root/.config/opencode")
    }

    // -----------------------------------------------------------------

    /** The adapters keyed by registry token. Entries = proven mechanisms only. */
    private val adapters: Map<String, AgentSignalAdapter> = listOf(
        ClaudeCodeAdapter("claude"),
        CodexAdapter("codex"),
        OpenCodeAdapter("opencode"),
    ).associateBy { it.agentToken }

    /** The adapter for an agent token, or null when no mechanism is proven. */
    fun forToken(token: String): AgentSignalAdapter? = adapters[token]
}
