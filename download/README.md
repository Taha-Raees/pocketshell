# download/ — delivery masters

Current: **v0.11.2-m7.1.1-m72p1** (M7.2 P1 — the notification foundation;
the FIRST M7.2 production code, infrastructure only per the approved P0
audit; phase build on the inherited versionCode 47 / versionName
0.11.2-m7.1.1 stamp per the M7.x phase-build precedent — the -m72p1 suffix
is filename-only; the M7.1.1 fix release v0.11.2-m7.1.1 remains the frozen
fix-record below it in history).

CUT NOTE: this set was cut fresh from the P1 record tip a7c635e on the
re-provisioned toolchain (Temurin 21.0.12.1+1, cmdline-tools 11076708,
platform-36, build-tools 36.0.0, NDK 28.2.13676358 — the sandbox reset
required the new scripts/provision_toolchain_m72.sh recipe). The M7.1.1
FIX set is SUPERSEDED by this phase build (same stamp, same cert, the
keyboard system unchanged — the P1 build carries the notification
foundation on top) and is withdrawn from the serving surface — its
insurance copies SURVIVE in upload/ byte-exact (APK 5d876141…, zip
d8c1ef85…, tar.gz d183c3eb…, bundle af73c57b…), and its content and
history ride in this bundle (the fix tip 3cbfec2 is a direct ancestor of
a7c635e, as is the M7.2 P0 audit commit c8d0059/024cd28).

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk  sha256 e63fb9b539f4b786307f6597b3a54427c7b8b63bedd1a081f50880719d18bf5b  (30,473,480 B, versionCode 47 / 0.11.2-m7.1.1 — the inherited phase stamp)
  Installs IN PLACE over the M7.1.1 fix (vc47 — same versionCode, updated
  content, same pinned cert), the M7.1 release (vc46), every M7.1 phase
  build (vc45), the M7.0 release, and every previous pinned-cert build
  (vc16..47, same cert d96a6f66…8bf659). App data survives; the
  notifications DataStore starts empty (the first session after the
  update triggers the one-time Android 13+ permission dialog). Semantic
  pins verified on these exact bytes: version (aapt2 badging
  versionCode='47' versionName='0.11.2-m7.1.1', targetSdk 28), the
  UNCHANGED 6-permission merged set (INTERNET, ACCESS_NETWORK_STATE,
  FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS,
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION), embedded rev=2 layer asset
  sha 898131ff… / 17,920,000 B, dex carries the seven notifications
  symbols (NotificationCoordinator / NotificationIds /
  NotificationPermissionGateKt / NotificationPermissionPolicy /
  NotificationPreferences(+Kt) / NotificationRoute) beside the M7.1.1
  keyboard symbols and the launcher phases' symbols, all 26 launcher icon
  entries packaged (13 dark + 13 light).
  Verification executed at this tip (docs/TESTING.md §48 + the honest
  JVM-vs-device split recorded there): FULL JVM suite forced
  --rerun-tasks 780/780 green (app 635 = 601 + 34 new notifications
  tests + terminal-emulator 145, 0 failures / 0 errors). What P1 adds:
    - POST_NOTIFICATIONS requested exactly once per install on Android
      13+ (first-session trigger; the request flag persisted BEFORE the
      dialog — recreation/process death can never re-arm; system-prompt
      denials respected; denial never blocks terminal functionality).
    - NotificationCoordinator: the ONE output/integration layer
      (session_events channel beside the untouched terminal_sessions FGS
      channel; deterministic EVENT_BASE+sessionId ids refusing silent
      wraparound; FLAG_IMMUTABLE content intents with the routing extra;
      the DataStore posted-id ledger).
    - The startup stale-notification sweep: cancels exactly the
      coordinator-owned event notifications that outlived the process —
      never the FGS notification (id 1) or anything else.
    - Tap routing on BOTH activity paths: cold start (onCreate) and the
      previously-unhandled singleTask onNewIntent, through ONE exhaustive
      NotificationRoute handler.
    - HONEST SCOPE: no production event notifications posted yet, no
      agent detection, no waiting-for-input heuristics, no /proc
      scanning, no OSC 133. The real-device gate is §48 (12 steps).

- PocketShell-v0.11.2-m7.1.1-m72p1-source.zip  sha256 b4c9eb53d66e847a751d447c9c3dbc223a7f0b756f096e71965b998e270f8eb1  (50,362,905 B — tracked source cut at the M7.2 P1 record tip a7c635e via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.2-p1 git bundle embedded)
- PocketShell-v0.11.2-m7.1.1-m72p1-source.tar.gz  sha256 147441d49354021cd261e57ac7d6098b7a127f0e96bd7a35731dc53d9cdf659b  (50,072,958 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.2-p1.gitbundle  sha256 23c9117e4586e034abe59673ea90566982a171fe7568c0f004305500b4a722f9  (37,643,015 B — full history M0 → main tip a7c635e, the P1 record tip (implementation c6ced97 → 0ed5da0 → docs fd54aa0 → worklog a7c635e) that the source archives are cut at; the APK was built from the identical app sources (the app tree is unchanged since 0ed5da0); bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.2 P1 phase build; withdrawn from the serving surface — insurance copies survive byte-exact in upload/, history in the bundle): PocketShell-v0.11.2-m7.1.1-debug.apk (5d876141…, 30,451,377 B, the M7.1.1 FIX RELEASE APK — stays the frozen fix-record), the m7.1.1 source.zip/tar.gz (d8c1ef85…/d183c3eb…), pocketshell-m7.1.1.gitbundle (af73c57b…). Also withdrawn: pocketshell-m7.2-p0.gitbundle (17475b7f…, the M7.2 P0 docs-only audit bundle — its entire content is contained in this bundle's history; the audit itself is docs/M7.2-P0-AUDIT.md at c8d0059).

Withdrawn (superseded by the M7.1.1 fix, stands): PocketShell-v0.11.1-m7.1.0-debug.apk (2d298c85…, 30,448,845 B, the OFFICIAL M7.1 RELEASE APK — stays the frozen milestone record), the m7.1 source.zip/tar.gz (0b4e8843…/d00d076f…), pocketshell-m7.1.gitbundle (7f677222…).

Withdrawn (superseded by the M7.1 release, stands): PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (46fb0d8b…, 30,448,845 B, the P3 external-keyboard APK), the m7p3 source.zip/tar.gz (d71cc7a2…/8bc28a92…), pocketshell-m7.1-p3.gitbundle (8b0544b5…).

Withdrawn (superseded by P3, stands): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to earlier sandbox resets, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
