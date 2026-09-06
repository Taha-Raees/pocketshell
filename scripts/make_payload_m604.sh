#!/bin/bash
# make_payload_m604.sh — cut the m6.0.4 artifact set from the pinned release
# tip (8e20dc6, M6 Phase-C closure audit), replicating make_payload_m603.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted,
#   deterministic bytes;  tests tarball: flat layout + audit drill script,
#   zeroed mtimes, gzip -n.
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
VERSION=v0.10.0-m6.0.4
TOPDIR=PocketShell-$VERSION
RELEASE_TIP=ff4afa9
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
RELEASE_TIP_FULL=$(git rev-parse "$RELEASE_TIP^{commit}")
TIP=$(git rev-parse --short "$RELEASE_TIP_FULL")
echo "== payload $VERSION @ pinned git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m2.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M6 Phase-C closure audit, git tip $TIP)
=====================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints) is preserved
inside the single file:  pocketshell-m2.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT WAS NEW IN v0.10.0-m6.0.4 (this snapshot):
  - M6 Phase-C adversarial closure audit fixes:
    * structural integrity probe behind the layer marker (loader symlink
      must resolve to the canonical Debian loader; load-bearing files must
      exist) — detects a gcompat loader reclaim behind a valid marker and
      self-heals by re-extraction;
    * symlink-safe re-extraction (NOFOLLOW deletes; symlink nodes replaced
      as nodes; entries routed through earlier symlinks refused) — the
      multiarch directory is no longer wiped mid-re-extraction;
    * two-phase session creation (heavy guest prep on Dispatchers.IO,
      PTY spawn on main; ensureInstalled single-flight);
    * extractor hardening in both extractors + RuntimeStorage cleanups.
  - Permanent drill suite: runtime-tests/adversarial_closure_audit.sh
    (probe / drill-c2 / drill-c4 / drill-c5 / heal) — corruption,
    gcompat reclaim, apk-ops survival, doctor prediction accuracy,
    environment + filesystem maps, fast-path timing.
  - JVM suite 397 -> 406 leaf cases, 0 failures (incl. built-APK asset pin).
  - Docs: DUAL_LIBC.md §8 (closure contract), KNOWN_LIMITATIONS.md §5–§7.

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
cp "$BUNDLE" "$TREE/pocketshell-m2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m2.gitbundle"

# APK (already built + verified: vc44)
cp "$PROJECT/app/build/outputs/apk/debug/app-debug.apk" "$PUBLIC/PocketShell-$VERSION-debug.apk"

# tests tarball v2.4 (flat layout + Phase-C drill script + device gate runner, zeroed mtimes, gzip -n)
RT_TGZ=$PUBLIC/pocketshell-runtime-tests-aarch64.tar.gz
( cd "$PROJECT/runtime-tests" && find run_on_device.sh adversarial_closure_audit.sh device_gate.sh bin -exec touch -d @0 {} + 2>/dev/null; tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - run_on_device.sh adversarial_closure_audit.sh device_gate.sh -C bin t_cline_shape t_cpp t_dlopen t_fork_exec t_getaddrinfo t_getpwnam t_hello t_libm t_pthread t_static | gzip -n > "$RT_TGZ" )

# glibc layer artifact (rev=2, unchanged by m6.0.4 — byte-identical; the
# post-reset download/ copy is restored from the in-tree pinned asset)
cp "$PROJECT/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" "$PUBLIC/"
cp "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" "$PROJECT/download/glibc-sidecar/"
SHA=$(sha256sum "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" | cut -d' ' -f1)
printf '%s  pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz\n' "$SHA" > "$PROJECT/download/glibc-sidecar/SHA256SUMS"
echo "layer artifact sha: $SHA (pin: ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d)"
[ "$SHA" = "ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d" ] || { echo "LAYER SHA DRIFT — STOP"; exit 1; }

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | grep -c '/app/page\.tsx$' || echo 0)  (want 0)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           runtime-tests/adversarial_closure_audit.sh \
           app/src/main/java/app/pocketshell/runtime/GuestGlibcRuntime.kt \
           app/src/test/java/app/pocketshell/runtime/GuestGlibcRuntimeTest.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/runtime/DUAL_LIBC.md RESTORE.txt pocketshell-m2.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m2.gitbundle" \
          "$PUBLIC/PocketShell-$VERSION-debug.apk" \
          "$PUBLIC/pocketshell-runtime-tests-aarch64.tar.gz" \
          "$PUBLIC"/pocketshell-glibc-aarch64-*.tar.gz | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
