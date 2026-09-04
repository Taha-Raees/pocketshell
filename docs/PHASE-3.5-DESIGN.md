# Phase 3.5 — Command Launch Fix + System Pages Adopt Midnight (design contract)

Status: CONTRACT — committed before implementation.
Scope: one launch-pipeline bug fix (command apps opened a plain shell) +
adopting the Phase 3.3 Midnight Sapphire language on the three remaining
app-theme screens (Diagnostics, Packages, Settings).
Version target: `0.7.0-m3.5`, versionCode 22.

Trigger (user device report + screen recording, v0.7.0-m3.4): "when kilo is
clicked it opens normal Linux terminal, what it should be doing is kilo is
run when kilo command is typed in cli. It should directly open kilo when
clicked. And no hard code ... update all three pages which have old design.
Diagnostic, package manage, and settings".

Frozen (must not regress):

- Phase 3.1 — terminal rendering, keyboard, PTY dispatch, Midnight chrome.
- Phase 3.2 — verify-before-launch discipline: runtime gate, fresh probe, NEW
  dedicated guest session per launch, honest refusal banners, no-crash
  guarantee. The fix changes HOW the command reaches the shell, never WHETHER.
- Phase 3.3/3.4 — Home composition, registry data, probe honesty contracts,
  whole-row touch targets on system pages.
- PackageManager honesty rules: v0.4.2 (Working… only on the targeted card),
  v0.4.4 (failed probe never reads as "Not installed"), stderr passthrough.

---

## 1. Diagnosis — why a tapped tile opened a plain shell

`TerminalSessionManager.createLinuxCommandSession` wrote the launch command
into the PTY immediately after session construction:

```kotlin
val shellEntry = createLinuxSessionInternal(...)   // TerminalSession ctor ONLY
shellEntry.session.write(command + "\n")           // bytes silently DROPPED
```

But `TerminalSession` (upstream termux contract) does not fork the process in
its constructor. The process is created lazily in `initializeEmulator()`,
which runs when the view first renders the session (`updateSize`). And
`TerminalSession.write()` guards with `if (mShellPid > 0)` — at write time
`mShellPid == 0`, so the command bytes are discarded without any error. The
user gets a fresh login shell with nothing typed: exactly "opens normal
Linux terminal".

This affected EVERY consumer of the one generic path — the command-app
registry (kilo, claude, gemini, …) AND the catalog CLI apps (nano, git, …).
Nothing was kilo-specific; the bug was in the shared transport.

## 2. Fix — deliver the command through the shell's ARGV

The launch command now rides the guest shell's argv:
`sh -l -c "<command>; exec sh -l"` (`guestLaunchChain`, pure + test-pinned).

- Deterministic: the login shell itself reads its profiles, then runs the
  command — whenever the view attaches. No PTY timing, no dropped bytes.
- Same honesty: still exactly "what typing the command would do"; if the
  binary vanished between probe and launch, the shell prints its real
  `not found` and drops to a real prompt.
- Exit behavior preserved: `exec sh -l` takes over when the app exits, so
  the user lands at the guest prompt (the 3.2 contract).
- Tradeoff: the typed line is no longer echoed into scrollback (the shell
  never reads it as input). The visible app output starts immediately.
- ONE path for all apps — registry data drives everything; no per-app code.

## 3. Settings joins Midnight

`ui/system/MidnightPage.kt` — shared page kit: `MidnightPageScaffold`
(screenBg canvas, edge-to-edge, 720dp centered, back chevron + mono title),
mono section labels, hairline dividers, `MidnightRadioRow` (whole-row target,
ring + Sapphire dot), `MidnightSwitch`/Slider remapped to Sapphire,
contentMaxWidth respected. Behavior unchanged: DataStore persistence,
immediate apply, slider commits on release.

## 4. Diagnostics joins Midnight

Same kit. Mono fact rows with honest state coloring (Sapphire = confirmed
good, danger = confirmed bad, quiet otherwise). Actions: filled Sapphire
(install/retry) vs quiet hairline (remove — destructive tone; check package
environment). Check-failure now surfaces in a MidnightBanner instead of
being swallowed. Nothing runs on open (the 3.4 explicitness rule).

## 5. Packages joins Midnight

Search field: chrome plate, hairline → Sapphire on focus, mono text; the
inline trailing busy marker stays. Search hits: flat mono rows between
hairlines (name+version mono, dim description, Sapphire "Installed · v" or a
filled Install button). Catalog cards stay (real objects) in the chrome
tone; Uninstall is the quiet destructive weight. Operation progress: the
honest banner (danger border only on FAILED, hairline while running) with
Cancel/Retry. Every v0.4.2/v0.4.4 honesty rule preserved verbatim.

## 6. Status bar

All five screens now render fixed Midnight chrome → light status-bar icons
everywhere (`MainActivity` when-branch extended). The app-theme branch
remains for future screens.

## 7. Tests

- New `CommandAppsTest` pins for `guestLaunchChain`: single-token form
  (`kilo; exec /bin/sh -l`), multi-token joining, defense-in-depth quoting,
  and every registry entry building a chain that ends in the login-shell
  fallback.
- All pre-existing registry/probe/packages pins unchanged and green.
