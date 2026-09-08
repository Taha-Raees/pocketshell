#!/bin/bash
# make_payload_m71_release.sh — cut the OFFICIAL M7.1 RELEASE source payload
# from the release tip (4e86e50, "m7.1 release: official M7.1 stamp — vc46 /
# 0.11.1-m7.1.0"; the P3 + P2.x + P1 chain rides below), adapting
# make_payload_m71p3.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged from app/build/outputs as
# PocketShell-v0.11.1-m7.1.0-debug.apk (built at this tip; the official
# M7.1 release stamp vc46 / 0.11.1-m7.1.0).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.1-m7.1.0
TOPDIR=PocketShell-$VERSION
PHASE_TIP=4e86e50
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ release git tip $TIP =="

APK_SRC=$PROJECT/app/build/outputs/apk/debug/app-debug.apk
APK=$PUBLIC/PocketShell-$VERSION-debug.apk
[[ -f "$APK_SRC" ]] || { echo "APK missing: $APK_SRC (build first)"; exit 1; }

BUNDLE=$STAGE/pocketshell-m7.1.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, OFFICIAL M7.1 RELEASE, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through the
M7.1 release) is preserved inside the single file:
  pocketshell-m7.1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT THE OFFICIAL M7.1 RELEASE IS (this snapshot):
  - The M7.1 milestone, closed and frozen — five delivered phases:
      P1   Home launchers (Companions + Your tools grids, hide/restore,
           custom tools over the ONE verify-then-launch path, badges).
      P2   Launcher UI repair at the row-shape root cause, bundled
           official marks, Antigravity (agy) replacing Gemini CLI.
      P2.1 Aider removed with no stale trace; nine marks re-rendered
           from owner-supplied official SVGs (in-tree, offline).
      P2.2 Two-variant theme marks ({id}.webp Midnight / {id}-light.webp
           Daylight — 26 assets, live theme flips), Companions one
           x-scroll row + tools two rows with scroll dots, the packages
           affordance as the tools-header Manage action.
      P3   Live external-keyboard detection: event-driven (no polling,
           no new permissions) with a 400 ms stability window; real
           alphabetic hardware keyboards only; the on-screen deck hides
           itself on connect and returns on disconnect; the user's manual
           state is never destroyed; Settings "On-screen keyboard" toggle
           (default ON); ONE transient in-app notice per real transition.
  - OFFICIAL RELEASE STAMP: versionCode 46 / versionName 0.11.1-m7.1.0
    (the M5.1.0 sub-milestone precedent). Installs in place over every
    earlier pinned-cert build (cert d96a6f66…8bf659); the 6-permission
    set and the M6 frozen architecture unchanged.
  - RELEASE VERIFICATION: FULL JVM suite forced --rerun-tasks 734/734
    effective green (app 589 + terminal-emulator 145, 0 failures /
    0 errors) at this stamp; fresh APK audits green (badging, 6
    permissions, 26 launcher icon entries, dex symbols, cert).
  - Docs: docs/TESTING.md §46 = the M7.1 release QA record; §41-§45 are
    the phase device gates (the standing hardware pass); CHANGELOG
    [0.11.1-m7.1.0] records the release; ROADMAP marks M7.1 frozen.
  - M7.2 (notification & agent-activity system) is NOT started here.

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
cp "$BUNDLE" "$TREE/pocketshell-m7.1.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1.gitbundle"
cp "$APK_SRC" "$APK"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1.gitbundle" "$APK" "$DL/"

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
           runtime-tests/device_gate.sh \
           app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt \
           app/src/main/java/app/pocketshell/ExternalKeyboardViewModel.kt \
           app/src/main/java/app/pocketshell/ui/system/ExternalKeyboardNoticeBar.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardPredicateTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardDetectorTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardPolicyTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardIntegrationTest.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherBundledIcons.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt \
           app/src/test/java/app/pocketshell/launchers/HomeLauncherRowsTest.kt \
           app/src/main/assets/launcher_icons/hermes-light.webp \
           app/src/main/assets/launcher_icons/builtin-chatgpt-light.webp \
           scripts/make_launcher_icons.py \
           scripts/icon_sources/builtin-zai.svg \
           docs/TESTING.md docs/CHANGELOG.md docs/ROADMAP.md docs/THIRD_PARTY.md \
           RESTORE.txt pocketshell-m7.1.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== apk =="
sha256sum "$APK" | while read -r h f; do
  printf '%s  %s  (%s bytes)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
/home/z/android-sdk/build-tools/36.0.0/aapt2 dump badging "$APK" 2>/dev/null \
  | grep -E "^package:|uses-permission" || true

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$APK" "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
