# download/ — delivery masters

Current: v0.5.0-m2.5 (git tip loaded in bundle, versionCode 13)
- PocketShell-v0.5.0-m2.5-debug.apk  sha256 7c7ee055…644d
  Installs IN PLACE over v0.4.4 (same pinned cert d96a6f66…8bf659).
  M2.4 device gate PASSED (confirmed by your 10:04 screenshots: Nano +
  Git on Home, paste working). M2.5:
  · the Linux Shell is now apk-capable — your manual `apk update` /
    `apk add nodejs npm` works (was: SELinux "Permission denied" +
    stale 31-package cache, because sessions still bound /proc);
    honest cost: `ps`/`top` inside the guest can't read /proc.
  · search "node" now puts nodejs FIRST (name-match ranking) and
    every search hit has a real Install button (no executable
    promises for non-catalog packages).
- PocketShell-v0.5.0-m2.5-source.zip sha256 fa62b17f…c6bd1  (4.0 MB, 244 files)
- PocketShell-v0.5.0-m2.5-source.tar.gz sha256 f6709e11…c0339  (3.9 MB)
- pocketshell-m2.gitbundle           sha256 9d687e46…bbb774  (full history)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.4.4 superseded by the M2.5 shell/search
fixes; v0.4.3 closed the DNS chain; earlier superseded — see
docs/CHANGELOG for each confirmed fix).
