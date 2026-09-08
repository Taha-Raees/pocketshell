#!/bin/bash
# provision_toolchain_m71.sh — reinstall the build toolchain after the sandbox
# reset (same recipe as M0/Task 31/Task 33, pinned versions):
#   Temurin JDK 21.0.12.1+1 -> /home/z/tools/jdk-21.0.12.1+1
#   cmdline-tools 11076708  -> /home/z/android-sdk/cmdline-tools/latest
#   platform-tools + platforms;android-36 + build-tools;36.0.0 + ndk;28.2.13676358
set -euo pipefail
mkdir -p /home/z/tools
cd /home/z/tools

echo "== [1/4] Temurin JDK 21.0.12.1+1 =="
if [[ ! -x jdk-21.0.12.1+1/bin/javac ]]; then
  for url in \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"; do
    echo "trying $url"
    if curl -fSL --retry 3 --max-time 600 -o jdk.tar.gz "$url"; then break; fi
  done
  tar -xzf jdk.tar.gz && rm -f jdk.tar.gz
  [[ -x jdk-21.0.12.1+1/bin/javac ]] || { echo "JDK layout unexpected"; ls; exit 1; }
fi
export JAVA_HOME=/home/z/tools/jdk-21.0.12.1+1
"$JAVA_HOME/bin/javac" -version
echo "JDK OK"

echo "== [2/4] cmdline-tools =="
export ANDROID_HOME=/home/z/android-sdk
mkdir -p "$ANDROID_HOME"
cd "$ANDROID_HOME"
if [[ ! -x cmdline-tools/latest/bin/sdkmanager ]]; then
  curl -fSL --retry 3 --max-time 600 -o cmdtools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -qo cmdtools.zip
  mkdir -p cmdline-tools/latest
  mv -f cmdline-tools/bin cmdline-tools/lib cmdline-tools/NOTICE.txt cmdline-tools/source.properties cmdline-tools/latest/ 2>/dev/null || true
  rm -f cmdtools.zip
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

echo "== [3/4] licenses =="
yes | "$SDKM" --licenses > /dev/null 2>&1 || true

echo "== [4/4] SDK packages (platform-tools, platforms;android-36, build-tools;36.0.0, ndk;28.2.13676358) =="
yes | "$SDKM" --sdk_root="$ANDROID_HOME" platform-tools "platforms;android-36" "build-tools;36.0.0" "ndk;28.2.13676358" 2>&1 | tail -5

echo "== verify =="
ls "$ANDROID_HOME"
[[ -d "$ANDROID_HOME/ndk/28.2.13676358" ]] && echo "NDK OK"
[[ -f "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]] && echo "aapt2 OK"
echo "PROVISION COMPLETE"
