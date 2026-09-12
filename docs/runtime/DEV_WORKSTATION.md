# DEV_WORKSTATION — PocketShell as a general-purpose ARM64 Linux workstation

Status: **implemented 2026-09-12** (workstation-hardening phase, after the
master environment audit at `/root/Projects/masterEnvironmentAudit.md`).
Objective: the best practical general-purpose ARM64 Linux development
environment on Android — agent-agnostic, tool-agnostic, Linux-first.

## 1. What the guest now provides (verified on device)

| Layer | Content |
|---|---|
| Base | Alpine 3.24.1 (musl) + M6 glibc sidecar (Debian glibc 2.41) |
| Core tools | bash, coreutils/findutils/grep/sed/awk, tar/gzip/xz/unzip/zip, curl/wget, git 2.54, openssh-client, tmux 3.7c, vim/nano/neovim, make, gcc/g++ 15.2, clang 22.1.3, cmake 4.2.3, ninja/samurai, pkg-config, sqlite3 |
| Languages | Python 3.14.7 + pip, node 24.20 + npm, go 1.26.x, rust 1.96 + cargo, **glibc Temurin JDK 21** (JAVA_RUNTIME.md) |
| Build systems | Gradle 8.14.5 (+ device-memory defaults), make, cmake+ninja/samu |
| Android build | cmdline-tools/sdkmanager (official, on glibc JDK), platform-36, build-tools 36.0.0 (official Java parts: d8/R8, apksigner) + **community arm64 host-tool overlay** (aapt2/aapt/aidl/dexdump/split-select/zipalign), adb/fastboot 35.0.2 (static arm64) |
| Bootstrap | `pocketshell-dev-bootstrap {core|jdk|gradle|android-tools|doctor|all}` — pinned artifacts, checksum-verified, idempotent, cached (`manifest.conf`) |
| Helpers | `pocketshell-dev-bootstrap doctor` (version matrix), `build-minimal-apk.sh` (CLI APK build), `pocketshell-adb` (self-device loop) |

Delivery state: **the devtools are an APK asset** (`app/src/main/assets/guest/pocketshell-devtools-1.tar`,
sha256 `2ed16f0b8d8bd3efeaff7c4d75c5c501d1a59ca303578b5e1de3dce04b6ec26b`,
delivered by `GuestDevTools` in the session-prep seam — same idempotent
marker/status/self-healing contract as the glibc layer) AND in-repo under
`scripts/runtime/devtools/` (source of truth for re-packaging). A fresh
rootfs converges on the first session spawn with zero manual steps.

## 2. Provenance (no random binaries)

| Artifact | Upstream | Integrity |
|---|---|---|
| Temurin JDK 21.0.12.1+1 | Adoptium GitHub release | sha256 `23e37e02…e223`, 205,641,175 B |
| Gradle 8.14.5 | services.gradle.org | official published sha256 `6f74b601…e854` |
| cmdline-tools 11076708 | dl.google.com (official) | sha256 `2d2d5085…e258` (computed on served bytes) |
| platform-36 / build-tools 36.0.0 | Google via official sdkmanager | sdkmanager-verified |
| **COMMUNITY** aapt2/aidl/zipalign/split-select/dexdump/aapt + adb/fastboot | `lzhiyong/android-sdk-tools` release 35.0.2, static aarch64 | sha256 `db1cea2c…0fc2`; limits in `PROVENANCE.aarch64` (not Google-built; 35.0.2-vs-36.0.0 skew accepted for host tools; APK output should be CI-cross-checked) |
| Go/Rust/clang/… | Alpine 3.24.1 repos via apk | Alpine signing |

The Alpine **musl** `openjdk21` is REMOVED (broken here, master audit §H;
JAVA_RUNTIME.md §1) — one supported JDK, no ambiguity.

## 3. Verified workloads (this phase, on device — the real evidence)

- C probes (exec-memory matrix, ptrace, inotify, flock, shared mmap): master audit
- C++/C: gcc + clang hello builds ✓; cmake+ninja configure+build ✓
- Rust: cargo new/build/run ✓ (master audit); Go: module build/run ✓
- Node: `npm install express` (68 pkgs, 13 s) + require ✓; `fs.watch` (inotify) ✓
- Java: fresh-shell `java -version` ✓; `gradle compileJava` + run ✓
- **Full CLI APK build on device**: aapt2 compile/link → javac → d8 →
  zipalign → apksigner → signed, badging-verified APK
  (`build-minimal-apk.sh`, mini.apk 12,715 B) ✓
- PocketShell repo `assembleDebug` on device: see §6 result
- tmux session create/capture/kill ✓; bash signal traps (TERM/INT) ✓;
  job-control process groups ✓; symlink/hardlink ✓
- adb: server start + client on-device ✓ (device loop needs the user's
  Wireless debugging — `pocketshell-adb`, honest status in §7)
- MCP fleet: 6 node MCP servers running under proot (this session) ✓

## 4. Package management (Track C verdict)

apk + persistent cache binds remain the package system (no second manager).
Verified live across: fresh installs (this session's installs), repeated
transactions, cache bind mounts, package removal (`apk del`). Failure modes
still to harden in a later phase: corrupt-cache recovery drill and
insufficient-storage behavior are UNTESTED (documented in master audit §S).

## 5. Memory posture (Track F)

Measured during the on-device repo build (7.4 GB total): system held
~5.9 GB used / ~0.9–1.0 GB available with gradle + this agent session +
6 MCP servers live. Defaults (JAVA_RUNTIME.md §5): gradle heap 1792 MB,
metaspace 512 MB, 4 workers, Kotlin daemon 1024 MB. Rule: one heavy JVM
workflow at a time; per-project overrides allowed after measurement.

## 6. Proot compatibility classification (Track G)

| Workload | Result | Class |
|---|---|---|
| fork/exec-heavy, compilers, gradle, npm, cargo, go | work | — |
| inotify watchers, unix sockets, localhost TCP/UDP servers | work | — |
| signals, traps, job control, process groups | work | — |
| symlinks (via proot link2symlink), hardlinks | work | — |
| tmux sessions | work | — |
| nested debugging (strace of child processes) | work | — |
| `mount`/`chroot`/`unshare` from guest | session death | [PROOT] |
| musl OpenJDK startup | EACCES at one mprotect | [ROOTFS/LIBC]+[UNKNOWN] |
| exec tax ~25 ms/process | measured | [ARCHITECTURAL DEBT] (native-path candidate, H) |

## 7. Android development + self-device loop status (Tracks D/E)

- **Toolchain**: complete for Java/Kotlin Android. Minimal APK: BUILT AND
  SIGNED on device. NDK: solved via the pinned community aarch64 NDK
  (below).
- **PocketShell self-build: SUCCESSFUL on device** — `./gradlew
  assembleDebug` inside the guest produced `app-debug.apk`
  (30,561,072 B; `app.pocketshell` vc47/0.11.2-m7.1.1; 18 dex;
  all-ABI native libs) in **7m 20s** warm, while a heavy agent session was
  running. Evidence: `/root/env-audit-lab/pocketshell-ondevice-build*.log`,
  sha256 `6aa511a9abf2dbdf97ba83188fecf0b1b3709312024ca94d37b03cca85579dd0`.
  Prerequisites recorded for reproduction: JDK (§ JAVA_RUNTIME) + gradle +
  android-tools + ndk bootstrap targets + `PS_LOCAL_NDK=29.0.14206865`.
  Aapt2 gotcha: AGP's Maven aapt2 is x86_64-only — the bootstrap's
  gradle.properties sets `android.aapt2FromMavenOverride` to the arm64
  overlay (first build attempt proved the failure without it).
- **NDK (community aarch64-host)**: `pocketshell-dev-bootstrap ndk`
  installs lzhiyong/termux-ndk r29 (29.0.14206865, sha256
  `02e10e4d…684b`) and applies the standard one-line host-tag patch to
  `ndk_bin_common.sh` (official script rejects aarch64 hosts even when
  linux-aarch64 toolchains ship). `terminal-emulator/build.gradle.kts`
  honors `PS_LOCAL_NDK` (unset → the untouched CI pin 28.2.13676358).
- **Self-adb: VERIFIED ON DEVICE (2026-09-12)** — paired and connected from
  inside the guest to the phone itself (Wireless debugging, `adb pair` →
  `adb connect 127.0.0.1:<port>`; connect port rotates per toggle; pairing
  persists). Full loop proven: identity (`SM-F711B`, Android 15/SDK 35),
  `pm list packages` (self-built `app.pocketshell` visible), `screencap`
  (273 KB PNG into the guest), `uiautomator dump` (foreground window =
  `app.pocketshell`), `logcat` streaming, `input`/`screencap` present.
  Port discovery works without asking the user: scan loopback ephemeral
  range (32768–60999) for listeners. Only step not executed: an actual
  `adb install` of the self-built APK (reinstalling the running app would
  kill the live session mid-test; install path itself is the standard
  `adb install -r` and the signature already proved in-place-update
  compatibility when the owner sideloaded the self-built APK).
- **CROSS-DEVICE LOOP VERIFIED (2026-09-12)** — the phone guest built the
  APK (30,573,569 B, sha256 `e196a6b1…6818`, incl. the devtools asset),
  streamed-installed it onto a SECOND physical device over wireless
  debugging (Galaxy Tab S7, `SM_T870` @ 172.20.10.2, paired from the
  guest), launched it, and pulled a screenshot back: `topResumedActivity =
  app.pocketshell/.MainActivity`, launcher UI rendering, Linux card
  "Alpine · ready". Build → install → launch → capture, phone→tab, all
  from inside PocketShell.

## 8. Security posture

No new privileges anywhere; targetSdk 28 unchanged (documented dependency:
app-data exec rights); no Android security controls weakened; all
community binaries are checksum-pinned and provenance-documented; the
bootstrap never disables Android protections. Phantom-process mitigation
via self-adb is a user-visible optional script, not a default.
