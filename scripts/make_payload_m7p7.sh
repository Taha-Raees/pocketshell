#!/bin/bash
# make_payload_m7p7.sh — cut the M7 Phase 7 source payload from the pinned
# app tip (d1fe8b6, "m7 p7: open terminal here for Linux directories" — the
# exact tip the M7P7 APK was built from), replicating make_payload_m604.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE: the embedded git bundle's pack bytes are NOT
#   stable across re-cuts (pack generation), and the bundle is embedded in
#   the archives — so re-running this script yields different bytes. The
#   sha pins on the page/README refer to the ONE delivered cut; re-cutting
#   requires re-pinning everywhere.
# Naming is ADDITIVE: new files carry the -m7p7 suffix; every M6.0.4 release
# artifact and its sha pin stay byte-identical on the delivery surface.
# NOT re-cut (unchanged inputs, current pins remain authoritative):
#   - the APK (delivered separately, a3ce9d3a…)
#   - pocketshell-runtime-tests-aarch64.tar.gz (runtime-tests/ untouched
#     since ff4afa9 — the M6 device-gate suite is still the current one)
#   - the glibc layer artifact (ed82daa8…, rev=2 unchanged)
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.10.0-m6.0.4-m7p7
TOPDIR=PocketShell-$VERSION
RELEASE_TIP=d1fe8b6
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
RELEASE_TIP_FULL=$(git rev-parse "$RELEASE_TIP^{commit}")
TIP=$(git rev-parse --short "$RELEASE_TIP_FULL")
echo "== payload $VERSION @ pinned git tip $TIP =="
[ "$TIP" = "$RELEASE_TIP" ] || echo "(using full-resolved short tip $TIP)"

BUNDLE=$STAGE/pocketshell-m7p7.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7 phases 1-7, git tip $TIP)
=======================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through
M7 Phase 7) is preserved inside the single file:
  pocketshell-m7p7.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7p7.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT WAS NEW IN M7 (phases 1-7, this snapshot):
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
  - Phase 7 (NEW in this snapshot): "Open Terminal Here" — Linux
    directories in the Files explorer launch a NORMAL Alpine session
    whose guest working directory is the exact browsed directory (pwd
    prints it literally), through the UNCHANGED canonical session path
    (preflight -> prepareLinuxSession on IO -> spawnLinuxSession on Main
    -> navigate only after onReady). The directory travels as PTY argv
    via guestTerminalChain = cd -- '<dir>' && exec /bin/sh -l: POSIX
    single-quote wrapping, -- ends options, && so the interactive login
    shell follows only a SUCCESSFUL cd; execution-proven through real
    /bin/sh incl. a genuine injection attempt. Android areas get the
    honest boundary note instead of a fake button. Existing sessions
    untouched.
  - JVM suite 600/600 green (app 455 + terminal-emulator 145).
  - Docs: docs/TESTING.md sections 35-37 (editor, real Downloads, Open
    Terminal Here device checklists).

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
cp "$BUNDLE" "$TREE/pocketshell-m7p7.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7p7.gitbundle"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7p7.gitbundle" "$DL/"

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | grep -c '/app/page\.tsx$' || echo 0)  (want 0)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/files/TerminalLaunch.kt \
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/main/java/app/pocketshell/runtime/GuestGlibcRuntime.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/TESTING.md RESTORE.txt pocketshell-m7p7.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7p7.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7p7.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
