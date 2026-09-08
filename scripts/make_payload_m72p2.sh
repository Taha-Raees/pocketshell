#!/bin/bash
# make_payload_m72p2.sh — cut the M7.2 P2 (session lifecycle engine &
# structured exit status) payload from the phase tip, adapting
# make_payload_m72p1.sh:
#   source archives: git archive at the TIP (immutable), web shim + dotfiles
#   excluded, RESTORE.txt + git bundle embedded, zeroed mtimes, sorted tgz,
#   gzip -n.  HONESTY NOTE (unchanged): the embedded git bundle's pack bytes
#   are NOT stable across re-cuts — the sha pins on the page/README refer to
#   the ONE delivered cut; re-cutting requires re-pinning everywhere.
# The APK is staged from app/build/outputs as
# PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk (M7.x phase-build precedent:
# the versionName/versionCode stamp is NOT bumped by phase builds — the
# phase rides the inherited vc47 / 0.11.2-m7.1.1 stamp, the suffix is
# filename-only). The glibc layer artifact is carried forward
# byte-identically (rev=2, unchanged by M7.2 P2).
set -euo pipefail
PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DL=$PROJECT/download
VERSION=v0.11.2-m7.1.1-m72p2
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

BUNDLE=$STAGE/pocketshell-m7.2-p2.gitbundle
git bundle create "$BUNDLE" refs/heads/main HEAD

cat > "$STAGE/RESTORE.txt" <<EOF
PocketShell - source snapshot ($VERSION, M7.2 P2, git tip $TIP)
===================================================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints, M0 through the
M7.2 P2 lifecycle engine) is preserved inside the single file:
  pocketshell-m7.2-p2.gitbundle

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m7.2-p2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

WHAT M7.2 P2 IS (this snapshot):
  - The session LIFECYCLE ENGINE + STRUCTURED EXIT STATUS only
    (docs/M7.2-P0-AUDIT.md §10.3/PART I is the design baseline):
      * Typed SessionLifecycleState on every SessionEntry — STARTING
        (entry exists, the PTY child is not yet forked — the audit's
        lazy-fork fact, observed via the real setTerminalShellPid
        callback), RUNNING (the real fork signal), FINISHED (the real
        waitpid delivery) — with REMOVED modeled by tab removal plus a
        typed event, never a stored flag. Invalid combinations are
        unrepresentable (private constructor; FINISHED always carries
        its exit status; STARTING/RUNNING never do). The stored
        isFinished boolean is retired — the getter is DERIVED.
      * Structured exit status faithful to the underlying API:
        ExitStatus.Exited(code) / ExitStatus.Signaled(signal) — exactly
        what JNI.waitFor/waitpid provides (positive = WEXITSTATUS,
        negative = negated WTERMSIG); nothing invented, no polling.
      * ONE authoritative owner (TerminalSessionManager) with pure,
        race-safe transitions: duplicate/out-of-order callbacks are
        REJECTED with a logged reason and can never corrupt a recorded
        status; the close path is guarded against the upstream
        kill(0) hazard for a never-forked pid; typed
        SessionLifecycleEvents are emitted only at the mutation sites.
      * Structured launch identity at every spawn site: SpawnOrigin
        (Shell / LinuxShell / FilesTerminal / CommandApp(id) /
        CatalogApp(id) / CustomTool(id)) + AgentHint for the three
        named-launcher paths, graded LAUNCH_METADATA — spawn metadata
        only, never a process claim.
      * AgentActivityRepository — the ONE derived read model
        (runningLaunchedSessions, finishedLaunchedSessions); stores
        nothing, decides nothing, touches no notifications.
  - Lifecycle/exit state is IN-MEMORY ONLY by design (process-scoped
    like the sessions themselves; no DataStore). P2 posts NO
    notifications and implements NO agent detection, NO
    waiting-for-input heuristics, NO /proc scanning, NO OSC 133, and
    NO user-visible UI change (the real-device gate is a parity
    regression pass).
  - TerminalService and its terminal_sessions foreground-service
    channel are UNTOUCHED; the FGS lifecycle policy (runs iff >= 1
    session entry) is unchanged; no new permissions (declared set
    unchanged).
  - VERIFICATION: full JVM suite forced --rerun-tasks green (810/810:
    app 665 + terminal-emulator 145, 0 failures / 0 errors / 0 skipped)
    incl. the NEW lifecycle suites (the pure transition truth table +
    14 structural integration pins) AND the verification-honesty fix
    (the P1 structural pins silently skipped under the module-dir
    runner and now run and pass); fresh APK audited (badging vc47 /
    0.11.2-m7.1.1, unchanged declared permission set, cert unchanged).
    Real-device regression/parity checks are the mandatory
    docs/TESTING.md §49 gate.
  - Docs: CHANGELOG [0.11.2-m7.1.1-m72p2]; ROADMAP M7.2 P2 section;
    TESTING §49 (the P2 parity gate); README status.

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
cp "$BUNDLE" "$TREE/pocketshell-m7.2-p2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"
find "$TREE" -exec touch -d @0 {} +

ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rqX "$ZIP" "$TOPDIR")
tar --sort=name --mtime=@0 --owner=0 --group=0 --numeric-owner -cf - -C "$STAGE" "$TOPDIR" | gzip -n > "$TGZ"
cp "$BUNDLE" "$PUBLIC/pocketshell-m7.2-p2.gitbundle"
cp "$APK_SRC" "$APK"

# Withdraw the superseded serving surface (the M7.2 P1 set): the insurance
# copies survive in upload/, the history rides in this bundle. Re-running
# this script reproduces the exact serving surface — current set present,
# stale set gone (404).
rm -f "$PUBLIC/PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk" \
      "$PUBLIC/PocketShell-v0.11.2-m7.1.1-m72p1-source.zip" \
      "$PUBLIC/PocketShell-v0.11.2-m7.1.1-m72p1-source.tar.gz" \
      "$PUBLIC/pocketshell-m7.2-p1.gitbundle"

# The glibc layer artifact: UNCHANGED by M7.2 P2 — carry the pinned bytes forward.
GLIBC=public/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz
GLIBC_PIN=ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d
[[ "$(sha256sum "$GLIBC" | cut -d' ' -f1)" == "$GLIBC_PIN" ]] \
  || { echo "glibc artifact drifted from the pin — refusing"; exit 1; }
echo "glibc artifact: byte-identical to the pinned rev=2 bytes"

# Delivery masters: same bytes in download/
cp "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.2-p2.gitbundle" "$APK" "$DL/"

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
           app/src/main/java/app/pocketshell/terminal/SessionLifecycle.kt \
           app/src/main/java/app/pocketshell/terminal/TerminalSessionManager.kt \
           app/src/main/java/app/pocketshell/terminal/AgentActivityRepository.kt \
           app/src/main/java/app/pocketshell/terminal/PocketShellSessionClient.kt \
           app/src/main/java/app/pocketshell/terminal/TerminalService.kt \
           app/src/main/java/app/pocketshell/notifications/NotificationCoordinator.kt \
           app/src/test/java/app/pocketshell/terminal/SessionLifecycleStateTest.kt \
           app/src/test/java/app/pocketshell/terminal/SessionLifecycleIntegrationTest.kt \
           app/src/test/java/app/pocketshell/notifications/NotificationIntegrationTest.kt \
           app/src/main/java/app/pocketshell/keyboard/ExternalKeyboard.kt \
           docs/M7.2-P0-AUDIT.md \
           docs/TESTING.md docs/CHANGELOG.md docs/ROADMAP.md docs/THIRD_PARTY.md \
           RESTORE.txt pocketshell-m7.2-p2.gitbundle gradlew; do
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done

echo "== apk =="
sha256sum "$APK" | while read -r h f; do
  printf '%s  %s  (%s bytes)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
/home/z/android-sdk/build-tools/36.0.0/aapt2 dump badging "$APK" 2>/dev/null \
  | grep -E "^package:|uses-permission" || true

echo "== bundle heads =="
git bundle list-heads "$PUBLIC/pocketshell-m7.2-p2.gitbundle"

echo "== sha256 (paste into the delivery page) =="
sha256sum "$APK" "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m7.2-p2.gitbundle" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(stat -c%s "$f")"
done
