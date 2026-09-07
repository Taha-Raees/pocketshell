# download/ — delivery masters

Current: **v0.11.0-m7.0.0-m7.1p1** (M7.1 Phase 1 — Home launchers:
companion + CLI tool launcher grids, hide/restore, custom tools,
deterministic text badges; versionCode 45 / versionName 0.11.0-m7.0.0 —
P1 deliberately does NOT bump the version).

CONTINUITY NOTE: the toolchain (JDK/SDK) and the upload/ insurance copies
were lost to another sandbox reset before this phase; the git history
survived intact in the repo (through the platform snapshot commit
993bc0f). The toolchain was rebuilt from the staged recipe
(scripts/install_toolchain_staged.sh), the full gate re-ran green, and
this set was cut from the new phase tip 3abb2e8. The M7.0 release APK
(8826d30d…) is SUPERSEDED by this build (same version stamp, new bytes)
and no longer served; its history rides in the bundle.

- PocketShell-v0.11.0-m7.0.0-m7p1-debug.apk  sha256 4d7349f7c16c31802891441701d4c8f5fd3ed4a0d744894e8f91c9bc9b962298  (30,260,409 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over the M7.0 release (same stamp) and every previous
  pinned-cert build (vc16..45, same cert d96a6f66…8bf659). App data
  survives. Semantic pins verified on these exact bytes: version (aapt2
  badging), the UNCHANGED 6-permission set, embedded rev=2 layer asset
  sha 898131ff… / 17,920,000 B, dex carries the M7.1 + P9 + P8 + P8.1 +
  P7.1 + P6 symbols.
  NEW in this build (M7.1 P1 — a launcher is not an app):
    - Companions on Home: ChatGPT / Claude / Z.ai / GitHub preinstalled
      (seeded once through the existing companion storage with fixed
      ids); tapping raises the EXISTING companion sheet on the SAME web
      canvas — no APIs, no SDKs, no Android-app integration, the frozen
      companion package untouched (ZERO file changes).
    - Your tools: the built-in CLI launchers (registry + the NEW Cline
      entry) + user-defined custom tools; tiles are NOT install claims —
      the tap-time verify-then-launch path (real guest-shell probe →
      real Linux session, or an honest refusal banner) is the honesty
      mechanism; the old Home-visible probe pre-pass is retired.
    - Custom tools: Name + Command (+ optional icon) — user
      configuration carried verbatim through the SAME launch chain
      (guestCustomCommandChain, the sibling of guestLaunchChain); ONE
      spawn core (launchGuestCommand) shared with the registry path.
    - Remove from Home: long-press → confirm → HIDE-only; restorable in
      Settings → Home launchers; deleting a built-in companion stays
      possible in the existing Companion screen with a fixed-id restore.
    - Icons: system-picker images COPIED into app storage (referenced by
      stored filename, never the picker URI); deterministic letter
      badges with greedy collision resolution (C/Cl/Z/G; K/C/H/Cl/Co);
      every icon failure degrades to the badge.

- PocketShell-v0.11.0-m7.0.0-m7p1-source.zip  sha256 3bdd755fb0e3cf268edc3e259ee0fbadad0f85aa34320ef963daa87c57d2ffbd  (49,554,661 B — tracked source cut at the M7.1 P1 tip 3abb2e8 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1-p1 git bundle embedded)
- PocketShell-v0.11.0-m7.0.0-m7p1-source.tar.gz  sha256 f6bad9aacc3790eeafdc98f5778fefc338d9337e990023058b2ac66b5c58d540  (49,346,974 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1-p1.gitbundle  sha256 98a80b0c3d765bca5898255fc9b78ed9a1d0db83f934c204758112e8e19e3d94  (37,192,930 B — full history M0 → main tip 3abb2e8, the EXACT app tip this APK was built from; continues the user-restored P7.1 bundle through the M7.0 release chain; bundle pack bytes are not re-cut-stable, so all three pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1 P1 build): PocketShell-v0.11.0-m7.0.0-debug.apk (8826d30d…, the M7.0 release APK), m7.0.0 source.zip/tar.gz (fc354ee3…/a5a86f5f…), pocketshell-m7.0.0.gitbundle (b1931f9c…). Their content is fully contained in this delivery (same features + the launcher model; history in the bundle).

Withdrawn (lost to the earlier sandbox reset, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
