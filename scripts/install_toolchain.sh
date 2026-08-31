#!/bin/bash
# Reinstall PocketShell build toolchain after sandbox reset (repeatable recipe).
# Pin: Temurin JDK 21.0.12.1+1, cmdline-tools 11076708, platform-36,
# build-tools 36.0.0, NDK 28.2.13676358. See worklog.md Tasks 4/5.
set -euo pipefail

TOOLS=/home/z/tools
SDK=/home/z/android-sdk
JDK_VER="jdk-21.0.12.1+1"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }

mkdir -p "$TOOLS" "$SDK"

# 1. Temurin JDK 21.0.12.1+1
if [ ! -x "$TOOLS/$JDK_VER/bin/javac" ]; then
  log "downloading Temurin $JDK_VER"
  curl -fsSL --retry 3 --max-time 900 "https://api.adoptium.net/v3/binary/version/jdk-21.0.12.1%2B1/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" -o "$TMP/jdk.tar.gz"
  log "extracting JDK"
  tar -xzf "$TMP/jdk.tar.gz" -C "$TOOLS"
  mv "$TOOLS/jdk-21.0.12.1+1" "$TOOLS/$JDK_VER" 2>/dev/null || true
fi
export JAVA_HOME="$TOOLS/$JDK_VER"
export PATH="$JAVA_HOME/bin:$PATH"
log "javac: $(javac -version)"

# 2. cmdline-tools
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "downloading cmdline-tools 11076708"
  curl -fsSL --retry 3 --max-time 600 "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$TMP/clt.zip"
  log "extracting cmdline-tools"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$TMP/clt.zip" -d "$TMP/clt"
  mv "$TMP/clt/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

# 3. SDK packages + licenses
log "accepting licenses"
yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
log "installing platform-tools, platforms;android-36, build-tools;36.0.0, ndk;28.2.13676358 (NDK is ~600MB, be patient)"
yes | "$SDKMANAGER" --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" "ndk;28.2.13676358" 2>&1 | tail -3 || true

# 4. local.properties (gitignored)
echo "sdk.dir=$SDK" > /home/z/my-project/local.properties

log "DONE. Verification:"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
ls "$SDK" | head -8
[ -d "$SDK/ndk/28.2.13676358" ] && log "NDK 28.2.13676358 present" || { log "NDK MISSING"; exit 1; }
