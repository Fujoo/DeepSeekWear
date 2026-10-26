#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ROOT=..
AAPT2="$ROOT/build/toolchain/bt34/aapt2.exe"
ZIPALIGN="$ROOT/build/toolchain/bt34/zipalign.exe"
APKSIGNER="$ROOT/build/toolchain/bt34/apksigner.bat"
ANDROID_JAR="$ROOT/build/toolchain/android-28/android.jar"
R8JAR="$ROOT/build/toolchain/r8.jar"
KS="$ROOT/build/debug.keystore"
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"
"$AAPT2" compile --dir res -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --java "$OUT/gen" \
  --auto-add-overlay --min-sdk-version 23 --target-sdk-version 28 \
  --version-code 8 --version-name 1.7.0
find java "$OUT/gen" -name '*.java' | sort > "$OUT/sources.txt"
javac --release 8 -encoding UTF-8 -nowarn -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' | sort > "$OUT/classes.txt"
java -cp "$R8JAR" com.android.tools.r8.D8 --release --min-api 23 \
  --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt"
( cd "$OUT/dex" && jar -0uf ../base.apk classes.dex )
"$ZIPALIGN" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/DeepSeekWear-v1.7.0.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify --print-certs "$OUT/DeepSeekWear-v1.7.0.apk" | head -4
ls -lh "$OUT/DeepSeekWear-v1.7.0.apk"
