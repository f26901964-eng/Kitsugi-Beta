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
2. `KBD2TOUCH_MODUL/` — özel KernelSU/Magisk modülü v1.0.x
   - `dist/kbd2touch-v1.0.0.zip` → flaşlanabilir paket
   - `system/bin/kbd2touch` → ARM64 statik binary (zig cc, musl)
   - `src/kbd2touch.c` → kaynak (~1100 satır)
   - `profiles/` → Dancing Line, GTA SA dokunmatik, GTA SA gamepad, genel şablon
   - Kurulum: yöneticiden zip flaşla **veya** `install.sh`
3. Google Drive yedeği: klasör **"Kitsugi-Beta_Klavye_Modul_Yedek_2026-10-29"**
   (tam paket zip + flaş zip + rapor + README + bu not).

## v1.0.1'de eklenenler (aynı gün, 2. tur)
- **Dead Cells profili** (`com.playdigious.deadcells.mobile.ini`): sanal Xbox kolu üzerinden,
  PC klavye varsayılanlarıyla birebir (Space=A zıpla, Shift=B takla, 1/J=X, 2/K=Y, Q/3=LT, E/4=RT,
  R=RB, F/H=LB iksir, Tab=SELECT harita, ESC=START, oklar=sağ analog kamera).
  Kaynak: resmi deadcells.wiki.gg Controls sayfası + Reddit (mobil klavye desteği YOK, kol desteği mükemmel).
- **Akıllı sanal gamepad** (C değişikliği): `gamepad = 0` iken bile gp eşlemeli profil/uygulama
  öne gelince pad OTOMATİK oluşur (`ensure_pad_for_context`), bağlamdan çıkınca yıkılır.
  `gamepad = 1` = eski davranış (her zaman var). gtasa_gamepad için artık config şartı yok.
- grab_mode (1. turda): klavye sadece eşlenen oyun öndeyken ele geçiriliyor.

## v1.0.1 kritik kurulum düzeltmeleri (3. tur — SukiSU install simülasyonunda bulundu)
- **(a) pkill intihar hatası**: service.sh/uninstall.sh/install.sh içindeki `pkill -f kbd2touch`,
  betiğin KENDİ cmdline'ını da eşleştiriyordu (`sh /data/adb/modules/kbd2touch/service.sh`)
  → script daemon'u başlatamadan ölüyordu. Çözüm: /proc taramalı `kill_daemon()`
  (`.sh` içeren ve tanı modlarını atlar, $$/$PPID korur). README'deki tüm pkill
  komutları da güvenli karşılıklarıyla değiştirildi (service.sh ile yeniden başlat /
  daemon.pid ile durdur).
- **(b) update-binary ui_print hatası**: KSU akışında customize.sh `ui_print` tanımsız
  çalışıyordu → konsol çıktısı "not found" çöpü oluyordu. Çözüm: alt-kabukta
  `ui_print()` tanımlanıp customize.sh source ediliyor.
- **(c)** module.prop: name içindeki UTF-8 uzun tire (—) düz '-' yapıldı (ayrıştırıcı
  riski); version=v1.0.1, versionCode=101.
- **(d)** daemon artık `--home "$KBD_DIR"` ile başlatılıyor (log/status/pid hep doğru dizinde).
- **(e)** install.sh (reboot'suz kurulum): ekran boyutunu config'e otomatik doldurmuyordu
  (daemon "ekran 0x0" ile başlıyordu) + daemon.pid yazmıyordu → ikisi de eklendi.
- **(f)** customize.sh: kopyalama BAŞARISIZSA artık dürüstçe "! HATA" basıyor (önceden
  sessizce "olusturuldu" diyordu); update-binary MODDIR kopyasını doğruluyor.
- Doğrulama: tüm scriptler `bash -n` temiz; KSU-tipi update-binary dry-run simülasyonu,
  service/uninstall/install akışları x86 sandbox'ta stub'larla uçtan uca test edildi.
  Sonuç: **36/36 PASS** (A: update-binary KSU akışı 9, B: post-fs-data 3, C: service 7,
  D: çift çalıştırma 3, E: uninstall 3, F: install.sh 8 + doğrulama). Sahte "decoy" daemon
  ile kill_daemon'un hem öldürdüğü hem de betiğin kendisini ÖLDÜRMEDİĞİ kanıtlandı.

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
- [ ] Touchpad tıklamaları (BTN_LEFT/RIGHT) ayrı evdev düğümünde — istenirse ikinci cihaz olarak grab edilip Dead Cells'te sol/sağ tık = X/Y yapılabilir
- [ ] Çapraz hareket (W+A) için bileşik bind desteği
- [ ] İsteğe bağlı GUI Android uygulaması (ekran üstü sürükle-bırak düzenleyici)
- [ ] Online oyunlar için jitter profil şablonları

## İletişim tercihleri
- Kullanıcı Türkçe, samimi hitap ("balım/bir tanem") — aynı tonla devam
- Sonuçları tablolu, adım adım vermeyi seviyor; dürüst uyarılar önemli
