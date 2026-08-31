# PocketShell — Downloadable Artifacts

**Primary delivery channel: the built-in download page** (this app, served at `/`) —
it hosts every artifact with SHA-256 checksums. This file is the offline manifest.

## PocketShell-v0.2.1-m2.2-debug.apk (20.4 MB) — LATEST
M1 feature set + M2.2 runtime installer, WITH the Install-crash fix.

- Package: `app.pocketshell` · versionCode 4 · versionName 0.2.1-m2.2
- minSdk 26 (Android 8.0+) · targetSdk 36 · ABIs: arm64-v8a, armeabi-v7a, x86, x86_64
- Signed with the debug keystore → directly installable
- SHA-256: b7b54d1f4eacbedaebd659c2bf49b8b15888f8e4467feaa4888d2c5405c96e04

### What changed in v0.2.1 (vs v0.2.0-m2.2-wip)
- **FIXED: tapping "Install Linux environment" crashed the app** (device
  recording: spinner → instant launcher, no dialog). Two defects:
  1. Missing `INTERNET` permission — the M2.2 downloader's first HTTPS connect
     threw SecurityException. Now declared (used only for the checksum-pinned
     Alpine minirootfs download from dl-cdn.alpinelinux.org).
  2. Uncontained coroutine failures — the installer rethrew after reporting
     FAILED, killing the process. New `RuntimeCrashGuard` contains every
     failure into the honest retryable FAILED / REPAIR_REQUIRED states.
- 6 regression tests pin the exact incident throwable through the real
  installer pipeline (58 app-module tests, 145 terminal-emulator tests green).

### Unchanged (M1 + M2.2 feature set)
- Real terminal: PTY → /system/bin/sh, in-app keyboard, multi-session, themes
- M2.2 Diagnostics → Linux environment: download ~4 MB pinned Alpine 3.24.1
  (aarch64) minirootfs over your network → size+SHA-256 verify → guarded
  extraction → atomic promotion → READY; kill-mid-install recovery, retry,
  remove. No Linux shell inside the terminal yet (M2.3: proot).

### M2.2 device gate (manual, on arm64 hardware)
Install → Diagnostics → Linux environment → Install → watch honest progress to
READY; kill mid-install and confirm recovery/retry; airplane-mode failure must
land in FAILED (never exit the app). See docs/TESTING.md §7.

## PocketShell-v0.2.1-m2.2-source.zip (2.4 MB, 188 files) — LATEST SOURCE
Full buildable tree at git tip 85b46bb, zero dotfiles (delivery-panel safe):
app/, terminal-emulator/, terminal-view/, docs/ (incl. M2-RESEARCH.md,
M2-ARCHITECTURE.md), scripts/, gradle wrapper + pocketshell-m2.gitbundle
(complete history) + RESTORE.txt.
- SHA-256: 3b581e0ddc0e2182c5fb40d186d40b37b3a4735cbc939ef010b8b99c837fcc21
- Twin: PocketShell-v0.2.1-m2.2-source.tar.gz — 1c5c2c4f65aa57a55d97ecb7a75fa8963c364d062dc356c19a4bd1f27b722fc1
- Bundle alone: pocketshell-m2.gitbundle — acf07b1a32595b78a189f6b48d5802dde3e74595a5e55bfa4801dc6479d5d307

Restore: extract, `git clone pocketshell-m2.gitbundle pocketshell`,
`git checkout -- .gitignore`, then `./gradlew :app:assembleDebug`
(JDK 17+, Android SDK platform 36 / build-tools 36.0.0 / NDK 28.2.13676358).

## Archive — v0.1.1-m1 (superseded)
- PocketShell-v0.1.1-m1-source.zip (532 KB) — 24b4f4518e5729715f3d61017bc88fdb25688cc75c9c1cb8f49404cd1b200f33
- pocketshell-m1.gitbundle (256 KB) — 55178978ff6942ba5b85f6572f29a661bab287a379a5dbed0348ae329f15b30f

Note: the v0.2.0-m2.2-wip artifacts were withdrawn (Install-crash build) and
are intentionally not distributed.
