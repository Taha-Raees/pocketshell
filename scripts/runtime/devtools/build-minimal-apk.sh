#!/bin/sh
# build-minimal-apk.sh — CLI Android APK build for aarch64 Linux hosts
# (PocketShell guest: Alpine + glibc sidecar + Temurin JDK + arm64 aapt2).
#
# Proves the toolchain class end-to-end WITHOUT Gradle/AGP:
#   aapt2 compile/link -> javac -> d8 -> zip -> zipalign -> apksigner
#
# usage: build-minimal-apk.sh <project-dir> <out.apk>
# project-dir must contain AndroidManifest.xml, res/ (optional), src/ (java).
#
# Environment (defaults match the PocketShell guest layout):
#   ANDROID_HOME (default /opt/android-sdk)
#   BT           (default 36.0.0)
#   PLATFORM     (default android-36)
#   KEYSTORE     (default <project>/debug.keystore, generated if missing)
set -eu

PROJ="$1"; OUT="$2"
AH="${ANDROID_HOME:-/opt/android-sdk}"
BT="$AH/build-tools/${BT_VERSION:-36.0.0}"
AJAR="$AH/platforms/${PLATFORM:-android-36}/android.jar"
KEYSTORE="${KEYSTORE:-$PROJ/debug.keystore}"
KS_PASS="${KS_PASS:-pocketshell}"

command -v java >/dev/null || { echo "no java on PATH" >&2; exit 2; }
[ -f "$AJAR" ] || { echo "missing $AJAR" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# 1. resources
mkdir -p "$WORK/gen" "$WORK/classes"
if [ -d "$PROJ/res" ]; then
  "$BT/aapt2" compile --dir "$PROJ/res" -o "$WORK/res.zip"
  "$BT/aapt2" link -o "$WORK/base.apk" -I "$AJAR" --manifest "$PROJ/AndroidManifest.xml" \
    --java "$WORK/gen" --auto-add-overlay "$WORK/res.zip"
else
  "$BT/aapt2" link -o "$WORK/base.apk" -I "$AJAR" --manifest "$PROJ/AndroidManifest.xml" \
    --java "$WORK/gen"
fi

# 2. compile java against android.jar
find "$PROJ/src" "$WORK/gen" -name '*.java' > "$WORK/sources.txt"
javac -source 11 -target 11 -nowarn -bootclasspath "$AJAR" \
  -d "$WORK/classes" @"$WORK/sources.txt" 2>/dev/null || \
javac -source 11 -target 11 -nowarn -classpath "$AJAR" \
  -d "$WORK/classes" @"$WORK/sources.txt"

# 3. dex
find "$WORK/classes" -name '*.class' > "$WORK/classes.txt"
mkdir -p "$WORK/dex"
"$BT/d8" --release --lib "$AJAR" --min-api 26 --output "$WORK/dex" @"$WORK/classes.txt"

# 4. pack + align
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK/dex" && zip -q -j "$WORK/unsigned.apk" classes.dex)
"$BT/zipalign" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

# 5. sign
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias pocketshell -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=PocketShell Dev,O=PocketShell,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" \
  --out "$OUT" "$WORK/aligned.apk"

"$BT/apksigner" verify "$OUT"
echo "OK: $OUT"
