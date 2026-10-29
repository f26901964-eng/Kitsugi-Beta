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
cp module.prop customize.sh post-fs-data.sh service.sh uninstall.sh config.ini install.sh "$STAGE/"
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
