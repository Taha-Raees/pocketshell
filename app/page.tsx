const VERSION = "v0.4.2-m2.4";
const TIP = "1f72be9";

const HASHES = {
  apk: "78306b16aa4ec43adeda7226ce125f741dff910dd42d6ef4885604401d7d680b",
  zip: "5662870ed3fcd6827d2133b5d54f0aa67deab904c7da2c8358061f2c56e90a02",
  tgz: "131c051249c9d537e22b48e2ce3aa9fa33922e8aaa14cd6078e97ca6b3cfebf4",
  bundle: "25db79c18a1bf5c86b09967324eca5cb1a792e25b7b43188603e41f788f05963",
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
          PocketShell {VERSION} <span className="badge">versionCode 10</span>
        </h2>
        <p>
          Fixes what your 2026-09-02 screenshots showed on v0.4.1: the
          repository fetch still dying with <i>“Permission denied”</i> even
          though DNS was working, and every catalog card flipping to
          “Working…” when you installed nano. Root cause of the fetch failure
          (verified in apk-tools 3.0.6 source + AOSP SELinux policy): apk
          commits each download with a hardlink (
          <code>linkat(/proc/self/fd/N)</code>), which Android&apos;s
          neverallow for untrusted apps denies — so apk cancelled the whole
          download. Package commands no longer bind <code>/proc</code> into
          the guest, so apk uses its plain create+rename commit path, which is
          allowed. End-to-end re-rehearsed with the same apk-tools: update →
          nano → run → del, all green. 277 unit tests green.
        </p>
        <a className="btn" href="/PocketShell-v0.4.2-m2.4-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall needed</h2>
        <p>
          Installs <b>in place over v0.4.1</b> (same pinned signing key,
          committed at <code>keystore/debug.keystore</code>). Your Linux
          runtime, its DNS setup and the package cache stay as they are — the
          fix is in how package commands launch, not in the data. If Android
          still refuses the update for any reason, uninstall → reinstall and
          tap “Install Linux environment” once in Diagnostics; nothing else
          is lost.
        </p>
      </div>

      <div className="card">
        <h2>What this build actually does (honest scope)</h2>
        <ul className="steps">
          <li>
            Real terminal (M1): Termux-emulator PTY sessions, full keyboard,
            themes, diagnostics.
          </li>
          <li>
            Linux runtime (M2.2): installs pinned Alpine 3.24.1 aarch64
            (SHA-256-verified) into app storage.
          </li>
          <li>
            Linux Shell (M2.3): real Alpine guest shell via proot on the same
            PTY — <code>uname; id; echo hello</code> verified on device.
          </li>
          <li>
            Package management (M2.4): Explore CLI Apps — real <code>apk</code>{" "}
            search / install / open / uninstall (nano, htop, vim, git,
            python3), verified by real exit codes, never faked. Only the card
            you tap shows “Working…”; the others keep their true labels.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gate (docs/TESTING.md §9 — M2.4)</h2>
        <ol className="steps">
          <li>Install {VERSION} APK over v0.4.1 (no uninstall).</li>
          <li>
            Explore CLI Apps → <b>Install Nano</b> → this time the update
            must pass: no “Permission denied” banner — the card reaches
            “nano installed” (Working… only on the nano card).
          </li>
          <li>
            Open → real nano in a real session; type, Ctrl+O save, Ctrl+X
            exit.
          </li>
          <li>
            Diagnostics → <b>Check package environment</b>: Repository fetch{" "}
            <b>OK</b>, Guest DNS = <i>device resolvers</i>.
          </li>
          <li>Reopen app → Nano still installed (read from the real apk db).</li>
          <li>Uninstall → “nano removed” → Open stays protected, no crash.</li>
          <li>Linux Shell → <code>uname; id; echo hello</code> still works.</li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source at git tip {TIP}. The zip intentionally
          contains no dotfiles; full history rides in the git bundle.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.4.2-m2.4-source.zip">
          source.zip (4.0 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.4.2-m2.4-source.tar.gz">
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
        (device-verified) · M2.4 package layer (this build — needs your device
        gate). v0.4.1 and earlier are withdrawn (apk fetch was SELinux-blocked;
        see CHANGELOG 0.4.2). Next: M2.5 CLI app cards on Home after the §9
        gate passes.
      </footer>
    </main>
  );
}
