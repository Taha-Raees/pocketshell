#!/bin/bash
# make_payload_m71p21.sh — cut the M7.1 P2.1 source payload from the phase tip
# (6004805, "m7.1 p2.1: Aider removed from the curated launcher set + nine
# owner-supplied official brand marks"; the P2 UI-repair/icon/Antigravity
# phase 1b15bde and the P1/M7.0 chains ride below), adapting make_payload_m71p2.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged separately as
# PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (built from this tip; the
# version stamp stays vc45 / 0.11.0-m7.0.0 — P2.2 does not bump the version).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0-m7p2.2
TOPDIR=PocketShell-$VERSION
PHASE_TIP=6004805
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7.1-p2.2.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1 PHASE 2.2, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.1 P2.2) is preserved inside the single file:
  pocketshell-m7.1-p2.2.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1-p2.2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.1 PHASE 2.2 IS (this snapshot, on top of M7.1 P2.1):
  - THEME-SCHEME ICON VARIANTS: every curated mark ships TWO variants
    ({id}.webp Midnight/dark, {id}-light.webp Daylight/light), rendered
    by the pipeline on the app's OWN plate tones (TerminalTheme.keyAlt
    #16233F dark / #EDF1F7 light — the same surface the badge tiles
    paint). Monochrome glyphs invert per theme (Hermes mascot, Kilo
    letters, Cline robot white-on-dark — the black marks vanished on
    Midnight; ChatGPT knot, GitHub octocat, Codex flower adapt with
    their own brand inks); self-contained brand tiles (Z.ai, OpenCode)
    and colored transparent marks (Claude terracotta, Antigravity,
    Claude Code, Qwen) are theme-proof. LauncherTileIcon resolves the
    variant from TerminalTheme.isLight and re-resolves on a live theme
    flip; imported copies outrank everything; zero runtime network.
  - X-SCROLL HOME ROWS: Companions in ONE horizontal row, Your tools in
    TWO (row-major reading order preserved), fixed entry width, shared
    LauncherScroller + ScrollDots (the accent pill tracks the visible
    page; dots hide on a single page).
  - PACKAGES AFFORDANCE: the mid-page footer link is retired — the
    "Your tools" header's Manage action opens the packages page (the
    divider above it is the requested alignment); Companions keep their
    launcher-settings Manage. Verify-then-launch honesty and hide-only
    removal unchanged.
  - Everything from M7.1 P2.1 rides below this tip (Aider removed, the
    nine owner-supplied marks, locals-first pipeline) and M7.1 P2 below
    that (LauncherSettingRow row repair, bundled offline icon layer,
    Antigravity/agy) — see git history and docs/TESTING.md 35-44.
  - versionCode 45 / versionName 0.11.0-m7.0.0 (P2.2 does NOT bump the
    version — the P1/P2/P2.1 precedent stands); the 6-permission set and
    the M6 frozen architecture unchanged.
  - JVM suite 696/696 effective green (app 551 + terminal-emulator 145,
    forced --rerun-tasks; the release variant mirrors the 145).
  - Docs: docs/TESTING.md §44 = the M7.1 P2.2 device gate (10 steps).

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
cp "$BUNDLE" "$TREE/pocketshell-m7.1-p2.2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1-p2.2.gitbundle"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.2.gitbundle" "$DL/"

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
AIDER=$(echo "$LIST" | grep -ci 'aider' || true); AIDER=${AIDER:-0}
echo "stale aider paths      : $AIDER  (want 0)"
GEMINI=$(echo "$LIST" | grep -ci 'gemini' || true); GEMINI=${GEMINI:-0}
echo "stale gemini paths     : $GEMINI  (want 0)"
ICONS=$(echo "$LIST" | grep -c 'launcher_icons/.*\.webp' || true)
echo "bundled icon assets    : $ICONS  (want 26 = 13 dark + 13 light)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/launchers/LauncherBundledIcons.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherTileIcon.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt \
           app/src/main/java/app/pocketshell/ui/theme/TerminalTheme.kt \
           app/src/test/java/app/pocketshell/launchers/HomeLauncherRowsTest.kt \
           app/src/main/assets/launcher_icons/hermes-light.webp \
           app/src/main/assets/launcher_icons/builtin-chatgpt-light.webp \
           scripts/make_launcher_icons.py \
           scripts/icon_sources/agy.svg \
           scripts/icon_sources/builtin-chatgpt.svg \
           scripts/icon_sources/builtin-claude.svg \
           scripts/icon_sources/builtin-github.svg \
           scripts/icon_sources/builtin-zai.svg \
           scripts/icon_sources/cline.svg \
           scripts/icon_sources/hermes.svg \
           scripts/icon_sources/kilo.svg \
           scripts/icon_sources/opencode.svg \
           app/src/main/assets/launcher_icons/agy.webp \
           app/src/main/assets/launcher_icons/codex.webp \
           app/src/main/assets/launcher_icons/qwen.webp \
           app/src/test/java/app/pocketshell/apps/CommandAppsTest.kt \
           app/src/test/java/app/pocketshell/launchers/LauncherBundledIconsTest.kt \
           docs/TESTING.md docs/THIRD_PARTY.md RESTORE.txt \
           pocketshell-m7.1-p2.2.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p2.2.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.2.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
