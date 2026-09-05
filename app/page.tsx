const VERSION = "v0.10.0-m6.0.1";

const HASHES = {
  apk: "915677b6d60383324ed208d8e58b84a1794d16f2748256b35bd7cec79e768388",
  zip: "62eafc420d267d363f1c6f50079b76056a6a2de5e9817a30f2454e4c5c5efcc3",
  tgz: "6b58ab445ceaa0375398f40ec0c147687749a4ab4fb212378cb1202f14d0b2bf",
  bundle: "3a5992644e899f22fa7791dc198af410685a3051c34a4a582870dd62f607ae94",
  tests: "b203fa58488411a6a32cc2dfce18f1a657209f81b330258c30a4dcd74c5e6b21",
  glibc: "2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11",
  report: "0e0a2bf8363647aece215f4f0a00b658debb3d42a443b480d564b01cd7d17c0d",
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
          M6.0.1: Universal Runtime Compatibility — now with install
          observability{" "}
          <span className="badge">versionCode 41</span>
        </h2>
        <p>
          <b>
            ONE Alpine distribution — now running musl AND glibc AND static
            ARM64 software side by side. The glibc blocker the two forensic
            reports identified is closed as architecture, not as a per-tool
            hack.
          </b>
        </p>
        <ul className="steps">
          <li>
            <b>Real glibc inside the Alpine guest:</b> Debian 13&apos;s glibc
            2.41 runtime (loader, libc, libm, pthread/dl/rt, NSS, libstdc++,
            libgcc, zlib + a common library set) installed at the canonical
            multiarch paths. musl paths are disjoint by construction and never
            touched — <code>apk</code>, node, git, bash behave exactly as
            before.
          </li>
          <li>
            <b>Transparent by construction:</b> a glibc binary — and every
            child process it spawns — execs through the real loader with zero
            env vars, zero proot changes, zero per-binary wrappers. No user
            ever needs to know which libc a tool uses.
          </li>
          <li>
            <b>Cline 3.0.61 runs:</b> the real 151 MB glibc-native binary
            (the exact artifact that failed on device at the loader stage)
            passes version/help/node-spawn/relaunch ×3 under the layer in the
            validated rig — 24/24 suite rows green, musl regression included.
          </li>
          <li>
            <b>Pinned &amp; self-healing delivery:</b> the layer ships inside
            the APK (offline, atomic, SHA-256-pinned) and installs itself on
            the next session spawn over any existing runtime — no reinstall,
            no user steps, musl sessions never depend on it.
          </li>
          <li>
            <b>Diagnosable:</b> <code>pocketshell-doctor</code> reports
            arch/class/interpreter/DT_NEEDED/max-GLIBC-version and asks the
            real loader to resolve every dependency — SUPPORTED or UNSUPPORTED
            with the exact reason. <code>pocketshell-exec</code> routes any
            ELF to the right runtime.
          </li>
          <li>
            <b>New in m6.0.1 — the device-gate lesson:</b> the layer&apos;s
            best-effort install is now OBSERVABLE. Every install outcome is
            mirrored to the guest (<code>/etc/pocketshell/glibc-runtime.status</code>),
            the suite prints a PREFLIGHT diagnosis and treats a missing layer
            as SKIP-with-fix-path (not FAIL noise), and
            <code> POCKETSHELL_INSTALL_LAYER=1</code> repairs the layer
            in-guest without waiting for the app. Validated in 4 device
            states under emulation, including repair of a gcompat-
            contaminated rootfs — the exact state found at the gate.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.1-debug.apk">
          Download APK (debug, 29 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.10.0-m6.0.1)
        </p>
      </div>

      <div className="card">
        <h2>
          Platform / Runtime Forensic Audit <span className="badge">PDF · 34 pages</span>
        </h2>
        <p>
          The complete read-only engineering audit of the real architecture:
          repository provenance, the full Linux launch chain, Termux + proot
          deep dives, the targetSdk-28 exec model, /proc attribution, the
          ELF/libc strategy this build implements, the tool compatibility
          matrix, answers to all 30 audit questions, and the Runtime 2.0
          proposal.
        </p>
        <a className="btn secondary" href="/PocketShell-Runtime-Forensic-Audit.pdf">
          Download audit report (PDF, 300 KB)
        </a>
        <Sha text={HASHES.report} />
      </div>

      <div className="card">
        <h2>Executable compatibility suite <span className="badge">runtime-tests</span></h2>
        <p>
          The permanent ARM64 compatibility suite (musl / static / glibc matrix
          + <code>pocketshell-doctor</code> + the real Cline test). Run it
          inside the PocketShell terminal:
        </p>
        <p className="mono">
          mkdir -p /tmp/pocketshell-tests &amp;&amp; curl -fsSL
          &lt;this-site&gt;/pocketshell-runtime-tests-aarch64.tar.gz | tar -xz -C
          /tmp/pocketshell-tests &amp;&amp; sh
          /tmp/pocketshell-tests/run_on_device.sh
        </p>
        <a className="btn secondary" href="/pocketshell-runtime-tests-aarch64.tar.gz">
          runtime-tests (700 KB)
        </a>
        <Sha text={HASHES.tests} />
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 41 installs <b>in place over v0.10.0-m6.0.0 (40),
          v0.9.1-m5.1.0 (39),
          v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
          v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
          v0.8.0-m4.1.0 (34, an intermediate that was never announced),
          v0.7.0-m4.0.9 (33) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Kilo/Hermes installation, the
          procfs contract, every Phase 3 behavior and all Companion data
          (logins included) are untouched. The glibc layer installs into your
          EXISTING runtime on the next session spawn — nothing is reinstalled,
          nothing is wiped.
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
            Linux Shell (M2.3 + M2.6 + 3.6): the Alpine guest via proot —
            guaranteed real <code>/proc</code>, apk that survives guest-side
            upgrades.
          </li>
          <li>
            Package management (M2.4/M2.5): the Packages screen — search,
            install, uninstall, open.
          </li>
          <li>
            Phase 3.1–3.5: Midnight Sapphire terminal + Home workspace, the
            nine-CLI probe-gated registry, tap-to-launch, Midnight system
            pages.
          </li>
          <li>
            Phase 4 (m4.0) + m4.0.1–m4.0.12 + m5.0.x + m5.1.0: the Companion
            workspace and every evidence-driven fix along the way — startup
            hotfix, honest failure cards, one keyboard, the rendering reset,
            the native re-host, the frozen winner, compact chrome, the Light
            Theme, the silent minimized Companion and the audit-first
            performance pass.
          </li>
          <li>
            <b>v0.10.0-m6.0.0:</b> Universal Runtime Compatibility — real
            glibc 2.41 at canonical multiarch paths inside the Alpine guest;
            musl + glibc + static coexist; glibc children spawn correctly;
            NSS/DNS work; the layer is pinned, self-healing and rides the
            APK; pocketshell-doctor/exec ship inside the guest; the
            Cline-class loader failure is closed.
          </li>
          <li>
            <b>v0.10.0-m6.0.1 (this build):</b> install observability + suite
            diagnosis — guest-visible install status, suite PREFLIGHT with
            honest SKIPs, in-guest repair hatch; validated on a
            gcompat-contaminated rootfs (the exact device-gate condition).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §33 — the m6.0 gates)</h2>
        <ol className="steps">
          <li>
            <b>Automated suite:</b> one fresh session, then the
            runtime-tests command above → 24 rows PASS, ALL GREEN.
          </li>
          <li>
            <b>Doctor:</b> <code>pocketshell-doctor /bin/sh</code> → musl
            SUPPORTED; <code>pocketshell-doctor</code> on a glibc binary →
            full DT_NEEDED + loader resolution report.
          </li>
          <li>
            <b>Cline:</b> version/help/node-spawn (deep test with
            CLINE_DEEP_TEST=1 when credentials exist).
          </li>
          <li>
            <b>musl regression:</b> apk update/search/install, node, npm,
            git, curl, Kilo — all unchanged.
          </li>
          <li>
            <b>Nothing broke:</b> keyboard, themes, Companion, sessions —
            all unchanged (the layer is additive).
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle — includes the
          complete milestone history, all design contracts, the procfs
          contract, the rendering-reset report, and the new runtime
          documentation (docs/runtime/ + runtime-tests/ + scripts/runtime/).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.1-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.1-source.tar.gz">
          source.tar.gz
        </a>
        <a className="btn secondary" href="/pocketshell-m2.gitbundle">
          git bundle (full history)
        </a>
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m2.gitbundle pocketshell</code>.
          Includes <code>keystore/debug.keystore</code> — clones build APKs
          with the same signing identity.
        </p>
      </div>

      <footer>
        Milestones: M0–M1.3 terminal · M2.2 runtime install · M2.3 Linux shell
        (device-verified) · M2.4 package layer (device gate PASSED) · M2.5
        search-install · M2.6 real /proc + real apk · v0.6.2 sysdata repairs ·
        v0.7.0-m3.1 terminal redesign · m3.2 OS launcher · m3.3 flat
        workspace · m3.4 registry expansion · m3.5 command launch fix · m3.6
        procfs contract · m4.0 Phase 4 Companion · m4.0.1 startup hotfix ·
        m4.0.2 honest failure surfaces · m4.0.3 one keyboard for everything ·
        m4.0.4 pixels over promises · m4.0.5 the black page attacked at the
        root · m4.0.6 the page tells us everything · m4.0.7 the host was the
        bug · m4.0.8 the painted-but-black decode · m4.0.9 the rendering reset
        · m4.1.0 the native rebuild · m4.0.11 replace renderer only — the
        winner frozen · m4.0.12 companion finalization · m5.0.0 UI &amp;
        interaction polish · m5.0.1 the workspace bar · m5.1.0 audit-first
        performance · m6.0.0 universal runtime compatibility — real glibc
        inside Alpine, musl untouched, Cline runs ·{" "}
        <b>v0.10.0-m6.0.1 (this build): install observability + suite
        diagnosis — the device-gate lesson turned into instrumentation</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
