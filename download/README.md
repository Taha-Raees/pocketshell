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
disjoint and never touched; layer bytes UNCHANGED (2242f8ef…). JVM suite
780 executions / 0 failures at the original release; RE-VERIFIED 2026-09-06
after a sandbox reset with a clean-room rebuild of the same tree (git
e5b0c93 content): 392 debug-variant test cases / 0 failures including the
built-APK asset pin, on the exact rebuilt bytes below. Archive shas differ
from the first cut (embedded timestamps); versionCode, signing cert and
the embedded layer asset are identical. versionCode 42)
- PocketShell-v0.10.0-m6.0.2-debug.apk  sha256 b6ade0745734cfe8dd101ba31c5980efaccf904d503fe7a83d3cde379cfcf577  (29.8 MB)
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
- PocketShell-v0.10.0-m6.0.2-source.zip sha256 7b53c731cc112781c8ee6bbc68263cc0745560d553a066f28e99308529bd5953  (54 MB uncompressed, 382 files)
- PocketShell-v0.10.0-m6.0.2-source.tar.gz sha256 01afd3b48a6108dcf7c230071797b70f3e1bc85e8588731ec3e34524466bdeb7
- pocketshell-m2.gitbundle           sha256 c116ca76822e2347019148a0c72803f9e571c4d1247897e360bc82fc2c1b7cd5  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/RENDER-RESET-M4.0.9.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 6717f981a50a1733ff7c4f089db380ffd107296a7e29458732e916380d5963a7  (705 KB — suite v2.1: self-locating runner (flat + bin/ layouts), suite version in header, PREFLIGHT with app-version stamp + status file + ls/readlink loader evidence + SKIP-with-fix-path + POCKETSHELL_INSTALL_LAYER=1 repair hatch, plus the cross-compiled test binaries; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11  (6.5 MB — the glibc layer artifact, transparency copy; the APK ships the identical bytes decompressed (sha 5be400dd…, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 913fd2e37a478236f1ff2e21d102bc11d482cad3dfca601bda985377acb652ea  (34 pages — the read-only platform/runtime forensic audit; kept downloadable as the phase baseline; regenerated from the committed generator script after the sandbox reset — content identical, PDF timestamps differ)

All served on :3000 from public/ (same bytes, HTTP-verified; the release
mirror now also extracts and sha-verifies the APK's embedded layer asset —
the m6.0.2 release-checklist lesson).
Older builds: withdrawn (v0.10.0-m6.0.1 superseded by m6.0.2 after one day —
the install-path fix; layer bytes unchanged; its record lives in the bundle
history — see docs/CHANGELOG for each confirmed fix).
