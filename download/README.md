# download/ — delivery masters

Current: v0.6.1-m2.6 (git tip loaded in bundle, versionCode 15)
- PocketShell-v0.6.1-m2.6-debug.apk  sha256 26a8f188079f97cfe41b5a1b7a614c67bd08208c1d02adebe9e7cedc65bb8897
  Installs IN PLACE over v0.6.0/v0.5.0 (same pinned cert d96a6f66…8bf659).
  The runtime/rootfs does NOT need reinstalling — behaviorally identical
  to v0.6.0; only wording/docs changed after the 2026-09-02 device test.
  DEVICE TEST RESULT (2026-09-02, SM-F711B): the M2.6 architecture is
  CONFIRMED on hardware — Diagnostics shows "apk fd-link patch: applied",
  the interactive session binds a REAL /proc (cat /proc/meminfo returns
  the host's real values; numeric pid dirs visible).
  EXPECTED ON-DEVICE, NOT BUGS (both are real Android policy):
  · `ls /proc` prints a wall of "Permission denied" lines for
    kernel-internal entries (kmsg, kcore, vmcore, kpage*, sched_debug,
    …) before the readable tail — the guest /proc IS the host procfs
    (the design) and SELinux denies this app getattr on those nodes.
    The PASS signal is the readable tail: pid dirs, meminfo, cpuinfo,
    cmdline, uptime, loadavg, mounts, sys, tty, fs, bus, irq, driver
    (Samsung adds memsize/memextra). ps/top/htop skip silently.
  · `cat /proc/version` is denied by the One UI kernel (proc_version is
    not granted to apps targeting SDK 28). Informational only —
    `uname -a` shows the kernel banner. Synthesizing /proc/version was
    considered and REJECTED (no-fake rule).
  · apk update / search / add / del keep working BOTH in the shell
    (patched guest apk with /proc bound) and from the app UI
    (no-/proc PACKAGE_OPERATION profile).
  · mechanism: ONE checksum-pinned byte inside the guest's own
    libapk (3.0.6-r0) disables apk's fd-link commit
    (linkat /proc/self/fd — an Android SELinux neverallow) so apk
    always uses its allowed renameat commit. Full evidence chain:
    docs/M2.6-RESEARCH.md. Reproducible: scripts/patch_apk_fdlink.py.
  · Diagnostics "Interactive /proc" row states the EACCES wall up front;
    docs/TESTING.md §10 Gate A re-anchored (readable tail + meminfo/
    cpuinfo; /proc/version is INFORMATIONAL) with an "Expected on-device
    (NOT bugs)" subsection.
- PocketShell-v0.6.1-m2.6-source.zip sha256 f198423bd2c6d25dfffd27b4c554d39906c0018fe2dd560b4794019ec4145ecb  (27 MB, 255 files)
- PocketShell-v0.6.1-m2.6-source.tar.gz sha256 e99561d3d7a235bb7e8bee7c025da7f207c8400907ed6ee6ac47209e4b8c74d0  (27 MB)
- pocketshell-m2.gitbundle           sha256 1e2afa7e33703d7f6101a64da16a6b23d0f98bcfda95861e691e35a16afcdf5a  (full history; ~25 MB — includes one-time scratch/ objects from the accidental 0380901 snapshot, see the payload-hygiene commit; future bundles no longer grow from scratch)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.6.0 superseded by the v0.6.1 wording/docs
update — see docs/CHANGELOG for each confirmed fix).
