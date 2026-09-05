# download/ — delivery masters

Current: v0.9.0-m5.0.1 (payload cut at the M5.0-final-correction tip —
THE WORKSPACE BAR, a surgical pass: no redesign, no new features, the
working Companion implementation untouched architecturally.
(1) WORKSPACE HEADER REMOVED: the terminal screen's large top title row
(back + live session title) is GONE — the active session's name already
lives in its tab; the workspace starts directly under the Android status
area (the strip consumes the status-bar inset itself). (2) BACK LIVES IN
THE TAB BAR: a compact integrated glyph at the far LEFT of the workspace
bar — `← [Tab] [Tab] [Tab] +` — aligned with the tabs, not a header-
sized button in its own row. (3) COMPACT IDE TABS, BOTH STRIPS: strip
34dp (was 40); active tab 34 / inactive 26 (was 40/30 — no more
oversized heavy active tab); 2dp gaps (was 4); 8dp horizontal tab
padding (was 10); tab width 64–136dp (was 84–160 — more tabs fit on
screen); 6dp corner radius (was 10, shared token); the active indicator
is a subtle 2dp hairline (was 2.5). The editor language is untouched —
active tab still opens into the canvas and cuts the hairline, quiet
separators, no pill outlines. (4) TAB TEXT: long titles truncate with an
ellipsis, the close button always stays reachable, both strips scroll
horizontally and the ACTIVE tab is always scrolled back into view when
a switch lands off-screen. (5) COMPANION NEAR-FULL DRAG SURFACE: below
90% height NOTHING changed — the dedicated drag bar is the only sheet
drag control and the tab bar behaves normally (taps switch tabs, close/
+/refresh work, horizontal scroll scrolls). At/above 90% of the
available height (TAB_BAR_DRAG_THRESHOLD = 0.90, pure-pinned) the TAB
STRIP also drags the sheet vertically — gated behind the vertical touch
slop so tab taps, close, + and refresh are NEVER mistaken for drags,
and the strip NEVER minimizes on touch (tap-to-minimize stays the
dedicated bar's exclusive duty). Drag math shared verbatim by both
surfaces; free positioning preserved (no snap points, ever); the
surface stays attached while a drag is in flight so a drag crossing
below the threshold is not cut mid-gesture. NOT TOUCHED: renderer,
sheet mechanics, tab system, pool, refresh/hard-refresh, session
persistence, keyboard internals, themes.
versionCode 38)
- PocketShell-v0.9.0-m5.0.1-debug.apk  sha256 9d08e75192b260634ec4515a19bd380c56c77b34d6c804e2621e3d067ae222e3
  Installs IN PLACE over v0.9.0-m5.0.0 (37), v0.8.0-m4.0.12 (36),
  v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34, unannounced intermediate),
  v0.7.0-m4.0.9 (33), m4.0.8 (32), m4.0.7 (31), m4.0.6 (30), m4.0.5 (29),
  m4.0.4 (28), m4.0.3 (27), m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every
  earlier build (vc16..23) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins, theme
  choice) survives.
  WHAT THIS BUILD IS: the field report's correction list, executed
  exactly — header gone, back in the bar, dense IDE tabs, and the
  ≥90% tab-strip drag surface with tap discipline.
  · Full suite green: 752 executions / 0 failures (new pure pins for
    the 90% drag-surface gate).
  Device gate: docs/TESTING.md §31 — the m5.0.1 gates (workspace-bar
  layout, compact tabs, near-full strip-drag matrix, no-regression
  ladder).
  Full record: docs/CHANGELOG [0.9.0-m5.0.1].
- PocketShell-v0.9.0-m5.0.1-source.zip sha256 32f43824711faaa0eb8cb6950e51e72b30ffd3b7043e1ec58090fb3e1aef71cf  (33M, 331 files)
- PocketShell-v0.9.0-m5.0.1-source.tar.gz sha256 78d69db74588693ee3209c7c003d66b7de45fa8be3d946e572ea18972503d832  (32 MB)
- pocketshell-m2.gitbundle           sha256 a0f9f8b66eda686781d1204ce6c6f89c3c166b10e52be7d5ef4a571530a401e4  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.9.0-m5.0.0 superseded by m5.0.1; its
record lives in the bundle history — see docs/CHANGELOG for each
confirmed fix).
