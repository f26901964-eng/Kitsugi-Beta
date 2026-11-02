# PLAN TASK — Videolar Sekmesi: Satır Bazlı İndirme + Oynamayan Klipler

**Tarih:** 2026-11-02
**Dal:** `arena/9212b7bd-kitsugi-beta` (taban: `3e09f9d`)
**Durum:** Uygulandı — `1ee9d01`
**Rapor:** `RAPOR_VIDEO_INDIRME_VE_OYNATMA_2026-11-02.md`

---

## 0. Kullanıcı istekleri (aynen)

1. Videolar ("Videolar") sekmesinde her video satırının sağında paylaş düğmesi var;
   **paylaşın hemen soluna indirme düğmesi** eklensin, o video indirilebilsin.
2. Bazı **kısa/klip videolar oynamıyor** — sebebi bulunup **eksiksiz** düzeltilsin.

## 1. Kabul kriterleri

| # | Kriter | Durum |
|---|---|---|
| K1 | Her satırda indirme düğmesi paylaşın **solunda** | ✅ |
| K2 | İndirme sürerken satırda yüzde, tamamlanınca onay, hatada yenile | ✅ |
| K3 | İndirme sürerken satıra tekrar dokunmak **iptal** eder | ✅ |
| K4 | Aynı anda birden çok satır indirilebilir (durumlar karışmaz) | ✅ |
| K5 | Dosya `Download/Kitsugi/Video` altında, dosya yöneticisinde görünür | ✅ |
| K6 | YouTube (imzalı/HLS) ve doğrudan (`AnimeThemes .webm`) kaynakların ikisi de indirilebilir | ✅ |
| K7 | Oynamayan klipte boş/siyah ekran yerine **oynayan** bir yol | ✅ (yedek akışlar + IFrame) |
| K8 | Ses var görüntü yok durumunda doğru tepki (motor, adres değil) | ✅ |
| K9 | Çevrimdışı/kalıcı hatada "Tarayıcıda aç" çıkışı | ✅ |

## 2. Kök neden özeti (detay raporda)

1. Extractor HLS manifestini bulunca **aynı yoklamadaki** adaptive/progressive adayları
   atıyordu → manifest 403 döndüğünde elde oynatılabilir adres kalmıyordu.
2. Deneme sırası "önce motor, sonra akış" idi; doğrusu "önce akış, sonra motor".
3. "Ses var görüntü yok" (VP9 çözücüsüz cihaz) adres değiştirmeden motor değiştiriyordu.
4. Son çare yoktu: "Tekrar Dene" aynı çıkarımı tekrarlıyordu.

## 3. İş kalemleri (dosya bazında)

### 3.1 İndirme altyapısı
- [x] `core/download/DetailVideoDownloader.kt` — HLS segment birleştirme (`EXT-X-MAP` önce),
      adaptive video+ses mux, progressive/doğrudan kopyalama, 900 MB sınırı, iptal desteği,
      dosya adı temizleme/`_1.._99` çakışma çözümü, `MediaScanner` bildirimi
- [x] `core/download/YouTubeDownloadEncoder.kt` (`data/trailer/`) — `MediaExtractor` +
      `MediaMuxer` ile **yeniden kodlamasız** remux; AVC/HEVC+AAC → mp4, VP8/VP9/AV1+Vorbis → webm (API 30+)
- [x] `core/download/KitsugiVideoDir.kt` — `Download/Kitsugi/Video` → dış files → `filesDir`
- [x] `core/download/DetailVideoDownloadNotifier.kt` — ayrı kanal, kimlik tabanı 7100,
      500 ms throttle, `POST_NOTIFICATIONS` koruması
- [x] `data/trailer/YouTubeHlsMediaPlaylistParser.kt` — saf ayrıştırıcı (init segment,
      segment listesi, `isTransportStream`, `isEncrypted`, `isUsable`)

### 3.2 UI
- [x] `ui/screens/detail/VideoDownloadController.kt` — satır anahtarlı durum
      (Idle/Running/Done/Failed), tekrar dokunuşta iptal, ekrandan çıkışta temizlik
- [x] `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt` — `DetailVideoRow`:
      indirme `Box` (30 dp) paylaşın solunda; `VideoDownloadAction` (spinner/onay/yenile);
      `DetailVideoGroup(downloadController)`
- [x] `res/values/strings.xml` + `res/values-en/strings.xml` — 18 metin (tr/en)

### 3.3 Oynatma düzeltmeleri
- [x] `data/trailer/TrailerPlaybackSource.kt` — `alternateVideoUrls` / `alternateAudioUrls`,
      `sourceAt(index)`, `totalSources`
- [x] `data/trailer/InAppYouTubeExtractor.kt` — 45 dk kaynak önbelleği,
      `resolveDownloadCandidates`, `resolveHlsMediaPlaylist`, `collectCandidates` refaktörü,
      `buildAlternateSources` (ses **aynı istemciden**)
- [x] `ui/screens/detail/KitsugiDetailPlayerComponents.kt` — sıra: akışlar → motor →
      yedek motorda yedekler → IFrame; `retryStreamFirst` ile kodek/akış ayrımı
- [x] `ui/screens/detail/KitsugiIframeYouTubePlayer.kt` — YouTube IFrame son çaresi
      (`enableAutomaticInitialization=false`, lifecycle observer, `release()`,
      `VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER` → tarayıcı düğmesi)

### 3.4 Test
- [x] `app/src/test/.../YouTubeHlsMediaPlaylistParserTest.kt` (6 test)
- [x] `app/src/test/.../TrailerPlaybackSourceTest.kt` (4 test)

## 4. Riskler / geri alma

| Risk | Etki | Önlem |
|---|---|---|
| HLS segment birleştirme fMP4 init'i kaçırır | Dosya açılmaz | Ayrıştırıcı testleri + başarısızsa alt yola düşme |
| `MediaMuxer` uyumsuz kodek çifti | Mux başarısız | `false` dönüp progressive'a düşülüyor |
| IFrame yedeği reklam gösterir | Beklenen | Yalnızca son çare |
| Önbellek bayat imza döndürür | 403 | 45 dk TTL + yedek akışlar |
| Geri alma | — | Tek commit: `git revert 1ee9d01` |

## 5. Doğrulama

- `git diff --check` temiz, parantez/import taraması temiz
- Bu ortamda **JDK/SDK yok** → derleme ve test çalıştırma yapılamadı
- CI'da: `./gradlew :app:testDebugUnitTest --tests '*YouTubeHlsMediaPlaylistParserTest' --tests '*TrailerPlaybackSourceTest'` + `:app:assembleDebug`

## 6. Elle test senaryoları

1. YouTube fragmanı indir → `Download/Kitsugi/Video` altında oynatılabilir dosya
2. Bir OP + bir ED'yi aynı anda indir → iki satırda iki ayrı yüzde
3. İndirme sürerken satıra tekrar dokun → iptal + bildirim kalkar
4. Ağ bağlantısını kes, video aç → 18 sn içinde IFrame ya da "Tarayıcıda aç" (boş ekran yok)
5. VP9 `.webm` (AnimeThemes) → ses var görüntü yok olursa motor değişimi, dosya oynar
