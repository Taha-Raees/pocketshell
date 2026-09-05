# download/ — delivery masters

Current: v0.10.0-m6.0.2 (M6.0.2 — THE ACTUAL INSTALL-PATH FIX. The m6.0.0/m6.0.1
device gates failed for one PROVEN root cause: AGP's asset merge decompresses
*.gz assets and strips the suffix — the shipped APK carried the layer as a PLAIN
tar under guest/….tar while the code opened guest/….tar.gz, so every session
spawn failed with FileNotFoundException BEFORE touching the rootfs (best-effort
swallowed on vc40; status-file-only on vc41, unread by the v1 suite). FIXED on
three rails: the pin now describes the PACKAGED form (guest/….tar,
17,909,760 B, sha 5be400dd…) beside the unchanged release-artifact pin; the
extractor format-sniffs gzip magic (either packaged form works) and sha-verifies
the asset BEFORE extraction; a new JVM regression pin opens the BUILT APK and
asserts the packaged asset name+size+sha. PLUS: app identity stamped to
/etc/pocketshell/app-version on every spawn (the suite PREFLIGHT proves WHICH
build owns the runtime); suite v2.1 self-locates its binaries (flat AND bin/
layouts), prints its version in the header, and PREFLIGHT reports the app stamp
+ raw ls/readlink loader evidence. M6.0's Universal Runtime Compatibility is
unchanged: ONE Alpine distribution running musl + glibc + static + Node tooling;
REAL Debian 13 trixie glibc 2.41 at the canonical multiarch paths; musl paths
disjoint and never touched; layer bytes UNCHANGED (2242f8ef…). JVM suite 780
executions / 0 failures; rig device-state validation 3 phases green (24/24
incl. Cline 3.0.61). versionCode 42)
- PocketShell-v0.10.0-m6.0.2-debug.apk  sha256 832648e5e6d89554bf11e786c9bb9d1a48b7054815502791d91175fcf31da6d0  (30 MB)
  Installs IN PLACE over v0.10.0-m6.0.1 (41), v0.10.0-m6.0.0 (40),
  v0.9.1-m5.1.0 (39), v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
  v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34, unannounced
  intermediate), v0.7.0-m4.0.9 (33) and every earlier build (vc16..32) — same
  pinned cert d96a6f66…8bf659. App data (Alpine runtime, Kilo, Hermes,
  packages, Companion logins, theme choice) survives; the glibc layer now
  REALLY adds itself to the EXISTING runtime on the next session spawn — no
  reinstall, no wipe, no user action.
  Device gate: docs/TESTING.md §33.1 with the FRESH v2.1 suite tarball (the
  /tmp/kilo/gdrive copy is the stale v1 suite — delete it and re-download).
- PocketShell-v0.10.0-m6.0.2-source.zip sha256 82875abeef54932afe7c2829e37b27a4304e170b228d81eed472515ecb7c411e  (47 MB uncompressed, 379 files)
- PocketShell-v0.10.0-m6.0.2-source.tar.gz sha256 a77cece3378cebc95003a0f672df96cf114048d7ec521e2a43d74d7858ce48eb
- pocketshell-m2.gitbundle           sha256 73d6bd4d844883b7ab72917afb4a3539fe1e4d86ec289ffc9393c92f5629b6b3  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/RENDER-RESET-M4.0.9.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 22e283ef07e2984942e8f6e40b4cb43b9b6d8ef240105285b4d30bb1873c0b1e  (708 KB — suite v2.1: self-locating runner (flat + bin/ layouts), suite version in header, PREFLIGHT with app-version stamp + status file + ls/readlink loader evidence + SKIP-with-fix-path + POCKETSHELL_INSTALL_LAYER=1 repair hatch, plus the cross-compiled test binaries; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11  (6.5 MB — the glibc layer artifact, transparency copy; the APK ships the identical bytes decompressed (sha 5be400dd…, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 0e0a2bf8363647aece215f4f0a00b658debb3d42a443b480d564b01cd7d17c0d  (34 pages — the read-only platform/runtime forensic audit; kept downloadable as the phase baseline)

All served on :3000 from public/ (same bytes, HTTP-verified; the release
mirror now also extracts and sha-verifies the APK's embedded layer asset —
the m6.0.2 release-checklist lesson).
Older builds: withdrawn (v0.10.0-m6.0.1 superseded by m6.0.2 after one day —
the install-path fix; layer bytes unchanged; its record lives in the bundle
history — see docs/CHANGELOG for each confirmed fix).
