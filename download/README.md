# download/ — delivery masters

Current: v0.8.0-m4.0.11 (payload cut at git tip f58f02d; REPLACE RENDERER
ONLY — the closing iteration: the winner of the rendering-reset sweep is
FROZEN (BASELINE — zero deltas from Android defaults; device evidence:
baseline rendered all four gate sites per your recording, +CHROME UA
rendered chat.z.ai completely, +MIDNIGHT BG rendered chatgpt.com
completely) and the existing Companion sheet keeps its drag handle,
remembered height, tab strip, tab system and destinations — ONLY the tab
content renderer is the exact copied baseline implementation
(WebView(realActivity), JS + DOM storage, plain FrameLayout, attach →
first layout → loadUrl), diagnostics stripped from the canvas; harness
stays behind the ⓘ chip; versionCode 35)
- PocketShell-v0.8.0-m4.0.11-debug.apk  sha256 1afcc7dbe69971e6136c64d033915b56269c7ff2b4e615fb78d22b3011a46335
  Installs IN PLACE over v0.8.0-m4.1.0 (34, unannounced intermediate),
  v0.7.0-m4.0.9 (33), m4.0.8 (32), m4.0.7 (31), m4.0.6 (30), m4.0.5 (29),
  m4.0.4 (28), m4.0.3 (27), m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every
  earlier build (vc16..23) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins) survives.
  WHAT THIS BUILD IS: existing Companion sheet + existing tabs + the
  proven baseline renderer = final Companion. The frozen configuration
  is pinned by CompanionRenderContractTest (settings surface = JS + DOM
  storage + two §16 denials, nothing else; create → attach → first
  layout → loadUrl; plain FrameLayout; real Activity; empty
  diagnostics-in-render-path). Failure surfaces: exactly "Page didn't
  load" and "Page renderer crashed" — nothing else exists.
  · Full suite green: 758 executions / 0 failures (+7 pins).
  Device gate: docs/TESTING.md §28 — Gates A–H on the REAL Companion.
  Sweep table + copy map: docs/RENDER-RESET-M4.0.9.md §8. Full record:
  docs/CHANGELOG [0.8.0-m4.0.11].
- PocketShell-v0.8.0-m4.0.11-source.zip sha256 631f1858627e81fbc01516707f99693161c8fe8b6e007b1303e842334e420867  (32M, 332 files)
- PocketShell-v0.8.0-m4.0.11-source.tar.gz sha256 72fdb83e2b882a75da2a92aef5f1cc9a6b891ae504210d0076a1dd3890968c22  (32 MB)
- pocketshell-m2.gitbundle           sha256 d2b419c512d35c864e168946b2cecb3797669aaa4cbf1b564fcca89fdea0f1d5  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.9 and the unannounced v0.8.0-m4.1.0
intermediate superseded by m4.0.11; their records live in the bundle
history — see docs/CHANGELOG for each confirmed fix).
