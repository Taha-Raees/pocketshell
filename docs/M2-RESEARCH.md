# M2 Research — Real Linux Runtime for PocketShell

Milestone: M2.1 · Status: COMPLETE (research phase)
Date: 2026-09-01 · Baseline: M1.2-input-fix verified (166/166 unit tests, assembleDebug PASS)

## 0. Executive decision

> **PocketShell M2 will use: PRoot + Alpine Linux minirootfs (aarch64) + apk,
> with the proot binary bundled through `jniLibs` (installed to
> `nativeLibraryDir`) and built from the Termux proot fork.**
>
> `compileSdk 36 / targetSdk 36 / minSdk 26` are **kept unchanged**.

This is the only architecture that satisfies the M2 requirement — *real
installation, real execution, no simulation* — under Android's executable
policy at modern targetSdk levels, while reusing mature open-source
components end-to-end.

The single most important research finding (Section 2) was verified against
primary sources (AOSP system/sepolicy, main branch, fetched 2026-09-01), not
blog folklore: the popular claim "you must target SDK 28 like Termux" is
**only half true** — it applies to `execve()` of app-data files, which proot
can be built to avoid entirely.

---

## 1. Android's executable policy (the constraint that decides everything)

### 1.1 What is forbidden (primary-source verified)

AOSP `system/sepolicy/private/app_neverallows.te` (main):

```
neverallow {
  all_untrusted_apps
  -untrusted_app_25
  -untrusted_app_27
  -runas_app
} { app_data_file privapp_data_file }:file execute_no_trans;
```

`all_untrusted_apps` includes `untrusted_app` (targetSdk ≥ 34), `_32`
(31 < t ≤ 33), `_30` (29 < t ≤ 31) and `_29` (t = 29). Only `untrusted_app_25`
/ `_27` (targetSdk ≤ 28) and `runas_app` are exempt.

**Consequence:** any app with targetSdk ≥ 29 — including PocketShell at 36 —
can never `execve()` a file that lives in its own writable app data
(`filesDir`, `noBackupFilesDir`, cache, everything). This holds on current
AOSP main (checked 2026-09-01). This is exactly why Termux refuses to raise
its targetSdk: Termux's *entire* userspace (its shell, interpreters, every
installed package) lives in app data and is exec'd directly.

### 1.2 What is deliberately still allowed

Two facts from the same policy tree change the picture:

1. **Plain `execute` on `app_data_file` is NOT blocked.** The policy authors
   note explicitly (`app_neverallows.te`): *"execute cannot be blocked on all
   of app_data_file without causing backwards compatibility issues (see
   b/237289679)"*. Only the *no-transition* execve path is denied. Memory
   mappings with `PROT_EXEC` of app-data files remain permitted (`map` is
   granted; the blanket `execute` ban was consciously avoided).

2. **Binaries packaged in the APK as `lib*.so` (jniLibs) are extracted by the
   system to `nativeLibraryDir` and remain executable at any targetSdk.**
   This is the long-established sanctioned location for app-shipped
   executables.

### 1.3 The escape hatch: proot's split-loader design

proot does not need to `execve()` guest binaries. The Termux proot fork
(`github.com/termux/proot`, GPL-2.0, proot 5.1.0 + ~107 Android-specific
patches) builds a **separate, tiny loader binary** — see its packaging:
`PROOT_UNBUNDLE_LOADER=$TERMUX_PREFIX/libexec/proot` in
`termux-packages/packages/proot/build.sh`. At guest startup, proot executes
only **its own loader** (which can live in `nativeLibraryDir` — always
executable); the loader maps the guest ELF and its interpreter into memory
(`mmap`, uses the still-allowed `execute`/`map` on app data) and transfers
control — **no execve on app-data files ever happens.**

Empirical proof that this class of architecture ships at modern targetSdk:
**UserLAnd** (Google Play) runs real Ubuntu/Debian/Kali root filesystems via
proot with `targetSdkVersion 35` (their `app/build.gradle`, master,
2026-09-01). The pattern is production-proven, not theoretical.

### 1.4 M2 device-gate consequence

The loader path is the highest-risk assumption in M2. It must be validated on
a real device **immediately after the first runtime boots (M2.3 gate
`uname; id; echo hello`)** — before any package-manager work is built on top
of it. A documented fallback exists (Section 6, Plan B).

---

## 2. Option evaluation (Master Prompt §6)

| Criterion | A — Termux-native userspace | **B — PRoot + Alpine minirootfs (chosen)** | C — Other (UserLAnd/Andronix/Linux Deploy) |
|---|---|---|---|
| License | termux-app GPLv3-only; repackaging their bootstrap pulls the whole GPL stack + their repo bandwidth policy forbids third-party redistribution at scale | **GPL-2.0 proot (we compile from source), Alpine minirootfs = official tarball, apk inside guest** | UserLAnd AGPL/Apache mix; Andronix paid scripts; Linux Deploy unmaintained |
| Maintenance | Termux infra, but not designed for reuse by other packages | **proot-me since 2011; termux/proot actively patched for Android** | Mostly single-maintainer |
| ARM64 | yes | **official aarch64 minirootfs + sha256 published** | yes |
| Performance | native (best) | **ptrace overhead ~2–5× on syscall-heavy workloads; fine for CLI tools** | same as B |
| Exec policy @ targetSdk 36 | **broken** (needs execve in app data) | **works via nativeLibraryDir proot + loader/mmap guests** | UserLAnd proves it at 35 |
| Real package manager | apk/dpkg (Termux repos) | **apk against real Alpine repos** | B same |
| Reuse difficulty | very high (hardcoded `/data/data/com.termux/…` prefix paths across thousands of scripts) | **low-moderate: compile proot, download rootfs, wire PTY** | n/a |

**Rejected:** Option A — Termux's userspace assumes its own package name
(paths hardcoded in scripts and its dynamic linker setup), and redistribution
of its repository binaries is against their policy; we already vendored the
two pieces we need (terminal-emulator, terminal-view) in M1 and will likewise
compile proot **ourselves from source**, keeping the GPL obligation minimal
and clean.

**Adopted from Option C:** the *pattern* only (proot in jniLibs, rootfs
downloaded, distro-as-data). No code copied from UserLAnd/Andronix.

---

## 3. Answers to the 15 required questions (Master Prompt §7)

**1) Runtime architecture?**
PRoot (Termux fork, compiled by us, bundled as `libproot.so` + loader in
`jniLibs`) running an **Alpine Linux minirootfs (aarch64)** guest; packages
via **apk** inside the guest.

**2) Why is it better for PocketShell?**
It is the only option that satisfies "real install / real execution / no
simulation" under targetSdk 36's exec policy (Section 1), with official
checksummed rootfs downloads, a real package ecosystem, and no dependency on
Termux's prefix layout or repository policy. It also keeps the M1 terminal
stack untouched — the runtime slots in behind the existing PTY.

**3) What open-source projects are reused?**
- `github.com/termux/proot` (GPL-2.0) — compiled from source in M2.3, never
  downloaded at runtime.
- Alpine Linux `minirootfs` (official release artifact) — downloaded at
  runtime with published SHA-256.
- `org.apache.commons:commons-compress` (Apache-2.0) — in-app tar.gz
  extraction (safe, mature; handles GNU long-name entries).
- Existing M1 vendored `terminal-emulator` / `terminal-view` (GPLv3,
  documented since M0).

**4) Licenses that apply?**
GPL-2.0 (proot binary we distribute → license text + source reference in
`docs/THIRD_PARTY.md`), Apache-2.0 (commons-compress), Alpine packages
(various OSS licenses; attribution aggregated in THIRD_PARTY.md), GPLv3
(Termux terminal library, already handled in M0/M1). PocketShell is
sideloaded; GPL source-availability obligations are met by the public source
archive itself.

**5) Bundled binaries (in APK)?**
`libproot.so` + proot loader, per ABI, in `jniLibs` (added in M2.3).
Nothing else — no rootfs in the APK.

**6) Downloaded binaries/artifacts?**
`https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/alpine-minirootfs-<ver>-aarch64.tar.gz`
— HTTPS, official CDN, version **pinned** in our code (e.g. 3.24.1, size
4,023,732 B, sha256 `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259`),
with checksum + size validated before extraction. Packages are downloaded
later *by apk inside the guest* (not by the Android app).

**7) Where does the root filesystem live?**
`context.noBackupFilesDir/runtime/` — chosen deliberately:
- private by default (no permissions), wiped on uninstall,
- excluded from Android auto-backup (a 25 MB+ Linux rootfs must not be
  silently synced),
- survives app updates and restarts (it is regular app data),
- sibling temp dirs `runtime-download.tmp` / `runtime-extract.tmp` enable
  atomic promotion (Master Prompt §10).

**8) How does a terminal session enter the runtime?**
Existing PTY (M1) → existing `TerminalSession` → process argv becomes the
proot launch line (`proot` + root-relative binds + `/bin/sh -l`) instead of
bare `/system/bin/sh`. Same session object, same view, same keyboard. The
guest's first process is Alpine's ash. Details in M2-ARCHITECTURE §5.

**9) How do packages install?**
Real `apk update` / `apk add <pkg>` typed or launched **inside the proot
session**; output streams through the real PTY to the real terminal. The app
never fabricates progress (Master Prompt §14) — the terminal is the source of
truth.

**10) How are installed applications detected?**
After the user installs a supported package, PocketShell verifies reality:
binary exists in the rootfs (`stat` on `runtime/rootfs/usr/bin/<exe>` plus a
PATH probe inside the guest: `command -v`). Only then does the CLI app appear
in *Installed CLI Apps* (Master Prompt §19/§20). No static fake lists.

**11) Survival model (restart / recreation / backgrounding / upgrade)?**
The rootfs is plain files — survives process death, activity recreation and
app restart by construction. `runtime.json` (Master Prompt §31) records
version/state/timestamps so the app can diagnose on startup. Terminal
*sessions* are a separate M1 concern (existing foreground service +
session manager); runtime state and session lifecycle are decoupled.

**12) Relevant Android restrictions?**
- exec policy (Section 1 — solved, device-gated in M2.3).
- ptrace: an app may ptrace its own children — proot's requirement — allowed.
- No storage permissions needed (private dirs only).
- 16 KB page alignment for bundled native binaries — already satisfied by our
  NDK r28 build config (M1 worklog: "r28c, 16KB default alignment").
- OEM battery managers may kill sessions — existing foreground service
  mitigates; not a runtime-integrity issue.

**13) Disk-space model?**
Download ≈ 3.9 MB → extracted ≈ 20–30 MB → apk cache grows with installs.
Diagnostics expose: runtime dir size, free space (`StatFs`), state. Size is
shown to the user *before* download (Master Prompt §18).

**14) Recovery from corrupted installation?**
Install is staged (`runtime-download.tmp` → verify → `runtime-extract.tmp` →
validate → atomic rename). Any failure leaves any existing `runtime/`
untouched; state becomes `FAILED`/`REPAIR_REQUIRED` with Retry/Repair paths.
A crashed install (app killed mid-way) is detected at startup by orphaned tmp
dirs + missing/invalid `runtime.json` → auto-cleanup → honest
`NOT_INSTALLED`.

**15) Future CLI apps (Hermes etc.)?**
The `CliApp` model binds a user-visible app to a rootfs executable path +
launch command. Once `nodejs` installs via apk (a future milestone), Hermes
and friends layer on top of the same detection/launch plumbing. Nothing in
the M2 design needs rework for them (Master Prompt §34/§35 respected).

---

## 4. M2 validation package (Master Prompt §15)

**Chosen: `nano`** (Alpine `main` repository).
- Small (~200 KB + ncurses dep ≈ 600 KB), installs in seconds.
- `nano --version` proves a real executable.
- Interactive TUI proves keyboard + PTY integration (arrows, CTRL+X exit) —
  a stronger end-to-end signal than a batch tool.

Fallback validation package if nano misbehaves on a device: `file` (also
small, `file --version`, non-interactive).

## 5. ABI strategy (Master Prompt §11)

- **aarch64 (arm64-v8a) is the only supported runtime ABI for M2.** The APK
  continues to ship all 4 ABIs of the *terminal* (M1), but the runtime
  installer refuses (honestly, state `UNSUPPORTED_ABI`) on non-arm64 devices.
- proot is compiled for all 4 ABIs and bundled for all 4 (trivial via
  jniLibs), so the only gate is the aarch64 rootfs.
- x86_64 rootfs support: deferred; the storage/state code is ABI-agnostic.

## 6. Risks & fallbacks

| # | Risk | Likelihood | Mitigation / Plan B |
|---|---|---|---|
| R1 | proot loader path fails on some OEM/SELinux variant at targetSdk 36 | low–medium | **M2.3 is a hard device gate before anything builds on it.** Plan B: `compat` build flavor pinning `targetSdk 28` (one-line switch; rootfs/exec model identical to Termux's, proven by millions of installs). Architecture keeps this switch cheap. |
| R2 | ptrace restrictions on exotic kernels | very low | proot on Android is mainstream (Termux/UserLAnd); document device in test matrix if hit. |
| R3 | Alpine repo reachability from mobile networks | low | apk retries; `apk update` failure is honest and retryable; version-pinned CDN URLs for rootfs. |
| R4 | Extraction bugs (symlinks, long names, modes) | medium (bug-class) | commons-compress + explicit mode/symlink/zip-slip handling + unit tests with synthetic tar fixtures (M2.2). |
| R5 | `app_exec_data_file` (new sandbox exec mechanism, flag-gated) changes the landscape | n/a for M2 | Watch-item only; not relied upon. |

## 7. Sources (primary, fetched 2026-09-01)

- AOSP system/sepolicy `main`: `private/app_neverallows.te`, `private/app.te`,
  `public/untrusted_app.te`, `private/untrusted_app_29.te`,
  `private/untrusted_app_30.te` (exec policy evidence, quoted in §1).
- termux/termux-packages `packages/proot/build.sh` (fork version 5.1.107.92,
  GPL-2.0, `PROOT_UNBUNDLE_LOADER` split-loader packaging).
- dl-cdn.alpinelinux.org `latest-releases.yaml` (minirootfs 3.24.1 aarch64,
  size + sha256).
- UserLAnd `app/build.gradle` (compileSdk 35 / targetSdk 35 / minSdk 24,
  proot-based distros on Play — existence proof).
- developer.android.com "Behavior changes: apps targeting API 29+" (W^X
  framing of the home-dir exec restriction).
