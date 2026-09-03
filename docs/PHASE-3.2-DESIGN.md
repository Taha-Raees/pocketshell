# Phase 3.2 — PocketShell Home / OS Launcher

Status: DESIGN CONTRACT (committed before implementation, plan-first discipline)
Scope: the HOME screen ONLY + the command-launchable app architecture. The Phase 3.1
terminal redesign (chrome, tabs, keyboard, canvas, palette, PTY pipeline), the runtime,
the Linux environment, the package manager and installed packages, Hermes' installation,
user data, and every other screen's behavior are explicitly OUT of scope.

## 0. Concept

PocketShell is NOT a terminal app with a home page. Android is the host; PocketShell is
a Linux-centric environment running inside it. Home is the launcher / workspace of that
environment — the place Terminal, Linux, and command apps grow from.

Composition (an OS home screen, NOT a dashboard of stacked cards):

    TOP        PocketShell identity · minimal system actions (info, settings)
    CENTER     the two foundations: Terminal and Linux (prominent launch surfaces)
    BELOW      Command apps — launcher-style grid of installed interactive tools
    QUIET      compact running-session continuation area (only when sessions exist)
    FLOATING   one custom quick-action control (no permanent bottom navigation)

Hard rules from the brief, restated as engineering constraints:
- Packages are infrastructure; apps are experiences. git/nano/python/node/gcc/g++/htop/npm
  are NEVER launcher apps (test-pinned).
- A command app appears only when the guest actually answers `command -v` (login-shell
  semantics — see §4). Probe failure is rendered as "could not check", never as "none".
- No fake future features: Files/SSH/Containers/AI/marketplace get NO UI yet; the FAB
  action list and the app registry are the extension points.

## 1. Identity, surfaces, tokens

Midnight Sapphire continues (docs/PHASE-3.1-DESIGN.md §1). Home renders the SAME fixed
blue-dark identity in every app theme (same decision as the Terminal screen): it must
belong directly beside the redesigned terminal. The M2.6 app theme keeps serving
Explore/Packages/Settings/Diagnostics untouched.

Home consumes the `TerminalTheme` token object (it is the Midnight Sapphire system) and
adds a tiny home-specific set — `ui/home/HomeTokens.kt`:

| Token          | Value     | Role                                                        |
|----------------|-----------|-------------------------------------------------------------|
| (screenBg)     | `#0B1424` | page background (TerminalTheme.screenBg)                     |
| surfaceHero    | `#080F1D` | Terminal tile = the exact canvas color (visually "is" the terminal) |
| surfaceEnv     | `#101B30` | Linux tile (chrome tone)                                     |
| surfaceApp     | `#16233F` | command-app icon tiles (keyAlt tone)                         |
| surfaceRaised  | `#131F38` | FAB cluster chips (deck tone)                                |
| hairline       | `#1D2C4A` | 1dp borders (TerminalTheme.divider)                          |
| textPrimary    | `#DCE6F8` | titles, labels                                               |
| textDim        | `#7C8DB0` | supporting text (≥4.5:1 on screenBg)                         |
| accent         | `#7FA3EF` | THE one accent: FAB, running dots' environment cue, focus    |
| accentBright   | `#A5C0FF` | monogram glyphs, pressed accent text                         |
| runningGreen   | `#5FB572` | ONLY on the session dot that means "process really running" (same value as the terminal's ANSI green) |
| danger         | `#E37993` | launch-error banner accent                                   |

- NO pure black anywhere; the darkest surface is surfaceHero `#080F1D`.
- NO gradients on Home (depth comes from surface steps, hairlines, restrained shadows).
  The page's identity is open + airy against the Terminal's deep + focused.
- Radii: hero tiles 20dp, app tiles 16dp icon square, chips 14dp (full pill for FAB cluster).

## 2. Typography

- Wordmark "PocketShell": JetBrains Mono NL Medium (TerminalTheme.mono) — the
  environment's brand is set in the terminal's own type; recognizable, ownable.
- Tagline: Roboto Regular, textDim, 14sp.
- Tile titles: Roboto Medium 16–18sp; descriptions 12–13sp textDim.
- Monogram glyphs / session ids / command names: JetBrains Mono NL.
- No new font files (payload unchanged beyond code).

## 3. Header / branding

Integrated into the launcher — NOT an app bar, no toolbar surface:
- Top-left: the drawn brand mark (pocket-shaped rounded square with a prompt chevron,
  Sapphire on keyAlt) + "PocketShell" wordmark + tagline "Your Linux workspace on
  Android." below.
- Top-right: two 40dp icon buttons — Info (→ Diagnostics), Settings. Nothing else.
- The header scrolls with the content (it is part of the launcher, not pinned chrome).

## 4. Command-launchable app architecture (the technical heart)

### 4.1 Model + registry — `app/pocketshell/apps/CommandApps.kt`

    data class CommandApp(id, displayName, launchCommand: List<String>,
                          description, monogram: Char)

`CommandAppCatalog.registry`: the seed list from the brief — hermes ("Hermes Agent",
`hermes`), opencode ("OpenCode", `opencode`), claude ("Claude Code", `claude`),
zcode ("ZCode", `zcode`). Plain single-token launch commands (argv-safe, test-pinned).
Descriptions are static registry metadata (honest, human-written); availability is NEVER
metadata — only the guest decides.

Classification rules (documented in-code, test-pinned):
- The registry contains ONLY meaningful interactive applications launched by a dedicated
  command. Forbidden forever: git, nano, python/python3, node, npm, gcc, g++, htop, vim,
  and every normal shell utility — packages are infrastructure.
- Extensible by adding one `CommandApp(...)`; no UI redesign needed for future apps.
- `availableCommandApps(paths: Map<String, String>): List<CommandApp>` — pure function:
  registry order, only names the guest confirmed.

### 4.2 Availability probe — login-shell semantics

Fact (verified in inspection): the guest spec PATH is
`/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin` — it does NOT include
`/root/.local/bin`, where uv installs launchers (M2.6 Hermes). Interactive sessions run
`/bin/sh -l`, a LOGIN shell that sources /etc/profile + ~/.profile — which is exactly why
`hermes` is "on PATH" on the device. Therefore:

- New method on `AlpinePackageManager` (concrete class only — the `PackageManager`
  interface and every existing method stay untouched):
      guestCommandPaths(names: List<String>): Map<String, String>
  ONE guest exec: `/bin/sh -lc 'for n in "$@"; do p=$(command -v "$n" 2>/dev/null) &&
  echo "$n $p"; done; exit 0' sh <names…>` — login shell (matches what the user's own
  typing sees), positional args (no string-building), terminal `exit 0` (the v0.4.4
  lesson: a mixed answer is still a completed probe). A real exec failure throws
  `PackageProbeException`.
- New `PackageGateway.commandPaths(names)` + `commandPath(name)` wrappers (same shape as
  installedVersions/installedVersion). No existing gateway method changes.

### 4.3 Launch flow — verify-then-launch, typed-command fidelity

Tap "Hermes Agent" → `TerminalViewModel.openCommandApp(app, onReady)`:
1. Refuse honestly unless runtime READY (message routes the user to Diagnostics).
2. Fresh single `commandPath` probe (the same login-shell probe — verify-then-launch).
3. `TerminalSessionManager.createLinuxCommandSession(context, label, launchCommand)` —
   NEW method, identical machinery to the existing CLI-app session: a NEW dedicated guest
   login-shell session; the command is written into THAT session's PTY (visible in its
   own scrollback; exiting the app returns to the guest prompt). The existing
   `createLinuxAppSession` delegates to it — byte-identical behavior for Explore/Open.
4. Select the session; `onReady()` navigates to the Terminal screen.
Every refusal lands in the existing non-fatal `launchError` banner. The user never types
`hermes` by hand if they tapped Hermes — and everything the launcher does is exactly
what typing would have done.

### 4.4 ViewModel state

    data class CommandAppsState(apps: List<CommandApp>, probeError: String?, checked: Boolean)
    TerminalViewModel.commandApps: StateFlow<CommandAppsState>
    TerminalViewModel.refreshCommandApps()   // one batched probe, on Home visibility
                                             // (READY) + after package ops land
    TerminalViewModel.openCommandApp(app, onReady)

v0.4.4 discipline: on probe failure the LAST REAL app list is kept on screen and the
error is surfaced next to it — a dead probe never renders as "no apps". The old
`installedCatalogApps` flow keeps existing (package-layer API, tests pin it); Home simply
no longer consumes it — the "Installed CLI Apps" section is REMOVED from Home.

## 5. Environment launchers (the two foundations)

Two prominent surfaces, asymmetric but balanced (Row, weights 1.25f / 1f, equal height):

- TERMINAL (surfaceHero `#080F1D` — the exact canvas color, Sapphire 1dp hairline):
  large drawn prompt mark (chevron + block cursor), title "Terminal", description
  "Native PocketShell environment", and a live mono chip "N running" when sessions
  exist. Tap → openTerminal (reuse newest live session, else spawn — existing rule).
- LINUX (surfaceEnv `#101B30`): drawn twin-peak mountain mark (a quiet nod to Alpine —
  original art, no third-party logo), title "Linux", honest state line:
  READY → "Alpine Linux · ready" (+ "Enter the guest shell"); every other RuntimeState
  keeps the exact existing honest subtitles and routes to Diagnostics. The architecture
  is distro-agnostic: the state line is derived from runtime metadata, never hardcoded
  UI around Alpine alone (one string lives in one place; multiple distros extend it).
- Both: 48dp+ touch height, ≥148dp tall, press = 0.98 scale 80ms, no elevation glow.

## 6. Command app launcher grid

- Section label "Command apps" (small caps dim) — only when apps exist OR probe failed.
- Launcher-style tiles, NOT full-width cards: 64dp icon square (surfaceApp, 16dp radius,
  hairline) + monogram glyph (mono, accentBright) + name below (12sp, 1 line).
- Responsive columns by window width: <600dp → 3, 600–839dp → 4, ≥840dp → 6;
  content max-width 720dp centered on tablets; rows are simple chunked Rows inside the
  page scroll (no nested-scroll grid machinery for a realistically small collection).
- Tile tap → §4.3 launch flow. No long-press menus yet (nothing honest to put in them).

### Empty state (no apps available, probe succeeded, checked)

A drawn ghost-tile illustration (three dim outlined squares with a faint plus), title
"Your tools will appear here", body "Interactive command apps installed in your Linux
environment — like Hermes Agent — launch directly from this home screen.", and one
elegant quiet action "Explore packages" → the existing package screen (real feature,
no fake marketplace). If the runtime is not installed yet, the body says so instead.

### Probe-failure state

The quiet honest line: "App availability could not be checked right now — <reason>."
plus the last real app grid if one existed. Never a fake "none installed".

## 7. Running sessions — compact continuation area

- Only when sessions exist; capped at 4 rows + a quiet "+N more in Terminal" line.
- Row (48dp): 8dp status dot — runningGreen ONLY for a live process; outline for
  "(exited)" — then the session's real display label (OSC title when the program sets
  one), trailing mono "#id". Tap → select + navigate (existing behavior).
- No environment column invention: the label already carries it ("Alpine Linux",
  app names, "Terminal N"). No giant repeated cards.

## 8. Floating quick-action system

- ONE main control, bottom-right above the gesture bar: 56dp circle, accent fill,
  dark "+" glyph, restrained shadow. Custom-built (Surface + shadow + rotation) —
  not a Material FAB. contentDescription "Quick actions".
- Tap: the + rotates to × (180ms), a scrim (40% black, 150ms fade) focuses the page,
  and an upward stack of labeled chips emerges from the control (fade + 8dp slide,
  25ms stagger, gentle spring — no bounce): each chip = 44dp icon circle (surfaceRaised,
  hairline) + label pill.
- Real actions only, top-down: "New Terminal" (fresh session — existing spawn flow),
  "New Linux session" (only when READY — otherwise the Linux tile already guides to
  Diagnostics; no action that pretends), then each available command app (monogram,
  launch flow §4.3, capped at 4). No fake future entries — the list is data
  (`List<QuickAction>`), so Files/SSH/etc. will simply be new entries later.
- Dismiss: scrim tap, × tap, or system Back (BackHandler) — no navigation change.
- FAB hides while the cluster is open? No — it BECOMES the close control (position
  never moves, satisfying the Phase 3.1 stability lesson).

## 9. Motion

- FAB cluster: described in §8 — all motion 150–220ms, FastOutSlowIn / gentle spring,
  nothing looping, nothing bouncy.
- Tile press: 80ms scale (0.97–0.98). Session dot: static. Page: no entrance animations
  on recomposition (launcher must feel instant).
- Screen transitions stay the app's existing (none) — no navigation animation system is
  introduced in this phase.

## 10. Status bar & edge-to-edge coordination

Home goes edge-to-edge like the Terminal (MainActivity passes the raw modifier to the
home branch; the screen pads status bar itself and the FAB pads the gesture bar).
The window's status-bar icon appearance is coordinated per screen in `PocketShellRoot`:
home/terminal → dark-background (light icons); other screens → follow the app theme as
before. One `DisposableEffect`, no new permission, no style regressions on light screens.

## 11. Accessibility

- Every icon-only control carries a contentDescription (Info, Settings, FAB, cluster
  chips, close). State is never color-only: exited sessions say "(exited)"; the running
  dot is decorative with the label carrying the meaning.
- Touch targets: tiles ≥48dp, session rows 48dp, header icon buttons 40dp (existing
  app-wide minimum), cluster chips 44dp.
- Contrast: textPrimary on screenBg > 12:1; textDim ≥ 4.5:1; monograms on surfaceApp
  ≥ 4.5:1.
- Semantic order: branding → environments → apps → sessions (logical TalkBack sweep).

## 12. Performance

No blur, no continuous animation, no gradient shaders. Home recomposes on session list
changes only (small lists, stable keys). The availability probe runs ONE exec per
refresh — never a loop of proot startups. Monogram marks are drawn vector paths/Canvas —
zero bitmap allocations.

## 13. Honest-state contract (summary)

| Situation | Rendered truth |
|-----------|----------------|
| No command apps available (probe OK) | "Your tools will appear here" + Explore packages |
| Probe failed | "could not be checked right now" + last real grid |
| Runtime not READY | Linux tile guides to Diagnostics; apps area explains the runtime need |
| Launch refused | existing launchError banner (verbatim cause, non-fatal) |
| Session exited | dim dot + "(exited)" suffix |
| Packages installed | NEVER on Home — Explore/Packages screen remains their only surface |

## 14. Checkpoints (each ends in compile + focused tests)

Build order (data before pixels; checkpoint numbers = the brief's deliverables):

| order | step | content |
|-------|------|---------|
| 1 | 3.2.3 | CommandApps model/registry/classification + tests; AlpinePackageManager.guestCommandPaths + Gateway wrappers; createLinuxCommandSession (createLinuxAppSession delegates); TerminalViewModel commandApps state + openCommandApp + refresh wiring |
| 2 | 3.2.1 | HomeScreen rewrite: Midnight Sapphire launcher layout, brand header, edge-to-edge + status-bar coordination, launch-error banner restyle |
| 3 | 3.2.2 | Terminal + Linux environment launchers (drawn marks, honest states, running chip) |
| 4 | 3.2.4 | Responsive command-app grid + empty/probe-failure states |
| 5 | 3.2.5 | Compact sessions continuation area |
| 6 | 3.2.6 | Custom floating quick-action system (+ Back handling) |
| 7 | 3.2.7 | Motion, a11y sweep, tablet/foldable polish, versionCode 19 / 0.7.0-m3.2 |

## 15. Validation gate (before "complete" may be claimed)

Full suite green (628 existing executions must stay green) + new CommandApps tests;
assembleDebug; aapt2 (versionCode 19 / 0.7.0-m3.2) + apksigner cert continuity
(d96a6f66…8bf659). Device gate (docs/TESTING.md §13) for the human: nano/git/python
NEVER on Home; Hermes appears when available and launches by tap; Terminal/Linux work
exactly as before; sessions continue; FAB actions; phone + tablet layout; Phase 3.1
keyboard/tabs untouched.

Version: **0.7.0-m3.2, versionCode 19** (in-place update chain: 16 → 17 → 18 → 19,
cert d96a6f66…8bf659 unchanged).
