# Phase 4 — Terminal/PTY audit benchmarks (Agent Z)

ISOLATED INVESTIGATION TOOLING — not part of the app build, not referenced by
any product code. Results captured on the development device (Samsung
SM-F711B, Android 15 / SDK 35, kernel 5.4.274-qgki, Alpine 3.24.1 guest under
the app's proot, main @ fe45250) in `results/`.

## What this measures

| File | Purpose |
|---|---|
| `ph4bench.c` | One C harness, six subcommands (below). Compiles inside the guest with `gcc -O2`. |
| `run_bench.sh` | Battery runner: `layer1`, `nested`, `appspawn`, `sigtest`, `sigtest-nested`. |
| `results/*.txt` | Raw captured output, labeled by mode. |

`ph4bench` subcommands:

- `exec <N> <argv...>` — fork/execvp/waitpid loop; per-exec wall latency
  (min/median/p95/max) plus **reaped-subtree CPU per exec**
  (getrusage(RUSAGE_CHILDREN) delta, so a nested proot tracer's CPU is
  included automatically).
- `fs <N> <path>` — stat() loop (proot path-translation cost).
- `ptylat <N> [argv...]` — PTY round trips: raw byte echo (cat), login-shell
  echo RTT, external-command RTT (fork+exec per trip), fork→first-output.
- `ptytp <MiB> [--rev]` — PTY throughput, one dd/head exec, 1 MiB blocks.
- `session [argv...]` / `sessionfo [argv...]` — replicates the app's exact
  spawn chain (termux.c create_subprocess: ptmx → fork → setsid → open slave
  → dup2 → exec) and reports fork→first-output / fork→exit.
- `sigtest [-- argv...]` — 7-case PTY correctness battery (fresh shell per
  case): Ctrl-C, Ctrl-Z, TIOCSWINSZ, UTF-8, 8-bit raw passthrough, EOF, and
  orphan survival of a setsid'd grandchild across session SIGKILL+master
  close. When `argv` is a proot chain, the same battery runs against a
  nested guest (tracer-death semantics).

## Methodology rules

1. **Layer accounting.** Every measurement in the guest already sits under
   the app's proot (layer 1). `nested` re-runs the whole battery one proot
   layer deeper (Alpine `proot` 5.4.0, musl — same ptrace mechanism as the
   app's termux pin 5.1.107.92, different build; noted per run). The marginal
   cost of one extra layer is `T(layer2) − T(layer1)`. Because a layer-2
   tracee is itself traced by layer 1, the per-layer cost is **superlinear
   with depth** — the delta OVERSTATES the app's single-layer tax. True
   bare-metal (layer 0) numbers are not reachable from inside the guest
   (no adb this session); treat layer-1 numbers as upper bounds.
2. **Warmups.** exec: 10 warmup runs before the timed loop. fs: 100. PTY
   round trips: the first shell spawn is reported separately (cold), round
   trips are warm.
3. **Harness constraints discovered on device** (both documented in the
   report):
   - Sleeping (nanosleep or ppoll-timeout) in the harness process across an
     INTR write desyncs the outer proot tracer → the process resumes into
     the resident proot loader breakpoint and dies with SIGTRAP
     (loader+0x1bb4). sigtest therefore keeps a tight read loop across INTR
     windows. This is a harness-level observation; the app owns the PTY
     master and is the TRACER, not a tracee, so the app path cannot hit this
     exact desync.
   - Busybox ash restores its startup termios when it regains the foreground
     after a job dies — so every sigtest case gets a FRESH shell (no shared
     termios state).
4. **Throughput direction bug fixed**: dd counts short tty reads as full
   blocks; the input direction uses `head -c $BYTES` instead.

## Reproduce

```sh
# inside the PocketShell guest, in this repo:
apk add proot gcc            # gcc + musl-gcc for the harness; proot only for nested modes
sh benchmarks/phase4-terminal/run_bench.sh layer1 results
sh benchmarks/phase4-terminal/run_bench.sh nested results
sh benchmarks/phase4-terminal/run_bench.sh sigtest results
```

Leave the environment as found: `apk del proot` if it was not installed
before (the 2026-09-14 audit session left it installed for the nested
battery; recorded here for honesty).

## What is NOT measured here (and why)

- **App-side (Android) latency** — TerminalView rendering, the app's I/O
  thread hops, `prepareLinuxSession`, Activity recreate. Requires adb or an
  in-app instrumentation harness; Wireless debugging was OFF this session
  and adb pairing needs the owner to re-enable it. These are analyzed at
  code level in the Phase 4 report and marked accordingly.
- **UI-visible frame costs** — same reason (no adb screencap/uiautomator).
