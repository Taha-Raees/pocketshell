# The PocketShell Linux Environment & the Procfs Contract

**Status:** shipped in v0.7.0-m3.6 (versionCode 23) · **Supersedes:** the
conditional-`/proc` policy of M2.6 (docs/M2.6-RESEARCH.md §4.2) ·
**Device trigger:** 2026-09-04 report — Kilo Code died with
`ENOENT: no such file or directory, realpath '/root/.local/state'` on an
*existing* directory after an in-guest `apk update && apk upgrade`.

---

## 1. How the Linux environment actually launches (inspected, not guessed)

PocketShell does **not** use native namespaces, a custom kernel, or a VM.
The environment is **PRoot** — the Termux proot build (pin `7266fb3e`,
compiled into the APK as `libproot.so` + `libproot-loader.so` +
`libtalloc.so` in `nativeLibraryDir`, the only executable location at
targetSdk ≥ 29) — running against a **pinned Alpine minirootfs**
(`alpine-minirootfs-3.24.1-aarch64.tar.gz`, sha256-verified by
`RuntimeInstaller` at install time, extracted under the app's
`noBackupFilesDir`). PRoot is ptrace-based path translation: there are **no
real mounts and no root privileges** — every "mount" in this document is a
proot bind (`--bind=host[:guest]`) recorded when the guest process is
exec'd, translated for that process tree only, and gone when the session
exits (`--kill-on-exit`).

Launch chain for every guest session (`TerminalSessionManager.createLinuxSessionInternal`):

1. `RuntimeProcessLauncher.canEnterLinuxShell` gates on runtime READY.
2. `PackageGateway.prepareGuestForSession` repairs the guest environment
   (DNS resolv.conf, apk cache/tmp dirs, **apk fd-link self-repair** — §3)
   and prepares the sysdata overlays.
3. `RuntimeProcessLauncher.buildSessionSpec` builds the argv/env
   (`proot --kill-on-exit --link2symlink --rootfs=… --root-id --cwd=/root
   --bind=… <guest command>`).
4. `TerminalSession` (termux JNI) owns the PTY: the **host** side allocates
   the pty pair and forks proot when the view first renders the session
   (lazy — the m3.5 argv-launch fix exists precisely because of this).
5. Proot binds live **per session**: they are created at every session
   spawn and vanish with it. Nothing is mounted "once per environment".

### The virtual-filesystem audit (task requirement §4)

| Path | How it is provided | Since | Functional? |
|---|---|---|---|
| `/dev` | `--bind=/dev` (real host devtmpfs) — every interactive AND package session | v0.3 | yes |
| `/dev/ptmx`, `/dev/pts` | ride the `/dev` bind (host devpts). The terminal's own PTY is allocated host-side by the termux JNI; guest processes allocate through the same devpts. | v0.3 | yes |
| `/sys` | `--bind=/sys` (real host sysfs); some nodes deny reads under app SELinux — honest, kernel-enforced | v0.3 | yes |
| `/proc` | `--bind=/proc` (real host procfs, hidepid=2 — the guest sees the app's own real process tree) **+ verified sysdata file-over-file overlays for kernel-denied standard files** (stat, uptime, …; M2.6.12) | **v0.7.0-m3.6: ALWAYS for interactive sessions** (was: conditional, see §2) | yes — and now unconditional |
| `/tmp` | **rootfs-internal** (not a host bind — intentional isolation), mode 1777 guaranteed by `GuestEnvironment.ensureApkWorkspace`; the spec sets `TMPDIR=/tmp` | v0.2 | yes (device-proven: the 2026-09-04 session wrote `/tmp/kilo-state` successfully) |
| apk cache | two app-owned host dirs bound over `etc/apk/cache` and `var/cache/apk` so rootfs permissions can never block apk | v0.4.1 | yes |

Package-operation execs (`AlpinePackageManager` via
`GuestExecutionProfile.PACKAGE_OPERATION`) deliberately bind `/dev` + `/sys`
but **never** `/proc` — the builder `require()`-guards it. That profile is
the SELinux-safe apk commit environment (named-tmpfile + renameat; the
fd-link commit is never available there). Unchanged by m3.6.

---

## 2. Root cause of the 2026-09-04 regression (exact chain)

M2.6 made the interactive `/proc` bind **conditional**: sessions only bound
`/proc` when `GuestApkCompat` verified that the guest's
`usr/lib/libapk.so.3.0.0` was the checksum-pinned *patched* build (the
one-byte fd-link patch that keeps in-guest `apk` off the SELinux-forbidden
`linkat("/proc/self/fd/N")` commit). Any other library state →
`Result.NotApplicable` → **session spawns with no `/proc`**, by design,
silently.

The trigger: `apk update && apk upgrade` **inside the guest** replaced the
patched library with a newer apk-tools build. The pin no longer matched, so
every subsequently spawned session lost `/proc`.

Why that broke Kilo Code (and anything Bun-based) while `ls`, `realpath`
(coreutils) and `apk` kept working: **aarch64 Linux has no `realpath`
syscall.** Bun-compiled binaries resolve canonical paths through
`/proc/self/fd/<fd>` (open path → readlink the fd). With no procfs, every
Bun `realpath()` of an *existing* path returns ENOENT — exactly the
user-visible `TUI worker error ENOENT … realpath '/kilo/state'`. Alpine's
coreutils `realpath` walks symlinks in userspace and never notices. `ps`,
`top`, `cat /proc/version`, `readlink /proc/self/root` were missing for the
same reason.

Manual `mount -t proc proc /proc` "worked" in-session because proot
emulates `mount(2)` by recording the bind (host procfs appeared in the
guest) — that proved the diagnosis; it was never a fix.

---

## 3. The permanent fix (m3.6)

Two halves, both in the environment-initialization layer — nothing for the
user to do, nothing faked:

### 3.1 `/proc` is unconditional for interactive sessions

`RuntimeProcessLauncher.buildSessionSpec` no longer takes a `procEnabled`
parameter — the bind is **derived from the profile**: interactive ⇒ always
`--bind=/proc` (+ sysdata overlays), package operation ⇒ never. There is no
remaining code path that can express a no-`/proc` user session.

### 3.2 The apk fd-link gate self-heals (pattern-based, version-independent)

`GuestApkCompat.ensure` no longer depends on a checksum pin matching one
apk-tools build. It scans the guest's `libapk.so.3*` libraries for:

- the standalone NUL-terminated gate literal `"/proc/self/fd"` (what
  `is_proc_fd_ok()` probes), and
- the `"/proc/self/fd/%d"` format literal (proof the file is apk's io.c).

Then:

| Scan result | Action |
|---|---|
| 0 gates + format present | **Ready** — already safe (pinned patched build or previously self-repaired); nothing written |
| exactly 1 gate + format present | **self-repair**: flip the gate's trailing `d`→`X` (`"/proc/self/fd"` → `"/proc/self/fX"`, same length), stage via `tmp`+`rename`, re-verify **from disk**, keep the executable bit → **Ready** |
| anything else (ambiguous literals, no format literal, junk) | **NotApplicable** — nothing is ever written to a file we don't recognize; Diagnostics reports the residual risk (manual in-guest apk may hit the SELinux linkat denial while `/proc` is bound) |

Byte-exactness proof (re-run 2026-09-04 in the sandbox, `scratch/procfix`):
repairing the pinned minirootfs's `libapk.so.3.0.0`
(`ef1c9d8d…db4`) produces sha256 `b8cd95e2…de9` — **identical to the
asset** `scripts/patch_apk_fdlink.py` shipped in M2.6–m3.5
(`PATCHED_LIBAPK_SHA256`). The same script's evidence header documents the
identical literal layout in apk-tools **3.0.8**, so a post-upgrade library
self-heals on the next session spawn with the same single-byte patch.
Running ensure() on every spawn is idempotent and free (a safe shape is
read-only detection).

Idempotency & failure honesty: verification is a pure byte scan; repairs
are staged tmp+rename (a library mapped by a running process keeps its old
inode); every outcome is reported (`Ready`/`NotApplicable`/`Failed`) and
surfaced in Diagnostics. Repeated launches, app restarts, activity
recreation and environment restarts all funnel through the same
`prepareGuestForSession` → `buildSessionSpec` path, so the guarantee holds
for every session shape.

---

## 4. Automated environment validation (three layers)

1. **Spawn-time audit (fail-loud).** `RuntimeProcessLauncher.procContractProblem(spec)`
   audits an interactive spec's argv for the complete virtual-fs contract
   (`--bind=/proc`, `--bind=/dev`, `--bind=/sys`). `buildSessionSpec` runs
   it on **every** spawn; a spec without the contract throws a
   human-readable `IllegalStateException` instead of silently starting a
   broken guest. Pure argv inspection — no I/O, no measurable overhead.
2. **Unit-test pins.** `RuntimeProcessLauncherTest` pins the argv contract
   (interactive always `/proc`; package never; overlays directly after the
   `/proc` bind; the audit catches stripped specs), and
   `GuestApkCompatTest` pins the scan/repair decision table (safe,
   repairable, ambiguous, alien, junk, sibling libraries, read-only probe).
3. **Device gate (TESTING.md §16).** In a freshly launched Linux session:
   `ls -ld /proc` → real dir · `ls /proc/self` works ·
   `cat /proc/version` prints the kernel banner ·
   `readlink /proc/self/root` → `/` · `ps` lists processes · then the
   regression flow with Kilo (`kilo --version`, `kilo config check` with
   XDG dirs set, `kilo`) — **without** any manual mount.

---

## 5. Why the old implementation shipped `/proc` missing

The M2.6 design traded `/proc` for apk: bind procfs only when the patched
library was *proven*. The proof was a whole-file checksum pin — correct but
brittle, because the guest's package manager is allowed to replace the very
file being pinned. When it did, the honest-degradation fallback (no
`/proc`) fired, and the fallback that was reasonable for `ps` in 2026-09-02
was fatal for Bun-based CLIs on aarch64. m3.6 removes the conditional
entirely: procfs is part of the platform contract; apk's commit-path safety
is maintained by the self-repairing patch instead.
