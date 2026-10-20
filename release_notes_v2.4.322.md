# Kitsugi Beta v2.4.322

## 🇹🇷 Türkçe

### 🛠️ Geliştirici Günlükleri Seçim Çökmesi & Açılış Sertleştirmesi

Bu sürüm, gönderilen çökme raporundaki **kesin kök nedeni** (`NoSuchElementException: Cannot find value for key 1`) düzeltir ve açılış zincirini olası çökme veya sistem sonlandırmalarına karşı tam izole eder. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md` ve `PLAN_TASK_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_VE_ACILIS_SERTLESTIRMESI_2026-10-21.md`.

- **Geliştirici Günlükleri metin seçimi çökmesi giderildi (`DeveloperLogsDialog.kt`):**
  - **Hata:** `java.util.NoSuchElementException: Cannot find value for key N` (`SelectionManager.getSelectionLayout` -> `MultiWidgetSelectionDelegateKt.getDirectionById`).
  - **Kök Neden:** `SelectionContainer { LazyColumn { ... } }` deseni. `LazyColumn` satırları kaydırılınca kompozisyondan düşüyor, Compose seçim yöneticisi serbest bırakılan öğenin kimliğini ararken süreci düşürüyordu.
  - **Çözüm:** Seçim kapsamı her günlük satırının kendi içine alındı. Artık her `SelectionContainer` tek bir `Text` barındırıyor, çoklu seçim kaybolması ve çökme riski tamamen ortadan kalktı. Satır kopyalama ve seçme eksiksiz çalışmaya devam eder.
- **Çökme uyarısı artık her açılışta tekrar etmiyor (`MainActivity.kt`):**
  - "Beklenmedik Kapanma Tespit Edildi" diyaloğu kapatıldığında "okundu" işaretleniyor; eski raporun her uygulama açılışında ekrana gelmesi engellendi. Dosya tabanlı sessiz kapanma uyarılarına 7 gün tazelik sınırı getirildi.
- **En taze çökme raporu paylaşımı (`KitsugiCrashLogger.kt` & `KitsugiCrashActivity.kt`):**
  - `ensureCrashLogFile` artık her zaman `readCrashLog()` ile en güncel logu üretir; eski `crash_log.txt` arşivi ezilmez, paylaşılan rapor her zaman en taze çökme kanıtını içerir.
- **Açılış aşaması izleme mekanizması (`StartupStage.kt`):**
  - Başlangıç adımları (`filesDir/startup_stage.txt`) anlık takip edilir; ana ekran ilk kareyi çizince silinir. UI'dan önce süreç ölürse son çalışan init adımı rapora kaydedilir.
- **Açılış zinciri izolasyonu (`KitsugiApplication.kt` & `KitsugiSplashActivity.kt`):**
  - İndirme yöneticisi, izleme geçmişi, DNS, P2P motoru vb. tüm alt sistem init'leri `runCatching` ile korumaya alındı; bir sistemdeki hata tüm uygulamanın açılışını engellemez.
  - Açılış animasyonu ve prefetch çağrıları korumaya alındı.

---

## 🇬🇧 English

### 🛠️ Developer Logs Selection Crash & Startup Hardening

Resolves the exact root cause (`NoSuchElementException: Cannot find value for key 1`) from diagnostic crash logs and insulates the startup lifecycle against aborts. Detailed analysis in `KOK_NEDEN_RAPORU_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_2026-10-21.md` and `PLAN_TASK_GELISTIRICI_GUNLUKLERI_SECIM_COKMESI_VE_ACILIS_SERTLESTIRMESI_2026-10-21.md`.

- **Resolved Text Selection Crash in Developer Logs (`DeveloperLogsDialog.kt`):**
  - **Root Cause:** `SelectionContainer { LazyColumn { ... } }` pattern. When lazy items were scrolled out of view, Compose's `SelectionManager` failed to find evicted selectable IDs (`getDirectionById`), throwing `NoSuchElementException`.
  - **Fix:** Confined `SelectionContainer` into each individual log row. Long-press text selection and copying remain intact without layout leaks.
- **Persistent Crash Dialog Resolved (`MainActivity.kt`):**
  - Crash notices are marked as read on dismissal; stale dialogs will not repeatedly prompt on every cold start. Imposed a 7-day expiration on silent file-based notices.
- **Fresh Crash Log Export (`KitsugiCrashLogger.kt` & `KitsugiCrashActivity.kt`):**
  - `ensureCrashLogFile` now captures the freshest diagnostic dump (`readCrashLog()`) instead of serving stale historical archives.
- **Startup Stage Tracing (`StartupStage.kt`):**
  - Logs progressive initialization steps to `filesDir/startup_stage.txt`. Cleared on first frame render; if a pre-UI death occurs, the fatal stage is captured in the error report.
- **Isolated Initialization Chain (`KitsugiApplication.kt` & `KitsugiSplashActivity.kt`):**
  - Subsystems (Downloads, Watch History, DNS, P2P engine) are wrapped in `runCatching` so isolated startup exceptions cannot block app launch.
  - Splash prefetch and animations are safely guarded.
