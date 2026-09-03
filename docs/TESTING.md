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

---

## 11. Manual acceptance — UI redesign (v0.7.0-ui) — DEVICE GATE PENDING

Everything below rides on top of an already-gated runtime: M2.6 Gates A–H
(§10) must still pass after the update — the UI phase did not touch the
runtime stack, but the update-in-place is exactly where regressions hide.

### 11.1 Install
- [ ] Install v0.7.0-ui OVER v0.6.2 (no uninstall) — update succeeds
      (same signing cert), runtime stays installed (Home shows "Linux ready").

### 11.2 Navigation & Home
- [ ] Home: brand block + 2×2 grid (Terminal hero, Linux Shell, Apps,
      Packages) — NO CLI-utility cards (git/python/nano must NOT appear).
- [ ] Hamburger (top-left, two unequal lines) opens the drawer on every
      screen; destinations: Home, Terminal, Apps, Packages, Diagnostics,
      Settings, Linux Shell; footer shows the runtime state.
- [ ] Back button: closes the drawer if open; otherwise returns Home.
- [ ] Drawer "Linux Shell" spawns the guest (or refuses to Home with the
      honest banner when not READY — with runtime READY it enters Alpine).

### 11.3 Terminal & keyboard (FINAL keyboard spec)
- [ ] Top accessory row reads exactly: Esc · Tab · (gap) · ← ↑ ↓ →.
- [ ] Bottom accessory row reads exactly: [⌨ icon] · Ctrl · Alt · Space ·
      Shift · ↵. The toggle is an ICON only — no "Keyboard"/"ON/OFF" text.
- [ ] Tapping the ⌨ icon hides the Android keyboard; tapping it again (or
      tapping the terminal) brings it back. Both accessory rows stay visible
      above the Android keyboard while it is open.
- [ ] Esc, Tab, arrows, Space, Enter dispatch into the shell (verify in the
      guest: `ls` + Tab completion + arrow history + Enter).
- [ ] Ctrl works (Ctrl+C interrupts a running command; Ctrl+D ends a
      session); Alt works (Alt+b word-left in the shell); Shift works
      (Shift+letter types uppercase; the Android keyboard also has Shift).
- [ ] Long-press ← / → inserts Home / End (cursor jumps to prompt / line
      end); long-press ↑ / ↓ inserts PgUp / PgDn (scrolls alt buffers e.g.
      in `less`).
- [ ] Long-press Esc opens the F1–F12 strip above the bottom row; F-keys
      work (e.g. htop: F1 help, F10 quit — or digits 1–0 from the Android
      keyboard); the strip dismisses after a tap.
- [ ] Ctrl/Alt/Shift tap cycles: tap = one-shot (tinted cap), tap-tap =
      locked (filled cap + lock dot), tap again = off. NO textual ON/OFF.
- [ ] Session tabs: open 2 sessions + switch; close via the × on the
      selected tab (confirm dialog appears — it kills a real process).
- [ ] Pinch resizes the terminal font; scrollback still holds (existing
      behavior, regression check).

### 11.4 Apps (launchable detection — honesty rules)
- [ ] If hermes is installed in the guest (it is, from the M2.6 episode):
      Apps lists "Hermes"; tapping it spawns a dedicated guest session
      running hermes; exiting returns to the guest shell prompt.
- [ ] If opencode is NOT installed: it must NOT appear (no dead tiles).
- [ ] After installing/removing an app in the guest, returning to Apps
      re-detects (probe runs on screen visibility).
- [ ] With the runtime NOT READY, Apps shows the honest "not ready" banner —
      never an empty pretense.

### 11.5 Packages (regression — same behavior, new skin)
- [ ] Search "node" → nodejs ranks first (M2.5 rule); Install works;
      the row flips to "Installed · version".
- [ ] Featured cards: Nano install/uninstall/Open still verify against the
      real apk database; "Working…" appears ONLY on the targeted card.
- [ ] A failed fetch (airplane mode) shows the real apk stderr in the
      banner + Retry (honest failure surface preserved).

### 11.6 Settings & AI configuration
- [ ] Appearance: theme switches apply immediately (Light/Dark/AMOLED/
      System); dynamic color toggle on Android 12+; font-size slider persists.
- [ ] AI Assistant: enter an OpenRouter key → Save → field clears, a masked
      reminder shows ("••••••••abcd"); "Show" reveals ONLY the draft being
      typed, never the stored key; "Remove key" clears it. Model id is
      free-text (no fixed list). The section states the assistant chat is
      not in the app yet.
- [ ] Diagnostics must NOT show the key (it never renders secrets).
- [ ] Home FAB (bottom-right) opens this same Settings section.

### 11.7 Visual/quality sweep
- [ ] One product feel: Home, Terminal, Apps, Packages, Settings,
      Diagnostics share the same surfaces/typography/radii.
- [ ] Terminal canvas is dark ink in BOTH light and dark app themes.
- [ ] Dark/light/AMOLED all render legibly (contrast, no dark-on-dark).
- [ ] Icon-only controls announce themselves (TalkBack: "Open menu",
      "New session", "More options", "Show/Hide Android keyboard",
      "AI Assistant settings").
- [ ] Foldable/tablet width: grid spreads, keyboard caps scale, nothing
      stretches into absurdity.

### Regression guards (MUST stay green — brief §18)
- [ ] Gates A–H of §10 still pass on this build (uname banner, overlays,
      apk lifecycle, gcc/g++/ld --version after apk fix).
- [ ] hermes still runs from the Linux Shell (`hermes --help`).
- [ ] Android shell + Linux shell + package operations + Diagnostics
      behave exactly as v0.6.2.
