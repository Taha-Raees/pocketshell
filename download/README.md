# download/ — delivery masters

Current: **v0.11.2-m7.1.1-m72p2** (M7.2 P2 — the session lifecycle engine &
structured exit status; the second M7.2 production code phase, internal
infrastructure exactly on the P0 audit's provable state machine; phase
build on the inherited versionCode 47 / versionName 0.11.2-m7.1.1 stamp
per the M7.x phase-build precedent — the -m72p2 suffix is filename-only;
the M7.1.1 fix release v0.11.2-m7.1.1 remains the frozen fix-record below
it in history).

CUT NOTE: this set was cut fresh from the P2 record tip 3b144be (chain
0c9a792 implementation → 577e1e9 tests → 133e656 docs/tooling → the Task
40 worklog record) on the re-provisioned toolchain (Temurin 21.0.12.1+1,
cmdline-tools 11076708, platform-36, build-tools 36.0.0, NDK
28.2.13676358). The M7.2 P1 set is SUPERSEDED by this phase build (same
stamp, same cert, the notification foundation unchanged — the P2 build
carries the lifecycle engine on top) and is withdrawn from the serving
surface — its insurance copies SURVIVE in upload/ byte-exact (APK
e63fb9b5…, zip b4c9eb53…, tar.gz 147441d4…, bundle 23c9117e…), and its
content and history ride in this bundle (the P1 record tip a7c635e is a
direct ancestor of 3b144be).

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk  sha256 4a144d23710e7cd3893d1cdb59b0e3892a7b585ba1d4ed9015a7988360d2dfce  (30,802,996 B, versionCode 47 / 0.11.2-m7.1.1 — the inherited phase stamp)
  Installs IN PLACE over the M7.2 P1 phase build (vc47 — same versionCode,
  updated content, same pinned cert), the M7.1.1 fix (vc47), the M7.1
  release (vc46), every M7.1 phase build (vc45), the M7.0 release, and
  every previous pinned-cert build (vc16..47, same cert d96a6f66…8bf659).
  App data survives; P2 adds NO persistence (the lifecycle engine is
  in-memory, process-scoped like the sessions themselves) and posts NO
  notifications. Semantic pins verified on these exact bytes: version
  (aapt2 badging versionCode='47' versionName='0.11.2-m7.1.1', targetSdk
  28), the UNCHANGED 6-permission merged set (INTERNET,
  ACCESS_NETWORK_STATE, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS,
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION), embedded rev=2 layer asset
  sha 898131ff… / 17,920,000 B, dex carries the six new P2 symbols
  (SessionLifecycleState / SessionLifecycleEvent / ExitStatus /
  SpawnOrigin / AgentHint / AgentActivityRepository) beside the P1
  notifications symbols, the M7.1.1 keyboard symbols and the launcher
  phases' symbols. Verification executed at this tip (docs/TESTING.md
  §49 + the honest JVM-vs-device split recorded there): FULL JVM suite
  forced --rerun-tasks 810/810 green (app 665 = 635 + 30 new lifecycle
  tests + terminal-emulator 145, 0 failures / 0 errors / 0 SKIPPED),
  including the verification-honesty fix (the P1 structural pins
  silently skipped under the module-dir runner and now run and pass).
  What P2 adds:
    - The typed lifecycle model: SessionLifecycleState (STARTING →
      RUNNING → FINISHED; REMOVED = tab removal plus a typed event)
      with a private constructor making invalid combinations
      unrepresentable; the stored isFinished boolean retired (the
      getter is DERIVED).
    - Structured exit status faithful to the underlying API:
      ExitStatus.Exited(code) / ExitStatus.Signaled(signal) — exactly
      what JNI.waitFor/waitpid provides; nothing invented, no polling.
    - ONE authoritative owner with race-safe pure transitions: the real
      fork signal (the previously-discarded setTerminalShellPid
      callback) and the real waitpid delivery drive every change;
      duplicate/out-of-order callbacks are rejected with logged reasons
      and can never corrupt a recorded status; the close path is
      guarded against the upstream kill(0) hazard for a never-forked
      pid.
    - Structured launch identity at every spawn site: SpawnOrigin
      (Shell / LinuxShell / FilesTerminal / CommandApp(id) /
      CatalogApp(id) / CustomTool(id)) + AgentHint(displayName, command,
      matchedBy=LAUNCH_METADATA) for the three named-launcher paths —
      spawn metadata only, never a process claim.
    - Typed SessionLifecycleEvents emitted only at mutation sites +
      AgentActivityRepository (the derived read model that stores
      nothing, decides nothing, touches no notifications).
    - HONEST SCOPE: no notifications posted (the P1 sweep stays
      dormant), no agent detection, no waiting-for-input heuristics, no
      /proc scanning, no OSC 133, no persistence, NO user-visible UI
      change. The real-device gate is §49 (10 steps — a parity
      regression pass).

- PocketShell-v0.11.2-m7.1.1-m72p2-source.zip  sha256 996148b0fe4573ef14f44b43880efa8b2d02a6304fa2438e4d23773c7c78714e  (50,446,875 B — tracked source cut at the M7.2 P2 record tip 3b144be via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.2-p2 git bundle embedded)
- PocketShell-v0.11.2-m7.1.1-m72p2-source.tar.gz  sha256 ea19e8232d9c4294046f730843e763c2f7e05bb077ff2dafee817eda3d326300  (50,144,080 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.2-p2.gitbundle  sha256 d821d94fe21181574a706ceba88368e4defd336f01ec340393af947b91b0c548  (37,687,491 B — full history M0 → main tip 3b144be, the P2 record tip (implementation 0c9a792 → tests 577e1e9 → docs 133e656 → worklog 3b144be) that the source archives are cut at; the APK was built from the identical app sources (the app tree is unchanged since 0c9a792); bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.2 P2 phase build; withdrawn from the serving surface — insurance copies survive byte-exact in upload/, history in the bundle): PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk (e63fb9b5…, 30,473,480 B, the M7.2 P1 notification-foundation APK), the m72p1 source.zip/tar.gz (b4c9eb53…/147441d4…), pocketshell-m7.2-p1.gitbundle (23c9117e…).

Withdrawn (superseded by the M7.2 P1 phase build, stands): PocketShell-v0.11.2-m7.1.1-debug.apk (5d876141…, 30,451,377 B, the M7.1.1 FIX RELEASE APK — stays the frozen fix-record), the m7.1.1 source.zip/tar.gz (d8c1ef85…/d183c3eb…), pocketshell-m7.1.1.gitbundle (af73c57b…). Also withdrawn: pocketshell-m7.2-p0.gitbundle (0d8c5eb0…, head 02e8166, 37,607,042 B — the M7.2 P0 docs-only audit bundle; its entire content is contained in this bundle's history; the audit itself is docs/M7.2-P0-AUDIT.md at c8d0059).

Withdrawn (superseded by the M7.1.1 fix, stands): PocketShell-v0.11.1-m7.1.0-debug.apk (2d298c85…, 30,448,845 B, the OFFICIAL M7.1 RELEASE APK — stays the frozen milestone record), the m7.1 source.zip/tar.gz (0b4e8843…/d00d076f…), pocketshell-m7.1.gitbundle (7f677222…).

Withdrawn (superseded by the M7.1 release, stands): PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (46fb0d8b…, 30,448,845 B, the P3 external-keyboard APK), the m7p3 source.zip/tar.gz (d71cc7a2…/8bc28a92…), pocketshell-m7.1-p3.gitbundle (8b0544b5…).

Withdrawn (superseded by P3, stands): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to earlier sandbox resets, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.
