# FINDINGS — Codex CLI notification/event signals (lab codex-1)

Date: 2026-09-17. Subject: OpenAI Codex CLI `codex-cli 0.154.0`
(native binary at `~/agent-lab/npm/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64/vendor/x86_64-unknown-linux-musl/bin/codex`, ~263 MB).
Method: local mock OpenAI-compatible server on `http://127.0.0.1:4546` (`mock-server.js`, request log `mock-requests.log`), isolated `CODEX_HOME=codexhome/`, no real API credentials. All raw logs in this directory.

---

## 1. VERIFIED mechanisms

### 1.1 `notify` config hook — VERIFIED (fires once per completed turn)

`notify-handler.sh` receives the JSON payload as `$1` and appends it to `notify.log`. Exact payload from run 2 (well-formed SSE):

```json
{"type":"agent-turn-complete","thread-id":"01a0afc1-d67b-7f71-b1a3-efe60da626d9","turn-id":"01a0afc1-d709-7920-b524-cef61066af6a","cwd":"/home/muhammad-taha/agent-lab/experiments/codex-1/workdir","client":"codex_exec","input-messages":["say hi"],"last-assistant-message":"Lab run complete."}
```

Field notes:
- `type` is always `agent-turn-complete` for turn completion. `strings` on the binary shows exactly one notify payload type; keys `thread-id`, `turn-id`, `cwd`, `client`, `input-messages`, `last-assistant-message` sit adjacent to it in the binary's serde metadata.
- `thread-id` == session id == UUID in the rollout filename == `thread_id` of `--json` events.
- `turn-id` == `turn_id` used in session JSONL `turn_context` / `task_started` / `task_complete` / `token_usage_record`.
- `client` = `codex_exec` (presumably `codex_cli` for the TUI — not verified).
- `last-assistant-message` is `null` if no assistant message was captured (run 1 had a malformed SSE stream → null), otherwise the final text.
- `input-messages` accumulates across turns: on `codex exec resume <thread-id> "thanks, bye"` it became `["say hi","thanks, bye"]`, with the SAME `thread-id` and a NEW `turn-id` (run 4).

Timing (run 2): codex start 1789655110.837, notify fired 1789655111.719, process exit 1789655112.002 → notify fires ~0.3 s BEFORE process exit, i.e. at TURN COMPLETION, not at process exit. With a tool call in the turn (run 8: model request → `exec_command` shell call → shell executes → second model request → final message) notify still fired exactly ONCE, at the end → it is a per-turn signal, not a per-model-round-trip signal.

Negative results (also verified):
- Run 10 (dead port): codex loops "Reconnecting... waiting for network" forever; `timeout` killed it (exit 124) → NO notify payload. Notify fires only on successful turn completion.
- Run 5 (mock died mid-run, timeout kill): same — no notify; the `--json` stream had emitted `{"type":"error","message":"Reconnecting... waiting for network (Connection failed: error sending request)"}` events.

### 1.2 `codex exec --json` — VERIFIED (JSONL event stream on stdout)

Exact stream, run 2 (`codex exec --skip-git-repo-check --json "say hi"`):

```jsonl
{"type":"thread.started","thread_id":"01a0afc1-d67b-7f71-b1a3-efe60da626d9"}
{"type":"item.completed","item":{"id":"item_0","type":"error","message":"Model metadata for `lab-model` not found. Defaulting to fallback metadata; this can degrade performance and cause issues."}}
{"type":"turn.started"}
{"type":"item.completed","item":{"id":"item_1","type":"agent_message","text":"Lab run complete."}}
{"type":"turn.completed","usage":{"input_tokens":1,"cached_input_tokens":0,"cache_write_input_tokens":0,"output_tokens":1,"reasoning_output_tokens":0}}
```

Run 8 (tool call) inserted, between `turn.started` and the agent_message item:

```jsonl
{"type":"item.started","item":{"id":"item_1","type":"command_execution","command":"/bin/bash -lc 'bash -lc \"echo TOOLDONE-from-shell\"'","aggregated_output":"","exit_code":null,"status":"in_progress"}}
{"type":"item.completed","item":{"id":"item_1","type":"command_execution","command":"...","aggregated_output":"TOOLDONE-from-shell\n","exit_code":0,"status":"completed"}}
```

Item types seen: `error`, `agent_message`, `command_execution`. Event names present in the binary's serde strings (broader set for this protocol): `thread.started`, `thread.failed`, `turn.started`, `turn.completed`, `turn.failed`, `item.started`, `item.updated`, `item.completed`, `error`, `stream_error`, `shutdown_complete`. `--json` prints events only (final assistant text is in the `agent_message` item); `codex --help`/`codex exec --help` also expose `-o/--output-last-message <FILE>` and `--output-schema <FILE>`.

### 1.3 Session rollout files — VERIFIED

Path: `$CODEX_HOME/sessions/YYYY/MM/DD/rollout-<local-timestamp>-<session-uuid>.jsonl` (18 lines for the tool-call run). Top-level line types with `"type"` / `"payload"`:

| top-level type | payload `type` values seen | notable payload fields |
|---|---|---|
| `session_meta` | — | `session_id` (= uuid in filename), `cwd`, `originator` (`codex_exec`), `cli_version`, `source` (`exec`), `model_provider` |
| `event_msg` | `task_started` | `turn_id`, `started_at`, `model_context_window`, `collaboration_mode_kind` |
| `event_msg` | `item_completed` | `thread_id`, `turn_id`, `item:{type: UserMessage \| CommandExecution (process_id, command, aggregated_output, exit_code) \| AgentMessage, id, content}`, `started_at_ms`, `completed_at_ms` |
| `event_msg` | `token_count` | `info.total_token_usage`, `info.last_token_usage`, `info.rate_limits` |
| `event_msg` | `task_complete` | `turn_id`, `last_agent_message`, `started_at`, `completed_at`, `duration_ms`, `time_to_first_token_ms` |
| `response_item` | `message` (roles developer/user/assistant) | `content[].{type: input_text \| output_text \| Text, text}`, `internal_chat_message_metadata_passthrough.turn_id` |
| `turn_context` | — | `turn_id`, `root_turn_id`, `cwd`, `workspace_roots`, `approval_policy`, ... |
| `world_state` | — | full workspace/environment snapshot |
| `token_usage_record` | — | `thread_id`, `turn_id`, `session_id`, `root_turn_id`, `response_id`, `usage` |

grep of `"type":"..."` over one rollout: `event_msg`(7), `response_item`(6), `message`(4), `input_text`(4), `item_completed`(3), `token_usage_record`(2), `token_count`(2), plus `session_meta`, `turn_context`, `world_state`, `task_started`, `task_complete`, `UserMessage`, `AgentMessage`, `CommandExecution`, `Text`, `text`, `output_text`, `unknown`, `special`, `restricted`, `read-only`, `managed`, `model`.

Other `$CODEX_HOME` state: `history.jsonl` and `log/` do NOT exist in 0.154.0; state moved to SQLite (`thread_history_1.sqlite`, `state_5.sqlite`, `logs_2.sqlite`, `memories_1.sqlite`, `goals_1.sqlite`, `queue_1.sqlite`) + `shell_snapshots/`.

### 1.4 `hooks.json` (Claude-Code-compatible hooks) — VERIFIED (3 events live, more present in binary)

`hooks` is a `stable`, default-ON feature (`codex features list` → `hooks stable true`). A Claude-Code-style `$CODEX_HOME/hooks.json` works; handlers get the payload as JSON on **stdin**. Trust must be bypassed or persisted (`--dangerously-bypass-hook-trust` printed: "`--dangerously-bypass-hook-trust` is enabled. Enabled hooks may run without review for this invocation." — twice per hook on stderr, plus `hook: SessionStart` / `hook: SessionStart Completed` lines on stderr).

Exact stdin payloads (run 9):

```json
{"session_id":"01a0afc8-cd1f-7f31-9d03-1a321eebcc97","transcript_path":"/home/muhammad-taha/agent-lab/experiments/codex-1/codexhome/sessions/2026/09/17/rollout-2026-09-17T19-32-47-01a0afc8-cd1f-7f31-9d03-1a321eebcc97.jsonl","cwd":"/home/muhammad-taha/agent-lab/experiments/codex-1/workdir","hook_event_name":"SessionStart","model":"lab-model","permission_mode":"bypassPermissions","source":"startup"}
{"session_id":"01a0afc8-...","turn_id":"01a0afc8-cd9f-7fa1-9ebc-0833f5d0b88b","transcript_path":"...","cwd":"...","hook_event_name":"UserPromptSubmit","model":"lab-model","permission_mode":"bypassPermissions","prompt":"hi"}
{"session_id":"01a0afc8-...","turn_id":"01a0afc8-cd9f-7fa1-9ebc-0833f5d0b88b","transcript_path":"...","cwd":"...","hook_event_name":"Stop","model":"lab-model","permission_mode":"bypassPermissions","stop_hook_active":false,"last_assistant_message":"Lab run complete."}
```

Binary strings (`codex_hooks` crate) show the fuller event set: `PreToolUse`, `PermissionRequest`, `PostToolUse`, `PreCompact`, `PostCompact`, `SessionStart`, `SessionEnd`, `SubagentStart`, `SubagentStop`, `Interrupt`, `UserPromptSubmit`, `Stop`; handler types `command` and MCP tool; Claude-style wire fields (`hook_event_name`, `session_id`, `turn_id`, `transcript_path`, `permission_mode`, `tool_name`, `tool_input`, `tool_response`, `hookSpecificOutput` with `permissionDecision` `approve|block|ask`, `additionalContext`, etc.); env `CLAUDE_PLUGIN_ROOT`/`CLAUDE_PLUGIN_DATA`.

### 1.5 Wire API — VERIFIED (responses only)

- Codex 0.154.0 hit only `POST /v1/responses` (SSE, `stream:true`), body keys: `model, instructions, input, tools, tool_choice, parallel_tool_calls, reasoning, store, stream, include, prompt_cache_key, client_metadata`. Tools advertised: `exec_command`, `write_stdin`, `request_user_input`, `view_image`, `multi_agent_v1`, `get_goal`, `create_goal`, `update_goal`, `web_search`.
- `wire_api = "chat"` is REJECTED at config load: "Error loading config.toml: `wire_api = "chat"` is no longer supported. How to fix: set `wire_api = "responses"` ... https://github.com/openai/codex/discussions/7782". So the mock MUST implement /v1/responses.
- Minimal SSE that produced a complete turn with populated `last-assistant-message`: `response.created` → `response.output_item.added` (message item) → `response.output_text.delta` → `response.output_item.done` → `response.completed` (with `output[]` and `usage`) → `data: [DONE]`. Without the `output_item.added` first, codex logs "OutputTextDelta without active item" and `last-assistant-message` came out null.
- `exec_command` call format (reverse-engineered from router errors): function_call named `exec_command` with arguments `{"cmd":"<shell string>"}` (string, not array).

## 2. Reproduction config

`$CODEX_HOME/config.toml` (note: `notify` must stay OUTSIDE the `[model_providers.lab]` table):

```toml
model = "lab-model"
model_provider = "lab"

notify = ["/home/muhammad-taha/agent-lab/experiments/codex-1/notify-handler.sh"]

[model_providers.lab]
name = "Lab Mock OpenAI"
base_url = "http://127.0.0.1:4546/v1"
env_key = "LAB_API_KEY"
wire_api = "responses"
```

Run: `env CODEX_HOME=<dir> LAB_API_KEY=dummy codex exec --skip-git-repo-check --json "say hi"` from a plain directory (mock server: `mock-server.js`, `node mock-server.js`).

`$CODEX_HOME/hooks.json` (verified form):

```json
{
  "hooks": {
    "SessionStart":     [ { "hooks": [ { "type": "command", "command": "/abs/path/hook-handler.sh session-start" } ] } ],
    "UserPromptSubmit": [ { "hooks": [ { "type": "command", "command": "/abs/path/hook-handler.sh user-prompt-submit" } ] } ],
    "Stop":             [ { "hooks": [ { "type": "command", "command": "/abs/path/hook-handler.sh stop" } ] } ]
  }
}
```

`notify-handler.sh`: `printf '%s\n' "$1" >> "$EXP/notify.log"`. `hook-handler.sh`: `{ echo "=== argv: $*"; cat; echo; } >> "$EXP/hooks.log"` (both chmod +x).

## 3. Signal assessment

| question | answer |
|---|---|
| Structured JSON? | YES. notify = 1 JSON argv; `exec --json` = JSONL on stdout; hooks = JSON on stdin; session = JSONL files. |
| Session-correlatable? | YES. One UUID everywhere: notify `thread-id` == `--json` `thread_id` == `session_meta.session_id` == rollout filename == hooks `session_id`. Per-turn: `turn-id`/`turn_id` in notify, hooks, `--json` (via turn events), and every turn-scoped session record. Hooks additionally carry `transcript_path` (absolute rollout path). |
| Attention-capable (permission/waiting)? | PARTIAL. `notify` is NOT (single event type; nothing fires while waiting). Hooks are designed for it: `PermissionRequest`/`PreToolUse` hooks with `approve|block|ask` decisions exist in the binary, but were NOT runtime-verified (exec runs `approval: never`; a permission request never occurred). Waiting-state signal during network loss appears only as `{"type":"error","message":"Reconnecting..."}` on the `--json` stream. |
| Completion-capable? | YES. Four synchronized completion signals per turn: notify `agent-turn-complete`, hook `Stop`, session `task_complete` (+ `token_count`, `token_usage_record`), `--json` `turn.completed`. `last-assistant-message` / `last_agent_message` / `agent_message` item carry the final text. |

## 4. NOT verified (and why)

- **Interactive TUI / long-lived session**: not testable here (needs a TTY; exec is one turn per process). Notify per-turn in a multi-turn TUI session is inferred from resume behavior (per-turn-id payloads), not observed in-process.
- **`PermissionRequest` / `PreToolUse` / `PostToolUse` / `SessionEnd` / `SubagentStart`/`Stop`-during-interrupt hook payloads**: events exist in the binary (strings), but exec mode runs `approval: never` and `--dangerously-bypass-approvals-and-sandbox`/read-only sandbox meant no approval was ever requested. Would need TUI or `app-server` with an approval-demanding tool call.
- **Hook trust persistence**: used `--dangerously-bypass-hook-trust` for run 9; the normal "persist hook trust" flow was not exercised.
- **Failure-path notify**: verified that NOTHING fires on killed/failed turns (runs 5 and 10); whether a distinct failure notification exists in other configurations (e.g. turn.failed → notify?) was not established — no payload of any type other than `agent-turn-complete` was ever observed, and the binary contains only that one notify event string.
- **Non-exec `client` values** (e.g. `codex_cli` for TUI, app-server): unverified.
- **history.jsonl**: absent in 0.154.0 (SQLite instead); the DB schemas were not dissected.
- `client_metadata` field in the request body and the one non-stream `POST /v1/responses` probe observed during one startup were not investigated.

## 5. Raw artifacts in this directory

- `mock-server.js`, `mock-requests.log`, `mock-server.out` — mock + request log
- `codexhome/config.toml`, `codexhome/hooks.json`, `codexhome/sessions/2026/09/17/*.jsonl` — configs + rollouts
- `notify.log` — all notify payloads (runs 1,2,4,5,6,7,8,9)
- `hooks.log` — hooks stdin payloads (run 9)
- `run1-responses.{stdout,stderr}` — first run, wire_api responses, notify payload with `last-assistant-message:null`
- `run2-responses-json.*` — `--json` stream, clean SSE
- `run3-chat-json.stderr` — wire_api "chat" rejection
- `run4-resume-json.*` — resume: same thread, new turn, accumulated input-messages
- `run5-toolcall-json.*` — mock down mid-run: reconnect loop, no notify on timeout kill
- `run6/7-execcmd-json.*` — tool-call router errors (tool name / arg schema)
- `run8-execcmd-json.*` — successful `exec_command` round-trip; per-turn single notify
- `run9-hooks.*`, `hook-handler.sh` — hooks.json live firing
- `run10-deadport.*` — dead endpoint: infinite reconnect, no notify
- `run2-exit-time.txt` — exit timestamp used for notify-vs-exit timing (note: empty; timing taken from shell `$START/$END` in run 2 instead)
