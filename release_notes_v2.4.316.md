# Kitsugi Beta v2.4.316

## 🇹🇷 Türkçe

### ⚡ İndirmeler Ekranı & İndirme Sırasında Donma / Kasma Düzeltmesi

Bu sürüm, arka planda veya aktif olarak bir bölüm indirilirken İndirmeler (`DownloadsScreen`) ekranının kilitlenmesine, kaydırmanın donmasına ve arayüzün saniyede binlerce kez gereksiz yere yeniden çizilmesine (recomposition loop) yol açan kök nedenleri tamamen çözer. Ayrıntılı plan ve analiz: `PLAN_TASK_INDIRMELER_SAYFASI_KASMA_FIX_2026-10-20.md`.

- **İndirme İlerleme Hız Sınırlaması (Throttling) & 64 KB Tamponlama (`AnimeDownloader`):**
  - Doğrudan ve HLS dosya indirmelerinde 8 KB'lık mikro okumalar yerine `64 KB BufferedOutputStream` ve `64 KB` tampona geçildi.
  - Saniyede 500-2.000 kez tetiklenerek ana StateFlow'u ve UI'ı boğan `onProgress` geri çağrıları `SystemClock.elapsedRealtime()` tabanlı 250 ms hız sınırlaması (throttling) ile dengelendi (yüzde değişimlerinde en az 100 ms ve indirme tamamlandığında kesin %100 bildirimi).
  - HLS segment indirmelerinde her segmentte diske `localFile.length()` sormak yerine indirilen bayt bellekte takip edildi.
- **İlerleme Emniyet Kilidi & Asenkron Disk İşlemleri (`AnimeDownloadManager`):**
  - `updateProgress` metoduna 200 ms hız sınırlaması ve değişmeyen değerler için erken çıkış filtresi eklendi.
  - `saveAndEmit`, `deleteDownload` ve `clearAllDownloads` içindeki Gson serileştirme ve dosya/klasör silme işlemleri UI thread'den `Dispatchers.IO` (`ioScope`) üzerine taşındı.
- **Ana İş Parçacığında Disk Taraması Kaldırıldı (`DownloadsScreen`):**
  - `loadAllDownloadedSubtitles` ve `loadAllDownloadedImages` tamamen `Dispatchers.IO` (`produceState`) üzerine alındı.
  - Altyazı disk taraması aktif indirme baytlarından izole edildi; yalnızca tamamlanan indirmelerin anahtarına (`completedDownloadsKey`) bağlandı.
  - `DownloadItemRow` içinde kart bazlı metadata/altyazı disk okuması indirme sürerken (`DOWNLOADING`) devre dışı bırakıldı; yalnızca `COMPLETED` durumunda `Dispatchers.IO`'da çalışacak şekilde optimize edildi.
  - Tüm Regex derlemeleri dosya düzeyinde tek seferlik önbelleğe alındı; $O(N^2)$ `results.none` kontrolleri $O(1)$ `HashSet` ile değiştirildi.
  - Resimler sekmesindeki çoklu görsel grupları `chunked(4)` ile satır bazlı tembel yüklemeye (`LazyRow`) çevrilerek ilk yükleme rahatlatıldı.
- **Tek Geçişli Klasör Taraması (`OfflinePlaybackHelper`):**
  - İndirilen her video için diski baştan sona tekrar tarayan $O(N^2)$ `listFiles()` döngüsü tek geçişli önbelleklemeli yapıya (`primaryEntries`) indirgendi.
- **Kararlı Compose Modeli & Hafif Bildirim IPC'si:**
  - `AnimeDownload` modeli `@Immutable` olarak işaretlendi ve tüm değişken alanları (`var`) değiştirilemez (`val`) yapıldı.
  - Bildirim posteri maksimum `384px` boyuta küçültülerek Binder IPC bellek ve işlemci yükü düşürüldü (`AnimeDownloadService`).
  - `ApiDetailTabContents` ve `EntryDetailTabContents` içerisindeki gereksiz üst seviye `downloads.collectAsState()` abonelikleri doğrudan liste değeri okumasına çevrildi.

---

## 🇬🇧 English

### ⚡ Downloads Screen Performance & Lag Elimination During Active Downloads

This release eliminates UI freezing, frame drops, and severe jank on the Downloads screen (`DownloadsScreen`) while videos are being downloaded in the background. Detailed root-cause breakdown in `PLAN_TASK_INDIRMELER_SAYFASI_KASMA_FIX_2026-10-20.md`.

- **Download Progress Throttling & 64 KB Buffering (`AnimeDownloader`):**
  - Upgraded direct and HLS downloads to `64 KB BufferedOutputStream` with a `64 KB` read buffer (up from 8 KB).
  - Added a 250ms throttling guard (`SystemClock.elapsedRealtime()`) to `onProgress` callbacks, preventing hundreds/thousands of socket-driven recomposition events per second while ensuring 100% completion is reported accurately.
  - Tracked HLS segment byte lengths in memory rather than querying `localFile.length()` from disk on every segment.
- **Progress Safety Gate & IO Thread Offloading (`AnimeDownloadManager`):**
  - Added a 200ms throttle guard and no-op check to `updateProgress`.
  - Moved Gson serialization, export cleanup, and file deletion out of the UI thread into `ioScope` (`Dispatchers.IO`).
- **Main-Thread Disk Scans Removed (`DownloadsScreen`):**
  - Offloaded `loadAllDownloadedSubtitles` and `loadAllDownloadedImages` entirely to `Dispatchers.IO` via `produceState`.
  - Decoupled subtitle scanning from continuous download byte updates, listening only to completed download state keys.
  - Bypassed per-card disk metadata reads in `DownloadItemRow` while downloading, restricting them to completed downloads on IO dispatcher.
  - Cached regex instances at file level and converted $O(N^2)$ checks to $O(1)$ `HashSet` lookups.
  - Partitioned image groups into `chunked(4)` lazy rows to optimize memory and initial render time.
- **Single-Pass Directory Traversal (`OfflinePlaybackHelper`):**
  - Replaced repetitive $O(N^2)$ directory scans across `root.listFiles()` with a single-pass indexed scan.
- **Compose Stability & Notification IPC Optimization:**
  - Marked `AnimeDownload` as `@Immutable` and transitioned all fields from `var` to `val`.
  - Downscaled notification posters to a maximum of `384px`, significantly decreasing Binder IPC transmission overhead.
  - Eliminated high-level `downloads.collectAsState()` subscriptions in `ApiDetailTabContents` and `EntryDetailTabContents`.
