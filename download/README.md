# PocketShell — Downloadable Artifacts

**Primary delivery channel: the built-in download page** (this app, served at `/`) —
it hosts every artifact with SHA-256 checksums. This file is the offline manifest.

## PocketShell-v0.2.0-m2.2-wip-debug.apk (20.4 MB) — LATEST
M1 feature set + the M2.2 runtime-installation vertical slice.

- Package: `app.pocketshell` · versionCode 3 · versionName 0.2.0-m2.2-wip
- minSdk 26 (Android 8.0+) · targetSdk 36 · ABIs: arm64-v8a, armeabi-v7a, x86, x86_64
- Signed with the debug keystore → directly installable
- SHA-256: 3f6998db2783f65c1f5cfa6069ed4135d75b75dabb8e005d3908db5731a8abe7

### What's inside (honest scope)
- Full M1 terminal: real PTY → /system/bin/sh, in-app keyboard, multi-session,
  themes, read-only diagnostics
- NEW M2.2: Diagnostics → "Linux environment" — downloads the pinned
  Alpine Linux 3.24.1 (aarch64) minirootfs from dl-cdn.alpinelinux.org over your
  network, verifies size + SHA-256, extracts with a traversal guard, configures,
  promotes atomically; state machine NOT_INSTALLED → DOWNLOADING → VERIFYING →
  EXTRACTING → CONFIGURING → READY/FAILED/REPAIR_REQUIRED with retry
- 197 unit tests green (166 M1 + 31 M2.2); real-archive sandbox validation
  (410 files, 635 symlinks) passed against the live Alpine CDN checksum
- NOT yet: Linux shell inside the terminal (M2.3 compiles proot), package
  manager UI (M2.4), CLI detection (M2.5)

### M2.2 device gate (manual, on arm64 hardware)
Install → Diagnostics → Linux environment → Install → watch honest progress to
READY; kill mid-install and confirm recovery/retry. Report the highest state
reached.

## PocketShell-v0.2.0-m2.2-wip-source.zip (2.3 MB, 185 files) — LATEST SOURCE
Full buildable tree at git tip 948e9b1 with zero dotfiles (delivery-panel safe):
app/, terminal-emulator/, terminal-view/, docs/ (incl. M2-RESEARCH.md,
M2-ARCHITECTURE.md), scripts/, gradle wrapper + pocketshell-m2.gitbundle
(complete history, initial → M2.2) + RESTORE.txt.
- SHA-256: 7df868f66302d1e7ba97295d8598e12b3919cf0b8604d7a2baea76d33e9cb9d3
- Twin: PocketShell-v0.2.0-m2.2-wip-source.tar.gz — 1302b07915f0f85d478d3f098701be4347806b15723cc34b35383a98ba36eab7
- Bundle alone: pocketshell-m2.gitbundle — 4450ec50a7b8725859d7d31248054a207b563592b9539351c68c8ac9a9efbf0d

Restore: extract, `git clone pocketshell-m2.gitbundle pocketshell`,
`git checkout -- .gitignore`, then `./gradlew :app:assembleDebug`
(JDK 17+, Android SDK platform 36 / build-tools 36.0.0 / NDK 28.2.13676358).

## Archive — v0.1.1-m1 (superseded)
- PocketShell-v0.1.1-m1-source.zip (532 KB) — 24b4f4518e5729715f3d61017bc88fdb25688cc75c9c1cb8f49404cd1b200f33
- pocketshell-m1.gitbundle (256 KB) — 55178978ff6942ba5b85f6572f29a661bab287a379a5dbed0348ae329f15b30f

To build: set `JAVA_HOME` to a JDK 21 and `sdk.dir`/`ANDROID_HOME` to an
Android SDK with platform 36, build-tools 36.0.0, NDK 28.2.13676358, then
`./gradlew assembleDebug` (see docs/RESEARCH.md §7 for the pinned toolchain).
