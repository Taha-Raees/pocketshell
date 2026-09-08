#!/bin/bash
# provision_toolchain_m72.sh — M7.2-era toolchain reinstall after the sandbox
# reset. Same pins as scripts/provision_toolchain_m71.sh, but SKIPS the JDK
# download: the system OpenJDK is already 21.0.12.1+1 (the identical pinned
# build). Installs cmdline-tools 11076708, platform-tools, platforms;android-36,
# build-tools;36.0.0 and ndk;28.2.13676358 (terminal-emulator's ndkBuild
# needs the NDK — install_sdk_only.sh's "no native build" note predates that
# module's in-tree JNI and is wrong for assembleDebug).
set -euo pipefail
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/z/android-sdk
mkdir -p "$ANDROID_HOME"
cd "$ANDROID_HOME"

echo "== [1/3] cmdline-tools =="
if [[ ! -x cmdline-tools/latest/bin/sdkmanager ]]; then
  curl -fSL --retry 3 --max-time 600 -o cmdtools.zip \
    https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -qo cmdtools.zip
  mkdir -p cmdline-tools/latest
  mv -f cmdline-tools/bin cmdline-tools/lib cmdline-tools/NOTICE.txt cmdline-tools/source.properties cmdline-tools/latest/ 2>/dev/null || true
  rm -f cmdtools.zip
fi
SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

echo "== [2/3] licenses =="
yes | "$SDKM" --licenses > /dev/null 2>&1 || true

echo "== [3/3] SDK packages =="
yes | "$SDKM" --sdk_root="$ANDROID_HOME" platform-tools "platforms;android-36" "build-tools;36.0.0" "ndk;28.2.13676358" 2>&1 | tail -5

echo "== verify =="
echo "sdk.dir=$ANDROID_HOME" > /home/z/my-project/local.properties
ls "$ANDROID_HOME"
[[ -f "$ANDROID_HOME/platforms/android-36/android.jar" ]] && echo "platform-36 OK"
[[ -x "$ANDROID_HOME/build-tools/36.0.0/aapt2" ]] && echo "aapt2 OK"
[[ -d "$ANDROID_HOME/ndk/28.2.13676358" ]] && echo "NDK OK"
echo "PROVISION COMPLETE"
