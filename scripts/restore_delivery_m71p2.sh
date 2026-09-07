#!/bin/bash
# restore_delivery_m71p2.sh — restore the M7.1 P2 delivery set after the sandbox reset
# that wiped public/ and the download/ binaries.  The upload/ insurance copies hold the
# EXACT delivered APK + git bundle bytes; the delivered source archives embedded that
# same bundle file (the cutter copies one bundle into tree + public), so rebuilding the
# archives around the DELIVERED bundle bytes can reproduce the delivered zip/tgz
# byte-identically (git archive at the pinned tip + zeroed mtimes + sorted tgz + gzip -n).
# Every restored artifact is verified against the pins on the delivery page (app/page.tsx
# HASHES) — any mismatch is reported loudly, never silently accepted.
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
UPLOAD=$PROJECT/upload
VERSION=v0.11.0-m7.0.0-m7p2
TOPDIR=PocketShell-$VERSION
PHASE_TIP=1b15bde
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

# ---- delivered pins (app/page.tsx HASHES) ----
PIN_APK=ae6f64458c18d9cffd380596a9c4525dc87db2e9c79b2813b413dff71f09c74f
PIN_ZIP=10e459c2bd4a609d49062ee4cba16a669b0c2c38afea696956e22805a41eeaac
PIN_TGZ=d694fafb528e9f2471a7cdad8c009248ca6321923bbbe45700cedcfbb2ffde20
PIN_BUNDLE=3e1dcaafbf2bb67566b32f47c991033524d6513fdc2088a75cd3ba287dadf0ee
PIN_GLIBC=ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d

UP_APK=$UPLOAD/PocketShell-$VERSION-debug.apk
UP_BUNDLE=$UPLOAD/pocketshell-m7.1-p2.gitbundle

cd "$PROJECT"
echo "== 1. verify upload/ insurance bytes =="
sha_upload_apk=$(sha256sum "$UP_APK" | cut -d' ' -f1)
sha_upload_bundle=$(sha256sum "$UP_BUNDLE" | cut -d' ' -f1)
[[ "$sha_upload_apk" == "$PIN_APK" ]] && echo "APK    upload copy == pin  OK" || { echo "FATAL: upload APK sha $sha_upload_apk != pin"; exit 1; }
[[ "$sha_upload_bundle" == "$PIN_BUNDLE" ]] && echo "BUNDLE upload copy == pin  OK" || { echo "FATAL: upload bundle sha $sha_upload_bundle != pin"; exit 1; }

echo "== 2. stage public/ + download/ dirs =="
mkdir -p "$PUBLIC" "$DL"

echo "== 3. place APK + bundle (byte-identical from upload/) =="
cp "$UP_APK" "$PUBLIC/PocketShell-$VERSION-debug.apk"
cp "$UP_APK" "$DL/PocketShell-$VERSION-debug.apk"
cp "$UP_BUNDLE" "$PUBLIC/pocketshell-m7.1-p2.gitbundle"
cp "$UP_BUNDLE" "$DL/pocketshell-m7.1-p2.gitbundle"

echo "== 4. rebuild source archives around the DELIVERED bundle bytes =="
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "   phase tip: $TIP"
TREE=$STAGE/$TOPDIR
mkdir -p "$TREE"
git archive --format=tar "$TIP_FULL" \
  | tar -xf - -C "$TREE" \
      --exclude='app/page.tsx' --exclude='app/layout.tsx' \
      --exclude='.env' --exclude='.gitignore' --exclude='.kotlin' \
      --exclude='delivery' \
      --exclude='.initial_snapshot.json' --exclude='dev-server-*.log' \
      --exclude='dev.log'
cp "$UP_BUNDLE" "$TREE/pocketshell-m7.1-p2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt" 2>/dev/null || true
cat > "$TREE/RESTORE.txt" <<EOF
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
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"

echo "== 5. glibc layer artifact (byte-identical from the in-tree asset) =="
cp "$PROJECT/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" \
   "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"
cp "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" \
   "$DL/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"

echo "== 6. delivery masters into download/ =="
cp "$ZIP" "$TGZ" "$DL/"

echo "== 7. VERIFY every artifact against the page pins =="
fail=0
check() { # $1 name, $2 path, $3 pin
  local sha size
  sha=$(sha256sum "$2" | cut -d' ' -f1)
  size=$(stat -c%s "$2")
  if [[ "$sha" == "$3" ]]; then
    printf 'PASS  %-14s %12d B  sha %s…\n' "$1" "$size" "${sha:0:8}"
  else
    printf 'FAIL  %-14s %12d B  sha %s…  (pin %s…)\n' "$1" "$size" "${sha:0:8}" "${3:0:8}"
    fail=1
  fi
}
check apk    "$PUBLIC/PocketShell-$VERSION-debug.apk"        "$PIN_APK"
check zip    "$ZIP"                                          "$PIN_ZIP"
check tgz    "$TGZ"                                          "$PIN_TGZ"
check bundle "$PUBLIC/pocketshell-m7.1-p2.gitbundle"         "$PIN_BUNDLE"
check glibc  "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" "$PIN_GLIBC"

echo "== 8. archive sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
DOTS=$(unzip -l "$ZIP" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries: $DOTS  (want 0)"
GEMINI=$(unzip -l "$ZIP" | grep -ci 'gemini' || true); GEMINI=${GEMINI:-0}
echo "stale gemini paths: $GEMINI  (want 0)"
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p2.gitbundle"

if [[ $fail -ne 0 ]]; then
  echo "RESULT: MISMATCH — at least one artifact does not reproduce the delivered bytes."
  exit 2
fi
echo "RESULT: ALL 5 ARTIFACTS BYTE-IDENTICAL TO THE DELIVERED PINS"
