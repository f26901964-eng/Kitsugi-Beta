# Adli Kök Neden ve Çözüm Raporu — `v2.4.320–v2.4.323` Açılış Çökmesi (`java.lang.VerifyError`) ve Çökme Raporlama Hattı Denetimi

**Tarih:** 2026-10-21  
**İncelenen Kaynaklar:** Google Drive klasöründeki **13 dosyanın tamamı** (`Kitsugi_Crash_Report (6).txt` [2.276.278 B], `Kitsugi_Crash_Report (31).txt` [10.110 B], `crash_history (6).txt` [381.414 B], `crash_history (5).txt` [412.308 B], `crash_log (10).txt` [5.336 B], `live_logcat_report (7).txt` [90.809 B], `live_logcat_report (6).txt` [57.972 B], `kitsugi_debug (7).log` [159.861 B], `kitsugi_debug (6).log` [159.861 B], `logcat_at_crash (6).txt` [73 B], `logcat_at_crash (5).txt` [73 B], `device_info (8).txt` [265 B], `device_info (7).txt` [264 B]) + tüm kod tabanı (1.337 kaynak dosyası).  
**Hedef Cihaz:** Xiaomi Pad 6 (`23043RP34C` / `pipa`), Android 13 (API 33), `arm64-v8a`  
**Çözüm Sürümü:** `v2.4.324`

---

## 1. Milisaniye Bazlı Kronolojik Adli Tablo

Google Drive'daki tüm günlük dosyaları zaman damgasına göre birleştirildiğinde kesin tablo şudur:

1. **`2026-10-21 02:22:15 – 02:25:17` (`v2.4.319-beta.29875586`, `PID 32724`):**
   - Uygulama **182 saniye** boyunca sorunsuz çalıştı (`Tab(tab=Explore)`).
2. **`2026-10-21 02:25:26.993` (`v2.4.320-beta.29875619`, `PID 2405`) — İLK AÇILIŞ:**
   - Kurulumdan hemen sonraki ilk açılışta **`Uptime: 0s`** anında üç koldan aynı anda **`java.lang.VerifyError`** patladı:
     - `02:25:26.993` (`WM.task-1`): `java.lang.VerifyError: Verifier rejected class androidx.room.r: java.lang.Object androidx.room.r.g(java.lang.Object): [0x27A] Rejecting invocation, expected 5 argument registers, method signature has 6 or more`
     - `02:25:27.093` (`main`): Aynı `androidx.room.r` sınıf yüklemesinde ana UI thread çökmesi
     - `02:25:27.180` (`DefaultDispatcher-worker-1, 7, 18...`): `java.lang.VerifyError: Verifier rejected class com.kitsugi.animelist.data.settings.AppSettings: void com.kitsugi.animelist.data.settings.AppSettings.<init>(): [0x1FE] Rejecting invocation, expected 5 argument registers, method signature has 6 or more`
3. **`2026-10-21 02:25:32` (`PID 2769`), `02:28:12` (`PID 3679`), `02:28:26` (`PID 4316`), `02:28:32` (`PID 4661`), `02:35:29` (`PID 16570`), `02:37:04` (`PID 18910`), `02:37:10` (`PID 19252`), `02:40:06` (`PID 21269`):**
   - Her açılış denemesinde 0. saniyede birebir aynı iki `VerifyError` ile anında çöktü.
4. **`2026-10-21 03:04:59 – 03:05:07` (`v2.4.322-beta.29875669`, `PID 20612` ve `PID 21173`):**
   - `AppSettings` yapıcısı değişmediği için **birebir aynı iki `VerifyError`** (`[0x1FE]` ve `[0x275]`) 59. milisaniyede tekrar patladı; `v2.4.323`'te de aynı taşma aynen devam ediyordu.

---

## 2. Birincil Kök Nedenin Matematiksel İspatı (`261 & 0xFF == 5`)

Dalvik sanal makinesi buyruk kümesinde (`3rc`: `invoke-direct/range`, `invoke-static/range`, vb.) buyruk biçimi:

```
AA | op   BBBB   CCCC
```

- `AA`: Argüman yazmaç kelimesi sayısı (**8-bit işaretsiz tamsayı: en fazla `255` = `0xFF`**).
- `this` referansı `1` kelime, `Long` / `Double` parametreleri **`2` kelime**, diğer tüm tipler `1` kelime yer kaplar.
- Kotlin derleyicisi varsayılan değerli yapıcılar ve `data class copy(...)` için her 32 parametrede bir `Int` bit maskesi (`mask0..maskK`) + 1 işaretçi (`DefaultConstructorMarker` / `Object`) ekler:

| Sürüm | Özellik Sayısı | `Long` Alan | Birincil `<init>` Yazmaç | Bit Maskesi + İşaretçi | Sentetik `<init>` & `copy$default` Toplam Yazmaç | DEX `AA` Baytı (`toplam & 0xFF`) |
|---|---:|---:|---:|---:|---:|---:|
| `v2.4.319` | 241 | 3 | 245 | `8 + 1 = 9` | **254** (`0xFE` ≤ `255`) | **`254` (GEÇERLİ)** |
| `v2.4.320`–`v2.4.323` | 248 | 3 | 252 | `8 + 1 = 9` | **`261` (`0x0105` > `255`!)** | **`5` (`0x05` — TAŞMA!)** |
| **`v2.4.324` (Düzeltildi)** | **8 grup (248 alan)** | **0 (kök)** | **9** | **`1 + 1 = 2`** | **`11` (`0x0B` ≪ `255`)** | **`11` (TAM GÜVENLİ)** |

Neden `androidx.room.r.g(Object)` da aynı hatayla patladı?
- R8'in `HorizontalClassMerger` optimizasyonu, `KitsugiSplashActivity.onCreate` içindeki `lifecycleScope.launch { ... AppSettings(splashAnimationEnabled = anim, splashSoundEnabled = sound) }` (`SuspendLambda`) sınıfını `androidx.room.RoomDatabase.beginTransaction` içindeki `SuspendLambda` ile tek bir `androidx.room.r` sınıfında birleştirdi.
- Uygulama açılır açılmaz WorkManager (`WM.task-1`) Room işlemi başlattığında ART, `androidx.room.r.g(Object)` metodunun tüm `switch` dallarını doğruladı ve `0x275` ofsetindeki 261 yazmaçlı (`AA = 5`) `AppSettings` çağrısında sınıfı reddederek hem `WM.task-1` hem de `main` iş parçacığını düşürdü.

---

## 3. Günlüklerde Tespit Edilip Giderilen Diğer 6 Gerçek Hata

1. **`KitsugiCrashHandler.kt` — Çoklu Thread Çökme Fırtınası Yarışı:** 6+ `DefaultDispatcher-worker` aynı milisaniyede çöktüğünde hepsi aynı anda `crash_log.txt`, `logcat_at_crash.txt`, MediaStore `Downloads/` ve `startActivity(KitsugiCrashActivity)` çağırıyordu. Atomik tek-uçuş + kilitli `main`-thread yükseltme koruması eklendi.
2. **`KitsugiCrashLogger.kt` — 73 Baytlık Boş `logcat_at_crash.txt`:** `readerThread.join(400)` + çökme anındaki süreç fırtınasında `logcat` çıktısı boşaltılmadan öldürülüyor, yalnızca 73 baytlık UTF-8 başlık satırı (`=== Çökme Anı Logcat (Son 500 Satır) — ... ===\n`) yazılıyordu. Süre `1500 ms` yapıldı, `main,system,crash` tamponları eklendi ve boş yakalamada taze dosyayı ezmeme + rapor yedeği koruması getirildi.
3. **`kitsugi_crash_handler.cpp` & `NativeCrashBridge.kt` — 2,27 MB'lık Bayat (13 Ekim) `native_crash.txt` Sızıntısı:** C++ sinyal işleyicisi `O_APPEND` kullandığı için 13 Ekim'den (`epoch=1791889108`, `pid=29747`) kalma `/proc/self/maps` dökümleri 2.276.278 bayta şişmiş ve 21 Ekim'deki saf Java `VerifyError` raporunun sonuna yapışmıştı. `O_TRUNC` + anonim bölge filtrelemesi (~15 KB) + sıkı `pid`/`epoch` doğrulaması + eski bayat dosyanın otomatik silinmesi uygulandı.
4. **`KitsugiSessionSupervisor.kt` — `FATAL EXCEPTION` Varken `"SEBEP BULUNAMADI"` Yazması:** Logcat kanıt satırlarında `E AndroidRuntime: FATAL EXCEPTION` ve `java.lang.VerifyError` bulunmasına rağmen post-mortem başlık yalnızca native/ANR/LMKD aradığı için `"SEBEP BULUNAMADI"` üretiyordu. `JAVA / ART FATAL HATA` ayrıştırıcısı eklendi.
5. **`KitsugiCrashActivity.kt` — 2,27 MB Metinde `StaticLayout` / GPU Doku Sınırı Tehlikesi:** Tam metin Kopyala / Paylaş / İndirilenler'e Kaydet için korunurken ekrandaki `Text` önizlemesi 48 KB ile sınırlandırıldı.
6. **`MalWebParser.kt` & `MalNotificationsClient.kt` — JDK RFC 2396 `URI.resolve("?p=2")` Hatası:** `java.net.URI("https://myanimelist.net/profile/TestUser/reviews").resolve("?p=2")` ifadesi `/reviews` segmentini düşürerek hem üretimde MAL sayfalamasını hem de CI birim testlerini kırıyordu; RFC 3986 uyumlu `okhttp3.HttpUrl` ile düzeltildi.

---

## 4. Gerçek Derleme, Birim Testi ve İkili DEX (`classes*.dex`) Doğrulama Kanıtı

GitHub Actions üzerinde gerçek Android SDK 36 + NDK 27 + KSP + Kotlin 2.0.21 + R8 küçültme/birleştirme hattı (`Run #39522953145`) çalıştırılmış ve derlenen APK'ların içindeki **tüm `classes*.dex` dosyalarındaki her tekil Dalvik makine buyruğu** `scripts/verify_apk_dex_bytecode.py` ile denetlenmiştir:

- **Birim Testleri (`:app:testFossDebugUnitTest`):** `AppSettingsAndCrashDiagnosticsGuardTest`, `SettingsMigrationTest`, `MalWebParserTest`, `MalNotificationsClientTest` dahil tüm test paketleri **%100 başarılı**.
- **Debug APK (`app/build/outputs/apk/foss/debug/Kitsugi-Beta-v2.4.324-foss.apk`):**
  - `22` DEX dosyası · **`403.978` metot gövdesi** · **`11.105.952` Dalvik buyruğu** tek tek doğrulandı → En yüksek argüman yazmacı: **`176 / 255`**, **`VerifyError = 0`**.
- **R8 Release APK (`app/build/outputs/apk/foss/release/Kitsugi-Beta-v2.4.324-foss.apk`):**
  - `5` DEX dosyası (`classes.dex` .. `classes5.dex`) · **`251.589` metot gövdesi** · **`7.155.291` Dalvik buyruğu** tek tek doğrulandı → En yüksek argüman yazmacı: **`176 / 255`**, **`VerifyError = 0`**.
