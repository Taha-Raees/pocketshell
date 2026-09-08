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
| PocketShell command-app tests | `:app` | new | Phase 3.2: package/app separation (forbidden list), registry invariants, guest-driven classification |
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

## 9. Manual acceptance — M2.4 (real apk package management) — GATE PASSED 2026-09-02 (v0.4.3/v0.4.4); v0.5.0 M2.5 checks below

Prerequisite: runtime READY (§7) and **v0.4.1-m2.4 or newer**.

> **Sandbox ≠ device:** the whole apk flow (update → search → add → verify →
> run → del) was rehearsed end-to-end on the x86_64 sandbox with the same
> apk-tools 3.0.6 and the same argv/env the app uses
> (scripts/rehearse_m24_packages.sh, including the v0.4.1 cache binds). The
> checklist below is the real gate. v0.4.0 failed on-device with
> `DNS: transient error` / `Permission denied` — the fetch actually worked;
> it died at apk's O_TMPFILE→linkat("/proc/self/fd") download commit, which
> Android SELinux neverallows for untrusted apps (hardlink). v0.4.2 drops
> the /proc bind for package commands, so apk commits downloads via
> renameat instead — and the failure moved to DNS: v0.4.1–v0.4.2 wrote a
> DEVICE-ONLY resolv.conf (one hotspot gateway resolver + an unparseable
> zone-suffixed link-local), so when that one resolver timed out every
> fetch died with `DNS: transient error` (user screenshots 2026-09-02
> 08:13). v0.4.3 writes device resolvers FIRST + public fallbacks (musl
> MAXNS=3, parallel query, first answer wins) and refreshes managed files
> on every operation, so neither a flaky gateway nor a network change can
> wedge package management again.
>
> **M2.4 GATE PASSED ON DEVICE (2026-09-02 09:09–09:10, v0.4.3):** user
> screenshots show GNU nano 9.2 running in the Alpine guest, apk-tools
> 3.0.6-r0, combined Guest DNS and `Repository fetch: OK — OK: 28546
> distinct packages available`. What the same screenshots exposed next
> were two UI bugs — fixed in v0.4.4: the installed-state batch probe let
> the uninstalled LAST catalog package (python3) fail the whole loop and
> then discarded the good stdout (nano rendered "Not installed"), and
> Home's list read an M1-era DataStore registry nothing wrote ("No apps
> installed yet"). Plus: the terminal Paste action was wired to an empty
> client callback and did nothing. The v0.4.4 checks below pin all three.

- [ ] **v0.4.2 installs OVER v0.4.1 in place** (same pinned signing key as
      v0.4.1 — no uninstall needed; the runtime and any installed packages
      are kept). Only if you are still on v0.4.0: uninstall first (that cert
      break happened at v0.4.1, see CHANGELOG), then install and re-run §7:
      Diagnostics → Install Linux environment → READY (the 9.3 MB runtime
      comes back in one tap).
- [ ] **v0.4.3 installs OVER v0.4.2 in place** (same pinned signing key —
      no uninstall needed; the runtime stays). Its DNS repair upgrades the
      old device-only resolv.conf on the first package operation — no
      reinstall, no data loss.
- [ ] **v0.4.4 installs OVER v0.4.3 in place** (same pinned signing key —
      no uninstall needed; the runtime and installed packages stay; all
      fixes are in the Android layer).
- [ ] **v0.4.4 state sync (the 09:10 bug):** with nano already installed
      (any version's install — via a card OR typed `apk add nano` in the
      guest), Explore shows **Nano — Installed · <real version>** with
      Open/Uninstall (no more "Install"/"Not installed"), and **Home →
      Installed CLI Apps lists Nano** with its real version. The UI state
      comes from a fresh `apk info -e -v` probe on every visit — terminal
      installs made outside the app light up here too.
      **PASSED on device 2026-09-02 10:04** (v0.4.4 screenshots: Home lists
      Nano 9.2-r0 + Git 2.54.0-r0; Explore Nano card shows Open/Uninstall).
- [ ] **v0.4.4 paste:** copy text anywhere on the phone → in a terminal
      session long-press → select text → **Paste** → the clipboard text
      lands on the command line (and inside nano's buffer, bracketed-paste
      aware). Pasting with an empty clipboard does nothing and harms
      nothing. (**PASSED on device** — user: "I can copy paste".)
- [ ] **v0.5.0 apk-capable guest shell (the 10:03 bug):** update to
      v0.5.0, open **Linux Shell**, run `apk update` → it must finish with
      **no "Permission denied"** and a full count ("OK: 28546 distinct
      packages available" shape, NOT "2 unavailable … 31 distinct"); then
      `apk add nodejs npm` → installs. (`ps`/`top` inside the guest now
      honestly report they cannot read /proc — that is the documented cost
      of a working in-shell apk; see CHANGELOG 0.5.0.)
- [ ] **v0.5.0 search + install:** Explore → search `node` → **nodejs**
      appears at the TOP (name match ranking) → tap its **Install** →
      "nodejs installed"; the hit flips to "Installed · <version> — run
      'nodejs' from the shell". Cross-check in Linux Shell: `node --version`
      works.
- [ ] Diagnostics → **Check package environment** (explicit button, nothing
      runs on open): apk version banner (apk-tools 3.x), the two dl-cdn
      v3.24 repositories, package database present, **Guest DNS lists the
      device's resolver(s) FIRST plus the public fallbacks (1.1.1.1,
      8.8.8.8), capped at 3** with the source line "device resolvers first,
      public fallback (musl queries all in parallel)" (v0.4.3 — musl takes
      the first answer of the parallel query, so one dead resolver — e.g. a
      flaky hotspot gateway — can no longer block every fetch, which is
      exactly what the 2026-09-02 "DNS: transient error" screenshots
      showed), and **Repository fetch: OK** — the button runs ONE real
      bounded `apk update` and shows its honest outcome. (v0.4.2: the fetch
      commits via renameat — if you EVER see `Permission denied` here again,
      report the text; `DNS: transient error` on v0.4.3+ means ALL THREE
      resolvers failed — report that too, it becomes the next fix.)
- [ ] Explore CLI Apps (runtime READY): the five featured entries show the
      REAL state — all "Not installed" on a fresh runtime.
- [ ] **Install Nano**: honest stages only — Updating repositories →
      Installing → Verifying → "nano installed". No fake percent. The output
      tail shows real apk lines. (First run repairs DNS/workspace silently;
      network failures show the real apk error + stderr tail + Retry button,
      never "Something went wrong".)
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

## 10. Manual acceptance — M2.6 (real /proc + real apk + sysdata overlays + hardlink-capable extraction) — DEVICE GATE PENDING

Install v0.6.2-m2.6 in place over v0.6.1/v0.6.0/v0.5.0 (same signing
certificate). The guest rootfs does NOT need reinstalling — the fd-link patch
installs itself on the first session spawn. Architecture evidence:
docs/M2.6-RESEARCH.md. Sandbox rehearsal passed (scripts/rehearse_m262.sh,
FULL PASS 19/19); the device is the gate.

v0.6.2 adds two layers on top of the v0.6.1 wording fixes, both from the same
2026-09-02 device session:

- PRIMARY GATE (M2.6.12, the user-pinned set): /proc/stat, /proc/uptime,
  /proc/loadavg, /proc/version, /proc/vmstat and top. Files the kernel denies
  get a PROBE-GATED compatibility overlay at each spawn (probe first — real
  files are never overlaid; denied files get content derived from real host
  sources, attributed in /proc/version itself).
- M2.6.13: guest sessions run with proot's link2symlink extension
  (--link2symlink, enabled by default in Termux PRoot-Distro), so packages
  shipping HARDLINK entries (binutils, gcc, g++, …) extract correctly —
  the 2026-09-02 run failed exactly those 19 entries with EACCES
  (SELinux neverallow all_untrusted_apps file_type:file link).

### Gate A — /proc in the interactive guest (Linux Shell) — PRIMARY GATE
- [ ] Open Linux Shell (first spawn also installs the fd-link patch and
      writes the sysdata overlays — Diagnostics afterwards shows
      "apk fd-link patch: applied" and the "sysdata overlays" row).
- [ ] `ls /proc` → real guest-visible procfs. EXPECTED NOISE: `ls` stats
      every entry, and Android's SELinux policy denies this app getattr on
      kernel-internal nodes (kmsg, kcore, vmcore, kpage*, sched_debug,
      timer_list, sysrq-trigger, …), so those lines read
      "Permission denied" — that wall is REAL policy output, not a bug,
      and v0.6.2 does NOT overlay those (they are not standard
      compatibility files). The PASS signal is the readable tail: numeric
      pid entries, meminfo, cpuinfo, cmdline, uptime, loadavg, mounts,
      self, thread-self, sys, tty, fs, bus, irq, driver … (Samsung adds
      memsize/memextra etc.).
- [ ] `cat /proc/meminfo | head -3` → real values (GATE FILE — required;
      never overlaid — the kernel allows it, and probe-first real wins).
- [ ] `cat /proc/cpuinfo | head -5` → real values (GATE FILE — required).
- [ ] `cat /proc/stat` → parseable: an aggregate cpu line + one cpuN line
      per real core + intr/ctxt/btime/processes/procs_running/
      procs_blocked/softirq. btime is REAL (epoch boot time); the jiffies
      counters are documented zero placeholders (global counters are
      denied to apps). On kernels that ALLOW /proc/stat the REAL file
      shows instead — probe-first real wins, both outcomes are PASS.
- [ ] `cat /proc/uptime` → field 1 is the REAL seconds-since-boot
      (elapsedRealtime clock, includes deep sleep); field 2 (idle) is a
      documented 0.00 placeholder.
- [ ] `cat /proc/loadavg` → fields 1–3 are documented 0.00 placeholders
      (Android exposes no load-average source to apps); the tail
      "0/N PID" reflects the REAL hidepid-filtered pid set this app can
      see (its own process tree).
- [ ] `cat /proc/vmstat` → standard kernel counter-name skeleton, zero
      values (names are kernel facts; values are honest placeholders).
- [ ] `cat /proc/version` → ON KERNELS THAT ALLOW IT: the REAL kernel
      banner (never overlaid). ON DENYING KERNELS (device-observed
      2026-09-02, SM-F711B/One UI — proc_version not granted to apps
      targeting SDK 28): the v0.6.2 overlay —
      "Linux version <real release> (PocketShell sysdata overlay: kernel
      identity via uname(2); the kernel's own file is denied to apps by
      Android SELinux) <real build tail>". The release and build tail are
      the REAL uname(2) identity; the parenthetical plainly says what the
      file is. This supersedes v0.6.1's "informational only" stance:
      the file is now readable either way, and `uname -a` still works
      for comparison.

### Gate B — process tools (top is part of the PRIMARY GATE)
- [ ] `ps` → real process list (the app's own process tree, host pids,
      hidepid-filtered — see docs/M2.6-RESEARCH.md §4.3).
- [ ] `top` → opens, updates, redraws; `q` quits. (busybox top is batched:
      `top -b -n 2` also proves refresh.) EXPECTED under the overlay:
      the CPU% column reads ~0% because the kernel's global jiffies
      counters are denied and the overlay's are static placeholders —
      the PROCESS ROWS are real. htop renders the same way.

### Gate C — package manager in the SAME session (the M2.6 point)
- [ ] `apk --version` → apk-tools 3.0.6-r0.
- [ ] `apk update` → fetches OK (no "Permission denied").
- [ ] `apk search nano` → hits.
- [ ] `apk add htop` → installs; `htop` opens, updates, quits.

### Gate D — Node.js end-to-end (M2.5 validation carried forward)
- [ ] `apk add nodejs npm` → installs.
- [ ] `node --version` && `npm --version` → real versions.
- [ ] `mkdir -p ~/test-node && cd ~/test-node`
- [ ] `echo 'console.log("PocketShell Node works")' > index.js`
- [ ] `node index.js` → prints `PocketShell Node works`.

### Gate E — app-side installation (M2.5 must keep working)
- [ ] Explore → search "nano" → Install/Uninstall still honest, per-card
      "Working…", real apk execution, real installed state.
- [ ] Home still lists installed catalog apps with real versions.

### Gate F — interactive CLI regression
- [ ] `nano test.txt` → type, arrows, Enter, Ctrl+O, Ctrl+X — unchanged.
- [ ] `echo hello`, `pwd`, `ls`, `cd`, `mkdir/rm -rf`, `cat /etc/alpine-release`.
- [ ] `git --version` (if installed), `node --version` (after Gate D).
- [ ] Keyboard: CTRL+C/D/Z/L/A/E/W, TAB, ESC, arrows, HOME, END, PgUp/PgDn.

### Gate G — session isolation
- [ ] Start `top` in the Linux Shell; while it runs, install a package from
      Explore → top keeps updating, the terminal never freezes, the install
      completes.
- [ ] Quit top (`q`) → shell prompt returns.

### Gate H — hardlink extraction (M2.6.13; heals the 2026-09-02 broken state)
- [ ] In the Linux Shell: `apk fix` (or `apk add --force-refresh binutils gcc
      g++`) → completes with NO "failed to extract … Permission denied"
      errors. The 2026-09-02 run left binutils/gcc/g++ recorded but
      incomplete; the first v0.6.2 session re-extracts them.
- [ ] `gcc --version && g++ --version && ld --version` → real GNU
      toolchain banners (15.2.x).
- [ ] `ls -l /usr/bin/ld /usr/bin/gcc` → the cross-arch duplicates are
      SYMLINK chains, not hardlinks — that is link2symlink's emulation
      doing its job (the kernel never evaluates the denied link() call).
      Expected honest difference, not a defect; the binaries are
      byte-identical to the real ones (rehearsal-proven).
- [ ] From the app UI: Explore → install any package → still works
      (PACKAGE_OPERATION sessions carry --link2symlink too).

### Diagnostics (explicit button)
- [ ] "apk fd-link patch" → `applied — fd-link commit disabled (patched
      libapk verified)` after the first session spawn (CONFIRMED on device,
      2026-09-02).
- [ ] "Interactive /proc" → `interactive sessions bind /proc (real process
      tools). Host procfs: kernel-internal entries show 'Permission
      denied' — Android SELinux policy, expected` (v0.6.1 wording).
- [ ] "sysdata overlays" (v0.6.2, read-only probe — the button never
      writes) → on this kernel expect `overlay at next spawn: stat,
      uptime, loadavg, version, vmstat (kernel-denied; content from
      uname(2)/clock, attributed in /proc/version); N of 5 probed files
      are real`. A kernel granting some of the five shows them as real;
      a kernel granting all five shows `none — kernel grants all 5
      probed files (real data wins)`.

### Expected on-device (NOT bugs — seen and confirmed 2026-09-02, SM-F711B)
- `ls /proc` prints a wall of `Permission denied` lines for kernel-internal
  entries (kmsg, kcore, vmcore, kpage*, sched_debug, timer_list,
  sysrq-trigger, …) BEFORE listing the readable set. Cause: the guest's
  /proc IS the Android host procfs (the design — no re-export, no
  simulation), and the kernel's SELinux policy for this app genuinely
  denies getattr on those nodes. busybox `ls` reports each denial. The
  readable tail (pid dirs, meminfo, cpuinfo, cmdline, uptime, loadavg,
  mounts, self, thread-self, sys, tty, fs, bus, irq, driver, plus Samsung
  extras like memsize/memextra/uid_0_procstat) is the real, working set.
  `ps`/`top`/`htop` do NOT print this noise — they silently skip
  unreadable entries by design.
- `cat /proc/version` → "Permission denied" on this Samsung/One UI kernel
  (proc_version is not granted to apps targeting SDK 28). v0.6.2 covers
  this with the probe-gated sysdata overlay (Gate A) — the denial is the
  REASON the overlay exists, and on kernels that grant proc_version the
  REAL banner shows instead (never overlaid).
- v0.6.2 hardlink difference: packages that ship hardlink entries
  (binutils/gcc/g++) land with those names as symlink chains
  (`ls -l /usr/bin/ld`), not hardlinks — proot's link2symlink emulation
  exists because Android SELinux neverallows link() to untrusted apps.
  Binaries are byte-identical to the real files; `du` counts each copy.
- Overlay CPU% columns (top/htop) read ~0%: the overlay's /proc/stat
  jiffies are documented zero placeholders (the kernel denies the global
  counters to apps), so deltas are zero. Process rows, memory values and
  uptime remain real.
- Pids seen in /proc are HOST pids, filtered by hidepid=2 to this app's
  own processes. This is documented process semantics
  (docs/M2.6-RESEARCH.md §4.3), not a defect.

### Honest degradation (only if it happens — report it if seen)
- If "apk fd-link patch" shows `NotApplicable` (e.g. an in-guest
  `apk upgrade` replaced the library), interactive sessions run WITHOUT
  /proc (v0.5.0 shape) and Diagnostics says so; reinstall the runtime from
  Diagnostics to restore the pinned rootfs. apk keeps working either way.
  (No-/proc sessions carry NO sysdata overlays either — the builder
  refuses to fabricate a partial procfs; pinned by tests.)
- If a sysdata overlay cannot be written or verified at spawn, that ONE
  file stays unbound (its real file remains denied — the tool reading it
  errors honestly) and Diagnostics' sysdata row lists it as FAILED;
  the other overlays are unaffected. The session itself never fails
  because of an overlay.

## 12. Manual acceptance — Phase 3.1 (Terminal Experience Redesign, v0.7.0-m3.1) — DEVICE GATE PENDING

Scope reminder: this gate covers the TERMINAL SCREEN ONLY (chrome, tabs,
workspace, keyboard). Everything else must look/behave exactly as v0.6.2 —
if anything outside the terminal changed on device, that is a defect.
Install note: versionCode 18 updates in place over 16 (v0.6.2) and 17
(discarded v0.7.0-ui); same cert — app data survives.

### 12.1 Visual (Midnight Sapphire)
- [ ] Terminal page is blue-dark everywhere: chrome `#101B30`, strip `#0D1730`,
      canvas `#080F1D`, deck `#131F38` — NO pure-black flat rectangle anywhere
      on the page (AMOLED theme included: terminal page keeps its navy look).
- [ ] Terminal text renders in a JetBrains Mono look (distinct 0/O, 1/l/I);
      no ligature merging (`->` stays two characters).
- [ ] Cursor is a Sapphire block, blinking; text on it is readable (inverted).
- [ ] Prompt/paths from `ls --color` / programs show the new ANSI palette;
      nothing unreadable, no neon.
- [ ] Chrome header shows the live session title; back arrow returns Home.
- [ ] Status bar area is covered by the chrome surface (edge-to-edge top);
      no content collides with the status bar or camera cutout.
- [ ] Rest of the app (Home/Explore/Settings/Diagnostics) unchanged vs 0.6.2.

### 12.2 Session tabs
- [ ] Tabs are editor-style (rounded TOP corners, flat bottom): NO pill boxes.
- [ ] Active tab: taller, canvas-colored, 2.5dp Sapphire top hairline; the
      strip's bottom hairline is visibly interrupted (cut) beneath it.
- [ ] Inactive tabs: recessed, dim text, quiet right separator; tapping
      switches sessions; output keeps flowing in the background session.
- [ ] Close ×: closes that session (real process killed — expected honesty);
      `+` creates a new session; tabs scroll when many; "(exited)" shows on
      finished sessions.

### 12.3 Keyboard layout (verify EXACTLY)
- [ ] TOP accessory row, left→right: `Esc` `Tab` … grouped arrow panel
      `← ↑ ↓ →` on the right; NO Ctrl/Alt/Shift/Fn on the top row.
- [ ] QWERTY body: `1..0` / `q..p` / `a..l` / `?123 z..m ⌫` /
      `- / : ; , . $ ' " @` (portrait); holding a digit shows an "F#"-style
      bubble above the key.
- [ ] BOTTOM accessory row, left→right: `[⌨ icon]` `Ctrl` `Alt` `Space(wide)`
      `Shift` `Enter(⏎, accent-filled)`; the ⌨ toggle is icon-only — no
      ON/OFF text, no label — and stays far-left at all times.
- [ ] `?123` → symbol page (`!@#$%^&*()` / `` ~ ` { } [ ] \ | = + `` /
      `< > ? _ INS DEL` / `ABC HOME END PGUP PGDN ⌫`); `ABC` returns.

### 12.4 Keyboard behavior
- [ ] Typing letters/digits/symbols reaches the shell (echo, paths, flags).
- [ ] `⌨` toggle: collapses only the QWERTY body; top+bottom accessory rows
      remain; terminal gains the space; toggle position never moves; tapping
      the terminal expands the body again.
- [ ] Modifiers: tap Ctrl → one-shot (accent-tinted + outline); next key
      consumes it; second tap locks (accent fill + dot); third tap unlocks.
      Same machine for Alt and Shift.
- [ ] Shift: one-shot uppercases the next letter; locked = caps; Shift+digit
      yields the shifted symbol.
- [ ] Fn long-press: hold `1`…`0` → F1…F10 bubble, release sends F-key
      (e.g. `tput clear`-style apps, `clear`, or `vim` help keys); a quick
      tap still types the digit; sliding off the key cancels the F-key.
- [ ] ⌫ repeats with acceleration; arrows repeat (held ↑ recalls shell
      history); Esc/Tab/Space/Enter all act immediately.
- [ ] Haptic tick on every key press.

### 12.5 Modifier combinations (real terminal semantics)
- [ ] Ctrl+C interrupts a running command.
- [ ] Ctrl+D ends a session / closes the shell.
- [ ] Ctrl+L clears the screen.
- [ ] Ctrl+A / Ctrl+E jump to line start / end (readline).
- [ ] Ctrl+W deletes the previous word.
- [ ] Alt+key reaches the shell (e.g. Alt+. in bash inserts last arg —
      if the guest shell supports it; at minimum ESC-prefixed byte verified
      via `cat -v` then Alt+key showing `^[key`).
- [ ] Shift+Tab emits reverse-tab (`cat -v` shows `^[[Z`).

### 12.6 Linux regression set (redesign must not have broken the runtime)
- [ ] `apk update` succeeds (repository fetch OK).
- [ ] `node --version` prints the installed Node version.
- [ ] `hermes --version` prints the Hermes version (M2.6 Gates A–H
      environment still valid; UV_LINK_MODE=copy workaround unchanged).
- [ ] Tab completion works (`ls /usr/bin/lo` + Tab → locals...); terminal
      resize on rotate/keyboard-toggle keeps the prompt visible and correct
      (PTY TIOCSWINSZ path).
- [ ] Copy/paste + text selection still work (long-press selection upstream).
- [ ] Pinch changes font size; new size survives tab switches.

### 12.7 Responsive / performance
- [ ] Landscape: body compresses to 4 rows; no overlap; terminal still
      visible above the deck.
- [ ] Tablet width (≥600dp): 14/15-column rows; same identity.
- [ ] No dropped frames while typing fast; no janky animations; cursor
      blink smooth.

### Known, documented limitation (honesty note)
- Keys are gesture-driven (press/hold/repeat engine); TalkBack announces
  every key (role + description + modifier state), but TalkBack double-tap
  activation does not type characters — the same tradeoff Termux makes.
  Modifier buttons, tabs, +, close, and the ⌨ toggle ARE standard
  accessibility-activatable controls.

---

## 13. Manual acceptance — Phase 3.2 (Home / OS Launcher + Command Apps, v0.7.0-m3.2) — DEVICE GATE PENDING

Prep: install the versionCode 19 APK in place over 18 (same cert). Phase 3.1
gate (§12) must still pass afterwards — the terminal is untouched by this
phase and any regression there is a 3.2 defect.

### 13.1 Launcher identity & honesty (the core 3.2 assertions)
- [ ] Home opens on the Midnight Sapphire launcher: blue-dark page (NO pure
      black), drawn brand mark + mono "PocketShell" wordmark + tagline, no
      app bar, no bottom navigation bar.
- [ ] With packages installed (nano, git, python, node, htop — whatever the
      device already has): NONE of them appear on Home. No "Installed CLI
      Apps" section exists anymore.
- [ ] If `hermes` is available in the guest (M2.6 install + UV_LINK_MODE=copy
      environment): a "Hermes Agent" launcher tile appears on Home
      automatically. If it is NOT available: no tile and no fake entry.
- [ ] Fresh runtime (or before any command app exists): the empty state reads
      "Your tools will appear here" with the "Explore packages" quiet action —
      never "No CLI apps installed".
- [ ] Airplane mode (or stopped runtime): Home does NOT claim "no apps" —
      either the last real grid stays with "availability could not be checked
      right now", or the honest checking/empty state shows after a real probe.

### 13.2 Environment launchers
- [ ] Terminal tile: wears the terminal canvas color, shows the drawn prompt
      mark and a live "N running" chip matching the real session count; tap
      opens/reuses a real session (existing behavior).
- [ ] Linux tile: shows "Alpine Linux · ready" when READY and enters the
      guest on tap; with no/failed runtime it shows the honest state line and
      routes to Diagnostics on tap (never a fake launch).

### 13.3 Command app launch flow
- [ ] Tap the Hermes tile → the spinner state shows on the tile → the app
      lands in a NEW terminal session where `hermes` is running (the typed
      command visible in scrollback; the user never typed it).
- [ ] Exiting the app returns to the guest shell prompt (same session).
- [ ] After uninstalling/breaking the binary, the tile disappears at the next
      Home visit; tapping a stale tile is impossible or fails with an honest
      banner (verify-then-launch).

### 13.4 Floating quick actions
- [ ] One custom Sapphire control bottom-right; position never moves; + → ×
      rotation; scrim dims the page; labeled chips emerge upward (New
      Terminal, New Linux session [only when READY], available command apps).
- [ ] New Terminal always creates a FRESH session; New Linux session opens a
      guest login shell; command-app chips launch the right command.
- [ ] Dismiss via scrim tap, × tap, and system Back (no navigation change).
- [ ] During a session spawn the session chips disable (no double-spawn).

### 13.5 Sessions continuation area
- [ ] Compact rows (≤4 + "+N more in Terminal"); green dot only for live
      processes; exited sessions dimmed with "(exited)"; tap returns.

### 13.6 Responsive / polish
- [ ] Phone portrait: 3-column app grid; comfortable spacing; nothing
      stretches edge-to-edge except full-bleed background.
- [ ] Tablet/foldable (Galaxy Tab S7 ≥600dp): 4+ columns, content capped
      ~720dp and centered — phone cards are NOT stretched across the screen.
- [ ] Status bar: light icons on Home in BOTH light and dark system themes;
      Settings/Packages screens keep readable icons in their own theme.
- [ ] No blur, no continuous animation; the launcher feels instant; scrolling
      is smooth; FAB cluster motion 150–220ms with no bounce.

### 13.7 Phase 3.1 regression (must be completely unaffected)
- [ ] Terminal chrome/tabs/canvas/keyboard identical to the §12-accepted
      state; keyboard final layout unchanged; Ctrl+C/D/L/A/E/W, Alt+key,
      Shift+Tab all still correct.
- [ ] Linux regression set from §12.6 still passes (apk update, node
      --version, hermes --version).
- [ ] Explore/Packages screen behavior unchanged (install/uninstall/open
      still work — Home no longer shows their results, but the screen is
      byte-identical in behavior).

## 14. Manual acceptance — Phase 3.3 (Home & System UI Redesign, v0.7.0-m3.3) — DEVICE GATE PENDING

The redesign is visual architecture + Home interaction cleanup. Backend
(command discovery, probing, verify-then-launch, PTY) is untouched — §13's
functional checks re-run as a regression; this gate adds the STRUCTURE checks.
Design contract: `docs/PHASE-3.3-DESIGN.md`.

### 14.1 The "no boxes" sweep (the core gate)
- [ ] No bordered rounded container around: the tools section, the empty
      state, session rows, section headers, or the footer link. These regions
      sit directly on the canvas, separated by hairline dividers.
- [ ] Surfaces exist ONLY for: Terminal tile, Linux tile, CLI Apps menu,
      launch-error banner, FAB + chips, a pressed row/tile. Anything else
      drawing a box is a defect.
- [ ] No card inside a card anywhere on Home; nothing exceeds 16dp radius.
- [ ] No ghost/placeholder icons anywhere.

### 14.2 Foundations
- [ ] Terminal and Linux tiles are borderless tone-step surfaces (Terminal
      darker/canvas, Linux lighter/chrome), 14dp radius; no accent border.
- [ ] Terminal descriptor reads "Native shell" with NO truncation; the
      running count is plain mono text (no chip box), correct count.
- [ ] Linux READY: `Alpine · ready` in Sapphire + tiny ready dot; tap enters
      the guest. NOT_READY: honest state + `Diagnostics`; tap routes to
      Diagnostics.

### 14.3 CLI Apps menu (the ONE CLI control)
- [ ] `CLI Apps ▾` appears in the header area ONLY when at least one command
      app is available; with none installed there is NO dead trigger.
- [ ] Menu opens under the trigger: chrome surface, compact rows — monogram
      plate + app name + dim command (`hermes` etc.). No logos, no dialog.
- [ ] Only actually-available apps are listed (matches §13 honesty checks:
      nano/git/python can never appear here).
- [ ] Row tap launches the app into a dedicated guest session (same pipeline
      as the grid tile); scrim/outside tap/Back dismiss without navigation.

### 14.4 Tools grid + empty state
- [ ] "Your tools" label; apps render as icon (52dp borderless monogram
      plate) + label — NOT cards; surface only while pressed.
- [ ] Empty: `No CLI apps yet.` + one sentence + `Explore packages` link —
      three quiet lines, NO container. Exactly ONE "Explore packages" visible
      in this state (the footer link is absent).
- [ ] With apps present: footer shows a single quiet `Packages` link and the
      empty-state link is gone — still exactly ONE packages affordance.
- [ ] Checking state: one quiet spinner line. Probe failure: one dim honest
      line (never a fake "none"); with apps present the failure footnote stays.

### 14.5 FAB
- [ ] Exactly TWO creation actions: New Terminal, New Linux session (READY
      only). NO command apps, NO icon circles, NO logos in the chips.
- [ ] Tap → chips; tap again → runs; scrim/×/Back dismiss; disabled during
      spawn; 150–220ms motion, no bounce.

### 14.6 Sessions, responsive, regression
- [ ] Session rows are flat (dot + label + `#id`, hairline dividers); pressed
      row is the only surface; green dot ONLY for live processes; tap returns.
- [ ] Phone: 3-column grid; tablet/foldable: 4/6 columns, 720dp cap, CLI menu
      and launchers stay proportioned (nothing stretches).
- [ ] Phase 3.1 regression: terminal chrome/tabs/keyboard identical to §12
      state; §12.6 Linux set still passes.
- [ ] Phase 3.2 regression: §13.1–13.3 honesty checks (packages never on
      Home, Hermes iff available, tap-launch), §13.5 sessions behavior.

## 15. Manual acceptance — Phase 3.4 (Registry Expansion + System Pages, v0.7.0-m3.4) — DEVICE GATE PENDING

One registry data fix (the user-reported "installed kilocli doesn't show up")
plus the Phase 3.3 guidelines applied to the non-Home screens. No pipeline
changes — §12/§13/§14 re-run as regressions. Contract:
`docs/PHASE-3.4-DESIGN.md`.

### 15.1 Kilo Code appears (the fix)
- [ ] With `kilo` installed in the guest (`npm i -g @kilocode/cli` inside a
      READY runtime): revisiting Home surfaces a **Kilo Code** entry under
      "Your tools" AND in the `CLI Apps ▾` menu — within one Home revisit, no
      app restart needed (the probe re-runs on Home-visible-with-READY and
      after package operations).
- [ ] Tap launches a dedicated guest session running `kilo` (same
      verify-then-launch pipeline as every other command app).
- [ ] `npm uninstall -g @kilocode/cli` (or removing the binary) → the tile
      disappears on the next probe. Never renders when the command is absent.
- [ ] The other expansion agents (codex/qwen — and the M7.1-era entries
      gemini/aider that have since LEFT the defaults) do NOT appear unless
      actually installed — expansion entries are probe-gated like every app.
- [ ] Pre-existing entries unchanged: Hermes still appears iff available;
      nano/git/python still NEVER appear.

### 15.2 Packages screen
- [ ] Title reads "Packages".
- [ ] With the runtime NOT installed: the state is inline text (three quiet
      lines, NO card/container) and carries a working `Open Diagnostics`
      accent link that actually routes to Diagnostics.
- [ ] With the runtime READY: search, featured catalog cards (per-package
      surfaces = real objects — unchanged), install/uninstall/open all behave
      exactly as §9/§13.

### 15.3 Settings
- [ ] Tapping ANYWHERE on a theme row (label included) selects that theme;
      the radio dot only reflects state. Rows are ≥48dp tall.
- [ ] Tapping anywhere on the "Use wallpaper-based colors" row toggles the
      switch; the switch itself also works.
- [ ] Font-size slider unchanged; no visual restyle of this screen.

### 15.4 Diagnostics
- [ ] Sections read, in order: **System** → **Linux runtime** →
      **Package environment**, each = full-width divider + header + plain
      label/value rows (no internal row dividers).
- [ ] Values, colors (ok=primary / fail=error), install/remove/check buttons,
      and the explicit check-only package gate are unchanged (§10 set).

### 15.5 Regressions
- [ ] §14 (Phase 3.3 Home structure) unchanged — especially: Home itself
      gained NO new elements from this phase; kilo only ADDS entries to the
      existing tools grid/menu.
- [ ] §12 Phase 3.1 keyboard/terminal set still passes; §13.1–13.5 command
      app + sessions behavior still passes.

## 16. Manual acceptance — Phase 3.6 (Procfs Contract, v0.7.0-m3.6) — DEVICE GATE PENDING

The environment-initialization fix for the 2026-09-04 "kilo ENOENT" report
(`/proc` silently missing after an in-guest `apk upgrade`). Contract:
`docs/PROCFS-CONTRACT.md`. Install the vc23+ APK, open a FRESH Linux
session, and run the whole gate WITHOUT ever typing a manual mount command.

### 16.1 Procfs is there, automatically
- [ ] `ls -ld /proc` → real directory, populated (not empty).
- [ ] `ls /proc/self` → lists the shell process's own procfs entries.
- [ ] `cat /proc/version` → prints the kernel banner (no ENOENT).
- [ ] `readlink /proc/self/root` → `/`.
- [ ] `cat /proc/cpuinfo`, `cat /proc/meminfo` → real content.
- [ ] `ps` → lists processes (the app's own tree — hidepid=2 isolation is
      expected; kernel-internal entries like `/proc/kmsg` may show
      "Permission denied" — that is Android SELinux, documented, not a bug).

### 16.2 The regression flow (exactly the user's scenario)
- [ ] In the guest: `apk update && apk upgrade` — let it replace libapk if
      it wants to; then CLOSE the session and open a NEW one.
- [ ] The new session still has the FULL `/proc` set from 16.1 (this is the
      exact step that used to kill procfs — the self-repair must have
      re-applied the fd-link patch to the upgraded library).
- [ ] `apk add --no-cache libstdc++ libgcc` (or any package) inside the
      SAME /proc-bound session → downloads commit fine (the fd-link gate is
      patched; no "Permission denied" download failures).
- [ ] Diagnostics → "Check package environment": the apk fd-link row reads
      "fd-link gate disabled — … self-repair applied" (or asset-hash match
      on un-upgraded rootfs); the Interactive /proc row reads
      "every Linux session binds /proc unconditionally (v0.7.0-m3.6)".

### 16.3 Kilo without any manual mount
- [ ] `kilo --version` → version banner (no spawn ENOENT, no TUI worker
      error). If the XDG dirs do not exist yet, create them once
      (`mkdir -p /root/.local/state /root/.local/share /root/.config`) —
      that is the CLI's own requirement, unrelated to procfs.
- [ ] `kilo` → the TUI STARTS. No
      `ENOENT: no such file or directory, realpath …` anywhere.
- [ ] Tapping the Kilo Code tile on Home (m3.5 argv launch) → dedicated
      session with the Kilo TUI running.

### 16.4 Regressions
- [ ] §12 (terminal/keyboard), §13 (command apps + sessions), §14 (Home),
      §15 (system pages) still pass.
- [ ] Package operations from the UI (Explore search / install / uninstall)
      still pass §9 — they run the no-/proc PACKAGE_OPERATION profile by
      design and must be untouched.

---

## 17. Manual acceptance — Phase 4 (Companion, v0.7.0-m4.0) — DEVICE GATE PENDING

Prereq: install vc24 IN PLACE over vc23; existing runtime + Kilo untouched.
Companion starts collapsed: the ONLY visible element is the bottom handle
bar (no floating button, no text).

### 17.1 Login persistence (the reason Companion exists)
- [ ] Settings → Companion websites → quick-add "ChatGPT" → Save
      (fields pre-filled, still fully editable).
- [ ] Pull the handle up → the Companion surface rises from the bottom;
      ChatGPT loads (its own mobile web UI, no PocketShell chrome around it).
- [ ] Log in normally with the real account.
- [ ] Close PocketShell completely (recents swipe).
- [ ] Reopen PocketShell → pull Companion up → STILL LOGGED IN.
- [ ] Settings → Companion → Default Companion = ChatGPT.

### 17.2 Drag experience (smooth as silk, gestures never fight)
- [ ] Drag the handle up/down through MANY heights: the surface follows
      the finger 1:1 with no jump on grab and no reflow of the page
      mid-drag (one resize on release).
- [ ] Release near half / near-full → gentle snap to the anchor; release
      elsewhere → stays exactly there; kill + reopen → height restored.
- [ ] Scroll the webpage itself → the page scrolls, the Companion does
      NOT resize (only the handle zone resizes).
- [ ] Expand near full-screen → the handle is still reachable at the top;
      drag it down → collapses again. Back with no web history also
      collapses.

### 17.3 Tabs
- [ ] Add a second Companion (GitHub) via "+" → tab strip shows both,
      inverted editor style (active tab opens into the content).
- [ ] Switch ChatGPT → GitHub → ChatGPT: NO reloads, ChatGPT conversation
      exactly where it was; scroll positions preserved.
- [ ] Close a tab (✕ on the selected tab) → neighbor becomes active.
- [ ] Reopen the closed Companion via "+" → fresh load, still logged in
      (cookies live in the web profile, not the tab).

### 17.4 File upload
- [ ] In ChatGPT, attach a file → the normal Android picker opens.
- [ ] Pick a PDF/image → upload proceeds (single-file Phase 4 scope).

### 17.5 Navigation
- [ ] Navigate inside a site (e.g. GitHub → a repo) → press Android Back →
      webpage goes back (NOT out of the screen).
- [ ] At the page's root, Back again → Companion collapses (screen stays).
- [ ] Back once more → normal PocketShell navigation (e.g. Terminal → Home).
- [ ] Open a mailto:/tel: link → the system resolves it (or an honest
      Toast appears); no silent breakage.

### 17.6 Performance
- [ ] Open a heavy modern site; scroll, switch tabs, resize repeatedly —
      watch for jank, crashes, surprise reloads.
- [ ] With several tabs open, background the app under memory pressure →
      return: active tab intact; background tabs may rehydrate on demand
      (documented strategy, never a crash).

### 17.7 PocketShell regression (the workspace must not move)
- [ ] Return to Terminal → terminal, custom keyboard, PTY input all normal.
- [ ] Linux environment works: `apk update` inside the guest.
- [ ] Command apps still launch from Home tiles (argv transport intact).
- [ ] Home / Packages / Settings / Diagnostics visuals unchanged; Settings
      now carries the Companion entry row.

## 18. Manual acceptance — Hotfix m4.0.1 (startup decoupled from WebView provider health, v0.7.0-m4.0.1) — DEVICE GATE PENDING

Context: m4.0 (vc24) crashed on EVERY launch on a Samsung/microG device
whose Android System WebView had just been updated — Samsung Device Care
offered "Uninstall WebView updates?". Root cause: `CookieManager.getInstance()`
during `Application.onCreate` loaded the entire WebView provider before any
UI; a broken provider killed every start. m4.0.1 (vc25) makes Application
startup WebView-free and guards every provider touch.

Prereq: install vc25 IN PLACE over the crashing vc24 (same cert; app data
including Companion logins survives). Do NOT uninstall WebView updates
first — the hotfix must start cleanly regardless.

### 18.1 Startup (the reported bug)
- [ ] PocketShell opens normally to Home — no crash, even though the
      device's WebView package is the same one Samsung blamed.
- [ ] Launch → kill → relaunch 3×: starts every time, no Device Care
      crash dialog reappears for PocketShell.
- [ ] All pre-existing screens normal: Terminal (PTY, keyboard, tabs),
      Home tiles, Explore, Diagnostics, Settings.

### 18.2 Companion degradation (only if the WebView stays broken)
- [ ] Pull the Companion handle up → minimal Midnight notice
      "Companion unavailable / Android System WebView is missing or
      crashing on this device…" — no crash, no blank panel, handle
      still drags, Back still collapses.
- [ ] Terminal keeps working while the notice is up (switch screens,
      run commands).

### 18.3 Companion recovery (healthy WebView path)
- [ ] Update/repair Android System WebView (Play Store) or accept the
      Samsung rollback, restart PocketShell.
- [ ] Pull the handle up → Companion loads normally (§17.1 behavior);
      previously logged-in site still logged in (cookies live in the
      app's private web storage, untouched by this update).

### 18.4 Regression
- [ ] §17 spot-check: drag 1:1 without page reflow, tab switch without
      reload, file upload, Back = web history → collapse → navigation.
- [ ] §12–§16 spot-check: fresh Linux session shows /proc (`cat
      /proc/version`, `ps`), `apk update` in-guest, kilo launches.

## 19. Manual acceptance — m4.0.2 (honest Companion failure surfaces, v0.7.0-m4.0.2) — DEVICE GATE PENDING

Context: after m4.0.1 the device showed the ChatGPT Companion tab with a
PURE WHITE canvas — no error, no explanation. m4.0.2 (vc26) makes the
canvas report main-frame load failures and dead renderers with the real
cause + installed WebView version + Retry.

Prereq: install vc26 IN PLACE over vc25 (same cert; data survives).

### 19.1 White-canvas follow-up (the reported case)
- [ ] PocketShell starts; pull the Companion up with the ChatGPT tab.
- [ ] If the page cannot load: the canvas shows the dark card
      "Page didn't load" + the real error string + "Android System
      WebView <version>" + the network/VPN hint. NO white rectangle.
- [ ] If the renderer dies: the canvas shows "Page renderer crashed" +
      the WebView version + the update/rollback hint; PocketShell does
      NOT die.
- [ ] If the page loads normally: no card — the site renders.
- [ ] Retry: tap it → the tab's WebView is recreated and the URL reloads.
- [ ] Note the displayed WebView version and report it.

### 19.2 Isolation (identify which cause it was)
- [ ] Settings → Companion → add a second Companion "example.com".
- [ ] Switch tabs: example.com renders (static page works on ANY WebView)
      → network + WebView OK; chatgpt.com alone failing points at site
      requirements (VPN/region or a too-old WebView for its JS).
- [ ] example.com ALSO blank/failing → the WebView build or the network
      is the problem (update/roll back WebView; check the VPN).

### 19.3 Regression
- [ ] §18 startup checks still pass (starts 3× with a broken WebView).
- [ ] §17 spot-check: drag 1:1, tab switch without reload, file upload,
      Back = history → collapse → navigation.

## 20. Manual acceptance — m4.0.3 (keyboard everywhere + render-stall honesty + Companion picker, v0.7.0-m4.0.3) — DEVICE GATE PENDING

Prereq: install vc27 IN PLACE over vc26 (data survives; do not uninstall).
All prior Companion behavior (§17/§18/§19) must keep working.

### 20.1 Keyboard for BOTH surfaces
- [ ] Terminal screen, Companion raised (any height): tap a text field in
      the Companion page, then type on the PocketShell deck — characters
      appear IN THE PAGE (search box, chat input).
- [ ] No system keyboard (GBoard) appears while the deck is up.
- [ ] Tap the terminal canvas above, type — characters go to the SHELL
      again (routing follows focus; last tap wins).
- [ ] Deck keys that must work in the page: letters, digits, space,
      Backspace, Enter, arrows (scroll/caret), Tab (focus move).

### 20.2 Stacking: the keyboard pushes everything up
- [ ] With the Companion raised AND the deck up: the deck is the
      bottom-most surface; the Companion panel (handle + tab strip +
      page) rides ABOVE the deck; NOTHING is hidden under the keyboard.
- [ ] Toggle the keyboard off ([⌨] button): the WHOLE deck disappears
      (terminal canvas grows down to the gesture bar) and a small round
      keyboard icon appears at the bottom-right corner.
- [ ] Tap the corner icon: the full deck comes back.
- [ ] Keyboard off + Companion raised + tap a page input: the SYSTEM
      keyboard opens and the Companion panel lifts above it; dismissing
      it leaves the layout intact.

### 20.3 Keys
- [ ] Quick-tap "-" → "-" appears (terminal AND Companion).
- [ ] Quick-tap each digit → the digit; HOLD a digit ≥350ms → F-key popup
      + F-key on release.
- [ ] Arrow keys visibly longer (wider than tall); repeat on hold works.

### 20.4 "+" opens the Companion picker
- [ ] With the Companion raised, tap "+" on the tab strip: a Midnight
      sheet lists EVERY Companion; open tabs are check-marked.
- [ ] Tap a listed Companion → sheet closes, that tab opens/raises.
- [ ] Tap "+ Add Companion" → the Companions management page opens.
- [ ] Scrim tap and Back both close the sheet (Back does not collapse
      the panel while the sheet is open).

### 20.5 White canvas honesty (the m4.0.2 leftover)
- [ ] Open the previously-white tab: EITHER the page now renders
      (Activity-context creation fixed it) OR within ~15s a card appears:
      "Page never rendered" + the WebView version + the compat-retry hint.
      NEVER a silent white rectangle.
- [ ] Tap Retry: the tab reloads with SOFTWARE rendering (compatibility
      mode); tapping Retry again returns to GPU rendering.
- [ ] If the page renders now — log the installed WebView version.

### 20.6 Regression
- [ ] §18 startup checks still pass; §17 spot-checks (drag 1:1, frozen
      height while dragging, tab switch without reload, login persists
      across app restart, file upload, Back = history → collapse).
- [ ] §19 spot-check: a real load error still shows "Page didn't load".
- [ ] Terminal: §4/§8 spot-check (echo while typing, tabs, font pinch).

## 21. Manual acceptance — m4.0.4 (pixel-truth stall detection + one-spot keyboard toggle, v0.7.0-m4.0.4) — DEVICE GATE PENDING

Prereq: install vc28 IN PLACE over vc27 (data survives; do not uninstall).
All prior Companion behavior (§17–§20) must keep working.

### 21.1 The black page — honest outcome, one way or another
- [ ] Open the previously-black Companion tab (ChatGPT). Within ~15s ONE
      of these must happen — a bare black canvas that sits there silently
      is a FAILURE of this gate:
      a) the page renders (device WebView builds it), or
      b) the canvas swaps to the "Page never rendered" card: title +
         "Android System WebView <version>" + hint (the tab silently
         retried on the software renderer first — that part is invisible).
- [ ] On the card: "Open in browser" opens the SAME address in the
      device's real browser (settles site vs device WebView).
- [ ] On the card: "Continue anyway" reveals the raw canvas — if the
      site's cookie banner is there, it must be tappable; the card must
      NOT pop back over it (the probe stays quiet after a dismissal).
- [ ] On the card: Retry reloads the tab (render mode alternates);
      after a Retry the honesty contract is re-armed (the card may
      return if the canvas is still dead).

### 21.2 Cookie banner is a one-time site prompt
- [ ] First open: the site's own banner may appear (it belongs to the
      site). Tap Accept or Reject ONCE.
- [ ] Fully close PocketShell (swipe away), reopen, open the Companion:
      the banner must NOT reappear (cookies flushed on pause persist).

### 21.3 Keyboard toggle — one spot, one shape, both states
- [ ] Deck up: the [⌨] key sits BETWEEN Space and Enter (row reads
      Ctrl · Alt · Space · Shift · [⌨] · Enter) — rectangular key box.
- [ ] Tap it: the whole deck collapses; a RECTANGULAR key-styled box
      (NOT a round bubble) appears near the bottom-RIGHT, roughly where
      the [⌨] key was — same fill/border/icon as the deck key.
- [ ] Tap the parked box: the deck returns; the parked box disappears.
- [ ] Tap the collapsed terminal canvas: the deck also returns (m4.0.3
      behavior kept).

### 21.4 Regression
- [ ] §20 spot-checks: deck types into Companion AND terminal; the deck
      pushes everything up; "-" and digits quick-tap fine.
- [ ] §18 startup check: app still starts with a broken/updated WebView
      provider (no startup crash).
- [ ] §17 spot-check: drag 1:1, frozen page while dragging, login
      persists across app restart.

## 22. Manual acceptance — m4.0.5 (the black page fixed at the root + the page testifies, v0.7.0-m4.0.5) — DEVICE GATE PENDING

The one job: the Companion canvas must show the real site. Everything in
this gate follows from the m4.0.5 diagnosis: Force Dark off (3 layers),
Chrome-identical UA, and a DOM witness that makes any remaining failure
self-describing.

### 22.1 The ChatGPT tab actually renders
- [ ] Install vc29 in place (over vc28); app data must survive.
- [ ] Open the ChatGPT Companion. EXPECT the real page: ChatGPT's own
      UI ("What can I help with?" / composer), NOT a black canvas.
- [ ] The page may render in its OWN dark or light theme — either is
      correct; what matters is that the app's content is visible and
      interactive, not the site's empty shell.
- [ ] Type into the page via the deck; tap around; scroll. Site must
      respond normally.
- [ ] The cookie banner (already accepted previously, or on first open):
      choose once; it must stay gone after a full app restart.

### 22.2 If anything still fails, the card must say WHY
- [ ] If a failure card appears for a page that loads but never starts,
      it reads "Page won't start" and its detail line contains the
      page's OWN numbers, e.g.
      `readyState=complete · 23 DOM elements · error: SyntaxError … ·
      console: … · Android System WebView <version>`.
- [ ] REPORT THAT DETAIL LINE VERBATIM (screenshot) — it names the
      exact cause (too-old WebView / refused script / blocked request).
- [ ] Before retrying: Settings → Device care (or Play Store) → update
      "Android System WebView", reopen PocketShell, Retry the tab.

### 22.3 Honest-fit spot checks
- [ ] A healthy page never shows a card (pixels + mounted DOM both
      verified internally; no nagging).
- [ ] "Open in browser" on any card opens the same address in the
      device browser; "Continue anyway" shows the raw canvas and the
      card does not return until Retry.
- [ ] Logins: a Google sign-in inside a Companion must no longer be
      refused with disallowed_useragent (Chrome UA now presented).

### 22.4 Regression
- [ ] §21 spot-checks: keyboard toggle one spot/one shape both states;
      first-stall silent software retry behavior unchanged for
      never-painted pages.
- [ ] §20: deck types into Companion AND terminal; keyboard pushes
      everything up; "-" and digits quick-tap fine.
- [ ] §17/§18: drag 1:1, login persists, startup OK with a
      broken/updated WebView provider.

## 23. Manual acceptance — m4.0.6 (the last dark lever + the SSR-proof witness + the page can tell us everything, v0.7.0-m4.0.6) — DEVICE GATE PENDING

Still the one job. m4.0.5's witnesses were both defeated by a
server-rendered dark shell; this build forces the light scheme at the
context level, makes boot errors decisive, and adds a STANDING Page
health sheet with a copy button so the device's own testimony reaches
the chat no matter what the canvas does.

### 23.1 The main event: does the tab render now?
- [ ] Install vc30 in place (over vc29); app data must survive.
- [ ] Open the ChatGPT Companion. EXPECT the real page — most likely in
      its LIGHT theme now (white background), because the WebView is
      forced to prefer light. Content must be visible and interactive:
      "What can I help with?" / composer / sidebar.
- [ ] If the page now shows WHITE and renders correctly — the dark
      scheme path was the killer; report success and the dark-theme
      question can be revisited later as a feature, not a bug.
- [ ] If it STILL fails, any failure card must carry real numbers
      (readyState · elements · interactive · text chars · error) —
      see 23.3 for how to hand them over.

### 23.2 The Page health chip (new, always available)
- [ ] A small ⓘ glyph sits at the right end of the Companion tab strip,
      next to "+". Tap it: the "Page health" sheet slides up.
- [ ] EXPECT a report block: url, webview version, renderer, pixels
      verdict, readyState/DOM/interactive/text counts, boot errors,
      console lines, UA.
- [ ] Tap **Copy report**: a toast confirms; paste the clipboard into
      the chat. THAT is the deliverable when anything is still broken.
- [ ] **Refresh** re-reads the page live; **Reload** reloads the tab;
      **Reload in compatibility mode** re-creates it on the software
      renderer. Back or scrim tap closes the sheet.

### 23.3 If the canvas is STILL black
- [ ] Open Page health → Copy report → paste into the chat verbatim.
      That single paste names the cause (SyntaxError from an old
      WebView build, a bot-challenge page, zero pixels = compositor,
      etc.) and ends the guess loop permanently.
- [ ] Also try "Reload in compatibility mode" once, and note whether
      the page then renders.

### 23.4 Regression
- [ ] §22.2 still holds: a never-booting page escalates to the honest
      card after one silent reload — now also when its SSR shell is
      huge (the m4.0.6 verdict rule).
- [ ] §21/§20 spot-checks: keyboard toggle one spot/one shape; deck
      types into Companions; first-stall silent software retry intact.
- [ ] §17/§18: drag 1:1, login persistence, startup OK.

## 24. Manual acceptance — m4.0.7 (the health sheet's verdict decoded: swap-safe host + creation rollback + glass-first probe + attach kick, v0.7.0-m4.0.7) — DEVICE GATE PENDING

Still the one job. The m4.0.6 health sheet's Copy report answered the
decisive question: the page is FULLY alive (761 elements · 62 interactive
· 394 text chars · zero boot errors) while pixels never present. That is
a presentation failure, and this build fixes its two proven code causes:
(1) AndroidView's factory never re-ran, so every silently swapped WebView
(compat retry, boot retry, TAB SWITCH) never reached the screen — the
compatibility renderer had literally never been tested on the device;
(2) the forced-light createConfigurationContext creation recipe was the
painting regression (m4.0.4's plain activity context painted partially;
m4.0.6's recipe painted nothing).

### 24.1 The main event: does the tab render now?
- [ ] Install vc31 in place (over vc30); app data must survive
      (logins, tabs, panel height).
- [ ] Open the ChatGPT Companion. EXPECT the real page within a few
      seconds — theme now follows the device (dark Midnight device is
      fine; the light-forcing experiment is REVERTED). Content visible
      and interactive: composer, sidebar, text.
- [ ] The attach kick is silent: at most ONE extra load in the first
      seconds if the first frame was slow — never a card, never a flash.
- [ ] Let the tab run 30+ seconds: the "Page never rendered" card must
      NOT appear while the page is actually visible.

### 24.2 Tab switching (the latent bug this build also fixes)
- [ ] Open a second Companion (Zai). Switch ChatGPT ⇄ Zai several times.
      EXPECT: the tab STRIP and the CANVAS always agree — the shown page
      is always the selected tab's, with its scroll/login state intact.
- [ ] If a tab ever stalls, the canvas must go to the honest card —
      never to a black void.

### 24.3 The compat renderer — first REAL device test
- [ ] Page health → Reload in compatibility mode. EXPECT the tab to
      RE-CREATE and the page to appear ON SCREEN (previously the compat
      view loaded invisibly and the screen stayed black until the card).
      If the page renders in compat mode, the GPU raster path is the
      device's culprit — say so in the chat; we then consider defaulting
      to software for this device class.
- [ ] Page health afterwards: pixels must read "painted" (glass probe)
      within ~20 s, never "unknown" forever.

### 24.4 Regression
- [ ] §23.2: the Page health sheet still works (Copy report / Refresh /
      Reload / Reload-in-compat).
- [ ] §21/§20 spot-checks: keyboard toggle one spot/one shape; deck
      types into Companions AND terminal; first-stall silent software
      retry intact (card only after BOTH modes stall).
- [ ] §17/§18: drag 1:1 with frozen reflow, login persistence across
      restart, startup never touches android.webkit.

## 25. Manual acceptance — m4.0.8 (the painted-but-black decode: the light package returns on top of the fixed host, v0.7.0-m4.0.8) — DEVICE GATE PENDING

Still the one job. The m4.0.7 health report decoded the state: BOTH tabs
painted (GPU tab and software-layer tab) while the screen stayed black —
so the black is the page's own near-black output, not a presentation
failure. m4.0.7's rollback had re-armed both dark sources (targetSdk 28
⇒ algorithmic darkening ON by default on Android 15; prefers-color-scheme
⇒ dark because the app is Midnight). This build re-applies the light
package ON TOP of the fixed host and makes the health report
color-truthful.

### 25.1 The main event: LIGHT page visible
- [ ] Install vc32 in place (over vc31); app data must survive
      (logins, tabs, panel height).
- [ ] Open the ChatGPT Companion. EXPECT the LIGHT (white) ChatGPT theme
      with visible dark text — NOT black, NOT near-black. A login wall is
      an acceptable and DIAGNOSABLE state; pure black is not.
- [ ] Open the Zai Companion. EXPECT the same: a light, readable page.
- [ ] Let both tabs run 30+ seconds: no failure card while a page is
      actually visible.
- [ ] Log in if asked (the Chrome-like UA keeps Google logins accepted);
      login persistence still applies (§18).

### 25.2 The health report must now name the glass
- [ ] Tab strip ⓘ chip → Page health. EXPECT the new lines:
      `scheme: forced light` and
      `glass: dominant #XXXXXX · N% near-black · N colors`, plus
      `page says: "<title> — <first words>"`.
- [ ] Copy report and paste it into the chat IF anything still looks
      wrong. The glass line names the exact on-screen color — a
      `dominant #000000 · 99% near-black` answer is a page-state verdict,
      no more guessing.

### 25.3 Escapes still honest
- [ ] Page health → Reload, and → Reload in compatibility mode, both
      still re-create the tab and show a page (the compat path now ALSO
      gets the light package).
- [ ] Back, drag-handle, tab switching behave as in §24.2 (the keyed
      host is untouched).

### 25.4 Regression
- [ ] §24 spot-checks repeat in one pass: tab switching shows the
      selected tab's page with state intact; first-stall silent retry
      never flashes a card while a page renders.
- [ ] §17/§18: drag 1:1, login persistence across restart, startup never
      touches android.webkit.
- [ ] Terminal/Home untouched (Midnight stays Midnight — only the
      WebView's scheme is forced light).


## 26. Manual acceptance — m4.0.9 (Companion Rendering Reset: the minimal baseline experiment, v0.7.0-m4.0.9) — DEVICE GATE PENDING

The Companion render path is FROZEN this build. The deliverable is the
control experiment: a plain-Activity WebView baseline inside PocketShell
that will name the exact failing layer. Success is redefined: a real
website visibly displays its actual UI on the physical device and is
usable by touch and keyboard. DOM counts, readyState and "pixels
painted" are NOT success.

### 26.1 The baseline (gates A–D)
- [ ] Install vc33 in place (over vc32). Open a Companion tab → ⓘ
      Page health → **Render baseline (diagnostic)**.
- [ ] MODE shows `BASELINE (Android defaults)`. URL ▸ until
      `https://example.com/`. Gate A: the page's text must be VISIBLE.
      Copy the status.
- [ ] URL ▸ `https://www.wikipedia.org/`. Gate B: visible content +
      scrolling works.
- [ ] URL ▸ `https://chatgpt.com/`. Gate C: the REAL ChatGPT UI visible
      (login wall acceptable if it renders with visible text/buttons).
      Tap INSPECT; note viewport + title.
- [ ] URL ▸ `https://chat.z.ai/`. Gate D: the REAL Z.ai UI visible.
      INSPECT.
- [ ] If the BASELINE fails on example.com: STOP — do not touch the
      Companion; report the copied status (WebView provider/device
      investigation begins).

### 26.2 The single-variable sweep (finds the breaking layer)
- [ ] MODE ▸ `+CHROME UA` → ChatGPT + Z.ai. Visible? Copy status.
- [ ] MODE ▸ `+FORCED LIGHT CTX` → ChatGPT + Z.ai. Visible? Copy status.
- [ ] MODE ▸ `+MIDNIGHT BG` → ChatGPT + Z.ai. Visible? Copy status.
- [ ] MODE ▸ `+LOAD BEFORE ATTACH` → ChatGPT + Z.ai. Visible? Copy.
- [ ] MODE ▸ `+WIDE VIEWPORT` → ChatGPT + Z.ai. Visible? Copy status.
- [ ] Report the FIRST mode that blanks (and INSPECT's viewport reading
      for it) — that is the failing variable.

### 26.3 The Compose-host arm
- [ ] With the harness showing a site correctly, open the REAL Companion
      tab for the same site. If the harness renders and the Companion
      does not, the failure is in the Compose host/panel path (S2/S3) —
      the decision rule then rebuilds the host as a native ViewGroup.

### 26.4 Regression
- [ ] Companion behavior identical to m4.0.8 (frozen): tabs, drag,
      health sheet, escapes — plus the new "Render baseline" button.
- [ ] Terminal/Home/runtime untouched; §17–§25 spot-checks on the
      Companion paths that existed before this build.

> **RESULT (2026-09-05): §26.1 PASSED — all four gates visible in one
> screen recording** (example.com, wikipedia.org, chatgpt.com, chat.z.ai
> fully rendered; status `attached=true 1080x2061px layer=none`, default
> UA, WebView 151.0.7922.199). §26.2 was not needed: per the decision
> rule, baseline-pass + Companion-fail (the same recording shows both
> ChatGPT-white and Z.ai-dark blank canvases in the real panel) triggered
> decision B — the rebuild of §27.

## 27. Manual acceptance — m4.1.0 (Companion Native Rebuild: the proven baseline becomes the architecture, v0.8.0-m4.1.0) — DEVICE GATE PENDING

The verdict build. The Companion render path is now the m4.0.9 baseline
recipe verbatim, hosted natively: `Activity → Compose panel → ONE stable
plain FrameLayout → one WebView per tab → attach → first layout → load`.
Every former lever (config context, UA spoof, background override,
software layer, wide viewport, pre-attach load, attach kick, pixel
watchdog, boot witness, retry ladders, health sheet) is DELETED. The
only remaining delta to the proven baseline is the parent chain (the
overlay's AndroidView node). Success = Gates A–H below, on the physical
device, by eye and finger.

### 27.1 The four render gates (same sites, new architecture)
- [ ] Install vc34 in place (over vc33). Open a Companion tab (ChatGPT).
      **Gate E:** the REAL ChatGPT UI must be visible in the panel (this
      exact state was blank-white on every build since m4.0).
- [ ] Switch to the Z.ai tab. **Gate F:** the REAL Z.ai UI visible (was
      blank-dark before).
- [ ] ⓘ chip still launches the render-baseline harness; a BASELINE run
      still passes gates A–D as before (regression of the control).
- [ ] example.com as a new tab renders; scrolling works.

### 27.2 Touch, tabs, panel
- [ ] **Gate G (touch):** tap focus into the page's input field; type
      with the Phase 3.1 keyboard (shared deck routes to the web target);
      links respond; scrolling is smooth.
- [ ] Tab switch ChatGPT → Z.ai → back: each tab still displays its page
      (no reload, no blank).
- [ ] Drag the handle: 1:1 tracking, frozen page height during drag, one
      reflow on release; settled height persists across collapse/reopen.
- [ ] Collapse (drag down) and reopen: the page is STILL displayed
      (the stable native container survives the panel leaving composition).
- [ ] Back gesture with in-page history goes back; without it, collapses.

### 27.3 Sessions, uploads, downloads
- [ ] Log into a site (cookies incl. third-party accepted). Kill
      PocketShell, reopen, reopen the tab: **Gate H** — the session
      SURVIVES (cookie flush on pause).
- [ ] A site file-attachment control opens the system picker and the
      chosen file reaches the page (upload bridge).
- [ ] A download link enqueues into DownloadManager → app-specific
      storage, with the toast; no crash.

### 27.4 Honest failure states (kept, now rare)
- [ ] Airplane mode + open a tab → "Page didn't load" card with the real
      error; Retry restores when the network returns; "Continue anyway"
      and "Open in browser" work.
- [ ] The renderer-death path (if a broken WebView build ever triggers
      it) still shows "Page renderer crashed" and kills NEITHER the app
      NOR the terminal.

### 27.5 Regression
- [ ] Terminal (PTY output, keyboard, pinch), Linux runtime install,
      packages, Home, Settings, Diagnostics untouched: §4–§16 spot-checks.
- [ ] Clear web data (Settings → Companions) wipes cookies/storage and
      live tabs without touching definitions.
- [ ] If ANY gate still blank: do NOT reinstate removed levers — copy the
      ⓘ → Render baseline status + this tab's state into the chat; the
      one remaining delta (panel parent chain) is the investigation.

---

## 28. Manual acceptance — m4.0.11 (Replace Renderer Only: the winner frozen and shipped, v0.8.0-m4.0.11) — DEVICE GATE PENDING

The closing gate of the whole investigation. The winner of the m4.0.9
mode sweep is FROZEN (BASELINE — zero deltas; the sweep table lives in
docs/RENDER-RESET-M4.0.9.md §8) and the production tab content renderer
is the exact copy of the proven baseline implementation, diagnostics
stripped (`CompanionRenderContract`, unit-pinned). The sheet, drag
handle, remembered height, tab strip, tab system, picker and destination
storage are untouched. Install **vc35 in place** (over anything) and run
the eight gates — by eye and finger, on the physical device.

### 28.1 The eight gates (A–H, on the REAL Companion)
- [ ] **Gate A:** add/open an example.com Companion → the page is visible.
- [ ] **Gate B:** a "modern site" tab (wikipedia.org) renders and scrolls.
- [ ] **Gate C:** the ChatGPT tab shows the REAL ChatGPT UI (composer,
      header — not a blank white/dark canvas).
- [ ] **Gate D:** the Z.ai tab shows the REAL Z.ai UI (GLM header, Z
      logo, composer).
- [ ] **Gate E (touch):** tap into the page's input; the Phase 3.1
      keyboard opens and typing reaches the page; links respond.
- [ ] **Gate F:** switch ChatGPT → Z.ai → back — each tab still displays
      its page (no reload, no blank).
- [ ] **Gate G:** drag the panel down (collapse) and raise it again —
      the page is STILL displayed; drag is 1:1, one reflow on release,
      height remembered.
- [ ] **Gate H:** log into a site, kill PocketShell, reopen, reopen the
      tab — the session SURVIVES (cookie flush on pause).

### 28.2 The frozen contract, spot-checked
- [ ] The canvas shows NO diagnostics: no URL/MODE/INSPECT/COPY buttons,
      no status header, no health chip sheet — only the page (ⓘ in the
      strip still launches the separate render-baseline harness; the
      canvas itself is clean).
- [ ] The failure states are the only cards: airplane mode → "Page
      didn't load" (Retry / Open in browser / Continue anyway); no
      watchdog, no stall, no boot cards exist anywhere.
- [ ] ⓘ → Render baseline still works (regression of the control).

### 28.3 Regression
- [ ] Terminal (PTY output, keyboard, pinch), Linux runtime install,
      packages, Home, Settings, Diagnostics: §4–§16 spot-checks.
- [ ] Settings → Companions → Clear web data still wipes cookies/storage
      + live tabs, keeps definitions.
- [ ] If ANY gate fails: do NOT reinstate removed levers. Capture the
      state (ⓘ → Render baseline → COPY) and paste it into the chat —
      the sole remaining delta to the proven baseline is the overlay's
      AndroidView parent chain, and that is the entire investigation.

## 29. Manual acceptance — m4.0.12 (Companion Finalization: cleanup, polish, ONE keyboard, v0.8.0-m4.0.12) — DEVICE GATE PENDING

The finalization gate. The renderer is FROZEN and byte-identical to
vc35; this build only (1) removes every trace of the diagnostic UI,
(2) adds refresh + hard refresh, (3) enlarges the drag handle's invisible
touch zone, (4) makes the PocketShell keyboard the ONE keyboard
everywhere with the system IME permanently blocked. Install **vc36 in
place** (over anything) and work the four checklists plus the
regression ladder — by eye and finger, on the physical device.

### 29.1 Website rendering (the freeze, re-proven)
- ChatGPT: [ ] page loads [ ] page visible [ ] scrolling works
  [ ] login works [ ] message input receives focus
  [ ] PocketShell keyboard appears [ ] typing enters the ChatGPT input
  [ ] the Android/Samsung keyboard NEVER appears.
- Z.ai: [ ] page loads [ ] page visible [ ] scrolling works
  [ ] input receives focus [ ] PocketShell keyboard appears
  [ ] typing works.
- One additional normal website: [ ] HTML input [ ] password field
  [ ] textarea [ ] scrolling.
- The Companion shows ONLY the real website: no Render-baseline header,
  no URL/WebView-version/UA/attached-bounds/layer readouts, no
  URL/MODE/INSPECT/COPY buttons, no ⓘ chip — all of it is GONE (the
  harness code is deleted, not hidden).

### 29.2 Refresh (normal + hard)
- Normal refresh (tap the ↻ glyph in the tab strip):
  [ ] reloads the ACTIVE tab [ ] same URL [ ] same tab
  [ ] other tabs unaffected (open two tabs, refresh one, switch to the
      other — still intact, no reload).
- Hard refresh (LONG-PRESS the ↻ glyph — haptic tick + "Hard reloading…"):
  [ ] the current URL reloads freshly (e.g. a site's stale banner/counter
      updates) [ ] session/cookies remain (still logged in) [ ] tab
      remains [ ] other tabs unaffected.

### 29.3 Drag handle
- [ ] easy to grab (invisible touch zone now 40dp tall, full width)
- [ ] still visually the same small 36×4dp bar
- [ ] never hidden underneath other UI (it is the topmost element of the
      panel's column)
- [ ] smooth dragging (1:1, frozen WebView height during drag, one reflow
      on release, height remembered)
- [ ] does not interfere with website scrolling (the zone never overlaps
      the canvas).

### 29.4 The universal keyboard (ONE keyboard, ONE layout)
- Terminal: [ ] focus terminal → deck opens → type a command → the shell
  receives it (unchanged behavior).
- ChatGPT: [ ] open Companion → tap the message box → the PocketShell
  deck opens by itself → type → the message appears in ChatGPT
  [ ] no Android keyboard, ever.
- Z.ai: [ ] tap the prompt → same deck → type → prompt appears.
- Companion settings: [ ] Add Companion → tap the Name field → the SAME
  deck opens → letters/numbers/punctuation/space/backspace/enter all
  reach the field (the universal dispatch fallback) — this replaces the
  system IME that used to serve these fields.
- Hide / toggle: [ ] tap [⌨] in the deck row → the deck hides
  [ ] the small rectangular [⌨] button appears bottom-right — on EVERY
      screen now (Home, Terminal, Linux, CLI Apps, Companion)
  [ ] tap it → the same deck reopens.
- Over every screen: [ ] Terminal → Companion → ChatGPT works
  [ ] Linux → Companion → Z.ai works [ ] CLI Apps → Companion → GitHub
  works [ ] Home → Companion → any site works.
- Focus conflicts: [ ] Terminal underneath + Companion open → tap a
  ChatGPT input → typing goes to ChatGPT (NOT the terminal)
  [ ] close the Companion → the terminal receives typing again, cleanly
  [ ] no keyboard flickering, no double-open, no wrong-target input
  [ ] hiding the deck and tapping the SAME field does not re-open it
      (use the [⌨] toggle — by design; a DIFFERENT field or surface
      auto-opens it again).

### 29.5 Regression
- [ ] Terminal (PTY output, pinch, F-key long-presses) unchanged — the
      deck only moved composition scope, nothing else.
- [ ] Companion Gates A–H of §28 still hold (they are the freeze).
- [ ] Settings → Companions → Clear web data still wipes cookies/storage
      + live tabs, keeps definitions (this — and only this — remains the
      way to clear data; hard refresh is NOT a data reset).
- [ ] §22 holds: app startup still never touches android.webkit (the IME
      block is a window flag, not a WebView call).
- [ ] If ANY gate fails: the renderer is NOT the suspect (it is
      byte-identical to vc35); capture the failing screen and paste it —
      the finalization layer (refresh/keyboard/handle) is the search
      space.

---

## 30. Manual acceptance — Phase 5 (UI & Interaction Polish: free-position Companion, decluttered Home, Light Theme, v0.9.0-m5.0.0) — DEVICE GATE PENDING

The Companion renderer, sheet mechanics, tab system, pool and the ONE
keyboard dispatch chain are FROZEN — this gate only verifies the polish
layer around them. Everything here is on the REAL device, in order.

### 30.1 Companion drag bar (the exact required behavior)
- [ ] The bar is visibly ~2× wider than m4.0.12 (72dp), still slim.
- [ ] Raise the Companion. Single-tap the bar → it minimizes
      IMMEDIATELY. Repeat from ~25%, ~50%, ~80%, near-full. No drag
      required, no snap on the way (the bar just goes down).
- [ ] Tap the bar while minimized → nothing (no accidental close/open
      loop).
- [ ] Drag up/down: height follows the finger 1:1; release at an
      arbitrary height (e.g. ~33%, ~61%, ~85%) → it STAYS there. No
      snapping to 25/50/75/full at any release point.
- [ ] Drag down near the bottom → minimized (only the bar remains).
- [ ] Drag the bar up from minimized → the Companion restores smoothly
      (and keeps working: pages still render, scroll, refresh).
- [ ] The bar is never hidden: raise the Companion over Home, Terminal,
      Linux, Packages, Settings — the bar always rides on top of the
      strip and content.
- [ ] Website scrolling inside the canvas is untouched (ChatGPT/Z.ai
      scroll normally; the bar zone does not swallow page touches).

### 30.2 Home decluttering
- [ ] No floating action button anywhere on Home; nothing replaced it.
- [ ] No "CLI Apps ▾" dropdown; the SAME apps appear in "Your tools" and
      launch exactly as before (tap → verify → session).
- [ ] Session creation still exists: Terminal → "+" in the tab bar (and
      the empty state's "New session").
- [ ] Home feels tighter but not cramped; all rows/tiles tappable.

### 30.3 Keyboard toggle (§4 of the brief)
- [ ] Hide the keyboard (deck [⌨] toggle): the rebirth icon sits at the
      bottom-RIGHT corner — near the right edge and above the gesture
      bar — on Home, Terminal, Linux, Packages, Settings, Companion.
- [ ] It is easy to tap, never clipped, never over system navigation.
- [ ] Tapping it re-opens the SAME one keyboard; Android IME never
      appears (any screen, any input).
- [ ] Full keyboard regression ladder from §29.4 still passes (ChatGPT
      input, Z.ai input, terminal, settings fields, focus follows, no
      cross-typing).

### 30.4 Theme sweep (System / Light / Dark / AMOLED)
- [ ] Settings → Appearance → Light: the ENTIRE app switches
      immediately (no restart, no dark flash): Home, Terminal chrome,
      Linux chrome, Packages, Settings, Diagnostics, Companion strip +
      picker + settings, the keyboard deck, dialogs/menus.
- [ ] The terminal CONTENT canvas stays dark in Light (it is a terminal)
      and remains fully usable; the Companion WEBSITES keep their own
      themes — PocketShell never re-themes ChatGPT/Z.ai pages.
- [ ] Active tab labels are readable on the dark active-tab surface in
      Light (pinned onCanvas); inactive tabs readable on the light strip.
- [ ] Text contrast / icons / dividers / cards / switches / slider are
      legible in Light everywhere; no white-on-white or dark-on-dark.
- [ ] Status-bar icons flip correctly (dark icons on Light, light on
      Dark/AMOLED) on every screen.
- [ ] Dark: identical to the pre-Phase-5 Midnight look (no visual
      regression vs m4.0.12). AMOLED: pure-black M3 surfaces preserved.
- [ ] System: follows the device toggle live while the app is open.
- [ ] Choose Light → kill the app → reopen: Light persisted. Repeat for
      Dark and AMOLED. (Activity recreation / process death included.)

### 30.5 Tabs & chrome (§6/§7)
- [ ] Open 5+ terminal sessions and 3+ Companion tabs: tabs stay
      readable, horizontally scrollable, the ACTIVE tab is always
      reachable/visible; close buttons work; no crushed tabs.
- [ ] The terminal "+" reads as part of the strip (no circle plate).
- [ ] No visual regressions in tab switching, active indicator, or the
      Companion strip's refresh/hard-refresh buttons.

### 30.6 Packages & Settings
- [ ] Packages: search field + Search button share one row; search,
      install, uninstall, honest states all still work (real apk).
- [ ] Settings groups: Appearance (theme + dynamic color) / Terminal
      (font size) / Companion (websites entry); font slider persists on
      release and applies to new sessions.

### 30.7 Regression ladder (the frozen things)
- [ ] ChatGPT + Z.ai render, scroll, log in, type (the full §29.2/§29.4
      ladder) — untouched by Phase 5.
- [ ] Refresh (tap) and hard refresh (long-press) still act on the
      ACTIVE tab only; hard refresh keeps sessions.
- [ ] Session/tab state survives collapse, screen switches, and app
      restart as before.

## 31. Manual acceptance — m5.0.1 (M5.0 Final UI Correction: the Workspace Bar, v0.9.0-m5.0.1) — DEVICE GATE PENDING

Everything here is on the REAL device, in order. The frozen things
(renderer, pool, sheet mechanics, ONE keyboard, themes) must survive
untouched — run the §30.7 ladder at the end.

### 31.1 Workspace bar layout (the header is gone)
- [ ] Open Terminal (and a Kilo CLI session): there is NO large title
      row anymore — no `← Kilo CLI | …` header line. The workspace bar
      (`← [Tab] [Tab] +`) is the FIRST thing under the Android status
      area; the terminal canvas gained its vertical space back.
- [ ] The back arrow sits at the FAR LEFT of the tab bar, vertically
      aligned with the tabs, visually integrated (not a big header
      button, no separate row, no unnecessary rounding).
- [ ] Tapping the back arrow returns to Home — exactly once, no
      double-fire; from Home, re-entering Terminal restores the bar.
- [ ] With NO sessions: the bar shows `←` and `+`; the empty state's
      "New session" still works and fills the bar with a tab.

### 31.2 Compact tabs (both strips)
- [ ] Terminal: open 5+ sessions (rename one to a VERY long title by
      running something that sets the terminal title). Tabs are
      noticeably more compact than m5.0.0; the active tab is NOT a
      giant floating card — its accent indicator is a subtle hairline;
      inactive tabs are small, readable, borderless (quiet separator
      only).
- [ ] Long titles truncate with `…` and never stretch the bar; the
      close (×) button on every tab stays tappable.
- [ ] Scroll the tabs horizontally; switch to a tab that was scrolled
      off-screen → the strip brings the ACTIVE tab back into view.
- [ ] More tabs fit on screen than in m5.0.0 (narrower min width,
      2dp gaps); nothing is crushed or unreadable; no mis-taps caused
      by the tighter geometry (tabs still ≥ ~26–34dp tall).
- [ ] Companion raised: its strip mirrors the same compact language
      (34dp strip, smaller tabs); the + and refresh glyphs behave as
      before (+ opens the picker; tap refresh reloads the active tab;
      long-press hard-reloads with the toast).

### 31.3 Companion near-full drag surface (the ≥90% rule)
- [ ] Below 90%: raise the Companion to ~50% and ~80%. Vertical drags
      on the TAB STRIP do NOT move the sheet (only the dedicated bar
      drags); tapping tabs switches, × closes, + opens the picker,
      refresh works. The strip behaves 100% normally.
- [ ] At/above 90%: drag the sheet to near-full (past ~90% — e.g.
      release it at ~93%). Now press on the TAB STRIP (empty space
      between tabs or on the bar itself) and drag DOWN → the whole
      sheet follows the finger smoothly; release mid-way → it stays
      exactly there (free positioning, no snap).
- [ ] From ~93%, drag the strip UP → the sheet expands toward max and
      stops at FULL (0.94) — same clamp as the handle.
- [ ] While near-full, drag the strip DOWN continuously past 90%
      (e.g. release at ~70%): the drag must NOT cut out when crossing
      the threshold — it follows until the finger lifts.
- [ ] TAP discipline at ≥90%: tapping a tab still SWITCHES tabs (never
      minimizes); × still closes its tab; + still opens the picker;
      refresh still reloads. NOTHING on the strip minimizes the sheet
      — only the dedicated bar's tap does.
- [ ] Horizontal tab scroll at ≥90% still scrolls the tabs (it must
      not be hijacked as a sheet drag).
- [ ] The dedicated bar below/above 90% behaves exactly as §30.1
      (tap-to-minimize at any height, drag 1:1, release stays, drag-up
      restore when minimized).

### 31.4 No-regression ladder
- [ ] Keyboard: canvas tap opens the deck; typing lands in the
      terminal; ChatGPT + Z.ai input via the deck (§29.4 ladder); the
      [⌨] toggle is still corner-anchored on every screen; the Android
      IME never appears.
- [ ] Themes: Light / Dark / AMOLED / System sweep on the NEW bar —
      strip, tabs, back arrow, active indicator all legible in every
      theme (active tab label pinned light on the canvas-dark surface);
      no light-on-light anywhere.
- [ ] Companion renderer: ChatGPT + Z.ai render, scroll, refresh,
      hard-refresh-keeps-login — untouched.
- [ ] Session/tab state survives collapse, screen switches, app
      restart as before. No crashes across the whole gate.

## 32. Manual acceptance — m5.1.0 (M5.1: ARM64 performance & architecture optimization, v0.9.1-m5.1.0) — DEVICE GATE PENDING

The audit found the architecture sound in most places and fixed four
real inefficiencies. This gate VERIFIES the fixes on hardware and
MEASURES the scenarios the brief named. Where possible, compare against
vc38 (m5.0.1) behavior — "before" evidence.

### 32.1 The four fixes, behaviorally
- [ ] **F1 — collapsed Companion is silent:** open ChatGPT in the
      Companion, note it fully loaded, minimize the sheet, then work in
      the terminal for several minutes. The device stays cool/quiet
      compared to vc38 (in vc38 the page kept running JS underneath).
      Raise the sheet again → the page is EXACTLY as you left it (no
      reload, no scroll jump, still logged in).
- [ ] Same for every tab: minimize with 3 tabs open → raise → switch
      between them → all three are alive and preserved.
- [ ] **F2 — Home revisits are instant:** with the runtime READY, go
      Home → Terminal → Home → Terminal → Home repeatedly. Home renders
      immediately (no tool-grid "checking…" churn); the tools grid keeps
      its last real answer. After 60s away (or an install), the probe
      refreshes again.
- [ ] Install/uninstall something in Packages → return Home → the tools
      grid reflects the change (the forced post-operation probe).
- [ ] **F3 — background session output does not shake the foreground:**
      session 1 runs `yes` (or a build with heavy output); switch to an
      idle session 2 and type/read. The visible screen is smooth (in
      vc38 it repainted at session 1's output rate). Switch back to
      session 1 → its screen is correct and current.
- [ ] Rapid tab switching A↔B↔C: every switch renders the right session
      immediately (no stale/wrong content, no flash of the previous
      session).
- [ ] **F4 — no new startup risk:** force-stop the app, cold start, use
      ONLY the terminal (never raise the Companion) — no crash, no
      provider loading (the m4.0.1 rule). Then a session WITH Companion
      open while the device is under memory pressure (open many apps) —
      PocketShell keeps running, Companion logins survive backgrounding.

### 32.2 The A–G scenario matrix (measure with Android Studio memory
profiler / `adb shell dumpsys meminfo app.pocketshell`, and by feel)
- [ ] **A — Home only:** starts fast, idle memory stable, no probe churn.
- [ ] **B — Terminal active:** typing responsive with the deck; no lag
      on output.
- [ ] **C — Terminal + Linux:** one guest session; CPU settles when
      idle at the prompt; apk operations still work.
- [ ] **D — Terminal + Companion:** sheet drag smooth (GPU, no WebView
      reload); typing via the deck into the WebView works.
- [ ] **E — Multiple Companion tabs (3–5):** switching is immediate;
      memory grows per NEW tab but switching itself does not; minimized
      sheet → memory/CPU idle (F1).
- [ ] **F — Multiple terminal sessions + Companion:** background output
      does not drop frames (F3); all sessions remain manageable.
- [ ] **G — Background → return:** sessions keep running (FGS), pages
      keep logins, nothing re-initialized wastefully, no orphan
      processes (`adb shell ps | grep proot` before/after).

### 32.3 Regression ladder (nothing broke)
- [ ] Full §30.7/§31.4 ladder: keyboard everywhere, themes sweep,
      ChatGPT + Z.ai render/scroll/login, refresh/hard-refresh, session
      persistence, workspace bar + compact tabs from §31.

---

## 33. Manual acceptance — m6.0.0 (Universal Runtime Compatibility: musl + glibc in ONE Alpine guest, v0.10.0-m6.0.0) — DEVICE GATE PENDING

Preconditions: install vc40 IN PLACE over vc39 (data preserved). Open the
Linux Shell (one fresh session — this also installs the glibc layer
silently). Then run the automated suite and the manual checks below.

### 33.1 Automated suite (paste-ready, in the guest)
- `mkdir -p /tmp/pocketshell-tests && curl -fsSL <mirror>/pocketshell-runtime-tests-aarch64.tar.gz | tar -xz -C /tmp/pocketshell-tests`
- `sh /tmp/pocketshell-tests/run_on_device.sh`
- EXPECT (vc42, suite v2.1): header prints `suite: v2.1 (m6.0.2)` and the
  auto-located binaries dir; PREFLIGHT prints marker/status/app-stamp/
  loader + ls/readlink/disk; every row PASS or honest SKIP; RESULT: 24 passed,
  0 failed, 0 skipped; VERDICT: ALL GREEN. Cline rows require Cline installed
  (npm i -g cline) — if absent they SKIP and the verdict says so.
- The runner SELF-LOCATES its binaries (flat staging OR the served tarball's
  `bin/` subdir) — `POCKETSHELL_TESTS_DIR` is now an override, not a
  requirement. A stale suite copy prints its old version in the header — if
  it does not say v2.1 (m6.0.2), re-download.
- If the verdict is LAYER NOT INSTALLED: the PREFLIGHT section says why. Fully
  close + reopen the app (one fresh session installs the layer) and re-run; or
  repair in-guest without the app: put
  `pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz` beside the suite (or
  `export POCKETSHELL_LAYER_URL=<mirror>/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz`)
  and run `POCKETSHELL_INSTALL_LAYER=1 sh /tmp/pocketshell-tests/run_on_device.sh`.
  A `status: state=FAILED reason=…` line in PREFLIGHT is an app-side install
  failure — paste it.

### 33.2 pocketshell-doctor (no more guessing; v2 semantics since m6.0.3)
- `pocketshell-doctor /tmp/pocketshell-tests/t_cline_shape` → ELF64 AArch64,
  DT_NEEDED libc.so.6/libpthread.so.0/libdl.so.2/libm.so.6,
  `Required GLIBC: GLIBC_2.34`, `Version gate: PASS (installed glibc 2.41
  satisfies required GLIBC_2.34)`, loader resolution PASS per-library,
  Compatibility: SUPPORTED with the combined reason.
- `pocketshell-doctor <real Cline binary>` → `Required GLIBC: GLIBC_2.17`,
  Version gate PASS (2.41 ≥ 2.17), Compatibility: SUPPORTED — the m6.0.2
  device's UNSUPPORTED verdict was the doctor v1 bug, fixed in v2.
- `pocketshell-doctor /bin/sh` → SUPPORTED (musl — executed directly).
- `pocketshell-doctor /bin/busybox` → SUPPORTED (musl).
- `pocketshell-doctor --selftest` → 15/15 semantic-comparison cases PASS
  (2.17≤2.41, 2.41=2.41, 2.42>2.41, the 2.9/2.10 lexical trap, numeric
  max extraction).

### 33.3 Cline end-to-end (the real-world test; needs credentials for 4–5)
1. `cline --version` → 3.0.61
2. `cline --help` → full usage text
3. node-spawn chain: the suite's "cline via node spawn" row PASS
4. `CLINE_DEEP_TEST=1 sh /tmp/pocketshell-tests/run_on_device.sh` → startup +
   initialization render a UI/error honestly (no loader failures)
5. an actual agent turn (if credentials configured) + repeat launches from a
   CLEAN second session (kill the app, reopen, rerun)

### 33.4 musl regression (nothing may have changed)
- apk update / apk search / apk add nano (or any small package) — still works
- node -e 'console.log(1+1)', npm --version, git clone of a small repo, curl
- existing tools unaffected: Kilo starts, MCP servers start

### 33.5 Session lifecycle
- new session + close sessions — no change in terminal behavior, keyboard,
  Companion (the layer installs on FIRST session only; later sessions show
  the marker fast path)

### 33.6 Honesty checks
- `cat /etc/pocketshell/app-version` shows the app build that last prepared
  this rootfs (m6.0.3+: `0.10.0-m6.0.3 (versionCode 43)`; older builds show
  their own stamp; absent = the app is pre-m6.0.2 — that alone is a verdict,
  install the current APK)
- `cat /etc/pocketshell/glibc-runtime` shows the layer version line —
  `… rev=2` after the m6.0.3 update has taken effect; rev-less or `rev=1`
  means the updated app has not prepared a session yet (open one session)
- `cat /etc/pocketshell/glibc-runtime.status` shows the last install outcome
  (state=OK source=extractor|fastpath|manual-hatch …, or FAILED + reason)
- `ls /lib/ld-musl-aarch64.so.1` untouched; `apk` still fully functional
- Diagnostics: package-environment check unchanged/green

## 33A. m6.0.2 re-gate — the install-path fix (v0.10.0-m6.0.2, vc42) — DEVICE GATE PENDING

Forensic record (why gates 1 and 2 failed identically): AGP's asset merge
decompresses `*.gz` assets and strips the suffix, so the shipped APK carried
the layer as `assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar`
(plain, 17,909,760 B, sha 5be400dd…) while `GlibcRuntimePin.ASSET_PATH`
declared `…tar.gz` — `AssetManager.open` threw FileNotFoundException on EVERY
spawn before the rootfs was ever touched. Fixed: the pin describes the
packaged form, the extractor sniffs gzip magic + sha-verifies the asset
before extraction, a JVM pin opens the BUILT APK and asserts the packaged
entry, the release mirror extracts + sha-verifies the embedded asset, and
every spawn stamps the app identity into the guest.

Preconditions: install vc42 IN PLACE (data preserved). Fully close the app,
reopen, open ONE fresh session (the layer now really installs — first spawn
extracts ~18 MB, subsequent spawns are the marker fast path). Then:
1. §33.1 with a FRESH v2.1 suite download (expect header `suite: v2.1
   (m6.0.2)`; the old /tmp/kilo/gdrive copy is v1 — delete it).
2. §33.6 honesty checks: app-version shows `0.10.0-m6.0.2 (versionCode 42)`;
   marker shows the layer version line; status shows
   `state=OK source=extractor` (first spawn) then `source=fastpath`.
3. `/lib/ld-linux-aarch64.so.1 --version` → real GNU loader
   (`ld.so (Debian GLIBC 2.41-12+deb13u3) stable release version 2.41`),
   NOT the gcompat stub.
4. `command -v pocketshell-doctor && pocketshell-doctor /tmp/pocketshell-tests/t_cline_shape`
   → SUPPORTED.
5. EXPECT 24 passed, 0 failed, 0 skipped — ALL GREEN incl. Cline 3.0.61.
   Paste the full output either way.

## 33B. m6.0.3 re-gate — the doctor correctness gate (v0.10.0-m6.0.3, vc43) — DEVICE GATE PENDING

Context (gate #3 outcome): the runtime PROVED itself 24/24 on real hardware
(real loader, real Cline 3.0.61 end-to-end) — but the diagnostic tool was
wrong: doctor v1's version "comparison" was structurally always-false, so
every versioned glibc binary (real Cline included: GLIBC_2.17 vs 2.41) got
UNSUPPORTED, and the suite's unanchored "SUPPORTED" grep hid it. Fixed in
doctor v2 (numeric comparison, exit-code-authoritative loader check,
fact-hierarchy output, `--selftest`); the layer is re-cut as rev=2 with
BYTE-IDENTICAL glibc files (proven: exactly one tar member changed) and the
marker revision forces every device to re-extract on the next session.

Preconditions: install vc43 IN PLACE (same cert — data preserved), fully
close + reopen the app, open ONE fresh session (one ~18 MB re-extraction:
rev=1 → rev=2), then:
1. §33.1 with a FRESH v2.2 suite download (header must read
   `suite: v2.2 (m6.0.3)` — delete any older copy).
2. §33.6: app-version `0.10.0-m6.0.3 (versionCode 43)`; marker ends `rev=2`;
   status shows `state=OK source=extractor` (first spawn) then `fastpath`.
3. `pocketshell-doctor /usr/local/lib/node_modules/cline/node_modules/@cline/cli-linux-arm64/bin/cline`
   → `Required GLIBC: GLIBC_2.17`, `Version gate: PASS`, Compatibility:
   SUPPORTED.
4. `pocketshell-doctor --selftest` → SELFTEST PASS (15/15).
5. EXPECT 27 passed, 0 failed, 0 skipped — ALL GREEN (24 prior rows + the
   three new doctor rows; the real-cline doctor row adds a 28th on
   Cline-equipped devices — report what you see, either is correct).
   Paste the full output either way.

## 34. Manual acceptance — M7.0.0 Phase 5 (Android Storage Bridge: SAF folders, Share, Import, Export) — DEVICE GATE PENDING

Context: Phase 5 adds USER-GRANTED Android folders (SAF), persistent
re-grants across restarts, honest revocation handling, Share via FileProvider
staging, Import into the current folder, and Export via the system save
dialog. ZERO new permissions were added (manifest diff = the FileProvider
declaration only). The JVM suite pins the area contract, revocation, the
verified import/export bridges and the collision integration; the steps below
need real hardware (a real SAF provider, a real share sheet, a real save
dialog).

Preconditions: install the vc44+ build IN PLACE (same debug cert), open one
terminal session once so the runtime is installed, then open Files from Home.

1. SAF — add a folder: Files → switcher → "Add Android folder…" → the SYSTEM
   folder picker opens (no permission dialog beyond it) → pick e.g.
   Download/MyProject → the folder appears in the switcher, opens at "/",
   shows its real files. URIs are never rendered as POSIX paths (the location
   row shows "/" inside the folder area, not a fake /storage/... path).
2. SAF — operate: create a file and a folder (＋), rename one, delete one,
   paste a Linux file INTO the folder and a folder file INTO Linux (both
   directions: verified copy), move Linux → SAF. Every operation refreshes
   the listing; errors, if any, are rendered verbatim.
3. SAF — persistence: force-close PocketShell fully, reopen, Files → the
   folder is still in the switcher with its content (persisted grant).
4. Revocation: in system Settings → Apps → PocketShell → Permissions, revoke
   the folder access (or use a file manager to "forget" the grant), then in
   PocketShell open the folder area → an honest banner
   ("Access to ... is no longer available.") appears, the listing shows the
   honest error, the app does NOT crash, and Reconnect / Remove are offered.
   Reconnect re-opens the picker; after re-picking, the folder works again.
   Remove drops it from the switcher. Other areas (Linux, Downloads) keep
   working throughout.
5. Share: pick a file in ANY area (Linux, Downloads, SAF folder) → Share →
   the Android share sheet opens (Files/Gmail/WhatsApp/Nearby…). The shared
   attachment must arrive intact at the receiver. Never share a directory
   (folders show no Share action).
6. Import: browse /root (or any folder) → ＋ → "Import file…" → the system
   document picker opens → select e.g. a ZIP from the real Downloads → it
   appears in the CURRENT folder. Import onto an existing name asks
   Replace / Cancel (the Phase 4 dialog — no duplicate collision UI).
7. Export: pick a PocketShell file → Export → the system SAVE dialog opens
   with the file name pre-filled → save into real Downloads → verify the
   file appears there with correct size. Directories show no Export action.
8. Downloads shelf intact: in the Companion browser, download a file from a
   website ("Downloading to app storage…" toast) → Files → Downloads area →
   the new file is listed. NO redesign of that flow.
9. Regression: Linux /root listing, navigation, copy/move within Linux,
   rename, delete, New folder/file still behave exactly as Phase 4 (the
   storage engine under them is untouched). Rotation while on the Files
   screen keeps location and the pending paste banner.

## 35. Manual acceptance — M7.0.0 Phase 6 (Quick Text Editor) — DEVICE GATE PENDING

Context: Phase 6 adds a QUICK TEXT VIEWER/EDITOR (Open on regular files) —
UTF-8 only, 1 MiB cap, NUL-byte binary refusal, byte-exact round trip, a
(size, mtime) save gate with explicit overwrite confirmation, a dirty-back
guard, and honest full-screen refusal states. NO system IME involved: the
editor is a multiline BasicTextField served by the ONE keyboard deck through
the existing focused-view dispatch chain. The JVM suite pins the pure model
and the full load/gate/save flow through the real guest/shelf/SAF engines;
the steps below need real hardware (the deck, a real SAF provider, real
cursor-key behaviour).

Preconditions: install the P6 build IN PLACE (same debug cert), have a
PocketShell Linux session available, and put a small UTF-8 text file (e.g.
notes.txt) into /root.

1. Open: Files → tap a FILE → the action sheet now shows "Open" (first row)
   → the editor opens showing the content, file name + area label in the
   header, and the size in the status line. Tapping a DIRECTORY still opens
   the folder; symlinks still show no Open action (honest: a
   write-through-symlink save is refused by the engine).
2. Edit + Save: type with the keyboard deck (letters, ⏎ newline, ⌫) → the
   status line flips to "Unsaved changes" and Save enables → Save → status
   flashes "Saved ✓" and returns to the size label → leave and re-open the
   file: the edit persisted. Verify from a terminal session too
   (`cat /root/notes.txt`).
3. Cursor/selection keys (the known device check): ←/→ move the cursor
   within a line; ↑/↓ move across lines; the cursor stays inside the text
   field instead of hopping focus to the header buttons. If an edge arrow
   moves FOCUS instead of the cursor, report it — that is a known Compose
   focus-search edge, not a data risk.
4. Deck presence: the deck opens over the editor; the text area sits ABOVE
   it (nothing hidden behind the deck); toggling the deck off (⌨) reclaims
   the space; the parked ⌨ button brings it back.
5. Dirty back guard: make an edit → press system back → the guard dialog
   appears (Save / Discard / Keep editing). Keep editing stays; Save saves
   and returns to Files automatically; Discard drops the edits and returns.
   With NO edits, back leaves the editor directly. Rotation with a dirty
   buffer keeps the buffer and the guard state.
6. External change: open a file in the editor, then from a terminal session
   run `echo more >> /root/notes.txt` → Save in the editor → an honest
   dialog ("changed outside the editor") with Save anyway / Cancel → Save
   anyway overwrites; Cancel keeps the editor buffer and the disk file
   untouched. Delete the file from the terminal instead → Save → the
   dialog honestly says the file no longer exists (saving recreates it).
7. Honest refusals: put a PNG or ZIP in /root → Open → "is not a text file"
   and the file is NOT changed. Put a Latin-1 file (invalid UTF-8) → Open →
   "is not UTF-8 text", NOT shown garbled. `dd if=/dev/zero of=/root/big bs=1M count=2`
   → Open → "too large ... up to 1 MB" with the real size shown.
8. SAF folder: Open a text file that lives INSIDE a granted Android folder →
   edit + save works through the provider. Revoke the grant (system
   Settings) → save → an honest error banner in the editor (no crash), the
   buffer is preserved; back → Files shows the usual Reconnect/Remove
   banner.
9. Save refusals: try opening /etc/hosts (Linux area) → Open → editing
   works (reading is allowed) but Save is refused honestly
   ("Could not save: refused ..."). The file on disk is unchanged.
10. Regression: Files listing, copy/move/paste, rename, delete, New
    folder/file, Share/Import/Export (Phase 5) all unchanged; the terminal,
    Companion and shelf flows untouched; no new permissions (APK permission
    list identical to vc44).

## 36. Manual acceptance — M7.0.0 P6.1 (real Android Downloads via SAF + shelf ownership labels) — DEVICE GATE PENDING

Context: P6.1 is a LABELS-ONLY fix on top of the already-working Phase 5
SAF architecture. The app-owned external shelf
(Android/data/app.pocketshell/files/Download — where Companion downloads
land) is now labeled "PocketShell Downloads" in the switcher and
"PocketShell Downloads (app storage)" in dialogs, so it can never be
confused with the user's REAL shared Download folder
(Internal storage → Download), which enters through the UNCHANGED system
SAF picker under the folder's own name ("Download"). No storage code, no
permissions, no manifest change; a JVM regression pin forbids the shelf
from ever silently returning to the ambiguous plain "Downloads" label.

Preconditions: install the P6.1 build IN PLACE (same debug cert); a
Samsung or other device whose My Files shows Internal storage → Download.

Shelf ownership:

1.  Open Files.
2.  Open the area switcher (the chip at the top).
3.  Confirm the app-owned shelf is labeled "PocketShell Downloads" (NOT
    plain "Downloads", NOT "Android Downloads").
4.  Confirm the Linux area is still labeled "Linux".

Real shared Download via SAF (the UNCHANGED Phase 5 flow):

5.  Select "Add Android folder…" — the SYSTEM picker opens (this is the
    only door; PocketShell never discovers storage by itself).
6.  Navigate to Internal storage → Download and confirm with the system
    "USE THIS FOLDER" button.
7.  Confirm a SEPARATE area appears in the switcher labeled "Download"
    (the folder's own name — distinct from "PocketShell Downloads").
8.  Confirm files visible in Samsung My Files → Internal storage →
    Download are visible in the PocketShell "Download" area.
9.  Create/copy/move a test file into the SAF Download area from
    PocketShell; confirm it appears in Samsung My Files.
10. Add a file from Samsung My Files into Download; refresh/re-enter the
    area in PocketShell and confirm it appears.
11. Restart PocketShell and confirm the SAF grant persists (the
    "Download" area rejoins the switcher with its contents — no
    re-selection needed).

Cross-domain operations (all through the UNCHANGED Phase 4/5 verified
paths — collision Replace/Cancel semantics included):

12. Copy Linux → Download, and Download → Linux: both succeed; the Linux
    side shows the file with correct size (cat it in a terminal if
    convenient).
13. Move Linux → Download, and Download → Linux: source disappears,
    destination verified.
14. Trigger a name collision in the Download area: the dialog offers
    Replace/Cancel exactly as everywhere else — never silent overwrite.
15. Enter a file in the Download area and "Open" it in the quick editor
    (Phase 6 path over the SAME abstraction): edit, save, and confirm the
    change in Samsung My Files.

Honesty and safety:

16. Revoke the grant (system Settings → PocketShell → remove access, or
    revoke from the folder's provider): the area stays listed behind the
    honest banner ("Access to "Download" is no longer available") with
    Reconnect / Remove — never a fake empty folder, never a crash.
17. Confirm the APK permission list is UNCHANGED (5 permissions, no
    MANAGE_EXTERNAL_STORAGE, no READ/WRITE_EXTERNAL_STORAGE) — the real
    Download access is PURELY the user-granted persistent SAF grant.
18. Regression: Linux, PocketShell Downloads, Share/Import/Export,
    editor, terminal, Companion all behave exactly as in the Phase 6
    build (this fix touches labels only).

## 37. Manual acceptance — M7.0.0 Phase 7 (Open Terminal Here) — DEVICE GATE PENDING

Context: Phase 7 adds "Open Terminal Here" for directories inside
PocketShell Linux ONLY. The action sheet of a directory entry in the
Linux area offers a real launch: a NORMAL Alpine terminal session is
created (never reusing another session's PTY, never writing into a
running session) whose guest working directory is THE TAPPED FOLDER
(p7.1 — the directory entry whose sheet the action was opened for,
resolved under the browsed location by the ONE validated child
composition; the p7.0 device report caught the launch landing in the
browsed parent instead — the browsed location must never be launched
as a fallback), established through the guest shell chain
`cd -- '<directory>' && exec /bin/sh -l` delivered as PTY argv (the
proven command-app launch shape; the directory is POSIX single-quoted
so spaces, apostrophes, double quotes, $, ;, &&, |, backticks and
newlines all stay literal path data). Navigation to the Terminal screen
happens only after the session really exists (the onReady pattern).
Android-owned areas (PocketShell Downloads shelf, user-granted SAF
folders) never offer the action; their directory sheets show the honest
boundary note: "Android folders are not Linux guest directories. Copy
or move files into PocketShell Linux to work with them in Terminal."

Preconditions: P7 build installed in place; Linux runtime installed;
at least one existing terminal session for the integrity checks (E).

A. BASIC

1.  Open Files; browse PocketShell Linux (the "Linux" area), e.g. /root.
2.  Create a directory (e.g. /root/projects/my app).
3.  Long-press / "⋮" THAT directory entry to open its action sheet;
    confirm "Open Terminal Here" appears as a real action (between
    "Open" and "New folder").
4.  Tap Open Terminal Here.
5.  Confirm the Terminal screen appears only AFTER session creation
    (no empty/fake terminal tab, no flash of a dead session).
6.  Run `pwd` at the prompt.
7.  Confirm pwd prints THE TAPPED FOLDER's exact guest path
    (e.g. /root/projects/my app) — NOT the location the explorer was
    browsing when the sheet opened (the p7.0 regression this pins out).

B. NESTED DIRECTORY

8.  Create a deeply nested path (e.g. /root/a/b/c/d/e) and repeat
    steps 3-7 with the LEAF tapped from its parent listing.
9.  Run `pwd`.
10. Confirm the exact nested path.

C. SPECIAL PATHS

11. Create directories whose names contain: spaces ("my folder"), an
    apostrophe ("it's-here"), a dollar sign ("$HOME" as a literal
    name), a semicolon ("semi;colon"), and other shell metacharacters
    where the filesystem allows ("a && b", "a | b", "`cmd`").
12. For each: open its action sheet from the parent listing, tap Open
    Terminal Here, run `pwd`, and confirm pwd prints the literal TAPPED
    directory path (no truncation at the space, no shell expansion of
    $/backticks, no command ever executed from the name, prompt lands
    inside the directory).

D. NORMAL TERMINAL (a real session, not a one-shot)

13. After a launch: confirm `ls`, `cd ..`, `cd -` and normal commands
    work; the interactive prompt keeps accepting commands (the shell
    is NOT stuck in a one-shot command and did not exit).
14. Confirm the session behaves like every other Alpine session
    (keyboard deck, scrolling, session switcher).

E. EXISTING SESSIONS

15. Create two or more terminal sessions first (with content/cwd of
    their own, e.g. cd /root and leave a file listing on screen).
16. Open Terminal Here from Files.
17. Confirm a NEW session appears in the session switcher and is the
    selected one; the previous sessions are still open, still in the
    same order, with their content and cwd UNCHANGED.
18. Confirm no text was ever written into the old sessions' PTYs
    (their screens show exactly what was there before).

F. ANDROID BOUNDARY (honest, never a fake launch)

19. Switch to the "PocketShell Downloads" shelf; open a directory
    entry's action sheet: confirm NO "Open Terminal Here" action and
    the honest note instead ("Android folders are not Linux guest
    directories. Copy or move files into PocketShell Linux to work
    with them in Terminal.").
20. Repeat for a user-granted SAF folder (a real shared Download
    granted via the system picker) and one more SAF folder.
21. Confirm no fake Linux path is displayed anywhere for those areas
    (no /storage/emulated/0 fabrication, no content:// rendered as a
    POSIX path) and no launch can be triggered there.
22. Confirm the suggested workflow stays real: copy/move from the
    Android area into PocketShell Linux still works (Phase 4/5
    paths), and after copying, Open Terminal Here works in the Linux
    copy's directory.

G. M6/GLIBC REGRESSION (all through the UNCHANGED runtime path)

23. Plain Linux Shell from Home still works (musl Alpine shell).
24. node still works (node -v in a session).
25. cline / the glibc loader still work (existing M6 closure commands
    where applicable).
26. Sessions still survive screen rotation and process switching.
27. Confirm the APK permission list is UNCHANGED (5 permissions; no
    MANAGE/READ/WRITE_EXTERNAL_STORAGE, no new entries) — Phase 7
    adds no permissions, no manifest entries, no storage mounts.

H. THE "+" BUTTON MATCHES THE CURRENT ENVIRONMENT (p7.1)

28. In a LINUX session, tap "+" in the terminal's session bar: confirm
    a NEW "Alpine Linux" session opens at a real Linux prompt
    (`uname -a` shows Alpine) — NOT the Android /system/bin/sh shell.
29. In an ANDROID terminal session, tap "+": confirm the historical
    Android shell still opens there (the fallback is intentional).
30. Confirm all previous sessions (both kinds) remain open, unchanged,
    in order, with no text ever written into their PTYs.
31. Rotate the screen (ViewModel recreation), enter a fresh "Alpine
    Linux" session and repeat 28 — the pinned guest-label fallback
    keeps "+" on Linux. Disclosed limit: a command-app/catalog session
    after a rotation falls back to the Android shell (spawn-time
    registration is the only source for those labels; nothing is
    guessed).

## 38. Manual acceptance — M7.0.0 Phase 8 (File search: name search inside the selected storage area) — DEVICE GATE PENDING

Build under test: `PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk`
(version 0.10.0-m6.0.4, versionCode 44 — unchanged until P9). Note: the
original m7p8 APK was lost to a sandbox reset before delivery; this gate
runs on the rebuilt artifact, which re-verified every semantic pin (version,
permission set, embedded layer asset, dex symbols) — the rebuild also
carries Phase 8.1 (multi-select), which shares the same build.

What Phase 8 IS: a recursive NAME search of the CURRENTLY SELECTED storage
area — literal, case-insensitive substring on file/directory names, walked
through the unchanged Phase 2 storage abstraction, with explicit honest
limits (stops at 200 matches or 2000 folders; skipped folders are counted
and shown). What it is NOT: no background indexing, no database, no content
search, no fuzzy/AI matching, no new permissions, no cross-area search.

A. ENTERING / LEAVING SEARCH MODE

1. Open Files (Linux area). Confirm the header now shows a search icon
   next to the area chip.
2. Tap the search icon: the location row is REPLACED by a focused search
   field with the placeholder "Search in PocketShell Linux"; the area chip
   disappears (the scope must be unambiguous); the keyboard appears.
3. With the field EMPTY, confirm NO loading spinner and no scan happens
   (the hint line explains the scope; an empty query never walks storage).
4. Tap the "‹" (close) affordance at the field's right, then re-enter via
   the header icon. Confirm the field opens empty again.
5. Press system BACK inside search mode: search closes (the explorer
   location never moved during the search — confirm the listing is exactly
   where it was).

B. BASIC SEARCH (Linux area)

6. From any location, type `proj`: results appear with a folder/file icon,
   the NAME, and a relative location line (e.g. "root/projects"). Results
   come from the WHOLE area, not just the browsed folder.
7. Confirm case-insensitivity: `PROJ`, `Proj`, `proj` produce the same set.
8. Confirm substring behavior: `roje` matches "projects"; "abc" matches
   nothing with the honest "No matches for "abc"" center text.
9. Confirm dotfiles are searchable: `.env` is found by `env` and by `.env`.
10. Confirm directories AND files both appear, directories sorted first,
    names case-insensitive — consistent with the explorer's own order.
11. While a large search is running, confirm the spinner shows and typing
    more characters REPLACES the running search (newest query wins; no
    flashing of stale results).

C. RESULT ACTIVATION (the required behavior)

12. Tap a NESTED result: search mode closes and the explorer shows the
    result's PARENT directory, with the tapped entry visibly highlighted
    (tinted row). The location line shows the parent path.
13. Tap a ROOT-LEVEL result: the explorer shows the area root with the
    entry highlighted.
14. Open another folder afterwards: the highlight disappears (it marks
    only the activation landing).

D. SAFETY / QUERY-IS-DATA

15. Search for `..`, `../etc`, `/etc`, `*`, `$`, `;`, `&&`: no crash, no
    traversal, no fake matches — queries are literal data. (If a file
    literally named with `*` exists, the `*` query finds it by name.)
16. Confirm search NEVER leaves the selected area: in the Linux area there
    is no result that resolves into Android storage, and vice versa.

E. AREA SCOPE (each area searches only itself)

17. Linux area: results' relative paths are guest spines (e.g.
    "root/projects"), and protected prefixes (/etc, /usr) ARE searchable
    read-only — this is the user's own rootfs.
18. PocketShell Downloads shelf: switch to the shelf, enter search, type
    part of a file placed there earlier; confirm results and honest spine
    paths; confirm NO Linux-area results can appear.
19. A user-granted SAF folder (real shared Download): search finds names
    inside the granted tree only; relative locations are the document-tree
    spine (never a fabricated POSIX path).
20. Revoke the SAF grant in the system Settings while it is the current
    area, then search: confirm the honest error ("Could not search …") —
    never an empty-results pretend.
21. Switch areas WHILE search results are visible…: the area chip is
    hidden in search mode; exit search (back or ‹), switch, confirm no
    stale results from the previous area ever appear.

F. LIMITS AND PARTIALS (honest disclosures)

22. On a tree with >200 matching names, confirm the banner: "Stopped at
    the first 200 matches — refine the query to narrow the search."
23. On a very deep/wide tree (or slow provider), if the 2000-folder cap
    hits, confirm: "Stopped early — only part of <area> was searched, so
    results may be incomplete."
24. If a sub-folder cannot be listed, confirm the banner names the count
    and the first reason ("1 folder(s) could not be searched: …") while
    results from the rest of the area still appear.

G. REGRESSION (P2–P7.1 untouched)

25. Explorer regression ladder: navigation, up-nav at root, area switcher
    (remembered locations), New Folder/File, Import, copy/move/paste with
    Replace/Cancel, rename, delete, Share, Export, editor open/save —
    all through the SAME flows as P6/P7 (§34–§37 spot checks).
26. Open Terminal Here still opens THE TAPPED folder (§37 A steps).
27. Terminal "+" still matches the current session's environment (§37 H).
28. Confirm the APK permission list is UNCHANGED (6 permissions; no
    MANAGE/READ/WRITE_EXTERNAL_STORAGE, no new entries) — Phase 8 adds
    no permissions, no manifest entries, no new dependencies.

## 39. Manual acceptance — M7.0.0 Phase 8.1 (multi-select: copy / move / delete several entries at once) — DEVICE GATE PENDING

Build under test: the SAME `PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk`
as §38 (one build carries Phase 8 search AND Phase 8.1 multi-select).

What Phase 8.1 IS: a selection MODE over the CURRENT listing — toggle rows
(header select icon), then Copy / Move / Delete act on the whole selection
through the UNCHANGED Phase 4 per-entry operations, with an honest
aggregate notice (per-item failures keep their names and reasons) and the
same Replace/Cancel dialog per collision. What it is NOT: no new storage
APIs, no path input anywhere (selection is names of the CURRENT listing
only), no silent partial results, no new permissions.

A — Enter / exit selection mode
 1. Open a folder with several entries → header select icon → rows show
    checkboxes, the location row is replaced by "0 selected" + All / Copy /
    Move / Delete (the three actions disabled while nothing is picked).
 2. Tap rows → each toggles its checkbox and accent tint; the count updates.
 3. All → every row selected; tap one row again → only that one deselects.
 4. X (or system Back) → leaves selection mode; rows navigate again;
    re-enter → the selection starts empty.

B — Multi delete
 5. Select 2 files + 1 folder WITH content → Delete → dialog "Delete 3
    items?" names them and carries the folder-contents warning → Delete →
    notice "Deleted 3 items."; the rows disappear.
 6. Select a symlink → Delete → the "only links are deleted" warning shows;
    confirm → the link is gone, the target still opens with its content.
 7. Delete a selection where one entry disappears first (e.g. a folder
    holding it is deleted by another flow) → the notice names the failed
    item and its reason; the rest are still deleted.

C — Multi copy (the clipboard)
 8. Select 2–3 entries → Copy → selection mode exits; the paste banner
    reads "Holding N items to copy from <area> — open a destination and
    paste here."
 9. Navigate into another folder → Paste here → every item lands; sources
    remain; notice "Copied N items."
10. Cross-area: mark the copy in the Linux area, switch the chip to the
    Downloads shelf → the selection bar is GONE (a selection never survives
    an area switch, but the clipboard banner stays) → Paste → the items
    arrive in the shelf.
11. Cancel on the banner → nothing was changed anywhere.

D — Multi move
12. Select entries → Move → navigate elsewhere → Paste here → the sources
    are gone from the origin, present at the destination, "Moved N items."

E — Collisions (the honest dialog, reused per item)
13. Copy 2 entries where ONE name already exists at the destination → the
    Replace dialog names THAT item → Replace → that one overwrites, the
    rest complete; the aggregate notice reports everything.
14. Copy with a collision → Cancel → the notice says what already landed
    and "Cancelled at "x" — the remaining items were not touched."; the
    already-landed items ARE at the destination (partial never hidden).

F — Scope & safety
15. A selection never survives leaving its directory: select → open any
    folder (or Up) → selection mode is closed.
16. Search and selection never mix: opening search closes the selection;
    the select icon is hidden while search mode is open.
17. Selection is row-only: there is no way to type a path into it; nothing
    outside the current area listing can ever be selected.
18. SAF folder: multi copy/move/delete inside a granted tree works through
    the same flows; if the grant is revoked mid-session the honest error
    appears — never a fake success or a fake empty result.
19. Guest policy still bites: a delete that the area refuses (frozen
    runtime prefix) surfaces as a failed item with the area's own reason.

G — Regression ladder (P2–P8)
20. Single-entry copy / move / paste / rename / delete / New Folder / New
    File / Import / Share / Export still behave exactly as before (§34–§37
    spot checks) — the single paths were not rewritten.
21. Search (§38 spot): one query, tap a result → lands in the parent with
    the highlight.
22. Open Terminal Here still opens THE TAPPED folder (§37 A).
23. Terminal "+" still matches the current session's environment (§37 H).
24. Editor still opens listing files; Companion/terminal untouched.
25. App info: still versionName 0.10.0-m6.0.4 / versionCode 44 and the SAME
    6 permissions — Phase 8.1 adds no permissions, no manifest entries, no
    new dependencies.

## 40. Manual acceptance — M7.0.0 Phase 9 / FINAL RELEASE (search interaction fixes + M7 integration + version 0.11.0-m7.0.0) — DEVICE GATE PENDING

Build under test: `PocketShell-v0.11.0-m7.0.0-debug.apk` (versionCode 45,
versionName 0.11.0-m7.0.0 — the M7.0 release build; installs in place over
every earlier M7 build, same debug cert).

What Phase 9 IS: three device-reported search-UX fixes — (1) the search
results (and the whole Files screen) now end ABOVE the shared keyboard deck
(the Terminal/Editor inset rule), so a short result list is fully visible
and a long one scrolls every row into view — previously the list viewport
extended behind the deck and results were unreachable; (2) long-pressing a
search result lands on the result's parent and opens the SAME contextual
action sheet as an explorer row (Open / Open Terminal Here / New folder /
New file / Copy / Move / Share / Export / Rename / Delete — only the actions
valid for the entry's kind and area are exposed); (3) the duplicated
field-row close arrow is gone — the header X is the ONE close affordance,
the in-field ✕ only clears the query. What it is NOT: no change to how
search is triggered (same header icon, no Search button, no submit UI), no
change to search semantics (limits, generation guard, serial worker,
symlink behavior, query-as-data, error honesty, area boundaries), no new
operations engine (the sheet routes through the existing per-entry ops on
the landed parent), no new permissions or dependencies.

A — Search scrolling (the fix)
 1. Search a query with 2–3 results → every result is visible; the list
    ends above the keyboard deck; nothing hides behind it.
 2. Search a query with MANY results (more than fit the screen) → drag the
    results list up → it scrolls naturally; results beyond the initial
    viewport arrive; the tail of the list is reachable above the deck.
 3. With the keyboard deck toggled OFF, search again → the list ends above
    the gesture bar; the last rows are reachable.
 4. The search field row stays usable while results scroll (focus, typing,
    the in-field clear all keep working).

B — Search controls (one close behavior)
 5. The ONLY close controls are the header X (top-right, the same icon that
    opened search) and the system Back — both exit search mode.
 6. There is NO second arrow/close control in or beside the field row
    (the Phase 8 duplicate is removed).
 7. The in-field ✕ (visible while the query is non-empty) CLEARS the query
    only — it does not leave search mode.
 8. No Search button, no submit/Enter UI, no extra icon appeared anywhere —
    search triggers exactly as in §38 (type to search).

C — Search-result actions (long-press)
 9. Long-press a FILE result → the screen lands in the result's parent
    directory (entry marked, the §38 tap behavior) and the SAME action
    sheet opens: Copy / Move / Share / Export / Rename / Delete (Share and
    Export for files only); Kind shows File with its real size/date.
10. Long-press a DIRECTORY result → lands in its parent; the sheet shows
    Open / Open Terminal Here (Linux area only) / New folder / New file /
    Copy / Move / Rename / Delete.
11. Tap "Open" on a long-pressed directory result → navigates INTO it;
    tap "Open" (edit) on a file result → the editor opens it.
12. Actions work against the LANDED directory: Copy on a search result →
    navigate elsewhere → Paste here → the file arrives (the operation used
    the result's real parent, never the pre-search location).
13. Android-owned areas (shelf, SAF): the directory sheet shows the honest
    Android-boundary note instead of Open Terminal Here — no launch button.
14. Long-press a SYMLINK result → the sheet shows Kind: Symlink and Target;
    Copy/Move/Delete act on the link (targets untouched), no descent.
15. Special-character names: long-press a result whose name contains
    quotes/spaces/& → the sheet title and every action treat it literally.
16. Vanished entry: long-press a result, then (before acting) delete its
    parent via another path/session if reproducible → the landing lists
    honestly (or errors honestly); NO sheet opens over a stale entry.
17. Protected paths stay protected: a Linux result under a frozen runtime
    prefix refuses deletion through the sheet with the area's own reason.

D — Multi-select regression (§39 spot ladder)
18. Multi copy → paste (same area + cross-area) → "Copied N items."
19. Multi move → sources gone, destination has them.
20. Multi delete with folder contents → per-kind warnings, then deleted.
21. Collision: Replace completes the item and the rest; Cancel reports
    what already landed + "Cancelled at …" — partial never hidden.

E — Editor regression (§35 spot)
22. Open a text file from the listing → edit → Save → back → the guard
    only fires when dirty; binary/oversized files still refuse honestly.

F — Terminal regression (§37 spot)
23. Open Terminal Here on a nested folder and on a folder whose name
    contains metacharacters → the session's pwd IS that folder (p7.1).
24. Existing sessions keep their integrity; "+" still matches the current
    session's environment.

G — Android / SAF boundaries (§36 spot)
25. The switcher still reads "PocketShell Downloads" (app shelf) vs the
    granted real "Download" (its own name); both still work.
26. Real Android Download via SAF: copy a file in from the shelf, see it
    in the system Files app; kill + reopen the app → the grant reattaches.
27. App info: versionName 0.11.0-m7.0.0 / versionCode 45 and the SAME
    6 permissions — Phase 9 adds no permissions, no manifest entries,
    no new dependencies.

## 41. Manual acceptance — M7.1 P1 (Home launchers: companion + CLI tool
launchers, hide/restore, custom tools, text badges) — DEVICE GATE PENDING

Build under test: the vc45 `0.11.0-m7.0.0` build with M7.1 P1 (installs in
place over the M7.0 release, same debug cert, version stamp unchanged —
P1 does not bump the version; the next number is deliberately NOT guessed
here).

What P1 IS: Home gains a Companions section (the four preinstalled
companion websites ChatGPT / Claude / Z.ai / GitHub, seeded once through
the existing companion storage) and a launcher-style "Your tools" grid
(the built-in CLI registry incl. the new Cline entry + user-defined custom
tools). Long-press removes a launcher from Home (HIDE-only, restorable in
Settings → Home launchers). Custom tools launch through the SAME
verify-then-launch guest path as the built-in registry apps. What it is
NOT: no package manager, no app store, no CLI discovery, no install-state
claims (a launcher on Home never means "installed" — the tap-time guest
probe answers that honestly), no companion APIs/SDKs/Android-app
integration (companions stay websites on the existing web canvas), no new
permissions, no frozen-architecture changes (the companion package is
touched ZERO — seeds ride its public API).

A — Companions on Home
 1. First launch after install → Home shows a Companions section with
    ChatGPT, Claude, Z.ai, GitHub (letter badges C / Cl / Z / G).
 2. Tap ChatGPT → the EXISTING companion sheet raises (~55% default) and
    loads chatgpt.com on the same web canvas as before; the tab strip
    shows the tab; long-press badge behaviors unchanged.
 3. Tap each of the four → each opens its own site; switching tabs,
    closing tabs, and the "+" (default companion) all behave as in M6.
 4. Sign in to one companion, kill + reopen the app → the session
    survives (web data lives in the existing private web storage).

B — Companion management
 5. Settings → Launchers → Companions: the four built-ins each have a
    show-on-Home switch; a deleted built-in shows "Restore" instead.
 6. Hide ChatGPT from Home (long-press → Remove from Home → confirm) →
    the tile disappears; the definition and its web data stay; Settings
    → Launchers shows the switch OFF; turning it ON brings the tile back.
 7. Delete a built-in in Settings → Companion websites (existing screen)
    → gone everywhere; Settings → Launchers shows "Restore" → restoring
    re-adds it with the SAME fixed identity.
 8. Add a custom companion (existing Companion settings screen) → it
    appears on Home with a text badge; long-press → Remove from Home
    hides it; the Launchers screen can show it again; delete there stays
    possible only in the Companion screen.
 9. Set an icon for a companion in Settings → Launchers (system image
    picker) → the Home tile shows the image; delete the ORIGINAL image
    in the system Files app first → the launcher icon STILL works (the
    import was copied into PocketShell storage).

C — Your tools (built-in CLI launchers)
10. Home → Your tools shows the registry launchers (Hermes Agent,
    OpenCode, Claude Code, ZCode, Kilo Code, Cline, Gemini CLI, Codex,
    Aider, Qwen Code) with letter badges; collisions resolve (Claude
    Code → Cl, Codex → Co, Cline → C).
11. Tap a tool whose command is NOT installed → the honest launch-error
    banner: "'<cmd>' command was not found (verified with the real guest
    shell)". NO fake session, NO crash, NO install claim on the tile.
12. Install one (e.g. `apk add` / npm the CLI inside the Linux shell),
    return to Home, tap its tile again → the verify-then-launch path
    confirms and a REAL Linux session opens running the tool.
13. Tap a tool while the runtime is NOT ready (fresh install) → the
    honest "needs the Linux runtime" banner.

D — Custom tools
14. Settings → Launchers → + Add Custom Tool: Name + Command (e.g.
    "htop --tree") → Add to Home → the tile appears on Home.
15. Tap it → a real Linux session runs the command (same verify-then-
    launch path); when the command exits, a live prompt remains.
16. A custom tool whose head command is absent → the honest not-found
    banner naming the probed command.
17. Edit a custom tool (Launchers → Edit) → changes persist; Remove →
    the tool AND its icon copy are gone (nothing else is touched).
18. Custom tool validation: blank name/command refused; a multi-line
    command is refused (single-line rule stated in the UI).

E — Icons and badges
19. A custom tool with an imported icon shows the image on Home; one
    without shows its letter badge; deleting the imported icon via the
    Launchers screen falls back to the badge.
20. Home badge collisions stay deterministic per section (companions:
    C / Cl / Z / G; tools: Claude Code → Cl, Cline → C, Codex → Co).

F — Remove-from-Home semantics
21. Long-press ANY launcher tile → confirm dialog states hide-only
    semantics ("Nothing is uninstalled or deleted") → confirming hides
    ONLY that tile; the dialog's Cancel changes nothing.
22. After hiding everything in a section, the section collapses (tools
    section shows one quiet "All launchers are hidden" line); Manage
    links restore.

G — Regression ladder
23. Files: search scrolls above the deck (§40 A), long-press actions on
    results still land on the fresh listing (§40 C), multi-select
    copy/move/delete still aggregate honestly (§39).
24. Terminal: Open Terminal Here from a directory with hostile
    characters still lands inside it; sessions from launcher taps are
    normal Alpine sessions (session list, "+", retention all unchanged).
25. Editor: open/save/dirty-guard unchanged.
26. Android/SAF: Downloads shelf + real SAF Download still label and
    work; no permission prompts re-appear.
27. App info: versionName 0.11.0-m7.0.0 / versionCode 45 and the SAME
    6 permissions — P1 adds no permissions, no manifest entries, no new
    dependencies.

## 42. Manual acceptance — M7.1 P2 (launcher UI repair + official icons + Antigravity) — DEVICE GATE PENDING

The screenshot that opened this phase: the Home-launchers settings row for a
DELETED built-in companion seed rendered its title ("GitHub") and subtitle
("Not on this install") one character per line, with a full-width Restore
button stretched across the row. Root cause: MidnightQuietButton hard-fills
its width (`Box(Modifier.fillMaxWidth())`) — a page-level component placed
in an unweighted Row slot starves the weighted text column to zero. P2 fixes
the row (Restore is now the compact text action every other row action uses)
and unifies ALL built-in settings rows through one shared
`LauncherSettingRow` (fixed icon → weighted text column → optional action →
optional control). A source-contract JVM test
(LauncherRowLayoutTest) pins that structure; this checklist is the visual
gate on real hardware.

### A — Launcher settings layout (the must-fix regression reference)

1. Settings → Home launchers, delete a built-in companion (existing
   Companion websites screen) so the "Not on this install" restore row
   appears → the title is a NORMAL horizontal single line, the subtitle
   sits under it, "Restore" is a compact right-aligned text action — the
   screenshot's vertical collapse and stretched button are gone.
2. Every companion row: icon left, title + URL left-aligned in the middle
   (one line each at default widths), Icon/Clear-icon action and the toggle
   right-aligned, toggle never pushed off screen.
3. Every CLI tool row: title + mono command line; a long command (e.g.
   Codex's or a custom long line) wraps naturally by words when genuinely
   necessary — never character-by-character, never clipped by a control.
4. Long display names ("Claude Code", "Antigravity", "Hermes Agent") stay
   on one line at default widths and ellipsize cleanly only on the
   narrowest screens; controls remain visible and aligned in every case.
5. Small-width device (or split-screen narrow): repeat 1–4 — the text
   column shrinks first, controls keep their sizes, nothing overlaps.
6. Toggle alignment: all switches in a section sit on one vertical line.

### B — Icons

7. Home → Companions: ChatGPT (white knot on the OpenAI-black tile),
   Claude (terracotta starburst), Z.ai (Z tile), GitHub (white octocat on
   the GitHub-dark tile) render from bundled assets — no network needed
   (airplane mode: identical).
8. Home → Your tools: Hermes (mascot on white), OpenCode (its own dark
   tile glyph), Claude Code (starburst), ZCode, Kilo Code (pixel letters
   on white), Cline (robot head on white), Antigravity (colored arc),
   Codex (white knot on black), Qwen Code show their bundled marks at
   consistent visual bounds; no mark dominates by shape (normalized
   inside one square). Aider is absent from every surface.
9. A custom companion/tool with NO icon still shows the deterministic
   letter badge (bundled icons never displace the badge fallback for
   custom launchers).
10. A user-imported icon STILL wins over the bundled mark (import one for
    a built-in, e.g. GitHub → the user image shows; Clear icon → the
    bundled mark returns).
11. Settings → Home launchers rows show the same bundled icons (36dp) —
    same resolution order as Home (imported → bundled → badge).

### C — Antigravity / Gemini CLI

12. Home → Your tools (and the CLI tools settings section): Antigravity is
    present in the curated default set (command `agy` shown in the row);
    Gemini CLI appears NOWHERE (defaults, settings, restore).
13. Tap Antigravity when `agy` is not installed → the honest not-found
    banner ("'agy' command was not found (verified with the real guest
    shell)") — a launcher on Home is not an install claim.
14. If `agy` becomes available in the guest (upstream ships a musl build,
    or the glibc route), the tap launches it through the SAME
    verify-then-launch path as every other launcher.

### D — Regression ladder

15. §41 spot checks: hide/restore semantics, custom tool add/edit/remove,
    badge collisions on the settings screen (a deleted-seed restore row
    now shares the collision space with existing definitions — no
    duplicate badge letters visible at once).
16. Companion sheet/WebView behavior untouched (open a companion, tabs,
    back); no new permissions (App info: versionName 0.11.0-m7.0.0 /
    versionCode 45, the SAME 6 permissions).

## 43. Manual acceptance — M7.1 P2.1 (Aider removed + owner-supplied mark refresh) — DEVICE GATE PENDING

A small increment on §42: the owner removed Aider from the curated
CLI launcher set and supplied nine official brand SVGs (vendored
in-tree at `scripts/icon_sources/*.svg`) that replace the fetched
favicons for ChatGPT, Claude, Z.ai, GitHub, Hermes Agent, OpenCode,
Kilo Code, Cline, and Antigravity. The bundled-asset pipeline,
resolution order (imported → bundled → badge), and the M6 frozen
surfaces are untouched.

1. Home → Your tools: Aider appears NOWHERE (defaults, settings,
   restore); the section shows nine CLI launchers.
2. A stale persisted 'aider' hide id is inert (hide/restore list never
   resurrects it; no crash, no empty row).
3. The nine refreshed marks render from bundled assets (airplane mode:
   identical): ChatGPT white knot on OpenAI-black, Claude terracotta
   starburst, Z.ai tile, GitHub white octocat on GitHub-dark, Hermes
   mascot on white, OpenCode dark tile glyph, Kilo Code pixel letters on
   white, Cline robot head on white, Antigravity colored arc.
4. The four untouched marks (Claude Code, ZCode, Codex, Qwen Code)
   render exactly as §42 step 8 described.
5. Letter-badge fallback still works for custom launchers and for any
   bundled asset that fails to decode; a user-imported icon still wins
   over every bundled mark.

## 44. Manual acceptance — M7.1 P2.2 (theme-scheme icons + x-scroll home rows + packages affordance) — DEVICE GATE PENDING

The user's P2.2 quick-fix brief, three parts: (1) bundled icon colors
follow the app theme scheme — every curated mark now ships TWO variants
(`{id}.webp` Midnight, `{id}-light.webp` Daylight), with glyph marks
painted on the SAME theme plate the badge tiles use
(`TerminalTheme.keyAlt`: #16233F dark / #EDF1F7 light); (2) Companions
scroll in ONE horizontal row, tools in TWO, both with page dots;
(3) the packages affordance lives in the "Your tools" header (Manage →
packages page) and the old mid-page footer link is gone. No new
permissions, no new dependencies, no version bump, M6 frozen surfaces
untouched.

1. Theme — dark: Home launcher icons read on Midnight (Hermes mascot,
   Kilo letters, Cline robot in WHITE on the dark navy plate; ChatGPT
   knot, GitHub octocat, Codex flower white on the same plate).
2. Theme — light (Settings → Theme → Daylight): the SAME tiles flip to
   the paper plate with ink glyphs (ChatGPT black knot, GitHub
   near-black octocat, ink Cline/Hermes/Kilo/Codex) — nothing white-on-
   white, nothing glaring.
3. Theme — AMOLED and System follow the same rule; a live theme flip
   swaps the marks WITHOUT leaving Home (no reopen needed).
4. User-imported icons still win over every bundled mark and do NOT
   change with the theme.
5. Companions: exactly ONE row, x-scroll; four built-ins (ChatGPT,
   Claude, Z.ai, GitHub) overflow one page on a phone and show 2 dots.
6. Your tools: exactly TWO rows, x-scroll, nine CLI launchers (+
   customs); dots show when content exceeds one page (3 visible columns
   on a phone → 2 dots).
7. Scroll dots: the accent pill tracks the visible page while scrolling;
   with one page of content (wide tablet) no dots render.
8. Long-press remove-from-Home still works on BOTH scrollers; the
   remove dialog restores via Manage as before.
9. Your tools header: "Manage" opens the PACKAGES page (not launcher
   settings); Companions' "Manage" still opens Home-launcher settings;
   the old centered Packages footer link is GONE from the page.
10. Verify-then-launch honesty unchanged: a tap on any tool still runs
    the guest probe with the verifying spinner in the scrolled row.

## 45. Manual acceptance — M7.1 P3 (external keyboard detection + automatic on-screen keyboard control) — DEVICE GATE PENDING

The P3 brief: when a physical external keyboard (USB / Bluetooth / dock /
DeX) connects while PocketShell is running, the shared on-screen deck hides
itself, one in-app notice explains it ("External keyboard detected — the
on-screen keyboard has been turned off. You can change this in Settings."),
and the hardware keyboard types straight into the terminal; when it
disconnects, the deck returns exactly as the user left it — no restart, no
replug, no polling. A Settings toggle ("On-screen keyboard — automatically
hide when an external keyboard is connected", default ON) owns the
automatic behavior; turning it OFF keeps the deck fully manual. No new
permissions, no new dependencies, no version bump, M6 frozen surfaces
untouched. JVM coverage: the device predicate (ExternalKeyboardPredicateTest
— touchscreens/mice/gamepads/virtual/button-clusters all rejected), the
debounced transition machine (ExternalKeyboardDetectorTest — duplicates
coalesce, sub-window BT flaps never flicker), the suppression policy
(ExternalKeyboardPolicyTest — the user's manual state is never destroyed),
and the integration contract (ExternalKeyboardIntegrationTest). REAL
attach/detach can only be proven here, on hardware.

External keyboard — USB

1.  Launch PocketShell with NO keyboard attached: the deck appears
    normally; no notice is shown anywhere.
2.  Open a terminal session and type with the deck: normal input.
3.  Attach a USB keyboard (OTG / dock): within ~a second the deck hides,
    the terminal area expands to the bottom edge, and ONE notice appears
    near the top: "External keyboard detected — the on-screen keyboard has
    been turned off. You can change this in Settings."
4.  Type on the hardware keyboard in the terminal: characters arrive
    immediately (deck presses are not needed).
5.  The notice auto-dismisses within ~5 s and NEVER re-appears while the
    keyboard stays connected (navigate between screens, rotate, tap
    around — no re-notify).
6.  Files screen with the keyboard attached: the listing runs to the
    bottom of the screen (M7.0 inset fix intact) and scrolls fully.
7.  Unplug the USB keyboard: the deck returns automatically, the inset
    recalculates on Terminal, Editor, Files, and Companion immediately —
    no restart, no toggle needed.

External keyboard — Bluetooth

8.  Pair a BT keyboard: same connect behavior (deck hides, one notice,
    hardware typing works). Repeat the unplug/turn-off: deck returns.
9.  BT flaps: toggle the keyboard's connection a few times quickly — no
    flickering of the deck beyond one hide/show cycle per stable change,
    no notice spam.

Launch with the keyboard already attached

10. Connect the keyboard, then launch PocketShell cold (force-stop
    first): the deck auto-hides during the first moments of the session
    and the state is correct without replugging.

Settings

11. Settings → Keyboard: the "On-screen keyboard" toggle exists and is
    ON by default. Turn it OFF; connect the keyboard: the deck does NOT
    change; disconnect: the deck does NOT change (fully manual).
12. Turn it back ON while the keyboard is connected: the deck hides
    immediately. Disconnect: the deck returns. Toggle OFF → kill and
    relaunch the app → toggle still OFF (persists across restart); ON
    likewise.

Manual controls and regression

13. With the keyboard connected (deck hidden): tap the floating [⌨]
    icon — the deck opens (manual override); disconnect the keyboard —
    the deck STAYS open (the user wins; no forced restore fight).
14. Hide the deck manually with NO keyboard attached, connect the
    keyboard, disconnect it: the deck returns HIDDEN (the pre-connect
    state is restored, not blindly opened).
15. Companion over any screen with the keyboard attached: tapping a web
    input does NOT pop the deck open while suppression is active; the
    hardware keyboard types into the web input.
16. Full keyboard regression: extra keys, CTRL/ALT/SHIFT (one-shot +
    locked), arrow keys, deck animations, keyboard bottom inset on
    Terminal / Editor / Files / Companion — all as before (§42 B steps).

---

## 46. M7.1 release QA (closure verification, v0.11.1-m7.1.0 / versionCode 46)

The M7.1 closure pass. Every check below was EXECUTED at the release cut on
the exact release bytes; the honesty line stands — JVM + structural audits
prove the build, the device gates prove the hardware, and both are required.

Automated closure verification (executed at the release cut):

1.  FULL JVM suite forced --rerun-tasks at the release stamp:
    734/734 effective green (app 589 + terminal-emulator 145),
    0 failures, 0 errors — P3 suites, P2.2 dual-theme/home-rows pins and
    the P2.1 aider-absence pins all included.
2.  Fresh `:app:assembleDebug` at the release stamp: app-debug.apk
    30,448,845 B, sha256 2d298c85…27eaa0.
3.  aapt2 badging: versionCode='46', versionName='0.11.1-m7.1.0',
    targetSdk 28 — the official M7.1 stamp (the M5.1.0 sub-milestone
    precedent: 0.9.1-m5.1.0 → semantic patch digit + milestone suffix).
4.  Permission audit: exactly the UNCHANGED 6-permission set
    (INTERNET, ACCESS_NETWORK_STATE, FOREGROUND_SERVICE,
    FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS,
    DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION). M7.1 added zero.
5.  Icon audit: exactly 26 launcher_icons asset entries in the APK
    (13 Midnight + 13 Daylight).
6.  Dex audit: ExternalKeyboardDetector / ExternalKeyboardViewModel /
    ExternalKeyboardPolicy / ExternalKeyboardNoticeBar present (P3);
    LauncherScroller / ScrollDots present (P2.2); PackagesFooterLink ABSENT
    and zero aider strings (P2.1) — no stale phase symbols on the release.
7.  Cert audit: apksigner Signer #1 SHA-256 d96a6f66…8bf659 — the pinned
    debug identity since v0.4.1; in-place update over every earlier build.

Release QA scope (what §41-§45 gates still owe on hardware):

-   Home: launchers render (one x-scroll Companions row, two tools rows,
    scroll dots), themed marks flip with the theme, Manage opens packages.
-   Terminal + launchers: launch a curated tool and a custom tool through
    verify-then-launch; badges and hide/restore behave.
-   External keyboard: §45 in full — USB connect hides the deck + ONE
    notice; UNPLUG restores the configured on-screen keyboard exactly as
    the user left it (manual-hidden returns hidden, manually-open returns
    open, a mid-suppression reopen cancels suppression).
-   Custom keyboard toggle + persisted settings: the deck toggle, floating
    [⌨] icon, terminal tap, extra keys, modifiers, arrows; the Settings
    toggles (theme, font size, On-screen keyboard) persist across restart.

## 47. M7.1.1 — external keyboard detection fix (v0.11.2-m7.1.1 / versionCode 47)

M7.1 P3 passed every JVM suite and still failed on the real device
(Samsung SM-F711B / Galaxy Z Flip 3, One UI, API 35). M7.1.1 is the fix
release: it names the failure causes, rebuilds the detection and visibility
layers against them, and defines the mandatory real-device gate below.

### 47.1 Why M7.1 P3 failed (the audit findings)

1.  **The terminal-canvas tap cancelled the auto-hide (primary defect).**
    `TerminalScreen.onSingleTap` funneled every canvas tap through the
    M7.1 manual-open path, which cleared the suppression memory. On a
    terminal app the canvas tap is the most-used gesture: the deck hid on
    connect and popped straight back up on the next touch of the terminal.
    The auto-hide was therefore invisible in the app's primary workflow.
2.  **Debounce starvation.** The M7.1 stability window restarted on EVERY
    input-device event with no ceiling. A device stack that emits periodic
    `onInputDeviceChanged` re-announcements (Bluetooth LE HID reality:
    LED/battery/layout config events; some OEM stacks) could defer the
    confirm forever — detection never fires. The fix is a hard confirm
    deadline (2,000 ms from the first unconfirmed event) that no event
    storm can push back; sub-window flaps still never flicker.
3.  **A single detection mechanism.** Only the `InputManager
    .InputDeviceListener` drove re-evaluation while foregrounded. OEM
    stacks can miss listener callbacks for Bluetooth HID (re)connection
    flows. The fix adds the Application-level `ComponentCallbacks2
    .onConfigurationChanged` cross-check (keyboard connect/disconnect is a
    system configuration change; the manifest already declares
    `keyboard|keyboardHidden|navigation`, so the callback fires in place)
    — a second, independent system path into the same coalescing detector.
4.  **No disconnect notice.** The spec requires the
    "External keyboard disconnected." message; M7.1 restored silently.
    M7.1.1 emits one notice per confirmed transition in BOTH directions.
5.  **No persistent on-screen keyboard preference.** The deck visibility
    was a runtime state and the only setting was an auto-hide opt-out, so
    a disconnect could force the deck back on against the user's wishes.
    M7.1.1 replaces both with the persistent
    `onscreen_keyboard_enabled` preference (DataStore, default On):
    detection is a temporary runtime override that never writes it, and a
    disconnect re-evaluates the preference instead of a memory.

### 47.2 The M7.1.1 state model (one authoritative system)

    externalKeyboardConnected      (hardware truth — detector)
    onscreenKeyboardUserEnabled    (persistent preference — DataStore)
    manualRequest                  (the user's last explicit [⌨]/deck action)

    shouldShowOnscreenKeyboard = manualRequest ?: (userEnabled && !connected)

-   Hardware transitions and preference changes clear `manualRequest`.
-   The terminal-canvas tap is gated in the model: it does NOT reopen the
    deck while an external keyboard is connected; without one it reopens
    exactly like m4.0.12.
-   `[⌨]` / deck collapse (`requestShow`) always work and never touch the
    preference; web-focus auto-open is gated while connected.
-   JVM-pinned by ExternalKeyboardVisibilityModelTest (14 cases),
    ExternalKeyboardDetectorTest (14 cases incl. the storm/deadline
    proofs), ExternalKeyboardPredicateTest (11, unchanged), and the
    structural ExternalKeyboardIntegrationTest (11). Clean rerun at this
    stamp: 746/746 (app 601 + terminal-emulator 145).

### 47.3 REAL-DEVICE GATE — run on the actual phone (mandatory)

No JVM run completes this section. The keyboard used must be the user's
real external keyboard. Install v0.11.2-m7.1.1 first.

Connect-direction checks:

1.  App open in the TERMINAL, no keyboard attached → deck behaves
    normally (shows on canvas tap, hides via the deck toggle).
2.  Connect the external keyboard (USB) while the terminal is open →
    within ~0.5–2 s the deck hides, ONE banner
    ("External keyboard detected — onscreen keyboard disabled.") appears
    and auto-dismisses; no further banners.
3.  WITH the keyboard still connected, tap the terminal canvas several
    times → the deck must STAY hidden (the M7.1 failure). Hardware keys
    reach the terminal.
4.  Tap the floating [⌨] icon → the deck opens (the user's explicit
    action wins); tap the deck's collapse toggle → hidden again.
5.  Repeat 2–4 over BLUETOOTH (disconnect the BT keyboard from the device,
    wait, reconnect from Bluetooth settings) → same behavior.
6.  Start the app WITH the keyboard already attached → within ~0.5 s of
    the home screen the deck is disabled (floating [⌨] visible), exactly
    one connect banner.

Disconnect-direction checks:

7.  Unplug the USB keyboard while the terminal is open → the deck returns
    (preference On), ONE banner ("External keyboard disconnected."), no
    spam.
8.  Disconnect the BLUETOOTH keyboard (power it off) while the app is
    open → same restore. Also verify the app recovers if the keyboard
    reconnected while the phone was in another app: return to PocketShell
    → the deck state matches reality on return.
9.  Start the app with the keyboard attached, then unplug → the deck
    returns per the preference.

Settings-interaction checks:

10. Settings → "On-screen keyboard" OFF (keyboard connected): the deck
    stays hidden; unplug → the deck STAYS hidden (never forced back on);
    the disconnect banner still says the keyboard is off.
11. Settings → "On-screen keyboard" OFF (no keyboard): the deck closes
    now; it stays closed across app restart (the persistent preference).
12. Settings → back ON with the keyboard attached: the deck stays hidden
    while connected and returns on disconnect. Tapping [⌨] still opens it
    immediately.
13. Kill the app process (recents swipe) with the preference OFF, relaunch
    → the deck does not resurrect.

Regression sweep (with and without the keyboard connected):

14. Companions: a WebView input focus does not pop the deck while a
    keyboard is connected (and does open it when none is).
15. Terminal basics with the hardware keyboard: typing, Enter, arrows,
    Ctrl-combos via the hardware keys reach the session.
16. The deck with no keyboard: every key still dispatches (letters,
    modifiers, extra keys) — the M6 one-keyboard behavior unchanged.

## 48. Manual acceptance — M7.2 P1 (notification foundation: POST_NOTIFICATIONS runtime request, channels, tap routing) — DEVICE GATE PENDING

Build under test: `PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk`
(versionCode 47 / versionName `0.11.2-m7.1.1` — the M7.x phase-build
precedent: phase builds ride the inherited stamp, the suffix is
filename-only), git tip of the M7.2 P1 chain.

What P1 IS: infrastructure only. The declared-but-never-requested
`POST_NOTIFICATIONS` runtime permission is now requested exactly once per
install on Android 13+, a new `session_events` channel exists beside the
untouched `terminal_sessions` FGS channel, notification taps are processed
on both activity paths, and a startup sweep cancels coordinator-owned
stale event notifications. What P1 is NOT: no event notification is ever
posted by production code yet (no real lifecycle signal exists to report
until P2), no agent detection, no waiting-for-input heuristics, no
`/proc` scanning.

Verified WITHOUT a device (this build's honest floor):

-   Full JVM suite forced rerun: 780/780 (app 635 + terminal-emulator 145,
    0 failures / 0 errors) including the four NEW suites:
    `NotificationPermissionPolicyTest` (6 — the complete anti-nag truth
    table), `NotificationIdsTest` (7 — deterministic identity, FGS-space
    separation, silent-wraparound refusals), `NotificationRouteTest`
    (7 — the routing parser), `NotificationIntegrationTest` (14 —
    structural pins: both intent paths, gate placement, flag-before-launch
    ordering, coordinator boundaries, untouched FGS channel, unchanged
    declared permissions).
-   APK audit on the exact bytes: aapt2 badging vc47 / `0.11.2-m7.1.1`
    targetSdk 28; the UNCHANGED 6-permission merged set (INTERNET,
    ACCESS_NETWORK_STATE, FOREGROUND_SERVICE,
    FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS,
    DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION); 26 launcher icon assets;
    dex carries the seven `notifications` symbols; cert
    d96a6f66…8bf659 unchanged.
-   Sweep honesty: no production path posts an event notification in P1,
    so the stale sweep cannot be user-exercised yet — it is pinned
    structurally and by JVM identity/ledger tests; a device-level sweep
    test arrives with P2's first real posts (§48 record updated then).

Device gate (the 12 steps below require a real Android 13+ phone; the
permission system and OEM notification surfaces cannot be exercised from
a JVM):

1.  Fresh install (or clear-data the app) on an Android 13+ device; open
    the app and wait on Home: NO permission dialog may appear (the gate
    fires only when the first session exists).
2.  Create the first terminal session (Open Terminal): the native
    Android notification-permission dialog appears exactly once, without
    an app-made interstitial.
3.  Grant the permission: the app continues normally; the terminal is
    unaffected.
4.  Background the app with the session running: the `TerminalService`
    foreground notification ("session(s) running") is now VISIBLE in the
    shade (on the M7.1.1 build it was invisible without the permission).
5.  Deny path: clear app data (permission resets to denied-and-unasked),
    first session → deny the dialog: NO crash, the terminal keeps
    working, the FGS service still starts and keeps the session alive in
    the background (only the notification is hidden).
6.  Anti-nag: on the denied install, relaunch the app and create more
    sessions (including after a full process kill): the dialog NEVER
    re-appears. (The one-shot flag is persisted before the dialog opens.)
7.  Channels: with a session running, `adb shell dumpsys notification`
    lists BOTH channels — `terminal_sessions` (existing FGS) and
    `session_events` (new, Importance Default) — created exactly once
    each.
8.  Warm tap routing: with the app open, tap the FGS notification in the
    shade: the app comes to front and behaves normally (the
    previously-unhandled `onNewIntent` path now processes the intent —
    logcat `PocketShellNotif` shows the route line).
9.  Cold tap routing: swipe the app away (or force-stop), then tap the
    FGS notification: the app cold-starts to Home and behaves normally.
10. Pre-13 behavior (if a pre-Android-13 device is available): no
    permission dialog ever; the FGS notification shows as before.
11. Regression: the M7.1.1 §47 keyboard sweep still passes (P1 touches no
    keyboard code) and terminal basics are unchanged (spawn, output,
    close, background retention via the FGS).
12. Honesty check: no notification in the shade ever claims an agent or
    command completed — P1 posts nothing; anything claiming otherwise is
    a defect.

## 49. Manual acceptance — M7.2 P2 (session lifecycle engine & structured exit status) — DEVICE GATE PENDING

Build under test: `PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk`
(versionCode 47 / versionName `0.11.2-m7.1.1` — the inherited phase stamp,
the `-m72p2` suffix is filename-only), git tip of the M7.2 P2 chain.

What P2 IS: internal lifecycle infrastructure, built exactly on the P0
audit's provable state machine (§10.3). `SessionEntry` now carries a typed
`SessionLifecycleState` (STARTING → RUNNING → FINISHED, with REMOVED
modeled by tab removal), the real waitpid exit status surfaces into the
entry as a structured value (`Exited(code)` / `Signaled(signal)` — exactly
what `JNI.waitFor` provides, nothing invented), every session carries
structured spawn identity (`SpawnOrigin` for the five launch paths +
`AgentHint` for the three named-launcher paths, graded
`LAUNCH_METADATA`), the manager emits typed lifecycle events for
downstream consumers, and `AgentActivityRepository` is the derived read
model. What P2 is NOT: no user-visible UI change, no notification posted
(P1's sweep stays dormant — nothing posts event notifications yet), no
agent detection, no completion claims, no waiting-for-input heuristics,
no `/proc` scanning, no OSC 133, no persistence (lifecycle state is
in-memory and dies with the process honestly, like the sessions
themselves).

Verified WITHOUT a device (this build's honest floor):

-   Full JVM suite forced rerun: **810/810** (app 665 + terminal-emulator
    145, 0 failures / 0 errors / **0 skipped**) including the two NEW
    suites: `SessionLifecycleStateTest` (16 — the full transition truth
    table: the fork signal, exited/signal mapping, duplicate-callback
    idempotency with the first real status surviving, rejected-transition
    state preservation, the raw waitpid mapping table, the spawn
    vocabulary) and `SessionLifecycleIntegrationTest` (14 — structural
    pins over the real sources: typed-state storage with a DERIVED
    `isFinished`, STARTING-at-spawn, the end-to-end fork-signal wiring,
    waitpid-through-the-machine with the still-running guard, the
    kill(0) close guard, honest logging of every unexpected delivery,
    exactly-one-emission-per-kind, no polling/timers//proc/DataStore in
    the engine, all five spawn-site origins, the repository's
    stores-nothing contract, the terminal↔notifications boundary,
    TerminalService untouched, the single LAUNCH_METADATA grade).
-   Verification-honesty fix (discovered while building the P2 suites):
    `NotificationIntegrationTest`'s 14 P1 structural pins resolved their
    sources only from a project-root working directory, so under the
    standard module-dir runner they silently SKIPPED (Assume) — pins that
    never ran could look green inside a totals line. Both suites now use
    the established dual-candidate resolution (root `app/src/…` and
    module `src/…`), and the 14 P1 pins RUN and pass inside the 810/810.
-   APK audit on the exact bytes: aapt2 badging vc47 / `0.11.2-m7.1.1`
    targetSdk 28; the UNCHANGED 6-permission merged set; dex carries the
    six new P2 symbols (`SessionLifecycleState`, `SessionLifecycleEvent`,
    `ExitStatus`, `SpawnOrigin`, `AgentHint`,
    `AgentActivityRepository`); cert d96a6f66…8bf659 unchanged.

Device gate (the checks below require a real phone; they are a
REGRESSION gate — P2 changes no user-visible behavior, so the goal is
proving the lifecycle engine's real-signal paths behave exactly as the
pre-P2 build did):

1.  Spawn matrix: every launch path still spawns a real session —
    Terminal ("+"/new session), Linux Shell, one registry app tile, one
    catalog app via Explore → Open, and Files → Open Terminal Here.
2.  Natural exit with code 0: in an Android-shell session, type
    `exit` → the tab stays, gains the `(exited)` label, and the terminal
    shows `[Process completed - press Enter]`.
3.  Non-zero exit: in a fresh Android-shell session, type `exit 3` →
    the terminal shows `[Process completed (code 3) - press Enter]`
    (the same waitpid delivery the engine now records as
    `Exited(3)` internally).
4.  Signal path (tab close during activity): start `sleep 300` in a
    session, close that tab → the entry disappears, no crash, every
    other session keeps working.
5.  FGS regression: with ≥ 1 session the retention notification stays in
    the shade; after closing/finishing every tab the service stops and
    the notification clears (P2 did not touch the FGS policy).
6.  Multi-session hygiene: spawn three sessions, close the middle one —
    the remaining two are unaffected; new sessions keep appending (ids
    never reused).
7.  Process death: kill the app from recents with sessions open →
    relaunch starts clean (no restoration claims), the shade shows no
    stale app notifications (the P1 sweep is the only notification
    cleanup and P2 posts nothing new).
8.  Honesty check: nothing in the UI or the shade claims an agent or
    command is running/completed/waiting; no notification beyond the P1
    FGS notification ever appears.
9.  Regression sweep: the §47 keyboard checks (with/without a keyboard)
    and the §48 terminal-basics steps still pass; terminal output,
    typing, paste and background retention behave exactly as the
    `-m72p1` build.
10. Parity statement: compared side-by-side with the m72p1 build, no
    user-visible difference should exist — P2 is the engine underneath.
    Any visible difference is a defect to report, not a feature.

## 50. M7.2 P3a — trusted agent signal audit & detection design (JVM-verified only; NO device gate)

Phase: internal metadata plumbing on top of the P2 lifecycle engine —
`LaunchIdentity` (the sealed KnownAgent / KnownNonAgentTool /
CustomOrUnknown classification resolved against the REAL registry/catalog
objects) plus the derived `AgentActivityRepository.classifiedLaunches`
projection. The full audit, the detection-classification matrix over every
registry/catalog/custom launcher, and the three-statement truth rule
(launched ≠ running ≠ completed) are `docs/M7.2-P3A-DETECTION-MATRIX.md`.

What P3a is NOT: no user-visible change of any kind, no notification, no
UI, no /proc, no polling, no output parsing, no OSC 133, no custom tool
promoted by its name, no spawn-site change, no persistence, no version
bump (the stamp stays 47 / 0.11.2-m7.1.1).

Verified WITHOUT a device (this build's honest floor):

-   Full JVM suite forced rerun: **833/833** on the historical debug
    basis (app 688 = 665 P2-era + 23 new P3a, terminal-emulator 145,
    0 failures / 0 errors / 0 skipped) — `LaunchIdentityTest` (14 pure:
    the full matrix incl. never-promotion of custom tools and
    phase-independence of the classification) and
    `LaunchIdentityIntegrationTest` (9 structural: the manager's single
    authority untouched and registry-free, the classifier exhaustive over
    the sealed origins, no agent running/completion API anywhere in the
    main sources, the P1/P2 boundaries carried through). The release
    unit-test variants ran the same 833 equally green.
-   APK assembled at the tip and audited: 30,802,996 B, sha256
    598829a8…f9ebd, versionCode 47 / versionName 0.11.2-m7.1.1 (inherited
    stamp), the UNCHANGED 6-permission merged set, 26 launcher icon
    assets, dex carries the new `LaunchIdentity` symbols beside the P2
    set, cert d96a6f66…8bf659 unchanged.

Device gate: **none required for P3a** — the phase intentionally creates
no user-visible feature to exercise. The standing hardware gates remain
§47 (M7.1.1), §48 (P1) and §49 (P2 parity) on a real Android 13+ device;
the next M7.2 device-facing work arrives with P3b (the procfs scanner),
whose device pass will be defined when P3b is mandated.

## 51. Manual acceptance — M7.2 P3b (runtime agent detection via /proc) — DEVICE GATE PENDING

What the JVM suite already proves (forced rerun at the P3b tip,
1764/1764, 0 skipped): the pure matching/correlation/state matrix —
including the mandated negatives (no substring matching; an unrelated
same-name process never produces RUNNING; UNKNOWN is never upgraded) —
and the structural boundaries (no notification APIs, no completion
vocabulary, no output heuristics, /proc confined to the reader seam, the
detector owns no lifecycle authority, the parking polling discipline).
The experiments (scripts/procfs_experiments_p3b.sh, E1–E5) proved the
kernel procfs contracts on a Linux sandbox.

What ONLY a real device can answer — and therefore what this gate
exercises:

- the REAL /proc shape of each registry agent on-device (which grade
  fires: PROCFS_EXE for single-file/bun-compiled binaries, PROCFS_CMDLINE
  for interpreter-hosted CLIs, or neither — an honest UNKNOWN);
- hidepid=2 listing behavior from the app's UID (foreign processes
  invisible) and SELinux treatment of `exe` readlink for the app's own
  processes;
- the end-to-end scan while a real agent actually runs inside the guest
  (proot + chain + agent), including the agent-exit transition to
  NOT_RUNNING and the relaunch transition back to RUNNING.

There is NO production UI for agent activity in P3b (by design — the
phase mandate forbids it). The observable channel is the existing log:
the detector logs every state transition at Log.d under the tag
`AgentRuntimeDetector` (`session N: X -> Y (pids=[...], grade=...)`).

Steps (requires: real Android 13+ device, the M7.2 P3b debug APK
installed, an installed guest runtime with at least one registry agent
available — e.g. Kilo Code or Claude Code; adb logcat for observation):

1.  Install the P3b debug APK; confirm normal operation (§47/§48/§49
    regressions are NOT re-run here — this gate adds only P3b behavior).
2.  Start `adb logcat -s AgentRuntimeDetector:V TerminalSessionManager:V
    PocketShell:V`.
3.  Launch a KNOWN AGENT from Home (e.g. Kilo Code) and let it reach its
    interactive UI.
4.  EXPECT (within ~2–4s): a log line `session N: (none) -> UNKNOWN`
    (pre-fork/startup) followed by `session N: UNKNOWN -> RUNNING
    (pids=[...], grade=PROCFS_EXE|PROCFS_CMDLINE)`. Record WHICH grade
    fired for this agent (the per-agent shape table this gate exists to
    build).
5.  Verify the pids are real agent processes: with the agent still
    running, run `ps -A | grep <pid>` INSIDE the guest session (same
    view, hidepid-filtered) and cross-check one cmdline
    (`cat /proc/<pid>/cmdline | tr '\0' ' '`) — the argv must contain the
    agent's token as an exact element (or the exe basename must be the
    token for PROCFS_EXE).
6.  Negative control A (correlation): launch a SECOND instance of the
    same agent in a second session. EXPECT each session to report its OWN
    pids only. Close the second session (tab close) — EXPECT the first
    session's observation unchanged (its state never references the
    other tree).
7.  Negative control B (non-agents): launch nano/vim/htop (catalog
    non-agent tools) and a custom tool. EXPECT NO agent RUNNING claims —
    these sessions are ineligible (no `AgentRuntimeDetector` transitions
    for them).
8.  Exit the agent (quit to the shell prompt). EXPECT (within ~2–4s):
    `session N: RUNNING -> NOT_RUNNING` — and NOTHING calling it
    completed/success/finished (the vocabulary has no such state).
9.  Relaunch the agent at the prompt (type `kilo` / `claude` manually).
    EXPECT `NOT_RUNNING -> RUNNING` again (manual relaunch inside the
    session is still real, correlated process evidence).
10. Park check: close the agent session's tab. EXPECT the observation
    pruning within one eligibility change and NO further scan activity
    (no new `AgentRuntimeDetector` lines with the agent sessions gone).
11. Scan-failure honesty (optional, if achievable without harmful
    settings): if procfs can be made unreadable for the app (e.g. a
    vendor SELinux policy), EXPECT UNKNOWN (never NOT_RUNNING) while the
    scan fails — record the exact behavior as a device finding.
12. Honesty check: at every step, the ONLY user-visible surface is the
    terminal itself. There must be NO notification, NO agent-status UI,
    NO completion claim anywhere (P3b boundary).

Pass criteria: steps 4–10 show the exact transitions above with no false
RUNNING claims (including both negative controls) and no completion
vocabulary anywhere; the recorded per-agent grade table becomes the
authoritative shape reference for future phases (extend
docs/M7.2-P3B-RUNTIME-DETECTION.md §4 with the results).

If a registry agent reports UNKNOWN persistently on device: that is a
VALID outcome (Success B — the agent's true shape matches neither graded
rule); record its real /proc shape and stop — do not add heuristics.

## 52. M7.2 P3c (runtime transition & event engine) — NO DEVICE GATE REQUIRED

Honest visibility statement: P3c adds NO user-visible behavior — no UI, no
notification, no state the phone's owner could see or act on. The event
engine is internal infrastructure (the deduplicated transition stream the
future notification phase will consume). Per the project rule — internal
phases the user cannot meaningfully see always deliver a NEW GIT BUNDLE,
never a mandatory APK install — the delivery for this phase is the bundle
cut at the P3c record tip; the APK was still built and audited at the
inherited stamp (versionCode 47 / 0.11.2-m7.1.1, the unchanged
6-permission set, cert d96a6f66…8bf659, all five P3c symbol groups in the
dex) purely as the regression gate.

What WAS verified without a device (forced JVM rerun at the P3c tip):
1850/1850 green, 0 skipped — including 29 pure transition-matrix tests
(every mandated transition, the dedup storms, the restart story, unknown
safety, finish/remove/stale-observation rejection, the never-completion
guards, non-agent silence, multi-session isolation) and 14 structural
source pins (no notification APIs, no /proc access, observer-only, no own
timers, replay-free stream).

Optional device observability (only if the phone is already attached for
the §51 pass): the engine's transitions appear in the SAME logcat stream —
`adb logcat -s AgentRuntimeEvents` alongside `-s AgentRuntimeDetector`.
Expected shapes during a §51-style controlled launch of a supported agent:

1.  Launch the agent from its Home launcher. EXPECT one
    `session N LAUNCHED agent=<name>` line (the spawn fact — emitted even
    before the first scan).
2.  When the agent's process appears: EXPECT
    `session N CONFIRMED_RUNNING ... (pids=[...], grade=...)` exactly once
    per entry into RUNNING (no repeats while it stays running).
3.  Exit the agent. EXPECT `session N NO_LONGER_DETECTED ... —
    disappearance is not completion` exactly once.
4.  Kill the session's tab while running. EXPECT
    `session N SESSION_ENDED ... cause=SESSION_REMOVED` and NO further
    event for that session — a stale detector result must not fabricate
    one.
5.  Let the session exit naturally (exit the guest). EXPECT
    `cause=SESSION_FINISHED` carrying the session's direct-child exit
    status — which is NOT the agent's completion, and must never be
    described as one.

Absence is the pass criterion too: plain shells, catalog tools (nano,
htop) and custom tools produce ZERO `AgentRuntimeEvents` lines; a RUNNING
agent produces NO further lines between transitions (dedup); nothing ever
names an agent completed/success/finished.

If no device is attached: nothing is owed — §51 remains the phase that
owns the on-device /proc shape verification; P3c rides its stream.
