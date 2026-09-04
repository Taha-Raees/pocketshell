# download/ — delivery masters

Current: v0.7.0-m4.0.5 (payload cut at git tip 095afc3; the black page
fixed at the root — Force Dark off in three layers, Chrome-identical UA,
DOM ground-truth witness: pixels AND a mounted app required, failures
carry the page's own testimony — versionCode 29)
- PocketShell-v0.7.0-m4.0.5-debug.apk  sha256 bbba0856e9771e01b44bb198d3b9923191903ce4e1f0a28cff27a0934393df26
  Installs IN PLACE over v0.7.0-m4.0.4 (28), m4.0.3 (27), m4.0.2 (26),
  m4.0.1 (25), m4.0 (24) and every earlier build (vc16..vc23) — same
  pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes,
  packages, Companion logins) survives.
  WHAT FIXED (the one job — your evidence named the killer):
  1. THE DIAGNOSIS: the cookie banner dismissed on tap → the page
     pipeline is ALIVE. No m4.0.4 card → the main region painted the
     site's own darkened empty body → the site's APP never mounted.
  2. FORCE DARK OFF — THREE LAYERS: legacy targetSdk (a documented
     proot constraint) leaves WebView Force Dark / algorithmic
     darkening ARMED BY DEFAULT in dark mode — the documented mangler
     of exactly this kind of page. Theme flag (API 29+) + runtime
     Force-Dark-OFF (API 29–32) + algorithmic-darkening-OFF (API 33+).
     Sites render as authored — own theme, own colors, unmangled.
  3. CHROME-IDENTICAL UA: the "; wv" and "Version/4.0" markers are
     stripped — byte-for-byte the Chrome mobile UA of your device.
     Google login stops answering disallowed_useragent; bot-fronted
     sites stop being served degraded bundles.
  4. THE PAGE NOW TESTIFIES: boot-error trap from the first moment,
     per-tab console tail, and a DOM witness (readyState + element
     count, 20s budget). Healthy = pixels painted AND app mounted.
     Failure → ONE silent fresh reload → honest "Page won't start"
     card with the page's OWN numbers (readyState · DOM elements ·
     first error · console line · WebView version) + Retry / Open in
     browser / Continue anyway. If you see it, report the detail line
     verbatim — it names the exact cause.
  · Full suite green: 754 executions / 0 failures (+12 new pins per
    variant).
  Device gate: docs/TESTING.md §22. Full record: docs/CHANGELOG
  [0.7.0-m4.0.5].
- PocketShell-v0.7.0-m4.0.5-source.zip sha256 9ce2750bf37b6acc7d2ab507a554d1ab33ab85b42219b2f5f6467345401b438d  (28M, 301 files)
- PocketShell-v0.7.0-m4.0.5-source.tar.gz sha256 af7c1ebfc530d93052910bfc6076ab6784ac33beccaeedf8e7859d3ab2982bce  (28 MB)
- pocketshell-m2.gitbundle           sha256 37c08ec7bb64cf1b8ccf847734c373857b4107f3e877ed98e930e6c364f92e08  (full history; ~26M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md with the §22/§23/§24 amendments — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.4 superseded by m4.0.5; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
