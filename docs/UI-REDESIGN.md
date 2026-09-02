# PocketShell — UI/UX Redesign & Design System

Status: ACTIVE (UI phase) · Written before implementation, per the redesign brief §19.
Baseline: v0.6.2-m2.6 (versionCode 16), 624 unit tests green, runtime M2.6 device-battle-tested.

---

## 0. Product definition

> PocketShell is a polished personal Linux environment on Android.

It is **not** an IDE, not a project manager, not a code editor, not a desktop UI
squeezed onto a phone. It is not Termux's look-alike. Every screen must feel
like one high-quality modern Android product. The UI stays **honest**: no fake
features, no placeholder functionality pretending to work, no invented state
(continued discipline from v0.3.1–v0.6.2 — see docs/CHANGELOG).

Chosen direction: **Style A + a small amount of D** — sleek, elegant, soft,
premium, modern, mobile-first, visually calm, slightly futuristic, subtle
depth, restrained gradients only where they carry meaning, clean typography,
generous spacing, polished (restrained) animations, smooth rounded surfaces
that never become bubbly. Explicitly avoided: neon, cyberpunk, hacker
aesthetics, monochrome terminal boringness, excessive glassmorphism,
excessive gradients, clutter.

## 1. Current-state inspection (what exists today)

Screens (string-keyed `when` in `PocketShellRoot`):

| Screen | File | Verdict |
|---|---|---|
| Home | `ui/home/HomeScreen.kt` | List of big cards: Terminal, Linux Shell, "Installed CLI Apps" (real apk probe), Explore row, Active sessions. **Replace**: reads like a settings page; CLI-app cards clutter the gateway; no brand identity. |
| Terminal | `ui/terminal/TerminalScreen.kt` | Tab strip (back, chips, keyboard toggle, +) + TerminalView + built-in keyboard. **Keep the engine**, redesign chrome; keyboard replaced by the §6 final spec. |
| Explore | `ui/apps/ExploreAppsScreen.kt` | Search + honest install states. **Keep all logic**, restyle; rename destination to "Packages". |
| Settings | `ui/settings/SettingsScreen.kt` | Radio rows + switch + slider. **Restyle** into grouped cards; add AI Assistant section. |
| Diagnostics | `ui/diagnostics/DiagnosticsScreen.kt` | Dense fact rows + runtime install controls. **Restyle only** — every row and button preserved (this is the technical backbone, deliberately not "pretty"). |

Supporting pieces inspected:

- `keyboard/` — `KeyboardState` (OFF→ONE_SHOT→LOCKED modifiers), `KeyLayouts`
  (phone alpha/symbol pages, tablet full layout, FN remaps), `TerminalKeyDispatcher`
  (KeyCharacterMap → TerminalView), `TerminalKeyboard` (M1 full built-in keyboard).
  **Decision**: the M1 built-in typing keyboard is replaced by the two accessory
  rows of the final keyboard spec (§6 of the brief) + the Android IME. The
  state/dispatcher model is reused unchanged; layouts and tests are rewritten.
- `packages/CliAppCatalog.kt` — 5 curated packages (nano/htop/vim/git/python3).
  These are **packages**, not launchers: they move out of Home and stay in
  Packages (Featured). Launchable-app detection is a new, documented rule set (§7).
- `TerminalViewModel` — openTerminal/openLinuxShell/openCatalogApp + honest
  `launchError` surface. **Unchanged**; UI re-wires to it.
- Theme — 3 schemes (brand light/dark/AMOLED + dynamic). **Replaced** by the
  §3 design system (keeps ThemeMode enum + dynamic color option).
- Runtime gating everywhere (Home Linux Shell card routes non-READY states to
  Diagnostics; Explore refuses without READY; app opens verify against apk).
  **Preserved 1:1** — the redesign must not regress M2.6 (brief §18).

Reusable vs replaced:

- **Reuse**: TerminalView + sessions + session client (untouched), keyboard
  state/dispatcher, all runtime/package logic and its honesty rules, the
  screen set itself, DataStore settings plumbing (extended).
- **Replace**: Theme.kt colors, M1 keyboard layouts, Home structure,
  navigation spine (string `when` → typed navigation + drawer).
- **New**: design-system component kit (`ui/components/`), launchable-app
  detection, OpenRouter config storage, assistant FAB.

## 2. Research inputs

- Material 3 Expressive (m3.material.io, 2025): shape variety, emphasized
  type, physics-based motion, containment — used **restrained** (calm variant).
- Android layout/navigation guidance (developer.android.com, 2026): modal
  navigation drawer is a sanctioned primary-navigation pattern; navigation bar
  is the other. The brief mandates the drawer (no permanent tabs) — drawer it is.
- Mobile UX 2026 playbook: thumb-zone placement for primary actions, WCAG 2.2
  AA contrast, comfortable touch targets (≥48dp), keyboard accessory rows as
  the terminal-input pattern.
- Terminal-app conventions (Termux et al.): extra-keys row above the IME;
  modifier toggles with visible state; arrow long-press for HOME/END/PGUP/PGDN.

## 3. Design system — "Quiet Aurora"

One system, four layers: **tokens → primitives → components → screens**.
All tokens live in `ui/theme/`; all shared components in `ui/components/`.

### 3.1 Color

Brand scheme "Quiet Aurora": cool graphite surfaces, a single soft
periwinkle-violet accent, semantic green/amber/red reserved for state.
Terminal canvas stays a near-black ink in **every** app theme (a light terminal
canvas is a readability trap; modern terminal apps keep a dark canvas and
frame it with the theme).

Dark (default) — backgrounds:
`#0B0D12` bg · `#12151D` surfaceLow · `#171B25` surface · `#1D2230` surfaceHigh
· `#242A3B` surfaceHighest.
Accent: `primary #93A6F5` / `onPrimary #101631` · `primaryContainer #232C52` /
`onPrimaryContainer #DCE2FF`. Semantics: `ok #7CC7A5` · `warn #E8C07A` ·
`error #F0A9A5` (+ containers). Text: `onSurface #E4E7F2`, `onSurfaceVariant
#9AA1B8`, `outline #2C3347`.

Light — `#F7F8FC` bg · `#FFFFFF` surface · `#EEF0F7` surfaceLow ·
`primary #3D4EA8` / `onPrimary #FFFFFF` · `primaryContainer #DFE4FF` ·
text `#171A24` / `#5A6072`.

AMOLED: pure `#000000` bg, surfaces `#050505`/`#0A0A0F`, same accent family.

Dynamic color (Android 12+, user opt-in) still maps onto the same roles —
the system provides the palette, the layout roles stay ours.

Gradient policy (the "tiny amount of D"): exactly one hero gradient
(Home Terminal card: `#1B2238 → #26304F` in dark, `#E7EBFF → #D6DEFF` in
light) and a soft radial glow behind the Home brand mark. Nothing else
gradients — buttons, sheets, banners stay flat.

Background hierarchy: `background` (screen) < `surfaceContainerLow`
(recessed zones: terminal frame, keyboard deck) < `surface` (cards) <
`surfaceContainerHigh` (nested chips, key caps) < `surfaceBright` (selected
states). Elevation is tonal first; drop shadows only on the drawer scrim
edge, the FAB, and pressed hero card (max 2dp).

### 3.2 Typography

System font stack (no bundled font — APK honesty and size): Roboto /
sans-serif for UI, `monospace` for anything that quotes real terminal output.
Type scale (M3 roles, tuned):

- `displaySmall` 34/40 — Home brand wordmark only.
- `headlineSmall` 26/32 — screen titles.
- `titleLarge` 20/28 — card titles (hero).
- `titleMedium` 17/24 w600 — card titles, section labels.
- `bodyLarge` 15/22, `bodyMedium` 14/20, `bodySmall` 12.5/18 — facts, captions.
- `labelLarge` 13/16 w600 — buttons, pills, key caps.
Numbers/versions inside UI text use tabular figures where available.

### 3.3 Spacing, shape, elevation

Spacing scale (4dp base): 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48.
Screen gutter 20dp; card padding 20dp (16dp dense); section gap 20dp;
grid gap 12dp; keyboard key gap 4dp.

Corner radius system: `small 12` (inputs, chips) · `medium 16` (standard
cards) · `large 20` (hero cards, sheets, dialogs, drawer) · `full` (pills,
FAB, status dots). Not bubbly: nothing exceeds 20 except circles.

### 3.4 Iconography & imagery

Material **outlined** set (already vendored, consistent stroke), 24dp, 2dp
stroke visual weight; filled variants only for selected nav states. The brand
mark (`PSLogo`) is drawn, not an image asset: rounded-square (20dp radius)
with the one hero gradient, a `>_` glyph and a thin terminal-cursor bar —
scales from 40dp (drawer header) to 64dp (Home).

### 3.5 States

- Loading: circular progress 20–24dp inside the affected surface, never
  full-screen spinners; operation banners show real apk output lines.
- Empty: `PSEmptyState` — outlined icon in a 56dp tinted circle, one-line
  title, one-two line body, optional action. Always says what *would* appear
  and why it doesn't (honesty rule).
- Error: `PSBanner` (error variant) with the real message + Dismiss +
  optional Diagnostics action. Never vague.
- Disabled: 38% content alpha, no shadow, no press ripple.
- Pressed: scale 0.98 + tonal rise, 120ms.

### 3.6 Motion

Durations 180–260ms, `FastOutSlowIn` / emphasized easing. Uses: drawer slide
+ scrim fade, screen transitions (fade-through: 90ms out / 180ms in + 4dp
slide up), card press, banner enter/exit, FAB entrance (once, spring),
modifier key state, session tab selection. Forbidden: looping ambient
animation, parallax, >300ms transitions, animated gradients.

### 3.7 Component kit (`ui/components/`)

| Component | Purpose |
|---|---|
| `PSLogo` | Brand mark (gradient rounded square + `>_`). |
| `PSScreenScaffold` | Column scaffold: `PSScreenHeader` + content; standard gutters. |
| `PSScreenHeader` | Hamburger (two unequal lines, custom draw) + title + optional actions. |
| `PSNavDrawer` | Modal drawer: logo header, destination rows, runtime status footer. |
| `PSHeroCard` | The one gradient card (Home Terminal). |
| `PSActionTile` | Launcher grid tile (icon disc + label + state line). |
| `PSListCard` | Row card (leading icon/disc, title, supporting line, trailing). |
| `PSSectionLabel` | Small-caps section header. |
| `PSBanner` | info/ok/warn/error banner with dismiss + optional action. |
| `PSEmptyState` | §3.5. |
| `PSStatusPill` / `PSStatusDot` | Runtime/session state (READY, exited, …). |
| `PSFab` | Assistant floating icon (Home only). |
| `PSKeyCap` | Keyboard key surface (used by both accessory rows). |

### 3.8 Accessibility

Contrast ≥ 4.5:1 body / 3:1 large text (tokens above verified); every
icon-only control carries a `contentDescription` or `semantics` label
(hamburger, keyboard toggle, FAB, session close); touch targets ≥ 48dp
(key caps 44dp visual inside 48dp slots); text scales with system font
scale (sp everywhere, no fixed-height text containers); focus states via
Compose defaults; drawer/dialogs announce themselves (ModalDrawer /
Dialog semantics).

### 3.9 Responsiveness

Phone-portrait first. `WindowWidthSizeClass`-style split at 600dp:
launcher grid 2×2 → 4-across; drawer becomes always-compact (280dp max
width); keyboard rows get taller key gaps but the same structure; terminal
padding grows. No two-pane layouts (not an IDE); tablets get breathing room,
not stretched cards (max content width 640dp centered on Home/Packages).

## 4. Navigation architecture (brief §4)

- No permanent tab bar. Ever.
- `PSScreenHeader` hamburger (top-left, two lines, upper shorter) opens
  `PSNavDrawer` on every screen.
- Drawer destinations (real, honest): **Home, Terminal, Linux Shell, Apps,
  Packages, Diagnostics, Settings**. Future destinations (GUI Apps, AI
  Assistant chat, SSH, Distributions) are added as enum entries + one drawer
  row when they actually exist — the architecture (typed `Screen` enum +
  single `when` + drawer list builder) makes that a two-line change.
- Screen keys move from `String` to `enum class Screen` with
  `rememberSaveable` (state survives process death the same way).
- Back: drawer open → close it; else non-Home → Home; Home → system default.
- Linux Shell and Terminal drawer entries reuse the exact spawn flows Home
  uses (same `openLinuxShell()` / `openTerminal()` gates; refusals surface
  in the launch-error banner on Home — no new honesty surface invented).

## 5. Home (brief §3)

Structure (top → bottom):

1. **Brand block** — 64dp `PSLogo` + "PocketShell" (`displaySmall`) +
   tagline "Your Linux environment, in your pocket", optically centered,
   soft radial glow behind. Compact status pill (runtime state) sits in the
   top header row next to the hamburger.
2. **Launcher grid (2×2)** — `PSActionTile`s:
   - **Terminal** (`PSHeroCard` spans both columns) — Android shell session.
   - **Linux Shell** — state-aware tile: READY → enter guest; any other
     state → honest state line + tap routes to Diagnostics (M2.6 gate kept).
   - **Apps** — opens the launchable-apps sheet (below).
   - **Packages** — package explorer (search/install Alpine packages).
3. **Apps area** — launchable apps detected in the guest (§7 rules):
   `PSListCard` per app, tap = verify-then-launch (existing honest flow).
   Empty state when none detected: "No launchable apps detected yet — apps
   installed inside the Linux guest that provide an interactive command
   (e.g. hermes, opencode) appear here."
4. **Active sessions** — only when they exist: dot + label + "(exited)".
5. **Launch-error banner** (`PSBanner`, error) when a spawn was refused.
6. **Assistant FAB** — bottom-right, `PSFab`; opens the AI Assistant
   settings section (real config) with an honest "chat arrives in a future
   update" line in that section. Never pretends to chat.

Explicitly absent from Home: git/python/nano/gcc/htop cards, project cards,
recent-projects, IDE concepts, fake counts, fake recents. The Home is useful
with zero installs: Terminal + Packages + the honest Apps empty state.

## 6. Terminal (brief §5)

- **Chrome**: single header row = hamburger + session tab pills + "+"
  (new session) + "⋮" overflow (Close session; New session). Overflow grows
  for future capabilities (rename, profiles, SSH) without new UI paths.
- **Tabs**: rounded pills (`full` radius, 36dp tall); selected = accent
  container; finished sessions get a hollow dot + "(exited)". Close "×"
  appears on the selected tab only.
- **Canvas**: `surfaceContainerLow` frame with 20dp top / 12dp side padding
  around the TerminalView; terminal background stays its native near-black
  (the "framed ink" look); terminal text uses the platform monospace at the
  user's size (12–40sp, pinch + settings default unchanged).
- **Cursor/selection**: existing upstream behavior kept (blinker lifecycle
  already correct; copy/paste ActionMode kept — v0.5-era paste fix untouched).
- **Session states**: creating → `PSEmptyState` "Starting shell…"; no
  sessions → `PSEmptyState` + action; exited session stays open in its tab
  with the honest `[process completed]` line from the shell itself.
- **Keyboard sandwich** (brief §6, see §7): accessory rows belong to the
  terminal screen; the Android IME visibility is toggled from the keyboard
  row icon only.

## 7. Keyboard — final specification (brief §6, binding)

```
┌──────────────────────────────────────────────┐
│ Esc   Tab                     ←  ↑  ↓  →     │   top row (always)
├──────────────────────────────────────────────┤
│                ANDROID KEYBOARD              │   system IME (toggled)
├──────────────────────────────────────────────┤
│ [⌨]   Ctrl   Alt   Space   Shift   ↵         │   bottom row (always)
└──────────────────────────────────────────────┘
```

- **Top row**: `Esc` · `Tab` · (spring) · `← ↑ ↓ →` cluster right-aligned.
  Nothing else. Ever.
- **Bottom row**: keyboard toggle **icon only** (Material `Keyboard`/`KeyboardHide`
  outlined glyph; no "ON/OFF"/"Keyboard" text, contentDescription set) ·
  `Ctrl` · `Alt` · `Space` (flex) · `Shift` · `↵`. Positions are permanent.
- The toggle shows/hides the **Android IME** (InputMethodManager on the
  focused TerminalView). Both PocketShell rows remain visible regardless;
  when the IME is open the rows ride above it (`imePadding`), giving the
  sandwich in the diagram (rows adjacent when the IME is closed).
- **Modifiers**: Ctrl/Alt/Shift keep the OFF → ONE_SHOT → LOCKED cycle with
  visual state only (filled container = active, small lock dot = LOCKED,
  bold label = one-shot). No textual ON/OFF.
- **Fn key: removed** (`ModifierKey.FN` deleted; `fnRemap` retires).
  Function-key access without wasting bar space:
  - long-press `←`/`→`/`↑`/`↓` → HOME/END/PGUP/PGDN (repeatable);
  - long-press `Esc` → a compact F1–F12 strip pops above the bottom row;
    tap sends the key and dismisses; tap-away dismisses.
  Documented on the escape hatch grounds the brief allows ("where practical");
  the accessory bar itself never grows special-character or number keys —
  symbols/digits come from the Android keyboard (its long-press behavior).
- **Enter**: keycode ENTER (shell newline); **Space**: repeatable text.
- Session switch clears modifier state (existing rule kept).
- The M1 built-in typing pages (alpha/symbol/extended rows, tablet full
  layout) are deleted together with their layout tests; `KeyboardState` +
  `TerminalKeyDispatcher` survive unchanged (tests updated, count maintained).

## 8. Packages (brief §7, formerly Explore)

Same logic, new skin: header, prominent search field (rounded 16dp, honest
"…" progress in the field), ranked results as `PSListCard`s with real
version + install state, per-card "Working…" rule (v0.4.2) untouched,
operation banner (`PSBanner` + apk stderr lines + Cancel/Retry), Featured
catalog section (the curated five **stay here** — they are packages), honest
probe-failure surface. Installed-state rules from v0.4.4 are inviolable.

## 9. Launchable-app detection (brief §7 — documented rules)

New `packages/LaunchableApps.kt` — the only source of Home "Apps" rows:

- A launchable app = a documented entry
  `(id, name, description, executable, launchCommand)` **plus a live guest
  confirmation**: runtime READY **and** `command -v <executable>` succeeds
  right now. Probe failures hide the row (never a dead tile).
- Seed entries (small, real, interactive): `hermes` (Hermes Agent),
  `opencode` (OpenCode). Adding an entry = one data row + a test pin.
  No large hardcoded list; **ordinary CLI tools are categorically excluded**
  (they are packages; Packages search reaches them).
- Launch reuses the existing catalog-app verify-then-launch flow (real apk
  db + `command -v` + dedicated guest session), so "Open" can never fake.
- Documented in this file's KDoc + TESTING §11 checklist.

## 10. AI Assistant & OpenRouter settings (brief §10/§11)

- **Foundation only, honestly labeled**: Settings → "AI Assistant" section
  with OpenRouter API key + model id fields (free-text model, no fixed
  list), Save/Clear, and a status line "Assistant chat is not part of the
  app yet — this screen stores your configuration for when it lands."
- Storage: DataStore (app-private). The key is **masked after entry**
  (shows `••••` + last 4). Never logged, never in Diagnostics, never in
  crash surfaces (grep-verified). Honest note rendered in the section:
  "Stored in the app's private storage; OS-keystore-backed storage is a
  planned upgrade." No pretending.
- The assistant FAB opens this section; no chat UI is built.

## 11. Diagnostics (brief §13)

All facts and buttons preserved; restyled into grouped `PSListCard`
sections (Device, Linux runtime, Package environment) inside the design
system; the install/remove/repair/retry buttons and the explicit
package-environment check keep their exact semantics. Not hidden, not
de-emphasized into uselessness — it stays the advanced area reachable from
the drawer and every honest error path.

## 12. Future extension points (brief §14)

- `Screen` enum + drawer builder: GUI Apps / AI chat / SSH / Distributions
  plug in as entries; no navigation rewrite.
- Storage roots (File Explorer): design reserves the "storage context"
  concept (Android storage vs Linux guest) as a future segmented control in
  explorer screens; nothing ships now.
- Session profiles: `SessionEntry` already carries label/title; overflow
  menu is the future hook.
- Design tokens are one file each — re-theming for new surfaces is token
  work, not screen work.

## 13. Checkpoint plan (brief §20) & verification

UI.0 baseline (done: 624 tests, APK v0.6.2-m2.6) → UI.1 tokens+kit →
UI.2 navigation → UI.3 Home → UI.4 Terminal → UI.5 Keyboard → UI.6 Packages
→ UI.7 Settings/Diagnostics → UI.8 polish. After every checkpoint:
`gradlew test` (full suite) + `assembleDebug`; compile-level verification is
never claimed as completion. Before declaring the phase complete: full suite,
fresh APK, artifact hashes, delivery page, device checklist (TESTING §11)
covering runtime, packages, terminal, keyboard keys, toggle, navigation,
Home/Apps/Packages/Diagnostics/Settings.

Inviolable regressions to guard (brief §18): Alpine install/PRoot, apk
update/add/remove, node, hermes, terminal sessions, Android shell, runtime
diagnostics, storage behavior, M2.6 /proc sysdata + link2symlink stack.
