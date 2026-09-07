const VERSION = "v0.11.0-m7.0.0-m7.1p2.1";

// SHA pins — the M7.1 PHASE 2.1 delivery. The payload cutter stages from the
// pinned phase tip (e0a2471) with zeroed mtimes; the embedded git bundle's
// pack bytes are not re-cut-stable, so these pins refer to the ONE delivered
// cut. Semantic pins: versionCode 45, versionName 0.11.0-m7.0.0 (P2.1 does
// NOT bump the version), cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…). The M7.1 P2 APK
// (ae6f6445…) is superseded by this build (same version stamp, new content)
// and withdrawn below; its history rides in the bundle.
const HASHES = {
  apk: "97c04120ec56af2761c7923bbcd699cd96c8a9f17ccf7990aff4507d33a4a066",
  zip: "1bb4c41fc0f02373074c43f1397a194a1755f2b7b154453bef824105c0477231",
  tgz: "a38213b3cd0138cf0a6805b74ed17d1f444f5b1f808d319930da74e39f12e64b",
  bundle: "fb0887b5b93a859f833164eff55f4fa305d6e0a3fd5eccb4c0fc889b40c7d35f",
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
          M7.1 Phase 2.1: Aider removed + owner-supplied official marks{" "}
          <span className="badge">phase tip e0a2471 · vc45</span>
        </h2>
        <p>
          <b>
            <b>AIDER IS GONE</b> from the curated default CLI launcher set —
            registry, ids, commands, display names, and the bundled-asset
            mapping, with no stale trace anywhere (the Gemini CLI removal
            pattern, pinned by a dedicated test; a stale persisted hide id is
            inert). <b>Nine official marks re-rendered from owner-supplied
            official brand SVGs</b> vendored in-tree (zonalogo.com mirrors,
            fully offline and byte-reproducible): ChatGPT (white knot on the
            OpenAI-black tile), Claude (terracotta starburst), Z.ai (Z tile),
            GitHub (white octocat on the GitHub-dark tile), Hermes Agent
            (mascot on white plate), OpenCode (its own dark tile glyph), Kilo
            Code (pixel letters on white — the vector ships no fill), Cline
            (robot head on white plate), Antigravity (colored arc). Claude
            Code, ZCode, Codex, and Qwen Code keep their P2 marks — the
            curated set is now 13. Everything else rides from P2 unchanged:
            the settings-row repair through ONE shared weighted row, the
            bundled offline icon layer with the imported-copy → bundled →
            badge resolution, and Antigravity (<code>agy</code>) as the honest
            verify-then-launch launcher that replaced Gemini CLI. No new
            permissions, no new dependencies, the M6-frozen companion package
            untouched, JVM suite 691/691 green.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk">
          Download M7.1 P2.1 APK (debug, 30.3 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 45 / versionName 0.11.0-m7.0.0 (unchanged — P2.1 does not
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
          The M7.1 P2 APK (ae6f6445…, 30,447,567 B) and its source set are
          SUPERSEDED by this build: same version stamp (vc45 / 0.11.0-m7.0.0 —
          P2.1 deliberately does not guess the next version number), new
          content (Aider removed, the nine owner-supplied marks). Their exact
          bytes are no longer served; the complete history rides in the bundle
          below (P2 tip 1b15bde, now one commit below this tip e0a2471).
          Earlier withdrawals stand: the M7.1 P1 APK (4d7349f7…), the M7.0
          release APK (8826d30d…), the m7p8.1 (vc44) and the reset-lost
          m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and history are fully
          contained in this bundle. The glibc layer artifact (rev=2) is
          byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 45 installs <b>in place over the M7.1 P2 build (also 45
          — same stamp, new bytes), the M7.1 P1 build (also 45), the M7.0
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
          launcher visibility/icon settings are untouched. M7.1 P2.1 is
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
            <b>M7.1 P2.1 (this build):</b> Aider removed from the curated set
            with no stale trace, and nine marks re-rendered from the
            owner-supplied official brand SVGs (vendored in-tree, offline,
            byte-reproducible) with the documented tile/plate treatments —
            Claude Code, ZCode, Codex, and Qwen Code unchanged (13 curated
            launchers total).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §43)</h2>
        <ol className="steps">
          <li>
            <b>Aider absence:</b> Home → Your tools shows nine CLI launchers;
            Aider appears nowhere (defaults, settings, restore); a stale
            persisted hide id is inert.
          </li>
          <li>
            <b>The nine refreshed marks render offline</b> (airplane mode
            identical) — ChatGPT white knot on OpenAI-black, Claude terracotta
            starburst, Z.ai tile, GitHub white octocat on GitHub-dark, Hermes
            mascot on white, OpenCode dark tile glyph, Kilo Code pixel letters
            on white, Cline robot head on white, Antigravity colored arc.
          </li>
          <li>
            <b>The four untouched marks</b> (Claude Code, ZCode, Codex, Qwen
            Code) render exactly as §42 step 8 described.
          </li>
          <li>
            <b>Fallbacks intact:</b> the letter badge still covers custom
            launchers and any asset that fails to decode; a user-imported icon
            still outranks every bundled mark; §42 layout checks (the row
            repair) still pass.
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
          Source at the M7.1 P2.1 tip e0a2471 (the Aider removal with its test
          pins, the nine owner-supplied brand SVGs vendored in-tree at
          scripts/icon_sources/ with the locals-first icon pipeline, the
          refreshed marks, §43, and the full P2 + P1 + M7.0 release below it).
          The zip intentionally contains no dotfiles; full history rides in the
          git bundle — the complete milestone history (M0 → m7.1 p2.1), all
          design contracts, the procfs contract, and the runtime documentation.
          Bundle main tip e0a2471 = the exact app tip this APK was built from;
          the archived tree is cut at the same commit. History note: this
          bundle continues the user-restored P7.1 delivery bundle (fb01540)
          through the M7.0 release chain (47bed42 → 709d126 → dd81bc8), the P1
          launcher phase (3abb2e8), and the P2 UI-repair phase (1b15bde — see
          worklog Tasks 22–30).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p2.1-source.zip">
          source.zip (M7.1 P2.1 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p2.1-source.tar.gz">
          source.tar.gz (M7.1 P2.1 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.1-p2.1.gitbundle">
          git bundle (full history, M7.1 P2.1 tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.1-p2.1.gitbundle pocketshell</code>.
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
        CLI ·{" "}
        <b>m7.1 p2.1 (this build): Aider removed with no stale trace; nine
        marks re-rendered from the owner-supplied official brand SVGs
        (in-tree, offline, byte-reproducible)</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}
