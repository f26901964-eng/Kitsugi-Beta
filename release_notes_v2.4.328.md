# Kitsugi Beta v2.4.328

## 🇹🇷 Türkçe

### 🛡️ Kapsamlı Platform Hata Kodları Sistemi (KTS-SRC) & Manga / Simkl Entegrasyonu
- **Tüm Liste Sağlayıcılarında Standart Hata Kodları (`PlatformFailureRegistry`):**
  - 7 ana liste platformu (AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl) için tüm hata durumları tek merkezde sınıflandırıldı ve `PlatformFailureRegistry` kayıt defterine bağlandı.
  - Profil, Kullanıcı Listesi, Bildirimler, Arama, Keşfet (Explore), Yayın Takvimi, Detay ekranları (Anime, Bölümler, Karakterler, Personel, Stüdyo), TV arayüzü ve OAuth kimlik doğrulama akışlarının tamamına şık `PlatformErrorCodeChip` hata rozetleri eklendi.
- **Manga Ekosistemi Kaynak Hata Kodları:**
  - Manga ana sayfası, manga detay, kaynak tarayıcısı ve okuyucu ekranlarında kaynak hataları artık ilgili `KTS-SRC` hata kodlarıyla görüntüleniyor.
  - Kaynak tarayıcısında hata veren kaynağın çipinin yanında doğrudan ilgili hata kodu sunuluyor.
- **Simkl Kimlik Doğrulama Ağ Entegrasyonu (`SimklAuthManager`):**
  - `SimklAuthManager`, uygulamanın ortak HTTP istemcisine (`KitsugiHttpClient`) bağlanarak tüm HTTP ve ağ hatalarının platform hata defterine eksiksiz kaydedilmesi sağlandı.

---

## 🇬🇧 English

### 🛡️ Unified Platform Error Codes System (KTS-SRC) & Manga / Simkl Integration
- **Universal Error Codes Across All 7 Providers (`PlatformFailureRegistry`):**
  - Standardized error classification across AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, and Simkl via `PlatformFailureRegistry`.
  - Added dedicated `PlatformErrorCodeChip` widgets to Profiles, User Media Lists, Notifications, Search, Explore, Airing Calendar, Detail subpages (Episodes, Characters, Staff, Studios), TV UI, and OAuth login flows.
- **Manga Source Error Codes:**
  - Integrated `KTS-SRC` error reporting into Manga Home, Manga Detail, Browse, and Chapter Reader views.
  - In the manga source browser, failing sources surface their designated error codes right next to their status chips.
- **Simkl Auth Shared Client Integration (`SimklAuthManager`):**
  - Routed Simkl authentication through the shared `KitsugiHttpClient`, enabling automatic capture of HTTP/network exceptions into the centralized platform registry.
