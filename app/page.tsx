const VERSION = "v0.4.1-m2.4";
const TIP = "ba15e63";

const HASHES = {
  apk: "2bbb3155bc220623eed878fed6a85259c89f5e84aa6181b185cb04054d26855f",
  zip: "3a1b81b73be55f1449af55df56111d1b531ad43884445c190db51cf8c12141ac",
  tgz: "8a000bedd7e60145539c285ea46752219fb23a73fe4d29ee71e72b6a0aeea139",
  bundle: "064c2ca89f4112b8bdc9a9bb19d7ab74fe4783b463e5675f451cb31783128bd7",
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

      <div className="card warn">
        <h2>⚠ Read first — one-time uninstall required</h2>
        <p>
          A build-machine reset destroyed the original (debug) signing key.
          Android treats a debug APK&apos;s signature as its update identity, so{" "}
          <b>{VERSION} cannot install over v0.4.0</b>. Uninstall the old app
          first, then install this one, then reinstall the Linux runtime from
          Diagnostics (one tap, ~9.3 MB). No installed packages are lost —
          none could be installed on v0.4.0 (that&apos;s what this release
          fixes).
        </p>
        <p>
          <b>This is the last time:</b> the debug keystore is now committed in
          the repo (<code>keystore/debug.keystore</code>), so every future
          build installs as a normal in-place update again.
        </p>
      </div>

      <div className="card primary">
        <h2>
          PocketShell {VERSION} <span className="badge">versionCode 9</span>
        </h2>
        <p>
          The M2.4 device-recording hotfix: guest DNS now uses the device&apos;s
          own resolvers (the v0.4.0 hardcoded public ones were unreachable on
          your network — that&apos;s the &quot;Permission denied&quot; /
          &quot;DNS: transient error&quot; in your recording), and the apk
          cache moved outside the rootfs via proot binds. Failed updates show
          apk&apos;s real stderr + a Retry button. 271 unit tests green;
          end-to-end apk flow re-rehearsed on x86_64 (update → nano → del).
        </p>
        <a className="btn" href="/PocketShell-v0.4.1-m2.4-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (NEW — see warning above)
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
            python3), verified by real exit codes, never faked.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gate (docs/TESTING.md §9 — M2.4)</h2>
        <ol className="steps">
          <li>Uninstall old app → install {VERSION} APK.</li>
          <li>Diagnostics → Install Linux environment → READY.</li>
          <li>
            Diagnostics → <b>Check package environment</b>: apk version, both
            dl-cdn repos, Guest DNS = <i>device resolvers</i>, Repository
            fetch: <b>OK</b>.
          </li>
          <li>Explore CLI Apps → Install Nano → stages → “nano installed”.</li>
          <li>Open → real nano in a real session; Ctrl+O / Ctrl+X work.</li>
          <li>Reopen app → Nano still installed (read from real apk db).</li>
          <li>Uninstall → “nano removed” → Open protection, no crash.</li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source at git tip {TIP}. The zip intentionally
          contains no dotfiles; full history rides in the git bundle.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.4.1-m2.4-source.zip">
          source.zip (4.7 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.4.1-m2.4-source.tar.gz">
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
        gate). Previous builds (v0.4.0 and earlier) are withdrawn — their
        signing key is gone. Next: M2.5 CLI app cards on Home after the §9
        gate passes.
      </footer>
    </main>
  );
}
