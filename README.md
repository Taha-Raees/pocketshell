# PocketShell

A modern Android application that provides a **real** Linux/Unix terminal with
an Android-native UI built around it — not a terminal awkwardly wrapped in an
app.

```
REUSE → INTEGRATE → OPTIMIZE → IMPROVE
```

The terminal engine is **not** reinvented: PocketShell vendors the mature,
battle-tested Termux `terminal-emulator` + `terminal-view` modules (GPLv3,
pinned upstream commit — see `docs/THIRD_PARTY.md`) and builds a clean, modern,
honest product experience on top: a built-in terminal keyboard, a simple Home
screen, real multi-session management, and a data-driven CLI-app architecture
for genuinely user-installed software.

**License: GPL-3.0-only** (consequence of vendoring GPLv3 terminal modules).

## Product principles

1. The terminal is real: real PTY (`fork`/JNI), real shell, real programs.
2. Nothing is faked: no fake installed apps, no fake sessions, no fake installs.
3. System commands (`ls`, `vim`, `top`, …) are **commands inside the terminal**,
   never presented as "installed CLI apps" on Home.
4. The built-in keyboard lives inside PocketShell (not a system-wide IME) and
   can type everything a terminal needs.
5. Small architecture, strict milestones, upstream reuse, honest documentation.

## Repository layout

```
app/                  PocketShell application (Kotlin, Compose, Material 3)
terminal-emulator/    vendored Termux module (GPLv3, pinned) — emulator + PTY JNI
terminal-view/        vendored Termux module (GPLv3, pinned) — TerminalView
docs/                 RESEARCH · ARCHITECTURE · THIRD_PARTY · ROADMAP · TESTING · CHANGELOG
```

## Building

Requirements: JDK 17+, Android SDK (platform 36, build-tools 36.0.0) and NDK
28.2.13676358 — see `docs/RESEARCH.md §7` for the exact pinned toolchain.

```bash
./gradlew :app:assembleDebug      # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest       # upstream emulator suite + PocketShell tests
```

`local.properties` (not committed) must point `sdk.dir` at your SDK, or set
`ANDROID_HOME`.

## Status

Current milestone: **M1 — Terminal Foundation** (see `docs/ROADMAP.md`).
Manual on-device acceptance checklists live in `docs/TESTING.md`; a green build
alone never completes a milestone.
