#!/bin/bash
# Build the M2 delivery payload for the Next.js download page:
#   - full git bundle at current tip (complete milestone history)
#   - zero-dotfile buildable source tree zip + tar.gz twin
#     (web scaffold + app/page.tsx + app/layout.tsx shims excluded)
#   - copies of the M2 APK
# Outputs land in public/ (served by the web app) with a backup in dist-master/.
set -euo pipefail

PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DIST=$PROJECT/dist-master
VERSION=v0.4.1-m2.4
TOPDIR=PocketShell-$VERSION
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP=$(git rev-parse --short HEAD)
echo "== payload $VERSION @ git tip $TIP =="

mkdir -p "$DIST" "$PUBLIC"

# --- 1. git bundle: complete history, one file ---
BUNDLE=$STAGE/pocketshell-m2.gitbundle
git bundle create "$BUNDLE" --all
git bundle verify "$BUNDLE" > /dev/null
echo "bundle ok: $(du -h "$BUNDLE" | cut -f1)"

# --- 2. RESTORE.txt (references the bundle for history) ---
cat > "$STAGE/RESTORE.txt" << EOF
PocketShell - source snapshot ($VERSION, git tip $TIP)
=========================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints: initial -> M0
-> M1 -> M1.1 -> M1.2 -> M1.3 -> web-delivery records -> M2.1 research
-> M2.2 runtime install) is preserved inside the single file:

  pocketshell-m2.gitbundle

WHAT IS NEW IN $VERSION (vs v0.4.0-m2.4):
  - FIXED on-device M2.4 failures (user recording, Samsung SM-F711B): apk
    update died with "Permission denied" / "DNS: transient error". Root
    cause: the v0.4.0 DNS repair hardcoded public resolvers (1.1.1.1/8.8.8.8)
    that are UNREACHABLE on the user's network; every apk fetch died in the
    socket layer before ever reaching dl-cdn. v0.4.1 points the guest
    /etc/resolv.conf at the DEVICE's own resolvers (ConnectivityManager /
    LinkProperties, IPv4 first), falls back to the public pair only when the
    OS reports none, and upgrades the v0.4.0 fallback file in place
    (user-written resolv.conf is never touched). New normal permission:
    ACCESS_NETWORK_STATE (read-only metadata, no traffic).
  - HARDENED: the apk download cache now lives OUTSIDE the rootfs — the
    package spec binds two app-owned host dirs over /etc/apk/cache and
    /var/cache/apk (same proot --bind mechanism as /dev,/proc,/sys), plus a
    pre-op workspace repair inside the rootfs. Rootfs permissions can no
    longer block apk.
  - HONESTY: the Explore FAILED banner shows apk's real stderr + a Retry
    button; Diagnostics "Check package environment" runs ONE real bounded
    apk update probe (explicit press) and reports the true outcome + which
    DNS servers the guest received and their source.
  - SIGNING CERT CHANGED (one-time uninstall required): sandbox reset #5
    destroyed the old debug keystore; Android debug signatures are the
    update identity, so v0.4.1 installs AFTER uninstalling v0.4.0 (runtime
    comes back in one tap). The new debug keystore is COMMITTED at
    keystore/debug.keystore and pinned in signingConfigs.debug - this is
    the last cert break: every future build is an in-place update again.
  - 12 new unit tests (271 total, 0 failures); x86_64 rehearsal re-run with
    the cache binds (update 28645 pkgs -> add nano -> runs -> del).

WHAT WAS NEW IN v0.4.0-m2.4:
  - M2.4: REAL Alpine package management. Explore CLI Apps is now a working
    frontend for the real apk inside the guest: search (real \`apk search\`),
    install (\`apk add\`), verify (\`apk info -e -v\` exit codes + POSIX
    `command -v`), open (new dedicated guest session running the real
    program), uninstall (\`apk del\`). The same proot exec infrastructure as
    the Linux Shell is reused; nothing is faked: no fake progress, no fake
    installed state, no fake catalog claims. Package operations run in a
    dedicated background guest process (never typed into a user session).
  - Guest DNS repair: the Alpine minirootfs ships no /etc/resolv.conf, so
    apk would fail every name lookup. Fresh installs get one at configure;
    existing runtimes are repaired in place before the first package op.
  - Curated catalog (metadata only): nano, htop, vim, git, python3. Normal
    shell commands (sh/ls/cat/df/ping...) never become launcher cards.
  - Diagnostics: explicit "Check package environment" button (apk version,
    repositories, package database, DNS) - nothing runs automatically.
  - 41 new unit tests (259 total, 0 failures).
  - M2.3 device gate PASSED on the user's device (screenshot 2026-09-01:
    guest prompt, uname/id/hello, alpine-release 3.24.1, exit clean).

WHAT WAS NEW IN v0.3.1-m2.3:
  - FIXED (device crash): tapping "Linux Shell" exited the app instantly.
    Root cause 1: AGP 8 default extractNativeLibs=false left
    nativeLibraryDir EMPTY, so a bare require() escaped the click handler
    and killed the process. Now useLegacyPackaging=true (libs extracted;
    verified extractNativeLibs=true in the built APK).
  - Root cause 2: targetSdk 36 could never run the guest anyway — Android's
    W^X policy (AOSP app_neverallows.te + seapp_contexts) blocks execve of
    app-data files for targetSdk >= 29. Now targetSdk 28 (untrusted_app_27,
    the Termux model) so proot can exec the Alpine guest shell.
  - Crash-proof launch path: pure preflight (RuntimeProcessLauncher
    .preconditionProblem), a single no-crash boundary for every session
    spawn, and an honest dismissible error banner on Home with a
    Diagnostics shortcut. A refused launch can never kill the process again.
  - 4 new unit tests (215 total, 0 failures). Same signing cert — installs
    as a direct update over v0.3.0, runtime data kept.

WHAT WAS NEW IN v0.3.0-m2.3:
  - M2.3 Linux shell: tap "Linux Shell" on Home to enter the installed
    Alpine guest through proot — SAME PTY, SAME terminal, real Linux
    userland (RuntimeProcessLauncher + TerminalSessionManager
    .createLinuxSession; honest READY-only gate)
  - proot v5.1.107.92 (termux fork, GPL-2.0) + libtalloc 2.4.2 compiled
    from pinned source for all 4 ABIs (scripts/build_proot_m23.sh);
    bundled as libproot.so / libproot-loader.so / libtalloc.so via jniLibs

WHAT WAS NEW IN v0.2.x:
  - M2.2 runtime installation layer: RuntimeState machine,
    RuntimeInstaller (HTTPS download -> size + SHA-256 verify -> guarded
    tar.gz extraction -> configure -> atomic promotion), RuntimeManager,
    RuntimeChecksum, RuntimeStorage, RuntimeDiagnostics, RuntimePin
    (Alpine 3.24.1 aarch64 minirootfs); Diagnostics install section
  - v0.2.1 FIX: tapping "Install Linux environment" crashed the app
    (missing INTERNET permission + uncontained coroutine failure).
    Now declared honestly and RuntimeCrashGuard contains any pipeline
    failure as a retryable FAILED/REPAIR_REQUIRED state

NOTE: the on-device gate for M2.3 is `uname; id; echo hello` inside the
guest (docs/TESTING.md §8) — the M2.4 package gate is TESTING.md §9 (install nano via the UI,
open it, uninstall).

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

HOW TO RESTORE .gitignore (excluded from this archive):

  After cloning from the bundle:
        cd pocketshell
        git checkout .gitignore

BUILDING THE APK:

  Android Studio: File > Open > select the cloned folder.
  Command line:   ./gradlew :app:assembleDebug
  (Requires JDK 17+ and Android SDK; the gradle wrapper downloads Gradle.)

Everything else in this archive is the plain, buildable working tree:
  app/  terminal-emulator/  terminal-view/  docs/  scripts/  gradle/
EOF

# --- 3. source tree: zero dotfiles, zero web scaffold, zero build outputs ---
TREE=$STAGE/$TOPDIR
mkdir -p "$TREE"
tar --exclude='./.git' --exclude='./.gitignore' --exclude='./.gitattributes' \
    --exclude='./.gradle' --exclude='./.kotlin' --exclude='./.next' \
    --exclude='./.zscripts' --exclude='./.idea' \
    --exclude='.*/' --exclude='./_*' \
    --exclude='./node_modules' --exclude='*/node_modules' \
    --exclude='./*/build' --exclude='./build' \
    --exclude='./src' --exclude='./public' --exclude='./prisma' --exclude='./db' \
    --exclude='./package.json' --exclude='./bun.lock' --exclude='./bun.lockb' \
    --exclude='./tsconfig.json' --exclude='./next.config.ts' \
    --exclude='./postcss.config.mjs' --exclude='./components.json' \
    --exclude='./eslint.config.mjs' --exclude='./next-env.d.ts' \
    --exclude='./tailwind.config.ts' --exclude='./Caddyfile' \
    --exclude='./dev.log' --exclude='./server.log' \
    --exclude='./skills' --exclude='./upload' --exclude='./download' \
    --exclude='./scratch' \
    --exclude='./dist-master' --exclude='./examples' --exclude='./mini-services' \
    --exclude='./tests' \
    --exclude='./.env' --exclude='./local.properties' \
    --exclude='./*.zip' --exclude='./*.tar.gz' --exclude='./*.bundle' \
    --exclude='./app/page.tsx' --exclude='./app/layout.tsx' \
    -cf - . | tar -xf - -C "$TREE"
cp "$BUNDLE" "$TREE/pocketshell-m2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"

# --- 4. publish zip + tar.gz twin + bundle + apk ---
ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rq "$ZIP" "$TOPDIR")
tar -czf "$TGZ" -C "$STAGE" "$TOPDIR"
cp "$BUNDLE" "$PUBLIC/pocketshell-m2.gitbundle"
cp "$PROJECT/download/PocketShell-$VERSION-debug.apk" "$PUBLIC/"

# backup masters (survive download/ drains and public/ churn)
cp "$ZIP" "$TGZ" "$BUNDLE" "$DIST/"

# --- 5. sanity checks ---
echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | rg -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | rg -c '/app/page\.tsx$' || echo 0)  (want 0)"
echo "real node_modules dirs : $(echo "$LIST" | rg -c '/node_modules/' || echo 0)  (want 0)"
for key in docs/M2-RESEARCH.md docs/M2-ARCHITECTURE.md \
           app/src/main/java/app/pocketshell/runtime/RuntimeInstaller.kt \
           app/src/main/java/app/pocketshell/runtime/RuntimeManager.kt \
           pocketshell-m2.gitbundle RESTORE.txt gradlew \
           gradle/libs.versions.toml docs/THIRD_PARTY.md; do
  echo "$LIST" | rg -q "$key\$" && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done
FILES=$(echo "$LIST" | awk '/files$/ { print $1 }')
echo "uncompressed bytes     : $FILES  (files: $(echo "$LIST" | awk '/files$/ { print $2 }'))"

# --- 6. delivery summary ---
echo "== sha256 (paste into download page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m2.gitbundle" \
          "$PUBLIC/PocketShell-$VERSION-debug.apk" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
