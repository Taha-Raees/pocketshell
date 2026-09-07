#!/bin/bash
# make_payload_m71p3.sh — cut the M7.1 P3 source payload from the phase tip
# (93ee631, "m7.1 p3: live external-keyboard detection + automatic on-screen
# keyboard control"; the P2.x launcher/icon chain rides below), adapting
# make_payload_m71p22.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged from app/build/outputs as
# PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (built from this tip; the
# version stamp stays vc45 / 0.11.0-m7.0.0 — P3 does not bump the version).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0-m7p3
TOPDIR=PocketShell-$VERSION
PHASE_TIP=93ee631
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase git tip $TIP =="

APK_SRC=$PROJECT/app/build/outputs/apk/debug/app-debug.apk
APK=$PUBLIC/PocketShell-$VERSION-debug.apk
[[ -f "$APK_SRC" ]] || { echo "APK missing: $APK_SRC (build first)"; exit 1; }

BUNDLE=$STAGE/pocketshell-m7.1-p3.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1 PHASE 3, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.1 P3) is preserved inside the single file:
  pocketshell-m7.1-p3.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1-p3.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.1 PHASE 3 IS (this snapshot, on top of M7.1 P2.2):
  - LIVE EXTERNAL-KEYBOARD DETECTION: event-driven, no polling, no new
    permissions. InputManager.InputDeviceListener -> a 400 ms stability
    window (duplicate connect bursts and Bluetooth flaps coalesce; a
    sub-window flap never flickers the state) -> one fresh device scan
    -> at most ONE state transition. A launch scan covers "app started
    with the keyboard already attached"; an ON_RESUME rescan covers
    backgrounded connects/disconnects. The predicate requires a real
    alphabetic hardware keyboard (SOURCE_KEYBOARD + KEYBOARD_TYPE_
    ALPHABETIC + non-virtual) — touchscreens, mice, gamepads, stylus
    pointers, and button clusters never trigger it.
  - AUTOMATIC ON-SCREEN KEYBOARD CONTROL: when an external keyboard
    connects, the shared PocketShell deck hides itself and the hardware
    keyboard types straight into the terminal/WebView; when it
    disconnects, the deck returns exactly as the user left it — no
    restart, no replug. The root stays the ONE visibility owner: the
    suppression overlays the user's manual state (preExternalExpanded)
    and is CANCELLED by an explicit user reopen (deck toggle, floating
    keyboard icon, terminal tap). keyboardBottomInset recalculates on
    every screen through the existing deck mount/unmount path.
  - SETTINGS: "On-screen keyboard — automatically hide when an external
    keyboard is connected" (DataStore, default ON, immediately
    effective — flipping it mid-connection suppresses/restores at once;
    OFF keeps the deck fully manual).
  - NOTICE: ONE transient in-app banner per real connect transition
    ("External keyboard detected — the on-screen keyboard has been
    turned off. You can change this in Settings."), auto-dismisses in
    ~4.5 s, tappable to Settings. No notification permissions, no
    channel, no spam on duplicate events or resume.
  - Everything from M7.1 P2.2 rides below this tip (theme-scheme icon
    variant pairs, x-scroll home rows with scroll dots, the packages
    affordance) and P2.1/P2/P1 below that — see git history and
    docs/TESTING.md 35-45.
  - versionCode 45 / versionName 0.11.0-m7.0.0 (P3 does NOT bump the
    version — the P1/P2/P2.x precedent stands); the 6-permission set
    and the M6 frozen architecture unchanged.
  - JVM suite 734/734 effective green (app 589 + terminal-emulator 145,
    forced --rerun-tasks), including the four new P3 suites (device
    predicate, virtual-time detector transitions, suppression policy,
    structural integration contract).
  - Docs: docs/TESTING.md §45 = the M7.1 P3 device gate (16 steps).

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
cp "$BUNDLE" "$TREE/pocketshell-m7.1-p3.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1-p3.gitbundle"
cp "$APK_SRC" "$APK"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p3.gitbundle" "$APK" "$DL/"

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
           docs/TESTING.md docs/THIRD_PARTY.md RESTORE.txt \
           pocketshell-m7.1-p3.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== apk =="
sha256sum "$APK" | while read -r h f; do
  printf '%s  %s  (%s bytes)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
$PROJECT/../android-sdk/build-tools/36.0.0/aapt2 dump badging "$APK" 2>/dev/null \
  | grep -E "^package:|uses-permission" || true

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p3.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$APK" "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p3.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
