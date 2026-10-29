#!/system/bin/sh
# Kbd2Touch — boot sonrasi baslatma (Magisk/KernelSU/SukiSU service.d)
MODDIR=${0%/*}
export PATH=/system/bin:/system/xbin:/vendor/bin:/data/adb/magisk:/data/adb/ksu/bin:$PATH
KBD_DIR=/data/adb/kbd2touch
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

# Eski sureci kapat
if [ -f "$KBD_DIR/daemon.pid" ]; then
    kill "$(cat "$KBD_DIR/daemon.pid")" 2>/dev/null
fi
pkill -f kbd2touch 2>/dev/null
sleep 1

nohup "$BIN" -c "$KBD_DIR/config.ini" >> "$KBD_DIR/daemon.out" 2>&1 &
echo "$!" > "$KBD_DIR/daemon.pid"
