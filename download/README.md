# download/ — delivery masters

Current: v0.7.0-m3.1 (git tip 006e5df + payload commit; Phase 3.1 "Midnight Sapphire" — Terminal Experience Redesign, versionCode 18)
- PocketShell-v0.7.0-m3.1-debug.apk  sha256 76a241ca3ca88a1331ea00e16a0cf898cfe730b471e5796c0076154f5ab0d584
  Installs IN PLACE over v0.6.2 (versionCode 16) AND the discarded v0.7.0-ui (17)
  — same pinned cert d96a6f66…8bf659. App data (Alpine runtime, Hermes) survives.
  SCOPE: Terminal screen ONLY — Home/Explore/Packages/Settings/Diagnostics and
  the whole M2.6 runtime are untouched.
  Phase 3.1 highlights (design contract: docs/PHASE-3.1-DESIGN.md):
  · Midnight Sapphire blue-dark identity — canvas #080F1D, never pure black;
    ONE accent (Sapphire #7FA3EF); real 16-color ANSI palette (OSC still wins).
  · JetBrains Mono NL terminal typeface (OFL 1.1, no-ligature build —
    character-exact output); Sapphire block cursor.
  · Editor-style session tabs: rounded-TOP tabs, recessed inactive tabs, the
    active tab is canvas-colored with a Sapphire top hairline and cuts the
    strip's bottom hairline — it opens into the terminal workspace.
  · Keyboard rebuilt from scratch (NO system IME): TOP Esc Tab + grouped
    arrows · MIDDLE PocketShell QWERTY (terminal punctuation row, symbols
    with INS/DEL/HOME/END/PGUP/PGDN) · BOTTOM [⌨] Ctrl Alt Space Shift Enter.
  · Dedicated FN key REMOVED: F1–F10 = number-row long-press (hold → bubble →
    release), F11/F12 on the tablet -/= long-press.
  · Keyboard toggle collapses only the QWERTY body; both accessory rows stay;
    landscape 4 rows; tablet wide rows. Dispatch pipeline unchanged —
    Ctrl+C/D/L/A/E/W, Alt, Shift all remain real terminal input.
  Device gate: docs/TESTING.md §12 (visual sweep, exact keyboard layout,
  modifier combos, Fn long-press, apk update / node --version / hermes --version).
- PocketShell-v0.7.0-m3.1-source.zip sha256 733602ee66156a54795377ca04dc9ff8123cd1c3dcc2c494be4f92ed5e3d3570  (28 MB, 279 files)
- PocketShell-v0.7.0-m3.1-source.tar.gz sha256 310612cfd540445a7dd57a6d2a1d4646bb50172a5fcda7695e5eae5e4c2ee0e3  (28 MB)
- pocketshell-m2.gitbundle           sha256 2ca125b9580e7396b16850f97a93f2ee5f4dd57ef56369904039311bef2c2850  (full history @ 006e5df; ~26 MB — includes the complete milestone history AND the discarded UI attempt + rollback records, honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.6.2 superseded by Phase 3.1; its records live in
the bundle history — see docs/CHANGELOG for each confirmed fix).
