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

Current state: **Phase 4.0.11 (v0.8.0-m4.0.11, versionCode 35) — Replace
Renderer Only: the winner frozen and shipped** (see `docs/ROADMAP.md`).
The stack: native PTY terminal (M1), Linux Alpine runtime via proot with
real package management (M2), keyboard + Home 3.x design system (M3),
and the Companion embedded-web layer (Phase 4) — its tab content renderer
is now the exact copy of the m4.0.9 control experiment's proven baseline
(`WebView(activity)` in a plain FrameLayout, Android defaults, load after
first layout), winner frozen (BASELINE, zero deltas) and pinned in
`CompanionRenderContract`; the sheet, tabs and every product surface are
untouched (docs/RENDER-RESET-M4.0.9.md §8). All automated tests pass
(758 executions) and `assembleDebug` produces a working APK; the
**manual on-device acceptance checklists** in `docs/TESTING.md`
(currently §28, Gates A–H) are the remaining gate — a green build alone
never completes a milestone.

Current state: **Phase 4.1.0 (v0.8.0-m4.1.0, versionCode 34) — Companion
Native Rebuild** (see `docs/ROADMAP.md`). The stack: native PTY terminal
(M1), Linux Alpine runtime via proot with real package management (M2),
keyboard + Home 3.x design system (M3), and the Companion embedded-web
layer (Phase 4) — rebuilt in m4.1.0 around the m4.0.9 control
experiment's physically proven baseline (`WebView(activity)` in a plain
FrameLayout, Android defaults, load after first layout) after the device
verdict exonerated WebView itself (docs/RENDER-RESET-M4.0.9.md §7).
All automated tests pass (744 executions) and `assembleDebug` produces a
working APK; the **manual on-device acceptance checklists** in
`docs/TESTING.md` (currently §27, Gates E–H) are the remaining gate — a
green build alone never completes a milestone.
