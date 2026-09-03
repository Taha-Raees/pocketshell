const VERSION = "v0.7.0-m3.3";

const HASHES = {
  apk: "a7acd843ff85d691ad50c548d566663f3919a3de2e963e17087a81828aa0dca0",
  zip: "cd554f97e300f3d859c2242919760e1750fe22c935a39e81118a17a312be4de7",
  tgz: "d5e11ae7b905622cea1ab4cafe78cd0233f75e024fd9b7a73aff78125b651e9d",
  bundle: "108a986d24e13f0b23a810d75a57028be7a6d256b878407458bd244d4ea81611",
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
          Phase 3.3 — Home &amp; System UI Redesign{" "}
          <span className="badge">versionCode 20</span>
        </h2>
        <p>
          Phase 3.2 fixed the command-app architecture; Phase 3.3 fixes the{" "}
          <b>layout structure itself</b>. The Home screen stops being a stack
          of rounded rectangles and becomes a workspace drawn on a canvas: a
          surface exists ONLY for a real object — the two environments, the
          CLI Apps menu, the floating create control, an actionable banner, a
          pressed row. Everything else is spacing, typography, hairline
          dividers and Midnight tone steps. ZERO backend changes: discovery,
          probing, verify-then-launch, the Phase 3.1 terminal/keyboard and
          every runtime behavior are byte-identical. Design contract committed
          before implementation (<code>docs/PHASE-3.3-DESIGN.md</code>).
        </p>
        <ul className="steps">
          <li>
            <b>One CLI control:</b> a quiet <code>CLI Apps ▾</code> trigger in
            the header area — shown only when the guest actually confirmed
            apps, never a dead button — opens a compact Midnight launcher
            menu: monogram plate + app name + the command dim and secondary
            (<code>hermes</code>). A row tap runs the exact Phase 3.2
            verify-then-launch pipeline. No logos, no dialog — the app itself
            is the identity. Every floating CLI affordance is gone.
          </li>
          <li>
            <b>Foundations, flatter:</b> Terminal and Linux are borderless
            tone-step surfaces (canvas / chrome), 14dp radius, no borders, no
            chip boxes. The truncating copy is gone — "Native shell" always
            fits; the running count is plain mono text; Linux states honestly
            ("Alpine · ready" in Sapphire; other states route to Diagnostics,
            distro-agnostic by construction).
          </li>
          <li>
            <b>Your tools:</b> command apps as icon + label launcher entries —
            52dp borderless monogram plates, NOT cards; a surface appears only
            while pressed. The empty state is three quiet lines directly on
            the canvas ("No CLI apps yet." + one sentence + the page's only
            Explore packages link) — no container, no ghost placeholders. With
            apps present, a single quiet "Packages" footer link replaces it:
            exactly ONE packages affordance in every state.
          </li>
          <li>
            <b>Sessions, flat:</b> dot + label + mono id between hairline
            dividers — the pressed row is the only surface the section ever
            draws. Green still means ONLY a live process; tap returns.
          </li>
          <li>
            <b>FAB, single-purpose:</b> the floating control now means exactly
            one thing — create a new session. New Terminal / New Linux
            session, as text-only chips. No icon circles, no logo marks, no
            command apps in the menu (apps live in the grid + the CLI menu).
          </li>
          <li>
            <b>Removed</b> (contract §12): the giant empty-state card, the
            ghost-tile placeholders, the duplicated "Explore packages", the
            FAB's app actions, the bordered chip on the Terminal tile, the
            bordered session cards, every decorative tile border, the 20dp
            hero radius, and "Command apps" naming on the page.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.3-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.3)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 20 installs <b>in place over v0.7.0-m3.2 (19),
          v0.7.0-m3.1 (18), the discarded v0.7.0-ui (17) and v0.6.2 (16)</b> —
          same pinned signing key. Your Alpine runtime, installed packages,
          Hermes installation and cache are untouched: Phase 3.3 changed
          presentation code only — the M2.6 Gates A–H environment, the Phase
          3.1 §12 gate and the Phase 3.2 §13 gate remain valid.
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
            Package management (M2.4/M2.5, device-gate PASSED): the Explore
            packages screen — search, install, uninstall, open; packages are
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
            <b>v0.7.0-m3.3 (this build):</b> Home &amp; system UI redesigned
            as a flat workspace — surfaces only for objects, one CLI Apps
            menu, single-purpose FAB, lightweight honest states. Everything
            else is deliberately byte-identical behavior.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §14 — Phase 3.3 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.7.0-m3.2 (no uninstall) → Home opens as
            the flat Midnight workspace.
          </li>
          <li>
            <b>The no-boxes sweep:</b> no bordered container around the tools
            section, the empty state, session rows or section headers — they
            sit on the canvas between hairline dividers. Surfaces exist only
            for the two environment tiles, the CLI Apps menu, the launch-error
            banner, the FAB + chips, and pressed states.
          </li>
          <li>
            <b>CLI Apps menu:</b> appears only when apps exist; lists ONLY
            guest-confirmed apps (nano/git/python can never appear); rows are
            plate + name + dim command; a tap launches into a dedicated guest
            session.
          </li>
          <li>
            <b>Empty state:</b> with no apps — "No CLI apps yet." + one
            sentence + Explore packages, three quiet lines, exactly ONE
            packages affordance. With apps — a single quiet "Packages" footer
            link instead.
          </li>
          <li>
            <b>FAB:</b> exactly two creation actions (New Terminal fresh; New
            Linux session when READY), text-only chips; scrim/×/Back dismiss.
          </li>
          <li>
            <b>Sessions + responsive:</b> flat divider rows, green only for
            live processes; phone 3-column grid, tablet 4/6 columns capped
            ~720dp; nothing truncated on the foundation tiles.
          </li>
          <li>
            <b>Regressions:</b> Phase 3.1 terminal chrome/tabs/keyboard
            identical to §12; Phase 3.2 §13 honesty checks (no packages on
            Home, Hermes iff available, tap-launch) all still pass.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 9803940 —
          includes the complete milestone history, the honest record of the
          discarded UI attempt + rollback, and all three Phase 3 design
          contracts).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.3-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.3-source.tar.gz">
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
        redesign · v0.7.0-m3.2 OS launcher + command apps · <b>v0.7.0-m3.3
        (this build): Phase 3.3 — Home &amp; system UI redesign, flat workspace,
        presentation-only</b>. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}
