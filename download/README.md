# download/ — delivery masters

Current: v0.8.0-m4.0.12 (payload cut at git tip 1e59c00 — COMPANION
FINALIZATION, a surgical cleanup-and-polish pass with the working
renderer FROZEN and byte-identical to m4.0.11: (1) ALL baseline
diagnostics retired completely — the ⓘ chip, the launch path, the
harness Activity + BaselineMatrix DELETED, code + manifest; the
Companion shows only the real website; (2) refresh on the new ↻ strip
glyph — tap = plain reload of the ACTIVE tab only; long-press = HARD
refresh, the freshest possible reload that is NOT a data reset (one
transient LOAD_NO_CACHE restored on page finish; cookies, logins and
other tabs preserved; haptic + "Hard reloading…" toast); (3) drag
handle: invisible full-width touch zone 28→40dp, visible bar unchanged
36×4dp; (4) ONE PocketShell keyboard everywhere — the deck moved to the
app root over every screen, the system IME permanently blocked
(FLAG_ALT_FOCUSABLE_IM), universal dispatch fallback serves Compose
text fields, WebView-input focus auto-opens the deck, bottom-right [⌨]
toggle on every screen, Companion collapse restores terminal focus;
(5) untouched: renderer recipe, sheet, drag mechanics, remembered
height, tab system, tab state, destination storage, navigation,
provider management. Frozen configuration pinned by
CompanionRenderContractTest — winner pinned by VALUE. Failure surfaces:
exactly "Page didn't load" and "Page renderer crashed" — nothing else.
versionCode 36)
- PocketShell-v0.8.0-m4.0.12-debug.apk  sha256 679dff59a5290260dbf543209bf0eb5c9df7b1cc062b0d743c69f50c7e62f990
  Installs IN PLACE over v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34,
  unannounced intermediate), v0.7.0-m4.0.9 (33), m4.0.8 (32), m4.0.7
  (31), m4.0.6 (30), m4.0.5 (29), m4.0.4 (28), m4.0.3 (27), m4.0.2 (26),
  m4.0.1 (25), m4.0 (24) and every earlier build (vc16..23) — same
  pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes,
  packages, Companion logins) survives.
  WHAT THIS BUILD IS: the finalization pass the Phase 4 brief asked
  for — diagnostics gone, refresh + hard refresh, easier drag handle,
  ONE keyboard everywhere, no Android keyboard, no second layout, and
  NOT ONE LINE changed in the working renderer.
  · Full suite green: 750 executions / 0 failures (contract re-pinned
    by value + refresh-layer pins; harness test retired with the code).
  Device gate: docs/TESTING.md §29 — the finalization gates
  (rendering re-proof, refresh normal + hard, drag handle, universal
  keyboard, regression ladder).
  Sweep evidence + frozen winner: docs/RENDER-RESET-M4.0.9.md §1–§8.
  Full record: docs/CHANGELOG [0.8.0-m4.0.12].
- PocketShell-v0.8.0-m4.0.12-source.zip sha256 c2bf59801d8d10b3063b1603ac0edd8b6f3f88125d43f7727ed03abb815ce28e  (32M, 330 files)
- PocketShell-v0.8.0-m4.0.12-source.tar.gz sha256 b42c2dbc8ad2d12f813aec81db6a0150613a98a8f4457d3c0891bc4d437e7bbc  (32 MB)
- pocketshell-m2.gitbundle           sha256 86648801410362bb8889a8cadf865a0aaedf1e2e73fcebdbae160e4eda107b28  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)

Archive provenance: sandbox reset #10 wiped the binaries; toolchain
reinstalled, APK rebuilt BYTE-IDENTICAL (pin above unchanged, cert
d96a6f66…8bf659 re-verified), archives re-cut at the recovery tip
(tree carries the final page/README/worklog commits that postdate the
original cut at 1e59c00 — code state identical, hence new archive
shas; file count still 330).

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.8.0-m4.0.11 superseded by m4.0.12; its
record lives in the bundle history — see docs/CHANGELOG for each
confirmed fix).
