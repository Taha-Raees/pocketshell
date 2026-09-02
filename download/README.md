# download/ — delivery masters

Current: v0.4.3-m2.4 (git tip 79afdee, versionCode 11)
- PocketShell-v0.4.3-m2.4-debug.apk  sha256 02e0e746…a00fe6
  Installs IN PLACE over v0.4.2 (same pinned cert d96a6f66…8bf659).
  Fixes "DNS: transient error": guest resolv.conf now = device resolvers
  FIRST + public fallbacks (musl MAXNS=3, parallel query, first answer
  wins), refreshed on every package operation; the old v0.4.1–v0.4.2
  device-only file upgrades itself on the first tap (docs/CHANGELOG 0.4.3).
- PocketShell-v0.4.3-m2.4-source.zip sha256 35ae6adb…a26cbcd  (4.0 MB, 249 files)
- PocketShell-v0.4.3-m2.4-source.tar.gz sha256 e38a7513…797da37  (3.9 MB)
- pocketshell-m2.gitbundle           sha256 39d69f16…d7fd17  (full history)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.4.2 device DNS was a single point of failure —
CHANGELOG 0.4.3; v0.4.1 SELinux-blocked — 0.4.2; earlier superseded).
Source history for every milestone stays reachable through the bundle
inside each source archive.
