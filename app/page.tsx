const VERSION = "v0.7.0-ui";

const HASHES = {
  apk: "f8db8394fb7ce6b3f272abee094658f0d3a7c660d74690e3c99080874799d719",
  zip: "05aab694e3ec8c6f53456e92c66213e6c52ee793686716e8cebdf5d9d138bf26",
  tgz: "08ef0a80345fcd541b3d1e9fcfadffc369ec61eaf47cd54a0bd9c4ce88063360",
  bundle: "3edd2bbd397176b09010126e8cf744638e1f5b8c5306cbd55907a223180d8198",
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
        A polished personal Linux environment on Android — Alpine guest via
        proot, real <code>apk</code>, a battle-tested terminal, and now a
        complete design system. Nothing faked, ever.
      </p>

      <div className="card primary">
        <h2>
          The Linux runtime is proven — this release makes the app look like
          it <span className="badge">versionCode 17</span>
        </h2>
        <p>
          M2.6 is device-battle-tested (your Hermes install exercised the
          whole guest stack). This phase is the UI/UX redesign the project
          was waiting for: one coherent design system, honest navigation, and
          the keyboard exactly as you specced. <b>The Linux runtime stack is
          untouched</b> — update in place, everything you validated stays.
        </p>
        <ul className="steps">
          <li>
            <b>A real design system — "Quiet Aurora":</b> cool graphite
            surfaces, one soft periwinkle accent, light/dark/AMOLED (+ opt-in
            Material You), a 4dp spacing scale, calm rounded shapes,
            restrained motion. Exactly one gradient exists in the whole app
            (the Terminal hero card). Terminal canvas stays framed ink in
            every theme. Every screen — Home, Terminal, Apps, Packages,
            Diagnostics, Settings — now speaks the same visual language.
          </li>
          <li>
            <b>Navigation without tabs:</b> a small hamburger (two unequal
            lines, top-left on every screen) opens a drawer — Home, Terminal,
            Apps, Packages, Diagnostics, Settings, Linux Shell, with the
            runtime status in the footer. No permanent tab bar eats your
            screen, and future destinations (GUI Apps, SSH, AI chat) plug in
            without a rewrite.
          </li>
          <li>
            <b>Home is a gateway, not a launcher dump:</b> the PocketShell
            brand block and a clean grid — Terminal, Linux Shell, Apps,
            Packages. The git/python/nano/htop "installed CLI app" cards are
            <b> gone</b>: those are packages, reachable via Packages search
            and Featured. No project cards, no IDE concepts, no placeholders
            pretending to work.
          </li>
          <li>
            <b>Apps — honest launch detection:</b> applications that provide
            a real interactive command (Hermes, OpenCode) appear only while
            the guest confirms <code>command -v</code> right now — probed on
            every visit, rows vanish when the binary does, probe failures are
            surfaced. Ordinary CLI tools are categorically excluded
            (test-pinned).
          </li>
          <li>
            <b>Packages (formerly Explore):</b> same honest apk machinery
            verbatim — ranked search, real versions, per-card "Working…",
            real stderr in the banner, Cancel/Retry — in the new skin.
          </li>
          <li>
            <b>Settings & AI foundation:</b> Appearance cards plus an
            OpenRouter configuration section (free-text model id; API key
            masked after entry — shown only as "••••abcd", never logged,
            never in Diagnostics; storage honestly labeled as app-private
            DataStore, keystore-backed upgrade planned). The assistant chat
            itself is stated to not exist yet; Home's small FAB opens this
            config — it never pretends to chat.
          </li>
          <li>
            <b>Diagnostics stays Diagnostics:</b> every fact row and button
            preserved (Gates A–H evidence included), grouped into clean
            cards. Nothing hidden, nothing vague.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>The keyboard — your final spec, implemented</h2>
        <p className="mono">
          Esc · Tab · (gap) · ← ↑ ↓ →
          <br />
          [android keyboard]
          <br />
          [⌨] · Ctrl · Alt · Space · Shift · ↵
        </p>
        <ul className="steps">
          <li>
            The ⌨ toggle is <b>icon-only</b> (no ON/OFF text) and permanently
            first in the bottom row; it shows/hides the <b>Android
            keyboard</b>. Both accessory rows stay visible above it.
          </li>
          <li>
            Arrows auto-repeat; <b>long-press</b> ← → ↑ ↓ gives
            Home/End/PgUp/PgDn. The dedicated Fn key is <b>removed</b> —
            long-press <b>Esc</b> opens an F1–F12 strip (tap to send,
            auto-dismiss). Digits/symbols come from the Android keyboard's
            own long-press.
          </li>
          <li>
            Ctrl/Alt/Shift are real toggles with visual state only: tap =
            one-shot (tinted), tap-tap = locked (lock dot), tap again = off.
          </li>
        </ul>
        <p>
          Device checks for all of this live in <b>docs/TESTING.md §11</b> —
          plus re-running the M2.6 Gates A–H (§10) after the update.
        </p>
      </div>

      <div className="card">
        <h2>Install (in-place update)</h2>
        <ol className="steps">
          <li>Download the APK below and install it OVER v0.6.2 — same
            signing cert (d96a6f66…), so no uninstall, and your installed
            Linux runtime stays exactly as it is.</li>
          <li>Quick sanity: Home shows "Linux ready" → drawer opens →
            Linux Shell → <code>uname -a; apk update</code> still behave.</li>
          <li>Keyboard: verify the two rows, the ⌨ toggle, Esc long-press
            F-strip, arrow long-press nav keys, modifier locks.</li>
          <li>Apps: Hermes should be detected (you installed it); OpenCode
            should NOT appear until it exists in the guest.</li>
        </ol>
      </div>

      <div className="card primary">
        <h2>Download the APK</h2>
        <a className="btn" href="/PocketShell-v0.7.0-ui-debug.apk">
          Download APK ({VERSION}, versionCode 17)
        </a>
        <Sha text={HASHES.apk} />
        <p>
          Debug-signed with the project's pinned key — installs as an update
          over every build since v0.4.1. targetSdk 28 remains the deliberate,
          documented tradeoff that lets the proot guest exist (docs/M2-RESEARCH
          §1.2).
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip d662ed7).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-ui-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-ui-source.tar.gz">
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
        · M2.4 package layer (gate PASSED) · M2.5 apk-capable shell · M2.6
        real /proc + real apk + sysdata overlays + link2symlink
        (device-CONFIRMED, Hermes-validated) · <b>v0.7.0-ui (this build): the
        Quiet Aurora design system, drawer navigation, honest Home/Apps, and
        the final keyboard spec</b>. Next candidates: assistant chat on the
        OpenRouter foundation, file explorer with storage roots, session
        profiles. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}
