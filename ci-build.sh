#!/usr/bin/env bash
set -euo pipefail

BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
OUT=build

[ -f "$ANDROID_JAR" ] || { echo "No hay android-34 en el runner"; ls "$ANDROID_HOME/platforms"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/classes"

"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml "$OUT/res.zip"

javac --release 8 -cp "$ANDROID_JAR" -Xlint:-options -d "$OUT/classes" $(find src -name '*.java')
"$BT/d8" --release --min-api 31 --lib "$ANDROID_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q base.apk classes.dex)

"$BT/zipalign" -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks leex.keystore --ks-pass pass:leexleex --key-pass pass:leexleex \
    --out "$OUT/LeexBox.apk" "$OUT/aligned.apk"
