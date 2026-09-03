# download/ — delivery masters

Current: v0.7.0-m3.2 (git tip 0efd645 + delivery page commit; Phase 3.2 — Home / OS Launcher + Command Apps, versionCode 19)
- PocketShell-v0.7.0-m3.2-debug.apk  sha256 1138ba4df9e702d2a8b650547e583dd4e1524a6b5bb8d186533328b3372b5f16
  Installs IN PLACE over v0.7.0-m3.1 (versionCode 18), the discarded v0.7.0-ui (17)
  and v0.6.2 (16) — same pinned cert d96a6f66…8bf659. App data (Alpine runtime,
  Hermes, packages) survives.
  SCOPE: the HOME screen ONLY + the command-launchable app architecture — the
  Phase 3.1 terminal (chrome, tabs, keyboard, palette, PTY pipeline), the
  runtime, the Linux environment, the package manager and installed packages
  are untouched. Design contract: docs/PHASE-3.2-DESIGN.md (committed before
  implementation).
  Phase 3.2 highlights:
  · Home = the launcher of a Linux-centric environment (identity → the two
    foundations → command apps → sessions → one floating quick-action control);
    no bottom navigation bar, not a card dashboard.
  · PACKAGES ≠ APPS: the "Installed CLI Apps" section is GONE — git, nano,
    python, node, npm, gcc, g++, htop, vim can never become launcher tiles
    (test-pinned). New extensible command-app registry: Hermes Agent,
    OpenCode, Claude Code, ZCode.
  · Guest-confirmed availability: ONE batched login-shell probe (`sh -lc`)
    asking exactly "would a fresh guest login shell find this command?" —
    the same PATH semantics the user's typing sees (uv launchers reachable).
    Probe failure = "could not be checked" + last real list, never a fake
    "no apps" (v0.4.4 honesty rule).
  · Tap-to-launch: verify-then-launch into a NEW dedicated guest session
    whose PTY receives the command — what the launcher does is exactly what
    typing would do; exiting the app returns to the guest prompt.
  · Midnight Sapphire launcher: Terminal tile in the exact canvas color
    #080F1D, Linux tile #101B30 with an honest state line (READY enters the
    guest; other states route to Diagnostics), drawn brand/terminal/mountain
    marks, ONE Sapphire accent; no pure black, no gradients on this page.
  · Launcher grid (3/4/6 responsive columns, 720dp cap on tablets), honest
    empty state ("Your tools will appear here" + Explore packages), compact
    session continuation area, custom floating quick-action system (real
    actions only; extensible action list), edge-to-edge + status-bar icon
    coordination.
  · 644 test executions green (628 baseline + 8 new CommandApps invariants).
  Device gate: docs/TESTING.md §13 (no packages on Home; Hermes iff available;
  tap-launch flow; FAB actions; phone + tablet; Phase 3.1 §12 must still pass).
- PocketShell-v0.7.0-m3.2-source.zip sha256 6a10fd5e47f2ac9b94340e2fcb3dacf32b251ca581c0123ebb37765fdd050903  (28 MB, 287 files)
- PocketShell-v0.7.0-m3.2-source.tar.gz sha256 993046439838069be54551d72b1ed557e4b0b6f748f276ba3afb21c98355fce2  (28 MB)
- pocketshell-m2.gitbundle           sha256 1eaebb7e34dc99a687d7db8efd4df3cc4637f6972e4a77ec3f628d614f6f10ee  (full history @ 0efd645; ~26 MB — includes the complete milestone history, the discarded UI attempt + rollback records, and both Phase 3 design contracts — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.1 superseded by Phase 3.2; its records live
in the bundle history — see docs/CHANGELOG for each confirmed fix).
