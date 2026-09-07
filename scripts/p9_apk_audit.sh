#!/bin/bash
# P9 release audit: version stamp, permission set, feature symbols, asset pin.
set -euo pipefail
APK=/home/z/my-project/app/build/outputs/apk/debug/app-debug.apk
BT=/home/z/android-sdk/build-tools/36.0.0
export JAVA_HOME=/home/z/tools/jdk-21.0.12.1+1

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
  "Lapp/pocketshell/ui/files/FilesScreenKt;"          # P9 files screen (scroll+longpress+single close)
  "Lapp/pocketshell/files/FileSearch;"                # P8 search core
  "Lapp/pocketshell/files/MultiSelectOps;"            # P8.1 multi-select
  "Lapp/pocketshell/files/TerminalLaunchSupport;"     # P7 Open Terminal Here gate
  "Lapp/pocketshell/files/EditorLaunch;"              # P6 editor launch
  "guestTerminalChain"                                # P7 launch chain
  "newSessionMatchingCurrent"                         # P7.1 + matching
  "openSearchResult"                                  # P8 result activation
  "searchActionTarget"                                # P9 long-press landing
  "keyboardBottomInset"                               # P9 deck clearance
)
for s in "${SYMS[@]}"; do
  if [ -n "$DEXDUMP" ]; then
    if "$DEXDUMP" "$APK" 2>/dev/null | grep -q "$s"; then echo "PRESENT  $s"; else echo "MISSING  $s"; fi
  else
    # fallback: raw dex strings scan
    if unzip -p "$APK" classes*.dex 2>/dev/null | strings | grep -q "$s"; then echo "PRESENT  $s (strings)"; else echo "MISSING  $s (strings)"; fi
  fi
done

echo "=== Embedded glibc asset pin ==="
unzip -l "$APK" | grep -i "glibc" || echo "ASSET MISSING"
