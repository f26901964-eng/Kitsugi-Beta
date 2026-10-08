# Kitsugi-Beta v2.4.206 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎭 Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları & AniList Eşleme:** TMDB ve diğer kaynaklardan gelen içeriklerde karakter görsellerini doldururken ekran başlığı yerine tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca ve eşanlamlılar) taranır; karakter adıyla eşleşen doğru yapım tespit edilir.
- **Eksik Kalan Görseller İçin Karakter Araması:** Yapım düzeyinde eşleşmeyen karakterler için AniList karakter araması yedeği eklendi. Kimlikler `realMalId` ile çapraz doğrulanarak alakasız karakterlerin karta takılması önlenir.
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Gerçek kişi/oyuncu kadrolarında eşleşme bulunamadığında mevcut oyuncu fotoğrafı silinmez, korunur.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter detay sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana yerleştirilir; detay verisi yüklenirken sayfa asla resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır; TMDB kişi ID'si ile yapılan ve 404 dönen gereksiz ağ çağrıları sonlandırıldı.

### ⚡ Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori içeriklerinde `/similar` ve `/related` uç noktaları kullanılarak ilişkiler ve öneriler tek ağ isteğiyle saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si çözülerek Jikan personel listesi devreye alınır.
- **Sonsuz Skeleton Koruması (Zaman Aşımı & Tek-Uçuş):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı eklendi. Ağ veya API takılmalarında sekmeler kilitli kalmaz, hata durumuna geçerek yeniden deneme imkânı sunar.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; Fanart, MDBList ve bölüm listesi için gerçek MAL ID'si önbellekli olarak çözülür.

### 🎬 Detay Sayfası Tasarım & Kullanılabilirlik İyileştirmeleri
- **Kompakt İzle Butonu:** Detay sayfasında ve sol panelde tüm genişliği kaplayan devasa "İzle" butonu yerine, ikon ve metin genişliğine göre uyum sağlayan kompakt ve modern bir buton tasarlandı.
- **Canlı Çekim Dizi/Film Karakter Profilleri (`isRealMediaRole`):** TMDB ve Simkl canlı çekim dizi/filmlerinde karakter kartlarına dokunulduğunda anime arama zinciri yerine doğrudan TMDB kişi/karakter profili açılır.
- **"Bilgiler" Kartında Tekil Kopyalama Butonları:** Orijinal Başlık, İngilizce Başlık, Durum, Sezon, Bölüm Süresi vb. tüm satırlara ve çoklu değerlere (Diğer Adlar, Stüdyo) minik kopyalama butonları eklendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English

### 🎭 Character Images & Cast Photo Reliability
- **Multi-Title Candidates & AniList Matching:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms) to ensure accurate anime matching.
- **Direct Character Search Fallback:** Missing character portraits are resolved via AniList character search with strict `realMalId` verification, preventing mismatched portraits.
- **Preserved Cast Photos in Live-Action:** Fixed an issue where actor photos in live-action cast lists were discarded if no fictional character was found.
- **Instant Hint Image for Detail Pages:** Character detail pages immediately utilize the card image as a hint, preventing blank pages while fetching metadata.
- **Targeted Jikan /pictures Queries:** Restricted `/pictures` calls strictly to MAL IDs, eliminating 404 errors from TMDB IDs.

### ⚡ Shikimori Tabs & Speed Enhancements
- **Native Relations & Recommendations:** Leveraged Shikimori `/similar` and `/related` endpoints with batch poster resolution for near-instant shelf loading.
- **Staff Shelf Fallback:** If Shikimori lacks staff credits, automatically falls back to Jikan staff data.
- **Infinite Skeleton Prevention (Timeout & Single-Flight):** Added 25s timeouts (45s for episodes) and request-key deduplication to prevent endless loading skeletons.
- **ID Resolution Safety:** Prevents Shikimori IDs from being misinterpreted as MAL IDs across Fanart, MDBList, and ratings.

### 🎬 Media Detail UI & Usability Polish
- **Compact Watch Button:** Replaced full-width "Watch" buttons with sleek, content-hugging compact buttons.
- **Direct Live-Action Character Profiles (`isRealMediaRole`):** Clicking actors/characters in TMDB/Simkl live-action titles directly navigates to their TMDB person profile instead of anime lookup.
- **Individual Copy Buttons in Info Card:** Added quick-copy buttons for every metadata row, including multi-value tags (Synonyms, Studios).

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.
