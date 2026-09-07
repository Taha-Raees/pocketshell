#!/bin/bash
# Staged toolchain reinstall (no NDK — AGP auto-provisions what it needs).
# Idempotent: each stage can resume. Usage: install_toolchain_staged.sh <stage 1|2|3>
set -euo pipefail
STAGE="${1:-all}"
TOOLS=/home/z/tools
SDK=/home/z/android-sdk
JDK_VER="jdk-21.0.12.1+1"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
mkdir -p "$TOOLS" "$SDK"

if [ "$STAGE" = "1" ] || [ "$STAGE" = "all" ]; then
  if [ ! -x "$TOOLS/$JDK_VER/bin/javac" ]; then
    log "downloading Temurin $JDK_VER"
    curl -fsSL --retry 3 --max-time 560 "https://api.adoptium.net/v3/binary/version/jdk-21.0.12.1%2B1/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" -o "$TMP/jdk.tar.gz"
    log "extracting JDK"
    tar -xzf "$TMP/jdk.tar.gz" -C "$TOOLS"
    mv "$TOOLS/jdk-21.0.12.1+1" "$TOOLS/$JDK_VER" 2>/dev/null || true
  fi
  log "stage1 OK: $(bash -c "echo -n y | $TOOLS/$JDK_VER/bin/javac -version" 2>&1)"
fi

if [ "$STAGE" = "2" ] || [ "$STAGE" = "all" ]; then
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    log "downloading cmdline-tools 11076708"
    curl -fsSL --retry 3 --max-time 560 "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o "$TMP/clt.zip"
    mkdir -p "$SDK/cmdline-tools"
    unzip -q "$TMP/clt.zip" -d "$TMP/clt"
    mv "$TMP/clt/cmdline-tools" "$SDK/cmdline-tools/latest"
  fi
  log "stage2 OK: sdkmanager present"
fi

if [ "$STAGE" = "3" ] || [ "$STAGE" = "all" ]; then
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  export JAVA_HOME="$TOOLS/$JDK_VER"; export PATH="$JAVA_HOME/bin:$PATH"
  yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true
  log "installing platform-tools, platforms;android-36, build-tools;36.0.0 (no NDK)"
  yes | "$SDKMANAGER" --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" 2>&1 | tail -2 || true
  echo "sdk.dir=$SDK" > /home/z/my-project/local.properties
  log "stage3 OK: $(ls "$SDK" | tr '\n' ' ')"
fi
log "STAGE $STAGE DONE"
