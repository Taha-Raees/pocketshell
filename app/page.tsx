const VERSION = "v0.10.0-m6.0.3";

// SHA pins — DETERMINISTIC: the APK is a clean-room gradle build, the payload
// cutter stages from the pinned release tip (git f98360f) with zeroed mtimes
// (two consecutive cuts byte-identical), the tests tarball is cut the same
// way, and the PDF has fixed metadata dates. Semantic pins: versionCode 43,
// versionName 0.10.0-m6.0.3, cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The layer is rev=2: the glibc
// files are BYTE-IDENTICAL to the proven rev=1 layer (proven single-member
// rebuild) — the only change is the fixed pocketshell-doctor v2, and the
// marker revision makes every device re-extract it on the next session.
const HASHES = {
  apk: "89704a14b16f477fa2d9b5e2a941f2c008a1f5a08fe0e101c970bdca0171d400",
  zip: "bc176239b40ba24287d7ac0b9c7710a595029ad9534bfb996d57219860e72334",
  tgz: "9b429c6ea2e726ffd4744bec7dfb51e6b7f30039033267e96752bde7835409f6",
  bundle: "d3e135a1f96e04b67c3625125b8d8b3f678cd6999c3a88fdca853186ae4ce529",
  tests: "beb3ad5e2ec4caf905a9d24644a8d4d20764cb7f804da05d61c396a8ce916506",
  glibc: "ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d",
  report: "9cb3ccb0fcb0f0d6736226148b2c33aa2664c8c571cedd304c4a5087a97e56a6",
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
          M6.0.3: the doctor correctness gate — the runtime is proven, now
          the diagnostic tool tells the truth{" "}
          <span className="badge">versionCode 43</span>
        </h2>
        <p>
          <b>
            The m6.0.2 runtime PROVED itself on real hardware: 24/24 suite
            rows, real Debian glibc 2.41, Cline 3.0.61 end-to-end. What failed
            was the diagnostic tool: pocketshell-doctor v1 verdicted
            UNSUPPORTED for every versioned glibc binary — the real Cline
            binary needs only GLIBC_2.17 and the layer provides 2.41, yet the
            doctor said UNSUPPORTED. v2 fixes the comparison properly (numeric,
            semantic, exit-code-authoritative), and the suite that let it slip
            now anchors its verdict greps and ships permanent doctor rows.
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
            <b>Diagnosable (v2 semantics):</b> <code>pocketshell-doctor</code>
            reports arch/class/interpreter/DT_NEEDED/max-GLIBC-version, gates
            versions NUMERICALLY (2.17 ≤ 2.41 ⇒ SUPPORTED; only required &gt;
            installed ⇒ UNSUPPORTED), asks the real loader to resolve every
            dependency with an exit-code-authoritative check, prints the full
            fact hierarchy before the verdict, and <code>--selftest</code> runs
            a permanent 15-case comparison matrix. <code>pocketshell-exec</code>
            routes any ELF to the right runtime.
          </li>
          <li>
            <b>The m6.0.3 fix (proven root causes):</b> doctor v1&apos;s version
            "comparison" concatenated the required and installed versions and
            string-compared the concatenation against the installed version
            alone — structurally always false, for every input, even exact
            matches; and its missing-library grep looked for "not found", which
            glibc never prints (it prints "error while loading shared
            libraries … cannot open shared object file", exit 127). The suite
            hid both: its doctor row grepped "SUPPORTED" unanchored —
            UNSUPPORTED CONTAINS SUPPORTED. All three defects have regression
            tests now (397 JVM tests green).
          </li>
          <li>
            <b>The layer is rev=2, not a new layer:</b> the glibc files are
            byte-identical to the proven m6.0.2 layer (proven at rebuild:
            exactly one tar member changed — the doctor script). The marker
            gains <code>rev=2</code> so every device re-extracts the fixed
            doctor on its next session prep — zero user action, musl untouched.
          </li>
          <li>
            <b>Provable installs:</b> every session prep stamps the app
            identity into <code>/etc/pocketshell/app-version</code> — the
            suite PREFLIGHT shows WHICH build owns your runtime; install
            outcomes stay mirrored to <code>/etc/pocketshell/glibc-runtime.status</code>
            and logcat. Suite v2.2 self-locates its binaries, prints its
            version, and its doctor rows anchor on
            <code> ^Compatibility: SUPPORTED</code> — a wrong verdict can never
            pass as a right one again.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.3-debug.apk">
          Download APK (debug, 29 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.10.0-m6.0.3)
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
          versionCode 43 installs <b>in place over v0.10.0-m6.0.2 (42),
          v0.10.0-m6.0.1 (41),
          v0.10.0-m6.0.0 (40),
          v0.9.1-m5.1.0 (39),
          v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
          v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
          v0.8.0-m4.1.0 (34, an intermediate that was never announced),
          v0.7.0-m4.0.9 (33) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Kilo/Hermes installation, the
          procfs contract, every Phase 3 behavior and all Companion data
          (logins included) are untouched. On the next session spawn the layer
          marker flips to rev=2 and the fixed doctor re-extracts — the glibc
          files themselves are byte-identical, nothing else changes.
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
            <b>v0.10.0-m6.0.1:</b> install observability + suite diagnosis —
            guest-visible install status, suite PREFLIGHT with honest SKIPs,
            in-guest repair hatch; validated on a gcompat-contaminated
            rootfs (the exact device-gate condition).
          </li>
          <li>
            <b>v0.10.0-m6.0.2:</b> the actual install-path fix —
            the packaged-asset pin, format-sniffing + sha-verified extraction,
            the built-APK regression pin, the app-version stamp, and suite
            v2.1 (self-locating, layout-agnostic). PROVEN on the device:
            24/24 ALL GREEN incl. Cline 3.0.61.
          </li>
          <li>
            <b>v0.10.0-m6.0.3 (this build):</b> the doctor correctness gate —
            pocketshell-doctor v2 (numeric semantic comparison, numeric max
            extraction, exit-code-authoritative loader check, fact hierarchy,
            --selftest), suite v2.2 (anchored verdict greps + three permanent
            doctor rows), layer rev=2 (byte-identical glibc files, marker-
            revision propagation). The runtime was right; now the doctor is.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §33 — the m6.0 gates)</h2>
        <ol className="steps">
          <li>
            <b>Automated suite:</b> one fresh session, then the
            runtime-tests command above → 27 rows PASS, ALL GREEN (24 prior
            rows + the three permanent doctor rows; a 28th real-Cline doctor
            row appears on Cline-equipped devices).
          </li>
          <li>
            <b>Doctor:</b> <code>pocketshell-doctor --selftest</code> → 15/15;
            <code> pocketshell-doctor /bin/sh</code> → musl SUPPORTED;
            <code> pocketshell-doctor</code> on the real Cline binary →
            Required GLIBC_2.17, Version gate PASS, SUPPORTED.
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
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.3-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.3-source.tar.gz">
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
        inside Alpine, musl untouched, Cline runs · m6.0.1 install
        observability + suite diagnosis ·{" "}
        <b>v0.10.0-m6.0.2 (this build): the actual install-path fix — the
        packaged-asset pin, verified extraction, and a suite that proves
        which build owns your runtime</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
