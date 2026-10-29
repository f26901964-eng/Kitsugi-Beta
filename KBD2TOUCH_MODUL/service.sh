#!/system/bin/sh
# Kbd2Touch — boot sonrasi baslatma (Magisk/KernelSU/SukiSU service.d)
MODDIR=${0%/*}
export PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:/data/adb/ksu/bin:$PATH
KBD_DIR=/data/adb/kbd2touch
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

BIN="$KBD_DIR/kbd2touch"
[ -x "$BIN" ] || BIN="$MODDIR/system/bin/kbd2touch"
[ -x "$BIN" ] || exit 0

# Sistem hazir olana kadar bekle
i=0
while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ] && [ $i -lt 120 ]; do
    sleep 2; i=$((i+1))
done
sleep 8

# Ekran boyutunu algila ve config'teki 'auto' alanlarini doldur
SIZE=$(wm size 2>/dev/null | grep -o '[0-9]*x[0-9]*' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
if [ -n "$W" ] && [ -n "$H" ] && [ -f "$KBD_DIR/config.ini" ]; then
    if grep -q '^screen_width *= *auto' "$KBD_DIR/config.ini" 2>/dev/null; then
        sed -i "s/^screen_width *= *auto/screen_width = $W/"    "$KBD_DIR/config.ini"
        sed -i "s/^screen_height *= *auto/screen_height = $H/"  "$KBD_DIR/config.ini"
        sed -i "s/^touch_max_x *= *auto/touch_max_x = $((W-1))/" "$KBD_DIR/config.ini"
        sed -i "s/^touch_max_y *= *auto/touch_max_y = $((H-1))/" "$KBD_DIR/config.ini"
    fi
    echo "screen=${W}x${H}" > "$KBD_DIR/screen.txt"
fi

# enable_on_boot = 0 ise baslatma
if grep -q '^enable_on_boot *= *0' "$KBD_DIR/config.ini" 2>/dev/null; then
    echo "enable_on_boot=0 -> daemon baslatilmadi" > "$KBD_DIR/status.txt"
    exit 0
fi

# Eski sureci kapat (pid dosyasi bayat olabilir -> /proc taramasi esas)
kill_daemon
sleep 1

nohup "$BIN" -c "$KBD_DIR/config.ini" --home "$KBD_DIR" >> "$KBD_DIR/daemon.out" 2>&1 &

# v1.10: 25 sn sonra OTOMATIK teshis raporu — /sdcard/Download/kbd2touch_teshis.txt
# (kullanici bu dosyayi paylasinca hangi halkanin koptugu KANITLA gorunur)
( sleep 25; "$BIN" --diagnose >/dev/null 2>&1 ) &
echo "$!" > "$KBD_DIR/daemon.pid"
