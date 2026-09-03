# download/ — delivery masters

Current: **v0.7.0-ui** (git tip 639a760, versionCode 17) — the UI/UX redesign
release ("Quiet Aurora" design system + final keyboard). The Linux runtime
stack (M2.6) is UNTOUCHED — update in place and re-run Gates A–H after.

- PocketShell-v0.7.0-ui-debug.apk  sha256 f8db8394fb7ce6b3f272abee094658f0d3a7c660d74690e3c99080874799d719
  Installs IN PLACE over v0.6.2/v0.6.1/v0.6.0/v0.5.0 (same pinned cert
  d96a6f66…8bf659). The runtime/rootfs does NOT need reinstalling.

  What you will see (full contract: docs/UI-REDESIGN.md):
  - HOME: brand block + a clean 2×2 launcher grid — Terminal (hero),
    Linux Shell, Apps, Packages. The old "Installed CLI Apps" cards
    (git/python/nano/htop) are GONE — those are packages, found via
    Packages search/Featured. No project cards, no fake tiles.
  - NAVIGATION: no permanent tabs; a small hamburger (two unequal lines,
    top-left on every screen) opens the drawer (Home/Terminal/Apps/
    Packages/Diagnostics/Settings/Linux Shell + runtime status footer).
  - TERMINAL: session pills + overflow menu (closing a session asks for
    confirmation — it kills a real process); framed-ink canvas; same real
    terminal engine.
  - KEYBOARD (final spec): top row Esc · Tab · ← ↑ ↓ → (arrow long-press
    = Home/End/PgUp/PgDn); bottom row [⌨ icon] · Ctrl · Alt · Space ·
    Shift · ↵. The ⌨ ICON (no ON/OFF text) toggles the ANDROID keyboard;
    both accessory rows stay above it. Fn key REMOVED — Esc long-press
    opens an F1–F12 strip; digits/symbols come from the Android keyboard.
    Modifiers: tap = one-shot, tap-tap = locked (lock dot), visual only.
  - APPS: launchable applications detected in the guest (Hermes, OpenCode)
    — a row exists ONLY while the guest confirms its command right now.
    CLI tools never appear here.
  - PACKAGES (formerly Explore): same honest apk logic verbatim, new skin.
  - SETTINGS: Appearance + AI Assistant (OpenRouter key + free-text model;
    masked after entry, never logged; chat honestly "not yet") + About.
  - DIAGNOSTICS: every row/button preserved, grouped into cards.
  - DEVICE GATE: docs/TESTING.md §11 + re-run §10 Gates A–H after update.

- PocketShell-v0.7.0-ui-source.zip  sha256 b37a8084cfb5a3aec36d3fb7bc7a588cdf22ae798f543565e710483c42ec44ef
- PocketShell-v0.7.0-ui-source.tar.gz  sha256 22e96ff3f17b575574da169798585d1b6f83b1a73023c58b35955db019d3f258
- pocketshell-m2.gitbundle  sha256 6254414796296b3b4e42f4749382fe9c7ddc514eb5fd8624dc4b0ac87d87e947
  (bundle tip = 639a760; full milestone history incl. the UI phase)

Sanity: 290 files in the source archive, 0 dot-paths, 0 web shims,
0 node_modules; all 20 key-file pins verified at payload time.
