# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.1.0-m0] — 2026-08-30 — M0: Research + Architecture

### Added
- Project brief compliance docs: `README.md`, `docs/RESEARCH.md`,
  `docs/ARCHITECTURE.md`, `docs/THIRD_PARTY.md`, `docs/ROADMAP.md`,
  `docs/TESTING.md`, `docs/CHANGELOG.md`.
- Build environment installed and pinned: Gradle 8.14.5, AGP 8.13.2,
  Kotlin 2.4.20, SDK platform android-36, build-tools 36.0.0, NDK 28.2.13676358.

### Decisions
- D1: vendor Termux `terminal-emulator` + `terminal-view` at pinned upstream
  commit `3b66f8799635a4dba4a206563048ff0e6792c487` (GPLv3 consequence accepted).
- D2: reuse upstream PTY JNI (`libtermux`) verbatim.
- D3: M1 shell = `/system/bin/sh` + toybox applets; no userspace runtime until M2.
- D4: PocketShell keyboard routes input as synthetic KeyEvents through the
  upstream `TerminalView` pipeline with `KeyboardState` answering modifier hooks.

---
