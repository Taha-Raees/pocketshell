#!/bin/bash
# Build the M2.2 delivery payload for the Next.js download page:
#   - full git bundle at current tip (complete milestone history)
#   - zero-dotfile buildable source tree zip + tar.gz twin
#     (web scaffold + app/page.tsx + app/layout.tsx shims excluded)
#   - copies of the M2.2 APK
# Outputs land in public/ (served by the web app) with a backup in dist-master/.
set -euo pipefail

PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DIST=$PROJECT/dist-master
VERSION=v0.2.1-m2.2
TOPDIR=PocketShell-$VERSION
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP=$(git rev-parse --short HEAD)
echo "== payload $VERSION @ git tip $TIP =="

mkdir -p "$DIST"

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

WHAT IS NEW IN $VERSION (vs v0.1.1-m1):
  - M2.2 runtime installation layer: RuntimeState machine,
    RuntimeInstaller (HTTPS download -> size + SHA-256 verify -> guarded
    tar.gz extraction -> configure -> atomic promotion), RuntimeManager,
    RuntimeChecksum, RuntimeStorage, RuntimeDiagnostics, RuntimePin
    (Alpine 3.24.1 aarch64 minirootfs); Diagnostics install section
  - v0.2.1 FIX: tapping "Install Linux environment" crashed the app
    (missing INTERNET permission + uncontained coroutine failure).
    Now declared honestly and RuntimeCrashGuard contains any pipeline
    failure as a retryable FAILED/REPAIR_REQUIRED state
  - 6 crash-guard regression tests (58 app-module tests total);
    commons-compress 1.28.0 (Apache-2.0); docs/M2-RESEARCH.md +
    docs/M2-ARCHITECTURE.md

NOTE: this snapshot installs the Linux userland files only. The Linux
shell session inside the terminal (proot launch, M2.3) is NOT part of
this snapshot yet.

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
