# 🛠️ Kbd2Touch — Xiaomi Pad 6 için klavye → dokunuş / gamepad modülü

**v1.0.6** · MIT lisans · `aarch64` statik binary (bionic'e bağımlı değil)  
Hedef cihaz: **Xiaomi Pad 6 (pipa)** + MIUI 14 / Android 13 + **SukiSU Ultra / KernelSU / Magisk** root

> Bu modül, araştırma raporundaki ("`KLAVYE_OYUN_KONTROL_ARASTIRMA_RAPORU_2026-10-29.md`") hazır uygulamaların
> **yapamadığını** yapmak için yazıldı: root'lu cihazda klavye tuşlarını **kernel seviyesinde** (`/dev/uinput`)
> gerçek dokunuş olaylarına ve **sanal Xbox gamepad'e** çevirmek.
> Erişilebilirlik API'si kullanmadığı için Android'in "aynı anda tek dokunuş" sınırına ve
> MIUI'nin `INJECT_EVENTS` kilidine **takılmaz**.

---

## 1) Ne yapar?

```
Xiaomi Pad 6 klavyesi (pogo-pin, USB HID)
        │  /dev/input/eventX
        ▼
  EVIOCGRAB ile klavyeyi tamamen ele geçirir
  (oyun HAM tuş basımını görmez → çift tetikleme olmaz)
        │
        ▼
   kbd2touch daemon (root, tek C dosyası)
        │
        ├──► /dev/uinput → "kbd2touch touchscreen"  (10 parmak, Protocol B)
        │        tap / hold / swipe / joystick / otomatik tekrar
        │
        ├──► /dev/uinput → "kbd2touch keys"         (sanal klavye)
        │        KEY_BACK, KEY_HOME, ses tuşları…
        │
        └──► /dev/uinput → "Microsoft X-Box 360 pad" (opsiyonel)
                 GTA SA gibi yerel kol desteği olan oyunlar için analog + tetik
```

**Özellikler**
- ✅ **Çoklu dokunuş** — 10 parmak aynı anda (W ile sür + J ile ateş et + L ile nişan al)
- ✅ **Basılı tutma** (`hold = 1`) — analog joystick, sprint, nişan alma
- ✅ **Sürükleme** (`swipe`) — WASD'yi ekran joystick'ine çevirme, sayfa kaydırma
- ✅ **Otomatik tekrar** (`repeat_ms`) — otomatik ateş / otomatik tık
- ✅ **Oyun başına profil + otomatik geçiş** — ön plandaki pakete göre kendisi seçer
- ✅ **Akıllı sanal gamepad** — gamepad eşlemeli oyun (Dead Cells, GTA SA…) açılınca sanal Xbox kolu **otomatik** oluşur ve `gamepad_sticky=1` ile oyun odak kaybetse bile **kaldırılmaz** (Unity hotplug sorunu yok). Oyun "kol bağlandı" moduna geçer
- ✅ **Klavye gaspı (EVIOCGRAB)** — oyun ham tuşu görmez
- ✅ **Bluetooth/USB fare desteği** — eşlenen oyun açıkken fare de yakalanır (`grab_mouse`):
  sol/sağ/orta tık + tekerlek eşlenebilir; oyun dışında imleç **normal** çalışır.
  Touchpad'e dokunulmaz (ABS_MT hariç) → klavye kılıfının touchpad'i fare olarak kalır
- ✅ **MIUI bypass** — kurulumda `persist.security.adbinput=1` otomatik ayarlanır
- ✅ **Dokunuş jitter'ı** — istenirse koordinatlara rastgelelik (anti-detection)
- ✅ **F9** aç/kapa · **F10** profil değiştir · **F11** köşe kalibrasyonu
- ✅ Klavye çıkarılırsa yeniden bağlanmayı bekler; çökme durumunda iz bırakmaz

---

> **v1.0.4 notu:** Gerçek cihaz bugreport analiziyle kritik hata bulundu ve
> düzeltildi: ön plan uygulama adı `dumpsys window` çıktısından ayrıştırılırken
> `u0` (kullanıcı kimliği) öneki pakete yapışıyordu → profiller hiç devreye
> girmiyordu (grab=0, sanal gamepad oluşmuyordu). v1.0.4'te ayrıştırma
> token-tabanlı hale getirildi; ayrıca klavye boot'ta takılı değilse daemon
> artık çıkmıyor, klavyeyi bekleyip takılınca yakalıyor.
> Cihazında doğrulama: `cat /data/adb/kbd2touch/log.txt` → oyun açılınca
> `on plandaki uygulama: com.playdigious.deadcells.mobile` +
> `klavye ELE GECIRILDI` + `sanal gamepad OLUSTURULDU` satırlarını görmelisin.

> **v1.0.5 notu (gerçek cihaz testi sonrası):** İki kritik oyun-kolu hatası
> düzeltildi: ① Sanal pad'in analog eksenleri `0..32767` tanımlanıyordu
> (`minimum` hiç yazılmamıştı) → kernel `-32767`'yi `0`'a kırpıyor, Android
> `0`'ı "sonuna kadar sol" sayıyordu: **karakter kendi kendine sola koşuyor,
> WASD/oklar saçmalıyor, sağ analog (oklar) ölüyordu.** Artık eksenler
> `-32768..32767`, merkez gerçekten `0`. ② Pad, oyun her odak kaybettiğinde
> (bildirim çubuğu, overlay, kısa geçiş) yok edilip yeniden yaratılıyordu;
> Unity oyunlarda kol hot-plug'ı yeniden taramadığı için **"bir kere algıladı,
> sonra algılamadı"** oluyordu. Artık `gamepad_sticky=1` (varsayılan): pad
> bir kez oluşunca kalır, sadece nötrlenir. Ayrıca Dead Cells profili resmî
> wiki'ye göre yenilendi: **W ve ↑ = zıpla** (PC'deki gibi), oklar = hareket,
> **I = tırmanma**, S+Space = platformdan düş.

## 2) Klasör yapısı

```
KBD2TOUCH_MODUL/
├── README.md               ← bu dosya
├── module.prop             ← Magisk/KernelSU modül kimliği
├── customize.sh            ← flaşlama sırasında çalışır (config + profilleri kopyalar, MIUI kilidini açar)
├── post-fs-data.sh         ← her açılışta: binary + config hazırlığı + MIUI prop
├── service.sh              ← boot sonrası: ekran boyutunu algıla, daemon'u başlat
├── uninstall.sh            ← modül kaldırılırken
├── install.sh              ← ⭐ MANUEL KURULUM — **flaş zip'inde YOK!** (KSU legacy yolunu
│                              tetikler; tam paket zip'inde ve Drive'da durur)
├── config.ini              ← ana yapılandırma (şablon)
├── profiles/
│   ├── com.cmplay.dancingline.ini      ← Dancing Line (Space = dokun, oklar = menü/bölüm)
│   ├── com.rockstargames.gtasa.ini     ← GTA SA dokunmatik eşleme (WASD joystick + butonlar)
│   ├── com.playdigious.deadcells.mobile.ini ← ⭐ Dead Cells: sanal Xbox kolu (PC tuş düzeni, OTOMATİK)
│   ├── gtasa_gamepad.ini               ← GTA SA sanal gamepad (PC tuşları gibi, sadece F10 ile)
│   └── genel.ini                       ← boş şablon (yeni oyun için kopyala)
├── src/kbd2touch.c         ← kaynak kod (~1000 satır C, tamamı açık)
├── system/bin/kbd2touch    ← ⭐ derlenmiş arm64 binary (statik musl, ~1.4 MB)
├── bin/                    ← yedek binary'ler (aarch64 + linux test)
├── build.sh                ← yeniden derleme (zig ile)
├── pack.sh                 ← flaşlanabilir zip üretme
├── installer/              ← evrensel update-binary (Magisk + KernelSU/SukiSU/APatch)
└── dist/kbd2touch-v1.0.6.zip  ← ⭐ flaşlanabilir paket
```

---

## 2.5) Senin cihazınla uyumluluk analizi (ekran görüntünden doğrulandı)

| Özellik (SukiSU Ultra ana sayfası) | Değer | Kbd2Touch etkisi |
|---|---|---|
| Kernel | **4.19.325-perf+** (pipa, SM8250) | ✅ 4.19'ta `uinput` ve `EVIOCGRAB` mevcut. **Tek doğrulama:** `su -c "ls -l /dev/uinput"` → dosya varsa tamam. Yoksa: `su -c "zcat /proc/config.gz \| grep UINPUT"` ile bak; kapalıysa Bölüm 8'deki alternatife geç |
| Android | **13** (API 33) | ✅ Tam destekli (min API sınırı yok; musl statik binary 4.4+ çalışır) |
| Yönetici | **SukiSU Ultra v3.2.0 (13375)** | ✅ Modul sistemi KernelSU ile birebir aynı: `dist/*.zip` yöneticiden flaşlanır ya da `install.sh` ile kurulur |
| SELinux | **Enforcing (Etkin)** | ✅ Sorun DEĞİL: KernelSU/SukiSU modül betiklerini Magisk-uyumlu (`u:r:magisk:s0`) bağlamda çalıştırır ve bu bağlam permissiftir. **SELinux'u permissive yapma** |
| KPM | Destekleniyor (3072) | ➖ Gerekmiyor (kernel patch module kullanmıyoruz; kullanıcı alanında çalışıyoruz) |
| SuSFS | **v1.5.7 (Manuel Kanca)** | ✅ Bonus: root'u oyunlardan gizler → anti-cheat'ın modülü görme ihtimali daha da düşer |
| Klavye | Orijinal Pad 6 klavyesi (pogo-pin) | ✅ Sistemde USB HID klavye + ayrı touchpad düğümü olarak görünür. Daemon harf/tuş düğümünü otomatik bulur (`--list-devices`), touchpad'e dokunmaz → **touchpad'i fare gibi kullanmaya devam edersin** |

**Orijinal klavye gündelik kullanımda bozulur mu?** Hayır — `grab_mode = 1` (varsayılan) sayesinde:
- Masaüstü / tarayıcı / sohbet / Kitsugi: klavye **%100 normal** yazar (daemon arka planda sessizce bekler).
- Dancing Line / GTA SA açıldığı an: daemon klavyeyi ele geçirir → oyun ham tuşu görmez, sadece eşlenmiş dokunuşlar gider (çift tetikleme imkânsız).
- Oyundan çıkınca: otomatik serbest bırakır.
- İstediğin an **F9** ile tüm eşlemeyi kapatırsın.

**Aklıma gelmeyenler / ekstra kontrol listesi**
1. `su -c "ls -l /dev/uinput"` → yoksa kernel uinput'suz demektir (Bölüm 8 alternatifi).
2. Geliştirici seçenekleri → **USB hata ayıklama (Güvenlik ayarları)**: tablette SIM olmadığı için UI'dan açılamaz; modül kurulumda `persist.security.adbinput=1` yapıyor (Shizuku/KeySync/scrcpy kullanacaksan şart; Kbd2Touch'un kendisi buna ihtiyaç duymaz).
3. **Game Turbo → "Tuşları ve hareketleri kısıtla" = KAPALI** (fiziksel tuşları yutar).
4. Oyun sırasında **otomatik ekran döndürme kapalı** kalsın (koordinatlar kaymasın); kalibrasyonu oyunla aynı yönelimde yap.
5. Klavyenin **Fn kısayolları** (parlaklık/ses) oyun içindeyken çalışmaz (klavye ele geçirilmiş olur) — oyun dışındaysa normal.
6. Ekran klavyesi kaybolursa: `virtual_keyboard = 0`.
7. Daemon bir **root sistem servisi** olduğu için MIUI pil yönetimi onu öldüremez (uygulama değil). Yine de SukiSU'da modülün "açılışta çalışsın" listesinde olduğundan emin ol (varsayılan: evet).
8. Yedek: `profiles/` ve `config.ini` senin ayarların — Drive yedeğini tut (bu oturumda oluşturuldu).

---

## 3) Kurulum

### Yöntem A — Zip flaşlama (önerilen)
1. `dist/kbd2touch-v1.0.4.zip` dosyasını tablete kopyala (İndirilenler'e).
2. **SukiSU Ultra / KernelSU yöneticisi** → Modüller → **Depodan yükle** → zip'i seç.
   (Magisk kullanıyorsan: Magisk → Modüller → Depodan yükle.)
3. **Yeniden başlat.**
4. Kontrol: `su -c "cat /data/adb/kbd2touch/log.txt"` → "sanal dokunmatik oluşturuldu" ve "seçilen klavye: …" satırlarını gör.

> Zip içindeki `installer/update-binary` evrenseldir: Magisk varsa resmî `install_module`'ü kullanır,
> yoksa (KernelSU/SukiSU/APatch) modülü doğrudan `/data/adb/modules/kbd2touch` içine kendisi kurar.

### Yöntem B — Manuel kurulum (flaşlama derdi yok)
Zip'i ve bu klasörü tabletin aynı yerine çıkart, sonra root kabukta:

```bash
# install.sh flaslama zip'inin icinde DEGIL (SukiSU legacy kurulum yolunu tetikler);
# tam paket zip'inden veya Drive'dan al:
su -c "sh /sdcard/Download/kbd2touch/install.sh /sdcard/Download/kbd2touch"
```

`install.sh` yeniden başlatmaya **gerek kalmadan** daemon'u hemen başlatır ve son 15 log satırını gösterir.

### Yöntem C — Sadece binary'yi çalıştır (deneme için)
```bash
su
/data/adb/kbd2touch/kbd2touch --list-devices          # klavyenin event düğümünü bul
/data/adb/kbd2touch/kbd2touch --check                 # tüm eşlemeleri tablo halinde göster ⭐
/data/adb/kbd2touch/kbd2touch --map 1440 900          # ekran koordinatını touch koordinatına çevir
/data/adb/kbd2touch/kbd2touch --calibrate             # 4 köşeye dokunur, rotation doğru mu gör
/data/adb/kbd2touch/kbd2touch --test 1440 900         # tek dokunuş testi
/data/adb/kbd2touch/kbd2touch -c /data/adb/kbd2touch/config.ini   # normal çalışma
```

**`--check` çıktısı örneği** (config'i bozmadan her şeyi doğrula):
```
  ekran        : 2880x1800 (touch ekseni 2880x1800)
  rotation     : 0  swap_xy=0 invert=0/0
  grab_keyboard: 1 (mod=1)   slots: 10   gamepad: 0 (OTOMATIK)   sanal_klavye: 1
  kisayollar   : ac/kapa=F9 profil=F10 kalibrasyon=F11
  profiller    : 4
  eslesmeler   : 55
  TUS        UYGULAMA     EYLEM      KORDINAT/HEDEF                    ETIKET
  ESC        (genel)      key        tus=BACK                          geri
  SPACE      com.cmplay…  tap        (700,1500)                        dokun / yön degistir
  W          com.rockst…  swipe      (620,1240)->(620,1030) 100ms BASILI  ileri
  SPACE      com.rockst…  gp_button  buton=A                           ziPla  [sadece F10]
```

---

## 4) İlk çalıştırma: 3 adımlı kalibrasyon (5 dakika)

### Adım 0 — Config'i doğrula (root bile gerekmez)
```bash
su -c "/data/adb/kbd2touch/kbd2touch --check"
```
Tüm eşlemeleri okunabilir tablo halinde gösterir: tuş adı doğru mu, koordinatlar yerinde mi,
hangi profil hangi uygulamaya bağlı, hangisi "sadece F10 ile" — hepsini tek seferde görürsün.
**Bir şey çalışmıyorsa ilk bakılacak yer burası.**

### Adım 1 — Klavyeyi bul
```bash
su -c "/data/adb/kbd2touch/kbd2touch --list-devices"
```
Çıktıda `=> KLAVYE` işaretli satırı bul, örn:
```
/dev/input/event3     Xiaomi Pad 6 Keyboard                KEY ABS => KLAVYE
```
Bulamazsa `config.ini` içine elle yaz:
```ini
device = /dev/input/event3
# veya isme göre otomatik:
device_match = Xiaomi
```

### Adım 2 — Ekran dönüşünü (rotation) bul ⭐ EN ÖNEMLİ ADIM
`Geliştirici seçenekleri → Dokunmaları göster` ve `İşaretçi konumu`nu **aç**, sonra:
```bash
su -c "/data/adb/kbd2touch/kbd2touch --calibrate"
```
Daemon 1,5 saniye arayla **SOL-ÜST → SAĞ-ÜST → SAĞ-ALT → SOL-ALT** köşelerine dokunur.

- Dokunuşlar sırayla doğru köşelerde beliriyorsa → `rotation` doğru, **bitti**.
- Yanlış köşelerde/kaymış beliriyorsa → `config.ini` içinde `rotation = 90` yap, tekrar dene.
  Sırayla `0 → 90 → 180 → 270` dene; biri mutlaka uyar.
- Hâlâ kayıksa: `swap_xy = 1`, `invert_x = 1`, `invert_y = 1` kombinasyonlarını dene.

> Pad 6'yı oyun oynarken **yatay** tutuyorsan kalibrasyonu da yatay konumda yap.

### Adım 3 — Oyun koordinatlarını doldur
Hazır profillerdeki koordinatlar **2880×1800 yatay için tahminîdir**. Kendi HUD'una göre düzelt:

1. `Geliştirici seçenekleri → İşaretçi konumu` açık kalsın.
2. Oyuna gir, ekrandaki butona **parmağınla bir kez dokun**.
3. Ekranın üstünde çıkan `X=… Y=…` değerini oku.
4. `profiles/<oyun>.ini` dosyasındaki ilgili `x =` / `y =` satırına yaz.

Dosyayı düzenlemek için:
```bash
su -c "nano /data/adb/kbd2touch/profiles/com.rockstargames.gtasa.ini"
# veya bilgisayardan:
adb pull /data/adb/kbd2touch/profiles/ ./profiles_duzenle
adb push ./profiles_duzenle/. /data/adb/kbd2touch/profiles/
```
Değişiklikten sonra daemon'u yeniden başlat (service.sh eski daemon'u **güvenle** öldürüp yenisini başlatır, ~10 sn sürer):
```bash
su -c "sh /data/adb/modules/kbd2touch/service.sh"
```
> ⚠️ `pkill -f kbd2touch` KULLANMA: komutu çalıştıran kabuğun kendi adı da
> `kbd2touch` içerdiği için kabuk kendini öldürür, daemon yeniden başlamaz.

---

## 5) Config referansı

### `config.ini → [global]`
| Anahtar | Açıklama | Varsayılan |
|---|---|---|
| `screen_width` / `screen_height` | Oyunun gördüğü mantıksal ekran boyutu. `auto` → `service.sh` her açılışta `wm size` ile doldurur | auto |
| `touch_max_x` / `touch_max_y` | Sanal dokunmatik eksen aralığı (genelde ekran-1) | auto |
| `rotation` | Ekran dönüşü: `0 / 90 / 180 / 270` — **kalibrasyonla bul** | 0 |
| `swap_xy`, `invert_x`, `invert_y` | Ek eksen düzeltmeleri | 0 |
| `jitter` | Dokunuşa eklenecek rastgele piksel (anti-detection). Offline oyunda `0` | 0 |
| `slots` | Aynı anda basılı tutulabilecek parmak sayısı (≤10) | 10 |
| `grab_keyboard` | `0` = klavyeyi hiç ele geçirme | 1 |
| `grab_mouse` | `1` = eşlenen oyun açıkken **fare de** yakalanır (tık eşlemeleri çalışır), oyun dışında imleç normal · `0` = fareye dokunma | 1 |
| `grab_mode` | **Klavye ne zaman ele geçirilsin?** `0`=asla · `1`=**sadece eşlenen oyun açıkken (önerilen)** · `2`=eşleme açıkken her zaman. `1` iken tarayıcıda/sohbette klavyen **normal yazmaya devam eder**, oyuna girince otomatik ele geçirilir → çift tetikleme olmaz | 1 |
| `device` | Klavyenin `/dev/input/eventX` yolu (elle seçim) | — |
| `device_match` | İsim içinde aranacak metin (`Xiaomi` gibi) | — |
| `tap_ms` | Tek dokunuşun basılı kalma süresi | 40 |
| `swipe_ms` | Sürükleme süresi | 90 |
| `virtual_keyboard` | `1` = `action = key` için ayrı sanal klavye cihazı oluştur. Bazı ROM'larda "fiziksel klavye bağlı" sayılıp ekran klavyesini gizleyebilir → sorun olursa `0` | 1 |
| `gamepad` | `0` = **akıllı**: sanal kol sadece gamepad eşlemeli oyun açılınca oluşur, kapanınca kalkar · `1` = kol her zaman mevcut | 0 |
| `gamepad_name` | Sanal kolun görünen adı | `Microsoft X-Box 360 pad` |
| `auto_profile` | Ön plandaki uygulamaya göre profil seç | 1 |
| `auto_profile_ms` | Uygulama kontrol aralığı | 1500 |
| `enable_on_boot` | `0` ise açılışta pasif başlar, F9 ile açarsın | 1 |

### `config.ini → [keys]`
| Anahtar | İşlev |
|---|---|
| `toggle_key` (F9) | Eşlemeyi **aç/kapa** — yazı yazacağın zaman kapat |
| `profile_key` (F10) | Profiller arasında döngü: `OTOMATİK → profil1 → profil2 → … → OTOMATİK` |
| `calibrate_key` (F11) | 4 köşeye dokunarak rotation testi |
| `quit_key` (kapalı) | Daemon'u tamamen durdurur |

### Eşleme bölümleri
```ini
[bind:TUS_ADI]          ; SPACE, ENTER, ESC, A-Z, 0-9, F1-F12, UP/DOWN/LEFT/RIGHT,
                        ; TAB, SHIFT, CTRL, ALT, BTN_LEFT (touchpad tıklaması)…
action = tap            ; tap | swipe | key | gamepad_button | gamepad_stick
x = 1440                ; tap/swipe başlangıç X (ekran koordinatı)
y = 900                 ; tap/swipe başlangıç Y
x2 = 700                ; swipe bitiş X
y2 = 900                ; swipe bitiş Y
ms = 120                ; tap basılı kalma / swipe süresi
hold = 1                ; tuş bırakana kadar basılı tut (joystick/sprint için şart)
repeat_ms = 90          ; tuş basılıyken bu aralıkla tekrarla (otomatik ateş)
label = ileri           ; sadece log'da görünür

; action = key için:
out_key = BACK          ; BACK, HOME, MENU, VOLUMEUP, ENTER, A-Z…
out_hold_ms = 40

; action = gamepad_button için:
button = A              ; A B X Y LB RB LT RT START SELECT GUIDE THUMBL(L3) THUMBR(R3)

; action = gamepad_stick için:
axis = LX               ; LX LY RX RY
gx = -32767             ; -32767..32767
gy = 0
```

### Profil dosyaları (`profiles/*.ini`)
- Dosya adı = paket adı → **otomatik profil**: `com.cmplay.dancingline.ini` sadece Dancing Line açıkken devreye girer.
- Dosyada `[app]` bölümü varsa dosya adı yerine o kullanılır:
  ```ini
  [app]
  package = com.rockstargames.gtasa
  auto = 0        ; 0 = otomatik devreye girmez, SADECE F10 ile seçilir
  ```
- `[app:paket]` bölümüyle tek dosyada birden çok oyunu gruplayabilirsin:
  ```ini
  [app:com.tencent.ig]
  [bind:SPACE]
  action = tap
  x = 2600
  y = 1400
  ```
- Dosya adı `genel.ini` / `global.ini` / `default.ini` ise eşlemeler **her uygulamada** geçerli.

**Oyunun paket adını bulma:**
```bash
adb shell dumpsys window | grep mCurrentFocus
# veya oyun açıkken:
su -c "cat /data/adb/kbd2touch/status.txt"
```

---

## 6) Hazır tarifler

### 🎵 Dancing Line — Space ile dokunma
`profiles/com.cmplay.dancingline.ini` içinde hazır:

| Tuş | Eylem |
|---|---|
| **Space** / **Enter** / touchpad tık | Sol-alt boşluğa tap → yön değiştir |
| **← / →** | Ana sayfada bölüm sayfalarını kaydır (swipe) |
| **↑ / ↓** | Menü öğesine tap (çoğu menüde ok tuşları zaten çalışır) |
| **E** | Oyna butonuna tap |
| **P** | Duraklat (sağ üst) |
| **R** | Tekrar oyna |
| **Esc** | Geri (KEY_BACK) |

> Dancing Line'da dokunuşun **nerede** olduğu önemli değildir; ama HUD butonlarına denk
> gelmemesi için sol-alt boşluk `(700, 1500)` seçildi. Kendine göre değiştir.

### 🚗 GTA San Andreas — iki ayrı profil

**(a) Dokunmatik profil** (`com.rockstargames.gtasa.ini`) — oyunun kendi butonlarına basar:
WASD = sol joystick sürükleme (hold), Space = zıpla/el freni, F = araca bin, J = saldır,
L = nişan al, Shift = koş, C = eğil, R = şarjör, Q/E = silah değiştir, Tab = harita,
oklar = kamera, 1-5 = slot.

**(b) Gamepad profili** (`gtasa_gamepad.ini`) — oyunu **yerel kol moduna** geçirir:
F10 ile `gtasa_gamepad` profiline geç; sanal Xbox kolu **otomatik oluşur**
(eskiden `gamepad = 1` gerekirdi, artık gerekmiyor). WASD = sol analog, oklar = sağ analog (kamera),
Space = A, F = Y, J = B, L = LT (nişan), Shift = LT (koş), C = RT (eğil), Tab/Start = menü.
Analog sürüş ve nişan alma **dokunmatik eşlemeden çok daha doğal** olur.

> ⚠️ İki profili **aynı anda** otomatiğe alma: gamepad profili bu yüzden `auto = 0` (sadece F10).
> Hangisini kullanacağına karar ver; dokunmatik istemiyorsan `com.rockstargames.gtasa.ini`'yi
> `profiles/` dışına taşı.

> 📌 GTA SA Android **klavyeyi yerel desteklemez** (2.11.13'ten sonra klavye ile hile kodu girme de kaldırıldı).
> Bu yüzden ya bu modülle eşleme yap ya da **Winlator** ile PC sürümünü çalıştır
> (bkz. araştırma raporu Bölüm 3.6).

### ⚔️ Dead Cells — EN GÜVENİLİR profil (sanal kol, sıfır kalibrasyon)

> **Neden özel:** Dead Cells mobil klavyeyi **native desteklemez** (Reddit'te kullanıcılar
> yıllardır istiyor, eklenmedi) ama **kontrolcü desteği mükemmel** — oyun zaten kol için
> tasarlanmış. Bu yüzden dokunma taklidi yerine **sanal Xbox 360 kolu** üretiyoruz:
> oyun kolu görünce kendi kol moduna geçiyor (ekranda A/B/X/Y ipuçları), girdiyi
> **doğal** okuyor. Koordinat yok, kalibrasyon yok, gecikme minimum → bu profil
> "kesin çalışır" kategorisinde.

Oyunu açman yeterli (`com.playdigious.deadcells.mobile` ön plana gelince profil +
sanal kol otomatik devreye girer). Tuş düzeni = **PC klavye varsayılanları** (resmi wiki):

| Tuş | Oyun içi (Xbox) | İşlev (PC karşılığı) |
|---|---|---|
| **A / D** veya **← / →** | sol analog ∓ | sola/sağa yürüme (PC: A-D ve oklar) |
| **Space / W** | A | **zıpla** (PC'de ↑ de zıplar; bizde ↑ menüye ayrıldı) |
| **↑** | sol analog yukarı | **menülerde yukarı gezin** + oyunda asma/zincir tırmanma |
| **S** veya **↓** | sol analog aşağı | eğil; **S+Space = platformdan düş** (oyundaki "aşağı+A") |
| **I** | sol analog yukarı | asma/zincir tırmanma |
| **Shift** veya **Ctrl** | B | takla (dodge-roll) |
| **1 / J / sol tık** | X | ana silah |
| **2 / K / sağ tık** | Y | ikinci silah |
| **Q** veya **3** | LT | sol beceri (el bombası vb.) |
| **E** veya **4** | RT | sağ beceri (taret vb.) |
| **R** | RB | kullan / etkileş (kapı, sandık, NPC) |
| **F / H / orta tık** | LB | iyileştir (iksir) |
| **Tab** | SELECT | harita |
| **Esc** | START | duraklat |

> 📋 **MENÜLERDE (ana menü, duraklat, koleksiyoncu, sandık/upgrade):**
> ↑/↓/←/→ = gezin (dört yön tam), **Space/W = onayla** (A), **Shift/Ctrl = geri** (B),
> Esc = duraklat (START), Tab = harita (SELECT). Yani menü akışı PC'deki gibi
> ok tuşlarıyla; tek fark: PC'de ↑ oyun içinde de zıplatır, burada ↑ menü
> gezinmeye ayrılmış durumda (zıplama Space/W'de — PC'nin birincil tuşları).
>
> 🏹 **Yay nişanı:** kol modunda sağ analogla alınır; bu profil okları PC
> alışkanlığıyla harekete verdiği için sağ analoga tuş atamaz —
> Dead Cells'in **kontrolcü nişan yardımı (aim assist)** bunu büyük ölçüde
> karşılar. İstersen ileride `mouse_aim` (fare hareketi → sağ analog) eklenir.
>
> 🔄 **v1.0.6 profil güncelleme düzeltmesi:** Eski sürümlerde flaşlama,
> CİHAZDA ZATEN VAR olan profillerin ÜZERİNE YAZMIYORDU ("yalnızca yoksa
> kopyala" mantığı) — yani kod düzeltmeleri gelse bile tuş düzenin bayat
> kalıyordu. v1.0.6'dan itibaren paketle gelen 5 profil her flaşlamada
> güncellenir; senin düzenlediğin sürüm `profil.ini.bak` olarak saklanır.
> KENDİ eklediğin profiller (örn. `com.tencent.ig.ini`) asla ellenmez.
> config.ini bilinçli olarak KORUNUR (ekran/rotasyon ayarların kaybolmasın).

> 💀 Ölünce "vücuttan çıkma" (R3) gerekirse profildeki `[bind:V] THUMBR` örneğinin
> başındaki `#` işaretlerini kaldır.
> 🎮 v1.0.5'te pad **kalıcı** (sticky): oyun açıkken bildirim çubuğu/overlay
> odak çalsa bile kol yok olmaz. Yine de oyunu ilk açışında "kol görülmedi"
> dersen oyunu TAM kapatıp (son uygulamalardan at) yeniden aç — Unity kolu
> başlangıçta tarar.

### 🆕 Yeni oyun ekleme (3 dakika)
```bash
su
cp /data/adb/kbd2touch/profiles/genel.ini /data/adb/kbd2touch/profiles/com.tencent.ig.ini
nano /data/adb/kbd2touch/profiles/com.tencent.ig.ini
# [bind:SPACE] action=tap x=… y=… satırlarının başındaki # işaretlerini kaldır
sh /data/adb/modules/kbd2touch/service.sh   # daemon'u yeniden başlat (güvenli)
```

---

## 7) Günlük kullanım

| Durum | Ne yap |
|---|---|
| Yazı yazacaksın | **F9** → eşleme kapanır (klavye normale döner) |
| Oyuna geçtin | **F9** → aç (otomatik profil zaten oyunu tanır) |
| Oyun kolu modu istiyorum | **F10** → `gtasa_gamepad` |
| Koordinatlar kaydı | **F11** → köşe kalibrasyonu |
| Ne durumda? | `su -c "cat /data/adb/kbd2touch/status.txt"` |
| Log | `su -c "tail -f /data/adb/kbd2touch/log.txt"` |
| Durdur | `su -c 'kill $(cat /data/adb/kbd2touch/daemon.pid) 2>/dev/null'` |
| Yeniden başlat | `su -c "sh /data/adb/modules/kbd2touch/service.sh"` |

---

## 8) Sorun giderme

| Belirti | Çözüm |
|---|---|
| `KLAVYE BULUNAMADI` | `--list-devices` ile düğümü bul, `config.ini`'ye `device = /dev/input/eventX` yaz |
| `/dev/uinput açılamadı` | Root yok ya da kernel'de `CONFIG_INPUT_UINPUT` kapalı. `su -c "ls -l /dev/uinput"` ile kontrol et. SukiSU Ultra'da root iznini verdiğinden emin ol |
| Dokunuşlar yanlış köşede | `rotation` değerini değiştir (F11 ile test) → `0/90/180/270` |
| Oyun hem tuşu hem dokunuşu görüyor | `grab_keyboard = 1` yap (EVIOCGRAB). Çalışmazsa oyunun kendi klavye desteği var demektir |
| Tuşa basıyorum hiçbir şey olmuyor | Önce `--check`: tuş adı doğru mu, uygulama eşleşiyor mu? Sonra `log.txt`'e bak |
| Ekran klavyesi kayboldu (yazı yazamıyorum) | `config.ini` → `virtual_keyboard = 0` yap, daemon'u yeniden başlat |
| `SecurityException: INJECT_EVENTS` (başka bir uygulamada) | `su -c "resetprop persist.security.adbinput 1"` — modül kurulumda zaten yapıyor |
| Daemon 5 dk sonra ölüyor | MIUI pil yönetimi: `service.sh`'i SukiSU'da "boot script" olarak bırak; uygulama değil sistem servisi olduğu için normalde etkilenmez |
| Sanal gamepad'i oyun görmüyor | Oyun bazı cihazlarda sadece belirli VID/PID'yi kabul eder; `gamepad_name` zaten `Microsoft X-Box 360 pad`, VID `0x045e` PID `0x028e`. Yine görmezse dokunmatik profili kullan |
| Klavye çıkarıldı, tekrar takınca çalışmıyor | Daemon 20 dakika boyunca yeniden arar; log'da "klavye yeniden bağlandı" görürsün |

**Teşhis komutları**
```bash
su -c "/data/adb/kbd2touch/kbd2touch --list-devices"
su -c "/data/adb/kbd2touch/kbd2touch -c /data/adb/kbd2touch/config.ini --verbose"
su -c "getevent -c"                 # tüm giriş olaylarını canlı izle
su -c "dumpsys input | grep -A3 kbd2touch"   # Android sanal cihazları görüyor mu
su -c "cat /data/adb/kbd2touch/status.txt"
su -c "wm size"
```

---

## 9) Yeniden derleme

```bash
pip install ziglang        # veya https://ziglang.org/download/
cd KBD2TOUCH_MODUL
./build.sh                 # arm64 binary + test binary + dist/*.zip üretir
```

`build.sh` şunu çalıştırır:
```bash
zig cc -target aarch64-linux-musl -O2 -static -o system/bin/kbd2touch src/kbd2touch.c -lm
```
Android NDK ile de derlenebilir:
```bash
$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang \
    -O2 -static-libgcc -o system/bin/kbd2touch src/kbd2touch.c -lm
```

---

## 10) Güvenlik ve sınırlar — dürüst notlar

- ⚠️ **Bu modül senin cihazında test edilmedi.** Ben bir sandbox'ta çalışıyorum; tablette çalıştıramam.
  Derleme ve yapılandırma ayrıştırma testleri x86_64 üzerinde yapıldı (0 hata, 0 uyarı),
  arm64 binary statik musl ile üretildi. **Önce `--calibrate` ile dene**, sonra oyun profillerine geç.
- ⚠️ **Koordinatlar tahminî.** Her oyunun HUD düzeni farklı; Bölüm 4/Adım 3'teki kalibrasyonu yapmadan
  butonlar yanlış yere basar.
- 🔒 **Kaynak kodu tamamen açık** (`src/kbd2touch.c`). Hiçbir ağ bağlantısı yok, hiçbir veri toplamıyor,
  dışarıya bir şey göndermiyor. İstediğin gibi denetleyebilirsin.
- ⛔ **Online rekabetçi oyunda kullanma** (PUBG Mobile, CODM, Free Fire, Wild Rift…). Enjekte edilmiş
  dokunuş anti-cheat tarafından tespit edilebilir → **hesap banı**. Offline oyunlarda (Dancing Line,
  GTA SA, Minecraft, emülatörler) pratikte risk yok.
- 🧹 **Kaldırma:** SukiSU yöneticisinden modülü kaldır (ya da `sh uninstall.sh`) →
  `/data/adb/kbd2touch` silinir, sanal cihazlar kaybolur. Kalıcı sistem değişikliği yapmaz;
  yalnızca `persist.security.adbinput=1` özelliğini ayarlar (istersen `resetprop persist.security.adbinput 0`).
- 🚫 **SELinux'u permissive yapma.** Bu modül enforcing modda root ile çalışır (Tincore'un eski
  rehberlerinin aksine).
