#!/bin/bash
# make_payload_m7p81.sh — cut the M7 Phase 8.1 source payload from the pinned
# app tip (e7f2630, "m7 p8.1: multi-select" — the exact tip the M7P8.1 APK was
# built from; the P8 search redo commit 51cd18b rides directly below it), replicating make_payload_m7p7.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE: the embedded git bundle's pack bytes are NOT
#   stable across re-cuts (pack generation), and the bundle is embedded in
#   the archives — so re-running this script yields different bytes. The
#   sha pins on the page/README refer to the ONE delivered cut; re-cutting
#   requires re-pinning everywhere.
# Naming is ADDITIVE; the superseded same-day m7p7 artifacts (broken
# open-terminal-here cwd) are WITHDRAWN from the delivery surface — their
# record lives in the bundle history (d1fe8b6) and docs/CHANGELOG.
# DELIVERED ALONGSIDE (this cut): the APK f316ec67… staged separately as
# PocketShell-v0.10.0-m6.0.4-m7p8.1-debug.apk; the glibc layer artifact
# (ed82daa8…, rev=2, byte-identical in-tree asset) re-served. The m7p7.1
# artifacts and older APKs are GONE (sandbox reset) — withdrawn from the
# page; their history rides in this bundle. runtime-tests tarball not
# re-cut (withdrawn; re-cut on next M6 runtime gate).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.10.0-m6.0.4-m7p8.1
TOPDIR=PocketShell-$VERSION
RELEASE_TIP=e7f2630
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
RELEASE_TIP_FULL=$(git rev-parse "$RELEASE_TIP^{commit}")
TIP=$(git rev-parse --short "$RELEASE_TIP_FULL")
echo "== payload $VERSION @ pinned git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m7p81.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7 phases 1-8.1, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7 Phase 8.1) is preserved inside the single file:
  pocketshell-m7p81.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7p81.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT WAS NEW IN M7 (phases 1-7 + p7.1, this snapshot):
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
  - Phase 7: "Open Terminal Here" — Linux directories launch a NORMAL
    Alpine session through the UNCHANGED canonical session path
    (preflight -> prepareLinuxSession on IO -> spawnLinuxSession on Main
    -> navigate only after onReady), the directory traveling as PTY argv
    via guestTerminalChain = cd -- '<dir>' && exec /bin/sh -l (POSIX
    single-quote wrapping, execution-proven through real /bin/sh incl. a
    genuine injection attempt). Android areas get the honest boundary
    note instead of a fake button.
  - p7.1 (NEW in this snapshot, device-reported fixes):
    * Open Terminal Here now opens THE TAPPED FOLDER (p7.0 launched the
      browsed parent) — the SELECTED entry is resolved through the pure
      terminalLaunchDirectory: the listing the user tapped from, the
      sheet's own DIRECTORY check, and ExplorerOps.composeChild (the ONE
      validated child composition); a stale sheet is an honest refusal,
      never a somewhere-else launch.
    * The terminal "+" now MATCHES the current session's environment: a
      new Linux shell (canonical openLinuxShell path) when the current
      session is a guest (spawn-time id registration + the pinned
      "Alpine Linux" label fallback), else the historical Android shell.
  - Phase 8: file search — a recursive NAME search of the selected
    storage area only: literal, case-insensitive substring on file/dir/
    symlink-node names, composed ENTIRELY from the unchanged Phase 2
    list() abstraction (zero new storage APIs); the query is DATA (never
    regex/glob/shell/traversal); every descent through ExplorerOps
    .composeChild so the walk can never escape the area root; symlinks
    matchable but NEVER descended; honest limits (200 matches / 2000
    folders, skipped folders counted and shown); errors never fake empty
    results; its own serial worker + generation guard; results activate
    into the parent directory with a highlight.
  - Phase 8.1 (NEW in this snapshot): multi-select — select several
    entries of the current listing (header select toggle, row checkboxes)
    and Copy/Move/Delete them through the UNCHANGED Phase 4 per-entry
    engine with an honest aggregate; selection is names-only and dies at
    every navigation/area/SAF/search boundary; collisions reuse the one
    Replace dialog per item; a cancelled partial paste reports what
    already landed (never silent).
  - HISTORY NOTE: the original M7 commits and the first m7p8 build were
    lost to a sandbox reset; this history continues from the P7.1
    delivery bundle (fb01540) with the P8 content recovered from the
    platform snapshot and re-gated end-to-end (51cd18b), then P8.1 on
    top (e7f2630). See worklog.md Tasks 22-24.
  - JVM suite 653/653 green (app 508 + terminal-emulator 145).
  - Docs: docs/TESTING.md sections 35-39 (38 = search device gate,
    39 = multi-select device gate).

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
      --exclude='delivery'
cp "$BUNDLE" "$TREE/pocketshell-m7p81.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7p81.gitbundle"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7p81.gitbundle" "$DL/"

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
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/main/java/app/pocketshell/runtime/GuestGlibcRuntime.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/TESTING.md RESTORE.txt pocketshell-m7p81.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7p81.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7p81.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
