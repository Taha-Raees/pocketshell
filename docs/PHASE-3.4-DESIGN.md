# Phase 3.4 — Command Registry Expansion + System-Page Consistency (design contract)

Status: CONTRACT — committed before implementation.
Scope: one registry data fix (kilo missing from the launcher) + applying the
Phase 3.3 design guidelines (docs/PHASE-3.3-DESIGN.md §3/§4/§9/§10) to the
non-Home screens. **No pipeline changes, no terminal changes.**
Version target: `0.7.0-m3.4`, versionCode 21.

Trigger (user device report, v0.7.0-m3.3): "I have installed kilocli but it
doesn't show up" + request to improve the other pages according to the design
guidelines.

Frozen (must not regress):

- Phase 3.1 — terminal rendering, keyboard, PTY dispatch, Midnight chrome.
- Phase 3.2 — command-app **pipeline**: login-shell probing
  (`guestCommandPaths`), verify-before-launch, dedicated guest sessions,
  forbidden package list, the honesty contract (absent binary never renders,
  probe failure never fakes "none"). Only registry DATA changes.
- Phase 3.3 — Home composition, surface philosophy, single CLI control,
  single-purpose FAB. Home needs **zero code changes**: the tools grid and the
  CLI Apps menu render from the registry, so new entries surface
  automatically (§11 future-expansion promise of the 3.3 contract).

---

## 1. Diagnosis — why the installed Kilo CLI does not appear

Architecture recap (Phase 3.2): discovery = `registry ∩ guest PATH`. The
probe asks the guest's login shell `command -v <name>` **only for names in
`CommandAppCatalog.registry`**. Kilo Code CLI (`npm install -g @kilocode/cli`)
provides the command `kilo` — and `kilo` was never in the registry, so:

1. `refreshCommandApps()` never probes `kilo` → it can never appear in
   `CommandAppsState.apps`.
2. `availableCommandApps()` (pure classification) filters the registry — an
   unregistered but installed command is invisible by construction.

This is not a probe bug and not an installation failure: the honesty contract
deliberately refuses to guess apps from unknown binaries in PATH (any
executable could be a one-shot utility, a script, or a toolchain — packages
are infrastructure, apps are experiences). The registry is the curated list
of "known meaningful interactive command apps", and Kilo now belongs there.

Verification facts: Kilo Code CLI installs via `npm i -g @kilocode/cli`; the
binary/command is `kilo` (kilo.ai docs, npm). Inside the guest, npm global
installs land under the login-shell PATH (`/usr/local/bin` or `/usr/bin` —
both covered by `/etc/profile` + `~/.profile` sourcing that `sh -l` performs),
so once registered, the existing probe answers honestly. If a user's npm
prefix is outside PATH, the tile stays absent — which is correct, because
typing `kilo` in a fresh session would fail too (ground truth).

Fix: add `kilo` to the registry (§2). After the update, the next Home probe
(RUN on Home-visible-with-READY and after any package operation) surfaces it.

## 2. Registry expansion (data only)

Five additions to `CommandAppCatalog.registry`, appended after the existing
four (existing order untouched — launcher order on already-installed devices
does not shuffle):

| id       | displayName | command    | monogram | description                                   |
|----------|-------------|------------|----------|-----------------------------------------------|
| `kilo`   | Kilo Code   | `kilo`     | K        | Open-source AI coding agent for the terminal   |
| `gemini` | Gemini CLI  | `gemini`   | G        | Google's AI agent for the terminal             |
| `codex`  | Codex       | `codex`    | C        | OpenAI's terminal coding agent                 |
| `aider`  | Aider       | `aider`    | A        | AI pair programming in your terminal           |
| `qwen`   | Qwen Code   | `qwen`     | Q        | Qwen coding agent for the terminal             |

Rules held (pinned by `CommandAppsTest`):

- Plain single-token commands, argv-safe, `id == launchCommand.first()`
  (existing invariant).
- None collides with the forbidden package list (git/nano/python/node/npm/
  gcc/g++/htop/vim/shell utilities) — all five are interactive applications.
- Absent binaries still can never render (`availableCommandApps` unchanged,
  untouched code path) — installing e.g. `gemini` is the only way a Gemini
  tile ever appears.
- `kilo` display name is "Kilo Code" — matches the product (Kilo Code CLI);
  the Alpine `kilo` text editor is not in the guest's configured
  repositories, and even a hypothetical collision would launch exactly what
  the user's own `kilo` typing launches (the probe never lies).

Test updates (`CommandAppsTest`): pin `kilo` explicitly (id, displayName,
launchCommand) in the seed-registry test; add the four peers to the
present-name assertions; all existing invariants must stay green unmodified.

## 3. Packages screen (Explore) — guideline fixes

Audited against §3/§4/§9/§10 of the 3.3 contract:

| # | Finding | Rule | Fix |
|---|---------|------|-----|
| 1 | "Runtime not installed" state renders as a `Surface` info card — a container around a text-only empty state | §9 "empty states are inline text, never containers" | Replace with the 3-line inline state on the canvas: title line, dim explanation, accent link |
| 2 | That state says "Install the runtime from Diagnostics" but offers no affordance | every element justifies itself / actionable states act | Add the `Open Diagnostics` accent text link (new `onOpenDiagnostics` callback wired in `MainActivity`) |
| 3 | Screen title "Explore CLI Apps" vs Home's affordance "Explore packages" / "Packages" | one name per object | Title becomes **"Packages"** (the OS-level object name; the old title overclaims "CLI Apps" for what is apk package management) |
| 4 | Per-package `Surface` cards | §4 allowed — real objects (installable package + actions) | Keep, unchanged |

Non-changes: search flow, honest operation progress surface (an actionable
banner — an object, allowed), apk-truth status lines, cancel/retry wiring.

## 4. Settings — guideline fixes

Audited: structure is already divider-based flat (no card groups). Real
defects are interaction, not boxes:

| # | Finding | Rule | Fix |
|---|---------|------|-----|
| 1 | Theme radio rows toggle ONLY when the 24dp RadioButton is hit — the label is inert | ≥40dp targets, whole-row selection is the OS idiom | Whole row clickable (`Role.RadioButton`), min height 48dp |
| 2 | Dynamic-color Switch row toggles ONLY on the 40dp switch | same | Whole row clickable (`Role.Switch`), min height 48dp; switch stays the state renderer |
| 3 | Font-size slider | fine | unchanged |

Visual language stays the app-theme Material look (§10 keeps this decision):
no Midnight mono restyle on Settings.

## 5. Diagnostics — guideline fixes

Audited: honest content, read-only facts — keep every value and label.

| # | Finding | Rule | Fix |
|---|---------|------|-----|
| 1 | First fact block has no section header, while later blocks have "Linux runtime" / "Package environment" (titleMedium) | consistent hierarchy: typography + dividers, same pattern per section | Add the `System` header above the snapshot rows |
| 2 | Snapshot rows draw internal dividers; runtime/package fact rows have none — two list dialects on one page | one separation language | Fact rows are plain rows everywhere (uniform 6dp rhythm); sections are separated by the existing full-width divider + header. Snapshot block loses its internal dividers |

Non-changes: values, colors (ok/error semantics), action buttons, explicit
check-only package gate, "last event" honesty.

## 6. Non-goals

- Terminal screen: frozen (Phase 3.1 contract).
- `AlpinePackageManager.guestCommandPaths`, `TerminalViewModel` launch
  pipeline, `TerminalSessionManager`: untouched.
- No theme/token changes; no new colors; no Home edits.
- No auto-discovery of unregistered PATH executables (honesty contract).

## Checkpoints

- **3.4.1 Registry expansion** — §2 data + test pins. Build + full suite.
- **3.4.2 System pages** — §3 + §4 + §5. Build + full suite.
- **3.4.3 Polish + gate** — a11y recheck (roles, labels, targets),
  versionCode 21 / `0.7.0-m3.4`, full suite + assembleDebug + aapt2/apksigner
  verify, docs + payload + delivery page.

## Validation gate

- Full suite green (644 + new registry assertions, 0 failures);
  `CommandAppsTest` invariants intact with the five new entries.
- APK = versionCode 21 / 0.7.0-m3.4; cert chain intact
  (d96a6f66…8bf659) for in-place update 16→…→21.
- Device gate (TESTING §15): with `kilo` installed in the guest, the Kilo
  Code tile appears under "Your tools" and in the CLI Apps menu within one
  Home revisit; tapping it launches a dedicated guest session; uninstalling
  makes it disappear. Settings rows select on full-row taps; Packages
  not-ready state is inline text with a working Diagnostics link;
  Diagnostics sections read System → Linux runtime → Package environment.
