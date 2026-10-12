# Kitsugi-Beta v2.4.263 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🌐 1. Google Translate Otomatik Çeviri Hattı Baştan Aşağı Yenilendi
Detay sayfası açıklamalarında, karakter ve ekip biyografilerinde, stüdyo sayfalarında, spoiler bloklarında ve inceleme/tartışma/aktivite pencerelerinde çevirinin bazen çalışıp bazen sessizce durmasına yol açan tüm kök nedenler çözüldü:

- **4 Kademeli Yedekli Uç Nokta Zinciri:** Tek bir uç noktaya bağlı kalmak yerine 4 farklı Google Translate uç noktası (`client=gtx&dj=1`, `client=at&dj=1`, `client=dict-chrome-ex&dj=1`, `clients5.google.com/translate_a/t`) tanımlandı. HTTP 429/403 hız sınırında beklemeden anında sıradaki yedek uç noktaya geçiliyor.
- **İptal Edilebilir İstekler & Kilitlenme Koruması:** Bloklayıcı çağrılar yerine `suspendCancellableCoroutine` kullanıldı ve eşzamanlı istek semaforu 4 izne çıkarıldı. Sayfa değiştirildiğinde veya zaman aşımında devam eden istek anında iptal edilip kota serbest bırakılıyor.
- **Parça Bazlı (Chunk-Level) Room Önbelleği:** Uzun biyografi ve incelemelerde her parça çevrildiği anda kendi hash anahtarıyla veritabanına kaydediliyor; çeviri yarıda kesilse bile hazır kısımlar kaybolmuyor.
- **CJK (Çince / Japonca / Korece) Cümle Bölme:** Boşluksuz yazılan Çince/Japonca metinlerde noktalama işaretlerinin (`。！？；…`) cümle sınırı sayılması sağlandı; parça boyutu 1400 karaktere optimize edildi.
- **Bozulmayan Markdown & Spoiler Maskelemesi:** Eski yer tutuculardaki `START`/`END` gibi kelimelerin Türkçeye çevrilerek linkleri ve yapıyı bozması engellendi. Harf içermeyen sembolik yer tutuculara (`⟪#0⟫`, `⟪@0⟫`) geçildi; `img(...)` medya etiketleri ve `~!...!~` spoiler blokları tam korumaya alındı.
- **`isLikelyTurkish` Yanlış Pozitif Koruması:** Başında Türkçe künye ("Merkez: ...", "Ülke: ...") bulunan CJK veya Kiril açıklamaların yanlışlıkla "Türkçe" sanılıp çevrilmeden bırakılması önlendi.
- **Hedef Dil İyileştirmesi:** Sistem dili İngilizce olan cihazlarda hedef dilin hatalı çözümlenmesi düzeltildi.

### 🖼️ 2. Vitrin (Keşfet Hero Carousel) Otomatik Görsel Boyutlandırması
Detay sayfasındaki akıllı oran mantığı Keşfet ana sayfasındaki vitrine taşındı:
- **Dinamik Kutu:** Vitrin bandı artık sabit bir yükseklik değil, o an gösterilen görselin gerçek en-boy oranına kilitlenen esnek bir kutudur.
- **Dikey Poster:** Dikey poster gösterilirken uzun-dar kutu (ekranın %62'si tavan olacak şekilde) açılır.
- **Yatay Arka Plan:** 16:9 görsel gösterilirken geniş-kısa bant (ekranın %34'ü taban olacak şekilde) oturur; karakterlerin kafasının veya görselin kırpılması önlendi.
- **Yatay Mod & Manga Uyumu:** Manga ve manhwa yapımlarında dikey kapak önceliği korunur; yatay modda arka plan bulunamadığında poster merkezlenmiş yumuşak dolguyla taşmadan oturur.

### 👤 3. MyAnimeList Tek Oturum (Unified Auth) ve Gelişmiş Profil Desteği
- **Tek Giriş Sistemi (`MalUnifiedAuthManager`):** OAuth sayfası uygulama içi WebView ile açılarak tek bir girişle hem Bearer API jetonu hem de web scraping oturum çerezleri (`MALHLOGSESSID`) aynı anda elde edilir; kullanıcının iki kez giriş yapma zorunluluğu ortadan kalktı.
- **5 Yeni MAL Web Uç Noktası:**
  - `/profile/{username}/reviews` — Sayfalanabilir kullanıcı incelemeleri
  - `/profile/{username}/recommendations` — Kullanıcının önerileri
  - `/profile/{username}/clubs` — Üye olunan kulüpler
  - `/profile/{username}/statistics/anime` — Puan, format, tür, stüdyo ve seslendirmen dağılımları
  - `/profile/{username}/statistics/manga` — Manga puan ve format istatistikleri
- **Profil Banner & Dağılım Grafikleri:** MAL profil sayfasına banner desteği, puan grafikleri ve seslendirme/stüdyo sıralama state'leri eklendi.

### 🎭 4. Karakter, Ekip, Stüdyo ve TMDB Veri Entegrasyonu
- **Gereksiz Shikimori Çeviri Fırtınasının Önlenmesi:** Jikan/MAL zaten biyografi döndürdüğünde arka planda atılan onlarca Rusça isim/biyografi çevirisi engellendi. Kiril isimler 12'lik gruplar halinde tek istekte toplu çevriliyor.
- **TMDB İngilizce Biyografi Yedeği:** TMDB oyuncu ve ekip üyelerinde Türkçe biyografi bulunmadığında doğrudan `en-US` yedeğine düşülüyor.
- **Stüdyo Meta ve Açıklama Zenginleştirmesi:** AniList ve Shikimori'de boş gelen stüdyo açıklamaları, logoları ve kuruluş tarihleri Jikan/MAL üzerinden otomatik tamamlanıp çeviriye veriliyor.
- **Eksik Kaynak Tamamlama:** AniList karakter/ekip biyografileri boşsa Jikan üzerinden tamamlanıyor; `KitsugiDetailClient.fetchSynopsis` içine TMDB dalı eklendi.

### ⚡ 5. Anında Çeviri & Canlı Ayar Tepkisi
- **Galeri Beklemesini Kaldırma:** Karakter, ekip ve medya detay sayfalarında çeviri artık 5 farklı kaynaktan görsel toplayan galeri işlemlerini beklemeden sayfa açılır açılmaz anında paralel başlatılıyor.
- **Sayfa İçi Ayar Değişimi:** Detay ekranındayken otomatik çeviri açılıp kapatıldığında veya hedef dil değiştirildiğinde sayfa anında yenilenmeden güncelleniyor.
- **Pencerelerde Otomatik Çeviri:** Spoiler sayfası (`InteractiveSpoilerSheet`), inceleme, konu ve aktivite bottom sheet'leri (ve TV sürümleri) otomatik çeviri sekmesiyle açılıyor ve parçalı akışı (`onPartial`) canlı yansıtıyor. Arama, Hero önizleme ve kütüphane diyaloglarına da çeviri desteği getirildi.

### 🧩 6. Eklenti Yönetimi ve Arayüz Düzeltmeleri
- **Cloudstream Eklenti Başlık Düzeni:** Uzun başlıklı eklentilerde sağdaki butonların başlığı sıkıştırıp harf harf dikey yazdırması sorunu `FlowRow` ve tam genişlikli başlık düzeniyle giderildi.
- **Eklenti Kaldırma:** Kaldırılan eklentilerin dosya sistemindeki kalıntılarının temizlenmesi sağlandı.

---

## 🇬🇧 English

### 🌐 1. Complete Overhaul of Google Translate Engine
- **4-Tier Fallback Endpoints:** 4 Google Translate endpoints (`gtx`, `at`, `dict-chrome-ex`, `clients5`) fail over instantly upon 429/403.
- **Cancellable Requests:** Non-blocking `suspendCancellableCoroutine` with 4-permit semaphore frees concurrency quotas immediately on cancellation.
- **Chunk-Level Room Persistence:** Partially translated paragraphs are preserved in Room cache across interruptions.
- **CJK Sentence Splitting:** Proper CJK punctuation splitting at 1400-char chunks.
- **Symbolic Markdown & Spoiler Masking:** Pure symbolic tokens prevent corrupted links, image tags, and broken `~!...!~` spoilers.
- **`isLikelyTurkish` Accuracy & Language Resolution:** Eliminated false positives on prefixed CJK/Cyrillic texts.

### 🖼️ 2. Dynamic Hero Carousel Aspect-Ratio Scaling
- **Adaptive Box Height:** Hero carousel container dynamically locks to the resolved aspect ratio of the active page artwork instead of a fixed height.
- **Portrait & Landscape Balance:** Automatically extends up to 62% screen height for portrait posters and narrows to 34% screen height minimum for 16:9 backdrops.
- **Manga & Multi-Platform Fit:** Preserves portrait cover priority for manga/manhwa while maintaining centered contain presentation in landscape mode.

### 👤 3. MyAnimeList Unified Authentication & Expanded Profile Data
- **Single Sign-On (`MalUnifiedAuthManager`):** In-app WebView handles both OAuth token exchange and web session cookie extraction (`MALHLOGSESSID`) in a single login flow.
- **5 New Web Scraping Endpoints:** Integrated paginated user reviews, recommendations, clubs, and detailed anime/manga score/format/genre/studio/voice actor distribution statistics.
- **Banner & Breakdown State:** Added profile banner support and extended state modeling in `KitsugiProfileViewModel`.

### 🎭 4. Character, Staff, Studio & TMDB Enhancements
- Prevented redundant Shikimori translation requests when Jikan provides bios; batched Cyrillic translations.
- TMDB English biography fallback, AniList/Shikimori studio enrichment via Jikan/MAL, and missing AniList bio fallbacks.

### ⚡ 5. Instant Parallel Translation & Reactive Settings
- Immediate translation start decoupled from multi-source gallery aggregation.
- Live reactive updates on in-page settings toggle without reload.
- Full auto-translation and streaming support across spoiler, review, topic, activity sheets, and preview dialogs.

### 🧩 6. Extension Management & UI Layout Fixes
- Fixed extension header layout overflow using `FlowRow`.
- Guaranteed file deletion on extension uninstallation.
