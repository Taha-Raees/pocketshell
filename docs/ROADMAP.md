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

## M2 — Real Linux runtime (M2.0 – M2.4)

- [x] M2.0 baseline: 166/166 tests PASS before any change (strict §27/§28).
- [x] M2.1 research + architecture (docs/M2-RESEARCH.md, docs/M2-ARCHITECTURE.md):
      PRoot + Alpine minirootfs chosen; split-loader strategy verified against
      AOSP sepolicy primary sources; targetSdk kept at 36.
- [x] M2.2 runtime installation layer: download → verify → extract →
      configure → atomic promote; honest state machine; crash containment
      (RuntimeCrashGuard after the v0.2.1 device incident). **Device gate
      PASSED 2026-09-01** (TESTING.md §7): install → READY, honest numbers
      verified byte-level ("9.3 MB", 108 rootfs files).
- [x] M2.3 Linux shell: proot **v5.1.107.92** (@ 7266fb3e) + libtalloc 2.4.2
      compiled for all 4 ABIs and bundled via jniLibs;
      `RuntimeProcessLauncher` + `createLinuxSession` enter the guest on the
      existing PTY; sandbox rehearsal of the exact gate contract PASSED
      (scripts/rehearse_m23_gate.sh): guest `uname`, `uid=0(root)`, `hello`,
      release 3.24.1, BusyBox works — both loader modes.
      **Device gate PASSED 2026-09-01** (TESTING.md §8, Samsung SM-F711B):
      `uname; id; echo hello`, `cat /etc/alpine-release` → 3.24.1,
      `ls /usr/bin | head`, clean `exit`. (v0.3.1 fixed the tap-crash:
      extractNativeLibs + targetSdk 28; v0.3.2 fixed the guest linker death:
      LD_LIBRARY_PATH + argv[0].)
- [x] M2.4 real package management foundation: `AlpinePackageManager` runs
      the REAL `apk` (apk-tools 3.0.6) inside the guest through the same
      proot exec infrastructure — update/search/add/del/info, exit-code +
      `apk info -e -v` + `command -v` verification (no human-output state
      guessing), honest operation state machine, single-flight, dedicated
      background exec (never touches user PTY sessions), guest DNS repair
      (minirootfs ships no resolv.conf), curated 5-entry metadata-only
      catalog, Open-into-new-session launcher. Sandbox rehearsal PASSED
      (scripts/rehearse_m24_packages.sh); **device gate = TESTING.md §9**.
- [x] **M2.4 device gate (TESTING.md §9)** — **PASSED on device 2026-09-02**
      (Samsung SM-F711B, v0.4.3 screenshots: GNU nano 9.2 running in the
      Alpine guest, Repository fetch OK — 28546 distinct packages; v0.4.4
      screenshots: installed state visible in Explore AND Home). The v0.4.1→
      v0.4.4 chain fixed, in order: device-DNS reachability, the SELinux
      linkat neverallow, single-resolver DNS fragility, the installed-state
      batch-probe exit-code misread + the never-written Home registry, and
      the empty clipboard-paste callback.

## Later (unscheduled, do not start prematurely)

M2.5 CLI app catalog + installed-apps Home integration · file manager ·
profiles · AI CLI management · development environments · code editor ·
remote development.

### M2.5 (started 2026-09-02, v0.5.0)
- [x] Installed-apps Home integration (v0.4.4): Home renders exactly the
      catalog subset the real apk database confirms — M2-ARCHITECTURE §9
      contract realized (device-confirmed: Nano + Git cards).
- [x] Apk-capable guest shell (v0.5.0): interactive sessions drop /proc and
      share the app's apk cache — manual `apk update` / `apk add` works in
      the shell (was: SELinux "Permission denied" + stale 31-package cache).
- [x] Install any searched package (v0.5.0): search hits ranked by name
      match (nodejs first for "node") and installable with the honest
      pipeline; no executable promises for non-catalog packages.
- [ ] Remaining M2.5 candidates: file manager, profiles, richer per-app
      Home cards (launch metadata for non-catalog packages), development
      environments.

### M2.6 (2026-09-02, v0.6.0) — Linux compatibility & /proc architecture fix
- [x] Reproduce + document the M2.5 regression: /proc removed from ALL guest
      sessions → ps/top/htop broken (docs/M2.6-RESEARCH.md §1/§2).
- [x] Root cause verified from primary sources: apk-tools 3.0.x
      `is_proc_fd_ok()` → O_TMPFILE + linkat("/proc/self/fd/N") commit →
      AOSP `neverallow all_untrusted_apps file_type:file link` → EACCES →
      whole-download cancel, no fallback (identical in 3.0.6/3.0.8/master).
- [x] Options evaluated A–G (two profiles only, transport wrapper, nested
      proot, /proc/self/fd masking, apk 2 rollback, patched guest apk);
      chosen: one-byte checksum-pinned libapk patch + explicit profiles
      (docs/M2.6-RESEARCH.md §3/§4).
- [x] GuestApkCompat: hash-driven, idempotent installer/verifier for the
      patched guest apk library; honest Ready/NotApplicable/Failed states;
      asset verified against its pinned sha256 before anything is written.
- [x] GuestExecutionProfile INTERACTIVE_TERMINAL / PACKAGE_OPERATION on the
      SAME builder/launcher (no duplicated runtime); package profile
      refuse-guarded against /proc; tests pin no-drift.
- [x] Interactive sessions bind a REAL /proc again when the patch is
      verified; honest v0.5.0-shape degradation otherwise; Diagnostics rows
      ("apk fd-link patch", "Interactive /proc").
- [x] 292 tests/variant (584 executions) green; host rehearsal
      (rehearse_m26_proc.sh) green.
- [ ] Device gate §10 (Gates A–G: /proc, ps/top/htop, apk lifecycle, Node
      end-to-end, app-side install, interactive CLI, session isolation).
- [ ] Post-M2.6 candidates (per user direction): M2.7 session management +
      CLI app profiles, or curated CLI app catalog.

### UI — UI/UX redesign & design system (2026-09-03, v0.7.0-ui)
- [x] Written design contract BEFORE implementation: docs/UI-REDESIGN.md
      (direction, tokens, navigation, per-screen specs, launcher-detection
      rules, keyboard final spec, checkpoint plan).
- [x] UI.1 Design system "Quiet Aurora": tokens (color/typography/spacing/
      shape/motion) + reusable component kit (ui/components/).
- [x] UI.2 Navigation: typed Screen enum + dismissible drawer, hamburger on
      every screen, NO permanent tab bar; future destinations plug in as
      enum entries.
- [x] UI.3 Home: brand block + 2×2 launcher grid (Terminal/Linux Shell/
      Apps/Packages); CLI-utility launcher cards removed (they are
      packages); no project cards; honest launch-error banner + sessions.
- [x] UI.4 Terminal: session-pill chrome + overflow (confirm on close),
      framed-ink canvas; engine/repaint/blinker contracts untouched.
- [x] UI.5 Keyboard FINAL spec: top row Esc·Tab·arrows, bottom row
      icon-toggle·Ctrl·Alt·Space·Shift·Enter, Android IME toggled by the
      icon, Fn removed (arrows long-press = nav keys, Esc long-press =
      F1–F12 strip), modifiers visual-state-only.
- [x] UI.6 Packages restyle (logic verbatim) + launchable-app detection
      (hermes/opencode; live `command -v` probe; CLI tools excluded —
      test-pinned rules).
- [x] UI.7 Settings (Appearance / AI Assistant OpenRouter config — masked
      key, free-text model, honest storage note / About) + Diagnostics
      grouped cards (all facts and buttons preserved).
- [x] UI.8 Polish + accessibility pass (a11y labels on icon-only controls,
      44–48dp targets, touch/press states, consistent tokens).
- [x] Full suite 636 tests green; APK v0.7.0-ui (versionCode 17) built and
      verified (cert unchanged → in-place update).
- [ ] DEVICE GATE: TESTING.md §11 (navigation, keyboard sandwich + IME
      toggle, Apps detection honesty, Packages regression, Settings/AI,
      visual sweep) + §10 Gates A–H re-run after update.
