# FINDINGS — OpenCode CLI event/notification signals (lab experiment)

- Date: 2026-09-17
- Machine: Linux x86_64 (debian, 6.1.0-49-amd64)
- Subject: `opencode` 1.18.31 (npm package `opencode-ai`, Bun-compiled native binary at
  `~/agent-lab/npm/lib/node_modules/opencode-ai/bin/opencode.exe`, ~185 MB)
- Method: real `opencode run` + `opencode serve` driven against a local mock
  OpenAI-compatible provider (no real API credentials), with a project plugin that
  mirrors every bus event to disk. SSE stream captured via `curl -N /event`.
- Raw artifacts (this dir): `events.log` (plugin hook capture), `sse.log` / `sse-perm.log`
  (SSE captures), `requests.log` (mock provider HTTP log), `run1.log`, `run2.log`,
  `run3-perm-hang.log`, `serve.log`, `serve2.log`, `openapi.json`, `event-vocabulary.txt`,
  `perm-driver.log`, `msg-perm-resp.json`, `mock-server.js`, `perm-driver.sh`,
  `project/opencode.json`, `project/.opencode/plugin/lab.js`.

---

## 1. Reproduction setup (verified working)

### 1.1 Project config — `project/opencode.json`

```json
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "lab": {
      "npm": "@ai-sdk/openai-compatible",
      "name": "Lab",
      "options": { "baseURL": "http://127.0.0.1:4547/v1", "apiKey": "dummy" },
      "models": { "lab-model": { "name": "Lab Model" } }
    }
  },
  "model": "lab/lab-model",
  "permission": { "bash": "ask", "edit": "ask", "webfetch": "ask" },
  "autoupdate": false
}
```

- `@ai-sdk/openai-compatible` is referenced 271x inside the binary; opencode resolves it
  itself (it was NOT preinstalled in our project, and no `npm i` was needed — opencode
  downloaded/loaded the SDK itself; internet was available).
- Mock provider: Node http server on 127.0.0.1:4547 (`mock-server.js`) logging every
  request to `requests.log`, answering `POST /v1/chat/completions` with a static
  chat-completion (`"Lab complete."`). Observed requests from opencode:
  `stream=true`, `max_tokens=32000`, first a **title-generation call** (system prompt
  "You are a title generator...", `tools=none`), then the **real call**
  (`tools=bash,edit,glob,grep,question,read,skill,task,todowrite,webfetch,write`).
- Tool-call mode (`MOCK_MODE=tool`) returned an OpenAI `tool_calls` completion with
  `function.name="bash"`, `arguments='{"command":"echo hello-permission",...}'`;
  opencode accepted it and executed the real bash tool after approval.

### 1.2 Plugin — `project/.opencode/plugin/lab.js`

Both `.opencode/plugin` (singular) and `.opencode/plugins` (plural) strings exist in the
binary; the **singular** dir was used and loaded successfully (`GET /config` showed
`"plugin": ["file:///…/project/.opencode/plugin/lab.js"]`). ESM `export const` works.

```js
import fs from "fs"
import path from "path"
const EXP = "/home/muhammad-taha/agent-lab/experiments/opencode-1"
export const LabPlugin = async ({ project, client, $, directory, worktree }) => {
  return {
    event: async ({ event }) => {
      fs.appendFileSync(path.join(EXP, "events.log"), JSON.stringify(event) + "\n")
    },
    "tool.execute.before": async (input) => { /* logged */ },
    "tool.execute.after":  async (input, output) => { /* logged */ },
  }
}
```

Verified: the generic `event` hook captured EVERYTHING needed (see §2);
`tool.execute.before`/`after` fired with `{tool, sessionID, callID}` and full args/result.
NOT supported in 1.18.31 under the names tried: a `permission: { bash(){}, edit(){}, webfetch(){} }`
hook object (never invoked — permissions must be consumed via `permission.asked` events
or the permissions REST endpoint).

### 1.3 Run commands

```
env XDG_DATA_HOME=$EXP/xdg-data XDG_CONFIG_HOME=$EXP/xdg-config \
  opencode run "say hi"            # headless, exit 0, printed "Lab complete."
opencode serve --port 4548 --hostname 127.0.0.1   # headless server (web UI on /)
curl -N http://127.0.0.1:4548/event               # SSE bus
```

Server-API session drive (used for SSE + permission tests):

```
POST /session            {"title": "..."}                     -> {"info":{"id":"ses_…",…}}
POST /session/{sid}/message {"model":{"providerID":"lab","modelID":"lab-model"},
                             "parts":[{"type":"text","text":"…"}],"agent":"build"}
                         -> full assistant message JSON (blocks until turn ends)
POST /session/{sid}/permissions/{perID} {"response":"once"|"always"|"reject"} -> true
```

Full OpenAPI at `GET /doc` (`openapi.json`).

---

## 2. Verified event payloads (exact, from events.log / sse-perm.log)

Envelope for every bus event (plugin `event` hook and SSE `data:` lines are identical):

```json
{"id":"evt_<hex>","type":"<event.type>","properties":{ … }}
```

### session.created
```json
{"id":"evt_0afc3c451001lvzUPisM6erxMb","type":"session.created","properties":{"sessionID":"ses_f503c3bb0ffexZlkGc2WQUXvi9","info":{"id":"ses_f503c3bb0ffexZlkGc2WQUXvi9","slug":"proud-falcon","version":"1.18.31","projectID":"global","directory":"…/project","path":"home/…/project","title":"New session - 2026-09-17T14:27:17.711Z","permission":[{"permission":"question","pattern":"*","action":"deny"},{"permission":"plan_enter","pattern":"*","action":"deny"},{"permission":"plan_exit","pattern":"*","action":"deny"}],"cost":0,"tokens":{"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}},"time":{"created":1789655237711,"updated":1789655237711}}}}
```

### session.updated (info carries agent+model once bound)
`properties`: `{sessionID, info:{…,"agent":"build","model":{"id":"lab-model","providerID":"lab","variant":"default"},…}}`

### session.status (turn lifecycle)
```json
{"id":"evt_0afc3cb3c0013PX4mSZcHJdsX4","type":"session.status","properties":{"sessionID":"ses_…","status":{"type":"busy"}}}
… {"type":"session.status","properties":{"sessionID":"ses_…","status":{"type":"idle"}}}
```

### session.idle  (TURN-COMPLETE SIGNAL)
```json
{"id":"evt_0afc3d19b0022IMEWgnJgk6uBc","type":"session.idle","properties":{"sessionID":"ses_f503c3bb0ffexZlkGc2WQUXvi9"}}
```
Emitted once per completed turn, AFTER final `session.status {type:"idle"}`.

### message.updated (user and assistant)
```json
{"type":"message.updated","properties":{"sessionID":"ses_…","info":{"id":"msg_0afc3cb48001w1hpwaJYlqUSAJ","parentID":"msg_0afc3c507001HkGJ5TC5yMVaVA","role":"assistant","mode":"build","agent":"build","path":{"cwd":"…","root":"/"},"cost":0,"tokens":{"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}},"modelID":"lab-model","providerID":"lab","time":{"created":1789655239496},"sessionID":"ses_…"}}}
```
`message.updated` is emitted multiple times per message (create + per update).

### message.part.updated (part types: text | step-start | step-finish | tool)
```json
{"type":"message.part.updated","properties":{"sessionID":"ses_…","part":{"id":"prt_0afc3d160001KVZWgSQbeXn1IJ","messageID":"msg_…","sessionID":"ses_…","type":"text","text":"Lab complete.","time":{"start":1789655241056,"end":1789655241071}},"time":1789655241071}}
```
Tool part lifecycle (4 updates observed): `state.status` pending → running → running
(metadata.output accumulates) → completed:
```json
{"type":"tool","tool":"bash","callID":"call_lab_1","state":{"status":"completed","input":{"command":"echo hello-permission","description":"lab echo"},"output":"hello-permission\n","metadata":{"output":"hello-permission\n","exit":0,"truncated":false},"title":"echo hello-permission","time":{"start":1789655924198,"end":1789655924715}},"id":"prt_…","sessionID":"ses_…","messageID":"msg_…"}
```

### message.part.delta (token streaming)
```json
{"id":"evt_0afc3d166001fG4WmzN6RSMxoK","type":"message.part.delta","properties":{"sessionID":"ses_…","messageID":"msg_0afc3cb48001w1hpwaJYlqUSAJ","partID":"prt_0afc3d160001KVZWgSQbeXn1IJ","field":"text","delta":"Lab complete."}}
```

### permission.asked  (ATTENTION SIGNAL — verified live)
```json
{"id":"evt_0afce3e6e002V7SeRdXJL9TPN7","type":"permission.asked","properties":{"id":"per_0afce3e6e0012BNsiTnhV95EU9","sessionID":"ses_f5031d0ecffeQ0KqzbENqZft7V","permission":"bash","patterns":["echo hello-permission"],"metadata":{"command":"echo hello-permission"},"always":["echo *"],"tool":{"messageID":"msg_0afce368f001JZsohmnnaEwoXk","callID":"call_lab_1"}}}
```
Schema-required props: `id (^per)`, `sessionID`, `permission`, `patterns[]`, `metadata{}`, `always[]`, optional `tool{messageID,callID}`.

### permission.replied
```json
{"id":"evt_0afce3fa4001pjrlSNnJYhyCGp","type":"permission.replied","properties":{"sessionID":"ses_…","requestID":"per_0afce3e6e0012BNsiTnhV95EU9","reply":"once"}}
```
`reply` enum: `"once" | "always" | "reject"`. Note the field rename: asked emits `id`,
replied references it as `requestID`.

### session.diff
`{"type":"session.diff","properties":{"sessionID":"ses_…","diff":[]}}` (empty here — no file edits)

### Housekeeping events observed
- `plugin.added` — `{"properties":{"id":"core/config-reference"}}` (x45 per process; each
  built-in plugin announces itself)
- `catalog.updated`, `reference.updated`, `integration.updated` — `{"properties":{}}`
- Transport-level SSE-only: `server.connected` `{"properties":{}}`, periodic
  `server.heartbeat` `{"properties":{}}` (~every 20 s)

---

## 3. Full event-bus vocabulary (authoritative, from binary's OpenAPI `Event` union)

89 typed events (extracted programmatically into `event-vocabulary.txt`; payload detail
above for the key ones). Complete list of `type` values:

session.created, session.updated, session.deleted, session.status, session.idle,
session.diff, session.error, session.compacted, message.updated, message.removed,
message.part.updated, message.part.removed, message.part.delta,
session.next.{agent.switched, model.switched, moved, prompted, prompt.admitted,
context.updated, synthetic, shell.started, shell.ended, step.started, step.ended,
step.failed, text.started, text.delta, text.ended, reasoning.started, reasoning.delta,
reasoning.ended, tool.input.started, tool.input.delta, tool.input.ended, tool.called,
tool.progress, tool.success, tool.failed, retried, compaction.started, compaction.delta,
compaction.ended},
permission.asked, permission.replied, permission.v2.asked, permission.v2.replied,
question.asked, question.replied, question.rejected, question.v2.asked,
question.v2.replied, question.v2.rejected,
file.edited, file.watcher.updated, reference.updated, todo.updated, lsp.updated,
command.executed, plugin.added, project.updated, project.directories.updated,
installation.updated, installation.update-available, catalog.updated,
models-dev.refreshed, integration.updated, integration.connection.updated,
mcp.tools.changed, mcp.browser.open.failed, pty.created, pty.updated, pty.exited,
pty.deleted, vcs.branch.updated, workspace.ready, workspace.failed, workspace.status,
worktree.ready, worktree.failed, server.connected, server.instance.disposed,
global.disposed, tui.prompt.append, tui.command.execute, tui.toast.show,
tui.session.select.

Notes:
- `permission.updated` does NOT exist in 1.18.31 (0 hits in binary) — it is
  `permission.asked`/`permission.replied`.
- `session.error` properties: `{sessionID, error}` where error is a tagged union:
  ProviderAuthError, UnknownError, MessageOutputLengthError, MessageAbortedError,
  StructuredOutputError, ContextOverflowError, ContentFilterError, APIError.
- A "SyncEvent*/session.next.*" parallel vocabulary exists (finer-grained step/text/
  reasoning/tool deltas — the newer "session next" pipeline); observed only in schema,
  not on the default `/event` stream in our runs.

---

## 4. Observed event sequence for one turn (headless `opencode run`, plain text answer)

```
plugin.added x45 (process boot)
session.created → session.updated
message.updated (role=user) → message.part.updated (part type=text, user text)
session.status {busy} → message.updated (role=assistant)
[categories/reference/integration housekeeping]
message.part.delta ("Lab complete.")
message.part.updated (text part, time.start/end)
message.updated (assistant, cost/tokens now filled) — x2
session.diff
session.status {idle}
session.idle ← TURN COMPLETE
session.updated (title/tokens updated)
```

With a tool call + permission (server session):

```
… session.status{busy} → message.updated(assistant) → message.part.updated(step-start)
→ message.part.updated(tool, state=pending) → message.part.updated(tool, state=running)
→ permission.asked {perID, sessionID, bash, callID}      ← blocks here
→ (POST /permissions/{perID} {response:"once"}) → permission.replied {requestID, reply}
→ tool part running (output streams) → tool part completed (exit 0)
→ message.part.updated(step-finish) → message.updated(assistant)
→ 2nd provider call (requests.log) → message.part.updated(step-start/text)
→ message.part.delta → step-finish → message.updated
→ session.status{busy} → session.status{idle} → session.idle
```

Tool part error path (headless auto-reject): `permission.asked → permission.replied
{reply:"reject"} → tool part state.status="error" → step-finish → session.status{idle} →
session.idle`. **No `session.error` was emitted for a tool-permission rejection** — the
failure is visible via the tool part `error` state and the assistant text; `session.error`
is reserved for provider/API-class errors (not triggered in this lab — see §7).

---

## 5. Transport comparison

| Aspect | Plugin `event` hook | `opencode serve` SSE `GET /event` |
|---|---|---|
| Same payloads? | YES — byte-identical JSON objects | YES |
| Scope | events of the process the plugin runs in | that server instance's bus |
| `opencode run` events visible? | yes (in-process) | **NO** — `opencode run` does NOT attach to a separately started `opencode serve`; it is an independent instance. Zero session events appeared on `serve`'s SSE while `run` executed. |
| Server-driven sessions | visible via plugin loaded by serve | full stream: `session.*`, `message.*`, `permission.*`, plus `server.connected`/`server.heartbeat` |
| Framing | none (function callback) | `data: {json}\n\n` (no `event:` field; type inside JSON) |

Implication: to watch `opencode run` externally, either use the plugin hook, or drive
sessions through `opencode serve`'s HTTP API (`POST /session`, `POST /session/{id}/message`).

## 6. Session correlation — VERIFIED STRONG

Every session-scoped event carries IDs:
- `properties.sessionID` (`ses_…`) on all session/message/permission/todo/diff/status events.
- `properties.id` = messageID (`msg_…`) on message events; `part.id` (`prt_…`), `part.messageID` on part events.
- `permission.asked`: `properties.id` (`per_…`) + `tool.callID` (the provider tool_call id,
  e.g. `call_lab_1`) + `tool.messageID` — full correlation from permission → message → provider tool call.
- `message.part.delta`: `{sessionID, messageID, partID, field, delta}`.
- Envelope `id` (`evt_…`) is globally unique per event.
So a watcher can route signals to ONE specific session deterministically.

## 7. Capability assessments

### Completion detection (is the agent done with a turn?) — VERIFIED, good
- Primary: `session.idle` `{sessionID}` — fires exactly once per finished turn (both
  success and after rejection/error-in-tool paths).
- Supporting: `session.status` `{type:"busy"|"idle"}` transitions; final assistant
  `message.updated` has `time.completed`/`finish:"stop"` in the REST response body
  (`POST /session/{id}/message` returns the full finished message — a synchronous
  alternative). `GET /session/status` also exists (list of busy sessions).

### Attention detection (does it need human input?) — VERIFIED, good
- `permission.asked` fires when config `"permission": {"bash":"ask"}` (also edit/webfetch)
  gates a tool; carries per-request `per_…` id, tool name, patterns, command metadata,
  callID. Resolve via `POST /session/{sid}/permissions/{perID} {"response":"once"|"always"|"reject"}`
  or the TUI. Turn blocks (session stays `busy`, message POST hangs) until replied.
- Headless `opencode run` **auto-rejects** asks (~"permission requested: bash (…); auto-rejecting")
  → tool error → turn ends; no hang, exit 0 with error text.
- Same mechanism exists for `question.asked` (AskUserQuestion-equivalent) per schema (not live-tested).

### Streaming text — VERIFIED
`message.part.delta` carries incremental `delta` strings with `field:"text"`.

## 8. Session storage (XDG_DATA_HOME/opencode/)

- `opencode.db` (+`-wal`/`-shm`): **SQLite** (v1.18.31; no per-session JSON files).
  Tables: `session` (id, project_id, slug, directory, title, version, cost,
  tokens_input/output/reasoning/cache_*, `permission` JSON rules, agent, model,
  time_created/updated/compacting/archived), `message` (data JSON = role/agent/model/time),
  `part` (data JSON = full part incl. tool state/output), `todo`, `event` +
  `event_sequence` (a durable event store: observed rows `message.updated.1:35`,
  `message.part.updated.1:34`, `session.updated.1:26`, `session.created.1:6`),
  plus `permission`, `project`, `workspace`, `credential`, `account`, `session_share`, `migration`.
- `log/opencode.log`: structured `timestamp= level= run= message=` lines.
- `repos/`: per-worktree bookkeeping.
- Also useful: `opencode export [sessionID]` dumps a session as JSON; `GET /session`,
  `GET /session/{id}/message` on the server.

## 9. Honest unverified items / caveats

1. `session.error` payload not captured live (mock never returned a provider error);
   shape taken from the binary's own OpenAPI schema (`{sessionID, error:<union>}`).
2. `question.asked`, `permission.v2.asked`, `session.next.*`, `pty.*`, `workspace.*`,
   `vcs.branch.updated`, `installation.*`, `file.watcher.updated` etc. are schema-confirmed
   but not live-fired in this lab.
3. `opencode run` ↔ `serve` non-attachment was observed empirically (heartbeats only on
   SSE during a concurrent `run`); no lock-file internals were investigated. There IS an
   `opencode attach <url>` command (not exercised) that presumably subscribes a TUI to a server.
4. Plugin permission-hook names: we only proved `permission:{bash(){}}` does NOT fire in
   1.18.31; other hook spellings (e.g. `permission.ask`) were not exhaustively tried —
   the event + REST flow is the verified mechanism.
5. The plugin's `client` (SDK) and `$` context objects were received but not exercised.
6. Two background reaper quirks of the lab sandbox (a dead mock causing one
   "Cannot connect to API" run) are environmental, not opencode behavior.
7. Auto-reject timing in `run` mode is not precisely measured (rejection appeared
   immediately; a configurable timeout may exist in config).

## 10. Cleanup performed

`opencode serve` (ports 4548) and mock server processes were stopped after experiments;
`$EXP/xdg-data` + `$EXP/xdg-config` contain all state. Nothing outside
`~/agent-lab/experiments/opencode-1` (plus throwaway copies in `/tmp/opencode.db*`) was
modified; `~/Downloads/work/pocketshell` untouched.
