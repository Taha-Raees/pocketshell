# download/ — delivery masters

Current: **v0.10.0-m6.0.4-m7p8.1** (M7 Phase 8.1 — file search + multi-select,
one build carrying both, on top of the full M7 P1–P7.1 + M6.0.4 line).

RECOVERY NOTE: a sandbox reset rolled the project back to the m6.0.3 era and
destroyed the original m7p8 build and every previously served artifact (the
m7p7.1/m7p6/m6.0.4 APKs and source cuts). The git history was restored from
the user-supplied P7.1 delivery bundle (sha 0f8fd0a3… == the P7.1 pin), the
P8 content was recovered from the platform snapshot, re-gated end-to-end
(636/636), re-committed (51cd18b), extended with Phase 8.1 (e7f2630,
653/653), and the whole set below was cut from that restored tip. The glibc
layer artifact survived byte-identical in-tree. The lost artifacts cannot be
reproduced byte-exact; their history rides in the bundle.

- PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk  sha256 f316ec67ffa5a5ce6983caaea8644a7a5b4cefba9805aa78a225d693ae9bfaf2  (30,451,017 B, versionCode 44 / 0.10.0-m6.0.4)
  Installs IN PLACE over every previous pinned-cert build (vc16..44, same
  cert d96a6f66…8bf659). App data survives. Semantic pins re-verified on the
  rebuilt bytes: version, the UNCHANGED 6-permission set, embedded rev=2
  layer asset sha 898131ff… / 17,920,000 B, dex carries the P8 search +
  P8.1 multi-select + P7.1 terminal symbols.
  NEW in this build:
    - Phase 8 file search: header magnifier → focused field → literal
      case-insensitive NAME search of the SELECTED area only (Linux / shelf /
      SAF), symlinks matched but never followed, query is data, honest
      limits (200 matches / 2000 folders, skipped folders counted), results
      open the parent folder with a highlight.
    - Phase 8.1 multi-select: header check icon → selection mode → Copy /
      Move / Delete several entries through the unchanged per-entry engine,
      per-item Replace dialogs on collisions, honest aggregates, selection
      never survives leaving its folder.

- PocketShell-v0.10.0-m6.0.4-m7p8.1-source.zip  sha256 5f1f3eb2800f145a539019baf83c53192831a7f60759240e07eac07bfbc98401  (49,446,382 B — tracked source cut at M7 app tip e7f2630 via git archive, web shim excluded, zeroed mtimes; RESTORE.txt + the m7p81 git bundle embedded)
- PocketShell-v0.10.0-m6.0.4-m7p8.1-source.tar.gz  sha256 788970c0e0f176b2e3f25fe51fa53e12194bac930656b94002b3aca6ec202e7c  (49,255,759 B — same cut, sorted tar, gzip -n)
- pocketshell-m7p81.gitbundle  sha256 1068ed17d37da2095abed2ba050941995bc4e2c33fda6ef96d283760fa1a9f96  (37,125,915 B — full history M0 → main tip e7f2630, the EXACT app tip the M7P8.1 APK was built from; continues the user-restored P7.1 bundle; bundle pack bytes are not re-cut-stable, so all three pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (lost to the sandbox reset, NOT re-servable): m7p7.1 APK
(189deebb…), m7p7.1 source.zip/tar.gz (200c4e2a…/f12df8c5…),
pocketshell-m7p71.gitbundle (0f8fd0a3… — the user's copy of this bundle IS
the recovery source), m7p6 APK (35ae7a48…), m6.0.4 APK (e633ca3c…) and its
source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut
from the bundle sources at the next M6 runtime gate), the forensic audit
PDF.

Mirror discipline: public/ (served) and download/ (masters) hold the same
bytes; the mirror check extracts and sha-verifies the APK's embedded layer
asset. Bundle main tip == the APK build tip (e7f2630).
