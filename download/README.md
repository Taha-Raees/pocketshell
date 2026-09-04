# download/ — delivery masters

Current: v0.7.0-m3.5 (payload cut at git tip 7a8a136; Phase 3.5 — Command
Launch Fix + System Pages Join Midnight, versionCode 22)
- PocketShell-v0.7.0-m3.5-debug.apk  sha256 c8d0effb3fb1ff81feeb9deb2822f9c8e15630e1ca584ce80dc07f1c06d91d0e
  Installs IN PLACE over v0.7.0-m3.4 (versionCode 21), m3.3 (20), m3.2 (19),
  m3.1 (18), the discarded v0.7.0-ui (17) and v0.6.2 (16) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes, packages) survives.
  SCOPE: one launch-transport fix + three pages' presentation — ZERO
  probing/runtime/terminal changes. Verify-before-launch, login-shell
  probing, dedicated guest sessions, the forbidden package list, the
  Phase 3.1 terminal and every runtime behavior are untouched.
  Design contract: docs/PHASE-3.5-DESIGN.md (committed before implementation).
  Phase 3.5 highlights:
  · THE FIX: tapping an app tile launches the app. Root cause: the launch
    command was PTY-written right after session construction, but
    TerminalSession forks the process only when the view renders the
    session, and write() silently drops bytes while mShellPid == 0 — every
    tile opened a plain shell. Fixed by argv delivery: the guest login
    shell runs `sh -l -c "kilo; exec sh -l"` — deterministic,
    timing-independent, still exactly what typing would do; exiting the
    app returns to the guest prompt. ONE generic path for all registry +
    catalog apps — the registry stays pure data, nothing per-app.
  · Diagnostics / Packages / Settings adopt Midnight Sapphire via the
    shared page kit (ui/system/MidnightPage.kt): Midnight canvas, mono
    titles + section labels, hairline dividers, mono fact values with
    honest state coloring; filled-Sapphire vs quiet-hairline actions
    (destructive tone only on Remove runtime / Uninstall); Packages gets
    the chrome search plate with Sapphire focus, mono search-hit rows and
    the honest operation banner; Settings keeps whole-row targets with
    ring + dot radios; light status-bar icons everywhere.
  · 656 test executions green (baseline + 4 new launch-chain pins).
  Device gate: docs/TESTING.md §15 (kilo TUI opens on tile tap; every
  other tile runs its command; Packages open runs the program; §12/§13/§14
  regressions).
- PocketShell-v0.7.0-m3.5-source.zip sha256 5d3ef098a8bc69e4cd777845f426d4737c85432ed0b67543845a8f4337b3619b  (28 MB, 300 files)
- PocketShell-v0.7.0-m3.5-source.tar.gz sha256 9e95b3112dc898b7a276e89c85c6bc2467cf337e540fddbfbb7277015b5ebaee  (28 MB)
- pocketshell-m2.gitbundle           sha256 a74645767f56a6a290616dd452b8fee76695be5492c0e470108bd36db568767e  (full history @ 7a8a136; ~26 MB — includes the complete milestone history, the discarded UI attempt + rollback records, and all five Phase 3 design contracts — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.4 superseded by Phase 3.5; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
