const VERSION = "v0.5.0-m2.5";

const HASHES = {
  apk: "7c7ee05571201abdd4780fe63064b91eab88ccfdafc719f1888447f97ae7644d",
  zip: "fa62b17f0e28310e24c5cf4197c0a011e4be2f05a02e00f9370cfee2a49c6bd1",
  tgz: "f6709e11164ef3e3f4d7b6fc528aaa122cfe6c52ad18b2afda8c2e4a97ec0339",
  bundle: "9d687e4626cb137d311bda4fa4bb29a3bc538b6705ec21955ae37b7096bbb774",
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
          M2.4 gate <b>PASSED</b> — thank YOU for the evidence. M2.5 starts
          here <span className="badge">versionCode 13</span>
        </h2>
        <p>
          Your 10:04 screenshots confirmed v0.4.4 on device: Home lists{" "}
          <b>Nano 9.2-r0 and Git 2.54.0-r0</b> from the real apk database,
          Explore shows Nano “Installed · 9.2-r0”, and paste works. Then they
          caught two more real bugs — the “small errors when trying to add
          node” — both fixed in this build:
        </p>
        <ul className="steps">
          <li>
            <b>Your manual <code>apk update</code> / <code>apk add nodejs
            npm</code> in the Linux Shell died with “Permission denied”</b>{" "}
            while app installs worked. Why: v0.4.2 removed the{" "}
            <code>/proc</code> bind only from the app’s own package commands —
            the shell session kept it, and with <code>/proc</code> visible
            apk’s download commit hits the same Android SELinux neverallow.
            Your session also read a stale 31-package cache (hence
            “nodejs (no such package)”). Now <b>every guest session is
            apk-capable</b>: no <code>/proc</code>, the same shared apk cache
            the app uses, DNS refreshed at spawn. Install from the terminal
            or the UI — one cache, one index, one database.
            <br />
            <i>
              Honest cost: the guest can’t see <code>/proc</code>, so{" "}
              <code>ps</code>/<code>top</code> (and htop’s process list) have
              nothing to read inside the guest. A working package manager
              wins.
            </i>
          </li>
          <li>
            <b>Searching “node” buried nodejs</b> behind description matches
            (abseil-cpp-dev, ceph18…) and the 8-hit cutoff. Search results are
            now <b>ranked by name match</b> — nodejs and nodejs-current first
            — and <b>every hit is installable</b> with the same honest
            pipeline (apk update → add → info -e verify). No executable
            promises for non-catalog packages: nodejs ships <code>node</code>,
            not <code>nodejs</code>, so SUCCESS means exactly “the real
            database confirms it” — run it from the shell.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.5.0-m2.5-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.4.4)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall needed</h2>
        <p>
          Installs <b>in place over v0.4.4</b> (same pinned signing key). The
          runtime, your installed packages (nano, git) and the shared apk
          cache are untouched. Your existing terminal sessions keep running;
          <b> new</b> Linux Shell sessions spawn with the apk-capable shape.
        </p>
      </div>

      <div className="card">
        <h2>What this build actually does (honest scope)</h2>
        <ul className="steps">
          <li>
            Real terminal (M1): Termux-emulator PTY sessions, full keyboard,
            working clipboard paste.
          </li>
          <li>
            Linux runtime (M2.2): pinned Alpine 3.24.1 aarch64,
            SHA-256-verified, installed into app storage.
          </li>
          <li>
            Linux Shell (M2.3 + v0.5.0): the Alpine guest via proot — and now
            a shell where <code>apk</code> actually works.
          </li>
          <li>
            Package management (M2.4, device-gate PASSED): curated cards
            (nano, htop, vim, git, python3) with Open/Uninstall; Home shows
            exactly what the real apk database confirms.
          </li>
          <li>
            M2.5 (this build): search any Alpine package, ranked by name
            match, installable in one tap — verified by real exit codes only.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §9 — v0.5.0 additions)</h2>
        <ol className="steps">
          <li>Install {VERSION} APK over v0.4.4 (no uninstall).</li>
          <li>
            Open <b>Linux Shell</b> → <code>apk update</code> → finishes with{" "}
            <b>no “Permission denied”</b> and the full count (~28k packages).
          </li>
          <li>
            Then <code>apk add nodejs npm</code> → installs;{" "}
            <code>node --version</code> works.
          </li>
          <li>
            Explore → search <code>node</code> → <b>nodejs</b> at the top →
            tap <b>Install</b> → the row flips to “Installed · version — run
            ‘nodejs’ from the shell”.
          </li>
          <li>
            Known honest limitation: <code>ps</code>/<code>top</code> inside
            the guest report they cannot read <code>/proc</code> — that’s the
            SELinux tradeoff, not a bug.
          </li>
          <li>
            Regression: Home still lists Nano + Git; paste still works;
            Check package environment still shows fetch OK.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.5.0-m2.5-source.zip">
          source.zip (4.0 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.5.0-m2.5-source.tar.gz">
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
        (device-verified) · M2.4 package layer (<b>device gate PASSED</b>) ·
        M2.5 started: apk-capable shell + ranked/installable search (this
        build). Remaining M2.5 candidates: file manager, profiles, richer
        per-app Home cards. Thank you for the screenshots — they are the
        project’s real test suite.
      </footer>
    </main>
  );
}
