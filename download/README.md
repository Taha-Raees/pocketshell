# download/ — delivery masters

Current: v0.6.0-m2.6 (git tip loaded in bundle, versionCode 14)
- PocketShell-v0.6.0-m2.6-debug.apk  sha256 c350caca5728a971bd81305fcb0e6d8a94d5345877cdca75f1bad8e245001971
  Installs IN PLACE over v0.5.0 (same pinned cert d96a6f66…8bf659).
  The runtime/rootfs does NOT need reinstalling — the fd-link patch
  installs itself on the first Linux Shell spawn.
  M2.6 — Linux compatibility recovery, no trade:
  · interactive sessions bind a REAL /proc again → `ls /proc`,
    `cat /proc/version`, `ps`, `top`, `htop` work in the guest;
  · `apk update / search / add / del` keep working BOTH in the shell
    (patched guest apk with /proc bound) and from the app UI
    (no-/proc PACKAGE_OPERATION profile);
  · mechanism: ONE checksum-pinned byte inside the guest's own
    libapk (3.0.6-r0) disables apk's fd-link commit
    (linkat /proc/self/fd — an Android SELinux neverallow) so apk
    always uses its allowed renameat commit. Full evidence chain:
    docs/M2.6-RESEARCH.md. Reproducible: scripts/patch_apk_fdlink.py.
  · Diagnostics adds "apk fd-link patch" + "Interactive /proc" rows.
- PocketShell-v0.6.0-m2.6-source.zip sha256 87550cc003c1e2f1c3efe8aba10c72bfafdb0db8c9bab3ddf22e803dbd5059fd  (4.4 MB, 252 files)
- PocketShell-v0.6.0-m2.6-source.tar.gz sha256 1116d6339d16b7cfdd92f638e4b93a478ffaff6df0e0d25c0984b9d91344fbc3  (4.3 MB)
- pocketshell-m2.gitbundle           sha256 bd008b4e8699bdee19839926f90a9a258cea4e5dadbbede54241b3191ba73439  (full history)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.5.0 superseded by the M2.6 /proc
architecture fix — see docs/CHANGELOG for each confirmed fix).
