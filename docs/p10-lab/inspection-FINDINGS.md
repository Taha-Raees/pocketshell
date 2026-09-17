# Static Mechanism Inspection — Six CLI Coding Agents

Date: 2026-09-17. Method: static inspection only (no API calls, no LLM runs). Sources: `--help` output, package/bundle JS greps, `strings` on native ELF binaries, on-disk state dirs.
Scratch: `agy_strings.txt`, `/tmp/kilo_strings.txt` (string dumps used for evidence).

Legend: **VERIFIED** = seen directly in code/help/strings/on disk. **LIKELY** = strong inference (e.g., standard behavior of a fork parent). **UNKNOWN** = not resolvable statically.

---

## 1. Kilo Code CLI (`kilo`, v7.0.27)

Install: `/usr/local/lib/node_modules/@kilocode/cli` — npm wrapper (`bin/kilo`, a Node launcher honoring `KILO_BIN_PATH`) that spawns a **Bun-compiled native ELF**: `node_modules/@kilocode/cli-linux-x64/bin/kilo` (157 MB). It is an **OpenCode fork** (embedded source paths `src/...`, mDNS default `opencode.local`, dependency on `@opencode-ai/plugin`, and a legacy `~/.local/share/opencode/opencode.db` on this machine).

### A. Hooks / plugins / events — VERIFIED
- **Plugin system** (OpenCode-style). Config dir `package.json` declares `"dependencies": {"@opencode-ai/plugin": "7.0.27"}` (seen in `~/.config/kilo/package.json`). Plugins are listed under `config.plugin` (`const list2 = sync.data.config.plugin ?? []`) and resolved relative to the config file (`data3.plugin[i8] = import.meta.resolve(plugin, configFilepath)`); each plugin module's exports are called with an init input and the returned objects are the hooks.
- **Plugin.trigger(name, input, output)** dispatch; hooks registered for: `chat.message`, `tool.execute.before`, `tool.execute.after`, `experimental.chat.messages.transform` (all seen as literal `await Plugin.trigger(...)` call sites in the binary strings).
- **Event bus** (`Bus`/`BusEvent.define`): `Bus.subscribeAll` feeds every event to `hook["event"]?.({ event: input })` — so plugins can observe *all* lifecycle events. Bus events include (all VERIFIED strings):
  - `permission.asked`, `permission.replied`, `permission.list`, `permission.reply`
  - `session.idle`, `session.error`, `session.deleted`, `session.created`, `session.compacted`, `session.abort`, `session.init`
  - `file.edited`, `file.watcher.updated`, `file.time`, `file.list`...
- Config: `~/.config/kilo/kilo.jsonc` (verified content: `permission.bash` allow-rules, `permission.external_directory`), `$schema: https://app.kilo.ai/config.json`. Also `~/.kilo/` (rules, skills, workflows, node_modules, kilo.jsonc) and project `.kilo/` dirs (code checks `dir.endsWith(".opencode") || dir === Flag.KILO_CONFIG_DIR`).

### B. Attention / waiting-for-user signal — VERIFIED
- `BusEvent.define("permission.asked", PermissionNext.Request)` plus handling code `if (event.type === "permission.asked")`. Every permission request is a first-class bus event observable by plugins and by the SDK event stream. CLI flag `--auto` = "auto-approve all permissions (for autonomous/pipeline usage)".

### C. Completion / task-done signal — VERIFIED
- `BusEvent.define("session.idle", ...)` — fired when the agent turn finishes; also `session.error` and `message.part.updated` part type `step-finish`. In `kilo run --format json` mode these surface as JSON events (see D).

### D. Machine-readable output — VERIFIED
- `kilo run --format json` (`choices: "default","json"`): emits NDJSON lines `{type, timestamp, sessionID, ...data}` with `type` ∈ `tool_use`, `step_start`, `step_finish`, `text`, `reasoning`, `error` (exact emit code seen: `process.stdout.write(JSON.stringify({ type, timestamp: Date.now(), sessionID, ...data2 }) + EOL2)`), driven by `sdk.event.subscribe()`.
- **ACP server**: `kilo acp` (Agent Client Protocol). **HTTP server**: `kilo serve` + `kilo attach <url>` + `--port/--hostname/--mdns/--cors`. `kilo mcp` for MCP management.
- `kilo session list --format json`; `kilo export [sessionID]` (session as JSON), `kilo import`.

### E. Structured state — VERIFIED
- `~/.local/share/kilo/`: `kilo.db` (+ `-shm`/`-wal`, **SQLite**), `auth.json`, `log/YYYYMMDDTHHMMSS.log`, `telemetry-id`, `storage/{migration,session_diff,session_share}`, `plans`, `repos`. Sessions live in the SQLite DB (legacy JSON `storage/session` layout was migrated — `src/storage/json-migration.ts` string seen). Ids use short prefixes (`"ses"`, `"msg"`, `"prt"`, `"usr"`, `"cmp"` literals present; OpenCode convention `ses_...` — prefix VERIFIED as string, format LIKELY `ses_<random>`).
- Session correlation: `sessionID` is embedded in every `--format json` event line; `--session <id>` / `-c` continue; `kilo session list --format json` prints `id/title/created/updated/projectId/directory`.

### F. Per-session config isolation via env — VERIFIED
- `KILO_CONFIG_DIR` (config dir override; used for `AGENTS.md` discovery too), `KILO_CONFIG` (config file), `KILO_CONFIG_CONTENT` (inline config JSON) — all seen as `process.env[...]` reads in the binary. Plus `KILO_BIN_PATH`, `KILO_CLIENT` (`cli|acp|app|desktop|vscode`), `KILO_SERVER_USERNAME/PASSWORD`, `KILO_EXPERIMENTAL_PLAN_MODE`, and XDG (`XDG_DATA_HOME`, `XDG_CONFIG_HOME`, `HOME`) respected. `--config`-style isolation is achievable with `KILO_CONFIG_DIR` + `XDG_DATA_HOME` per session.

---

## 2. Cline CLI (`cline`, v2.5.0)

Install: `/usr/local/lib/node_modules/cline`, readable ESM bundles `dist/cli.mjs` (27 MB) / `dist/lib.mjs` (21 MB). Requires Node ≥ 20 (system Node 18 fails on `v`-flag regex; used `~/agent-lab/downloads/node-v22.14.0-linux-x64/bin`).

### A. Hooks / plugins — VERIFIED (Claude-Code-style script hooks)
- Hook directory: `<workspace>/.clinerules/hooks/<HookName>` and global `~/.cline/...` equivalent (code: `join(c, ".clinerules", "hooks")`; discovery counts `globalCount, workspaceCount`).
- Hook name list (exact array `Sta`): **`TaskStart, TaskResume, TaskCancel, TaskComplete, PreToolUse, PostToolUse, UserPromptSubmit, PreCompact`**.
- Hooks are external **scripts** (`this.scriptPath`, `exitCode`, `timeout`, `cancelRequested`, `contextModified`, `contextSize`, `pendingToolInfo` passed in; hook returns a **JSON response** — "`[Hook ...] Completed successfully but no JSON response found`"; a nonzero exit or `{cancel:true}` cancels; `PreToolUseHookCancellationError` = "PreToolUse hook requested cancellation"). `refreshHooks` (`.clinerules/hooks` scan), `SDKHooks` class, hooks gate behind a feature flag `requiresFlag:"hooks"`. Hook execution logged to `~/.cline/data/logs/hooks.jsonl` (verified on disk: entries with `taskId`, `rootSessionId`, `hookName`).
- No MCP/extension hook registry beyond this in the CLI build.

### B. Attention / waiting-for-user signal — VERIFIED
- The Cline `ask` mechanism: message `type === "ask"` with `ask` ∈ {`followup`, `tool`, `command`, `error`, `api_req_failed`, `completion_result`, ...} — the CLI pauses for user yes/no (`yesButtonClicked` / `noButtonClicked`, `didRejectTool`). In `--acp` mode these map to ACP `sessionUpdate:"tool_call_update"` (status `failed`, `rawOutput:{reason:"rejected"}`) and ACP permission requests ("[ClineAgent] Error handling permission request").
- `CLINE_COMMAND_PERMISSIONS` env var can pre-block commands ("Command `X` was denied by CLINE_COMMAND_PERMISSIONS").

### C. Completion signal — VERIFIED
- `attempt_completion` tool + `completion_result` say/ask type (`r.type==="say"&&r.say==="completion_result" || r.type==="ask"&&r.ask==="completion_result"`); `TaskComplete` hook fires at task end; subagent runner emits `{status:"failed", error:"Subagent did not call attempt_completion."}`. In `--json` output mode the stream emits an `event: "completion"` record (`n.event==="completion"` → `JSON.parse(n.data)`).

### D. Machine-readable output — VERIFIED
- `--json` ("Output messages as JSON instead of styled text") on both root and `task` subcommand; event stream includes at least `completion` events.
- `--acp` (Agent Client Protocol; `sessionUpdate` types: `agent_message_chunk`, `agent_thought_chunk`, `available_commands_update`, `plan`, `tool_call`, `tool_call_update`). MCP support throughout (client + `cline_mcp_settings.json`). No socket daemon.

### E. Structured state — VERIFIED
- `~/.cline/data/` (verified layout): `sessions/<taskId>/<taskId>.json` (session header: `session_id`, `source:"cli"`, `pid`, `started_at`, `status:"running"`, `interactive`, `provider`, `model`, `cwd`, `workspace_root`) and `<taskId>.messages.json` (full message list); `db/sessions.db` (SQLite, +wal/shm); `logs/cline-cli.1.log` and `logs/hooks.jsonl`; `globalState.json`, `secrets.json`, `settings/`, `workspaces/<hash>/workspaceState.json`; task list under `~/.cline/kanban`, worktrees `~/.cline/worktrees`.
- Ids: session id like `3561d-1777830326681-colo4a23`; task/ULID `taskId` like `conv_1777830326911_arpxt26` (both verified in files); `--taskId <id>` resumes.

### F. Per-session config isolation via env — VERIFIED
- `CLINE_DATA_DIR` (data root override — exact `process.env.CLINE_DATA_DIR` read), `CLINE_DIR`, `CLINE_LOG_DIR`, `CLINE_ENVIRONMENT_OVERRIDE`, `CLINE_NO_AUTO_UPDATE`, `CLINE_OTEL_*`, plus CLI `--config <path>` ("Configuration directory"). Combining `CLINE_DATA_DIR` + `--config` gives full per-session isolation.

---

## 3. Hermes (`hermes`, python venv)

Install: `/home/muhammad-taha/.hermes/hermes-agent` (package `hermes_cli` + `agent/` + `tools/`; venv has `hermes` entry point). Config home `~/.hermes/` (verified: `config.yaml`, `sessions/`, `hooks/`, `checkpoints/`, `processes.json`, `gateway_state.json`, `kanban.db`, `logs/`, `pairing/`).

### A. Hooks / plugins / events — VERIFIED (two layers)
- **Shell hooks** (`agent/shell_hooks.py`, CLI `hermes hooks list|test|revoke|doctor`): configured in the **`hooks:` block of `~/.hermes/config.yaml`** (verified key present, empty in this install: `hooks: {}`, plus `hooks_auto_accept: false`). Spec = dataclass `ShellHookSpec {event, command, matcher?, timeout}`; events validated against the same `VALID_HOOKS` set; consent allowlist `~/.hermes/shell-hooks-allowlist.json` (`(event, command)` approvals); hooks run a command and **parse JSON from stdout** ("shell hook stdout was not valid JSON (event=%s)"). Docs path referenced in code: `website/docs/user-guide/features/hooks.md`.
- **Plugin hooks** (`hermes_cli/plugins.py`, entry-point group `hermes_agent.plugins`): full `VALID_HOOKS` set (verified, exact names): `pre_tool_call, post_tool_call, transform_terminal_output, transform_tool_result, transform_llm_output, pre_llm_call, post_llm_call, pre_api_request, post_api_request, api_request_error, on_session_start, on_session_end, on_session_finalize, on_session_reset, subagent_start, subagent_stop, pre_gateway_dispatch, pre_approval_request, post_approval_response`.
- **Webhooks**: `hermes webhook subscribe|list|remove|test` — dynamic inbound webhook subscriptions persisted to `~/.hermes/webhook_subscriptions.json` (HMAC-secret protected, hot-reloaded by the gateway webhook adapter).

### B. Attention / permission signal — VERIFIED
- Approval flow in `tools/approval.py` fires **`pre_approval_request` / `post_approval_response`** plugin hooks "BOTH for CLI-interactive prompts and for gateway/ACP approvals (Telegram, Discord, Slack, TUI...)". Choices: `once | session | always | deny | timeout`. Interactive `approval_callback(cli, command, description)` in `hermes_cli/callbacks.py`. YOLO bypass: `--yolo` / `HERMES_YOLO_MODE=1` (oneshot sets it).
- `pre_gateway_dispatch` hook can `{action:"skip"|"rewrite"|"allow"` every inbound message (attention-control surface for gateways).

### C. Completion / done signal — VERIFIED
- **`bell_on_complete`** display config (`CLI_CONFIG["display"].get("bell_on_complete", False)`) → writes terminal bell `\a` when the agent finishes a response (`sys.stdout.write("\a")`, noted "Works over SSH").
- **`notify_on_complete`** (the GitHub-issue mechanism): a **terminal-tool parameter** (`terminal_tool.py`: `background=True` + `notify_on_complete=True` → "you'll be notified exactly once when the process exits"), implemented in `tools/process_registry.py` (`ProcessSession.notify_on_complete`, `drain_notifications()`, `format_process_notification(evt)`; watch-pattern matches rate-limited 1/15s, 3 strikes then auto-promote to notify_on_complete). Delivery in `cli.py`: drained notifications are pushed into `self._pending_input` — i.e., **injected into the agent loop as synthetic user input** (matches issue "CLI notify_on_complete injects notification as user"). Config: `display.background_process_notifications: all` (verified in `~/.hermes/config.yaml`), per-session persisted in `~/.hermes/processes.json`.
- No "run completed" hook beyond `on_session_end` / `on_session_finalize`.

### D. Machine-readable output — VERIFIED
- `hermes -z/--oneshot "prompt"`: "send a prompt, get the final content block, exit. ... Just the agent's final text to stdout", auto-bypasses approvals.
- `hermes acp` (ACP server), `hermes mcp` ("run Hermes as an MCP server" + manage MCP), `hermes send` (send a message via platform — for scripts/cron/CI), gateway + web server + TUI websocket (`tui_gateway/server.py` also drains notifications), `hermes sessions export`.
- No `--output-format json` for chat itself (stdout is human-oriented outside oneshot).

### E. Structured state — VERIFIED
- `~/.hermes/sessions/`: `session_<YYYYMMDD>_<HHMMSS>_<6hex>.json` (session metadata) + `<...>.jsonl` transcript, `sessions.json` index; also `request_dump_*.json`. Background processes in `~/.hermes/processes.json`; `gateway_state.json`; `checkpoints/`; `kanban.db`; logs under `~/.hermes/logs`.
- Correlation: `--resume SESSION`, `--continue [NAME]`, `--pass-session-id` flag; session id embedded in file names; `hermes sessions list|rename|export|prune|delete`.

### F. Config isolation via env — VERIFIED
- `HERMES_HOME` env var overrides the whole config/state home (`hermes_constants.get_hermes_home()`: "Reads HERMES_HOME env var, falls back to the platform-native default"; plus an internal `_HERMES_HOME_OVERRIDE` ContextVar). `--ignore-user-config` flag exists. Per-session: `HERMES_HOME=$(mktemp -d)`.

---

## 4. Antigravity CLI (`agy`, 217 MB stripped Go ELF)

`/home/muhammad-taha/.local/bin/agy`. Internals: Google `google3/third_party/gemini_coder/framework` + `jetski`/`cortex_pb` + `exa` protos. Help output and `strings` dump (`agy_strings.txt`, 604k lines).

### A. Hooks / plugins — VERIFIED (embedded docs + strings)
- **`hooks.json`** — Claude-Code-style named hooks. Locations: `<workspace>/.agents/hooks.json` (workspace), `~/.gemini/config/hooks.json` (shared/global), `~/.gemini/antigravity-cli/hooks.json` (CLI-local; changelog: `/hooks` command now writes the shared file), `plugins/<name>/hooks.json` (bundled with plugins).
- Embedded doc (exact, from binary): hook file format = JSON object, each top-level key a **named hook** → `{enabled?, PreToolUse:[{matcher, hooks:[{type:"command", command, timeout}]}], PostToolUse:[...], PreInvocation:[...], PostInvocation:[...], Stop:[...]}`. Supported events: **`PreToolUse`, `PostToolUse`, `PreInvocation`, `PostInvocation`, `Stop`** ("Handlers running when the execution loop terminates"); matcher = tool name; hooks merge sequentially across files/plugins.
- Underlying engine (strings): `jsonhook.JSONHookSpec/ParseHooksFile/HookHandler.CommandLine/executeCommandModeHook/NewCommandHook`, `agent.PreToolHook/PostToolHook/StopHook`, `core.InvocationHook`, protos `SessionStartHookArgs, PostToolArgs, PostTurnArgs, StopHookArgs, OnCompactionArgs, HookToolCall, PreToolHookResult, HookEphemeralMessage`. Changelog: "`Stop` hooks run at all instead of sitting unreachable behind the built-ins"; stop hooks can block continuation but are capped ("after a configurable number of consecutive continuations, the hook can no longer block", counter string `stop_hook_continuations`).
- **Plugin subsystem**: `agy plugin install|uninstall|list|enable|disable`; plugins contribute skills/rules/hooks/MCP ("Automatic Ingestion: All skills, rules, hooks, and MCP servers defined..."). `/hooks` slash command in-session.

### B. Attention / waiting-for-user signal — VERIFIED (state + prompts)
- **`STATE_WAITING_FOR_USER`** executor-state enum (with `STATE_RUNNING, STATE_COMPLETED, STATE_WAITING_FOR_TASKS, STATE_FULLY_IDLE, STATE_ERROR, STATE_CANCELLED`) — part of the language-server/remote-control conversation state model (`language_server_go_proto`, RPC `WaitForConversationFullyIdle`, `GetSidecarEvents`, `HandleCascadeUserInteraction`, `StreamCascadeReactiveUpdates`).
- Permission prompts: `--dangerously-skip-permissions`, `--mode accept-edits|plan`, persisted grants in `~/.gemini/antigravity-cli/settings.json` under `permission.allow` (changelog-verified); headless `-p` **soft-denies** tools needing approval and prints a stderr notice (no external signal emitted in print mode).
- Internal notify RPC: `/v1internal:notifyUser` + `devtools_jetski_boq_api_proto.NotifyUserRequest{event, instance_id}` (server push to UI channels).

### C. Completion signal — VERIFIED
- stream-json **terminal `result` event** (see D); `Stop` hook fires when the execution loop terminates; executor `STATE_COMPLETED` / `STATE_FULLY_IDLE`; literal marker string `<!-- GOAL_COMPLETE -->` (coordinator/subagent goal-complete marker); "Time to completion" telemetry.

### D. Machine-readable output — VERIFIED (richest of the six)
- `--output-format text|json|stream-json` (print mode), `--input-format text|stream-json` (NDJSON turns from stdin), `--json-schema` (structured final output). Changelog (exact): "a strongly-typed NDJSON event stream that emits typed `init`, `step_update`, and terminal `result` events with a stable, closed-vocabulary `step_type` discriminator"; usage object includes `cache_read_tokens`.
- `agy mcp` management; `--remote-control` (creates a remote connection for the session; `remote-control` background daemon start/status/stop); `ANTIGRAVITY_LS_ADDRESS`/`ANTIGRAVITY_SIDECAR_WEB_PORT`/`ANTIGRAVITY_AGENTAPI_EXE` env wiring for its language-server/sidecar processes; `mic-serve` etc.
- Session resume: `--conversation <ID>`, `-c/--continue`, `--project <ID|name>`.

### E. Structured state — VERIFIED
- `~/.gemini/antigravity-cli/`: `conversations/<UUID>.db` (**SQLite per conversation**, verified files e.g. `0498506e-...db`), `history.jsonl` (verified record shape: `{"display","timestamp","workspace","conversationId"}`), `cli.log`, `installation_id`, `cache/projects.json`, `mcp_config.json`, `keybindings.json`, `brain|builtin|implicit|projects|sidecars|plugins` dirs; shared `~/.gemini/config/{skills,workflows,projects,mcp_config}`.
- Correlation: `conversationId` (UUID) in history.jsonl + per-conversation db file + `--conversation` resume + `ANTIGRAVITY_CONVERSATION_ID` env + `ANTIGRAVITY_TRAJECTORY_ID`.

### F. Per-session config isolation via env — PARTIAL
- No `AGY_CONFIG_DIR`-style override found (UNKNOWN). Env vars seen: `ANTIGRAVITY_SIDECAR_WEB_PORT, ANTIGRAVITY_CONVERSATION_ID, ANTIGRAVITY_PROJECT_ID, ANTIGRAVITY_LS_ADDRESS, ANTIGRAVITY_BROWSER, ANTIGRAVITY_BROWSER_WS_URL(AGY_), ANTIGRAVITY_AGENTAPI_EXE, ANTIGRAVITY_VSCODE_HOST, ANTIGRAVITY_TRAJECTORY_ID, ANTIGRAVITY_CSRF_TOKEN, ANTIGRAVITY_SIDECAR_UI_TOKEN, ANTIGRAVITY_LOGIN_PATH__, ANTIGRAVITY_SAFECLIS_SOURCE, AGY_ADC_AUTH, AGY_CLI_HIDE_LOGO, AGY_CLI_DISABLE_LATEX, AGY_CLI_DISABLE_ESCAPE_SEQUENCE_OPTIMIZATIONS, AGY_CLI_CMD_OUTPUT_PERCENTAGE`, plus `XDG_CONFIG_HOME/XDG_CACHE_HOME/XDG_DATA_HOME` (5/3/2 refs) and `GOOGLE_GEMINI_BASE_URL`, `GEMINI_API_KEY`, `GOOGLE_API_KEY`, `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_PROJECT`. Per-session isolation therefore via `HOME`/`XDG_CONFIG_HOME` redirection or `--log-file` only (LIKELY, not a first-class knob).

---

## 5. Qwen Code (`qwen`, v0.24.0, Gemini-CLI fork)

Install: `~/agent-lab/npm/lib/node_modules/@qwen-code/qwen-code` (bin `cli-entry.js`; main bundle `cli.js` + 54 MB `chunks/*.js`).

### A. Hooks — VERIFIED
- `qwen hooks` subcommand ("Manage Qwen Code hooks (use /hooks in interactive mode)"); hooks are configured **in settings.json** (UI string: "To add hooks, edit settings.json directly or ask Qwen."); `--safe-mode` "Disable all customizations (context files, hooks, extensions, skills, MCP servers)"; `getDisableAllHooks` config accessor.
- Event names (HookEventName enum in chunks; VERIFIED): **`PreToolUse, PostToolUse, SessionStart, SessionEnd, Notification, UserPromptSubmit, Stop, SubagentStop, PreCompress`** (Claude-Code-compatible). Hook outputs modeled as classes (`PreToolUseHookOutput extends DefaultHookOutput`) with per-event `case "PreToolUse"` decision handling.

### B. Attention signal — VERIFIED
- `ask_user_question` tool with daemon re-hang flag: `--restore-ask-user-question` — "On daemon session load/resume, re-hang a trailing unanswered ask_user_question instead of synthesizing a failed tool result".
- `Notification` hook event; approval machinery (`--approval-mode plan|default|auto-edit|auto|yolo`, `--allowed-tools` bypass); ACP permission requests via `--acp`.

### C. Completion signal — VERIFIED (headless) / LIKELY (hook)
- stream-json final result + `--json-schema` ("the session ends on the first valid call" of synthetic `structured_output` tool); run budgets abort with exit code 55 (`--max-wall-time`, `--max-tool-calls`). A `Stop`-style hook event exists (name VERIFIED; firing semantics LIKELY same as Gemini parent).

### D. Machine-readable output — VERIFIED (strongest in test)
- `-o/--output-format text|json|stream-json`; `--include-partial-messages`; **`--json-fd <fd>` / `--json-file <path|FIFO>`** — "dual output mode": TUI renders normally while structured JSON events stream to a separate fd/file/fifo (ideal external watcher channel); `--input-format stream-json`; `--input-file` ("external process writes JSONL commands; the TUI watches and processes them" — bidirectional remote control); `--json-schema` headless structured output; `--channel SDK|CI|daemon|ACP|VSCode|desktop`; `--acp`; `qwen serve` ("local HTTP daemon"); `qwen mcp`; `qwen board`/`qwen channel` inter-agent messaging; `--session-id`, `--resume`, `--fork-session`, `--continue`, `qwen sessions`.

### E. Structured state — VERIFIED (code) / dir absent (not run yet)
- Session transcripts: `<projectDir>/chats/<sessionId>.jsonl` (`chatFile: join10(projectDir,"chats",\`${sessionId}.jsonl\`)`), `SESSION_FILE_PATTERN = /^[0-9a-fA-F-]{32,36}\.jsonl$/` (UUID ids); global root `~/.qwen` via `Storage.getGlobalQwenDir()`; `.qwen/` project dirs (`.qwen/agents`, `.qwen/artifacts`, `.qwen/archived`, `.qwen/worktrees/<slug>`, `.qwen/arena`); `~/.qwen` not present on this machine (Qwen never run here).

### F. Per-session config isolation via env — VERIFIED
- **`QWEN_HOME`** (global config/state dir override: `const envDir = process.env["QWEN_HOME"]`; sandbox launcher even passes `--env QWEN_HOME=...`), `QWEN_DIR` (project dotdir name), `QWEN_RUNTIME_DIR`, `QWEN_CODE_SESSION_ID`, `QWEN_MODEL`, `QWEN_DEBUG_LOG_FILE`, `QWEN_MEMORY_SETTINGS`, `QWEN_TLS_INSECURE`, `QWEN_SANDBOX`, `QWEN_DAEMON_URL_ENV`/`QWEN_DAEMON_TOKEN_ENV`/`QWEN_SERVER_TOKEN` (daemon auth), `QWEN_CODE_IDE_SERVER_STDIO_COMMAND/ARGS`, `QWEN_DISABLED_SLASH_COMMANDS`, `QWEN_CODE_PROJECT_DIR`, `QWEN_OPENAI_LOGDIR`-style logging dirs. Per-session isolation: `QWEN_HOME=$(mktemp -d)` — first-class.

---

## 6. Gemini CLI (`gemini`, v0.60.0)

Install: `~/agent-lab/npm/lib/node_modules/@google/gemini-cli` (bin `bundle/gemini.js`).

### A. Hooks / extensions — VERIFIED
- `gemini hooks <command>` subcommand; **`gemini hooks migrate`** — "Migrate hooks from Claude Code to Gemini CLI".
- Hook events (HookEventName enum + `fire*` call sites, VERIFIED): **`SessionStart, SessionEnd, BeforeAgent, AfterAgent, BeforeModel, AfterModel, BeforeTool, AfterTool, BeforeToolSelection, Notification, PreCompress`** (no PreToolUse/PostToolUse naming — Gemini uses Before/AfterTool).
- Config surfaces: `settings.json` `"hooks"` key (`settings["hooks"]`, `hooksConfig.enabled`, `hooksConfig.disabled` per scope) AND extension-bundled hooks: `<extensionDir>/hooks/hooks.json` ("Invalid hooks configuration in ...: 'hooks' property must be an object"). Hook firing API: `hookSystem.fireSessionStartEvent(SessionStartSource.Clear)`, `fireSessionEndEvent(SessionEndReason.Clear)`, `fireBeforeModelEvent`, `fireAfterModelEvent`, `fireBeforeToolEvent(toolName, ...)`, `fireAfterToolEvent`, `fireBeforeToolSelectionEvent`, `firePreCompressEvent(trigger)`, `fireNotificationEvent(NotificationType.ToolPermission, message, details)`.
- Extensions: `gemini extensions` (install/enable/list); skills: `gemini skills`; Policy Engine (`--policy`, `--admin-policy`, policy-integrity hashing).

### B. Attention / permission signal — VERIFIED
- `Notification` hook event with `NotificationType.ToolPermission` — hooks are notified on permission requests (exact call site: `fireNotificationEvent(NotificationType.ToolPermission, message, serializedDetails)`).
- Approval modes `--approval-mode default|auto_edit|yolo|plan`; ACP mode (`--acp`) carries `session/request_permission`; policy engine allow/deny.

### C. Completion signal — PARTIAL
- No `Stop`/`TaskComplete` hook in the enum (closest: `AfterAgent`). Headless: `-o stream-json` emits a stats/summary event at end (`StreamJsonFormatter.convertToStreamStats`: total/input/output/cached tokens, `duration_ms`, `tool_calls`, per-model breakdown) — terminal marker LIKELY a final `result`/`stats` event (formatter VERIFIED; exact terminal event name UNKNOWN).
- Session lifecycle hooks `SessionEnd` exist.

### D. Machine-readable output — VERIFIED
- `-o/--output-format text|json|stream-json` (`OutputFormat2["STREAM_JSON"] = "stream-json"`); `stream-json-formatter.ts` present in bundle (JSONL to stdout, token `cache`d accounting); `--acp` / `--experimental-acp`; `gemini mcp`; `--session-file` (load session from JSON); `--raw-output` sanitization toggle. No `--json-fd` dual-output (Qwen-only).
- Telemetry: OTEL (`OTEL_EXPORTER_OTLP_HEADERS` etc.), `GEMINI_CLI_SESSION_ID`, `GEMINI_CLI_PROMPT_ID`, `GEMINI_CLI_SURFACE` metric labels.

### E. Structured state — VERIFIED (code) / dirs not present here
- Session history: `chatsDir` + session files `SESSION_FILE_PREFIX*-{shortId}.json|.jsonl` with `SHORT_ID_REGEX /-([a-zA-Z0-9]{8})\.jsonl?$/` — i.e. `~/.gemini/tmp/<project-hash>/...` per-project session store (Gemini convention; `~/.gemini/tmp` absent on this machine because Gemini CLI hasn't run here — only Antigravity's `~/.gemini/antigravity-cli` exists). Session ids: UUID + 8-char short id; `--session-id <UUID>`, `--resume latest|N`, `--list-sessions`, `--delete-session N`.

### F. Per-session config isolation via env — PARTIAL/VERIFIED bits
- **`GEMINI_CLI_SYSTEM_SETTINGS_PATH`** (verified read: `if (process.env["GEMINI_CLI_SYSTEM_SETTINGS_PATH"]) return ...`) — overrides the *system-level* settings file. API/model env: `GEMINI_API_KEY`, `GOOGLE_API_KEY`, `GOOGLE_GEMINI_BASE_URL`, `GOOGLE_CLOUD_PROJECT`, `GEMINI_SYSTEM_MD` (LIKELY — not searched in this pass), `GOOGLE_CLOUD_PROJECT` etc. A full home override is via `HOME`/`XDG` (the code uses `os.homedir()`; no `GEMINI_CLI_HOME`-style override found — UNKNOWN/PARTIAL).

---

## Comparison table

| Agent | Hook/Plugin? | Attention signal? | Completion signal? | Machine-readable output? | Session-correlatable id? | Config-dir env override? |
|---|---|---|---|---|---|---|
| **Kilo Code 7.0.27** | YES — plugins (`@opencode-ai/plugin`, `config.plugin`), `Plugin.trigger` (tool.execute.before/after, chat.message) + event bus (`session.idle`, `permission.asked`, `session.error`, `file.edited`...) | YES — `permission.asked` bus event (VERIFIED) | YES — `session.idle` bus event + `step_finish` JSON (VERIFIED) | YES — `run --format json` NDJSON w/ sessionID; ACP; HTTP serve/attach; `session list --format json` (VERIFIED) | YES — `sessionID` in every JSON event; SQLite `kilo.db`; ses_/msg_ prefixes (VERIFIED/LIKELY) | YES — `KILO_CONFIG_DIR`, `KILO_CONFIG`, `KILO_CONFIG_CONTENT` + XDG (VERIFIED) |
| **Cline 2.5.0** | YES — script hooks in `.clinerules/hooks/`: TaskStart, TaskResume, TaskCancel, **TaskComplete**, PreToolUse, PostToolUse, UserPromptSubmit, PreCompact (VERIFIED) | YES — `ask` mechanism (followup/tool/command) + ACP permission updates (VERIFIED) | YES — `attempt_completion`/`completion_result`, TaskComplete hook, `--json` `completion` event (VERIFIED) | YES — `--json`; `--acp` (VERIFIED) | YES — `~/.cline/data/sessions/<taskId>/` + hooks.jsonl `taskId`+`rootSessionId` (VERIFIED) | YES — `CLINE_DATA_DIR`, `CLINE_DIR`, `CLINE_LOG_DIR`, `--config` (VERIFIED) |
| **Hermes** | YES — 19 plugin hooks (pre/post_tool_call, pre/post_approval_request, on_session_*, subagent_*, pre_gateway_dispatch) + shell hooks in config.yaml `hooks:` + webhooks (VERIFIED) | YES — approval lifecycle hooks + interactive/gateway approval surfaces (VERIFIED) | YES — `bell_on_complete` (\a), `notify_on_complete` bg-process notification (injected as user input), `on_session_end` (VERIFIED) | PARTIAL — `-z` oneshot stdout, `acp`, `mcp` server, `send`, `sessions export`; no --output-format json (VERIFIED) | YES — `session_<ts>_<id>.json(l)` + sessions.json; `--resume/--continue/--pass-session-id` (VERIFIED) | YES — `HERMES_HOME` (VERIFIED) |
| **Antigravity (agy)** | YES — hooks.json named hooks: PreToolUse, PostToolUse, PreInvocation, PostInvocation, Stop (+ matcher/timeout, plugins bundle hooks) (VERIFIED, embedded docs) | YES — `STATE_WAITING_FOR_USER` executor state, permission prompts/grants, notifyUser RPC (VERIFIED; headless soft-denies) | YES — terminal `result` stream event, Stop hook, STATE_COMPLETED/FULLY_IDLE, `WaitForConversationFullyIdle` RPC, `<!-- GOAL_COMPLETE -->` (VERIFIED) | YES — `--output-format json/stream-json` (init/step_update/result), `--input-format stream-json`, `--json-schema`, MCP, remote-control daemon (VERIFIED) | YES — conversationId UUID: `conversations/<uuid>.db`, history.jsonl, `--conversation` (VERIFIED) | PARTIAL — no direct config-dir env var; XDG/HOME respected; `--log-file` (UNKNOWN for first-class override) |
| **Qwen Code 0.24.0** | YES — Claude-compatible hooks (PreToolUse, PostToolUse, SessionStart/End, Notification, UserPromptSubmit, Stop, SubagentStop, PreCompress) in settings.json + `/hooks` (VERIFIED) | YES — `ask_user_question` re-hang flag, Notification hook, approval modes, ACP (VERIFIED) | YES — stream-json result + `--json-schema` session end; Stop hook name VERIFIED | YES — `--output-format json/stream-json`, `--json-fd/--json-file` dual output, `--input-file` JSONL control, `--acp`, `serve` HTTP daemon (VERIFIED) | YES — `chats/<uuid>.jsonl` (VERIFIED pattern) | YES — `QWEN_HOME`, `QWEN_DIR`, `QWEN_RUNTIME_DIR` (VERIFIED) |
| **Gemini CLI 0.60.0** | YES — hooks: SessionStart/End, Before/AfterAgent, Before/AfterModel, Before/AfterTool, BeforeToolSelection, Notification, PreCompress; settings.json + extension hooks.json; Claude migrator (VERIFIED) | YES — `Notification` hook fires w/ `NotificationType.ToolPermission`; ACP permission requests (VERIFIED) | PARTIAL — no Stop hook; stream-json stats summary at end; SessionEnd hook (formatter VERIFIED, terminal event name UNKNOWN) | YES — `-o json/stream-json`, `--acp`, `--session-file`, MCP, OTEL telemetry (VERIFIED) | YES — `session-<uuid>-<shortid>.json(l)` per project; `--session-id`, `--resume latest|N` (VERIFIED) | PARTIAL — `GEMINI_CLI_SYSTEM_SETTINGS_PATH` VERIFIED; no full-home env var found (UNKNOWN) |

---

## Key cross-cutting observations

1. **Best external-monitoring surface**: Qwen (`--json-fd/--json-file` dual output + `--input-file` control) and Kilo (`--format json` NDJSON with sessionID, plus HTTP server) — both let a watcher correlate events to a session without scraping the TUI. agy's stream-json (`init`/`step_update`/`result`) is close behind.
2. **Strongest hook parity with Claude Code**: Qwen (same event names/semantics) and Cline (`.clinerules/hooks/<EventName>` scripts) and agy (hooks.json, same `matcher`+`hooks[{type:command}]` shape — its `gemini hooks migrate`-style compat is explicit in Gemini's `hooks migrate` command).
3. **Waiting-for-user as a *state*** is only first-class in agy (`STATE_WAITING_FOR_USER`, pollable via language-server RPC `WaitForConversationFullyIdle`). Elsewhere it must be derived from permission events (`permission.asked` in Kilo, `Notification` hook in Gemini/Qwen, `ask` messages in Cline, approval hooks in Hermes).
4. **Completion**: only Cline and Hermes expose an explicit "task done" artifact (TaskComplete hook / attempt_completion / bell). Kilo's `session.idle`, agy's `result`/`Stop` hook, Qwen/Gemini's stream-json terminal event are the practical equivalents.
5. **Config isolation**: `HERMES_HOME`, `QWEN_HOME`, `KILO_CONFIG_DIR`(+`KILO_CONFIG_CONTENT`) and Cline's `CLINE_DATA_DIR` are first-class; agy and Gemini require `HOME`/XDG redirection tricks.
