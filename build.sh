#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-$HOME/android.jar}"
KEYSTORE="$HOME/.leex.keystore"
OUT=build

[ -f "$ANDROID_JAR" ] || { echo "Falta android.jar en $ANDROID_JAR (o exporta ANDROID_JAR)"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/classes"

aapt2 compile --dir res -o "$OUT/res.zip"
aapt2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml "$OUT/res.zip"

ecj -source 17 -target 17 -cp "$ANDROID_JAR" -d "$OUT/classes" $(find src -name '*.java')
d8 --release --min-api 31 --lib "$ANDROID_JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
(cd "$OUT" && zip -q base.apk classes.dex)

if command -v zipalign >/dev/null; then
    zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"
else
    cp "$OUT/base.apk" "$OUT/aligned.apk"
fi

[ -f "$KEYSTORE" ] || keytool -genkeypair -keystore "$KEYSTORE" -alias leex -keyalg RSA -keysize 2048 \
    -validity 36500 -storepass leexleex -keypass leexleex -dname "CN=Leex"

apksigner sign --ks "$KEYSTORE" --ks-pass pass:leexleex --key-pass pass:leexleex \
    --out "$OUT/LeexBox.apk" "$OUT/aligned.apk"

echo "Listo: $OUT/LeexBox.apk"
termux-open "$OUT/LeexBox.apk" || true
