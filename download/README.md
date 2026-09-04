# download/ — delivery masters

Current: v0.7.0-m4.0.3 (payload cut at git tip c7bc60e; the device bug
batch — keyboard everywhere, keyboard pushes everything up, dead "-"
fixed, honest render-stall card, "+" Companion picker — versionCode 27)
- PocketShell-v0.7.0-m4.0.3-debug.apk  sha256 b456160e678b1883ace401903904dd99206e6f4dadfc2241a7b8d671223ce431
  Installs IN PLACE over v0.7.0-m4.0.2 (26), m4.0.1 (25), m4.0 (24) and
  every earlier build (vc16..vc23) — same pinned cert d96a6f66…8bf659.
  App data (Alpine runtime, Kilo, Hermes, packages, Companion logins)
  survives.
  WHAT FIXED (from your m4.0.2 bug report, one by one):
  1. KEYBOARD FOR COMPANIONS TOO: the deck now follows focus — tap the
     Companion page, type: text lands in the page; tap the terminal,
     type: text lands in the shell. No system keyboard while the deck
     is up; deck off → system keyboard allowed for Companion inputs.
  2. KEYBOARD PUSHES EVERYTHING UP: the deck is the bottom-most
     surface; the Companion panel rides ABOVE it — nothing is ever
     under the keyboard anymore.
  3. TOGGLE = WHOLE KEYBOARD GONE → a small Midnight keyboard icon
     floats at the bottom-right corner to bring it back anytime.
  4. "-" (and the whole digit row) works on quick taps now — the
     hold-gesture layer had swallowed short taps; holding still gives
     F-keys. Arrow keys are 12dp longer horizontally.
  5. WHITE CANVAS: WebViews now use the ACTIVITY context (blank-canvas
     source on OEM builds), and a 15s watchdog turns "paints nothing"
     into an honest "Page never rendered" card + WebView version.
     Retry alternates GPU → SOFTWARE rendering (compatibility mode).
  6. "+" opens a Midnight sheet listing every Companion (open tabs
     marked); "Add Companion" goes to the management page.
  · 724 tests green (0 failures; +6 new pins per variant).
  Device gate: docs/TESTING.md §20. Full record: docs/CHANGELOG
  [0.7.0-m4.0.3]; contract amendment: docs/PHASE-4-COMPANION-DESIGN §24.
- PocketShell-v0.7.0-m4.0.3-source.zip sha256 0bcc2312ba0c02b3657c59af90b359dad20093fa03a3e2801b2db972a28d7ea5  (28 MB, 298 files)
- PocketShell-v0.7.0-m4.0.3-source.tar.gz sha256 b1bdd622ae5661687804829fb900ab50b2d95e0ce1e225762953c7840600faa3  (28 MB)
- pocketshell-m2.gitbundle           sha256 919c9bcf569f3d1d0244216d960c0f823196df910f2a53b758dcd1ad3755abdd  (full history; ~26 MB — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md with the §22/§23/§24 amendments — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.2 superseded by m4.0.3; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
