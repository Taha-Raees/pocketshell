const VERSION = "v0.6.0-m2.6";

const HASHES = {
  apk: "c350caca5728a971bd81305fcb0e6d8a94d5345877cdca75f1bad8e245001971",
  zip: "87550cc003c1e2f1c3efe8aba10c72bfafdb0db8c9bab3ddf22e803dbd5059fd",
  tgz: "1116d6339d16b7cfdd92f638e4b93a478ffaff6df0e0d25c0984b9d91344fbc3",
  bundle: "bd008b4e8699bdee19839926f90a9a258cea4e5dadbbede54241b3191ba73439",
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
          M2.6 — you were right: <b>never trade one Linux feature for another</b>{" "}
          <span className="badge">versionCode 14</span>
        </h2>
        <p>
          M2.5 made <code>apk</code> work inside the shell by removing the{" "}
          <code>/proc</code> bind from every guest session — which silently
          broke <code>ps</code>, <code>top</code> and <code>htop</code>. That
          was an architectural compromise, and this build reverses it properly.
          The full evidence chain is in <b>docs/M2.6-RESEARCH.md</b>.
        </p>
        <ul className="steps">
          <li>
            <b>What actually broke, source-verified:</b> apk-tools 3.0.x picks
            its download-commit strategy with one probe —{" "}
            <code>access("/proc/self/fd")</code>. With <code>/proc</code>{" "}
            visible it commits every download through{" "}
            <code>linkat("/proc/self/fd/N")</code>; Android’s security policy
            (“neverallow all_untrusted_apps file_type:file link”) makes that
            fail with <i>Permission denied</i>, and apk cancels the whole
            download with <b>no fallback</b>. Upstream (3.0.6 → master) has no
            fix to reuse.
          </li>
          <li>
            <b>The fix (surgical, honest):</b> a <b>one-byte, checksum-pinned
            patch</b> to Alpine’s own <code>libapk</code> (3.0.6-r0) inside
            the runtime turns that probe path into a permanently-missing file,
            so apk always uses its allowed named-tmpfile + <code>renameat</code>{" "}
            commit — the exact path already device-proven since v0.4.2. Same
            binary version, same real downloads, same output, same exit codes,
            same database. Reproducible: <code>scripts/patch_apk_fdlink.py</code>.
          </li>
          <li>
            <b>The architecture:</b> two explicit guest profiles on the same
            launcher — <b>INTERACTIVE_TERMINAL</b> (Linux Shell: full devices,
            PTY, shared apk cache <b>and a real <code>/proc</code></b> once the
            patched apk is verified) and <b>PACKAGE_OPERATION</b> (the app UI’s
            apk runs: minimal mounts, never <code>/proc</code>, refuse-guarded
            and test-pinned). One rootfs, one cache, one database.
          </li>
          <li>
            <b>What works now:</b> <code>ls /proc</code>,{" "}
            <code>cat /proc/version</code>, <code>ps</code>, <code>top</code>,{" "}
            <code>htop</code> — <b>and</b> <code>apk update / search / add /
            del</code> both in the shell and from the app UI, <b>and</b> Node
            end-to-end. Honest process semantics: <code>/proc</code> is the
            real host procfs filtered by Android’s own app isolation — you see
            the app’s real process tree, nothing faked, nothing hidden by us.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.6.0-m2.6-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.5.0)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          Installs <b>in place over v0.5.0</b> (same pinned signing key). Your
          runtime, installed packages and cache are untouched — the fd-link
          patch <b>installs itself on the first Linux Shell spawn</b> and is
          verified by checksum on every package operation. If your rootfs was
          ever modified by an in-guest <code>apk upgrade</code>, the app will
          tell you honestly (Diagnostics → “apk fd-link patch”) instead of
          overwriting anything.
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
            Linux Shell (M2.3 + M2.6): the Alpine guest via proot — with{" "}
            <code>/proc</code> back, and <code>apk</code> still working.
          </li>
          <li>
            Package management (M2.4, device-gate PASSED): curated cards
            (nano, htop, vim, git, python3) with Open/Uninstall; Home shows
            exactly what the real apk database confirms.
          </li>
          <li>
            M2.5 (device-requested): search any Alpine package, ranked by name
            match, installable in one tap; Node runs end-to-end.
          </li>
          <li>
            M2.6 (this build): real process tools restored without giving up
            the package manager — the foundation before more features.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §10 — Gates A–G)</h2>
        <ol className="steps">
          <li>Install {VERSION} over v0.5.0 (no uninstall) → open Linux Shell.</li>
          <li>
            Gate A: <code>ls /proc</code> → real entries;{" "}
            <code>cat /proc/version</code> and <code>cat /proc/meminfo</code>{" "}
            → real values.
          </li>
          <li>
            Gate B: <code>ps</code> → a real process list; <code>top</code> →
            opens, updates, <code>q</code> quits.
          </li>
          <li>
            Gate C: <code>apk update</code> → OK (~28k packages, no “Permission
            denied”); <code>apk add htop</code> → <code>htop</code> runs.
          </li>
          <li>
            Gate D: <code>apk add nodejs npm</code> →{" "}
            <code>node --version</code> → write{" "}
            <code>console.log("PocketShell Node works")</code> to{" "}
            <code>index.js</code> → <code>node index.js</code> prints it.
          </li>
          <li>
            Gates E–G: Explore install/uninstall still honest; <code>nano</code>{" "}
            unchanged; keep <code>top</code> running while the UI installs a
            package — neither breaks.
          </li>
          <li>
            Diagnostics → “Check package environment” now shows{" "}
            <b>apk fd-link patch: applied</b> and{" "}
            <b>Interactive /proc: bind</b>.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.6.0-m2.6-source.zip">
          source.zip (4.4 MB)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.6.0-m2.6-source.tar.gz">
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
        apk-capable shell + ranked/installable search · <b>M2.6 (this build):
        real /proc + real apk — the architecture fixed before features grow</b>.
        Next candidates: M2.7 session management + CLI app profiles, or a
        curated CLI app catalog. Thank you for pushing on the architecture —
        this fix is why your screenshots matter.
      </footer>
    </main>
  );
}
