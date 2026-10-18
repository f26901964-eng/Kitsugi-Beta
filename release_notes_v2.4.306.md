# Kitsugi Beta v2.4.306

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Video Oynatma & Akış Düzeltmeleri
- **Embed Origin Başlığı Kurtarma ve 5004 Düşme Koruması (`KN-B`):** FilmMakinesi ve HDFilmCehennemi gibi gömülü sağlayıcılarda `rapidrame`, `playmix.uno`, `closeload` akışları embed sayfasının origin'ine göre doğrulanır. `CsStreamRunner` kurtarma yollarında (medya içerik kontrolü, `EmbedMediaScanner` derin HTML taraması, WebView medya yakalama) ve `verifyMediaCandidate` doğrulama çağrısında `Origin` başlığı eksik kalmıyor; embed sayfasının gerçek origin'i (`originOfUrl`, `applyEmbedOriginHeaders`) otomatik dolduruluyor. Eklentinin sağladığı özel token ve başlıklar kesinlikle ezilmiyor.
- **Bozuk URL ve Çift Şema Savunma Katmanı (`CsStreamSanitizer`):** Eski eklenti sürümlerinin ürettiği çift şemalı (`httpshttp://`, `httpshttps://`, `httphttps://`), şemasız veya çözümlenemeyen bozuk adreslerin ExoPlayer'a ulaşıp oynatıcıyı kilitlenmesi engellendi. `isBrokenStreamUrl` filtresi Adım 0 olarak sanitizasyon hattına eklendi; bozuk linkler elenerek oynatıcının çökmesi ve sahte kaynak kartı gösterimi engellendi.

### 🧪 Kapsamlı Birim Testleri
- `CsEmbedOriginHeadersTest`: URL'den origin türetme, başlık ezmeme koruması, büyük/küçük harf duyarsızlığı, rapidrame/closeload/playmix gerçek başlık testleri eklendi.
- `CsStreamSanitizerTest`: Bozuk şema tespiti, `master.txt` gibi meşru manifestlerin filtrelenmemesi ve torrent kaynaklarının korunması doğrulandı.
- `PlayerHlsDetectionTest`: Canlı token ve parametre taşıyan gerçek dünya CDN URL'leri test setine eklendi.

### 🔌 Eklenti Yaması Hazırlandı (`KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`)
- `Kitsugi-Plugins` deposundaki HDFilmCehennemi (v54) ve FilmMakinesi (v64) için `linkTypeFor()` fonksiyonundaki `INFER_TYPE!!` NullPointerException hatasını gideren, `.ts` içeren meşru manifestlerin elenmesini önleyen eklenti yaması hazırlandı.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Playback & Stream Recovery Fixes
- **Embed Origin Header Propagation & 5004 Drop Prevention (`KN-B`):** Embedded streaming hosts such as `rapidrame`, `playmix.uno`, and `closeload` authenticate requests against the embed page's origin. Enhanced `CsStreamRunner` fallback mechanisms (media content check, `EmbedMediaScanner` deep DOM inspection, WebView media sniffer) and `verifyMediaCandidate` to populate the canonical embed origin (`originOfUrl`, `applyEmbedOriginHeaders`) without overwriting provider-specified token headers.
- **Corrupt URL & Double-Scheme Safeguard (`CsStreamSanitizer`):** Prevented malformed URLs produced by legacy addons (e.g. `httpshttp://`, `httpshttps://`, `httphttps://`, or missing schemes) from reaching ExoPlayer and crashing playback. Implemented Step 0 `isBrokenStreamUrl` validation to eliminate broken streams early.

### 🧪 Comprehensive Unit Tests
- `CsEmbedOriginHeadersTest`: Regression tests for canonical origin derivation, header preservation, case-insensitivity, and live rapidrame/closeload/playmix headers.
- `CsStreamSanitizerTest`: Verified corrupt URL detection, safe handling of `master.txt` manifests, and retention of schemeless torrent sources.
- `PlayerHlsDetectionTest`: Expanded test suite with real-world query tokens and dynamic CDN parameters.

### 🔌 Addon Fix Patch Prepared (`KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch`)
- Addon patch prepared for `Kitsugi-Plugins` (HDFilmCehennemi v54 & FilmMakinesi v64) fixing the silent `INFER_TYPE!!` NullPointerException in `linkTypeFor()` and preventing false drops of `.ts`-containing playlist URLs.
