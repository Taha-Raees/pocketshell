const VERSION = "v0.10.0-m6.0.4-m7p8.1";

// SHA pins — the m7p8.1 delivery. HONESTY NOTE: the ORIGINAL m7p8 build and
// the older milestone artifacts were lost to a sandbox reset before they
// could be delivered; this page serves the REBUILT, re-gated m7p8.1 set cut
// from the restored history (bundle tip e7f2630). The payload cutter stages
// from the pinned tip with zeroed mtimes; the embedded git bundle's pack
// bytes are not re-cut-stable, so these pins refer to the ONE delivered cut.
// Semantic pins: versionCode 44, versionName 0.10.0-m6.0.4, cert
// d96a6f66…8bf659, embedded layer asset 898131ff… /17,920,000 B ==
// GlibcRuntimePin. The glibc layer is UNCHANGED rev=2 (byte-identical
// artifact ed82daa8…).
const HASHES = {
  apk: "f316ec67ffa5a5ce6983caaea8644a7a5b4cefba9805aa78a225d693ae9bfaf2",
  zip: "5f1f3eb2800f145a539019baf83c53192831a7f60759240e07eac07bfbc98401",
  tgz: "788970c0e0f176b2e3f25fe51fa53e12194bac930656b94002b3aca6ec202e7c",
  bundle: "1068ed17d37da2095abed2ba050941995bc4e2c33fda6ef96d283760fa1a9f96",
  glibc: "ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d",
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
          M7 Phase 8.1 build: file search + multi-select{" "}
          <span className="badge">M7 tip e7f2630 · vc44</span>
        </h2>
        <p>
          <b>
            LATEST BUILD — includes M7 Phases 1–8.1 on top of M6.0.4 (one
            build carries both new features). <b>Search:</b> a magnifier in
            the Files header opens a focused search field — a literal,
            case-insensitive NAME search of the CURRENTLY SELECTED storage
            area only (PocketShell Linux, the Downloads shelf, or a granted
            SAF folder; it can never escape the area). Symlinks appear as
            results but are never followed; the query is data — never a
            pattern, never a path. Honest limits are shown (200 matches /
            2000 folders; skipped folders are counted), tapping a result
            opens its parent folder and highlights it. <b>Multi-select:</b>
            the header check icon turns the listing into a selection mode —
            tap rows to toggle, then Copy / Move / Delete the whole selection
            through the same per-entry engine as before, with per-item
            Replace/Cancel dialogs on collisions and an honest aggregate
            notice (a cancelled partial paste reports exactly what already
            landed). A selection never survives leaving its folder. No
            background indexing, no database, no content search, no new
            permissions, no new dependencies. JVM suite 653/653 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk">
          Download M7P8.1 APK (debug, 30.5 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          installs in place over M6.0.4 / every M7 build (same versionCode 44, same cert) — glibc layer ed82daa8… unchanged.
          Note: the first m7p8 APK was lost to a sandbox reset before delivery; this rebuilt artifact re-verified every
          semantic pin (version, permission set, embedded layer asset, dex symbols) and carries P8 + P8.1.
        </p>
      </div>

      <div className="card">
        <h2>
          Previous builds — withdrawn after the sandbox reset{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          A sandbox reset wiped the delivery surface: the m7p7.1
          (Open Terminal Here p7.1 fixes), m7p6 (quick text editor) and
          m6.0.4 (adversarial closure audit) APKs and their source cuts are
          no longer servable — the exact bytes cannot be reproduced. Nothing
          is lost that matters: the restored git bundle below carries the
          COMPLETE milestone history M0 → m7p8.1, the P7.1 baseline was
          re-verified from the user-supplied delivery bundle (sha
          0f8fd0a3…), and every semantic pin of the current build is
          re-established. The glibc layer artifact (rev=2) survived
          byte-identical in-tree and is served again below.
        </p>
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
          contract, all Files explorer data and all Companion data are
          untouched. The M7 phases are APP-side only: the layer marker and
          the glibc files stay byte-identical (rev=2, ed82daa8…).
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
            <b>v0.10.0-m6.0.0–m6.0.4:</b> Universal Runtime Compatibility —
            real glibc 2.41 at canonical multiarch paths inside the Alpine
            guest, self-healing pinned delivery, install observability, the
            doctor correctness gate (numeric semantic comparison), the 27/27
            device gate, and the adversarial closure audit (corruption,
            loader-reclaim, concurrency — survived and self-healed).
          </li>
          <li>
            <b>M7.0.0 phases 1–7:</b> one storage abstraction behind the Files
            explorer (PocketShell Linux, the app Downloads shelf, user-granted
            SAF folders), real file operations with collision semantics, the
            byte-honest quick text editor, and &quot;Open Terminal Here&quot;
            on Linux directories — a normal Alpine session in THE TAPPED
            folder through the canonical launch path (injection-proof chain,
            execution-proven), with the terminal &quot;+&quot; matching the
            current session&apos;s environment.
          </li>
          <li>
            <b>M7.0.0 phase 8 (this build):</b> file name search inside the
            selected storage area — recursive, literal, case-insensitive;
            composed entirely from the unchanged storage abstraction;
            symlink-safe by construction; explicit honest limits; errors that
            never masquerade as empty results; its own serial worker so it
            never blocks navigation or races it.
          </li>
          <li>
            <b>M7.0.0 phase 8.1 (this build):</b> multi-select for copy /
            move / delete — the same per-entry operations executed over a
            selection with honest per-item outcomes; per-name Replace/Cancel
            on collisions; nothing silent, ever.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §38–§39)</h2>
        <ol className="steps">
          <li>
            <b>Search (§38):</b> enter/exit search, basic + nested matches,
            case-insensitivity, query-as-data ({"`..`"}, {"/"}, shell
            metacharacters stay literal), per-area scope, revoked-grant
            honesty, limit/skip banners, result activation into the parent.
          </li>
          <li>
            <b>Multi-select (§39):</b> enter/exit selection, multi delete
            (files + folder contents + symlink nodes only), multi copy within
            and across areas, multi move, per-item Replace dialogs, honest
            partial-cancel notice, selection dying at every boundary.
          </li>
          <li>
            <b>Regression ladder:</b> single-entry operations, the editor,
            Open Terminal Here (tapped folder), the terminal &quot;+&quot;
            environment match, and the SAME 6 permissions (§39 G / §38 G).
          </li>
        </ol>
        <p>
          The M6 runtime gates (§33) keep their section in docs/TESTING.md;
          the runtime-tests tarball is not re-served after the reset — it is
          re-cut from the bundle sources at the next runtime gate.
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7 tip e7f2630 (M7 phases 1–8.1: storage abstraction,
          explorer, operations, Android bridge, editor, Open Terminal Here,
          file search, multi-select). The zip intentionally contains no
          dotfiles; full history rides in the git bundle — the complete
          milestone history (M0 → m7p81), all design contracts, the procfs
          contract, and the runtime documentation. Bundle main tip e7f2630 =
          the exact app tip this APK was built from; the archived tree is cut
          at the same commit. History note: this bundle continues the
          user-restored P7.1 delivery bundle (fb01540) — the reset-lost M7
          commits were re-created from the platform snapshot and re-gated
          (see worklog Tasks 22–24).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-m7p8.1-source.zip">
          source.zip (M7 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.10.0-m6.0.4-m7p8.1-source.tar.gz">
          source.tar.gz (M7 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7p81.gitbundle">
          git bundle (full history, M7 tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7p81.gitbundle pocketshell</code>.
          Includes <code>keystore/debug.keystore</code> — clones build APKs
          with the same signing identity (d96a6f66…8bf659, unchanged since
          v0.4.1).
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
        correctness gate · v0.10.0-m6.0.4: the adversarial closure audit ·
        m7.0.0 phases 1–7: storage abstraction, file explorer, file
        operations, Android storage bridge, quick text editor, Open Terminal
        Here ·{" "}
        <b>m7.0.0 phases 8–8.1 (this build): file search + multi-select —
        rebuilt after the reset, re-gated, and delivered</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
