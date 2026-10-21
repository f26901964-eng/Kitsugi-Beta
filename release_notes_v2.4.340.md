# Kitsugi Beta v2.4.340

## 🇹🇷 Türkçe

### ⚡ Bangumi Performansı — "Veri 5 Dakika Gelmiyor" Kök Düzeltmesi
- **4,5 dk'lık gecikmenin kaynağı çözüldü:** Bangumi isteği başına 450 ms rezervasyon yapan eski hız sınırlayıcı saati ileriye taşıyordu. Yerine **5 istek/sn + 5 patlama** jeton kovası (token bucket) getirildi; iptal edilen istekler kredi tüketmez.
- **Fail-fast Bangumi HTTP istemcisi:** 10/12/12 sn + 14 sn toplam tavan, gereksiz yeniden denemeler kaldırıldı.
- **İstek seli sonlandırıldı:** Keşfet, arama şeritleri ve kütüphane artık liste modunda çalışır; yüzlerce tekil istek yerine kalıcı önbellek ve tek AniList toplu araması kullanılır.
- **p1 (`next.bgm.tv`) dayanıklılığı:** Tek uçuş (single-flight) paylaşımı, 8 sn tavan, 60 sn geri çekilme ve 4 ağ hatasında devre kesici.

### 🌐 Dil Seçeneği Artık Her Yerde Uygulanıyor
- **Sekmelerde aşamalı boyama:** Karakterler, Ekip, Öneriler ve İlişkiler sekmeleri ham veriyi anında gösterir; Latin isim ve başlıklar arka planda çözülerek dinamik olarak güncellenir.
- **Önbellekten açılan sekmeler de zenginleştirilir:** Önbellekteki Japonca/kanji listeler sekmeye her girişte Latin karşılıklarıyla tamamlanır.
- **Karakter ve kişi adları kalıcı önbellekte:** `bangumi_entity_names_v1` deposu ile çözülen adlar diske kaydedilir; oturumlar ve yapımlar arasında tekrar ağ isteği yapılmaz.
- **Karakter/kişi adı çözümü yarış modunda:** v0 API ve p1 eşzamanlı sorgulanır; ilk yanıt veren kullanılır, yavaş kalan iptal edilir.
- **getIdentifier fırtınası bitti:** ~40.000 `bangumi_*` kaynak dizesi ve görev etiketleri önbellekli taranır; `Locale.ROOT` ile Türkçe `I/ı` uyumsuzlukları giderildi.

### 🎭 TMDB Karakter Portreleri & Biyografi Çevirileri
- TMDB kredilerinde anime karakterleri için doğru görsel ataması sağlandı; seslendirme sanatçısı görselinin karakter portresi sanılması önlendi.
- Biyografi ve HTML içerik çevirilerinde güvenli ayrıştırma ve otomatik dil uyarlaması pekiştirildi.

---

## 🇬🇧 English

### ⚡ Bangumi Performance — Root Cause Fix for Delays
- **4.5-minute delay eliminated:** Replaced the legacy reservation limiter with a **5 req/s + 5 burst** token bucket; cancelled requests no longer drain credit.
- **Fail-fast Bangumi HTTP client:** 10/12/12 s + 14 s call timeout with zero redundant retries.
- **Request storm eliminated:** Explore, search rails, and library hydration operate in list mode with persistent caching and batched AniList lookups.
- **p1 (`next.bgm.tv`) resilience:** Single-flight per path, 8 s timeout ceiling, backoff on failure, and circuit breaker.

### 🌐 Universal Language Preference & Progressive Tab Painting
- **Instant tab rendering:** Characters, Staff, Recommendations, and Relations display raw data instantly, while Latin names/titles resolve asynchronously in the background.
- **Persistent name cache (`bangumi_entity_names_v1`):** Resolved Latin names are stored on disk and reused across media entries and restarts.
- **Raced name resolution:** Queries v0 and p1 in parallel, using the fastest response and cancelling the slower request.
- **Optimized resource lookups:** Eliminated thread contention in `getIdentifier` scans and secured keys against locale-specific casing (`Locale.ROOT`).

### 🎭 TMDB Character Portraits & Content Translation
- Fixed TMDB credits enrichment for anime titles, ensuring character portraits match without falling back to voice actor imagery.
- Improved biography translation formatting and HTML parsing stability across detail tabs.
