# download/ — delivery masters

Current: v0.7.0-m3.6 (payload cut at git tip 9539a03; Phase 3.6 — The
Procfs Contract, versionCode 23)
- PocketShell-v0.7.0-m3.6-debug.apk  sha256 195443f9eaf1ccaa1c686de88f318749427d4a53897a7d3308000b074e665e79
  Installs IN PLACE over v0.7.0-m3.5 (versionCode 22), m3.4 (21), m3.3 (20),
  m3.2 (19), m3.1 (18), the discarded v0.7.0-ui (17) and v0.6.2 (16) — same
  pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes,
  packages) survives.
  SCOPE: procfs initialization + apk self-repair only — ZERO probing/
  launch-transport/terminal/presentation changes. Tap-to-launch, the
  registry, verify-before-launch, the Midnight pages and every runtime
  behavior from Phase 3.5 are untouched.
  Contract: docs/PROCFS-CONTRACT.md (launch architecture, per-session bind
  audit, validation layers, device gate 16).
  Phase 3.6 highlights:
  · THE FIX: every interactive terminal session now binds a REAL /proc —
    absolutely, derived from the session profile, never conditionally.
    Root cause chain: an in-guest `apk upgrade` replaced the checksum-
    pinned patched libapk → the M2.6 conditional /proc gate failed → new
    sessions spawned without /proc → Bun-compiled CLIs (Kilo's embedded
    runtime) resolve paths via /proc/self/fd on aarch64 (no realpath
    syscall) → realpath() of existing directories returned ENOENT. The
    manual `mount -t proc proc /proc` workaround is no longer needed.
  · apk fd-gate SELF-REPAIR: GuestApkCompat now pattern-scans whatever
    libapk.so.3* the guest carries for the standalone '/proc/self/fd'
    gate literal (+ the '/proc/self/fd/%d' format literal as proof) and
    re-applies the one-byte patch to any matching apk-tools build;
    ambiguous/alien shapes are refused without writes. Byte equivalence
    with the M2.6 asset re-proven on the pinned minirootfs.
  · FAIL-LOUD spawn contract: procContractProblem() audits every
    interactive spec for /proc+/dev+/sys at spawn — a stripped spec
    refuses to start with an explicit diagnostic, never runs crippled.
  · PACKAGE_OPERATION sessions stay /proc-free (require-guarded,
    device-proven SELinux-safe apk commit environment).
  · Guest smoke gate: scripts/diagnose_platform.sh (procfs + /dev + /dev/
    pts + /sys + /tmp + libc identity + ELF interpreter probe) — verified
    PASS=11/FAIL=0 with the fixed shape in the proot rehearsal.
  · 664 test executions green (baseline + proc-contract/self-repair pins).
  Device gate: docs/TESTING.md §16 (fresh session: ls /proc/self, cat
  /proc/version without any manual mount; kilo starts clean; apk survives
  an in-guest upgrade; §12–§15 regressions).
- PocketShell-v0.7.0-m3.6-source.zip sha256 5e58c9302377e12027168024054e1c726b3293d3a58ebcf32d272ab83dd50385  (28 MB, 303 files)
- PocketShell-v0.7.0-m3.6-source.tar.gz sha256 bb17d7262ed7ad759c7677ca2fa19b0aa93fa4f163553d81cd55a59d4a4a9391  (28 MB)
- pocketshell-m2.gitbundle           sha256 122206cd69f56ce279bf02d66f336e101aa6f1656959c017d061b55c8054b689  (full history @ 9539a03; ~26 MB — includes the complete milestone history, the discarded UI attempt + rollback records, all six Phase 3 design contracts, and docs/PROCFS-CONTRACT.md — honest, no rewrites)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.7.0-m3.5 superseded by Phase 3.6; its records
live in the bundle history — see docs/CHANGELOG for each confirmed fix).
