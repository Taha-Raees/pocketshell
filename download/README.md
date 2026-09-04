# download/ — delivery masters

Current: v0.7.0-m4.0.8 (payload cut at git tip 25b826d; the painted-but-
black decode — the m4.0.7 report showed BOTH tabs painting (GPU and
software-layer) while the screen stayed black, so the black is the page's
own near-black output: m4.0.7's rollback had re-armed algorithmic
darkening (targetSdk 28 ⇒ ON by default on Android 15) and the dark
prefers-color-scheme. The light package returns ON TOP of the fixed host —
versionCode 32)
- PocketShell-v0.7.0-m4.0.8-debug.apk  sha256 6626d8dd51a26c12fa5d6990eb688d1eccf40961541452460c96939a1d33f22c
  Installs IN PLACE over v0.7.0-m4.0.7 (31), m4.0.6 (30), m4.0.5 (29),
  m4.0.4 (28), m4.0.3 (27), m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every
  earlier build (vc16..23) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins) survives.
  WHAT FIXED (your m4.0.7 health report cracked it — BOTH tabs answered
  "pixels: painted", a GPU tab AND a software-layer tab, while you still
  saw black; a software-layer view cannot fail to reach the screen, so
  the black IS the page's own painted near-black body):
  1. FORCED-LIGHT SCHEME: the WebView's configuration is pinned to
     UI_MODE_NIGHT_NO (the documented prefers-color-scheme lever), so
     sites ALWAYS serve their light themes — a white body with dark text
     you can SEE, even when a page's app shell is thin.
  2. DARKENING OFF at every API level: setAlgorithmicDarkeningAllowed
     (false) on Android 13+, setForceDark(FORCE_DARK_OFF) on 12 and
     below; the theme already carries android:forceDarkAllowed=false.
  3. THE ACTIVITY LOOKUP THE CONFIG CONTEXT BREAKS, FIXED: the glass
     probe now unwraps ANY context chain to find the hosting Activity,
     and the pool remembers the host from acquire (a configuration
     context is not an Activity — a hidden m4.0.6 glass-probe
     regression).
  4. THE PROBE CANNOT BE FOOLED AGAIN: the health report now names WHAT
     IS ON THE GLASS — "glass: dominant #0D0D0D · 97% near-black · 3
     colors" — plus "scheme: forced light" and the PAGE'S OWN VOICE
     (title + first visible words). If anything is ever wrong again, one
     pasted report names the page state with zero guessing.
  · Full suite green: 772 executions / 0 failures (+4 pins).
  Device gate: docs/TESTING.md §25. Full record: docs/CHANGELOG
  [0.7.0-m4.0.8].
- PocketShell-v0.7.0-m4.0.8-source.zip sha256 7d9b738ae9b83806363e24ac3c9f81ef9d202d4c17c8818f701a4b3dbf64355c  (28M, 306 files)
- PocketShell-v0.7.0-m4.0.8-source.tar.gz sha256 8e9bd2b5eab347fa94e552c1210f349a9793d76229099e4f54e79603940ef172  (28 MB)
- pocketshell-m2.gitbundle           sha256 91a01edcb725321329b3c474269863ebbef8e8209321bb75aae10d20936c2ff6  (full history; ~26M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.7 superseded by m4.0.8; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
