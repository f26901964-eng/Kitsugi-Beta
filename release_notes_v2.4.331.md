# Kitsugi Beta v2.4.331

## 🇹🇷 Türkçe

### 🎬 Detay Sayfası Video Oynatıcı & YouTube/Tema Akış Kök Neden Onarımı
- **İstek Başlığı Ortaklaştırması (`YouTubeRequestPolicy.kt`):**
  - Varsayılan MPV motoru dahil küçük oynatıcı ve tam ekran oynatma rotalarına YouTube ve doğrudan video istek başlıkları (`Referer`, `Origin`, `User-Agent`) aktarılıyor. Başlıksız istek kaynaklı 403 ve oynatma reddi engellendi.
- **Media3 YouTube Tek & Çift Akış Ortak Data Source:**
  - Media3'ün tek URL'li (HLS/progressive) akışları da özel factory'ye bağlandı (`YoutubeChunkedDataSourceFactory.kt`). HLS manifest ve segmentlerinde range query chunking devre dışı bırakılırken progressive akışlarda korundu.
- **YouTube HLS Manifest Desteği Genişletildi (`YouTubeHlsManifestParser.kt`):**
  - Yalnızca master playlist değil, doğrudan media playlist, UTF-8 BOM ve göreli varyant URL'leri destekleniyor. Eşit kalite/çözünürlük durumunda genişlik adayları karşılaştırılarak en uygun akış seçiliyor.
- **Fragman Overlay Düzeltmesi (`SharedTrailerOverlay.kt`):**
  - Tek URL'li akışlarda özel factory üzerinden `MediaSource` kuruluyor ve YouTube HLS adreslerine açık HLS MIME türü atanıyor.
- **Zaman Aşımı & Otomatik Motor Yedekleme (Fallback):**
  - Küçük oynatıcılar `READY`/`ENDED` durumunu bekliyor; 18 saniyede veya oynatma hatasında diğer dahili motora (MPV ↔ Media3) bir kez otomatik geçiş yapıyor. İkinci deneme de başarısız olursa "Yeniden Dene" ve "Tarayıcıda Aç" seçenekleri sunuluyor.
- **Birim Testleri:**
  - `PlayerHlsDetectionTest`, `YouTubeHlsManifestParserTest`, `YouTubeRequestPolicyTest` ve `InlinePlayerSupportTest` ile akış tespiti, başlık politikası ve fallback zinciri doğrulandı.

---

## 🇬🇧 English

### 🎬 Detail Video Player & YouTube/Theme Stream Root Cause Fix
- **Unified Request Header Policy (`YouTubeRequestPolicy.kt`):**
  - Small player routes (including default MPV and full-screen playback) now forward authentic YouTube and direct video request headers (`Referer`, `Origin`, `User-Agent`), resolving headerless 403 playback failures.
- **Media3 YouTube Single & Dual Stream Custom Data Source:**
  - Single-URL YouTube HLS/progressive streams now route through `YoutubeChunkedDataSourceFactory.kt`. Range-chunking is disabled for HLS manifests/segments while preserved for progressive streams.
- **Expanded YouTube HLS Manifest Parser (`YouTubeHlsManifestParser.kt`):**
  - Full support for master playlists, direct media playlists, UTF-8 BOMs, and relative variant URLs. Ties in candidate resolutions are properly resolved by width.
- **Trailer Overlay Stream Alignment (`SharedTrailerOverlay.kt`):**
  - Single-URL trailers construct `MediaSource` through the specialized factory and specify explicit HLS MIME types.
- **Timeout & Automatic Engine Fallback (`InlinePlayerSupport.kt`):**
  - The small inline player tracks playback readiness. If unready after 18s or upon a playback error, it automatically fails over once between internal engines (MPV ↔ Media3). If both fail, actionable "Retry" and "Open in Browser" options appear.
- **Comprehensive Unit Tests:**
  - Added test suites for HLS detection, request policies, manifest parsing, and inline player fallback behavior.
