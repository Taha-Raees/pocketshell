const VERSION = "v0.10.0-m6.0.4";

// SHA pins — DETERMINISTIC: the APK is a clean-room gradle build, the payload
// cutter stages from the pinned release tip (git a7441ff) with zeroed mtimes,
// the tests tarball is cut the same way (now including the Phase-C drill
// script), and the PDF has fixed metadata dates. Semantic pins: versionCode
// 44, versionName 0.10.0-m6.0.4, cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…): m6.0.4 fixes are APP-side only.
const HASHES = {
  apk: "e633ca3cef54434474c58648a489329c875ba1ab1ffcf6d15b77a4e1c2529750",
  m7p7: "a3ce9d3a03e8307ec3eca6893ee7b8fe96e6d0b2f4723a22e8a00b6347192cd8",
  m7p7zip: "9383ab00c82108913f02fd03471f0b3c5ca30e8deca2af45117499dee4f3184a",
  m7p7tgz: "8a0d48605bd4cbe5b8eea905b82116fb6cc536e1bcfcc1922ffef120ed3b0132",
  m7p7bundle: "3b67d58d2965de1884ba09c7175af254da59d26fee3d45c33fedd957d85ea6a2",
  m7p6: "35ae7a488af89a3403823f78ecadeaacb5c6b2806f5a240d73320128e94fff37",
  zip: "b0985c77d8c9e8100072b4f54d961c08aba0d79a02a75918cc5d5e8be753da12",
  tgz: "c46e585dc90663f5349f654940bbde1a6437acd4790f647d6405e1d3b7884595",
  bundle: "9982485d3c2e93213f9ec815f53228d74efa091773fc3ba9a9dc00a1f62f3938",
  tests: "90009339d4baeae001f0417c4c926fb18256a8f4b96e62e3dbb074b55fe87ac5",
  glibc: "ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d",
  report: "92015b7547b2e9b7dd6bd8888f5d641c8804a3e33cafaf8c069e811934af10ae",
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
          M7 Phase 7 build: Open Terminal Here{" "}
          <span className="badge">M7 tip d1fe8b6 · vc44</span>
        </h2>
        <p>
          <b>
            LATEST BUILD — includes M7 Phases 1–7 on top of M6.0.4: storage
            abstraction, file explorer, file operations, Android storage
            bridge (SAF folders), the quick text editor, and now
            &quot;Open Terminal Here&quot;. Linux directories in the Files
            explorer gain a REAL launch action: a normal Alpine session
            (titled &quot;Alpine Linux&quot;) whose guest working directory is
            the exact browsed directory — <code>pwd</code> prints it
            literally. Created through the UNCHANGED canonical session path:
            navigation happens only after the session is actually ready,
            never navigate-first. The directory travels as PTY argv through
            the new injection-proof chain{" "}
            {"cd -- '<dir>' && exec /bin/sh -l"} — POSIX single-quote
            wrapping, the -- option terminator, and && (never ;) so the
            interactive login shell follows only a SUCCESSFUL cd; proven by
            execution through real /bin/sh, including a genuine injection
            attempt whose <code>touch</code> never runs. Android areas
            (PocketShell Downloads, SAF folders) get NO fake button — the
            honest boundary note instead: copy or move files into PocketShell
            Linux to work with them in Terminal. Existing sessions untouched.
            JVM suite 600/600 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.4-m7p7-debug.apk">
          Download M7P7 APK (debug, 30.3 MB)
        </a>
        <Sha text={HASHES.m7p7} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          installs in place over M6.0.4 / M7P6 (same versionCode 44, same cert) — glibc layer ed82daa8… unchanged
        </p>
      </div>

      <div className="card primary">
        <h2>
          M7 Phase 6 build: quick text editor{" "}
          <span className="badge">M7 tip 25f421f · vc44</span>
        </h2>
        <p>
          <b>
            Includes M7 Phases 1–6 on top of M6.0.4: storage
            abstraction, file explorer, file operations, Android storage bridge
            (SAF folders), and now the quick text editor. &quot;Open&quot; on
            regular files across all three storage domains — PocketShell Linux,
            the Downloads shelf, user-granted SAF folders. Byte-honest (strict
            UTF-8 or refusal — no silent corruption; BOM/CRLF round-trip
            byte-exact; NUL binary sniff; 1 MiB cap with the real size),
            concurrency-honest ((size, mtime) save gate + explicit overwrite
            confirmations), input-honest (the ONE keyboard deck, no IME).
            Dirty back guard. NOT a mini IDE by design. JVM suite 586/586
            green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.4-m7p6-debug.apk">
          Download M7P6 APK (debug, 30.3 MB)
        </a>
        <Sha text={HASHES.m7p6} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          installs in place over M6.0.4 (same versionCode 44, same cert) — glibc layer ed82daa8… unchanged
        </p>
      </div>

      <div className="card primary">
        <h2>
          M6.0.4: the adversarial closure audit — the runtime survives its own
          failure modes{" "}
          <span className="badge">versionCode 44</span>
        </h2>
        <p>
          <b>
            The M6.0.3 device gate scored 27/27 ALL GREEN. Before freezing the
            runtime architecture, the phase was audited adversarially:
            corruption drills, concurrency, loader ownership vs the gcompat
            package, extractor security, environment contamination, doctor
            prediction accuracy, and lifecycle timing. Three real engineering
            issues were found — all fixed, all regression-pinned, none touched
            the proven runtime architecture. The glibc layer itself is
            byte-identical to the one that scored 27/27.
          </b>
        </p>
        <ul className="steps">
          <li>
            <b>Integrity beyond the marker (C2/C4):</b> Alpine&apos;s{" "}
            <code>gcompat</code> package provably owns
            <code> /lib/ld-linux-aarch64.so.1</code> and ships a real ELF shim
            there (verified from the actual package bytes) —{" "}
            <code>apk fix/reinstall gcompat</code> could reclaim the loader
            behind a perfectly valid marker. The layer fast path now runs a
            structural integrity probe (the loader symlink must resolve to the
            canonical Debian loader; load-bearing files must exist) and
            self-heals by re-extraction on the next session. Deleted libraries
            or tools are detected the same way.
          </li>
          <li>
            <b>Symlink-safe re-extraction (C3/C12):</b> the layer legitimately
            ships <code>lib/aarch64-linux-gnu → ../usr/lib/aarch64-linux-gnu</code>
            {" "}BEFORE the files it points to — and the old symlink replacement
            followed directory symlinks, so every in-place re-extraction first
            wiped the entire multiarch directory, then rewrote it. Fixed:
            symlink nodes are replaced as nodes, every recursive delete is
            NOFOLLOW, and archive entries routed through earlier symlink
            entries are refused fail-closed.
          </li>
          <li>
            <b>Session prep off the UI thread (C1.1/C3):</b> the heavy guest
            preparation (18 MB asset read + sha-256 + re-extraction path) used
            to run synchronously in the click handler. Session creation is now
            two-phase — prep on Dispatchers.IO, PTY spawn on the main thread —
            and the layer ensure is single-flight, so concurrent sessions
            serialize instead of racing an extraction.
          </li>
          <li>
            <b>Permanent drill suite:</b>{" "}
            <code>adversarial_closure_audit.sh</code> rides the tests tarball —
            <code> probe</code> (doctor prediction accuracy, environment
            contamination, filesystem ownership map, fast-path timing),{" "}
            <code>drill-c2</code> (corruption), <code>drill-c4</code> (gcompat
            loader reclaim), <code>drill-c5</code> (apk update/upgrade/add/del
            survival), and <code>heal</code> (post-session self-heal
            verification).
          </li>
          <li>
            <b>Doctor accuracy is measured, not assumed (C7):</b> the audit
            arms the doctor against real binaries and compares its verdict
            with what actually executes — true positives, true negatives, and
            an explicit FAIL on any false SUPPORTED/UNSUPPORTED. The sandbox
            evidence battery (decision tree + malformed-input matrix) scores
            20/20.
          </li>
          <li>
            <b>Nothing else changed:</b> the glibc layer artifact sha is
            byte-identical to the 27/27 gate (ed82daa8…), musl is untouched by
            construction, no loader routing was modified, no wrappers added.
            JVM suite 397 → 406 leaf cases, 0 failures, including the
            built-APK asset pin against this exact APK.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.4-debug.apk">
          Download APK (debug, 29 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.10.0-m6.0.4)
        </p>
      </div>

      <div className="card">
        <h2>
          Platform / Runtime Forensic Audit <span className="badge">PDF · 33 pages</span>
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
          Download audit report (PDF, 200 KB)
        </a>
        <Sha text={HASHES.report} />
      </div>

      <div className="card">
        <h2>Executable compatibility suite <span className="badge">runtime-tests</span></h2>
        <p>
          The permanent ARM64 compatibility suite (musl / static / glibc matrix
          + <code>pocketshell-doctor</code> + the real Cline test) and the
          Phase-C adversarial drill script. Run inside the PocketShell
          terminal:
        </p>
        <p className="mono">
          mkdir -p /tmp/pocketshell-tests &amp;&amp; curl -fsSL
          &lt;this-site&gt;/pocketshell-runtime-tests-aarch64.tar.gz | tar -xz -C
          /tmp/pocketshell-tests &amp;&amp; sh
          /tmp/pocketshell-tests/run_on_device.sh
        </p>
        <p>
          Then the closure drills (each prints its own verdict):
          <span className="mono">
            {" "}
            sh /tmp/pocketshell-tests/adversarial_closure_audit.sh probe
            &nbsp;·&nbsp; drill-c2 &nbsp;·&nbsp; drill-c4 &nbsp;·&nbsp;
            drill-c5
          </span>{" "}
          — after a drill, open ONE new session and run{" "}
          <span className="mono">adversarial_closure_audit.sh heal</span> to
          prove the app self-heals the layer. The guided closure runner is{" "}
          <span className="mono">device_gate.sh</span> (gate → resume-c2 →
          resume-c4 → resume-c5; each destructive stage gated by baseline).
        </p>
        <a className="btn secondary" href="/pocketshell-runtime-tests-aarch64.tar.gz">
          runtime-tests (718 KB)
        </a>
        <Sha text={HASHES.tests} />
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 44 installs <b>in place over v0.10.0-m6.0.3 (43),
          v0.10.0-m6.0.2 (42),
          v0.10.0-m6.0.1 (41),
          v0.10.0-m6.0.0 (40),
          v0.9.1-m5.1.0 (39),
          v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
          v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
          v0.8.0-m4.1.0 (34, an intermediate that was never announced),
          v0.7.0-m4.0.9 (33) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Cline installation, the procfs
          contract, every Phase 3 behavior and all Companion data (logins
          included) are untouched. This update is APP-side only: the layer
          marker and the glibc files stay byte-identical — the new integrity
          probe simply starts guarding them on the next session prep.
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
            Phase 3.1–3.5 + m4.x + m5.x: Midnight Sapphire terminal + Home
            workspace, the CLI registry, and the Companion workspace with every
            evidence-driven fix along the way.
          </li>
          <li>
            <b>v0.10.0-m6.0.0–m6.0.3:</b> Universal Runtime Compatibility —
            real glibc 2.41 at canonical multiarch paths inside the Alpine
            guest, self-healing pinned delivery, install observability, the
            doctor correctness gate (numeric semantic comparison), and the
            27/27 device gate.
          </li>
          <li>
            <b>v0.10.0-m6.0.4 (this build):</b> the adversarial closure audit —
            structural integrity probe behind the marker (gcompat loader
            reclaim detection + self-heal), symlink-safe NOFOLLOW
            re-extraction, session prep off the UI thread with single-flight
            layer install, permanent drill suite, doctor prediction-accuracy
            measurement, and the documented engineering boundary of the
            compatibility claim (DUAL_LIBC.md §8).
          </li>
          <li>
            <b>M7.0.0 phases 1–7 (this build):</b> one storage abstraction
            behind the Files explorer (PocketShell Linux, the app Downloads
            shelf, user-granted SAF folders), real file operations with
            collision semantics, the byte-honest quick text editor, and
            &quot;Open Terminal Here&quot; on Linux directories — a normal
            Alpine session at the exact browsed directory through the
            canonical launch path, with the honest boundary note instead of a
            fake button on Android areas.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §33 — the m6.0 gates)</h2>
        <ol className="steps">
          <li>
            <b>Automated suite:</b> one fresh session, then the
            runtime-tests command above → 27 rows PASS, ALL GREEN.
          </li>
          <li>
            <b>Doctor:</b> <code>pocketshell-doctor --selftest</code> → 15/15;
            <code> pocketshell-doctor /bin/sh</code> → musl SUPPORTED;
            <code> pocketshell-doctor</code> on the real Cline binary →
            Required GLIBC_2.17, Version gate PASS, SUPPORTED.
          </li>
          <li>
            <b>Closure drills:</b> probe → all green; drill-c2 + one new
            session + heal → every corruption state detected and fully
            repaired; drill-c4 → the loader-ownership verdict with evidence;
            drill-c5 → apk operations leave the layer intact.
          </li>
          <li>
            <b>Cline:</b> version/help/node-spawn (deep test with
            CLINE_DEEP_TEST=1 when credentials exist).
          </li>
          <li>
            <b>musl regression:</b> apk update/search/install, node, npm,
            git, curl, Kilo — all unchanged.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7 tip d1fe8b6 (M7 phases 1–7 included, incl. Open
          Terminal Here). The zip intentionally contains no dotfiles; full
          history rides in the git bundle — the complete milestone history
          (M0 → M7P7), all design contracts, the procfs contract, and the
          runtime documentation (docs/runtime/ + runtime-tests/ +
          scripts/runtime/). Bundle main tip 0c979f1 = the P7 app tip
          d1fe8b6 plus the delivery-page updates and server log; the
          archived tree is cut at d1fe8b6.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-m7p7-source.zip">
          source.zip (M7 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-m7p7-source.tar.gz">
          source.tar.gz (M7 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7p7.gitbundle">
          git bundle (full history, M7 tip)
        </a>
        <Sha text={HASHES.m7p7zip} />
        <Sha text={HASHES.m7p7tgz} />
        <Sha text={HASHES.m7p7bundle} />
        <p>
          Previous release cut — v0.10.0-m6.0.4 (M6.0.4 release tip ff4afa9;
          M7 not included):
        </p>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-source.zip">
          source.zip (m6.0.4)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-source.tar.gz">
          source.tar.gz (m6.0.4)
        </a>
        <a className="btn secondary" href="/pocketshell-m2.gitbundle">
          git bundle (m6.0.4)
        </a>
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7p7.gitbundle pocketshell</code>.
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
        observability · m6.0.2 the actual install-path fix · m6.0.3 the doctor
        correctness gate · v0.10.0-m6.0.4: the adversarial closure audit — the
        runtime survives corruption, reclaims, and concurrency, and says so
        honestly ·{" "}
        <b>m7.0.0 phases 1–7 (this build): storage abstraction, file explorer,
        file operations, Android storage bridge, quick text editor, Open
        Terminal Here</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
