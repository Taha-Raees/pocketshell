#!/bin/bash
# Trimmed toolchain reinstall after sandbox reset (2026-09-06 recovery).
# Same pins as scripts/install_toolchain.sh but SKIPS the JDK (system OpenJDK
# is already 21.0.12.1 — identical version) and SKIPS the NDK (the app has no
# native build; libapk + glibc layer ride as committed assets). Only the SDK
# surface needed by AGP 8.13.2 is installed.
set -euo pipefail
SDK=/home/z/android-sdk
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }

mkdir -p "$SDK"

# 1. cmdline-tools 11076708 (same pin as the full installer)
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "downloading cmdline-tools 11076708"
  curl -fsSL --retry 3 --max-time 600 "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$TMP/clt.zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$TMP/clt.zip" -d "$TMP/clt"
  mv "$TMP/clt/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
log "sdkmanager present"

# 2. licenses + packages
log "accepting licenses"
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
log "installing platform-tools, platforms;android-36, build-tools;36.0.0"
yes | "$SDKMANAGER" --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" 2>&1 | tail -2 || true

# 3. local.properties (gitignored)
echo "sdk.dir=$SDK" > /home/z/my-project/local.properties

log "DONE. Verification:"
ls "$SDK" | head -8
[ -f "$SDK/platforms/android-36/android.jar" ] && log "platform-36 OK" || { log "platform-36 MISSING"; exit 1; }
[ -x "$SDK/build-tools/36.0.0/aapt2" ] && log "build-tools 36.0.0 OK" || { log "build-tools MISSING"; exit 1; }
