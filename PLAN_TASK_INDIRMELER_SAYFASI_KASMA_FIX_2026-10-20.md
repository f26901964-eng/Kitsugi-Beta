# PLAN_TASK — İndirmeler Sayfası İndirme Sırasında Kasma (Lag/Jank) Çözümü (2026-10-20)

## 1. Sorun Özeti
Bir video indirilirken (`AnimeDownloader` / `AnimeDownloadService` aktifken) **İndirmeler (`DownloadsScreen`)** ekranı aşırı derecede kasıyor, kaydırma ve sekme geçişleri donuyordu.

## 2. Kök Neden Analizi

1. **`AnimeDownloader.kt` — Saniyede Binlerce Kez Tetiklenen `onProgress` Döngüsü**
   - `downloadDirectFile` içinde `8 KB` tamponla (`while (inputStream.read(buffer) != -1)`) yapılan her soket okumasında (ör. `2,5 MB/s` hızda saniyede **500–2.000 kez**) `onProgress(...)` → `AnimeDownloadManager.updateProgress(...)` çağrılıyordu.
   - `downloadedBytes` her okumada değiştiği için `AnimeDownloadManager._downloads` `StateFlow`'u saniyede binlerce kez yeni `List<AnimeDownload>` kopyası yayarak Compose arayüzünü kesintisiz recomposition'a zorluyordu.

2. **`DownloadsScreen.kt` — Her İlerleme Güncellemesinde UI Thread'de Senkron Disk Taraması**
   - `val allSubtitles = remember(downloads, refreshSubsTrigger) { loadAllDownloadedSubtitles(context, downloads) }` ifadesinde `remember` anahtarı doğrudan `downloads` listesiydi.
   - İndirme sürerken `downloads` her karede değiştiği için Compose **her karede (60–120 FPS) ana iş parçacığında (Main Thread) senkron olarak** `loadAllDownloadedSubtitles` çalıştırıyor; `Download/Kitsugi/Video`, `Download/Kitsugi/Subtitles` ve `cacheDir/subtitles` klasörlerinde `listFiles()`, `.json` okuma, `length()`, `lastModified()` ve her dosya için yeniden `Regex` derleme yapıyordu.
   - `loadAllDownloadedImages(context)` de ekran açılışında 6 dizini ve yüzlerce resmi (ör. 269 resim) UI thread üzerinde senkron tarıyor ve $O(N^2)$ `results.none` kontrolü yapıyordu.

3. **`DownloadItemRow` — Her Byte Artışında Kart İçinde Senkron `OfflinePlaybackHelper.getLocalMedia` Çağrısı**
   - `val localMedia = remember(download) { OfflinePlaybackHelper.getLocalMedia(context, mediaId) }` tüm `AnimeDownload` nesnesine (`downloadedBytes` ve `progress` dahil) bağlıydı.
   - İndirme devam ederken her karede UI thread üzerinde `Download/Kitsugi/Video` dizini listeleniyor ve diskteki tüm `.json` metadata dosyaları okunup ayrıştırılıyordu.

4. **Compose Kararsızlığı (`var` Alanlar), Tek `FlowRow` İçinde Yüzlerce Resim ve Bildirim IPC Yükü**
   - `AnimeDownload` veri sınıfındaki `var` alanlar nedeniyle Compose modeli kararsız (unstable) sayıyor ve listedeki değişmeyen satırları da her güncellemede yeniden çiziyordu.
   - Resimler sekmesinde bir grubun tüm resimleri tek bir `FlowRow` `LazyColumn` öğesinde tutulduğu için çok resimli gruplarda tüm kartlar tek seferde oluşturuluyordu.
   - `AnimeDownloadService.loadPosterBitmap` posteri tam çözünürlükte decode edip her saniye Binder IPC üzerinden `NotificationManager`'a gönderiyordu.

## 3. Yapılan Değişiklikler

- [x] **`app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt`**
  - Doğrudan ve HLS indirmelerde `64 KB` `BufferedOutputStream` + `64 KB` okuma tamponuna geçildi.
  - `onProgress` geri çağrısına `SystemClock.elapsedRealtime()` tabanlı **250 ms hız sınırlaması (throttling)** eklendi (yüzde değişimlerinde en az 100 ms + %100 tamamlanma bildirimi).
  - HLS segment döngüsünde her segmentte `localFile.length()` çağırmak yerine indirilen bayt bellekte takip edildi.
- [x] **`app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt`**
  - `updateProgress` içine `200 ms` hız sınırı ve değişmeyen değer kontrolü eklendi.
  - `saveAndEmit`, `deleteDownload` ve `clearAllDownloads` içindeki Gson serileştirme ve dosya silme işlemleri UI thread'den `ioScope` (`Dispatchers.IO`) üzerine taşındı.
- [x] **`app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`**
  - `loadAllDownloadedSubtitles` ve `loadAllDownloadedImages` tamamen `Dispatchers.IO` (`produceState`) üzerine alındı.
  - Altyazı taraması aktif indirme byte'larından koparıldı; yalnızca tamamlanan indirmelerin anahtarına (`completedDownloadsKey`) ve `refreshSubsTrigger`'a bağlandı.
  - `DownloadItemRow` içindeki altyazı/metadata okuma işlemi `DOWNLOADING` durumunda tamamen devre dışı bırakıldı; yalnızca `COMPLETED` (veya `localPath` mevcut) durumunda `Dispatchers.IO` üzerinde çalışacak şekilde izole edildi.
  - Tüm `Regex` nesneleri dosya düzeyinde tek seferlik önbelleğe alındı; $O(N^2)$ `results.none` kontrolleri $O(1)$ `HashSet` ile değiştirildi.
  - Resimler sekmesindeki gruplar `chunked(4)` ile satır bazlı tembel yüklemeye (lazy row) çevrildi.
- [x] **`app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt`**
  - Her video için tekrar tekrar `root.listFiles()` çağıran $O(N^2)$ dizin taraması tek geçişli (`primaryEntries`) yapıya indirgendi.
- [x] **`app/src/main/java/com/kitsugi/animelist/data/model/AnimeDownload.kt`**
  - `@Immutable` eklendi ve `var` alanlar `val` yapıldı.
- [x] **`app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloadService.kt`**
  - Bildirim posteri maksimum `384px` boyuta ölçeklenerek Binder IPC yükü düşürüldü.
- [x] **`app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt` & `EntryDetailTabContents.kt`**
  - Üst seviye gereksiz `downloads.collectAsState()` abonelikleri kaldırıldı.

## 4. Dokunulan Dosyalar
```
app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloadService.kt
app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt
app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt
app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt
app/src/main/java/com/kitsugi/animelist/data/model/AnimeDownload.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt
PLAN_TASK_INDIRMELER_SAYFASI_KASMA_FIX_2026-10-20.md
```
