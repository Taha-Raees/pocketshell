# download/ — delivery masters

Current: v0.7.0-m4.0.1 (payload cut at git tip 8244772; HOTFIX — startup
decoupled from WebView provider health, versionCode 25)
- PocketShell-v0.7.0-m4.0.1-debug.apk  sha256 c9fc0cc3c2bfbdca98fa1b948bec2bec7a1fd9d60f3cdd77a348414c23ccd678
  Installs IN PLACE over v0.7.0-m4.0 (versionCode 24) and every earlier
  build (vc16..vc23) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins) survives.
  SCOPE: failure-path hardening of the Phase 4 Companion only — no
  feature/UI/contract change; the Companion behaves exactly as shipped
  in m4.0 on devices with a healthy WebView.
  THE REPORTED BUG (device 2026-09-05): m4.0 crashed on EVERY launch —
  Samsung Device Care blamed the freshly updated Android System WebView
  ("Uninstall WebView updates?"). Root cause: m4.0's Application.onCreate
  called CookieManager.getInstance(), which synchronously LOADS the
  entire WebView provider before any UI; a provider that crashes at init
  (seen on Samsung+microG after a WebView update) killed every start,
  although the Companion was never opened.
  THE FIX: Application startup is WebView-free; cookie configuration and
  WebView creation happen lazily at first Companion use and are guarded —
  a broken provider now degrades ONLY the Companion surface (an honest
  "Companion unavailable" notice) while the terminal, Home, packages,
  Diagnostics and Settings keep working. No path can crash the process
  on a broken provider. Logins/data unchanged.
  · 704 tests green (0 failures, both modules × both variants).
  Device gate: docs/TESTING.md §18. Full record: docs/CHANGELOG
  [0.7.0-m4.0.1]; contract amendment: docs/PHASE-4-COMPANION-DESIGN §22.
- PocketShell-v0.7.0-m4.0.1-source.zip sha256 7435127f3ecf4697cde67e45eb4c7948a315c85cd2223570ac877cb83df50b37  (28 MB, 317 files)
- PocketShell-v0.7.0-m4.0.1-source.tar.gz sha256 dabbdb9692592a8ff3195a6acafade79b5a9e4d16fbc3044a330c091e2c4b213  (28 MB)
- pocketshell-m2.gitbundle           sha256 9020f9da0e25758d62520fb0e37b1a8e9b70cdae00c86a99723749f990fab205  (full history @ 8244772; ~26 MB — includes the complete milestone history, all six Phase 3 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0 superseded by the m4.0.1 hotfix;
its records live in the bundle history — see docs/CHANGELOG for each
confirmed fix).
