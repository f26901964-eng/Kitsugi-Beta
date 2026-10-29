#!/usr/bin/env bash
# Magisk / KernelSU / SukiSU Ultra tarafindan flaşlanabilir zip uretir.
set -e
cd "$(dirname "$0")"
mkdir -p dist
VER=$(grep '^version=' module.prop | cut -d= -f2)
OUT="dist/kbd2touch-${VER}.zip"
rm -f "$OUT"

STAGE=$(mktemp -d)
mkdir -p "$STAGE/system/bin" "$STAGE/profiles" "$STAGE/META-INF/com/google/android"
# DIKKAT: install.sh BURADA KOPIALANMAZ! KernelSU/SukiSU zip'te install.sh gorurse
# modulu "legacy script" sanip sadece 3 dosyayi /dev/tmp'ye cozer ve kurulum patlar
# (v1.0.1'de yasanan hata). Manuel kurulum scripti tam pakette / Drive'da kalir.
cp module.prop customize.sh post-fs-data.sh service.sh uninstall.sh config.ini "$STAGE/"
cp profiles/*.ini "$STAGE/profiles/"
cp system/bin/kbd2touch "$STAGE/system/bin/kbd2touch"
chmod 755 "$STAGE/system/bin/kbd2touch" "$STAGE"/*.sh

cp installer/update-binary  "$STAGE/META-INF/com/google/android/update-binary"
cp installer/updater-script "$STAGE/META-INF/com/google/android/updater-script"
chmod 755 "$STAGE/META-INF/com/google/android/update-binary"

(cd "$STAGE" && zip -qr "$OLDPWD/$OUT" .)
rm -rf "$STAGE"
echo "paketlendi: $OUT"
ls -l "$OUT"
