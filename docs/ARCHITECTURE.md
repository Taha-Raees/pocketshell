# PocketShell — Architecture (M0)

Status: **M0 complete** · Companion docs: `RESEARCH.md`, `THIRD_PARTY.md`, `ROADMAP.md`, `TESTING.md`

---

## 1. System overview

```
┌──────────────────────────────────────────────────────────────────┐
│                        PocketShell (app)                         │
│                                                                  │
│  ┌──────────────────────── UI (Compose, Material 3) ──────────┐  │
│  │  HomeScreen      TerminalScreen      Apps  Settings  Diag  │  │
│  │  (honest state)  (tabs + view + kb)  (M2)   (M1.3)  (M1.3) │  │
│  └──────────┬───────────────────┬─────────────────────────────┘  │
│             │                   │                                │
│  ┌──────────▼───────────┐  ┌────▼─────────────────────────────┐  │
│  │  cliapps/            │  │  terminal/ (app layer)           │  │
│  │  CliApp model        │  │  TerminalSessionManager (owner)  │  │
│  │  CliAppRegistry      │  │  PocketShellSessionClient        │  │
│  │  CliAppLauncher      │  │  PocketShellTerminalViewClient   │  │
│  │  (verify→launch)     │  │  KeyboardState + TerminalKeyboard│  │
│  └──────────┬───────────┘  └────┬─────────────────────────────┘  │
└─────────────┼───────────────────┼────────────────────────────────┘
              │                   │  GPLv3 vendored modules (upstream)
              │          ┌────────▼─────────────────────────────┐
              │          │ :terminal-view  (com.termux.view)    │
              │          │  TerminalView, TerminalRenderer,     │
              │          │  selection, gestures                 │
              │          ├──────────────────────────────────────┤
              │          │ :terminal-emulator (com.termux.…)    │
              │          │  TerminalSession → TerminalEmulator  │
              │          │  → TerminalBuffer (+ KeyHandler)     │
              │          │  JNI: libtermux (termux.c)           │
              │          └────────┬─────────────────────────────┘
              │                   │ fork/exec
              ▼                   ▼
   [M2: real userspace]     /system/bin/sh  ← real PTY, real shell
```

**Boundaries.** The app module never touches `TerminalEmulator`/`TerminalBuffer`
internals. It owns sessions through `TerminalSession`, receives events through
the two upstream client interfaces, and feeds input through the
`TerminalView` dispatch path. This keeps upstream code upgradeable and the
terminal "real" by construction.

---

## 2. Gradle modules

| Module | Origin | Contents |
|---|---|---|
| `:app` | PocketShell | All Kotlin/Compose product code |
| `:terminal-emulator` | **Vendored GPLv3** (pinned upstream commit, see THIRD_PARTY.md) | Emulator core + PTY JNI + 19 upstream test classes |
| `:terminal-view` | **Vendored GPLv3** (same pinned commit) | `TerminalView`, renderer, text selection, gesture recognizer |

Vendored packages keep upstream names (`com.termux.terminal`, `com.termux.view`)
so future re-syncs against upstream remain mechanical diffs.

## 3. App package structure (inside `:app`)

```
app.pocketshell
├── MainActivity.kt                 # single activity, Compose host
├── ui/
│   ├── theme/                      # M3 themes: light/dark/AMOLED/dynamic
│   ├── home/HomeScreen.kt          # Terminal card, Installed CLI Apps, Explore, Sessions
│   ├── terminal/TerminalScreen.kt  # session tabs + TerminalView + PocketShellKeyboard
│   ├── apps/ExploreAppsScreen.kt   # honest M2 placeholder (no fake installs)
│   ├── settings/SettingsScreen.kt  # M1.3
│   └── diagnostics/DiagnosticsScreen.kt # M1.3 read-only facts
├── terminal/
│   ├── TerminalSessionManager.kt   # process-scoped owner of all sessions
│   ├── PocketShellSessionClient.kt # implements TerminalSessionClient
│   ├── PocketShellTerminalViewClient.kt # implements TerminalViewClient
│   └── ShellEnvironment.kt         # env/HOME/PATH/TMPDIR/TERM construction
├── keyboard/
│   ├── TerminalKeyboard.kt         # Compose keyboard (phone/tablet adaptive)
│   ├── KeyDefinitions.kt           # key model: chars, symbols, special keys, layers
│   └── KeyboardState.kt            # one-shot/locked modifiers (single source of truth)
├── cliapps/
│   ├── CliApp.kt                   # data-driven model (§16 of brief)
│   ├── CliAppRegistry.kt           # DataStore-backed, empty on fresh install
│   └── CliAppLauncher.kt           # verify installed → verify executable → launch session
├── settings/SettingsRepository.kt  # DataStore (theme, font size, haptics)
└── diagnostics/Diagnostics.kt      # build/runtime facts for DiagnosticsScreen
```

Not created (deliberately, anti-overengineering per §26): no IME service, no
content providers, no background sync, no network layer, no DI framework
(manual singletons suffice at this size), no package installer (M2).

---

## 4. Input flow (single pipeline, §9 of brief)

```
PocketShell keyboard deck (Compose, custom from scratch — no system IME)
  │  letter/symbol keys  → KeyCharacterMap.VIRTUAL_KEYBOARD.getEvents()
  │  special keys        → synthetic KeyEvent(KEYCODE_*)
  │  long-press actions  → dispatched as ordinary actions (digits → F1–F10)
  │  modifier taps       → KeyboardState (one-shot / LOCKED / off, observable)
  ▼
TerminalView.dispatchKeyEvent()          [vendored, unmodified]
  │  uses KeyHandler.getCode(code, keyMode, cursorApp, keypadApplication)
  │  keyMode ← TerminalViewClient.readCtrl/Alt/Shift/FnKey()
  │             └─ delegates to KeyboardState ─── the same state the UI shows
  ▼
TerminalSession.write(bytes)
  ▼
PTY master fd (libtermux JNI)
  ▼
shell / interactive program (vim, top, tmux, …)
```

Output path:

```
program → PTY → reader thread → TerminalEmulator(TerminalBuffer)
        → onTextChanged → Compose tab/title state + TerminalView.invalidate()
```

Why synthetic KeyEvents instead of writing bytes directly: it reuses upstream
handling of ctrl-combos, application cursor mode, numpad/alternate esc sequences
and IME edge cases. Writing bytes straight to the session would fork input
semantics into two systems — exactly what §9 forbids. (Direct `session.write()`
is reserved for clipboard paste and explicit text injection.)

Modifier semantics (§10):
- **Tap** → one-shot: consumed by next key event, then auto-cleared.
- **Second tap** → LOCKED: persists; visually distinct (accent fill + lock dot).
- **Tap while locked** → unlocked.
- **CTRL, ALT, SHIFT** all support both states and freely combine
  (e.g. CTRL then SHIFT then C). State is always visible on the key itself
  (fill + outline + dot + text — never color alone).

Deck layout (Phase 3.1 — docs/PHASE-3.1-DESIGN.md §5), top to bottom:

```
Esc  Tab                   ←  ↑  ↓  →     top accessory row (always visible)
1..0  q..p  a..l  ?123 z..m ⌫  - / : ; , . $ ' " @     QWERTY body (collapsible)
[⌨]  Ctrl  Alt  Space  Shift  Enter       bottom accessory row (always visible)
```

- **No dedicated FN key and no FN modifier** (Phase 3.1, brief §18): F1–F10 are
  the long-press actions of the number-row keys (hold ≥350ms → "F#" bubble →
  release commits); F11/F12 ride on the tablet rows' -/= long-press.
  `readFnKey()` honestly returns `false` (upstream interface method retained).
- HOME/END/PGUP/PGDN/INS/DEL live on the SYMBOL page.
- The `⌨` toggle collapses only the QWERTY body; both accessory rows and the
  toggle's far-left position are permanent. No system IME is used anywhere —
  PocketShell's keyboard is the only typing surface (hardware keyboards keep
  flowing through the same dispatch pipeline).

---

## 5. Session lifecycle model (§14, §24)

- `TerminalSessionManager` is a **process-scoped singleton**. It owns every
  `TerminalSession` (PTY, pid, environment, cwd, emulator state, scrollback).
- UI (`ViewModel` + Compose state) holds only: selected tab index, UI flags.
  Rotation, dark-mode flips, activity recreation therefore **cannot** destroy
  sessions — the view simply re-attaches to the manager's session list.
- Each session: created with its own env + cwd + title; killed via its own
  `finishIfRunning`; finished sessions are removed from the tab strip only
  after the UI observes `onSessionFinished` (user sees the exit, then closes).
- M1.2 adds a `specialUse` foreground service whose only job is process
  retention while sessions run in background; it shows a persistent, honest
  notification and stops itself when the last session exits.
- Process death (LMK) kills sessions — this is inherent to Android process
  boundaries (Termux identical). Declared in docs; no fake session restore (§34).

Session isolation rules (no state leakage, §14): env map and cwd are captured
per session at creation; `TerminalSession` instances never share buffers; the
keyboard `KeyboardState` is per-terminal-screen but modifiers are cleared on
session switch (no sticky modifiers crossing tabs).

---

## 6. CLI app architecture (§16–§18, data-driven)

```kotlin
data class CliApp(
  val id: String,            // stable slug ("hermes")
  val name: String,          // "Hermes"
  val description: String,
  val executable: String,    // "hermes" (resolved via PATH) or absolute
  val arguments: List<String>,
  val packageName: String?,  // M2 package id
  val version: String?,      // reported by real install only
  val category: String,
  val workingDirectory: String?,   // default: HOME
  val environment: Map<String,String>,
  val launchConfiguration: LaunchConfig, // window size hints, title pattern
)
```

- `CliAppRegistry` (DataStore JSON) starts **empty**; Home shows "No apps
  installed yet" until a real install (M2) adds an entry. Nothing auto-populates
  the registry from `PATH` (§2/§35).
- `CliAppLauncher.launch(app)` performs, in order: registry lookup →
  executable existence check (`command -v` inside the session's shell
  environment, no shell spoofing) → env assembly → new session via
  `TerminalSessionManager` → exec. Failure at any step surfaces an honest error
  state; nothing pretends to run (§34).
- No `Hermes` hard-coding anywhere; Hermes is just one future registry entry.

### Phase 3.2 update (2026-09-04): command-launchable apps (the shipped shape)

The M0 sketch above was superseded by M2.4 (real apk catalog) and is now
superseded again by the Phase 3.2 package/app separation
(docs/PHASE-3.2-DESIGN.md §4). The shipped architecture:

- `apps/CommandApps.kt` — `CommandApp(id, displayName, launchCommand,
  description, monogram)` + `CommandAppCatalog.registry` (seeds: hermes,
  opencode, claude, zcode) + `availableCommandApps(paths)` classification
  (pure; registry order; only guest-confirmed names). Packages are
  infrastructure and NEVER launcher apps — the forbidden list
  (git/nano/python/node/npm/gcc/g++/htop/vim) is test-pinned.
- Availability: `AlpinePackageManager.guestCommandPaths` — ONE batched
  LOGIN-shell probe (`sh -lc`, positional args, terminal `exit 0`,
  `PackageProbeException` on real failure). Login semantics are deliberate:
  the question is "would a fresh guest login shell find this command?" —
  uv-installed launchers (~/.local/bin, e.g. Hermes from M2.6) are on PATH
  only after profile sourcing; the spec's static PATH would answer a false
  absence. Exposed via `PackageGateway.commandPaths/commandPath`.
- Launch: `TerminalViewModel.openCommandApp` (runtime gate → fresh probe →
  `TerminalSessionManager.createLinuxCommandSession` = new dedicated guest
  login-shell session whose PTY receives the launch command → select →
  navigate). `createLinuxAppSession` (M2.4 Explore path) delegates to the
  same method — one launch machinery, two entry points.
- Home state: `TerminalViewModel.CommandAppsState` (apps / probeError /
  checked) refreshed on Home visibility (READY) and after package
  operations; probe failure keeps the last real list and surfaces the error
  (v0.4.4 honesty rule applied to command apps).

### Phase 3.3 update (2026-09-04): presentation-only restructure

The Phase 3.2 architecture above is unchanged — Phase 3.3 changed only how
Home PRESENTS it (docs/PHASE-3.3-DESIGN.md):

- The same `CommandAppsState` now drives TWO launcher presentations: the
  "Your tools" icon+label grid and the header `CLI Apps ▾` menu (rendered
  only when `apps` is non-empty). Both funnel into the same
  `openCommandApp` pipeline; no new discovery/launch code exists.
- The floating control no longer carries app launches: `QuickAction` is
  `id/label/enabled/onRun` and only ever contains session-creation actions.
- Surface rule for the UI layer: surfaces only for real objects
  (environments / menus / floating control / actionable banner / pressed
  states); everything else separates via spacing, section labels, hairline
  dividers and Midnight tone steps.

### Phase 3.4 update (2026-09-04): registry expansion + system pages

No architecture change — two applications of the existing rules
(docs/PHASE-3.4-DESIGN.md):

- The registry is the designed extension point of the command-app
  architecture: five terminal AI agents seeded (`kilo` → Kilo Code, `gemini`,
  `codex`, `aider`, `qwen`), appended after the brief's four so existing
  launcher order never shuffles. Classification/probing code untouched — an
  entry still only surfaces when the guest's login shell finds its command.
  Root-caused the "installed Kilo CLI doesn't show up" report to registry
  absence (the probe only asks registry names — the honesty contract
  deliberately never guesses from unknown PATH binaries).
- System pages adopted the Phase 3.3 §9/§10 rules concretely: the Packages
  not-ready state is inline text with a real Diagnostics affordance (no
  container; screen titled "Packages"), Settings selection rows are
  whole-row targets (≥48dp, Role.RadioButton/Switch on the row), and
  Diagnostics uses one uniform section pattern (divider + header + plain
  fact rows) across System / Linux runtime / Package environment.

---

## 7. Rendering & performance notes (§23)

- Rendering stays entirely in vendored `TerminalRenderer` (bitmap-row blitting
  into a `Canvas`, invalidation-driven) — no Compose recomposition per terminal
  output frame; `TerminalView` sits inside `AndroidView` in the Compose tree.
- Compose recomposition is driven by low-frequency state only: session list,
  selected tab, title changes (throttled upstream), keyboard modifier flags.
- Font size changes resize the PTY (`TIOCSWINSZ` via upstream `updateSize`)
  rather than scaling pixels.

## 8. Security posture (§22)

- Permissions in M1: **none** (not even internet).
- Terminal I/O never leaves the process; no telemetry, no crash reporting SDK.
- No scripts executed automatically; launcher only runs user- or registry-chosen
  executables after verification.
- GPLv3 vendored code carries upstream license headers unchanged.
