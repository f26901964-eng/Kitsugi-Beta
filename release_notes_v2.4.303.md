# Kitsugi Beta v2.4.303

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Oynatıcı ve Kaynak Düzeltmeleri
- **Düz MP4 Dosyalarının HLS Sanılması Engellendi (`KN-1`):** `PlayerMediaSourceFactory.isHlsUrl` fonksiyonunda host listesi (`closeload`, `rapidrame`, `hdfilmcehennemi`, `filmmakinesi` vb.) dosya uzantısından önce kontrol edildiği için bu sunuculardan gelen düz `.mp4` ve `.mkv` dosyaları HLS MIME türüyle (`application/x-mpegURL`) ExoPlayer'a veriliyordu. Sıralama düzeltilerek önce gerçek manifest belirteçleri (`m3u8`, `master.txt`, `/hls/`), ardından `PROGRESSIVE_PATH_REGEX` (`.mp4/.mkv/.webm/.avi/.m4v`) kontrolü sağlandı. Düz MP4 videoları artık sorunsuz oynatılıyor.
- **`loadLinks` Zaman Aşımı Bütçesi Genişletildi (`KN-2`):** HDFilmCehennemi ve FilmMakinesi gibi her sunucu ve iframe için sıralı istek atan eklentilerde bağlantı çözümleme süresi aşama bazında 25 saniyeden **60 saniyeye**, sağlayıcı genel bütçesi ise 75 saniyeden **100 saniyeye** çıkarıldı. Böylece kaynakların süre yetersizliği nedeniyle düşmesi ve 0–1 kaynak bulunması sorunu giderildi.
- **Dublaj ↔ Orijinal Kaynak Değişiminde Kesintisiz Konum Koruma (`KN-4`):** Eskiden oynatıcıda dublaj veya orijinal ses/sunucu değiştirildiğinde yeni URL 0. saniyeden başlıyordu. Mevcut oynatma konumu `pendingSourceSwitchResumeMs` içinde saklanıp yeni kaynak başlatılırken `startPos` olarak aktarılıyor. Kaynak değiştirildiğinde izleme tam kaldığı yerden devam eder.
- **Harici Ses Akışları Desteği (`KN-3`):** CloudStream eklentilerinin bildirdiği ayrık ses akışları (`ExtractorLink.audioTracks`) artık atılmıyor; `StreamSource.externalAudioUrl` alanına kaydedilerek video ile birlikte ExoPlayer motoruna ses parçası olarak bağlanıyor.

### 📝 Altyazı URL Normalizasyonu ve Çok Dilli ISO Eşleme
- **HDFilmCehennemi Altyazı URL'sindeki Hatalı Sondaki `/` Temizlendi:** HDFilmCehennemi eklentisinin göreli altyazı yollarının sonuna eklediği gereksiz `/` karakteri (`.../altyazi/a.vtt/`) sunucularda 404 hatasına yol açıyordu. `normalizeCsSubtitleUrl` fonksiyonu eklenerek `.vtt`, `.srt`, `.ass` gibi uzantılardan sonraki sondaki eğik çizgi otomatik olarak temizlendi.
- **Türkçe İsimlendirilen Dillerin ISO Koduna Eşlenmesi:** Türk kaynaklarında (ör. Anizium) altyazı dil adları Türkçe olarak yazılmaktadır. "İngilizce", "Almanca", "Fransızca", "İtalyanca", "İspanyolca", "Rusça", "Arapça", "Japonca", "Korece" ve "Çince" adları doğru BCP-47 / ISO kodlarına (`en`, `de`, `fr`, `it`, `es`, `ru`, `ar`, `ja`, `ko`, `zh`) eşlendi.
- **Türkçe `İ` Harfi Birleşik Nokta (U+0307) Düzeltmesi:** Büyük `İ` harfi küçük harfe çevrildiğinde oluşan `i` + birleşik nokta karakteri temizlenerek "İngilizce" ve "İtalyanca" etiketlerinin doğru dille eşleşmesi garanti altına alındı.
- **Bilinmeyen Dillerin Türkçe Sayılması Engellendi (`KN-5`):** Tanınmayan yabancı dillerin varsayılan olarak `tr` etiketlenmesi (`contains("tr")` hatası) önlendi; bilinmeyen diller `und` (undetermined) olarak işaretleniyor. "Altyazı" ve "Dublaj" metinleri ise Türkçe kaynak kabul ediliyor.

### 🔌 Eklenti Tanımları ve Canlı Domain Güncellemeleri
- **HDFilmCehennemi Domaini .nl Olarak Güncellendi:** Park sayfasına düşen ve ölü durumda olan `hdfilmcehennemi.biz` alan adı, canlı ve çalışan `https://www.hdfilmcehennemi.nl` adresine güncellendi.
- **FilmMakinesi Embed Tanıma:** `closeload.filmmakinesi.to` alan adı tanınan gömülü oynatıcı listesine eklendi.
- **Eklenti Kaynak Eşitlemesi:** AnimeciX, FilmMakinesi ve HDFilmCehennemi eklenti tanımları canlı deponun son kararlı sürümlerine eşitlendi.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Player and Source Fixes
- **Prevented Progressive MP4 Files from Being Misidentified as HLS (`KN-1`):** In `PlayerMediaSourceFactory.isHlsUrl`, host domain checks (`closeload`, `rapidrame`, `hdfilmcehennemi`, `filmmakinesi`, etc.) previously ran before file extension checks, incorrectly assigning progressive `.mp4` and `.mkv` files the HLS MIME type (`application/x-mpegURL`). Reordered checks to prioritize explicit manifest indicators (`m3u8`, `master.txt`, `/hls/`), followed by `PROGRESSIVE_PATH_REGEX` (`.mp4/.mkv/.webm/.avi/.m4v`). Direct MP4 video sources now play seamlessly in ExoPlayer.
- **Extended `loadLinks` Timeout Budget (`KN-2`):** For providers executing sequential requests across multiple iframes/servers (like HDFC and FilmMakinesi), the stage timeout budget was increased from 25s to **60s**, and overall provider budget from 75s to **100s**, preventing stream links from prematurely timing out and returning 0 or 1 source.
- **Seamless Position Persistence on Dub ↔ Sub Source Switching (`KN-4`):** Switching between dubbed and original streams or changing servers previously reset playback position to second 0. Current playback timestamp is now preserved in `pendingSourceSwitchResumeMs` and supplied as `startPos` when preparing the new stream, resuming playback exactly where you left off.
- **External Audio Tracks Support (`KN-3`):** Disjoint audio tracks declared by CloudStream providers (`ExtractorLink.audioTracks`) are no longer discarded; they are retained in `StreamSource.externalAudioUrl` and piped into ExoPlayer alongside the video stream.

### 📝 Subtitle URL Normalization & Multilingual ISO Detection
- **Stripped Erroneous Trailing Slash from HDFilmCehennemi Subtitle URLs:** Fixed an issue where relative subtitle links appended with a trailing slash (`.../sub.vtt/`) caused HTTP 404 errors on upstream CDN servers. Added `normalizeCsSubtitleUrl` to trim trailing slashes following subtitle extensions (`.vtt`, `.srt`, `.ass`).
- **Mapped Turkish-Named Languages to ISO Codes:** Providers writing language names in Turkish (e.g. "İngilizce", "Almanca", "Fransızca", "İtalyanca", "Arapça", "Çince") are now mapped to standardized BCP-47 / ISO codes (`en`, `de`, `fr`, `it`, `ar`, `zh`, etc.).
- **Turkish `İ` Combining Dot (U+0307) Normalization:** Handled lowercase conversion of Turkish dotted capital `İ` (`i` + U+0307 combining dot) to ensure language tags like "İngilizce" reliably resolve to `en`.
- **Prevented Unknown Languages from Defaulting to Turkish (`KN-5`):** Eliminated the loose fallback where any unidentified language was tagged as `tr`, tagging them as `und` instead while preserving their human-readable name in the player selector.

### 🔌 Plugin Manifests & Live Domain Updates
- **Updated HDFilmCehennemi Domain to .nl:** Switched from the parked/dead `hdfilmcehennemi.biz` address to the active `https://www.hdfilmcehennemi.nl` domain.
- **FilmMakinesi Embed Support:** Added `closeload.filmmakinesi.to` to recognized video embed domains.
- **Plugin Manifest Parity:** Synchronized AnimeciX, FilmMakinesi, and HDFilmCehennemi plugins in `plugins.json` with their latest stable upstream releases.
