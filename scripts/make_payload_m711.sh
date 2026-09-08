#!/bin/bash
# make_payload_m711.sh — cut the M7.1.1 source payload from the fix tip
# (3cbfec2, "m7.1.1: external keyboard detection fix — vc47 /
# 0.11.2-m7.1.1"; the M7.1 release chain rides below), adapting
# make_payload_m71_release.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged from app/build/outputs as
# PocketShell-v0.11.2-m7.1.1-debug.apk (built at this tip; vc47 /
# 0.11.2-m7.1.1). The glibc layer artifact is carried forward
# byte-identically (rev=2, unchanged by M7.1.1).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.2-m7.1.1
TOPDIR=PocketShell-$VERSION
PHASE_TIP=3cbfec2
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ fix git tip $TIP =="

APK_SRC=$PROJECT/app/build/outputs/apk/debug/app-debug.apk
APK=$PUBLIC/PocketShell-$VERSION-debug.apk
[[ -f "$APK_SRC" ]] || { echo "APK missing: $APK_SRC (build first)"; exit 1; }

BUNDLE=$STAGE/pocketshell-m7.1.1.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1.1 FIX RELEASE, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through the
M7.1.1 fix) is preserved inside the single file:
  pocketshell-m7.1.1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1.1.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT THE M7.1.1 FIX RELEASE IS (this snapshot):
  - The M7.1 P3 external-keyboard detection fix, driven by the real-device
    failure on the Samsung SM-F711B (Galaxy Z Flip 3, One UI). The audit
    findings and the fix:
      1. The terminal-canvas tap cancelled the auto-hide (the primary
         defect) — the gate now lives in the authoritative model.
      2. Debounce starvation — a hard 2,000 ms confirm deadline now
         guarantees the scan under any event storm.
      3. A single detection mechanism — the Application-level
         configuration-change cross-check now feeds the same detector.
      4. The silent disconnect — BOTH directions emit exactly one notice.
      5. No persistent preference — the DataStore
         onscreen_keyboard_enabled setting is the user's preference;
         detection is a temporary runtime override that never writes it.
  - ONE authoritative state system: ExternalKeyboardVisibilityModel
    (userEnabled + externalConnected + manualRequest →
    shouldShowOnscreenKeyboard); the root observes it and holds no local
    copy.
  - FIX RELEASE STAMP: versionCode 47 / versionName 0.11.2-m7.1.1
    (the sub-milestone precedent). Installs in place over every earlier
    pinned-cert build (cert d96a6f66…8bf659); the 6-permission set and
    the M6 frozen architecture unchanged.
  - VERIFICATION: FULL JVM suite forced --rerun-tasks 746/746
    (app 601 + terminal-emulator 145, 0 failures / 0 errors) at this
    stamp; fresh APK audits green (badging, 6 permissions, 26 launcher
    icon entries, dex symbols, cert).
  - Docs: docs/TESTING.md §47 = the M7.1.1 audit findings + the
    16-step MANDATORY real-device gate (USB/BT, settings matrix,
    canvas-tap regression); §46 = the M7.1 release QA; CHANGELOG
    [0.11.2-m7.1.1]; ROADMAP carries the M7.1.1 section.
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
cp "$BUNDLE" "$TREE/pocketshell-m7.1.1.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1.1.gitbundle"
cp "$APK_SRC" "$APK"

# The glibc layer artifact: UNCHANGED by M7.1.1 — carry the pinned bytes forward.
GLIBC=public/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
GLIBC_PIN=ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d
[[ "$(sha256sum "$GLIBC" | cut -d' ' -f1)" == "$GLIBC_PIN" ]] \
  || { echo "glibc artifact drifted from the pin — refusing"; exit 1; }
echo "glibc artifact: byte-identical to the pinned rev=2 bytes"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1.1.gitbundle" "$APK" "$DL/"

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
POLICY=$(echo "$LIST" | grep -c 'ExternalKeyboardPolicyTest' || true); POLICY=${POLICY:-0}
echo "retired PolicyTest     : $POLICY  (want 0)"
VIS=$(echo "$LIST" | grep -c 'ExternalKeyboardVisibilityModelTest' || true)
echo "VisibilityModelTest    : $VIS  (want >= 1)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           runtime-tests/device_gate.sh \
           app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt \
           app/src/main/java/app/pocketshell/ExternalKeyboardViewModel.kt \
           app/src/main/java/app/pocketshell/ui/system/ExternalKeyboardNoticeBar.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardPredicateTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardDetectorTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardVisibilityModelTest.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardIntegrationTest.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherBundledIcons.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt \
           app/src/test/java/app/pocketshell/launchers/HomeLauncherRowsTest.kt \
           app/src/main/assets/launcher_icons/hermes-light.webp \
           app/src/main/assets/launcher_icons/builtin-chatgpt-light.webp \
           scripts/make_launcher_icons.py \
           scripts/icon_sources/builtin-zai.svg \
           docs/TESTING.md docs/CHANGELOG.md docs/ROADMAP.md docs/THIRD_PARTY.md \
           RESTORE.txt pocketshell-m7.1.1.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== apk =="
sha256sum "$APK" | while read -r h f; do
  printf '%s  %s  (%s bytes)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
/home/z/android-sdk/build-tools/36.0.0/aapt2 dump badging "$APK" 2>/dev/null \
  | grep -E "^package:|uses-permission" || true

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1.1.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$APK" "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1.1.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
