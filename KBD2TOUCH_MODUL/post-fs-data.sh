#!/system/bin/sh
MODDIR=${0%/*}
export PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:/data/adb/ksu/bin:$PATH
KBD_DIR=/data/adb/kbd2touch

mkdir -p "$KBD_DIR/profiles"

if [ -f "$MODDIR/system/bin/kbd2touch" ]; then
    cp -f "$MODDIR/system/bin/kbd2touch" "$KBD_DIR/kbd2touch" 2>/dev/null
    chmod 755 "$KBD_DIR/kbd2touch" 2>/dev/null
fi

[ -f "$KBD_DIR/config.ini" ] || cp -f "$MODDIR/config.ini" "$KBD_DIR/config.ini" 2>/dev/null
for f in "$MODDIR"/profiles/*.ini; do
    b=$(basename "$f")
    [ -f "$KBD_DIR/profiles/$b" ] || cp -f "$f" "$KBD_DIR/profiles/$b" 2>/dev/null
done

# MIUI: adb/Shizuku ile dokunus enjeksiyonu kilidini her acilista zorla
if [ -x /data/adb/magisk/resetprop ]; then
    /data/adb/magisk/resetprop persist.security.adbinput 1
    /data/adb/magisk/resetprop persist.security.adbinstall 1
elif [ -x /data/adb/ksu/bin/resetprop ]; then
    /data/adb/ksu/bin/resetprop persist.security.adbinput 1
else
    setprop persist.security.adbinput 1 2>/dev/null
    setprop persist.security.adbinstall 1 2>/dev/null
fi
