# Kitsugi-Beta v2.4.244 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌐 1. Birleşik Logo Seçim Politikası ve Dil / Sezon Önceliği
- **Ortak Dil ve Eşleşme Politikası (`LogoSelectionPolicy.kt`):** TMDB ve Fanart.tv logo adayları artık tek ve tutarlı bir dil sıralama politikasından geçirilir. Öncelik sırasıyla: seçili uygulama dili → İngilizce → romanize/metinsiz görseller → Japonca/Çince.
- **Sezon Bazlı Logo Uyumu:** Dizi logolarında, aynı dil kademesi içerisinde o an izlenen sezon ile tam eşleşen logo adayları önceliklendirilir.
- **Afiş/Logo İzolasyonu:** Film ve dizi logo sorguları kesin sınırlarla ayrıldı; sezon afişlerinin (posters) logo zannedilip seçilmesi engellendi.
- **Önbellek & Galeri Düzenlemesi:** Galeri sıralamaları ve görsel önbellekleri (`DetailCache.kt`, `GalleryItem.kt`) seçili dil ve sezon bağlamını koruyacak şekilde güncellendi.

### ⚡ 2. P2P Torrent Önbellek Tahliyesi & Otomatik Disk Temizliği
- **Korunan Torrent Tahliyesi Fix (`P2pStreamingEngine.kt`):** `libnuvio_engine` motoru korunan torrent verisini tahliye etmediği için biriken disk boyutu sınırı aşıyordu ("disk cache reclaim limited by protected torrent data"). Oynatıcı kapatıldığında veya akış durduğunda torrent motordan `removeTorrent(id)` ile siliniyor ve `reclaimDiskCache(0L)` ile önbellek anında sıfırlanıyor.
- **Bağlantı Hatası Temizliği:** Akış bağlanamadan hata aldığında eklenen torrent nesnesi de motordan kaldırılarak diskte artık kalması önlendi.
- **Açılışta Artık Süpürme:** Uygulama açılışında, motor boşta ve aktif akış yokken önceki oturumlardan kalan artık payload dosyaları otomatik olarak temizlenir.
- **Önbellek Yönetim Anahtarı (`P2pSettingsSection.kt`):** P2P Depolama ayarları altına *"Video kapanınca önbelleği temizle"* seçeneği eklendi.

### 🏢 3. Bangumi Kurum/Yapımcı Arama Dayanıklılığı & Negatif Önbellek Koruması
- **Genişletilmiş Arama Limiti (`KitsugiBangumiDetailClient.kt`):** Kurum arama limiti 5'ten 10'a yükseltildi, aynı ada sahip birden çok kayıt olduğunda doğru kurumun bulunması sağlandı.
- **Geçici Hata Koruması (Negatif Önbellek Koruması):** Ağ veya zaman aşımı hataları artık "bulunamadı (0)" olarak önbelleğe yazılmaz; geçici hatalar atlatıldığında kurum sayfaları sorunsuz açılır. Bulunamayan geçerli sonuçlar için 10 dakikalık TTL önbelleği (`STUDIO_MISS_TTL_MS`) uygulandı.

### 🔄 4. Profil Hata Ekranı 'Yeniden Dene' Desteği
- **Hızlı Yeniden Deneme (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Profil hata ekranlarına doğrudan tetiklenebilir "Yeniden Dene" butonu eklendi.
- **Hız Sınırı Mesajı:** AniList HTTP 429 hız aşımı yanıtları için net ve anlaşılır uyarı mesajı eklendi.

---

## 🇬🇧 English

### 🌐 1. Unified Logo Selection Policy & Language / Season Priority
- **Shared Language Ranking (`LogoSelectionPolicy.kt`):** TMDB and Fanart.tv candidate logos now run through a unified language ranking algorithm: User's selected language → English → Romanized/Textless artwork → Japanese/Chinese.
- **Season-Aware Matching:** For series logos, season-specific artwork matching the current season is preferred within the same language tier.
- **Poster/Logo Isolation:** Strict endpoint and role separation ensures season posters are never mistakenly picked as title logos.
- **Cache & Gallery Sync:** Image gallery ordering and detail caches (`DetailCache.kt`, `GalleryItem.kt`) now take language and season context into account.

### ⚡ 2. P2P Torrent Cache Eviction & Automatic Disk Cleanup
- **Protected Torrent Eviction Fix (`P2pStreamingEngine.kt`):** Addressed root cause where `libnuvio_engine` refused to reclaim disk cache due to active torrent protection ("disk cache reclaim limited by protected torrent data"). Stopping a stream or closing the player now disposes the torrent via `removeTorrent(id)` and calls `reclaimDiskCache(0L)`.
- **Startup & Failure Cleanup:** Any torrent created during a failed connection attempt is proactively unmounted. Leftover cache payloads from previous sessions are swept on application launch.
- **Toggleable Cache Policy (`P2pSettingsSection.kt`):** Added a "Clear cache when video closes" toggle in P2P settings.

### 🏢 3. Bangumi Studio/Person Search Robustness & Cache Safety
- **Expanded Search Limit (`KitsugiBangumiDetailClient.kt`):** Increased studio search limit from 5 to 10 candidates to resolve homonym records accurately.
- **Transient Error Resilience:** Network glitches and timeouts are no longer cached as permanent misses (0 ID), preventing studio pages from staying blocked throughout the session. Legitimate misses use a 10-minute TTL.

### 🔄 4. Profile Error Screen 'Retry' Action
- **Interactive Retry (`KitsugiProfileScreen.kt` / `ProfileSharedComponents.kt`):** Added an explicit Retry button on profile error cards to quickly re-fetch state without navigating away.
- **Rate Limit Messages:** Refined AniList HTTP 429 explanations with dedicated bilingual catalog strings.
