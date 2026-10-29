# 🛠️ Kbd2Touch — Xiaomi Pad 6 için klavye → dokunuş / gamepad modülü

**v1.0.0** · MIT lisans · `aarch64` statik binary (bionic'e bağımlı değil)  
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
- ✅ **Sanal gamepad** — oyun "kol bağlandı" moduna geçer (GTA SA, Minecraft, Asphalt…)
- ✅ **Klavye gaspı (EVIOCGRAB)** — oyun ham tuşu görmez
- ✅ **MIUI bypass** — kurulumda `persist.security.adbinput=1` otomatik ayarlanır
- ✅ **Dokunuş jitter'ı** — istenirse koordinatlara rastgelelik (anti-detection)
- ✅ **F9** aç/kapa · **F10** profil değiştir · **F11** köşe kalibrasyonu
- ✅ Klavye çıkarılırsa yeniden bağlanmayı bekler; çökme durumunda iz bırakmaz

---

## 2) Klasör yapısı

```
KBD2TOUCH_MODUL/
├── README.md               ← bu dosya
├── module.prop             ← Magisk/KernelSU modül kimliği
├── customize.sh            ← flaşlama sırasında çalışır (config + profilleri kopyalar, MIUI kilidini açar)
├── post-fs-data.sh         ← her açılışta: binary + config hazırlığı + MIUI prop
├── service.sh              ← boot sonrası: ekran boyutunu algıla, daemon'u başlat
├── uninstall.sh            ← modül kaldırılırken
├── install.sh              ← ⭐ MANUEL KURULUM (zip flaşlamak istemiyorsan, en sağlam yol)
├── config.ini              ← ana yapılandırma (şablon)
├── profiles/
│   ├── com.cmplay.dancingline.ini      ← Dancing Line (Space = dokun, oklar = menü/bölüm)
│   ├── com.rockstargames.gtasa.ini     ← GTA SA dokunmatik eşleme (WASD joystick + butonlar)
│   ├── gtasa_gamepad.ini               ← GTA SA sanal gamepad (PC tuşları gibi, sadece F10 ile)
│   └── genel.ini                       ← boş şablon (yeni oyun için kopyala)
├── src/kbd2touch.c         ← kaynak kod (~1000 satır C, tamamı açık)
├── system/bin/kbd2touch    ← ⭐ derlenmiş arm64 binary (statik musl, ~1.4 MB)
├── bin/                    ← yedek binary'ler (aarch64 + linux test)
├── build.sh                ← yeniden derleme (zig ile)
├── pack.sh                 ← flaşlanabilir zip üretme
├── installer/              ← evrensel update-binary (Magisk + KernelSU/SukiSU/APatch)
└── dist/kbd2touch-v1.0.0.zip  ← ⭐ flaşlanabilir paket
```

---

## 3) Kurulum

### Yöntem A — Zip flaşlama (önerilen)
1. `dist/kbd2touch-v1.0.0.zip` dosyasını tablete kopyala (İndirilenler'e).
2. **SukiSU Ultra / KernelSU yöneticisi** → Modüller → **Depodan yükle** → zip'i seç.
   (Magisk kullanıyorsan: Magisk → Modüller → Depodan yükle.)
3. **Yeniden başlat.**
4. Kontrol: `su -c "cat /data/adb/kbd2touch/log.txt"` → "sanal dokunmatik oluşturuldu" ve "seçilen klavye: …" satırlarını gör.

> Zip içindeki `installer/update-binary` evrenseldir: Magisk varsa resmî `install_module`'ü kullanır,
> yoksa (KernelSU/SukiSU/APatch) modülü doğrudan `/data/adb/modules/kbd2touch` içine kendisi kurar.

### Yöntem B — Manuel kurulum (flaşlama derdi yok)
Zip'i ve bu klasörü tabletin aynı yerine çıkart, sonra root kabukta:

```bash
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
  grab_keyboard: 1   slots: 10   gamepad: 0   sanal_klavye: 1
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
Değişiklikten sonra daemon'u yeniden başlat:
```bash
su -c "pkill -f kbd2touch; sleep 1; /data/adb/kbd2touch/kbd2touch -c /data/adb/kbd2touch/config.ini &"
```

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
| `grab_keyboard` | `1` = klavyeyi EVIOCGRAB ile ele geçir (önerilen) | 1 |
| `device` | Klavyenin `/dev/input/eventX` yolu (elle seçim) | — |
| `device_match` | İsim içinde aranacak metin (`Xiaomi` gibi) | — |
| `tap_ms` | Tek dokunuşun basılı kalma süresi | 40 |
| `swipe_ms` | Sürükleme süresi | 90 |
| `virtual_keyboard` | `1` = `action = key` için ayrı sanal klavye cihazı oluştur. Bazı ROM'larda "fiziksel klavye bağlı" sayılıp ekran klavyesini gizleyebilir → sorun olursa `0` | 1 |
| `gamepad` | `1` = sanal Xbox 360 kolu da oluştur | 0 |
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
```ini
# config.ini
gamepad = 1
```
Sonra F10 ile `gtasa_gamepad` profiline geç. WASD = sol analog, oklar = sağ analog (kamera),
Space = A, F = Y, J = B, L = LT (nişan), Shift = LT (koş), C = RT (eğil), Tab/Start = menü.
Analog sürüş ve nişan alma **dokunmatik eşlemeden çok daha doğal** olur.

> ⚠️ İki profili **aynı anda** otomatiğe alma: gamepad profili bu yüzden `auto = 0` (sadece F10).
> Hangisini kullanacağına karar ver; dokunmatik istemiyorsan `com.rockstargames.gtasa.ini`'yi
> `profiles/` dışına taşı.

> 📌 GTA SA Android **klavyeyi yerel desteklemez** (2.11.13'ten sonra klavye ile hile kodu girme de kaldırıldı).
> Bu yüzden ya bu modülle eşleme yap ya da **Winlator** ile PC sürümünü çalıştır
> (bkz. araştırma raporu Bölüm 3.6).

### 🆕 Yeni oyun ekleme (3 dakika)
```bash
su
cp /data/adb/kbd2touch/profiles/genel.ini /data/adb/kbd2touch/profiles/com.tencent.ig.ini
nano /data/adb/kbd2touch/profiles/com.tencent.ig.ini
# [bind:SPACE] action=tap x=… y=… satırlarının başındaki # işaretlerini kaldır
pkill -f kbd2touch; /data/adb/kbd2touch/kbd2touch -c /data/adb/kbd2touch/config.ini &
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
| Durdur | `su -c "pkill -f kbd2touch"` |
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
