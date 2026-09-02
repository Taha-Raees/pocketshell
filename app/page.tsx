const VERSION = "v0.4.4-m2.4";
const TIP = "37d4f82";

const HASHES = {
  apk: "3161f40fb9e12f8209213205c0609cf0a0dcebdab4f6d584eee4047eefe5baa7",
  zip: "7bae560dc3a9aea69cca16eac4ef2e41bc196bd4292521f7b6f4a5a0b96363ca",
  tgz: "10906ec2a6ce94cf587bb81a18f70543fc18cbb39aa5feac937ad0b2855e1b62",
  bundle: "e9203d0d08b7c3baed13fde16c1c2fe0a9256d44f144c6d3c7adf44ec51761f2",
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
          M2.4 <b>PASSED on your device</b> — and this build fixes the three
          bugs your screenshots caught <span className="badge">versionCode 12</span>
        </h2>
        <p>
          Your 09:09–09:10 screenshots are the milestone evidence we were
          waiting for: <b>GNU nano 9.2 running inside the Alpine guest</b>,
          apk-tools 3.0.6, combined DNS, and <b>“Repository fetch: OK —
          28546 distinct packages available”</b>. The SELinux and DNS chains
          are closed. But the same screenshots caught the app lying to you in
          three places — all fixed here:
        </p>
        <ul className="steps">
          <li>
            <b>Nano showed “Not installed” in Explore:</b> the installed-state
            probe ran one shell loop over the five catalog packages and let
            the loop’s <i>exit status</i> stand for the whole probe. The last
            package checked (<code>python3</code>) isn’t installed, so the
            loop exited 1 — and the app <i>threw away the perfectly good
            answer</i> that contained <code>nano nano-9.2-r0</code>. Now the
            probe calls the absolute <code>/sbin/apk</code> and always exits 0
            when it completes: a mixed answer is a success, and versions come
            from the same strict parser as everything else.
          </li>
          <li>
            <b>Home said “No apps installed yet”:</b> that list read an
            old M1-era registry that nothing ever wrote (M2.4 installs go
            through apk, not that registry). Home now shows exactly what the
            real apk database confirms — fresh probe on every visit and after
            every install/uninstall — with the real version. The dead registry
            is deleted, not patched.
          </li>
          <li>
            <b>Paste did nothing:</b> the terminal’s Paste action ends in a
            client callback that was <i>empty</i> (its comment claimed
            upstream does the paste — it doesn’t, nothing did). It now reads
            the real clipboard and pastes with upstream semantics —
            bracketed-paste aware, so it behaves inside nano too. Copy
            anywhere on your phone → long-press → Paste → it lands.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.4.4-m2.4-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.4.3)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall needed</h2>
        <p>
          Installs <b>in place over v0.4.3</b> (same pinned signing key). Your
          Linux runtime, every installed package and the network-managed DNS
          file are untouched — all three fixes live in the Android layer. The
          first time you open Home or Explore, the app re-probes the real apk
          database and your installed nano appears in both places.
        </p>
      </div>

      <div className="card">
        <h2>What this build actually does (honest scope)</h2>
        <ul className="steps">
          <li>
            Real terminal (M1): Termux-emulator PTY sessions, full keyboard,
            themes, diagnostics — plus working clipboard paste (v0.4.4).
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
            Package management (M2.4, <b>device-gate passed</b>): real{" "}
            <code>apk</code> search / install / open / uninstall; SELinux-safe
            download commits; parallel-query DNS; and — since this build — UI
            state that can only come from real apk answers (“Not installed”
            is never guessed; a failed probe says “state unavailable”).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §9 — the v0.4.4 additions)</h2>
        <ol className="steps">
          <li>Install {VERSION} APK over v0.4.3 (no uninstall).</li>
          <li>
            Home → <b>Installed CLI Apps</b> now lists <b>Nano</b> with its
            real version; tapping it opens a real nano session.
          </li>
          <li>
            Explore CLI Apps → Nano card shows <b>“Installed · 9.2-r0”</b>{" "}
            with Open/Uninstall — not “Install”/“Not installed”.
          </li>
          <li>
            Paste test: copy text anywhere on the phone → terminal →
            long-press → select → <b>Paste</b> → the text lands on the command
            line (and inside nano).
          </li>
          <li>
            Install HTop from Explore → Home’s list updates to show both apps
            without leaving the app.
          </li>
          <li>
            Regression: Check package environment still shows fetch OK;
            Linux Shell still works.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source at git tip {TIP}. The zip intentionally
          contains no dotfiles; full history rides in the git bundle.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.4.4-m2.4-source.zip">
          source.zip (4.0 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.4.4-m2.4-source.tar.gz">
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
        (device-verified) · M2.4 package layer (<b>device gate PASSED</b>,
        v0.4.3; state-sync + paste polish in this build). v0.4.3 and earlier
        are withdrawn (see CHANGELOG 0.4.2/0.4.3/0.4.4 for the confirmed fixes
        each shipped). Next: M2.5 — richer CLI-app surface on Home beyond the
        curated five.
      </footer>
    </main>
  );
}
