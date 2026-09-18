# M8 — Home Linux Widget System: candidate research

Status: research complete (Agent L, 2026-09-18). Architecture: `docs/M8-WIDGET-SYSTEM.md`.

The product question every candidate had to answer:

> Why would a Linux developer want this information/action on their phone
> instead of simply typing a command in the terminal?

Facts below come from the M8 repository audit + lab experiments on the x86
development machine (BunsenLabs). Lab numbers are **proxies** — the device
gate (`docs/TESTING.md` §64) re-measures on ARM64 hardware.

## 1. Environment facts the research rests on

- Guest = Alpine 3.24.1 aarch64 under proot (`RuntimePin`); the app and the
  guest share one Linux UID, so app-side `/proc` shows exactly the guest's
  process/socket world (hidepid=2, PROCFS-CONTRACT §1).
- Pinned minirootfs busybox applet inventory (verified by extraction of the
  SHA256-pinned tar, `f55a90f6…21259`): `netstat`, `lsof`, `du`, `df`, `ps`,
  `top`, `ip`, `nc` ship; **`ss`, `tmux`, `ssh` do not** (tmux/ssh only via
  the optional `pocketshell-dev-bootstrap` layer).
- SELinux denies untrusted apps `/proc/stat`, `loadavg`, `uptime`, `version`,
  `vmstat` (device-confirmed, M2.6) — anything built on global CPU/load
  counters cannot be honest on this platform.
- `/proc/net/*` readability app-side is **expected but unproven on device**
  (modern Android scopes it per-UID, which is exactly the view we need);
  probed with honest degradation, verified in the device gate.
- Lab cost measurements (x86 proxy):
  - `/proc/net/tcp`+`tcp6` LISTEN parse: **~1.9 ms** per pass.
  - full `/proc/<pid>/fd` socket-inode scan (239 procs): **~28–92 ms** —
    must run only when the listener inode set changes (parse is the cheap
    heartbeat). `Files.readSymbolicLink` is required; `canonicalPath`
    throws on `socket:[…]` links (lab-verified).
  - recursive size walk, 30k files: **~122 ms** — fine as an on-demand,
    cached scan.

## 2. Candidate evaluation table

| # | Widget | User value on a phone | Data source | Cost | Guest compat | Verdict | Notes / concerns |
|---|--------|----------------------|-------------|------|--------------|---------|------------------|
| 1 | **Terminal** (core) | THE entry point; running-session count at a glance | `TerminalSessionManager.sessions` (reactive) | zero | n/a | **BUILD — core slot 1** | existing card, becomes the default slot-1 widget |
| 2 | **Linux** (core) | runtime state honesty (ready / installing / repair) | `RuntimeManager.state` (reactive) | zero | n/a | **BUILD — core slot 2** | existing card, becomes the default slot-2 widget |
| 3 | **Servers** (listening ports + owner) | dev servers started in the guest are otherwise invisible until you type `netstat`; tap → terminal | app-side `/proc/net/tcp(6)` + `/proc/<pid>/fd` socket-inode match (same-UID) | ~2 ms steady / ~30 ms on change | perfect (no proot spawn; works before any terminal opens) | **BUILD (built-in)** | merges the "Servers" and "Ports" candidates — same data, developer framing; no `ss`/iproute2 needed; kernel may scope `/proc/net` per-UID (exactly our view) — probe + degrade honestly |
| 4 | **Storage** (Linux-side) | dev caches/toolchains eat GBs; `du` by hand is slow and awkward on a phone | app-side NOFOLLOW walk of the app-owned rootfs + apk cache | ~122 ms / 30k files, on-demand + cached | perfect (app UID owns every byte) | **BUILD (built-in)** | shows Linux storage, NOT Android storage; tap → Files at guest root; honest "scanning…" state |
| 5 | **Agents** | "is my agent still running / does it need me?" — the M7.2 question, answerable without opening a tab | `AgentActivityRepository.homeSessionClaims` (the ONE projection) | zero (pure projection) | n/a | **BUILD (built-in)** | reuses the exact parity wording; no second detector, no `/proc`, vocabulary allowlist discipline extends |
| 6 | Processes | `ps`/`top` already answer this in one command; weak differentiation | same as #3 | low | ok | **optional (catalog later)** | value did not beat "just type ps"; revisit only with a genuinely interactive angle (inspect/kill flow) |
| 7 | Tmux | session list at a glance is real value for a persistent workstation | guest exec `tmux ls` (one proot spawn per refresh) | one spawn ≫ probes above | **tmux absent unless dev-bootstrap ran** | **optional (catalog later)** | capability-gated; every refresh costs a proot spawn; the existing Sessions section already covers "get back to work" |
| 8 | SSH | remote hosts are a major mobile workflow | none today (no connection tracking exists in-app) | — | — | **deferred** | nothing authoritative to show; faking it violates terminal honesty; wait for a real SSH session layer |
| 9 | Toolchain versions | "Node/Go/Rust versions" is diagnostics, not Home material | guest exec batch | one spawn | ok | **rejected** | the prompt's own suspicion confirmed: a static inventory nobody acts on from Home |
| 10 | Build status | live build state WOULD be valuable | none — no authoritative build-event source exists in-app | — | — | **rejected** | would be fake status (banned); agent-side hooks (M7.2 P10 pattern) are the only honest future path |
| 11 | Databases / Containers | real workflows, but nothing to manage in-guest today | apk/podman absent; Docker daemon impossible (no root, no namespaces) | — | **docker/podman: no** (kernel features proot cannot provide) | **deferred** | documented platform limit, not laziness: proot has no cgroups/namespaces; a podman-less rootful path does not exist |
| 12 | Device/system stats (battery, Wi-Fi, RAM, CPU, temperature, clock, weather, uptime) | zero Linux-workstation value | — | — | — | **banned** | Android already has a status bar; global CPU/load counters are SELinux-denied anyway (M2.6) |

## 3. What was built and why it earns its space

Exactly five built-in widgets ship (the two preserved core cards + three new
ones), because each one answers the product question with data the terminal
either cannot show at a glance or costs real interaction time:

- **Servers** — a server you started and forgot is invisible; the card makes
  it visible ~2 ms of parsing after it opens its port, from the same source
  of truth the kernel keeps. No command to type, no spawn, no PTY.
- **Storage** — the answer to "why is my phone full?" for the Linux half of
  the device, computed where the bytes actually live, on demand.
- **Agents** — the one authoritative M7.2 activity projection, aggregated
  client-side into a glance; zero new state, zero new detection.

Everything else in the table is either dishonest today (build status), better
as a command (processes, toolchain), capability-gated future work (tmux,
SSH), or banned platform-wide (device stats).
