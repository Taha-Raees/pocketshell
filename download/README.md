# download/ — delivery masters

Current: v0.8.0-m4.0.11 (payload cut at git tip a391b5d — sandbox-reset
REBUILD of the vc35 delivery: the code state is identical to fix tip
f58f02d (zero app/ changes since; only logs/delivery records differ),
the APK came back BYTE-IDENTICAL (same sha as the original cut, cert
pin d96a6f66…8bf659 re-verified), and the source zip/tgz/bundle were
re-cut from the current tip (two delivery scripts + newer worklog
entries are the only content delta — 334 files vs 332). REPLACE RENDERER
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
- PocketShell-v0.8.0-m4.0.11-source.zip sha256 a10dbbe47f87dd5d643ba563da348bb0e2819edc50a493ecfb20f6b588795d2c  (32M, 334 files)
- PocketShell-v0.8.0-m4.0.11-source.tar.gz sha256 1277d21cccd39df7f05f3f9450258d813503a9f5d7dc2b8c7b30563ade6389be  (32 MB)
- pocketshell-m2.gitbundle           sha256 d617970ca306482281f013722566666c83ad0c5e8f81957fc9042a1b9c218150  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.9 and the unannounced v0.8.0-m4.1.0
intermediate superseded by m4.0.11; their records live in the bundle
history — see docs/CHANGELOG for each confirmed fix).
