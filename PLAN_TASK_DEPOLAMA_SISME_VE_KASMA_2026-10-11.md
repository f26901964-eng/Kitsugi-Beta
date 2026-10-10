# PLAN/TASK — Depolama şişmesi (~4 GB) ve zamanla artan kasma/donma

Tarih: 2026-10-11
Durum: Kod düzeltmeleri tamamlandı; **Gradle derleme + cihaz doğrulaması bekliyor.**
Sürüm: 2.4.235 → **2.4.236**
Analiz: `KOK_NEDEN_RAPORU_4GB_SISME_VE_KASMA_2026-10-11.md`
Uygulama raporu: `DEPOLAMA_SISME_FIX_RAPORU_2026-10-11.md`

## Belirti
- Uygulama zamanla ağırlaşıyor, kaydırma/gezinme kasıyor, tam ekran oynatma takılıyor.
- Uygulama verisi ~4 GB'a çıkıyor.
- **Silip yeniden kurunca bütün kasma/donmalar düzeliyor.** (Bu, kök nedenin "kod" değil
  "birikmiş çalışma zamanı durumu" olduğunun en güçlü kanıtıydı.)

## Kök nedenler (üç katman)

### 1. Merkezî temizlik yokluğu
Uygulamada **hiçbir ortak önbellek temizleyicisi yoktu.** Ölçülen kalemler:

| Kalem | Üst sınır | Durum |
|---|---|---|
| P2P torrent önbelleği (`cache/p2p-engine/payload`) | **2 GB (varsayılan)** | Ayarlardan 5/10 GB seçilebiliyor |
| Coil görsel önbelleği (`cache/nuvio_images`) | 512 MB | Kendi LRU'su var |
| Manga sayfa önbelleği (`files/manga_cache`) | 150 MB | Kırpma var, temizleme UI'ı yok |
| Video önizlemeleri (`files/previews`) | 150 MB | Kırpma var, tetiklemesi pahalı |
| Altyazı arşivleri (`cache/anisub_subs`) | **SINIRSIZ** | Hiç temizlenmiyor |
| `persistent_details_cache` (Room, JSON blob) | **SINIRSIZ*** | *Koşullu işe bağlı — bkz. 2.1 |
| `video_cache` | **SINIRSIZ** | `clearExpiredVideos` hiç çağrılmıyor |
| `translation_cache` | **SINIRSIZ** | DAO'da DELETE yok |
| `search_history` | Sınırsız büyüme | UI sadece 20 gösteriyor |
| CS eklentileri, WebView verisi, teşhis dosyaları | — | Birikimli |

→ 2 GB + 512 MB + 150 MB + 150 MB = **~2,8 GB taban**; üstüne kalanlar eklenince **~4 GB**.

### 2. Yazılmış ama hiç çalışmayan temizlik kodları
1. **`persistent_details_cache` budaması** yalnızca `MappingSyncWorker` içindeydi ve o iş
   `NetworkType.UNMETERED` (yalnızca Wi-Fi) + `requiresBatteryNotLow` ile planlanıyordu.
   → Mobil veri kullanan cihazda **hiç çalışmadı**; detay JSON'ları süresiz birikti.
2. **`video_cache`:** `VideoDao.clearExpiredVideos()` repoda **hiçbir yerden çağrılmıyordu**
   (`grep` ile doğrulandı: yalnızca tanım).
3. **`translation_cache`:** DAO'da tek bir DELETE sorgusu yoktu.
4. **Arama geçmişi:** sınırsız satır; bulut senkronizasyonuyla daha da şişiyor.
5. **Altyazı zip açılımları:** hiçbir temizlik yolu yok.

### 3. "Kırpma işi, kırpılandan pahalı" kasma zinciri
- **`MangaCache.trimIfNeeded()`** her `putImageToCache()` çağrısında tüm dizini `listFiles()` ile
  listeliyor + `sumOf { it.length() }` ile boyutları topluyordu. Erken çıkış
  (`if (totalSize <= MAX_BYTES) return`) **ancak bu pahalı ölçümden sonra** yapılabildiği için,
  önbellek 150 MB'ın altındayken bile her manga sayfasında on binlerce dosya taranıyordu.
  Metod `@Synchronized` olduğundan eşzamanlı sayfa indirmeleri de birbirini bekliyordu.
- **`PreviewGenerator.runLruCleanup()`** HER örnek oluşturulduğunda tüm `files/previews` ağacını
  gezip boyut topluyordu → Coil decode + Room yazımıyla disk yarışı → kaydırma kareleri düşüyor.
- **Açılış:** `AnimeDownloadManager.init` ve `WatchHistoryManager.init`, `Application.onCreate`
  içinde **ana iş parçacığında** Gson ile tüm JSON'u ayrıştırıyordu.
- **Oynatma:** Her ilerleme tikinde 200 kayıtlık listenin tamamı serialize edilip
  SharedPreferences XML'i baştan yazılıyordu.

## Yapılan işler (özet)

**Yeni bileşenler**
- `core/storage/StorageJanitor.kt` — kalem kalem ölçüm + yaş/bütçe budama + kullanıcı temizliği
  (9 kalem; tümü IO dispatcher'ında; `AtomicBoolean` ile eşzamanlılık koruması; 6 saat throttle).
- `core/storage/StorageMaintenanceWorker.kt` — `Constraints.NONE` ile günlük bakım (ağ şartı yok).
- `ui/screens/settings/StorageSettingsContent.kt` + `SettingsRoute.Storage` + menü öğesi —
  Ayarlar > Depolama ekranı.

**Performans**
- `MangaCache`: süreç genelinde paylaşılan **artımlı boyut sayacı**; dizin yalnızca bütçe
  gerçekten aşıldığında taranıyor. `trimToBudget()` dışarıya açıldı.
- `PreviewGenerator`: init taraması kaldırıldı (merkezî bekçiye devredildi).
- `AnimeDownloadManager` / `WatchHistoryManager`: açılışta IO'da okuma + okuma sürerken yazılanla
  güvenli **birleştirme** (veri kaybı önleme).
- `WatchHistoryStore`: `saveHistoryDebounced` (2,5 sn) + `flush()`; uygulama arka plana geçince
  `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` ile anında diske yazma.
- `KitsugiApplication`: açılış bakımı + günlük iş planlama + flush kancası.

**Veritabanı budamaları (şema DEĞİŞMEDİ — sürüm 28)**
- `TranslationCacheDao.pruneToLimit(5000)` **yeni**
- `SearchHistoryDao.pruneToLimit(500)` **yeni**
- `PersistentDetailCacheDao.deleteOlderThan` artık koşulsuz çağrılıyor
- `VideoDao.clearExpiredVideos` artık gerçekten çağrılıyor

## Kabul kriterleri
- [ ] `./gradlew assembleFossRelease` hatasız derleniyor.
- [ ] Ayarlar > Depolama açılıyor; kalem kalem boyut ve dosya sayısı görünüyor.
- [ ] "Güvenli önbellekleri temizle" sonrası hesap/mylist/geçmiş/indirmeler yerinde.
- [ ] logcat'te `StorageJanitor` "Bakım tamamlandı: X KB geri kazanıldı" satırı görünüyor.
- [ ] 2-3 günlük normal kullanımdan sonra uygulama verisi boyutu artmıyor (kalıcı < ~600 MB
      önbellek + kullanıcı verisi).
- [ ] Manga okuyucuda sayfa geçişlerinde gözle görülür takılma yok.
- [ ] Tam ekran oynatıcıda ilerleme kaydı çalışıyor; uygulamayı kapatıp açınca konum korunuyor.

## Bilinçli olarak yapılmayanlar
- **P2P varsayılan önbelleğini düşürmek:** P2P akışının çalışma tamponu; motor kapasiteyi kendi
  yönetiyor. Tek küçük seçenek `NONE` (kalıcı önbellek yok) ve akışı bozabilir. Bunun yerine
  Depolama ekranında görünür + temizlenebilir yapıldı.
- **Coil dizinini arkadan dosya silerek budamak:** Coil kendi günlüğünü yönetir; resmî API
  (`diskCache.clear()`) kullanıldı.
- **Room `watch_history` tablosu:** kullanıcı verisi (bölüm ilerlemesi), önbellek değil.
- **WebView verisini otomatik silmek:** eklenti oturumlarını (Cloudflare) bozar; yalnızca onayla.
