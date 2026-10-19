# Kitsugi Beta v2.4.308

## 🇹🇷 Türkçe

### 🎬 HDFilmCehennemi & FilmMakinesi Oynatıcı Hatası Kök Çözümü (KN-E & KN-U)
- **Embed Origin Başlığı Düzeltmesi (`KN-U`):** `PlayerMediaSourceFactory` ve `CsStreamRunner` oynatıcıya akış hazırlarken `Origin` başlığını ya boş bırakıyor ya da video host'u (`playmix.uno`, `s427.rapidrame.com`) ile dolduruyordu. Bu CDN'ler `Origin`'i embed sayfasının origin'ine (`closeload.filmmakinesi.to`, `rapidrame.com`) göre doğruladığı için CDN tarafında 403/5004 hatası oluşuyor ve ExoPlayer `ParserException` fırlatarak oynatıcı hatasına düşüyordu. `PlayerMediaSourceFactory` artık `Origin`'i `Referer` başlığının origin'inden (`originOfUrl`) türetir; video host yalnızca Referer bulunamadığında son çare olarak kullanılır.
- **Akış Koşucusu Başlık Koruması (`CsStreamRunner`):** Hem doğrudan medya akışında hem de fallback kollarında `Origin` başlığı eksikse otomatik olarak `Referer` veya kaynak URL'sinin origin'i ile tamamlanır. Eklentinin sağladığı özel başlıklar asla ezilmez.
- **Canlı Eklenti Manifest Senkronizasyonu (`KN-E`):** Gömülü `assets/plugins/plugins.json` dosyası canlı `builds` dalındaki en güncel derlemelerle senkronize edildi: FilmMakinesi v66 (`47441 B`, `sha256-0da3db3d...`) ve HDFilmCehennemi v56 (`72972 B`, `sha256-62d1abaf...`). Çevrimdışı ve ilk kurulumlarda cihaz doğrudan güncel `.cs3` eklentileriyle başlar.

---

## 🇬🇧 English

### 🎬 HDFilmCehennemi & FilmMakinesi Player Error Root Fix (KN-E & KN-U)
- **Accurate Embed Origin Derivation (`KN-U`):** `PlayerMediaSourceFactory` and `CsStreamRunner` previously omitted `Origin` or defaulted it to the stream's video host (`playmix.uno`, `s427.rapidrame.com`). Regional CDN clusters validate requests strictly against the embed origin (`closeload.filmmakinesi.to`, `rapidrame.com`); improper Origin caused HTTP 403/5004 responses, prompting ExoPlayer `ParserException` errors. `PlayerMediaSourceFactory` now reliably extracts `Origin` from the `Referer` header's origin via `originOfUrl()`, utilizing video host solely as an ultimate fallback.
- **Header Enrichment in Stream Runner (`CsStreamRunner`):** Both direct-playback and fallback execution paths in `CsStreamRunner` automatically populate missing `Origin` headers from the Referer or sanitized URL, preserving provider-specified tokens.
- **Addon Manifest Parity (`KN-E`):** Synchronized embedded `assets/plugins/plugins.json` with live upstream `builds`: FilmMakinesi v66 (`47441 B`, `sha256-0da3db3d...`) and HDFilmCehennemi v56 (`72972 B`, `sha256-62d1abaf...`). Fresh or offline installs immediately operate with the latest patched addons.
