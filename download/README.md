# download/ — delivery masters

Current: v0.4.2-m2.4 (git tip 27a3cb5)
- PocketShell-v0.4.2-m2.4-debug.apk  sha256 c0ae7234…b8f7d  (versionCode 10)
  Installs IN PLACE over v0.4.1 (same pinned cert d96a6f66…8bf659, committed
  at keystore/debug.keystore). Fixes the SELinux hardlink neverallow that
  made every apk fetch die with "Permission denied" (docs/CHANGELOG 0.4.2).
- PocketShell-v0.4.2-m2.4-source.zip sha256 98dedce8…32161  (3.1 MB, 249 files)
- PocketShell-v0.4.2-m2.4-source.tar.gz sha256 8babc01e…5925a3  (3.0 MB)
- pocketshell-m2.gitbundle           sha256 5c71eb3e…89897  (full history,
  55 commits; working dirs untracked — bundle back to 1.6 MB from 96 MB)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.4.1 apk fetch was SELinux-blocked — see
CHANGELOG 0.4.2; v0.4.0 and earlier superseded). Source history for every
milestone stays reachable through the bundle inside each source archive.
