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

## v1.0.6 — PROFİL GÜNCELLEME TUZAĞI + menü gezinme (kullanıcı sorusuyla bulundu)

**Kullanıcı sorusu:** "Dead Cells ANA MENÜSÜNDE de PC'deki gibi ok tuşları
çalışacak mı?" → İki kritik sorun ortaya çıktı:

**1. YÜKSEK ETKİLİ KURULUM HATASI — profiller flaşlamada güncellenmiyordu:**
customize.sh ve post-fs-data.sh profilleri `[ -f hedef ] || cp` mantığıyla
"YALNIZCA YOKSA" kopyalıyordu. Kullanıcının cihazında v1.0.2'den kalma
profiller durduğu için v1.0.5 flaşlansa bile YENİ TUŞ DÜZENİ CİHAZA
ULAŞMAYACAKTI (binary güncellenir, profil bayat kalır: W=analog yukarı,
oklar=sağ analog). Bu, "flaşladım ama değişmedi" hissinin gizli ikinci
kaynağıydı. Düzeltme: paketle gelen 5 profil artık her flaşlamada
md5 karşılaştırmasıyla GÜNCELLENİR; farklıysa eski dosya `.bak` yapılır.
Kullanıcının kendi profilleri (başka adlar) ve config.ini (ekran/rotasyon
ayarları) korunur. post-fs-data.sh "yoksa onar" modunda kaldı (her boot'ta
zorla üzerine yazmak runtime düzenlemeleri silerdi; güncelleme anı = flaş).

**2. Menü gezinme eksikti — ↑ tuşu A'ya (onayla) bağlıydı:**
Dead Cells menülerinde seçim ↑↓←→ ile gezilir; onay=A, geri=B. v1.0.5
profilinde ↑=A (zıpla) olduğundan menüde YUKARI gezinme imkânsızdı
(↑ basınca seçim onaylanıyordu!). PC'de ↑ hem zıpla hem menü-yukarı;
daemon oyun-içi/menü bağlamını AYIRT EDEMEZ → seçim gerekti: ↑ = LY yukarı
(menü gezin + asmada tırman), zıplama Space/W'de kaldı (PC'nin birincil
iki jump bind'ı; wiki: Jump = Space, W, Up). Menü düzeni artık:
↑↓←→ gezin, Space/W onayla (A), Shift/Ctrl geri (B), Esc START, Tab SELECT.

**Doğrulama:** --check'te UP → gp_stick LY(0,-32767) görünüyor; yükseltme
senaryosu simüle edildi (bayat profil + kullanıcı config'i + kullanıcı
profili → customize → profil güncellendi, .bak oluştu, config ve kullanıcı
profili dokunulmadı).

## v1.0.5 — GERÇEK CİHAZ TESTİ SONUCU: analog eksen + sticky gamepad düzeltmeleri

**Kullanıcı testi (v1.0.4 sonrası, gerçek cihaz):** Oyun "bir kere kol gibi
algıladı sonra algılamadı"; karakter **sürekli sola** koşuyor; WASD düzgün
çalışmıyor; ok tuşları hiç çalışmıyor. Game Booster kapatıldı, değişmedi.

**Kök neden #1 — `abs_setup()` `minimum`'u hiç yazmıyordu:**
`struct uinput_abs_setup` memset'lenip yalnız `maximum` set edilince analog
eksenler **0..32767** aralığıyla oluşuyordu. Zincir:
1. Profil `-32767` (sol/yukarı) yazıyor → kernel `input_handle_abs_event`
   değeri `[min,max]=[0,32767]`'ye **kırpıyor** → 0.
2. Pad oluşturulurken "merkezleme" diye yazılan 0 → aralığın DİBİ.
3. Android InputReader ekseni `(v-min)/(max-min)*2-1` ile normalize eder →
   0 → **-1.0 = SONUNA KADAR SOL/YUKARI**.
Sonuç: pad doğar doğmaz sol analog tam sola yapışık → karakter sürekli sola
koşuyor; W/A/S/D bırakınca 0 = yine sol; RX/RY de aynı → oklar ölü.
**Düzeltme:** `abs_setup(fd, code, min, max, fuzz, flat)`; analoglar
`-32768..32767` (merkez 0 = Linux gamepad standardı), tetikler `0..255`,
D-pad `ABS_HAT0X/Y -1..1` (xpad ile aynı; cihaz menülerinde gezinme bonusu).

**Kök neden #2 — pad'in odak kaybında yok edilmesi (Unity hot-plug):**
`ensure_pad_for_context` her gp-dışı ön plana geçişte (bildirim çubuğu!,
MIUI overlay, FivePlay menüsü, kısa app geçişi) pad'i `UI_DEV_DESTROY` ile
yok edip dönüşte yeniden yaratıyordu. Unity tabanlı oyunlar çalışma sırasında
eklenen/çıkan joystick'i **yeniden taramaz** → "bir kere algıladı, sonra
algılamadı". Ayrıca yaratma bir kez başarısız olursa `g_pad_auto_failed`
kalıcı latch'lenip pad'i sonsuza dek kapatıyordu.
**Düzeltme:** `gamepad_sticky = 1` (yeni config, varsayılan): pad bir kez
oluşunca KALDIRILMAZ; bağlam gp-dışına çıkınca `pad_center_all()` ile
nötrlenir (tüm eksenler 0 + tüm butonlar release). Latch kaldırıldı:
başarısız yaratma 10 sn'de bir yeniden denenir. `gamepad_sticky = 0` eski
davranışı korur (dokunmatik oyunlar kol görmesin isteyenler için).

**Profil düzeltmesi — Dead Cells resmî wiki'ye göre (deadcells.wiki.gg/Controls):**
PC klavyesinde **Jump = Space VEYA W VEYA ↑** (üç keybind), hareket = A/D +
←/→, eğil/düş = S/↓ (Drop = aşağı+A), tırman = yukarı (vine/chain). Eski
profilimiz W'yi "sol analog yukarı" yapıyordu → PC alışkanlığıyla W'ye basan
kullanıcı zıplayamıyordu. Yeni düzen: W/↑/Space → **A (zıpla)**; ←/→ → LX;
↓ → LY aşağı; **I → LY yukarı (tırmanma)**; S+Space = platformdan düş
(oyunun rebind edilemez "aşağı+A" kombinasyonu, birebir). Butonlar zaten
wiki ile birebirdi (X ana silah, Y ikinci, LT/RT beceri, LB iyileştir,
RB kullan, SELECT harita, START duraklat, B takla) — doğrulandı, dokunulmadı.
Yay nişanı (sağ analog) bu sürümde tuşsuz: aim assist yeterli; istenirse
v1.0.6'da `mouse_aim` (fare REL_X/Y → RX/RY, hassasiyetli, decay'li) eklenir.

**Doğrulama:** gcc -Wall -Wextra 0 uyarı; `--check` → 87 eşleşme, 0
bilinmeyen, W/↑/SPACE→A, I→LY↑, ←/→→LX, S/↓→LY↓ görünüyor; `--fg-parse`
8/8; ARM64 (zig 0.17.0, aarch64 ELF makine kodu 0xb7 doğrulandı); SukiSU
akış simülasyonu; zip integrity + legacy=FALSE.

**Ders:** uinput ABS eksenlerinde `minimum` ASLA varsayılana (0)
bırakılmamalı — çift kutuplu eksenler (analog) için -32768 şart; aksi halde
kernel kırpma + Android normalizasyonu "nötr = tam basılı" üretiyor. Ve
Unity oyunlarında sanal giriş cihazı YARAT-YOK etme: bir kez yarat, kalıcı tut.

## v1.0.4 — KÖK NEDEN BULUNDU: "u0 " öneki (gerçek cihaz bugreport analizi)

**Belirti (kullanıcı, gerçek cihaz):** Modül sorunsuz kuruldu, daemon çalışıyor
(logcat'ta `13152/kbd2touch` canlı, Gboard `kbd2touch keys` cihazını görüyor),
ama Dead Cells ve Dancing Line'da klavye HİÇBİR şey yapmıyor.

**Kanıt (KernelSU_bugreport_2026-10-29_21_50.tar.gz):**
- `status.txt` = 41 bayt = `enabled=1 grab=0 app=u0 com.sukisu.ultra` → **grab=0!**
- `log.txt` = 530 bayt, 21:42'den beri büyümüş değil → `set_grab` hiç logmamış
  → klavye hiç ele geçirilmemiş, `ensure_pad_for_context` pad'i hiç oluşturmamış.
- modül v1.0.2 olarak flaşlanmış (modules.json versionCode=102).

**Kök neden:** `get_foreground_app()`, `dumpsys window` çıktısındaki
`mCurrentFocus=Window{3af0708 u0 com.pkg/act}` satırını ayrıştırırken hash'ten
sonraki **`u0` (kullanıcı kimliği) token'ını atlıyordu** → paket adı
`"u0 com.pkg"` oluyordu → `app_has_profile`/`bind_for_key`/`context_needs_pad`
hepsi boş dönüyordu. Simülasyonla birebir doğrulandı (gcc + eski parser kodu).

**Düzeltmeler (v1.0.4):**
1. `parse_focus_pkg()`: süslü parantez içi token'lanır; `/` içeren ve `.` içeren
   token seçilir, `/` öncesi paket alınır. 8/8 gerçek-biçim testi geçiyor
   (FivePlay mod activitiy, ActivityRecord+t, iç içe MIUI token, null, IME).
2. `--fg-parse "<satır>"` debug flag'i (sandbox'ta test edilebilirlik).
3. Klavye boot'ta yoksa daemon ARTIK ÇIKMIYOR: 2 sn'de bir yeniden arar
   (pogo klavye sonradan takılınca reboot gerekmez).
4. Ön plan değişimi artık `logmsg` (logv değil) → log.txt'de iz bırakır.
5. Sanal pad oluşunca log: "sanal gamepad OLUSTURULDU".
6. `VERSION` define'ı 1.0.4'e çekildi (kod içi log satırı için).

**Ders:** dumpsys/window çıktıları cihaz sürümüne göre token sayısı değişebilir;
ayrıştırma token-tabanlı olmalı, sabit boşluk sayısına güvenmemeli. Gerçek
cihaz logu (bugreport) olmadan bu bulunamazdı — kullanıcıya bugreport için teşekkür.

## v1.0.3 — Bluetooth/USB fare desteği (kullanıcı isteği)
- Daemon artık fare evdev düğümünü de açıyor (poll 2 fd): BT fare sonradan bağlanırsa
  ön-plan değişiminde otomatik yakalanıyor; `grab_mouse=1` iken grab_mode ile birlikte
  sadece eşlenen oyun açıkken EVIOCGRAB (oyun dışında imleç normal).
- Touchpad'ler HARİÇ (ABS_MT kontrolü) → klavye kılıfı touchpad'i imleç olarak kalır;
  sanal cihazlarımız isimden ("kbd2touch") elenir.
- Yeni bind adları: SOL_TIK/SAG_TIK/ORTA_TIK (BTN_* alias'ları parse'a eklendi —
  önce sadece keyname()'deydi, profiller parse edilemiyordu!) + TEEKERLEK_UP/DOWN
  (REL_WHEEL → sahte keycode 0x3001/2, press+release sentezi).
- Profil fare eşlemeleri: Dead Cells (sol=X saldırı, sağ=Y ikinci silah, orta=LB iyileştir
  = PC düzeni), GTA touch (sol=saldırı tap, sağ=nişan hold), GTA gamepad (sol=B, sağ=LT),
  Dancing Line (sol tık zaten vardı).
- Fare hareketi (REL_X/Y) bilinçli olarak dokunulmaz — imleç/nişan oyunun kendisinde kalır
  (Dead Cells gamepad modunda nişanı sağ analog/oklar verir).

## v1.0.2 — GERÇEK CİHAZ HATASI (kullanıcı flaş denemesinde patladı, kök neden bulundu)
- Kullanıcı SukiSU Ultra'dan flaşladı → "Kaynak: /dev/tmp / HATA: /dev/tmp/system/bin/kbd2touch yok /
  Failed to install module script (kod 1)".
- **Kök neden:** SukiSU ksud `installer.sh` → `is_legacy_script()`: zip kökünde `install.sh`
  varsa modül LEGACY sayılır; sadece `module.prop install.sh uninstall.sh common/*` /dev/tmp'ye
  açılır ve install.sh SOURCE edilir (system/ hiç açılmaz!) → guard'ımız haklı olarak abort etti.
  Kanıt: SukiSU-Ultra/userspace/ksud/src/installer.sh (GitHub'dan okundu) + eski zip ile
  `unzip -l zip install.sh | grep -q install.sh` = TRUE.
- **Çözüm:** pack.sh artık install.sh'i flash zip'ine KOYMUYOR → is_legacy_script=FALSE →
  normal akış: tüm zip (META-INF hariç) $MODPATH'a açılır, customize.sh Magisk-uyumlu
  ortamda source edilir. install.sh manuel kurulum için tam pakette/Drive'da kaldı.
- v1.0.2 = v1.0.1 + bu tek ama kritik değişiklik (kod aynı, paket içeriği farklı).

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
