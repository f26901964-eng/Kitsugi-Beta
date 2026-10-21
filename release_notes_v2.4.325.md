# Kitsugi Beta v2.4.325

## 🇹🇷 Türkçe

### 🧯 Kritik Açılış Çökmesi (0. Saniye `java.lang.VerifyError`) ve Çökme Raporlama Hattı Kökten Düzeltildi

Google Drive üzerinden iletilen 13 tanı dosyasının tamamı satır satır incelenerek v2.4.320–v2.4.324 sürümlerinde uygulamayı açıldığı anda (`Uptime: 0s`) çökerten birincil Dalvik DEX bayt kodu hatası ve çökme raporlama hattındaki 6 ayrı ikincil hata kökten giderildi. Ayrıntılı adli analiz ve görev planı:
- `PLAN_TASK_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md`
- `KOK_NEDEN_RAPORU_VERIFYERROR_255_YAZMAC_TASTMASI_VE_COKME_SISTEMI_2026-10-21.md`

- **1. Birincil Kök Neden — `AppSettings` Dalvik DEX 255-Yazmaç Taşması (`VerifyError` @ `0x1FE` & `0x275`):**
  - **Hata:** `java.lang.VerifyError: Verifier rejected class com.kitsugi.animelist.data.settings.AppSettings` (`[0x1FE]`) ve R8 yatay sınıf birleştirmesi (`HorizontalClassMerger`) nedeniyle `androidx.room.r.g(java.lang.Object)` (`[0x275]`): `Rejecting invocation, expected 5 argument registers, method signature has 6 or more`.
  - **Kesin Mekanizma:** v2.4.319'da 241 alanı (sentetik varsayılan yapıcıda 254 yazmaç — sınıra 1 yazmaç kala!) bulunan `data class AppSettings`, v2.4.320 ve sonrasında 248 alana ulaştı (birincil `<init>` 252 yazmaç, Kotlin sentetik `<init>(..., mask0..mask7, DefaultConstructorMarker)` ve `copy$default` ise **261 yazmaç**). Dalvik `invoke-*/range` (`3rc`) buyruğundaki `AA` alanı 8 bitlik işaretsiz tamsayı (`0..255`) olduğundan D8/R8 `261 & 0xFF = 5` taşmasıyla buyruğa `5` yazdı; Android ART `MethodVerifier` ise 261 parametreli imzayla karşılaştırıp sınıfı anında reddetti. Ayrıca R8 `HorizontalClassMerger`, `KitsugiSplashActivity` içindeki `SuspendLambda`'yı Room işlem sınıfı `androidx.room.r` ile birleştirdiği için hem `WM.task-1` (59. ms) hem `main` UI thread'i (198. ms) daha ekran çizilemeden çöktü.
  - **Mimari Çözüm (`AppSettings.kt`, `SettingsDataStore.kt`, `KitsugiSplashActivity.kt`, `SettingsScreen.kt`):** 248 alan; `ProfileAndListSettings` (42), `ThemeAndAppUiSettings` (19), `PlayerPlaybackSettings` (28), `PlayerOverlaySettings` (28), `MpvAndSubtitleSettings` (36), `GestureAndSkipSettings` (24), `IntegrationsAndNotificationsSettings` (40) ve `DownloadAndMangaSettings` (31) olmak üzere 8 alt veri sınıfına ayrıldı. `AppSettings` birincil/sentetik yapıcısı **261 yazmaçtan 11 yazmaca (%95,8 düşüş)**, en büyük alt sınıf ise **46 yazmaca** indirildi; tüm 248 alan sıfır ek bellekli hesaplanan özellik (`get()`) olarak %100 geriye dönük uyumlu korundu.
- **2. Çökme İşleyicisi Çoklu-Thread Yarışı Önlemi (`KitsugiCrashHandler.kt`):**
  - Birden fazla arka plan worker'ının aynı milisaniyede çöktüğü durumlarda (ör. 6+ `DefaultDispatcher-worker` fırtınası) tek uçuş (`AtomicBoolean` + kilitli ön-plan yükseltmesi) koruması eklendi; `crash_log.txt` ezilmesi, çoklu `KitsugiCrashActivity` başlatılması ve İndirilenler'e kopya dosya yağması engellendi.
- **3. Boş (73 Bayt) `logcat_at_crash.txt` Sorunu Çözüldü (`KitsugiCrashLogger.kt`):**
  - 400 ms'lik erken `process.destroy()` zaman aşımı 1500 ms'ye çıkarıldı, `main,system,crash` tamponları birleştirildi ve boş çıktı durumunda taze dosyanın ezilmesi engellenip doğrudan çökme raporu yedeği eklendi.
- **4. 2,27 MB'lık Bayat `native_crash.txt` Sızıntısı Giderildi (`kitsugi_crash_handler.cpp`, `NativeCrashBridge.kt`, `KitsugiSessionSupervisor.kt`):**
  - C++ sinyal yakalayıcısındaki `O_APPEND` bayrağı `O_TRUNC` yapıldı ve `/proc/self/maps` çıktısında boş anonim guard bölgeleri elenerek boyut ~2,3 MB'tan ~15 KB'a düşürüldü.
  - `NativeCrashBridge` artık `pid=` ve `epoch=` alanlarını ayrıştırıp yalnızca çöken sürece ait izi iliştirir; önceki günlerden kalan bayat `native_crash.txt` dosyaları ilk açılışta otomatik temizlenir.
- **5. Post-Mortem Logcat Analizinde Java/ART Fatal İstisna Tespiti (`KitsugiSessionSupervisor.kt`):**
  - `FATAL EXCEPTION:` ve `VerifyError` / `LinkageError` satırları artık doğrudan ölüm sebebi başlığına yazılır; kanıt satırları varken yanlış `"SEBEP BULUNAMADI"` gösterilmez.
- **6. Çökme Ekranı Büyük Metin Render Koruması (`KitsugiCrashActivity.kt`):**
  - Kopyala / Paylaş / İndirilenler'e Kaydet tam metni korurken ekrandaki önizleme 48 KB ile sınırlandırılarak `StaticLayout` takılması ve `GL_MAX_TEXTURE_SIZE` taşması engellendi.
- **7. MAL Web/Bildirim Sayfalama RFC 3986 URL Çözümleme Düzeltmesi (`MalWebParser.kt`, `MalNotificationsClient.kt`):**
  - `java.net.URI.resolve("?p=2")` (RFC 2396) hatası nedeniyle son yol bileşeninin düşmesi sorunu `okhttp3.HttpUrl` (RFC 3986) ile giderildi.
- **8. Kalıcı Regresyon ve İkili DEX Doğrulama Kalkanı (`AppSettingsAndCrashDiagnosticsGuardTest.kt`, `scripts/verify_apk_dex_bytecode.py`):**
  - Hem birim testi düzeyinde yazmaç sınırı denetimi hem de derlenen gerçek R8 Release ve Debug APK'larının içindeki tüm `classes*.dex` buyruklarını ART `MethodVerifier` kurallarıyla doğrulayan CI adımı eklendi.

---

## 🇬🇧 English

### 🧯 Critical Cold-Start Crash (`java.lang.VerifyError` at `Uptime: 0s`) & Crash Diagnostics Pipeline Overhaul

Line-by-line forensic inspection of all 13 diagnostic artifacts identified and permanently resolved the primary Dalvik DEX bytecode overflow killing v2.4.320–v2.4.324 immediately at launch (`Uptime: 0s`), alongside 6 secondary bugs in the crash reporting pipeline and MAL pagination URL resolution.

- **1. Primary Root Cause — `AppSettings` Dalvik DEX 255-Register Overflow (`VerifyError` @ `0x1FE` & `0x275`):**
  - `AppSettings` crossed from 254 synthetic constructor slots in v2.4.319 to **261 register slots** in v2.4.320+, overflowing the 8-bit `AA` count field (`261 & 0xFF = 5`) in Dalvik `3rc` `invoke-*/range` instructions and crashing both `SettingsDataStore` (`AppSettings.<init>`) and R8 horizontally merged `SuspendLambda` classes (`androidx.room.r.g`) on `WM.task-1` and `main`.
  - Refactored `AppSettings` into 8 domain sub-models (`AppSettings` synthetic constructor reduced from **261 -> 11 slots**, max sub-model **46 slots**) while preserving all 248 properties via zero-overhead delegated getters.
- **2. Crash Pipeline Hardening (`KitsugiCrashHandler`, `KitsugiCrashLogger`, `NativeCrashBridge`, `KitsugiSessionSupervisor`, `KitsugiCrashActivity`, `kitsugi_crash_handler.cpp`):**
  - Added single-flight coordination with foreground main-thread upgrade lock against multi-worker crash stampedes.
  - Fixed 73-byte empty `logcat_at_crash.txt` captures (1500 ms join window, `main,system,crash` buffers, non-destructive fallback).
  - Replaced `O_APPEND` with `O_TRUNC` and filtered anonymous `/proc/self/maps` regions in native signal handler; added strict `pid`/`epoch` matching and automatic cleanup of legacy multi-MB `native_crash.txt` files.
  - Added `FATAL EXCEPTION` / `VerifyError` recognition to post-mortem logcat analysis and 48 KB UI render capping in `KitsugiCrashActivity`.
- **3. MAL Web & Notifications RFC 3986 Pagination Resolution (`MalWebParser.kt`, `MalNotificationsClient.kt`) & CI DEX Bytecode Verifier (`scripts/verify_apk_dex_bytecode.py`):**
  - Replaced broken RFC 2396 `java.net.URI.resolve("?...")` calls with RFC 3986 `okhttp3.HttpUrl.resolve` and added full binary `classes*.dex` instruction verification on both Debug and R8 Release APKs in CI.
