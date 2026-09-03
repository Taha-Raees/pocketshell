# Phase 3.3 — Home & System UI Redesign (design contract)

Status: CONTRACT — committed before implementation.
Scope: visual architecture + Home interaction cleanup. **No backend changes.**
Version target: `0.7.0-m3.3`, versionCode 20.

Phase 3.2 fixed the command-app architecture; visually the Home screen is still
a stack of rounded rectangles. This phase re-thinks the LAYOUT STRUCTURE so
PocketShell reads as a mobile Linux workspace — an OS home screen — not another
Material dashboard. Every visible element must justify why it exists; anything
without a clear functional purpose is removed.

Frozen (must not regress):

- Phase 3.1 — terminal rendering, keyboard, Ctrl/Alt/Shift, Fn long-press → F
  keys, keyboard toggle, PTY dispatch, Midnight Sapphire terminal chrome.
- Phase 3.2 — command-app discovery (registry + login-shell probing +
  `~/.local/bin`), verify-before-launch, dedicated guest command sessions, the
  forbidden package-app list, `CommandAppsTest` invariants, LaunchErrorBanner
  behavior contract.

---

## 1. Current Home problems (audit, with evidence)

Observed on v0.7.0-m3.2 (device screenshots + code):

| # | Problem | Evidence |
|---|---------|----------|
| 1 | Giant empty-state card dominates the screen (~300dp of mostly-empty rounded box with fake ghost tiles and an embedded button) | `HomeScreen.kt` `EmptyAppsState` (surfaceBanner + hairline + 30dp padding + `GhostTiles`) |
| 2 | "Explore packages" appears TWICE — inside the empty-state card AND again as a footer link directly below it | `HomeScreen.kt` `EmptyAppsState` + `PackagesFooterLink` |
| 3 | Cards inside cards: bordered pill button inside the empty card; bordered mono chip on the Terminal tile; section-label cards for probe states | `EmptyAppsState`, `RunningChip`, `CommandAppsArea` probe branches |
| 4 | Session rows are bordered boxes — a plain list is drawn as card stack | `SessionsContinuationArea` (surfaceBanner + border per row) |
| 5 | Terminal/Linux tiles read as generic dashboard widgets: 20dp radius, strong accent border on Terminal, hairline box on Linux, descriptor text truncates ("Native PocketShell envir…", "Enter the guest s…") | `TerminalTile` / `LinuxTile` |
| 6 | FAB menu duplicates what already exists on the page: command-app chips duplicate the tools grid; chip icon circles repeat the logo/marks as decoration | `quickActions` builder in `HomeScreen.kt`, `QuickActionChip` icon circle |
| 7 | Fake placeholder icons (ghost tiles) that mean nothing | `HomeMarks.kt` `GhostTiles` |
| 8 | Result: the page is a pile of rounded boxes instead of a workspace | screenshots — every section is a bordered container |

Root cause of the *look*: surfaces were used for GROUPING (text containers)
instead of for OBJECTS. The fix is structural, not cosmetic.

## 2. New Home hierarchy

Top-to-bottom, one scroll, dividers between major regions — no page-level
containers:

```
┌ PocketShell                                    ⓘ   ⚙ ┐   identity row
└ Your Linux workspace on Android                      ┘

  ┌────────────────────┐  ┌──────────────┐
  │ >_                 │  │ ⛰            │      the two foundations
  │ Terminal           │  │ Linux        │      (borderless tone-step
  │ Native shell       │  │ Alpine · ready│      surfaces, §8)
  └────────────────────┘  └──────────────┘

  CLI Apps ▾                                   only when apps exist (§5)
  ────────────────────────────────────────────  hairline divider
  YOUR TOOLS                                   section label (§6)
  ◉ Hermes Agent     ◉ OpenCode    ◉ Claude    icon + label grid
     — or the lightweight empty state (§9) —
  ────────────────────────────────────────────  hairline divider
  SESSIONS                                     section label
  ● Alpine Linux  Terminal 1            #1     flat rows, dividers (§2a)
  ○ Hermes  (exited)                    #2

  Packages                                     single quiet footer link,
                                               only when tools exist (§9)
```

Order is fixed: identity → foundations → command apps → sessions. The FAB
floats; it is not a layout region (§7).

**2a. Session rows.** Full-width flat rows: status dot (green ONLY for a live
process, dim for exited) + label (`(exited)` suffix) + right-aligned mono `#id`.
Hairline dividers between rows; pressed state = subtle surface tint; no boxes,
no borders at rest. Capped at 4 rows + "+N more in Terminal" dim line (unchanged
contract). Tap returns to the session.

## 3. Surface philosophy

1. **The screen background is the canvas.** Content lives directly on it.
   Separation is achieved with: spacing → typography (section labels) →
   hairline dividers → surface tone steps — in that order of preference.
2. **A surface (distinct background / border / shadow) is drawn ONLY for a
   real interactive object**: the two environment launchers, the CLI Apps
   dropdown menu, the FAB + its chips, the launch-error banner (an actionable
   error is an object), and a pressed/focused row or tile.
3. **Tone steps replace borders.** Depth on Home comes from the Midnight
   Sapphire surface stack alone (canvas `#080F1D` < screenBg `#0B1424` <
   chrome `#101B30` < keyAlt `#16233F`). A tone step is a border that never
   draws a line.
4. **No new colors.** Everything comes from the existing `TerminalTheme` /
   `HomeTokens` palette. `runningGreen` still appears ONLY on a real
   running-process dot. Sapphire remains the one accent.
5. **Shadows** exist only under floating layers (FAB, its chips, the dropdown
   menu) — objects that physically float above the page. Nothing else casts.

## 4. Card usage rules

- A **card/surface may represent**: an environment (Terminal, Linux), a
  floating menu, a floating action, an actionable banner, a pressed row/tile.
- A **card must never represent**: a text grouping ("Your tools", "Sessions"),
  an empty state, a section header, a list row at rest, a hint, a footer link.
- **No cards inside cards.** A surface may not contain another bordered
  surface (chip-in-tile, button-in-card are all removed).
- **Radius discipline**: environment tiles and the menu = 14dp (down from
  20dp); icon plates 16dp; banner 14dp. Nothing on Home exceeds 16dp.
- **Border discipline**: no element on Home carries a decorative border at
  rest. Hairlines are used ONLY as full-width dividers between sections/list
  rows, and on floating objects (menu, FAB chips) where the page behind moves.

## 5. CLI Apps dropdown design

ONE CLI control, in the header area, replacing every floating CLI affordance:

- **Trigger**: a quiet text control `CLI Apps ▾` on its own row directly under
  the foundation tiles (per the approved hierarchy). Rendered **only when the
  Phase 3.2 discovery state contains at least one available app** — a menu
  with nothing to open never renders ("every button must have a clear
  purpose"). Mono small-caps styling, `textDim`, chevron rotates 180° when
  open.
- **Menu**: compact anchored popup — Material `DropdownMenu` restyled onto the
  Midnight surface (chrome background, 14dp shape, zero tonal elevation,
  restrained shadow). It must feel like an OS launcher menu, never a dialog.
- **Rows** (one per *actually available* command app — same
  `terminalViewModel.commandApps` state, zero new discovery logic):
  small borderless monogram plate + display name (`textPrimary`) + the launch
  command right-aligned in dim mono. The command is visible but secondary.
- **Interaction**: tap a row → the existing Phase 3.2
  `onOpenCommandApp` pipeline (fresh verify → dedicated guest session →
  focus). Outside tap / Back dismisses. Long lists scroll (max ~5 visible).
- The app is the identity: no logos in the menu, no decorative icons —
  name + command carry it.

## 6. Command app launcher design ("Your tools")

- Section label `YOUR TOOLS` (existing `HomeSectionLabel` style — dim mono,
  letterspaced). Not "Command apps" (architecture jargon does not belong on
  an OS home).
- **Grid of icon + label**, like an OS launcher page — not cards:
  - Icon: 52dp monogram plate, `keyAlt` tone step, **no border**, 16dp radius,
    accentBright monogram. Reads as an app icon, not a container.
  - Label: display name only, single line, centered under the icon. NO
    description, NO command on the grid (details live in the CLI Apps menu).
  - Surface appears ONLY on press (subtle tint). Selected/focus equivalents
    handled by the standard pressed state.
  - Responsive columns: 3 (phones) / 4 (≥600dp) / 6 (≥840dp), content capped
    at 720dp centered — unchanged Phase 3.2 breakpoints.
- Verifying state: the app's plate slot shows the small spinner (unchanged).
- Probe-failure footnote (apps present) stays as a dim single line.

## 7. FAB purpose

The floating control represents exactly one idea: **create a new session.**

- Actions: `+ New Terminal`, `+ New Linux session` (READY-gated). NOTHING else.
  Command apps are NOT creation actions — they left the FAB in this phase
  (they live in §5/§6).
- Chips are **text-only pills** (deck tone, hairline, 14dp radius): no icon
  circles, no logo marks, no monograms — the duplicated-decoration channel is
  closed.
- Main control unchanged: 56dp Sapphire circle, `+` rotates to `×`, 42% scrim,
  chips staggered 30ms / 160–180ms, BackHandler + scrim dismiss, disabled
  while creating. No bounce, no glow, no icon cloud.
- The `QuickAction` data model stays a list (future creation actions slot in
  as data), but shrinks to `id / label / enabled / onRun`.

## 8. Terminal/Linux foundation layout

Still the two most prominent objects on the page — but flatter and quieter:

- Two tone-step surfaces in one row (weights 1.25 : 1), **radius 14dp, no
  borders, no shadows**. Terminal wears the canvas tone (it IS a terminal);
  Linux wears the chrome tone (one step lighter than the page). The tone
  difference alone separates them from the background and each other.
- Contents:
  - Top row: environment mark (drawn `TerminalMark` / `MountainMark`, 32dp) —
    left. Top-right: Terminal shows `N running` as plain dim mono text (the
    bordered chip is gone); Linux shows the 6dp sapphire ready dot (READY
    only).
  - Bottom: `Terminal` / `Linux` (titleLarge, semibold) + ONE short dim line:
    Terminal → `Native shell` (no truncation at any width).
    Linux → honest state line (`Alpine Linux · ready` in accent when READY;
    otherwise the true state + `Set up from Diagnostics`, which is the
    functional action the tile routes to).
- Height 160dp (down from 168dp), press = 80ms soft scale (unchanged).
- Non-READY Linux still routes to Diagnostics (M2 honest-gate contract,
  untouched).

## 9. Empty states

No containers, no fake placeholder icons, nothing that dominates:

- **No command apps** (real empty answer): directly under the section label —
  `No CLI apps yet.` (textPrimary) + one dim sentence: "Install an interactive
  command application and it will appear here." + `Explore packages` accent
  text link. Total height ≈ 3 text lines. This is the ONLY "Explore packages"
  in this state.
- **Runtime not ready**: same shape, sentence becomes "Install the Linux
  runtime first — interactive command apps live inside your Linux environment
  and launch from here." (honest, unchanged truth).
- **Probe in flight**: one quiet line + 14dp spinner: `Checking the Linux
  environment…` (no card).
- **Probe failed, no apps**: one dim line: `App availability could not be
  checked — <error>` (never a fake "none"; v0.4.4 rule preserved).
- **Packages reachability**: when apps DO exist, the empty-state link is gone —
  a single quiet `Packages` text link sits at the page footer in that state.
  Exactly ONE packages affordance is visible in every state; duplication is
  impossible by construction.

## 10. Other-page consistency rules

Review-level only (these screens are already structurally clean — dividers,
full-width rows, typography hierarchy; no rounded-rect groupings found):

- **Settings / Diagnostics**: keep the app-theme Material look and the exact
  row/button inventory. Rule for the future: sections are typography +
  dividers, never card groups. No code change unless a violation appears
  (none found in inspection).
- **Packages (Explore)**: per-entry surfaces represent REAL objects (an
  installable package with actions) — allowed under §4. Info panels are
  informational cards with actions/meaning; keep. No redesign this phase.
- **Global rules adopted from this phase** (recorded for all future screens):
  dividers before cards; radius ≤16dp; no decorative borders; logo only in
  identity contexts (header/splash/about); icons must be functional;
  one accent + status green; empty states are inline text, never containers.

## 11. Future expansion architecture

The Home grows along one axis: **labeled sections on a canvas**.

- A future capability (GUI Apps, AI Assistant, Tools, …) becomes a new
  `HomeSectionLabel` + content block between the same hairline dividers — no
  new layout concept, no redesign.
- The CLI Apps menu and the tools grid both render from the Phase 3.2
  registry/probe state — new registry entries surface in both automatically.
- `QuickAction` stays a data list — future creation actions (e.g. "New
  environment" when multiple distros exist) join as entries; the FAB stays
  "create" only.
- The foundation row accepts a third environment surface (future runtime) by
  adjusting weights; the tone-step system already encodes "object vs page".
- Multi-distro: the Linux tile's state line and the runtime gate are the only
  Alpine-coupled strings; the tile structure is distro-agnostic.

## 12. Explicit list of removed Phase 3.2 UI concepts

1. `EmptyAppsState` giant container (surfaceBanner card + 30dp padding +
   embedded "Explore packages" pill button).
2. `GhostTiles` / `GhostTile` fake placeholder illustration (deleted).
3. `PackagesFooterLink` as an unconditional duplicate of the empty-state
   action (replaced by the exactly-one-affordance rule, §9).
4. Command-app actions inside the FAB menu (apps are not "create" actions).
5. Icon circles + brand marks inside FAB chips (chips are text-only now).
6. `RunningChip` bordered mono chip on the Terminal tile (plain dim text).
7. Bordered per-row session cards (flat divider rows instead).
8. Terminal tile accent border + Linux tile hairline border (tone steps
   only).
9. 20dp hero radius (→ 14dp; nothing on Home exceeds 16dp).
10. Truncating descriptor copy ("Native PocketShell envir…", "Enter the
    guest s…") — replaced with short, always-fitting lines.
11. "Command apps" section naming on Home (→ "Your tools"; the term stays in
    code/docs where it is architecture).
12. Logo/marks as decorative icons anywhere outside identity contexts
    (header mark, environment marks on their own tiles).

---

## Checkpoints

- **3.3.1 Home workspace composition** — foundation tiles (§8), tools grid +
  inline honest states (§6/§9), flat session rows (§2a), dividers, removals
  (§12.1–10). Build + full tests.
- **3.3.2 CLI Apps dropdown** — trigger row + popup menu (§5). Build + tests.
- **3.3.3 FAB single-purpose** — QuickAction trim, text chips (§7). Build +
  tests.
- **3.3.4 Polish + gate** — consistency review pass (§10), a11y (roles,
  labels, ≥40dp targets, state never color-only), responsive sweep (phone /
  tablet / foldable widths), versionCode 20 / `0.7.0-m3.3`, full test suite +
  assembleDebug + aapt2/apksigner verify.

## Validation gate

- Full suite green (644 executions baseline — app + terminal-emulator × 2
  variants), Phase 3.1 keyboard/tab contracts untouched, `CommandAppsTest`
  untouched and green.
- assembleDebug OK; APK = versionCode 20 / 0.7.0-m3.3; cert chain intact
  (d96a6f66…8bf659) for in-place update.
- Visual checklist: no bordered boxes except the four object classes in §3;
  exactly one packages affordance per state; no ghost placeholders; no text
  truncation on the foundation tiles; FAB = create only.
