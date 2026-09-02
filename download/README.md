# download/ — delivery masters

Current: v0.6.2-m2.6 (git tip loaded in bundle, versionCode 16)
- PocketShell-v0.6.2-m2.6-debug.apk  sha256 35c4cd698010cb8039369059df5c60042cc06232630848acb556fc8136249226
  Installs IN PLACE over v0.6.1/v0.6.0/v0.5.0 (same pinned cert d96a6f66…8bf659).
  The runtime/rootfs does NOT need reinstalling.
  M2.6.13 — HARDLINK EXTRACTION FIXED (your 2026-09-02 report):
  · apk add binutils/gcc/g++ failed with 19 "failed to extract …
    Permission denied" errors — EXACTLY the hardlink entries of those
    packages (verified against the tar entry types: 11 + 5 + 3).
  · root cause: apk materializes tar hardlinks with link(), and Android
    SELinux neverallows link() to untrusted apps (the same neverallow
    M2.6 bypassed for download commits — extraction is a different call
    site).
  · fix: guest sessions now run proot's link2symlink extension
    (--link2symlink) — the SAME Termux-proot extension PRoot-Distro
    enables by default; link() is intercepted and emulated as a symlink
    chain, so the kernel never evaluates the denied operation. Pure
    reuse of the proot we already ship; no binary patch.
  · your broken binutils/gcc/g++ state self-heals on the first
    `apk fix` under v0.6.2; then gcc/g++/ld --version are real.
  · honest difference: emulated links appear as symlinks
    (ls -l /usr/bin/ld) and each costs the file's disk space;
    binaries are byte-identical (rehearsal-proven).
  M2.6.12 — SELECTIVE /proc SYSDATA OVERLAY (Termux PRoot-Distro's
  architecture, adapted; see docs/M2.6-RESEARCH.md §7):
  · at every interactive spawn the app probes the five standard procfs
    files (stat, uptime, loadavg, version, vmstat) with a one-byte read;
    kernel-READABLE files are NEVER overlaid (real wins);
  · kernel-DENIED files get a verified compatibility overlay bound
    file-over-file on top of the real /proc bind, content from real host
    sources: uname(2) identity for /proc/version with an explicit
    "PocketShell sysdata overlay" attribution marker in the file itself,
    elapsedRealtime for uptime field 1, real core count + real btime for
    stat, the real hidepid-filtered pid set for loadavg's tail —
    documented placeholders where no allowed source exists;
  · kernel-internal entries (kmsg, kcore, …) stay untouched — the
    ls /proc EACCES wall remains expected and is not overlaid;
  · Diagnostics gains a read-only "sysdata overlays" row (probe-only).
- PocketShell-v0.6.2-m2.6-source.zip sha256 2336c0de5ff55e89a0e5ae1f7277f8a32d8fbabe0dde3bd8c3c09ee4e433b83d  (27 MB, 261 files)
- PocketShell-v0.6.2-m2.6-source.tar.gz sha256 ac74c7e6659ca92f5d86c455a087d68facb0de9618aff856f164869b6d46ede9  (27 MB)
- pocketshell-m2.gitbundle           sha256 66da676c8850d1ef3e55155fbaeb063e937b566e55c6faaf6fc6b7f4ebc64328  (full history @ 615f467; ~25 MB — includes one-time scratch/ objects from the accidental 0380901 snapshot; future bundles stay clean)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.6.1 superseded by the v0.6.2 hardlink +
sysdata fixes — see docs/CHANGELOG for each confirmed fix).
