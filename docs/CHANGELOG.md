# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.1.0-m1.1 / m1.2 / m1.3] — 2026-08-31 — Keyboard hardening · Input reliability · Polish

### M1.1 — Built-in keyboard hardening
- System IME fully suppressed inside the terminal (window `SOFT_INPUT_STATE_ALWAYS_HIDDEN`;
  PocketShell keyboard is the sole typing surface, brief §7).
- Terminal font size state plumbing (default from Settings, live pinch changes).
- App shell refactor: screens wired through a single root with settings-aware theming.

### M1.2 — Input reliability
- Foreground service (`specialUse`) keeps real session processes alive while
  backgrounded; runs only while sessions exist, stops itself when the last one
  closes/finishes (brief §24); notification states session count truthfully.
- Pinch-to-resize font with PTY reflow (scale thresholds → `setTextSize` →
  upstream `TIOCSWINSZ`).
- Clipboard copy/paste via upstream selection ActionMode (wired through
  `PocketShellSessionClient` to the system clipboard).

### M1.3 — UI/UX polish
- Settings screen: theme mode (System/Light/Dark/AMOLED), dynamic color
  (Android 12+, honest fallback), default terminal font size (DataStore-persisted).
- Diagnostics screen: read-only runtime facts only — version, API level, device,
  ABIs, shell presence, PTY library state, HOME/TMPDIR, real session counts,
  exact permission list (brief §34).
- Theme system: restrained brand scheme + AMOLED pure-black surfaces + dynamic
  color where the platform provides it.
- Home header gains Settings/Diagnostics entries.

### Verification
- Full build + all unit tests pass.
- **Manual on-device acceptance for M1/M1.1/M1.2/M1.3 remains pending
  (docs/TESTING.md §3–§5) — mandatory human step before M2 (brief §27/§28).**

---

## [0.1.0-m1] — 2026-08-31 — M1: Terminal Foundation

### Added
- Gradle skeleton: settings/root/version catalog (`gradle/libs.versions.toml`),
  wrapper (Gradle 8.14.5), pinned AGP 8.13.2 / Kotlin 2.4.10 / platform 36 /
  NDK 28.2.13676358.
- Vendored `:terminal-emulator` + `:terminal-view` (byte-identical upstream
  sources at pinned commit; Kotlin-DSL build ports documented in THIRD_PARTY.md).
- `ShellEnvironment` — real `/system/bin/sh` runtime (HOME/TMPDIR/TERM/PATH),
  executable resolver.
- `TerminalSessionManager` — process-scoped multi-session owner; per-session
  PTY/env/cwd/title; finished sessions marked, never faked; CLI-app session
  factory with executable verification.
- `PocketShellSessionClient` / `PocketShellTerminalViewClient` — upstream
  client implementations bridging events, clipboard, logging and keyboard
  modifier hooks.
- PocketShell keyboard v1 (full §8 coverage): QWERTY + digits + 28-symbol page
  + extended row (INS/DEL/HOME/END/PGUP/PGDN), ESC/TAB/ENTER/BACKSPACE,
  arrows, FN layer (F1–F12, HOME/END/PGUP/PGDN, DEL), one-shot/locked
  modifiers with always-visible state, hold-to-repeat, haptics,
  phone/tablet adaptive layouts.
- `TerminalKeyDispatcher` — single input pipeline: keyboard → synthetic
  KeyEvents → vendored TerminalView → upstream KeyHandler → PTY.
- Compose UI: Home (honest empty states), Terminal (tabs + TerminalView +
  keyboard), Explore placeholder (honest M2 notice); navigation without extra
  dependencies.
- CLI app architecture: `CliApp` model, DataStore registry (empty by default),
  verified launcher.
- Tests: upstream emulator suite (19 classes, all pass), keyboard state
  machine, §8 symbol coverage, FN remaps, CLI app model/resolver.

### Fixed
- Kotlin `KeyAction.Char` name clashed with `kotlin.Char` → renamed to
  `KeyAction.Text`.
- `KeyCharacterMap.getEvents` requires an instance; use `load(VIRTUAL_KEYBOARD)`.
- Upstream `TerminalView` exposes only `(Context, AttributeSet)` constructor.

### Verification
- `./gradlew :app:assembleDebug` → 20 MB debug APK containing `libtermux.so`
  for all 4 ABIs, 16 KB-aligned (align 2**14).
- All unit test suites pass (emulator + app).
- **Manual on-device acceptance pending (docs/TESTING.md §M1) — requires a
  human with real hardware; no device exists in this sandbox.**

---

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
