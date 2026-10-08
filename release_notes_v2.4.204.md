## 🔔 Bildirimler Ekranı — Açılır Panel (Bottom Sheet) & Seçici Hap Buton
- **Yatay Sekmeler Barı Kaldırıldı:** Bildirimler ekranındaki yatay platform sekmeleri çubuğu tamamen kaldırıldı, ekran alanı genişletildi.
- **Seçici Hap Buton (`NotificationSourceSelectorPill`):** Listem sayfasındaki "Tümü ▾" hap butonu stiline uygun olarak; seçili platformun logosu, platform adı, bağlantı durumu göstergesi (bağlıysa yeşil nokta) ve AniList okunmamış bildirim rozetini içeren modern hap buton eklendi.
- **Açılır Panel (`NotificationSourcePickerSheet`):** Butona tıklandığında açılan alt panel ile 5 platform (AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori) arasında geçiş yapılabilir hale getirildi. Her platformun orijinal logosu, kullanıcı adı/bağlantı durumu rozetleri ve bildirim kapsamı açıklamaları eklendi.

## 📚 Kitsu Manga Logo ve Görsel İzolasyonu
- **Manga ID Çakışması Engellendi:** Kitsu'da manga ve anime ID havuzları bağımsız olduğundan, manga içeriklerinde ARM API üzerinden yapılan anime sorguları ve TMDB/Fanart logo/galeri aramaları izole edildi.
- **Yanlış Anime Logoları ve Resimleri Temizlendi:** Manga içeriklerine TMDB/Fanart'tan tamamen alakasız anime afiş ve logolarının (örn. Berserk mangasında Hungry Heart anime verilerinin) gelmesi engellendi.
- **Kitsu Orijinal Görselleri Entegre Edildi:** Kitsu manga detaylarında orijinal `coverImage` (banner/arka plan) ve yüksek çözünürlüklü kapak görselleri (`pictures`) veri modeline dahil edilerek doğru afiş ve arka planlar yüklendi.

---

## 🔔 Notifications Screen — Bottom Sheet Source Picker & UI Modernization
- **Removed Horizontal Tabs Bar:** Cleaned up notifications interface by replacing the rigid horizontal platform bar with a sleek selector pill.
- **Platform Selector Pill (`NotificationSourceSelectorPill`):** Features the active platform's authentic logo, title, connection status (green dot), and unread badge count (for AniList).
- **Source Picker Bottom Sheet (`NotificationSourcePickerSheet`):** Smooth modal sheet matching the My List filter sheet. Easily switch between 5 supported platforms (AniList, MyAnimeList, TMDB & Simkl, Kitsu, Shikimori) with account handles and status indicators.

## 📚 Kitsu Manga Logo & Art Isolation Fix
- **Prevented Manga-Anime ID Collisions:** Because Kitsu maintains independent ID spaces for anime and manga, ARM API lookups and TMDB/Fanart queries are now bypassed for Manga media types.
- **Cleaned Hero and Gallery Views:** Eliminated incorrect anime logos and backdrops (e.g. Berserk manga receiving Hungry Heart anime art).
- **Native Kitsu Artwork Support:** Extracted high-resolution `coverImage` (banner) and `pictures` from Kitsu manga responses directly into the media model for proper rendering.
