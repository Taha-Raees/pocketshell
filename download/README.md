# download/ — delivery masters

Current: v0.7.0-m4.0 (payload cut at git tip 4ebf734; Phase 4 — Companion,
the embedded web workspace, versionCode 24)
- PocketShell-v0.7.0-m4.0-debug.apk  sha256 a9f1fb71949b558a3f6d89a8d92b615d3d6a4d0905f714853898d6d7b418a438
  Installs IN PLACE over v0.7.0-m3.6 (versionCode 23), m3.5 (22), m3.4 (21),
  m3.3 (20), m3.2 (19), m3.1 (18), the discarded v0.7.0-ui (17) and v0.6.2
  (16) — same pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo,
  Hermes, packages) survives.
  SCOPE: one NEW feature layer — Companion — plus its Settings section.
  Terminal, Home, Packages, Diagnostics and the entire guest pipeline are
  untouched. Contract: docs/PHASE-4-COMPANION-DESIGN.md (committed before
  implementation).
  Phase 4 highlights:
  · THE WORKSPACE: a persistent web layer below every screen, pulled up
    by a small bottom drag handle ONLY (no floating button, no labels,
    no browser chrome). A Companion is exactly Name + URL — fully
    generic; not an AI chatbot, no AI APIs; you use the real website
    with your real account.
  · SMOOTH AS SILK: 1:1 drag, page never reflows mid-drag (frozen web
    height, one resize on release); gentle half/near-full anchors (6%
    window) else stay-put; height persisted; handle reachable at every
    height — never trapped. Only the handle zone resizes; page scrolling
    is never stolen.
  · REAL LOGINS PERSIST: cookies + DOM storage in the app's private web
    profile (flush at pause); log in once, restart, still logged in.
    Engine = Android System WebView (zero new dependencies), Safe
    Browsing on, mixed content never, file/content access off, camera/
    mic/geo denied, default UA (no spoofing).
  · TABS: inverted Phase 3.1 editor language; switch WITHOUT reloads
    (background tabs alive-but-paused, active + 4 LRU pool,
    saveState-on-evict / restore-on-reactivate, onTrimMemory drops
    background pages first).
  · INTEGRATION: Back = web history → collapse → normal navigation;
    uploads via the normal Android picker; downloads via DownloadManager
    into app-private storage (no permission, no crash); mailto/tel/
    intent resolved by the system with an honest toast on failure;
    .imePadding() lifts the panel above the keyboard.
  · SETTINGS > COMPANION: add/edit/delete, quick-add templates (editable
    pre-fills only), Default Companion, Clear web data.
  · 704 test executions green (baseline + 20 Companion pins).
  Device gate: docs/TESTING.md §17 (login persistence, drag experience,
  tabs, file upload, navigation, performance, §12–§16 regressions).
- PocketShell-v0.7.0-m4.0-source.zip sha256 577b86f759f500286d91b2264aaa0f22fd2d440f1fab0b9aa5ca6a005332152a  (28 MB, 316 files)
- PocketShell-v0.7.0-m4.0-source.tar.gz sha256 a0001fd68cce64fc57166c8842a569a1c454debff403c995065d053ac5c5b57e  (28 MB)
- pocketshell-m2.gitbundle           sha256 6a90c25990ce255e94235767fb1e47a487678f196f4475e38a274f6522b1903d  (full history @ 4ebf734; ~26 MB — includes the complete milestone history, all six Phase 3 design contracts, docs/PROCFS-CONTRACT.md, and docs/PHASE-4-COMPANION-DESIGN.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.6 superseded by Phase 4; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
