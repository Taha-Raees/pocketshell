const VERSION = "v0.6.2-m2.6";

const HASHES = {
  apk: "35c4cd698010cb8039369059df5c60042cc06232630848acb556fc8136249226",
  zip: "c32feb43c6426d3e0500004af3f7da1aeec5a1009c373d48725721b2a370a27d",
  tgz: "004ae63559499d5343c24d8d9679257e69436c955a42a93cc07bc0b4786cfc87",
  bundle: "c97956e8695704ddd84bd3a9d3a74607cd3d1ffd3574e07cf85777a7074e0e6f",
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
          The device session that confirmed M2.6 also broke your build tools —
          v0.6.2 fixes both findings{" "}
          <span className="badge">versionCode 16</span>
        </h2>
        <p>
          The 2026-09-02 run on your SM-F711B confirmed the M2.6 architecture
          end-to-end <b>and</b> exposed two real Android-SELinux walls this
          build now repairs — with the same discipline as before: reuse what
          the ecosystem already proved, derive everything from real host
          sources, and say plainly what is overlaid and why.
        </p>
        <ul className="steps">
          <li>
            <b>M2.6.13 — hardlink extraction, fixed:</b>{" "}
            <code>apk add binutils gcc g++</code> failed with exactly 19{" "}
            <i>"failed to extract … Permission denied"</i> errors — byte-for-
            byte the hardlink entries of those packages (11 + 5 + 3). apk
            materializes tar hardlinks with <code>link()</code>, and Android
            neverallows <code>link()</code> to untrusted apps (the same
            neverallow M2.6 bypassed for download commits — extraction is a
            different call site). The fix is <b>Termux's own proot extension</b>{" "}
            <code>--link2symlink</code> (compiled into the libproot we ship
            since M2.3; PRoot-Distro enables it by default): link/linkat are
            intercepted and emulated as symlink chains, so the kernel never
            evaluates the denied call. Binaries are byte-identical
            (rehearsal-proven); the honest difference — emulated links show as
            symlinks and each costs its own disk space — is documented. Your
            broken binutils/gcc/g++ state <b>self-heals on the first{" "}
            <code>apk fix</code></b> under v0.6.2.
          </li>
          <li>
            <b>M2.6.12 — selective /proc sysdata overlay (Termux
            PRoot-Distro's architecture, adapted):</b> at every interactive
            spawn the app probes the five standard procfs files Android's
            policy denies this app domain (<code>stat</code>,{" "}
            <code>uptime</code>, <code>loadavg</code>, <code>version</code>,{" "}
            <code>vmstat</code>) with a one-byte read. <b>Kernel-readable
            files are NEVER overlaid — real wins.</b> Only genuinely-denied
            files get a verified compatibility overlay bound file-over-file on
            top of the real <code>/proc</code> bind, with content from real
            host sources: <code>uname(2)</code> identity for{" "}
            <code>/proc/version</code> (with an explicit{" "}
            <i>"PocketShell sysdata overlay"</i> attribution marker in the file
            itself), <code>elapsedRealtime</code> for uptime, real core count
            + real btime for <code>/proc/stat</code>, the real
            hidepid-filtered pid set for <code>/proc/loadavg</code>'s tail —
            and documented placeholders where no allowed source exists. The
            kernel-internal wall (<code>kmsg</code>, <code>kcore</code>, …)
            stays untouched: those are not standard compatibility files.
          </li>
          <li>
            <b>What stays real:</b> the readable <code>/proc</code> tail (pid
            dirs, <code>meminfo</code>, <code>cpuinfo</code>,{" "}
            <code>mounts</code>, …), <code>ps</code>/<code>top</code>/
            <code>htop</code>'s process rows, <code>apk update / search / add
            / del</code> everywhere, Node end-to-end. Probe-first is the
            honesty rule: if your kernel allows a standard file, you get the
            kernel's own file — never our overlay.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.6.2-m2.6-debug.apk">
          Download APK (debug, 21 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.6.2)
        </p>
      </div>

      <div className="card">
        <h2>Expected on-device — updated for v0.6.2 (SM-F711B)</h2>
        <ul className="steps">
          <li>
            <b><code>ls /proc</code> still prints a wall of "Permission
            denied" lines</b> (kmsg, kcore, vmcore, kpage*, sched_debug, …)
            before the readable tail — Android SELinux genuinely denies this
            app getattr on kernel-internal nodes, and v0.6.2 deliberately does
            NOT overlay those (they are not standard compatibility files).
            The PASS signal remains the readable tail: numeric pid dirs,
            meminfo, cpuinfo, cmdline, uptime, loadavg, mounts, sys, tty, fs,
            bus, irq, driver (Samsung adds memsize/memextra).
          </li>
          <li>
            <b><code>cat /proc/version</code> is now REPAIRED on denying
            kernels:</b> the overlay shows{" "}
            <i>"Linux version &lt;real release&gt; (PocketShell sysdata
            overlay: kernel identity via uname(2); the kernel's own file is
            denied to apps by Android SELinux) &lt;real build tail&gt;"</i> —
            the release and build tail are the real <code>uname(2)</code>{" "}
            identity; the parenthetical says plainly what the file is. This
            supersedes v0.6.1's synthesis refusal per the project owner's
            direction, with the marker keeping it honest. On kernels that
            allow the file, you get the real kernel banner — never overlaid.
            <code> uname -a</code> still works for comparison.
          </li>
          <li>
            <b><code>top</code>'s CPU% column reads ~0%</b> under the overlay:
            the kernel's global jiffies counters are denied to apps, so the
            overlay's counters are static, documented placeholders — the
            process ROWS stay real. On kernels that allow <code>/proc/stat</code>{" "}
            the real file wins and CPU% comes alive.
          </li>
          <li>
            <b>Emulated hardlinks appear as symlinks</b> ({" "}
            <code>ls -l /usr/bin/ld</code> shows a symlink chain): that is{" "}
            <code>link2symlink</code> doing its job, not a defect — the
            binaries are byte-identical to the real ones.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          Installs <b>in place over v0.6.1/v0.6.0/v0.5.0</b> (same pinned
          signing key). Your runtime, installed packages and cache are
          untouched: the fd-link patch is already applied on your device, the
          sysdata overlays write themselves on the first Linux Shell spawn
          (probe-gated, verified before binding), and the first{" "}
          <code>apk fix</code> re-extracts the binutils/gcc/g++ state the
          2026-09-02 run left broken. If anything cannot be written or
          verified, that one entry degrades honestly and Diagnostics reports
          it — a spawn never fails because an overlay failed.
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
            <code>/proc</code>, and <code>apk</code> still working.
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
            M2.6 (confirmed on device 2026-09-02): real process tools without
            giving up the package manager.
          </li>
          <li>
            <b>v0.6.2 (this build):</b> the two SELinux walls that same device
            session exposed — standard procfs files denied to apps, and
            hardlink extraction denied to apk — repaired with Termux-proven
            architecture (PRoot-Distro's sysdata model + proot's{" "}
            <code>link2symlink</code>), real-source content, and honest
            attribution markers.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §10 — Gates A–H)</h2>
        <ol className="steps">
          <li>Install {VERSION} over v0.6.1/v0.6.0/v0.5.0 (no uninstall) → open Linux Shell.</li>
          <li>
            <b>Gate A (PRIMARY):</b> the five standard files —{" "}
            <code>cat /proc/stat</code> (parseable; real btime),{" "}
            <code>cat /proc/uptime</code> (real field 1),{" "}
            <code>cat /proc/loadavg</code> (real pid tail),{" "}
            <code>cat /proc/version</code> (real uname identity + attribution
            marker), <code>cat /proc/vmstat</code> (kernel-name skeleton) —
            plus <code>meminfo</code>/<code>cpuinfo</code> still real
            (never overlaid) and the <code>ls /proc</code> EACCES wall still
            expected on kernel-internal entries.
          </li>
          <li>
            <b>Gate B:</b> <code>ps</code> → real process rows;{" "}
            <code>top</code> → opens, updates, <code>q</code> quits (CPU% ~0%
            is the documented overlay placeholder — rows are real).
          </li>
          <li>
            <b>Gate C:</b> <code>apk update</code> → OK (~28k packages, no
            "Permission denied"); <code>apk add htop</code> →{" "}
            <code>htop</code> runs.
          </li>
          <li>
            <b>Gate D:</b> <code>apk add nodejs npm</code> →{" "}
            <code>node --version</code> → <code>node index.js</code> prints
            your line.
          </li>
          <li>
            <b>Gate H (new, heals your device):</b> <code>apk fix</code> →
            completes with NO extract errors;{" "}
            <code>gcc --version && g++ --version && ld --version</code> → real
            GNU banners; <code>ls -l /usr/bin/ld</code> → symlink chain
            (expected, documented).
          </li>
          <li>
            Gates E–G: Explore install/uninstall still honest;{" "}
            <code>nano</code> unchanged; session isolation intact.
          </li>
          <li>
            Diagnostics → <b>apk fd-link patch: applied</b> (confirmed on your
            device) + the new read-only <b>sysdata overlays</b> row, which
            names exactly which files are real and which are overlaid.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 5592173).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.6.2-m2.6-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.6.2-m2.6-source.tar.gz">
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
        apk-capable shell + ranked/installable search · M2.6 real /proc + real
        apk (<b>device-CONFIRMED 2026-09-02</b>) · v0.6.1: docs matched the
        honest device behavior · <b>v0.6.2 (this build): the two SELinux walls
        that run exposed are now repaired — Termux PRoot-Distro's sysdata
        architecture adapted, proot's own link2symlink enabled</b>. Next
        candidates: M2.7 session management + CLI app profiles, or a curated
        CLI app catalog. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}
