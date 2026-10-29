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
# kbd2touch DAEMON'larini oldurur; betiklerin/kabuklarin KENDINI oldurmez.
# (pkill -f kbd2touch kullanilmaz: bu betigin kendi cmdline'i da eslesir
#  ve script daha daemon'u baslatmadan intihar eder!)
kill_daemon() {
    for p in /proc/[0-9]*; do
        pid=${p#/proc/}
        [ "$pid" = "$$" ] && continue
        [ "$pid" = "$PPID" ] && continue
        cmd=$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null) || continue
        case "$cmd" in
            *kbd2touch*)
                case "$cmd" in
                    *.sh*|*--check*|*--calibrate*|*--list-devices*|*--map*|*--test*) : ;;
                    *) kill "$pid" 2>/dev/null ;;
                esac ;;
        esac
    done
    return 0
}


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

# Ekran boyutunu algila ve config'teki 'auto' alanlarini doldur
# (service.sh her boot'ta zaten yapar; burasi reboot'suz kurulum icin)
SIZE=$(wm size 2>/dev/null | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
if [ -n "$W" ] && [ -n "$H" ] && [ -f "$KBD/config.ini" ]; then
    if grep -q '^screen_width *= *auto' "$KBD/config.ini" 2>/dev/null; then
        sed -i "s/^screen_width *= *auto/screen_width = $W/"    "$KBD/config.ini"
        sed -i "s/^screen_height *= *auto/screen_height = $H/"  "$KBD/config.ini"
        sed -i "s/^touch_max_x *= *auto/touch_max_x = $((W-1))/" "$KBD/config.ini"
        sed -i "s/^touch_max_y *= *auto/touch_max_y = $((H-1))/" "$KBD/config.ini"
    fi
fi
echo "Ekran: ${SIZE:-bilinmiyor}"

# Yeniden baslatmadan calistir
kill_daemon
sleep 1
nohup "$KBD/kbd2touch" -c "$KBD/config.ini" --home "$KBD" >> "$KBD/daemon.out" 2>&1 &
echo "$!" > "$KBD/daemon.pid"
echo "daemon pid=$!"
sleep 2
echo "--- son log ---"
tail -n 15 "$KBD/log.txt" 2>/dev/null || echo "(log henuz yok)"
echo
echo "KURULUM TAMAM. Simdi F11'e basarak kose kalibrasyonunu calistir."
