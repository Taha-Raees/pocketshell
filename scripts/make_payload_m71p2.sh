#!/bin/bash
# make_payload_m71p2.sh — cut the M7.1 P2 source payload from the phase tip
# (1b15bde, "m7.1 p2: launcher UI repair + official icons + Antigravity…";
# the P1 launcher phase 3abb2e8 and the M7.0 release chain ride below),
# adapting make_payload_m71p1.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE: the embedded git bundle's pack bytes are NOT
#   stable across re-cuts (pack generation), and the bundle is embedded in
#   the archives — so re-running this script yields different bytes. The
#   sha pins on the page/README refer to the ONE delivered cut; re-cutting
#   requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged separately as
# PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (built from this tip; the
# version stamp stays vc45 / 0.11.0-m7.0.0 — P2 does not bump the version);
# the glibc layer artifact (ed82daa8…, rev=2) re-served byte-identical.
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0-m7p2
TOPDIR=PocketShell-$VERSION
PHASE_TIP=1b15bde
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7.1-p2.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1 PHASE 2, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.1 P2) is preserved inside the single file:
  pocketshell-m7.1-p2.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1-p2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.1 PHASE 2 IS (this snapshot, on top of M7.1 P1):
  - Launcher UI repair (the device-reported screenshot bug, ROOT-CAUSED):
    the deleted-seed restore row collapsed its text one character per line
    because MidnightQuietButton hard-fills its width from the inside (a
    PAGE-level component) and, placed in an unweighted Row slot, starved
    the weighted text column to zero. ALL built-in launcher settings rows
    now render through ONE shared LauncherSettingRow (fixed icon ->
    weight(1f) text column -> optional intrinsically-sized action ->
    optional fixed control); Restore is a compact text action; long
    URLs/commands wrap naturally instead of clipping; the deleted-seed
    badge shares one collision space with existing definitions.
  - Official icons, bundled offline: 14 curated launcher marks (ChatGPT,
    Claude, Z.ai, GitHub; Hermes, OpenCode, Claude Code, ZCode, Kilo Code,
    Cline, Antigravity, Codex, Aider, Qwen Code) live in
    app/src/main/assets/launcher_icons/*.webp (192x192 lossless, ~125 KB
    total), produced at build time by scripts/make_launcher_icons.py from
    OFFICIAL first-party origins (URLs documented in the script), zero
    runtime network. Resolution: user's imported copy -> bundled mark ->
    deterministic badge; every failure degrades to the badge.
  - Antigravity REPLACES Gemini CLI in the curated default launcher set:
    the command is the official binary name 'agy' (Google's own installer,
    docs/ANTIGRAVITY-PLATFORM.md §1); the honest verify-then-launch probe
    stays the only availability claim. No stale 'gemini' default anywhere;
    a P1-era persisted 'gemini' hide id is inert by construction.
  - The P1 launcher model (companion + CLI tool grids, hide/restore,
    custom tools through the ONE guestCustomCommandChain path, copied
    icons) and the M7.0 release features (P1-P9) all ride below this tip
    — see git history and docs/TESTING.md sections 35-42.
  - versionCode 45 / versionName 0.11.0-m7.0.0 (P2 does NOT bump the
    version — the P1 precedent stands); the 6-permission set and the M6
    frozen architecture unchanged; embedded rev=2 glibc layer asset sha
    898131ff… / 17,920,000 B.
  - JVM suite 690/690 effective green (app 545 + terminal-emulator 145,
    forced --rerun-tasks; the release variant mirrors the 145).
  - Docs: docs/TESTING.md §42 = the M7.1 P2 device gate (16 steps).

BUILDING THE APK:

  Android Studio: File > Open > select the cloned folder.
  Command line:   ./gradlew :app:assembleDebug
  (Requires JDK 17+ and Android SDK; the gradle wrapper downloads Gradle.)

Everything else in this archive is the plain, buildable working tree:
  app/  terminal-emulator/  terminal-view/  docs/  scripts/  runtime-tests/
EOF

TREE=$STAGE/$TOPDIR
mkdir -p "$TREE"
git archive --format=tar "$TIP_FULL" \
  | tar -xf - -C "$TREE" \
      --exclude='app/page.tsx' --exclude='app/layout.tsx' \
      --exclude='.env' --exclude='.gitignore' --exclude='.kotlin' \
      --exclude='delivery' \
      --exclude='.initial_snapshot.json' --exclude='dev-server-*.log' \
      --exclude='dev.log'
cp "$BUNDLE" "$TREE/pocketshell-m7.1-p2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1-p2.gitbundle"

# glibc layer artifact: byte-identical re-serve from the in-tree asset
cp "$PROJECT/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" \
   "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.gitbundle" "$DL/"

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | grep -c '/app/page\.tsx$' || echo 0)  (want 0)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/launchers/LauncherBundledIcons.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherTileIcon.kt \
           app/src/test/java/app/pocketshell/launchers/LauncherRowLayoutTest.kt \
           app/src/test/java/app/pocketshell/launchers/LauncherBundledIconsTest.kt \
           scripts/make_launcher_icons.py \
           app/src/main/assets/launcher_icons/agy.webp \
           app/src/main/assets/launcher_icons/builtin-github.webp \
           app/src/main/assets/launcher_icons/codex.webp \
           app/src/main/java/app/pocketshell/launchers/LauncherModels.kt \
           app/src/main/java/app/pocketshell/ui/settings/LauncherSettingsScreen.kt \
           app/src/test/java/app/pocketshell/launchers/LauncherModelsTest.kt \
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/TESTING.md docs/THIRD_PARTY.md RESTORE.txt pocketshell-m7.1-p2.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done
GEMINI=$(echo "$LIST" | grep -ci 'gemini' || true); GEMINI=${GEMINI:-0}
echo "stale gemini paths in archive: $GEMINI (want 0)"

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p2.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.gitbundle" "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
