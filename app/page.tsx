const VERSION = "v0.11.0-m7.0.0-m7.1p1";

// SHA pins — the M7.1 PHASE 1 delivery. The payload cutter stages from the
// pinned phase tip (3abb2e8) with zeroed mtimes; the embedded git bundle's
// pack bytes are not re-cut-stable, so these pins refer to the ONE delivered
// cut. Semantic pins: versionCode 45, versionName 0.11.0-m7.0.0 (P1 does
// NOT bump the version), cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…). The M7.0 release APK
// (8826d30d…) is superseded by this build (same version stamp, new content)
// and withdrawn below; its history rides in the bundle.
const HASHES = {
  apk: "4d7349f7c16c31802891441701d4c8f5fd3ed4a0d744894e8f91c9bc9b962298",
  zip: "3bdd755fb0e3cf268edc3e259ee0fbadad0f85aa34320ef963daa87c57d2ffbd",
  tgz: "f6bad9aacc3790eeafdc98f5778fefc338d9337e990023058b2ac66b5c58d540",
  bundle: "98a80b0c3d765bca5898255fc9b78ed9a1d0db83f934c204758112e8e19e3d94",
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
          M7.1 Phase 1: Home launchers{" "}
          <span className="badge">phase tip 3abb2e8 · vc45</span>
        </h2>
        <p>
          <b>
            THE LAUNCHER MODEL — one design rule: a launcher is not an app.{" "}
            <b>Companions on Home:</b> ChatGPT, Claude, Z.ai and GitHub are
            preinstalled (seeded once through the existing companion storage) —
            tapping raises the SAME companion sheet as before on the SAME web
            canvas; no APIs, no SDKs, no Android-app integration.{" "}
            <b>Your tools:</b> the built-in CLI launchers (Hermes, OpenCode,
            Claude Code, ZCode, Kilo Code, the NEW Cline, Gemini CLI, Codex,
            Aider, Qwen Code) plus user-defined custom tools — a tile is NOT
            an install claim; tapping runs the honest verify-then-launch path
            (real guest-shell probe → real Linux session, or a truthful
            refusal banner naming the missing command). <b>Custom tools:</b>{" "}
            Name + Command (+ optional icon) — the command is user
            configuration carried verbatim through the SAME launch chain.{" "}
            <b>Remove from Home:</b> long-press → confirm → hide-only;
            everything stays configured and restorable in Settings → Home
            launchers. <b>Icons:</b> system-picker images are COPIED into app
            storage (the original can be deleted); everything else wears the
            deterministic letter badges with collision resolution (ChatGPT→C,
            Claude→Cl, Codex→Co). The M6-frozen companion package: ZERO file
            changes. No package manager, no app store, no CLI discovery, no
            new permissions, no new dependencies. JVM suite 679/679 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.0-m7.0.0-m7p1-debug.apk">
          Download M7.1 P1 APK (debug, 30.3 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 45 / versionName 0.11.0-m7.0.0 (unchanged — P1 does not
          bump the version) — installs in place over the M7.0 release and
          every earlier build (same cert) — glibc layer ed82daa8… unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The M7.0 release APK (8826d30d…, 30,164,873 B) is SUPERSEDED by this
          build: same version stamp (vc45 / 0.11.0-m7.0.0 — P1 deliberately
          does not guess the next version number), new content. Its exact
          bytes are no longer served; the complete history rides in the bundle
          below (release tip 709d126, now two commits below this tip
          3abb2e8). Earlier withdrawals stand: the m7p8.1 (vc44) and the
          reset-lost m7p8/m7p7.1/m7p6/m6.0.4 artifact sets — their content and
          history are fully contained in this bundle. The glibc layer artifact
          (rev=2) survived byte-identical in-tree and is served again below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 45 installs <b>in place over the M7.0 release build
          (also 45 — same stamp, new bytes), v0.10.0-m6.0.4 (44),
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
          contract, all Files explorer data, all Companion data and the
          launcher visibility/icon settings are untouched. M7.1 P1 is
          APP-side only: the layer marker and the glibc files stay
          byte-identical (rev=2, ed82daa8…).
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
            doctor correctness gate, and the adversarial closure audit.
          </li>
          <li>
            <b>M7.0.0 (phases 1–9):</b> one storage abstraction behind the
            Files explorer (Linux, the Downloads shelf, user-granted SAF
            folders) with real operations and collision semantics, the
            byte-honest quick text editor, Open Terminal Here (a normal Alpine
            session in THE TAPPED folder), recursive file search composed from
            the unchanged storage abstraction, multi-select with honest
            aggregates, and the P9 interaction fixes (scrollable results above
            the keyboard deck, long-press actions on the landed folder, one
            close behavior).
          </li>
          <li>
            <b>M7.1 P1 (this build):</b> Home launchers — the Companions grid
            (four seeded companion websites) + the Your tools grid (registry +
            custom tools), hide-only remove with restore, custom tool
            add/edit/remove through the ONE verify-then-launch guest path,
            deterministic text badges, copied icon imports. No package
            manager, no app store, no discovery, no install-state claims.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §41)</h2>
        <ol className="steps">
          <li>
            <b>Companions (§41 A–B):</b> the four seeded sites open the
            existing sheet and load; web sessions survive restarts; hide /
            restore / delete / restore-built-in semantics; custom companions
            via the existing screen; icon copies survive deleting the original
            image.
          </li>
          <li>
            <b>Tools (§41 C–E):</b> registry tiles always visible with honest
            tap-time refusals for absent commands, real launches for installed
            ones (including the new Cline entry); custom tool add / edit /
            launch / remove; single-line validation; badge collisions
            (Claude Code→Cl, Cline→C, Codex→Co); icon fallbacks.
          </li>
          <li>
            <b>Remove-from-Home (§41 F):</b> long-press → confirm → hide-only
            (nothing uninstalled, nothing deleted), restore in Settings →
            Launchers.
          </li>
          <li>
            <b>Regression ladders (§41 G, §35–§40):</b> P8 search + P9
            scroll/long-press fixes, P8.1 multi-select, the editor, Open
            Terminal Here, SAF/Downloads, and the SAME 6 permissions on
            versionCode 45.
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
          Source at the M7.1 P1 tip 3abb2e8 (the launcher domain, Home +
          Settings wiring, the Cline registry entry, the custom-tool launch
          chain, §41, and the full M7.0 release below it). The zip
          intentionally contains no dotfiles; full history rides in the git
          bundle — the complete milestone history (M0 → m7.1 p1), all design
          contracts, the procfs contract, and the runtime documentation.
          Bundle main tip 3abb2e8 = the exact app tip this APK was built from;
          the archived tree is cut at the same commit. History note: this
          bundle continues the user-restored P7.1 delivery bundle (fb01540)
          through the M7.0 release chain (47bed42 → 709d126 → dd81bc8 — see
          worklog Tasks 22–27).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p1-source.zip">
          source.zip (M7.1 P1 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p1-source.tar.gz">
          source.tar.gz (M7.1 P1 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.1-p1.gitbundle">
          git bundle (full history, M7.1 P1 tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.1-p1.gitbundle pocketshell</code>.
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
        the reset, re-gated) · v0.11.0-m7.0.0: phase 9 — scrollable search
        results (deck-inset root cause), long-press actions, one close
        behavior, the M7 integration pass ·{" "}
        <b>m7.1 p1 (this build): home launchers — companion + CLI tool
        grids, hide/restore, custom tools, deterministic badges</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
