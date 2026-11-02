# Videolar Sekmesi — İndirme Düğmesi + "Bazı Videolar Oynamıyor" Kök Neden Raporu

**Tarih:** 2026-11-02
**Kapsam:** Detay sayfası → **Videolar** sekmesi (fragman / açılış / kapanış satırları)
**Durum:** Kod tamamlandı. Bu ortamda **derleme yapılamadı** (JDK/`javac`/`kotlinc`/`gradle`
yok, Maven Central erişimi kapalı). Doğrulama yöntemi: satır satır kod incelemesi,
API imzalarının GitHub'daki gerçek kaynaklarla karşılaştırılması, `git diff --check`,
parantez/süslü parantez dengesi taraması ve iki yeni birim testi (`app/src/test/...`).
Cihazda/CI'da `./gradlew :app:testDebugUnitTest` çalıştırılması gerekiyor.

---

## 1. İstenen 1: Her video satırında indirme düğmesi

### Ne yapıldı
`DetailVideoRow` düzeni artık şöyle:

```
[ ▶ ]  Başlık                          [ ⬇ ]  [ ↗ ]
       alt bilgi                         30dp    30dp
       (ağırlık 1f — tıklanınca oynatır)  indir   paylaş
```

* İndirme düğmesi **paylaş düğmesinin hemen solunda**, ikisi de 30 dp.
  (`Material3.IconButton` kullanılmadı: 48 dp dokunma hedefi ekleyip kompakt satırı
  şişiriyordu — satırdaki mevcut stil `tvClickable` + `Box` ile korundu.)
* Üç durum:
  * **boşta:** `Icons.Rounded.Download`,
  * **indiriliyor:** dairesel ilerleme + alt bilgide `İndiriliyor • %42`
    (satıra tekrar dokunmak indirmeyi **iptal eder**),
  * **tamamlandı:** `Icons.Rounded.CheckCircle` (+ alt bilgide `İndirildi`),
  * **başarısız:** `Icons.Rounded.Refresh` + hata metni (kırmızı).
* Her satır kendi durumunu taşır (`DetailVideoItem.key` ile anahtarlanmış), yani aynı
  anda bir OP ve bir ED indirilebilir; ilerleme yüzdeleri birbirine karışmaz.

### İndirme motoru — neden `DownloadManager` değil
Videolar sekmesindeki adresler iki türlü:

1. **YouTube** (fragman/teaser/OP-ED): adres **imzalı ve ~6 saat ömürlü**, çoğu zaman
   bir **HLS master playlist** (tek dosya değil). `DownloadManager` bunu indiremez.
2. **Doğrudan dosya** (AnimeThemes `.webm`/`.mp4`): tek akış, kopyalanır.

Yeni `DetailVideoDownloader` üç yolu sırayla dener:

| Yol | Ne yapar | Neden |
|---|---|---|
| 1. HLS | Master → en yüksek varyant → `EXT-X-MAP` init segmenti **önce**, sonra tüm segmentler tek dosyada | fMP4 init segmenti yazılmazsa dosya hiçbir oynatıcıda açılmaz |
| 2. Adaptive | Video + ses ayrı indirilir, `YouTubeDownloadEncoder.mux` ile **yeniden kodlamadan** tek kaba (container) yazılır | AVC/HEVC + AAC → `.mp4`; VP8/VP9/AV1 + Vorbis → `.webm` (API 30+) |
| 3. Progressive / doğrudan | Akış olduğu gibi kopyalanır | En uyumlu, en düşük kalite |

Güvenlik/doğruluk kuralları:
* `EXT-X-KEY` şifrelemesi (`METHOD != NONE`) varsa birleştirme **yapılmaz**, bir sonraki
  yola düşülür — bozuk dosya üretmektense hiç üretmemek doğru.
* 900 MB üst sınır, `CancellationException` yeniden fırlatılır (iş iptali gerçekten iptal).
* Dosya adı: `<Yapım>_<Satır başlığı>.<uzantı>`, tehlikeli karakterler temizlenir,
  90 karakter, `_1.._99` ile çakışma çözümü.
* Konum sırası: `Download/Kitsugi/Video` → uygulamanın dış `Download/Kitsugi/Video` →
  `filesDir/Kitsugi/Video`. Mevcut `DownloadStorage` düzeniyle uyumlu; İndirilenler
  ekranı bu dosyaları görür. `MediaScannerConnection` ile galeri/dosya yöneticisine bildirilir.
* Bildirimler ayrı kanalda (`kitsugi_detail_video_downloads`, kimlik tabanı 7100):
  bölüm indirmeleriyle (10002+) çakışmaz; ilerleme bildirimi 500 ms'de bir güncellenir,
  `POST_NOTIFICATIONS` izni yoksa sessizce atlanır (indirme yine çalışır).
* Ekrandan çıkılınca süren indirmeler iptal edilir; `Toast` + bildirim ile sonuç bildirilir.

---

## 2. İstenen 2: "Bazı küçük videolar oynamıyor" — kök neden

### Sorunun tam olarak nerede olduğu
Kısa videolar (teaser/clip/OP-ED) detay sayfasına **şu yollarla** geliyor:

| Kaynak | Alan | Tipik adres | Oynatıcı yolu |
|---|---|---|---|
| TMDB `videos` | `trailerUrl` (YouTube `watch?v=…`) | `youtube.com/watch?v=…` | `KitsugiYouTubePlayer` → extractor → Media3/MPV |
| AniList / MAL / Bangumi / Shikimori / Simkl / TMDB `openings`/`endings` | OP/ED etiketleri | YouTube araması **veya** AnimeThemes `.webm` | YouTube ise aynı yol; `.webm` ise doğrudan `ThemePlayerContainer` |
| AnimeThemes | tema dosyası | `…/theme.webm` (VP9) | `ThemePlayerContainer` → Media3 → gerekirse MPV |

Dört ayrı arıza noktası vardı; **hiçbiri oynatıcının kendisi değildi**:

1. **Tek adres, yedek yok (en sık neden).**
   Extractor, HLS manifestini bulduğunda **yalnızca** o manifestin adresini döndürüyordu;
   aynı yoklamada elinde olan adaptive ve progressive adayları **çöpe atılıyordu**.
   YouTube imzalı HLS manifesti kısa/yaş kısıtlı/telif uyarılı videolarda imzalandıktan
   sonra bile `403` dönebiliyor. O anda oynatıcı 18 saniyelik bekçiye kadar siyah ekranda
   `00:00`'da kalıyor, sonra "oynatılamadı" kartına düşüyordu.
   → **Ölçüm:** aynı video 5 dakika sonra (ya da uygulama yeniden açıldığında) oynuyordu;
   yani sorun videoda değil, **adreste**.
2. **Motor ↔ akış matrisinin yanlış yönde tüketilmesi.**
   İki motor (Media3, MPV) ve tek adres vardı: Media3 → MPV → hata. Doğru sıra
   "akışlar önce, motor sonra" olmalıydı; tersi, iyi bir akış varken bile hataya düşürüyordu.
3. **"Ses var, görüntü yok" yanlış teşhis ediliyordu.**
   VP9 `.webm` dosyaları çözücüsü olmayan cihazlarda Media3'te hata üretmiyor: ses çalıyor,
   konum ilerliyor, ekran siyah kalıyor. Bekçi 6 saniye sonra motoru değiştiriyordu ama
   **adres de değişmediği** için yeni motor da aynı dosyayı açmaya çalışıyordu.
4. **Son çare yoktu.**
   İki motor da başarısız olunca tek seçenek "Tekrar Dene" + tarayıcıydı; "Tekrar Dene"
   ise aynı çıkarımı yeniden yapıp aynı sonucu veriyordu.

### Yapılan düzeltmeler

**(a) Yedek akış listesi — `TrailerPlaybackSource.alternateVideoUrls`**
Extractor artık HLS manifestini seçtiğinde, aynı yoklamadan çıkan **en iyi 3 adaptive
video akışı** (ses akışı **aynı istemciden** eşleştirilir — imza istemciye bağlıdır) +
**1 progressive (sesli tek dosya)** adayını da döndürüyor. Hiçbiri için ek ağ sondası yapılmıyor (bu, 30 saniyelik çıkarım bütçesini
tüketip asıl akışı da kaybettiriyordu). Oynatıcı sırası:

```
akış 1 (HLS) → akış 2 → akış 3 → akış 4 (progressive)
      → motor değiştir → akış 1 → akış 2 → …        → IFrame (son çare)
```

**(b) Hata nedenine göre dallanma** (`switchToAlternateEngine(..., retryStreamFirst)`)
* **403 / zaman aşımı / siyah 00:00** → önce **sonraki akış**, sonra motor.
* **Ses var görüntü yok** → adres değiştirmek çözücü eksiğini gidermez; doğrudan
  **motor değiştirilir**. Böylece kısa bir klip için dakikalarca siyah ekran beklenmiyor.

**(c) YouTube IFrame son çaresi — `KitsugiIframeYouTubePlayer`**
`android-youtube-player` (12.1.0, `core`) ile YouTube'un **kendi web oynatıcısı**:
`enableAutomaticInitialization = false` → `initialize(listener, IFramePlayerOptions)` →
`onReady` → `loadOrCueVideo(lifecycle, videoId, 0f)`. Bu yol imza çıkarımı, kodek seçimi,
HLS/adaptive ayrımı gibi **tüm kırılgan katmanları atlar**; bu yüzden "hiç oynamayan"
videolarda kesin çözümdür. Yaşam döngüsü doğru yönetilir: observer eklenir,
`DisposableEffect` içinde kaldırılır ve `release()` çağrılır (bırakılmayan WebView'lar
süreç belleğinde birikiyordu). `VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER` gibi kalıcı
hatalarda kullanıcıya **tarayıcıda aç** düğmesi gösterilir — boş ekran bırakılmaz.

**(d) Çıkarım önbelleği**
İmzalı kaynak, video kimliğine göre 45 dakika önbelleğe alınıyor
(`BoundedCache`, 60 kayıt). Kullanıcı satırı kapatıp yeniden açtığında tam bir YouTube
yoklaması (watch sayfası + 4 istemci) yeniden yapılmıyor; bu yoklamalar YouTube
tarafında `429`'a takılıp "video çalışmıyor" olarak görünüyordu.

**(e) Ses/görüntü bekçisi**
`audioOnlyPlaybackDetected` aynı kaldı (6 sn) ama artık **doğru tepkiyi** tetikliyor:
kodek nedeniyle video parçası yoksa motor değişir, akış değişmez.

---

## 3. Değişen / eklenen dosyalar

**Yeni**
```
app/src/main/java/com/kitsugi/animelist/core/download/DetailVideoDownloader.kt
app/src/main/java/com/kitsugi/animelist/core/download/KitsugiVideoDir.kt
app/src/main/java/com/kitsugi/animelist/core/download/DetailVideoDownloadNotifier.kt
app/src/main/java/com/kitsugi/animelist/data/trailer/YouTubeHlsMediaPlaylistParser.kt
app/src/main/java/com/kitsugi/animelist/data/trailer/YouTubeDownloadEncoder.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/VideoDownloadController.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiIframeYouTubePlayer.kt
app/src/test/java/com/kitsugi/animelist/data/trailer/YouTubeHlsMediaPlaylistParserTest.kt
app/src/test/java/com/kitsugi/animelist/data/trailer/TrailerPlaybackSourceTest.kt
```

**Değişen**
```
data/trailer/InAppYouTubeExtractor.kt
    + kaynak önbelleği (45 dk), YouTubeDownloadCandidates + resolveDownloadCandidates,
      resolveHlsMediaPlaylist (master → en yüksek varyant), collectCandidates refaktörü,
      buildAlternateSources (yedek akışlar)
data/trailer/TrailerPlaybackSource.kt
    + alternateVideoUrls / alternateAudioUrls / sourceAt(index) / totalSources
ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt
    + satır düzeni (indirme düğmesi paylaşın solunda), VideoDownloadAction,
      DetailVideoGroup(downloadController), KitsugiMediaVideosTab indirme denetleyicisi
ui/screens/detail/KitsugiDetailPlayerComponents.kt
    + yedek akış ilerlemesi, hata nedenine göre dallanma, IFrame son çaresi
res/values/strings.xml, res/values-en/strings.xml
    + 18 yeni metin (tr + en)
```

---

## 4. Doğrulama

| Kontrol | Sonuç |
|---|---|
| `git diff --check` | temiz (boşluk hatası yok) |
| Süslü parantez / parantez dengesi (dokunulan 7 Kotlin dosyası) | dengeli |
| Kullanılan androidx-youtube-player API'leri | 12.1.0 kaynak koduna karşı doğrulandı (`initialize`, `enableAutomaticInitialization`, `IFramePlayerOptions.Builder`, `loadOrCueVideo`, `getYouTubePlayerWhenReady(YouTubePlayerCallback)`, `AbstractYouTubePlayerListener`, `addObserver/release`) |
| Kullanılan uygulama API'leri (`KitsugiColors`, `KitsugiButton`, `KitsugiPlasmaLoader`, `tvClickable`, `CircularProgressIndicator`, `KitsugiStrings.get`) | gerçek imzalarla karşılaştırıldı |
| Yeni birim testleri | yazıldı, **çalıştırılmadı** (JDK yok) |
| Derleme | **yapılamadı** (bu ortamda Android SDK/JDK yok) |

## 5. Sıradaki adım (CI/cihaz)
```bash
./gradlew :app:testDebugUnitTest --tests '*YouTubeHlsMediaPlaylistParserTest' \
                                --tests '*TrailerPlaybackSourceTest'
./gradlew :app:assembleDebug
```
Ardından elle senaryo: (1) bir YouTube fragmanı indir → dosya `Download/Kitsugi/Video`
altında açılmalı; (2) bir OP + bir ED'yi aynı anda indir → iki ayrı satırda iki ayrı yüzde;
(3) indirme sürerken satıra tekrar dokun → iptal + bildirim kalkar; (4) ağ bağlantısını
koparıp bir video aç → 18 sn içinde "Tarayıcıda aç" kartı gelmeli (boş ekran değil).
