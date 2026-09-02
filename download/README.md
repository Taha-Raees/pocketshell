# download/ — delivery masters

Current: v0.4.2-m2.4 (git tip 1f72be9) — REBUILT after sandbox reset #6;
adds the cancel-race hardening on top of the recorded c0ae7234 build
(that APK was never delivered — it died in the reset before reaching the
user, so reusing versionCode 10 is safe).
- PocketShell-v0.4.2-m2.4-debug.apk  sha256 78306b16…d7d680b  (versionCode 10)
  Installs IN PLACE over v0.4.1 (same pinned cert d96a6f66…8bf659, committed
  at keystore/debug.keystore). Fixes the SELinux hardlink neverallow that
  made every apk fetch die with "Permission denied", the all-cards-"Working…"
  bug, and the cancel-race wedge (docs/CHANGELOG 0.4.2).
- PocketShell-v0.4.2-m2.4-source.zip sha256 5662870e…e90a02  (4.0 MB, 249 files)
- PocketShell-v0.4.2-m2.4-source.tar.gz sha256 131c0512…3ecfebf4  (3.9 MB)
- pocketshell-m2.gitbundle           sha256 25db79c1…8f05963  (full history,
  56 commits; working dirs untracked — bundle 2.5 MB)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.4.1 apk fetch was SELinux-blocked — see
CHANGELOG 0.4.2; v0.4.0 and earlier superseded). Source history for every
milestone stays reachable through the bundle inside each source archive.
