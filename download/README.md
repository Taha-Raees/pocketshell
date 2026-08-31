# PocketShell — Downloadable Artifacts

## PocketShell-v0.1.0-m1-debug.apk (20 MB)
Installable build of PocketShell v0.1.0-m1 (M0–M1.3 milestone feature set).

- Package: `app.pocketshell` · minSdk 26 (Android 8.0+) · targetSdk 36
- ABIs: arm64-v8a, armeabi-v7a, x86, x86_64 (real PTY via `libtermux.so`)
- Signed with the debug keystore → directly installable on any device/emulator
  (`adb install PocketShell-v0.1.0-m1-debug.apk`)
- Verified in this sandbox: 166/166 unit tests pass, APK signature valid,
  16 KB-aligned arm64 native library.
- SHA-256: dc9544a937fdd85497d8a4637d217704a8a67484de6e4c28b5249cc6ca877824

### What works in this build
- Real terminal: real PTY → `/system/bin/sh`, real command execution
  (echo/pwd/ls/cd/mkdir/rm/cat/clear, vim/top/htop if present, etc.)
- Built-in in-app keyboard (not an IME): ESC/TAB/CTRL/ALT/SHIFT/FN/arrows/
  HOME/END/PGUP/PGDN/INS/DEL with semantically correct key encoding
- Multi-session management, sessions survive rotation/background
- Pinch-to-resize font, text selection & clipboard via long-press
- Foreground service keeps sessions alive while active
- Themes: Light / Dark / AMOLED / Dynamic Color (API 31+), Settings &
  read-only Diagnostics screens
- Honest Home screen: Terminal entry + "Installed CLI Apps: none yet" +
  Explore CLI Apps (no fake system-command listings)

### Not yet (per roadmap, gated on manual on-device acceptance)
- M2 Linux userspace (proot distros, real CLI app packages) — starts only
  after M1.x manual acceptance on real hardware (see docs/TESTING.md)
