# Kitsugi-Beta v2.4.264 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🔔 MyAnimeList Bildirimleri: Gerçek Medya Adı, Doğru Detay ve Sonsuz Kaydırma
- **Detay Sayfası Artık Doğru Medyayla Açılıyor:** Bildirim kartının başlığı MAL'ın kategori adıydı (`categoryName` = "New Related Anime") ve tıklamada bu metin detay sayfasına başlık olarak veriliyordu; `ApiResultDetailPage` çağıranın başlığını API başlığına tercih ettiği için ekranda kategori adı kalıyordu. Yönlendirme artık medyanın gerçek adı (`mediaTitle`) ve MAL kimliği ile yapılıyor. Kimlik yoksa ad üzerinden `searchMALOnly` ile çözülüp açılıyor.
- **Daha Okunur Kartlar:** Medya bildirimlerinde başlık artık medyanın adı yapıldı; MAL kategori adı ve format türü ikinci satıra alındı (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Tür Karışması Düzeltildi:** Novel, Light Novel, Manhwa, Manhua, One-shot, Doujin kayıtları artık manga evreninde doğru sınıflandırılıyor; manga kimliğinin anime aramasına gidip yanlış yapımı açması engellendi. Tür payload'da yoksa URL'deki `/manga/` yolundan tespit ediliyor.
- **Sonsuz Kaydırma & Geçmiş:** `historyItems` altındaki okunmuş/eski bildirimler `items` ile birleştirilip id ile tekilleştiriliyor. Liste sonuna geldikçe akışın sonraki sayfası deneniyor (`?page=N`); yeni kayıt gelmezse akış temiz bir şekilde sonlanıyor. MAL web akışı yerel arşive de kaydediliyor.
- **Hızlı Yükleme Bildirimi:** Arama sürerken kısa bir "Detay sayfası hazırlanıyor…" göstergesi eklendi.

---

## 🇬🇧 English

### 🔔 MyAnimeList Notifications: Accurate Detail Routing, Media Title & Infinite Scroll
- **Accurate Media Navigation:** Notification cards previously used MAL category titles (`categoryName` = "New Related Anime") as the detail title. Navigation now passes the true `mediaTitle` and MAL ID. When ID is missing, it dynamically resolves via `searchMALOnly`.
- **Readable Card Layout:** Card headers now display the real media title, moving category and format info to the secondary line (`Umayuru: Full Gate!` / `New Related Anime • ONA`).
- **Media Type Disambiguation:** Novel, Light Novel, Manhwa, Manhua, One-shot, and Doujin items are strictly routed to the manga catalog, preventing anime ID collisions. Fallback detection inspects `/manga/` in the URL.
- **Infinite Scrolling & Deduplication:** Merges and deduplicates read notifications from `historyItems` with live `items`. Supports pagination (`?page=N`) and persists the stream to local archive.
- **Loading Feedback:** Added a clean preparation indicator while resolving media details.
