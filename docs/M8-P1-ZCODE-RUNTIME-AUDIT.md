# P1 — ZCode Runtime / GUI Boundary Audit (Agent L(P)) — PAUSED

**Status:** PAUSED by owner direction (2026-09-18). PARALLEL WORK 2 (Linux GUI
Runtime) took priority before the full report was written. This document
preserves the verified findings; raw lab evidence lives in
`~/agent-lab/experiments/p1-zcode-appserver/` on the Bunsen laptop (ephemeral —
probes copied to `docs/p1-lab/` in this commit, raw traffic logs kept out of
git on purpose: they contain real session titles).

Evidence classes used below: VERIFIED (live experiment / source inspection on
this machine), OBSERVED (read off installed artifacts), INFERRED (reasoned,
not directly proven), UNKNOWN.

---

## 1. The three ZCode artifacts (all on this laptop)

| Artifact | What it is | Evidence |
|---|---|---|
| **Official GUI** | Electron desktop app `ZCode-3.12.3-linux-x64.AppImage` (Electron 41.0.3). Renders the GUI; spawns the engine as a child host process (`zcode-host-local-1`, same Electron binary re-invoked). User data in `~/.config/ZCode`. | OBSERVED (process table, `/tmp/.mount_ZCode-*/resources`) |
| **Official runtime (the engine)** | Pure-JS single-file bundle `resources/glm/zcode.cjs` (11,416,833 bytes), meta: `{"runtime":"electron-node","entry":"zcode.cjs","platform":"linux-x64","source":"apps/zcode-cli/packages/cli/dist/zcode.cjs"}`. Runs the agent, the TUI, and the app-server. | VERIFIED (byte-identical copy runs standalone under Node 24) |
| **Unofficial/extracted ARM64-viable CLI** | The same `zcode.cjs` extracted to disk runs fine under a modern Node (`node:sqlite` requires Node ≥22.5; verified with v24.14.0, reports `zcode 0.16.5`). No native deps in the engine itself. Platform-specific parts are external tools: `resources/tools/{ripgrep,ugrep,bfs}` (shipped x64 binaries; ARM64 builds must be substituted). | VERIFIED (ran it live) |

Consequence: **the engine is portable JS**; ARM64 viability = portable Node +
ARM64 tool binaries. The GUI is the only x86-bound piece, and it is not needed
to drive the runtime.

## 2. The official protocol surfaces (VERIFIED live unless noted)

`zcode app-server` — help text: *"Run the ZCode Protocol stdio app server"*.
There is also an `agent-server` alias. Over **newline-delimited JSON on
stdin/stdout**, an external process can drive the whole runtime.

### 2.1 RPC methods (method enum recovered from the bundle; subset live-fired)

```
runtime/capabilities                    session/create  session/resume
session/list   session/subagents        session/read    session/messages
session/events session/subscribe        session/send    session/stop
session/cancelBackgroundTask             session/fork    session/compact
session/goal   session/close             session/setModel
session/setThoughtLevel session/setMode  session/requestRuntimePreferences
workspace/readPresentation               workspace/hooks/trustGrant
workspace/updateInteractionPreferences   workspace/updateModelIoPreferences
workspace/updateOffPeakToolPolicy        workspace/generateText (cancel)
provider/updateAccountConfig             provider/testModelConnectivity
mcp/list  plugins/* (list/install/uninstall/update/marketplace/configure/validate/describe)
automation/* (create/update/list/delete/checkTaskBinding)   offPeak/* (create/list)
usage/stats   session/usage   process/childProcesses
interaction/requestPermission  interaction/requestUserInput
interaction/requestProviderRuntimeHeaders  interaction/requestOfficialMcpAuthHeaders
interaction/browserList  interaction/browserExecute
```

Live-fired: `runtime/capabilities` → `{"independentPlanState":true}`;
`session/list` → real sessions (`sessionId,title,mode,status,sessionKind,createdAt,updatedAt,traceId,workspace{workspaceKey,workspacePath}`);
`usage/stats {range:all|7d|30d}` → full usage dashboard payload (§4);
`session/create {workspace}` → new session + projection snapshot;
`session/resume {sessionId,workspace}` → full message history;
`session/subscribe {sessionId,deliveryKind:"desktop-continuous"|"web-remote-replayable"}`
→ subscription with `eventSeq` cursor. `automation/*`/`offPeak/*` → "Method
not found" in this build's app-server (GUI-host layer methods — INFERRED).

### 2.2 The protocol is a state-replication system (VERIFIED from schemas)

Session snapshot schema (`protocolVersion:1`): `control` (phase
draft/prewarming/running/completedSuccess/completedInterrupted/error,
canStop/stopState, activeWorks, lastError, apiRetry), `availability`
(per-action allowed/reasonCode: fork, compact, switchModelConfig,
setFollowupMode, queueEdit, sendQueuedNow, pauseGoal, resumeGoal),
`inputRouting` (startNow/enqueue/guide/reject/choice), `meta.title`,
`config` (provider, model, thought level, followupMode queue/guide, mode,
planEnabled), `modelTransition`, **`usage`** (contextWindow usedTokens/
maxTokens/autoCompactThreshold + cumulative tokens), `queue` (queued inputs,
autoDrain, pauseReason), **`pendingInteractions`** (permission with
allowOnce/allowAlways/deny/custom options; userInput (AskUserQuestion) with
options/multiSelect; workspaceHookReview), `pendingCommands`,
**`backgroundWorks`** (bash/subagent, running/resultPending/failed/cancelled,
cancellable, childSessionId), **`subagents`** (childSessionIds + running
entries with subagentType/title/status running/waiting/blocked + endedTotal),
**`goal`** (objective, status active/paused/verifying/verified/notSatisfied/
failed, iterations, verifications), **`plan`** (todo items
pending/inProgress/completed, iteration), `workspaceHookAdmission`, `rows`
(paged activity log: assistantText/reasoning/toolCall/userInput/error/
checkpoint/file/subagent/timelineMarker/hookInvocation/... rows; toolCall row
carries toolCallId/toolName/status
inputStreaming|pendingApproval|running|success|error|cancelled/input/output/
progress/approvalInteractionId).

Transport: `clientHello` (protocolVersion **3**, clientId,
`clientKind: desktop|web|mobileRemote|mobileApp`, appVersion) → `hello`;
subscriptions deliver `snapshot` then `deltas`; large payloads use
`fragment`/`complete`. **The protocol explicitly anticipates mobile clients
(`mobileRemote`, `mobileApp` client kinds) and there is a full v4
controller/conversation remote API** (`v4/connection/flow`,
`v4/controller/subscribe|resync|unsubscribe`, `v4/conversation/subscribe|
resync|unsubscribe|rowsRange|plans|fileChanges|fileRewindPreview|usage`,
`v4/attachment/*`, `v4/usage/stats`, `v4/commands/query|command`).
UNKNOWN: whether the v4 gateway ships in the CLI app-server build or only in
the GUI's host (its methods are wired via `requireV4Gateway()` — did not
live-fire a v4 gateway).

### 2.3 Bidirectional: the server calls the client (VERIFIED live)

During `session/resume` the server issued `session/requestRuntimePreferences`
(15 s timeout; result requires at least
`{nativeSearchEnhancementsEnabled:boolean}`) and during startup
`interaction/requestOfficialMcpAuthHeaders`, `process/mcpTelemetry`.
Permission prompts and AskUserQuestion arrive as server→client interaction
requests / pendingInteractions; the client answers via
`resolveInteraction {interactionId, answer:{optionId|freeText|
action:accept|decline|cancel, content}}`. (resolveInteraction VERIFIED from
schema; live approval round-trip not yet fired — next step when resumed.)

### 2.4 Subagents, history, sessions

Subagent sessions exist as first-class sessions (`sess_subagent_agent_*`,
`parent_id` chain) both in `session/list` and in the SQLite DB
(`~/.zcode/cli/db/db.sqlite`: tables `session, message, part, model_usage,
turn_usage, tool_usage (approval_status, side_effect_scope, read_only,
destructive), todo, session_task_link (agent tree w/ role/depth/path),
workflow_* / dwf_* engines, input_history, session_input`). Per-session
model-I/O transcripts: `~/.zcode/cli/rollout/model-io-sess_*.jsonl`.
INFERRED: the DB is a local projection; the protocol is the supported access
path (the DB schema is not a contract).

## 3. The official GUI vs what the runtime exposes

GUI features inspected in app.asar: Settings→Usage ("Sync exact plan level,
5-hour prompt pool, weekly quota, and monthly tool quota from the connected
Coding Plan"; `chat.planUsage.contextWindow/contextDetail/promptPool/
promptReset/weeklyQuota` widgets), **quota reset flows**
(`codingPlanQuotaReset*` state machine, resetType `FIVE_HOUR`/`WEEK`,
startedAt/completedAt/observedAt lifecycle), quota banners
(`remainingTokens`, `remainingPercent`, `blocksSubmit`), MCP status, plugins
marketplace UI, automations UI, remote-control (clientMode
desktop-continuous/web-remote-replayable; Telegram/WeChat drive configs also
present).

Verdict per area: **everything the GUI shows maps to runtime data already
exposed by the protocol or local DB** — quota numbers (§4), plan identity
(entitlement state machine: coding_plan/start_plan/no_plan/unknown from
`quota`/`subscription`/`remaining` snapshot), usage, sessions, subagents,
tools, permissions, diffs (summary_additions/deletions/files + fileChanges
endpoints), plans/goals. GUI-only bits observed: the quota-reset operation
buttons and the OAuth flows (auth material lives host-side). No GUI capability
was found that inherently requires the GUI process.

## 4. Quota / usage — the answers

- **Per-session context usage** — protocol `usage` snapshot +
  `usage.delta` events (contextWindow usedTokens/maxTokens/
  autoCompactThresholdTokens, cumulative in/out/cache tokens). VERIFIED (schema).
- **Aggregate usage dashboard** — `usage/stats {range}` VERIFIED live:
  totals, cache hit rate, session/turn/tool-call counts, tool error rate,
  avg TTFT/turn duration, active-day streaks, GitHub-style daily heatmap,
  per-model daily breakdown. Same data also exists locally in
  `model_usage`/`turn_usage`/`tool_usage` tables.
- **Plan quota (5-hour pool / weekly / monthly tool quota)** — the GUI fetches
  provider-side state: `GET {zai-origin}/coding-plan/personal/overview` (and
  `/coding-plan/team/plans`) with coding-plan auth headers
  (`X-Bigmodel-Authorization` etc.). The CLI exposes quota *state* via the
  entitlement snapshot (`t.quota||t.subscription||t.remaining`) and
  `remainingPercent` banners; error codes `quota_exceeded`,
  `coding_plan_required`, `rate_limited` are protocol-visible. UNKNOWN: exact
  JSON schema of the overview response (not reversed yet); a PocketShell
  adapter can obtain it the same way the GUI does (same credentials, same
  endpoint) — auth reuse needs a device-gate check of the credential store.

## 5. Implications for a PocketShell ZCode Mode (design sketch, NOT started)

1. A native GUI can spawn `zcode app-server` (stdio) and get: session list,
   full conversation, live streaming rows (assistant text + reasoning +
   tool calls with status), subagent tree, permission approvals (user can tap
   allow/deny in the GUI), queue/goal/plan control, model/thought/mode
   switching, usage + context meters — **without scraping terminal output**.
2. Notifications: runtime events (`pendingInteractions`, control phase
   transitions, turn.terminal, usage) are authoritative and structured —
   a ZCode adapter can drive M7.2-style attention/turn notifications from
   protocol events instead of (or alongside) the P10 hook bridge. The P10
   bridge remains the fallback for agents without such a protocol; it must not
   be replaced for them.
3. Risks/unknowns: protocol is UNDOCUMENTED and version-frozen (handshake v3 /
   snapshot v1) — pin the engine version in the adapter, gate on
   `runtime/capabilities`, tolerate unknown fields; `node:sqlite` requires
   modern Node on the ARM64 guest (check guest Node version); ARM64 needs
   ripgrep/ugrep/bfs equivalents in the guest (Alpine has ripgrep; ugrep/bfs
   are optional perf tools).

## 6. Other agents (survey NOT completed — researchers were stopped)

P10 §3.1 already documents the hook/notify/plugin mechanisms per agent
(Claude Code hooks 13 events; Codex notify + hooks; OpenCode plugin bus 89
events; Kilo fork of OpenCode; Cline script hooks; Qwen Code Claude-compatible
hooks + `--json-fd`; Gemini/Antigravity/Hermes entries). The deeper
"external GUI can drive the runtime" survey (Claude Code stream-json/control
protocol, Codex app-server JSON-RPC, OpenCode HTTP+SSE server API, ACP for
Qwen/Kilo/Cline) was IN PROGRESS via two researcher agents and got stopped
with the pivot. Findings should be re-collected when the ZCode Mode track
resumes.

## 7. Honesty notes

- Live-fired on x86 laptop against the real logged-in runtime: handshake,
  session/list, usage/stats, session/create, session/resume (+ server→client
  preference requests), session/subscribe. NOT yet live-fired: a full turn
  with row deltas streaming to a client, permission round-trip, v4 gateway,
  fork/compact/goal. Cost of resuming: one tiny probe turn.
- All protocol knowledge beyond the live-fired subset comes from reading the
  shipped bundle (OBSERVED from source) — legitimately strong evidence (it is
  the code that runs), but not a public contract.
- No Android/device validation of any of this has happened.
