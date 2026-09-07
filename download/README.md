# download/ — delivery masters

Current: **v0.11.0-m7.0.0-m7.1p3** (M7.1 Phase 3 — live external-keyboard
detection + automatic on-screen keyboard control; versionCode 45 /
versionName 0.11.0-m7.0.0 — P3 deliberately does NOT bump the version, the
P1/P2/P2.x precedent stands).

CONTINUITY NOTE: carried — the sandbox toolchain was re-provisioned across
the reset epochs (Adoptium JDK 21.0.12.1+1 pinned at /home/z/tools, SDK,
git history, and the upload/ insurance set intact). This P3 set was cut
from the phase tip 93ee631 on that toolchain. The M7.1 P2.2 APK
(7e0e99a9…) is SUPERSEDED by this build (same version stamp, new bytes)
and no longer served; its history rides in the bundle.

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk  sha256 46fb0d8b3905f2e96c281e38a3805c74a0716fd4618261268b9e5ecae0477989  (30,448,845 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over M7.1 P2.2 / P2.1 / P2 / P1 / the M7.0 release
  (same stamp) and every previous pinned-cert build (vc16..45, same cert
  d96a6f66…8bf659). App data survives. Semantic pins verified on these
  exact bytes: version (aapt2 badging), the UNCHANGED 6-permission set
  (zero new permissions — the P3 notice needs none), embedded rev=2 layer
  asset sha 898131ff… / 17,920,000 B, dex carries the ExternalKeyboard
  symbols (ExternalKeyboardViewModel / ExternalKeyboardDetector /
  ExternalKeyboardPolicy / ExternalKeyboardNoticeBar present) plus the
  P2.2 + P2.1 + P2 + P1 launcher symbols (LauncherScroller / ScrollDots /
  isLight present; PackagesFooterLink absent), all 26 launcher icon
  entries packaged (13 dark + 13 light), ZERO stale 'aider'/'Aider'
  strings in the dex.
  NEW in this build (M7.1 P3):
    - LIVE EXTERNAL-KEYBOARD DETECTION: event-driven (no polling, no new
      permissions) — InputManager.InputDeviceListener → a 400 ms
      stability window (duplicate connect bursts and Bluetooth flaps
      coalesce; a sub-window flap never flickers the state) → one fresh
      device scan → at most ONE state transition. A launch scan covers
      starting with the keyboard already attached; an ON_RESUME rescan
      covers backgrounded connects. The predicate requires SOURCE_KEYBOARD
      + KEYBOARD_TYPE_ALPHABETIC + non-virtual — touchscreens, mice,
      gamepads, stylus pointers, and button clusters never trigger it.
    - AUTOMATIC ON-SCREEN KEYBOARD CONTROL: external keyboard connects →
      the shared deck hides and the hardware keyboard types straight into
      the terminal/WebView; disconnects → the deck returns exactly as the
      user left it (the manual state is overlaid, never destroyed; an
      explicit reopen cancels the suppression and the user wins). The
      keyboard bottom inset recalculates on every screen through the
      existing deck mount/unmount path (the M7.0 Files fix intact).
    - SETTINGS: "On-screen keyboard — automatically hide when an external
      keyboard is connected" (the ONE DataStore settings repository, key
      auto_hide_keyboard_on_external, default ON, immediately effective).
    - NOTICE: ONE transient in-app banner per real connect transition
      ("External keyboard detected — the on-screen keyboard has been
      turned off. You can change this in Settings."), auto-dismisses in
      ~4.5 s, tappable to Settings — no notification permission, no
      channel, no spam on duplicates or resume.
    - Carried from P2.2/P2.1/P2: theme-variant icon pairs, x-scroll home
      rows + scroll dots, the tools-header packages affordance, Aider
      absent with no stale trace, the nine owner-supplied marks, ONE
      shared LauncherSettingRow, imported icon → bundled mark →
      deterministic badge, Antigravity (agy) with the honest
      verify-then-launch probe.

- PocketShell-v0.11.0-m7.0.0-m7p3-source.zip  sha256 d71cc7a2743340dfa059af76eecd559089125c53a96129816279ed2f8ae19bdb  (50,141,048 B — tracked source cut at the M7.1 P3 tip 93ee631 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1-p3 git bundle embedded)
- PocketShell-v0.11.0-m7.0.0-m7p3-source.tar.gz  sha256 8bc28a9273e03c918723224e1cb07bae85373b490d39e6dae893db28f5193d8d  (49,875,467 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1-p3.gitbundle  sha256 8b0544b5fe1b6c2231f7ca5b2f67679a6ae821c85399a1ba05cdd5489693ea9c  (37,510,759 B — full history M0 → main tip 93ee631, the EXACT app tip this APK was built from; continues the P2.2 bundle through the P2.1 + P2 + P1 + M7.0 chains; bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1 P3 build): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…). Their content is fully contained in this delivery (the same launcher model with the external-keyboard system on top; history in the bundle).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to the earlier sandbox reset, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
