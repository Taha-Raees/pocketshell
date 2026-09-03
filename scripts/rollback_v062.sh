#!/bin/bash
# Rollback the working tree to the pre-UI v0.6.2 source state (23c7234),
# keeping worklog.md append-only (current version retained).
# History is NOT rewritten — the rollback is a new commit on top.
set -euo pipefail
cd /home/z/my-project

BASE=23c7234
echo "== rollback to $BASE (v0.6.2 pre-UI state), worklog.md kept =="

# Classify every difference between BASE and HEAD and act on it.
# --no-renames: renames show as plain D(old)+A(new) — simpler, deterministic.
git diff --name-status --no-renames "$BASE" HEAD | while IFS=$'\t' read -r st rest; do
  case "$st" in
    A)
      # -f: the running dev server can hold local modifications to
      # tool-results logs; everything here is deliberately leaving the tree.
      git rm -qf --ignore-unmatch "$rest"
      ;;
    D)
      git checkout "$BASE" -- "$rest" 2>/dev/null || true
      ;;
    M)
      if [ "$rest" != "worklog.md" ]; then
        git checkout "$BASE" -- "$rest" 2>/dev/null || true
      fi
      ;;
    *)
      echo "UNHANDLED STATUS: $st $rest"
      exit 1
      ;;
  esac
done

# Drop the noisy modified dev log (not part of v0.6.2 state)
git checkout 23c7234 -- tool-results/next-dev-delivery.log 2>/dev/null || git rm -q --cached tool-results/next-dev-delivery.log 2>/dev/null || true

echo "== verification: remaining diff vs $BASE (want ONLY worklog.md) =="
git diff --name-status "$BASE" HEAD
