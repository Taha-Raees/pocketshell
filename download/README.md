# download/ — delivery masters

Current: **v0.11.0-m7.0.0-m7.1p2.2** (M7.1 Phase 2.2 — theme-scheme icon
variants + x-scroll home rows + the packages affordance aligned into the
tools header; versionCode 45 / versionName 0.11.0-m7.0.0 — P2.2
deliberately does NOT bump the version, the P1/P2/P2.1 precedent stands).

CONTINUITY NOTE: carried from P2.1 — the sandbox JDK was lost to a reset
before that phase and re-provisioned from the Adoptium archive; the SDK,
git history, and the upload/ insurance set survived. This P2.2 set was
cut from the new phase tip 6004805 on the same toolchain. The M7.1 P2.1
APK (97c04120…) is SUPERSEDED by this build (same version stamp, new
bytes) and no longer served; its history rides in the bundle.

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk  sha256 7e0e99a92a3234be11308ce487ee82199a3fef7d9276eee13cab107c2567b6c1  (30,664,394 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over M7.1 P2.1 / P2 / P1 / the M7.0 release (same
  stamp) and every previous pinned-cert build (vc16..45, same cert
  d96a6f66…8bf659). App data survives. Semantic pins verified on these
  exact bytes: version (aapt2 badging), the UNCHANGED 6-permission set,
  embedded rev=2 layer asset sha 898131ff… / 17,920,000 B, dex carries
  the M7.1 P2.2 + P2.1 + P2 + P1 + P9..P6 symbols (LauncherScroller /
  ScrollDots / isLight present; PackagesFooterLink absent), all 26
  launcher icon entries packaged (13 dark + 13 light), ZERO stale
  'aider'/'Aider' strings in the dex.
  NEW in this build (M7.1 P2.2):
    - ICON COLORS FOLLOW THE THEME SCHEME: every curated mark ships a
      two-variant pair ({id}.webp Midnight, {id}-light.webp Daylight)
      rendered by the offline pipeline on the app's OWN plate tones
      (TerminalTheme.keyAlt #16233F dark / #EDF1F7 light — the same
      surface the badge tiles paint). Monochrome glyphs invert per
      theme (Hermes mascot, Kilo letters, Cline robot white-on-dark —
      the black marks vanished on Midnight; ChatGPT knot, GitHub
      octocat #24292F, Codex flower adapt with their own brand inks);
      self-contained brand tiles (Z.ai, OpenCode) and colored
      transparent marks (Claude terracotta, Antigravity, Claude Code,
      Qwen) are theme-proof. LauncherTileIcon resolves the variant from
      TerminalTheme.isLight and re-resolves on a live theme flip;
      imported copies outrank everything and do not flip.
    - X-SCROLL HOME ROWS: Companions in ONE horizontal row, Your tools
      in TWO (row-major reading order preserved), fixed entry width,
      shared LauncherScroller + ScrollDots (the accent pill tracks the
      visible page; dots hide on a single page).
    - PACKAGES AFFORDANCE: the mid-page footer link is RETIRED — the
      "Your tools" header's Manage action opens the packages page (the
      divider above it is the requested alignment); Companions keep
      their launcher-settings Manage.
    - Carried from P2.1/P2: Aider absent with no stale trace, the nine
      owner-supplied marks, ONE shared LauncherSettingRow, imported
      icon → bundled mark → deterministic badge, Antigravity (agy) with
      the honest verify-then-launch probe.

- PocketShell-v0.11.0-m7.0.0-m7p2.2-source.zip  sha256 0e2b9d3d5932ebf415b989b8594c9fa9f2b3dc3cbb2e5cb8d3b8750d164e45c4  (50,086,168 B — tracked source cut at the M7.1 P2.2 tip 6004805 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1-p2.2 git bundle embedded)
- PocketShell-v0.11.0-m7.0.0-m7p2.2-source.tar.gz  sha256 a45a0f02d4d46a2bd1a3d03783f7dcc8bbdba7498822ceeeebf431050370add4  (49,829,695 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1-p2.2.gitbundle  sha256 1e8822fc280edca4395ff93a9e580cc579cf8b898a1cbdb143b12aaa4e9bcf27  (37,479,158 B — full history M0 → main tip 6004805, the EXACT app tip this APK was built from; continues the P2.1 bundle through the P2 + P1 + M7.0 chains; bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1 P2.2 build): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…). Their content is fully contained in this delivery (same launcher model with the theme-variant marks, the x-scroll rows, and the realigned affordance on top; history in the bundle).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to the earlier sandbox reset, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
