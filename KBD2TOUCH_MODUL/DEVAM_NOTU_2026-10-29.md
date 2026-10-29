# 📌 DEVAM NOTU — Klavye→Dokunuş projesi (2026-10-29)

**Amaç:** Xiaomi Pad 6 (pipa) + orijinal pogo-pin klavye ile mobil oyunları PC gibi oynamak
(Dancing Line: Space=dokun, oklar=menü/bölüm · GTA SA: WASD/saldır/araba sür…).

## Cihaz künyesi (SukiSU Ultra ekran görüntüsünden, 29.10.2026)
| Alan | Değer |
|---|---|
| Kernel | 4.19.325-20260715-262e55875-perf+ |
| Android | 13 |
| Cihaz | Xiaomi Pad 6 (pipa, SM8250/SD870, 2880×1800) |
| Root | **SukiSU Ultra v3.2.0 (13375)** |
| SELinux | **Enforcing** |
| KPM | Destekleniyor (3072, 10 Ağu 2025) |
| SuSFS | v1.5.7 (Manuel Kanca) |
| Klavye | Orijinal Pad 6 klavye kılıfı (pogo-pin HID + touchpad) |

## Bu oturumda üretilenler (branch: `arena/40822679-kitsugi-beta`)
1. `KLAVYE_OYUN_KONTROL_ARASTIRMA_RAPORU_2026-10-29.md` — 14 bölümlük araştırma
   (XtMapper / KeySync / Panda Mouse Pro / KeyMapper / sanal gamepad / Winlator karşılaştırması,
   MIUI `INJECT_EVENTS` engeli + çözümü, oyun tarifleri, ban riski, sorun giderme).
2. `KBD2TOUCH_MODUL/` — özel KernelSU/Magisk modülü v1.0.0
   - `dist/kbd2touch-v1.0.0.zip` → flaşlanabilir paket
   - `system/bin/kbd2touch` → ARM64 statik binary (zig cc, musl)
   - `src/kbd2touch.c` → kaynak (~1100 satır)
   - `profiles/` → Dancing Line, GTA SA dokunmatik, GTA SA gamepad, genel şablon
   - Kurulum: yöneticiden zip flaşla **veya** `install.sh`
3. Google Drive yedeği: klasör **"Kitsugi-Beta_Klavye_Modul_Yedek_2026-10-29"**
   (tam paket zip + flaş zip + rapor + README + bu not).

## v1.0.0'da olanlar
- uinput çoklu dokunuş (10 parmak), tap/hold/swipe/repeat, sanal Xbox gamepad, sanal klavye (KEY_BACK)
- oyun başına profil + ön plan otomatik geçişi, F9 aç/kapa, F10 profil, F11 kalibrasyon
- **grab_mode=1**: klavye sadece eşlenen oyun açıkken ele geçirilir → günlük yazma bozulmaz
- `--check / --map / --calibrate / --list-devices / --test` teşhis modları
- MIUI `persist.security.adbinput=1` otomatik (SIM'siz tablet kilidi)

## Test durumu (dürüst)
- ✅ x86_64'te 0 hata / 0 uyarı; config ayrıştırma, profil/uygulama eşleme, koordinat dönüşümü,
  harf tuş kodu tablosu (KEY_W=17 hatası düzeltildi), satır-sonu yorum hatası düzeltildi
- ✅ ARM64 binary: ELF64 AArch64, statik (dynamic section yok)
- ❌ **Tablette çalıştırılmadı** (sandbox'ta /dev/uinput yok)

## Kullanıcının cihazında sıradaki adımlar (bu sırayla!)
1. `su -c "ls -l /dev/uinput"` → yoksa kernel uinput'suz; rapordaki alternatife (KeyMapper/XtMapper erişilebilirlik yolu) geç
2. `su -c "getprop persist.security.adbinput"` → 1 olmalı (modül kurulumda ayarlar)
3. Modülü kur: SukiSU yönetici → Modüller → zip flaş **veya** `install.sh`
4. `su -c "/data/adb/kbd2touch/kbd2touch --list-devices"` → klavye düğümünü doğrula
5. `su -c "/data/adb/kbd2touch/kbd2touch --calibrate"` → rotation'ı bul (0/90/180/270)
6. `su -c "/data/adb/kbd2touch/kbd2touch --check"` → eşleme tablosunu gözden geçir
7. Dancing Line'ı aç → Space'i dene; olmazsa log: `su -c "tail -50 /data/adb/kbd2touch/log.txt"`
8. GTA SA: önce `gtasa_gamepad` (config'te `gamepad = 1` + F10), olmazsa dokunmatik profil koordinatlarını düzelt

## Bilinen açık işler / fikirler
- [ ] Dokunmatik profil koordinatları kullanıcının HUD'ına göre ayarlanacak (işaretçi konumu ile)
- [ ] Touchpad'i fare olarak kamera kontrolüne bağlama (XtMapper'da var, bizde yok)
- [ ] Çapraz hareket (W+A) için bileşik bind desteği
- [ ] İsteğe bağlı GUI Android uygulaması (ekran üstü sürükle-bırak düzenleyici)
- [ ] Online oyunlar için jitter profil şablonları

## İletişim tercihleri
- Kullanıcı Türkçe, samimi hitap ("balım/bir tanem") — aynı tonla devam
- Sonuçları tablolu, adım adım vermeyi seviyor; dürüst uyarılar önemli
