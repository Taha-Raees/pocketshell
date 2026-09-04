const VERSION = "v0.7.0-m3.5";

const HASHES = {
  apk: "c8d0effb3fb1ff81feeb9deb2822f9c8e15630e1ca584ce80dc07f1c06d91d0e",
  zip: "5d3ef098a8bc69e4cd777845f426d4737c85432ed0b67543845a8f4337b3619b",
  tgz: "9e95b3112dc898b7a276e89c85c6bc2467cf337e540fddbfbb7277015b5ebaee",
  bundle: "a74645767f56a6a290616dd452b8fee76695be5492c0e470108bd36db568767e",
};

function Sha({ text }: { text: string }) {
  return <div className="mono">sha256 {text}</div>;
}

export default function Home() {
  return (
    <main className="wrap">
      <h1>
        PocketShell <span className="badge">{VERSION}</span>
      </h1>
      <p className="sub">
        A real Linux terminal for Android — Termux-grade terminal emulator,
        Alpine Linux guest via proot, real <code>apk</code> package manager.
        REUSE → INTEGRATE → OPTIMIZE → IMPROVE. Nothing faked, ever.
      </p>

      <div className="card primary">
        <h2>
          Phase 3.5 — Command Launch Fix + System Pages Join Midnight{" "}
          <span className="badge">versionCode 22</span>
        </h2>
        <p>
          The fix you reported: <b>tapping Kilo Code (or any app tile) now
          directly launches the app</b> — no more plain shell. Root cause: the
          launch command was written into the PTY immediately after session
          construction, but the terminal&apos;s <code>TerminalSession</code>{" "}
          forks the process only when the view first renders the session, and{" "}
          <code>write()</code> silently drops bytes while no process exists —
          so every tapped tile opened a plain login shell. The fix delivers the
          command through the login shell&apos;s argv (
          <code>sh -l -c &quot;kilo; exec sh -l&quot;</code>): deterministic,
          timing-independent, still exactly what typing the command would do.
          Design contract committed before implementation (
          <code>docs/PHASE-3.5-DESIGN.md</code>).
        </p>
        <ul className="steps">
          <li>
            <b>One generic launch path — no hardcoding:</b> the registry stays
            pure data (nine command apps); availability still comes ONLY from
            the real login-shell probe. The same fixed path serves command apps
            (Kilo Code, Claude Code, Gemini CLI, …) and catalog apps (nano,
            git, …). Installing an app is still the only way its tile appears.
          </li>
          <li>
            <b>Launch semantics preserved:</b> a fresh dedicated guest session
            per launch, verify-before-launch with honest refusal banners, and{" "}
            <code>exec sh -l</code> drops you at a real prompt when the app
            exits.
          </li>
          <li>
            <b>Diagnostics joins Midnight Sapphire:</b> the Midnight canvas,
            mono page title + section labels, hairline dividers, mono fact
            values with honest coloring (Sapphire = confirmed good, danger =
            confirmed bad). Install/retry is the filled Sapphire action;
            remove/check is the quiet hairline action.
          </li>
          <li>
            <b>Packages joins Midnight Sapphire:</b> chrome search plate with a
            Sapphire focus ring, search hits as flat mono rows, catalog cards
            in the chrome tone, uninstall as the quiet destructive weight, and
            operation progress as the honest banner (danger border only on
            FAILED) with Cancel/Retry. All honesty rules intact — a failed
            probe never reads as &quot;Not installed&quot;.
          </li>
          <li>
            <b>Settings joins Midnight Sapphire:</b> whole-row radio rows
            (ring + Sapphire dot), Midnight switch and slider, same immediate
            persistence. Status bar: light icons on every screen now.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.5-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.5)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 22 installs <b>in place over v0.7.0-m3.4 (21),
          v0.7.0-m3.3 (20), v0.7.0-m3.2 (19), v0.7.0-m3.1 (18), the discarded
          v0.7.0-ui (17) and v0.6.2 (16)</b> — same pinned signing key. Your
          Alpine runtime, installed packages, Kilo/Hermes installation and
          cache are untouched: Phase 3.5 changed one launch transport and three
          screens&apos; presentation — the M2.6 Gates A–H environment, the
          Phase 3.1 §12 gate, the Phase 3.2 §13 gate and the Phase 3.3 §14
          gate remain valid.
        </p>
      </div>

      <div className="card">
        <h2>What this build actually does (honest scope)</h2>
        <ul className="steps">
          <li>
            Real terminal (M1): Termux-emulator PTY sessions, clipboard paste,
            scrollback, selection.
          </li>
          <li>
            Linux runtime (M2.2): pinned Alpine 3.24.1 aarch64,
            SHA-256-verified, installed into app storage.
          </li>
          <li>
            Linux Shell (M2.3 + M2.6): the Alpine guest via proot — with{" "}
            <code>/proc</code>, and <code>apk</code> still working.
          </li>
          <li>
            Package management (M2.4/M2.5, device-gate PASSED): the Packages
            screen — search, install, uninstall, open; packages are
            infrastructure and never launcher apps.
          </li>
          <li>
            Phase 3.1: the Terminal screen — Midnight Sapphire surfaces,
            JetBrains Mono NL, editor tabs, from-scratch keyboard (no system
            IME anywhere).
          </li>
          <li>
            Phase 3.2: the command-launchable app architecture — Hermes Agent,
            OpenCode, Claude Code, ZCode appear when the guest confirms them
            and launch by tap into dedicated guest sessions.
          </li>
          <li>
            Phase 3.3: Home as a flat workspace — surfaces only for objects,
            one CLI Apps menu, single-purpose FAB, lightweight honest states.
          </li>
          <li>
            Phase 3.4: registry grows to nine command apps (Kilo Code + four
            peers, all probe-gated).
          </li>
          <li>
            <b>v0.7.0-m3.5 (this build):</b> the launch-pipeline fix — tiles
            actually run their command now — and Diagnostics / Packages /
            Settings adopt the Midnight Sapphire design language via one
            shared page kit. Everything else is deliberately byte-identical
            behavior.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §15 — Phase 3.5 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.7.0-m3.4 (no uninstall) → with{" "}
            <code>kilo</code> installed in the guest, tap the <b>Kilo Code</b>{" "}
            tile: the session opens and <b>kilo itself launches</b> — its TUI
            appears without typing anything. Exiting lands at the guest prompt.
          </li>
          <li>
            <b>Same for every app:</b> Claude Code / Gemini CLI tiles launch
            their commands; Packages → Open on an installed nano opens nano in
            a dedicated session.
          </li>
          <li>
            <b>Probe-gating holds:</b> gemini/codex/aider/qwen do NOT appear
            unless actually installed; nano/git/python still never appear as
            command apps; removing the <code>kilo</code> binary removes the
            tile.
          </li>
          <li>
            <b>Midnight system pages:</b> Diagnostics, Packages and Settings
            now share the Home/Terminal Midnight identity — same canvas, mono
            titles, hairline dividers; light status-bar icons on every screen.
          </li>
          <li>
            <b>Packages:</b> search plate focuses with a Sapphire ring;
            installs show honest progress; a failed probe keeps the last real
            installed state visible.
          </li>
          <li>
            <b>Regressions:</b> Home unchanged (§14 sweep); §12
            keyboard/terminal set and §13 command-app + sessions behavior all
            still pass.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 7a8a136 —
          includes the complete milestone history, the honest record of the
          discarded UI attempt + rollback, and all five Phase 3 design
          contracts).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.5-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.5-source.tar.gz">
          source.tar.gz
        </a>
        <a className="btn secondary" href="/pocketshell-m2.gitbundle">
          git bundle (full history)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <p>
          Restore: <code>git clone pocketshell-m2.gitbundle pocketshell</code>.
          Includes <code>keystore/debug.keystore</code> — clones build APKs
          with the same signing identity.
        </p>
      </div>

      <footer>
        Milestones: M0–M1.3 terminal · M2.2 runtime install · M2.3 Linux shell
        (device-verified) · M2.4 package layer (device gate PASSED) · M2.5
        search-install · M2.6 real /proc + real apk (device-CONFIRMED
        2026-09-02) · v0.6.2 hardlink + sysdata repairs · v0.7.0-m3.1 terminal
        redesign · v0.7.0-m3.2 OS launcher + command apps · v0.7.0-m3.3 flat
        workspace · v0.7.0-m3.4 registry expansion · <b>v0.7.0-m3.5 (this
        build): Phase 3.5 — command launch fix + system pages join
        Midnight</b>. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}
