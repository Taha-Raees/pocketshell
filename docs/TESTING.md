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
- [ ] Regression guard (v0.2.1): ANY failure above must show a state + message
      in Diagnostics — the app must NEVER exit to the launcher from this flow.

### Emulator note (build sandbox)
Emulator-based verification of this flow was attempted and is currently
impossible in the build sandbox: only Android 11+ (API 30+) system images
expose `arm64-v8a` (required by the arm64-gated runtime), and the emulator
enforces a fixed ~6 GB userdata floor on those images (~7.2 GiB free at boot),
which cannot coexist with the Android SDK on the 9.9 GB sandbox disk. The
crash-containment itself is covered by JVM regression tests
(RuntimeCrashGuardTest — the incident's exact SecurityException through the
real installer pipeline); the on-device checklist above remains the M2.2 gate.
