const VERSION = "v0.11.0-m7.0.0-m7.1p2.2";

// SHA pins — the M7.1 PHASE 2.2 delivery. The payload cutter stages from the
// pinned phase tip (6004805) with zeroed mtimes; the embedded git bundle's
// pack bytes are not re-cut-stable, so these pins refer to the ONE delivered
// cut. Semantic pins: versionCode 45, versionName 0.11.0-m7.0.0 (P2.2 does
// NOT bump the version), cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…). The M7.1 P2.1 APK
// (97c04120…) is superseded by this build (same version stamp, new content)
// and withdrawn below; its history rides in the bundle.
const HASHES = {
  apk: "7e0e99a92a3234be11308ce487ee82199a3fef7d9276eee13cab107c2567b6c1",
  zip: "0e2b9d3d5932ebf415b989b8594c9fa9f2b3dc3cbb2e5cb8d3b8750d164e45c4",
  tgz: "a45a0f02d4d46a2bd1a3d03783f7dcc8bbdba7498822ceeeebf431050370add4",
  bundle: "1e8822fc280edca4395ff93a9e580cc579cf8b898a1cbdb143b12aaa4e9bcf27",
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
          M7.1 Phase 2.2: theme-scheme icons + x-scroll home rows + the
          packages affordance aligned{" "}
          <span className="badge">phase tip 6004805 · vc45</span>
        </h2>
        <p>
          <b>
            <b>ICONS FOLLOW THE THEME SCHEME</b> — every curated launcher mark
            now ships a two-variant theme pair ({`{id}`}.webp Midnight,{" "}
            {`{id}`}-light.webp Daylight), rendered by the offline pipeline on
            the app's OWN plate tones (the same theme surface the badge tiles
            paint: #16233F dark / #EDF1F7 light). Monochrome glyphs invert per
            theme — the Hermes mascot, Kilo letters, and Cline robot render
            WHITE on the dark plate (they vanished as black marks on Midnight)
            and ink-on-light; ChatGPT's knot, GitHub's octocat, and Codex's
            flower adapt with their own brand inks; self-contained brand tiles
            (Z.ai, OpenCode) and colored transparent marks (Claude terracotta,
            Antigravity, Claude Code, Qwen) are theme-proof. A live theme flip
            swaps the marks without leaving Home. <b>X-SCROLL HOME ROWS</b>:
            Companions in ONE horizontal row, Your tools in TWO, both with
            scroll dots (the accent pill tracks the visible page).{" "}
            <b>THE PACKAGES BUTTON LEFT THE MIDDLE OF THE SCREEN</b>: it now
            lives in the "Your tools" header — Manage, right where the section
            lives below its divider — and opens the packages page; the old
            footer link is retired. Companions keep their launcher-settings
            Manage; verify-then-launch honesty, hide-only removal, and the
            badge fallback are unchanged. No new permissions, no new
            dependencies, the M6-frozen companion package untouched, JVM suite
            696/696 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk">
          Download M7.1 P2.2 APK (debug, 30.7 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 45 / versionName 0.11.0-m7.0.0 (unchanged — P2.2 does not
          bump the version) — installs in place over every earlier build (same
          cert) — glibc layer ed82daa8… unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The M7.1 P2.1 APK (97c04120…, 30,342,970 B) and its source set are
          SUPERSEDED by this build: same version stamp (vc45 / 0.11.0-m7.0.0 —
          the P2.2 quick fix deliberately does not guess the next version
          number), new content (theme-variant icons, the x-scroll rows, the
          packages affordance). Their exact bytes are no longer served; the
          complete history rides in the bundle below (P2.1 tip e0a2471, now one
          commit below this tip 6004805). Earlier withdrawals stand: the M7.1
          P2 APK (ae6f6445…), the M7.1 P1 APK (4d7349f7…), the M7.0 release
          APK (8826d30d…), the m7p8.1 (vc44) and the reset-lost
          m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and history are fully
          contained in this bundle. The glibc layer artifact (rev=2) is
          byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 45 installs <b>in place over the M7.1 P2.1 build (also
          45 — same stamp, new bytes), the M7.1 P2 build (also 45), the M7.1
          P1 build (also 45), the M7.0
          release (also 45), v0.10.0-m6.0.4
          (44),
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
          launcher visibility/icon settings are untouched. M7.1 P2.2 is
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
            <b>M7.1 P1:</b> Home launchers — the Companions grid (four seeded
            companion websites) + the Your tools grid (registry + custom
            tools), hide-only remove with restore, custom tool add/edit/remove
            through the ONE verify-then-launch guest path, deterministic text
            badges, copied icon imports.
          </li>
          <li>
            <b>M7.1 P2:</b> the launcher settings rows fixed at the root cause
            (ONE shared weighted-row shape — no more one-character-per-line
            collapse, no stretched in-row buttons), the bundled official
            launcher marks (offline, normalized, badge fallback), and
            Antigravity (<code>agy</code>) replacing Gemini CLI in the
            curated defaults with the same honest probe.
          </li>
          <li>
            <b>M7.1 P2.1:</b> Aider removed from the curated set with no stale
            trace, and nine marks re-rendered from the owner-supplied official
            brand SVGs (vendored in-tree, offline, byte-reproducible).
          </li>
          <li>
            <b>M7.1 P2.2 (this build):</b> every curated mark ships a
            two-variant theme pair on the app's own plate tones (theme-reactive
            icon colors, live theme flips), Companions scroll in one row and
            tools in two — both with scroll dots — and the packages affordance
            is the tools header's Manage action (the mid-page footer link
            retired).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §44)</h2>
        <ol className="steps">
          <li>
            <b>Theme — dark:</b> launcher icons read on Midnight (Hermes, Kilo,
            Cline, ChatGPT, GitHub, Codex in white on the dark navy plate).
          </li>
          <li>
            <b>Theme — light:</b> the same tiles flip to the paper plate with
            ink glyphs — nothing white-on-white, nothing glaring.
          </li>
          <li>
            <b>Theme — AMOLED/System</b> follow the same rule; a live theme
            flip swaps the marks without leaving Home.
          </li>
          <li>
            <b>One row / two rows:</b> Companions x-scroll in one row (2 dots
            on a phone), Your tools in two rows (dots when content overflows);
            the accent pill tracks the visible page; no dots on a single page.
          </li>
          <li>
            <b>The packages affordance:</b> Your tools → Manage opens the
            packages page; Companions → Manage still opens Home-launcher
            settings; the old centered Packages footer link is gone.
          </li>
          <li>
            <b>Fallbacks intact:</b> user-imported icons still outrank every
            bundled mark (and do NOT flip with the theme); the letter badge
            still covers custom launchers and any asset that fails to decode.
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
          Source at the M7.1 P2.2 tip 6004805 (the theme-variant icon pipeline
          and the 26 packaged assets, the theme-reactive tile loader, the
          x-scroll home rows with scroll dots, the packages affordance
          realigned into the tools header, §44, and the full P2.1 + P2 + P1 +
          M7.0 release below it). The zip intentionally contains no dotfiles;
          full history rides in the git bundle — the complete milestone history
          (M0 → m7.1 p2.2), all design contracts, the procfs contract, and the
          runtime documentation. Bundle main tip 6004805 = the exact app tip
          this APK was built from; the archived tree is cut at the same commit.
          History note: this bundle continues the user-restored P7.1 delivery
          bundle (fb01540) through the M7.0 release chain (47bed42 → 709d126 →
          dd81bc8), the P1 launcher phase (3abb2e8), the P2 UI-repair phase
          (1b15bde), and the P2.1 owner-marks phase (e0a2471 — see worklog
          Tasks 22–32).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p2.2-source.zip">
          source.zip (M7.1 P2.2 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p2.2-source.tar.gz">
          source.tar.gz (M7.1 P2.2 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.1-p2.2.gitbundle">
          git bundle (full history, M7.1 P2.2 tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.1-p2.2.gitbundle pocketshell</code>.
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
        behavior, the M7 integration pass · m7.1 p1: home launchers —
        companion + CLI tool grids, hide/restore, custom tools, deterministic
        badges · m7.1 p2: launcher UI repair — the settings-row collapse
        root-caused, official bundled icons, Antigravity (agy) replaces Gemini
        CLI · m7.1 p2.1: Aider removed with no stale trace; nine marks
        re-rendered from the owner-supplied official brand SVGs (in-tree,
        offline, byte-reproducible) ·{" "}
        <b>m7.1 p2.2 (this build): icon colors follow the theme scheme
        (two-variant marks on the app's own plates), Companions one x-scroll
        row, tools two with scroll dots, the packages affordance aligned into
        the tools header</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
