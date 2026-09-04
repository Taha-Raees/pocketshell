# download/ — delivery masters

Current: v0.7.0-m4.0.6 (payload re-cut at git tip 66b0d00; the last dark
lever off — the WebView now always answers prefers-color-scheme: light,
the boot witness is SSR-proof, and a standing Page health sheet puts the
page's full testimony on the clipboard with one tap — versionCode 30)
- PocketShell-v0.7.0-m4.0.6-debug.apk  sha256 5934b41635c06f90c2b2a604215484e3e613050c916ac0849cc6f827d1c54273
  Installs IN PLACE over v0.7.0-m4.0.5 (29), m4.0.4 (28), m4.0.3 (27),
  m4.0.2 (26), m4.0.1 (25), m4.0 (24) and every earlier build (vc16..23)
  — same pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo,
  Hermes, packages, Companion logins) survives.
  WHAT FIXED (still the one job — the silent black canvas decoded us):
  1. THE NO-CARD BLACK DECODED: under m4.0.5 BOTH witnesses stood down.
     The pixel probe passed because the page painted its own near-black
     body; the DOM witness passed because chatgpt.com's SSR shell lands
     with hundreds of inert nodes BEFORE hydration, clearing the
     60-element floor with a dead script bundle.
  2. FORCED-LIGHT SCHEME: the WebView is created in a configuration
     context pinned to light — every site sees
     prefers-color-scheme: light (the one dark lever m4.0.5 left armed:
     Force-Dark-off does not change what the WebView ANSWERS). ChatGPT
     now serves its light theme; the black-shell path dies at source.
  3. SSR-PROOF WITNESS: a captured boot error is decisive — an erroring
     page only counts as alive with real visible text (>= 200 chars).
     The probe also reads interactive-element and text counts.
  4. PAGE HEALTH + COPY REPORT: a new info chip at the tab strip's end
     opens a sheet with the tab's full live testimony (url, WebView
     version, renderer, pixel verdict, readyState/DOM/interactive/text,
     boot errors, console lines, UA) and a COPY REPORT button — if
     anything is ever still broken, paste the report in the chat and it
     names the cause verbatim. Plus Refresh / Reload / Reload-in-compat.
  · Full suite green: 762 executions / 0 failures (+8 new pins per
    variant).
  Device gate: docs/TESTING.md §23. Full record: docs/CHANGELOG
  [0.7.0-m4.0.6].
- PocketShell-v0.7.0-m4.0.6-source.zip sha256 7eacc76c9c623cdd74232cd61b694b68545f87a8d2085080672e248645b29610  (28M, 305 files)
- PocketShell-v0.7.0-m4.0.6-source.tar.gz sha256 d4274524e5771c60838957bd796d1d03903600ceea289f5b82f7e86795892d00  (28 MB)
- pocketshell-m2.gitbundle           sha256 84871ad987adc6b621c663596ff42f3c119cca7a964607910fcf6b6610bddbdc  (full history; ~26M — includes the complete milestone history, all Phase 3/4 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md with the §22/§23/§24 amendments — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m4.0.5 superseded by m4.0.6; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
