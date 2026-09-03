const VERSION = "v0.7.0-m3.2";

const HASHES = {
  apk: "1138ba4df9e702d2a8b650547e583dd4e1524a6b5bb8d186533328b3372b5f16",
  zip: "6a10fd5e47f2ac9b94340e2fcb3dacf32b251ca581c0123ebb37765fdd050903",
  tgz: "993046439838069be54551d72b1ed557e4b0b6f748f276ba3afb21c98355fce2",
  bundle: "1eaebb7e34dc99a687d7db8efd4df3cc4637f6972e4a77ec3f628d614f6f10ee",
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
          Phase 3.2 — Home / OS Launcher + Command Apps{" "}
          <span className="badge">versionCode 19</span>
        </h2>
        <p>
          PocketShell Home is no longer a terminal dashboard — it is the{" "}
          <b>launcher of a Linux-centric environment</b>. The Phase 3.1
          terminal redesign (chrome, tabs, keyboard, palette, PTY pipeline),
          the runtime, the Linux environment, the package manager, installed
          packages and Hermes are <b>untouched</b>. Design contract committed
          before implementation (<code>docs/PHASE-3.2-DESIGN.md</code>),
          plan-first.
        </p>
        <ul className="steps">
          <li>
            <b>Packages ≠ Apps:</b> packages are infrastructure, apps are
            experiences. The old "Installed CLI Apps" section is GONE — git,
            nano, python, node, npm, gcc, g++, htop and vim can never become
            launcher tiles (test-pinned). Only interactive command apps the
            guest confirms appear: <b>Hermes Agent</b>, OpenCode, Claude Code,
            ZCode (extensible registry).
          </li>
          <li>
            <b>Guest-confirmed availability:</b> one batched{" "}
            <code>sh -lc</code> probe asks exactly "would a fresh guest login
            shell find this command?" — the same environment your typing sees,
            where uv-installed launchers (<code>hermes</code>) are reachable.
            A failed probe renders "could not be checked" and keeps the last
            real list — never a fake "no apps".
          </li>
          <li>
            <b>Launch by tap, not by typing:</b> tapping Hermes runs a fresh
            single probe, then opens a NEW dedicated guest session whose PTY
            receives <code>hermes</code> — the typed command stays visible in
            its scrollback and exiting the app returns to the guest prompt.
            What the launcher does is exactly what typing would do.
          </li>
          <li>
            <b>Midnight Sapphire launcher:</b> the same system the Phase 3.1
            terminal uses — page <code>#0B1424</code>, the Terminal tile in
            the exact canvas color <code>#080F1D</code> (it IS the terminal),
            Linux tile <code>#101B30</code>, drawn brand mark + mono wordmark
            + tagline, drawn prompt and mountain marks, ONE Sapphire accent{" "}
            <code>#7FA3EF</code>. No pure black, no gradients on this page.
          </li>
          <li>
            <b>The two foundations:</b> Terminal (live "N running" chip) and
            Linux (honest state line: "Alpine Linux · ready" → enters the
            guest; every other state routes to Diagnostics exactly as before —
            distro-agnostic by construction).
          </li>
          <li>
            <b>Launcher composition, not a card stack:</b> 64dp monogram app
            tiles in a responsive grid (3 columns phone / 4 tablet / 6 large),
            content capped 720dp centered on tablets; a beautiful honest
            empty state ("Your tools will appear here" + Explore packages); a
            compact session continuation area (green dot only for live
            processes, tap returns); ONE custom floating quick-action control
            (no bottom navigation bar): New Terminal (fresh), New Linux
            session, each available command app — real actions only, the list
            is data so future capabilities join without a redesign.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.2-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.2)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 19 installs <b>in place over v0.7.0-m3.1 (18), the
          discarded v0.7.0-ui (17) and v0.6.2 (16)</b> — same pinned signing
          key. Your Alpine runtime, installed packages, Hermes installation
          and cache are untouched: Phase 3.2 changed no runtime code — the
          M2.6 Gates A–H environment and the Phase 3.1 §12 gate remain valid.
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
            packages screen — search, install, uninstall, open; Home no longer
            lists packages (they are infrastructure, not launcher apps).
          </li>
          <li>
            Phase 3.1: the Terminal screen redesigned — Midnight Sapphire
            surfaces, JetBrains Mono NL, editor tabs, from-scratch keyboard
            with the exact final layout (no system IME anywhere).
          </li>
          <li>
            <b>v0.7.0-m3.2 (this build):</b> the Home screen redesigned into
            the PocketShell OS launcher, with the command-launchable app
            architecture (Hermes appears when available and launches by tap).
            Everything else is deliberately byte-identical behavior.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §13 — Phase 3.2 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.7.0-m3.1 (no uninstall) → Home opens on
            the Midnight launcher.
          </li>
          <li>
            <b>Honesty:</b> whatever packages the device has (nano, git,
            python, node, htop…), NONE appear on Home. If{" "}
            <code>hermes</code> is available in the guest, a Hermes Agent tile
            appears automatically; if not, no tile and no fake entry.
          </li>
          <li>
            <b>Launch:</b> tap Hermes → spinner on the tile → a NEW terminal
            session where <code>hermes</code> is running (you never typed it);
            exiting returns to the guest prompt.
          </li>
          <li>
            <b>Floating quick actions:</b> the Sapphire control bottom-right
            rotates + → ×, the page dims, labeled chips emerge (New Terminal
            creates a FRESH session; New Linux session only when READY;
            command apps launch). Dismiss via scrim, ×, or Back.
          </li>
          <li>
            <b>Sessions:</b> compact rows with green dots only for live
            processes; "(exited)" dimmed; tap returns to the session.
          </li>
          <li>
            <b>Responsive:</b> phone 3-column grid; tablet ≥600dp gets more
            columns with content capped ~720dp centered; status-bar icons are
            light on Home in both system themes.
          </li>
          <li>
            <b>Phase 3.1 regression:</b> terminal chrome/tabs/keyboard
            identical to the §12-accepted state; <code>apk update</code>,{" "}
            <code>node --version</code>, <code>hermes --version</code> still
            pass (§12.6).
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 0efd645 —
          includes the complete milestone history, the honest record of the
          discarded UI attempt + rollback, and both Phase 3 design contracts).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.2-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.2-source.tar.gz">
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
        2026-09-02) · v0.6.2 hardlink + sysdata repairs · v0.7.0-m3.1 Phase 3.1
        terminal redesign · <b>v0.7.0-m3.2 (this build): Phase 3.2 — Home / OS
        launcher + command-launchable apps, home-only scope</b>. Next: Phase
        3.3 candidates per your direction. Your device keeps doing the QA that
        matters.
      </footer>
    </main>
  );
}
