# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.7.0-ui] — 2026-09-03 — UI/UX redesign: the "Quiet Aurora" design system + final keyboard

### What this phase is

A complete UI/UX redesign phase (docs/UI-REDESIGN.md is the design contract,
written and committed BEFORE implementation). PocketShell is "a polished
personal Linux environment on Android" — not an IDE, not Termux-lookalike, no
project cards, no permanent tab bar, no fake features. Every honesty rule from
v0.3.1–v0.6.2 is preserved 1:1; the Linux runtime stack (M2.6) is untouched.

### Design system — "Quiet Aurora" (new `ui/theme/` + `ui/components/`)

- Tokens: cool-graphite surfaces with one soft periwinkle accent (Light /
  Dark / AMOLED schemes + optional Material You), 4dp spacing scale,
  12/16/20dp radius system, tonal elevation, 180–260ms motion budget.
  Terminal canvas stays framed ink in every theme. Exactly ONE gradient
  exists (Home Terminal hero card).
- Component kit: PSLogo (drawn brand mark), PSScreenHeader/PSScreenScaffold,
  PSNavDrawer, PSMenuButton (two-line hamburger with a11y label), PSHeroCard,
  PSActionTile, PSListCard, PSSectionLabel, PSBanner (info/ok/warn/error),
  PSEmptyState, PSStatusPill/PSStatusDot, PSAssistantFab.
- Typography: system font stack (no bundled font), tuned M3 scale,
  monospace reserved for quoting real terminal output.

### Navigation (no permanent tabs)

- String-keyed screen `when` replaced by a typed `Screen` enum +
  DismissibleNavigationDrawer (M3 1.4's successor of the modal drawer) with a
  compact hamburger in the top-left of EVERY screen. Destinations: Home,
  Terminal, Apps, Packages, Diagnostics, Settings (+ the Linux Shell spawn
  row). Back: drawer → close; else → Home. Future destinations (GUI Apps,
  SSH, AI chat, distributions) are enum entries when they really exist.

### Home — complete redesign

- Brand block (drawn PS logo + wordmark + tagline) and a clean 2×2 launcher
  grid: Terminal (hero gradient card), Linux Shell (state-aware, non-READY
  routes to Diagnostics exactly as before), Apps, Packages. Active sessions
  and the honest launch-error banner below; assistant FAB bottom-right.
- REMOVED: "Installed CLI Apps" launcher cards (git/python/nano/htop are
  packages, not launchers — they live in Packages search/Featured), IDE
  concepts, placeholder tiles. The fourth grid slot stays deliberately empty
  until a real destination exists.

### Launchable apps (new, documented rules — packages/LaunchableApps.kt)

- An app appears ONLY while the runtime is READY and the guest confirms
  `command -v <executable>` RIGHT NOW (re-probed on screen visibility);
  probe failures hide rows and are surfaced. Seed: hermes, opencode.
  Launch = the same verify-then-launch flow as catalog apps (dedicated guest
  session, real scrollback). Registry stays tiny (test-pinned ≤ 8) and
  categorically excludes CLI tools (test-pinned).

### Terminal — visual redesign

- Chrome: hamburger · session tab pills (selected = accent, close on
  selected tab only, "(exited)" honest label) · new-session · overflow menu
  (New/Close session; confirm dialog — closing kills a real process).
- Canvas: framed-ink Surface with rounded corners and breathing room; the
  TerminalView, font sizing (pinch 12–40), blinker lifecycle, upstream
  repaint contract and clipboard behavior are unchanged.

### Keyboard — FINAL specification (binding, brief §6)

- Top accessory row: Esc · Tab · (spring) · ← ↑ ↓ → (repeatable; arrows
  long-press to HOME/END/PGUP/PGDN).
- Bottom accessory row: [⌨ icon-only keyboard toggle, permanent first
  position] · Ctrl · Alt · Space (flex) · Shift · ↵.
- The toggle shows/hides the ANDROID IME (TerminalView's real
  InputConnection; rows ride above the IME via imePadding). No ON/OFF text,
  visual state only; modifiers keep OFF → ONE_SHOT → LOCKED with tinted cap
  + lock dot.
- DEDICATED FN KEY REMOVED (ModifierKey.FN deleted; readFnKey() honestly
  false): F1–F12 via Esc long-press strip (tap key to send, auto-dismiss);
  digits/symbols come from the Android keyboard. The M1 built-in typing
  pages are deleted with their layout pins (KeyLayoutsTest rewritten for the
  final spec; KeyboardStateTest FN pins updated).

### Packages (formerly Explore) — same logic, new skin

- Search (ranked hits, real versions, installable), Featured catalog (the
  curated five STAY here — they are packages), operation banner with real
  apk stderr + Cancel/Retry, per-card "Working…" rule (v0.4.2),
  installed-state honesty (v0.4.4) — all preserved verbatim; only the skin
  changed.

### Settings & Diagnostics

- Settings: grouped cards — Appearance (theme/dynamic/font-size),
  AI Assistant (OpenRouter API key + free-text model id; masked after
  entry, never logged, never in Diagnostics; storage = app-private
  DataStore, stated honestly as not-yet-keystore-backed; assistant chat
  honestly "not part of the app yet"), About.
- Diagnostics: all facts/buttons preserved, grouped into Device / Linux
  runtime / Package environment cards. Not hidden, not decorated.
- AI Assistant FAB on Home opens the configuration section (never a fake
  chat).

### Integrity

- versionCode 17 / versionName 0.7.0-ui; same pinned debug keystore
  (d96a6f66…) → in-place update over v0.4.1–v0.6.2.
- Tests: 636 per variant (app 173 + terminal-emulator 145; M1 keyboard
  layout pins replaced by final-spec pins + LaunchableApps rule pins),
  0 failures. APK verified (aapt2/apksigner).
- NOT device-verified yet: keyboard sandwich (IME toggle), drawer, new
  screens — TESTING.md §11 is the device gate. Runtime/package behavior is
  architecturally unchanged (same launchers, same builders, same probes).

## [0.6.2-m2.6] — 2026-09-02 — M2.6.12+M2.6.13: selective /proc sysdata overlay + hardlink extraction fix

### Why this release exists (both layers device-reported 2026-09-02, SM-F711B)

**M2.6.13 — `apk add binutils gcc g++` failed to extract exactly their hardlink
entries.** The transcript showed 19 "failed to extract … Permission denied"
errors — byte-for-byte the `hrwxr-xr-x` hardlink entries of the three Alpine
packages (binutils 11, gcc 5, g++ 3; verified by downloading the packages and
listing their tar entry types). Every regular file extracted fine, including
the real driver `usr/bin/aarch64-alpine-linux-musl-gcc`. Root cause: apk's
extractor materializes hardlink entries with `link()`, and the SAME AOSP
neverallow that M2.6 worked around for download commits
(`neverallow all_untrusted_apps file_type:file link`) forbids `link()` to
untrusted apps outright — EACCES, per entry. The fd-link patch fixed
downloads only; extraction hardlinks are a separate surface of the same
neverallow.

**The fix — Termux's own `link2symlink` proot extension (M2.6.13):** our
shipped `libproot.so` is built from termux/proot pin `7266fb3e`, which
contains the extension, and Termux PRoot-Distro enables it BY DEFAULT for
every non-Termux distro (`--link2symlink`). It intercepts `link()`/`linkat()`
at the ptrace layer and emulates the hard link as a symlink chain (with
link-count translation for stat/statx), so the kernel never evaluates the
denied operation. v0.6.2 passes `--link2symlink` in BOTH guest profiles.
Honest differences, documented and visible: links appear as symlinks
(`ls -l /usr/bin/ld`), and each emulated link costs the file's disk space.
Host rehearsal (scripts/rehearse_m262.sh): binutils installs, `ld/ar/readelf
--version` work through the emulated links, and the emulated binary is
byte-identical to the control install's real file.

**M2.6.12 — selective /proc sysdata overlay.** The same 2026-09-02 session
confirmed the kernel also denies this app read access to the STANDARD procfs
files (/proc/version confirmed on-device; proc_stat/proc_uptime/proc_loadavg/
proc_vmstat are the same AOSP neverallow class). v0.6.1 documented those
denials; v0.6.2 repairs them the way Termux PRoot-Distro does
(proot_distro/sysdata.py, `setup_fake_sysdata()` + `fake_sysdata_bindings()`
— architecture studied and ADAPTED, not copied):

- PROBE FIRST, REAL WINS (upstream's core honesty mechanism, kept 1:1): at
  every interactive spawn the app probes each real file with a one-byte
  read; kernel-readable files are NEVER overlaid — real data always wins.
  Only genuinely-denied files get a verified compatibility file bound
  file-over-file ON TOP of the real /proc bind.
- HONEST CONTENT (adapted from upstream's static constants): /proc/version
  is composed from the REAL uname(2) identity with an explicit attribution
  marker ("PocketShell sysdata overlay: kernel identity via uname(2); the
  kernel's own file is denied to apps by Android SELinux") — this
  SUPERSEDES v0.6.1's refusal to synthesize /proc/version, per the project
  owner's direction to adopt the probe-gated overlay model; the marker
  keeps the no-fake rule intact (any reader sees it IS an overlay).
  /proc/uptime field 1 is the REAL elapsedRealtime clock; /proc/stat has
  the REAL core count and REAL btime (epoch now − uptime) with documented
  zero placeholders for the unreadable global jiffies; /proc/loadavg's
  process-count tail is the REAL hidepid-filtered pid set; /proc/vmstat is
  the standard counter-name skeleton with zero values. Scope is exactly the
  five standard files the gate names — upstream's sysctl entries and
  /sys/fs/selinux empty-dir bind are deliberately not shipped.
- WRITE HARDENING (proportionate port of upstream's descriptor discipline):
  entries are validated before use (regular file, not a symlink, exactly
  one link — planted entries are dropped and remade), writes are
  CREATE_NEW+NOFOLLOW, and every overlay is verified by content round-trip
  before it may be bound. Unlike upstream's write-if-missing, files REFRESH
  at every spawn.
- DIAGNOSTICS: new read-only "sysdata overlays" row (probe-only — the
  Diagnostics button never writes), plus per-spawn overlay status.

### Other
- GuestSysDataCompat (new, unit-tested — 20 pins: probe matrix, generator
  formats, planted symlink/hardlink handling, per-entry honest degradation)
  and launcher guard pins (sysdata binds only on /proc-bound interactive
  sessions; refused for PACKAGE_OPERATION and no-/proc sessions).
- Suite: 312 tests/variant (167 app + 145 terminal-emulator), 624
  executions, 0 failures. Rehearsal scripts/rehearse_m262.sh FULL PASS 19/19.
- v0.6.2 installs IN PLACE over v0.6.1/v0.6.0/v0.5.0 (same pinned signing
  key); runtime, packages and cache untouched. Existing broken binutils/gcc/
  g++ states self-heal on the next `apk fix` after updating (the failed
  packages were recorded but incomplete; `apk fix` re-extracts them).

### Why this patch release exists
- The 2026-09-02 device test (SM-F711B) CONFIRMED the M2.6 architecture end to
  end: Diagnostics shows `apk fd-link patch: applied` and the interactive
  session binds a real /proc — `cat /proc/meminfo` returns the host's real
  values and numeric pid dirs appear. The same test surfaced two behaviors the
  docs and Diagnostics wording had not prepared anyone for, and one Gate A
  bullet (`cat /proc/version`) that reads as a failure but is actually
  device policy working as designed.
- What the test showed, and what each means:
  1. `ls /proc` prints a wall of `Permission denied` lines (kmsg, kcore,
     vmcore, kpage*, sched_debug, timer_list, sysrq-trigger, …) before the
     readable tail. REAL and EXPECTED: the guest's /proc IS the Android host
     procfs (the design — no re-export, no simulation), and SELinux genuinely
     denies this app getattr on kernel-internal nodes; busybox `ls` reports
     each denial. `ps`/`top`/`htop` skip unreadable entries silently, so only
     directory listings are noisy.
  2. `cat /proc/version` → `Permission denied` on this Samsung/One UI kernel:
     the OEM policy does not grant untrusted_app read on `proc_version` (the
     legacy grant disappears at targetSdk 28). Informational only —
     `uname -a` shows the kernel banner. Synthesizing /proc/version from
     uname() was considered and REJECTED: fabricated content violates the
     project's no-fake rule.
  3. The readable set is genuinely useful and real: pid dirs (host pids,
     hidepid=2-filtered to this app), meminfo, cpuinfo, cmdline, uptime,
     loadavg, mounts, self, thread-self, sys, tty, fs, bus, irq, driver,
     plus Samsung extras (memsize, memextra, uid_0_procstat).

### Changed
- Diagnostics "Interactive /proc" row (patched-libapk state) now states the
  full truth up front: `interactive sessions bind /proc (real process
  tools). Host procfs: kernel-internal entries show 'Permission denied' —
  Android SELinux policy, expected`. The no-patch fallback wording is
  unchanged.
- docs/TESTING.md §10 Gate A re-anchored: the PASS signals are the readable
  tail (`ls /proc`), `cat /proc/meminfo`, `cat /proc/cpuinfo`; the denial
  wall is documented as expected noise; `cat /proc/version` is INFORMATIONAL
  (OEM-dependent). New subsection "Expected on-device (NOT bugs)" records
  the 2026-09-02 observations verbatim.
- No runtime, launcher, profile, patch, or rootfs changes — the artifacts
  (APK payload beyond the one string) are behaviorally identical to v0.6.0.

## [0.6.0-m2.6] — 2026-09-02 — M2.6: Linux compatibility recovery — real /proc + real apk, no trade

### Why this milestone exists
- M2.5 made the interactive guest apk-capable by removing the /proc bind from
  EVERY guest session. The package manager worked; `ps`, `top` and `htop` had
  no procfs to read. That violates the product's own compatibility list
  (top/htop/ps/tmux/vim/…) and the Golden Rule: PocketShell must not trade
  one Linux feature for another.

### Architecture (docs/M2.6-RESEARCH.md — full evidence chain)
- **Root cause, source-verified:** apk-tools 3.0.x picks its download-commit
  strategy with `is_proc_fd_ok()` = `access("/proc/self/fd", F_OK) == 0`
  (src/io.c, identical in 3.0.6 and 3.0.8 incl. upstream master). With /proc
  visible it commits every download through
  `linkat("/proc/self/fd/N", …, AT_SYMLINK_FOLLOW)`; AOSP
  `app_neverallows.te` (`neverallow all_untrusted_apps file_type:file link`)
  makes the kernel return EACCES and apk cancels the whole download — no
  fallback for that errno. Without /proc it uses the named-tmpfile + renameat
  path (allowed; device-proven since v0.4.2).
- **The M2.6 fix — GuestApkCompat:** a ONE-BYTE, checksum-pinned patch to
  Alpine's OWN `usr/lib/libapk.so.3.0.0` (3.0.6-r0 from the pinned
  minirootfs) turns the gate literal `"/proc/self/fd"` into
  `"/proc/self/fX"`, so `is_proc_fd_ok()` is permanently false and apk always
  commits via renameat. Same binary version, same real downloads/output/exit
  codes, same database. The `"/proc/self/fd/%d"` script-execution literal is
  untouched. Reproducible: `scripts/patch_apk_fdlink.py` (two literals,
  exactly one code reference each — disassembly-verified per arch).
- **GuestExecutionProfile (M2.6.3):** the two launch policies are now
  explicit on the SAME builder/proot/launcher —
  `INTERACTIVE_TERMINAL` (sessions; binds a REAL /proc when the patched
  library is verified, honest no-/proc fallback otherwise) and
  `PACKAGE_OPERATION` (app-side apk execs; minimal mounts, NEVER /proc —
  refuse-guarded in the builder and pinned by tests). One rootfs, one shared
  cache, one database; no duplicated runtime.
- **Expected process semantics (documented, not faked):** with /proc bound the
  guest sees the Android host procfs filtered by the kernel's hidepid=2 app
  isolation — `ps`/`top` show the app's real process tree with host pids;
  system-wide `/proc/stat`/`meminfo` are real. No filtering, no fake table.

### Added
- `GuestApkCompat` — hash-driven, idempotent patch installer/verifier:
  Ready (patched verified) / NotApplicable (user-modified rootfs — never
  touched) / Failed (honest reason). The patched library ships as an app
  asset and is verified against its pinned sha256 BEFORE anything is written;
  install is temp-file + rename with a post-write re-verification.
- `PackageGateway.prepareGuestForSession` now also verifies/installs the
  patch and its result decides the session's /proc bind
  (`TerminalSessionManager`).
- Diagnostics (M2.6.11): "apk fd-link patch" and "Interactive /proc" rows in
  the package-environment report — read-only, never installs from the
  button; explains the exact state and how to fix it.
- Regression tests: profile pins (proc-enabled session shape, no-drift argv
  equivalence, PACKAGE_OPERATION refuse-guard), GuestApkCompat matrix
  (Ready/rewrite/unknown-untouched/missing/corrupt/status-only/unreadable).
  292 tests per variant, 584 executions, 0 failures.

### Compatibility restored (device gate pending — docs/TESTING.md §10)
- `ls /proc`, `cat /proc/version`, `/proc/meminfo` real again in the guest;
  `ps` and `top` work; `apk update/search/add/del` still work BOTH in the
  shell (patched apk with /proc bound) and from the app UI (no-/proc profile).
- If the guest rootfs was modified by an in-guest `apk upgrade`, the patch
  reports NotApplicable, sessions degrade honestly to the v0.5.0 shape, and
  Diagnostics explains the reinstall path — the user's runtime is never
  overwritten blindly.

## [0.5.0-m2.5] — 2026-09-02 — M2.5: an apk-capable guest shell + install-any-searched-package

### Device context (v0.4.4 confirmed working)
- The 2026-09-02 10:04 screenshots (SM-F711B, v0.4.4) confirm the state-sync
  fixes on real hardware: Home "Installed CLI Apps" lists **Nano
  9.2-r0 AND Git 2.54.0-r0** straight from the real apk database; Explore
  shows Nano "Installed · 9.2-r0" with Open/Uninstall. Clipboard paste
  works ("I can copy paste"). M2.4's device gate is PASSED; M2.5's Home
  integration (verified-entries-only) is realized.

### Fixed — the guest shell could not run `apk` (the "small errors when trying to add node")
- **What the 10:03 terminal screenshot showed:** a MANUAL `apk update` and
  `apk add nodejs npm` typed INSIDE the Linux Shell died with
  `updating and opening …/APKINDEX.tar.gz: Permission denied` — the v0.4.2
  SELinux failure shape — while app-side installs (the user's Git install)
  worked fine. With the failed index update only the stale rootfs-internal
  cache remained ("2 unavailable, 0 stale; 31 distinct packages available"),
  so apk answered `nodejs (no such package)`.
- **Root cause:** v0.4.2 dropped the /proc bind from APP-SIDE package
  commands only; INTERACTIVE sessions kept it. With /proc visible, apk-tools
  3.0.x commits downloads via `linkat("/proc/self/fd/N", …)` — the exact
  AOSP neverallow for untrusted apps — so every in-session apk fetch died,
  and the session's apk also read the rootfs-internal cache (no cache binds)
  instead of the app's healthy index.
- **Fix — one session shape, `RuntimeProcessLauncher.buildSessionSpec`:**
  every interactive guest session (Linux Shell AND catalog-app sessions) now
  uses the SAME SELinux-driven shape as package commands — **no /proc** —
  plus **the same shared apk cache binds**, and spawns after a best-effort
  DNS/workspace refresh (`PackageGateway.prepareGuestForSession`). The
  session's manual `apk update && apk add …` now commits via renameat into
  the ONE cache the UI uses — install from the terminal or from the UI, the
  result is the same real database (apk's own lock keeps them serialized).
- **Honest cost, documented:** the guest can no longer see /proc, so process
  tools (`ps`, `top`, htop's process list) have nothing to read inside the
  guest and fail with their own errors. A working package manager in the
  shell outweighs process listing; Android's SELinux forces the choice.

### Fixed — searching "node" buried nodejs behind description hits
- `apk search` matches names AND descriptions and returns everything
  alphabetically — the user's "node" search showed abseil-cpp-dev, ceph18,
  certbot-dns-linode… while `nodejs` was cut off by the 8-hit limit.
- **Ranking:** exact-name → name-prefix ("nodejs", "nodejs-current") →
  name-contains ("dpdk-node", "certbot-dns-li**node**") → description-only
  matches, alphabetical within groups; 12 hits shown with a "…and N more"
  note.

### Added — install any searched package (M2.5 catalog surface)
- Search hits are now **installable**: one tap runs the same honest
  operation pipeline (`apk update` → `apk add` → `apk info -e` verify) by
  the exact package name (`PackageOperationManager.installPackage`). No
  executable promise is made for non-catalog packages (nodejs ships `node`,
  not `nodejs` — the install verifies ONLY what the real database
  confirms); installed hits show "Installed · version — run 'name' from the
  shell". Catalog-known hits keep their descriptions. Per-hit "Working…"
  busy state follows the running operation exactly (v0.4.2 rule).
- Search results join the installed-state probe, so a fresh install flips
  the hit row to Installed without leaving the screen.

### Notes
- 7 new/updated pins: the apk-capable session spec (no /proc + shared cache
  binds + guest argv tail), search ranking over the exact device query
  shape ("node" → nodejs first; the first expectation even caught that
  certbot-dns-linode is a NAME match via "linode"), and the
  search-install flow (SUCCESS without any `command -v` call, executable
  gate kept when an executable IS known). 281 tests per variant (136 app +
  145 terminal-emulator), 562 executions, 0 failures.
- v0.4.4→v0.5.0 installs as an in-place update (same pinned signing key);
  the runtime, its packages and the shared apk cache are untouched. NOTE:
  the first Linux Shell session opened after updating spawns with the new
  apk-capable spec — `ps`/`top` inside the guest will honestly report they
  cannot read /proc; everything else behaves as before, plus working apk.

## [0.4.4-m2.4] — 2026-09-02 — the app finally sees what apk installed (state-sync hotfix) + clipboard paste that pastes

### Device context (v0.4.3 confirmed working)
- The 2026-09-02 09:09–09:10 screenshots (SM-F711B, v0.4.3) are the M2.4
  gate PASSING on real hardware: GNU nano 9.2 running inside the Alpine
  guest, Diagnostics showing `apk-tools 3.0.6-r0`, combined Guest DNS and
  `Repository fetch: OK — OK: 28546 distinct packages available`. The DNS
  and SELinux chains are closed; what remained were two UI-layer bugs the
  user found immediately.

### Fixed — installed packages stayed invisible in the UI (Explore "Not installed", Home "No apps installed yet")
- **Bug 1, deterministic root cause (batch probe exit-code misread):** the
  Explore screen's installed-state probe runs one guest exec:
  `for p in "$@"; do v=$(apk info -e -v "$p" 2>/dev/null) && echo "$p $v"; done`.
  A POSIX for-loop's exit status is the LAST command it ran — and the
  catalog's last package is `python3`, which was not installed, so the last
  iteration ended with `apk info`'s exit 1 and the loop (and the whole
  probe) "failed". The caller treated the exec as failed and returned an
  EMPTY map — discarding the perfectly good stdout that contained
  `nano nano-9.2-r0`. A genuinely installed nano rendered as "Not
  installed" on every entry, deterministically, whenever the answer was
  mixed. The rehearsal missed it because it pinned the single-package
  `getPackageInfo` path, never the batch script.
- **Fix:** the probe script now calls the absolute `"/sbin/apk"` (the
  PATH-free form every other apk invocation already uses — this was the
  only PATH-dependent apk call in the codebase) and ends with `; exit 0` —
  a completed loop is a successful probe no matter how many listed packages
  are absent. The version column is now parsed by the same strict parser as
  the single probe, so both paths report identical versions ("9.2-r0", not
  "nano-9.2-r0").
- **Honesty hardening:** a failed probe can no longer masquerade as
  "nothing installed". The manager now throws `PackageProbeException`
  (timeout / destroyed / non-zero exec) instead of returning a silent empty
  map; Explore keeps the last real answer, shows an "Installed state
  unavailable: …" banner and renders untouched cards as "Installed state
  unknown"; Home does the same for its list. "Not installed" is now
  exclusively a real apk answer.
- **Bug 2, Home's second invented source of truth:** Home's "Installed CLI
  Apps" read an M1-era DataStore registry (`CliAppRegistry`) that NOTHING
  in the M2.4 flow ever wrote — M2.4 installs go through apk, not the
  registry — so Home claimed "No apps installed yet" over a genuinely
  installed nano. Home now renders the catalog subset the real apk database
  confirms (`installedCatalogApps`, probed when Home becomes visible and
  after every package operation reaches a terminal state), with the real
  version on each row. Tapping a row runs the same verify-then-launch flow
  as Explore's Open (`apk info -e` + `command -v`, then a dedicated guest
  session). The orphaned legacy chain (`CliApp`, `CliAppRegistry`,
  `CliAppLauncher`, `TerminalSessionManager.createSessionForApp`,
  `ShellEnvironment.resolveExecutable`) is removed — an unused registry
  that claims installed state is exactly the kind of fake this project
  refuses to keep around.

### Fixed — terminal Paste did nothing
- The vendored Termux selection toolbar's Paste action ends in
  `TerminalSession.onPasteTextFromClipboard()` → the session CLIENT callback
  — and PocketShell's implementation was an empty body with a comment
  claiming "upstream TerminalView performs the actual paste internally"
  (false: nothing did). The menu item was enabled whenever the clipboard
  had content, then silently did nothing — the user's exact report
  ("I can see option for paste but nothing paste when choosed").
- `PocketShellSessionClient.onPasteTextFromClipboard` now reads the real
  clipboard and pastes via `TerminalEmulator.paste` — upstream semantics:
  strips escape/C1 control bytes, converts LF/CRLF to CR, honours bracketed
  paste mode (nano/auto-indent aware). An absent or empty clip pastes
  nothing (honest no-op).

### Notes
- 5 new/updated pins: the v0.4.4 probe script (absolute `/sbin/apk` +
  `exit 0`) parsing the exact device case (nano installed, python3 last and
  absent) with the plain version form; `PackageProbeException` on timeout
  and on exec failure (exit codes carried); not-ready refusal; and the
  `installedCatalogApps` catalog-order mapping. Legacy `CliAppTest`
  removed with the chain it tested. 278 tests per variant (133 app + 145
  terminal-emulator), 556 executions, 0 failures.
- v0.4.3→v0.4.4 installs as an in-place update (same pinned signing key).
  No runtime reinstall: the fixes are all in the Android layer; the first
  Home/Explore visit re-probes the real apk database.

## [0.4.3-m2.4] — 2026-09-02 — guest DNS can no longer be a single point of failure (device-screenshot hotfix)

### Fixed — "DNS: transient error (try again later)" on every fetch (v0.4.2 device screenshots)
- **What the 2026-09-02 08:13 device screenshots showed (SM-F711B,
  v0.4.2):** the SELinux fix WORKED — the old "Permission denied" is gone,
  and only the tapped card shows "Working…" (both v0.4.2 fixes confirmed on
  device). The remaining failure moved to name resolution: Diagnostics
  showed the guest resolv.conf containing `nameserver 172.20.10.1` (the
  hotspot's gateway) plus `nameserver fe80::8c98:6bff:fe13:bf64%wlan0`, and
  both `apk update` attempts died with `DNS: transient error (try again
  later)`.
- **Root cause:** v0.4.1–v0.4.2 wrote a DEVICE-ONLY resolv.conf — a single
  usable resolver (the second entry is LinkProperties scope syntax;
  musl's `inet_pton` rejects the `%wlan0` zone suffix, so that line was
  dead weight). When that one resolver doesn't answer, musl exhausts its
  retry budget and getaddrinfo returns EAI_AGAIN — apk's "DNS: transient
  error". One resolver is a single point of failure; v0.4.0 had already
  proven the public resolvers ARE reachable on this network (its fetch
  downloaded the index fine before dying at the linkat commit).
- **Fix — combined, self-healing guest DNS** (`GuestEnvironment`): the
  resolv.conf now lists usable device resolvers FIRST, then the public
  fallbacks (1.1.1.1, 8.8.8.8), capped at musl's MAXNS=3. musl queries all
  configured nameservers in parallel and takes the first answer, so one
  dead resolver can no longer block a fetch. Files carry a
  `# managed by PocketShell` marker and are refreshed on EVERY package
  operation to the CURRENT network's resolvers (a resolv.conf pointing at
  yesterday's hotspot gateway is a guaranteed failure tomorrow — the old
  never-overwrite rule kept stale resolvers forever). Legacy shapes we
  wrote (v0.4.0 public-only constant, v0.4.0–v0.4.2 bare
  `nameserver <literal>` lists) are upgraded in place on the first package
  operation; anything with comments/options/search lines/hostnames is user
  content and is never touched. Zone-suffixed link-locals are dropped at
  the source (`deviceDnsServers`) and by shape when building the file.
- Diagnostics "Guest DNS" now renders without comment lines and reports
  the source honestly: "device resolvers first, public fallback (musl
  queries all in parallel)".

### Notes
- 9 new/updated DNS pins in `GuestEnvironmentTest` (combined content, MAXNS
  cap, zone-suffix drop, v0.4.1→combined upgrade of the exact on-device
  file, stale-network refresh, user-content protection, managed-shape
  recognition) + the pre-op repair pins in `AlpinePackageManagerTest`.
  281 tests total, 0 failures.
- v0.4.2→v0.4.3 installs as an in-place update (same pinned signing key);
  the DNS repair applies on the first package operation — no reinstall.

## [0.4.2-m2.4] — 2026-09-02 — fix the SELinux hardlink neverallow that killed every apk download (device-screenshot hotfix)

### Fixed — the real reason `apk update` died with "Permission denied" (v0.4.1 device screenshots)
- **Root cause, verified in apk-tools 3.0.6 source + AOSP sepolicy:** the
  2026-09-02 device screenshots (SM-F711B, v0.4.1 with working device-DNS)
  still showed every fetch failing with `updating and opening …
  APKINDEX.tar.gz: Permission denied` — while the status bar showed real
  download traffic (3–10 KB/s). That shape (bytes flow, then EACCES) pointed
  away from DNS entirely and at apk's download COMMIT step: apk-tools 3.0.x
  (the `HAVE_O_TMPFILE` build Alpine ships) downloads every cached object —
  APKINDEX **and** packages — into an anonymous `O_TMPFILE` file and commits
  it with `linkat(AT_FDCWD, "/proc/self/fd/N", atfd, name,
  AT_SYMLINK_FOLLOW)` (`src/io.c`, `__apk_ostream_to_file`/`fdo_close`). AOSP
  system/sepolicy `app_neverallows.te` answers that with
  `neverallow all_untrusted_apps file_type:file link;` — untrusted apps keep
  create/rename/unlink on their own data (`create_file_perms` deliberately
  contains no `link`), so the kernel denies the hardlink with EACCES and apk
  **cancels the whole download** (no retry, no fallback). This is why
  v0.4.1's cache binds changed nothing (the denial is on the link
  *operation*, not the path), why host rehearsals never saw it (no SELinux),
  and why the v0.4.1 DNS fix could not cure it (DNS was never the whole
  story — the v0.4.1 changelog's "instant blocking" reading of the first
  EACCES was wrong).
- **Fix — package commands no longer bind /proc into the guest**
  (`RuntimeProcessLauncher.buildLaunchSpec(bindProc=…)`, package specs pass
  `false`; interactive shell/app sessions keep `/proc` unchanged). Without a
  visible `/proc`, apk's own `is_proc_fd_ok()` (a bare
  `access("/proc/self/fd")`) is false and it downloads through the
  named-tmpfile + `renameat` commit path — plain create/rename/unlink, fully
  allowed for apps. apk needs `/proc` for nothing else in this flow: the
  only other consumer (`find_mountpoint` → `/proc/mounts`) degrades to a
  no-op and only matters for read-only cache remounts. Rehearsed end-to-end
  with the same apk-tools 3.0.6: `update → search → add → run → del` all
  pass without `/proc`, and the committed `APKINDEX.<hash>.tar.gz` files
  land in the bound host cache dir with zero leftover temp files.

### Fixed — catalog cards no longer claim work they are not doing
- v0.4.1 rendered the global single-flight busy flag on every card, so
  installing nano flipped all five featured cards to "Working…" at once
  (user screenshot). A card now shows "Working…" only while the running
  operation targets *that* card's package (`packageOperationTargetsCard`,
  pinned by unit tests); other cards keep their true Install/Open labels and
  stay disabled only because the one-mutation-at-a-time lock is honest.
- The failed-operation banner no longer repeats apk stderr lines verbatim
  when the summary line already contains them.

### Fixed — a cancel racing the operation start can no longer wedge the manager
- Found by the `cancel destroys the process and lands FAILED` pin while
  rebuilding v0.4.2 (it caught a real scheduling race, not a flake): if
  `cancelCurrent()` fired after the operation was requested but BEFORE the
  IO dispatcher first ran the job body, the body never executed — so its
  `finally` never released the single-flight lock or the busy flag, and
  `_current` never reached a terminal state. Every later package op would
  then be refused forever with "another package operation is already
  running" until the app process died. The operation job now starts
  `CoroutineStart.ATOMIC` with `ensureActive()` first: the block ALWAYS
  begins, the cancel lands as an honest `FAILED("cancelled")` (or the real
  result when the process was already started and destroyed), and the
  cleanup `finally` is unavoidable. The same pin passes deterministically
  on both race orderings.

### Notes
- 4 new/updated unit pins: package specs never carry `/proc` while shell
  specs keep it (exact argv pins), and the per-card busy rule. 277 tests
  (132 app + 145 terminal-emulator), 0 failures.
- v0.4.1→v0.4.2 installs as an in-place update (same pinned signing key).

## [0.4.1-m2.4] — 2026-09-01 — fix guest DNS + apk cache for real networks (device-recording hotfix)

### Fixed — why every package operation failed on the device (v0.4.0 recording)
- **Root cause (from the SM-F711B recording, 2026-09-01 13:37):** Explore CLI
  Apps → Install failed twice, in two different ways, for the SAME underlying
  reason: the v0.4.0 DNS repair wrote hardcoded public resolvers
  (`1.1.1.1` / `8.8.8.8`), and on the user's network those are UNREACHABLE
  (port-53 egress blocked / strict Private DNS is common on carrier and
  hotspot networks). With `resolv.conf` pointing at dead servers, musl's
  resolver retried for ~10 s and apk reported `DNS: transient error (try
  again later)`; under different (instant) blocking the same failure surfaced
  as the raw errno `Permission denied` — apk-tools 3 passes socket-layer
  errnos through verbatim (`FETCH_ERRCAT_ERRNO`), which is why the first
  failure never mentioned DNS at all. No package operation ever reached the
  network; the Android side had perfect connectivity the whole time.
- **Fix — guest DNS now uses the DEVICE's own resolvers**
  (`ConnectivityManager` → `LinkProperties.dnsServers`, IPv4 first, top 3,
  re-read per operation). The public pair remains only as a fallback when the
  OS reports nothing usable. A `resolv.conf` that exactly equals the v0.4.0
  fallback is upgraded in place (PocketShell wrote it, PocketShell replaces
  it); user- or Alpine-written content is never touched. Requires one new
  normal permission: `ACCESS_NETWORK_STATE` (read-only, no traffic).
- **Hardened — apk cache can no longer be blocked by rootfs permissions.**
  apk-tools 3 keeps its download cache in `etc/apk/cache` (fallback
  `var/cache/apk`) and failed v0.4.0 installs never got past opening the
  index there. The package spec now binds two app-owned host directories
  over both guest cache paths (`--bind=<cache>/etc:/etc/apk/cache`,
  `--bind=<cache>/var:/var/cache/apk`) — the same proot mechanism as the
  `/dev`, `/proc`, `/sys` binds, so the cache lives OUTSIDE the rootfs and
  rootfs-internal ownership/modes can never block a fetch again. The cache
  dir sits beside the runtime under `noBackupFilesDir`.
- **Pre-op workspace repair:** every package operation now also guarantees
  `etc/apk/cache`, `var/cache/apk` and `tmp` exist inside the rootfs with
  sane modes (best-effort; the binds are the hard guarantee) — runtimes
  installed by v0.2.x–v0.4.0 are repaired in place, no reinstall needed.
- **Honest failure surface:** the Explore FAILED banner now also shows apk's
  real stderr (up to 4 lines) and offers **Retry** for repository updates;
  Diagnostics' "Check package environment" now runs ONE real bounded
  `apk update` probe (explicit button press — nothing automatic) and reports
  the true outcome plus which DNS servers the guest got and where they came
  from ("device resolvers" vs "public fallback").

### Signing certificate changed — ONE-TIME uninstall required (read first)
- The sandbox reset (#5) destroyed `~/.android/debug.keystore`. Android
  debug-APK signatures ARE the update identity: a build signed with a
  regenerated key cannot install over v0.3.x–v0.4.0. The key is
  unrecoverable (it never left the sandbox), so **v0.4.1 must be installed
  after uninstalling the old app** — the runtime (9.3 MB) is reinstalled
  from Diagnostics in one tap; no packages were installed yet (M2.4 never
  succeeded on v0.4.0).
- **This is the last time.** The (new) debug keystore is now committed at
  `keystore/debug.keystore` and pinned via `signingConfigs.debug` — every
  future build signs identically regardless of sandbox resets, and remains
  an in-place update over v0.4.1+. New cert SHA-256:
  `d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659`
  (was `34391676…cf3f`).

### Tests
- 12 new unit tests (271 total): device-resolver flow into the pre-op repair,
  v0.4.0-fallback upgrade rule, never-overwrite for user content, cache-bind
  argv pins (after the fixed binds, before the guest argv), workspace repair
  creates/fixes dirs, workspace-repair failure refuses the op without exec.
- Rehearsal (scripts/rehearse_m24_packages.sh) extended with the exact cache
  binds + workspace repair steps; x86_64 end-to-end apk flow re-run.

## [0.4.0-m2.4] — 2026-09-01 — real Alpine package management (apk) + CLI app installation foundation

### Added — the first real package-management layer (M2.4)
- **Real `apk`, zero fakery.** Explore CLI Apps is now a working frontend for
  the real Alpine package manager inside the proot guest:
  `Android UI → PackageOperationManager → AlpinePackageManager →
  RuntimeProcessLauncher (the M2.3 launcher, reused — same proot, loader,
  LD_LIBRARY_PATH, argv contract) → proot → real apk → real package
  database`. PocketShell never touches Alpine package files directly; every
  installed/removed claim is confirmed by `apk info -e` exit codes, versions
  by real `apk info -e -v` stdout, executables by POSIX `command -v`.
- **M2.4.4 proven in the sandbox first** (scripts/rehearse_m24_packages.sh,
  apk-tools 3.0.6 on Alpine 3.24.1 x86_64): `apk update` (28,645 packages) →
  `apk search` → `apk add nano` → `apk info -e nano` → `command -v nano` →
  nano 9.2 actually runs → `apk del nano` → `apk info -e` exit 1. Device
  validation (§9) is the actual gate — sandbox ≠ device.
- **Dedicated background exec, not a PTY session** (Option A): package
  commands run through a new `GuestCommandRunner` (ProcessBuilder on the same
  proot spec) with captured stdout/stderr and real exit codes. Nothing is
  ever typed into a user-visible terminal session; active PTY sessions are
  untouched. Non-interactive by design (stdin → /dev/null).
- **Honest operation state machine** (`PackageOperationManager`): IDLE →
  UPDATING_REPOSITORIES → INSTALLING → VERIFYING → SUCCESS/FAILED
  (UNINSTALLING for removal, SEARCHING for searches). Every state maps to
  real in-flight apk work; no percentages, no delays. SUCCESS is emitted only
  after the guest confirms BOTH the package database entry and the
  executable; verification failure after a successful `apk add` is reported
  as FAILED("refusing to claim installation"), never as success.
- **Single-flight**: one mutating operation at a time (atomic guard). A
  second concurrent request fails immediately with a readable reason — never
  queued silently, never overlapping. Operations run in a process-scoped
  scope (Activity recreation cannot kill a real apk transaction); explicit
  Cancel destroys the real guest process. Cancel/timeout handling survived a
  deep sandbox hunt: poll-based wait (a JDK-21 blocking-waitFor quirk), and
  EOF-drain with grace on cancel (an orphaned child inherits the pipe FDs and
  would otherwise hang cancellation for the orphan's lifetime).
- **Guest DNS repair (critical device discovery)**: the Alpine minirootfs
  ships NO /etc/resolv.conf, so every guest name lookup would fail. Fresh
  installs now get one written during configure; existing v0.2.x–v0.3.x
  runtimes are repaired in place before the first package operation
  (never overwriting a file that already has content). Without this the
  user's existing 9.3 MB install could never run `apk update`.
- **Curated catalog (metadata only)**: nano, htop, vim, git, python3 — tiny
  by design. The catalog cannot claim installation status (no such field
  exists; pinned by reflection test). Normal shell commands (sh/ls/cat/df/…)
  are explicitly never launcher cards.
- **UI**: Explore CLI Apps = search (real `apk search`, apk-tools 3 output
  parsed strictly, junk lines skipped) + Featured cards with real
  Install / Open / Uninstall; honest progress card with real apk output tail
  and Cancel. "Open" re-verifies READY + installed + executable, then starts
  a NEW dedicated guest session and types the launch command into THAT
  session only — exiting the app returns to the real guest shell prompt.
- **Diagnostics**: "Package environment" section with an explicit check
  button (apk version banner, configured repositories, world package count,
  DNS state) — nothing runs automatically on screen open.
- RuntimeProcessLauncher.buildLaunchSpec grew a `guestCommand` parameter
  (default unchanged: /bin/sh -l — the M2.3 contract is untouched and
  re-pinned by tests).

### Fixed
- Payload hygiene follow-up: none this cycle (v0.3.2 carried the fix).
- `apk info -e` on a runtime without resolv.conf would have failed DNS for
  ALL networked commands — see GuestEnvironment above (repaired pre-op).

### Changed
- Version 0.4.0-m2.4 (code 8). Same signing cert → direct update, installed
  runtime + installed packages kept.
- 41 new unit tests (259 total, 0 failures): parser pins from real apk-tools
  3 output, command construction, exit-code mapping, DNS repair, single
  flight, verification-gated SUCCESS, cancellation process-destroy, real
  /bin/sh process executor (streams/timeout/destroy/large-output deadlock),
  catalog invariants.

## [0.3.2-m2.3] — 2026-09-01 — device fix: guest dies at dynamic linking (`libtalloc.so not found`)

### Fixed — Linux Shell session died at exec with a linker error (user recording, Samsung SM-F711B / Android 15)
- **What the recording shows:** v0.3.1's crash fix works — install runs to
  READY (9.3 MB), tapping "Linux Shell" no longer kills the app; the session
  opens and honestly prints the guest's death:
  `CANNOT LINK EXECUTABLE "--kill-on-exit": library "libtalloc.so" not found:
  needed by main executable` → `[Process completed (code 1)]`. Two launcher
  defects, both fixed in `RuntimeProcessLauncher.buildLaunchSpec`:
  1. **`LD_LIBRARY_PATH` was never set.** proot's `DT_NEEDED` is
     `libtalloc.so` (verified at build time), which lives in the app's
     `nativeLibraryDir` — but bionic's dynamic linker searches only its
     default system paths plus `LD_LIBRARY_PATH`, never `nativeLibraryDir`.
     The exec'd proot died the instant the linker resolved dependencies. The
     sandbox rehearsal masked exactly this:
     `scripts/rehearse_m23_gate.sh` exports `LD_LIBRARY_PATH` in the shell
     (glibc happily used it), while the device has no shell to do that. Fix:
     the spec environment now carries
     `LD_LIBRARY_PATH=<nativeLibraryDir>` itself.
  2. **argv was misaligned: no argv[0].** The JNI layer execs
     `execvp(cmd, argv)` with the args array verbatim, and v0.3.1's array
     started at `"--kill-on-exit"`. Consequences visible in the recording:
     bionic quoted argv[0] as the executable name (`CANNOT LINK EXECUTABLE
     "--kill-on-exit"`), and proot's getopt — which starts at argv[1] —
     silently swallowed the flag. Fix: argv[0] is now the proot path (standard
     exec convention).
- **Preflight grew a tooth:** `preconditionProblem()` now also verifies
  `libtalloc.so` is present next to proot/loader, so this failure class is
  reported as an honest actionable message instead of spawning a process that
  can only die.
- Version 0.3.2-m2.3 (code 7). Same signing cert → direct update over
  v0.3.1, runtime data kept.
- 3 new unit tests (218 total): `LD_LIBRARY_PATH` pinned to equal
  nativeLibraryDir; argv[0] pinned to the executable with `--kill-on-exit` at
  argv[1]; missing-`libtalloc.so` preflight message pin. The argv-contract
  test now pins argv[0] too.

## [0.3.1-m2.3] — 2026-09-01 — device crash fix: Linux Shell tap killed the app

### Fixed — device crash (user recording, Samsung SM-F711B / Android 15)
- **Tapping "Linux Shell" exited the app instantly to the launcher.** Two
  independent root causes, both fixed:
  1. **Native libraries were never on the filesystem.** AGP 8 defaults to
     `extractNativeLibs=false`: `.so` files ship only inside the APK,
     `System.loadLibrary` still works (PT ran, install ran, 9.3 MB runtime
     installed fine — everything the recording shows), but
     `applicationInfo.nativeLibraryDir` is EMPTY, so the path-based execve()
     proot needs is impossible. `buildLaunchSpec`'s bare `require(proot.isFile)`
     then threw from the Compose click handler → unhandled main-thread
     exception → process death. Fix: `packaging { jniLibs {
     useLegacyPackaging = true } }` → `extractNativeLibs=true` in the merged
     manifest (verified with aapt2).
  2. **targetSdk 36 could never run the guest anyway (W^X).** AOSP policy
     (`app_neverallows.te`) neverallows `execute_no_trans` on
     `app_data_file` for every untrusted-app domain except the legacy ones,
     and `seapp_contexts` maps targetSdk 28 → `untrusted_app_27` (29+ →
     blocked domains). proot's whole job is execve()ing the guest shell
     inside app data, so no amount of loader plumbing fixes targetSdk ≥ 29 —
     this is precisely why Termux targets 28. **targetSdk 36 → 28** (the
     pre-documented Plan B, promoted by evidence; side-load distribution is
     unaffected, Android 14+ installs targetSdk ≥ 23).
- **Launch path is now crash-proof by construction** (defense in depth — a
  refused launch can never again kill the process regardless of cause):
  - `RuntimeProcessLauncher.preconditionProblem()` — pure preflight returning
    an honest, actionable reason (missing rootfs → "install or repair from
    Diagnostics"; missing proot/loader → names the directory). `buildLaunchSpec`
    now throws with exactly that message (consistency pinned by tests).
  - `TerminalViewModel` routes every spawn (Terminal / Linux Shell / CLI app /
    new session) through a single `safeSpawn` no-crash boundary; failures set
    a `launchError` StateFlow instead of propagating.
  - Home renders a dismissible error banner with a Diagnostics shortcut;
    navigation to the terminal happens only on a real spawn.
  - `TerminalSessionManager.spawn()` wraps PTY construction in try/finally so
    `_creating` can never stick true.
- If the guest process itself dies on-device (e.g. a vendor policy surprise),
  the terminal shows the exec error and `[process exited]` — visible, honest,
  and the app stays alive.

### Changed
- Version 0.3.1-m2.3 (code 6). Same debug signing cert as v0.2.x/v0.3.0
  (SHA-256 34391676…cf3f) → installs as a direct update, runtime data kept.
- 4 new unit tests (215 total, 0 failures): preflight messaging pins for the
  exact v0.3.0 crash conditions.

## [0.3.0-m2.3] — 2026-09-01 — Linux shell: proot guest behind the existing PTY

### Added
- **Linux Shell (Home card) enters the installed Alpine guest** when the
  runtime is READY: `RuntimeProcessLauncher` builds the proot launch line,
  `TerminalSessionManager.createLinuxSession()` spawns it on the SAME PTY and
  session machinery as the system shell — no second terminal implementation.
  In every other runtime state the Home card shows the truth (not installed /
  installing / failed / repair / unsupported ABI) and routes to Diagnostics.
- proot stack compiled from pinned source and bundled via jniLibs for all
  four ABIs: `libproot.so` + `libproot-loader.so` (termux/proot pinned tag
  **v5.1.107.92** @ 7266fb3e8516535682f5a9c8f3a7e70f6506eddb, GPL-2.0) and
  `libtalloc.so` 2.4.2 (LGPL-3.0+, dynamic, SONAME normalized). See
  scripts/build_proot_m23.sh + docs/THIRD_PARTY.md.
- Loader strategy (the M2 risk item): runtime env `PROOT_LOADER` points at
  `nativeLibraryDir/libproot-loader.so` — the one location that stays
  executable at targetSdk ≥ 29 — while the loader embedded in libproot.so
  remains a fallback. No execve() on app-data files, ever.
- argv contract: `--kill-on-exit --rootfs=<rootfs> --root-id --cwd=/root
  --bind=/dev --bind=/proc --bind=/sys /bin/sh -l`. Long options use the
  joined `=` form (proot rejects the separated form — rehearsed and pinned
  by unit tests).
- 8 new unit tests (211 total, 0 failures): argv/env pins, missing-artifact
  refusal, READY-only gate.

### Verified (sandbox rehearsal)
- The exact launch contract was rehearsed end-to-end on the sandbox host
  with the SAME proot source and the x86_64 variant of the SAME pinned
  Alpine 3.24.1 rootfs: `uname; id; echo hello; cat /etc/alpine-release`
  returned the guest kernel view, uid=0(root), hello, 3.24.1 and a working
  BusyBox — in BOTH loader modes, exit 0 (scripts/rehearse_m23_gate.sh).
- The Android-specific exec/ptrace policy is the remaining risk and stays
  the M2.3 device gate (docs/TESTING.md §8).

### Changed
- versionCode 5, versionName 0.3.0-m2.3.

## [0.2.1-m2.2] — 2026-09-01 — Fix: crash when tapping "Install Linux environment"

### Fixed
- **The app crashed (silent process death, no dialog) immediately after tapping
  "Install Linux environment" in Diagnostics** (observed in a device screen
  recording: spinner on the button → instant return to launcher). Two
  independent defects, both fixed:
  1. *Missing `INTERNET` permission.* The M1 app legitimately needed no
     network, and the manifest said so. M2.2 added a real HTTPS downloader but
     nobody revisited the permission set — the first connect threw
     `SecurityException("Permission denied (missing INTERNET permission?)")`.
     Fix: `INTERNET` is now declared, with the honest justification inline
     (used solely to fetch the checksum-pinned Alpine minirootfs from
     dl-cdn.alpinelinux.org).
  2. *No crash containment around runtime coroutines.* The installer converts
     its own failures to `FAILED` + a `Failed` event but rethrows; nothing
     caught the rethrow, so ANY pipeline failure (missing permission, offline
     device, DNS failure, even an `Error`) killed the whole app mid-install.
     Fix: `RuntimeCrashGuard` contains install/remove failures — they now land
     in the retryable `FAILED` / `REPAIR_REQUIRED` states the state machine
     already designed for them. A scope-level `CoroutineExceptionHandler` is
     the last-resort net.

### Added
- `RuntimeCrashGuard` (internal): the containment extracted into a unit-testable
  unit; `RuntimeManager` delegates to it.
- Regression tests (`RuntimeCrashGuardTest`, 6 new): the incident's exact
  `SecurityException` driven through the REAL installer pipeline must land in
  `FAILED` with transient state cleaned and must NOT escape; non-Exception
  `Error`s likewise; successful install passes through to `READY`; partially
  undeletable runtime on remove lands `REPAIR_REQUIRED` (and `clearRuntime()`
  returning false is now treated as the failure it is); retry transitions from
  `FAILED`/`REPAIR_REQUIRED` remain legal.

### Notes
- Emulator re-verification was attempted in the sandbox and is NOT possible
  there: Android 11+ (API 30+) system images are the only ones exposing
  `arm64-v8a` (required by the arm64-gated runtime), and the emulator enforces
  a fixed ~6 GB userdata floor for them (~7.2 GiB free required at boot) which
  cannot coexist with the SDK on the 9.9 GB sandbox disk. DEVICE VALIDATION
  for the install flow remains the M2.2 gate (docs/TESTING.md).
- Live CDN re-check (2026-09-01): pinned Alpine 3.24.1 aarch64 minirootfs
  still matches `SIZE_BYTES` + `SHA256` byte-for-byte.
- 58 app-module unit tests (52 + 6 new), 145 terminal-emulator tests, all
  green; assembleDebug clean.

## [0.2.0-m2.2-wip] — 2026-09-01 — M2.2: real Linux runtime installation layer

### Added
- `runtime/` package: Linux runtime installation vertical slice
  (docs/M2-ARCHITECTURE). Alpine minirootfs 3.24.1 (aarch64) pinned by URL,
  size and SHA-256; pipeline DOWNLOADING → VERIFYING → EXTRACTING →
  CONFIGURING → atomic promotion → READY.
- `RuntimeManager` facade + `RuntimeState` machine (illegal transitions
  rejected; state derived from disk at startup, incl. orphaned-tmp recovery
  and honest REPAIR_REQUIRED for damaged metadata).
- Safe extractor: zip-slip guard, GNU longnames, symlink/hardlink handling,
  POSIX mode preservation. Validated against the REAL Alpine 3.24.1 aarch64
  minirootfs (410 files, 635 symlinks) in a sandbox test run.
- Diagnostics screen: Linux runtime section (state, size, free space,
  metadata, install/retry/remove controls). No Home screen changes.
- New dependency: commons-compress 1.28.0 (Apache-2.0).

### Notes
- proot integration is M2.3; no process execution exists yet in M2.2.
- DEVICE VALIDATION REQUIRED: install flow must be exercised on real
  hardware (download over mobile network, state transitions, recovery).

## [0.1.1-m1] — 2026-08-31 — Fix: terminal did not repaint on session output

### Fixed
- **Terminal did not show typed input while the built-in keyboard was visible;
  text only appeared after toggling the keyboard off** (observed in a device
  screen recording). Root cause: the vendored `TerminalView` follows the
  upstream contract that the *host* must call `TerminalView#onScreenUpdated()`
  when a session's screen changes — the view never observes session data
  itself. Our `PocketShellSessionClient.onTextChanged` was an empty stub with
  an incorrect comment ("TerminalView invalidates itself").
  Fix: session clients now forward screen updates through a new
  `TerminalSessionManager.onScreenUpdateListener` hook, installed by
  `TerminalScreen` to call `onScreenUpdated()` (main thread — `TerminalSession`
  dispatches via its `MainThreadHandler`).
- Cursor never blinked: `setTerminalCursorBlinkerState` (upstream-documented
  host duty) was never called. Now started in `onEmulatorSet` and toggled with
  host lifecycle (ON_RESUME/ON_PAUSE).
- `TerminalView` was never focused: hardware (Bluetooth) keyboard input could
  not reach the terminal. The view now takes focus after attach.

### Verification
- All 166 unit tests pass; APK rebuilt and re-signed as `v0.1.1-m1`
  (versionCode 2). Regression items added to `docs/TESTING.md` §4 for the
  mandatory on-device re-check.

## [0.1.0-m1.1 / m1.2 / m1.3] — 2026-08-31 — Keyboard hardening · Input reliability · Polish

### M1.1 — Built-in keyboard hardening
- System IME fully suppressed inside the terminal (window `SOFT_INPUT_STATE_ALWAYS_HIDDEN`;
  PocketShell keyboard is the sole typing surface, brief §7).
- Terminal font size state plumbing (default from Settings, live pinch changes).
- App shell refactor: screens wired through a single root with settings-aware theming.

### M1.2 — Input reliability
- Foreground service (`specialUse`) keeps real session processes alive while
  backgrounded; runs only while sessions exist, stops itself when the last one
  closes/finishes (brief §24); notification states session count truthfully.
- Pinch-to-resize font with PTY reflow (scale thresholds → `setTextSize` →
  upstream `TIOCSWINSZ`).
- Clipboard copy/paste via upstream selection ActionMode (wired through
  `PocketShellSessionClient` to the system clipboard).

### M1.3 — UI/UX polish
- Settings screen: theme mode (System/Light/Dark/AMOLED), dynamic color
  (Android 12+, honest fallback), default terminal font size (DataStore-persisted).
- Diagnostics screen: read-only runtime facts only — version, API level, device,
  ABIs, shell presence, PTY library state, HOME/TMPDIR, real session counts,
  exact permission list (brief §34).
- Theme system: restrained brand scheme + AMOLED pure-black surfaces + dynamic
  color where the platform provides it.
- Home header gains Settings/Diagnostics entries.

### Verification
- Full build + all unit tests pass.
- **Manual on-device acceptance for M1/M1.1/M1.2/M1.3 remains pending
  (docs/TESTING.md §3–§5) — mandatory human step before M2 (brief §27/§28).**

---

## [0.1.0-m1] — 2026-08-31 — M1: Terminal Foundation

### Added
- Gradle skeleton: settings/root/version catalog (`gradle/libs.versions.toml`),
  wrapper (Gradle 8.14.5), pinned AGP 8.13.2 / Kotlin 2.4.10 / platform 36 /
  NDK 28.2.13676358.
- Vendored `:terminal-emulator` + `:terminal-view` (byte-identical upstream
  sources at pinned commit; Kotlin-DSL build ports documented in THIRD_PARTY.md).
- `ShellEnvironment` — real `/system/bin/sh` runtime (HOME/TMPDIR/TERM/PATH),
  executable resolver.
- `TerminalSessionManager` — process-scoped multi-session owner; per-session
  PTY/env/cwd/title; finished sessions marked, never faked; CLI-app session
  factory with executable verification.
- `PocketShellSessionClient` / `PocketShellTerminalViewClient` — upstream
  client implementations bridging events, clipboard, logging and keyboard
  modifier hooks.
- PocketShell keyboard v1 (full §8 coverage): QWERTY + digits + 28-symbol page
  + extended row (INS/DEL/HOME/END/PGUP/PGDN), ESC/TAB/ENTER/BACKSPACE,
  arrows, FN layer (F1–F12, HOME/END/PGUP/PGDN, DEL), one-shot/locked
  modifiers with always-visible state, hold-to-repeat, haptics,
  phone/tablet adaptive layouts.
- `TerminalKeyDispatcher` — single input pipeline: keyboard → synthetic
  KeyEvents → vendored TerminalView → upstream KeyHandler → PTY.
- Compose UI: Home (honest empty states), Terminal (tabs + TerminalView +
  keyboard), Explore placeholder (honest M2 notice); navigation without extra
  dependencies.
- CLI app architecture: `CliApp` model, DataStore registry (empty by default),
  verified launcher.
- Tests: upstream emulator suite (19 classes, all pass), keyboard state
  machine, §8 symbol coverage, FN remaps, CLI app model/resolver.

### Fixed
- Kotlin `KeyAction.Char` name clashed with `kotlin.Char` → renamed to
  `KeyAction.Text`.
- `KeyCharacterMap.getEvents` requires an instance; use `load(VIRTUAL_KEYBOARD)`.
- Upstream `TerminalView` exposes only `(Context, AttributeSet)` constructor.

### Verification
- `./gradlew :app:assembleDebug` → 20 MB debug APK containing `libtermux.so`
  for all 4 ABIs, 16 KB-aligned (align 2**14).
- All unit test suites pass (emulator + app).
- **Manual on-device acceptance pending (docs/TESTING.md §M1) — requires a
  human with real hardware; no device exists in this sandbox.**

---

## [0.1.0-m0] — 2026-08-30 — M0: Research + Architecture

### Added
- Project brief compliance docs: `README.md`, `docs/RESEARCH.md`,
  `docs/ARCHITECTURE.md`, `docs/THIRD_PARTY.md`, `docs/ROADMAP.md`,
  `docs/TESTING.md`, `docs/CHANGELOG.md`.
- Build environment installed and pinned: Gradle 8.14.5, AGP 8.13.2,
  Kotlin 2.4.20, SDK platform android-36, build-tools 36.0.0, NDK 28.2.13676358.

### Decisions
- D1: vendor Termux `terminal-emulator` + `terminal-view` at pinned upstream
  commit `3b66f8799635a4dba4a206563048ff0e6792c487` (GPLv3 consequence accepted).
- D2: reuse upstream PTY JNI (`libtermux`) verbatim.
- D3: M1 shell = `/system/bin/sh` + toybox applets; no userspace runtime until M2.
- D4: PocketShell keyboard routes input as synthetic KeyEvents through the
  upstream `TerminalView` pipeline with `KeyboardState` answering modifier hooks.

---
