# download/ — delivery masters

Current: v0.7.0-m4.0.7 (payload cut at git tip edfde05; the health sheet
cracked it — the compat renderer never reached the screen and the creation
recipe was the regression: swap-safe WebView host, creation rolled back to
the proven activity context, glass-first pixel probe, attach kick —
versionCode 31)
- PocketShell-v0.7.0-m4.0.7-debug.apk  sha256 e2c046913fe5b894052f41ab77442163d6937a66101bcc0e0c046923aa358703
  Installs IN PLACE over v0.7.0-m4.0.6 (30), m4.0.5 (29), m4.0.4 (28),
  m4.0.3 (27), m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every earlier
  build (vc16..23) — same pinned cert d96a6f66…8bf659. App data (Alpine
  runtime, Kilo, Hermes, packages, Companion logins) survives.
  WHAT FIXED (your Copy report cracked the case — it showed the page is
  FULLY alive — chat.com: readyState=complete · 761 elements · 62
  interactive · 394 text chars · zero boot errors — while pixels never
  presented; a hydrated app with a black canvas is a PRESENTATION
  failure, and it exposed two real bugs):
  1. THE COMPAT RENDERER WAS NEVER ON SCREEN: AndroidView runs its
     factory once per node, so every silently swapped WebView (first-
     stall compat swap, boot retry, AND plain tab switching) never
     attached — the device showed a DESTROYED view (the dead black
     canvas) while the fresh software-mode view loaded invisibly (why
     pixels read "unknown" forever). The host is now keyed on the view
     instance: every swap reaches the screen, software mode gets its
     first REAL test, tab switching stops showing a stale page.
  2. THE CREATION RECIPE WAS THE REGRESSION: the forced-light
     configuration context (m4.0.5/6) chased a dark-CSS theory your DOM
     evidence refutes — m4.0.4 (plain activity context) still painted
     the cookie banner; m4.0.5/6 (config context) painted NOTHING.
     Creation is rolled back to the proven activity-context recipe; the
     Chrome-like UA stays (Google login fix, orthogonal to painting).
  3. GLASS-FIRST PIXEL PROBE: PixelCopy (the frame as PRESENTED, cropped
     to the keyboard-free top half) is now the primary verdict; software
     readback only as fallback; a hung copy times out after 1.5 s (no
     more "unknown" forever); a throwing fallback never manufactures a
     stall.
  4. ATTACH KICK: the pool loads URLs before the view attaches; some
     Chromium builds never bind the frame sink for such loads (DOM
     alive, pixels never present — your exact signature). If nothing
     painted 3.5 s after first layout, ONE silent reload rebinds the
     load to the live surface. Once per view, never a card.
  · Full suite green: 764 executions / 0 failures (+1 glass-region pin).
  Device gate: docs/TESTING.md §24. Full record: docs/CHANGELOG
  [0.7.0-m4.0.7].
- PocketShell-v0.7.0-m4.0.7-source.zip sha256 11c7472a3779cc15b86be697a5d4faf1c77ccba9f2afee30996805eab8c8d441  (28M, 305 files)
- PocketShell-v0.7.0-m4.0.7-source.tar.gz sha256 d662794e83616a151adc66233af3eb27ec888ebe894c154a002f3b12227324b2  (28 MB)
- pocketshell-m2.gitbundle           sha256 7b6c470265efb00f9c072ed2f64b57cea03f329e7de77a0e5902e594f75ff05d  (full history; ~26M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.6 superseded by m4.0.7; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
