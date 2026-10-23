# Kitsugi Beta v2.4.343

## 🇹🇷 Türkçe

### 🎵 Detay Sayfası Açılış / Kapanış (OP/ED) Müzikleri Küçük Oynatıcı & Açılma Animasyonu
- **Uygulama İçi Küçük Oynatıcı Deneyimi:** Açılış (OP) ve kapanış (ED) satırlarına dokunulduğunda doğrudan video bağlantısı olmasa bile harici YouTube aramasına çıkılmaz. Video arka planda çözülerek doğrudan fragmandaki gibi satır içi küçük oynatıcıda oynatılır.
- **Arka Planda Video Çözümleme (`InAppYouTubeExtractor`):** Bağlantısız müzik etiketleri için uygulama içi YouTube araması yapılarak ilk gerçek video kimliği (`videoRenderer`) çözülür ve bellek önbelleğinde saklanır.
- **Bağımsız Arama Ayrıştırıcısı (`YouTubeSearchResultParser`):** Kanal veya oynatma listesi sonuçlarını atlayıp doğrudan oynatılabilir video kimliğini ayıklayan, süslü parantez ve kaçışlı metinlere dayanıklı hafif ayrıştırıcı eklendi.
- **Ortak `KitsugiDetailReveal` Açılma Animasyonu:** Oynatıcı, ilgili satırın altında fragmandaki gibi aşağıya doğru yumuşak animasyonla açılır.
- **Anında Oynatıcı Temizliği:** Başka bir müziğe geçişte veya kapatmada eski oynatıcı motoru anında kaldırılır; iki oynatıcının aynı anda bellekte kalması veya ses çakışması yaşanması engellenir.
- **Hata Durumu ve Yedek Seçenek:** Çözümleme başarısız olursa küçük oynatıcı alanı içinde "Yeniden Dene" ve yalnızca isteğe bağlı yedek seçenek olarak "YouTube'da ara" eylemleri gösterilir.
- **Birim Testleri:** `YouTubeSearchResultParserTest` ile video ayıklama, kanal/playlist eleme ve eksik veri durumları doğrulandı.

---

## 🇬🇧 English

### 🎵 Detail Screen Opening / Ending (OP/ED) In-App Mini Player & Reveal Animation
- **Seamless Inline Playback:** Tapping Opening (OP) or Ending (ED) theme rows no longer redirects to an external YouTube search when a direct video link is missing. The video is resolved in the background and played directly inside the inline mini-player.
- **Background Video Resolution (`InAppYouTubeExtractor`):** Unlinked theme labels resolve to playable video IDs via in-app YouTube queries and are cached in bounded memory.
- **Robust Search Parser (`YouTubeSearchResultParser`):** Extracts authentic playable `videoRenderer` results while skipping channels and playlists, resilient against nested braces and escaped quotes.
- **Unified `KitsugiDetailReveal` Animation:** The player expands smoothly downwards below the selected row using the exact same reveal transition as trailers.
- **Immediate Engine Disposal:** Switching rows or collapsing the player immediately tears down the previous player instance to eliminate duplicate audio and heavyweight leaks.
- **Inline Retry & Fallback:** Displays inline "Retry" and fallback "Search on YouTube" buttons if resolution fails.
- **Unit Tests:** Verified via `YouTubeSearchResultParserTest` (renderer selection, playlist filtering, and malformed data handling).
