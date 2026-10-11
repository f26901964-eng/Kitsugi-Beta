# Ayarlar > Depolama — "İndirilenler" Bölümü ve Toplu Silme Raporu (2026-10-11, v2.4.247)

## 1. İstek
Kullanıcı, Depolama ekranında (ekran görüntüsü: 810,5 MB toplam) yalnızca **önbelleklerin** göründüğünü; kendi indirdiği **video, resim ve altyazıların** hiç görünmediğini bildirdi. İstek:
- indirilen video / resim / altyazıların tamamı bu ekranda görünsün,
- gerekirse buradan **topluca silinebilsin**,
- silme öncesi **onay penceresi** olsun.

Netleştirme sonrası kapsam:
| Soru | Seçim |
|---|---|
| Bölüm biçimi | **Özet** (tür başına toplam boyut + dosya sayısı) |
| "Resim" | **İkisi ayrı ayrı** → indirilen görseller (yeni satır) + görsel önbelleği (mevcut satır) |
| "Altyazı" | **İkisi ayrı ayrı** → indirilen altyazılar (yeni satır) + altyazı önbelleği (mevcut satır) |
| Toplam kullanım | **Değişmesin** → üstteki rakam yalnızca önbellekleri kapsar; indirilenler kendi bölümünde |

## 2. Önceki durum (kök neden analizi)

| Kalem | Disk konumu | Ekranda görünür müydü? | Silinebilir miydi? |
|---|---|---|---|
| İndirilen videolar (uygulama içi) | `Download/Kitsugi/Video/<mediaId>/video.mp4` + `subs/` + `metadata.json` | ❌ | Sadece İndirilenler ekranından tek tek |
| Dışa aktarılan video kopyası | `Download/Kitsugi/Video/<Başlık>_Bölüm_N.mp4` | ❌ | ❌ (hiçbir akış temizlemiyordu) |
| Dışa aktarılan altyazılar | `Download/Kitsugi/Video|Subtitles/<Başlık>_Bölüm_N.<dil>.<uzantı>` | ❌ | ❌ |
| İndirilen görseller | `Download/Kitsugi/Images`, `Pictures/Kitsugi/Images`, uygulama dış klasörü, eski `Download/Kitsugi` | ❌ | Sadece galeride tek tek |
| Eski indirme kökü | `cacheDir/Kitsugi_downloads` | ❌ | ❌ |
| Altyazı önbelleği (eklenti arşivleri) | `cacheDir/anisub_subs` | ✅ (229,8 MB) | ✅ |
| Görsel önbelleği (Coil) | `cacheDir/nuvio_images` | ✅ (512,2 MB) | ✅ |

**Tespit edilen ikinci sorun:** `AnimeDownloadManager.deleteDownload()` yalnızca `OfflinePlaybackHelper.deleteDownload(mediaId)` çağırıyordu; yani tek bölüm silindiğinde uygulama içi klasör gidiyor ama **dışa aktarılan `.mp4` ve `Kitsugi/Subtitles` altındaki altyazılar diskte kalıyordu**. Kullanıcı bölümü "sildim" sanırken aynı dosyanın ikinci kopyası kalıcı olarak yer kaplıyordu (her indirme ≈ 2× alan).

## 3. Yapılan değişiklikler

### 3.1 Yeni katman: `core/storage/DownloadStorage.kt`
- `Kind { VIDEOS, IMAGES, SUBTITLES }`, `Usage(kind, bytes, fileCount)`, `CleanResult(freedBytes, removedFiles, failures, message)`.
- `scan(context)` → üç türü ölçer; `totalBytes(context)` → bölüm başlığı için **tekrarsız** toplam (video klasörlerindeki altyazılar iki satırda birden sayıldığı için satır toplamı gerçek disk kullanımından büyük çıkar, bu fonksiyon her dosyayı bir kez sayar).
- `clean(context, kind)` / `cleanAll(context)` → yalnızca açık kullanıcı onayıyla çalışır.
- Video silme: `<mediaId>/` klasörleri bütün olarak, kök dizindeki düz `.mp4`/`.partial` dosyaları tek tek; ardından indirme kayıtları temizlenir ve dosyalar `MediaScanner` ile sistemden düşürülür.
- Altyazı silme: yalnızca altyazı uzantılı dosyalar (`srt/vtt/ass/ssa/sub`) + boşalan `subs/` klasörleri; **videolara dokunmaz**.
- Devam eden indirme varsa servis durdurulur (`stopService` + kayıt temizliği → servis içindeki izleyici aktif işi iptal eder).
- **Kritik tasarım kararı:** indirilen dosyalar `StorageJanitor.Bucket` listesine **eklenmedi**. Eklenseydi "Güvenli önbellekleri temizle" (`cleanAll`) ve 6 saatlik otomatik bakım kullanıcının çevrimdışı arşivini tek dokunuşta silerdi. İki liste bilinçli olarak ayrıdır.

### 3.2 Görsel API'si: `KitsugiImageDownloadHelper`
- `imageDirectories(context)`, `downloadedImageFiles(context)`, `downloadedImagesStats(context)` → ölçüm ve silme **aynı dosya listesini** kullanır.
- `deleteAllDownloadedImages(context)`: index kayıtlarını (varsayılan klasör / `mediastore:` / SAF klasörü) tek tek temizler, ardından klasörlerde kalan artıkları siler ve index'i boşaltır → galerideki "indirildi" rozetleri de kalkar, resimler yeniden indirilebilir.
- MediaStore kayıtları için boyut sorgusu (`SIZE`), SAF için `DocumentFile.length()` ile **silinen bayt gerçek ölçülür**.

### 3.3 Ortak dosya adı kaynağı: `core/download/DownloadExportNaming.kt`
- `exportFileName()` / `exportBaseName()` → indirme sırasında ve silme sırasında aynı ad hesaplanır (film özel durumu dahil).
- `deleteExports(context, animeTitle, episode)` → `Download/Kitsugi/Video|Subtitles` içindeki video + altyazı kopyalarını siler, MediaStore kayıtlarını düşürür.
- `AnimeDownloader` artık ad üretimini buradan alıyor; `AnimeDownloadManager.deleteDownload()` silme sonrası `deleteExports()` çağırıyor → **hayalet kopya sorunu kapandı**.
- `AnimeDownloadManager.clearAllDownloads(context)` eklendi (kayıtları + StateFlow'u boşaltır, servisi durdurur).

### 3.4 Arayüz: `ui/screens/settings/StorageSettingsContent.kt`
- Yeni **İndirilenler** bölümü: "İndirilen dosyalar: X" özeti + üç satır (İndirilen videolar / İndirilen görseller / İndirilen altyazılar). Her satırda boyut, dosya sayısı ve **"Tümünü sil"** eylemi; altında **"Tüm indirilenleri sil"** satırı.
- Her silme işlemi `KitsugiConfirmDialog(isDestructive = true)` ile **ayrı onay** ister; diyalog metni ne silineceğini, neyin korunacağını (hesap/mylist/izleme geçmişi) ve özel klasöre kopyalananların oradan silinmesi gerektiğini açıkça yazar.
- İşlem bitince liste otomatik tazelenir ve "X silindi" / "Silinecek indirilmiş dosya yoktu" durum mesajı gösterilir.
- Üstteki "Toplam kullanım" ve **"Güvenli önbellekleri temizle"** davranışı değişmedi; koruma notu ("indirdiğiniz videolar bu ekrandan silinmez") yeni bölüme işaret edecek şekilde güncellendi.
- TR + EN metinler eklendi (`strings.xml`, `values-en/strings.xml`).

## 4. Doğrulama
- Her iki `strings.xml` dosyası XML ayrıştırıcıdan geçirildi (6363 düğüm, hata yok).
- Dokunulan 6 Kotlin dosyasında süslü/parantez dengesi kontrol edildi (simge tabanlı hızlı denetim).
- **Not:** Bu ortamda Android SDK/Gradle bulunmadığı için derleme (assembleFossRelease) çalıştırılamadı; kod yolu statik incelendi. Derleme GitHub Actions (`android-build.yml` / `release.yml`) ile yapılmalıdır.

## 5. Sürüm
- `appVersionName` 2.4.246 → **2.4.247**, `release_notes_v2.4.247.md` eklendi.
