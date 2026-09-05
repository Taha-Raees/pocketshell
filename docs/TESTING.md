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
- [ ] The other seeded agents (gemini/codex/aider/qwen) do NOT appear unless
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
