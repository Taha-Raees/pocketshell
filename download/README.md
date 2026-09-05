# download/ — delivery masters

Current: v0.10.0-m6.0.0 (M6.0 — UNIVERSAL RUNTIME COMPATIBILITY: ONE Alpine
distribution running musl + glibc + static + Node tooling side by side. The
glibc blocker identified by both forensic reports is closed as architecture:
REAL Debian 13 trixie glibc 2.41 at the canonical multiarch paths inside the
Alpine rootfs (/lib/ld-linux-aarch64.so.1 + /lib/aarch64-linux-gnu +
/usr/lib/aarch64-linux-gnu + /etc/nsswitch.conf); musl paths disjoint by
construction and never touched; transparent exec for glibc binaries AND their
children with zero env vars / zero proot changes / zero per-binary wrappers;
layer pinned, ships in the APK, self-heals into EXISTING runtimes on the next
session spawn (GuestApkCompat pattern); pocketshell-exec + pocketshell-doctor
ship inside the guest; new permanent suite runtime-tests/ — sandbox 20/20,
device runner under emulation 24/24 ALL GREEN incl. the REAL Cline 3.0.61
binary (version/help/node-spawn/relaunch x3); JVM suite 768 executions /
0 failures; decision record docs/runtime/DUAL_LIBC.md, limitations
docs/runtime/KNOWN_LIMITATIONS.md. versionCode 40)
- PocketShell-v0.10.0-m6.0.0-debug.apk  sha256 16fc63edde2809c88d29ec7a0dcae4b29a2012078bb3bea98f16ed8ed1dc8aac  (29 MB)
  Installs IN PLACE over v0.9.1-m5.1.0 (39), v0.9.0-m5.0.1 (38),
  v0.9.0-m5.0.0 (37), v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
  v0.8.0-m4.1.0 (34, unannounced intermediate), v0.7.0-m4.0.9 (33) and every
  earlier build (vc16..32) — same pinned cert d96a6f66…8bf659. App data
  (Alpine runtime, Kilo, Hermes, packages, Companion logins, theme choice)
  survives; the glibc layer adds itself to the EXISTING runtime — no
  reinstall, no wipe, no user action.
  Device measurement gate: docs/TESTING.md §33 — the automated
  compatibility suite + doctor + Cline end-to-end + musl regression.
  Full record: docs/CHANGELOG [0.10.0-m6.0.0].
- PocketShell-v0.10.0-m6.0.0-source.zip sha256 50f80df94147dee960ba6df818443c19bbe437a0dd8664b421354a7980b5c2ff  (47 MB uncompressed, 374 files)
- PocketShell-v0.10.0-m6.0.0-source.tar.gz sha256 0c9f92ee1c3ece4ecba5be53ef5aa6d690d06287b9a90f5e1a72d13e42ec4ea3
- pocketshell-m2.gitbundle           sha256 00ec6d1cd4db5941c82437873c88965ab52755f931a23dc951d46faf3bdcc2e4  (full history; ~35M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/RENDER-RESET-M4.0.9.md, and the new docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 deac812b016726cc3d4645e7c78639b25ed94aa5dab3abeb30de5715473bb12b  (704 KB — the executable compatibility suite: run_on_device.sh + the cross-compiled test binaries; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11  (6.5 MB — the glibc layer artifact, transparency copy; the APK ships the identical bytes and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 0e0a2bf8363647aece215f4f0a00b658debb3d42a443b480d564b01cd7d17c0d  (34 pages — the read-only platform/runtime forensic audit this milestone implements; kept downloadable as the phase baseline)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.9.1-m5.1.0 superseded by m6.0.0; its record
lives in the bundle history — see docs/CHANGELOG for each confirmed fix).
