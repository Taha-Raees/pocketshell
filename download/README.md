# download/ — delivery masters

Current: **v0.11.0-m7.0.0** (the M7.0 RELEASE — all of M7 phases 1–8.1 plus
the Phase 9 integration: scrollable search results, long-press result
actions, one close behavior; versionCode 45 / versionName 0.11.0-m7.0.0).

RECOVERY NOTE: a sandbox reset rolled the project back to the m6.0.3 era and
destroyed the original m7p8 build and every previously served artifact (the
m7p7.1/m7p6/m6.0.4 APKs and source cuts). The git history was restored from
the user-supplied P7.1 delivery bundle (sha 0f8fd0a3… == the P7.1 pin), the
P8 content was recovered from the platform snapshot, re-gated end-to-end,
re-committed (51cd18b), extended with Phase 8.1 (e7f2630), then Phase 9 +
the release bump (47bed42 + 709d126), and the whole set below was cut from
that release tip. The glibc layer artifact survived byte-identical in-tree.
The lost artifacts cannot be reproduced byte-exact; their history rides in
the bundle. The m7p8.1 artifact set (vc44) is SUPERSEDED by this release
build and no longer served.

- PocketShell-v0.11.0-m7.0.0-debug.apk  sha256 8826d30dfc23dc6318e8306e5e17ea16a8500ea671217b6d24367afd4f53dd08  (30,164,873 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over every previous pinned-cert build (vc16..44, same
  cert d96a6f66…8bf659). App data survives. Semantic pins verified on these
  exact bytes: version (aapt2 badging), the UNCHANGED 6-permission set,
  embedded rev=2 layer asset sha 898131ff… / 17,920,000 B, dex carries the
  P9 + P8 + P8.1 + P7.1 + P6 symbols.
  NEW in this build (Phase 9 — the M7.0 release integration):
    - Search results scroll fully into view: the Files screen now ends
      above the shared keyboard deck (the Terminal/Editor inset rule it
      never applied) — the device-reported "results cannot be scrolled"
      symptom was the viewport extending behind the deck; short lists are
      fully visible, long lists scroll every row into view.
    - Long-press a search result → lands in its parent folder and opens
      the SAME action sheet as an explorer row (Open / Open Terminal Here
      on Linux folders / Copy / Move / Share / Export / Rename / Delete),
      resolved from the fresh listing and routed through the existing
      per-entry operations — no second operations engine.
    - One close behavior: the duplicated field-row close arrow is gone;
      the header X (or system Back) closes search; the in-field ✕ only
      clears the query; the search trigger itself is unchanged.

- PocketShell-v0.11.0-m7.0.0-source.zip  sha256 fc354ee3c82510d4fea3ae509edd8b7e8faf715d4eb0ea8a576b26482c7db065  (49,477,060 B — tracked source cut at the M7.0 release tip 709d126 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.0.0 git bundle embedded)
- PocketShell-v0.11.0-m7.0.0-source.tar.gz  sha256 a5a86f5fdee1cb2f3aae0d1aaeaec05005be04eee99d13454b24728f7ba9b173  (49,283,718 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.0.0.gitbundle  sha256 b1931f9c632cd36dc0e183fb2af91a91ad1f3616598d7a3c4823b96aaba1f160  (37,150,738 B — full history M0 → main tip 709d126, the EXACT app tip the M7.0 APK was built from; continues the user-restored P7.1 bundle; bundle pack bytes are not re-cut-stable, so all three pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by the M7.0 release): PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk (f316ec67…), m7p8.1 source.zip/tar.gz (5f1f3eb2…/788970c0…), pocketshell-m7p81.gitbundle (1068ed17…). Their content is fully contained in this release (same features + the P9 fixes; history in the bundle).

Withdrawn (lost to the sandbox reset, NOT re-servable): m7p7.1 APK
(189deebb…), m7p7.1 source.zip/tar.gz (200c4e2a…/f12df8c5…),
pocketshell-m7p71.gitbundle (0f8fd0a3… — the user's copy of this bundle IS
the recovery source), m7p6 APK (35ae7a48…), m6.0.4 APK (e633ca3c…) and its
source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut
from the bundle sources at the next M6 runtime gate), the forensic audit
PDF.
