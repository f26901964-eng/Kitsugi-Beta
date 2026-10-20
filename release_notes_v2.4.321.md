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
