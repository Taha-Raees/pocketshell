const VERSION = "v0.7.0-m3.4";

const HASHES = {
  apk: "2059d1965957e09a05d1bfa313f24c98030e3df23ed2893397b66d44f0d60dd5",
  zip: "9b73fe8cfa915b476fde3debed1b413820a1447559aebe7f20c251ebd240c4d6",
  tgz: "37fd374080fdf74cb84b569434775a19f55acd3555822d0ade004a7c5f74f651",
  bundle: "b9c53eef35652c553773860e04c3a7f4c365a6c4a8da1e18ecdda9fdcbaabd20",
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
          Phase 3.4 — Registry Expansion + System Pages{" "}
          <span className="badge">versionCode 21</span>
        </h2>
        <p>
          The fix you reported: <b>an installed Kilo CLI now shows up</b>. Root
          cause: command-app discovery = registry ∩ guest PATH — the probe asks
          the login shell <code>command -v</code> only for registry names, and{" "}
          <code>kilo</code> had no registry entry, so it was never probed and
          could never appear. That is the honesty contract working (the
          launcher never guesses from unknown PATH binaries); the registry is
          the designed extension point. Kilo is now registered — probe-gated
          like every app: the tile appears within one Home revisit once{" "}
          <code>kilo</code> exists, and disappears when it does not. ZERO
          pipeline changes. Design contract committed before implementation (
          <code>docs/PHASE-3.4-DESIGN.md</code>).
        </p>
        <ul className="steps">
          <li>
            <b>Registry expansion (data only):</b> five terminal AI agents
            seeded — <b>Kilo Code</b> (<code>kilo</code>), <b>Gemini CLI</b> (
            <code>gemini</code>), <b>Codex</b> (<code>codex</code>),{" "}
            <b>Aider</b> (<code>aider</code>), <b>Qwen Code</b> (
            <code>qwen</code>) — appended after the brief&apos;s four
            (Hermes/OpenCode/Claude/ZCode), so launcher order on installed
            devices never shuffles. Installing any of them is still the ONLY
            way its tile can ever render; uninstall makes it disappear.
          </li>
          <li>
            <b>Packages screen:</b> the &quot;runtime not installed&quot; state
            is now inline text on the canvas (no container card) with a real{" "}
            <b>Open Diagnostics</b> link — the action the copy always pointed
            at. Title renamed &quot;Explore CLI Apps&quot; →{" "}
            <b>&quot;Packages&quot;</b> (one name per object, matching Home).
            Per-package surfaces stay — real objects (an installable package
            with actions) are explicitly allowed.
          </li>
          <li>
            <b>Settings:</b> whole-row selection — theme radio rows and the
            dynamic-color row are ≥48dp full-row touch targets with correct
            accessibility roles; the dot/switch only render state. Visual
            language unchanged.
          </li>
          <li>
            <b>Diagnostics:</b> one uniform section pattern — full-width
            divider + header for <b>System / Linux runtime / Package
            environment</b>, plain label/value fact rows, no internal per-row
            dividers. Every value stays exactly as honest as before.
          </li>
          <li>
            <b>Home needs zero changes:</b> the &quot;Your tools&quot; grid and
            the <code>CLI Apps ▾</code> menu render from the registry — new
            entries surface automatically.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.4-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.4)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 21 installs <b>in place over v0.7.0-m3.3 (20),
          v0.7.0-m3.2 (19), v0.7.0-m3.1 (18), the discarded v0.7.0-ui (17) and
          v0.6.2 (16)</b> — same pinned signing key. Your Alpine runtime,
          installed packages, Hermes installation and cache are untouched:
          Phase 3.4 changed registry DATA and three screens&apos; presentation —
          the M2.6 Gates A–H environment, the Phase 3.1 §12 gate, the Phase
          3.2 §13 gate and the Phase 3.3 §14 gate remain valid.
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
            <b>v0.7.0-m3.4 (this build):</b> the registry grows to nine
            command apps (Kilo Code + four peers, all probe-gated) and the
            Packages / Settings / Diagnostics screens adopt the Phase 3.3
            guidelines concretely. Everything else is deliberately
            byte-identical behavior.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §15 — Phase 3.4 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.7.0-m3.3 (no uninstall) → with{" "}
            <code>kilo</code> installed in the guest, a <b>Kilo Code</b> entry
            appears under &quot;Your tools&quot; AND in the <code>CLI Apps ▾</code>{" "}
            menu within one Home revisit; tapping it launches a dedicated
            guest session.
          </li>
          <li>
            <b>Probe-gating holds:</b> gemini/codex/aider/qwen do NOT appear
            unless actually installed; nano/git/python still never appear;
            removing the <code>kilo</code> binary removes the tile.
          </li>
          <li>
            <b>Packages screen:</b> titled &quot;Packages&quot;; with no
            runtime the state is inline text (no card) with a working
            Open Diagnostics link; search/install/open behave exactly as §9/§13.
          </li>
          <li>
            <b>Settings:</b> tapping anywhere on a theme row selects it (the
            label is no longer inert); the wallpaper-colors row toggles
            end-to-end; no visual restyle.
          </li>
          <li>
            <b>Diagnostics:</b> sections read System → Linux runtime →
            Package environment, each divider + header + plain rows; values,
            colors and buttons unchanged (§10 set).
          </li>
          <li>
            <b>Regressions:</b> Home itself gained no new elements (§14 sweep
            unchanged); §12 keyboard/terminal set and §13 command-app +
            sessions behavior all still pass.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip fcbe2f2 —
          includes the complete milestone history, the honest record of the
          discarded UI attempt + rollback, and all four Phase 3 design
          contracts).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.4-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.4-source.tar.gz">
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
        workspace · <b>v0.7.0-m3.4 (this build): Phase 3.4 — registry expansion
        (Kilo Code + peers) + system pages</b>. Your device keeps doing the QA
        that matters.
      </footer>
    </main>
  );
}
