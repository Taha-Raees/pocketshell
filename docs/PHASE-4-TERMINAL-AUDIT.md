# PHASE 4 — Terminal / PTY Excellence: Audit-First Investigation Report

Agent Z · 2026-09-14 · branch `agent-Z/phase4-terminal-audit` (from `main` @ `fe45250`, M7.3)

Scope: audit-only phase. **No optimization was implemented** — per the brief, this
report establishes the real architecture, the measured baseline, bottleneck root
causes, and the Phase 5 contract. All device numbers are from the live Samsung
SM-F711B (Android 15 / SDK 35, kernel 5.4.274-qgki, 7.4 GB RAM) running the M7.3
guest (Alpine 3.24.1 musl under the app's proot). Raw outputs: `benchmarks/phase4-terminal/results/`.
Tooling: `benchmarks/phase4-terminal/` (isolated, not part of any build).

Verification levels used below, per repo honesty rules:
**[STATIC]** code reading · **[JVM]** not used here · **[GUEST]** measured inside the
live guest on the device · **[DEVICE-UI]** requires adb — **unavailable this session**
(Wireless debugging toggled off; pairing requires the owner). Nothing in this report
claims DEVICE-UI verification.

---

## A. Current architecture

```
Compose UI (TerminalScreen tabs + TerminalView in AndroidView)
  → TerminalViewModel (per-Activity; holds only selection/UI state)
  → TerminalSessionManager (process-scoped SINGLETON object — the ONE owner)
      SessionEntry { id, TerminalSession, label, title, SessionLifecycleState,
                     SpawnOrigin, AgentHint, shellPid, launchRecordPath }
  → com.termux.terminal.TerminalSession (vendored, unmodified)
      mProcessToTerminalIOQueue (64 KiB) / mTerminalToProcessIOQueue (4 KiB)
      3 threads per session: TermSessionInputReader, TermSessionOutputWriter,
      TermSessionWaiter; MainThreadHandler feeds TerminalEmulator on main thread
  → JNI.createSubprocess (terminal-emulator/src/main/jni/termux.c:25-115)
      open("/dev/ptmx") → grantpt/unlockpt/ptsname_r → IUTF8, IXON/IXOFF off
      → fork → child: sigprocmask(unblock all), setsid(), open(pts) [= ctty],
        dup2 0/1/2, close other fds, clearenv+putenv, chdir, execvp
  → execvp = libproot.so (termux proot pin 7266fb3e, bionic build)
      argv: proot --kill-on-exit --link2symlink --rootfs=<no_backup>/runtime/rootfs
            --root-id --cwd=/root --bind=/dev --bind=/proc [sysdata overlays]
            --bind=/sys --bind=<apk-cache>:/etc/apk/cache --bind=<apk-cache>:/var/cache/apk
            /bin/sh -l [-c "cd -- '<dir>' && exec /bin/sh -l"]
      env: LD_LIBRARY_PATH=<nativeLibraryDir>, PROOT_LOADER=…, PROOT_TMP_DIR=…,
           HOME=/root, PATH=…, TERM=xterm-256color, LANG=C.UTF-8, TMPDIR=/tmp
      (RuntimeProcessLauncher.buildLaunchSpec, RuntimeProcessLauncher.kt:284-404)
  → proot ptraces Alpine busybox ash → the whole guest process tree
```

Load-bearing facts (all [STATIC], file:line):

1. **The PTY child is proot, not the shell.** `mShellPid` is proot's PID
   (TerminalSession.java:127-129; SessionLifecycle.kt:43 documents "for guest
   sessions that is proot"). The shell is a ptrace-tracee one level down.
2. **Fork is LAZY**: `TerminalSession` spawns nothing until the first
   `updateSize` (TerminalSession.java:103-110, initializeEmulator at 123).
   A session created but never laid out has **no process at all** (phase
   STARTING, TerminalSessionManager.kt:314-318).
3. **Ownership is process-scoped and in-memory only, by design**:
   TerminalSessionManager is a Kotlin `object` (TerminalSessionManager.kt:44);
   its header states "process death loses everything honestly
   (START_NOT_STICKY, no restoration)" (lines 38-42). Nothing about a session
   (id, cwd, env, scrollback) is persisted. IDs are a process-lifetime
   counter (`nextId`, line 126).
4. **Retention**: `TerminalService` is a foreground service
   (FOREGROUND_SERVICE_TYPE_SPECIAL_USE on API 34+, TerminalService.kt:58-66)
   started while ≥1 session exists and stopped otherwise (syncWithSessionState,
   lines 87-96). START_NOT_STICKY. **No WakeLock anywhere in the app** (grep:
   only a comment in RuntimeAgentDetector.kt:56). An FGS process is not
   "cached", so cached-app freezing should not apply while it runs —
   [STATIC] inference, not device-verified.
5. **Two launch profiles on one builder**: INTERACTIVE_TERMINAL (always binds
   real /proc + sysdata overlays + apk cache binds) and PACKAGE_OPERATION
   (never /proc, SELinux-safe apk commit) — RuntimeProcessLauncher.kt:127-149;
   interactive specs are fail-loud validated (procContractProblem, 260-273).
6. **One-shot commands bypass the PTY entirely**: GuestCommandRunner uses
   `ProcessBuilder` fork/exec of proot with pipes
   (GuestCommandRunner.kt:48-67) — every package operation pays a fresh
   proot boot.
7. **The interactive guest chain is a double shell**: plain sessions spawn
   `/bin/sh -l` (TerminalViewModel.kt:154); "Open Terminal Here" and command
   apps spawn `/bin/sh -l -c "cd -- '<dir>' && exec /bin/sh -l"`
   (TerminalViewModel.kt:188-196, live ps confirms). Two login-shell
   startups per launch; the outer execs away (no extra resident process).

## B. Current session lifecycle

| Event | What actually happens |
|---|---|
| Session created | entry appended in phase STARTING; **no process yet** (lazy fork) |
| First `updateSize` (view layout) | fork/exec proot on the calling (main) thread via JNI; fork callback → RUNNING (markStarted, TerminalSessionManager.kt:405) |
| App backgrounded | FGS keeps the process alive; sessions keep running ([STATIC] + prior device gates in docs/TESTING.md §47-§58) |
| Activity destroyed/recreated | nothing happens to sessions — ViewModel holds no process state; the view re-attaches to the manager's list (ARCHITECTURE.md §5) |
| User closes tab | `closeSession`: if `pid > 0` → `finishIfRunning()` = **SIGKILL to proot only** (TerminalSession.java:235-243, guarded at TerminalSessionManager.kt:364); entry removed immediately; launch-record file deleted; master closed later by the waiter thread → `cleanupResources` (TerminalSession.java:246-256) |
| Shell/guest exits by itself | TermSessionWaiter's `waitpid` → MSG_PROCESS_EXITED → master closed, "[Process completed …]" appended, FINISHED recorded with structured ExitStatus (TerminalSession.java:349-368) |
| Android kills the app process (LMK/swipe) | PTY master dies with the process; FGS is START_NOT_STICKY; **no restore of any kind**. Guest processes: proot has no PR_SET_PDEATHSIG (termux.c has no prctl) and is reparented — [STATIC] inference: proot and (detached parts of) the guest can outlive the app; not live-verified (would kill my own session; no adb) |
| Process-group cleanup | **There is none.** No kill(-pid), no SIGHUP escalation, no job sweep — cleanup relies entirely on proot's own teardown + master-close SIGHUP to the terminal's foreground pgrp |

## C. PTY/process model

- PTY: one `/dev/ptmx` master per session; slave opened after `setsid()` so
  it becomes the controlling terminal (termux.c:77-85). termios: IUTF8 set,
  IXON/IXOFF cleared (Ctrl+S safe), default ISIG/ICANON/ECHO otherwise
  (termux.c:54-59). Initial winsize set from the view (rows/cols + cell px).
- Signals: Ctrl-C/Ctrl-Z ride the line discipline (ISIG) → kernel delivers
  SIGINT/SIGTSTP to the terminal's foreground process group. Device-verified
  below. Session close does NOT use signals down the tree — only SIGKILL to
  proot.
- Reaping: exactly one `waitpid(pid, &status, 0)` per session on a dedicated
  thread (termux.c:201-213, TerminalSession.java:166-172). No SIGCHLD
  handler; no global reaper; zombies impossible for the direct child, but
  **grandchildren are never reaped by anything in the app** (they reparent
  to Android init when their parents die).
- I/O model: reader thread → 64 KiB ByteQueue → main-thread handler →
  TerminalEmulator; writer: main thread → 4 KiB ByteQueue → writer thread →
  master write. Backpressure: queue writes block (upstream ByteQueue), so a
  flooding guest throttles at 64 KiB of unread output; a flooding paste
  throttles at 4 KiB of unread input.

## D. Benchmark methodology

Full text in `benchmarks/phase4-terminal/README.md`. Key points:

- Harness: `ph4bench.c` (one C binary; exec/fs/pty-latency/pty-throughput/
  session-spawn/sigtest subcommands), compiled on-device with the guest gcc.
  fork/execvp/waitpid mirrors termux.c exactly for spawn measurements.
- **Layer accounting**: every in-guest measurement already includes one proot
  layer. `nested` mode re-runs the battery one proot layer deeper; the delta
  attributes per-layer cost. Per-layer cost is **superlinear with depth**
  (a layer-2 tracee is itself traced), so the delta overstates the app's
  single-layer tax; layer-1 numbers are honest upper bounds for app-path
  cost. Bare metal (layer 0) was unreachable (no adb).
- Warmups before every timed loop; CLOCK_MONOTONIC; medians/p95 reported.
- CPU attribution: getrusage(RUSAGE_CHILDREN) delta — includes the proot
  tracer's CPU of reaped children automatically.
- Known harness constraints discovered on device (documented in README):
  sleeping across an INTR window desyncs the outer proot tracer (SIGTRAP
  into the resident loader breakpoint); dd miscounts short tty reads
  (input direction uses `head -c`); busybox ash restores startup termios
  after job control (fresh shell per correctness case).

## E. Real-device benchmark results

Raw: `results/layer1.txt` (app-current depth), `results/nested.txt`
(one extra proot layer), `results/sigtest.txt`, plus probes quoted below.
System was under real load (my own agent session + 6 MCP servers), so treat
absolute values as realistic-worst-case, and deltas as the signal.

**Exec/process-creation (median wall ms per exec, subtree CPU per exec):**

| Target | layer 1 (app depth) | layer 2 (one more proot) | marginal cost of one proot layer |
|---|---|---|---|
| static hello (tiny ELF) | 1.0–3.2 | 3.2 | +2.2 |
| dynamic hello (musl ld) | 1.6–4.0 | 4.0 | +2.4 |
| `/bin/true` (busybox ~1 MB) | 4.9–5.6 (CPU 2.6–2.8) | 10.2 (CPU 4.7) | +5.3 |

- **The documented "~25 ms/process tax" (docs/runtime/DEV_WORKSTATION.md:86)
  is STALE.** A trivial exec today is ~1–5 ms; the heaviest common case
  measured (busybox dispatch) ~5 ms. The 25 ms figure does not reproduce for
  any target measured; likely historical (older build/cold cache/heavier
  binary). The doc row should be corrected, not propagated.
- Exec cost scales with the binary's mapped size (page-in under ptrace), not
  with proot constants: static 1 ms vs busybox 5 ms.

**Interactive feel (layer 1):**

| Path | median |
|---|---|
| PTY raw byte echo RTT (cat, raw mode) | 0.11–0.15 ms |
| login-shell echo RTT (builtin) | 0.45–0.50 ms |
| **login-shell external-command RTT** (`/bin/true` per trip) | **9.6–10.9 ms** |
| stat() | 0.15–0.20 ms |

The dominant interactive cost is **fork+exec of external commands in the
shell** (~10 ms per command), which is the exec row above plus shell/PTY
plumbing — not typing latency, which is sub-millisecond.

**Session spawn (fork→first output, i.e., "new tab until prompt"):**

| Chain | median |
|---|---|
| `sh -l -c 'echo READY'` at layer 1 (no proot; profile included) | ~43 ms (light load) – ~52 ms |
| proot + `sh -l -c 'echo READY'` at layer 2 (app-shaped, one extra layer) | 117.8 ms (n=7) |
| proot + `sh -c 'echo READY'` (no login profile), 4 in parallel | 50–69 ms each |
| interactive `sh -l` first output at layer 1 (cold first spawn) | ~115 ms single-shot |

App-shaped new-tab cost to first prompt is therefore **~100–120 ms**, of
which ~40–60 ms is guest shell profile sourcing and most of the rest is
proot boot (loader exec, ptrace attach, initial execs). App-side additions
(not measurable without adb): `prepareLinuxSession` (marker fast path when
the glibc layer is present — PackageGateway.prepareGuestForSession) plus
Compose first-layout, both on top.

**PTY throughput (32 MiB, layer 1):**

| Direction | rate |
|---|---|
| guest → tty → master (output) | 50–51 MiB/s |
| master → tty → guest (input, `head -c`) | 9.2–11.4 MiB/s |

Input is ~5× slower (N_TTY input-buffer flow control + per-chunk wakeups).
App-side the input queue is only 4 KiB (TerminalSession.java:49) — paste
throughput is bounded by that first. Output 50 MiB/s far exceeds what the
emulator/render path consumes.

**Concurrency (layer 1):** 4 simultaneous app-shaped spawns: 50–69 ms each
(no serialization collapse). Exec battery with 3 idle sessions present:
4.9→6.3 ms median (~+25%). Multiple concurrent sessions scale acceptably.

**Memory (idle, per session):** app's proot (bionic) RSS 2.8 MB (live tree);
Alpine proot (musl) 0.8 MB; busybox ash ~1.5 MB; most of a long-lived
session was swapped out under memory pressure — static per-session cost is
a few MB; the real memory pressure comes from workloads (node/JVM), not the
terminal layer.

## F. Bottleneck ranking by measured user impact

1. **~10 ms per external command in interactive shells** (exec + proot
   ptrace + busybox dispatch). Multiplied by agent/compiler workloads that
   spawn thousands of processes (gradle, npm, cargo), this is THE tax.
2. **New-tab latency ~100–120 ms** (proot boot + double login-shell chain +
   profile sourcing). Perceptible but minor.
3. **No cleanup path for detached guest processes on session close**
   (correctness, not latency — but user-visible: orphaned servers/builds
   survive and are undiscoverable in the UI).
4. **PTY input throughput 9–11 MiB/s** and the 4 KiB app input queue — only
   matters for large pastes.
5. **Per-op proot path translation ~0.2 ms/stat** — irrelevant alone;
   measurable in filesystem-heavy workloads (build systems), dominated by #1.
6. **Rendering/emulator path** — not measured (no adb); code analysis shows
   the standard termux design (64 KiB queue, main-thread emulator append,
   invalidate-driven rendering) with no app-specific pathology found.

## G. Root cause per major bottleneck

1. **Exec tax**: every guest syscall is intercepted twice (entry+exit
   ptrace-stops) by its session's proot tracer; exec additionally pays
   path translation of binary + interpreter and page-faults under ptrace.
   Evidence: layer-2 delta +5.3 ms on busybox; subtree CPU 2.6–2.8 ms per
   exec at layer 1 (the tracer burns most of it); cost tracks binary size.
   This is inherent to the ptrace architecture of proot, NOT a
   misconfiguration.
2. **New-tab latency**: proot boot (loader re-exec + attach) ≈ tens of ms
   plus the DOUBLE login chain (`sh -l -c … && exec sh -l` sources the
   profile twice) plus profile content. App-side `prepareLinuxSession` adds
   I/O on the fast path (marker checks; ~103-file extraction only on repair).
3. **Orphan escape**: closeSession's only kill is SIGKILL to proot
   (direct child). Detached (setsid'd/nohup'd) processes aren't in the
   terminal's foreground group, receive no SIGHUP at master close, and proot
   dies before killing them — device-CONFIRMED (results/sigtest.txt:
   "orphan hazard … grandchild alive=y state=S", reproduced twice + the
   tracer-kill probe).
4. **Input throughput**: kernel N_TTY input buffer flow control (inherent)
   + 2 proot-intercepted syscalls per chunk; app-side 4 KiB queue upstream.
5. **Path translation**: proot rewrites path arguments on every
   path-taking syscall (0.15–0.2 ms/stat measured).

## H. Architectural debt vs acceptable overhead

**Debt (should change):**
- Orphan cleanup on session close (kill process group / escalate; see §J).
- The stale 25 ms doc row (DEV_WORKSTATION.md:86) — replace with measured data.
- Double login-shell chain on "Open Terminal Here"/command apps (two profile
  sourcings; a single `sh -l` with a precmd-style cd, or exec before profile,
  saves ~tens of ms and one exec chain).
- No PDEATHSIG / no owner for proot when the app dies (headless-guest
  ambiguity; Phase 5 must define this deliberately — see §K).
- One-shot commands (packages) pay full proot boot per operation — a
  persistent package-executor channel would remove it (future, low priority).

**Acceptable overhead (do NOT chase):**
- Sub-millisecond typing/echo latency and 50 MiB/s output — the interactive
  path is excellent; leave it alone.
- ~5 ms busybox exec at the current depth — inherent to ptrace-based proot;
  alternatives (see §I) are large rewrites with their own risk.
- stat translation cost — same class.
- 64 KiB output queue — matches termux upstream; rendering isn't the
  bottleneck by construction.

## I. Alternative architectures considered

| Option | What it would fix | Cost/risk | Verdict |
|---|---|---|---|
| Tune current proot (args, version bump) | little — mechanism cost is ptrace itself | low | keep as-is; no evidence any flag moves the numbers |
| **PR_SET_PDEATHSIG in the forked child** (termux.c) | guarantees guest dies with app process | one-line C, well-understood; BUT it also forecloses deliberate headless survival (Phase 5 option) | defer to Phase 5 decision (§K) — do not add silently |
| Kill process group on close (kill(-pid)) | orphans | must be careful: the fg pgrp of the terminal is the shell's job; killing -proot-pgid also hits detached-same-group cases; needs setsid'd-server awareness | Phase 4/5 small change, evidence-backed (§J plan) |
| Native Android PTY without proot (Termux-style host shell) | removes the exec tax entirely | loses the entire Linux guest (the product's reason to exist) | rejected |
| bind-mount sandbox / chroot via a helper (no ptrace) | removes per-syscall tax | needs root or privileged helper; Android SELinux denies mount in app domain (documented in DEV_WORKSTATION §6: mount/chroot = session death) | rejected on this device, no root |
| Kernel facilities (namespaces + userns) | real isolation without ptrace | Android kernels: userns disabled/blocked for apps (5.4-qgki); same SELinux walls | rejected on this device |
| Different rootfs/libc (glibc-first, Debian) | not a latency lever (tax is proot, not musl) | large migration | not justified by any Phase 4 evidence |
| tmux/abduco for persistence | survives UI/app restarts | tmux 3.7c already in guest and verified; client still dies with app tty, server (detached, session-leader, no tty) can survive IF proot survives | strongest Phase 5 lever — see §K |
| Custom persistent supervisor (headless proot daemon owning PTYs, socket reattach) | full control of session identity/reattach | new native-ish component, Android lifecycle integration, security surface | Phase 5 alternative to tmux; more code, more control |

**Conclusion on proot**: the evidence shows the exec tax is genuinely caused
by ptrace-based syscall interception, which is proot's core mechanism on
non-root Android — every alternative that removes it requires privileges
this device does not grant (verified: mount/chroot/session-death row in
DEV_WORKSTATION.md:84, userns unavailable). "Proot limitation" is therefore
EVIDENCED, not assumed — for the syscall-tax class. The session/orphan
problems, by contrast, are PocketShell implementation gaps, not proot's.

## J. Recommended Phase 4 implementation plan (small, measurable, in order)

Nothing below has been implemented — this is the evidence-justified queue:

1. **Graceful close**: closeSession currently SIGKILLs proot. Change to:
   SIGHUP the terminal's foreground process group first (or close the master
   first and wait briefly), then SIGKILL the direct child, and record
   whether a process-group kill is needed for guests whose shells exit
   cleanly. Measure: orphan survival before/after with the sigtest battery
   (currently: grandchild survives; target: documented, intentional
   semantics instead of accident). Files: TerminalSessionManager.kt
   (closeSession), possibly TerminalSession.java (add a close-with-signal
   path). Gate: sigtest + docs/TESTING.md manual pass.
2. **Single-shell launch chain**: replace `sh -l -c "cd … && exec sh -l"`
   with a construct that sources the profile once (e.g. env PWD/cwd via
   proot --cwd into the guest path + plain `sh -l`, since proot already has
   --cwd; the guest cd is only needed for guest-path working dirs).
   Measure with `session` subcommand: expect −20–50 ms on affected launches.
3. **Doc correction**: update DEV_WORKSTATION.md:86 exec-tax row to the
   measured numbers (cite results/layer1.txt), note the superlinear
   layer-depth finding.
4. **Optional, only after 1–3 land**: expose "detached processes" awareness
   (list surviving guest processes for a closed session) — groundwork for
   Phase 5 stale-session detection, using the existing /proc contract
   (docs/PROCFS-CONTRACT.md).

Each step: small diff, before/after with the committed harness, real-device
gate per docs/TESTING.md.

**Implementation status (same branch, same session):**
- **J2 — IMPLEMENTED** (commit following this report): the guest directory of
  "Open Terminal Here" now rides proot's `--cwd` (`guestCwd` param through
  `RuntimeProcessLauncher.buildLaunchSpec`/`buildSessionSpec` →
  `TerminalSessionManager.spawnLinuxSession` → `TerminalViewModel.openLinuxShellAt`),
  and the launch is a bare `/bin/sh -l`. Measured on device before/after
  (layer 1, n=7 medians): double login chain **157.7 ms** → single login +
  `--cwd` **81.7 ms** (−76 ms, −48%). `RuntimeProcessLauncherTest` pins the
  new contract (`guestCwd rides the cwd flag verbatim`, blank → `/root`
  fallback) — the whole suite plus every pre-existing argv pin passes.
  `assembleDebug` builds green (app-debug.apk, 2026-09-14). **Device-verified
  2026-09-14**: the owner installed this build; the live process tree under
  the new APK shows the exact new spawn shape (`proot … --cwd=<dir> …
  /bin/sh -l`) for a Files "Open Terminal Here" session, functional end to
  end (the Phase 4 agent session itself ran inside it). Command-app and
  custom-tool launches keep the `sh -l -c` chains — they run real command
  lines and need the shell.
- **J3 — IMPLEMENTED**: DEV_WORKSTATION.md §6 exec-tax row replaced with the
  measured numbers and the phase-4 citation.
- **J1 — IMPLEMENTED** (commit `c6dfb48`, device-gated on SM-T870 — see
  §M-result J1 battery below). Final semantics:

  | Process class | Fate on normal tab close | Mechanism |
  |---|---|---|
  | Terminal-owned foreground work (the shell, or a foreground job) | terminated through normal terminal semantics | `TIOCGPGRP` on the session's own PTY master → `SIGCONT`+`SIGHUP` to exactly the kernel-tracked foreground process group (the pair the kernel itself delivers on master close) |
  | The direct child (proot) | exits naturally when its tracees are gone; otherwise force-finished once | the existing `finishIfRunning()` SIGKILL, fired by ONE bounded fallback (1000 ms, main handler) only if the same pid is still alive |
  | Deliberately detached descendants (`setsid`, nohup'd daemons) | NOT signaled by the close path — ever | the policy never enumerates or scans pids; multi-session isolation is structural (one session's own terminal state) |
  | Stubborn processes that ignore SIGHUP (`trap '' HUP`) | survive the graceful signal; the bounded SIGKILL fallback then force-finishes the direct child; tracer-SIGKILL cannot kill tracees (device-proven) | documented, intentional exception — a process that ignores SIGHUP opts out of graceful teardown and survives to its natural end |

  **Architecture verdict on detached survival (gate D amendment):** on the
  Flip's nested-proot experiments the app's proot pin IGNORES SIGHUP/SIGINT
  (`SigIgn 7ffffffc7fc0f053`), so signaling proot directly is impossible;
  and — verified on SM-T870 independently of any close code — typing plain
  `exit` in a session reaps ALL remaining tracees including `setsid`'d
  ones (`--kill-on-exit`, pinned in `RuntimeProcessLauncher` argv, reaps
  tracees on every orderly proot exit). Detached processes therefore
  survive only DISORDERLY paths (app process death / external SIGKILL —
  device-proven pre-J1 on this same device). Making detached processes
  survive orderly closes would require dropping `--kill-on-exit` from the
  spawn argv (leaks the whole guest on every crash — strictly worse, out
  of J1 scope) or the Phase 5b detached-runner architecture (report §K).
  Gate D records this honestly: the detached process was reaped by the
  pre-existing kill-on-exit policy, not by J1's signals.

  Source pins updated consciously to the J1 era: the pid-guard/kill(0)
  pin now pins the graceful-first shape; the "no timers" pins now allow
  exactly ONE manager timer (the graceful fallback via
  `mainHandler.postDelayed`), pinned to a single occurrence.

## K. Phase 5 (Persistent Sessions) — exact requirements

R1 **Session identity** must survive process death: stable UUIDs persisted
   (DataStore) with metadata (label, origin, cwd, created-at, agent hint);
   the current in-memory `nextId` counter is explicitly insufficient.
R2 **Process truth** must be distinguishable from UI truth: a persisted
   session is ALIVE only if its guest process tree is alive (pid +
   starttime identity, the kernel's pid-reuse-proof stamp — same mechanism
   as AgentLaunchRecords/M7.2-P9). UI may show "reattachable/dead" from that.
R3 **PTY ownership across UI loss**: sessions must survive (a) Activity
   destruction (already true), (b) app backgrounding (already true via FGS),
   (c) app process death — the new capability. The PTY master must be owned
   by something that outlives the app process, OR the session must be
   reattachable at a lower layer (multiplexer socket), OR death must be
   declared honest (current state).
R4 **Reconnecting UI**: on launch, enumerate live sessions from persisted
   metadata + process truth; reattach scrollback or present
   "session alive, scrollback not recoverable" honestly (the terminal
   buffer is app-process memory; across death only the guest side survives
   — unless the multiplexer owns scrollback, e.g. tmux).
R5 **Multiple sessions / long-running workloads** (agents, builds, servers):
   persistence must be per-session opt-in (not every tab) — a 2-minute shell
   and a 3-hour build have different value.
R6 **App restart / PocketShell process death**: defined behavior per session
   class — interactive shells may die (cheap to recreate); named/agent/build
   sessions must survive or the design must say why not.
R7 **Cleanup/recovery**: on restart, detect stale sessions (dead process +
   live metadata) and reap them; orphaned guest processes must become
   discoverable and killable (feeds R3's ownership decision).
R8 **Stale-session detection**: pid+starttime+runtime-generation checks, not
   bare pids.
R9 **Resource limits**: concurrent persisted-session cap (memory is the
   binding constraint on a 7.4 GB device — DEV_WORKSTATION §5), CPU policy
   left to Android, but phantom-process-killer exposure (Android 12+ kills
   app-child processes; DEV_WORKSTATION §8's optional mitigation becomes
   ARCHITECTURALLY RELEVANT once sessions are meant to outlive the app).
R10 **Security**: persisted reattachment must not let another app/entity
   attach (uds under app-private dir; no network listeners).

**Architecture comparison (evidence-based):**

- **A) Built directly into PocketShell** — the app keeps owning PTYs; across
  process death nothing survives. To survive death you'd need a resident
  process outside the app (a native daemon) — that's really option C.
  A alone = today's honesty, extended with persisted identity/metadata and
  graceful close (J1). Cheap, honest, does NOT deliver survival.
- **B) Delegate to tmux** — tmux server (session leader, detached, no tty)
  survives app death **if proot survives app death**, which is currently
  accidental (no PDEATHSIG) and unverified live. Reattach = spawn a fresh
  minimal session and `tmux attach`. Scrollback survives (tmux owns it).
  Zero new architecture code; tmux is already installed and device-verified
  (DEV_WORKSTATION §3). Cost: coupling session semantics to tmux; agent
  workloads must tolerate tmux (they're generic Linux workloads — fine).
- **C) Hybrid** — in-app layer does identity/metadata/reattach UI/stale
  detection (A), while survival is delegated to a detached guest-side
  runner. The runner can be tmux (fastest credible path) or, later, a
  PocketShell-owned supervisor if tmux semantics prove limiting. The
  proot-survives-app-death question must first be settled DELIBERATELY
  (PDEATHSIG or explicit headless mode) — today it is an accident either way.

**Recommendation: C (hybrid), staged.** Phase 5a = A (identity, graceful
close, stale detection, honest non-survival, orphan discoverability) +
decide proot's death semantics explicitly. Phase 5b = tmux-backed detached
sessions for named/long-lived workloads (opt-in per launch), with the
custom supervisor kept as a fallback if tmux limits identity/limits goals.
Do NOT build the custom supervisor first — it duplicates tmux's solved
problems without evidence tmux is insufficient.

## L. Risks / regressions to watch

- Killing process groups on close can kill WORK the user wanted to keep
  (that's why graceful-close-first ordering in J1 matters; default must
  stay conservative).
- PDEATHSIG (if added) changes app-death behavior for EVERYTHING running
  in the guest — including tmux-based persistence before it exists; sequence
  it with Phase 5b, not before.
- Vendored termux modules must stay unmodified except where upstream has
  the same change (keeps re-syncs mechanical — ARCHITECTURE.md §2).
- proot/loader desync observed from the guest side (SIGTRAP at
  loader+0x1bb4 when a traced process sleeps across an INTR window) is a
  harness-level observation; the app is the tracer and cannot hit that
  exact path, but it is a reminder that ptrace timing bugs exist in the
  pin — worth upstream tracking if any user report ever resembles it.
- Nested-guest interactive startup stalled in profile sourcing under Alpine
  proot 5.4 (harness environment; no app-path implication measured).

## M. Tests / device gates required before any Phase 4 change is "done"

### M-result. Device gates RUN (2026-09-14, cross-device adb loop)

Run by Agent Z from the Flip guest onto the **Galaxy Tab S7 (SM-T870)** over
wireless debugging (the Flip itself was already device-verified by the
owner's install). APK: `app-debug.apk` built from this branch
(`aa15cad`, 30,628,825 B, sha256 `827c8cf36731bac6…`).

| # | Gate | Result |
|---|---|---|
| 1 | Streamed install + in-place update, runtime data preserved ("Linux · Alpine · ready" on first launch) | PASS |
| 2 | Linux Shell: prompt; typed `uname -m` → `aarch64`; arithmetic `$(())` expands — full input→PTY→shell→output→render path | PASS |
| 3 | **J2 "Open Terminal Here"**: created `/root/ott-test`, Files → actions sheet → Open Terminal Here → prompt `localhost:~/ott-test#`, `pwd` = `/root/ott-test` (the new `--cwd` spawn shape, live-verified on a second device) | PASS |
| 4 | Multi-session: two Alpine Linux tabs coexist; exactly 2 `libproot.so` processes under the app UID | PASS |
| 5 | Close session: tab × closes cleanly; honest "No open sessions" empty state | PASS |
| 6 | **Orphan hazard in the real app**: `setsid sleep 300 &` (PPID 1) **survived** tab close — device-confirmed the report's §G3 finding; expired naturally after its 300 s (no long-term residue) | CONFIRMED (hazard, as predicted) |
| 7 | Background/foreground: Home → 6 s → return — both sessions intact, 2 proots alive before/during/after (FGS retention; no cached-app freeze) | PASS |

This ledger also retroactively covers the owner's on-phone install
(2026-09-14): the Flip's live process tree shows the identical new
`--cwd` spawn shape working end to end.

### M-result J1. Graceful-close battery (2026-09-15, SM-T870, J1 build)

APK: sha256 `7cb737c0bafba26d9c77a79f437a90e1853c0fd5788d556192f1029a1c6db3e4`
(commit `c6dfb48`); `getForegroundProcessGroup` symbol verified inside the
packaged `libtermux.so`. In-app process checks read the Tab's real process
table over adb.

| Gate | Scenario | Result |
|---|---|---|
| A | Idle shell open → close tab | PASS — 1 proot → 0; honest "No open sessions" state |
| B | Foreground `sleep 61` → close tab | PASS — fg sleep terminated through the graceful path (zero orphans); proot gone ≤ ~0.8–1.2 s (grace + fallback); UI clean |
| C | HUP-immune fg command (`trap "" HUP; sleep 91`) → close | PASS (documented exception) — SIGHUP ignored by the process's own trap; bounded SIGKILL fallback fired (proot → 0); the HUP-immune process and its shell survive to natural expiry, then exit |
| D | `setsid sleep 95 &` → close | AMENDED VERDICT (see §J1 architecture verdict) — the detached process was reaped by the pre-existing `--kill-on-exit` policy on proot's orderly exit; verified the same reaping occurs with a plain user `exit` (no close involved), proving it is architecture policy, not a J1 regression. Detached survival on disorderly paths was device-proven earlier on this device |
| E | Multi-session isolation: fg `sleep 120` in session 1, close session 2 | PASS — session 1's proot, fg job and PTY untouched and usable |
| F | Background 6 s → return → close one of two sessions | PASS — proots stable through backgrounding (no freeze), isolation preserved after return |
| G | Repeated open/close cycles + final audit | PASS — final state 0 proots, 0 zombies, 0 stray processes across the whole battery (~12 sessions); intermediate single-proot readings were open sessions from tap pacing, each matching a visible tab |

JVM side: 9 GracefulSessionCloseControllerTest contract pins (signals order,
unresolvable-group fallback, signaler failure, timeout→single SIGKILL,
cancelled-on-exit, id replacement, cross-session isolation, never-a-tree-
killer policy pin) + updated source pins; full suite 988 tests with only the
4 documented environmental `/proc` failures (identical to pristine-main
baseline; verified via clean worktree run at `fe45250`).

1. `benchmarks/phase4-terminal/run_bench.sh sigtest` — all 7 cases PASS,
   orphan semantics matching the intended design (currently documents the
   hazard; after J1 it must document the chosen policy).
2. Full battery re-run (layer1) — no interactive-latency regression
   (echo RTT stays sub-ms; ext-cmd RTT must not grow).
3. Launch-path measurement (`session`/`sessionfo`) before/after J2.
4. docs/TESTING.md manual device gates for terminal (§47–§58 scope): new
   tab, two sessions, background/return, close tab while a server runs,
   swipe-away warning behavior.
5. CI green (unit suites; known environment failures per
   docs/M7.3-INTEGRATION.md §4 excluded).
6. If PDEATHSIG/close semantics change: explicit device test for app-death
   behavior WITH a controlled recovery procedure (adb required — currently
   unavailable; gate blocked until Wireless debugging is re-enabled).

---

### Method notes / honesty ledger

- All [GUEST] numbers: live SM-F711B, loaded system (agent session + 6 MCP
  servers). Deltas across layers are the signal; absolute values are
  realistic-worst-case.
- DEVICE-UI claims: NONE possible this session (adb off; needs the owner to
  re-enable Wireless debugging for `pocketshell-adb connect`).
- Alpine proot 5.4.0 was installed (`apk add proot`) for the nested battery
  and LEFT INSTALLED (noted in benchmarks README; `apk del proot` reverts).
- Subagent delegation was attempted 3× (architect ×2, android ×1); all
  failed with model-request errors, so the entire investigation was done
  sequentially by the primary agent.
