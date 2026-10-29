#!/system/bin/sh
# Kbd2Touch kurulum betigi (Magisk / KernelSU / SukiSU Ultra)
ui_print "*******************************************"
ui_print "  Kbd2Touch v1.0.6 - menu gezinme + profil guncelleme     "
ui_print "*******************************************"

KBD_DIR=/data/adb/kbd2touch
mkdir -p "$KBD_DIR/profiles"
chmod 755 "$KBD_DIR" "$KBD_DIR/profiles"

# Varsayilan config ve profilleri (yalnizca yoksa) kopyala
if [ ! -f "$KBD_DIR/config.ini" ]; then
    if cp -f "$MODPATH/config.ini" "$KBD_DIR/config.ini" 2>/dev/null; then
        ui_print "- /data/adb/kbd2touch/config.ini olusturuldu"
    else
        ui_print "! HATA: config.ini kopyalanamadi -> $KBD_DIR (root izinleri?)"
    fi
else
    ui_print "- mevcut config.ini korundu"
fi
# PAKETLE GELEN profiller: her flaslamada GUNCELLENIR (v1.0.6 — eski surum
# yalnizca "yoksa" kopyaliyordu; cihazdaki bayat profil yuzunden duzeltmeler
# oyuna hic ulasmiyordu!). Kullanici dosyayi duzenlediyse .bak olarak saklanir.
# Kullanicinin KENDI profilleri (paketle gelmeyen dosya adlari) asla ellenmez.
UPD=0
for f in "$MODPATH"/profiles/*.ini; do
    b=$(basename "$f")
    t="$KBD_DIR/profiles/$b"
    if [ -f "$t" ]; then
        same=1
        if command -v md5sum >/dev/null 2>&1; then
            [ "$(md5sum < "$f" 2>/dev/null)" = "$(md5sum < "$t" 2>/dev/null)" ] || same=0
        else
            same=0
        fi
        if [ "$same" = "0" ]; then
            cp -f "$t" "$t.bak" 2>/dev/null
            cp -f "$f" "$t" 2>/dev/null && UPD=$((UPD+1))
        fi
    else
        cp -f "$f" "$t" 2>/dev/null && UPD=$((UPD+1))
    fi
done
NPC=$(ls "$KBD_DIR/profiles"/*.ini 2>/dev/null | grep -v '\.bak$' | wc -l)
ui_print "- oyun profilleri hazir ($NPC adet, $UPD tanesi guncellendi/yeni)"

chmod 755 "$MODPATH/system/bin/kbd2touch" 2>/dev/null

# MIUI: adb/Shizuku ile dokunus enjeksiyonunun kilidini ac (tablet SIM'siz acilamaz)
if [ -x /data/adb/magisk/resetprop ]; then
    /data/adb/magisk/resetprop persist.security.adbinput 1
    /data/adb/magisk/resetprop persist.security.adbinstall 1
elif command -v resetprop >/dev/null 2>&1; then
    resetprop persist.security.adbinput 1
    resetprop persist.security.adbinstall 1
else
    setprop persist.security.adbinput 1
    setprop persist.security.adbinstall 1
fi
ui_print "- persist.security.adbinput=1 (MIUI enjeksiyon kilidi acildi)"

ui_print ""
ui_print "Kurulum bitti. Yeniden baslat."
ui_print "Sonra: F11 ile kose kalibrasyonunu calistir (rotation dogru mu?)"
ui_print "Log: /data/adb/kbd2touch/log.txt"
