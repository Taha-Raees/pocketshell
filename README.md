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

Current state: **M7.2 P1 — notification foundation (phase build
`-m72p1` on the inherited v0.11.2-m7.1.1 / versionCode 47 stamp; M7.1.1
fix release and M7.1 release below it)**
(see `docs/ROADMAP.md`). The stack: native PTY terminal (M1), Linux Alpine
runtime via proot with real package management and the pinned glibc layer
(M2 + M6), the Midnight/Daylight design system and workspace (M3 + M5), the
Companion embedded-web layer (M4), the Files explorer across Linux/Downloads/
SAF storage with operations, editor, Open-Terminal-Here and search (M7.0),
and M7.1 — Home launchers (Companions + Your tools grids with hide/restore,
custom tools over the ONE verify-then-launch path, 13 curated official marks
as two-variant theme pairs, x-scroll rows with scroll dots, the tools-header
packages affordance) and live external-keyboard detection, rebuilt in
M7.1.1 after the real-device failure: ONE authoritative keyboard-state
model (persistent On-screen keyboard preference + hardware state + explicit
user request), the gated terminal-canvas tap, the confirm deadline against
event storms, the configuration-change cross-check as a second detection
mechanism, and both-direction transition notices — event-driven, no
polling, no new permissions. M7.2 P0 produced the full architecture audit
(`docs/M7.2-P0-AUDIT.md`); M7.2 P1 built the notification foundation —
POST_NOTIFICATIONS requested exactly once per install on Android 13+
(when the first session exists, never re-asked), the
`notifications/` coordinator layer (event channel, deterministic ids,
DataStore posted-id ledger, startup stale sweep, FLAG_IMMUTABLE tap
intents), and intent routing on both activity paths — infrastructure
only: no event notifications posted yet, no agent detection, no
heuristics. Full JVM suite 780/780 green (app 635 + terminal-emulator
145, forced clean rerun at the tip) and `assembleDebug` produces a
working APK; the **manual on-device acceptance checklists** in
`docs/TESTING.md` remain the hardware pass — the M7.1.1 gate is §47 and
the M7.2 P1 gate is §48, and a green build alone never completes a
milestone.
