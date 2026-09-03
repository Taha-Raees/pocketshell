# Phase 3.1 — Terminal Experience Redesign: "Midnight Sapphire"

Status: DESIGN CONTRACT (committed before implementation, plan-first discipline)
Scope: the Terminal screen ONLY (chrome, session tabs, terminal workspace, terminal
keyboard). Home / Explore / Packages / Settings / Diagnostics / navigation are
explicitly OUT of scope. No runtime architecture changes — the PTY, proot runtime,
session manager, and the vendored Termux dispatch pipeline stay exactly as they are.

Direction from the user (authoritative):
- Deep blue-toned dark theme. NOT pure black. Premium, calm, Android-native.
- Terminal session tabs: NOT rounded pill boxes — editor/browser-style tabs where
  the active tab visually connects to the terminal workspace (asymmetric borders,
  structured top edge).
- Keyboard: 100% custom, built from scratch ( clarified by user: the Android
  system IME is NOT used at all — PocketShell's own QWERTY is the middle layer).
- Final stack, top to bottom:
      TERMINAL OUTPUT
      Esc   Tab                 ←  ↑  ↓  →      (top accessory row)
      CUSTOM QWERTY KEYBOARD                    (PocketShell, from scratch)
      [⌨]  Ctrl  Alt  Space  Shift  Enter       (bottom accessory row)
- No dedicated Fn key: F1–F12 via long-press on the number-row keys (now fully
  reliable because we own the keyboard — no IME limitations).
- One restrained accent color for the whole page.

## 1. Identity & palette

Theme name: **Midnight Sapphire**. One accent: **Sapphire `#7FA3EF`**.

The Terminal screen renders its own fixed Midnight identity in every app theme
(Light/Dark/AMOLED/Dynamic included). Rationale: a terminal canvas is a dark
professional surface (Termius/Termux/Warp all do this); mixing a light chrome
around a dark canvas was tried by nobody and reads broken. The rest of the app
keeps the M2.6 theme untouched. Decision is recorded here on purpose.

### 1.1 Surface stack (outer → inner, cool blue-dark family)

| Token            | Value     | Role                                          |
|------------------|-----------|-----------------------------------------------|
| screenBg         | `#0B1424` | page background behind everything              |
| chrome           | `#101B30` | top app chrome (back, title)                   |
| tabStrip         | `#0D1730` | session tab strip surface                      |
| activeTab/canvas | `#080F1D` | active tab fill == terminal canvas (the merge) |
| canvas           | `#080F1D` | terminal workspace — deepest blue-black        |
| deck             | `#131F38` | keyboard accessory deck surface                |
| key              | `#1B2947` | content key (letters/digits/symbols)           |
| keyAlt           | `#16233F` | control key (Esc/Tab/arrows/modifiers)         |
| keyPressed       | `#26365B` | pressed key                                    |
| keyActive        | `#24406E` | modifier one-shot (accent-tinted)              |
| divider          | `#1D2C4A` | hairlines (1dp)                                |
| textPrimary      | `#DCE6F8` |                                                |
| textDim          | `#7C8DB0` |                                                |
| accent           | `#7FA3EF` | cursor, active states, Enter, tab indicator    |
| accentBright     | `#A5C0FF` | accent text on dark                            |
| accentDeep       | `#3D5A96` | accent outline / pressed accent                |

Gradients: exactly one — a barely-visible vertical `#111C33 → #0E1730` brush on
the top chrome, for depth. Nothing else glows. No neon, no cyberpunk.

### 1.2 Terminal ANSI palette (real 16-color override)

Written into `TerminalColors.COLOR_SCHEME.mDefaultColors` at process start
(before any session spawns) — real terminal semantics preserved (OSC overrides
from programs still win, upstream `TerminalColors.mCurrentColors` untouched).

| idx | name | value | idx | name | value |
|----|-------|---------|----|-------|---------|
| fg | foreground | `#D7E3F7` | bg | background | `#080F1D` |
| 0 | black | `#182238` | 8 | brightBlack | `#4E5F82` |
| 1 | red | `#DA6C7D` | 9 | brightRed | `#EE7D90` |
| 2 | green | `#5FB572` | 10 | brightGreen | `#7BCB8C` |
| 3 | yellow | `#C9A45C` | 11 | brightYellow | `#E2BB6E` |
| 4 | blue | `#6C9BD8` | 12 | brightBlue | `#8AB4F0` |
| 5 | magenta | `#B57FC6` | 13 | brightMagenta | `#C996D8` |
| 6 | cyan | `#56B3C2` | 14 | brightCyan | `#74CFDE` |
| 7 | white | `#C2D0E8` | 15 | brightWhite | `#E6EEFA` |
| cursor | | `#7FA3EF` | | | |

Cursor: block (upstream default), Sapphire fill with inverted text — modern,
unambiguous, zero custom rendering code (renderer already draws it). Blinker
behavior stays upstream (500ms, host-managed). DECSCUSR bar/underline still
honored — the terminal remains a real terminal.

## 2. Typography

- Terminal: **JetBrains Mono NL** (no-ligature build, OFL 1.1) — Regular, Bold,
  Italic. NL variant keeps output character-exact (no `->`→`→` merging): a
  terminal must show the shell's actual bytes. Tall x-height + distinct
  `0/O 1/l/I` + excellent small-size rendering. Applied via
  `TerminalView.setTypeface()` (vendored API, verified).
- Key labels: the app sans (Roboto), medium weight. Key glyphs that are icons
  (arrows, backspace, enter, keyboard toggle) use Material icons.
- Font files live in `res/font/` (3 × ~110KB). License noted in
  docs/THIRD_PARTY.md.

## 3. Screen composition

```
┌──────────────────────────────────────────────────┐
│ chrome  [←]  Alpine Linux                         │  52dp · chrome · 1 subtle gradient
├──────────────────────────────────────────────────┤
│ tabs    ╭─────────╮ ╭─────────╮        ◯ +        │  40dp · tabStrip · hairline bottom
│         │ session │ │ term 2 ×│ ── cut ──────────  │  (active tab cuts the hairline)
├───────────── active tab fill == canvas ───────────┤
│                                                  │
│                TERMINAL CANVAS                    │  deepest blue-black · full width
│                (JetBrains Mono)                   │  bottom corners 16dp rounded
│                                                  │
├──────────────────────────────────────────────────┤
│ Esc   Tab                    ┌────────────────┐  │  top accessory row 40dp
│                              │ ←  ↑  ↓  →     │  │  arrows grouped in a panel
│                              └────────────────┘  │
├──────────────────────────────────────────────────┤
│  1  2  3  4  5  6  7  8  9  0        (hold→Fn)   │  QWERTY body (collapsible)
│  q  w  e  r  t  y  u  i  o  p                    │
│     a  s  d  f  g  h  j  k  l                    │
│  ?123  z  x  c  v  b  n  m  ⌫                    │
│  -  /  :  ;  ,  .  $  '  "  @                    │
├──────────────────────────────────────────────────┤
│ [⌨]  Ctrl   Alt   [ Space ]   Shift   ⏎ Enter    │  bottom accessory row 44dp
└──────────────────────────────────────────────────┘
```

- **Chrome**: back arrow (48dp target), active session's real display label
  (live OSC title when the program sets one). No invented controls; the right
  side stays empty as the honest expansion slot.
- **Edge-to-edge**: the Terminal screen consumes the status-bar inset itself so
  the chrome surface extends behind the status bar (one MainActivity change:
  terminal branch receives the raw modifier instead of Scaffold padding;
  every other screen keeps its current padding). The accessory deck pads for
  the gesture-nav bar.
- **Canvas**: full-bleed width (max columns on a phone), flush under the tab
  strip so the active tab genuinely merges into the workspace; rounded bottom
  corners (16dp) land on screenBg. Empty state: no widgets, no quotes — only
  the honest "No open sessions" line from v0.6.2, restyled.

## 4. Session tabs (the centerpiece)

- Shape: rounded TOP corners (10dp), flat bottom. Inactive tabs are 34dp tall
  (recessed), active is 40dp (rises to meet the canvas).
- Active tab: fill = exact canvas color `#080F1D`, 2.5dp Sapphire top hairline,
  primary text, visible close ×. It covers the strip's bottom hairline — the
  classic editor-tab "cut", i.e. the asymmetric border treatment from the brief.
- Inactive tabs: transparent over tabStrip (quieter by contrast, not by outline),
  dim text, 1dp right hairline separator, dim × (still a 32dp touch target).
- "+" new session: 32dp circular keyAlt surface, stable at the strip's end
  (right of the scrollable list) — not floating, not crowded.
- Tabs live in a LazyRow (existing keys preserved); close still kills the real
  session (v0.6.2 semantics, no confirm-dialog invention); finished sessions
  still show "(exited)".

## 5. Keyboard (custom, from scratch)

Everything is Compose; the dispatch pipeline is UNCHANGED (KeyAction →
TerminalKeyDispatcher → synthetic KeyEvents → vendored KeyHandler → PTY).
Only the source of presses and the visuals are rebuilt.

### 5.1 Top accessory row (always visible)
`Esc` · `Tab` · (spring) · arrow cluster `← ↑ ↓ →` inside a subtle inset panel
(keyAlt keys, 44×40dp each, 5dp gaps, panel radius 10dp).
Esc/Tab dispatch through the existing pipeline; arrows auto-repeat (350ms/60ms,
existing engine). Arrows keep repeat — held ↑ recalls shell history, that beats
long-press-for-HOME (which the discarded UI attempt tried); HOME/END/PGUP/PGDN
live on the symbol page.

### 5.2 QWERTY body (collapsible — the [⌨] toggle)
- Phone portrait, 5 rows:
  - `1..0` — long-press ≥350ms → F1..F10 (popup bubble "F1" over the key,
    haptic tick, release commits, slide-off cancels; digits never auto-repeat)
  - `q..p` / `a..l` / `?123 z..m ⌫` (⌫ auto-repeats, accelerating)
  - terminal punctuation row: `- / : ; , . $ ' " @`
- Symbol page (?123 ⇄ ABC): `!@#$%^&*()` · `` ~ ` { } [ ] \ | = + `` ·
  `< > ? _ INS DEL` · `ABC HOME END PGUP PGDN ⌫` — every symbol the v0.6.2
  test pins (`~`!@#$%^&*()-_+={}[]\|;:'",.<>/?`) plus the extended navigation
  keys stay reachable (no feature regression).
- Landscape: digits + QWERTY rows only (4 rows), compressed 34dp keys —
  landscape height budget respected; symbol page keeps the extended keys.
- Tablet (≥600dp width): 14/15-column rows (digits+`-=`+⌫, QWERTY+`[]\`,
  ASDF+`;'`, `?123`+ZXCVM+`,./`+⌫) — width used intelligently, same identity.
- Page switch: 120ms crossfade + 0.98 scale-in. No bouncing.

### 5.3 Bottom accessory row (always visible)
`[⌨]` `Ctrl` `Alt` `Space(wide)` `Shift` `Enter` — exactly this order.
- `⌨` keyboard toggle: icon-only (no ON/OFF text, no label), permanently the
  far-left control; shown = accent-tinted surface + accent icon, hidden = dim
  icon on transparent; position NEVER moves; contentDescription "Show/Hide
  keyboard". Toggles ONLY the QWERTY body (180ms vertical expand/shrink) —
  both accessory rows always stay available; the terminal reclaims the space.
- `Ctrl` / `Alt`: existing KeyboardState machine kept (tap = one-shot, second
  tap = lock, third = off). Active: accent-tinted fill + accent text + 1dp
  accentDeep outline (state visible through ≥2 cues, not color alone); LOCKED
  adds the small lock dot (existing pattern).
- `Shift`: same machine; one-shot uppercases the next letter (dispatcher
  resolveChar) and produces digit-row shifted symbols; LOCKED = caps.
- `Space`: weighted wide key (≈3× modifier width), quiet look, easy thumb reach.
- `Enter`: accent-filled key (Sapphire surface, dark glyph ⏎) — the single
  strongest visual weight on the deck; the "commit" key should look committed.
- No dedicated Fn key anywhere. No second alphabet/symbol mega-row beyond the
  pages above (brief §19 respected).

### 5.4 What deliberately does NOT change
- KeyboardState consumption discipline (peeks vs one-shot consumption),
  TerminalKeyDispatcher → dispatchKeyEvent path, KeyCharacterMap encoding,
  readControlKey/readAltKey peeks consumed by upstream KeyHandler, hardware
  keyboard support (TerminalView keeps focus), pinch font resize, selection,
  copy/paste, scrollback, session lifecycle, `ModifierKey.FN` removal with
  `readFnKey()` honestly `false` (upstream interface method still implemented).

## 6. Motion (only where it earns its place)

- Tab selection: shape/color crossfade ≤150ms.
- Keyboard show/hide: 180ms vertical expand/shrink (FastOutSlowIn).
- Key press: 80ms scale 0.97 + surface shift; haptic tick on press/modify.
- Modifier activation: 100ms surface/text crossfade.
- Nothing continuous, nothing looping (cursor blinker is upstream and expected).

## 7. Accessibility & performance

- Every icon-only key carries contentDescription; modifier state is encoded by
  fill + outline + dot (not color alone); touch targets ≥40dp tall, arrows 44dp,
  close × 32dp; terminal text contrast (fg on canvas) > 13:1; dim labels ≥4.5:1.
- No blur, no continuous animation, ~50 static composables recomposed per page;
  palette + typeface applied once per process/session attach; per-key pressed
  state stays local (v0.6.2 pattern) so typing never recomposes the deck.

## 8. Implementation checkpoints (each ends in compile + tests)

| step | content |
|------|---------|
| 3.1.1 | Midnight Sapphire tokens (`ui/theme/TerminalTheme.kt`), ANSI palette applier (`terminal/TerminalPalette.kt`), JetBrains Mono packaging, PocketShellApp hook, canvas bg/rounding |
| 3.1.2 | Chrome header + connected session tabs (+ button, hairline cut) |
| 3.1.3 | Terminal typeface wiring, workspace polish |
| 3.1.4 | Top accessory row (Esc/Tab/arrow panel) |
| 3.1.5 | QWERTY body + pages + long-press Fn + KeyLayouts rewrite + layout tests |
| 3.1.6 | Bottom accessory row + keyboard toggle + FN removal + dispatcher cleanup + tests |
| 3.1.7 | Motion, landscape/tablet variants, a11y sweep, versionCode 18 / `0.7.0-m3.1` |

Final: full suite + assembleDebug + payload/docs/delivery chain + device test
checklist (docs/TESTING.md §12) for the human: exact keyboard layout, Ctrl+C/D/L/A/E/W,
Alt+key, Shift+key, arrow/Esc/Tab, toggle behavior, Fn long-press, apk update /
node --version / hermes --version in the guest.

Version: **0.7.0-m3.1, versionCode 18** (in-place update over v0.6.2=16 and
the discarded v0.7.0-ui=17; same cert d96a6f66…8bf659).
