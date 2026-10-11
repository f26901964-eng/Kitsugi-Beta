# PLAN_TASK — Depolama Ekranı: İndirilenler Bölümü ve Toplu Silme (v2.4.247, 2026-10-11)

## Amaç
Ayarlar > Depolama ekranı yalnızca önbellekleri gösteriyordu. Kullanıcının **kendi indirdiği**
video, görsel ve altyazılar hiçbir yerde ölçülmüyor, görünmüyor ve toplu silinemiyordu.
Bu görevde indirilen dosyalar da ölçülür + listelenir + açık onayla topluca silinebilir hâle getirildi.

## Kapsam kararları (kullanıcı onaylı)
- [x] Bölüm biçimi: **özet** (tür başına toplam boyut + dosya sayısı), tek tek liste yok
- [x] "Resim" → indirilen görseller **ve** mevcut görsel önbelleği ayrı satırlar
- [x] "Altyazı" → indirilen altyazılar **ve** mevcut altyazı önbelleği ayrı satırlar
- [x] Üstteki "Toplam kullanım" **yalnızca önbellekleri** kapsamaya devam eder
- [x] Her silme için **onay penceresi** (yıkıcı diyalog)

## Görevler
- [x] `core/storage/DownloadStorage.kt`: VIDEOS / IMAGES / SUBTITLES ölçüm + temizleme + tekrarsız toplam
- [x] `KitsugiImageDownloadHelper`: `imageDirectories`, `downloadedImageFiles`, `downloadedImagesStats`, `deleteAllDownloadedImages`
- [x] `core/download/DownloadExportNaming.kt`: dışa aktarılan kopyaların ortak ad üretimi + `deleteExports`
- [x] `AnimeDownloader`: ad üretimini ortak kaynaktan al
- [x] `AnimeDownloadManager`: `clearAllDownloads` + tek bölüm silmede hayalet kopya temizliği
- [x] `StorageSettingsContent`: "İndirilenler" bölümü, satırlar, "Tüm indirilenleri sil", onay diyalogları
- [x] TR + EN metinler (`values/strings.xml`, `values-en/strings.xml`)
- [x] Sürüm 2.4.246 → 2.4.247, `release_notes_v2.4.247.md`
- [x] Rapor: `INDIRILENLER_VE_TOPLU_SILME_RAPORU_2026-10-11.md`

## Dokunulan dosyalar
```
app/build.gradle.kts                                                  (sürüm)
app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt          (M)
app/src/main/java/com/kitsugi/animelist/core/download/DownloadExportNaming.kt     (A)
app/src/main/java/com/kitsugi/animelist/core/storage/DownloadStorage.kt           (A)
app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt        (M)
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt (M)
app/src/main/java/com/kitsugi/animelist/utils/KitsugiImageDownloadHelper.kt       (M)
app/src/main/res/values/strings.xml                                               (M)
app/src/main/res/values-en/strings.xml                                            (M)
release_notes_v2.4.247.md                                                         (A)
INDIRILENLER_VE_TOPLU_SILME_RAPORU_2026-10-11.md                                  (A)
```

## Güvenlik kuralları
- İndirilen dosyalar `StorageJanitor` önbellek listesine **eklenmedi** → "Güvenli önbellekleri temizle"
  ve otomatik bakım kullanıcı arşivine dokunamaz; silme yalnızca açık kullanıcı onayıyla.
- Devam eden indirme varsa servis durdurulur, kayıtlar temizlenir.
- Silinen dosyalar `MediaScanner` ile sistemden düşürülür; tek dosya hatası toplu işlemi düşürmez.

## Doğrulama
- [x] Her iki `strings.xml` XML ayrıştırıcıdan geçti (6363 düğüm, hata yok)
- [x] Dokunulan Kotlin dosyalarında süslü/parantez dengesi kontrol edildi
- [ ] Cihaz derlemesi (bu ortamda Android SDK yok) → GitHub Actions `android-build.yml` ile yapılmalı

## Sürüm
`2.4.247-beta.<zamanVersionCode>` — commit: `feat(v2.4.247): Depolama ekraninda indirilen video/gorsel/altyazi takibi ve toplu silme`
