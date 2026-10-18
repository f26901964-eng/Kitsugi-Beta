# Kitsugi Beta v2.4.305

## 🇹🇷 Türkçe

### 🎬 Close & Rapid Kaynakları İçin Yerleşik Çözücü (`KitsugiCdnJwExtractor`)
- **Yerleşik Close/Rapid Çözücüsü:** HDFilmCehennemi ve FilmMakinesi videolarının geldiği gömülü CDN ailesi (`closeload.*`, `rapidrame.*`, `rplayer.*`) için uygulama kendi yerleşik çözücüsünü kazandı. JWPlayer `dc_` JavaScript şifresi native olarak çözülüyor; JSON-LD `contentUrl` ve `master.txt`/`.m3u8` fallback'leri devrede.
- **En Yüksek Çözünürlüklü Varyant & Altyazı Ayıklama:** Master playlist listesinden en yüksek çözünürlüklü video varyantı otomatik seçilir; `Origin` ve `Referer` başlıkları GERÇEK yayın host'una sabitlenerek CDN tarafında yaşanan 5004 düşme hatası engellendi. JWPlayer `tracks:` bloğundaki altyazılar da ayıklanıp oynatıcıya sunuluyor.
- **FilmMakinesi "DUAL Rapid" Artık Link Üretiyor:** `rapidrame` iframe'leri eklenti tarafında `loadExtractor`'a bırakılıyor ama hiçbir eklenti bu host'u tanımıyordu; yerleşik çözücüler CloudStream `extractorApis` listesine kaydedildiği için eklenti sürümünden bağımsız olarak Rapid kaynakları da çözülüyor.

### 🔌 Bayat Eklenti Güncelleme Döngüsü Kırıldı & Canlılık Doğrulaması
- **Gerçek Eklenti Sürümleri ve Hash Senkronizasyonu:** `plugins.json` içindeki FilmMakinesi kaydı v62'ye, HDFilmCehennemi kaydı v52'ye (gerçek `.cs3` dosya boyutu ve SHA-256 değerleriyle) senkronlandı. Cihazlarda kalan eski sürümler artık güncelleme denetimine takılmadan doğrudan yeni sürümlere güncelleniyor.
- **HLS ve Progressive URL Ayrıştırma İyileştirmesi:** `PlayerMediaSourceFactory.isHlsUrl` sıralaması kesin manifest belirteçleri → progressive dosya uzantısı → zayıf yol/host belirteçleri olarak optimize edildi; CDN'lerin `/hls/` altındaki düz MP4/MKV dosyaları doğru MIME tipiyle oynatıcıya veriliyor.
- **Güçlendirilmiş Canlılık Doğrulaması:** HTTP HEAD canlılık kontrolü artık `Origin` dahil tüm başlıklarla yapılıyor; 404 dönen CDN'lerde kaynak hemen düşürülmeyip GET+Range ile ikinci şans verilerek çalışan kaynakların kaybolması engellendi.

### 🧪 Birim Testleri
- `KitsugiCdnJwExtractorTest`: Sentetik şifre çözme turu, varyant seçimi, altyazı filtreleme, origin/header üretimi test edildi.
- `PlayerHlsDetectionTest`: Yeni URL ayrıştırma sıralaması ve regresyon testleri genişletildi.

---

## 🇬🇧 English

### 🎬 Built-in Close & Rapid Extractors (`KitsugiCdnJwExtractor`)
- **Native Close/Rapid Extractor:** Added a dedicated in-app native extractor for embedded CDN domains (`closeload.*`, `rapidrame.*`, `rplayer.*`) serving HDFilmCehennemi and FilmMakinesi. Decrypts JWPlayer `dc_` JS payload natively, supporting JSON-LD `contentUrl` and `master.txt`/`.m3u8` fallbacks.
- **Adaptive Variant Selection & Header Pinning:** Automatically selects the highest-resolution stream from the master playlist and pins `Origin`/`Referer` headers to the actual streaming host (preventing CDN 5004 errors). Embedded JWPlayer `tracks:` subtitles are properly extracted.
- **FilmMakinesi "DUAL Rapid" Stream Resolution:** Rapidrame iframes are now resolved out-of-the-box via app-registered extractors in CloudStream `extractorApis`, regardless of plugin version.

### 🔌 Stale Plugin Update Loop Broken & Enhanced Liveness Checks
- **Accurate Hashes & Manifest Parity:** Synchronized `plugins.json` manifests to actual served upstream builds (FilmMakinesi v62, HDFilmCehennemi v52 with true size and SHA-256 digests), allowing devices on legacy builds to update seamlessly.
- **Refined Progressive / HLS URL Matching:** Strict detection hierarchy ensures progressive MP4/MKV files under `/hls/` paths are not falsely tagged as HLS streams.
- **Resilient Stream Probing:** Probing requests now transmit full header sets including `Origin`; sources returning 404 on HEAD are verified via GET+Range before being dropped.

### 🧪 Unit Tests
- `KitsugiCdnJwExtractorTest`: Tested decryption cycles, variant selection, subtitle extraction, and header generation.
- `PlayerHlsDetectionTest`: Extended regression coverage for URL detection rules.
