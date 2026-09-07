# download/ — delivery masters

Current: **v0.11.0-m7.0.0-m7.1p2.1** (M7.1 Phase 2.1 — Aider removed from
the curated launcher set + nine owner-supplied official brand marks;
versionCode 45 / versionName 0.11.0-m7.0.0 — P2.1 deliberately does NOT
bump the version, the P1/P2 precedent stands).

CONTINUITY NOTE: this sandbox lost the JDK to another reset before the
phase (only the JRE survived); the Android SDK at ~/android-sdk and the
full git history survived. Temurin JDK 17 was re-provisioned from the
Adoptium archive, the full gate re-ran green, and this set was cut from
the new phase tip 1b15bde. The M7.1 P1 APK (4d7349f7…) is SUPERSEDED by
this build (same version stamp, new bytes) and no longer served; its
history rides in the bundle.

RESTORE NOTE (post-delivery reset): a later sandbox reset wiped public/
and the download/ binaries. The upload/ insurance copies restored the
APK, git bundle, and glibc artifact BYTE-IDENTICALLY (same pins, verified
5/5 against this page before serving). The source zip/tar.gz are re-cut
bytes from the SAME pinned tip 1b15bde around the SAME delivered bundle
(archive bytes are not re-cut-stable across sandbox toolchain builds, as
this file always disclosed) — their pins below are the re-cut values, and
fresh zip/tar.gz insurance copies now ride in upload/ so any future reset
restores the whole set byte-identically.

- PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk  sha256 97c04120ec56af2761c7923bbcd699cd96c8a9f17ccf7990aff4507d33a4a066  (30,342,970 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over M7.1 P2 / P1 / the M7.0 release (same stamp) and
  every previous pinned-cert build (vc16..45, same cert d96a6f66…8bf659).
  App data survives. Semantic pins verified on these exact bytes: version
  (aapt2 badging), the UNCHANGED 6-permission set, embedded rev=2 layer
  asset sha 898131ff… / 17,920,000 B, dex carries the M7.1 P2.1 + P2 + P1 +
  P9..P6 symbols, all 13 launcher icon entries packaged, ZERO stale
  'aider'/'Aider' strings in the dex.
  NEW in this build (M7.1 P2.1):
    - Aider REMOVED from the curated default CLI launcher set: registry,
      ids, commands, display names, and the bundled-asset mapping, with no
      stale trace anywhere (the Gemini CLI removal pattern; pinned).
    - Nine official marks re-rendered from OWNER-SUPPLIED official brand
      SVGs vendored in-tree (scripts/icon_sources/*.svg, zonalogo.com
      mirrors — offline, byte-reproducible): ChatGPT white knot on the
      OpenAI-black tile, Claude terracotta starburst, Z.ai tile, GitHub
      white octocat on the GitHub-dark tile, Hermes mascot on a white
      plate, OpenCode's own dark tile glyph, Kilo Code pixel letters on
      white (the vector ships NO fill), Cline robot head on white,
      Antigravity colored arc. Claude Code / ZCode / Codex / Qwen Code
      keep their P2 marks — 13 curated launchers total.
    - Carried from P2: the settings-row repair through ONE shared
      LauncherSettingRow (fixed icon → weight(1f) text column →
      intrinsically-sized action → fixed toggle); Restore is a compact
      text action; long URLs/commands wrap naturally; imported icon →
      bundled mark → deterministic badge; Antigravity (agy) seats in the
      curated defaults with the honest verify-then-launch probe.

- PocketShell-v0.11.0-m7.0.0-m7p2.1-source.zip  sha256 1bb4c41fc0f02373074c43f1397a194a1755f2b7b154453bef824105c0477231  (49,904,796 B — tracked source cut at the M7.1 P2.1 tip e0a2471 via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1-p2.1 git bundle embedded)
- PocketShell-v0.11.0-m7.0.0-m7p2.1-source.tar.gz  sha256 a38213b3cd0138cf0a6805b74ed17d1f444f5b1f808d319930da74e39f12e64b  (49,682,378 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.1-p2.1.gitbundle  sha256 fb0887b5b93a859f833164eff55f4fa305d6e0a3fd5eccb4c0fc889b40c7d35f  (37,402,389 B — full history M0 → main tip e0a2471, the EXACT app tip this APK was built from; continues the P2 bundle through the P1 + M7.0 chains; bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1 P2.1 build): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…). Their content is fully contained in this delivery (same launcher model with Aider removed and the marks refreshed; history in the bundle).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to the earlier sandbox reset, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
