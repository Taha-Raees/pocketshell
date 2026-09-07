#!/bin/bash
# make_payload_m71p1.sh — cut the M7.1 P1 source payload from the phase tip
# (3abb2e8, "m7.1 p1: home launchers…"; the M7.0 release chain
# 47bed42 → 709d126 → dd81bc8 rides below), adapting make_payload_m7p9.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE: the embedded git bundle's pack bytes are NOT
#   stable across re-cuts (pack generation), and the bundle is embedded in
#   the archives — so re-running this script yields different bytes. The
#   sha pins on the page/README refer to the ONE delivered cut; re-cutting
#   requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged separately as
# PocketShell-v0.11.0-m7.0.0-m7p1-debug.apk (built from this tip; the
# version stamp stays vc45 / 0.11.0-m7.0.0 — P1 does not bump the version);
# the glibc layer artifact (ed82daa8…, rev=2) re-served byte-identical.
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0-m7p1
TOPDIR=PocketShell-$VERSION
PHASE_TIP=3abb2e8
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse "$PHASE_TIP^{commit}")
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7.1-p1.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.1 PHASE 1, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.1 P1) is preserved inside the single file:
  pocketshell-m7.1-p1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.1-p1.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.1 PHASE 1 IS (this snapshot, on top of the M7.0 release):
  - Home launchers — ONE design rule: a launcher is not an app.
    * Companions on Home: the four preinstalled companion websites
      (ChatGPT, Claude, Z.ai, GitHub) seeded ONCE through the existing
      companion storage with fixed ids; tapping raises the EXISTING
      companion sheet (~55%) on the existing web canvas — no new web
      stack, no APIs/SDKs/Android-app integration.
    * Your tools: the built-in CLI registry (incl. the NEW Cline entry)
      plus user-defined custom tools; the old Home-visible probe pre-pass
      is retired — tiles are NOT install claims, the tap-time
      verify-then-launch path (runtime gate -> real guest-shell probe ->
      spawn -> honest refusal banner) is the honesty mechanism.
    * Remove-from-Home: long-press -> confirm -> HIDE-only (the launcher
      stays configured; restorable in Settings -> Launchers).
    * Custom tools: Name + Command (user config, stored verbatim after
      single-line hygiene validation) launched through the SAME extracted
      launchGuestCommand core (probe = command head; the full line via
      guestCustomCommandChain — the verbatim sibling of
      guestLaunchChain). No second spawn system.
    * Custom companions: managed by the existing Companion settings
      screen (frozen companion package: ZERO file changes); optional
      icons are side-car copies keyed by definition id.
    * Icons: system image picker -> COPIED into app storage (referenced
      by stored filename, never the picker URI); deterministic letter
      badges with greedy collision resolution everywhere else
      (companions C/Cl/Z/G; tools K/C/H/Cl/Co); every icon failure
      degrades to the badge.
  - The M7.0 release features (P1-P9: storage abstraction, SAF bridge,
    editor, Open Terminal Here, file search, multi-select, the P9 search
    fixes) all ride below this tip — see the M7.0 RESTORE narrative in
    git history and docs/TESTING.md sections 35-41.
  - versionCode 45 / versionName 0.11.0-m7.0.0 (P1 does NOT bump the
    version — the next number is deliberately not guessed); the
    6-permission set and the M6 frozen architecture unchanged; embedded
    rev=2 glibc layer asset sha 898131ff… / 17,920,000 B.
  - JVM suite 679/679 effective green (app 534 + terminal-emulator 145,
    forced --rerun-tasks; the release variant mirrors the 145).
  - Docs: docs/TESTING.md §41 = the M7.1 P1 device gate (27 steps).

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
cp "$BUNDLE" "$TREE/pocketshell-m7.1-p1.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.1-p1.gitbundle"

# glibc layer artifact: byte-identical re-serve from the in-tree asset
cp "$PROJECT/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" \
   "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p1.gitbundle" "$DL/"

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | grep -c '/app/page\.tsx$' || echo 0)  (want 0)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/files/TerminalLaunch.kt \
           app/src/main/java/app/pocketshell/files/FileSearch.kt \
           app/src/main/java/app/pocketshell/files/MultiSelectOps.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherModels.kt \
           app/src/main/java/app/pocketshell/launchers/LauncherViewModel.kt \
           app/src/main/java/app/pocketshell/ui/settings/LauncherSettingsScreen.kt \
           app/src/test/java/app/pocketshell/launchers/LauncherModelsTest.kt \
           scripts/p9_apk_audit.sh \
           app/src/main/java/app/pocketshell/ui/files/FilesScreen.kt \
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/main/java/app/pocketshell/runtime/GuestGlibcRuntime.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/TESTING.md docs/CHANGELOG.md RESTORE.txt pocketshell-m7.1-p1.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.1-p1.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.1-p1.gitbundle" "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
