#!/bin/bash
# Build multiple distribution variants of the PocketShell project source.
# Purpose: the files panel never synced archives containing .git/ — ship
# several differently-shaped variants in one round to find one that passes.
set -euo pipefail

PROJECT=/home/z/my-project
OUT=$PROJECT/download
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

BUNDLE=$STAGE/pocketshell-m1.gitbundle
RESTORE=$STAGE/RESTORE.txt
cp /tmp/pocketshell-m1.gitbundle "$BUNDLE"

cat > "$RESTORE" << 'EOF'
PocketShell - source snapshot (v0.1.1-m1, M1.2-input-fix, git tip 09fc0db)
===========================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints: initial -> M0
-> M1 -> M1.1 -> M1.2 -> M1.3 -> M1.2-input-fix) is preserved inside the
single file:  pocketshell-m1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m1.gitbundle pocketshell
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

# --- Variant A: "lite" zip — no dotfiles at all, history via bundle ---
LITE=$STAGE/pocketshell-src-lite-m1
mkdir -p "$LITE"
# copy working tree excluding dotfiles, build outputs, delivery dirs
cd "$PROJECT"
tar --exclude='./.git' --exclude='./.gitignore' --exclude='./.gitattributes' \
    --exclude='./node_modules' --exclude='*/node_modules' \
    --exclude='./*/build' --exclude='./build' \
    --exclude='.*/' --exclude='./_*' \
    --exclude='./skills' --exclude='./upload' --exclude='./download' \
    --exclude='./.env' --exclude='./local.properties' \
    --exclude='./*.zip' --exclude='./*.tar.gz' --exclude='./*.bundle' \
    -cf - . | tar -xf - -C "$LITE"
cp "$BUNDLE" "$LITE/pocketshell-m1.gitbundle"
cp "$RESTORE" "$LITE/RESTORE.txt"
cd "$STAGE" && zip -rq "$OUT/pocketshell-src-lite-m1.zip" pocketshell-src-lite-m1
tar -czf "$OUT/pocketshell-src-lite-m1.tar.gz" pocketshell-src-lite-m1

# --- Variant B: full original archive (tree + .git/) under a NEW name ---
cp "$PROJECT/download/PocketShell-project-v0.1.1-m1.zip" "$OUT/pocketshell-full-m1-v2.zip"

echo "=== sanity checks ==="
unzip -t "$OUT/pocketshell-src-lite-m1.zip" | tail -1
tar -tzf "$OUT/pocketshell-src-lite-m1.tar.gz" | head -3
unzip -l "$OUT/pocketshell-src-lite-m1.zip" | rg -c '\.git/' || echo "lite zip: 0 dot-git entries (good)"
ls -la "$OUT"
