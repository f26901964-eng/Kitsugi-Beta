# KİTSUGİ — ÇÖKME / KASMA / DİL SORUNU KÖK NEDEN ANALİZİ

**Tarih:** 2026-10-11
**İncelenen rapor:** `Kitsugi_Crash_Report (15).txt` (3.4 MB, 35.008 satır)
**Sürüm:** 2.4.235-beta.29861112
**Cihaz:** Xiaomi / MIUI, 8 GB RAM, 64-bit (arm64)

---

## 0. YÖNETİCİ ÖZETİ — "Kasma donma sebebi dil dosyası mı?"

**Hayır.** Ölçümler bunu doğrulamıyor. İki ayrı gerçek var ve birbirine karışmış:

| Semptom | Gerçek sebep | Dil dosyasıyla ilgisi |
|---|---|---|
| **Uygulama donup kapanıyor (sessiz çökme)** | `libhwui.so` içinde **RenderThread**'de SIGSEGV | ❌ İlgisiz |
| **Kasıp donuyor (300–1100 ms kare süreleri)** | Ana iş parçacığı mesaj yağmuru + GPU katmanları | ❌ İlgisiz |
| **Dil İngilizce seçili ama her yer Türkçe** | Android 13+ dil yönetimi + eksik dil beyanı + koda gömülü Türkçe | ✅ **Gerçek hata** |
| **Türkçe'ye geri dönmeye çalışınca çökme** | `recreate()` yarışı + beyan edilmemiş `tr` dili | ⚠️ **Tetikleyici** |

Yani kullanıcı haklı olarak "dil değiştirince çöktü" gözlemini yapmış — ama dil
değişimi **bombanın kendisi değil, fünyesi**. Bomba render hattında.

Bunu rapordan sayılarla kanıtlayacağım.

---

## 1. ÇÖKMENİN GERÇEK SEBEBİ: `libhwui` RenderThread SIGSEGV

Raporun içinde **7 ayrı native çökme izi** var. Hepsini `/proc/self/maps` ile
adres çözümlemesine tabi tuttum. Sonuç çarpıcı biçimde tek tip:

```
                     ÇÖKME #3   #4    #5    #6    #7
signal               SIGSEGV  SIGSEGV ...  hepsi SIGSEGV (11)
fault_addr           0x20     0x20   0x20  0x20  0x20
thread               RenderThread  ——  TAMAMI RenderThread
pid (farklı oturum)  15790   18716  25099 11543  3845
```

Adres çözümlemesi (crash #7, en yeni — 2026-10-10 14:24):

```
#0  libkitsugi_crash_handler.so  +0x3b98   ← bizim yakalayıcı
#1  libsigchain.so               +0x25c
#2  [vdso]                       +0x5dc
#3  /system/lib64/libhwui.so     +0x1ff650   ← ███ ÇÖKME BURADA
#4  /system/lib64/libhwui.so     +0x779c4
#5  /system/lib64/libhwui.so     +0x109550
#6  /system/lib64/libhwui.so     +0x56618
...
#42 /system/lib64/libhwui.so     +0x86560
#43 /system/lib64/libhwui.so     +0x75e70
#44 /system/lib64/libhwui.so     +0x97248
#45 /system/lib64/libutils.so    +0x559c    ← Thread::_threadLoop
#46 /system/lib64/libc.so        +0xad5cc
#47 /system/lib64/libc.so        +0x46fc0   ← __start_thread
```

### Bu ne demek?

* **`libhwui.so`** = Android'in donanım hızlandırmalı arayüz çizim kütüphanesi
  (Skia + RenderNode + GPU katmanları). Uygulama kodumuz **değil**, sistem kodu.
* **`RenderThread`** = arayüzü çizen ayrı iş parçacığı. Kotlin/Java kodu burada
  çalışmaz; bu yüzden `UncaughtExceptionHandler` hiç devreye girmiyor ve
  raporun kendisi de "SEBEP BULUNAMADI (sessiz SIGKILL)" diyor.
* **`fault_addr=0x20`** = null bir işaretçinin 32 bayt ilerisinden alan okuma.
  Klasik anlamı: **yok edilmiş ya da hiç oluşturulmamış bir nesnenin alanına
  erişim.** RenderNode / SkImageFilter bağlamında bu, "serbest bırakılmış bir
  GPU katmanını çizmeye çalışmak" demektir.
* Yığındaki `#8…#41` arası **tekrar eden döngü** (`+0x109550 → +0x53590 →
  +0x3bba0 → +0x3c2dc → …` defalarca) — bu bir DisplayList/RenderNode gezisi.

> **Sonuç:** Çökme, Android'in çizim hattında, bir GPU katmanı (layer) ile
> ilgili. Dil dosyaları saf metin kaynağıdır; çizim hattıyla hiçbir temasları
> yoktur. **Dil dosyası bu çökmeyi üretemez.**

### Peki o katmanı kim açıyor?

Kod tabanında `RenderEffect` tabanlı bulanıklığı **API kontrolü olmadan** kullanan
üç yer vardı. `Modifier.blur()` tam olarak `RenderEffect.createBlurEffect()`
çağırır ve GPU'da katman açar:

| Dosya | Eski kod | Sorun |
|---|---|---|
| `KitsugiCinematicLoadingScreen.kt:85` | `.blur(16.dp)` | Koşulsuz **+** içerideki `KitsugiNsfwImage` de bulanıklık uyguluyor → **üst üste iki GPU katmanı** |
| `MangaDetailScreen.kt:240` | `.blur(24.dp)` | Koşulsuz, **24 dp büyük yarıçap** |
| `KitsugiAddonDetailDialog.kt:223` | `.blur(20.dp)` | Koşulsuz, 20 dp |

Özelleştirilmiş `libhwui` sürümlerinde (özellikle MIUI/HyperOS) büyük yarıçaplı
RenderEffect'ler bilinen bir SIGSEGV kaynağı. Bu dosyalardan ikincisi
(Manga detay) tam olarak kullanıcının gezindiği yerlerden biri.

---

## 2. KASMA / DONMA: Dil dosyası DEĞİL, kanıtı zaman damgalarında

Raporun "LOGCAT KANIT SATIRLARI" bölümünde `PerfMonitor doFrame` değerleri var.
60 fps için kare bütçesi **16,6 ms**. Ölçülenler:

```
00:18:45  doFrame time=1065 ms   ← OTURUMUN 3. SANİYESİNDE
00:18:58  doFrame time=1101 ms   (Compose AndroidUiDispatcher)
00:19:00  doFrame time= 563 ms
00:19:18  doFrame time= 586 ms
00:20:27  doFrame time= 408 ms
00:20:33  doFrame time= 530 ms
00:40:46  doFrame time= 876 ms
00:41:29  doFrame time= 642 ms
00:41:33  doFrame time= 736 ms
00:41:44  doFrame time= 794 ms
00:42:41  doFrame time= 756 ms
00:45:45  doFrame time= 325 ms
00:47:17  doFrame time= 616 ms
```

**Kritik nokta:** İlk 1065 ms'lik kare, oturum açıldıktan **3 saniye** sonra,
saat `00:18:45`'te oluşmuş. Kullanıcının dil değiştirdiği an ise `00:48` civarı
— yani **oturumun sonunda**. Arada 30 dakika var.

> Kasma, dil değişiminden **çok önce** başlamış. Dolayısıyla dil dosyası
> kasmanın sebebi olamaz.

### Ana iş parçacığı mesaj yağmuru

MIUI'ın ANR izlemesi şunu yazmış:

```
In recent 20s, total historyMsgCount=512
messages for the same target Choreographer$FrameHandler
    execute time>50ms (count=177 totalwalltime=980ms)
messages for the same target android.os.Handler
    execute time>50ms (count=165 ... AndroidUiDispatcher$dispatchCallback)  ← Compose
messages for the same target ViewRootImpl$ViewRootHandler
    execute time>50ms (count=3  ... AndroidPrefetchScheduler)               ← LazyList prefetch
```

Mesaj sıra numaralarına bakarak ana iş parçacığının yükünü hesaplayabiliriz:

* `00:18:42` → seq=2, `00:20:33` → seq=13004 ⇒ **~117 mesaj/saniye**
* `00:41:33` → seq=20605, `00:48:16` → seq=107751 ⇒ **~216 mesaj/saniye**

Ana iş parçacığı 30 dakika boyunca hiç boş kalmamış, saniyede 100–200 mesaj
işlemiş. Bu, "donma" hissinin doğrudan açıklaması.

### İkinci güçlü ipucu: sürekli pencere oluşturuluyor

Logcat'te `ForceDarkHelperStubImpl: setViewRootImplForceDark` satırı 30 dakikada
**40+ kez**, genelde ikili gruplar hâlinde geçiyor:

```
00:45:38.237  setViewRootImplForceDark: false for MainActivity@38e93b3
00:45:38.289  setViewRootImplForceDark: false for MainActivity@38e93b3
00:45:41.156  ...  00:45:41.185  ...
00:46:16.849  ...  00:46:16.908  ...
```

Bu çağrı **yeni bir Window/ViewRootImpl eklendiğinde** tetiklenir. Tek
Activity'li bir Compose uygulamasında bunun anlamı: sürekli **Dialog / Popup /
DropdownMenu** açılıp kapanıyor. Aynı anda `AndroidPrefetchScheduler`
(LazyList ön yükleme) de ANR geçmişinde görünüyor.

> **Takip edilmesi gereken yol:** Keşfet raflarında hem yoğun LazyList ön
> yüklemesi hem de sık pencere (dialog) oluşturma var. Bu ikisi birlikte
> 216 mesaj/saniye üretiyor.

---

## 3. DİL SORUNU: "İngilizce seçili ama her yer Türkçe" — BURADA GERÇEK HATALAR VAR

Kullanıcının bu gözlemi doğru ve üç ayrı sebebi var.

### 3.1 Sebep 1 — Türkçe, Android 13+ için "desteklenen dil" olarak hiç beyan edilmemiş ❗

`app/build.gradle.kts` içinde şu vardı:

```kotlin
androidResources {
    generateLocaleConfig = true   // ← otomatik üretim
}
```

Bu ayar, dil listesini `values-*` klasörlerinden üretir. Projede ise:

```
app/src/main/res/values/     → NİTELİKSİZ (Türkçe metinler burada)
app/src/main/res/values-en/  → İngilizce
app/src/main/res/values-v31/ → sürüm niteleyicisi, dil değil
```

**Niteliksiz `values/` bir dil niteleyicisi taşımaz.** AGP onu bir yerel (locale)
olarak saymaz. Sonuç: üretilen `locale-config` içinde **tek bir dil kalıyordu:
`en`.**

Android 13 (API 33) ve üzerinde uygulama dili çerçevenin `LocaleManager`'ı
tarafından yönetilir ve **yalnızca beyan edilmiş diller kabul edilir.** Bu
yüzden:

* Sistemin "Uygulama dili" ayarında Türkçe hiç görünmüyordu.
* Türkçe'ye geçiş isteği filtreleniyor / yok sayılıyordu → arayüz eski dilde
  takılıp kalıyordu. Kullanıcının "İngilizce seçili ama Türkçe görünüyor"
  gözleminin ve "Türkçe'ye dönerken çöktü" şikâyetinin kökü büyük ölçüde bu.

**Düzeltme:** Otomatik üretim kapatıldı, beyan elle verildi
(`res/xml/locales_config.xml`, `en` + `tr`) ve manifest'e
`android:localeConfig="@xml/locales_config"` eklendi.

> Not: Türkçe metinler niteliksiz `values/` altında olduğu için `tr`
> seçildiğinde kaynak çözümlemesi yine doğru yere düşer; 340 KB'lık bir
> `values-tr/` kopyası oluşturmaya gerek yok.

### 3.2 Sebep 2 — Dil değişimi Android 13+ ile uyumsuz yöntemle yapılıyordu

Eski kod:

```kotlin
fun updateLocale(context: Context, tag: String) {
    ...
    java.util.Locale.setDefault(locale)
    context.resources.updateConfiguration(config, displayMetrics)  // API 25+ deprecated
    findActivity(context)?.recreate()                              // ← tehlikeli
}
```

`updateConfiguration()` API 25'ten beri deprecated ve Android 13+ üzerinde
çerçevenin kendi per-app dil kararıyla **çakışıyor**: siz bir dil dayatıyorsunuz,
`LocaleManager` üstüne kendi kararını yazıyor. Activity yeniden kurulunca
kaynaklar eski dile dönüyordu.

**Düzeltme:** API 33+ üzerinde iş tamamen `AppCompatDelegate.setApplicationLocales()`
'e devredildi (AppCompat 1.7.0 zaten mevcut). API < 33 için eski yol korundu.

### 3.3 Sebep 3 — `recreate()` bir Compose tıklama geri çağrısının İÇİNDEN çağrılıyordu ❗ (çökme fünyesi)

`onAppLanguageSelected` bir menü öğesinin `onClick`'inden geliyor:

```kotlin
internal fun SettingsContext.onAppLanguageSelected(lang: String) {
    coroutineScope.launch { settingsDataStore.setAppLanguage(lang) }
    LocaleCache.updateLocale(context, lang)      // → activity.recreate()
}
```

`recreate()` burada **senkron** çağrılıyordu; yani ana iş parçacığı tıklama
olayını dağıtırken, o olayın ortasında tüm görünüm hiyerarşisi
(RenderNode'lar + GPU katmanları) yıkıma gidiyordu. O sırada `RenderThread`
çizim yapıyorsa serbest bırakılmış bir katmana erişiyor → **tam olarak
raporda gördüğümüz `SIGSEGV fault_addr=0x20` / `libhwui.so` / `RenderThread`.**

**Düzeltme:** `recreate()` artık doğrudan çağrılmıyor; `Handler.post {}` ile
looper kuyruğunun **sonuna** bırakılıyor, böylece mevcut olay temiz biçimde
tamamlanıyor. Ayrıca `isFinishing` / `isDestroyed` kontrolleri eklendi.

### 3.4 Sebep 4 — Koda gömülü ~290 Türkçe metin (çevirilemez)

Kaynak dosyalarının kendisi aslında **sağlam**. Denetim sonucu:

```
values/strings.xml     : 4194 string      values-en/strings.xml : 4194 string
Eksik anahtar          : 0                Yinelenen anahtar     : 0
values-en içinde Türkçe kalmış / TR ile aynı : 431  (çoğu "Kitsugi",
     "TMDB", "Romaji", "Modern", "IMDb" gibi özel ad — normal)
```

Yani **İngilizce dosyası eksiksiz**; "her yer Türkçe"nin sebebi eksik çeviri
değil. Asıl sebep Compose/Kotlin kodunun içine yazılmış sabit metinler:

```
Text("...") ile Türkçe        : 157
Toast.makeText(...) Türkçe     : 110
showSnackbarMessage("...")    :  23
                               ────
                               ~290
```

Bunlar hiçbir zaman çevrilmez. Örnek — `ExploreComponents.kt`:

```kotlin
if (isTmdbUpcoming) "${mins} dk sonra çıkıyor" else "${mins} dk sonra yayınlanacak"
... "$days gün sonra yayınlanacak" ... "$months ay sonra yayınlanacak"
```

Bu geri sayım metni İngilizce arayüzde bile Türkçe çıkıyor. (Bu dosya ayrı bir
iş olarak kaynak dosyalarına taşınmalı.)

---

## 4. BULUNAN VE DÜZELTİLEN EK HATALAR

### 4.1 Yüzde işareti yanlış yerde (12 dize)

Türkçe dosyada `%` sayının solundaydı, İngilizcede sağında:

| Anahtar | Eski (TR) | Yeni (TR) | EN |
|---|---|---|---|
| `sub_opacity_normal` | `Normal (%0)` | `Normal (0%)` | `Normal (0%)` |
| `sub_opacity_low` | `Düşük (%25)` | `Düşük (25%)` | `Low (25%)` |
| `sub_opacity_max` | `Maksimum (%100)` | `Maksimum (100%)` | `Maximum (100%)` |
| `pd_volume_cap_desc` | `… (%100 - %200)` | `… (100% - 200%)` | `… (100% - 200%)` |
| … ve 8 dize daha | | | |

Kullanım yerlerini tek tek doğruladım: hepsi **argümansız**
(`stringResource(R.string.x)`) çağrılıyor, yani `String.format` devreye
girmiyor. Bu yüzden **`%%` kaçışı YANLIŞ olurdu** (ekranda çift yüzde
görünürdü); doğru çözüm işareti yerine taşımaktı. TR ve EN artık birebir uyumlu.

### 4.2 `attachBaseContext` tek bir hata yüzünden uygulamayı açtırmayabilirdi

`MainActivity.attachBaseContext` içindeki dil uygulaması korumasızdı. Bozuk bir
etiket (ör. SharedPreferences'a yanlış bir değer kaçmışsa) dosyanın **en erken**
noktasında istisna fırlatıp uygulamayı açılmaz hâle getirebilirdi. Artık
`runCatching` ile korunuyor; hata durumunda sistem diliyle normal açılıyor.

---

## 5. YAPILAN DEĞİŞİKLİKLER

| # | Dosya | Değişiklik |
|---|---|---|
| 1 | `app/src/main/res/xml/locales_config.xml` | **YENİ** — `en` + `tr` dil beyanı |
| 2 | `app/src/main/AndroidManifest.xml` | `android:localeConfig="@xml/locales_config"` |
| 3 | `app/build.gradle.kts` | `generateLocaleConfig = false` |
| 4 | `KitsugiApplication.kt` (`LocaleCache`) | API 33+ için `AppCompatDelegate`; `commit()`; `recreate()` kuyruğa alındı; koruma blokları |
| 5 | `MainActivity.kt` | `attachBaseContext` hata güvenli + geçersiz etiket kontrolü |
| 6 | `KitsugiNsfwImage.kt` | **YENİ** `Modifier.safeBlur()` + `MAX_SAFE_BLUR_RADIUS = 16.dp`; iç kullanım güncellendi |
| 7 | `KitsugiCinematicLoadingScreen.kt` | Çift GPU bulanıklık katmanı kaldırıldı |
| 8 | `MangaDetailScreen.kt` | Koşulsuz `.blur(24.dp)` → `.safeBlur(16.dp)` |
| 9 | `KitsugiAddonDetailDialog.kt` | Koşulsuz `.blur(20.dp)` → `.safeBlur(16.dp)` |
| 10 | `TvDetailScreen.kt` | `Modifier.blur` → `.safeBlur()` |
| 11 | `app/src/main/res/values/strings.xml` | 12 dizede yüzde işareti düzeltildi |

---

## 6. DOĞRULAMA DURUMU

Yapılamayan: **derleme.** Bu ortamın ağ erişimi `github.com`, `pypi.org` ve
`registry.npmjs.org` ile sınırlı; Android Gradle bağımlılıkları
`dl.google.com`'dan geldiği için `./gradlew assembleDebug` çalıştırılamadı.
Değişiklikler elle gözden geçirildi; XML dosyalarının geçerliliği
(`ElementTree` ile ayrıştırma) ve `strings.xml` bütünlüğü doğrulandı.

**Lütfen derleyip cihazda şunları test edin:**

1. Ayarlar → Dil → **Türkçe** → artık çökmeden geçiyor mu?
2. Ayarlar → Dil → **İngilizce** → metinler gerçekten İngilizce oluyor mu?
   (Koda gömülü ~290 metin hâlâ Türkçe kalacak — 3.4)
3. Ayarlar → Dil → **Sistem** → cihaz dilini takip ediyor mu?
4. Uygulamayı öldürüp yeniden açın → seçili dil **kalıcı** mı?
5. Manga detay ve eklenti detay sayfalarında bulanıklık hâlâ çalışıyor mu?

---

## 7. SIRADA NE OLMALI (öncelik sırasıyla)

1. **Ana iş parçacığı profillemesi.** 216 mesaj/saniye nereden geliyor?
   Android Studio Profiler → CPU → *System Trace* alın; `AndroidUiDispatcher`
   ve `AndroidPrefetchScheduler` yığınlarına bakın. En olası iki kaynak:
   Keşfet raflarındaki LazyList ön yükleme ve sık açılan dialog/pencereler.
2. **Koda gömülü Türkçe metinleri kaynak dosyalarına taşı** (~290 adet).
   Bunlar yapılmadan İngilizce arayüz hiçbir zaman tam olmaz.
3. **`Text("...")` sabitlerini `stringResource()`'a çevirin.**
4. Mümkünse **ikinci bir cihazda** (MIUI olmayan) test edin; `libhwui`
   çökmesinin MIUI'a özgü olup olmadığını netleştirir.

---

## EK: Raporu ben nasıl okudum

`Kitsugi_Crash_Report (15).txt` 3,4 MB ve 35.008 satır. Bölümleri:

```
    1 –  47   Özet + breadcrumb (ekran gezinti) izi
   48 – 162   LOGCAT kanıt satırları        ← kasma verisi burada
  171 – 353   NATIVE ÇÖKME İZİ (3 adet)    ← çökme imzası burada
  354 – 7815  PROC MAPS
 7817 – 35008 NATIVE ÇÖKME + PROC MAPS (4 çift daha)
```

`/proc/self/maps` ile adres → kütüphane eşlemesi yapıldı (`crash #3…#7`,
her biri kendi maps bloğuyla). Tarih dönüşümleri `epoch` alanından UTC+3'e
çevrildi.
