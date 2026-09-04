# download/ — delivery masters

Current: v0.7.0-m4.0.4 (payload cut at git tip d4b19d9; the cookie-banner
lesson — pixel-truth stall detection, silent software-retry, honest card
with escape hatches, keyboard toggle in one spot/one shape — versionCode 28)
- PocketShell-v0.7.0-m4.0.4-debug.apk  sha256 adbdcfe30fa99db486a671c813a1b0bc22f952527767a278bc4e1baa5d52333e
  Installs IN PLACE over v0.7.0-m4.0.3 (27), m4.0.2 (26), m4.0.1 (25),
  m4.0 (24) and every earlier build (vc16..vc23) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes, packages,
  Companion logins) survives.
  WHAT FIXED (from your post-m4.0.3 report):
  1. BLACK PAGE: your cookie-banner screenshot cracked it — the site's
     own consent bar painted on an otherwise dead canvas, which defeated
     every event-based check. The watchdog now reads PIXELS (software +
     glass readback), judges the MAIN region only (above the bottom 25%
     consent-bar dock), silently retries once on the SOFTWARE renderer,
     and only then shows the honest "Page never rendered" card with the
     WebView version.
  2. THREE WAYS OUT on that card: Retry (alternates GPU/SOFTWARE),
     "Open in browser" (same address in your real browser — proves
     whether it's the site or this device's WebView build), and
     "Continue anyway" (raw canvas — you can tap the site's own Accept;
     the probe stays quiet and never fights you).
  3. KEYBOARD TOGGLE IN ONE PLACE: the [⌨] key now sits between Space
     and Enter in the deck row — and toggled off, the SAME rectangular
     key box parks at that same right-hand spot (round bubble gone).
  4. COOKIE BANNER: it belongs to the WEBSITE (ChatGPT/OpenAI's), not
     PocketShell. Accept/Reject once — the choice persists across
     launches (cookies flush on every pause).
  · Full suite green (0 failures; +2 new pins per variant).
  Device gate: docs/TESTING.md §21. Full record: docs/CHANGELOG
  [0.7.0-m4.0.4].
- PocketShell-v0.7.0-m4.0.4-source.zip sha256 5facc45d377f0767db5f7afe8111638242a3df015f58d9d471ec2a58cbe6c54a  (28, 299 files)
- PocketShell-v0.7.0-m4.0.4-source.tar.gz sha256 722695d7bdb3f85116b01b5488a3311452e20a769072c2160c6f72445cccb7e3  (28 MB)
- pocketshell-m2.gitbundle           sha256 ff4b449e615e43738a4a273786e4b7bff2a794f7c121beb6b0ab287d8ed42246  (full history; ~26 — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md with the §22/§23/§24 amendments — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.3 superseded by m4.0.4; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
