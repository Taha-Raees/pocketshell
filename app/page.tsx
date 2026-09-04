const VERSION = "v0.7.0-m3.6";

const HASHES = {
  apk: "195443f9eaf1ccaa1c686de88f318749427d4a53897a7d3308000b074e665e79",
  zip: "5e58c9302377e12027168024054e1c726b3293d3a58ebcf32d272ab83dd50385",
  tgz: "bb17d7262ed7ad759c7677ca2fa19b0aa93fa4f163553d81cd55a59d4a4a9391",
  bundle: "122206cd69f56ce279bf02d66f336e101aa6f1656959c017d061b55c8054b689",
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
          Phase 3.6 — The Procfs Contract: real /proc, every session, forever{" "}
          <span className="badge">versionCode 23</span>
        </h2>
        <p>
          The fix you proved by hand: <b>the guest now always has a real,
          populated <code>/proc</code> — automatically, at every environment
          start, with no manual mount</b>. Root cause chain (fully
          reconstructed from the evidence): your in-guest{" "}
          <code>apk update &amp;&amp; apk upgrade</code> replaced the
          checksum-pinned, patched <code>libapk</code> library; that made the
          M2.6 conditional /proc gate fail its verification; every new session
          then silently spawned <i>without</i> /proc; and Bun-compiled CLIs
          (Kilo Code&apos;s embedded Bun runtime) resolve paths through{" "}
          <code>/proc/self/fd</code> on aarch64 — which has no{" "}
          <code>realpath</code> syscall — so <code>realpath()</code> of
          perfectly existing directories returned <code>ENOENT</code>. Your
          manual <code>mount -t proc proc /proc</code> proved the diagnosis.
          Full contract: <code>docs/PROCFS-CONTRACT.md</code>.
        </p>
        <ul className="steps">
          <li>
            <b>The /proc bind is now ABSOLUTE:</b> every interactive terminal
            session binds a real host <code>/proc</code> through proot — the
            conditional gate is gone, the bind is derived from the session
            profile, and it can no longer be skipped by any apk state. The
            separate package-operation session class stays /proc-free
            (require-guarded, the device-proven SELinux-safe apk commit
            environment).
          </li>
          <li>
            <b>apk compatibility is now self-repairing:</b> the patched-libapk
            verification was rebuilt as a pattern-based repair — it scans
            whatever <code>libapk.so.3*</code> the guest actually carries for
            the apk-tools fd-gate literals and re-applies the one-byte patch
            to any matching build, refusing ambiguous shapes without writing.
            A future in-guest <code>apk upgrade</code> can no longer silently
            break <code>apk</code> itself.
          </li>
          <li>
            <b>Fail-loud spawn contract:</b> every interactive session spec is
            audited at spawn for the /proc + /dev + /sys bindings — a stripped
            spec refuses to start with an explicit diagnostic instead of
            running crippled. Pinned by unit tests, including the
            stripped-spec cases.
          </li>
          <li>
            <b>Per-session mount audit shipped:</b> docs/PROCFS-CONTRACT.md
            documents every binding (/dev, /dev/pts, /sys, /tmp, /proc) as
            intentional and functional, plus the guest-runnable smoke gate{" "}
            <code>scripts/diagnose_platform.sh</code> (procfs + pseudo-fs +
            libc identity checks) you can run in any session.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.6-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.6)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 23 installs <b>in place over v0.7.0-m3.5 (22),
          v0.7.0-m3.4 (21), v0.7.0-m3.3 (20), v0.7.0-m3.2 (19), v0.7.0-m3.1
          (18), the discarded v0.7.0-ui (17) and v0.6.2 (16)</b> — same pinned
          signing key. Your Alpine runtime, installed packages, Kilo/Hermes
          installation and cache are untouched: Phase 3.6 changed only how
          sessions bind procfs and how apk compatibility repairs itself — the
          tap-to-launch pipeline, the registry probing, the M2.6 Gates A–H
          environment and the §12–§15 gates remain valid.
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
            Linux Shell (M2.3 + M2.6 + <b>3.6</b>): the Alpine guest via proot
            — with a <b>guaranteed</b> real <code>/proc</code>, and{" "}
            <code>apk</code> that survives guest-side upgrades.
          </li>
          <li>
            Package management (M2.4/M2.5): the Packages screen — search,
            install, uninstall, open; packages are infrastructure and never
            launcher apps.
          </li>
          <li>
            Phase 3.1–3.4: Midnight Sapphire terminal + Home workspace, the
            command-app registry (nine probe-gated CLIs), tap-to-launch via
            the argv transport, and the Midnight system pages.
          </li>
          <li>
            <b>v0.7.0-m3.6 (this build):</b> the procfs contract — real /proc
            on every interactive session (absolute, not conditional), the
            pattern-based apk self-repair, the fail-loud spawn audit, and the
            per-session bind documentation + guest diagnostic script.
            Everything else is deliberately byte-identical behavior.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §16 — Phase 3.6 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.7.0-m3.5 (no uninstall) → open a fresh
            Linux session: <code>ls /proc/self &amp;&amp; cat /proc/version</code>{" "}
            works <b>without any manual mount</b>; <code>ps</code>,{" "}
            <code>top</code>, <code>htop</code> all show real process data.
          </li>
          <li>
            <b>The Kilo reproduction:</b> with <code>kilo</code> installed, tap
            the Kilo Code tile — the CLI starts with no{" "}
            <code>ENOENT ... realpath</code> and no manual mount; its embedded
            Bun runtime resolves paths normally.
          </li>
          <li>
            <b>apk survives upgrades:</b> run <code>apk update &amp;&amp; apk
            upgrade</code> inside the guest, then <code>apk add</code> a small
            package — the fd-gate self-repair re-patches any replaced libapk
            and apk keeps committing transactions (no SELinux denial).
          </li>
          <li>
            <b>Fail-loud, never silently crippled:</b> the guest diagnostic{" "}
            <code>sh scripts/diagnose_platform.sh</code> (if present in a
            session) reports PASS on procfs + pseudo-fs contract.
          </li>
          <li>
            <b>Regressions:</b> tap-to-launch still runs tiles (§15), the
            §14 Home sweep, §12 keyboard/terminal set and §13 command-app
            behavior all still pass; restarts of the app, terminal, session
            and environment all re-bind /proc.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 9539a03 —
          includes the complete milestone history, the honest record of the
          discarded UI attempt + rollback, all six Phase 3 design contracts,
          and the procfs contract document).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.6-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.6-source.tar.gz">
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
        workspace · v0.7.0-m3.4 registry expansion · v0.7.0-m3.5 command
        launch fix · <b>v0.7.0-m3.6 (this build): the procfs contract — real
        /proc, every session, forever</b>. Your device keeps doing the QA that
        matters.
      </footer>
    </main>
  );
}
