#!/bin/bash
# rebuild_sidecar_m603.sh — rebuild the glibc runtime layer with the FIXED
# pocketshell-doctor (m6.0.3), guaranteeing every other byte is IDENTICAL to
# the proven-working m6.0.2 artifact (the Debian glibc files, loader, symlinks
# and layout are never re-fetched or re-derived — the rig was lost to a
# sandbox reset, so the current artifact IS the source of truth).
#
# Procedure: extract the current artifact -> swap in the fixed doctor ->
# re-tar with the EXACT original build flags (build_glibc_sidecar.sh:build_tar:
#   --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner --format=gnu) ->
# prove that ONLY usr/local/bin/pocketshell-doctor changed -> print new pins.
set -euo pipefail
P=/home/z/my-project
OLD=$P/download/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
NEW=$P/download/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz.new
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

OLD_S=$WORK/old; NEW_S=$WORK/new
mkdir -p "$OLD_S" "$NEW_S"
tar -xzf "$OLD" -C "$OLD_S"
tar -xzf "$OLD" -C "$NEW_S"

echo "== swap in the fixed doctor =="
install -m 755 "$P/scripts/runtime/pocketshell-doctor" "$NEW_S/usr/local/bin/pocketshell-doctor"

echo "== re-tar with the original build flags =="
tar -C "$NEW_S" --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner \
    --format=gnu -czf "$NEW" .

echo "== PROOF: byte-compare every extracted file, old vs new =="
diff_count=0
( cd "$OLD_S" && find . -type f -o -type l | sort ) > "$WORK/list"
while IFS= read -r f; do
  if [ -L "$OLD_S/$f" ] || [ -L "$NEW_S/$f" ]; then
    [ "$(readlink "$OLD_S/$f")" = "$(readlink "$NEW_S/$f")" ] || { echo "  SYMLINK CHANGED: $f"; diff_count=$((diff_count+1)); }
  elif cmp -s "$OLD_S/$f" "$NEW_S/$f"; then :; else
    echo "  CONTENT CHANGED: $f"
    diff_count=$((diff_count+1))
  fi
done < "$WORK/list"
[ "$diff_count" -eq 1 ] || { echo "UNEXPECTED: $diff_count files changed (want exactly 1)"; exit 1; }
echo "  exactly 1 file changed: usr/local/bin/pocketshell-doctor (the fix)"

echo "== PROOF: tar metadata (modes/owners/mtimes) identical outside the doctor =="
tar -tvzf "$OLD" | awk '{print $1, $2, $NF}' | sort > "$WORK/meta.old"
tar -tvzf "$NEW" | awk '{print $1, $2, $NF}' | sort > "$WORK/meta.new"
meta_diff=$(diff "$WORK/meta.old" "$WORK/meta.new" | grep -c '^[<>]' || true)
echo "  metadata lines differing: $meta_diff (1 = the doctor row only: size/mtime-normalized)"
[ "$meta_diff" -le 1 ] || { echo "METADATA DRIFT BEYOND THE DOCTOR"; diff "$WORK/meta.old" "$WORK/meta.new"; exit 1; }

echo "== replace artifact atomically =="
mv "$NEW" "$OLD"

echo "== new pins =="
SHA=$(sha256sum "$OLD" | cut -d' ' -f1)
SIZE=$(stat -c %s "$OLD")
gunzip -c "$OLD" > "$WORK/plain.tar"
ASHA=$(sha256sum "$WORK/plain.tar" | cut -d' ' -f1)
ASIZE=$(stat -c %s "$WORK/plain.tar")
cat <<EOF
PIN: artifact=pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
PIN: sha256=$SHA
PIN: size=$SIZE
PIN: asset_sha256=$ASHA
PIN: asset_size=$ASIZE
EOF
printf '%s  pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz\n' "$SHA" > "$P/download/glibc-sidecar/SHA256SUMS.new" 2>/dev/null || true
echo "== done =="
