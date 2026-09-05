# download/ — delivery masters

Current: v0.7.0-m4.0.9 (payload cut at git tip cd85408; COMPANION RENDERING
RESET — the investigation build: the Companion render path is FROZEN, zero
symptom patches; ships the mandated minimal baseline WebView harness
inside PocketShell — plain Activity → FrameLayout → one WebView, one
variable at a time against the Companion suspects, gates A–D — so the
device run names the exact failing layer; versionCode 33)
- PocketShell-v0.7.0-m4.0.9-debug.apk  sha256 2c83af33efc335fdb2649430b3bcbe3ce87c2e29c8ba3a5d6c72e57c0840e2c9
  Installs IN PLACE over v0.7.0-m4.0.8 (32), m4.0.7 (31), m4.0.6 (30),
  m4.0.5 (29), m4.0.4 (28), m4.0.3 (27), m4.0.2 (26), m4.0.1 (25),
  m4.0 (24) and every earlier build (vc16..23) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes, packages,
  Companion logins) survives.
  WHAT THIS BUILD IS: not a fix claim — the control experiment. Open a
  Companion → ⓘ Page health → "Render baseline (diagnostic)":
  1. BASELINE = plain WebView(activity) in a plain layout, JS + DOM
     storage only, URL loaded after first layout — Android defaults, no
     pool/Compose/UA/context tricks.
  2. MODE ▸ switches ONE variable at a time: +CHROME UA, +FORCED LIGHT
     CTX, +MIDNIGHT BG, +LOAD BEFORE ATTACH, +WIDE VIEWPORT — the first
     mode that blanks names the failing layer.
  3. URL ▸ cycles example.com → wikipedia.org → chatgpt.com → chat.z.ai
     (gates A–D).
  4. INSPECT reads the page's own viewport (innerWidth/innerHeight,
     visualViewport, title — read-only, on demand); COPY hands the whole
     status over for the chat.
  Success = a real website visibly displays its actual UI and is usable
  by touch and keyboard. DOM counts and "pixels painted" are not success.
  Decision rule + full A/B table: docs/RENDER-RESET-M4.0.9.md.
  · Full suite green: 788 executions / 0 failures (+8 pins).
  Device gate: docs/TESTING.md §26. Full record: docs/CHANGELOG
  [0.7.0-m4.0.9].
- PocketShell-v0.7.0-m4.0.9-source.zip sha256 1fbcf3940992a2da9f6de7ecb115c5c0cf3afcf272ddb0044704b080ca3e0555  (28M, 312 files)
- PocketShell-v0.7.0-m4.0.9-source.tar.gz sha256 3d90706b4041dbfe98de22a076f5f8a4e9df7bc44a90ae302c8d97dea90459ce  (28 MB)
- pocketshell-m2.gitbundle           sha256 8f3056378dbe26f5e54c83a62c2558c2ef295ed9dd407c8f3563bd2bfcb5c848  (full history; ~26M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.8 superseded by m4.0.9; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
