# download/ — delivery masters

Current: v0.4.4-m2.4 (git tip 37d4f82, versionCode 12)
- PocketShell-v0.4.4-m2.4-debug.apk  sha256 3161f40f…e5baa7
  Installs IN PLACE over v0.4.3 (same pinned cert d96a6f66…8bf659).
  M2.4 device gate PASSED on v0.4.3 (nano 9.2 running, fetch OK
  28546 pkgs). v0.4.4 closes the three UI bugs those screenshots
  exposed: installed-state probe no longer discards good output when
  the LAST catalog package (python3) is absent (loop exit-status
  misread hid an installed nano behind "Not installed"); Home's
  "Installed CLI Apps" now comes from the real apk database (the
  never-written M1 DataStore registry is gone); terminal Paste
  actually pastes (empty client callback → TerminalEmulator.paste,
  bracketed-paste aware). All Android-layer — runtime + packages
  untouched (docs/CHANGELOG 0.4.4).
- PocketShell-v0.4.4-m2.4-source.zip sha256 7bae560d…6363ca  (4.0 MB, 244 files)
- PocketShell-v0.4.4-m2.4-source.tar.gz sha256 10906ec2…5e1b62  (3.9 MB)
- pocketshell-m2.gitbundle           sha256 e9203d0d…1761f2  (full history)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.4.3 superseded by the state-sync + paste
fixes; v0.4.2 device DNS was a single point of failure — CHANGELOG
0.4.3; v0.4.1 SELinux-blocked — 0.4.2; earlier superseded).
Source history for every milestone stays reachable through the bundle
inside each source archive.
