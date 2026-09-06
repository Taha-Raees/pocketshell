# download/ — delivery masters

Current: v0.10.0-m6.0.4 (M6 PHASE-C ADVERSARIAL CLOSURE AUDIT. The M6.0.3
device gate scored 27/27 ALL GREEN — real Debian glibc 2.41, Cline 3.0.61,
doctor v2 correct on device. Per the Phase-C mandate the phase was then
audited adversarially BEFORE closure: corruption/self-healing, concurrency,
loader ownership vs the gcompat package, extractor security, environment
contamination, doctor prediction accuracy, lifecycle timing. Three real
engineering issues were found and fixed, all regression-pinned, none touching
the proven runtime architecture: (1) STRUCTURAL INTEGRITY PROBE — the layer
fast path trusted only the marker text; Alpine's gcompat package provably
owns /lib/ld-linux-aarch64.so.1 and ships a real ELF shim there (verified
from the actual 1.1.0-r4 package bytes), so apk fix/reinstall/upgrade gcompat
could reclaim the loader behind a valid marker — the fast path now also
verifies the loader symlink resolves to the canonical Debian loader and the
load-bearing files exist, and self-heals by re-extraction; (2) SYMLINK-SAFE
RE-EXTRACTION — the layer ships lib/aarch64-linux-gnu ->
../usr/lib/aarch64-linux-gnu BEFORE the files it points to and the old
symlink replacement followed directory symlinks, so every in-place
re-extraction first wiped the whole multiarch directory; symlink nodes are
now replaced as nodes, all recursive deletes are NOFOLLOW, and archive
entries routed through earlier symlink entries are refused fail-closed;
(3) SESSION PREP OFF THE UI THREAD — heavy guest prep (18 MB asset + sha +
re-extraction path) moved to Dispatchers.IO, PTY spawn stays on main, and
ensureInstalled is single-flight so concurrent sessions serialize instead of
racing. NEW PERMANENT TOOLING: adversarial_closure_audit.sh (probe /
drill-c2 / drill-c4 / drill-c5 / heal) rides the tests tarball — corruption
drills, the gcompat loader-reclaim drill, apk-ops survival, doctor
prediction accuracy vs reality, environment + filesystem ownership maps,
fast-path timing, and post-session self-heal verification. The glibc layer
artifact is BYTE-IDENTICAL to the 27/27 gate (ed82daa8…) — m6.0.4 is
APP-side only. JVM suite 397 → 406 leaf cases / 0 failures (incl. the
built-APK asset pin against this exact APK). Payload pins are DETERMINISTIC:
the cutter stages from the pinned release tip (git a7441ff) with zeroed
mtimes.

- PocketShell-v0.10.0-m6.0.4-debug.apk  sha256 e633ca3cef54434474c58648a489329c875ba1ab1ffcf6d15b77a4e1c2529750  (29.8 MB, versionCode 44)
  Installs IN PLACE over v0.10.0-m6.0.3 (43), v0.10.0-m6.0.2 (42),
  v0.10.0-m6.0.1 (41), v0.10.0-m6.0.0 (40), v0.9.1-m5.1.0 (39),
  v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37), v0.8.0-m4.0.12 (36),
  v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0 (34, unannounced intermediate),
  v0.7.0-m4.0.9 (33) and every earlier build (vc16..32) — same pinned cert
  d96a6f66…8bf659. App data (Alpine runtime, Cline, packages, Companion
  logins, theme choice) survives; this update is APP-side only — the layer
  marker and glibc files stay byte-identical, the new integrity probe simply
  starts guarding them on the next session prep.
  Closure drills: docs/TESTING.md §33 + runtime-tests/adversarial_closure_audit.sh
  (drill-c2 → one new session → heal is the audited self-heal proof).
- PocketShell-v0.10.0-m6.0.4-source.zip sha256 04c055c18e1f1eaeb825db8cdd3e8cb8c01c2209dbd1c074c5dcd16affef2509  (the tracked source at release tip a7441ff — zero dotfiles, zero web scaffold, zero build junk)
- PocketShell-v0.10.0-m6.0.4-source.tar.gz sha256 655f2ece1618b0f7f57c8fc33462754921a801e5f04bcfc66fe986c45ade5524
- pocketshell-m2.gitbundle           sha256 4388da2f64a2240c80185b933cf2adca78a51559119c912592ec64496d141bf9  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 8392edab7ba42930c0e43a4390a355c90a56c6639edafd7e62ce789d941c63c0  (712 KB — suite v2.2 (27-row device suite, anchored doctor rows, repair hatch) PLUS the Phase-C adversarial drill script adversarial_closure_audit.sh: probe / drill-c2 / drill-c4 / drill-c5 / heal; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6.5 MB — the glibc layer artifact rev=2, UNCHANGED by m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 92015b7547b2e9b7dd6bd8888f5d641c8804a3e33cafaf8c069e811934af10ae  (33 pages — the read-only platform/runtime forensic audit; kept downloadable as the phase baseline; regenerated with fixed metadata dates from the committed generator after the sandbox reset)

All served on :3000 from public/ (same bytes, HTTP-verified; the release
mirror also extracts and sha-verifies the APK's embedded layer asset —
the m6.0.2 release-checklist lesson, kept mandatory).
Older builds: withdrawn (v0.10.0-m6.0.3 superseded by m6.0.4 the same day —
the Phase-C closure audit; app-side only; its record lives in the bundle
history — see docs/CHANGELOG for each confirmed fix).
