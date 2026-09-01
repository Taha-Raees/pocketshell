# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.3.0-m2.3] — 2026-09-01 — Linux shell: proot guest behind the existing PTY

### Added
- **Linux Shell (Home card) enters the installed Alpine guest** when the
  runtime is READY: `RuntimeProcessLauncher` builds the proot launch line,
  `TerminalSessionManager.createLinuxSession()` spawns it on the SAME PTY and
  session machinery as the system shell — no second terminal implementation.
  In every other runtime state the Home card shows the truth (not installed /
  installing / failed / repair / unsupported ABI) and routes to Diagnostics.
- proot stack compiled from pinned source and bundled via jniLibs for all
  four ABIs: `libproot.so` + `libproot-loader.so` (termux/proot pinned tag
  **v5.1.107.92** @ 7266fb3e8516535682f5a9c8f3a7e70f6506eddb, GPL-2.0) and
  `libtalloc.so` 2.4.2 (LGPL-3.0+, dynamic, SONAME normalized). See
  scripts/build_proot_m23.sh + docs/THIRD_PARTY.md.
- Loader strategy (the M2 risk item): runtime env `PROOT_LOADER` points at
  `nativeLibraryDir/libproot-loader.so` — the one location that stays
  executable at targetSdk ≥ 29 — while the loader embedded in libproot.so
  remains a fallback. No execve() on app-data files, ever.
- argv contract: `--kill-on-exit --rootfs=<rootfs> --root-id --cwd=/root
  --bind=/dev --bind=/proc --bind=/sys /bin/sh -l`. Long options use the
  joined `=` form (proot rejects the separated form — rehearsed and pinned
  by unit tests).
- 8 new unit tests (211 total, 0 failures): argv/env pins, missing-artifact
  refusal, READY-only gate.

### Verified (sandbox rehearsal)
- The exact launch contract was rehearsed end-to-end on the sandbox host
  with the SAME proot source and the x86_64 variant of the SAME pinned
  Alpine 3.24.1 rootfs: `uname; id; echo hello; cat /etc/alpine-release`
  returned the guest kernel view, uid=0(root), hello, 3.24.1 and a working
  BusyBox — in BOTH loader modes, exit 0 (scripts/rehearse_m23_gate.sh).
- The Android-specific exec/ptrace policy is the remaining risk and stays
  the M2.3 device gate (docs/TESTING.md §8).

### Changed
- versionCode 5, versionName 0.3.0-m2.3.

## [0.2.1-m2.2] — 2026-09-01 — Fix: crash when tapping "Install Linux environment"

### Fixed
- **The app crashed (silent process death, no dialog) immediately after tapping
  "Install Linux environment" in Diagnostics** (observed in a device screen
  recording: spinner on the button → instant return to launcher). Two
  independent defects, both fixed:
  1. *Missing `INTERNET` permission.* The M1 app legitimately needed no
     network, and the manifest said so. M2.2 added a real HTTPS downloader but
     nobody revisited the permission set — the first connect threw
     `SecurityException("Permission denied (missing INTERNET permission?)")`.
     Fix: `INTERNET` is now declared, with the honest justification inline
     (used solely to fetch the checksum-pinned Alpine minirootfs from
     dl-cdn.alpinelinux.org).
  2. *No crash containment around runtime coroutines.* The installer converts
     its own failures to `FAILED` + a `Failed` event but rethrows; nothing
     caught the rethrow, so ANY pipeline failure (missing permission, offline
     device, DNS failure, even an `Error`) killed the whole app mid-install.
     Fix: `RuntimeCrashGuard` contains install/remove failures — they now land
     in the retryable `FAILED` / `REPAIR_REQUIRED` states the state machine
     already designed for them. A scope-level `CoroutineExceptionHandler` is
     the last-resort net.

### Added
- `RuntimeCrashGuard` (internal): the containment extracted into a unit-testable
  unit; `RuntimeManager` delegates to it.
- Regression tests (`RuntimeCrashGuardTest`, 6 new): the incident's exact
  `SecurityException` driven through the REAL installer pipeline must land in
  `FAILED` with transient state cleaned and must NOT escape; non-Exception
  `Error`s likewise; successful install passes through to `READY`; partially
  undeletable runtime on remove lands `REPAIR_REQUIRED` (and `clearRuntime()`
  returning false is now treated as the failure it is); retry transitions from
  `FAILED`/`REPAIR_REQUIRED` remain legal.

### Notes
- Emulator re-verification was attempted in the sandbox and is NOT possible
  there: Android 11+ (API 30+) system images are the only ones exposing
  `arm64-v8a` (required by the arm64-gated runtime), and the emulator enforces
  a fixed ~6 GB userdata floor for them (~7.2 GiB free required at boot) which
  cannot coexist with the SDK on the 9.9 GB sandbox disk. DEVICE VALIDATION
  for the install flow remains the M2.2 gate (docs/TESTING.md).
- Live CDN re-check (2026-09-01): pinned Alpine 3.24.1 aarch64 minirootfs
  still matches `SIZE_BYTES` + `SHA256` byte-for-byte.
- 58 app-module unit tests (52 + 6 new), 145 terminal-emulator tests, all
  green; assembleDebug clean.

## [0.2.0-m2.2-wip] — 2026-09-01 — M2.2: real Linux runtime installation layer

### Added
- `runtime/` package: Linux runtime installation vertical slice
  (docs/M2-ARCHITECTURE). Alpine minirootfs 3.24.1 (aarch64) pinned by URL,
  size and SHA-256; pipeline DOWNLOADING → VERIFYING → EXTRACTING →
  CONFIGURING → atomic promotion → READY.
- `RuntimeManager` facade + `RuntimeState` machine (illegal transitions
  rejected; state derived from disk at startup, incl. orphaned-tmp recovery
  and honest REPAIR_REQUIRED for damaged metadata).
- Safe extractor: zip-slip guard, GNU longnames, symlink/hardlink handling,
  POSIX mode preservation. Validated against the REAL Alpine 3.24.1 aarch64
  minirootfs (410 files, 635 symlinks) in a sandbox test run.
- Diagnostics screen: Linux runtime section (state, size, free space,
  metadata, install/retry/remove controls). No Home screen changes.
- New dependency: commons-compress 1.28.0 (Apache-2.0).

### Notes
- proot integration is M2.3; no process execution exists yet in M2.2.
- DEVICE VALIDATION REQUIRED: install flow must be exercised on real
  hardware (download over mobile network, state transitions, recovery).

## [0.1.1-m1] — 2026-08-31 — Fix: terminal did not repaint on session output

### Fixed
- **Terminal did not show typed input while the built-in keyboard was visible;
  text only appeared after toggling the keyboard off** (observed in a device
  screen recording). Root cause: the vendored `TerminalView` follows the
  upstream contract that the *host* must call `TerminalView#onScreenUpdated()`
  when a session's screen changes — the view never observes session data
  itself. Our `PocketShellSessionClient.onTextChanged` was an empty stub with
  an incorrect comment ("TerminalView invalidates itself").
  Fix: session clients now forward screen updates through a new
  `TerminalSessionManager.onScreenUpdateListener` hook, installed by
  `TerminalScreen` to call `onScreenUpdated()` (main thread — `TerminalSession`
  dispatches via its `MainThreadHandler`).
- Cursor never blinked: `setTerminalCursorBlinkerState` (upstream-documented
  host duty) was never called. Now started in `onEmulatorSet` and toggled with
  host lifecycle (ON_RESUME/ON_PAUSE).
- `TerminalView` was never focused: hardware (Bluetooth) keyboard input could
  not reach the terminal. The view now takes focus after attach.

### Verification
- All 166 unit tests pass; APK rebuilt and re-signed as `v0.1.1-m1`
  (versionCode 2). Regression items added to `docs/TESTING.md` §4 for the
  mandatory on-device re-check.

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
