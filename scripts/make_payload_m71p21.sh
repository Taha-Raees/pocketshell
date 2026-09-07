#!/bin/bash
# make_payload_m71p21.sh — cut the M7.1 P2.1 source payload from the phase tip
# (e0a2471, "m7.1 p2.1: Aider removed from the curated launcher set + nine
# owner-supplied official brand marks"; the P2 UI-repair/icon/Antigravity
# phase 1b15bde and the P1/M7.0 chains ride below), adapting make_payload_m71p2.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged separately as
# PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (built from this tip; the
# version stamp stays vc45 / 0.11.0-m7.0.0 — P2.1 does not bump the version).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0-m7p2.1
TOPDIR=PocketShell-$VERSION
PHASE_TIP=e0a2471
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7.1-p2.1.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1 PHASE 2.1, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.1 P2.1) is preserved inside the single file:
  pocketshell-m7.1-p2.1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1-p2.1.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.1 PHASE 2.1 IS (this snapshot, on top of M7.1 P2):
  - Aider REMOVED from the curated default CLI launcher set: registry,
    ids, commands, display names, and the bundled-asset mapping, with no
    stale trace anywhere (the Gemini CLI removal pattern, pinned by a
    dedicated test; a stale persisted 'aider' hide id is inert).
  - Nine official brand marks re-rendered from OWNER-SUPPLIED official
    brand SVGs vendored in-tree (scripts/icon_sources/*.svg, zonalogo.com
    mirrors): ChatGPT (white knot on the OpenAI-black tile), Claude
    (terracotta starburst), Z.ai (Z tile), GitHub (white octocat on the
    GitHub-dark tile), Hermes Agent (mascot on white plate), OpenCode
    (its own dark tile glyph), Kilo Code (pixel letters on white plate —
    the vector ships NO fill), Cline (robot head on white plate),
    Antigravity (colored arc). The icon pipeline resolves LOCALS-FIRST:
    offline, byte-reproducible, zero build-time network for these nine.
    Claude Code / ZCode / Codex / Qwen Code keep their M7.1 P2 marks.
    Resolution order unchanged: user's imported copy -> bundled mark ->
    deterministic badge; zero runtime network.
  - Everything from M7.1 P2 rides below this tip: the launcher settings
    row repair (ONE shared LauncherSettingRow: fixed icon -> weight(1f)
    text column -> optional action -> optional control), the bundled
    offline icon layer, and Antigravity (agy) as the honest
    verify-then-launch launcher that replaced Gemini CLI — see git
    history and docs/TESTING.md sections 35-43.
  - versionCode 45 / versionName 0.11.0-m7.0.0 (P2.1 does NOT bump the
    version — the P1/P2 precedent stands); the 6-permission set and the
    M6 frozen architecture unchanged.
  - JVM suite 691/691 effective green (app 546 + terminal-emulator 145,
    forced --rerun-tasks; the release variant mirrors the 145).
  - Docs: docs/TESTING.md §43 = the M7.1 P2.1 device gate (5 steps).

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
cp "$BUNDLE" "$TREE/pocketshell-m7.1-p2.1.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1-p2.1.gitbundle"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.1.gitbundle" "$DL/"

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
echo "bundled icon assets    : $ICONS  (want 13)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/launchers/LauncherBundledIcons.kt \
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
           pocketshell-m7.1-p2.1.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p2.1.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p2.1.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
