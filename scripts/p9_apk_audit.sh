#!/bin/bash
# Release audit: version stamp, permission set, feature symbols, asset pin.
#
# Usage: p9_apk_audit.sh [apk-path]
#   apk-path defaults to <repo-root>/app/build/outputs/apk/debug/app-debug.apk
#   Build tools come from ANDROID_HOME (or ANDROID_SDK_ROOT), build-tools 36.0.0.
#   Works on the historical Z.ai sandbox (env vars set) and on GitHub Actions
#   runners (ANDROID_HOME preinstalled by the workflow setup step).
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
APK="${1:-$REPO/app/build/outputs/apk/debug/app-debug.apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
BT="${SDK:+$SDK/}build-tools/36.0.0"

[ -f "$APK" ] || { echo "APK NOT FOUND: $APK"; exit 1; }
[ -x "$BT/aapt2" ] || { echo "aapt2 NOT FOUND under: $BT (set ANDROID_HOME)"; exit 1; }

echo "=== APK identity ==="
SIZE=$(stat -c%s "$APK")
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
echo "size=$SIZE"
echo "sha256=$SHA"

echo "=== Badging (version + sdk) ==="
"$BT/aapt2" dump badging "$APK" 2>/dev/null | grep -E "^package|sdkVersion|targetSdkVersion" | head -4

echo "=== Permissions (uses-permission) ==="
"$BT/aapt2" dump badging "$APK" 2>/dev/null | grep "uses-permission" | sed 's/uses-permission: name=//;s/ .*//' | sort

echo "=== Feature symbols (dex) ==="
DEXDUMP=$(ls "$BT"/dexdump 2>/dev/null || echo "")
SYMS=(
  "Lapp/pocketshell/ui/files/FilesScreenKt;"          # M7 files screen (scroll+longpress+single close)
  "Lapp/pocketshell/files/FileSearch;"                # M7 search core
  "Lapp/pocketshell/files/MultiSelectOps;"            # M7 multi-select
  "Lapp/pocketshell/files/TerminalLaunchSupport;"     # M7 Open Terminal Here gate
  "Lapp/pocketshell/files/EditorLaunch;"              # M7 editor launch
  "guestTerminalChain"                                # M7 launch chain
  "newSessionMatchingCurrent"                         # p7.1 matching sessions
  "openSearchResult"                                  # M7 result activation
  "searchActionTarget"                                # M7 long-press landing
  "keyboardBottomInset"                               # M7 deck clearance
  "AgentHomeSessionClaims"                            # M7.2 P8 home claims
  "homeSessionClaims"                                 # M7.2 P8 projection
  "Runtime unknown"                                   # M7.2 P8 claim literal
  "AgentLaunchRecords"                                # M7.2 P9 record channel
  "LAUNCH_ANCHOR"                                     # M7.2 P9 anchor grade
  "var/lib/pocketshell-agent"                         # M7.2 P9 channel path
)
MISSING=0
for s in "${SYMS[@]}"; do
  if [ -n "$DEXDUMP" ]; then
    if "$DEXDUMP" "$APK" 2>/dev/null | grep -q "$s"; then echo "PRESENT  $s"; else echo "MISSING  $s"; MISSING=1; fi
  else
    # fallback: raw dex strings scan
    if unzip -p "$APK" classes*.dex 2>/dev/null | strings | grep -q "$s"; then echo "PRESENT  $s (strings)"; else echo "MISSING  $s (strings)"; MISSING=1; fi
  fi
done

echo "=== Embedded glibc asset pin ==="
unzip -l "$APK" | grep -i "glibc" || { echo "ASSET MISSING"; MISSING=1; }

exit "$MISSING"
