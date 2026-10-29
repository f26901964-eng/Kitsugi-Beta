#!/system/bin/sh
# =====================================================================
#  Kbd2Touch — MANUEL KURULUM (Magisk/KernelSU/SukiSU/APatch hepsi)
#  Zip flaşlamak istemiyorsan bunu kullan. En saglam yol.
#
#  Termux'tan:
#     su -c "sh /sdcard/Download/kbd2touch/install.sh /sdcard/Download/kbd2touch"
#  veya root kabuktan:
#     sh /path/to/kbd2touch/install.sh /path/to/kbd2touch
# =====================================================================
set -e
SRC="${1:-$(cd "$(dirname "$0")" && pwd)}"
MODDIR=/data/adb/modules/kbd2touch
KBD=/data/adb/kbd2touch

echo "Kaynak: $SRC"
[ -f "$SRC/system/bin/kbd2touch" ] || { echo "HATA: $SRC/system/bin/kbd2touch yok"; exit 1; }

mkdir -p "$MODDIR/system/bin" "$MODDIR/profiles" "$KBD/profiles"
cp -f "$SRC/module.prop" "$SRC/customize.sh" "$SRC/post-fs-data.sh" \
      "$SRC/service.sh" "$SRC/uninstall.sh" "$SRC/config.ini" "$MODDIR/"
cp -f "$SRC"/profiles/*.ini "$MODDIR/profiles/"
cp -f "$SRC/system/bin/kbd2touch" "$MODDIR/system/bin/kbd2touch"
chmod 755 "$MODDIR"/*.sh "$MODDIR/system/bin/kbd2touch"

# Kullanici config/profillerini koru, eksikleri tamamla
[ -f "$KBD/config.ini" ] || cp -f "$SRC/config.ini" "$KBD/config.ini"
for f in "$SRC"/profiles/*.ini; do
    b=$(basename "$f")
    [ -f "$KBD/profiles/$b" ] || cp -f "$f" "$KBD/profiles/$b"
done
cp -f "$MODDIR/system/bin/kbd2touch" "$KBD/kbd2touch"
chmod 755 "$KBD/kbd2touch"

# MIUI enjeksiyon kilidi
if [ -x /data/adb/magisk/resetprop ]; then
    /data/adb/magisk/resetprop persist.security.adbinput 1
    /data/adb/magisk/resetprop persist.security.adbinstall 1
else
    setprop persist.security.adbinput 1 2>/dev/null || true
    setprop persist.security.adbinstall 1 2>/dev/null || true
fi
echo "persist.security.adbinput=$(getprop persist.security.adbinput)"

# Ekran boyutunu algila
SIZE=$(wm size 2>/dev/null | grep -o '[0-9]*x[0-9]*' | tail -1)
echo "Ekran: ${SIZE:-bilinmiyor}"

# Yeniden baslatmadan calistir
pkill -f kbd2touch 2>/dev/null || true
sleep 1
nohup "$KBD/kbd2touch" -c "$KBD/config.ini" >> "$KBD/daemon.out" 2>&1 &
echo "daemon pid=$!"
sleep 2
echo "--- son log ---"
tail -n 15 "$KBD/log.txt" 2>/dev/null || echo "(log henuz yok)"
echo
echo "KURULUM TAMAM. Simdi F11'e basarak kose kalibrasyonunu calistir."
