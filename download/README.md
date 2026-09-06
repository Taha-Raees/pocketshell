# download/ — delivery masters

Latest build: **M7 Phase 7 (Open Terminal Here)** — PocketShell-v0.10.0-m6.0.4-m7p7-debug.apk
sha256 a3ce9d3a03e8307ec3eca6893ee7b8fe96e6d0b2f4723a22e8a00b6347192cd8
(30.3 MB, versionName 0.10.0-m6.0.4 / versionCode 44 UNCHANGED per plan —
the version bump is deferred to P9 integration). Built from M7 tip d1fe8b6
(Phases 1–7 included: architecture audit, storage abstraction, explorer
foundation, file operations, Android storage bridge, quick text editor,
Open Terminal Here). Installs IN PLACE over the M6.0.4 / M7P6 build (same
vc44, same pinned cert d96a6f66…8bf659) — app data and the glibc layer
(ed82daa8…, unchanged) survive. New in M7P7: Linux directories in the Files
explorer gain a REAL "Open Terminal Here" action — a NORMAL Alpine session
whose guest working directory is the exact browsed directory (pwd prints it
literally), titled "Alpine Linux", created through the UNCHANGED canonical
session path (RuntimeProcessLauncher preflight → prepareLinuxSession on IO →
spawnLinuxSession on Main → navigation only after the session is actually
ready — never navigate-first). The directory travels as PTY argv through the
new guestTerminalChain = cd -- '<dir>' && exec /bin/sh -l: POSIX single-quote
wrapping ('→'\''), -- ends options, && so the interactive login shell follows
only a SUCCESSFUL cd — quoting is value-not-syntax, execution-proven through
real /bin/sh including a genuine injection attempt whose touch never runs.
Android areas (PocketShell Downloads, user-granted SAF folders) get NO fake
button — the action sheet shows the honest boundary note instead: "Android
folders are not Linux guest directories. Copy or move files into PocketShell
Linux to work with them in Terminal." Existing sessions are untouched (one
new session created, none closed, no cwd changes, no PTY writes). Linux-only
by design: no proot bind mounts, no fabricated POSIX paths for content://.
JVM suite 600/600 green (13 new pins incl. two /bin/sh execution fixtures).
Device checklist: docs/TESTING.md §37.

Previous: **M7 Phase 6 (quick text editor)** — PocketShell-v0.10.0-m6.0.4-m7p6-debug.apk
sha256 35ae7a488af89a3403823f78ecadeaacb5c6b2806f5a240d73320128e94fff37
(30.3 MB, same vc44, built from M7 tip 25f421f). The explorer's action sheet
gained "Open" on regular files across all three storage domains (PocketShell
Linux, the Downloads shelf, user-granted SAF folders); the editor is
byte-honest (strict UTF-8 or refusal — no silent U+FFFD corruption, BOM/CRLF
round-trip byte-exact, NUL binary sniff, 1 MiB cap with the real size
reported), concurrency-honest ((size, mtime) save gate + explicit
overwrite/recreate confirmations — a running guest shell can never be raced
silently), and input-honest (the ONE hardware keyboard deck, no IME). Dirty
back guard with an explicit Save/Discard decision; process death loses
unsaved content (disclosed in the guard dialog, no fake autosave). NOT a
mini IDE by design: no syntax highlighting, no line numbers, no
search-in-file, no undo history, no tabs. JVM suite 586/586 green. Device
checklist: docs/TESTING.md §35. Superseded by the M7P7 build above.

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
  Closure drills: docs/runtime/TESTING.md + runtime-tests/device_gate.sh — the
  ONE guided runner for the remaining device gate: gate → (new session) →
  resume-c2 → (new session) → resume-c4 → (new session) → resume-c5 (chains
  the final suite+probe); heal provenance proves the APP re-extracted.
- PocketShell-v0.10.0-m6.0.4-source.zip sha256 b0985c77d8c9e8100072b4f54d961c08aba0d79a02a75918cc5d5e8be753da12  (the tracked source at release tip ff4afa9 — zero dotfiles, zero web scaffold, zero build junk)
- PocketShell-v0.10.0-m6.0.4-source.tar.gz sha256 c46e585dc90663f5349f654940bbde1a6437acd4790f647d6405e1d3b7884595
- pocketshell-m2.gitbundle           sha256 9982485d3c2e93213f9ec815f53228d74efa091773fc3ba9a9dc00a1f62f3938  (full history; ~36M — complete milestone history, all design contracts, docs/PROCFS-CONTRACT.md, docs/runtime/ + runtime-tests/ + scripts/runtime/ — honest, no rewrites)
- pocketshell-runtime-tests-aarch64.tar.gz sha256 90009339d4baeae001f0417c4c926fb18256a8f4b96e62e3dbb074b55fe87ac5  (718 KB — suite v2.2 (27-row device suite, anchored doctor rows, repair hatch) PLUS the Phase-C adversarial drill script adversarial_closure_audit.sh (probe / drill-c2 / drill-c4 / drill-c5 / heal with production-heal provenance) and the guided closure runner device_gate.sh (gate / baseline / c2 / resume-c2 / c4 / resume-c4 / c5 / resume-c5 / final / status); usage in docs/runtime/TESTING.md)
- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6.5 MB — the glibc layer artifact rev=2, UNCHANGED by m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)
- PocketShell-Runtime-Forensic-Audit.pdf sha256 92015b7547b2e9b7dd6bd8888f5d641c8804a3e33cafaf8c069e811934af10ae  (33 pages — the read-only platform/runtime forensic audit; kept downloadable as the phase baseline; regenerated with fixed metadata dates from the committed generator after the sandbox reset)

All served on :3000 from public/ (same bytes, HTTP-verified; the release
mirror also extracts and sha-verifies the APK's embedded layer asset —
the m6.0.2 release-checklist lesson, kept mandatory).
Older builds: withdrawn (v0.10.0-m6.0.3 superseded by m6.0.4 the same day —
the Phase-C closure audit; app-side only; its record lives in the bundle
history — see docs/CHANGELOG for each confirmed fix).
