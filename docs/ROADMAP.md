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
