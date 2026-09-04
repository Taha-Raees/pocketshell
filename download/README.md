# download/ — delivery masters

Current: v0.7.0-m4.0.2 (payload cut at git tip of the m4.0.2 chain; honest
Companion failure surfaces — the canvas is never mysteriously white,
versionCode 26)
- PocketShell-v0.7.0-m4.0.2-debug.apk  sha256 d4b04acc3919fc6da9bd72a37590bb2587e00c900cc83202619dec0158605bb2
  Installs IN PLACE over v0.7.0-m4.0.1 (25), m4.0 (24) and every earlier
  build (vc16..vc23) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins) survives.
  SCOPE: failure reporting of the Phase 4 Companion only — no
  feature/storage/contract change; behavior on healthy devices is
  identical to m4.0/m4.0.1.
  DEVICE FINDING (2026-09-05): after the m4.0.1 startup fix, the
  ChatGPT Companion tab rendered a PURE WHITE canvas — no page, no
  error, no explanation. The white came from the WebView content side
  (page can't load over network/VPN · WebView build too old after
  Samsung's rollback · or the updated build rendering blank).
  THE FIX: main-frame load failures render an in-canvas Midnight card —
  "Page didn't load" + the REAL net::ERR string + the installed Android
  System WebView version + the hint that matters + Retry. A dead page
  renderer (whose DEFAULT Android behavior kills the app) now destroys
  only the crashed view and reports "Page renderer crashed" — the app
  survives. The "Companion unavailable" notice shows the WebView
  version too. Retry rebuilds the tab; a successful navigation clears
  the failure.
  HONEST BOUNDARY: a page that loads but renders blank from an ancient
  WebView's failing JavaScript fires NO error — use the version line +
  a static-site Companion (example.com) to identify that case.
  · 712 tests green (0 failures; +4 new pins on the failure model).
  Device gate: docs/TESTING.md §19. Full record: docs/CHANGELOG
  [0.7.0-m4.0.2]; contract amendment: docs/PHASE-4-COMPANION-DESIGN §23.
- PocketShell-v0.7.0-m4.0.2-source.zip sha256 8247c6867121bfe6a18ea30f027b06d3f5ee206d71b1deeed718fc6c67b6f537  (28 MB, 296 files)
- PocketShell-v0.7.0-m4.0.2-source.tar.gz sha256 2d160f1095ff1b1589a6ce16c17e4d23af0323dfebec0e3e56dc19b7064f7ab2  (28 MB)
- pocketshell-m2.gitbundle           sha256 a82f9fcb547eb21b6441a116db43783d8732c3381f1b8c333f703b0ceffed091  (full history; ~26 MB — includes the complete milestone history, all six Phase 3 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md with the §22/§23 hotfix amendments — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0 superseded by the m4.0.1 hotfix;
its records live in the bundle history — see docs/CHANGELOG for each
confirmed fix).
