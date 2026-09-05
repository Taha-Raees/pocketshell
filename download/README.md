# download/ — delivery masters

Current: v0.9.1-m5.1.0 (payload cut at the M5.1 optimization tip —
ARM64 PERFORMANCE & ARCHITECTURE OPTIMIZATION, audit-first: the real
architecture was measured and read end-to-end before any change; only
what the evidence supports changed; nothing working broke.
ALREADY SOUND, DELIBERATELY UNTOUCHED: lazy startup (Application.onCreate
touches NO WebView provider, NO Linux init, NO package scanning); one
WebView per tab, never recreated or reloaded on switch; WebView height
FROZEN during sheet drags; background tabs platform-paused on switch and
at Activity pause; terminal scrollback capped (2000 rows), no polling,
blinker stopped on pause, repaint hook unregistered off-screen; one Linux
process per session; honest FGS; keyboard allocations trivial.
(1) F1 — THE MINIMIZED COMPANION IS SILENT: collapsing the sheet used to
leave the ACTIVE tab running JavaScript, timers and layout at full rate
while completely invisible; now collapse -> pause EVERYTHING (reversible,
cookies flushed), raise -> wake ONLY the active tab; no reload, no state
loss. (2) F2 — HOME NO LONGER SPAWNS A GUEST SHELL ON EVERY VISIT: the
command-app probe is a real proot login-shell exec; a 60s freshness
window + an in-flight guard gate the visibility-triggered probe; package
operations still force a fresh probe; a FAILED probe always re-probes.
(3) F3 — BACKGROUND TERMINAL OUTPUT NO LONGER REPAINTS THE SCREEN: the
repaint now happens only when the producer IS the visible session
(background output storms no longer shake the foreground); switches stay
correct by construction. (4) F4 — WEB STATE PERSISTED UNDER MEMORY
PRESSURE: onTrimMemory(>= RUNNING_LOW) flushes cookies while Companion
tabs are alive, strictly gated on the provider already being loaded and
fully contained. TAB RESOURCE POLICY: active = full; background =
platform-paused; minimized = everything paused; tabs live until closed —
saveState/restore + LRU eviction stay retired (the m4.0.11 verdict); no
user state is ever destroyed behind their back. NOT TOUCHED: renderer,
pool, sheet mechanics, tab system, keyboard, themes, terminal
implementation, Linux lifecycle contract, providers, logins.
versionCode 39)
- PocketShell-v0.9.1-m5.1.0-debug.apk  sha256 d8084b49f7af17b55cff643fc41ec0b56484b6830da219c11b4c2f56c3d56482
  Installs IN PLACE over v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
  v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34,
  unannounced intermediate), v0.7.0-m4.0.9 (33), m4.0.8 (32), m4.0.7
  (31), m4.0.6 (30), m4.0.5 (29), m4.0.4 (28), m4.0.3 (27), m4.0.2 (26),
  m4.0.1 (25), m4.0 (24) and every earlier build (vc16..23) — same
  pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes,
  packages, Companion logins, theme choice) survives.
  WHAT THIS BUILD IS: the M5.1 audit's four real fixes — silent
  minimized Companion, no guest-shell probe per Home visit, no
  background-output repaint storms, memory-pressure cookie flush —
  with the audit's "already sound" list deliberately untouched.
  · Full suite green: 752 executions / 0 failures.
  Device measurement gate: docs/TESTING.md §32 — the A–G scenario
  matrix (Home / Terminal / Terminal+Linux / Terminal+Companion /
  multi Companion tabs / multi sessions+Companion / background→return).
  Full record: docs/CHANGELOG [0.9.1-m5.1.0].
- PocketShell-v0.9.1-m5.1.0-source.zip sha256 1dcee490a87299bb493b016a91782144413d7cc8fe0b5498c11847e092ed8dda  (33M, 332 files)
- PocketShell-v0.9.1-m5.1.0-source.tar.gz sha256 f11ff7f3bf5d89f0e3b15ed6876ff0539f8f2f2fd1b3345860cd5f6bb8fa0fa1  (32 MB)
- pocketshell-m2.gitbundle           sha256 083253e4230208d8e8bc80883ec1dd42a1866b7533460a48d7d0b338041cba3e  (full history; ~28M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, docs/PHASE-4-COMPANION-DESIGN.md, and docs/RENDER-RESET-M4.0.9.md §1–§8 (verdict + frozen winner) — honest, no rewrites)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 0e0a2bf8363647aece215f4f0a00b658debb3d42a443b480d564b01cd7d17c0d  (34 pages, ~300 KB — the complete read-only platform/runtime forensic audit: repo provenance, launch-chain sequence diagram, Termux/proot deep dives, targetSdk-28 exec model, /proc attribution, ELF/libc strategy, tool matrix, storage, session model, Companion policy, perf risk register, security, all 30 answers, KEEP/MODIFY/ADD/REPLACE, Runtime 2.0 proposal, roadmap)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.9.0-m5.0.1 superseded by m5.1.0; its
record lives in the bundle history — see docs/CHANGELOG for each
confirmed fix).
