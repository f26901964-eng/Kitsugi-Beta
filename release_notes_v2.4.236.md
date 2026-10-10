# Kitsugi Beta v2.4.236 — Depolama Şişmesi ve Zamanla Artan Kasma Düzeltmesi

> Bu sürüm, "uygulama zamanla ağırlaşıyor / kasıyor, silip yeniden kurunca düzeliyor"
> şikâyetinin kök nedenini ortadan kaldırır.

---

## 🇹🇷 Türkçe

### 🗄️ 1. Merkezî Depolama Bekçisi (`StorageJanitor`) — ~4 GB'lık şişmenin asıl kaynağı
- **Tespit:** Uygulamada **hiçbir merkezî önbellek temizliği yoktu.** P2P torrent önbelleği (varsayılan **2 GB**), Coil görsel önbelleği (**512 MB**), manga sayfa önbelleği (~150 MB), video önizlemeleri (~150 MB) ve bunlara ek olarak altyazı arşivleri, Room'daki detay/video/çeviri blob tabloları, WebView verisi ve teşhis dosyaları birikiyordu. Toplam kurulum boyutu **~3-4 GB**'a ulaşıyordu.
- **Çözüm:** Tek noktadan ölçen, budayan ve raporlayan `StorageJanitor` eklendi. Açılışta (6 saatte bir) ve **ağ koşulu olmayan** günlük arka plan işinde (`StorageMaintenanceWorker`) yaş + bütçe kurallarına göre sessizce temizlik yapar. Tüm işlemler IO dispatcher'ında; ana iş parçacığı hiç meşgul edilmez.

### 🧹 2. Yazılmış ama Hiç Çalışmayan Temizlik Kodları Onarıldı
- **`persistent_details_cache` (detay önbelleği):** Budama kodu yalnızca `MappingSyncWorker` içindeydi ve o iş **yalnızca Wi-Fi + batarya iyi** koşuluyla planlandığı için mobil veri kullanan cihazlarda **hiç çalışmıyordu**; detay JSON kayıtları süresiz birikiyordu. Budama artık koşulsuz yapılıyor.
- **`video_cache`:** DAO'daki `clearExpiredVideos()` sorgusu **repoda hiçbir yerden çağrılmıyordu.** Artık 30 günden eski çözümleme kayıtları otomatik atılıyor.
- **`translation_cache`:** DAO'da **hiç DELETE sorgusu yoktu**; çevrilen her başlık/özet kalıcı olarak birikiyordu. Satır sınırı (en yeni 5.000) eklendi.
- **Arama geçmişi:** UI yalnızca son 20 kaydı gösterse de tablo sınırsız büyüyordu; artık en yeni 500 kayıt tutuluyor.
- **Altyazı arşivleri** (`cache/anisub_subs`): Hiç temizlenmiyordu; 14 günden eski klasörler atılıyor.

### ⚡ 3. "Kırpma İşi Kırpılandan Pahalı" Kasma Zinciri Kırıldı
- **Manga okuyucu (`MangaCache`):** Her görsel yazımında **tüm önbellek dizini** listeleniyor ve tüm dosyaların boyutu toplanıyordu (erken çıkış ancak bu pahalı ölçümden sonra yapılabiliyordu). Üstelik `@Synchronized` olduğu için eşzamanlı sayfa indirmeleri birbirini bekliyordu. Artık toplam boyut **süreç genelinde paylaşılan artımlı sayaçla** bellekte tutuluyor; dizin yalnızca bütçe gerçekten aşıldığında taranıyor.
- **Video önizlemeleri (`PreviewGenerator`):** HER önizleme üretici oluşturulduğunda tüm `files/previews` ağacı gezilip boyut toplanıyordu; bu tarama Coil görsel çözme ve Room yazımıyla disk yarışına girip kaydırma karelerini düşürüyordu. Bu iş merkezî bekçiye taşındı (günde bir, arka planda).
- **Açılış (soğuk başlangıç):** İndirme listesi ve izleme geçmişi `Application.onCreate` içinde **ana iş parçacığında** Gson ile ayrıştırılıyordu; liste büyüdükçe açılış uzuyordu. Artık okuma IO'da yapılır, UI akış dolunca güncellenir (okuma sürerken yazılan kayıtlar kaybolmaz, birleştirilir).
- **Oynatma sırasında yazma:** İzleme ilerlemesi her bildirimde tüm geçmiş listesi yeniden serialize edilip XML baştan yazılıyordu. Artık 2,5 saniyelik pencerede toplulaştırılır; ekran arka plana geçtiğinde bekleyen yazma hemen diske indirilir.

### 🆕 4. Ayarlar > Depolama Ekranı
- Kalem kalem gerçek disk kullanımı (görseller, P2P, manga, önizlemeler, altyazılar, veritabanı önbelleği, teşhis, Web verisi, eklentiler), dosya sayısı ve **tek dokunuşla temizleme**.
- "Güvenli önbellekleri temizle" seçeneği Web verisi dışındaki her şeyi boşaltır; **hesap, mylist, izleme geçmişi ve indirilen videolar asla silinmez.**
- Önceden yer açmak için tek yol uygulama verilerini tümden silmekti (hesap ve liste kaybı).

---

## 🇬🇧 English

### 🗄️ 1. Central Storage Janitor (`StorageJanitor`) — the real cause of ~4 GB bloat
- **Finding:** the app had **no central cache cleanup at all.** The P2P torrent cache (default **2 GB**), Coil image cache (**512 MB**), manga page cache (~150 MB), video thumbnails (~150 MB) plus subtitle archives, Room detail/video/translation blob tables, WebView data and diagnostics all accumulated — installations reached **~3–4 GB**.
- **Fix:** a single `StorageJanitor` that measures, prunes and reports. It runs at launch (at most every 6 h) and in a daily background job (`StorageMaintenanceWorker`) with **no network constraint**, pruning by age + budget. Everything happens on the IO dispatcher; the main thread is never touched.

### 🧹 2. Cleanup Code That Never Actually Ran
- **`persistent_details_cache`:** pruning lived only inside `MappingSyncWorker`, which was scheduled with **Wi-Fi-only + battery-not-low** constraints — so on mobile data it **never ran** and detail JSON grew forever. Now pruned unconditionally.
- **`video_cache`:** `clearExpiredVideos()` was **never called from anywhere**. Resolution records older than 30 days are now dropped.
- **`translation_cache`:** the DAO had **no DELETE query at all**. A row cap (newest 5,000) was added.
- **Search history:** the UI only showed 20 entries but the table grew forever; now capped at 500.
- **Subtitle archives** (`cache/anisub_subs`): never cleaned; folders older than 14 days are removed.

### ⚡ 3. The "Trimming Costs More Than What It Trims" Jank Chain, Broken
- **Manga reader (`MangaCache`):** every image write listed the **entire** cache directory and summed every file size (the early-exit could only happen *after* that expensive scan), and `@Synchronized` made concurrent page downloads queue up. Totals are now kept in a process-wide incremental counter; the directory is scanned only when the budget is actually exceeded.
- **Video previews (`PreviewGenerator`):** every generator instance walked the whole `files/previews` tree; that scan raced Coil decoding and Room writes on disk and dropped scroll frames. Moved to the central janitor (once a day, background).
- **Cold start:** the download list and watch history were parsed with Gson **on the main thread** in `Application.onCreate`. Reads now happen on IO and merge safely with anything written meanwhile.
- **During playback:** watch progress re-serialised the whole history and rewrote the XML on every tick; now debounced to a 2.5 s window, flushed when the app goes to the background.

### 🆕 4. Settings > Storage Screen
- Per-bucket real disk usage (images, P2P, manga, previews, subtitles, database cache, diagnostics, web data, plugins), file counts and **one-tap clearing**.
- "Clear safe caches" wipes everything except web data; **accounts, my list, watch history and downloaded videos are never deleted.**
- Previously the only way to reclaim space was wiping all app data — losing your account and list.
