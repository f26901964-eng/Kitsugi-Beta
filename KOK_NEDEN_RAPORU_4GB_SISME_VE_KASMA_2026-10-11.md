# KÖK NEDEN RAPORU — ~4 GB Şişme ve Zamanla Artan Kasma / Donma

> ✅ **DÜZELTİLDİ (v2.4.236).** Bu raporda tespit edilen maddelerin tamamı koda uygulandı.
> Uygulama detayları, doğrulama durumu ve bilinçli olarak yapılmayanlar için bkz.
> **`DEPOLAMA_SISME_FIX_RAPORU_2026-10-11.md`**. Aşağısı değiştirilmemiş orijinal analizdir.

**Tarih:** 2026-10-11
**Sürüm:** `2.4.235-beta` (dal: `arena/b067e73b-kitsugi-beta`, taban: `d59ed8e`)
**Paket:** `com.kitsugi.animelist`
**Yöntem:** Statik kod analizi (1.008 Kotlin dosyası tarandı). Cihazda ölçüm adımları §6'da.

---

## 0. Kısa cevap (TL;DR)

**1) Uygulama neden 4 GB'a çıktı?**
Çünkü uygulamada **otomatik/global bir önbellek temizliği yok**. Her önbellek kendi başına "kendi içinde sınırlı" görünüyor ama birleşince ve bazıları hiç kırpılmadığı için toplam veri **sadece büyüyor, hiç küçülmüyor**. En büyük kalemler:

| Kalem | Üst sınır | Durum |
|---|---|---|
| P2P disk önbelleği (`cacheDir/p2p-engine/payload`) | **2 GB (varsayılan!)** — 5/10 GB seçenekleri var | Türü gereği dolar, elle temizlenmezse durur |
| Coil görsel disk önbelleği (`cacheDir/nuvio_images`) | **512 MB** | Sabit, otomatik |
| Manga sayfa önbelleği (`filesDir/manga_cache`) | 150 MB | Kırpma var ama **UI'da temizleme yok** |
| Video önizleme kareleri (`filesDir/previews`) | 150 MB | Kırpma var, tetiklenmesi pahalı (§4) |
| Altyazı zip'leri (`cacheDir/anisub_subs/*`) | **SINIRSIZ** | Hiç temizlenmiyor |
| `translation_cache` (Room) | **SINIRSIZ** | DAO'da DELETE sorgusu **yok** |
| `video_cache` (Room) | **SINIRSIZ** | `clearExpiredVideos` yazılmış, **hiçbir yerden çağrılmıyor** |
| `persistent_details_cache` (Room, JSON blob) | **SINIRSIZ*** | *Yalnızca "14 günde bir + sadece Wi-Fi + batarya iyi" koşullu işte kırpılıyor → **mobil veri kullanan cihazda asla çalışmaz** |
| CS eklentileri (`filesDir/cs_extensions/*.cs3`) | Kaynak/tema sayısına bağlı | Birikimli |
| WebView verisi (`app_webview/`) | Sistem yönetir | Eklenti WebView'ları `domStorage` açık, birikimli |

Bu 4 kalem (2 GB + 512 MB + 150 MB + 150 MB) tek başına **~2.8 GB**; üstüne WebView, eklenti dosyaları, Room DB (+ WAL) ve SharedPreferences yığınları eklenince **4 GB** rakamı tamamen açıklanıyor.

**2) Neden kasıyordu?** "4 GB dosya var" tek başına Compose'u yavaşlatmaz. Yavaşlatan üç zincir:

- **(a) I/O yarışı ve depolama baskısı:** aynı diske aynı anda Coil görsel okuyor, manga sayfası yazıyor, Room WAL checkpoint yapıyor, altyazı zip'i açılıyor. Dizin doldukça flash gecikmesi artar, kareler düşer.
- **(b) Temizlik işleri ya hiç çağrılmıyor ya da yanlış koşula bağlı** (§3) → veri birikir → er ya da geç her I/O işi ağırlaşır.
- **(c) Kırpma işinin kendisi pahalı:** `MangaCache.trimIfNeeded()` **her görsel yazımında** dizinin tamamını `listFiles()` + sıralar; `PreviewGenerator.runLruCleanup()` **her önizleme başlatmada** tüm klasörleri gezip boyut toplar. On binlerce küçük dosya birikince kırpma işi kırpılacak işten pahalı hâle gelir (§4).

**3) Silip yeniden kurunca neden düzeldi?**
Yeniden kurulum tek seferde şunların **hepsini** sıfırlıyor: 6 disk önbelleği + Room DB (blob tabloları dâhil) + `cs_extensions` içerikleri + `cloudstream_datastore*` / `rebuild_preference` SharedPreferences yığınları + WebView verisi. Yani sorun "kendiliğinden çözülmedi", **sayaçlar sıfırlandı.** Kalıcı çözüm uygulanmazsa aynı noktaya geri döner — bu sefer "silmek" de işe yaramayacak çünkü veri hacmi artışı koddan geliyor.

---

## 1. Silinen veri = düzelen şey

| # | Ne silindi | Konum | Kanıt |
|---|---|---|---|
| 1 | P2P torrent/segment önbelleği | `cacheDir/p2p-engine/payload` | `P2pStreamingEngine.kt:357` — `diskCacheCapacityBytes = settings.cacheSize.bytes` (`:347`), varsayılan `P2pCacheSize.GB_2` (`P2pModels.kt:22`) |
| 2 | Görsel önbelleği | `cacheDir/nuvio_images` | `KitsugiApplication.kt:529-533` → `maxSizeBytes(512L * 1024 * 1024)` |
| 3 | Manga sayfa önbelleği | `filesDir/manga_cache` | `MangaCache.kt:16-25` → `MAX_SIZE_MB = 150` |
| 4 | Video önizleme kareleri | `filesDir/previews/<md5>` | `PreviewGenerator.kt:32, 25` → `MAX_CACHE_SIZE_BYTES = 150 MB` |
| 5 | Altyazı zip açılımları | `cacheDir/anisub_subs/<subId>` | `AddonStreamClient.kt:537-541` — **hiçbir temizlik yok** |
| 6 | Room veritabanı + `-wal` | `databases/Kitsugi_database` | `KitsugiDatabase.kt:401-434` (28 migration) |
| 7 | Çeviri / video / detay JSON blob'ları | `translation_cache`, `video_cache`, `persistent_details_cache`, `explore_cache` | §3 |
| 8 | CS eklenti dosyaları | `filesDir/cs_extensions/<id>.cs3` | `CsPluginLoader.kt:61, 74, 158, 287` |
| 9 | Eklenti anahtar-değer depoları | `cloudstream_datastore*`, `rebuild_preference` | `CloudStreamApp.kt:41-64`, `DataStore.kt:10` |
| 10 | WebView/domStorage verisi | `app_webview/` | `CloudflareInterceptor.kt:116`, `WebViewResolver.kt:107`, `KitsugiWebViewDialog.kt:168` |

---

## 2. "4 GB" bütçesi — kalem kalem

```
P2P payload            ≤ 2.00 GB   (varsayılan 2 GB; kullanıcı 5/10 GB seçebilir)
Coil nuvio_images      ≤ 0.50 GB
manga_cache            ≤ 0.15 GB
previews               ≤ 0.15 GB
─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
alt toplam             ≈ 2.80 GB
+ anisub_subs          sınırsız (her altyazı zip'i kalıcı)
+ Room DB + WAL        blob tabloları; prune koşullu (§3)
+ app_webview          yüzlerce MB olabilir (eklenti WebView'ları)
+ cs_extensions        eklenti sayısı × ~50-500 KB
+ SharedPreferences    watch_history, active_downloads, cloudstream_datastore*
+ teşhis dosyaları     5 MB log + crash history + CS trace raporları
─────────────────────────────────────────
toplam                 ≈ 3-4 GB  ✅ gözlemlenen rakamla uyumlu
```

---

## 3. Asıl hata: yazılmış ama **çalışmayan** temizlikler

Bu bölüm "neden zamanla kötüleşti" sorusunun teknik cevabı.

### 3.1 `persistent_details_cache` — prune var ama asla çalışmıyor
Tek temizlik çağrısı `MappingSyncWorker` içinde:

```kotlin
// MappingSyncWorker.kt:25-29
val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)
db.persistentDetailCacheDao().deleteOlderThan(thirtyDaysAgo)
```

Ama bu worker şu koşullarla planlanıyor:

```kotlin
// KitsugiApplication.kt:223-236
PeriodicWorkRequestBuilder<MappingSyncWorker>(14, TimeUnit.DAYS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)   // ⚠️ SADECE Wi-Fi
            .setRequiresBatteryNotLow(true)                   // ⚠️ batarya iyi olmalı
            .build())
```

**Sonuç:** cihaz sürekli mobil veride ya da şarjı %15 altındaysa bu iş **hiç koşmaz** → detay JSON blob'ları (`detailJson`, bölüm/galeri verisiyle yüzlerce KB olabilir) **süresiz** birikir. Her detay sayfası açılışında bu tablo okunur; DB büyüdükçe açılış ve sorgu süreleri artar.

### 3.2 `video_cache` — prune yazılmış, çağıran yok

```kotlin
// VideoDao.kt:20-21
@Query("DELETE FROM video_cache WHERE resolvedAt < :expireTime")
suspend fun clearExpiredVideos(expireTime: Long)
```
`grep -rn "clearExpiredVideos"` → yalnızca DAO + arayüz + impl tanımı. **Tek bir çağrı yeri yok.**

### 3.3 `translation_cache` — DAO'da DELETE sorgusu **yok**
`TranslationCacheDao.kt` yalnızca `getTranslation` + `insertTranslation` içeriyor. Çevrilen her başlık/özet kalıcı olarak birikiyor.

### 3.4 `anisub_subs` — hiç temizlik yok
`AddonStreamClient.kt:537` `cacheDir/anisub_subs/<subId>/` altına zip açıyor, sonraki ziyaretlerde oradan okuyor. Silme yolu yok (ne LRU ne de androidx cache).

### 3.5 `manga_cache` / `previews` — temizleme UI'da erişilemiyor
- `MangaCache.clearAll()` var; `MangaSourceRepository.clearCache()` var → **hiçbir ekrandan çağrılmıyor** (`grep` sonucu: sadece tanım).
- Ayarlarda yalnızca **P2P önbelleği** için "Önbelleği Temizle" var (`P2pSettingsSection.kt:346-375`).
- **Global "Depolama / Önbellek" ekranı yok.** Kullanıcı olarak uygulama verisini temizlemenin tek yolu Android ayarlarından "Verileri temizle" → o da hesapları/mylist'i uçurur.

---

## 4. Kasma mekanizması (kırpma işleri kırpılandan pahalı hâle geliyor)

### 4.1 Manga okuyucu — her sayfada tam dizin taraması
```kotlin
// MangaCache.kt:64-92
@Synchronized
fun putImageToCache(imageUrl: String, stream: InputStream) {
    ...
    trimIfNeeded()   // HER görsel yazımında çalışır
}

private fun trimIfNeeded() {
    val files = cacheDir.listFiles()?.filterNot { ... }?.toMutableList() ?: return
    var totalSize = files.sumOf { it.length() }          // TÜM dosyaların boyutu
    if (totalSize <= MAX_BYTES) return
    files.sortBy { it.lastModified() }                    // TAM sıralama
    ...
}
```
`if (totalSize <= MAX_BYTES) return` erken çıkışı **boyutu hesaplamadan önce olamıyor**. Yani önbellek 150 MB'ın altında olsa bile **her manga sayfası yazımında** on binlerce dosya listeleniyor + boyutları toplanıyor. `@Synchronized` olduğu için de eşzamanlı sayfa indirmeleri birbirini bekliyor. Önbellek büyüdükçe okuyucu "kasmaya" başlıyor — ve reinstall tam da bunu sıfırlıyor.

### 4.2 Önizleme üretici — her videoda tüm önizleme ağacı geziliyor
```kotlin
// PreviewGenerator.kt:41-46
init {
    if (!cacheDir.exists()) cacheDir.mkdirs()
    runLruCleanup()          // her enstantane üretici oluşturulduğunda
}
// PreviewGenerator.kt:217-243 → tüm klasörleri listele + her dosyanın length()'ini topla
```
`Dispatchers.IO`'da olması main thread'i kilitlemez ama **diski** kilitler: aynı anda Coil decode + Room yazımı ile yarışır → kaydırma sırasında kare düşmesi.

### 4.3 Açılışta main thread'de büyüyen veri okuma
```kotlin
// KitsugiApplication.kt:121,124  → Application.onCreate (MAIN THREAD)
AnimeDownloadManager.init(this)   // AnimeDownloadStore.kt:11-19 → getSharedPreferences + Gson.fromJson(downloads_list)
WatchHistoryManager.init(this)    // WatchHistoryStore.kt:11-21 → getSharedPreferences + Gson.fromJson(history_list)
```
Bunlar `apply()` ile yazılan XML'leri **ilk erişimde senkron** okur ve tüm JSON'u parse eder. Birikimli indirme listesi/geçmiş ile soğuk açılış yavaşlar.

### 4.4 Oynatma sırasında her ilerleme tikinde tam yeniden yazım
```kotlin
// WatchHistoryManager.kt:updateProgress → store.saveHistory(current)
// WatchHistoryStore.kt:23 → prefs.edit().putString("history_list", gson.toJson(entries)).apply()
```
Oynatıcı ilerleme kaydettikçe 200 kayıtlık listenin tamamı serialize edilip tüm XML yeniden yazılıyor. Zayıf cihazda oynatma sırasında mikro takılmalar.

> **Not (dürüst belirsizlik):** Repoda daha önce "debug APK'da sürekli diske log yazımı görsel decode ve Room trafiğiyle I/O yarışına girip kaydırma karelerini düşürüyordu" tespiti zaten yapılmış ve `logcat -f` süreci kaldırılmış (`KitsugiApplication.kt:151-155`). Yani bu sınıf bir sorun bu projede **belgelenmiş** bir jank kaynağı — §4.1/§4.2 aynı sınıfın hâlâ açıkta kalan örnekleri.

---

## 5. Kalıcı çözüm — öncelik sırası

| P | İş | Dosya | Etki |
|---|---|---|---|
| **P0** | Tek bir `KitsugiStorageJanitor` yaz: açılışta IO dispatcher'da çalışır; `cacheDir` + belirli `filesDir` alt dizinlerini gezer, **yaş + toplam boyut** kuralına göre budar; sonucu loglar | yeni dosya + `KitsugiApplication.onCreate` | 4 GB'ın ana kaynağı |
| **P0** | `trimIfNeeded()`'i **artımlı sayaçla** çalıştır: toplam boyutu bellekte tut (ilk açılışta bir kez hesapla), her yazımda `+bytes`; eşik aşılırsa kırp. `listFiles()` her sayfada çağrılmasın | `MangaCache.kt` | Manga okuyucu kasması |
| **P0** | `MappingSyncWorker` bağımlılığını gevşet: ay-önbellek budaması **ayrı ve koşulsuz** bir iş olsun (mobil veri şartı olmasın, hiç ağ gerektirmiyor) | `KitsugiApplication.kt:223-236`, `MappingSyncWorker.kt:25-29` | DB şişmesi |
| **P1** | `video_cache` için `clearExpiredVideos` çağrısını bağla; `translation_cache`'e `deleteOlderThan` ekle ve budamaya bağla; `anisub_subs`'a klasör-başına yaş sınırı koy | `VideoRepositoryImpl`, `TranslationCacheDao`, `AddonStreamClient` | Sınırsız 3 tablo/dizin |
| **P1** | Ayarlara **"Depolama"** ekranı: kalem kalem boyut + "Temizle" (Coil / manga / önizleme / altyazı / P2P / loglar / DB budama). `MangaCache.clearAll`, `MangaSourceRepository.clearCache`, `DetailCache.clear*`, `FileLoggingTree.clearLogs` zaten hazır — sadece bağla | yeni Settings ekranı | Kullanıcı kontrolü |
| **P2** | P2P varsayılanını `GB_2` yerine cihaz boş alanına göre ölçekle (ör. boş alanın %10'u, üst sınır 2 GB) ve ilk kurulumda bilgilendir | `P2pModels.kt:22`, `P2pSettingsRepository` | 2 GB'lık tek kalem |
| **P2** | Açılış okumalarını IO'ya taşı: `AnimeDownloadManager.init` / `WatchHistoryManager.init` `applicationScope.launch(Dispatchers.IO)` içine; ilerleme yazımını debounce et (3-5 sn) | `KitsugiApplication.kt`, `WatchHistoryStore.kt` | Soğuk açılış + oynatma takılması |
| **P3** | `PreviewGenerator.runLruCleanup()`'ı `init`'ten çıkar; günde bir / oturumda bir kez çalışan ortak temizleyiciye taşı | `PreviewGenerator.kt:41-46` | I/O yarışı |

---

## 6. Cihazda doğrulama (kullanıcı tarafı)

Kurulum sonrası ilerlemeyi ölçmek için:

```bash
# 1) Kalem kalem boyut (debuggable build gerekir)
adb shell run-as com.kitsugi.animelist du -h -d 3 /data/data/com.kitsugi.animelist/ | sort -h | tail -25

# 2) Sadece önbellekler
adb shell run-as com.kitsugi.animelist du -sh \
  /data/data/com.kitsugi.animelist/cache/nuvio_images \
  /data/data/com.kitsugi.animelist/cache/p2p-engine \
  /data/data/com.kitsugi.animelist/cache/anisub_subs \
  /data/data/com.kitsugi.animelist/files/manga_cache \
  /data/data/com.kitsugi.animelist/files/previews \
  /data/data/com.kitsugi.animelist/files/cs_extensions

# 3) Veritabanı ve tablo büyüklükleri
adb shell run-as com.kitsugi.animelist ls -lh /data/data/com.kitsugi.animelist/databases/
adb shell run-as com.kitsugi.animelist sqlite3 /data/data/com.kitsugi.animelist/databases/Kitsugi_database \
  "SELECT name, SUM(pgsize)/1048576.0 AS mb FROM dbstat GROUP BY name ORDER BY mb DESC LIMIT 10;"

# 4) Dosya sayısı (kasma mekanizmasının ölçüsü)
adb shell run-as com.kitsugi.animelist ls -1 /data/data/com.kitsugi.animelist/files/manga_cache | wc -l
```

**Beklenti:** yeni kurulumda tüm değerler küçük; tipik kullanımdan 2-3 ay sonra `p2p-engine` ve `nuvio_images` sınıra dayanmış, `manga_cache` binlerce dosya, DB `persistent_details_cache` yüzünden yüzlerce MB olacak. Bu ölçüm P0/P1 fix'lerinin etkisini sayısal olarak kanıtlar.

---

## 7. Tek cümlelik özet

> Sorun "uygulama bozuldu" değildi; **onlarca önbelleğin hiç temizlenmemesi + temizlik işlerinin yanlış koşullara bağlı olması + kırpma işlerinin her yazımda tüm dizini taraması** üçlüsüydü. Silip kurmak üçünün de ürettiği birikimi sıfırladı; kalıcı fix uygulanmazsa birikim aynı hızla geri gelir.
