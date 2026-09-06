#!/bin/bash
# make_payload_m603.sh — cut the m6.0.3 artifact set from the pinned release
# tip (f98360f), replicating the make_payload_m2.sh procedure exactly:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted,
#   deterministic bytes;  tests tarball: flat layout, zeroed mtimes, gzip -n.
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
VERSION=v0.10.0-m6.0.3
TOPDIR=PocketShell-$VERSION
RELEASE_TIP=f98360f
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
RELEASE_TIP_FULL=$(git rev-parse "$RELEASE_TIP^{commit}")
TIP=$(git rev-parse --short "$RELEASE_TIP_FULL")
echo "== payload $VERSION @ pinned git tip $TIP =="

BUNDLE=$STAGE/pocketshell-m2.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M6.0.3 doctor correctness gate, git tip $TIP)
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

WHAT WAS NEW IN v0.10.0-m6.0.3 (this snapshot):
  - pocketshell-doctor v2: the version gate is a numeric component-wise
    comparison (required <= installed => SUPPORTED; only required >
    installed => UNSUPPORTED), the max required GLIBC symbol version is
    computed numerically over ALL tokens, the real-loader resolution check
    is exit-code-authoritative, the full fact hierarchy prints before the
    verdict, and --selftest runs a permanent 15-case regression matrix.
    (v1 verdicted UNSUPPORTED for EVERY versioned glibc binary — real
    Cline 3.0.61 needs only GLIBC_2.17, the layer provides 2.41.)
  - Suite v2.2: doctor verdict rows grep "^\^Compatibility: SUPPORTED"
    (anchored — UNSUPPORTED contains SUPPORTED; the v2.1 row was vacuous),
    plus three permanent doctor rows (selftest, musl classification,
    real-Cline verdict): 24 rows -> 27.
  - glibc layer rev=2: byte-identical glibc files (proven single-member
    rebuild), marker revision forces every device to re-extract the fixed
    doctor on the next session prep.

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

# APK (already built + verified: vc43)
cp "$PROJECT/app/build/outputs/apk/debug/app-debug.apk" "$PUBLIC/PocketShell-$VERSION-debug.apk"

# tests tarball v2.2 (flat layout, zeroed mtimes, gzip -n)
RT_TGZ=$PUBLIC/pocketshell-runtime-tests-aarch64.tar.gz
( cd "$PROJECT/runtime-tests" && find run_on_device.sh bin -exec touch -d @0 {} + 2>/dev/null; tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - run_on_device.sh -C bin t_cline_shape t_cpp t_dlopen t_fork_exec t_getaddrinfo t_getpwnam t_hello t_libm t_pthread t_static | gzip -n > "$RT_TGZ" )

# glibc layer artifact (rebuilt rev=2 — doctor v2 inside)
cp "$PROJECT/download/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" "$PUBLIC/"

# transparency copy refresh
cp "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" "$PROJECT/download/glibc-sidecar/"
SHA=$(sha256sum "$PUBLIC/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz" | cut -d' ' -f1)
printf '%s  pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz\n' "$SHA" > "$PROJECT/download/glibc-sidecar/SHA256SUMS"

echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | grep -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | grep -c '/app/page\.tsx$' || echo 0)  (want 0)"
echo "doctor v2 selftest present: $(echo "$LIST" | grep -c 'scripts/runtime/pocketshell-doctor' || echo 0)  (want >=1)"
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           app/src/main/java/app/pocketshell/runtime/GlibcRuntimePin.kt \
           app/src/test/java/app/pocketshell/runtime/PocketShellDoctorScriptTest.kt \
           app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz \
           docs/CHANGELOG.md RESTORE.txt pocketshell-m2.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== sha256 (paste into the delivery page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m2.gitbundle" \
          "$PUBLIC/PocketShell-$VERSION-debug.apk" \
          "$PUBLIC/pocketshell-runtime-tests-aarch64.tar.gz" \
          "$PUBLIC"/pocketshell-glibc-aarch64-*.tar.gz | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done
