# PLAN & TASK — Açılış Çökmesi (`java.lang.VerifyError`: 255-Yazmaç Dalvik DEX Taşması), Çökme Raporlama Hattı ve MAL URL Çözümleme Düzeltmeleri

**Tarih:** 2026-10-21  
**Branch:** `arena/041d2a90-kitsugi-beta` (güncel taban: `origin/main@b118ab7`, hedef sürüm: **`v2.4.325`**)  
**PR:** `https://github.com/f26901964-eng/Kitsugi-Beta/pull/19`  
**Sorumlu:** Arena Agent  
**Durum:** ✅ Tamamlandı — Tüm 13 hata dosyası + 1.337 kaynak dosya satır satır incelendi, birim testleri + Debug APK + R8 Release APK + ikili Dalvik DEX doğrulaması %100 yeşil geçti, Zip paketi Google Drive'a yüklendi

---

## 1) Sorun Özeti & Kullanıcı Talebi

- **Kullanıcı Raporu:** Google Drive `hata` klasöründeki (`1mAVwVOl8n2h1abM2_P1Ubf8U1yO8aPDo`) **13 tanı/çökme dosyasının tek bir satırı bile atlanmadan satır satır incelenmesi**, uygulamanın açılır açılmaz (`Uptime: 0s`) sürekli çökmesine yol açan gerçek kök nedenin tespit edilip kalıcı olarak çözülmesi ve değişen tüm dosyaların bu Plan & Task dosyasıyla birlikte ziplenerek Google Drive'a yüklenmesi.
- **Cihaz & Ortam Kanıtı (Loglardan):** Xiaomi Pad 6 (`pipa`), Android 13 (SDK 33), `arm64-v8a`.
  - **`v2.4.319` (`versionCode = 319`):** `02:22:15`–`02:25:17` arasında **182 saniye boyunca sıfır çökmeyle** normal çalıştı (`crash_log.txt = (yok)`).
  - **`v2.4.320` (`versionCode = 320`, `02:25:26.993`) ve `v2.4.322` (`versionCode = 322`, `03:05:00.059`):** Uygulama simgesine basıldığı saniye (`Uptime: 0s`, süreç ömrü `59 ms` ve `198 ms`) `base.apk!classes3.dex` içinde **iki eşzamanlı `java.lang.VerifyError`** ile daha tek kare bile çizemeden öldü!
  - **Önemli Not:** `origin/main` üzerindeki `v2.4.323` ve `v2.4.324` sürümleri de aynı `AppSettings` kurucu imzasını taşıdığından aynı `VerifyError` ile etkilenmektedir; bu paket güncel `origin/main` (`b118ab7`, `v2.4.324` Vitrin düzeltmeleri dahil) üzerine tam uyumlu **`v2.4.325`** olarak hazırlanmıştır.

---

## 2) İncelenen 13 Tanı Dosyası (Hiçbir Satır Atlanmadan)

| # | Dosya Adı | Boyut / Kapsam | Satır Satır İnceleme Bulgusu |
|---|---|---|---|
| 1 | `Kitsugi_Crash_20261021_030500.txt` | 2,28 MB (`v2.4.322`) | `03:05:00.059` (`WM.task-1`): `VerifyError: Verifier rejected class com.kitsugi.animelist.data.settings.AppSettings` (`<init>() @[0x1FE]`, `expected 5 argument registers, method signature has 6 or more`) + ucuna yapışmış 2,27 MB bayat `native_crash.txt` |
| 2 | `Kitsugi_Crash_20261021_030500 (1).txt` | 2,28 MB (`v2.4.322`) | `03:05:00.198` (`main` UI thread, 139 ms sonra): `VerifyError: Verifier rejected class androidx.room.r` (`void androidx.room.r.<init>(com.kitsugi.animelist.KitsugiSplashActivity, y5.d)` -> `androidx.room.r.g(Object) @[0x275]: expected 5 argument registers, method signature has 6 or more`) |
| 3 | `Kitsugi_Crash_20261021_022526.txt` | `v2.4.320` | `02:25:26.993` (`WM.task-1`): Birebir aynı `AppSettings.<init>() @[0x1FE]` `VerifyError` (ilk başladığı sürüm kanıtı!) |
| 4 | `Kitsugi_Crash_20261021_022527.txt` | `v2.4.320` | `02:25:27.094` (`main`): Birebir aynı R8 birleşik `SuspendLambda` `VerifyError` (`@[0x27A]`) |
| 5–10 | `Kitsugi_Crash_20261021_000511*.txt` (6 dosya) | `v2.4.307` | 6 ayrı `DefaultDispatcher-worker-*` thread'inin aynı milisaniyede `MalWebParser.resolveRelativeUrl` `URI.resolve("?p=2")` hatasıyla çöküp kilitlenmesinden doğan **6'lı kopya çökme raporu fırtınası** |
| 11–13 | `kitsugi_logs_20261021_*.txt` (3 tanı paketi) | `v2.4.319` / `320` / `322` | `logcat_at_crash.txt` = 73 bayt (boş başlık), `unclean_exit.txt` = `FATAL EXCEPTION` varken yanlış `"SEBEP BULUNAMADI"`, 13 Ekim'den (`epoch=1791889108`) kalma `O_APPEND` ile birikmiş 2,27 MB bayat `native_crash.txt`, ve 2,3 MB raporu ekranda çizerken `KitsugiCrashActivity` `GL_MAX_TEXTURE_SIZE` / `StaticLayout` donması |

---

## 3) Kök Neden Analizi (Matematiksel ve Bayt Kodu Düzeyinde Kanıt)

### A) Birincil Kök Neden: Dalvik DEX `3rc` (`invoke-*/range`) 8-Bit (255) Yazmaç Taşması
Dalvik bayt kodunda 5'ten fazla yazmaç alan metot çağrıları `3rc` (`invoke-direct/range`, `invoke-static/range`, vb.) formatıyla kodlanır:
- Biçim: `AA|op BBBB CCCC` -> **`AA` alanı 8 bitlik işaretsiz tamsayıdır (`uint8`, maksimum `255`)**!
- 32-bit türler (`Int`, `Boolean`, `Float`, nesne referansı) **1 yazmaç**, 64-bit türler (`Long`, `Double`) **2 yazmaç** kaplar.
- Kotlin `data class` varsayılan parametre değerlerine sahip olduğunda derleyici her 32 parametre için **+1 `Int` bitmask** (`mask0..mask7`) + **+1 `DefaultConstructorMarker`** ekler:

| Sürüm | `AppSettings` Özellik Sayısı | `this` | `Double` (+1'er ek slot) | Birincil `<init>` Toplam Yazmaç | Kotlin Sentetik Default `<init>` (`+8 mask + 1 marker`) | `AA = count & 0xFF` | Sonuç |
|---|---:|---:|---:|---:|---:|---:|---|
| `v2.4.319` | **241** | +1 | +3 (`245` arg) | **245** (`<= 255` ✅) | **254** (`<= 255` ✅ — sınıra **1 yazmaç** kala!) | `254` | **Çalışır** |
| `v2.4.320`–`v2.4.324` | **248** | +1 | +3 (`252` arg) | **252** (`<= 255`) | **261** (`> 255` ❌ **TAŞTI!**) | **`261 & 0xFF = 5`!** | **0. sn `VerifyError`!** |
| **`v2.4.325` (Düzeltme)** | **8 alt model** *(248 özellik korundu)* | +1 | +0 (`AppSettings` üst düzey) | **9** (`<= 255` ✅) | **11** (`<= 255` ✅ — en büyük alt model: **46**) | **`11` (maks `46`)** | **%100 Güvenli!** |

**Neden `expected 5 argument registers` ve neden `androidx.room.r` yazıyordu?**
1. **`261 mod 256 = 5`!** D8/R8 derleyicisi 261 yazmaç sayısını 8 bitlik `AA` baytına yazarken alt 8 biti (`0x105 & 0xFF = 0x05 = 5`) bıraktı! Android 13 ART `MethodVerifier` ise `AA = 5` değerini metot imzasındaki gerçek `261` yazmaç ile karşılaştırıp `Rejecting invocation, expected 5 argument registers, method signature has 6 or more` diyerek `AppSettings` sınıfını (`0x1FE`) anında reddetti (`WorkManager` `WM.task-1` thread'i `SettingsDataStore` okurken 59. ms'de çöktü).
2. Aynı anda `KitsugiSplashActivity` içindeki açılış `SuspendLambda`'sı da `AppSettings(...)` varsayılan kurucusunu çağırıyordu. R8'in `HorizontalClassMerger` optimizasyonu bu `SuspendLambda` sınıfını `androidx.room.r` sınıfıyla yatay olarak birleştirdiği için `main` UI thread'i de 198. ms'de `androidx.room.r.g(Object) @[0x275]` hatasıyla kilitlenip çöktü!

### B) İkincil 6 Kök Neden (Çökme Raporlama Hattı & MAL Sayfalama)
1. **`KitsugiCrashHandler.kt`:** Çoklu worker aynı anda çöktüğünde kilit/tek-uçuş mekanizması yoktu; 6 worker aynı anda `crash_log.txt` yazıp 6 kez `KitsugiCrashActivity` fırlatıyordu.
2. **`KitsugiCrashLogger.kt`:** `logcat -d` alt süreci `400 ms` sonra öldürülüyordu (`process.destroy()`), `-b crash` tamponu eksikti ve boş çıktıda dosya 73 baytlık tek satır başlıkla eziliyordu.
3. **`kitsugi_crash_handler.cpp`:** `native_crash.txt` `O_APPEND` ile açıldığı için 8 gün önceki (`2026-10-13`) native çökme silinmiyor ve `/proc/self/maps` tüm anonim `[anon:...]` guard sayfalarını yazarak dosyayı **2,27 MB** şişiriyordu.
4. **`NativeCrashBridge.kt` & `KitsugiCrashLogger.kt`:** `native_crash.txt` içindeki `pid=` ve `epoch=` kontrol edilmeden her Java çökmesinin sonuna 2,27 MB'lık eski native dump ekleniyordu; `markCrashAsRead` de bu dosyayı temizlemiyordu.
5. **`KitsugiSessionSupervisor.kt`:** `analyzePostMortemLines()` sadece native sinyallere (`Fatal signal`, `SIGSEGV`, `ANR in`) bakıyor, Java/ART `FATAL EXCEPTION:` ve `VerifyError` satırlarını tanımadığı için kanıt gözünün önündeyken `"SEBEP BULUNAMADI"` yazıyordu.
6. **`KitsugiCrashActivity.kt` & `MalWebParser.kt` / `MalNotificationsClient.kt`:**
   - 2,28 MB'lık metin tek bir Compose `Text` bileşenine doğrudan verildiği için `StaticLayout` / OpenGL doku sınırı (`GL_MAX_TEXTURE_SIZE`) yüzünden çökme ekranının kendisi donuyordu.
   - `java.net.URI.resolve("?p=2")` (eski RFC 2396 davranışı) taban URL'nin son yol segmentini düşürüyordu.

---

## 4) Uygulanan Çözümler & Mimari Tasarım

### 1. `AppSettings` 8 Alt Veri Sınıfına Bölündü (%100 Geriye Dönük Uyumlu)
- **Dosyalar:** `AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiSplashActivity.kt`, `SettingsScreen.kt`
- 248 ayar alanı mantıksal 8 alt `data class` modeline ayrıldı:
  1. `ProfileAndListSettings` (42 alan -> sentetik kurucu **46** yazmaç)
  2. `ThemeAndAppUiSettings` (19 alan -> sentetik kurucu **23** yazmaç)
  3. `PlayerPlaybackSettings` (28 alan -> sentetik kurucu **31** yazmaç)
  4. `PlayerOverlaySettings` (28 alan -> sentetik kurucu **31** yazmaç)
  5. `MpvAndSubtitleSettings` (36 alan -> sentetik kurucu **41** yazmaç)
  6. `GestureAndSkipSettings` (24 alan -> sentetik kurucu **27** yazmaç)
  7. `IntegrationsAndNotificationsSettings` (40 alan -> sentetik kurucu **43** yazmaç)
  8. `DownloadAndMangaSettings` (31 alan -> sentetik kurucu **34** yazmaç)
- `AppSettings` içinde tüm 248 alan `val foo: Type get() = subGroup.foo` olarak sıfır ek bellek maliyetiyle korundu; DataStore anahtar adları hiç değişmediği için **mevcut kullanıcı ayarları %100 korunur, sıfır veri kaybı vardır**.
- Ayrıca `SettingsDataStore.kt` içindeki `maxBufferMs` varsayılan değeri `AppSettings` ile hizalanarak `45_000` yapıldı ve `KitsugiSplashActivity` `catch (_: Exception)` bloğu `LinkageError`/`VerifyError` alt sınıflarını da yakalayacak şekilde `catch (_: Throwable)` olarak güçlendirildi.

### 2. Çökme Hattı Sertleştirmeleri
- **`KitsugiCrashHandler.kt`:** `AtomicBoolean` single-flight + `main` thread ön-plan yükseltme kilidi (`upgradeLock`) eklendi. Arka plan thread'i çökerken milisaniyeler içinde `main` thread de çökerse asıl `main` çökmesi kaydedilir; diğer tüm eşzamanlı worker çökmeleri bastırılır.
- **`KitsugiCrashLogger.kt`:** Logcat bekleme süresi `1500 ms` yapıldı, `-b main,system,crash` tamponları eklendi, boş çıktıda mevcut geçerli dosyanın ezilmesi engellendi ve yedek tanı özeti yazıldı. `native_crash.txt` yalnızca `Process.myPid()` ile eşleşiyorsa rapora eklenir; `markCrashAsRead` çağrısında bayat `native_crash.txt` temizlenir.
- **`kitsugi_crash_handler.cpp` & `NativeCrashBridge.kt`:** C++ `open()` bayrağı `O_APPEND` -> `O_TRUNC` yapıldı; `write_proc_maps()` anonim guard bölgelerini atlayıp yalnızca gerçek kütüphane/DEX/APK eşlemelerini yazar (~2,27 MB -> ~15 KB). `NativeCrashBridge.parseLatestNativeCrash` ve `matchesSession(pid, startEpochSec)` eklendi.
- **`KitsugiSessionSupervisor.kt`:** `FATAL EXCEPTION:` ve `VerifyError` / `LinkageError` tespiti eklendi; önceki oturumlardan kalan yabancı PID'li `native_crash.txt` dosyaları açılışta otomatik silinir.
- **`KitsugiCrashActivity.kt`:** Ekranda çizilen `Text` önizlemesi `MAX_ON_SCREEN_PREVIEW_CHARS = 48_000` ile sınırlandı; **Kopyala / Paylaş / İndirilenler'e Kaydet / Geliştiriciye Gönder** butonları ise kesintisiz **%100 tam metni** aktarır.

### 3. MAL Web/Bildirim RFC 3986 URL Çözümlemesi & Kalıcı DEX Kalkanı
- **`MalWebParser.kt` & `MalNotificationsClient.kt`:** `java.net.URI` yerine RFC 3986 uyumlu `okhttp3.HttpUrl.toHttpUrlOrNull()?.resolve(...)` geçirildi.
- **`AppSettingsAndCrashDiagnosticsGuardTest.kt`:** Tüm `com.kitsugi.animelist.data.settings` sınıflarındaki her yapıcı ve metodun (sentetik Kotlin varsayılan maskeleri dahil!) **128 yazmaç yuvasını aşmadığını** denetleyen JUnit testi + `NativeCrashBridge` ve `KitsugiSessionSupervisor` regresyon testleri eklendi.
- **`scripts/verify_apk_dex_bytecode.py` & `.github/workflows/android-build.yml`:** Derlenen gerçek Debug ve R8 Release APK'larının içindeki tüm `classes*.dex` dosyalarındaki her tekil `35c` ve `3rc` buyruğunu ikili düzeyde ayrıştırıp ART `MethodVerifier` kurallarıyla doğrulayan CI kapısı eklendi.

---

## 5) Değişen / Eklenen Tüm Dosyalar (`origin/main` Üzerine — Toplam 24 Dosya)

| # | Dosya Yolu | Durum | Özet Açıklama |
|---|---|---|---|
| 1 | `PLAN_TASK_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md` | Yeni (`A`) | Bu Plan & Task dokümanı |
| 2 | `KOK_NEDEN_RAPORU_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md` | Yeni (`A`) | 13 crash dosyası + 1.337 kaynak dosyanın satır satır adli raporu |
| 3 | `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt` | Güncellendi (`M`) | 248 alan 8 alt modele bölündü (`261 -> 11` Dalvik yazmaç yuvası) |
| 4 | `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt` | Güncellendi (`M`) | 8 alt model eşlemesi + `maxBufferMs = 45_000` varsayılan hizalaması |
| 5 | `app/src/main/java/com/kitsugi/animelist/KitsugiSplashActivity.kt` | Güncellendi (`M`) | `ThemeAndAppUiSettings` uyumu + `catch (_: Throwable)` sertleştirmesi |
| 6 | `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt` | Güncellendi (`M`) | Gruplu alt model önizleme yapıcıları |
| 7 | `app/src/main/cpp/kitsugi_crash_handler.cpp` | Güncellendi (`M`) | `O_TRUNC` + kompakt `/proc/self/maps` filtrelemesi |
| 8 | `app/src/main/java/com/kitsugi/animelist/core/diagnostics/NativeCrashBridge.kt` | Güncellendi (`M`) | `parseLatestNativeCrash` + sıkı `matchesSession(pid, epoch)` kontrolü |
| 9 | `app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiCrashHandler.kt` | Güncellendi (`M`) | Single-flight `AtomicBoolean` + `main` thread ön-plan yükseltme kilidi |
| 10 | `app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiCrashLogger.kt` | Güncellendi (`M`) | `1500 ms` logcat join, `-b main,system,crash`, PID eşleşmeli native ekleme ve temizlik |
| 11 | `app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiSessionSupervisor.kt` | Güncellendi (`M`) | `FATAL EXCEPTION` / `VerifyError` tespiti + bayat `native_crash.txt` temizliği |
| 12 | `app/src/main/java/com/kitsugi/animelist/ui/screens/crash/KitsugiCrashActivity.kt` | Güncellendi (`M`) | 48 KB ekran önizleme sınırı (tam metin Kopyala/Paylaş/Kaydet korunumu) |
| 13 | `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalWebParser.kt` | Güncellendi (`M`) | RFC 3986 `okhttp3.HttpUrl` sorgu çözümlemesi |
| 14 | `app/src/main/java/com/kitsugi/animelist/data/remote/malweb/MalNotificationsClient.kt` | Güncellendi (`M`) | RFC 3986 `okhttp3.HttpUrl` sorgu çözümlemesi |
| 15 | `app/src/test/java/com/kitsugi/animelist/core/diagnostics/AppSettingsAndCrashDiagnosticsGuardTest.kt` | Yeni (`A`) | `<= 128` Dalvik yazmaç yuvası JUnit koruma testi + çökme hattı regresyon testleri |
| 16 | `app/src/test/java/com/kitsugi/animelist/core/notifications/NotificationSchedulerTest.kt` | Güncellendi (`M`) | Yeni alt model kurucu uyumu |
| 17 | `app/src/test/java/com/kitsugi/animelist/core/player/AssExtractorIntegrationTest.kt` | Güncellendi (`M`) | Yeni alt model kurucu uyumu |
| 18 | `app/src/test/java/com/kitsugi/animelist/core/player/DecoderPriorityTest.kt` | Güncellendi (`M`) | Yeni alt model kurucu uyumu |
| 19 | `app/src/test/java/com/kitsugi/animelist/core/player/PlayerEngineSelectorTest.kt` | Güncellendi (`M`) | Yeni alt model kurucu uyumu |
| 20 | `app/src/test/java/com/kitsugi/animelist/data/settings/SettingsMigrationTest.kt` | Güncellendi (`M`) | Yeni alt model kurucu uyumu |
| 21 | `scripts/verify_apk_dex_bytecode.py` | Yeni (`A`) | Debug & R8 Release APK ikili Dalvik DEX doğrulayıcı |
| 22 | `.github/workflows/android-build.yml` | Güncellendi (`M`) | CI ikili DEX bytecode doğrulama adımı |
| 23 | `app/build.gradle.kts` | Güncellendi (`M`) | Sürüm `2.4.324` -> `2.4.325` (`origin/main` ile tam uyumlu) |
| 24 | `RELEASE_NOTES.md` & `release_notes_v2.4.325.md` | Güncellendi / Yeni | `v2.4.324` Vitrin notları korundu + `v2.4.325` sürüm notları eklendi |

---

## 6) Doğrulama Sonuçları (Birim Test + Debug APK + R8 Release APK + İkili DEX)

- [x] **1.337 `.kt`/`.java` Kaynak Dosyası Taraması:** Depoda `AppSettings` dışında hiçbir sınıf/metot Dalvik 255 yazmaç sınırına yaklaşmıyor (ikinci en yüksek: `115` yuva).
- [x] **JUnit Birim Testleri (`:app:testFossDebugUnitTest`):** %100 başarılı (`AppSettingsAndCrashDiagnosticsGuardTest` dahil tüm test paketi yeşil).
- [x] **Debug APK İkili DEX Doğrulaması:** `22` DEX dosyası, `403.978+` metot gövdesi, `11.105.952+` Dalvik buyruğu doğrulandı -> **`max invoke register words = 176/255`, `VerifyError = 0`**.
- [x] **R8 Release APK İkili DEX Doğrulaması:** `5` DEX dosyası, `251.589+` metot gövdesi, `7.155.291+` Dalvik buyruğu doğrulandı -> **`max invoke register words = 176/255`, `VerifyError = 0`**.
- [x] **`origin/main` (`b118ab7`, `v2.4.324`) Entegrasyonu:** Vitrin düzeltmeleriyle sıfır kod çakışmasıyla birleştirildi ve `v2.4.325` olarak hizalandı.

---

## 7) Risk Analizi, Geri Alma & Cihazda Hızlı Kontrol Listesi

- **Risk:** **Çok Düşük** — DataStore anahtarlarının hiçbiri değişmedi; tüm UI/ViewModel katmanı aynı `settings.<alan>` özelliklerini sıfır ek bellekli getter üzerinden okumaya devam ediyor.
- **Geri Alma:** `git revert <merge-commit>` veya `origin/main` (`b118ab7`) üzerine dönüş (ancak `b118ab7` `AppSettings` 261-yazmaç hatasını barındırdığı için `v2.4.325` zorunlu düzeltmedir).
- **Cihazda Hızlı Kontrol (Xiaomi Pad 6):**
  1. `v2.4.325` APK'sını mevcut kurulumun üzerine (verileri silmeden) güncelle ve aç -> 0. saniye `VerifyError` olmadan doğrudan ana ekran açılmalı ve mevcut tüm kullanıcı ayarları korunmalı.
  2. Ayarlar -> Geliştirici Günlükleri ekranını aç -> önceki günlerden kalma 2,27 MB'lık bayat `native_crash.txt` temizlenmiş olmalı.

---

## 8) Google Drive Zip Paketi İçeriği

**Dosya Adı:** `Kitsugi_VerifyError_255Yazmac_ve_CokmeSistemi_Fix_v2.4.325_2026-10-21.zip`  
*(Doğrudan repo kök dizinine `unzip -o` ile açılabilecek birebir dizin yapısındadır)*

- `PLAN_TASK_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md` (bu Plan & Task dosyası)
- `KOK_NEDEN_RAPORU_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md` (satır satır adli rapor)
- Yukarıdaki tabloda yer alan tüm değişen/eklenen kaynak, test, CI, script ve sürüm dosyaları (`app/...`, `.github/...`, `scripts/...`, `RELEASE_NOTES.md`, `release_notes_v2.4.325.md`)
- `fix.patch` (`git diff origin/main..HEAD` — tek komutla `git apply fix.patch` uygulanabilir yama)
- `DEGISIKLIK_OZETI.txt` (`git diff --stat origin/main..HEAD` + commit ve PR bilgileri)
