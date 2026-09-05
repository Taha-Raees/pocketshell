# download/ — delivery masters

Current: v0.9.0-m5.0.0 (payload cut at the Phase 5 tip — UI &
INTERACTION POLISH, a refinement phase: no redesign, no new features,
the working Companion implementation untouched architecturally.
(1) COMPANION DRAG BAR: visible bar 2x wider (36→72dp, still 4dp slim)
in the same 40dp invisible full-width touch zone; the zone stays the
layer column's first child so it can never hide behind tabs or content;
a single TAP anywhere on the bar IMMEDIATELY minimizes the raised sheet
at ANY height (25/50/80/near-full); restore is drag-up only — still no
floating button. (2) FREE POSITIONING: the HALF/FULL snap windows are
RETIRED — height changes ONLY by dragging and a release settles exactly
where the user leaves it, any fraction; the collapse threshold (drag
near the bar → minimized) is the only special release. (3) HOME
DECLUTTERED: the floating action button REMOVED (code deleted) —
session creation lives in the Terminal's integrated "+"; the duplicate
CLI Apps dropdown RETIRED — the "Your tools" grid is the one path; no
functionality removed. (4) COMPACT CHROME: terminal strip 44→40dp, tab
min width 96→84dp, tab padding 12→10dp, gaps 6→4dp, terminal "+" now
the quiet integrated glyph (no circle plate); app-wide padding trim
(Home, MidnightPage kit, Packages one-row search, Settings) with touch
targets kept ≥44–48dp. (5) KEYBOARD TOGGLE corner-anchored: 12dp from
the right edge / 8dp above the gesture inset, on every screen; the ONE
keyboard system otherwise untouched (no second layout, no Android IME,
same dispatch chain). (6) LIGHT THEME FULL: TerminalTheme tokens are
snapshot state — Midnight (dark, historical values) ↔ Daylight
Sapphire (light); System/Light/Dark/AMOLED all live; switching is
immediate (token sync before first read — no flash) and persisted
(DataStore); the terminal CONTENT canvas stays Midnight in every theme
(a terminal is a dark professional surface) and websites keep owning
their appearance — NO theme injection into Companion pages, ever; new
pinned onCanvas tokens keep canvas-surface text readable in both
themes; status bar follows the theme; AMOLED preserved; dynamic color
intact. (7) Packages + Settings polish: one-row search, grouped
Settings (Appearance / Terminal / Companion), honest apk-backed states
preserved verbatim. NOT TOUCHED: renderer, sheet mechanics, tab system,
pool, refresh/hard-refresh, session persistence, keyboard internals.
versionCode 37)
- PocketShell-v0.9.0-m5.0.0-debug.apk  sha256 b6b9d121c98a7367de6ee9d767dcab99ef438920f233aa21da9dde8edca3efc5
  Installs IN PLACE over v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
  v0.8.0-m4.1.0 (34, unannounced intermediate), v0.7.0-m4.0.9 (33),
  m4.0.8 (32), m4.0.7 (31), m4.0.6 (30), m4.0.5 (29), m4.0.4 (28),
  m4.0.3 (27), m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every earlier
  build (vc16..23) — same pinned cert d96a6f66…8bf659. App data (Alpine
  runtime, Kilo, Hermes, packages, Companion logins, theme choice)
  survives.
  WHAT THIS BUILD IS: the Phase 5 polish the brief asked for — drag bar
  2× with tap-to-minimize, free positioning with no snap points, no
  Home FAB, no duplicate CLI Apps entry, compact tabs and chrome,
  corner-anchored keyboard toggle, ONE keyboard everywhere (unchanged),
  and a complete application-wide Light Theme — with websites and the
  terminal content canvas left in charge of their own appearance.
  · Full suite green: 750 executions / 0 failures (height-math pins
    reworked for free positioning).
  Device gate: docs/TESTING.md §30 — the Phase 5 gates (drag-bar
  behavior matrix, free positioning, Home/FAB/CLI removal, toggle
  corner, Light/Dark/System/AMOLED sweep — websites NOT re-themed,
  Packages/Settings, keyboard regression ladder).
  Full record: docs/CHANGELOG [0.9.0-m5.0.0].
- PocketShell-v0.9.0-m5.0.0-source.zip sha256 82a435ad5a61dd6d03ff7ad86c1788ef8543b0cff753f57230c68f0efeeba896  (32M, 330 files)
- PocketShell-v0.9.0-m5.0.0-source.tar.gz sha256 994c8ef2df8baf73f0598d95255ebd3cc88ea071249e9accd0309e9bf5840051  (32 MB)
- pocketshell-m2.gitbundle           sha256 643fd905d74eb4311ccb5f14116ac4d139f55e9bc94ee33c1002215fa83fb5f3  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.8.0-m4.0.12 superseded by m5.0.0; its
record lives in the bundle history — see docs/CHANGELOG for each
confirmed fix).
