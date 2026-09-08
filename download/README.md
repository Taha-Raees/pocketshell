# download/ — delivery masters

Current: **v0.11.2-m7.1.1** (the M7.1.1 FIX RELEASE — the external-keyboard
detection rebuilt after the real-device failure; versionCode 47 /
versionName 0.11.2-m7.1.1 per the sub-milestone precedent; the M7.1 release
v0.11.1-m7.1.0 remains the frozen milestone record below it in history).

CUT NOTE: this set was cut fresh from the fix tip 3cbfec2 on the pinned
toolchain (Temurin 21.0.12.1+1, cmdline-tools 11076708, platform-36,
build-tools 36.0.0, NDK 28.2.13676358). The M7.1 RELEASE set is SUPERSEDED
by this fix (the release stays frozen as the milestone record; its
external-keyboard behavior is what the §47 device gate rejected) and is
withdrawn from the serving surface — its insurance copies SURVIVE in
upload/ byte-exact (APK 2d298c85…, zip 0b4e8843…, tar.gz d00d076f…, bundle
7f677222…), and its content and history ride in the fix bundle (the
release tip 4e86e50 is a direct ancestor of 3cbfec2).

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.2-m7.1.1-debug.apk  sha256 5d8761418324950815588f170149342a6aea5fe90b963dfda3364e3d787e5dc1  (30,451,377 B, versionCode 47 / 0.11.2-m7.1.1)
  Installs IN PLACE over the M7.1 release (vc46), every M7.1 phase build
  (vc45), the M7.0 release, and every previous pinned-cert build
  (vc16..47, same cert d96a6f66…8bf659). App data survives. Semantic pins
  verified on these exact bytes: version (aapt2 badging versionCode='47'
  versionName='0.11.2-m7.1.1'), the UNCHANGED 6-permission set, embedded
  rev=2 layer asset sha 898131ff… / 17,920,000 B, dex carries
  ExternalKeyboardViewModel / ExternalKeyboardDetector /
  ExternalKeyboardNotice / ExternalKeyboardScanner /
  ExternalKeyboardVisibilityModel (the M7.1 Policy symbol retired with the
  policy) plus the P2.2 + P2.1 + P2 + P1 launcher symbols (LauncherScroller
  / ScrollDots / isLight present; PackagesFooterLink absent), all 26
  launcher icon entries packaged (13 dark + 13 light), ZERO stale
  'aider'/'Aider' strings in the dex.
  Fix verification executed at this stamp (docs/TESTING.md §47 + §47.2):
  FULL JVM suite forced --rerun-tasks 746/746 green (app 601 +
  terminal-emulator 145, 0 failures / 0 errors) — the rebuild carries:
    - ONE authoritative ExternalKeyboardVisibilityModel (persistent
      onscreen_keyboard_enabled preference + hardware state + the user's
      explicit request → shouldShowOnscreenKeyboard; the M7.1
      SUPPRESS/RESTORE memory policy and the root's local state retired).
    - The GATED terminal-canvas tap: with an external keyboard connected
      the tap never reopens the deck (the M7.1 real-device failure);
      without one the m4.0.12 tap-to-reopen is unchanged.
    - The 2,000 ms confirm deadline: an endless event storm (periodic BT
      onInputDeviceChanged re-announcements) can no longer starve the
      detection; sub-window flaps still never flicker.
    - The Application-level configuration-change cross-check: a SECOND,
      independent detection mechanism beside the InputDeviceListener.
    - BOTH-direction notices, once per transition (connect AND "External
      keyboard disconnected."), in-app only, zero new permissions.
    - The persistent On-screen keyboard preference: detection never writes
      it; a disconnect re-evaluates it; an OFF preference is never forced
      back on; the user's explicit [⌨] / deck actions always win.

- PocketShell-v0.11.2-m7.1.1-source.zip  sha256 d8c1ef85bb850c3ba6fad363912d559a2129b22f03ddbea59c6e64fa7034bd1b  (50,213,381 B — tracked source cut at the M7.1.1 FIX tip 3cbfec2 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1.1 git bundle embedded)
- PocketShell-v0.11.2-m7.1.1-source.tar.gz  sha256 d183c3eb4e7461ac5f5ec59204cf9d056bb502b32465e402221fbae01b53a954  (49,947,210 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1.1.gitbundle  sha256 af73c57b216206ce7e61acaf7bdf2ffe5dbf9b60b91772821d2dc26869a5632f  (37,565,865 B — full history M0 → main tip 3cbfec2, the EXACT app tip this APK was built from; continues the M7.1 release chain through the fix commit; bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1.1 FIX; withdrawn from the serving surface — insurance copies survive byte-exact in upload/, history in the bundle): PocketShell-v0.11.1-m7.1.0-debug.apk (2d298c85…, 30,448,845 B, the OFFICIAL M7.1 RELEASE APK — stays the frozen milestone record), the m7.1 source.zip/tar.gz (0b4e8843…/d00d076f…), pocketshell-m7.1.gitbundle (7f677222…).

Withdrawn (superseded by the M7.1 release, stands): PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (46fb0d8b…, 30,448,845 B, the P3 external-keyboard APK), the m7p3 source.zip/tar.gz (d71cc7a2…/8bc28a92…), pocketshell-m7.1-p3.gitbundle (8b0544b5…).

Withdrawn (superseded by P3, stands): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to earlier sandbox resets, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
