# download/ — delivery masters

Current: v0.10.0-m6.0.1 (M6.0.1 — INSTALL OBSERVABILITY + SUITE DIAGNOSIS, the
m6.0.0 device-gate lesson turned into instrumentation. M6.0's Universal
Runtime Compatibility is unchanged: ONE Alpine distribution running musl +
glibc + static + Node tooling side by side; REAL Debian 13 trixie glibc 2.41
at the canonical multiarch paths inside the Alpine rootfs; musl paths disjoint
by construction and never touched; transparent exec for glibc binaries AND
their children; layer pinned, ships in the APK, self-heals into EXISTING
runtimes. NEW in m6.0.1: every layer install outcome mirrored to the guest at
/etc/pocketshell/glibc-runtime.status (state=OK source=…/state=FAILED
reason=…); suite v2 PREFLIGHT diagnosis + honest SKIP-with-fix-path for a
missing layer + capability-probe layer detection + cline --help false-PASS fix
+ POCKETSHELL_INSTALL_LAYER=1 in-guest repair hatch; validated under emulation
in 4 device states incl. repair of a gcompat-contaminated rootfs (the exact
device-gate condition); JVM suite 776 executions / 0 failures. versionCode 41)
- PocketShell-v0.10.0-m6.0.1-debug.apk  sha256 915677b6d60383324ed208d8e58b84a1794d16f2748256b35bd7cec79e768388  (29 MB)
  Installs IN PLACE over v0.10.0-m6.0.0 (40), v0.9.1-m5.1.0 (39),
  v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37), v0.8.0-m4.0.12 (36),
  v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34, unannounced intermediate),
  v0.7.0-m4.0.9 (33) and every earlier build (vc16..32) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes, packages,
  Companion logins, theme choice) survives; the glibc layer adds itself to
  the EXISTING runtime — no reinstall, no wipe, no user action.
  Device measurement gate: docs/TESTING.md §33 — the automated
  compatibility suite (v2, with PREFLIGHT) + doctor + Cline end-to-end +
  musl regression. Full record: docs/CHANGELOG [0.10.0-m6.0.1].
- PocketShell-v0.10.0-m6.0.1-source.zip sha256 62eafc420d267d363f1c6f50079b76056a6a2de5e9817a30f2454e4c5c5efcc3  (47 MB uncompressed, 377 files)
- PocketShell-v0.10.0-m6.0.1-source.tar.gz sha256 6b58ab445ceaa0375398f40ec0c147687749a4ab4fb212378cb1202f14d0b2bf
- pocketshell-m2.gitbundle           sha256 3a5992644e899f22fa7791dc198af410685a3051c34a4a582870dd62f607ae94  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/RENDER-RESET-M4.0.9.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 b203fa58488411a6a32cc2dfce18f1a657209f81b330258c30a4dcd74c5e6b21  (708 KB — suite v2: run_on_device.sh with PREFLIGHT diagnosis + SKIP-with-fix-path + POCKETSHELL_INSTALL_LAYER=1 repair hatch, plus the cross-compiled test binaries; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11  (6.5 MB — the glibc layer artifact, transparency copy; the APK ships the identical bytes and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 0e0a2bf8363647aece215f4f0a00b658debb3d42a443b480d564b01cd7d17c0d  (34 pages — the read-only platform/runtime forensic audit this milestone implements; kept downloadable as the phase baseline)

All served on :3000 from public/ (same bytes, HTTP-verified).
Older builds: withdrawn (v0.10.0-m6.0.0 superseded by m6.0.1 after one day —
observability patch only, layer bytes unchanged; its record lives in the
bundle history — see docs/CHANGELOG for each confirmed fix).
