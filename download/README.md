# download/ — delivery masters

Current: **v0.11.0-m7.0.0-m7.1p2** (M7.1 Phase 2 — launcher UI repair +
official icons + Antigravity: the settings-row collapse root-caused and
fixed at the row level, 14 bundled official launcher marks offline,
Antigravity (`agy`) replaces Gemini CLI in the curated defaults;
versionCode 45 / versionName 0.11.0-m7.0.0 — P2 deliberately does NOT bump
the version, the P1 precedent stands).

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

- PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk  sha256 ae6f64458c18d9cffd380596a9c4525dc87db2e9c79b2813b413dff71f09c74f  (30,447,567 B, versionCode 45 / 0.11.0-m7.0.0)
  Installs IN PLACE over M7.1 P1 / the M7.0 release (same stamp) and every
  previous pinned-cert build (vc16..45, same cert d96a6f66…8bf659). App
  data survives. Semantic pins verified on these exact bytes: version
  (aapt2 badging), the UNCHANGED 6-permission set, embedded rev=2 layer
  asset sha 898131ff… / 17,920,000 B, dex carries the M7.1 P2 + P1 + P9 +
  P8 + P8.1 + P7.1 + P6 symbols, all 14 launcher icon entries packaged.
  NEW in this build (M7.1 P2):
    - Launcher settings rows repaired at the ROOT CAUSE: the screenshot's
      one-character-per-line collapse came from MidnightQuietButton (a
      page-level component that hard-fills its width) sitting in an
      unweighted Row slot and starving the weighted text column. ALL
      built-in rows now render through ONE shared LauncherSettingRow
      (fixed icon → weight(1f) text column → intrinsically-sized action →
      fixed toggle); Restore is a compact text action; long URLs/commands
      wrap naturally; the deleted-seed badge shares one collision space
      with existing definitions. LauncherRowLayoutTest pins the structure.
    - Official icons BUNDLED offline: app/src/main/assets/launcher_icons/
      carries 14 curated marks (ChatGPT, Claude, Z.ai, GitHub; Hermes,
      OpenCode, Claude Code, ZCode, Kilo Code, Cline, Antigravity, Codex,
      Aider, Qwen Code) as 192×192 lossless WebP (~125 KB total), built by
      scripts/make_launcher_icons.py from OFFICIAL first-party origins
      (URLs documented in the script; provenance + trademark note in
      docs/THIRD_PARTY.md). Resolution: imported copy → bundled mark →
      deterministic badge; zero runtime network; wide marks cannot
      dominate square ones (one normalized content box).
    - Antigravity REPLACES Gemini CLI in the curated default launcher
      set: command 'agy' — the binary name Google's own installer ships
      (docs/ANTIGRAVITY-PLATFORM.md §1); the honest verify-then-launch
      probe stays the only availability claim (upstream ships no musl
      build today). No stale 'gemini' default anywhere; a P1-era
      persisted 'gemini' hide id is inert by construction (pinned).

- PocketShell-v0.11.0-m7.0.0-m7p2-source.zip  sha256 e1dbbe6dd7d6ae29bc1ec43a0b1e840f3523c2f84384937764827bee8fdf3e14  (49,831,949 B — tracked source cut at the M7.1 P2 tip 1b15bde via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.1-p2 git bundle embedded; post-reset re-cut, see the RESTORE NOTE)
- PocketShell-v0.11.0-m7.0.0-m7p2-source.tar.gz  sha256 260f0163e7fac473a736e98a930c0544921eeffb4d41b91dfd4fd73058410cf3  (49,616,672 B — same cut, sorted tar, gzip -n; post-reset re-cut, see the RESTORE NOTE)
- pocketshell-m7.1-p2.gitbundle  sha256 3e1dcaafbf2bb67566b32f47c991033524d6513fdc2088a75cd3ba287dadf0ee  (37,318,556 B — full history M0 → main tip 1b15bde, the EXACT app tip this APK was built from; continues the P1 bundle through the M7.0 release chain; bundle pack bytes are not re-cut-stable, so all three pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.1 P2 build): PocketShell-v0.11.0-m7.0.0-m7p1-debug.apk (4d7349f7…, the P1 launcher APK), m7p1 source.zip/tar.gz (3bdd755f…/f6bad9aa…), pocketshell-m7.1-p1.gitbundle (98a80b0c…). Their content is fully contained in this delivery (same launcher model + the UI repair, icons, and the Antigravity swap; history in the bundle).

Withdrawn (superseded earlier, stands): the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to the earlier sandbox reset, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
