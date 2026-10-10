# UYGULAMA RAPORU — Depolama Şişmesi & Zamanla Artan Kasma (v2.4.236)

**Tarih:** 2026-10-11
**Sürüm:** 2.4.235 → **2.4.236**
**Analiz raporu:** `KOK_NEDEN_RAPORU_4GB_SISME_VE_KASMA_2026-10-11.md`
**Toplam değişen dosya:** 16 (4 yeni, 12 düzenlenen)

---

## 1. Belirti ve kök neden (özet)

Kullanıcı: *"kasıyor/donuyordu, 4 GB'a yakın yer kaplıyordu, silip yeniden kurunca hepsi düzeldi."*

Kök neden üç katmanlıydı:

1. **Merkezî temizlik yokluğu** — 6 disk önbelleği + 4 Room tablosu birikiyor, hiçbiri toplu yönetilmiyordu.
2. **Çalışmayan/yetim budama kodları** — yazılmış ama ya yanlış koşula bağlı (Wi-Fi zorunlu) ya da hiç çağrılmıyor.
3. **Kırpma işinin kendisinin pahalı olması** — her yazımda tüm dizini tarayan mantık; biriken veriyle birlikte asıl kasma sebebi.

Yeniden kurulum üçünün de ürettiği birikimi aynı anda sıfırladığı için "sorun kendiliğinden çözüldü" gibi göründü.

---

## 2. Uygulanan düzeltmeler

### 2.1 Yeni: `core/storage/StorageJanitor.kt` (merkezî bekçi)

| Kural | Kalem | Aksiyon |
|---|---|---|
| 14 gün | `cache/anisub_subs` | Alt klasörleri sil |
| 30 gün + 150 MB | `files/previews` | Eskileri sil, bütçeye indir |
| 150 MB | `files/manga_cache` | MangaCache bütçe kırpması |
| 7 gün | `files/diagnostics` | Eski log/rapor dosyaları |
| 30 gün | `persistent_details_cache` | `deleteOlderThan` |
| 30 gün | `video_cache` | `clearExpiredVideos` |
| 5.000 satır | `translation_cache` | `pruneToLimit` |
| 500 satır | `search_history` | `pruneToLimit` |
| 7 gün (P2P kapalıysa) | `cache/p2p-engine/payload` | Yarım kalan segmentler |

- `scan(context)` → kalem kalem (bayt, dosya sayısı); UI bunu kullanır.
- `clean(context, bucket)` / `cleanAll(...)` → kullanıcı temizliği; kazanılan bayt + hata sayısı raporlanır.
- `runMaintenance(context, force)` → 6 saatlik throttle + `AtomicBoolean` ile eşzamanlılık koruması.
- Tüm işlemler `Dispatchers.IO`; WebView temizliği bilinçli olarak `Dispatchers.Main`.
- Kullanıcı verisi (mylist, hesap, izleme geçmişi, indirilen videolar) **kapsam dışı**.

### 2.2 Yeni: `core/storage/StorageMaintenanceWorker.kt`

`MappingSyncWorker`'ın **Wi-Fi + batarya** koşulu yüzünden mobil veride hiç çalışmayan budamayı çözer: ağ gerektirmeyen, `Constraints.NONE` ile planlanan günlük iş (`ExistingPeriodicWorkPolicy.UPDATE`).

### 2.3 Yeni: Ayarlar > Depolama ekranı

`ui/screens/settings/StorageSettingsContent.kt` + `SettingsRoute.Storage` + ana menü öğesi.
Kalem kalem kullanım, dosya sayısı, tek dokunuşla temizleme, "Güvenli önbellekleri temizle" (Web verisi hariç), Web verisi için ayrı onay diyaloğu. TV'de `tvClickable` ile odak desteği.

### 2.4 Performans düzeltmeleri

| Dosya | Önce | Sonra |
|---|---|---|
| `MangaCache.kt` | Her `putImageToCache` → `listFiles()` + tüm boyutları topla | Süreç genelinde paylaşılan **artımlı sayaç**; dizin yalnızca bütçe aşılınca taranır |
| `PreviewGenerator.kt` | Her örnek oluşturulduğunda tüm `previews` ağacı taraması | Tarama kaldırıldı; bütçe merkezî bekçide |
| `AnimeDownloadManager.kt` | `Application.onCreate`'te ana iş parçacığında Gson parse | IO'da okuma + okuma sürerken yazılanlarla güvenli birleştirme |
| `WatchHistoryManager.kt` | Aynı şekilde ana iş parçacığında parse | IO'da okuma + `merge()` ile veri kaybı önleme |
| `WatchHistoryStore.kt` | Her ilerleme tikinde tüm liste serialize + XML yazımı | 2,5 sn toplulaştırma + `flush()`; `onTrimMemory(UI_HIDDEN)` ile boşaltma |
| `KitsugiApplication.kt` | — | Açılış bakımı + günlük iş planlama + bekleyen yazmayı boşaltma |

### 2.5 Veritabanı

- `TranslationCacheDao.pruneToLimit(keep)` **yeni** — tabloda daha önce hiç silme yolu yoktu.
- `SearchHistoryDao.pruneToLimit(keep)` **yeni** — UI 20 gösterse de tablo sınırsızdı.
- **Room şeması DEĞİŞMEDİ** (sürüm 28'de kaldı): zaman damgası kolonu eklemek yerine `rowid`/`timestamp` sıralı budama sorguları kullanıldı. Böylece migration riski **sıfır**.

---

## 3. Değişen dosyalar

**Yeni (4)**
- `app/src/main/java/com/kitsugi/animelist/core/storage/StorageJanitor.kt`
- `app/src/main/java/com/kitsugi/animelist/core/storage/StorageMaintenanceWorker.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt`
- `release_notes_v2.4.236.md`

**Düzenlenen (13)**
- `KitsugiApplication.kt` — bakım planı, `onTrimMemory` flush
- `MangaCache.kt` — artımlı sayaç, `trimToBudget` genel API
- `PreviewGenerator.kt` — init taraması kaldırıldı
- `AnimeDownloadManager.kt` — async init + birleştirme
- `WatchHistoryManager.kt` — async init + debounce + flush
- `WatchHistoryStore.kt` — `saveHistoryDebounced` / `flush`
- `TranslationCacheDao.kt` — `pruneToLimit`, `count`, `clearAll`
- `SearchHistoryDao.kt` — `pruneToLimit`
- `MappingSyncWorker.kt` — budamanın artık garanti olmadığına dair uyarı
- `SettingsScreen.kt` — `Storage` rotası + menü öğesi
- `res/values/strings.xml`, `res/values-en/strings.xml` — 37 yeni dize (TR + EN)
- `app/build.gradle.kts` — sürüm 2.4.236
- `RELEASE_NOTES.md` — v2.4.236 bölümü

---

## 4. Doğrulama durumu (dürüst not)

- ✅ **Statik doğrulama yapıldı:** tüm Kotlin dosyalarında parantez/blok dengesi, TR/EN dize anahtarlarının birebir eşleşmesi (37/37), koddan kullanılan 1.710 dize anahtarının tamamının tanımlı olduğu, XML geçerliliği, DAO sorgularının sütun adlarının entity'lerle uyumu.
- ⚠️ **Derleme YAPILAMADI:** bu çalışma ortamında JDK, Android SDK ve Kotlin derleyicisi yok; Gradle/KSP çalıştırılamıyor. Değişiklikler **derleme testinden geçirilmedi**.
- ⚠️ **Room şema export'u:** şema sürümü değişmediği için `app/schemas/` altında yeni dosya gerekmez.
- 👉 **İlk iş:** `./gradlew assembleFossRelease` (veya Android Studio'da derleme) ile derleyip cihazda doğrulamak.

### Cihazda doğrulama

```bash
# Bakımın gerçekten çalıştığını gör
adb logcat -s StorageJanitor StorageMaintenance
# Kalem kalem boyut
adb shell run-as com.kitsugi.animelist du -h -d 3 /data/data/com.kitsugi.animelist/ | sort -h | tail -20
# Beklenen: birkaç saniye içinde "Bakım tamamlandı: X KB geri kazanıldı"
```

---

## 5. Bilinçli olarak YAPILMAYANLAR (gerekçeli)

| Konu | Neden yapılmadı |
|---|---|
| P2P varsayılan önbelleğini 2 GB'tan düşürmek | P2P akışının çalışma tamponu; motor kapasiteyi kendi yönetiyor. Tek küçük seçenek `NONE` (kalıcı önbellek yok) ve bu akışı bozabilir. Bunun yerine Ayarlar > Depolama'da görünür + temizlenebilir yapıldı. |
| Coil disk önbelleğini arkadan dosya silerek budamak | Coil kendi günlüğünü (journal) yönetiyor; arkasından dosya silmek tutarsızlık yaratabilir. 512 MB sınırı zaten kendi LRU'suyla uygulanıyor; kullanıcı temizliği resmî API (`diskCache.clear()`) ile yapılıyor. |
| Room `watch_history` tablosunu budamak | Bu **kullanıcı verisi** (bölüm ilerlemesi), önbellek değil. Kapsam dışı bırakıldı. |
| WebView verisini otomatik silmek | Eklenti oturumlarını (Cloudflare doğrulaması) bozar. Yalnızca kullanıcı onayıyla temizlenir. |
