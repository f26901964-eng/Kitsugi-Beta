# 🧯 Kitsugi Çökme Teşhis ve Çözüm Raporu (v2.4.206)

> Bu doküman iki soruyu cevaplar:
> **1)** Uygulama neden her yerde çöküyor? **2)** Neden çökmeden önce/sonra hiçbir çökme penceresi, hiçbir rapor çıkmıyor?

---

## 1. Kısa cevap: "Neden hiç çökme raporu çıkmıyordu?"

Tek bir sebep yok — **birbirini besleyen 8 ayrı tasarım hatası** vardı. En önemlisi şu:

### ⚠️ 1.1 Java çökme yakalayıcı, çökmelerin ÇOĞUNU hiç göremez (en kritik)

`Thread.setDefaultUncaughtExceptionHandler` **yalnızca yönetilen (managed) Java/Kotlin istisnalarında** çalışır.
Aşağıdaki ölümler bu handler'a **hiç uğramaz** — süreç anında yok olur, hiçbir şey yazılamaz:

| Ölüm türü | Tipik kaynak | Önceden rapor? |
|---|---|---|
| **Native çökme** (SIGSEGV/SIGABRT/SIGBUS) | MPV/FFmpeg/MediaCodec, GPU & Skia (RenderEffect blur), libdovi JNI, TorrServer | ❌ HİÇ |
| **Bellek yetersizliği** (LMKD/OOM kill) | Dev boyutlu görseller, 1300+ manga kaynağı, bitmap decode | ❌ HİÇ |
| **ANR sonrası sistem öldürmesi** | Ana thread kilitlenmesi | ❌ HİÇ |
| **SIGKILL / Force stop / arka plan kısıtı** | Sistem, kullanıcı | ❌ HİÇ |
| Java istisnası | (kod hatası) | ✅ (ama aşağıdaki hatalar yüzünden çoğu zaman o da kayboluyordu) |

Senin yaşadığın **"anlık donup pat diye kapanma"** tablosu tam olarak yukarıdaki satırların imzasıdır.
Yani uygulama çökerken yazılım tarafında "beni bildirecek" hiçbir mekanizma yoktu.

### ⚠️ 1.2 Çökme raporu üretimi `catch (e: Exception)` ile korunuyordu

Rapor üretimi, tam da raporun en çok gerektiği durumlarda (`OutOfMemoryError`, `StackOverflowError`) çalışmıyordu:

```kotlin
// ESKİ KOD
val crashReport = try {
    KitsugiCrashLogger.writeCrashReport(this, thread, throwable, isForeground)
} catch (_: Exception) { ... }   // ← Error türleri buraya DÜŞMEZ
```
`OutOfMemoryError` ve `StackOverflowError` birer `Error`'dır, `Exception` değildir. Rapor üretimi yarıda kalıp
handler de ölünce süreç sessizce kapanıyordu.

### ⚠️ 1.3 Ana thread `Thread.sleep(800)` ile uyutuluyordu → çökme ekranı yarışı kaybediyordu

```kotlin
// ESKİ KOD (çökme handler'ı içinde)
startActivity(crashIntent)
Thread.sleep(800)                     // ← ANA THREAD UYUYOR
Process.killProcess(myPid())
```
Android'de bir ekranı başlatmanın ilk adımı, **mevcut ekranın duraklatılmasıdır** ve bu işlem ana thread'in
o mesajı işlemesini bekler. Ana thread uyuduğu için:

1. Android yeni ekranı açar mı diye beklerken süreç kendini öldürüyordu,
2. çökme ekranı **aynı görevde (task)** açıldığı için, ölen ana sürecin görevi temizlenirken çökme ekranı da
   yok ediliyordu.

Sonuç: çökme penceresi çoğu zaman **hiç açılmıyordu**.

### ⚠️ 1.4 "Cockroach" koruyucusu gerçek çökmeleri sessizce yutuyordu

```kotlin
// ESKİ KOD
val isPluginOrWindowError =
    trace.contains("com.lagradost.cloudstream3") || ...   // ← stack trace'in HERHANGİ bir yerinde
```
Bu uygulamanın arayüz yollarının neredeyse tamamı gömülü Cloudstream katmanından geçiyor. Yani **gerçek
uygulama çökmeleri "eklenti hatası" sanılıp yutuluyordu**: ekranda hiçbir şey görünmüyor, arayüz bozuk
durumda kalıyor, ardından sistem uygulamayı öldürüyordu (yine raporsuz).

Ek olarak: `Looper.loop()` geri dönerse (`while (true) { Looper.loop() }`) **saniyede binlerce kez boşa dönen
%100 CPU döngüsü** oluşuyor ve bu da ANR + sistem öldürmesi demek.

### ⚠️ 1.5 Çökme yakalayıcı çok geç kuruluyordu

Handler `Application.onCreate()` içinde, üstelik **bazı init'lerden sonra** kuruluyordu. ContentProvider'lar
(Hilt, WorkManager, tachiyomi/cloudstream init) `onCreate`'ten **önce** çalıştığı için oradaki çökmeler
hiçbir zaman raporlanmıyordu.

### ⚠️ 1.6 Bir sonraki açılış penceresi tek bir sinyale bağlıydı

`hasUnreadCrash`, yalnızca `SharedPreferences` bayrağına bakıyordu; bu bayrak da rapor yazımının **en son**
adımıydı. Rapor yazımı yarıda kaldığında ne bayrak, ne pencere oluyordu. (Dosya kanıtı diye bir kontrol yoktu.)

### ⚠️ 1.7 Çökme ekranının kendi süreci (%:crash) çöktüğünde geri bildirim yoktu

`:crash` sürecinin handler'ı sadece bir dosya yazıp **süreci canlı bırakıyordu** → kullanıcı boş/siyah ekranda
kilitli kalıyordu; ne rapor, ne uyarı.

### ⚠️ 1.8 İndirilenler'e kaydetme Android 11+ üzerinde sessizce başarısız oluyordu

`Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` altına doğrudan dosya yazmak
(scoped storage) Android 11+ üzerinde `EACCES` verir. "Raporu İndirilenler'e kaydet" butonu bu yüzden
çoğu cihazda hiç işe yaramıyordu.

---

## 2. Peki neden **her yerde** çöküyordu? (Bulunan somut hatalar)

### 2.1 🧨 Aynı Lazy anahtarı = anında çökme (7 ayrı liste)

Compose'da bir liste içinde **aynı anahtar iki kez** kullanılırsa çalışma anında şu istisna atılır:

```
java.lang.IllegalArgumentException: Key "123" was already used. If you are using LazyColumn/Row
please make sure you provide a unique key for each item.
```

Ve bu, **kaydırma / sekme değiştirme / detay açma** anında patlar — tam senin anlattığın davranış.
Bulunan yerler: ana sayfa grid'i (`key = malId`), modern ana sayfa satırları, yayın takvimi (2 liste),
genişletilmiş medya grid penceresi, manga kaynak listesi (1300+ kaynak → isim çakışması), özel liste
editörü, oynatma hızı listesi. **Hepsi düzeltildi.**

> Neden çakışıyor? Örn. TMDB/Kitsu/Shikimori kayıtlarının bir kısmında `malId = 0` veya aynı malId birden
> fazla kez gelebiliyor (birden fazla kaynaktan birleştirme). Tek başına `malId` bu yüzden benzersiz değil.

### 2.2 🖼️ Resim indirme: OOM + yasak yola yazma

```kotlin
// ESKİ KOD
val thumbnail = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)   // tam boy decode!
imageFile.writeBytes(bytes)  // /sdcard/Download/Kitsugi/Images → Android 11+ : EACCES
```
* 4000×6000 bir JPEG ≈ **96 MB** bitmap ister → bellek baskısı → LMKD süreci sessizce öldürür.
* İndirilen bayt dizisi için **hiçbir boyut sınırı yoktu**.
* Android 10+ üzerinde doğrudan dosya yazımı yasak olduğu için indirme **hiç kaydedilmiyordu** (sadece hata
  toast'ı görünüyordu, bazen hiç).

### 2.3 🎬 Cinematic loading ekranı: tam ekran GPU bulanıklığı

Detay sayfası açılırken tam ekran görsele `Modifier.blur(16.dp)` (RenderEffect) uygulanıyor. Bu, özellikle
düşük/orta segment GPU'larda **render thread'de native çökmeye** (dolayısıyla raporsuz kapanmaya) yol açabilen
ağır bir işlemdir. Şüpheli olarak işaretlendi — yeni native çökme yakalayıcı bunu kesin olarak teşhis edecek
(rapor `libhwui`/`Skia`/`libGLES` karelerini gösterirse blur kapatılacak).

### 2.4 🧵 İptal edilen coroutine'lerin geniş `catch` ile yakalanması

"Yüklenirken başka yere basınca çökme" tablosunun klasik sebebi: yükleme sırasında ekrandan çıkınca
`CancellationException` atılır; geniş `catch (e: Exception)` bunu yutar ve sonrasında `e.message`/state
üzerinden NPE üretilebilir. Bu desen tarandı; çökme raporu artık kalan noktaları **satır satır** gösterecek.

---

## 3. Ne değişti? (Çözümün mimarisi)

```
                    ┌──────────────────────────────┐
   Java/Kotlin      │ KitsugiCrashHandler          │  bayrak dosyası → rapor → logcat
   istisnası  ────► │ (attachBaseContext'te kurulu)│  → AYRI GÖREVDE çökme ekranı → süreç kapat
                    └──────────────────────────────┘
   Native çökme     ┌──────────────────────────────┐
   (SIGSEGV...) ──► │ libkitsugi_crash_handler.so  │  native_crash.txt (backtrace + adres)
                    └──────────────────────────────┘
   Sessiz ölüm      ┌──────────────────────────────┐
   (OOM/ANR/kill)   │ KitsugiSessionSupervisor     │  oturum dosyası + kalp atışı + eylem izi
                    │ (sonraki açılışta analiz)    │  → logcat kazısı → unclean_exit.txt
                    └──────────────────────────────┘
                                     ↓
                    Çökme penceresi (kendi görevi + :crash süreci)
                    veya açılışta "Beklenmedik Kapanma" penceresi
```

**Yeni / düzenlenen dosyalar**

| Dosya | Ne yapar |
|---|---|
| `core/diagnostics/KitsugiCrashHandler.kt` **(YENİ)** | Tek çökme giriş noktası. Önce kanıt, sonra rapor; ana thread'i asla uyutmaz; bekçi thread ile ölü süreci kapatır. |
| `core/diagnostics/KitsugiSessionSupervisor.kt` **(YENİ)** | Sessiz ölüm dedektörü: oturum defteri + kalp atışı + **eylem izi (breadcrumbs)** + ölüm sonrası logcat kazısı. |
| `core/diagnostics/NativeCrashBridge.kt` **(YENİ)** | Native yakalayıcıyı yükleyen/kuran JNI köprüsü. |
| `cpp/kitsugi_crash_handler.cpp` **(YENİ)** | SIGSEGV/SIGABRT/SIGBUS/SIGILL/SIGFPE/SIGTRAP → async-signal-safe rapor + yeniden yükseltme (tombstone korunur). |
| `core/diagnostics/KitsugiCrashLogger.kt` | `Throwable` güvenli raporlama, bayrak dosyası, MediaStore ile İndirilenler'e kayıt, 3 kanallı "gösterilmemiş çökme" kontrolü. |
| `KitsugiApplication.kt` | Yakalayıcı `attachBaseContext`'te; Cockroach artık sessizce yutmuyor (üst kare kontrolü + 5 kurtarma limiti + CPU döngüsü düzeltmesi); `:crash` süreci çökerse bayrak + kapanış. |
| `KitsugiCrashActivity.kt` | Ölü süreci kapatır, Compose çizilemezse düz-Android son çare ekranı gösterir. |
| `AndroidManifest.xml` | Çökme ekranı **kendi görevinde** (`taskAffinity`), recents'te görünmez. |
| `KitsugiImageDownloadHelper.kt` | MediaStore kaydı, boyut sınırı, örneklemeli thumbnail decode, indirme izleri. |
| 7 liste dosyası | Benzersiz anahtarlar (duplicate key çökmeleri). |
| `proguard-rules.pro` | JNI sınıfı korunur (release/R8'de native yakalayıcı devre dışı kalmasın). |

---

## 4. Bundan sonra nasıl çalışacak?

1. **Java çökmesi** → bayrak anında yazılır → rapor + logcat → çökme ekranı ayrı süreç/görevde açılır →
   ekrandan "Geliştiriciye Gönder" ile tüm dosyalar paylaşılır.
2. **Native çökme** → `native_crash.txt` (sinyal + geri iz) → sonraki açılışta pencere + rapor.
3. **Sessiz ölüm (LMKD/ANR/SIGKILL)** → sonraki açılışta `unclean_exit.txt`: **"ÖLÜM SEBEBİ"** başlığı ile
   (örn. `NATIVE ÇÖKME (SIGSEGV)`, `ANR`, `BELLEK YETERSİZLİĞİ (LMKD)`) + kanıt logcat satırları +
   **son ekran** + **son 40 eylem**.

Yani bir daha "hiçbir şey görünmedi" durumu yok: en kötü senaryoda bir sonraki açılışta pencere çıkar.

---

## 5. Sende ne yapman gerekiyor?

1. Bu sürümün (v2.4.206) APK'sını kur.
2. Çökmeyi **tekrar üret** (gezin, detay aç, galeri, resim indir).
3. Ne olduğuna göre:
   * **Çökme ekranı çıktıysa** → "Geliştiriciye Gönder (Tüm Dosyalar)".
   * **Sadece uygulama kapandıysa** → uygulamayı tekrar aç → "Beklenmedik Kapanma Tespit Edildi"
     penceresi → "Detaylı Çökme Ekranını Aç" → "Geliştiriciye Gönder".
   * **Pencere hiç çıkmadıysa** → **Ayarlar → Hakkında → Çökme & Hata Teşhis Raporu**.
4. Ya da doğrudan dosyaları gönder: `İndirilenler/Kitsugi/Kitsugi_Crash_Report.txt` (uygulama içinden
   "Dosyayı İndirilenler Klasörüne Kaydet" ile de üretilir).

Gelen raporlarda şu anahtar satırlar bize kesin sebebi söyler:
- `▶ ÖLÜM SEBEBİ` → native / ANR / LMKD / OOM
- `▶ SON EKRAN` → hangi ekran
- `▶ UYGULAMA EYLEM İZİ` → çökmeden hemen önce ne yaptın
- `▶ TAM STACKTRACE` → Java tarafı

---

## 6. Dürüst not: doğrulanamayan kısım

Bu ortamda Android SDK/JDK bulunmadığı için **derleme ve cihaz testi yapılamadı**; değişiklikler statik
olarak (Kotlin sözdizimi ayrıştırıcısı + kod incelemesi) doğrulandı. Cihazda ilk çalıştırmada beklenen ek
doğrulamalar:
- Çökme penceresi gerçekten açılıyor mu? (Yeni görev/süreç düzeni)
- `native_crash.txt` / `unclean_exit.txt` dosyaları oluşuyor mu?
- Release (R8'li) derlemede native yakalayıcı yükleniyor mu? (ProGuard kuralı eklendi)

Kalan çökme sebepleri artık **raporda yazılı olacağı için** tek tek, kesin veriyle temizlenebilir.
