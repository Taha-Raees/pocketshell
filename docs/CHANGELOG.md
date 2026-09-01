# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.4.1-m2.4] — 2026-09-01 — fix guest DNS + apk cache for real networks (device-recording hotfix)

### Fixed — why every package operation failed on the device (v0.4.0 recording)
- **Root cause (from the SM-F711B recording, 2026-09-01 13:37):** Explore CLI
  Apps → Install failed twice, in two different ways, for the SAME underlying
  reason: the v0.4.0 DNS repair wrote hardcoded public resolvers
  (`1.1.1.1` / `8.8.8.8`), and on the user's network those are UNREACHABLE
  (port-53 egress blocked / strict Private DNS is common on carrier and
  hotspot networks). With `resolv.conf` pointing at dead servers, musl's
  resolver retried for ~10 s and apk reported `DNS: transient error (try
  again later)`; under different (instant) blocking the same failure surfaced
  as the raw errno `Permission denied` — apk-tools 3 passes socket-layer
  errnos through verbatim (`FETCH_ERRCAT_ERRNO`), which is why the first
  failure never mentioned DNS at all. No package operation ever reached the
  network; the Android side had perfect connectivity the whole time.
- **Fix — guest DNS now uses the DEVICE's own resolvers**
  (`ConnectivityManager` → `LinkProperties.dnsServers`, IPv4 first, top 3,
  re-read per operation). The public pair remains only as a fallback when the
  OS reports nothing usable. A `resolv.conf` that exactly equals the v0.4.0
  fallback is upgraded in place (PocketShell wrote it, PocketShell replaces
  it); user- or Alpine-written content is never touched. Requires one new
  normal permission: `ACCESS_NETWORK_STATE` (read-only, no traffic).
- **Hardened — apk cache can no longer be blocked by rootfs permissions.**
  apk-tools 3 keeps its download cache in `etc/apk/cache` (fallback
  `var/cache/apk`) and failed v0.4.0 installs never got past opening the
  index there. The package spec now binds two app-owned host directories
  over both guest cache paths (`--bind=<cache>/etc:/etc/apk/cache`,
  `--bind=<cache>/var:/var/cache/apk`) — the same proot mechanism as the
  `/dev`, `/proc`, `/sys` binds, so the cache lives OUTSIDE the rootfs and
  rootfs-internal ownership/modes can never block a fetch again. The cache
  dir sits beside the runtime under `noBackupFilesDir`.
- **Pre-op workspace repair:** every package operation now also guarantees
  `etc/apk/cache`, `var/cache/apk` and `tmp` exist inside the rootfs with
  sane modes (best-effort; the binds are the hard guarantee) — runtimes
  installed by v0.2.x–v0.4.0 are repaired in place, no reinstall needed.
- **Honest failure surface:** the Explore FAILED banner now also shows apk's
  real stderr (up to 4 lines) and offers **Retry** for repository updates;
  Diagnostics' "Check package environment" now runs ONE real bounded
  `apk update` probe (explicit button press — nothing automatic) and reports
  the true outcome plus which DNS servers the guest got and where they came
  from ("device resolvers" vs "public fallback").

### Tests
- 12 new unit tests (266 total): device-resolver flow into the pre-op repair,
  v0.4.0-fallback upgrade rule, never-overwrite for user content, cache-bind
  argv pins (after the fixed binds, before the guest argv), workspace repair
  creates/fixes dirs, workspace-repair failure refuses the op without exec.
- Rehearsal (scripts/rehearse_m24_packages.sh) extended with the exact cache
  binds + workspace repair steps; x86_64 end-to-end apk flow re-run.

## [0.4.0-m2.4] — 2026-09-01 — real Alpine package management (apk) + CLI app installation foundation

### Added — the first real package-management layer (M2.4)
- **Real `apk`, zero fakery.** Explore CLI Apps is now a working frontend for
  the real Alpine package manager inside the proot guest:
  `Android UI → PackageOperationManager → AlpinePackageManager →
  RuntimeProcessLauncher (the M2.3 launcher, reused — same proot, loader,
  LD_LIBRARY_PATH, argv contract) → proot → real apk → real package
  database`. PocketShell never touches Alpine package files directly; every
  installed/removed claim is confirmed by `apk info -e` exit codes, versions
  by real `apk info -e -v` stdout, executables by POSIX `command -v`.
- **M2.4.4 proven in the sandbox first** (scripts/rehearse_m24_packages.sh,
  apk-tools 3.0.6 on Alpine 3.24.1 x86_64): `apk update` (28,645 packages) →
  `apk search` → `apk add nano` → `apk info -e nano` → `command -v nano` →
  nano 9.2 actually runs → `apk del nano` → `apk info -e` exit 1. Device
  validation (§9) is the actual gate — sandbox ≠ device.
- **Dedicated background exec, not a PTY session** (Option A): package
  commands run through a new `GuestCommandRunner` (ProcessBuilder on the same
  proot spec) with captured stdout/stderr and real exit codes. Nothing is
  ever typed into a user-visible terminal session; active PTY sessions are
  untouched. Non-interactive by design (stdin → /dev/null).
- **Honest operation state machine** (`PackageOperationManager`): IDLE →
  UPDATING_REPOSITORIES → INSTALLING → VERIFYING → SUCCESS/FAILED
  (UNINSTALLING for removal, SEARCHING for searches). Every state maps to
  real in-flight apk work; no percentages, no delays. SUCCESS is emitted only
  after the guest confirms BOTH the package database entry and the
  executable; verification failure after a successful `apk add` is reported
  as FAILED("refusing to claim installation"), never as success.
- **Single-flight**: one mutating operation at a time (atomic guard). A
  second concurrent request fails immediately with a readable reason — never
  queued silently, never overlapping. Operations run in a process-scoped
  scope (Activity recreation cannot kill a real apk transaction); explicit
  Cancel destroys the real guest process. Cancel/timeout handling survived a
  deep sandbox hunt: poll-based wait (a JDK-21 blocking-waitFor quirk), and
  EOF-drain with grace on cancel (an orphaned child inherits the pipe FDs and
  would otherwise hang cancellation for the orphan's lifetime).
- **Guest DNS repair (critical device discovery)**: the Alpine minirootfs
  ships NO /etc/resolv.conf, so every guest name lookup would fail. Fresh
  installs now get one written during configure; existing v0.2.x–v0.3.x
  runtimes are repaired in place before the first package operation
  (never overwriting a file that already has content). Without this the
  user's existing 9.3 MB install could never run `apk update`.
- **Curated catalog (metadata only)**: nano, htop, vim, git, python3 — tiny
  by design. The catalog cannot claim installation status (no such field
  exists; pinned by reflection test). Normal shell commands (sh/ls/cat/df/…)
  are explicitly never launcher cards.
- **UI**: Explore CLI Apps = search (real `apk search`, apk-tools 3 output
  parsed strictly, junk lines skipped) + Featured cards with real
  Install / Open / Uninstall; honest progress card with real apk output tail
  and Cancel. "Open" re-verifies READY + installed + executable, then starts
  a NEW dedicated guest session and types the launch command into THAT
  session only — exiting the app returns to the real guest shell prompt.
- **Diagnostics**: "Package environment" section with an explicit check
  button (apk version banner, configured repositories, world package count,
  DNS state) — nothing runs automatically on screen open.
- RuntimeProcessLauncher.buildLaunchSpec grew a `guestCommand` parameter
  (default unchanged: /bin/sh -l — the M2.3 contract is untouched and
  re-pinned by tests).

### Fixed
- Payload hygiene follow-up: none this cycle (v0.3.2 carried the fix).
- `apk info -e` on a runtime without resolv.conf would have failed DNS for
  ALL networked commands — see GuestEnvironment above (repaired pre-op).

### Changed
- Version 0.4.0-m2.4 (code 8). Same signing cert → direct update, installed
  runtime + installed packages kept.
- 41 new unit tests (259 total, 0 failures): parser pins from real apk-tools
  3 output, command construction, exit-code mapping, DNS repair, single
  flight, verification-gated SUCCESS, cancellation process-destroy, real
  /bin/sh process executor (streams/timeout/destroy/large-output deadlock),
  catalog invariants.

## [0.3.2-m2.3] — 2026-09-01 — device fix: guest dies at dynamic linking (`libtalloc.so not found`)

### Fixed — Linux Shell session died at exec with a linker error (user recording, Samsung SM-F711B / Android 15)
- **What the recording shows:** v0.3.1's crash fix works — install runs to
  READY (9.3 MB), tapping "Linux Shell" no longer kills the app; the session
  opens and honestly prints the guest's death:
  `CANNOT LINK EXECUTABLE "--kill-on-exit": library "libtalloc.so" not found:
  needed by main executable` → `[Process completed (code 1)]`. Two launcher
  defects, both fixed in `RuntimeProcessLauncher.buildLaunchSpec`:
  1. **`LD_LIBRARY_PATH` was never set.** proot's `DT_NEEDED` is
     `libtalloc.so` (verified at build time), which lives in the app's
     `nativeLibraryDir` — but bionic's dynamic linker searches only its
     default system paths plus `LD_LIBRARY_PATH`, never `nativeLibraryDir`.
     The exec'd proot died the instant the linker resolved dependencies. The
     sandbox rehearsal masked exactly this:
     `scripts/rehearse_m23_gate.sh` exports `LD_LIBRARY_PATH` in the shell
     (glibc happily used it), while the device has no shell to do that. Fix:
     the spec environment now carries
     `LD_LIBRARY_PATH=<nativeLibraryDir>` itself.
  2. **argv was misaligned: no argv[0].** The JNI layer execs
     `execvp(cmd, argv)` with the args array verbatim, and v0.3.1's array
     started at `"--kill-on-exit"`. Consequences visible in the recording:
     bionic quoted argv[0] as the executable name (`CANNOT LINK EXECUTABLE
     "--kill-on-exit"`), and proot's getopt — which starts at argv[1] —
     silently swallowed the flag. Fix: argv[0] is now the proot path (standard
     exec convention).
- **Preflight grew a tooth:** `preconditionProblem()` now also verifies
  `libtalloc.so` is present next to proot/loader, so this failure class is
  reported as an honest actionable message instead of spawning a process that
  can only die.
- Version 0.3.2-m2.3 (code 7). Same signing cert → direct update over
  v0.3.1, runtime data kept.
- 3 new unit tests (218 total): `LD_LIBRARY_PATH` pinned to equal
  nativeLibraryDir; argv[0] pinned to the executable with `--kill-on-exit` at
  argv[1]; missing-`libtalloc.so` preflight message pin. The argv-contract
  test now pins argv[0] too.

## [0.3.1-m2.3] — 2026-09-01 — device crash fix: Linux Shell tap killed the app

### Fixed — device crash (user recording, Samsung SM-F711B / Android 15)
- **Tapping "Linux Shell" exited the app instantly to the launcher.** Two
  independent root causes, both fixed:
  1. **Native libraries were never on the filesystem.** AGP 8 defaults to
     `extractNativeLibs=false`: `.so` files ship only inside the APK,
     `System.loadLibrary` still works (PT ran, install ran, 9.3 MB runtime
     installed fine — everything the recording shows), but
     `applicationInfo.nativeLibraryDir` is EMPTY, so the path-based execve()
     proot needs is impossible. `buildLaunchSpec`'s bare `require(proot.isFile)`
     then threw from the Compose click handler → unhandled main-thread
     exception → process death. Fix: `packaging { jniLibs {
     useLegacyPackaging = true } }` → `extractNativeLibs=true` in the merged
     manifest (verified with aapt2).
  2. **targetSdk 36 could never run the guest anyway (W^X).** AOSP policy
     (`app_neverallows.te`) neverallows `execute_no_trans` on
     `app_data_file` for every untrusted-app domain except the legacy ones,
     and `seapp_contexts` maps targetSdk 28 → `untrusted_app_27` (29+ →
     blocked domains). proot's whole job is execve()ing the guest shell
     inside app data, so no amount of loader plumbing fixes targetSdk ≥ 29 —
     this is precisely why Termux targets 28. **targetSdk 36 → 28** (the
     pre-documented Plan B, promoted by evidence; side-load distribution is
     unaffected, Android 14+ installs targetSdk ≥ 23).
- **Launch path is now crash-proof by construction** (defense in depth — a
  refused launch can never again kill the process regardless of cause):
  - `RuntimeProcessLauncher.preconditionProblem()` — pure preflight returning
    an honest, actionable reason (missing rootfs → "install or repair from
    Diagnostics"; missing proot/loader → names the directory). `buildLaunchSpec`
    now throws with exactly that message (consistency pinned by tests).
  - `TerminalViewModel` routes every spawn (Terminal / Linux Shell / CLI app /
    new session) through a single `safeSpawn` no-crash boundary; failures set
    a `launchError` StateFlow instead of propagating.
  - Home renders a dismissible error banner with a Diagnostics shortcut;
    navigation to the terminal happens only on a real spawn.
  - `TerminalSessionManager.spawn()` wraps PTY construction in try/finally so
    `_creating` can never stick true.
- If the guest process itself dies on-device (e.g. a vendor policy surprise),
  the terminal shows the exec error and `[process exited]` — visible, honest,
  and the app stays alive.

### Changed
- Version 0.3.1-m2.3 (code 6). Same debug signing cert as v0.2.x/v0.3.0
  (SHA-256 34391676…cf3f) → installs as a direct update, runtime data kept.
- 4 new unit tests (215 total, 0 failures): preflight messaging pins for the
  exact v0.3.0 crash conditions.

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
