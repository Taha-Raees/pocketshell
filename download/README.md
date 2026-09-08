# download/ — delivery masters

Current: **v0.11.1-m7.1.0** (the OFFICIAL M7.1 RELEASE — the five-phase
milestone closed and frozen; versionCode 46 / versionName 0.11.1-m7.1.0 per
the M5.1.0 sub-milestone precedent).

CONTINUITY NOTE: the sandbox was reset again before this release (toolchain
+ public/ + the download/ masters lost; git history AND the upload/
insurance set survived). The toolchain was re-provisioned to the exact pins
(Temurin 21.0.12.1+1, cmdline-tools 11076708, platform-36, build-tools
36.0.0, NDK 28.2.13676358 — scripts/provision_toolchain_m71.sh); the glibc
transparency artifact was restored byte-identically from the tracked
in-tree APK asset (sha matches the pin); the delivery set below was cut
fresh from the release tip 4e86e50 on that toolchain. The M7.1 P3 set is
SUPERSEDED by this release (the official stamp replaces the inherited
phase stamp) and is withdrawn from the serving surface — its insurance
copies SURVIVE in upload/ byte-exact (APK 46fb0d8b…, zip d71cc7a2…,
tar.gz 8bc28a92…, bundle 8b0544b5…, all re-verified against the published
pins), and its content and history ride in the release bundle (P3 tip
93ee631 is a direct ancestor of 4e86e50).

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.1-m7.1.0-debug.apk  sha256 2d298c85958f53db0497a2894b33fda70f60de8fe59ec5a14f725b146527eaa0  (30,448,845 B, versionCode 46 / 0.11.1-m7.1.0)
  Installs IN PLACE over every M7.1 phase build (vc45), the M7.0 release,
  and every previous pinned-cert build (vc16..45, same cert
  d96a6f66…8bf659). App data survives. Semantic pins verified on these
  exact bytes: version (aapt2 badging versionCode='46'
  versionName='0.11.1-m7.1.0'), the UNCHANGED 6-permission set (zero new
  permissions across ALL of M7.1), embedded rev=2 layer asset sha
  898131ff… / 17,920,000 B, dex carries the ExternalKeyboard symbols
  (ExternalKeyboardViewModel / ExternalKeyboardDetector /
  ExternalKeyboardPolicy / ExternalKeyboardNoticeBar present) plus the
  P2.2 + P2.1 + P2 + P1 launcher symbols (LauncherScroller / ScrollDots /
  isLight present; PackagesFooterLink absent), all 26 launcher icon
  entries packaged (13 dark + 13 light), ZERO stale 'aider'/'Aider'
  strings in the dex.
  Release verification executed at this stamp (docs/TESTING.md §46):
  FULL JVM suite forced --rerun-tasks 734/734 effective green (app 589 +
  terminal-emulator 145, 0 failures / 0 errors) — the M7.1 phases carry:
    - P1: Home launchers (Companions + Your tools grids, hide/restore,
      custom tools over the ONE verify-then-launch path, badges).
    - P2: the launcher UI repair at the shared weighted-row root cause,
      bundled official marks, Antigravity (agy) replacing Gemini CLI.
    - P2.1: Aider absent with no stale trace; nine owner-supplied marks.
    - P2.2: two-variant theme marks (26 assets, live flips), one x-scroll
      Companions row + two tools rows with scroll dots, the tools-header
      packages affordance.
    - P3: live external-keyboard detection (event-driven, 400 ms
      stability window, real alphabetic keyboards only, no polling, no
      new permissions), automatic deck suppression/restore through the
      ONE root visibility owner (manual state never destroyed; explicit
      reopen cancels), Settings "On-screen keyboard" toggle (default ON),
      ONE transient in-app notice per real transition.

- PocketShell-v0.11.1-m7.1.0-source.zip  sha256 0b4e8843f071e80d9296c0ce0792d04e5192a6f285d7e40215826d642cb44b35  (50,170,755 B — tracked source cut at the M7.1 RELEASE tip 4e86e50 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1 git bundle embedded)
- PocketShell-v0.11.1-m7.1.0-source.tar.gz  sha256 d00d076f2daaec38260e7fa2a5bc1f503a6801e3d37e7d00e46cb1c7f67f18c4  (49,906,882 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1.gitbundle  sha256 7f6772220d4944ac47f64e5bcb68c654f155bfe1fda2a984911d20e3dcc34eab  (37,532,338 B — full history M0 → main tip 4e86e50, the EXACT app tip this APK was built from; continues the P3 bundle through the release-closure commit; bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; restored byte-identically from the tracked in-tree APK asset after this reset; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this OFFICIAL M7.1 RELEASE; withdrawn from the serving surface — insurance copies survive byte-exact in upload/, history in the bundle): PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (46fb0d8b…, 30,448,845 B, the P3 external-keyboard APK), the m7p3 source.zip/tar.gz (d71cc7a2…/8bc28a92…), pocketshell-m7.1-p3.gitbundle (8b0544b5…).

Withdrawn (superseded by P3, stands): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to earlier sandbox resets, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
