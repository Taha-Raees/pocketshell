# download/ — delivery masters

Current: v0.7.0-m3.4 (git tip fcbe2f2 + delivery page commit; Phase 3.4 — Registry Expansion + System Pages, versionCode 21)
- PocketShell-v0.7.0-m3.4-debug.apk  sha256 2059d1965957e09a05d1bfa313f24c98030e3df23ed2893397b66d44f0d60dd5
  Installs IN PLACE over v0.7.0-m3.3 (versionCode 20), m3.2 (19), m3.1 (18),
  the discarded v0.7.0-ui (17) and v0.6.2 (16) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Hermes, packages) survives.
  SCOPE: one registry data fix + system-page guideline adoption — ZERO
  pipeline/terminal/runtime changes. Login-shell probing,
  verify-before-launch, dedicated guest sessions, the forbidden package
  list, the Phase 3.1 terminal and every runtime behavior are untouched.
  Design contract: docs/PHASE-3.4-DESIGN.md (committed before implementation).
  Phase 3.4 highlights:
  · THE FIX: installed Kilo CLI now appears. Root cause: discovery =
    registry ∩ guest PATH; `kilo` was unregistered so the probe never asked
    about it (honesty contract — no guessing from unknown binaries).
  · Registry expanded (data only): Kilo Code (`kilo`), Gemini CLI
    (`gemini`), Codex (`codex`), Aider (`aider`), Qwen Code (`qwen`) —
    appended after the brief's four (order stability); each still only
    surfaces when the guest's login shell finds its command.
  · Packages screen: not-ready state is inline text with a real
    "Open Diagnostics" link (container deleted); title → "Packages".
  · Settings: whole-row selection targets ≥48dp with correct a11y roles.
  · Diagnostics: uniform sections — System / Linux runtime / Package
    environment — header + plain fact rows, no internal dividers.
  · Home unchanged: tools grid + CLI Apps ▾ render the new entries
    automatically.
  · 648 test executions green (644 baseline + 4 new registry pins).
  Device gate: docs/TESTING.md §15 (kilo appears iff installed and launches;
  Packages/Settings/Diagnostics checks; §12/§13/§14 regressions).
- PocketShell-v0.7.0-m3.4-source.zip sha256 9b73fe8cfa915b476fde3debed1b413820a1447559aebe7f20c251ebd240c4d6  (28 MB, 294 files)
- PocketShell-v0.7.0-m3.4-source.tar.gz sha256 37fd374080fdf74cb84b569434775a19f55acd3555822d0ade004a7c5f74f651  (28 MB)
- pocketshell-m2.gitbundle           sha256 b9c53eef35652c553773860e04c3a7f4c365a6c4a8da1e18ecdda9fdcbaabd20  (full history @ fcbe2f2; ~26 MB — includes the complete milestone history, the discarded UI attempt + rollback records, and all four Phase 3 design contracts — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.3 superseded by Phase 3.4; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
