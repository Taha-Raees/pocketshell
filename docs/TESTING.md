# PocketShell — Testing

Per §28: **automated tests are necessary but not sufficient.** Every terminal
milestone requires (1) automated tests, (2) APK build, (3) installation,
(4) real-device testing, (5) the manual checklists below. A green build never
completes a milestone by itself.

## 1. Automated tests (run in CI / sandbox)

| Suite | Module | Origin | Covers |
|---|---|---|---|
| Upstream emulator suite (19 classes) | `:terminal-emulator` | Vendored unchanged | VT/ANSI, UTF-8, resize, scroll regions, styles, history, key encoding |
| PocketShell keyboard tests | `:app` | new | Modifier state machine (one-shot/lock), key→KeyEvent synthesis, symbol coverage table |
| PocketShell CLI app tests | `:app` | new | Registry empty-by-default, launcher verification gates, model serialization |
| Session manager tests | `:app` | new | Multi-session isolation of env/cwd/title, finished-session handling |

Run: `./gradlew testDebugUnitTest` (all modules).

**Sandbox limitation (honesty note, §31/§34):** the development sandbox has no
Android device or emulator. Instrumented tests and manual acceptance must be
performed by a human on real hardware; checklists below exist for exactly that.

## 2. Manual acceptance — M1 (terminal foundation)

Device prep: install debug APK; first launch creates HOME dir.

- [ ] Launch → Home shows exactly: Terminal card ("Open a real Linux shell"),
      "Installed CLI Apps — No apps installed yet", "+ Explore CLI Apps".
      No system commands appear as app cards (§2/§35).
- [ ] Tap Terminal → real shell prompt appears (mksh `$`).
- [ ] `echo hello` → `hello`
- [ ] `pwd` → HOME under app files dir
- [ ] `ls`, `ls -la` → sensible output
- [ ] `cd /`, `cd ~` navigation works
- [ ] `mkdir demo` → creates; `cd demo`; `touch f1` (toybox); `rm f1`; `cd ..`; `rmdir demo`
- [ ] `cat /proc/version` → kernel banner
- [ ] `clear` → screen clears
- [ ] `$ echo $TERM` → `xterm-256color`; `$ echo $HOME` → app home
- [ ] Close tab → session exits; back → app exits cleanly, no leaked process
      (`adb shell ps | grep <pid>` clean)

## 3. Manual acceptance — M1.1 (built-in keyboard)

No system keyboard may appear at any point (§7).

- [ ] Terminal opens with PocketShell keyboard visible by default.
- [ ] Letters a–z, A–Z (via SHIFT or double-tap lock) all type correctly.
- [ ] Number row 0–9; symbols page 1 and 2 cover the full required set:
      ~ ` ! @ # $ % ^ & * ( ) - _ + = [ ] { } \ | ; : ' " , . < > / ?
- [ ] Space, Enter, Backspace (incl. hold-to-repeat) correct.
- [ ] ESC, TAB, arrows, HOME, END, PGUP, PGDN, INS, DEL emit correct sequences
      (`showkey -a` style checks or `cat -v`):
      ESC → `^[`, TAB → `^I`, arrows → `^[[A/B/C/D` (app mode `^[OA`…), etc.
- [ ] F1–F12 produce application-keypad-independent sequences (`cat -v` shows `^[OP`… / `^[[24~`).
- [ ] CTRL: one-shot on tap; LOCK on double-tap; visual states obvious (§10).
      CTRL+C aborts `yes`; CTRL+D EOF; CTRL+Z suspends (`fg` resumes);
      CTRL+L clears; CTRL+A/E line home/end (readline); CTRL+W word delete.
- [ ] ALT works (e.g. ALT+b/f word motion); SHIFT+letters/numbers correct.
- [ ] FN layer: FN+1…0/-/= → F1–F12; FN+arrows → HOME/END/PGUP/PGDN; FN+Backspace → DEL.
- [ ] Locked modifier never sticks silently: lock badge visible; switching
      tabs clears modifiers.
- [ ] Phone layout ≤ 2 terminal-control rows (view area preserved, §11);
      tablet layout exposes full key set including F-row.
- [ ] Haptic feedback present; key press visual feedback immediate.

## 4. Manual acceptance — M1.2 (input reliability)

- [ ] **Terminal refresh (v0.1.1 regression, seen on device 2026-08-31): with
      the built-in keyboard visible, typed characters echo immediately — the
      view must never wait for a keyboard toggle / layout change to repaint.**
- [ ] **With the keyboard visible, command output (`ls`, `clear`, `seq 1 100`)
      repaints live; cursor blinks while idle.**
- [ ] In vim (toybox has none — use available interactive tool, e.g. `less`,
      `more`, `top`): navigation, insert/edit where applicable, ESC handling.
- [ ] `less /etc/fstab`-style paging: arrows, PGUP/PGDN, HOME/END, q quits.
- [ ] `top`: q quits, no garbled output after resize.
- [ ] tmux/htop **only if actually installed** — skip honestly otherwise (§29).
- [ ] Selection: long-press word → handles; copy → paste works (paste into `cat`).
- [ ] Double-tap selects word; triple-tap selects line; copy-mode indicators.
- [ ] Pinch in/out changes font size live; PTY reflows (`stty size` matches).
- [ ] Rotation: 4× rotate mid-`seq 1000` — no output loss, no duplicate process.
- [ ] Background → foreground (30 s): session continues; FGS notification shown.
- [ ] Two sessions side-by-side via tabs: isolated state; typing in A never
      appears in B; separate cwds/env verified via `$ cd /` in A then `$ pwd` in B.
- [ ] Hardware keyboard (if available): typing works; no double-input.

## 5. Manual acceptance — M1.3 (polish)

- [ ] Themes: light, dark, AMOLED (pure black), dynamic color (Android 12+)
      apply instantly; terminal area remains readable in all.
- [ ] Settings persist across restart (DataStore).
- [ ] Diagnostics screen shows true values only (§34): version, SDK int, ABI(s),
      shell path, session count, libtermux load status.
- [ ] Performance (§30): `yes` runs flat-out; CTRL+C stops instantly (≤1 frame
      lag). `seq 1 100000` completes without dropped scrollback crashes.
      `find /` streams without freezing UI thread. No input lag >1 keypress.
- [ ] Visual: no accidental fake elements; empty states honest.

## 6. APK verification (build machine)

- [ ] `./gradlew :app:assembleDebug` produces installable APK.
- [ ] `unzip -l` shows `lib/arm64-v8a/libtermux.so` (JNI present).
- [ ] 16 KB alignment: `llvm-objdump -p libtermux.so | grep LOAD` → max-page-size 16384 (NDK r27+ default).
- [ ] No permissions beyond zero-to-minimal set in merged manifest.

## 7. Manual acceptance — M2.2 (Linux runtime installation)

Prerequisite: v0.2.1-m2.2 or newer (v0.2.0-m2.2-wip had a crash on Install —
missing INTERNET permission + uncontained coroutine failure, fixed in 0.2.1).

> **GATE RESULT — PASSED 2026-09-01 (device recording Screen_Recording_20260901_004021):**
> NOT_INSTALLED → DOWNLOADING (519 KB → 3.8 MB honest progress) → EXTRACTING
> (250+ entries) → **READY**, no crash, no anomalies. Reported numbers verified
> byte-level against the pinned artifact: Runtime size "9.3 MB" =
> 9,700,988 apparent bytes (8.25 MiB real files + 1.0 MiB resolvable symlink
> targets, Android File.length() semantics) and "Rootfs files 108" = 84 real
> files + 24 resolvable symlinks (306 absolute guest symlinks correctly
> unresolvable outside a booted guest). Free space 123.7 GB consistent.
> Install/remove/airplane-mode recovery rows below remain re-testable but the
> gate itself is closed.

- [ ] Diagnostics → Linux runtime shows State = NOT_INSTALLED on first launch
      (arm64 device) and honest UNSUPPORTED_ABI on non-arm64 devices.
- [ ] "Install Linux environment": honest progress over YOUR network
      (~4 MB from dl-cdn.alpinelinux.org): DOWNLOADING → VERIFYING →
      EXTRACTING → CONFIGURING → READY.
- [ ] On READY: Distribution row shows "Alpine 3.24.1 (aarch64)", Rootfs files
      count > 0, Runtime size > 0.
- [ ] Kill the app mid-download/mid-extract; relaunch: no half-installed
      state — either clean NOT_INSTALLED (orphaned tmp cleaned) or honest
      retryable FAILED. Retry completes to READY.
- [ ] Retry from FAILED works (transient state cleaned, second attempt READY).
- [ ] "Remove runtime" on READY returns to NOT_INSTALLED; disk space freed.
- [ ] Airplane mode ON → Install: lands in FAILED with a readable message
      (never a crash); airplane mode OFF → Retry reaches READY.

## 8. Manual acceptance — M2.3 (Linux shell via proot) — GATE OPEN

Prerequisite: runtime READY (§7) and **v0.3.2-m2.3 or newer**.

> **v0.3.2 note (guest linker fix):** on v0.3.1 the session opened but the
> guest died instantly with `CANNOT LINK EXECUTABLE "--kill-on-exit": library
> "libtalloc.so" not found`. Root causes: the exec environment had no
> `LD_LIBRARY_PATH` (bionic never searches nativeLibraryDir), and argv had no
> argv[0]. v0.3.2 ships both. The app staying alive and printing that error
> was v0.3.1's crash-proofing working as designed.

> **v0.3.1 note (device crash fix):** v0.3.0 died instantly on tapping
> "Linux Shell" (extractNativeLibs=false → empty nativeLibraryDir → unhandled
> require() in the click handler) and targetSdk 36 could never exec the guest
> anyway (AOSP W^X). v0.3.1 ships extractNativeLibs=true + targetSdk 28
> (untrusted_app_27 — the Termux model) and a crash-proof launch path.
> Update over v0.3.0 (same signing cert); the installed runtime survives.

- [ ] Home shows a "Linux Shell" card; before install it reads the true
      runtime state and tapping it opens Diagnostics — never a fake session.
- [ ] **v0.3.1 regression guard:** remove the runtime in Diagnostics, tap
      "Linux Shell" → an honest error banner appears ON HOME with the reason
      and a Diagnostics shortcut. The app must stay alive (v0.3.0 died here).
- [ ] With runtime READY, tapping "Linux Shell" opens the terminal; the new
      tab is labelled "Alpine Linux" and shows a guest prompt.
- [ ] **GATE — split-loader + guest exec under real SELinux
      (untrusted_app_27):** run `uname; id; echo hello`:
      expected `Linux ... aarch64 ...` (guest view, not the Android kernel
      string), `uid=0(root) ...`, `hello`.
- [ ] `cat /etc/alpine-release` → 3.24.1; BusyBox applets work in the guest
      (e.g. `ls /usr/bin | head`).
- [ ] `exit` ends the guest cleanly; afterwards the System Shell (Terminal
      card) still starts and runs normally.
- [ ] Remove runtime in Diagnostics → Home card returns to the honest
      not-installed state; a new install reaches READY again.
- [ ] Regression guard (v0.2.1): ANY failure above must show a state + message
      in Diagnostics — the app must NEVER exit to the launcher from this flow.
      If the guest exec itself fails on a vendor ROM, the terminal shows
      `exec("...")` + the error and `[process exited]` — visible, not fatal.

### Emulator note (build sandbox)
Emulator-based verification of this flow was attempted and is currently
impossible in the build sandbox: only Android 11+ (API 30+) system images
expose `arm64-v8a` (required by the arm64-gated runtime), and the emulator
enforces a fixed ~6 GB userdata floor on those images (~7.2 GiB free at boot),
which cannot coexist with the Android SDK on the 9.9 GB sandbox disk. The
crash-containment itself is covered by JVM regression tests
(RuntimeCrashGuardTest — the incident's exact SecurityException through the
real installer pipeline); the on-device checklist above remains the M2.2 gate.

## 9. Manual acceptance — M2.4 (real apk package management) — GATE OPEN

Prerequisite: runtime READY (§7) and **v0.4.0-m2.4 or newer**.

> **Sandbox ≠ device:** the whole apk flow (update → search → add → verify →
> run → del) was rehearsed end-to-end on the x86_64 sandbox with the same
> apk-tools 3.0.6 and the same argv/env the app uses
> (scripts/rehearse_m24_packages.sh). The checklist below is the real gate.

- [ ] Update the app over v0.3.2 (same signing cert; runtime + any packages
      are kept).
- [ ] Diagnostics → **Check package environment** (explicit button, nothing
      runs on open): apk version banner (apk-tools 3.x), the two dl-cdn
      v3.24 repositories, package database present, Guest DNS configured
      (or "missing (repairs on first package operation)" — the first package
      operation repairs it in place; re-check afterwards shows configured).
- [ ] Explore CLI Apps (runtime READY): the five featured entries show the
      REAL state — all "Not installed" on a fresh runtime.
- [ ] **Install Nano**: honest stages only — Updating repositories →
      Installing → Verifying → "nano installed". No fake percent. The output
      tail shows real apk lines. (First run repairs DNS silently; network
      failures show the real apk error + Retry path, never "Something went
      wrong".)
- [ ] Cross-check in the Linux Shell: `apk info -e -v nano` → a real
      `nano-x.y-rZ` line; `command -v nano` → `/usr/bin/nano`.
- [ ] **Open Nano**: a NEW session tab opens; nano is real (visible in the
      session as the typed launch command). Type text, save with Ctrl+O,
      exit with Ctrl+X → you land back at the REAL guest shell prompt
      (`localhost:~#`). Arrow keys/Enter/ESC/Ctrl shortcuts work (M1
      keyboard).
- [ ] **Persistence**: close the app completely, reopen → Explore CLI Apps
      still shows Nano installed with its real version (discovered from apk,
      not remembered by the UI).
- [ ] **Uninstall**: Uninstall → stages → "nano removed"; card returns to
      Not installed; `apk info -e nano` in the guest exits non-zero.
- [ ] **Launcher protection**: after uninstall, "Open" is gone (card shows
      Install); any refused open (e.g. runtime removed first) shows an honest
      error — the app NEVER crashes or fakes.
- [ ] **Single-flight**: an Install and an Uninstall cannot run at once —
      the second attempt reports "another package operation is already
      running".
- [ ] Regression guards (§7/§8): normal Terminal works; Linux Shell works;
      install/remove-reinstall of the runtime still reaches READY.
- [ ] If the guest process dies mid-`apk add` (app killed): the next package
      operation just works (apk's own locking/journal keeps the database
      consistent; partial downloads are discarded by apk).
