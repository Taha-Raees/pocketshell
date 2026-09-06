# download/ — delivery masters

Current: v0.10.0-m6.0.3 (M6.0.3 — THE DOCTOR CORRECTNESS GATE. The m6.0.2
runtime PROVED itself on real hardware: device gate #3 = 24/24 ALL GREEN —
real Debian glibc 2.41 loader, Cline 3.0.61 end-to-end. What failed was the
DIAGNOSTIC TOOL: pocketshell-doctor v1 verdicted UNSUPPORTED for EVERY
versioned glibc binary — the real Cline binary needs only GLIBC_2.17 and the
layer provides 2.41 — because its version "comparison" concatenated the
required and installed versions and string-compared the concatenation against
the installed version alone (structurally always false), and its missing-lib
grep looked for "not found", which glibc never prints ("error while loading
shared libraries … cannot open shared object file", exit 127 — probed
empirically). The device suite masked it: the doctor row grepped "SUPPORTED"
UNANCHORED — UNSUPPORTED contains SUPPORTED. FIXED on three rails:
(1) doctor v2 — numeric component-wise semantic comparison, numeric-aware max
extraction over all GLIBC_x.y tokens (no sort -V dependence), exit-code-
authoritative loader resolution, full fact hierarchy before the verdict,
--selftest with a permanent 15-case regression matrix; (2) suite v2.2 — all
doctor rows anchor on ^Compatibility: SUPPORTED plus three permanent doctor
rows (selftest, musl classification, real-Cline verdict): 24 → 27 rows;
(3) layer rev=2 — the glibc files are BYTE-IDENTICAL to the proven rev=1
layer (proven at rebuild: exactly one tar member changed, the doctor script);
the marker revision makes every device re-extract the fixed doctor on its
next session prep. JVM suite 397 test cases / 0 failures (incl. the doctor
script selftest, real-fixture extraction, and the marker-rev re-extraction
regression). Payload pins are DETERMINISTIC: the cutter stages from the
pinned release tip (git f98360f) with zeroed mtimes — re-cuts are
byte-identical. M6.0's Universal Runtime Compatibility is unchanged: ONE
Alpine distribution running musl + glibc + static + Node tooling; REAL
Debian 13 trixie glibc 2.41 at the canonical multiarch paths; musl paths
disjoint and never touched.

- PocketShell-v0.10.0-m6.0.3-debug.apk  sha256 89704a14b16f477fa2d9b5e2a941f2c008a1f5a08fe0e101c970bdca0171d400  (29.8 MB, versionCode 43)
  Installs IN PLACE over v0.10.0-m6.0.2 (42), v0.10.0-m6.0.1 (41),
  v0.10.0-m6.0.0 (40), v0.9.1-m5.1.0 (39), v0.9.0-m5.0.1 (38),
  v0.9.0-m5.0.0 (37), v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35), v0.8.0-m4.1.0
  (34, unannounced intermediate), v0.7.0-m4.0.9 (33) and every earlier build
  (vc16..32) — same pinned cert d96a6f66…8bf659. App data (Alpine runtime,
  Kilo, Hermes, packages, Companion logins, theme choice) survives; on the
  next session spawn the layer marker flips to rev=2 and the fixed doctor
  re-extracts (~18 MB, one time) — the glibc files themselves are
  byte-identical, no reinstall, no wipe, no user action.
  Device gate: docs/TESTING.md §33B with the FRESH v2.2 suite tarball (delete
  any older suite copy — the header must read `suite: v2.2 (m6.0.3)`).
- PocketShell-v0.10.0-m6.0.3-source.zip sha256 bc176239b40ba24287d7ac0b9c7710a595029ad9534bfb996d57219860e72334  (the tracked source at release tip f98360f — zero dotfiles, zero web scaffold, zero build junk)
- PocketShell-v0.10.0-m6.0.3-source.tar.gz sha256 9b429c6ea2e726ffd4744bec7dfb51e6b7f30039033267e96752bde7835409f6
- pocketshell-m2.gitbundle           sha256 d3e135a1f96e04b67c3625125b8d8b3f678cd6999c3a88fdca853186ae4ce529  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/RENDER-RESET-M4.0.9.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 beb3ad5e2ec4caf905a9d24644a8d4d20764cb7f804da05d61c396a8ce916506  (708 KB — suite v2.2: self-locating runner, suite version in header, PREFLIGHT with app-version stamp + status file + loader evidence + SKIP-with-fix-path + POCKETSHELL_INSTALL_LAYER=1 repair hatch (writes the rev=2 marker), anchored doctor verdict rows + the three permanent doctor rows, plus the cross-compiled test binaries; usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6.5 MB — the glibc layer artifact rev=2, transparency copy; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself; glibc files byte-identical to the rev=1 layer — only usr/local/bin/pocketshell-doctor changed)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 9cb3ccb0fcb0f0d6736226148b2c33aa2664c8c571cedd304c4a5087a97e56a6  (34 pages — the read-only platform/runtime forensic audit; kept downloadable as the phase baseline; regenerated with fixed metadata dates — sha-stable)

All served on :3000 from public/ (same bytes, HTTP-verified; the release
mirror also extracts and sha-verifies the APK's embedded layer asset —
the m6.0.2 release-checklist lesson, kept mandatory).
Older builds: withdrawn (v0.10.0-m6.0.2 superseded by m6.0.3 the same day —
the doctor correctness gate; glibc files byte-identical; its record lives in
the bundle history — see docs/CHANGELOG for each confirmed fix).
