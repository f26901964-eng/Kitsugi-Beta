# Kitsugi Beta v2.4.321

## 🇹🇷 Türkçe

### 🛠️ Açılış / Çökme Düzeltmeleri (kararlılık sürümü)

Bu sürüm, gönderilen çökme raporundaki **kesin kök nedeni** düzeltir ve "uygulama açılmıyor" şikâyetlerinin
tekrar kör kalmaması için teşhis zincirini onarır.

- **Geliştirici Günlükleri metin seçimi çökmesi (kök neden) kaldırıldı**
  - Hata: `java.util.NoSuchElementException: Cannot find value for key N`
    (`SelectionManager.getSelectionLayout` → `MultiWidgetSelectionDelegateKt.getDirectionById`)
  - Sebep: `SelectionContainer { LazyColumn { ... } }` kalıbı. Lazy liste öğeleri kaydırılınca
    kompozisyondan düşüyor; Compose'un seçim yöneticisi sürükleme sırasında hâlâ o öğenin
    `selectableId`'sini arıyor ve uygulamayı öldürüyordu. (Upstream: compose-multiplatform-core#3480,
    Compose 1.7.x'te açık.)
  - Çözüm: Seçim kapsamı her günlük satırının **kendi içine** alındı. Her `SelectionContainer`
    tek bir `Text` barındırdığı için kaybolabilecek çoklu seçim kaydı oluşmuyor. Uzun basıp
    satır seçme/kopyalama çalışmaya devam ediyor.
  - Etki: Hakkında → Geliştirici Günlükleri, Ayarlar → Geliştirici günlükleri ve çökme raporu kartı.

- **Çökme uyarısı artık her açılışta tekrar etmiyor**
  - "Beklenmedik Kapanma Tespit Edildi" diyaloğu geri tuşuyla / dışına dokunarak kapatıldığında
    "okundu" işaretlenmiyordu; bu yüzden uygulama **her açılışta** aynı eski raporla karşınıza
    geliyordu. Kullanıcı bunu "uygulama açılmıyor / çöküyor" olarak yaşıyordu.
  - Artık her kapanış okundu sayılır. Rapor dosyaları silinmez; Geliştirici Günlükleri'nden ve
    Hakkında ekranındaki rapor kartından erişilebilir.
  - Dosya tabanlı "sessiz kapanma" uyarılarına 7 gün tazelik sınırı getirildi.

- **Paylaşılan çökme raporu artık eskisi değil, EN YENİSİ**
  - `ensureCrashLogFile` yalnızca "dosya var mı?" diye bakıyordu: `crash_log.txt` günler önceki bir
    Java çökmesini tutarken daha yeni bir sessiz kapanma (native/SIGSEGV/LMKD) olabiliyordu. Bu yüzden
    "Hata dosyasını paylaş" hep aynı **eski** raporu gönderiyordu (indirdiğiniz dosyaların " (31)"
    olması bundandır).
  - Artık paylaşılan dosya her zaman `readCrashLog()` çıktısı: en yeni Java çökmesi / sessiz kapanma /
    native iz / başlatma aşaması arasından en tazesi seçilir. `crash_log.txt` arşivi ezilmez.

- **"Uygulama açılmadan çöküyor" için kalıcı teşhis: açılış aşaması izi**
  - `Application.attachBaseContext`, `onCreate`, Splash, MainActivity ve her alt sistem init'i
    `filesDir/startup_stage.txt` dosyasına "buradayım" yazıyor. Ana ekran ilk kareyi çizince siliniyor.
  - Süreç ana ekran açılamadan ölürse (native çökme, class-load hatası, sistem kill — Java handler'ın
    hiç çalışmadığı durumlar) bu dosya rapora "son çalışan init adımı" olarak ekleniyor. Bir daha
    körlemesine tahmin yok.

- **Açılış zinciri artık izole**
  - `AnimeDownloadManager`, `WatchHistoryManager`, `FileLoggingTree`, `CsTrace`,
    `KitsugiEpisodeRatingsRepository`, `P2pSettingsRepository`, `P2pStreamingEngine`, `DnsManager`
    ve bellek bekçisi init'leri `runCatching` içine alındı. Biri hata verirse uygulama açılmamazlık
    etmiyor; hata loglanıyor, ilgili alt sistem devre dışı kalıyor.
  - Splash'taki `ExploreViewModel.prefetch` (super.onCreate'ten ÖNCE çalışıyordu) ve açılış
    animasyonu da korumaya alındı; animasyon/WebView patlarsa doğrudan ana ekrana geçilir.

### 🗓️ Haftalık Yayın Takvimi: "İlk Açılışta Boş Geliyor" Kök Nedeni Düzeltildi

Takvim hangi kaynak seçili olursa olsun (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi ve "Tümü") ilk açılışta boş görünüyor, geri çıkıp ikinci/üçüncü kez girildiğinde ise veri geliyordu. Sorun ağda değil, arayüz durum yönetiminde bir mantık hatasıydı. Ayrıntılı analiz: `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`.

- **Asıl neden — `remember` anahtarı yerinde değişen map:** `weekSchedule` bir `SnapshotStateMap` ve `clear()` + `putAll()` ile **yerinde** güncelleniyor. Map referansı hiç değişmediği için `remember(viewModel.weekSchedule, …)` anahtarı Compose tarafından "değişmedi" sayılıyordu; ilk açılışta (veri henüz yokken) hesaplanan **boş** harita önbellekte kalıyor, ağdan veri gelmesine rağmen ekran boş kalıyordu. Geri çıkıp tekrar girmek ekranı yeniden kurduğu için veri "ikinci açılışta" görünüyordu.
- **Çözüm:** `filteredScheduleMap` artık canlı map yerine içerik kopyası (`toMap()`) anahtarıyla hesaplanıyor. Bu hem snapshot okuması kaydeder (veri gelince recomposition) hem de içerik değiştiğinde `remember` önbelleğini geçersiz kılar. Gün sekmelerindeki rozetler ve başlıktaki toplam sayı da ilk açılışta doğru gelir.
- **Sessiz başarısızlık sona erdi:** AniList 429 cooldown'ı veya ağ hatası önceden "boş harita" olarak yutuluyor, ekran "Bu gün için yayın bulunamadı." diyordu. Artık yükleme başarısızsa **"Takvim yüklenemedi" + Yeniden Dene** durumu gösterilir; yanlış "veri yok" mesajı verilmez. Sonraki sayfa istekleri başarısız olursa eldeki kısmi veri korunur.
- **TMDB tarafı:** 8 isteğin hiçbiri başarılı olamazsa (API anahtarı/ağ) hata bildirilir; önceden takvim sessizce boş kalıyordu.
- **"Tümü" modu dayanıklılığı:** AniList veya TMDB'den biri hata verse bile diğer kaynağın verisiyle kısmi takvim gösterilir; yalnızca ikisi de boş + hatalıysa hata durumuna düşülür.
- **Kısa AniList cooldown'ları bekleniyor:** Keşfet ekranı kotayı doldurduğunda takvim açılışında istek hiç gönderilmeden `null` dönüyordu. 8 saniyeye kadar olan cooldown'lar beklenip istek gerçekten gönderilir (uzun cooldown'da fail-fast korunur).
- **Bayat veri:** Başarılı bir yükleme 10 dakika taze sayılır; süre dolduğunda ekran açılışında arka planda sessizce tazelenir (eski veri ekranda kalır, boş ekran görünmez) → gün/hafta değişimlerinde bayat takvim gösterilmez.

---

## 🇬🇧 English

### 🛠️ Startup / Crash Fixes (stability release)

- **Fixed the root cause of the Developer Logs text-selection crash**: `SelectionContainer` was
  wrapping a `LazyColumn`, so scrolled-out items got disposed while the selection manager still
  referenced their `selectableId` → `NoSuchElementException: Cannot find value for key N`
  (`MultiWidgetSelectionDelegateKt.getDirectionById`). Selection is now scoped per log line
  (one selectable per container). Upstream: compose-multiplatform-core#3480.
- **Crash recovery dialog no longer reappears on every launch**: dismissing it by back/outside tap
  now marks the crash as read (report files are preserved and remain accessible).
- **Shared crash report is always the freshest evidence**: the old code reused `crash_log.txt` even
  when a newer silent/native exit report existed, so users kept sending the same stale report
  (hence the " (31)" duplicate filenames). `ensureCrashLogFile` now materialises `readCrashLog()`.
- **Startup stage tracer**: every init step writes to `filesDir/startup_stage.txt`; the file is
  cleared after the first UI frame. If the process dies before the UI appears, the report now shows
  the last executed startup step.
- **Isolated startup initialisers**: download/history/log/ratings/P2P/DNS/memory initialisers,
  splash prefetch and splash animation can no longer prevent the app from opening.

### 🗓️ Weekly Airing Calendar: "Empty on First Open" Root Cause Fixed

The calendar appeared empty on the first open for every source (AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi and "All"), yet data showed up after going back and re-entering the screen. The cause was not the network but a state-management logic bug. Full analysis: `KOK_NEDEN_RAPORU_TAKVIM_ILK_ACILIS_BOS_2026-10-21.md`.

- **Root cause — `remember` keyed on an in-place mutated map:** `weekSchedule` is a `SnapshotStateMap` updated in place via `clear()` + `putAll()`. Because the map reference never changed, Compose treated `remember(viewModel.weekSchedule, …)` as unchanged: the **empty** map computed on first open (before data arrived) stayed cached forever, so the screen remained empty even after the data landed. Leaving and re-entering rebuilt the composition, which is why the data appeared on the second open.
- **Fix:** `filteredScheduleMap` is now derived from a content copy (`toMap()`) used as the `remember` key. That registers a snapshot read (so new data triggers recomposition) and invalidates the cache when the content changes. Day-tab badges and the header total are correct from the very first open.
- **No more silent failures:** AniList 429 cooldowns or network errors used to be swallowed into an empty map and surfaced as "No airings today." Failures now show **"Calendar could not be loaded" + Retry**; partial data is preserved when only later pages fail.
- **TMDB:** If none of the 8 requests succeed (API key/network), an error is reported instead of a silently empty calendar.
- **"All" mode resilience:** If either AniList or TMDB fails, the other source still produces a partial calendar; the error state is used only when both are empty and failing.
- **Short AniList cooldowns are awaited:** When the Explore screen exhausted the quota, the calendar request returned `null` without ever hitting the network. Cooldowns up to 8 seconds are now awaited before sending; longer cooldowns keep the fail-fast behaviour.
- **Stale data:** A successful load counts as fresh for 10 minutes; afterwards the screen silently refreshes in the background (previous data stays on screen, no empty flash), so day/week rollovers no longer show a stale calendar.
