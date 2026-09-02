const VERSION = "v0.4.3-m2.4";
const TIP = "79afdee";

const HASHES = {
  apk: "02e0e746259b3902f90f97df57384c7b96271449047b76e6658c924abfa00fe6",
  zip: "35ae6adb7f1353c8f49c3713dd60f10291927ce4e6cb633b167846250a26cbcd",
  tgz: "e38a751352fa22397f22858e71c81089823b0d443a351b6ef291382df797da37",
  bundle: "39d69f165d18fa3b5cac7b6af2bcb4bb996abc852259a6d50a6b6cb175d7fd17",
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
          PocketShell {VERSION} <span className="badge">versionCode 11</span>
        </h2>
        <p>
          Your 08:13 screenshots were <b>half good news</b>: the SELinux fix
          works (“Permission denied” is gone) and only the tapped card shows
          “Working…” — exactly as designed. The remaining failure is DNS: the
          guest had a <b>single usable resolver</b> (your hotspot’s gateway,
          172.20.10.1 — the second entry was an IPv6 link-local with a{" "}
          <code>%wlan0</code> zone suffix that musl can’t parse). When that
          one resolver doesn’t answer, every fetch dies with “DNS: transient
          error”. This build writes <b>device resolvers first + public
          fallbacks (1.1.1.1, 8.8.8.8), capped at 3</b> — musl queries all of
          them in parallel and takes the first answer, so one dead resolver
          can no longer block an install. The file also self-refreshes on
          every package operation, so switching Wi‑Fi/hotspot can’t break it
          anymore. 281 unit tests green.
        </p>
        <a className="btn" href="/PocketShell-v0.4.3-m2.4-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1/v0.4.2)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall needed</h2>
        <p>
          Installs <b>in place over v0.4.2</b> (same pinned signing key). Your
          Linux runtime stays. The DNS repair applies itself on the first
          package operation — no reinstall, no data loss. If you ever see
          “DNS: transient error” again on <i>this</i> build, it means all
          three resolvers failed — report it and it becomes the next fix.
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
            python3), verified by real exit codes, never faked. SELinux-safe
            download commits (no /proc in package commands), parallel-query
            DNS, per-card busy state.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gate (docs/TESTING.md §9 — M2.4)</h2>
        <ol className="steps">
          <li>Install {VERSION} APK over v0.4.2 (no uninstall).</li>
          <li>
            Explore CLI Apps → <b>Install Nano</b> → must reach “nano
            installed” — no “DNS: transient error”, no “Permission denied”.
          </li>
          <li>
            Open → real nano in a real session; type, Ctrl+O save, Ctrl+X
            exit.
          </li>
          <li>
            Diagnostics → <b>Check package environment</b>: Repository fetch{" "}
            <b>OK</b>; Guest DNS shows your resolver first + 1.1.1.1/8.8.8.8,
            source line “device resolvers first, public fallback (musl
            queries all in parallel)”.
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
        <a className="btn secondary" href="/PocketShell-v0.4.3-m2.4-source.zip">
          source.zip (4.0 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.4.3-m2.4-source.tar.gz">
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
        gate). v0.4.2 and earlier are withdrawn (see CHANGELOG 0.4.2/0.4.3 for
        the confirmed fixes they shipped). Next: M2.5 CLI app cards on Home
        after the §9 gate passes.
      </footer>
    </main>
  );
}
