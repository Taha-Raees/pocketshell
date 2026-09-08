#!/bin/bash
# make_payload_m72p1.sh — cut the M7.2 P1 (notification foundation) payload
# from the phase tip, adapting make_payload_m711.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# The APK is staged from app/build/outputs as
# PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk (M7.1 phase-build precedent:
# the versionName/versionCode stamp is NOT bumped by phase builds — the
# phase rides the inherited vc47 / 0.11.2-m7.1.1 stamp, the suffix is
# filename-only). The glibc layer artifact is carried forward
# byte-identically (rev=2, unchanged by M7.2 P1).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.2-m7.1.1-m72p1
TOPDIR=PocketShell-$VERSION
STAMP=v0.11.2-m7.1.1
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP_FULL=$(git rev-parse HEAD^{commit})
TIP=$(git rev-parse --short "$TIP_FULL")
echo "== payload $VERSION @ phase tip $TIP (stamp $STAMP) =="

APK_SRC=$PROJECT/app/build/outputs/apk/debug/app-debug.apk
APK=$PUBLIC/PocketShell-$VERSION-debug.apk
[[ -f "$APK_SRC" ]] || { echo "APK missing: $APK_SRC (build first)"; exit 1; }

BUNDLE=$STAGE/pocketshell-m7.2-p1.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.2 P1, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through the
M7.2 P1 notification foundation) is preserved inside the single file:
  pocketshell-m7.2-p1.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.2-p1.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.2 P1 IS (this snapshot):
  - The notification FOUNDATION only (docs/M7.2-P0-AUDIT.md is the design
    baseline; P0 shipped no code, P1 implements infrastructure only):
      * POST_NOTIFICATIONS is now REQUESTED at runtime on Android 13+ —
        exactly once per install, fired when the first terminal session
        exists; the request flag is persisted BEFORE the dialog opens, so
        recreation/rotation/process death can never re-ask; a system
        implicit prompt denial (pre-33 targetSdk behavior) is respected;
        denial is fully supported and never touches terminal
        functionality.
      * NotificationCoordinator — the ONE output/integration layer for
        event notifications (app.pocketshell.notifications/): owns the
        "session_events" channel (created idempotently here and only
        here), posts with deterministic ids (EVENT_BASE + sessionId),
        builds FLAG_IMMUTABLE content intents carrying the routing
        extra, and records posted ids in a DataStore ledger. It owns NO
        session/agent state, scans nothing, infers nothing — P2+ feeds
        it factual state transitions.
      * Startup stale-notification sweep: at app start the coordinator
        cancels exactly the coordinator-owned event notifications that
        outlived the process (process death loses session state —
        START_NOT_STICKY, no restoration) and never touches the FGS
        notification (id 1, TerminalService-owned).
      * Tap routing: MainActivity now processes notification intents on
        BOTH paths — cold start (onCreate) and the existing-instance
        singleTask path (onNewIntent, previously unhandled — the P0
        audit finding) — through one exhaustive route handler.
  - P1 posts NO production event notifications and implements NO agent
    detection, NO waiting-for-input heuristics, NO /proc scanning, NO
    OSC 133 — deliberately (the P0 audit's honest capability line).
  - TerminalService and its terminal_sessions foreground-service channel
    are UNTOUCHED; the FGS lifecycle policy (runs iff >= 1 session) is
    unchanged; no new permissions (declared set unchanged).
  - VERIFICATION: full JVM suite forced --rerun-tasks green (app +
    terminal-emulator) at this tip incl. the NEW notifications suites
    (permission policy truth table, deterministic identity, route
    parsing, structural integration pins); fresh APK audited (badging
    vc47 / 0.11.2-m7.1.1, unchanged declared permission set, 26 launcher
    icon entries, cert unchanged). Real-device verification of the
    permission flows, tap paths and stale sweep is the mandatory
    docs/TESTING.md §48 gate (documented separately from JVM results).
  - Docs: CHANGELOG [0.11.2-m7.1.1-m72p1]; ROADMAP M7.2 P1 section;
    TESTING §48 (the P1 device gate); README status.

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
cp "$BUNDLE" "$TREE/pocketshell-m7.2-p1.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.2-p1.gitbundle"
cp "$APK_SRC" "$APK"

# The glibc layer artifact: UNCHANGED by M7.2 P1 — carry the pinned bytes forward.
GLIBC=public/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
GLIBC_PIN=ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d
[[ "$(sha256sum "$GLIBC" | cut -d' ' -f1)" == "$GLIBC_PIN" ]] \
  || { echo "glibc artifact drifted from the pin — refusing"; exit 1; }
echo "glibc artifact: byte-identical to the pinned rev=2 bytes"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.2-p1.gitbundle" "$APK" "$DL/"

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
for key in scripts/runtime/pocketshell-doctor runtime-tests/run_on_device.sh \
           runtime-tests/device_gate.sh \
           app/src/main/java/app/pocketshell/notifications/NotificationCoordinator.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationIds.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationPermissionPolicy.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationPreferences.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationRoute.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationPermissionGate.kt \
           app/src/test/java/app/pocketshell/notifications/NotificationPermissionPolicyTest.kt \
           app/src/test/java/app/pocketshell/notifications/NotificationIdsTest.kt \
           app/src/test/java/app/pocketshell/notifications/NotificationRouteTest.kt \
           app/src/test/java/app/pocketshell/notifications/NotificationIntegrationTest.kt \
           app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt \
           app/src/main/java/app/pocketshell/ExternalKeyboardViewModel.kt \
           app/src/test/java/app/pocketshell/keyboard/ExternalKeyboardVisibilityModelTest.kt \
           docs/M7.2-P0-AUDIT.md \
           docs/TESTING.md docs/CHANGELOG.md docs/ROADMAP.md docs/THIRD_PARTY.md \
           RESTORE.txt pocketshell-m7.2-p1.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== apk =="
sha256sum "$APK" | while read -r h f; do
  printf '%s  %s  (%s bytes)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
/home/z/android-sdk/build-tools/36.0.0/aapt2 dump badging "$APK" 2>/dev/null \
  | grep -E "^package:|uses-permission" || true

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.2-p1.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$APK" "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.2-p1.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
