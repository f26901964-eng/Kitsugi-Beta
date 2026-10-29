# 🎮 Xiaomi Pad 6 (pipa) — Fiziksel Klavye ile Mobil Oyun Oynama: Kapsamlı Araştırma Raporu

**Tarih:** 2026-10-29  
**Cihaz:** Xiaomi Pad 6 (kod adı `pipa`, Snapdragon 870 / SM8250-AC, 2880×1800, 144 Hz)  
**Yazılım:** MIUI 14 / Android 13 + özel ROM + **SukiSU Ultra** (KernelSU tabanlı) root, özel kernel  
**Donanım:** Orijinal Xiaomi Pad 6 klavyesi (pogo-pin, USB HID olarak görünür + touchpad)  
**Amaç:** Mobil oyunları PC gibi klavyeyle oynamak (Dancing Line → Space ile dokunma, ok tuşlarıyla menü/bölüm geçme; GTA San Andreas → WASD, saldır, arabaya bin/sür…)

---

## 0) TL;DR — Sıralı Tavsiye

| # | Çözüm | Ne için | Ücret | Root/Shizuku | Durum (2026-10) |
|---|-------|---------|-------|--------------|-----------------|
| **1** | **XtMapper** (`xtr.keymapper`) | **Ana çözüm.** Klavye+fare+touchpad → çoklu dokunuş (uinput), oyun başına otomatik profil, makro, swipe/joystick | Ücretsiz, GPL-3.0, açık kaynak | **Root** (senin için en iyisi) veya Shizuku | v2.5.0 (07.09.2026), depo 28.10.2026'da güncel, 455 ⭐ |
| **2** | **KeySync** | Yedek/alternatif: klavye+fare → ekran dokunma noktaları, anti-detection jitter | Ücretsiz, Apache-2.0 | Shizuku (root gerekmez) | v1.41-beta (26.02.2026), 163 ⭐ |
| **3** | **Panda Mouse Pro** | "Uğraşmak istemiyorum, hazır oyun şablonları olsun" diyorsan; en olgun ticari oyun keymapper'ı | 5,99 $ | Root'ta otomatik aktive olur | v14.5 (09.2026), 430k+ indirme |
| **4** | **KeyMapper** (`io.github.sds100.keymapper`) | Basit tek-dokunuşlu oyunlar (Dancing Line gibi) + sistem geneli tuş kısayolları | Ücretsiz, GPL-3.0 | Erişilebilirlik (+ Shizuku/root önerilir) | **v4.6.2 (22.10.2026)**, 2782 ⭐ — en aktif proje |
| **5** | **Sanal gamepad (uinput)** | GTA SA, Minecraft, Asphalt gibi **yerel oyun kolu desteği olan** oyunlar için en temiz yol | Ücretsiz | Root veya Shizuku | Olgun hazır "klavye→gamepad" uygulaması **yok** → bu raporla birlikte **özel modül** verildi (bkz. Bölüm 9) |
| **6** | **Winlator (Cmod/GlibC) + PC GTA SA** | "Orijinal PC GTA San Andreas tuşları birebir olsun" istiyorsan: PC sürümünü tablette çalıştır | Ücretsiz, açık kaynak | Gerekmez | Cmod v13.1 (08.2026), SD870'te SA sınıfı oyunlar çalışır |

> **Tek cümlelik cevap:** Cihazında **root (SukiSU Ultra)** olduğu için en doğru kombinasyon → **XtMapper (root modu)** + **MIUI "USB hata ayıklama (Güvenlik ayarları)" kilidini açma** + gerekirse **GTA SA için sanal gamepad**. Aşağıda hepsinin adım adım kurulumu var.

---

## 1) Neden "basit bir uygulama" yetmiyor? İki teknik engel

### Engel A — Android'in kendisi
Normal bir uygulama başka bir uygulamanın ekranına dokunuş **enjekte edemez**. Bunun üç yolu var:

| Yöntem | Nasıl çalışır | Oyunlar için uygun mu? |
|--------|---------------|------------------------|
| **Erişilebilirlik `dispatchGesture()`** | KeyMapper'ın yolu | ❌ Kısıtlı: **aynı anda birden fazla noktaya dokunamaz**, "basılı tut" diğer dokunuşları iptal eder (KeyMapper bilinen sorun #1743). Tek dokunuşlu oyunlarda (Dancing Line) işe yarar, çift kollu oyunlarda (GTA) yetersiz. |
| **`injectInputEvent` (shell UID / Shizuku)** | KeySync, Mantis, scrcpy yolu | ✅ Çoklu dokunuş var. Ama **MIUI'de `INJECT_EVENTS` iznine takılır** → bkz. Engel B. |
| **`/dev/uinput` (kernel seviyesi sanal giriş cihazı)** | **XtMapper (root)**, Tincore, sanal gamepad çözümleri | ✅✅ En iyisi: sistem seni **gerçek bir dokunmatik ekran/gamepad** gibi görür. `INJECT_EVENTS` izni **gerekmez**, gecikme ~0 ms, çoklu dokunuş + sürekli sürükleme (joystick) tam destekli. Root veya shell UID ister. |

### Engel B — MIUI'ye özgü duvar (senin cihazında kritik!)
MIUI/HyperOS'ta **Geliştirici seçenekleri → "USB hata ayıklama (Güvenlik ayarları)"** kapalıysa, adb/Shizuku üzerinden **simüle dokunuş/tuş gönderimi engellenir**:

```
java.lang.SecurityException: Injecting input events requires the caller
to have the INJECT_EVENTS permission.
```

Ve bu anahtar normalde **Mi hesabı + SIM kart** ister → **tablette SIM yok, yani normal yoldan açılamaz.**

**Çözüm (sende root var, sorun değil):**

```bash
# Termux'ta veya herhangi bir root terminalde:
su
resetprop persist.security.adbinput 1      # SukiSU/KernelSU'da resetprop da kullanılabilir
setprop  persist.security.adbinput 1
getprop  persist.security.adbinput         # → 1 olmalı

# Test (bilgisayardan adb ile):
adb shell input tap 1440 900               # ekranda dokunuş görülüyorsa izin açılmıştır
```

Kalıcı olması için açılışta çalışacak bir betik ya da hazır modül:
- **CHERWING/xiaomi_usb_security_bypass** — `persist.security.adbinput / adbinstall / fastboot.enable` değerlerini Mi hesabı + SIM olmadan zorla açan Magisk/KernelSU modülü (v1.0, 09.2026).  
  `https://github.com/CHERWING/xiaomi_usb_security_bypass`
- **1067561191/miui-usbdebug-skip-sim-limit** — aynı işi Zygisk ile yapar, MIUI 14 / Android 13'te doğrulanmış (v1.0.0, 10.2026).  
  `https://github.com/1067561191/miui-usbdebug-skip-sim-limit`

> ⚠️ Bu modüller üçüncü taraf, kaynak kodları açık; kurmadan önce `service.sh` / `post-fs-data.sh` içeriğini oku. Yalnızca **üç `persist.*` özelliğini** değiştiriyorlar.

**Not:** `uinput` kullanan çözümler (XtMapper root modu, bu raporun 9. bölümündeki modül) `INJECT_EVENTS`'e ihtiyaç duymaz — ama Shizuku tabanlı her şey (KeySync, Mantis, KeyMapper Expert Mode, scrcpy) için yukarıdaki anahtar **şart**.

---

## 2) MIUI 14'te yerel (built-in) bir çözüm var mı? — Kısa cevap: Hayır

- **Game Turbo / Oyun Alanı:** performans, rahatsız etme modu, "tuşları ve hareketleri kısıtla", dokunma hassasiyeti, ekran kaydı gibi şeyler sunar — **klavye tuşunu ekran koordinatına eşleme özelliği yok**.
- **Ayarlar → Ek ayarlar → Klavye & giriş yöntemi → Fiziksel klavye:** sadece kısayol ataması (uygulama açma, sistem işlevleri) ve Xiaomi klavye tuş düzeni. Oyun içi dokunuş simülasyonu yok.
- **Xiaomi Pad 6 klavyesi** pogo-pin üzerinden bağlanır, sistemde **USB HID klavye + touchpad** olarak görünür. Bu iyi haber: eşleme uygulamaları onu "fiziksel klavye" olarak yakalayabilir (Bluetooth eşleştirme derdi yok).
- Eski **GameKeyboard+ / Tincore** gibi çözümler "yerel" hissi verir ama ikisi de 2016'dan beri güncellenmiyor, Android 13 + enforcing SELinux'ta pratik olarak ölü (Tincore için XDA rehberleri "SELinux permissive yap" diyor — önerilmez).

**Sonuç:** Üçüncü taraf eşleyici şart. MIUI'de bu işi yapan gizli bir özellik yok.

---

## 3) Adayların karşılaştırması (dürüst artı/eksi)

### 3.1 XtMapper — ⭐ birinci öneri
`https://github.com/Xtr126/XtMapper` · Dokümantasyon: `https://xtr126.github.io/XtMapper-docs/` · IzzyOnDroid'de de var

**Artıları**
- **uinput ile gerçek dokunuş enjeksiyonu** → 0 ms'ye yakın gecikme, **çoklu dokunuş**, sürekli basılı tutma, sürükleme (WASD joystick gibi).
- **Root'lu cihazda tek tuşla aktive olur** (senin durumun). Shizuku modu da var (v2.1.2+).
- Klavye + fare + **touchpad** desteği (v2.1.1+). Pad 6 klavyesinin touchpad'i fare gibi kullanılabilir → kamera/bakış kontrolü.
- **Otomatik profil değiştirme:** ön plandaki uygulamaya göre profil yükler (Dancing Line'a girince başka, GTA'ya girince başka düzen).
- **Makro**, swipe taklidi, pinch-zoom, fare tekerleği ile pürüzsüz kaydırma, profil içe/dışa aktarma (.zip) → başkalarının oyun şablonlarını kullanabilirsin.
- **`EVIOCGRAB` ile klavyeyi tamamen ele geçirme** (geliştirme dalında, Ctrl+B ile aç/kapa): oyun ham tuş basımını görmez, sadece eşlenmiş dokunuşu görür → "çift tetikleme" (hem tuş hem dokunuş) sorunu olmaz.
- GPL-3.0, açık kaynak, reklam yok, veri toplamıyor. Aktif geliştirme (depo 28.10.2026'da güncellendi, 2 geliştirici).

**Eksileri / dikkat**
- Proje **Android-x86 / BlissOS / Waydroid** odaklı doğdu. ARM telefon/tablet **resmî olarak "destekleniyor" demiyorlar**; ama native kodu evdev/uinput tabanlı (mimariye bağımlı değil), NDK tüm ABI'ler için derleniyor ve geliştiriciler "klavye paketli, root'lu fiziksel tablette test ettik" diyor. **Yine de senin cihazında denemen gerekiyor.**
- Shizuku modunda bazı cihazlarda "Connecting to server" takılması raporlanmış (issue #228, Android 17/Samsung). **Root modunda bu sorun yok.**
- Arayüz İngilizce; oyun şablonu kütüphanesi Panda kadar hazır değil (kendin yerleştireceksin).

### 3.2 KeySync — pratik yedek
`https://github.com/aka-munan/keysync` (Apache-2.0, 163 ⭐, v1.41-beta 26.02.2026)
- Shizuku ile **root'suz** çalışır; klavye/fare olaylarını ekran üzerindeki dokunma noktalarına çevirir.
- Kendi içinden oyun başlatma (launcher), dokunma noktalarına **rastgelelik (anti-detection)**, "shooting mode" (fare imleci ↔ oyun içi nişan alma geçişi).
- **Eksi:** son commit Şubat 2026 → XtMapper/KeyMapper kadar canlı değil; MIUI'de çalışması için Bölüm 1'deki `persist.security.adbinput` şart.

### 3.3 Panda Mouse Pro — hazır çözüm isteyenlere (ücretli)
`com.panda.mouse` · Play Store 5,99 $ · v14.5 (09.2026) · 430k+ indirme · 4,27/5
- Klavye+fare → dokunma; **oyun türü şablonları** (FPS/MOBA/spor), hazır bulut profil kütüphaları, hassasiyet ayarı, makro, oyun içi düzenleyici overlay.
- **Root'lu cihazda otomatik aktive olur** (PC'ye bağlanmana gerek yok). Oyunu klonlamadan başlatır.
- **Eksi:** kapalı kaynak, ücretli; kullanıcı yorumlarında "aktive oluyor sonra 3 saniyede kendini kapatıyor" şikâyetleri ve **PUBG gibi oyunlarda hesap dondurma** vakası var. Online rekabetçi oyunda kullanma.

### 3.4 KeyMapper — sistem geneli + basit oyunlar
`https://github.com/keymapperorg/KeyMapper` · `https://keymapper.app` · v4.6.2 (22.10.2026) · GPL-3.0 · 2782 ⭐ · Play Store/F-Droid
- **Çok aktif**, Türkçe çevirisi olmasa da arayüzü basit. Tetikleyici olarak **herhangi bir fiziksel klavye tuşu/kombinasyonu**, eylem olarak **"Tap screen" (ekrana dokun)**, "Swipe screen", "Pinch screen", tuş kodu, metin, kabuk komutu, intent, uygulama açma… 100+ eylem.
- **Uygulama kısıtı (constraint):** "sadece Dancing Line açıkken çalışsın" diyebilirsin → mükemmel.
- **Eksi (kritik):** erişilebilirlik `dispatchGesture` kullandığı için **aynı anda birden çok noktaya dokunamaz**, "Hold down" diğer dokunuşları iptal eder. Yani GTA'da "hem ilerle hem bakış açısını çevir" olmaz. Kendi resmî dokümanında bile "this app is NOT designed to control games" yazar.
- **Xiaomi notu:** erişilebilirlik servisinin MIUI tarafından öldürülmemesi için pil optimizasyonu kapatılmalı (bkz. Bölüm 4). Depoda "Launch App action doesn't work … due to Xiaomi permissions" gibi Xiaomi'ye özel kayıtlar var.

### 3.5 Sanal gamepad (uinput) — GTA SA için en temiz yol
- GTA San Andreas Android sürümü **fiziksel oyun kolunu yerel (HID) olarak destekler**, **klavye/fareyi desteklemez** (2.11.13 güncellemesinden sonra klavye ile hile kodu girme de kaldırıldı).
- Yani klavyeyi ekrandaki butonlara eşlemek yerine **sisteme "Xbox 360 kolu takılmış" gibi gösterirsen** oyun kendi yerel kol desteğini açar → analog sürüş, nişan alma, titreşim, her şey doğal.
- Mevcut açık kaynak projeler: `misakidebugged/Virtual-Xbox-Controller-Android` (gerçek kolu sanal Xbox 360'a çevirir, Shizuku+uinput), `xulthikrorth-hue/gamepad-bridge` (Shizuku+uinput ile sanal Xbox One/DS4 oluşturur, erken prototip), `3994305027/VirtualGamepad` (ekranda sanal tuşlar → uinput gamepad).
- **"Klavye tuşu → sanal gamepad" yapan olgun bir uygulama yok.** → Bölüm 9'da bunun için **hazır derlenmiş, senin cihaza özel modül** var.

### 3.6 Winlator ile PC GTA San Andreas (tam PC tuşları)
- `https://github.com/coffincolors/winlator/releases` (Cmod v13.1, 08.2026) — Wine + Box64 ile Windows oyunlarını Android'de çalıştırır. Gerekli: Android 11+, **ARM64 + Snapdragon** (✅ SD870), Vulkan 1.1+ (✅ Adreno 650), 6 GB+ RAM (✅).
- GTA SA / Vice City / GTA III sınıfı oyunlar bu donanımda oynanabilir seviyede çalışır; **klavye + touchpad doğrudan PC gibi çalışır**, ayrıca Cmod v13 "Controller Revolution" ile gamepad yapılandırması var.
- **Eksi:** kurulum/ince ayar ister (Turnip sürücüsü, container ayarları), pil tüketimi yüksek, bazı sürümlerde ses/görev hataları. "Mobil GTA SA'yı klavyeyle oynayayım"dan çok "PC GTA SA'yı tablette oynayayım" çözümü.
- Alternatif aynı amaç için: **ExaGear / Mobox + InputBridge** (`com.catfixture.inputbridge`) — InputBridge, fiziksel klavye tuşlarını ekran üzerindeki sanal tuşlara bağlayan, root gerektirmeyen bir IME/overlay; ExaGear/Mobox için standart araç, GTA SA PC sürümüyle yaygın kullanılıyor.

### 3.7 Uzak dur / dikkat
| Araç | Neden |
|------|-------|
| **Octopus**, "Game Keyboard" tarzı **klonlayıcı** uygulamalar | Oyunu **klon/paralel uzayda** çalıştırır → online oyunlarda **ban riski**, veri güvenliği belirsiz, reklamlı |
| **Tincore Keymapper** (3.7.8) | 2016'dan beri güncellenmiyor; touch simülasyonu için **root + SELinux permissive** istiyor (cihazı savunmasız bırakır) |
| **magiskmodule.com tarzı "Touch Input Engine / RTI / Faster Touch" modülleri** | Bunlar dokunuş **gecikmesini** azalttığını iddia eden "performans" modülleri; **tuş eşleme yapmazlar**. Çoğu kaynak kodu belirsiz, ölçülebilir etkisi tartışmalı → kernel'i bozma riski |
| **LSPosed modülleri** | "Klavye→dokunuş" yapan bakımlı bir LSPosed modülü **yok**. LSPosed yerine yukarıdaki yöntemler daha güvenli |
| **APK "mod/cracked" sürümleri** (Panda Mouse Pro mod vb.) | İmzası kırılmış giriş-enjeksiyonu yapan uygulama = en tehlikeli kombinasyon. Klavyede yazdığın her şeyi okuyabilir. Kesinlikle Play Store/GitHub/F-Droid dışından kurma |

---

## 4) Kurulumdan önce: MIUI 14 + SukiSU Ultra hazırlık listesi (10 dk)

1. **Geliştirici seçenekleri:** Ayarlar → Cihaz hakkında → *MIUI sürümü* üzerine 7 kez dokun.
2. **USB hata ayıklama:** Ayarlar → Ek ayarlar → Geliştirici seçenekleri → açık.
3. **USB hata ayıklama (Güvenlik ayarları):** açılabiliyorsa aç (Mi hesabı ister, tablette SIM olmadığı için genelde **açılamaz**) → o zaman Bölüm 1'deki `persist.security.adbinput 1` yöntemini kullan.
4. **Shizuku kur** (Shizuku tabanlı çözüm seçeceksen): `https://shizuku.rikka.app`  
   - Root'un olduğu için Shizuku'yu **root ile otomatik başlat**: Shizuku → Ayarlar → "Start with root" / ya da SukiSU yöneticisinde Shizuku'ya root izni ver. Böylece her yeniden başlatmada kablosuz hata ayıklama ile uğraşmazsın.
5. **SukiSU Ultra yöneticisinde** kullanacağın uygulamaya (XtMapper / Shizuku / KeySync) **root izni** ver.
6. **MIUI pil canavarını kapat** (eşleyici arka planda ölmesin):  
   Ayarlar → Uygulamalar → *XtMapper/KeyMapper* → Pil → **Otomatik yönetimi kapat** → "Arka plan etkinliği", "Otomatik başlatma" hepsi açık.  
   + Son kullanılanlarda uygulamayı **kilitle** (aşağı kaydırıp kilit simgesi).  
   + "Diğer uygulamaların üzerinde göster" (overlay) iznini ver.
7. **Erişilebilirlik servisi** kullanıyorsan (KeyMapper): Ayarlar → Ek ayarlar → Erişilebilirlik → *Key Mapper* → açık. MIUI bazen 30 dk sonra kapatır; pil ayarı bunu önler.
8. **Kalibrasyon için:** Geliştirici seçenekleri → **"Dokunmaları göster"** ve **"İşaretçi konumu"** aç. İşaretçi konumu, ekranın üstünde **canlı X/Y koordinatı** gösterir → oyun butonlarının koordinatını bununla okuyacaksın (eşleme yaparken şart).
9. Ekran çözünürlüğünü not al: `adb shell wm size` → Pad 6: **2880×1800** (yatay oyunda X: 0-2880, Y: 0-1800).
10. **Game Turbo**'da oyunu "Oyun Alanı"na ekle ama **"Tuşları ve hareketleri kısıtla"** seçeneğini **kapat** — fiziksel klavye tuşlarını yutabilir.

---

## 5) Yol A — XtMapper kurulumu (root, önerilen)

1. **APK:** `https://github.com/Xtr126/XtMapper/releases` → `app-release.apk` (v2.5.0) veya IzzyOnDroid (`xtr.keymapper`). GitHub'dan indir, imza `Xtr126` olmalı.
2. **Aktivasyon (root):** Uygulamayı aç → **Start** → SukiSU istemi çıkarsa izin ver. Servis çalışınca bildirim panelindeki bildirime dokunarak **editör** açılır.  
   *Manuel yol:* Termux'ta `su -c "sh /sdcard/Android/data/xtr.keymapper/files/xtMapper.sh"`
3. **Klavyeni tanıt:** Editörde bir tuşa bas → XtMapper o tuşu yakalar (Space, Enter, oklar, F tuşları dahil; geliştirme dalında alfasayısal olmayan tuşlar için `KeyUtils` eşlemesi eklendi).
4. **Eşleme türleri:** her tuş için **TAP** (tek dokunuş), **HOLD/SUSTAIN** (basılı tut), **SWIPE** (sürükleme), **joystick/anchor** (WASD için sabit merkezli sürükleme), fare butonu, tekerlek.
5. **Oyun profili:** `Profiles → New` → oyunu seç → ön plandaki uygulamaya göre **otomatik profil** (auto-profiling) aktif et.
6. **Klavye ele geçirme (çift tetiklemeyi önle):** Ayarlar → *Keyboard blocking / EVIOCGRAB* (geliştirme dalında `Ctrl+B` kısayolu). Bunu açmazsan oyun hem ham tuşu hem eşlenmiş dokunuşu görebilir.
7. **Test:** "Dokunmaları göster" açıkken Space'e bas → ekranda dokunuş izi çıkmalı. Çıkmıyorsa: root izni, overlay izni ve `getevent -c` ile klavyenin doğru cihaz olarak seçildiğini kontrol et.

**Olmazsa (ARM cihaz uyarısı):** `Settings → Advanced → Use Shizuku` moduna geç (Bölüm 1'deki `adbinput` anahtarı şart) veya Yol B'yi dene. Sorunu depoda issue olarak aç: `https://github.com/Xtr126/XtMapper/issues` (cihaz: pipa, Android 13, MIUI 14, SukiSU Ultra bilgisiyle).

---

## 6) Yol B — KeySync (Shizuku, root'suz çalışır)

1. Shizuku'yu kur ve **root ile otomatik başlat** (yoksa her açılışta kablosuz hata ayıklama ile başlatman gerekir).
2. `https://github.com/aka-munan/keysync/releases` → `app-release.apk` (v1.41-beta).
3. KeySync'i aç → Shizuku izni + **overlay** izni ver.
4. Oyunu **KeySync'in kendi launcher'ından** ekle ve başlat (FAB butonu).
5. Oyun açıkken eşleme düzenleyicisini çağır → tuşa bas → ekranda dokunma noktasını sürükleyerek yerleştir.
6. Anti-detection (dokunuş rastgeleliği) ayarını **offline oyunlarda kapatabilirsin** (daha isabetli olur), online'da açık bırak.
7. Klavye bağlandığında ekranın üstünde tanındığına dair gösterge çıkar — Pad 6 klavyesi pogo-pin olduğu için bağlanır bağlanmaz görünmeli.

---

## 7) Yol C — Panda Mouse Pro (ücretli, en az uğraş)

1. Play Store'dan satın al (5,99 $). **Mod/cracked APK kurma.**
2. Root'lu cihazda **otomatik aktive** olur; değilse Android 11+ için "cihaz üzerinde direkt aktivasyon" seçeneğini kullan.
3. Oyunu **Panda'nın içinden** başlat (klonlamaz).
4. Overlay düzenleyicisinde WASD için **joystick**, butonlar için **tap**, bakış için **fare** yerleştir; hassasiyeti ayarla.
5. Oyun başına profil kaydet. Hazır şablon kütüphanesinden GTA/aksiyon düzenlerini indirip üzerine ince ayar yapabilirsin.
6. **Uyarı:** online rekabetçi oyunlarda (PUBG Mobile, CODM) hesap riski var; yorumlarda dondurulma vakası mevcut. GTA SA/Dancing Line gibi offline oyunlarda sorun değil.

---

## 8) Yol D — KeyMapper (Dancing Line gibi tek dokunuşlu oyunlar + sistem kısayolları)

1. `https://github.com/keymapperorg/KeyMapper/releases` → `keymapper-4.6.2-foss.apk` (veya F-Droid / Play Store).
2. Erişilebilirlik servisini aç + pil optimizasyonunu kapat + overlay izni ver.
3. **Shizuku/root "Expert Mode"**'u aç (tuş kodu enjeksiyonu ve arka plan çalışması için önerilir; Android 11+).
4. Yeni key map → **Trigger:** `Space` (cihaz: fiziksel klavye) → **Action:** `Tap screen` → koordinat seç (ekran görüntüsü üzerinden noktayı işaretleyebilirsin).
5. **Constraint** ekle: *App = Dancing Line* → böylece sadece oyunda çalışır, yazı yazarken Space'i bozmaz.
6. Gerekirse `Hold down` yerine **Repeat** kullan (ritim oyunlarında daha güvenilir).

---

## 9) Yol E — Bu raporla birlikte verilen ÖZEL MODÜL: `Kbd2Touch` 🛠️

**Neden?** Hazır uygulamalar içinde "kök erişimli cihazda, klavye tuşunu **kernel seviyesinde** gerçek dokunuşa ve **sanal gamepad'e** çeviren, oyun başına profil seçen, MIUI'ye takılmayan" tek parça bir çözüm yok. Sende SukiSU Ultra olduğu için bu en temiz yol.

**Konum:** bu depoda `KBD2TOUCH_MODUL/` klasörü → **hazır paket:** `KBD2TOUCH_MODUL/dist/kbd2touch-v1.0.0.zip`  
**Detaylı kullanım:** `KBD2TOUCH_MODUL/README.md` (kurulum, kalibrasyon, config referansı, sorun giderme)

**İçinde ne var**
```
KBD2TOUCH_MODUL/
├── dist/kbd2touch-v1.0.0.zip   ← ⭐ SukiSU Ultra/KernelSU/Magisk'e flaşlanacak paket (376 KB)
├── install.sh                  ← flaşlamak istemezsen: tek komutla manuel kurulum
├── system/bin/kbd2touch        ← derlenmiş ARM64 binary (ELF64 AArch64, STATİK — bionic'e bağımsız)
├── src/kbd2touch.c             ← kaynak kod, ~1000 satır C, tamamen açık
├── config.ini                  ← ana ayarlar (ekran/rotation/kısayollar/gamepad)
├── profiles/
│   ├── com.cmplay.dancingline.ini    ← Dancing Line: Space=dokun, ←→=bölüm geç, Esc=geri
│   ├── com.rockstargames.gtasa.ini   ← GTA SA dokunmatik: WASD joystick, F/J/Shift/Tab…
│   ├── gtasa_gamepad.ini             ← GTA SA sanal kol: WASD=analog, PC tuş düzeni
│   └── genel.ini                     ← yeni oyun için boş şablon
├── build.sh / pack.sh          ← zig ile yeniden derleme + zip paketleme
└── README.md                   ← tam dokümantasyon
```

**Doğrulanan teknik durum** (bu sandbox'ta yapılanlar)
- `gcc -Wall -Wextra` ile **0 hata / 0 uyarı**
- `zig cc -target aarch64-linux-musl -O2 -static` → `ELF64, Machine: AArch64, Type: EXEC, no dynamic section` (statik → cihazda kütüphane derdi yok)
- Config ayrıştırma testleri geçti: 4 profil / 55 eşleme yüklendi, paket-adı otomatik eşleme, `auto = 0` (sadece F10) profili, `ESC → out_key=158 (KEY_BACK)` dönüşümü doğrulandı
- ⚠️ **Tablette çalıştırılmadı** (sandbox'ta `/dev/uinput` yok). Cihazda ilk iş: `--calibrate` ile köşe testi.

**Ne yapar**
- `/dev/input/event*` üzerinden klavyeni bulur ve (isteğe bağlı) **`EVIOCGRAB` ile tamamen ele geçirir** → oyun ham tuşu görmez, çift tetikleme olmaz.
- `/dev/uinput` ile **sanal çoklu-dokunmatik ekran** oluşturur (Protocol B, 10 parmak) → sistem bunu gerçek dokunmatik panel gibi görür. `INJECT_EVENTS` izni **gerekmez**, MIUI engeline **takılmaz**, çoklu dokunuş + sürekli basılı tutma + sürükleme çalışır.
- Ayrıca **sanal Xbox gamepad** oluşturabilir → GTA SA gibi oyunlar **yerel kol desteğini** açar (analog sürüş!).
- **Oyun başına profil + otomatik geçiş:** ön plandaki pakete göre (`com.rockstargtasandreasand`, `com.cmplay.dancingline`…) profil değiştirir.
- **F9** ile aç/kapa, **F10** ile profil değiştir, **F11/F12** ile kalibrasyon (köşe dokunuşları) → ekran dönüşünde koordinatların doğru olup olmadığını 10 saniyede anlarsın.
- Config dosyaları: `Dancing Line`, `GTA San Andreas (dokunmatik)`, `GTA San Andreas (gamepad)` hazır profillerle geliyor.

**Nasıl kurulur:** `KBD2TOUCH_MODUL/README.md` içinde adım adım (Termux ile kurulum, Magisk/KernelSU zip'i olarak flaşlama, ya da sadece binary'yi çalıştırma).

> ⚠️ **Dürüst uyarı:** Bu modül senin cihazında **test edilmedi** (ben bir sandbox'tayım, tablette çalıştıramıyorum). Derleme `zig cc -target aarch64-linux-musl` ile yapıldı, statik olarak bağlandı (bionic'e bağımlı değil). Kaynak kodu `src/kbd2touch.c` içinde — tamamen açık. Önce **kalibrasyon modunu** (F11) çalıştırıp köşe dokunuşlarını kontrol et, sonra oyun profillerini kullan.

---

## 10) Oyun bazlı hazır tarifler

### 10.1 Dancing Line (`com.cmplay.dancingline`) — Space ile dokunma
Oyun tek dokunuşla yön değiştirir; ekranda **nerede** dokunduğun önemli değildir (HUD butonlarına denk gelmesin yeter).

| Tuş | Eylem | Not |
|-----|-------|-----|
| **Space** (veya Enter) | Ekranın ortasına/sol-alt boşluğuna **tap** | Oyun içi yön değiştirme |
| **↑ / ↓** | Menüde gezinme | ⚠️ Çoğu Android menüsünde **D-pad tuşları zaten çalışır** (odak gezinmesi). Çalışmıyorsa ↑/↓'yi liste öğesi koordinatlarına **tap** olarak eşle |
| **← / →** | Bölüm/seviye sayfaları arasında **swipe** | Soldan sağa / sağdan sola kaydırma jesti |
| **Esc** | Geri (KEYCODE_BACK) | Menüden çıkış |
| **P** | Duraklat butonuna tap | Sağ üst köşe koordinatı |

**İpucu:** Koordinatları oyunun **oynarken görünen ekranına** göre al (Gameplay sırasında "İşaretçi konumu" açıkken dokun ve X/Y'yi oku). Telefon/tablet **yatay** konumdayken al — dikeyken alınan koordinat yatayda yanlış olur.

### 10.2 GTA San Andreas Android — PC tuşları → mobil

**En iyi yol: gamepad.** Oyun yerel olarak oyun kolunu destekliyor; sanal gamepad oluşturduğunda oyun "kol bağlı" moduna geçer ve analog sürüş/nişan alma doğal çalışır.

**PC tuşu → gamepad eşlemesi (hazır profilde var):**

| PC (orijinal) | Gamepad | Mobil GTA SA'da ne yapar |
|---|---|---|
| W A S D | Sol analog | Yürü / aracı sür |
| Fare hareketi | Sağ analog | Kamera / bakış |
| Space | A (BTN_SOUTH) | Zıpla / el freni (araçta) |
| F | Y (BTN_NORTH) | Arabaya bin-in / özel aksiyon |
| Sol tık / Ctrl | B (BTN_EAST) veya R2 | Saldır / ateş et |
| Shift | L2 / LT | Koş (sprint) |
| C / Ctrl | B uzun basma | Eğil |
| Tab | SELECT | Harita |
| Esc | START | Duraklat menüsü |
| Q / E | LB / RB | Silah değiştir |
| R | X (BTN_WEST) | Şarjör değiştir |
| 1-9 | D-pad | Silah/öğe seçimi |

**Alternatif: dokunmatik eşleme** (gamepad istemezsen). Ekranın sol yarısındaki **sanal joystick**'e WASD'yi "sürükle" olarak bağla:

| Tuş | Eylem (2880×1800 yatay için yaklaşık) |
|---|---|
| W | Joystick merkezinden yukarı sürükle ≈ (600, 1200) → (600, 1000) |
| S | Aşağı sürükle ≈ (600, 1200) → (600, 1400) |
| A | Sola sürükle ≈ (600, 1200) → (420, 1200) |
| D | Sağa sürükle ≈ (600, 1200) → (780, 1200) |
| Space | Zıpla butonuna tap |
| F | "Arabaya bin" butonuna tap |
| Sol tık / J | Saldırı/ateş butonuna tap |
| Shift | Koş butonuna basılı tut |
| Tab | Harita ikonuna tap |
| Esc | Duraklat ikonuna tap |

> Koordinatlar **senin HUD düzenine göre** değişir. "İşaretçi konumu" ile her butona bir kez dokunup gerçek X/Y'yi yaz — 15 dakikalık iş, sonra profil kalıcı.

**Hile kodları:** Android sürümünde **2.11.13'ten sonra klavye ile hile kodu girme kaldırıldı** (eskiden PC kodları doğrudan çalışıyordu). Kod istiyorsan ya eski sürüm APK (2.00) ya da PC sürümünü Winlator ile çalıştır.

### 10.3 Genel "PC gibi" şablon (çoğu mobil oyun için)
- **Sol el:** W/A/S/D → sanal joystick sürükleme (anchor + direction)
- **Sağ el:** fare hareketi → kamera sürükleme (touchpad'i fare olarak kullanmak daha rahat)
- **J / sol tık** → ana aksiyon butonu, **K / sağ tık** → ikincil, **L** → nişan al (basılı tut), **Space** → zıpla, **Shift** → koş, **E** → etkileşim, **R** → yeniden doldur, **Tab** → envanter/harita, **Esc** → duraklat, **1-5** → slot butonları
- **F9** → eşleyiciyi aç/kapa (yazı yazman gerektiğinde anında kapat)

---

## 11) Ban / güvenlik / risk

- **Offline oyunlar** (Dancing Line, GTA SA, Minecraft, emülatörler): risk **yok denecek kadar az**. Oyun tek başına çalışan bir uygulama; girişi kimin ürettiğini ayırt etmesi zor.
- **Online rekabetçi oyunlar** (PUBG Mobile, CODM, Free Fire, Wild Rift): eşleyici kullanımı **hizmet şartlarını ihlal edebilir**, anti-cheat enjekte edilmiş dokunuşları/model davranışını tespit edebilir → **hesap banı**. Panda Mouse Pro yorumlarında "hesabım donduruldu" vakası var; KeySync/XtMapper bu yüzden "jitter" (dokunuş rastgeleliği) ekliyor. **Tavsiyem: online rekabetçi oyunda kullanma.**
- **Yetki hijyeni:** giriş enjekte eden uygulama, klavyede yazdığın **her şeyi** (şifreler dahil) okuyabilir. Bu yüzden:
  - Sadece **GitHub/F-Droid/Play Store**'dan indir, imzayı kontrol et.
  - **Açık kaynak** olanı tercih et (XtMapper, KeyMapper, KeySync).
  - Cracked/mod APK **kesinlikle** kurma.
  - Oyun bittiğinde eşleyiciyi kapat (F9 / bildirim) — normal yazı yazarken devrede kalmasın.
- **Root tarafı:** SukiSU Ultra + özel kernel zaten riski üstlenmiş bir kurulum. Eşleyici için **SELinux'u permissive yapma** (Tincore'un eski rehberleri bunu ister) — gereksiz ve cihazı savunmasız bırakır. uinput tabanlı çözümler enforcing SELinux'ta root ile çalışır.

---

## 12) Sorun giderme

| Belirti | Sebep / çözüm |
|---|---|
| `SecurityException: INJECT_EVENTS` (Shizuku tabanlı uygulama) | `persist.security.adbinput 1` değil → Bölüm 1. Modül kur veya `resetprop` ile elle ayarla, yeniden başlat |
| Tuşa basıyorum, hiçbir şey olmuyor | 1) Eşleyici servisi çalışıyor mu? 2) Overlay izni var mı? 3) MIUI uygulamayı arka planda öldürmüş mü (pil ayarı)? 4) Game Turbo "tuşları kısıtla" açık mı? |
| Hem tuş hem dokunuş çalışıyor (çift tetikleme) | Klavye **grab**'ını aç: XtMapper'da keyboard blocking (EVIOCGRAB) / KeyMapper'da "do not remap" istisnası / Kbd2Touch'ta `grab_keyboard = 1` |
| Dokunuşlar yanlış yerde (kaymış/ters) | Ekran **dönüşü**. "Dokunmaları göster" + kalibrasyon modu ile köşeleri test et; Kbd2Touch'ta `rotation = 0/90/180/270` veya `swap_xy/invert_x/invert_y` |
| Basılı tutunca diğer dokunuşlar iptal oluyor | Erişilebilirlik (`dispatchGesture`) sınırı → KeyMapper yerine **uinput** tabanlı çözüme geç (XtMapper root / Kbd2Touch) |
| Klavye tuşları oyun dışında da eşleniyor (yazı yazamıyorum) | Profili **uygulama kısıtıyla** sınırla (KeyMapper constraint / XtMapper auto-profile / Kbd2Touch `[app:paket]` bölümü) + aç/kapa kısayolu (F9) kullan |
| Eşleyici 5-10 dk sonra ölüyor | MIUI pil yönetimi → otomatik başlatma + arka plan etkinliği + "son kullanılanlarda kilitle" + `adb shell dumpsys deviceidle whitelist +paket` |
| Xiaomi klavyesi hiç görünmüyor | Ayarlar → Bağlı cihazlar → **Xiaomi klavye ayarları → etkinleştir**; klavyeyi çıkarıp tak ve yeniden başlat |
| Oyun "kol bağlı değil" diyor ama sanal gamepad var | Oyun bazı cihazlarda yalnızca **Xbox/DS4 VID-PID**'yi kabul eder → sanal cihazın adını/ID'sini "Microsoft X-Box 360 pad" olarak ayarla (Kbd2Touch'ta `gamepad_name`) |

**Teşhis komutları**
```bash
adb shell getevent -pl              # tüm giriş cihazları (klavyenin event düğümünü bul)
adb shell getevent -c /dev/input/eventX   # tuşa basınca olay akıyor mu
adb shell dumpsys input | grep -A5 "Classes"  # Android'in gördüğü cihaz sınıfları
adb shell wm size                   # çözünürlük
adb shell settings put secure show_touches 1
adb shell settings put secure pointer_location 1
adb shell dumpsys window | grep mCurrentFocus   # ön plandaki uygulama
adb shell dumpsys deviceidle whitelist +xtr.keymapper
```

---

## 13) Kaynaklar (tümü 29.10.2026'da doğrulandı)

**Birincil öneriler**
- XtMapper: https://github.com/Xtr126/XtMapper · Docs: https://xtr126.github.io/XtMapper-docs/ · IzzyOnDroid: https://apt.izzysoft.de/fdroid/index/apk/xtr.keymapper
- KeyMapper: https://github.com/keymapperorg/KeyMapper · Docs: https://keymapper.app · F-Droid: https://f-droid.org/en/packages/io.github.sds100.keymapper/
- KeySync: https://github.com/aka-munan/keysync
- Panda Mouse Pro: Play Store `com.panda.mouse` · https://pandagame.app/a (aktivasyon rehberi)

**MIUI giriş izni (INJECT_EVENTS) modülleri**
- https://github.com/CHERWING/xiaomi_usb_security_bypass
- https://github.com/1067561191/miui-usbdebug-skip-sim-limit

**Sanal gamepad (uinput)**
- https://github.com/misakidebugged/Virtual-Xbox-Controller-Android
- https://github.com/xulthikrorth-hue/gamepad-bridge
- https://github.com/3994305027/VirtualGamepad

**PC oyunlarını tablette çalıştırma**
- Winlator Cmod: https://github.com/coffincolors/winlator/releases
- Resmî Winlator: https://github.com/brunodev85/winlator/releases
- InputBridge (ExaGear/Mobox için sanal klavye): `com.catfixture.inputbridge`

**Cihaz/root bağlamı (pipa)**
- XDA Pad 6 unlock/root rehberi: https://xdaforums.com/t/unlock-root-picture-guide-unlock-bootloader-roms-and-root-xiaomi-pad-6-6-pro.4644077/
- Pad 6 kernel + SukiSU Ultra örnekleri: https://github.com/cug211/kernel_xiaomi_pad6_mod · https://github.com/xiaocutedog/moon-pipa-sukisu

**Diğer**
- Shizuku: https://shizuku.rikka.app
- KeyMapper bilinen sorunlar (çoklu dokunuş yok): https://docs.keymapper.club/known-issues/
- scrcpy-mask (PC'den tablette oynama alternatifi): https://www.appinn.com/scrcpy-mask/

---

## 14) Ben senin yerinde olsam — 30 dakikalık plan

1. `persist.security.adbinput 1` ayarla (root terminal) → **her yolun ön şartı**. (2 dk)
2. **XtMapper**'ı GitHub'dan kur, root ile aktive et, `getevent` ile klavyenin bulunduğunu doğrula. (5 dk)
3. **Dancing Line** profili: Space → ekran ortasına tap, Esc → back. Oyna, gecikmeyi hisset. (5 dk)
4. **GTA SA** profili: önce **sanal gamepad** dene (Kbd2Touch modülü veya gamepad-bridge); oyun kolunu görürse WASD analog sürüş hazır. Görmezse **dokunmatik eşleme** (W/A/S/D joystick sürükleme + buton tapleri). (15 dk)
5. Otomatik profili ve **F9 aç/kapa** kısayolunu ayarla → klavye normal yazı yazarken eşleme kapalı olsun.
6. XtMapper ARM'da sorun çıkarırsa → **KeySync** (Shizuku) veya **Panda Mouse Pro** (ücretli, hazır şablonlar).
7. Online rekabetçi oyunlarda kullanma.

Hazır modül için → `KBD2TOUCH_MODUL/README.md`
