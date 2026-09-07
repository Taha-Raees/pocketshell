#!/bin/bash
# make_payload_m7p9.sh — cut the M7.0.0 RELEASE source payload from the pinned
# release tip (709d126, "release: pocketshell m7.0.0"; the P9 implementation
# commit 47bed42 rides directly below it; P8.1 e7f2630 / P8 redo 51cd18b /
# P7.1 fb01540 below those), adapting make_payload_m7p81.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE: the embedded git bundle's pack bytes are NOT
#   stable across re-cuts (pack generation), and the bundle is embedded in
#   the archives — so re-running this script yields different bytes. The
#   sha pins on the page/README refer to the ONE delivered cut; re-cutting
#   requires re-pinning everywhere.
# DELIVERED ALONGSIDE (this cut): the APK staged separately as
# PocketShell-v0.11.0-m7.0.0-debug.apk (8826d30d…, built from this tip);
# the glibc layer artifact (ed82daa8…, rev=2) re-served byte-identical.
# The m7p8.1 artifact set is WITHDRAWN from the delivery surface (superseded
# by the release build: vc44 / 0.10.0-m6.0.4-m7p8.1 → vc45 / 0.11.0-m7.0.0);
# its history rides in this bundle (e7f2630 + 48ac398 + b1a6a6e).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.0-m7.0.0
TOPDIR=PocketShell-$VERSION
RELEASE_TIP=709d126
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
RELEASE_TIP_FULL=$(git rev-parse "$RELEASE_TIP^{commit}")
TIP=$(git rev-parse --short "$RELEASE_TIP_FULL")
echo "== payload $VERSION @ pinned git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7.0.0.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.0 RELEASE, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7.0) is preserved inside the single file:
  pocketshell-m7.0.0.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.0.0.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.0 IS (phases 1-9, this snapshot):
  - Phase 1-2: M7 architecture audit; one storage abstraction behind the
    Files explorer — every area is a validated AreaPath (PocketShell
    Linux guest area, the app-owned Downloads shelf, user-granted SAF
    folders), with real file operations and Replace/Cancel collision
    semantics on protected paths.
  - Phase 5: Android storage bridge — SAF folders as first-class
    StorageAreas via a DocumentBackend seam; persisted grants rejoined at
    startup with honest revocation (Reconnect/Remove, never a crash);
    cross-domain copy/move through the verified CrossArea; Share via
    FileProvider; Import/Export with sha-256 read-back verification.
  - Phase 6: quick text editor — byte-honest (strict UTF-8 or refusal,
    BOM/CRLF round-trip byte-exact, NUL binary sniff, 1 MiB cap with the
    real size), concurrency-honest ((size, mtime) save gate + explicit
    overwrite confirmations), input-honest (the ONE keyboard deck).
    P6.1: the app shelf is labeled "PocketShell Downloads"; a granted
    real Download keeps its own name.
  - Phase 7 / p7.1: "Open Terminal Here" opens THE TAPPED FOLDER through
    the UNCHANGED canonical session path (guestTerminalChain = cd --
    '<dir>' && exec /bin/sh -l, POSIX single-quote wrapping,
    execution-proven through real /bin/sh); the terminal "+" matches the
    current session's environment (Linux session -> new Linux shell).
    Android areas get the honest boundary note instead of a fake button.
  - Phase 8: file search — a recursive NAME search of the selected
    storage area only: literal, case-insensitive substring on file/dir/
    symlink-node names, composed ENTIRELY from the unchanged Phase 2
    list() abstraction (zero new storage APIs); the query is DATA; every
    descent through ExplorerOps.composeChild (can never escape the area
    root); symlinks matchable but NEVER descended; honest limits (200
    matches / 2000 folders, skipped folders counted and shown); errors
    never fake empty results; own serial worker + generation guard.
  - Phase 8.1: multi-select — select several entries of the current
    listing and Copy/Move/Delete them through the UNCHANGED Phase 4
    per-entry engine with an honest aggregate; selection is names-only
    and dies at every navigation/area/SAF/search boundary; collisions
    reuse the one Replace dialog per item; cancelled partial pastes
    report what already landed (never silent).
  - Phase 9 (NEW in this snapshot — the release integration):
    * Search results (and the whole Files screen) now end ABOVE the
      shared keyboard deck (the Terminal/Editor inset rule the Files
      screen never applied) — a short result list is fully visible and a
      long one scrolls every row into view; the device-reported "results
      cannot be scrolled" symptom was the viewport extending behind the
      deck, not a broken list.
    * Long-pressing a search result lands on the result's parent and
      opens the SAME contextual action sheet as an explorer row
      (Open / Open Terminal Here / Copy / Move / Share / Export / Rename
      / Delete — resolved from the FRESH listing, routed through the
      existing per-entry operations; no second operations engine).
    * One close behavior: the duplicated field-row close arrow is gone —
      the header X (or system Back) closes search; the in-field X only
      clears the query. Search triggering itself is unchanged.
  - HISTORY NOTE: the original M7 commits and the first m7p8 build were
    lost to a sandbox reset; the history continues from the user-restored
    P7.1 delivery bundle (fb01540) with the P8 content recovered from the
    platform snapshot and re-gated end-to-end (51cd18b), then P8.1
    (e7f2630), then P9 + the release bump (47bed42 + 709d126). See
    worklog.md Tasks 22-26.
  - versionCode 45 / versionName 0.11.0-m7.0.0; the 6-permission set and
    the M6 frozen architecture unchanged; embedded rev=2 glibc layer
    asset sha 898131ff… / 17,920,000 B (the APK installs it itself).
  - JVM suite 653/653 green (app 508 + terminal-emulator 145).
  - Docs: docs/TESTING.md sections 35-40 (40 = the P9/M7.0 final device
    gate); docs/CHANGELOG.md carries the 0.11.0-m7.0.0 release entry.

BUILDING THE APK:

  Android Studio: File > Open > select the cloned folder.
  Command line:   ./gradlew :app:assembleDebug
  (Requires JDK 17+ and Android SDK; the gradle wrapper downloads Gradle.)

Everything else in this archive is the plain, buildable working tree:
  app/  terminal-emulator/  terminal-view/  docs/  scripts/  runtime-tests/
EOF

TREE=$STAGE/$TOPDIR
mkdir -p "$TREE"
git archive --format=tar "$RELEASE_TIP_FULL" \
  | tar -xf - -C "$TREE" \
      --exclude='app/page.tsx' --exclude='app/layout.tsx' \
      --exclude='.env' --exclude='.gitignore' --exclude='.kotlin' \
      --exclude='delivery' \
      --exclude='.initial_snapshot.json' --exclude='dev-server-*.log' \
      --exclude='dev.log'
cp "$BUNDLE" "$TREE/pocketshell-m7.0.0.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.0.0.gitbundle"

# glibc layer artifact: byte-identical re-serve from the in-tree asset
cp "$PROJECT/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" \
   "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.0.0.gitbundle" "$DL/"

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
           scripts/p9_apk_audit.sh \
           app/src/main/java/app/pocketshell/ui/files/FilesScreen.kt \
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/main/java/app/pocketshell/runtime/GuestGlibcRuntime.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/TESTING.md docs/CHANGELOG.md RESTORE.txt pocketshell-m7.0.0.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.0.0.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.0.0.gitbundle" "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
