const VERSION = "v0.11.0-m7.0.0";

// SHA pins — the M7.0 RELEASE delivery. The payload cutter stages from the
// pinned release tip (709d126) with zeroed mtimes; the embedded git bundle's
// pack bytes are not re-cut-stable, so these pins refer to the ONE delivered
// cut. Semantic pins: versionCode 45, versionName 0.11.0-m7.0.0, cert
// d96a6f66…8bf659, embedded layer asset 898131ff… /17,920,000 B ==
// GlibcRuntimePin. The glibc layer is UNCHANGED rev=2 (byte-identical
// artifact ed82daa8…). The m7p8.1 set (vc44) is superseded by this release
// build and withdrawn below; its history rides in the bundle.
const HASHES = {
  apk: "8826d30dfc23dc6318e8306e5e17ea16a8500ea671217b6d24367afd4f53dd08",
  zip: "fc354ee3c82510d4fea3ae509edd8b7e8faf715d4eb0ea8a576b26482c7db065",
  tgz: "a5a86f5fdee1cb2f3aae0d1aaeaec05005be04eee99d13454b24728f7ba9b173",
  bundle: "b1931f9c632cd36dc0e183fb2af91a91ad1f3616598d7a3c4823b96aaba1f160",
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
          M7.0 RELEASE build: the finalized M7{" "}
          <span className="badge">release tip 709d126 · vc45</span>
        </h2>
        <p>
          <b>
            THE M7.0 RELEASE — all of M7 (Phases 1–8.1) plus the Phase 9
            integration and search-UX fixes. <b>Search scrolling (fixed):</b>{" "}
            the device-reported &quot;results cannot be scrolled&quot; symptom
            had a real root cause — the Files screen never cleared the shared
            keyboard deck&apos;s inset (every other screen did), so the
            results viewport extended behind the deck. Now the whole screen
            ends above the deck: a short result list is fully visible, a long
            one scrolls every row into view. <b>Long-press actions:</b>{" "}
            long-pressing a search result lands in its parent folder and opens
            the SAME action sheet as an explorer row — Open, Open Terminal Here
            (Linux folders), Copy, Move, Share, Export, Rename, Delete —
            routed through the existing per-entry operations; no second file
            manager. <b>One close behavior:</b> the duplicated close arrow is
            gone — the header X (or system Back) closes search; the in-field
            ✕ only clears the query; search still triggers exactly as before
            (no Search button, no submit UI). Carried from P8/P8.1: literal
            case-insensitive name search of the selected area (symlink-safe,
            honest limits, query is data) and multi-select Copy / Move /
            Delete with per-item Replace dialogs and honest aggregates. No
            background indexing, no database, no new permissions, no new
            dependencies. JVM suite 653/653 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.0-m7.0.0-debug.apk">
          Download M7.0 APK (debug, 30.2 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 45 / versionName 0.11.0-m7.0.0 — installs in place over
          M6.0.4 / every M7 build (same cert) — glibc layer ed82daa8…
          unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The m7p8.1 build (vc44, search + multi-select) is SUPERSEDED by this
          release: same features plus the P9 fixes, now versioned 0.11.0-m7.0.0
          (45) — its exact bytes are no longer served; the complete history
          rides in the bundle below (tip 709d126). Earlier withdrawals stand:
          the original m7p8 APK and the m7p7.1/m7p6/m6.0.4 artifact sets were
          lost to a sandbox reset before the reset-survival protocol existed;
          they cannot be reproduced byte-exact. The P7.1 baseline was
          re-verified from the user-supplied delivery bundle (sha 0f8fd0a3…),
          and every phase since is re-gated on the restored history. The
          glibc layer artifact (rev=2) survived byte-identical in-tree and is
          served again below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 45 installs <b>in place over v0.10.0-m6.0.4 (44),
          v0.10.0-m6.0.3 (43),
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
            <b>M7.0.0 phase 8:</b> file name search inside the
            selected storage area — recursive, literal, case-insensitive;
            composed entirely from the unchanged storage abstraction;
            symlink-safe by construction; explicit honest limits; errors that
            never masquerade as empty results; its own serial worker so it
            never blocks navigation or races it.
          </li>
          <li>
            <b>M7.0.0 phase 8.1:</b> multi-select for copy /
            move / delete — the same per-entry operations executed over a
            selection with honest per-item outcomes; per-name Replace/Cancel
            on collisions; nothing silent, ever.
          </li>
          <li>
            <b>M7.0.0 phase 9 (this release):</b> the integration pass —
            search results scroll fully into view above the keyboard deck
            (root cause: the deck inset the Files screen never applied),
            long-press on any result opens the explorer&apos;s own action
            sheet against the landed folder, and one clear close behavior;
            no search-trigger redesign, no new operations engine.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §40 + §35–§39)</h2>
        <ol className="steps">
          <li>
            <b>Search fixes (§40 A–C):</b> short result lists fully visible,
            long lists scroll beyond the viewport (deck open AND closed),
            long-press file / directory / symlink results with only the valid
            actions exposed, actions resolved against the LANDED folder,
            special-character and protected paths, vanished entries never
            open a sheet.
          </li>
          <li>
            <b>Search controls (§40 B):</b> exactly one close behavior (header
            X / system Back), no duplicated arrow, the in-field ✕ clears the
            query only, and the search trigger is unchanged.
          </li>
          <li>
            <b>Regression ladders (§40 D–G, §35–§39):</b> multi-select
            copy/move/delete with collisions and honest partial-cancel, the
            editor (open/edit/save/back guard), Open Terminal Here (nested +
            metacharacter folders, session integrity), PocketShell Downloads
            vs the real SAF Download (restart persistence), and the SAME
            6 permissions on versionCode 45.
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
          Source at the M7.0 release tip 709d126 (M7 phases 1–8.1 plus the P9
          integration and the release finalization: storage abstraction,
          explorer, operations, Android bridge, editor, Open Terminal Here,
          file search, multi-select, search-UX fixes). The zip intentionally
          contains no dotfiles; full history rides in the git bundle — the
          complete milestone history (M0 → m7.0.0), all design contracts, the
          procfs contract, and the runtime documentation. Bundle main tip
          709d126 = the exact app tip this APK was built from; the archived
          tree is cut at the same commit. History note: this bundle continues
          the user-restored P7.1 delivery bundle (fb01540) — the reset-lost M7
          commits were re-created from the platform snapshot and re-gated
          (see worklog Tasks 22–26).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-source.zip">
          source.zip (M7.0 release tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-source.tar.gz">
          source.tar.gz (M7.0 release tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.0.0.gitbundle">
          git bundle (full history, M7.0 release tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.0.0.gitbundle pocketshell</code>.
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
        Here · m7.0.0 phases 8–8.1: file search + multi-select (rebuilt after
        the reset, re-gated) ·{" "}
        <b>v0.11.0-m7.0.0 (this release): phase 9 — scrollable search results
        (deck-inset root cause), long-press actions, one close behavior, and
        the M7 integration pass</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
