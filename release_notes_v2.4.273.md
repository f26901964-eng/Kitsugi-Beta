# Kitsugi-Beta v2.4.273 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.273)

### 📚 Fandom Wiki Faz 3 (Bilgi Kutusu Çevirileri, Görsel Dayanıklılığı, Kopyalama & Tüm Sayfalarda Wiki)
- **Bilgi Kutusu Etiket & Değer Çevirileri:** `FandomInfoboxLabels` ile ~90 bilgi kutusu etiketi (Name, Species, Birthday, Affiliation, Occupation...), grup başlıkları ve kısa durum değerleri (Male/Female, Alive, Deceased...) arayüz diline çevrilir. TR/EN dil dosyalarına 178 yeni anahtar eklendi.
- **Toplu Çeviri & Kopyalama Düğmeleri:** Bilgi kutusuna tek tıkla toplu çeviri ("Orijinal" ile geri dönüş) ve "Etiket: Değer" formatında panoya kopyalama eklendi. Wiki özeti ve her açılan bölüm kartına 36 dp'lik, değer satırlarına ise 13 dp'lik minik kopyalama düğmeleri yerleştirildi.
- **Görsel Yükleme Dayanıklılığı:** Fandom CDN'e yönelik `Referer` ve `Accept` başlık filtreleri (`FandomImageHeaders`), ölçekli → özgün → format=original yedek zinciri (`FandomWikiImage`) ve kırık görsel ikonu eklendi.
- **Tüm Sayfalarda Wiki Desteği:**
  - **Ekip / Seslendirme Sanatçısı:** `StaffDetailPage` ekranına 4. sekme olarak "Wiki" eklendi; sanatçı, seslendirdiği ana yapımların evren wiki'lerinde aranır.
  - **Eklenti Kaynaklı Manga/Manhwa:** `MangaDetailScreen` üzerine açılır "Wiki" kartı eklendi; başlık bazlı çözümleme sağlandı.
  - **Android TV:** TV detay ekranının sonuna kumanda uyumlu açılır "Wiki" kartı eklendi.

### ✨ Detay Sayfalarında Ortak Akıcı Animasyonlar (Smooth Detail Transitions)
- **Akıcı Video Açılışı & Liste Genişlemesi:** Video önizlemeleri ve açılış/kapanış (OP/ED) tema listeleri pat diye açılmak yerine yumuşak yükseklik ve görünürlük geçişiyle (`animateDetailContentSize`, `KitsugiDetailReveal`) genişleyip daralır. "Daha fazla" ile eklenen satırlar da animasyonla belirir.
- **Hafif Oynatıcı Temizliği:** Video satırı kapatılırken oynatıcı motoru gecikmesiz derhal serbest bırakılır; yalnızca satır yüksekliği yumuşakça küçülür.
- **Kaydırmayla Gelen Yapışkan Üst Çubuklar:** Karakter, ekip/seslendiren, medya ve stüdyo sayfalarındaki kaydırma başlıkları (`rememberDetailStickyHeaderVisible`) ortak fade ve kayma hareketiyle akıcı bir şekilde belirip kaybolur.
- **Akordeon & Wiki Bölümleri:** Wiki açılır kartları, sezon akordeonları ve uzun açıklama metinleri aynı merkezi hareket token'larıyla standartlaştırıldı.

### 🎬 TMDB Keşfet Başlık Dil Önceliği (Türkçe → İngilizce → Romaji)
- **Otomatik Keşfet Yenileme:** Ayarlar'dan "Dizi & Film Başlık Dili" (`tmdbLanguage`) değiştirildiğinde Keşfet sayfası artık otomatik olarak tam yeniden yüklenir; önceden eski dildeki başlıklar önbellekte kalıyordu.
- **Takvim Önbelleğinde Dil Desteği:** "Yakında Yayında" takvim şeridi önbellek anahtarına aktif TMDB dili eklendi (`tmdb|anon|tr-tr`), dil değişiminde 90 saniye eski dilde kalması önlendi.
- **Kanji Sızması Engellendi & Backoff:** İngilizce (`en-US`) yedek başlık isteklerine backoff'lu (300ms/700ms) 3 deneme mekanizması eklendi. Paralel isteklerde oluşan 429/zaman aşımı kaynaklı CJK/kanji sızması giderildi.
- **Kırılganlık Giderildi & Önbellek Geçersiz Kılma:** Bozuk tekil JSON kayıtlarının tüm şeridi düşürmesi `optJSONObject` ile engellendi. Eski hatalı başlıklı disk önbelleklerini geçersiz kılmak için `MediaTitleResolver.VERSION` 2'den 3'e yükseltildi.

### 🎌 Bangumi "Listem" Artık Başlık Dili Ayarına Uyuyor
- **Sorun:** Bangumi koleksiyonu içe aktarılırken API yalnızca özgün adı (`name`, çoğu zaman Japonca) döndürüyor; İngilizce/Romaji karşılığı **hiç gelmiyordu**. Bu yüzden "Listem → Bangumi" listesi, Ayarlar → Görünüm & Tercihler → **Anime & Manga Başlık Dili** ne seçilirse seçilsin CJK (Japonca/Çince) adla çiziliyordu.
- **Kalıcı Başlık Tamamlama:** Yeni `BangumiLibraryTitleHydrator`, kütüphanedeki eksik Latin adları arka planda tamamlar. Sıra: bellek önbelleği → Bangumi subject infobox'ı (`英文名` / `罗马字` / `别名`) → AniList birebir eşleşmesi. Sonuç veritabanına yazılır, yani **bir sonraki açılışta hiç ağ isteği yapılmaz**.
- **Anında Uygulama:** Latin ad öğrenildiği anda önbellekten okunur; liste, detay sayfası veya keşfet vitrininden hangisi önce yüklenirse yüklensin başlık aynı dile döner.
- **Özgün Ad Kaybolmaz:** Yeni Latin ad ana başlığa yazılırken özgün ad `titleJapanese` alanına taşınır; "Ana Dilde" seçeneği bozulmaz.
- **Bangumi Profil Listesi:** Profildeki koleksiyon satırları da seçilen başlık diline uyar.
- **Ağ/Performans Koruması:** Tamamlama yalnızca Latin adı gerçekten eksik satırlar için ve yalnızca "Listem" açıldığında çalışır; eşzamanlılık sınırlı, sonuçlar önbelleğe yazıldığı için tekrar denenmez.

### ⭐ Harici Puanlar (MDBList) Kartı Yeniden Tasarlandı
- **Uygulamanın Kendi Dili:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt kutuları artık tam doygun renkli bloklar değil; "İstatistikler" ve "Türler" bloklarıyla aynı zemin, köşe yarıçapı ve tipografiyi kullanan bir kart. Puanlar marka rengine hafifçe boyanmış yastıklarda, büyük ve okunur biçimde.
- **Kesilme Yok:** Puanlar yatay kaydırılan şeride sığmadığı için kesilmiyor; ekran genişliğine eşit dağılan ızgaraya yerleşiyor (5–7 puan iki satıra dengeli biçimde bölünür).
- **"0 Metacritic" Hatası Giderildi:** MDBList bazı kayıtlar için "puan yok" yerine **0** döndürüyordu ve ekranda `0` puanlı anlamsız kutular görünüyordu. Artık 0 değerleri "puan yok" kabul edilir; hiç puan yoksa kart tamamen gizlenir.
- **Yükleme İskeleti:** Kart iskeleti yeni ızgara geometrisine uyarlandı; yükleme bitince düzen zıplamaz.

### 🗳️ Wikidata Karakter Oylaması ile Akıllı Fandom Wiki Keşfi & Simkl Desteği
- **Karakter Sayfalarından Wiki Oylaması:** Wikidata üzerinde yapımın doğrudan Fandom bağlantısı (P4073) bulunmadığında (yapımların %99'unda eksik), medyanın QID'i bulunur → ters P1441 ("present in work") ile karakterler taranır → karakterlerin P6262 Fandom sayfa kimlikleri üzerinden wiki oylaması (`WikidataCharacterVotes`) yapılır.
- **Güvenli Eşik & Doğrulama:** Bir wiki'nin seçilmesi için en az 2 oy alması, ikinciden kesin fazla olması ve `siteinfo` ile doğrulanması (canlı ve ≥20 makale) şartı getirildi. Dil wikileri ve engelli wikiler elenir.
- **Simkl Entegrasyonu:** `FandomSourceIds` modeline `simklId` eklendi; `fromStableId("simkl", ...)` ofset dönüşümü tamamlandı ve detay sayfalarından Fandom sorgusuna iletildi.
- **Yeniden Deneme:** Wiki kartında "Tekrar dene" tıklandığında eski olumsuz önbellekler temizlenerek çözümleme sıfırdan koşturulur.

---

## 🇬🇧 English (v2.4.273)

### 📚 Fandom Wiki Phase 3 (Infobox Translations, Image Resilience, Copy Actions & Global Wiki Tabs)
- **Infobox Label & Value Localization:** Integrated `FandomInfoboxLabels` supporting ~90 infobox keys (Species, Birthday, Affiliation, Occupation...), group headers, and finite status values (Male/Female, Alive, Deceased...) localized via strings.xml.
- **Batch Translation & One-Tap Copy:** Added batch translation for character infoboxes alongside structured full-infobox copy actions. Added 36dp copy icon buttons for summary & section cards and compact 13dp copy buttons for individual attribute rows.
- **Resilient Image Pipeline:** Introduced `FandomImageHeaders` (`Referer` & AVIF-safe headers), `FandomWikiImage` multifold fallback chain (scaled → original → `format=original`), and broken-image indicator.
- **Ubiquitous Wiki Integration:**
  - **Staff / Voice Actor Screens:** Added a dedicated 4th "Wiki" tab to `StaffDetailPage` querying media wikis for the person's voiced characters.
  - **Extension Manga/Manhwa:** Added a collapsible "Wiki" card on `MangaDetailScreen` with title-fallback cache resolution.
  - **Android TV:** Added a remote-friendly collapsible "Wiki" section card at the end of TV detail screens.

### ✨ Smooth Detail Animations & Transitions
- **Fluid Video & List Expansions:** Video preview panels and opening/ending theme song lists now expand and collapse smoothly (`animateDetailContentSize`, `KitsugiDetailReveal`) rather than popping into view abruptly. Additional loaded rows animate in seamlessly.
- **Instant Media Player Release:** Closing a video row instantly releases the media player engine while the container height gracefully shrinks.
- **Sticky Header Transitions:** Scrolling floating top bars across character, staff/voice actor, media, and studio detail screens (`rememberDetailStickyHeaderVisible`) smoothly fade and slide into view without jitter.
- **Standardized Accordion & Wiki Sections:** Wiki collapsible cards, season accordions, and synopsis expanders now utilize centralized, unified motion tokens.

### 🎬 TMDB Explore Title Language Priority (Turkish → English → Romaji)
- **Automatic Explore Refresh:** Changing the "Series & Movie Title Language" (`tmdbLanguage`) now automatically refreshes the Explore screen, eliminating stale in-memory cached titles.
- **Language-Aware Airing Calendar:** Added the active TMDB language to the upcoming calendar cache key (`tmdb|anon|tr-tr`), preventing stale language serving during the 90-second TTL.
- **Kanji Fallback Elimination:** Implemented 3 retries with backoff (300ms/700ms) for `en-US` fallback title fetching, preventing inadvertent CJK/kanji fallbacks under rate limits (429) or timeouts.
- **Parsing Resilience & Cache Invalidation:** Switched to `optJSONObject` so a single corrupted item no longer breaks entire discover rows. Bumped `MediaTitleResolver.VERSION` from 2 to 3 to invalidate stale legacy title caches on disk.

### 🎌 Bangumi "My List" Now Follows the Title Language Setting
- **Root Cause:** The Bangumi collection API returns only the original name (`name`, usually Japanese) and never provides the English/Romaji counterpart. As a result, the Bangumi tab of My List always rendered CJK titles regardless of **Settings → Appearance & Preferences → Anime & Manga Title Language**.
- **Permanent Title Completion:** A new `BangumiLibraryTitleHydrator` fills in missing Latin titles for library rows in the background. Order: in-memory cache → Bangumi subject infobox (`英文名` / `罗马字` / `别名`) → exact AniList match. Results are written to the database, so **no network request happens on the next launch**.
- **Immediate Application:** Once a Latin title is known, it is served from cache — the list reflects the chosen language no matter which screen (list, detail, or explore) resolved it first.
- **Original Name Preserved:** The original name is moved into the `titleJapanese` field, so the "Native" option keeps working.
- **Bangumi Profile List:** Collection rows on the profile screen now follow the selected title language too.
- **Network/Performance Guard:** Completion only targets rows that actually lack a Latin title and only runs when My List is opened; concurrency is limited and results are cached so nothing is retried.

### ⭐ External Ratings (MDBList) Card Redesigned
- **Native App Language:** IMDb / Rotten Tomatoes / Metacritic / Letterboxd / TMDB / Trakt tiles are no longer fully saturated color blocks. They now use the same surface, corner radius, and typography as the "Statistics" and "Genres" blocks, with large readable scores on brand-tinted tiles.
- **No More Cut-off:** Ratings no longer overflow a horizontally scrolled strip; they are laid out in a grid that spans the screen width (5–7 ratings split across two balanced rows).
- **"0 Metacritic" Fixed:** MDBList returned **0** instead of "no score" for some entries, which rendered meaningless zero boxes. Zero is now treated as "no score", and the card hides entirely when nothing is available.
- **Loading Skeleton:** The skeleton mirrors the new grid geometry so the layout no longer jumps when loading finishes.

### 🗳️ Smart Fandom Wiki Discovery via Wikidata Character Voting & Simkl Support
- **Character Page Wiki Voting:** When an entry lacks a direct Fandom wiki property (P4073) on Wikidata (missing in ~99% of media), media QIDs are looked up → reverse P1441 ("present in work") queries characters → character P6262 Fandom article claims vote on the correct wiki (`WikidataCharacterVotes`).
- **Strict Consensus & Validation:** A wiki candidate must achieve at least 2 votes with a strict plurality over runner-ups, and is validated via `siteinfo` (active and ≥20 articles). Language variants and blocked wikis are filtered out.
- **Simkl Integration:** Added `simklId` to `FandomSourceIds`, integrated Simkl stable ID offset resolution, and piped Simkl IDs from media detail views into wiki queries.
- **Cache Invalidation on Retry:** Tapping "Retry" on wiki cards now flushes negative query caches to perform a clean re-resolution.
