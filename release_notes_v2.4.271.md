# Kitsugi-Beta v2.4.271 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.271)

### 📚 Fandom Wiki Entegrasyonu (Faz 1 — Karakter Detayı & Ayarlar)
- **Karakter Sayfasında Wiki Sekmesi:** Karakter detay ekranına Fandom Wiki sekmesi eklendi. Biyografi, görünüş, kişilik, yetenekler ve ilişkiler gibi zengin ansiklopedik bilgiler artık doğrudan karakter sayfasında.
- **Güvenilir Kimlik Eşleştirme (Wikidata Köprüsü):** Eşleştirme rastgele isim araması yerine öncelikle Wikidata ID'leri üzerinden yapılır. Doğrulanamayan kayıtlar gösterilmez.
- **Doğrulama & Bildirim Kartları:** "Bu eşleşme doğru mu? (Evet / Hayır)" onay kartı eklendi. "Yanlışsa bildir" tıklandığında hatalı wiki kaydı kalıcı olarak engellenir ve sıradaki aday denenir.
- **Spoiler Koruması & Türkçe Önceliği:** Karakterin durumu ve hikâye bölümleri istek üzerine spoiler örtüsüyle gizlenir; dokunulduğunda açılır. Türkçe fandom sayfası mevcutsa öncelikli olarak gösterilir.
- **TV & Mobil Ayar Yönetimi:** Hem mobil hem Android TV Ayarlar menüsüne Fandom Wiki kontrol paneli ve tek tıkla önbellek temizleme seçeneği eklendi.

### 🎬 Detay Sayfalarında "Videolar" Sekmesi & Hızlı Oynatıcı
- **Ayrı Videolar Sekmesi:** Fragmanlar, açılış (OP) ve kapanış (ED) müzik videoları "Bilgi" sekmesinden ayrılarak "Resimler" sekmesinin yanındaki yeni "Videolar" sekmesine taşındı.
- **Kesintisiz Oynatıcı Yönetimi:** Videolar arasında hızlı geçiş yapıldığında eski oynatıcı ve devam eden YouTube istekleri anında temizlenir; oynatıcının donması veya çakışması önlenir.

### 🏢 MAL Manga Stüdyo, Yapımcı & Yazar Düzeltmeleri
- **Doğru Rol Eşlemesi:** Manga yazarları (`Kenji Inoue` vb.) yanlışlıkla stüdyo/yapımcı listesinde yer almak yerine artık doğru kişi (Staff) sayfasına yönlenir.
- **Dergi Filtreli Arama:** Manga dergileri (`good! Afternoon` vb.) Jikan `magazines={id}` filtresiyle o dergiye ait tüm mangaları listeler.
- **Yayım Durumu Çevirisi:** MAL manga sayfalarındaki `currently_publishing` ham değeri "Yayımlanıyor" olarak Türkçeleştirildi.

### 🎴 Neon Kart Çerçeveleri & Vitrin İyileştirmesi
- **Opsiyonel Kart Çerçeveleri:** Ayarlar'dan açılabilen neon degrade çerçeveler Keşfet vitrinine, takvim kartlarına ve kaynak kutularına bağlandı.
- **Akıcı Vitrin:** Ana sayfa vitrininde yükseklik sıçramaları ve kaydırma sarsıntıları giderildi.

---

## 🇬🇧 English (v2.4.271)

### 📚 Fandom Wiki Integration (Phase 1 — Character Detail & Settings)
- **Wiki Tab on Character Pages:** Introduced a dedicated Fandom Wiki tab for character details, providing in-depth infoboxes, biographies, appearances, abilities, personality, and relationships.
- **Verified Identity Matching (Wikidata Bridge):** Matches rely primarily on Wikidata IDs rather than ambiguous name searches, ensuring accurate fandom resolution.
- **Verification & Feedback Cards:** Added "Is this match correct? (Yes / No)" cards. Tapping "Report wrong match" permanently disassociates the incorrect wiki and advances to the next candidate.
- **Spoiler Guard & Turkish Priority:** Character status and story plot sections can be kept hidden behind a spoiler barrier until tapped. Prefers Turkish wikis when localized content is available.
- **TV & Mobile Settings Integration:** Comprehensive Fandom Wiki toggle, spoiler controls, and cache wipe action added to both Mobile and Android TV Settings.

### 🎬 Dedicated "Videos" Tab & Rapid Player Switching
- **Separated Videos Tab:** Trailers, opening themes (OP), and ending themes (ED) have moved from Overview into a new "Videos" tab adjacent to Gallery.
- **Seamless Player Transitions:** Rapid switching between videos cleanly terminates existing playback and pending YouTube network requests, preventing lockups and overlapping audio.

### 🏢 MAL Manga Studio, Producer & Author Resolution Fixes
- **Accurate Role Mapping:** Manga authors are properly attributed with the `AUTHOR` role and link directly to person (Staff) detail pages.
- **Magazine Facet Search:** Manga serialization chips redirect to filtered searches targeting all manga published under that magazine (`magazines={id}`).
- **Publishing Status Localization:** Raw MAL status string `currently_publishing` is now properly localized.

### 🎴 Neon Card Frames & Showcase Polish
- **Neon Glow Frames:** Optional neon border modifiers integrated across Explore hero cards, calendar items, and source tiles.
- **Smooth Showcase:** Stabilized Explore hero dimensions to eliminate layout shifts and stutter during scrolling.
