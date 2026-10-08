# Kitsugi — Galeri İndirme: Tekrar İndirmeyi Önleme + İçerik Adına Göre Gruplama Planı

**Tarih:** 2026-10-08 · **Dal:** `arena/b3cbf78d-kitsugi-beta` · **Temel commit:** `05d1d172` (main)

---

## 1. Şikayet / İstek

Kullanıcı (2026-10-08, ekran görüntüsü: yatay galeri, sağ panelde "İndirilen Resimler"):

1. **İndirilenler galerisinde indirme butonu olmamalı** — İndirmeler → Resimler sekmesinden
   açılan resim galerisinde (yerel `file://` içerikler) indirme butonu gereksiz; zaten
   indirilmiş bir içerik tekrar indirilmek istenmiyor.
2. **Tekrar indirme mantığı** — Daha önce indirilmiş bir resim tekrar indirilmemeli;
   bu engel için bir mantık kurulmalı.
3. **İndirilen resimler içerik adına göre gruplansın** — İndirmeler → Resimler sekmesi
   resimleri anime / dizi / film adına göre kısım kısım (bölüm bölüm) göstermeli.
4. **Kaynak kontrolü** — Resim galerisi için veri sağlayan TMDB, Shikimori ve Fanart.tv
   kaynaklarının hem kendilerine hem diğer kaynaklara düzgün resim verip vermediği
   kontrol edilmeli.

## 2. Kök Neden

1. `KitsugiImageGalleryDialog` her koşulda indirme butonu çiziyordu; yerel dosya
   (`file://`) kontrolü yoktu.
2. `KitsugiImageDownloadHelper.downloadImage` dosya adında `System.currentTimeMillis()`
   kullandığı için aynı URL'nin tekrar indirilip indirilmediğini anlama imkânı yoktu —
   tekrar indirme ancak aynı URL'ye denk gelince fark ediliyordu.
3. `DownloadsScreen.DownloadedImagesTab` resimleri tek bir düz `LazyVerticalGrid` olarak
   gösteriyordu; içerik adına göre gruplama yoktu.
4. Ayrıca `downloadImage` içindeki `sanitizedTitle` yalnızca `[a-zA-Z0-9_]` bıraktığı için
   Türkçe/Japonca içerik adları bozuluyordu (örn. "Şokugeki no Soma" → "okugeki_no_Soma"),
   bu da içerik adına göre gruplamayı zedeliyordu.

## 3. Yapılan Değişiklikler

### 3.1 `app/src/main/java/com/kitsugi/animelist/utils/KitsugiImageDownloadHelper.kt`

- **Kalıcı indirme index'i** eklendi: `filesDir/kitsugi_downloaded_images.json`
  (URL → `{ fileName, customUri, timestamp }`). Bellek içi önbellek + `synchronized` ile
  thread-safe.
- `downloadedUrls: StateFlow<Set<String>>` — arayüzün dinlediği, hâlâ diskte duran
  indirilmiş URL'ler.
- `refreshDownloadedUrls(context)` — index'i tazeler, dosyası silinmiş (stale) kayıtları
  temizler.
- `isImageDownloaded(context, url)` — URL daha önce indirilmiş mi (diskte duruyor mu)?
- `findDownloadedImageFile(context, url)` — varsayılan konumdaki dosyayı döner.
- `markImageDownloaded(...)` — başarılı indirme sonrası index'e işler.
- `unmarkImageDownloadedByFileName(...)` — İndirmeler ekranından silinen dosyanın index
  kaydını düşürür (kullanıcı isterse tekrar indirebilir).
- `downloadImage` başına korumalar:
  - `file://` / `content://` URL → "Bu resim zaten cihazda kayıtlı." — indirme yok.
  - `isImageDownloaded` → "Bu resim zaten indirilmiş." — indirme yok.
  - Aynı URL için eşzamanlı (in-flight) ikinci istek → "Bu resim zaten indiriliyor."
  - Başarılı kayıt sonrası (hem SAF hem varsayılan konum) `markImageDownloaded`.
- `sanitizedTitle` artık Unicode harfleri koruyor: `[^\p{L}\p{N}_]` + ardışık `_`
  çökertme — içerik adları (Türkçe/Japonca) düzgün kalır.

### 3.2 `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt`

- Yeni parametre: `allowDownload: Boolean = true` (ana composable + `imageUrls` overload'ı).
- `KitsugiImageDownloadHelper.downloadedUrls` StateFlow'u dinlenir; açılışta
  `refreshDownloadedUrls` çağrılır.
- Mevcut sayfa için durum hesabı:
  - `isLocalImage` (`file://` / `content://`) veya `allowDownload=false` →
    **indirme butonu hiç çizilmez** (hem yatay sağ panel hem dikey header).
  - `isAlreadyDownloaded` → buton yeşil **tik (CheckCircle) "İndirildi"** durumunda;
    dokununca "Bu resim zaten indirilmiş." toast'u.
- `onDownload` korumaları: yerel dosya / zaten indirilmiş / izin / indirme sırası.

### 3.3 `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`

- `DownloadedImageGroup` + `normalizeImageGroupKey` eklendi.
- `DownloadedImagesTab` artık resimleri **içerik adına göre gruplar**: her grup bir başlık
  (içerik adı + "N resim" rozeti) ve kendi resim dizisi (`FlowRow`) ile gösterilir.
  Gruplar en yeni indirmenin tarihine göre sıralanır.
- Grup içinden açılan galeri: yalnızca o grubun resimleri + başlık içerik adı +
  **`allowDownload = false`** (indirme butonu yok).
- Silme işleminde `KitsugiImageDownloadHelper.unmarkImageDownloadedByFileName` çağrılır —
  index temiz kalır, resim istenirse tekrar indirilebilir.
- `DownloadedImageCard` artık `modifier` parametresi alıyor (FlowRow genişliği için).
- `loadAllDownloadedImages` başlık ayrıştırması: ardışık alt çizgiler tek boşluğa iner.

## 4. Kaynak Kontrolü (TMDB / Shikimori / Fanart.tv)

Galeri verisi sağlayan üç kaynak denetlendi — **üçü de düzgün çalışıyor**:

| Kaynak | Girdi | Mekanizma | Durum |
|---|---|---|---|
| **TMDB** | TMDB ID + tür | `GET /3/{movie\|tv}/{id}/images` (poster/backdrop/logo, `original` çözünürlük), birincil tür boşsa diğer tür denenir, `TmdbApiClient.getActiveApiKey()` (kullanıcı anahtarı → yerleşik yedek) | ✅ |
| **Shikimori** | Anime (MAL) ID | `GET /api/animes/{id}/screenshots`, `executeGetRequestResilient` (429/5xx tekrar), `ShikimoriPosterResolver.absoluteUrl` (göreli→mutlak, eksik görsel filtresi), bellek önbelleği | ✅ |
| **Fanart.tv** | Film: TMDB ID; TV/Anime: TVDB ID | TVDB çözümü: Room → TMDB `external_ids` → animeapi.my.id (MAL/AniList) → ARM (Kitsu); uçtan uca fallback (film↔TV); tüm kategoriler (poster/backdrop/logo/clearart/banner/thumb/characterart/square/disc) tek istekte | ✅ |

Üç kaynak da `MediaEntryDetailViewModel.fetchFanartGallery` ve
`ApiResultDetailViewModel` içinde paralel (`async`) çekilip `distinctBy { url }` ile
birleştiriliyor; mevcut kapak/oylama görselleri de zengin meta ile ekleniyor.
Ayarlar: `fanartTvEnabled`/`fanartTvApiKey` (SettingsDataStore), TMDB anahtarı
`KitsugiApplication` + `SettingsDataStore` üzerinden `updateCache` ile besleniyor.

**Sonuç:** üç kaynakta düzeltme gerektiren bir arıza bulunmadı.

## 5. Test

- Derleme: sandbox'ta Java/Android SDK bulunmadığından tam `gradlew` derlemesi
  yapılamadı; sözdizimi (parantez/küme dengesi) ve import'lar elle doğrulandı.
- Elle test senaryoları:
  1. İndirmeler → Resimler → bir gruba dokun → galeride **indirme butonu yok**.
  2. Uzak galeri (detay sayfası) → indir → buton yeşil tik; tekrar dokun →
     "Bu resim zaten indirilmiş."
  3. İndirmeler → Resimler → resim sil → aynı resim galeriden tekrar indirilebilir.
  4. Aynı anda çift dokunuş → "Bu resim zaten indiriliyor."
  5. Türkçe içerik adı (örn. "Şokugeki no Soma") → grup başlığı düzgün görünür.

## 6. Notlar

- Index dosyası uygulama içi depolamada tutulur; kullanıcı dosyayı dışarıdan
  (dosya yöneticisi) silerse bir sonraki `refreshDownloadedUrls` stale kaydı temizler.
- Eski sürümle indirilmiş resimlerde index kaydı yoktur; bu resimler listede görünür,
  ancak URL bazlı "zaten indirilmiş" kontrolüne düşmez (ilk kez bu sürümde indirilene
  kadar). Kabul edilebilir bir geçiş davranışıdır.
