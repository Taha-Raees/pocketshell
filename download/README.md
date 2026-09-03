# download/ — delivery masters

Current: v0.7.0-m3.3 (git tip 9803940 + delivery page commit; Phase 3.3 — Home & System UI Redesign, versionCode 20)
- PocketShell-v0.7.0-m3.3-debug.apk  sha256 a7acd843ff85d691ad50c548d566663f3919a3de2e963e17087a81828aa0dca0
  Installs IN PLACE over v0.7.0-m3.2 (versionCode 19), v0.7.0-m3.1 (18), the
  discarded v0.7.0-ui (17) and v0.6.2 (16) — same pinned cert d96a6f66…8bf659.
  App data (Alpine runtime, Hermes, packages) survives.
  SCOPE: visual architecture + Home interaction cleanup ONLY — ZERO backend
  changes. Command-app discovery, login-shell probing, verify-before-launch,
  dedicated guest sessions, the forbidden package list, the Phase 3.1
  terminal (chrome, tabs, keyboard, palette, PTY pipeline), the runtime, the
  Linux environment, the package manager and installed packages are
  untouched. Design contract: docs/PHASE-3.3-DESIGN.md (committed before
  implementation).
  Phase 3.3 highlights:
  · Surfaces only for OBJECTS: content sits directly on the Midnight canvas,
    separated by spacing, section labels, hairline dividers and tone steps.
    No cards for text groupings, no cards in cards, radius ≤ 16dp, no
    decorative borders, no ghost placeholders.
  · ONE CLI control: quiet "CLI Apps ▾" in the header area (only when apps
    exist) opening a compact Midnight launcher menu — monogram plate + name +
    the command dim and secondary; row taps run the exact Phase 3.2
    verify-then-launch pipeline. Replaces every floating CLI affordance.
  · Foundations, flatter: Terminal and Linux are borderless tone-step
    surfaces, 14dp radius; truncating copy replaced ("Native shell",
    "Alpine · ready"); running count is plain mono text.
  · "Your tools": command apps as icon + label launcher entries (52dp
    borderless monogram plates, NOT cards). Empty state = three quiet lines
    ("No CLI apps yet." + one sentence + the page's only Explore packages
    link); with apps present a single quiet "Packages" footer link replaces
    it — exactly ONE packages affordance in every state.
  · Sessions flat: dot + label + mono id between hairline dividers; pressed
    row is the only surface; green only for live processes.
  · FAB single-purpose: "create a new session" ONLY (New Terminal / New
    Linux session), text-only chips — no icon circles, no logos, no command
    apps in the menu.
  · Other pages reviewed: Settings/Diagnostics already flat (unchanged);
    Packages cards = real objects (allowed, unchanged).
  · 644 test executions green (baselines + CommandAppsTest untouched).
  Device gate: docs/TESTING.md §14 (no-boxes sweep; CLI menu honesty — only
  guest-confirmed apps; empty-state lightness; FAB purpose; §12/§13
  regressions).
- PocketShell-v0.7.0-m3.3-source.zip sha256 cd554f97e300f3d859c2242919760e1750fe22c935a39e81118a17a312be4de7  (28 MB, 291 files)
- PocketShell-v0.7.0-m3.3-source.tar.gz sha256 d5e11ae7b905622cea1ab4cafe78cd0233f75e024fd9b7a73aff78125b651e9d  (28 MB)
- pocketshell-m2.gitbundle           sha256 108a986d24e13f0b23a810d75a57028be7a6d256b878407458bd244d4ea81611  (full history @ 9803940; ~26 MB — includes the complete milestone history, the discarded UI attempt + rollback records, and all three Phase 3 design contracts — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.2 superseded by Phase 3.3; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
