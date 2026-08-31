# PocketShell — Downloadable Artifacts

## PocketShell-v0.1.1-m1-debug.apk (20 MB) — LATEST
Installable build of PocketShell v0.1.1-m1 (M0–M1.3 + terminal-refresh hotfix).

- Package: `app.pocketshell` · versionCode 2 · minSdk 26 (Android 8.0+) · targetSdk 36
- ABIs: arm64-v8a, armeabi-v7a, x86, x86_64 (real PTY via `libtermux.so`)
- Signed with the debug keystore → directly installable
  (`adb install PocketShell-v0.1.1-m1-debug.apk`)
- SHA-256: 336fc6b008dbac28… (full: `sha256sum` the file after download)

### What changed in v0.1.1 (vs v0.1.0)
- **FIXED: terminal did not repaint while the built-in keyboard was visible.**
  Typed text and command output only appeared after toggling the keyboard off
  (layout event). Root cause: vendored `TerminalView` follows the upstream
  contract that the *host* must call `TerminalView#onScreenUpdated()` when a
  session's screen changes — our session client stub was empty. Session output
  now drives the view refresh through `TerminalSessionManager.onScreenUpdateListener`.
- Cursor now blinks (upstream-documented host duty that was never invoked),
  blinking pauses with host lifecycle.
- Terminal view takes focus after attach → Bluetooth/hardware keyboards work.
- Regression checks added to `docs/TESTING.md` §4 (manual on-device re-test).

### Unchanged since v0.1.0
- Real terminal: real PTY → `/system/bin/sh`, real command execution
- Built-in in-app keyboard (not an IME): ESC/TAB/CTRL/ALT/SHIFT/FN/arrows/
  HOME/END/PGUP/PGDN/INS/DEL with semantically correct key encoding
- Multi-session management, sessions survive rotation/background
- Pinch-to-resize font, text selection & clipboard via long-press
- Themes: Light / Dark / AMOLED / Dynamic Color (API 31+), Settings &
  read-only Diagnostics screens
- Honest Home screen: Terminal entry + "Installed CLI Apps: none yet" +
  Explore CLI Apps (no fake system-command listings)

### Not yet (per roadmap, gated on manual on-device acceptance)
- M2 Linux userspace (proot distros, real CLI app packages) — starts only
  after M1.x manual acceptance on real hardware (see docs/TESTING.md)
