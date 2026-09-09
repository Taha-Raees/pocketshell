# PocketShell — Roadmap

Status: M0 complete · Strict milestone discipline per §27 of the project brief.
**A milestone is complete only after: automated tests + APK build + real-device
installation + manual acceptance checklist. Compilation alone is never
sufficient (§28).**

---

## M0 — Research + Architecture ✅ (this repository, 2026-08-30)

- [x] Repository inspected (greenfield; no prior Android work present).
- [x] Build environment installed and pinned (see RESEARCH.md §7).
- [x] Ecosystem research: Termux vs alternatives; W^X / 16 KB / FGS / scoped
      storage constraints verified.
- [x] Upstream audit: `terminal-emulator` + `terminal-view` verified
      self-contained, GPLv3, actively maintained; client interfaces + JNI
      surface documented.
- [x] Docs: RESEARCH / ARCHITECTURE / THIRD_PARTY / ROADMAP (+ README,
      TESTING, CHANGELOG).
- [x] Integration decisions D1–D4 recorded.

## M1 — Terminal Foundation (in progress)

Goal: Open PocketShell → tap Terminal → real terminal → real PTY → real shell
→ real command (`echo pwd ls cd mkdir rm cat clear`).

- [x] Gradle skeleton (pinned toolchain, version catalog, wrapper).
- [x] Vendored terminal modules integrated and building.
- [x] `TerminalSessionManager` + `/system/bin/sh` shell environment.
- [x] Home screen with honest empty states (Terminal card primary; Installed
      CLI Apps empty; Explore placeholder; sessions only when real).
- [x] Terminal screen: session tabs + `TerminalView` + basic bottom bar.
- [x] CLI app data model + registry (empty) + verified launcher path.
- [x] APK builds (`assembleDebug`); upstream emulator test suite passes on JVM.
- [ ] **Manual on-device acceptance (TESTING.md §M1)** — requires a human with a device.

## M1.1 — Built-in PocketShell keyboard

- [x] Keyboard component: full letter/number/symbol coverage, Enter/Backspace/Space.
- [x] Terminal keys: ESC TAB CTRL ALT SHIFT FN, arrows, HOME END PGUP PGDN INS DEL, F1–F12.
- [x] One-shot + locked modifier states with always-visible indication.
- [x] Phone compact / tablet expanded adaptive layouts.
- [x] Single input pipeline via synthetic KeyEvents + `KeyboardState` → upstream hooks.
- [x] Encoding tests for every key class (letters, symbols, all special keys).
- [x] System IME suppressed inside the terminal (keyboard is the sole input, §7).
- [ ] **Manual on-device acceptance (TESTING.md §M1.1)**.

## M1.2 — Terminal input reliability

- [x] Modifier combos stress set: CTRL+C/D/Z/L/A/E/W, TAB, ESC, arrows, HOME/END, F1–F12 (encoding via upstream KeyHandler + tests).
- [x] Interactive programs where available: vim/nano/top/less/tmux (rendering + sequences supported by vendored engine).
- [x] Clipboard copy/paste wiring (upstream ActionMode + session client).
- [x] Selection (long-press word → handles), double-tap word select, triple-tap line select (upstream).
- [x] Pinch font size (scale thresholds → `setTextSize` → PTY reflow).
- [x] Rotation / background / foreground / activity recreation survival (`configChanges` + process-scoped manager).
- [x] Foreground service (specialUse) for background session retention; self-stops when last session ends.
- [x] Multiple concurrent sessions; no cross-session state leakage (manager design + unit-tested model).
- [ ] **Manual on-device acceptance (TESTING.md §M1.2)**.

## M1.3 — UI/UX polish

- [x] Light / Dark / AMOLED / Dynamic Color themes (restrained M3).
- [x] Settings screen (theme mode, dynamic color, default font size — DataStore).
- [x] Diagnostics screen (versions, ABI, shell, session/PTY facts — read-only).
- [x] Keyboard layout/touch-target refinement; session tab polish; tablet layout.
- [x] Performance pass prepared: `yes` + CTRL+C, `seq 1 100000`, `find /` checklist (TESTING.md §5).
- [ ] **Manual on-device acceptance (TESTING.md §M1.3)**.

## Gate to M2

M2 starts **only after** M1, M1.1, M1.2, M1.3 are manually validated on a real
Android device (§27). M2 scope (research → implement):

- Linux userspace runtime decision (Termux-compatible vs Alpine/proot — see
  RESEARCH.md §3.3 W^X constraint and §6 packaging research; re-verify targetSdk
  policy at that time).
- Real package management (install/remove/update/search/info) with PocketShell
  UI sitting on top — never simulated.
- First installable CLI apps (Hermes/Git/Python/Node/… per catalog).
- Home "Explore CLI Apps" becomes a real catalog backed by the real installer.

## M2 — Real Linux runtime (M2.0 – M2.4)

- [x] M2.0 baseline: 166/166 tests PASS before any change (strict §27/§28).
- [x] M2.1 research + architecture (docs/M2-RESEARCH.md, docs/M2-ARCHITECTURE.md):
      PRoot + Alpine minirootfs chosen; split-loader strategy verified against
      AOSP sepolicy primary sources; targetSdk kept at 36.
- [x] M2.2 runtime installation layer: download → verify → extract →
      configure → atomic promote; honest state machine; crash containment
      (RuntimeCrashGuard after the v0.2.1 device incident). **Device gate
      PASSED 2026-09-01** (TESTING.md §7): install → READY, honest numbers
      verified byte-level ("9.3 MB", 108 rootfs files).
- [x] M2.3 Linux shell: proot **v5.1.107.92** (@ 7266fb3e) + libtalloc 2.4.2
      compiled for all 4 ABIs and bundled via jniLibs;
      `RuntimeProcessLauncher` + `createLinuxSession` enter the guest on the
      existing PTY; sandbox rehearsal of the exact gate contract PASSED
      (scripts/rehearse_m23_gate.sh): guest `uname`, `uid=0(root)`, `hello`,
      release 3.24.1, BusyBox works — both loader modes.
      **Device gate PASSED 2026-09-01** (TESTING.md §8, Samsung SM-F711B):
      `uname; id; echo hello`, `cat /etc/alpine-release` → 3.24.1,
      `ls /usr/bin | head`, clean `exit`. (v0.3.1 fixed the tap-crash:
      extractNativeLibs + targetSdk 28; v0.3.2 fixed the guest linker death:
      LD_LIBRARY_PATH + argv[0].)
- [x] M2.4 real package management foundation: `AlpinePackageManager` runs
      the REAL `apk` (apk-tools 3.0.6) inside the guest through the same
      proot exec infrastructure — update/search/add/del/info, exit-code +
      `apk info -e -v` + `command -v` verification (no human-output state
      guessing), honest operation state machine, single-flight, dedicated
      background exec (never touches user PTY sessions), guest DNS repair
      (minirootfs ships no resolv.conf), curated 5-entry metadata-only
      catalog, Open-into-new-session launcher. Sandbox rehearsal PASSED
      (scripts/rehearse_m24_packages.sh); **device gate = TESTING.md §9**.
- [x] **M2.4 device gate (TESTING.md §9)** — **PASSED on device 2026-09-02**
      (Samsung SM-F711B, v0.4.3 screenshots: GNU nano 9.2 running in the
      Alpine guest, Repository fetch OK — 28546 distinct packages; v0.4.4
      screenshots: installed state visible in Explore AND Home). The v0.4.1→
      v0.4.4 chain fixed, in order: device-DNS reachability, the SELinux
      linkat neverallow, single-resolver DNS fragility, the installed-state
      batch-probe exit-code misread + the never-written Home registry, and
      the empty clipboard-paste callback.

## Later (unscheduled, do not start prematurely)

M2.5 CLI app catalog + installed-apps Home integration · file manager ·
profiles · AI CLI management · development environments · code editor ·
remote development.

### M2.5 (started 2026-09-02, v0.5.0)
- [x] Installed-apps Home integration (v0.4.4): Home renders exactly the
      catalog subset the real apk database confirms — M2-ARCHITECTURE §9
      contract realized (device-confirmed: Nano + Git cards).
- [x] Apk-capable guest shell (v0.5.0): interactive sessions drop /proc and
      share the app's apk cache — manual `apk update` / `apk add` works in
      the shell (was: SELinux "Permission denied" + stale 31-package cache).
- [x] Install any searched package (v0.5.0): search hits ranked by name
      match (nodejs first for "node") and installable with the honest
      pipeline; no executable promises for non-catalog packages.
- [ ] Remaining M2.5 candidates: file manager, profiles, richer per-app
      Home cards (launch metadata for non-catalog packages), development
      environments.

### M2.6 (2026-09-02, v0.6.0) — Linux compatibility & /proc architecture fix
- [x] Reproduce + document the M2.5 regression: /proc removed from ALL guest
      sessions → ps/top/htop broken (docs/M2.6-RESEARCH.md §1/§2).
- [x] Root cause verified from primary sources: apk-tools 3.0.x
      `is_proc_fd_ok()` → O_TMPFILE + linkat("/proc/self/fd/N") commit →
      AOSP `neverallow all_untrusted_apps file_type:file link` → EACCES →
      whole-download cancel, no fallback (identical in 3.0.6/3.0.8/master).
- [x] Options evaluated A–G (two profiles only, transport wrapper, nested
      proot, /proc/self/fd masking, apk 2 rollback, patched guest apk);
      chosen: one-byte checksum-pinned libapk patch + explicit profiles
      (docs/M2.6-RESEARCH.md §3/§4).
- [x] GuestApkCompat: hash-driven, idempotent installer/verifier for the
      patched guest apk library; honest Ready/NotApplicable/Failed states;
      asset verified against its pinned sha256 before anything is written.
- [x] GuestExecutionProfile INTERACTIVE_TERMINAL / PACKAGE_OPERATION on the
      SAME builder/launcher (no duplicated runtime); package profile
      refuse-guarded against /proc; tests pin no-drift.
- [x] Interactive sessions bind a REAL /proc again when the patch is
      verified; honest v0.5.0-shape degradation otherwise; Diagnostics rows
      ("apk fd-link patch", "Interactive /proc").
- [x] 292 tests/variant (584 executions) green; host rehearsal
      (rehearse_m26_proc.sh) green.
- [ ] Device gate §10 (Gates A–G: /proc, ps/top/htop, apk lifecycle, Node
      end-to-end, app-side install, interactive CLI, session isolation).
- [ ] Post-M2.6 candidates (per user direction): M2.7 session management +
      CLI app profiles, or curated CLI app catalog.

### Phase 3.1 (2026-09-03, v0.7.0-m3.1) — Terminal Experience Redesign ("Midnight Sapphire")
- [x] Design contract committed before implementation
      (docs/PHASE-3.1-DESIGN.md, plan-first discipline).
- [x] Terminal screen only: chrome + editor-style session tabs (active tab
      merges into the canvas) + blue-dark surface stack; zero pure black;
      rest of the app untouched.
- [x] Terminal identity: JetBrains Mono NL (OFL, no-ligature build) + real
      16-color ANSI palette override (OSC still wins) + Sapphire accent
      block cursor.
- [x] Keyboard rebuilt from scratch per the final spec: `Esc Tab ←↑↓→` ·
      collapsible QWERTY (pages incl. terminal punctuation row) ·
      `[⌨] Ctrl Alt Space Shift Enter`; dedicated Fn key REMOVED — F1–F12
      via number-row long-press; no system IME anywhere.
- [x] 628 test executions green (keyboard contract tests rewritten to the
      new layout); assembleDebug OK.
- [ ] Device gate §12 (visual sweep, exact keyboard layout, Ctrl/Alt/Shift
      combos, Fn long-press, toggle, Linux regression set).

### Phase 3.2 (2026-09-04, v0.7.0-m3.2) — Home / OS Launcher + Command Apps
- [x] Design contract committed before implementation
      (docs/PHASE-3.2-DESIGN.md, plan-first discipline).
- [x] Home screen ONLY: an OS launcher (identity → foundations → apps →
      sessions → floating actions), not a dashboard; Midnight Sapphire
      identity continues (no pure black, no gradients on this page, one
      Sapphire accent); Phase 3.1 terminal + keyboard untouched.
- [x] Packages ≠ Apps architecture: Home no longer renders installed
      packages (forbidden list test-pinned); new command-launchable app
      registry + guest-driven classification; login-shell availability probe
      (matches what the user's typing sees; uv launchers reachable);
      verify-then-launch into a dedicated guest session (typed-command
      fidelity); honest empty/checking/probe-failure states (v0.4.4 rule).
- [x] Floating quick-action system (custom, extensible, real actions only) +
      edge-to-edge launcher + per-screen status-bar icon coordination +
      responsive grid (3/4/6 columns, 720dp cap on tablets).
- [x] 644 test executions green (628 baseline + 8 new CommandApps
      invariants); assembleDebug OK; versionCode 19 / 0.7.0-m3.2.
- [ ] Device gate §13 (no packages on Home; Hermes tile appears iff
      available and launches by tap; Terminal/Linux/sessions unchanged;
      FAB actions; phone + tablet layout).

### Phase 3.3 (2026-09-04, v0.7.0-m3.3) — Home & System UI Redesign
- [x] Design contract committed before implementation
      (docs/PHASE-3.3-DESIGN.md, plan-first discipline).
- [x] Surface philosophy enforced: content on the canvas; separation by
      spacing / section labels / hairline dividers / tone steps; surfaces
      ONLY for real objects (environments, menus, floating control, banner,
      pressed states). No cards for text groupings, no cards in cards,
      radius ≤ 16dp, no decorative borders.
- [x] Home restructured: borderless Terminal/Linux foundation tiles (fixed
      truncation), "Your tools" icon+label launcher grid (52dp borderless
      plates), lightweight inline empty state (exactly ONE packages
      affordance in every state), flat divider-separated session rows.
- [x] ONE CLI control: header `CLI Apps ▾` trigger (only when apps exist)
      opening a compact Midnight launcher menu over the unchanged Phase 3.2
      discovery/launch pipeline; floating CLI affordances removed.
- [x] FAB single-purpose: create sessions only (New Terminal / New Linux
      session), text-only chips; QuickAction model simplified to
      id/label/enabled/onRun.
- [x] Other pages reviewed (Settings/Diagnostics already flat; Packages
      cards = real objects — allowed); no code changes needed.
- [x] 644 test executions green (baselines + CommandAppsTest untouched);
      assembleDebug OK; versionCode 20 / 0.7.0-m3.3; cert chain unbroken.
- [ ] Device gate §14 (no-boxes sweep, CLI menu honesty, empty-state
      lightness, FAB purpose, §12/§13 regressions).

### Phase 3.4 (2026-09-04, v0.7.0-m3.4) — Registry Expansion + System Pages
- [x] Design contract committed before implementation
      (docs/PHASE-3.4-DESIGN.md, plan-first discipline).
- [x] Root-caused "installed Kilo CLI doesn't show up": discovery = registry
      ∩ guest PATH; `kilo` was unregistered, so the probe never asked about
      it (honesty contract — never guess from unknown binaries).
- [x] Registry expanded by data only: Kilo Code (`kilo`), Gemini CLI
      (`gemini`), Codex (`codex`), Aider (`aider`), Qwen Code (`qwen`) —
      appended after the brief's four (order stability), probe-gated like
      every app, forbidden-namespace excluded; 4 new test pins.
- [x] Packages screen: not-ready state inline on the canvas with a real
      `Open Diagnostics` link (container deleted); title → "Packages";
      package-object cards kept (§4-allowed).
- [x] Settings: whole-row selection targets ≥48dp with correct roles
      (visual language unchanged).
- [x] Diagnostics: uniform section pattern — System / Linux runtime /
      Package environment headers + plain fact rows, no internal dividers.
- [x] 648 test executions green (644 baseline + 4 new); assembleDebug OK;
      versionCode 21 / 0.7.0-m3.4; cert chain unbroken.
- [ ] Device gate §15 (kilo appears iff installed and launches; Packages/
      Settings/Diagnostics checks; §12/§13/§14 regressions).

### Phase 3.5 (2026-09-04, v0.7.0-m3.5) — Tap-to-Launch Fix + Midnight System Pages
- [x] Root-caused the "Kilo tile opens a plain shell" video report: the
      command was PTY-written right after session CONSTRUCTION, but
      TerminalSession forks lazily on first view render and write() drops
      bytes while no process exists — silently, for every app. Fixed by
      passing the launch chain (`sh -l -c "<cmd>; exec sh -l"`, pure
      `guestLaunchChain`) through ARGV — deterministic, no PTY timing.
- [x] Midnight design kit (ui/system/MidnightPage.kt) + Diagnostics /
      Packages / Settings rewritten on it; light status-bar icons on all
      five screens.
- [x] 656/0 tests; versionCode 22 / 0.7.0-m3.5; payload + delivery chain
      live (docs/PHASE-3.5-DESIGN.md).
- [ ] Device gate §15 (kilo TUI opens on tap).

### Phase 3.6 (2026-09-04, v0.7.0-m3.6) — Procfs Contract: /proc unconditional + apk fd-link self-repair
- [x] Root-caused the "kilo dies with ENOENT on an existing directory"
      device report: an in-guest `apk upgrade` replaced the checksum-pinned
      patched libapk, the M2.6 conditional /proc gate failed, every new
      session silently spawned WITHOUT /proc — and Bun CLIs (Kilo Code)
      resolve paths via /proc/self/fd on aarch64 (no realpath syscall), so
      realpath() of existing paths returned ENOENT.
- [x] /proc bind made ABSOLUTE for every interactive session (policy
      derived from the profile — the `procEnabled` parameter no longer
      exists); PACKAGE_OPERATION stays /proc-free (require-guarded).
- [x] GuestApkCompat rebuilt as a pattern-based SELF-REPAIR: scans the
      guest's libapk.so.3* for the fd-link gate literal + the io.c format
      literal, applies the same one-byte patch to ANY apk-tools build that
      carries them (ambiguous/alien shapes refused without writes). Byte
      equivalence with the M2.6 asset re-proven on the pinned minirootfs
      (repair output sha256 == PATCHED_LIBAPK_SHA256).
- [x] Spawn-time environment audit: procContractProblem() checks every
      interactive spec for /proc + /dev + /sys before it can be returned —
      fail-loud, never a silently broken guest.
- [x] Contract documented in docs/PROCFS-CONTRACT.md (launch architecture,
      per-session bind audit incl. /dev /dev/pts /sys /tmp, validation
      layers, regression procedure).
- [x] Antigravity CLI 404 diagnosed end-to-end with real network probes and
      executed binaries (docs/ANTIGRAVITY-PLATFORM.md): linux_arm64_musl is
      UNSUPPORTED BY UPSTREAM (no musl manifests at all); glibc build runs
      on real glibc but not on Alpine gcompat; nothing patched, checksums
      intact. Guest diagnostic added: scripts/diagnose_platform.sh.
- [ ] Device gate §16 (fresh session: /proc/self, /proc/version, ps, kilo
      end-to-end WITHOUT manual mount; §12–§15 regressions).

### Phase 4.0 (2026-09-05, v0.7.0-m4.0) — Companion: the embedded web workspace
- [x] Design contract committed BEFORE implementation
      (docs/PHASE-4-COMPANION-DESIGN.md): purpose, architecture, data
      model, WebView lifecycle, persistence, tab/memory strategies, drag +
      gesture policies, chooser/download/back/external-link policies,
      security considerations, UI structure, test + device plans.
- [x] Engine research: android.webkit System WebView chosen (zero new
      dependencies; Chromium renderer in its own sandboxed process;
      Play-updated). GeckoView (massive), Custom Tabs (external browser
      UI) and every AI/API approach rejected per the brief.
- [x] Companion data model = Name + URL, fully generic; fail-closed
      validation (https allowlist at definition AND navigation time;
      javascript:/file:/data:/about:/intent:/blob: rejected in both).
- [x] Persistence: DataStore "companion" (defs JSON, default, open tabs
      w/ cold-restore anchors, active tab, panel height); cookies + DOM
      storage persist via the platform WebView profile (flush at pause);
      honest boundary documented (page state survives tab switches and
      backgrounding, not process death — same as real mobile browsers).
- [x] CompanionLayer: bottom drag handle ONLY (no floating button, no
      labels, brief R1); 1:1 drag with frozen WebView height (page never
      reflows under the finger); gentle 6% snap to half/near-full anchors,
      otherwise stays put; height persisted; `.imePadding()` for chat
      inputs; handle reachable at every height (never trapped).
- [x] Tabs: inverted Phase 3.1 editor language; switch without reload;
      process-scoped WebView pool (active + 4 LRU, saveState-on-evict,
      restore-on-reactivate, onTrimMemory drops background pages first);
      close-neighbor selection; "+" focuses an existing tab of the same
      Companion instead of duplicating.
- [x] Policies shipped: Back = web history → collapse → normal navigation;
      file chooser via the normal Android picker; downloads via
      DownloadManager into app-private storage (no permission, no crash);
      mailto/tel/intent resolved to the system with an honest Toast on
      failure; camera/mic/geo denied.
- [x] Settings → Companion: add/edit/delete (inline Midnight editor),
      quick-add templates as pre-fills, default Companion rows, Clear web
      data (quiet destructive).
- [x] 19 unit pins (CompanionTest): validation, tab reducer, back
      decision, height math, JSON round-trips — no WebView fakes.
- [ ] Device gate §17 (login persistence, drag experience, tabs, upload,
      navigation, performance, §12–§16 regressions).

### Phase 4.0.1 (2026-09-05, v0.7.0-m4.0.1) — Hotfix: startup decoupled from WebView provider health
- [x] Device-reported crash: m4.0 crashed on EVERY launch (Samsung/microG,
      freshly updated WebView package); Samsung Device Care offered the
      WebView rollback. Root cause: `CookieManager.getInstance()` in
      `Application.onCreate` loaded the whole WebView provider before any
      UI — a broken provider killed every start of the whole app.
- [x] Fix: Application startup is WebView-free (context handoff only);
      cookie config + WebView creation lazy and guarded; failure flips
      `runtimeFailed` → Companion shows an honest "Companion unavailable"
      notice while every other screen keeps working; `pauseAll` /
      `clearWebData` guarded — no path crashes on a broken provider.
- [ ] Device gate §18 (starts with broken WebView; notice renders;
      recovery after WebView repair; §17 + §12–§16 spot-checks).

### Phase 4.0.2 (2026-09-05, v0.7.0-m4.0.2) — Honest Companion failure surfaces
- [x] Device finding: after the m4.0.1 startup fix, the Companion canvas
      rendered PURE WHITE (ChatGPT tab) with no error — silent failure
      violates the project's honesty rule.
- [x] Fix: main-frame load failures render an in-canvas Midnight card
      (real net::ERR string + installed WebView version + hint + Retry);
      `onRenderProcessGone` destroys only the crashed view (default would
      kill the app) and reports "Page renderer crashed"; the provider-
      broken notice shows the WebView version; Retry re-creates the tab.
      Honest boundary documented (silent-JS-failure blank pages fire no
      event — identified via version line + static-site test).
- [x] +4 unit pins on the pure failure model (one caught a real
      blank-string defect). Full suite: 712/0.
- [ ] Device gate §19 (failure cards, Retry, example.com isolation,
      §17/§18 spot-checks).

### Phase 4.0.3 (2026-09-05, v0.7.0-m4.0.3) — Keyboard everywhere + render-stall honesty + Companion picker
- [x] Device bug batch from the m4.0.2 session (screenshot analyzed):
      keyboard terminal-only, keyboard sandwiched under/behind the
      Companion panel, no clean toggle-off, dead "-" key, tiny arrows,
      STILL-white canvas, "+" doing nothing.
- [x] KeyboardInputRouter: deck presses route to the focused surface
      (terminal canvas OR active Companion WebView; last tap wins) — the
      Companion is typeable from the same deck. System IME hard-blocked
      while the deck is up; unblocked (with imePadding lift) when toggled
      off. Pure routing logic unit-pinned (5 tests).
- [x] Stacking: deck height reported to the root; Companion panel and its
      picker ride ABOVE the deck (nothing under the keyboard); toggling
      off unmounts the WHOLE deck and floats a small Midnight keyboard
      icon at the bottom-right corner to bring it back.
- [x] Keys: quick taps on long-press-capable keys (digit row, "-", tablet
      -/=/`) now commit the primary action on release (root cause of the
      dead "-"); arrow keys 12dp longer horizontally.
- [x] White canvas: WebView now created with the ACTIVITY context (was
      application context — blank-canvas source on OEM builds); load path
      guarded; 15s render-stall watchdog turns "paints nothing" into the
      honest "Page never rendered" card (+ WebView version); Retry
      alternates GPU → SOFTWARE rendering (compatibility mode) per tab.
- [x] "+": opens a Midnight picker sheet (Companion list, open tabs
      marked) + "Add Companion" → management page; Back closes the sheet.
- [x] +6 unit pins per variant. Full suite: 724/0.
- [ ] Device gate §20 (§17–§19 regressions included).

### Phase 4.0.4 (2026-09-05, v0.7.0-m4.0.4) — The cookie-banner lesson: pixel-truth stall detection + one-spot keyboard toggle
- [x] Device evidence closed the black-canvas mystery: the site's OWN
      cookie-consent banner painted at the bottom of an otherwise dead
      canvas — load events fire faithfully on this device's WebView
      build, so m4.0.3's event-based watchdog never fired (and the
      "any differing pixel" rule would have read the banner as health).
- [x] RenderProbe (pixel-truth watchdog rewrite): two readbacks per tab
      (software draw into a tiny bitmap; API 29+ PixelCopy of the window
      as PRESENTED), six probes ≈ 15s; only real page pixels stand it
      down. The verdict samples the MAIN region only (above the bottom
      25% — the consent-bar dock), so partial paint never vouches for a
      dead page; arithmetic pure + unit-pinned (2 pins/variant).
- [x] First stall self-heals silently on the SOFTWARE renderer; only a
      second stall raises the honest card (title + WebView version).
- [x] Failure card escape hatches: Retry (alternates GPU/SOFTWARE, lifts
      dismissal), "Open in browser" (site-vs-device diagnosis in the
      user's real browser), "Continue anyway" (raw canvas; the probe
      stays quiet for that tab until a Retry).
- [x] Keyboard toggle consistency: [⌨] moved into the deck row slot
      between Space and Enter (Ctrl · Alt · Space · Shift · [⌨] · ⏎);
      toggled off, the SAME rectangular key box parks at that right-hand
      spot (the m4.0.3 round corner bubble is gone).
- [x] versionCode 28. Full suite green: 0 failures (all modules ×
      variants).
- [ ] Device gate §21 (§17–§20 regressions included).

### Phase 4.0.5 (2026-09-05, v0.7.0-m4.0.5) — The black page, fixed at the root — and the page now testifies
- [x] Diagnosis completed from the device's own evidence: banner paints
      + dismisses (pipeline alive), no m4.0.4 card (main region painted
      the site's darkened body) → the site's app never mounts. Root
      suspects armed by `targetSdk 28`: WebView Force Dark (default-on
      for legacy targets in dark mode) + the `; wv` WebView user-agent
      (second-class client: `disallowed_useragent`, degraded bundles).
- [x] Force Dark OFF, three layers: theme `forceDarkAllowed=false`;
      runtime `setForceDark(FORCE_DARK_OFF)` (API 29–32);
      `setAlgorithmicDarkeningAllowed(false)` via the framework method
      (API 33+) — no new dependency, no manifest attribute.
- [x] `WebCompat.chromeLikeUserAgent`: WebView UA minus `; wv` and
      `Version/4.0` = the device's exact Chrome mobile UA; pure,
      idempotent, pinned.
- [x] `BootWitness` + `ConsoleTail`: boot-error trap injected at page
      start, per-tab console ring (8×160), DOM truth poll (2.5s × 8 =
      20s; mount floor 60 elements). Health now requires pixels AND a
      mounted app; failures escalate with the page's own testimony
      (readyState · element count · first error · first console line)
      → one silent fresh reload → honest APP_NOT_BOOTED card with the
      established escapes.
- [x] +12 unit pins/variant. Full suite: 754 executions / 0 failures.
- [x] versionCode 29.
- [ ] Device gate §22 (§17–§21 regressions included).

### Phase 4.0.6 (2026-09-05, v0.7.0-m4.0.6) — The last dark lever off, the witness de-fooled, the page tells us everything
- [x] Device verdict on m4.0.5 ("still black, no page, no error" + the
      pure-black screenshot, no card) decoded: BOTH witnesses stood down
      — the pixel probe on the site's own near-black body paint, the DOM
      witness on chatgpt.com's server-rendered shell clearing the
      60-element floor before hydration. The no-card black canvas was
      itself the diagnosis.
- [x] Forced-light scheme: the WebView is created in a
      `createConfigurationContext` with `UI_MODE_NIGHT_NO` (derived from
      the ACTIVITY context), so sites always receive
      `prefers-color-scheme: light` — the one dark lever m4.0.5 left
      armed (Force Dark off does not change what the WebView ANSWERS).
- [x] SSR-proof boot witness: a captured boot error is decisive (an
      erroring page must show ≥ 200 visible text chars to count as
      alive); the DOM probe also reads interactive-element and body-text
      counts for sharper testimony.
- [x] Page health sheet (standing, always reachable from the tab strip
      info chip): live DOM truth + boot errors + console tail + probe
      verdict + WebView version + UA, one-tap COPY REPORT, Refresh /
      Reload / Reload-in-compat escapes. The device can now hand us the
      exact cause of any future failure verbatim.
- [x] +8 unit pins/variant. Full suite: 762 executions / 0 failures.
- [x] versionCode 30.
- [ ] Device gate §23 (§17–§22 regressions included).

### Phase 4.0.7 (2026-09-05, v0.7.0-m4.0.7) — The health sheet cracked it: the compat renderer never reached the screen, and the creation recipe was the regression
- [x] The m4.0.6 health sheet's Copy report returned the decisive
      evidence (5 screenshots): the page is FULLY alive — chat.com
      `readyState=complete · 761 elements · 62 interactive · 394 text
      chars`, zero boot errors — while pixels read "never painted" (GPU)
      and "unknown" forever (compat). A hydrated app with zero presented
      frames is a PRESENTATION failure; the dark-CSS theories are refuted.
- [x] ROOT CAUSE 1 fixed (swap-safe host): `AndroidView(factory = …)`
      runs once per node — the silent first-stall compat swap, the boot
      retry, and plain TAB SWITCHING swapped the WebView instance without
      ever attaching it (the device showed a DESTROYED view; the fresh
      SOFTWARE view loaded invisibly; the "compat stalled" card was
      false). The host is now `key(webView) { AndroidView(...) }`.
- [x] ROOT CAUSE 2 fixed (creation rollback): the forced-light
      `createConfigurationContext` + darkening levers were the painting
      regression (m4.0.4's plain activity context painted partially;
      m4.0.5/6's recipe painted nothing, and broke the glass probe's
      Activity lookup). WebView creation restored to the m4.0.4 recipe;
      the Chrome-like UA stays.
- [x] Glass-first pixel probe: PixelCopy (presented window, cropped to
      the keyboard-free top half — `glassRegionRows`) is now the primary
      verdict; software readback only as fallback; 1.5 s timeout ends the
      "unknown forever" hang; a throwing fallback resolves "painted"
      (a broken probe never manufactures a stall).
- [x] Attach kick: once per view, one silent `reload()` 3.5 s after
      first layout if the render watchdog is still armed — rebinds the
      load to the live surface (frame-sink remedy for load-before-attach).
- [x] +1 unit pin (glass region). Full suite: 764 executions / 0
      failures.
- [x] versionCode 31.
- [ ] Device gate §24 (tab switching, first REAL compat test, health
      sheet, §17–§23 regressions).

### Phase 4.0.8 (2026-09-05, v0.7.0-m4.0.8) — The painted-but-black decode: the light package returns, on top of the fixed host
- [x] DECODE (device m4.0.7 health report): both tabs — GPU and software-
      layer — answered "pixels: painted" while the screen stayed black. A
      software-layer view cannot fail to reach the screen ⇒ the black IS
      the page's own near-black output. m4.0.7's creation rollback had
      re-armed both dark sources: targetSdk 28 ⇒ WebView algorithmic
      darkening ON by default on Android 15; prefers-color-scheme ⇒ dark
      because the app is Midnight everywhere. The m4.0.5/6 levers had
      looked guilty only because the never-attaching host (fixed in
      4.0.7) made every recipe paint nothing.
- [x] Forced-light scheme restored, done right: the WebView's
      configuration pinned to UI_MODE_NIGHT_NO (documented
      prefers-color-scheme lever) — sites always serve light themes.
- [x] Darkening OFF at every API level: setAlgorithmicDarkeningAllowed
      (false) on 33+, setForceDark(FORCE_DARK_OFF) on 29–32 (theme
      forceDarkAllowed=false already in place). Renderer-priority lever
      dropped — android-36 stubs removed it from WebSettings.
- [x] Context-safe activity lookup: RenderProbe.findActivity unwraps any
      ContextWrapper chain; the pool keeps the host Activity and hands it
      to the glass probe (a configuration context is not an Activity).
- [x] The probe cannot be fooled again: colorTruth (dominant color /
      near-black share / distinct colors) + scheme line + the page's own
      voice (title + first 100 visible chars) in the health report.
- [x] +4 unit pins. Full suite: 772 executions / 0 failures.
- [x] versionCode 32.
- [ ] Device gate §25 (LIGHT page visible; health report names the
      glass; escapes honest; §17–§24 regressions).


### Phase 4.0.9 (2026-09-05, v0.7.0-m4.0.9) — Companion Rendering Reset: the minimal baseline WebView experiment
- [x] The user's hard reset honored: NO Companion render-path changes, no
      new workarounds — the investigation build only.
- [x] Baseline harness inside PocketShell: plain Activity → FrameLayout →
      one WebView(activity), JS + DOM storage only, load AFTER first
      layout; entered from the Companion ⓘ health sheet.
- [x] One-variable-at-a-time variant matrix (BASELINE, +CHROME UA,
      +FORCED LIGHT CTX, +MIDNIGHT BG, +LOAD BEFORE ATTACH, +WIDE
      VIEWPORT) × gate URLs (example.com, wikipedia.org, chatgpt.com,
      chat.z.ai); view-truth status always; opt-in read-only page-
      viewport INSPECT; COPY for reports.
- [x] docs/RENDER-RESET-M4.0.9.md: facts table, working-vs-failing
      architectures, 15-layer A/B comparison (device rows PENDING),
      suspect→variant map, decision rule.
- [x] +8 unit pins. Full suite: 788 executions / 0 failures.
- [x] versionCode 33.
- [x] Device gate §26: **baseline PASSED all four gates** (video,
      2026-09-05) — decision B triggered: rebuild the host natively.

### Phase 4.1.0 (2026-09-05, v0.8.0-m4.1.0) — Companion Native Rebuild: the proven baseline becomes the architecture (decision B, executed)
- [x] The verdict executed: the Companion is rebuilt around what
      physically works — `Activity → Compose overlay → ONE stable plain
      FrameLayout → one WebView per tab (baseline recipe) → attach →
      first layout → load` (new CompanionWebHost engine).
- [x] Deleted permanently: CompanionWebPool (pool/LRU/saveState,
      forced-light context, UA spoof, flash-guard background, compat
      software layer, wide-viewport overrides, pre-attach loads),
      RenderProbe, BootWitness + ConsoleTail, CompanionHealth, the keyed
      swap host, the attach kick, the retry ladders, the health sheet;
      failure kinds reduced to LOAD_ERROR + RENDERER_GONE (retirement
      unit-pinned).
- [x] Kept product contract: definitions, tabs, persistence, cookies +
      flush, upload bridge, downloads, drag handle + remembered height,
      back nav, §15/§16 policy, m4.0.1 degradation + renderer-death
      guard, Phase 3.1 focus bridge; ⓘ now launches the retained harness.
- [x] docs/RENDER-RESET-M4.0.9.md §7 (device verdict + decision),
      PHASE-4-COMPANION-DESIGN §25 (the binding amendment), TESTING §27
      (Gates E–H device gate), CHANGELOG.
- [x] Suite: 744 executions / 0 failures. versionCode 34.
- [ ] Device gate §27: Gates E–H on the physical device (real UIs in the
      panel, touch/keyboard, tab-switch + collapse/reopen persistence,
      session survival), §27.5 regression spot-checks.

### Phase 4.0.11 (2026-09-05, v0.8.0-m4.0.11) — Replace Renderer Only: the winner frozen and shipped
- [x] The user's closing directive executed verbatim: no Companion
      redesign, no sheet-architecture changes — the existing drag
      handle, sheet behavior, remembered height, tab strip, tab system,
      picker and destination storage untouched; ONLY the tab content
      renderer was (already) replaced with the exact winning baseline
      implementation, diagnostics stripped.
- [x] Mode sweep completed from device evidence: BASELINE (all four
      gates, recording) + +CHROME UA (chat.z.ai full UI, screenshot) +
      +MIDNIGHT BG (chatgpt.com full UI, screenshot). Winner = BASELINE
      — most stable and least invasive by construction.
- [x] Winner frozen in code: `BaselineMatrix.WINNER` + new pure
      `CompanionRenderContract` (settings surface, load sequence, host
      container, creation context, empty diagnostics-in-render-path);
      the m4.1.0 host header now names the freeze.
- [x] Pins: `CompanionRenderContractTest` (6) + winner pin in
      `BaselineMatrixTest`. Suite: 758 executions / 0 failures.
- [x] docs/RENDER-RESET-M4.0.9.md §8 (sweep table + copy map + case
      closed), TESTING §28 (final Gates A–H), CHANGELOG.
- [x] versionCode 35 — the delivery of the m4.1.0 architecture under
      its final, user-named iteration.
- [ ] Device gate §28: Gates A–H on the physical device (the complete
      acceptance: real UIs on the two hard sites, touch/keyboard, tab
      switch + collapse/reopen persistence, session survival), §28.3
      regression spot-checks.

### Phase 4.0.12 (2026-09-05, v0.8.0-m4.0.12) — Companion Finalization: cleanup, polish, ONE keyboard
- [x] Surgical cleanup pass executed with the renderer FROZEN (the
      working baseline implementation untouched — the source of truth):
      the sheet, drag mechanics, remembered height, tab system, tab
      state, destination storage and Companion navigation unchanged.
- [x] ALL baseline diagnostics removed, completely: the ⓘ chip, the
      launch path, the harness Activity + BaselineMatrix (code DELETED,
      manifest entry removed) — the Companion shows only the real
      website. The frozen contract is unchanged in substance; the winner
      is pinned by VALUE now (evidence lives in RENDER-RESET §1–§8 and
      the git history).
- [x] Refresh (§3): the ↻ glyph in the tab strip — tap = plain reload of
      the ACTIVE tab only (same URL, same tab, others untouched).
- [x] Hard refresh (§4): long-press = transient LOAD_NO_CACHE around one
      reload, restored on page finish — session-safe (cookies/logins
      preserved), NOT a data reset; haptic tick + "Hard reloading…"
      toast. Pinned: `REFRESH_SCOPE` + `HARD_RELOAD` in the contract.
- [x] Drag handle (§5): visible bar unchanged (36×4dp); invisible
      full-width touch zone 28→40dp; above nearby UI by construction;
      no canvas overlap.
- [x] ONE keyboard everywhere (§6–§15): the deck moved to the app root —
      one deck over every screen; system IME permanently blocked
      (FLAG_ALT_FOCUSABLE_IM in onCreate — no more leak when the deck is
      hidden); universal dispatch fallback serves focused Compose text
      fields; WebView-input focus auto-opens the deck; the bottom-right
      [⌨] toggle works on every screen; Companion collapse restores
      terminal focus.
- [x] Pins: contract test reworked (winner by value + refresh layer
      pins) + 3 universal-dispatch pins. Suite: 750 executions /
      0 failures.
- [x] docs: TESTING §29 (finalization gates), CHANGELOG
      [0.8.0-m4.0.12], ARCHITECTURE §4 (root deck + universal chain).
- [x] versionCode 36 — the finalization build; in-place over 16..35,
      same pinned cert.
- [ ] Device gate §29: website rendering re-proof (ChatGPT/Z.ai/one
      more), refresh normal + hard, drag handle, universal keyboard
      (terminal, ChatGPT, Z.ai, settings fields, hide/toggle, over every
      screen, no focus conflicts), §29.5 regression ladder.

### Phase 5.0.0 (2026-09-05, v0.9.0-m5.0.0) — UI & Interaction Polish: free-position Companion, decluttered Home, Light Theme done fully
- [x] A refinement phase — no redesign, no new feature, the working
      Companion implementation untouched architecturally (renderer,
      sheet mechanics, tab system, pool, refresh/hard-refresh all
      preserved).
- [x] Companion drag bar (§1): visible bar doubled (36→72dp, still
      4dp slim) in the same 40dp invisible full-width zone; zone stays
      the column's first child (can never hide behind tabs/content).
      Single tap ANYWHERE on the bar minimizes the raised sheet at ANY
      height; restore is drag-up only (no floating button — the bar is
      the only affordance, as before).
- [x] Free positioning (§1): HALF/FULL snap windows RETIRED — release
      settles exactly where the user leaves it, any fraction; height
      changes only by dragging; collapse threshold (drag to the bar →
      minimized) is the only special release. Height-math unit pins
      reworked for the new contract.
- [x] Home FAB removed (§2): QuickActions.kt deleted, 140dp clearance
      gone; session creation lives in the Terminal (its "+" and empty
      state); no floating replacement.
- [x] Duplicate CLI Apps dropdown removed (§3): it listed the same apps
      the "Your tools" grid launches — one clear path remains. No
      functionality removed.
- [x] Compact chrome (§5–§7): terminal strip 44→40dp, tab min width
      96→84dp, paddings 12→10dp, gaps 6→4dp; terminal "+" integrated
      into the strip (no circle plate); app-wide padding trim (Home,
      MidnightPage kit, Packages, Settings) with touch targets ≥44–48dp
      preserved; active tab labels ride the pinned onCanvas token.
- [x] Keyboard toggle corner-anchored (§4): [⌨] rebirth icon at 12dp
      from the right edge / 8dp above the gesture inset on every
      screen; safe insets respected. ONE keyboard system untouched (no
      second layout, no IME, same dispatch chain, same web-input
      bridging).
- [x] Light Theme (§8): TerminalTheme tokens became snapshot state —
      Midnight (dark, historical values) ↔ Daylight Sapphire (light);
      HomeTokens read-through; token sync before first read (no flash);
      immediate switch, DataStore persistence; System/Light/Dark/AMOLED
      all live; AMOLED preserved; dynamic color intact. The terminal
      CONTENT canvas is PINNED dark (TerminalPalette/OSC authority) and
      websites keep their own themes — no injection, ever. New pinned
      onCanvas/onCanvasDim tokens keep canvas-surface text readable in
      both themes. Status bar follows the theme on every screen.
- [x] Packages page (§9) + Settings page (§10) polish: compact search
      row, grouped Settings (Appearance / Terminal / Companion), same
      honest apk-backed states, nothing invented.
- [x] Full suite green: 750 executions / 0 failures. versionCode 37 —
      in place over 16..36, same pinned cert.
- [ ] Device gate §30: drag-bar behavior matrix (tap-to-minimize at
      every height, free positioning, restore), FAB/CLI-menu removal,
      keyboard toggle corner, Light/Dark/System/AMOLED screen sweep
      (contrast, tabs, keyboard, Companion chrome — websites NOT
      re-themed), Packages/Settings, keyboard regression ladder.

### Phase 5.0.1 (2026-09-06, v0.9.0-m5.0.1) — M5.0 Final UI Correction: the Workspace Bar
- [x] A surgical pass — no redesign, no new features, the working
      Companion implementation untouched architecturally.
- [x] Workspace header REMOVED: the terminal screen's large top title
      row (back + live session title) is gone — the active session's
      name already lives in its tab; the workspace starts directly
      under the Android status area (the strip consumes the status-bar
      inset itself; the chrome-gradient header block is deleted).
- [x] Back lives IN the tab bar: a compact integrated glyph at the far
      left of the workspace bar (`← [Tab] [Tab] [Tab] +`), vertically
      aligned with the tabs, not a header-sized button in its own row.
- [x] Compact IDE tabs, BOTH strips (terminal + Companion): strip
      34dp (was 40); active tab 34 / inactive 26 (was 40/30); 2dp gaps
      (was 4); 8dp horizontal tab padding (was 10); tab width 64–136dp
      (was 84–160); 6dp corner radius (was 10, shared token); the
      active indicator is a subtle 2dp hairline (was 2.5). The editor
      language is untouched — only density changed.
- [x] Tab text: long titles truncate with an ellipsis, close buttons
      stay reachable, tabs share the bar intelligently (narrower
      minimum width + horizontal scroll + the ACTIVE tab is always
      scrolled back into view when a switch lands off-screen).
- [x] Companion near-full drag surface: below 90% height NOTHING
      changed (dedicated bar = the only sheet drag control; tab bar =
      normal tabs). At/above 90% of the available height the TAB STRIP
      also drags the sheet vertically, gated behind the vertical touch
      slop — tab taps, close, + and refresh are never mistaken for
      drags, and the strip NEVER minimizes on touch (tap-to-minimize
      stays the dedicated bar's exclusive duty). The drag math is
      shared verbatim by both surfaces; free positioning preserved
      (no snap points); the surface stays attached while a drag is in
      flight so a drag crossing below the threshold is not cut
      mid-gesture.
- [x] Full suite green: 752 executions / 0 failures (new pure pins for
      the 90% gate). versionCode 38 — in place over 16..37, same
      pinned cert.
- [ ] Device gate §31: workspace-bar layout, compact tabs, near-full
      strip-drag matrix, no-regression ladder (keyboard, themes,
      Companion renderer).

### Phase 5.1.0 (2026-09-06, v0.9.1-m5.1.0) — M5.1: ARM64 Performance & Architecture Optimization
- [x] Audit-first discipline: the real architecture was measured and read
      end-to-end before any change — startup path, Companion host/tab
      lifecycle, terminal session/rendering path, Linux process
      lifecycle, keyboard composition, memory hooks.
- [x] Audit verdict — already sound (untouched): lazy startup (no
      WebView/Linux/package work in Application.onCreate); one WebView
      per tab, never recreated or reloaded on switch; WebView height
      frozen during sheet drags (one resize on release); background
      tabs platform-paused on switch and at Activity pause; terminal
      scrollback capped (2000 rows), no polling, blinker stopped on
      pause, repaint hook unregistered off-screen; one Linux process
      per session, honest FGS that stops itself; keyboard allocations
      trivial.
- [x] F1 — minimized Companion pauses ALL WebViews (the active tab used
      to keep running JS/timers/layout while invisible); raise wakes
      only the active tab; no reload, no state loss; cookies flushed
      on collapse.
- [x] F2 — Home's command-app probe (a real proot login-shell exec)
      no longer re-runs on every Home visit: 60s freshness window +
      in-flight guard; operation landings force; failures re-probe.
- [x] F3 — terminal repaints only on output from the VISIBLE session
      (background session output storms no longer force full repaints
      of the unchanged screen); switches remain correct by
      construction (attachSession → updateSize → invalidate).
- [x] F4 — onTrimMemory(≥ RUNNING_LOW) flushes cookies while Companion
      tabs are alive, gated on the provider already being loaded and
      fully contained (the m4.0.1 startup rule is untouched).
- [x] Tab resource policy stated and pinned in code comments: active =
      full; background = platform-paused; minimized = everything
      paused; tabs live until closed — saveState/restore + LRU eviction
      stay retired (the m4.0.11 verdict family), so no user state is
      ever destroyed behind their back.
- [x] Full suite green: 752 executions / 0 failures. versionCode 39 —
      in place over 16..38, same pinned cert.
- [ ] Device measurement gate §32: the A–G scenario matrix (Home /
      Terminal / Terminal+Linux / Terminal+Companion / multi Companion
      tabs / multi sessions+Companion / background→return) with
      before/after observations — memory growth, CPU spikes, lag,
      reloads, dropped frames, process leaks.

---

## Phase 6.0.1 — Install observability + suite diagnosis (v0.10.0-m6.0.1, versionCode 41)

Device-gate lesson from §33.1: the layer's best-effort install was silent, so
"failed to install" and "not installed yet" were indistinguishable on device.

- [x] GuestGlibcRuntime: every ensure outcome mirrored to guest-visible
      `/etc/pocketshell/glibc-runtime.status` (diagnostics only; marker
      contract unchanged; never blocks a session)
- [x] Suite v2: PREFLIGHT diagnosis, SKIP-with-fix-path for a missing layer,
      capability-probe layer detection, `cline --help` false-PASS fix,
      POCKETSHELL_INSTALL_LAYER=1 in-guest repair hatch
- [x] Validated under emulation in 4 device states (stub/repair/unmarked/full)
- [ ] Device gate: §33 re-run on vc41 (expect 24/24 after one fresh session)

## Phase 6.0.0 — Universal Runtime Compatibility (v0.10.0-m6.0.0, versionCode 40)

ONE Alpine distribution; musl + glibc + static + Node tooling coexist.
Decision record: docs/runtime/DUAL_LIBC.md. Suite: docs/runtime/TESTING.md.

- [x] Phase A/B: architecture selected — REAL Debian trixie glibc 2.41 at
      canonical multiarch paths inside the rootfs (no binds, no env, no
      wrappers; children work); gcompat/sgerrand/patchelf/LD_LIBRARY_PATH/
      second-distro rejected on evidence, all documented.
- [x] Sidecar built from 15 pinned Debian pool packages with per-input
      SHA-256; artifact pinned (GlibcRuntimePin), ships in the APK.
- [x] Guest tools: pocketshell-exec (routing) + pocketshell-doctor
      (diagnosis incl. real loader `--list` resolution + symbol-version check).
- [x] Delivery via GuestGlibcRuntime.ensureInstalled at the per-session prep
      seam: idempotent marker fast path, self-healing, best-effort.
- [x] Sandbox validation rig (proot + qemu-aarch64, same pins): suite 20/20;
      device runner under emulation 24/24 ALL GREEN; REAL Cline 3.0.61
      (byte-identical size to the device report) runs version/help/
      node-spawn/relaunch ×3 under the layer.
- [x] JVM suite 768 executions / 0 failures (8 new GuestGlibcRuntimeTest pins).
- [x] Docs: DUAL_LIBC / ELF_COMPATIBILITY / runtime TESTING /
      KNOWN_LIMITATIONS + THIRD_PARTY licensing (LGPL-2.1 + set).
- [x] Other forensic findings documented for LATER phases (no scope creep):
      /proc/net + /proc/stat-family overlays, sysdata liveness, PID-namespace
      visibility — tracked in the Runtime Forensic Audit roadmap (P1/P3).
- [x] Device gate #1 (§33): 18 PASS / 6 FAIL — layer absent; response m6.0.1
      (status file, suite v2 PREFLIGHT, hatch). Device gate #2: 9/15 on a
      STALE v1 suite — layer still absent.
- [x] m6.0.2 FORENSICS: both gates had ONE proven root cause — AGP's asset
      merge decompresses .gz assets and strips the suffix, so the APK never
      carried the name the pin declared (FileNotFoundException on every
      spawn, before the rootfs was ever touched). Fixed on three rails
      (packaged-form pin + sniffing/sha-verified extraction + built-APK JVM
      pin and release-mirror asset check); app-version stamp + suite v2.1
      (self-locating, layout-agnostic). 780 JVM executions / 0 failures;
      rig 3-phase validation green.
- [ ] Device gate #3 (docs/TESTING.md §33A, vc42): fresh v2.1 suite download,
      one fresh session, expect 24/24 ALL GREEN incl. Cline 3.0.61.

## M7.1 — Home launchers, themed marks, external-keyboard intelligence ✅ (frozen 2026-09-08, v0.11.1-m7.1.0, versionCode 46)

Delivered in five phases (each with its own commit, JVM gate and device-gate
section in docs/TESTING.md §41-§45; M7.0 itself was closed by the
v0.11.0-m7.0.0 release — see CHANGELOG):

- [x] **P1 (3abb2e8)**: Home launchers — Companions grid + Your tools grid,
      hide-only remove with restore, custom tools through the ONE
      verify-then-launch guest path, deterministic badges, copied icons.
- [x] **P2 (1b15bde)**: launcher UI repair at the row-shape root cause
      (ONE shared weighted-row shape), bundled official marks, Antigravity
      (`agy`) replacing Gemini CLI in the curated defaults.
- [x] **P2.1 (e0a2471)**: Aider removed with no stale trace; nine marks
      re-rendered from owner-supplied official SVGs vendored in-tree
      (locals-first pipeline, offline, byte-reproducible).
- [x] **P2.2 (6004805)**: two-variant theme marks ({id}.webp Midnight /
      {id}-light.webp Daylight, 26 assets, live theme flips), Companions
      one x-scroll row + tools two rows with scroll dots, the packages
      affordance as the tools-header Manage action.
- [x] **P3 (93ee631)**: live external-keyboard detection — event-driven
      (InputDeviceListener → 400 ms stability window → one scan → at most
      ONE transition; no polling, no new permissions), alphabetic
      non-virtual SOURCE_KEYBOARD predicate, automatic deck
      suppression/restore through the ONE root visibility owner
      (preExternalExpanded memory, manual reopen cancels, web-focus gated),
      Settings "On-screen keyboard" toggle (DataStore, default ON),
      ONE transient in-app notice per real connect transition.
- [x] **Release closure (this freeze)**: clean-rerun JVM suite 734/734
      effective green at the release stamp; fresh APK audited (badging
      vc46 / 0.11.1-m7.1.0, 6 permissions, 26 icons, dex symbols, cert);
      TESTING §46 release QA; README/CHANGELOG refreshed; delivery
      artifacts re-cut and wire-verified; M7.1 tagged and FROZEN.
- [ ] Device gates §41-§45 (the standing hardware pass; §33A runtime gate
      #3 also still open from M6) — manual, requires a human with a device.

## M7.1.1 — external keyboard detection fix ✅ (2026-09-08, v0.11.2-m7.1.1, versionCode 47)

The M7.1 P3 device gate failed on the real phone (Samsung SM-F711B): the
auto-hide was real but the terminal-canvas tap cancelled it (the primary
defect), the unbounded stability window could starve under periodic BT
events, detection was single-mechanism, the disconnect was silent, and the
restore could fight the user. M7.1.1 rebuilds the keyboard state system:
ONE authoritative `ExternalKeyboardVisibilityModel` (persistent
`onscreen_keyboard_enabled` preference + hardware state + explicit user
request → `shouldShowOnscreenKeyboard`), the gated canvas-tap reopen, the
2 s confirm deadline against event storms, the Application-level
configuration-change cross-check as a second mechanism, and both-direction
transition notices. 746/746 JVM clean rerun; 6 permissions unchanged; the
mandatory real-device gate is TESTING §47 (run on the phone, no JVM
completion).

**M7.2 — Notification & agent-activity system — P0 COMPLETE (audit +
design only; no production code):** the full architecture audit is
`docs/M7.2-P0-AUDIT.md` — the real session/PTY/process architecture
(proot is the PTY's direct child; waitpid sees the direct child only),
the honest capability line (session-level lifecycle RELIABLE;
command/agent-level NOT knowable today; waiting-for-input has no real
signal), the launcher→session metadata trace (label only; structured id
not carried), the Tier 1/2/3 detection classification over the real
9-entry registry, the notification-infrastructure inventory (one FGS
channel; POST_NOTIFICATIONS declared but never requested at runtime),
and the proposed authoritative state model (extend
TerminalSessionManager; spawn-origin metadata; policy-gated
NotificationCoordinator). Phase adjustments: P1 gains the missing
notification-permission request; P2 stays session-level; P3 splits into
metadata propagation (P3a) and the device-verified /proc scanner (P3b);
P7 is conditional on real signals only.

**M7.2 P1 — notification foundation ✅ (2026-09-08, first M7.2 production
code, phase build on the inherited vc47 / `0.11.2-m7.1.1` stamp —
filename suffix `-m72p1`):** infrastructure only, per the P0 audit. The
declared-but-never-requested POST_NOTIFICATIONS is now requested at
runtime on Android 13+ — exactly once per install, fired when the first
terminal session exists, flag persisted BEFORE the dialog (no re-ask on
recreation/restart/process death), system-implicit-prompt denials
respected, denial never blocks terminal functionality. The new
`notifications/` package: NotificationCoordinator (the ONE
output/integration layer — owns the `session_events` channel beside the
untouched `terminal_sessions` FGS channel, deterministic
EVENT_BASE+sessionId ids, FLAG_IMMUTABLE routing intents, the DataStore
posted-id ledger, the startup stale-notification sweep that cancels only
coordinator-owned ids), NotificationPermissionPolicy (the pure anti-nag
decision function), NotificationRoute (the tap-routing vocabulary —
OpenApp in P1). MainActivity now processes notification intents on both
paths (cold start onCreate + the previously-unhandled singleTask
onNewIntent). TerminalService and the M7.1.1 keyboard system untouched;
declared permission set unchanged; 780/780 JVM green (app 635 + TE 145)
incl. 34 new notifications tests; APK audited on the exact bytes. P1
posts NO production event notifications — no agent detection, no
waiting-for-input heuristics, no /proc scanning, no OSC 133. The
real-device pass is TESTING §48 (12 steps).

**M7.2 P2 — session lifecycle engine & structured exit status ✅
(2026-09-08, second M7.2 production code, phase build on the inherited
vc47 / `0.11.2-m7.1.1` stamp — filename suffix `-m72p2`):** internal
lifecycle infrastructure, exactly the audit §10.3 provable machine.
`SessionEntry` stores a typed `SessionLifecycleState` — STARTING (entry
exists, PTY child not yet forked: the lazy-fork fact, observed via the
previously-discarded `setTerminalShellPid` callback), RUNNING (the real
fork signal), FINISHED (the real waitpid delivery, with the exit status
surfaced as a structured `ExitStatus.Exited(code)` / `ExitStatus.Signaled(signal)`
— precisely what `JNI.waitFor` provides, nothing invented); REMOVED is
tab removal plus a typed event, never a stored flag; invalid
combinations are unrepresentable (private constructor; FINISHED always
carries its status). One owner (`TerminalSessionManager`), pure
transitions with logged rejections (duplicate callbacks can never
corrupt a recorded status), main-handler serialization, the close path
guarded against the `kill(0)` hazard for a never-forked pid, and typed
`SessionLifecycleEvent`s emitted only at mutation sites for downstream
consumers (`AgentActivityRepository` — the derived read model that
stores nothing). Every spawn site passes structured `SpawnOrigin`
(Shell / LinuxShell / FilesTerminal / CommandApp(id) / CatalogApp(id) /
CustomTool(id)); the three named-launcher paths carry `AgentHint`
graded `LAUNCH_METADATA` — spawn metadata only, never a process claim.
Lifecycle state is in-memory only by design (no DataStore). 810/810 JVM
green (app 665 + TE 145, 0 skipped) incl. 30 new lifecycle tests, plus
the verification-honesty fix: the P1 structural pins silently skipped
under the module-dir runner and now run and pass. No notifications
posted, no agent detection, no heuristics, no /proc, no OSC 133, no UI
change. The real-device pass is TESTING §49 (10 steps — a parity gate).

**M7.2 P3a — trusted agent signal audit & detection design ✅
(2026-09-08, internal metadata plumbing on the P2 engine, phase build on
the inherited vc47 / `0.11.2-m7.1.1` stamp):** audit-first per the P0
design baseline (PART D/E), then the minimum the evidence supports. The
full signal audit and the detection-classification matrix over every
registry/catalog/custom launcher are `docs/M7.2-P3A-DETECTION-MATRIX.md`;
the core safety rule is now type-level: "PocketShell launched X" (proven
at spawn), "X is currently running" (NOT knowable — the direct child is
proot, the `; exec` chain outlives the agent, and the agent's exit status
is discarded by the intermediate shell) and "X completed" (NOT knowable)
are distinct statements, and only the first is representable. New
`terminal/LaunchIdentity.kt`: the sealed `KnownAgent` /
`KnownNonAgentTool` / `CustomOrUnknown` classification resolved by a PURE
`of(origin, agent)` against the real `CommandAppCatalog` /
`CliAppCatalog` objects — custom tools resolve against NO registry
(user names never promote to agents), unresolvable ids degrade honestly,
plain shells claim nothing, and the type carries no exit/running/
completion field. `AgentActivityRepository` gains the derived
`classifiedLaunches` projection (stores nothing, decides nothing). No
spawn-site change, no manager change (one lifecycle authority), no
persistence, no UI, no notifications, no /proc, no polling, no output
parsing. 833/833 JVM green (app 688 = 665+23 new + TE 145, 0 skipped)
incl. the truth-boundary suites (`LaunchIdentityTest` 14 pure +
`LaunchIdentityIntegrationTest` 9 structural, incl. the repo-wide
no-agent-running-API pin); APK audited at the inherited stamp (cert and
6-permission set unchanged). JVM-verified only by design — TESTING §50
records the honest floor and requires NO device gate for this phase.

**M7.2 P3b — runtime agent detection via /proc ✅ (2026-09-09, the
device-verified /proc descendant scanner, phase build on the inherited
vc47 / `0.11.2-m7.1.1` stamp):** the second truth level implemented —
"PocketShell has runtime process evidence that agent X is currently
running". The full investigation (real process architecture, controlled
procfs experiments E1–E5, correlation audit, matching rules, polling
discipline, limitations, truth guarantees) is
`docs/M7.2-P3B-RUNTIME-DETECTION.md`. Core mechanism: every session's
fork-proven direct-child PID (`SessionEntry.shellPid`, recorded on the
real fork signal) is the correlation root; a process is attributed to the
session ONLY via PPID-chain descent OR process-group membership under
that root (the root called setsid, so both domains are kernel-safe —
orphans keep their group, regrouped descendants keep their chain, and a
random same-name process elsewhere can satisfy neither). Matching is
exact-token with two graded shapes (`AgentMatchedBy` + the ROADMAP-named
`PROCFS_EXE`/`PROCFS_CMDLINE` compile-time-forced extensions): the exe
image basename, or an exact argv element (argv[0], or the kernel shebang
contract's script path at argv[1] — experiment-proven). The four-state
contract (`NOT_APPLICABLE / UNKNOWN / NOT_RUNNING / RUNNING`) never
invents certainty: a scanner that never saw the agent answers UNKNOWN
(not-started-yet vs already-exited is indistinguishable); proven
disappearance is NOT_RUNNING, never completion; scan failure is UNKNOWN,
never absence. The scanner activates ONLY for live `LaunchIdentity.
KnownAgent` sessions (custom tools and non-agent catalog tools are
NOT_APPLICABLE — PART K/L), ticks every 2s ONLY while such sessions
exist, and parks with zero scheduled work otherwise (no alarms, no
wake locks, no work after the last agent session dies). One lifecycle
authority: the detector is an observer of the manager's StateFlow and
owns no transitions; `AgentActivityRepository.runtimeActivities` is the
derived read model. No notifications, no UI, no output parsing, no OSC
133, no completion claims. 1764/1764 JVM forced-rerun green (app 737 =
688+49 new + TE 145, both variants, 0 skipped) incl. 38 pure
matching/correlation/state tests and 11 structural boundary pins; APK
audited at the inherited stamp (cert and 6-permission set unchanged; all
P3b symbols in the dex). Device-gated remainder: TESTING §51 (each
registry agent's real /proc shape, hidepid/SELinux listing behavior).

**M7.2 P3c (COMPLETE): the runtime transition & event engine — the third
truth level (trusted runtime transitions)** (the phase reframed by its
mandate: events first, notifications NOT in this phase). The pure
`AgentRuntimeTransitions.reduce` folds the P3b detector's published
observations plus the manager's authoritative session state into the typed
`AgentRuntimeEvent` vocabulary — Launched / ConfirmedRunning (with the
exact pids + grade) / NoLongerDetected (runtime disappearance ONLY, never
completion) / RuntimeUnknown (honest uncertainty) / SessionEnded
(SESSION_FINISHED with the direct child's waitpid status, explicitly not
the agent's completion, or SESSION_REMOVED) — with per-session in-memory
deduplication (same-state re-observation = no event; silent birth-UNKNOWN
emits nothing and can never seed an absence), reappearance re-emission,
staleness rejection (a detector result arriving after session closure can
never fabricate an event; ONE sequential collector over both StateFlows),
and terminal edges derived from the manager's own authoritative state (the
ONE lifecycle authority untouched). The stream is a replay-free SharedFlow
with a named 64-event buffer and logged drops — the future notification
consumer subscribes without ever needing /proc, process groups, PID
ancestry, token matching or polling. `AgentRuntimeEventEngine` is an
observer woken from the manager's spawn path beside the detector; it adds
ZERO polling, mutates nothing, and logs transitions only (Log.d
AgentRuntimeEvents — no UI). The repository re-exposes
`agentRuntimeEvents` like `lifecycleEvents`. 1850/1850 JVM forced-rerun
green (app 780 = 737 + 43 new × debug+release + TE 145 × 2, 0 skipped);
APK audited at the inherited stamp (cert + 6-permission set unchanged; all
five P3c symbol groups in classes14.dex). Full contract:
`docs/M7.2-P3C-EVENT-ENGINE.md`; no device gate required (no user-visible
change — TESTING §52 records the observability note).

**Next milestone (NOT started, not yet mandated): M7.2 P4 — notification
consumption of the event engine** (policy-gated posts over the P1
foundation, subscribing to `AgentActivityRepository.agentRuntimeEvents`
without any /proc/matching/polling knowledge; the honest UNKNOWN and
NOT_APPLICABLE states must never be dressed up; completion claims remain
unrepresentable — P0 PART D/E and the P2/P3a/P3b/P3c seams are the design
baseline; the source tree remains the implementation authority).

### M7.2 P4 — notification infrastructure & runtime notification consumption (COMPLETE, 2026-09-09)

The first user-visible M7.2 phase: P3c's deduplicated event stream now
drives honest Android notifications through ONE new consumer
(`AgentRuntimeNotificationConsumer`, subscribed ONCE at Application start —
the replay-free stream's correctness requirement, wired after the P1
coordinator's init) folding events through ONE pure truth contract
(`AgentRuntimeNotificationMapping`: Launched → silent; ConfirmedRunning →
ongoing "<RegistryName> is running" surface; RuntimeUnknown → the surface
updates to "runtime unknown" in place; NoLongerDetected → the surface is
CANCELLED — disappearance is never completion; SessionEnded → the surface
yields to a one-shot factual "Session ended" / "Terminal session N exited
(code X)" / "terminated by signal Y" statement, gated on the session having
ever been announced). Identity is deterministic
(`AGENT_RUNTIME_BASE + sessionId`, no hash/random/display strings); the
notification layer adds its own defensive dedup (posted/everPosted/
tombstones — identical surfaces never repost, ended sessions refuse every
later event) without becoming a second event state machine; one new
channel (`agent_runtime`, "Agent activity") beside the untouched P1
channel, `setOnlyAlertOnce` + ongoing-vs-auto-cancel keeping it calm; the
FGS retention notification (TerminalService, channel `terminal_sessions`,
id 1) is untouched and structurally pinned so; P4 adds ZERO permission
machinery (the P1 gate/policy remain the only path; the manifest is
unchanged); stale surfaces die three ways (tombstone in-process, the P1
startup sweep across processes, no restoration after process death —
nothing fake is re-shown). The honesty line is pinned over the shipped
string literals: no produced notification can claim
completed/success/finished/failed, exit 0 is never "success", and
"NoLongerDetected" is only ever a withdrawal. 1932/1932 JVM forced-rerun
green (app 821 = 780 + 41 new × debug+release + TE 145 × 2, 0 skipped);
APK audited at the inherited stamp (cert + 6-permission set unchanged; P4
symbols in the dex). Full contract: `docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md`;
device gate: TESTING §53 (ten steps — the shade must be verified on
hardware).

**M7.2 P5 — COMPLETE (2026-09-09): notification interaction & session
context** — the honest notifications became actionable: an agent-runtime
notification's tap now opens PocketShell into the terminal context of the
session the notification already names (confirmed-running / runtime-unknown
/ eligible session-ended all route; a stale id opens the app normally — a
notification can never resurrect a process). The route carries ONLY the
authoritative session id; resolution is a PURE model against the manager's
live list (JVM-pinned over the full Part-J matrix); identity stays
deterministic (request code = notification id = base + sessionId). P4's
wording is byte-unchanged — P5 changed what a tap DOES, never what a shade
may SAY. Zero new permissions, zero new channels, zero manifest delta, FGS
untouched. Full contract: `docs/M7.2-P5-NOTIFICATION-INTERACTION.md`; device
gate: TESTING §54 (eight steps). 1986/1986 JVM, 0 skipped; APK at the
inherited vc47 / 0.11.2-m7.1.1 stamp.

**M7.2 P6 — COMPLETE (2026-09-09): agent runtime notification device-state
refinement (verification phase, zero production delta)** — the runtime
state transitions the P3c/P4 architecture already implements were audited,
pinned as a continuous device story, and gated on hardware where naturally
reproducible: running → unknown → running updates ONE deterministic
notification identity in place (no duplicates, no second card);
RUNNING/UNKNOWN → NOT_RUNNING cancels it (never re-worded as
completed/success/finished); an ever-announced session's end yields only
the factual exit statement (exit 0 is never success). The mid-flight
notification-level UNKNOWN cycle is honestly documented as NOT
device-reproducible in P6 (no natural trigger without fabricated evidence;
stays JVM-pinned) — TESTING §55 records the reproducible subset (withdrawal,
in-session reappearance, birth silence, exit/exit-3 facts, tab-close, the
P5 tap regression, FGS). Implementation delta: one new pure test class
(`AgentRuntimeNotificationTransitionMatrixTest`, 11 tests/variant); the
rebuild is byte-identical to the P5 APK (sha `69ab4402…c57f`), the strongest
proof of zero production change. No completion/success/failure/
waiting-for-input detection was added; the detector architecture stays
frozen. Full contract: `docs/M7.2-P6-RUNTIME-STATE-TRANSITIONS.md`; device
gate: TESTING §55 (the naturally reproducible subset). 2008/2008 JVM,
0 skipped; APK at the inherited vc47 / 0.11.2-m7.1.1 stamp.

**M7.2 P7 — COMPLETE (2026-09-09): trusted waiting-for-user evidence audit
(evidence-audit phase, P7B verdict, zero production delta)** — the phase
asked whether PocketShell can KNOW that an agent is waiting for the user,
and the honest answer is NO, so no `NeedsInput` state shipped. The audit
mapped the real I/O path (raw PTY bytes are unreachable outside the
vendored emulator; stdin is one untagged door with kernel echo; the
emulator's only structured seams are title/colors/clipboard/BEL/cursor/
exit — OSC 133/9;4/777 and APC are swallowed — and every one of them is
spoofable by arbitrary output such as `printf '\a'`), audited all nine
curated launchers (bare commands, zero config staging, exit status
structurally discarded) plus external docs for their machine-readable
channels, and applied the mandate's eight-condition evidence standard:
Claude Code hooks / Codex `notify` / OpenCode plugins classify **B —
strong but agent-specific** (config staging + an app-side receiver +
per-session binding + a runtime-generation concept, none of which exist;
the strongest arm is also inactivity-based or completion-flavored), the
other six classify **D — no signal found**, and no candidate passes the
standard. Bell/title/exit-code/text-regex/inactivity heuristics remain
REJECTED, now with the spoofability proof recorded. P7's production delta
is ZERO: the boundary is pinned instead — `AgentRuntimeWaitingEvidenceBoundaryTest`
(4 tests/variant: the session client's attention seams terminate at
repaint/title/logging with no path into the evidence machinery; zero
screen-scraping references in the app layer; the runtime surface watches
no files and reads no terminal text; the notification layer cannot write
to the terminal — tap-to-terminal stays the ONLY interaction) and the
honesty ban lists extended with `needs input`. TESTING §56 records the
negative-result device gate (running wording unchanged, the on-device
spoof proof, P5/P6/FGS regressions) — no fake needs-input instructions.
The future-integration requirements (versioned per-agent evidence
contract, env-var session binding, runtime-generation concept, app-side
receiver, launcher-staging policy, typed attention event alongside — not
instead of — RUNNING, and the response-action safety contract) are
documented in the audit. Full contract:
`docs/M7.2-P7-WAITING-EVIDENCE-AUDIT.md`. Full JVM forced rerun
2016/2016 (app 863×2 = 859+4 new, terminal-emulator 145×2), 0 skipped;
the assembleDebug rebuild is byte-identical to the P5/P6 audited APK
(sha `69ab4402…c57f`) — no new APK delivered, the P6 one stays current.

**M7.2 P8 — COMPLETE (2026-09-09): home sessions integration & unified
agent activity (consumer/UI phase)** — the EXISTING Home → Sessions rows
now state the SAME authoritative agent activity the notification system
states, through ONE pure projection over the SAME two authorities P3c/P4
consume (`TerminalSessionManager.sessions` + `RuntimeAgentDetector.
observations` → `AgentHomeSessionClaims.present`, exposed by
`AgentActivityRepository.homeSessionClaims` and re-exposed verbatim by
`TerminalViewModel`). A row may say only `<Agent> — Running` (registry
name, green) or `<Agent> — Runtime unknown` (dim); a birth-unknown agent
session, a withdrawn (`NOT_RUNNING`) runtime, non-agent tools, custom
launchers and plain shells render the NORMAL row, and ended sessions keep
their pre-existing `(exited)` session fact — claims are keyed by the
manager's authoritative id, so nothing stale can survive a removal, a
finish, or a new session. The PARITY CONTRACT (notification surface ⇄
Home claim agree state-for-state, including the birth-silence gate via
the detector's `everObservedRunning` and the withdrawal arm) is pinned by
the new `AgentRuntimeHomeParityTest` (5 tests/variant — the same
authoritative sequence folded through the shipped engine→mapping chain
AND the Home projection must agree at every step); the pure claim matrix
is pinned by `AgentHomeSessionClaimsTest` (18/variant); the structural
observer rules (decision-only claim step, two-authority projection,
lifecycle-aware collection, no polling, no /proc, no second lifecycle
owner, NO PTY write path, one clickable per row — the existing
`onOpenSession` seam, honesty ban list over the Sessions literals) are
pinned by `AgentHomeSessionIntegrationBoundaryTest` (6/variant); the
running-vocabulary confinement allowlist gained the P8 consumer (the
established ROADMAP-authorized consumer-boundary evolution, disclosed).
No detector, no polling, no process heuristics, no completion/success/
failure/needs-input claim, no dashboard, no Home redesign, no second
navigation, no version bump (vc47 / 0.11.2-m7.1.1). Full contract:
`docs/M7.2-P8-HOME-SESSION-INTEGRATION.md`; device gate: TESTING §57.
Full JVM forced rerun 2074/2074 (app 892×2 = 863+29 new,
terminal-emulator 145×2), 0 skipped.

**Next milestone (NOT started, not yet mandated): M7.2 P9** — unassigned
by design; the completion-detection / waiting-for-input tiers remain
REJECTED until a phase arrives with real, authoritative evidence that
passes the P7 evidence standard (the P0 audit's line stands, re-verified
at the P7 baseline and inherited untouched by P8). Any future consumer
(per-agent channels, rich content, re-surfacing policy, trusted attention
evidence) extends the P4/P5/P8 truth contract — never around it.
