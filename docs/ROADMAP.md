# PocketShell — Roadmap

Status: M0 complete · Strict milestone discipline per §27 of the project brief.
**A milestone is complete only after: automated tests + APK build + real-device
installation + manual acceptance checklist. Compilation alone is never
sufficient (§28).**

---

## M0 — Research + Architecture ✅ (this repository, 2026-08-30)

- [x] Repository inspected (greenfield; no prior Android work present).
- [x] Build environment installed and pinned (see RESEARCH.md §7).
- [x] Ecosystem research: Termux vs alternatives; W^X / 16 KB / FGS / scoped
      storage constraints verified.
- [x] Upstream audit: `terminal-emulator` + `terminal-view` verified
      self-contained, GPLv3, actively maintained; client interfaces + JNI
      surface documented.
- [x] Docs: RESEARCH / ARCHITECTURE / THIRD_PARTY / ROADMAP (+ README,
      TESTING, CHANGELOG).
- [x] Integration decisions D1–D4 recorded.

## M1 — Terminal Foundation (in progress)

Goal: Open PocketShell → tap Terminal → real terminal → real PTY → real shell
→ real command (`echo pwd ls cd mkdir rm cat clear`).

- [x] Gradle skeleton (pinned toolchain, version catalog, wrapper).
- [x] Vendored terminal modules integrated and building.
- [x] `TerminalSessionManager` + `/system/bin/sh` shell environment.
- [x] Home screen with honest empty states (Terminal card primary; Installed
      CLI Apps empty; Explore placeholder; sessions only when real).
- [x] Terminal screen: session tabs + `TerminalView` + basic bottom bar.
- [x] CLI app data model + registry (empty) + verified launcher path.
- [x] APK builds (`assembleDebug`); upstream emulator test suite passes on JVM.
- [ ] **Manual on-device acceptance (TESTING.md §M1)** — requires a human with a device.

## M1.1 — Built-in PocketShell keyboard

- [x] Keyboard component: full letter/number/symbol coverage, Enter/Backspace/Space.
- [x] Terminal keys: ESC TAB CTRL ALT SHIFT FN, arrows, HOME END PGUP PGDN INS DEL, F1–F12.
- [x] One-shot + locked modifier states with always-visible indication.
- [x] Phone compact / tablet expanded adaptive layouts.
- [x] Single input pipeline via synthetic KeyEvents + `KeyboardState` → upstream hooks.
- [x] Encoding tests for every key class (letters, symbols, all special keys).
- [x] System IME suppressed inside the terminal (keyboard is the sole input, §7).
- [ ] **Manual on-device acceptance (TESTING.md §M1.1)**.

## M1.2 — Terminal input reliability

- [x] Modifier combos stress set: CTRL+C/D/Z/L/A/E/W, TAB, ESC, arrows, HOME/END, F1–F12 (encoding via upstream KeyHandler + tests).
- [x] Interactive programs where available: vim/nano/top/less/tmux (rendering + sequences supported by vendored engine).
- [x] Clipboard copy/paste wiring (upstream ActionMode + session client).
- [x] Selection (long-press word → handles), double-tap word select, triple-tap line select (upstream).
- [x] Pinch font size (scale thresholds → `setTextSize` → PTY reflow).
- [x] Rotation / background / foreground / activity recreation survival (`configChanges` + process-scoped manager).
- [x] Foreground service (specialUse) for background session retention; self-stops when last session ends.
- [x] Multiple concurrent sessions; no cross-session state leakage (manager design + unit-tested model).
- [ ] **Manual on-device acceptance (TESTING.md §M1.2)**.

## M1.3 — UI/UX polish

- [x] Light / Dark / AMOLED / Dynamic Color themes (restrained M3).
- [x] Settings screen (theme mode, dynamic color, default font size — DataStore).
- [x] Diagnostics screen (versions, ABI, shell, session/PTY facts — read-only).
- [x] Keyboard layout/touch-target refinement; session tab polish; tablet layout.
- [x] Performance pass prepared: `yes` + CTRL+C, `seq 1 100000`, `find /` checklist (TESTING.md §5).
- [ ] **Manual on-device acceptance (TESTING.md §M1.3)**.

## Gate to M2

M2 starts **only after** M1, M1.1, M1.2, M1.3 are manually validated on a real
Android device (§27). M2 scope (research → implement):

- Linux userspace runtime decision (Termux-compatible vs Alpine/proot — see
  RESEARCH.md §3.3 W^X constraint and §6 packaging research; re-verify targetSdk
  policy at that time).
- Real package management (install/remove/update/search/info) with PocketShell
  UI sitting on top — never simulated.
- First installable CLI apps (Hermes/Git/Python/Node/… per catalog).
- Home "Explore CLI Apps" becomes a real catalog backed by the real installer.

## Later (unscheduled, do not start prematurely)

File manager · profiles · AI CLI management · development environments · code
editor · remote development.
