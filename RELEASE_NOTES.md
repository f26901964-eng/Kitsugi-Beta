# Kitsugi-Beta — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.220)

### 🧹 1. Kitsu Bozuk Özet (Synopsis) Tespiti ve Temizliği (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Akıllı Bozuk Özet Tespiti:** Kitsu veritabanında vandalize edilmiş veya hatalı birleştirilmiş kayıtlar (örneğin 2004 yapımı anime olan "ROAR" kaydında 1997 yapımı Fox TV dizisi "Roar"ın özetinin bulunması) tespit edilir:
  - Anime veya film türündeki kayıtlarda Amerikan/İngiliz canlı-aksiyon TV dizisi tarif eden ifadeler (`is an American television show`, `aired on the Fox network` vb.) şüpheli olarak yakalanır.
  - Özet yayın tarihi bağlamında ("aired/premiered/released in 19XX") kayıt yılından 2 yıldan fazla sapan tarihler şüpheli kabul edilir.
  - Canlı-aksiyon diziler (`TvShow`), Kore dizileri ve hikaye içi geçen geçmiş yıllar yanlış-pozitif üretmeyecek şekilde korunur.
- **Doğrulanmış Kaynak İle Değiştirme:** Şüpheli Kitsu özetleri MAL/Jikan üzerinden doğrulanmış temiz özetle (`CrossSyncIdentityGuard` sıkı başlık kontrolüyle) otomatik değiştirilir; doğrulanamazsa özet boşaltılır ("Açıklama bulunamadı" gösterilir, alakasız özet engellenir).
- **Önbellek Sürümleme & Çeviri Temizliği:** Kitsu detay önbellek anahtarı `_ks1` olarak sürümlendi; eski bozuk önbellekler geçersiz kılındı. Bozuk özetlerin çevirileri `DetailCache.removeTranslation` ile bellekten temizlenir.
- **Yedek Zinciri Koruması:** AniList/MAL → Kitsu arama zinciri ve `fetchSynopsis` fonksiyonuna (listeye ekleme penceresi, hero önizleme) Kitsu özet doğrulama mantığı entegre edildi.

### 🌐 2. MyAnimeList & Jikan Çoklu Host Zinciri (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Yeni Kaynak Sıralaması:** Jikan genel API kesintileri ve kapanışına karşı arama ve keşfet zinciri optimize edildi:
  1. Resmi MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (son çare yedek)
- **Çoklu Host Desteği & Devre Kesici:** `JikanGateway` üzerinden host bazında bağımsız hız sınırlaması, 429 soğuması ve 3 ardışık hatada devre kesici (circuit breaker) eklendi.
- **Tenrai Uyumluluğu:** Tenrai API sorgu parametreleri (`sfw=true` → `sfw`) otomatik dönüştürülür.
- **İstek Tekilleştirme & Özel İstemci:** `KitsugiHttpClient.jikanClient` ile harici `RetryInterceptor` devre dışı bırakılarak gereksiz kota tüketimi ve 429 tetiklemeleri engellendi.
- **Detay ve Görsel Entegrasyonu:** `KitsugiMalDetailClient`, `CharacterDetailViewModel` ve `StaffDetailViewModel` `/pictures` istekleri merkezi `JikanGateway` üzerinden yönlendirildi.

### 🧪 3. Birim Testleri
- `KitsuSynopsisValidatorTest.kt` ile 15 birim testi tamamlandı ve başarıyla doğrulandı.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇬🇧 English (v2.4.220)

### 🧹 1. Kitsu Corrupted Synopsis Detection & Sanitization (`KitsuSynopsisValidator.kt`, `KitsugiDetailClient.kt`)
- **Smart Anomaly Detection:** Identifies vandalized or incorrectly merged Kitsu catalog entries (e.g., 2004 anime "ROAR" containing synopsis of the 1997 Fox TV live-action show "Roar"):
  - Flags western live-action TV show phrasing (`is an American television show`, `aired on the Fox network`, etc.) for Anime and Movie media types.
  - Flags broadcasting year discrepancies exceeding 2 years in release verbs (`aired/premiered/released in 19XX`).
  - Safe against false-positives for legitimate `TvShow` entries, K-dramas, and in-universe fictional dates.
- **Verified Replacement Fallback:** Suspicious synopses are replaced by verified summaries fetched from MAL/Jikan guarded by `CrossSyncIdentityGuard.titlesLookRelated`; unverified synopses are safely cleared to avoid misleading descriptions.
- **Cache Versioning & Translation Eviction:** Kitsu detail cache keyed with `_ks1` to bypass contaminated cache entries. Stale synopsis translations are evicted via `DetailCache.removeTranslation`.
- **Fallback Chain Protection:** Integrated into the AniList/MAL → Kitsu fallback chain (15s timeout) and `fetchSynopsis` (add-to-list dialog, hero preview).

### 🌐 2. MyAnimeList & Jikan Multi-Host Chain (`JikanGateway.kt`, `JikanSearchClient.kt`)
- **Optimized Fallback Hierarchy:** Resilient routing against public Jikan downtime:
  1. Official MAL v2 (`api.myanimelist.net`)
  2. miribyou (`https://miribyou-topaz.vercel.app` / `MIRIBYOU_BASE_URL`)
  3. Tenrai (`https://api.tenrai.org/v1`)
  4. AniList (ultimate fallback)
- **Multi-Host Gateway & Circuit Breakers:** `JikanGateway` now coordinates host-level rate limiting, 429 cool-down, and circuit breakers (tripping on 3 consecutive server/network failures).
- **Tenrai Compatibility:** Seamless parameter translation (`sfw=true` → `sfw`).
- **Quota Leak Fix:** Introduced `KitsugiHttpClient.jikanClient` without `RetryInterceptor` to prevent quota burning outside the gateway.
- **Detail & Asset Gateway Routing:** `KitsugiMalDetailClient`, `CharacterDetailViewModel`, and `StaffDetailViewModel` `/pictures` requests now traverse `JikanGateway`.

### 🧪 3. Unit Tests
- 15 unit tests in `KitsuSynopsisValidatorTest.kt` passed successfully.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.220-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.219)

### 🔑 1. Harici API Entegrasyonları Doğrulama & Yönetim (`KitsugiIntegrationsSettingsDialog.kt`)
- **Canlı Anahtar Doğrulama:** TMDB ve Fanart.tv kişisel API anahtarları için anlık servis bağlantı testi eklendi; yalnızca doğrulanmış anahtarlar kaydedilir.
- **Güvenli Temizleme:** Alan temizlendiğinde uygulamanın yerleşik ortak anahtarına geri dönüş sağlanır.
- **MDBList Anahtar Desteği:** Giriş sırasında boşluk kırpma ve doğrulama eklendi.
- **Şifre / Maske Görünürlük Kontrolü:** API anahtarlarını gizleme ve gösterme kontrolleri entegre edildi.

### 📊 2. MDBList TMDB ID Yedek Araması & Akıllı Ayrıştırma (`MdbListClient.kt`)
- **Otomatik TMDB Yedeği:** IMDb kimliği bulunmayan içeriklerde pozitif TMDB ID ile otomatik puan sorgulama desteği eklendi.
- **Genişletilmiş Yanıt Formatı:** Hem yeni dizi hem de eski üst-seviye alan formatları eksiksiz ayrıştırılır ve önbelleğe alınır.
- `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinde IMDb/TMDB puan çağrıları bu yedek mekanizmasına bağlandı.

### 🎬 3. TMDB Özellik Kapıları & Televizyon Ağları (`TmdbMediaDetailClient.kt`)
- **Granüler İstek Kapıları:** Ayrıntı, afiş/arka plan, fragman, yapım şirketi, ağlar, oyuncu/ekip ve koleksiyon ayar kapıları API isteklerine ve model alanlarına bağlandı.
- **Yayın Ağları (Networks):** TV içeriklerinde yapımcı televizyon kanalları ve yayın ağları detay kartlarına eklendi.
- **Dinamik Keşfet Senkronizasyonu:** Keşfet sayfasındaki modern TMDB içerik rafları ayar değişikliğinde anında güncellenir (`tmdbModernHomeEnabled`).

### 🛡️ 4. Hassas URL Maskeleme & İstek Güvenliği (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Günlük Güvenliği:** Ağ isteklerinde ve hata kayıtlarında API anahtarları, token'lar ve hassas parametreler otomatik olarak maskelenir (`***`).

### 🧪 5. Test Kapsamı
- `MdbListClientTest.kt` ve `TmdbMediaDetailClientTest.kt` ile MDBList URL/ayrıştırma ve TMDB özellik filtreleme mantığı için kapsamlı birim testleri eklendi.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇬🇧 English (v2.4.219)

### 🔑 1. External API Integration Validation & Management (`KitsugiIntegrationsSettingsDialog.kt`)
- **Live Key Validation:** Added in-place connection verification for personal TMDB and Fanart.tv API keys; unverified keys are never saved.
- **Safe Key Reset:** Clearing key fields safely falls back to the app's built-in shared credentials.
- **MDBList Trimming & Checks:** Key whitespace trimmed and sanitized automatically.
- **Masking Controls:** Added show/hide toggle buttons for sensitive key fields.

### 📊 2. MDBList TMDB ID Fallback & Smart Response Parsing (`MdbListClient.kt`)
- **Automatic TMDB Fallback:** When IMDb identifiers are absent, ratings are queried via positive TMDB IDs (`/ratings/tmdb/...`).
- **Unified Schema Support:** Smoothly parses both modern list schemas and legacy top-level rating structures with caching.
- Integrated into `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🎬 3. TMDB Feature Gating & TV Networks (`TmdbMediaDetailClient.kt`)
- **Granular Feature Toggles:** Respects user toggles for basic info, release dates, artwork, trailers, productions, networks, credits, more-like-this, and collections.
- **Broadcast Networks:** Added TV broadcast networks to detail metadata cards.
- **Instant Explore Sync:** Modern TMDB rails in Explore react immediately to setting changes (`tmdbModernHomeEnabled`).

### 🛡️ 4. Sensitive URL Redaction & Logging Safety (`SensitiveUrlRedactor.kt`, `KitsugiApiBase.kt`)
- **Log Sanitization:** Automatically masks API keys, secrets, and authorization tokens in URLs across logs and exceptions (`***`).

### 🧪 5. Test Suite
- Added regression unit tests in `MdbListClientTest.kt` and `TmdbMediaDetailClientTest.kt`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.219-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.218)

### 🔍 1. Cloudstream Canlı İzleme & Tanı Raporlama Sistemi (`CsTrace`)
- **Otomatik Canlı Kayıt (`CsTrace.kt`, `CsTraceCore.kt`):** Eklenti yükleme, arama, başlık varyantları, eşleşme, bölüm bulma, doğrudan link ve embed çıkarma, `loadLinks` aşamaları `filesDir/cs_trace/` altında otomatik olarak kalıcı günlüğe yazılır.
- **Eşleşme Reddi Şeffaflığı:** Hiçbir adayın eşleşme eşiğini geçemediği durumlarda hedef başlık, sezon/bölüm, yıl ve en iyi 5 adayın benzerlik skorları kaydedilir ("neden 0 sonuç" durumu netleşti).
- **Hata & Çökme Takibi:** `loadLinks` zaman aşımları, DEX yükleme hataları ve kritik istisnalar izleme sistemine aktarılır.
- **Yanıltıcı Durum Mesajı Düzeltildi:** `domain_fixes.json` engelli listesindeki eklentiler denenirken tracker'a yazılan yanıltıcı "skip" yerine durum yalnızca izleme günlüğüne yazılır.
- **Canlı Rapor Paylaşımı:** Ayarlar > Eklenti Tanı ekranına **"Canlı İzleme Raporunu Paylaş (.md)"** düğmesi eklendi; oluşturulan Markdown raporu Android paylaşım penceresiyle doğrudan iletilebilir.

### 🎬 2. Kapsamlı Fragman Zinciri (`DetailTrailerFallback.kt`)
- **Çok Katmanlı Yedek Mekanizması:** Kaynak kendi YouTube fragmanını sağlamadığında arka planda sırayla **TMDB → Diğer Metadata Kaynakları (AniList, Jikan, Kitsu) → Cloudstream Eklentileri** (arama ve load yanıtındaki fragmanlar) taranır.
- **Dinamik Ön İzleme Kartı:** Bulunan fragman detay durumuna ve önbelleğe yazılır; `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` üzerinden "Ön izleme" kartı otomatik olarak belirir.

### 🔘 3. İzle Butonu Tasarım Hizalaması (`KitsugiUiverseGlowButton.kt`)
- **Tek Tip Eylem Butonları:** `KitsugiDetailActionButton` artık `ApiActionButton` ve `ActionButton` ile tam uyumlu: 999dp hap form, 14×10dp dolgu, `labelMedium` tipografi ve 16dp ikon ile görsel bütünlük sağlandı.

### ❌ 4. Açılır Sayfa ve Oynatıcı Kapatma Butonu Düzenlemeleri
- **Player Dışındaki Yüzen Çarpılar Temizlendi (`KitsugiSheetOrDialog.kt`):** v2.4.215'te eklenen evrensel yüzen çarpı kaldırıldı; sayfaların kendi doğal başlık yapıları korundu.
- **Player İçi Açılır Sayfalara Çarpı Butonu:** `PlayerSheet` sürükleme kolu satırına sağa hizalı kapatma çarpısı eklendi (Altyazılar, Ses, Kalite/Kaynak, Bölümler, Hız, Ekran Görüntüsü vb.).
- `EpisodeListDialog` ve `QualityProfileDialog` için tek dokunuşluk kapatma butonları eklendi.

### 🔙 5. Oynatıcı Geri Tuşu ve Jest Gezinme Zinciri (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministik Çıkış Zinciri:** Jest gezinme, 3 tuşlu çubuk ve donanım geri tuşlarının tamamında çalışan `OnBackPressedDispatcher` callback'i kuruldu:
  - `dialog` → `panel` → `sheet` → `kontrolleri gizle` → `oynatıcıdan çık`.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇬🇧 English (v2.4.218)

### 🔍 1. Cloudstream Live Trace & Diagnostic Reporting (`CsTrace`)
- **Automated Live Tracing (`CsTrace.kt`, `CsTraceCore.kt`):** Plugin loading, search query variants, title matching, episode extraction, direct links, embed scrapers, and `loadLinks` phases are persisted to `filesDir/cs_trace/`.
- **Match Rejection Transparency:** When candidate items fail matching thresholds, the target title, season, episode, year, and similarity scores of top 5 candidates are logged to reveal the exact reason behind empty results.
- **Fail-Safe Exception Recording:** `loadLinks` timeouts, DEX loading exceptions, and critical errors are automatically captured.
- **Accurate Status Attribution:** Suppressed misleading "skip" tracker entries for plugins on domain blocklists that are still actively probed.
- **One-Tap Report Sharing:** Added **"Share Live Trace Report (.md)"** button in Settings > Plugin Diagnostics to export markdown traces via Android share sheet.

### 🎬 2. Universal Trailer Fallback Pipeline (`DetailTrailerFallback.kt`)
- **Multi-Source Fallback Chain:** If the primary source lacks a YouTube trailer, a fallback ladder queries **TMDB → Metadata Sources (AniList, Jikan, Kitsu) → Cloudstream Plugins**.
- **Dynamic Preview Card:** Resolved trailers are written to detail state and cached, automatically displaying the "Preview" card across `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

### 🔘 3. Action Button Visual Alignment (`KitsugiUiverseGlowButton.kt`)
- **Unified Action Styling:** `KitsugiDetailActionButton` now aligns with `ApiActionButton` and `ActionButton`: 999dp pill form factor, 14×10dp padding, `labelMedium` typography, and 16dp icon.

### ❌ 4. Sheet & Player Close Button Refinements
- **Removed Extraneous Floating Close Buttons (`KitsugiSheetOrDialog.kt`):** Reverted universal floating close button outside the player, preserving native sheet headers.
- **Player Sheet Close Buttons:** Added unified close buttons to the `PlayerSheet` header bar across all bottom sheets (Subtitles, Audio Tracks, Quality/Sources, Episodes, Playback Speed, Screenshot).
- Added close buttons to `EpisodeListDialog` and `QualityProfileDialog`.

### 🔙 5. Player Back Gesture & Navigation Dispatcher Chain (`KitsugiFullscreenPlayerActivity.kt`)
- **Deterministic Back Key Handling:** Integrated an `OnBackPressedDispatcher` callback supporting gesture navigation, 3-button navigation, and hardware back keys:
  - `dialog` → `panel` → `sheet` → `hide controls` → `exit player`.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.218-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.217)

### 🚪 1. Merkezi Jikan Gateway (`JikanGateway.kt`) & MAL Trafik Optimizasyonu
- **Merkezi Kota ve Hız Sınırı (Rate Limiting):** Tüm Jikan trafiği tek bir kapıdan yönetilir; saniyede 3 ve dakikada 55 istek sınırları aşılmaz.
- **Toplu 429 Koruması:** Jikan'dan 429 (Too Many Requests) yanıtı geldiğinde tüm Jikan istekleri birlikte beklemeye geçer ve `Retry-After` başlığına uyulur.
- **Akıllı Bellek Önbelleği (In-Memory Cache):** Detay uçları 6 saat, liste ve arama uçları 30 dakika boyunca önbelleğe alınır; gereksiz ağ istekleri önlenir.
- **Eşzamanlı İstek Birleştirme (Single-Flight Deduplication):** Aynı URL'ye aynı anda gelen istekler tek bir ağ çağrısını paylaşır; sekmelerin eşzamanlı yüklenmesinde sunucuya tek istek gider.
- **Öncelikli Trafik Yönetimi:** Arka plan görevleri (profil senkronizasyonu, kimlik eşleştirme) kullanıcının aktif gezindiği ekranların kotasını tüketmez.
- **Keşfet & Arama MAL Önceliği:** MAL için Jikan önce denenir, resmi MAL API ikinci, AniList ise son yedek olarak çalışır. Karakter, ekip, ilişki, öneri ve bölüm istekleri gateway üzerinden güvenle akar.

### 🎯 2. AniList Manuel Kayıtlarında Yanlış Öneri ve Kimlik Çakışması Düzeltmesi
- **Kök Neden Giderildi (`MediaEntryDetailViewModel.kt`):** Manuel eklenen AniList kayıtlarında `malId` boş kaldığında yerel veritabanı satır numarasının (`entry.id`) dış servis kimliği gibi okunması ve başka bir yapımın MAL ID'siyle çakışarak alakasız öneriler getirmesi (ör. Rent-a-Girlfriend sayfasında farklı animelerin önerilerinin çıkması) engellendi.
- **Yalnızca Gerçek Kimlik Kullanımı:** AniList kaynaklı kayıtlarda yerel satır numarası (`entry.id`) dış kimlik olarak kabul edilmez; yalnızca geçerli bir `malId` varsa dış ID olarak kullanılır.
- **Başlık Tabanlı Arama Fallback'i (`KitsugiMediaRelationsClient.kt`):** Dış kimliği bulunmayan AniList kayıtlarında temizlenmiş başlık üzerinden doğrudan AniList API'sinde arama yapılarak (`fetchRecommendationsFromAniListBySearch`) doğru öneriler getirilir.

### 🖼️ 3. Vitrin Kaplama & Responsive Görsel Sunumu (`KitsugiHeroSection.kt`)
- **Tam Kaplayan Vitrin (Full Bleed Cover/Crop):** Bulanık arka plan dolgusu ve sığdırma (fit) yapısı kaldırılarak resmin tüm vitrin alanını kaplaması sağlandı (`ContentScale.Crop`).
- **Responsive Kaynak Seçimi (`heroImageCandidates`):**
  - Geniş vitrin bandında (en/boy $\ge$ 1.1, yatay ekran veya tablet): yatay dikdörtgen fanart/backdrop öncelikli.
  - Dikey telefon modunda (en/boy < 1.1): dikey poster öncelikli.
- **Akıllı Hizalama (`BiasAlignment(0f, -0.2f)`):** Yatay arka planlar üst gövde ve yüzleri koruyacak şekilde yukarıdan hafif odaklı hizalanır; dikey posterler ise üstten kırpılır.
- **Yedek Görsel Zinciri:** Birincil görsel yüklenemediğinde otomatik olarak alternatif adaya (backdrop $\leftrightarrow$ poster) geçilir.

### 🌸 4. Bangumi Keşfet, REAL Diziler/Filmler ve Yerelleştirme Güncellemeleri
- **Canlı Çekim (REAL) Rafları (`BangumiRealExploreSections.kt`):** Bangumi TV dizileri/dramaları ve filmleri için Keşfet bölümüne özel raflar eklendi.
- **Filtreleme & Sayfalama:** Yetişkin içerik filtreleme, vitrin ve rastgele yapım seçimi, kaynak belirtimi ve "Tümünü Gör" sayfalaması entegre edildi.
- **Genişletilmiş İsim ve Çeviri Seçimi:** Karakter, seslendirmen ve ekip adları AniList'te eşleşmediğinde doğrudan Bangumi infobox yedeklerine başvurulur; AniList köprüsü 2. sayfayı da denetler.

### 🌐 5. Türkçe Akış & Başlık Temizleme Düzeltmeleri
- **Site Gürültüsü Temizleme (`stripTurkishSiteNoise`):** Türkçe sitelerin başlıklara eklediği gürültü kalıpları 16 özel regex ile ayıklandı.
- **Genişletilmiş Sorgular:** Arama sorguları 12'den 18'e çıkarıldı, geniş arama fallback'i ve Türkçe CDN/Range optimizasyonları korundu.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇬🇧 English (v2.4.217)

### 🚪 1. Centralized Jikan Gateway (`JikanGateway.kt`) & MAL Traffic Pipeline
- **Unified Rate Limiting:** All Jikan traffic routes through a single gateway strictly bounded by 3 req/sec and 55 req/min token-bucket limits.
- **Coordinated 429 Backoff:** Upon receiving HTTP 429 (Too Many Requests), all queued and concurrent Jikan requests hold together, respecting `Retry-After`.
- **In-Memory Caching:** Detail endpoints cached for 6 hours; list and search endpoints cached for 30 minutes.
- **Single-Flight Request Deduplication:** Concurrent identical URL requests share a single underlying network call, eliminating duplicate fetches during multi-tab screen initialization.
- **Priority-Aware Traffic Budgeting:** Background workloads (profile sync, identity mapping) do not exhaust quota allocated for active user UI navigation.
- **Explore & Search MAL Priority:** Jikan is tried first for MAL, with the official MAL API and AniList serving as reliable fallbacks.

### 🎯 2. AniList Manual Entry False ID & Recommendation Mismatch Fix
- **Resolved Database Row ID Leak (`MediaEntryDetailViewModel.kt`):** Fixed an issue where manual AniList entries without a `malId` mistakenly defaulted to using their local database row index (`entry.id`) as an external API identifier, causing erroneous recommendation lookups when clashing with other titles' MAL IDs.
- **Strict External ID Resolution:** Explicitly decoupled internal database row IDs from external identifier logic for AniList sources; only valid `malId` values are passed.
- **Title-Based Recommendation Fallback (`KitsugiMediaRelationsClient.kt`):** When external IDs are absent for AniList entries, recommendations are safely retrieved by title query (`fetchRecommendationsFromAniListBySearch`) instead of leaking erroneous IDs.

### 🖼️ 3. Full Bleed Hero Cover & Responsive Presentation (`KitsugiHeroSection.kt`)
- **Full Bleed Cover/Crop:** Replaced blur-filled letterboxing with edge-to-edge `ContentScale.Crop` presentation.
- **Responsive Media Selection (`heroImageCandidates`):**
  - Wide hero banners (aspect ratio $\ge$ 1.1, landscape & tablet): landscape backdrop/fanart first.
  - Portrait mobile phone viewports: high-res vertical poster first.
- **Smart Subject Framing (`BiasAlignment(0f, -0.2f)`):** Horizontal backdrops align with a slight upward bias to keep heads and focal subjects in frame; portrait posters align to top.
- **Seamless Fallback Chain:** If the primary candidate fails to render, the hero automatically cascades to the alternate candidate (backdrop $\leftrightarrow$ poster).

### 🌸 4. Bangumi Explore REAL Shelves & Localization Updates
- **Live-Action REAL Sections (`BangumiRealExploreSections.kt`):** Introduced dedicated Explore shelves for Bangumi TV dramas and films.
- **Filtering & Pagination:** Integrated adult content filters, hero & random selection, source attribution, and "See all" pagination.
- **Expanded Infobox & Fallbacks:** Direct Bangumi fallback for character, voice-actor, and staff names when AniList lacks a match; AniList bridge checks second-page search results.

### 🌐 5. Turkish Stream Scraper & Noise Cleaning
- **Noise Stripping (`stripTurkishSiteNoise`):** Strips 16 regex noise patterns commonly found on Turkish streaming websites.
- **Broad Search & Query Expansion:** Query variants expanded to 18 with intelligent fallback matching and CDN handling.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.217-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.216)

### 🖼️ 1. Vitrin Cover / Baştan Sona Kaplama & Akıllı Görsel Seçimi
- **Tam Ekran Kaplayan Vitrin (`ContentScale.Crop`):** Önceki "Fit + bulanık dolgu" katmanları tamamen kaldırıldı. Vitrin görseli artık ekranı baştan sona kaplar, kenarlarda boşluk veya bulanık zemin kalmaz.
- **Ekran Boyutu ve Oranına Göre Akıllı Seçim (`heroImageCandidates`):**
  - Geniş vitrin bandı (yatay telefon, tablet, TV — en/boy $\ge$ 1.1) $\rightarrow$ yatay dikdörtgen fanart / backdrop öncelikli.
  - Dikey telefon vitrini (kareye yakın kutu) $\rightarrow$ dikey poster öncelikli.
- **Dinamik Hata Yedekleme Zinciri (`onLoadingFailed`):** Birincil görsel yüklenemezse anında sıradaki adaya (backdrop $\leftrightarrow$ poster) geçilir.
- **Kırpma Odak Hizalaması (`heroImageAlignment`):** Geniş vitrinde poster kullanılmak zorunda kalındığında karakter yüzü ve başlık bandının korunması için hafif yukarı bias (`BiasAlignment(0f, -0.2f)`), backdrop görsellerde merkez hizalama uygulandı.
- **Çok Yönlü TMDB Backdrop Zenginleştirmesi:** Sadece yatay mod değil, dikey tablet ve geniş vitrin bantları da TMDB fanartlarından otomatik yararlanır.
- **Yumuşatılmış Gradyanlar:** Metin okunabilirliğini korurken görselin merkezini ve detaylarını kapatmayan yumuşak renk geçişleri.
- **HeroImageSelectionTest:** 13 birim testi %100 başarıyla doğrulandı.

### 🇹🇷 2. Türkçe Akış & Eklenti Pipeline İyileştirmeleri
- **Site Başlık Kirliliği Temizleme (`stripTurkishSiteNoise`):** Türkçe dizi/film sitelerinin başlıklara eklediği reklam ve gürültü metinleri ("Türkçe Dublaj 1080p izle" vb.) 16 regex kuralı ile filtrelenerek temiz başlık eşleştirmesi sağlandı.
- **Genişletilmiş Dil & Arama Varyantları:** Başlık varyantları 8'den 12'ye, arama sorguları 12'den 18'e çıkarıldı; Türkçe karakter transliterasyonu (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i) eklendi.
- **Geniş Arama Fallback'i (`buildBroadSearchQueries`):** Tüm varyantlar boş döndüğünde anlamlı ilk 2-3 kelime üzerinden genişletilmiş arama yapılarak farklı Türkçe adlandırmalar yakalanır.
- **45+ Yeni Türkçe CDN Tanıma:** AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy gibi 45+ sağlayıcı `isEmbedUrl` kapsamına eklendi.
- **Gelişmiş Stream Canlılık Doğrulaması (`tryGetRangeValidation`):** HEAD 400 hatası veren akışlarda doğrudan başarısız saymak yerine GET Range header ile doğrulama yapılır; zaman aşımı süresi 6s $\rightarrow$ 8s'ye yükseltildi.
- **Türkçe Başlık Model Alanı (`titleTurkish`):** TMDB üzerinden Türkçe başlıklar çekilerek modele ve arama motoruna entegre edildi.

### 📦 3. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇬🇧 English (v2.4.216)

### 🖼️ 1. Vitrin Cover Presentation & Responsive Hero Asset Selection
- **Full Bleed Cover (`ContentScale.Crop`):** Completely removed letterboxed "Fit + ambient blur" layers. Hero artwork now stretches end-to-end across the banner container without blurred sidebars or margins.
- **Aspect-Aware Asset Prioritization (`heroImageCandidates`):**
  - Wide hero containers (landscape, tablets, TV — aspect $\ge$ 1.1) prioritize horizontal fanart / backdrop artwork.
  - Portrait mobile phone boxes prioritize vertical posters.
- **Resilient Fallback Chain (`onLoadingFailed`):** Automatically cascades to the alternative candidate (backdrop $\leftrightarrow$ poster) upon asset load failure.
- **Focal Alignment Preservation (`heroImageAlignment`):** Applies slight top-vertical bias (`BiasAlignment(0f, -0.2f)`) when vertical posters are cropped in widescreen containers to prevent cropping faces and title text.
- **Omni-Directional TMDB Backdrop Enrichment:** TMDB backdrop enrichment now activates across all orientations, including portrait tablets.
- **Softened Gradients:** Refined horizontal and vertical gradient scrims to preserve artwork visibility while ensuring text readability.
- **Unit Tests (`HeroImageSelectionTest`):** 13 unit tests passed with 100% success.

### 🇹🇷 2. Turkish Stream & Extension Scraper Pipeline Optimization
- **Stripped Turkish Title Noise (`stripTurkishSiteNoise`):** Cleans up title noise and metadata suffixes ("Türkçe Dublaj 1080p izle", etc.) via 16 regex patterns for accurate content matching.
- **Expanded Query & Language Variants:** Increased title variants from 8 to 12 and search queries from 12 to 18 with ASCII transliteration (ğ $\rightarrow$ g, ş $\rightarrow$ s, ı $\rightarrow$ i).
- **Broad Search Fallback (`buildBroadSearchQueries`):** Secondary search pass utilizing the first 2-3 significant title tokens when exact matches return empty.
- **45+ Turkish Embed & CDN Patterns:** Integrated detection for AlionsPlayer, VidMoly, TRsTX, CloseLoad, Pichive, Rapidrame, VidGuard, TurboVid, HDVid, PlayTube, VidHide, Embedy, and others.
- **Enhanced Stream Probing (`tryGetRangeValidation`):** Mitigates HEAD 400 rejections by falling back to GET requests with HTTP Range headers; increased probe timeout from 6s to 8s.
- **Turkish Title Model Field (`titleTurkish`):** Added `titleTurkish` to `KitsugiMediaDetail` populated directly from TMDB localized payloads.

### 📦 3. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.216-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.215)

### 🛡️ 1. Sessiz Çökme Kök Neden Çözümü (RenderThread SIGSEGV & WebView Teardown)
- **Çifte Destroy ve Use-After-Free Giderildi (`CsCfWarmupManager.kt`):** Çökme raporlarındaki `cr_AwContents: Application attempted to call on a destroyed WebView` uyarıları ve ardından `RenderThread` display-list oynatımı sırasında oluşan `SIGSEGV` (null+0x20) hatası tamamen çözüldü.
- **Güvenli Tek Seferlik Teardown (`releaseWebView`):** `AtomicBoolean` korumasıyla WebView yalnızca bir kez sökülür, durdurulur, `about:blank` yüklenir ve güvenle yok edilir.
- **Açılış Rahatlatması (`KitsugiApplication.kt`):** Proaktif Cloudflare warmup işlemi ilk açılışta arayüzle yarışıp frame atlamalarına (`Skipped 35 frames`) ve `DequeueBuffer` zaman aşımlarına yol açmaması için 12 saniye gecikmeli başlatıldı.

### 🎬 2. Oynatıcı, Altyazı ve PiP İyileştirmeleri
- **Dahili Türkçe Altyazı Seçim Döngüsü (`Media3PlayerEngine.kt`):** Harici altyazılar bağlanmadan önce ilk `onTracksChanged` olayında seçimin sonlanması engellendi. Hedef Türkçe parça tespit edilene kadar (maksimum 8 deneme) otomatik seçim politikası çalışır. Kullanıcının elle yaptığı altyazı seçimi korunur (`userSelectedTextTrack`).
- **Altyazı Taşma Koruması:** Dikey konum kaydırıldığında altyazının ekran dışına veya alt çubuğun altına kaçması engellendi; 4dp kenar payı ile görünür alana sabitlendi.
- **PiP Kapatıldığında Sesin Devam Etmesi Engellendi (`KitsugiFullscreenPlayerActivity.kt`):** Mini pencere kapatıldığında veya odak kaybolduğunda sesin arkadan çalmaya devam etmesi çözüldü; `invokePipAction` yedek mekanizmasıyla doğrudan ViewModel'e iletildi, `KeepAliveService.stop()` ve temiz Activity sonlandırması (`finish()`) sağlandı.
- **Kalıcı Tam Ekran (Immersive Mode):** MIUI ve HyperOS gibi arayüzlerde PiP veya odak değişimi sonrası alt gezinme çubuğunun ekranda asılı kalması giderildi (`onResume` ve `onWindowFocusChanged` üzerinde dinamik yenileme).

### 📑 3. Açılır Sayfalar (Sheet/Dialog) ve Çarpı Butonu
- **Kaydırma Kilidi Kaldırıldı (`KitsugiSheetOrDialog.kt`):** Açılır sayfalarda içerik aşağı kaydırıldığında kapatma hareketinin kilitlenmesi sorunu giderildi.
- **Evrensel Kapatma Çarpısı (`KitsugiSheetCloseButton`):** Tüm açılır sayfalara sağ üstte şık ve her koşulda çalışan kapatma çarpısı eklendi.

### 🌐 4. Bangumi Kapsamlı Türkçe Çeviri ve Metadata Zenginleştirmesi
- **95+ Tür ve Etiket Çevirisi:** Çince ve Japonca tür/etiketler (恋爱 → Romantik, 日常 → Günlük Yaşam, 京阿尼 → Kyoto Animation, 神作 → Başyapıt vb.) Türkçe'ye çevrildi (`strings.xml` ve `values-en/strings.xml`). Tür etiketine tıklanarak arama yapıldığında İngilizce API terimine dönüştürme eklendi.
- **Stüdyo Adları:** Çince/Japonca stüdyo adları Latin karşılıklarına dönüştürüldü (京都アニメーション → Kyoto Animation vb.).
- **AniList Kişi & Karakter Köprüsü (`KitsugiAniListPersonBridge`):** Bangumi'de kanji kalan karakter ve seslendirmen isimleri AniList köprüsü üzerinden otomatik olarak Romaji ve İngilizce isimlerle eşleştirildi.
- **Kadro Rolleri Çevirisi:** Episode Direction → Bölüm Yönetmeni, Animation Direction → Animasyon Yönetmeni, 監督 → Yönetmen vb. kadro rolleri eksiksiz Türkçeleştirildi.
- **İlişki ve Öneri Başlıkları:** Önerilen ve ilişkili yapımların Japonca başlıkları İngilizce/Romaji karşılıklarıyla zenginleştirildi.

### 📋 5. Çökme Raporu Dosya (.txt) Paylaşımı
- **FileProvider ile Doğrudan Dosya Paylaşımı (`KitsugiCrashRecoveryDialog.kt`):** Çökme ekranındaki "Paylaş" butonu artık düz metin yerine doğrudan `crash_log.txt` dosyasını paylaştırır.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇬🇧 English (v2.4.215)

### 🛡️ 1. Silent Crash Resolution (RenderThread SIGSEGV & WebView Teardown)
- **Resolved Double-Destroy Use-After-Free (`CsCfWarmupManager.kt`):** Fixed the root cause of `cr_AwContents: Application attempted to call on a destroyed WebView` warnings and subsequent `RenderThread` display-list recursive traversal crashes (`SIGSEGV` at `fault_addr=0x20`).
- **One-Shot Teardown Routine (`releaseWebView`):** Enforced atomic, single teardown guarded by `AtomicBoolean` ensuring WebView is unattached, halted, pointed to `about:blank`, and cleanly destroyed without race conditions.
- **Warmup Startup Throttle (`KitsugiApplication.kt`):** Deferred proactive Cloudflare warmup by 12 seconds to prevent resource contention during initial frame rendering, eliminating frame drops and buffer timeouts.

### 🎬 2. Player, Subtitle & PiP Architecture Fixes
- **Embedded Subtitle Selection Convergence (`Media3PlayerEngine.kt`):** Resolved race condition where initial `onTracksChanged` fired before sideloaded/embedded Turkish subtitle tracks arrived. Retries up to 8 times until preferred language is settled; preserves manual user track overrides (`userSelectedTextTrack`).
- **Subtitle Bounds Clamping:** Prevented vertical subtitle offset adjustments from translating text offscreen or underneath navigation chrome (clamped with 4dp margins).
- **PiP Background Audio Leak Elimination (`KitsugiFullscreenPlayerActivity.kt`):** Fixed issue where closing the mini-player left playback audio active. Replaced dangling Compose callbacks with `invokePipAction` ViewModel fallbacks, stopped `KeepAliveService`, and terminated ghost player activities.
- **Persistent Immersive Mode:** Re-applied system bar hiding on `onResume` and `onWindowFocusChanged` to fix persistent navigation bar overlays on MIUI/HyperOS devices.

### 📑 3. Sheets & Universal Close Button
- **Fixed Sheet Drag Lock (`KitsugiSheetOrDialog.kt`):** Removed bottom-sheet dismissal locking when scrolling content down.
- **Universal Close Button (`KitsugiSheetCloseButton`):** Added persistent top-right close buttons across all sheets and dialogs.

### 🌐 4. Bangumi Full Turkish Localization & Metadata Enrichment
- **95+ Tag & Genre Translations:** Localized Chinese/Japanese Bangumi genres and tags into Turkish and English with fallback dictionaries and search query translation.
- **Studio Name Latinization:** Converted Kanji studio names into recognized Latin branding (e.g. Kyoto Animation, Pony Canyon).
- **AniList Person & Character Bridge (`KitsugiAniListPersonBridge`):** Automatically matches and hydrates Kanji character and voice actor names with Romaji/English names via AniList.
- **Staff Role Translations:** Fully translated production credits (Episode Director, Animation Director, Script, etc.) into Turkish.
- **Relation & Recommendation Title Hydration:** Hydrates Japanese titles in related and recommended lists with English/Romaji metadata.

### 📋 5. Direct Crash Log File Sharing
- **Crash Log File Provider (`KitsugiCrashRecoveryDialog.kt`):** "Share" action on the crash recovery dialog now shares the actual `crash_log.txt` file via FileProvider instead of raw text.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.215-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.214)

### 🌟 1. Vitrin v2: Akıllı Seçim ve Skorlama Motoru (`HeroSelection.kt`)
- **Metrik Tabanlı Puanlama:** Vitrin (Hero Section) içerik seçimi rastgelelikten çıkarılıp ağırlıklı metrik algoritmasına geçirildi:
  $$\text{Skor} = (\text{Puan} \times 10) + (\log_{10}(\text{Üye} + 1) \times 8) + (\log_{10}(\text{Favori} + 1) \times 6) + \text{Rank} + \text{Trend (15)} + \text{Sezon (10)} + \text{Manga (8)}$$
- **Katalog ve Format Çeşitliliği:** Vitrinin tek bir türe veya kaynağa boğulması engellendi; en fazla 2 manga, en az 5 anime/dizi/film ve kaynak başına tavan sınırı (en fazla 3) ile dengeli bir vitrin akışı sağlandı.
- **Kaynaklar Arası Metrik Normalizasyonu:** TMDB gibi üye/favori bilgisi içermeyen kaynaklarda oy sayısı (`voteCount`) ve oy ortalaması logaritmik olarak dengelenerek haksız puan kaybı önlendi.
- **Görsel Kalite Filtresi:** Arka planı (backdrop) veya yüksek çözünürlüklü yatay görseli bulunmayan içerikler vitrin önceliğinde elenir.

### 🖼️ 2. Fit + Bulanık Dolgu Katmanlı Görsel Mimarisi (Kırpma Düzeltmesi)
- **Görsel Kırpma / Kesilme Sorunu Çözüldü (`KitsugiHeroSection.kt`):** Eski zorunlu kırpma (`ContentScale.Crop`) nedeniyle dikey afişlerin ve karakter yüzlerinin kesilme problemi tamamen ortadan kaldırıldı.
- **Çift Katmanlı Sunum:**
  - **Ön Plan:** Orijinal en-boy oranını tam koruyan (`ContentScale.Fit`) net ve keskin ana görsel.
  - **Arka Plan:** Ekranı boşluk bırakmadan dolduran, ortam rengini yansıtan bulanık dolgu katmanı (`HeroAmbientBackground` — Crop + Blur + Karartma).
- **Yumuşatılmış Gradyanlar:** Başlık ve meta metinlerinin okunabilirliğini artırırken görseli boğmayan yumuşak alt ve üst geçiş katmanları uygulandı.

### 🔄 3. TMDB Arka Plan Entegrasyonu ve Birleştirme (`ExploreViewModel.kt`)
- **Eksik Arka Planları Tamamlama (`enrichHeroBackdrops`):** MAL, Bangumi veya diğer kaynaklardan gelen popüler animelerde yatay backdrop bulunmadığında, TMDB üzerinden otomatik olarak yatay afiş temin edilir ve `heroBackdropOverrides` ile vitrinde kullanılır.
- **Tüm Bölümlerden Havuz Oluşturma (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Trend, Popüler, Sezonluk ve Manga listelerinin tamamından tekilleştirilmiş zengin havuz toplanarak yeni vitrin motoruna teslim edildi.

### 📊 4. Zengin Meta ve İstatistik Satırı (`HeroSectionComponents.kt`)
- **Kompakt Metrik Bilgileri:** Vitrin kartlarının altındaki meta satırına "1.2M üye • 45.3K favori" şeklinde biçimlendirilmiş topluluk metrikleri eklendi.

### 🧪 5. Testler ve Doğrulama (`HeroSelectionTest.kt`)
- Vitrin seçim ve skorlama motoru için 10 adet birim testi eklendi; kaynak çeşitliliği, skor hesaplamaları, TMDB normalizasyonu ve fallback senaryoları %100 başarıyla doğrulandı.

### 📦 6. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇬🇧 English (v2.4.214)

### 🌟 1. Vitrin / Hero Section v2: Intelligent Selection Engine (`HeroSelection.kt`)
- **Weighted Metric Scoring:** Vitrin items are now selected through a multi-factor ranking algorithm:
  $$\text{Score} = (\text{Score} \times 10) + (\log_{10}(\text{Members} + 1) \times 8) + (\log_{10}(\text{Favorites} + 1) \times 6) + \text{Rank} + \text{Trending Bonus (15)} + \text{Seasonal Bonus (10)} + \text{Manga Bonus (8)}$$
- **Format & Source Diversity:** Guaranteed balanced representation across the carousel: max 2 manga, min 5 anime/shows, and provider caps (max 3 per provider) preventing single-source domination.
- **Cross-Source Metric Normalization:** Sources lacking member/favorite counts (e.g. TMDB) are normalized using vote counts and ratings to maintain fair scoring.
- **Backdrop Quality Gate:** Automatically prioritizes titles with high-resolution backdrops or landscape banners over raw vertical posters.

### 🖼️ 2. Layered Fit + Ambient Blur Presentation (Cropping Fix)
- **Eliminated Poster Cropping (`KitsugiHeroSection.kt`):** Fixed the aggressive `ContentScale.Crop` behavior that cropped character heads and vertical poster artwork.
- **Dual-Layer Rendering:**
  - **Foreground:** Pristine uncropped asset rendered with `ContentScale.Fit`, preserving the original aspect ratio.
  - **Background:** Ambient blur fill (`HeroAmbientBackground` — Crop + Blur + Dim) that seamlessly expands to fill widescreen or ultra-wide viewport edges.
- **Refined Gradients:** Softened multi-stop linear gradients ensuring text readability without obscuring artwork.

### 🔄 3. TMDB Backdrop Enrichment (`ExploreViewModel.kt`)
- **Backdrop Fallback Hydration (`enrichHeroBackdrops`):** When popular entries from MAL or Bangumi lack native horizontal backdrops, the ViewModel dynamically queries TMDB for matching fanart/backdrops, merging them via `heroBackdropOverrides`.
- **Multi-Shelf Pool Aggregation (`ExploreScreen.kt` & `AllSourcesExplore.kt`):** Merged items across Trending, Popular, Seasonal, and Manga shelves into an enriched pool fed into the selection engine.

### 📊 4. Compact Community Statistics (`HeroSectionComponents.kt`)
- **Rich Meta Display:** Added formatted community engagement badges (e.g. "1.2M members • 45.3K favorites") to the hero meta bar.

### 🧪 5. Testing & Validation (`HeroSelectionTest.kt`)
- Added 10 unit tests covering selection diversity, score calculation, TMDB fallback normalization, and edge cases with 100% pass rate.

### 📦 6. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.214-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.213)

### 🔍 1. Arama Ekranı: Yükleme Animasyonu Tutarlılığı (Shimmer Fix)
- **Kopya Shimmer Kaldırıldı:** "Tümü" sekmesinde verisi gelmiş rafların üstüne binen 5 sabit shimmer rafı tamamen kaldırıldı. Artık raf başına tek görsel durum var: Verisi gelmeyen raf kendi shimmer'ını gösterir, verisi gelen raf anında karta dönüşür.
- **Titreme Giderildi:** Arama başladığında yükleme bayrakları kapsama göre anında yazılır; ara "boş + yüklenmiyor" karesi ve rafların kaybolup geri gelmesi (titreme) engellendi.
- **Sayfalama Shimmer Çakışması:** Diğer sekmelerde shimmer yalnızca henüz hiç sonuç yokken çizilir; sayfalama ve yenilemede mevcut sonuçların üstüne binmez.

### 🎯 2. Veri Doğruluğu, Kapsam Süzme ve Alakalılık (`SearchRelevance`)
- **Doğru Kapsam Planlaması (`planAllSources`):**
  - Manhwa / Manga / Manhua / Light Novel kapsamında TMDB ve Simkl'e istek atılmaz; çizgi roman aramalarında alakasız live-action filmler çıkmaz.
  - Diziler / Filmler / K-Drama aramaları anime veritabanlarına düşürülmez; yalnızca TMDB ve Simkl sorgulanır.
  - Karakter / Personel araması "Tümü" motorunda medya raflarını tetiklemez.
- **Türe Duyarlı Tekilleştirme:** Aynı ID'ye sahip anime ve manga kayıtlarının birbirini ezmesi (`kaynak_tur_kimlik`) önlendi; Manhwa ve Manga kayıtları anime altında kaybolmaz (dönüşümlü `interleave` birleştirme).
- **Yeni Alakalılık Motoru (`SearchRelevance`):** Sorguyla doğrudan eşleşen başlıklar en öne alınır, kayıt silinmez. Türkçe karakter ve aksan indirgeme ("İstanbul" ↔ "istanbul") ve yabancı takma ad koruması eklendi.
- **Jikan & AniList İyileştirmesi:** Arama sonuçlarını bozan zorunlu popülerlik sıralaması kaldırıldı, metin alakalılığı ön plana çıkarıldı.

### ⌨️ 3. Onaysız Arama Kaldırıldı (Onaylı & Butonlu Arama)
- **Yazarken Kendiliğinden Arama Engellendi:** Her tuş vuruşunda sorgu atan debounce kaldırıldı. Arama artık yalnızca klavyedeki "Ara" onay tuşuna veya arama butonuna basıldığında tetiklenir.
- **Arama Onay Butonu:** Arama çubuğuna metin yazıldığında görünen arama butonu eklendi.
- **Kompakt Motor Hapı:** Küçük ekranlarda metin alanının daralmaması için yazma esnasında motor seçici kompakt moda geçer.

### 📦 4. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇬🇧 English (v2.4.213)

### 🔍 1. Search Screen: Shelf Shimmer Consistency (Shimmer Fix)
- **Eliminated Duplicate Shimmers:** Removed the redundant 5-shelf shimmer overlay on the "All" tab. Each provider shelf now owns its visual state: displaying shimmer until its data arrives, then immediately transforming into content.
- **Flicker Elimination:** Loading flags are populated immediately upon search initiation, removing the intermediate "empty/idle" flicker frame.
- **Pagination Shimmer Overlap:** Shimmer placeholders in single-engine tabs only render when the result set is completely empty, preventing overlay during pagination.

### 🎯 2. Data Accuracy, Scope Routing & Relevance (`SearchRelevance`)
- **Scoped Provider Planning (`planAllSources`):**
  - Manga / Manhwa / Manhua / Light Novel scopes bypass TMDB and Simkl completely, eliminating live-action film bleed into print searches.
  - TV Shows / Movies / K-Drama queries route exclusively to TMDB and Simkl without querying anime databases.
  - Character / Staff scopes never trigger media shelves.
- **Type-Aware Deduplication:** Multi-engine keys now include media type (`source_type_id`), preventing legitimate manga records from being dropped when sharing an ID with an anime. Anime and manga are interleaved in mixed searches.
- **Search Relevance Engine (`SearchRelevance`):** Query matches are ranked to the top without pruning non-exact matches (preserving synonyms, romaji, and alternate titles). Added Turkish diacritic folding ("İstanbul" ↔ "istanbul").
- **Jikan & AniList Ranking Fix:** Stripped artificial popularity overrides on search queries in favor of pure text relevance.

### ⌨️ 3. Explicit Search Triggering (Debounce Removed)
- **No Keystroke Auto-Search:** Eliminated automatic debounced searching while typing. Searches only execute upon tapping the keyboard action button or the search icon.
- **Search Action Button:** Added an explicit search trigger button in the search bar when query text is present.
- **Compact Engine Selector:** Engine selector switches to compact mode while typing on small screens to preserve input field width.

### 📦 4. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.213-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.212)

### 📥 1. Android 10 (API 29) ve Üzeri İndirme / Kaydetme Düzeltmesi (ENOENT Fix)
- **Kök Neden Giderildi:** Galeri ve detay ekranlarından görsel indirirken Android 10 ve üzeri Scoped Storage kısıtlamaları nedeniyle meydana gelen `open failed: ENOENT (No such file or directory)` hatası çözüldü.
- **Doğru MediaStore Koleksiyonu:** `MediaStore.Images` yerine Android 10 standartlarına uygun olarak `MediaStore.Downloads.EXTERNAL_CONTENT_URI` ile `Download/Kitsugi/Images` yolu kullanıldı; alternatif olarak `Pictures/Kitsugi/Images` desteği eklendi.
- **Kademeli Güvenli Kayıt (Fallback):** Scoped Storage veya özel ROM kısıtlaması olan cihazlarda doğrudan dosya yazımı engellense dahi `getExternalFilesDir` yedekleme mekanizmasıyla görsellerin her koşulda sıfır hatayla kaydedilmesi sağlandı.
- **Depolama İzinleri & Bayraklar:** `AndroidManifest.xml` içerisine `requestLegacyExternalStorage="true"` eklendi ve `WRITE_EXTERNAL_STORAGE` izni Android 10 (`maxSdkVersion=29`) için de geçerli kılındı.
- **Galeri Otomatik Tarama:** İndirilen resimler `MediaScannerConnection` ile anında taranarak cihazın yerel Galeri ve Fotoğraflar uygulamalarında hemen görünür hale getirildi.

### 📦 2. Dağıtım
- Yalnızca **FOSS** sürümü (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇬🇧 English (v2.4.212)

### 📥 1. Android 10+ (API 29) Image Download & Storage Fix (ENOENT Fix)
- **Root Cause Resolved:** Fixed `open failed: ENOENT (No such file or directory)` occurring when downloading gallery artwork on Android 10+ due to Scoped Storage directory restrictions.
- **Proper MediaStore Target:** Switched from `MediaStore.Images` (which rejects `DIRECTORY_DOWNLOADS` on Android 10) to `MediaStore.Downloads.EXTERNAL_CONTENT_URI` for `Download/Kitsugi/Images`, with `Pictures/Kitsugi/Images` fallback.
- **Graceful Multi-Tier Fallback:** Implemented seamless fallback to app-specific external storage (`getExternalFilesDir`) if public storage creation is restricted on customized OEM ROMs, guaranteeing zero download failures.
- **Manifest Storage Compatibility:** Added `requestLegacyExternalStorage="true"` and extended `WRITE_EXTERNAL_STORAGE` to `maxSdkVersion=29`.
- **Immediate Gallery Indexing:** Invoked `MediaScannerConnection` after write so downloaded media shows up immediately in system Gallery and Photos apps.

### 📦 2. Distribution
- Strictly released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.212-foss.apk`).

---

## 🇹🇷 Türkçe (v2.4.211)

### 🎌 1. Kapsamlı Bangumi Başlık ve İsim Yerelleştirmesi (Bangumi Localization)
- **Merkezi İsim Yerelleştirici (`BangumiNameLocalizer`):** Bangumi'den gelen İngilizce (English), Romaji, Japonca/Özgün (Native), Çince ve alternatif adları (`alias`) koruyan merkezi yerelleştirme katmanı eklendi.
- **Kullanıcı Başlık Dili Tercihi:** Başlık dili tercihi (İngilizce / Romaji / Japonca-Özgün) Bangumi için de tam olarak uygulanır; eksik alanlarda güvenli geri dönüş zinciri çalışır.
- **Çince Önceliği Kaldırıldı:** Bölüm isimlerinde ve varsayılan konu başlıklarında Çince ismin zorunlu önceliği kaldırıldı; orijinal ve İngilizce başlıklar ön plana çıkarılır.
- **Bağımsız İngilizce İsim Desteği:** Karakter ve yapım ekibi modellerinde İngilizce ve Romaji adlar birbirinden ayrıldı.
- **Tüm Ekranlarda Aktif:** Arama, keşfet, medya detayları, karakterler, seslendirmenler, ekip, ilişkiler, öneriler, profil/favoriler, içe aktarma ve Android TV ekranlarının tamamında yerelleştirilmiş isimler uygulandı.

### 🎮 2. Oynatıcı Arayüzü, Tema Uyumu ve Media3 ↔ MPV Paritesi
- **Sabit Gösterge Konumları:** Parlaklık göstergesi ekranın sağında, ses göstergesi solunda sabitlendi (kaydırma jest bölgeleri kullanıcının tercihine göre çalışmaya devam eder).
- **Yenilenen Altyazı Stil Paneli:** Yatay modda ekran dışına taşma sorunu giderildi; panel ekran sınırlarına sabit, kaydırılabilir ve çentik/sistem çubuklarını (safe-area) dikkate alacak şekilde yeniden yapılandırıldı.
- **Uygulama Teması ve Vurgu Rengi (`PlayerAccentTheme`):** Oynatıcı artık seçili tema rengini ve AMOLED modunu alıyor; diyalog, panel, kaydırıcı, buton ve sekmelerdeki sabit renkler seçili tema vurgu rengine uyarlandı.
- **Dahili Oynatıcı Yedekleme Zinciri:** Ana oynatıcıda (Media3) oynatma hatası meydana geldiğinde doğrudan ikinci dahili motor olan MPV'ye geçilir (harici uygulamaya düşme kaldırıldı). Motor geçişinde video sıfırdan başlamaz, kalınan saniyeden devam eder.
- **Ortak Altyazı Öncelik Politikası (`SubtitleSelectionPolicy`):** Hem Media3 hem MPV için tek kural: Gömülü Türkçe > Harici Türkçe > Tercih edilen diğer diller. `tr-TR` ve `tr_TR` gibi bölgesel etiketler Türkçe olarak tanınır.
- **Altyazı ve Ses Düzeltmeleri:** Önbellekteki harici altyazıların bozuk `file://$sub.url` yolu düzeltildi; harici altyazılar benzersiz ID ile eşleştirilir. MPV motoruna ayrı ses akışı (`audioUrl`) harici parça olarak eklendi.

### 🌐 3. CloudStream & Akış İyileştirmeleri
- `domain_fixes.json` blocked listesi temizlendi; Türk eklentileri üzerindeki sessiz atlama kapıları kaldırılarak gerçek hata durumu kartlarda gösterildi.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.211-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.211)

### 🎌 1. Comprehensive Bangumi Title and Name Localization
- **Central Name Localizer (`BangumiNameLocalizer`):** Dedicated localization engine preserving Bangumi English, Romaji, native/Japanese, Chinese, and alias records.
- **User Language Preference:** The global title language preference (English, Romaji, Native/Japanese) is now fully applied across all Bangumi surfaces with graceful fallback logic.
- **Removed Chinese Default Bias:** Forced Chinese title precedence on episode titles and main subjects has been removed in favor of user preferences.
- **Distinct English Names:** Added dedicated English naming fields for character and staff models so English and Romaji are no longer conflated.
- **Omnipresent UI Support:** Applied across search, explore, media details, characters, voice actors, staff credits, relations, recommendations, profiles/favorites, imports, and Android TV screens.

### 🎮 2. Player UI, Theme Accent & Media3 ↔ MPV Parity
- **Fixed Indicator Placement:** Brightness indicator is fixed on the right screen edge, volume indicator on the left (swipe gesture regions remain configurable).
- **Redesigned Subtitle Style Panel:** Eliminated horizontal overflow in landscape mode; panel is now bounded, scrollable, and aware of notches and system bars.
- **Dynamic Theme Accent (`PlayerAccentTheme`):** Player now inherits the active app theme color and AMOLED settings; hardcoded colors in sheets, dialogs, sliders, and buttons replaced with the chosen accent color.
- **Internal Engine Fallback Chain:** Playback failures on the primary engine (Media3) automatically switch to the secondary internal engine (MPV) rather than dropping to an external player. Playback resumes from the exact failure position.
- **Shared Subtitle Selection Policy (`SubtitleSelectionPolicy`):** Unified policy across both Media3 and MPV: Embedded Turkish > External Turkish > Other preferred languages. Regional language tags (e.g. `tr-TR`, `tr_TR`) properly recognized.
- **Track & Path Fixes:** Resolved broken `file://$sub.url` paths for cached external subtitles; external tracks identified by unique IDs. Added separate audio stream (`audioUrl`) loading support to MPV.

### 🌐 3. CloudStream & Streaming Refinements
- Cleared broken provider blocklists in `domain_fixes.json`, removed silent skip gates, and surfaced diagnostic error messages.

### 📦 4. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.211-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.210)

### 🎌 1. Bangumi Profil Sekmesi ve Koleksiyon Yönetimi
- **Profil Seçicisine Eklendi:** Profil kaynağı seçicisine (Profile Source Picker) "Bangumi" seçeneği eklendi. Hesap bağlıysa `@kullanıcıadı` görüntülenir.
- **Kapsamlı Profil Başlığı:** Kullanıcı avatarı, takma adı, kullanıcı adı, kişisel imzası (`sign`) ve tek tıkla açılan "bgm.tv profili" butonu.
- **Koleksiyon Listesi ve Filtreler:** Tür (Anime, Kitap/Manga, Oyun, Müzik, Dizi/Film) ve izleme durumu filtreleri, özet koleksiyon sayaçları, bölüm/cilt ilerleme göstergesi ve kullanıcı puanı.
- **Favori Karakterler ve Kişiler:** Kullanıcının Bangumi'de favorilediği karakterler ve kişiler profil ekranında listelenir.
- **Doğrudan Detay Ekranı:** Anime ve manga koleksiyon kartlarına dokunulduğunda doğrudan uygulama içi detay ekranı açılır.
- **Bildirimler:** Resmi Bangumi API'sinde bildirim ucu bulunmadığı için uygulama içi WebView ile doğrudan ve güvenli `bgm.tv/notify/all` sayfası açılır.

### 👤 2. Uygulama İçi Karakter ve Kişi Detayları & Favori Eşitleme
- **Uygulama İçi Detay Sayfaları:** Bangumi karakter ve kişi (yapım ekibi/seiyuu) sayfaları artık tarayıcıya yönlendirilmeden doğrudan uygulama içinde açılır (`KitsugiBangumiCreditsClient` ve `KitsugiBangumiDetailClient`).
- **Bangumi Favori (Kalp) Entegrasyonu:** Bangumi hesabı bağlıyken karakter veya kişi sayfasındaki favori durumu canlı olarak Bangumi sunucusuyla eşitlenir; ekleme ve çıkarma anında yazılır, ağ hatasında güvenli bir şekilde eski haline döner.
- **Dolu Karakter ve Ekip Sekmeleri:** Bangumi konularında "Karakterler" ve "Ekip" sekmeleri v0 OpenAPI ve p1 web uçlarıyla eksiksiz doldurulur.
- **Doğru Paylaşım Bağlantıları:** Karakter ve kişi paylaş butonları artık `bgm.tv/character/{id}` ve `bgm.tv/person/{id}` bağlantılarını üretir.

### 🎮 3. Oynatıcı Jest Bölgeleri, PiP Butonları ve Ses Yönetimi
- **Kaydırıcı ve Dokunma Hizalaması:** Ekrandaki kaydırıcı yerleşimiyle dokunma bölgeleri eşitlendi: Sol yarı parlaklık, sağ yarı ses (varsayılan). Ayar menüsündeki yön metinleri düzeltildi.
- **Çalışan PiP Tuşları:** Mini oynatıcı (PiP) penceresindeki Oynat, Duraklat ve Sonraki Bölüm tuşları Compose oynatıcı köprüsüne (`PipPlayerCallback`) bağlandı.
- **Arka Plan Ses Kesilmesi:** PiP kapatıldığında veya uygulama arka plana alındığında sesin çalmaya devam etmesi `onStop` ve `onDestroy` yaşam döngüsü kontrolleriyle engellendi.

### 🌐 4. CloudStream Kaynak Akışı ve Domain İyileştirmeleri
- **Sessiz Atlama Kapıları Kaldırıldı:** `KNOWN_BROKEN_DOMAINS` ve `domain_fixes.json` blocked listesi eşleştiğinde eklentiler artık peşinen atlanıp sıfır sonuca düşürülmüyor; her zaman denenir ve gerçek hata (DNS/Cloudflare/timeout vb.) UI kartında kullanıcıya bildirilir (`CsPluginStatusTracker.recordSkip`).
- **Domain Listesi Çelişkileri Çözüldü:** `4kfilmizlesene.nl` ve `666filmizle.site` ölü domainler listesinden çıkarıldı.
- **Temizlenen Blocked Listesi:** `domain_fixes.json` dosyasındaki gereksiz yere TR eklentilerini öldüren blocked listesi boşaltıldı (+18 filtreleme uygulamada `ADULT_PLUGINS` ile ayrı olarak yönetilir).
- **Gerçek Hata Gösterimi:** Boş akış döndüğünde "Bu anime için akış bulunamadı" yerine `CsPluginStatusTracker`'dan gelen gerçek sebep gösterilir.
- **Taze Domain Önceliği:** Dinamik güncel domain tablosundan gelen taze domainler, sabit `KNOWN_BROKEN_DOMAINS` listesi tarafından engellenmez.
- **Revert Kararlılığı:** Özgün domaine dönüldükten sonra aynı oturumda yerleşik tablonun tekrar zorlanması engellendi.
- **Güncelleme Geri Çekilme (Backoff):** 404 veren eklentiler için 12 saatlik bekleme süresi uygulandı (`CsAutoUpdateBackoff`).

### 🛠️ 5. Gelişmiş Çökme Teşhisi ve Oturum Denetimi (Crash Diagnostics Overhaul)
- **Native İz Genişletmesi:** Native çökme yığın izi sınırı 48'den 96 çerçeveye çıkarıldı; PC (Program Counter) ve LR (Link Register) bilgileri eklendi.
- **Bellek Haritası Desteği (`/proc/self/maps`):** Çökme anında bellek haritası kaydedilerek native adreslerin ilgili `.so` dosya adına ve ofsetine çözülebilmesi sağlandı.
- **Doğru Süreç ve Ekran Tespiti:** Teşhis raporundaki "Ölüm Sebebi" artık sadece çöken oturumun PID'sine odaklanır; "Son Ekran" bilgisi eylem izinden doğrulanarak gösterilir.
- **Genişletilmiş Kanıt Penceresi:** Sessiz kapanmalarda native iz okuma penceresi 30 dakikadan 7 güne çıkarıldı.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca FOSS varyantı** (`assembleFossRelease`) derlendi (`Kitsugi-Beta-v2.4.210-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.210)

### 🎌 1. Bangumi Profile Tab & Collection Management
- **Added to Profile Picker:** Bangumi is now available in the profile source selector, displaying `@username` when connected.
- **Full Profile Header:** Displays user avatar, nickname, username, personal signature (`sign`), and a direct link to open the bgm.tv web profile.
- **Collections & Filters:** Category (Anime, Manga/Book, Game, Music, Real/Drama) and status filters, collection counters, episode/volume progress, and user ratings.
- **Favorite Characters & People:** Highlights the user's favorited Bangumi characters and staff members directly on the profile screen.
- **In-App Navigation:** Tapping anime or manga entries seamlessly opens the existing in-app media detail page.
- **Notifications:** Since Bangumi does not provide a public notification API endpoint, notifications open via an in-app WebView targeting `bgm.tv/notify/all`.

### 👤 2. In-App Character & Staff Detail Pages & Bangumi Favorites
- **Native In-App Views:** Bangumi character and person/staff pages now render natively in-app (`KitsugiBangumiCreditsClient` & `KitsugiBangumiDetailClient`) rather than opening externally.
- **Live Favorite Toggle:** When logged in with Bangumi, tapping the favorite heart on character/person screens syncs directly to Bangumi with rollback on error.
- **Populated Credits Tabs:** Subjects on Bangumi now populate both "Characters" and "Staff" tabs via official v0 OpenAPI with p1 fallbacks.
- **Accurate Share URLs:** Sharing characters or staff members now correctly generates `bgm.tv/character/{id}` and `bgm.tv/person/{id}` links.

### 🎮 3. Player Gesture Zones, PiP Controls & Audio Lifecycle
- **Slider & Touch Zone Alignment:** Touch regions now match on-screen sliders: Left half controls brightness, right half controls volume (default). Settings labels aligned.
- **Functional PiP Actions:** Mini player (PiP) Play, Pause, and Skip Next buttons are wired to the player engine via `PipPlayerCallback`.
- **Background Audio Stop:** Playback reliably pauses upon closing PiP or backgrounding the activity via `onStop` and `onDestroy`.

### 🌐 4. CloudStream Stream & Domain Enhancements
- **Eliminated Silent Skip Gates:** Extensions matching `KNOWN_BROKEN_DOMAINS` or `domain_fixes.json` blocked list are no longer prematurely skipped; all extensions are executed and real errors (DNS, Cloudflare, timeouts) are surfaced directly on stream cards (`CsPluginStatusTracker.recordSkip`).
- **Domain Contradictions Resolved:** `4kfilmizlesene.nl` and `666filmizle.site` removed from broken domain blocklist.
- **Cleared Blocked List:** Cleared `domain_fixes.json` blocked list that previously incapacitated legitimate Turkish streaming providers (+18 filtering is handled strictly via `ADULT_PLUGINS`).
- **Real Diagnostic Error Display:** When stream discovery yields empty results, the actual root cause from `CsPluginStatusTracker` is displayed instead of a generic "No streams found" placeholder.
- **Fresh Domain Priority:** Fresh remote domains override the hardcoded `KNOWN_BROKEN_DOMAINS` blocklist.
- **Stable Revert Protection:** Restored domains are protected against immediate builtin overrides in the same session.
- **Update Backoff:** 12-hour retry backoff on 404/missing extensions (`CsAutoUpdateBackoff`).

### 🛠️ 5. Enhanced Crash Diagnostics & Session Supervisor
- **Extended Native Trace:** Increased native stack frames limit from 48 to 96, capturing PC and LR registers.
- **Memory Maps Dump (`/proc/self/maps`):** Recorded address maps allow translating raw crash addresses to `.so` library names and offsets.
- **Precise PID & Last Screen Tracking:** Root cause attribution strictly targets the crashing PID session; the last visited screen is inferred from the action breadcrumbs.
- **Extended Evidence Window:** Silent crash native trace retention window prolonged to 7 days.

### 📦 6. Distribution
- Strictly built and released the **FOSS variant only** (`assembleFossRelease` -> `Kitsugi-Beta-v2.4.210-foss.apk`). GMS is completely excluded.

---

## 🇹🇷 Türkçe (v2.4.209)

### 🎌 1. Bangumi Liste İçe Aktarma ve Eşitleme 404 Kök Neden Çözümü
- **Kullanıcı Adı Çözümlemesi:** Bangumi sunucusunda `"-"` takma adının yalnızca yazma uçlarında geçerli olması ve okuma ucunda (`GET /v0/users/-/collections`) 404 vermesi sorunu çözüldü. Artık saklanan kullanıcı adı, yoksa `/v0/me`, o da yoksa sayısal ID dinamik olarak çözümlenerek gerçek kullanıcı adına istek atılıyor.
- **Koleksiyon Uçları Güvenliği:** `getUserCollections`, `getAllUserCollections` ve `getUserCollection` fonksiyonlarındaki hatalı `"-"` varsayılan değeri kaldırılarak yanlışlıkla 404 hatasına düşülmesi engellendi.
- **Durum Kontrolü:** `BangumiSyncManager.fetchRemoteStatus` içerisindeki aynı hata giderilerek uzaktaki izleme/okuma durumu güvenle alınır hale getirildi.

### 🔍 2. Bangumi Arama Motoru İyileştirmeleri ve Yedek Uç
- **Yetişkin İçerik (NSFW) Filtresi:** Yetişkin içerik açıkken sunucuya metin (`"include"`) olarak gidip 400 hatası oluşturan `filter.nsfw` parametresi, sunucunun beklediği JSON boolean formatına dönüştürüldü (`false` veya filtresiz).
- **Sayfa Boyutu ve Atlama Düzeltmesi:** Sunucu limiti 20 olduğu halde istemcinin 24 kayıt istemesi ve offset hesaplamasında her sayfada 4 kaydın atlanması sorunu giderildi; tüm Bangumi aramalarında sayfa boyutu 20'ye sabitlendi.
- **Eski Uç Yedeği (Legacy Search Fallback):** v0 deneysel arama ucu hata verirse veya boş dönerse `GET /search/subject/{q}` eski arama ucu otomatik devreye girer.
- **Şeffaf Hata Bildirimi:** Arama hataları artık "Sonuç bulunamadı" ardına gizlenmez; sunucudan dönen gerçek hata mesajı ekranda gösterilir ("Tümü" karma rafında da anime ve manga hataları yüzeye çıkarılır).

### 🎬 3. Kapsamlı Bangumi Detay Sayfası ve Çapraz Kimlik Eşleştirme (`KitsugiBangumiDetailClient`)
- **Doğrudan Yerel Veri Doldurma:** Bilgi, karakterler (seslendirmenli), yapım ekibi, ilişkiler, öneriler, puan dağılım grafikleri, incelemeler/yorumlar ve bölümler doğrudan Bangumi'nin kendi verisinden (v0 ve p1 uçları) anında doldurulur.
- **Karakter ve Kişi Detay Sayfaları:** Bangumi kaynaklı karakter ve yapım ekibi detay sayfaları p1 uçları üzerinden eksiksiz çalışır hale getirildi.
- **Akıllı Çapraz Kimlik Eşleme:** Bangumi kaydı başlık, yıl, bölüm sayısı ve format puanlamasıyla AniList, MAL ve ARM üzerinden TMDB/Kitsu/TVDB/IMDb ile güvenli şekilde eşleştirilir.
- **Zenginleştirilmiş Galeri:** Resimler sekmesine Fanart.tv, TMDB, Shikimori, MAL ve AniList görselleri dahil edildi. `lain.bgm.tv` kaynaklı aynı görselin farklı boyut kopyaları kanonik anahtar ile tekil görsele indirildi.
- **Room Önbellek Sürümlemesi:** Önceki sürümlerin eksik/hatalı Kitsu aramasıyla önbelleğe aldığı satırların okunmasını engellemek için önbellek anahtarına `_bgm1` sürüm eki getirildi.
- **Bölüm Puanları ve Logolar:** MDBList, logo ve bölüm puanları eşleşen çapraz kimlikler üzerinden kusursuz çalışır.

### 📦 4. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.209)

### 🎌 1. Bangumi Collection Import & Sync 404 Root Cause Fix
- **Dynamic Username Resolution:** Resolved the issue where Bangumi rejected the `"-"` alias on read endpoints (`GET /v0/users/-/collections`) with 404 Not Found. Real username is now dynamically determined via stored name, `/v0/me`, or numeric ID.
- **Safe Collection Endpoints:** Removed the fallback `"-"` default in `getUserCollections`, `getAllUserCollections`, and `getUserCollection` to prevent accidental regressions.
- **Sync Status Audit:** Fixed identical issue in `BangumiSyncManager.fetchRemoteStatus` so remote progress sync works reliably.

### 🔍 2. Bangumi Search Improvements & Legacy Fallback
- **NSFW Boolean Filtering:** Fixed `filter.nsfw` sending string `"include"` which caused HTTP 400 Bad Request; now correctly sends JSON boolean or omits when inactive.
- **Page Size Alignment:** Standardized page size and pagination offsets to 20 to match Bangumi's hard limit, preventing 4-item skipping and false "no more pages" triggers.
- **Legacy Fallback Endpoint:** If experimental v0 search fails or returns empty, fallback to legacy `GET /search/subject/{q}` seamlessly engages.
- **Surfaced Error Messages:** API errors are no longer silently masked as "No results found"; descriptive server messages are displayed across both isolated and mixed search views.

### 🎬 3. Full Native Bangumi Media Details & Cross-ID Enrichment (`KitsugiBangumiDetailClient`)
- **Native Details & Tabs:** Information, characters with voice actors, staff, relations, recommendations, score distributions, reviews/comments, and episodes load immediately using native Bangumi APIs (v0 and p1).
- **Character & Staff Pages:** Native Bangumi character and person profile pages are fully supported via p1 endpoints.
- **Cross-ID Resolution:** Bangumi subjects are matched with AniList, MAL, and ARM (TMDB/Kitsu/TVDB/IMDb) using strict title, year, episode count, and format scoring.
- **Enhanced Gallery & Dedup:** Gallery tabs are enriched with fanart.tv, TMDB, Shikimori, MAL, and AniList images. Duplicate covers from `lain.bgm.tv` across various size buckets are deduplicated into single items.
- **Room Cache Busting:** Added `_bgm1` suffix to Bangumi detail cache keys, preventing stale/erroneous legacy Kitsu cache entries from loading.
- **Episode Ratings & Logos:** MDBList, clearlogos, and episode rating curves now correctly resolve using the linked cross-IDs.

### 📦 4. Distribution
- Built and published strictly as a **FOSS release** (`Kitsugi-Beta-v2.4.209-foss.apk`). GMS flavor is omitted.

---

## 🇹🇷 Türkçe (v2.4.208)

### 🏷️ 1. AniList İsim Dili (Romaji / İngilizce / Japonca) Yerelleştirmesi
- **Karakter, Seslendirmen & Yapım Ekibi (Staff) İsimleri:** AniList'ten gelen kişi ve karakter adları artık kullanıcının tercih ettiği isim diline (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`) göre dinamik olarak gösteriliyor.
- **Latin & Orijinal Ad Uyumu (`AniListPersonName.kt`):** AniList sorgularında `userPreferred` yerine `full`, `native` ve alternatif adlar çekilerek Romaji/İngilizce tercihinde Latin harfli isimler önceliklendirildi. Latin harfli karşılık bulunamadığında orijinal ad korunur.
- **Temiz Başlık & Alt Başlık:** Japonca/Yerel ad seçildiğinde alt başlıktaki mükerrer isimler temizlenir ("Karakter" veya doğrudan rol açıklaması gösterilir).
- **Kapsam:** Arama sonuçları, yapım detay sekmeleri (`CharactersTab`), API detay sayfası, yerel kütüphane detay sayfası, karakter detayı ve yapım ekibi detay sayfalarının tümü güncellendi.

### 📜 2. Akıllı Kaydırma Konumu Koruma (Scroll Retention)
- **Detaydan Geri Dönüş Güvencesi:** Bir listenin ortasından detay sayfasına girilip geri dönüldüğünde ekranın başa sarması ve kaydırma konumunun sıfırlanması tamamen önlendi.
- **Listem (My List):** 7 kütüphane sekmesinin (Tümü, İzlediklerim, İzleyeceklerim, Bıraktıklarım vb. ve 🎌 Bangumi) her biri için kaydırma konumu ekran kapansa dahi bağımsız saklanır (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Arama & Çoklu Platform Rafları:** Arama sayfası ve kaynağa özel arama (`SourceSearchPage`) için `rememberRetainedLazyListState` entegre edildi. Sonuçlar henüz yüklenirken veya yenilenirken listenin 0'a sıfırlanması engellendi. `SourceSearchOwner` ile izole ViewModel durumu geri dönüşte korunur.
- **Keşfet & Tam Ekran Izgaralar:** `FullScreenMediaGridPage` ve `AddonFullScreenGridPage` `SaveableStateProvider` ve derinlik takibi (`openingStackDepth`) ile korunarak detaydan dönüşte tam kaldığı noktaya döner.

### ✨ 3. Kartlarda Neon Degrade Çerçeve ve Parıltı (List Grid Glow)
- **Kare Izgara Kartları (`KitsugiMediaEntryCard`):** Kare poster görünümündeki kartlara zarif degrade çerçeve ve neon parıltı efekti (`kitsugiNeonGlow`) uygulandı. Listem sekmelerine ve Favorilerim ekranına modern, canlı bir görünüm kazandırıldı.
- **Kullanıcı Listeleri (`UserMediaListCards`):** Kaynak kullanıcı profil listelerindeki hem kare (grid) hem yatay satır (row) kartlarına degrade çerçeve ve parıltı stili dahil edildi.

### 🛠️ 4. MyAnimeList / Jikan Detay Sekmeleri Kök Neden Çözümü (`MalJikanMediaSupport`)
- **Sekme Donması ve Boş Kalma Çözümü:** MAL kaynaklı detay sayfalarında Karakterler, Ekip, Öneriler, İlişkiler, İstatistikler ve Yorumlar sekmelerindeki iskelet takılması ve boş kalma sorunu giderildi.
- **Tek Ortak Kural:** `mal`, `jikan`, `myanimelist` kimlik uzayları `MalJikanMediaSupport.canonicalSource` altında birleştirildi; `Movie` ve `TvShow` tiplerinin Jikan uç noktalarında (`anime`) veya AniList tipinde (`ANIME`) yanlışlıkla `MANGA`ya düşmesi engellendi.
- **Akıllı Hiyerarşi:** Jikan birincil kaynak yapıldı; AniList'ten toplu zenginleştirme için 25 saniye bekleme kaldırıldı (veriler anında ekrana gelir). Jikan boş döndüğünde ise karmaşık ARM araması öncesi doğrudan aynı MAL ID'si ile AniList denenerek anında sonuç üretilir.

### 🎬 5. Çökme ve Kurtarma Pencerelerinde Canlı Animasyon (`KitsugiCrashAnimation`)
- **Canlı Çökme Görseli:** Çökme ekranı (`KitsugiCrashActivity`) ve başlangıç kurtarma penceresinde (`KitsugiCrashRecoveryDialog`) statik hata ikonu yerine `res/raw/crash_animation.mp4` döngüsel animasyonu eklendi.
- **Sıfır Ek Yük ve Güvenlik:** Android'in yerel `TextureView` ve `MediaPlayer` altyapısı kullanılarak sessiz ve kesintisiz döngü sağlandı; donanım düzeyinde hata koruması sayesinde çökme ekranının kendisinin hata vermesi tamamen engellendi.

### 📦 6. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve GitHub'a yüklendi (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.208)

### 🏷️ 1. AniList Person Name Localization (Romaji / English / Native)
- **Characters, Voice Actors & Staff:** AniList person and character names now dynamically conform to the user's selected name/title language preference (`ROMAJI`, `ENGLISH`, `NATIVE`, `JAPANESE_STAFF`).
- **Latin & Native Fallback (`AniListPersonName.kt`):** Queries now request `full`, `native`, and alternate names instead of solely relying on `userPreferred`. When Romaji or English is selected, Latinized names take precedence; if no Latin name exists, the original name is safely preserved.
- **Clean Subtitles:** Redundant name prefixes in subtitles when native names are active are cleanly stripped (displaying "Character" or role details).
- **Universal Coverage:** Applied across Search results, Media Detail tabs (`CharactersTab`), API and Local library detail pages, Character Detail, and Staff Detail pages.

### 📜 2. Smart Scroll Retention Across All Lists
- **Seamless Navigation Return:** Returning from a detail screen to any list or search view now accurately preserves scroll position instead of snapping to top.
- **My List Tabs:** Each of the 7 library tabs (including 🎌 Bangumi) retains its scroll index and pixel offset independently across tab switching and detail navigation (`savedTabIndices`, `savedTabOffsets`, `restoredTabs`).
- **Search & Source Search:** Integrated `rememberRetainedLazyListState` to prevent intermediate empty/loading states from clamping scroll offsets to 0. `SourceSearchPage` leverages `SourceSearchOwner` to keep isolated ViewModel state alive during nested navigation.
- **Explore & Full-Screen Grids:** Wrapped `FullScreenMediaGridPage` and `AddonFullScreenGridPage` in `SaveableStateProvider` with depth tracking (`openingStackDepth`) for accurate back-stack restoration.

### ✨ 3. Neon Gradient Border & Glow on Media Cards
- **Grid Cards (`KitsugiMediaEntryCard`):** Added subtle neon glow and gradient border accents (`kitsugiNeonGlow`) to poster grid media cards across My List and Favorites.
- **User Profile Lists (`UserMediaListCards`):** Extended neon gradient styling to both grid and row cards within remote user profile lists.

### 🛠️ 4. MyAnimeList / Jikan Detail Tabs Unification (`MalJikanMediaSupport`)
- **Stall & Empty Tab Resolution:** Fixed root causes where Characters, Staff, Recommendations, Relations, Stats, and Reviews tabs on MAL-sourced items remained stuck on skeletons or empty.
- **Unified Identity Space:** Normalized `mal`, `jikan`, and `myanimelist` under `MalJikanMediaSupport.canonicalSource`. Corrected `Movie` and `TvShow` mapping to prevent accidental `MANGA` endpoints.
- **Instant Response Flow:** Made Jikan the primary responsive source without waiting 25s for AniList batch cover enrichment. If Jikan is empty, AniList is queried with the exact MAL ID before escalating to ARM/Shikimori resolvers.

### 🎬 5. Smooth Looping Animation for Crash & Recovery Screens (`KitsugiCrashAnimation`)
- **Dynamic Error Graphic:** Replaced static bug icons in `KitsugiCrashActivity` and `KitsugiCrashRecoveryDialog` with a sleek looping video animation (`res/raw/crash_animation.mp4`).
- **Zero Overhead & Robust Fallbacks:** Built on Android's native `TextureView` and `MediaPlayer` for seamless looping, zero audio intrusion, and complete crash immunity.

### 📦 6. Distribution
- Per user specification, **only the `foss` release APK** was built and released (`Kitsugi-Beta-v2.4.208-foss.apk`). GMS is strictly excluded.

---

## 🇹🇷 Türkçe (v2.4.207)

### 🎌 1. Bangumi (bgm.tv) Tam Platform Entegrasyonu (7. Kaynak)
- **OAuth 2.0 & Kimlik Doğrulama:** `kitsugi://bangumi-auth` ve `aniyomi://bangumi-auth` yönlendirme şemalarıyla modern yetkilendirme altyapısı kuruldu. `BangumiAuthStore` ve `BangumiAuthManager` ile "1-Tık Otomatik Giriş" ve manuel yetki kodu girme seçenekleri sağlandı.
- **Kütüphane & İçe Aktarma:** Bangumi koleksiyon durumları (想看/在看/看过/搁置/抛弃 - İzlemek İstiyorum / İzliyorum / İzledim / Beklemede / Bıraktım), 0-10 puanlama sistemi ve bölüm bazlı ilerleme (打格子) Kitsugi kütüphanesine içe aktarma (`BangumiImportManager`) ve iki yönlü senkronizasyon (`BangumiSyncManager`) ile tam entegre edildi.
- **Listem Sekmesi (7. Sekme):** Listem ekranına 7. sekme olarak 🎌 Bangumi kütüphanesi eklendi (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`, `MyListEmptyState`). Özel marka rengi (`#F09199`), rozetler, sayaçlar ve doğrudan sekme içinden açılan hesap bağlama diyaloğu eklendi.
- **Keşfet & Arama Motoru:** Keşfet ekranında 7. platform olarak Bangumi desteği (`AllSourcesExplore`, `ExplorePlatformToggle`, `ExploreViewModel`), Arama motorlarında Bangumi motoru (`KitsugiBangumiClient`, `SearchViewModel`), anime/manga gelişmiş filtreleme, sıralama, karakter ve personel arama kabiliyeti kazandırıldı.
- **Görsel Rozet & Marka:** Gerçek Bangumi vektör logosu (`ic_logo_bangumi.xml`), Hero rozetleri, detay sayfası başlıkları ve galeri rozetleri güncellendi.

### ⚠️ 2. Çapraz Eşitleme (Cross-Sync) Güvenlik & Hukuki Sorumluluk Reddi (Disclaimer)
- **Merkezi Sorumluluk Reddi (`CrossSyncDisclaimer.kt`):** Çapraz eşitlemenin deneysel (beta) bir özellik olduğunu, eşleştirmelerin otomatik yapıldığını ve hiçbir eşleştirmenin %100 doğruluk garantisi taşımadığını; yanlış eşleşme, liste karışması veya platform yaptırımlarından (kısıtlama, askıya alma, hesap yasağı vb.) sorumluluk kabul edilmediğini belirten açık ve kapsamlı uyarı metinleri eklendi.
- **Görünür Uyarılar:**
  - Hesap bağlantıları Cross-Sync satırına "Deneysel ·" etiketi ve kısa uyarı eklendi.
  - Çok Yönlü Eşitleme ve Cross-Sync ayar sayfasına bilgi kartı içine ayrıntılı uyarı, Hızlı İşlem bölümü altına kısa not eklendi.
  - Eşitleme penceresinde (`KitsugiCrossSyncDialog`) güvenlik notunun altına ayrıntılı uyarı yerleştirildi.
- **Çoklu Aday Grup Akrabalık Koruması (`AuthViewModel`):** Birden fazla aday grup bulunduğunda, tekil ortak kimlikle seçilen grup için yıl uyumu (`CrossSyncIdentityGuard.yearsCompatible`) ve başlık akrabalığı kontrolü eklendi; uyuşmayan kayıtların yanlış birleştirilmesi ve başka hesaplardaki doğru kayıtların bozulması engellendi (`identityReviewRequired = true`).
- **Rapor Teşhis Etiketleri:** Eşitleme teşhisinde iç kimlik alanları (`300M+` Kitsu, `100M+` AniList) açıkça etiketlenerek (`describeMalIdField`) MAL kimliği sanılması önlendi.

### ⚡ 3. Jikan Hız Limiti, Shikimori Başlık Dili & Kitsu Paralel İstek İyileştirmeleri
- **Jikan Hız Limiti & 429 Koruması (`KitsugiApiBase`):** Jikan için saniyede 3 ve dakikada 55 istek (kayan pencere) limiti getirildi. 429 yanıtı alındığında host geçici olarak bekletilir ve yeniden deneme sayısı 3'e çıkarılarak sekme/karakter boş kalma sorunları giderildi.
- **Shikimori Rusça Başlık Çözümü (`ShikimoriTitleResolver`):** Shikimori GraphQL API üzerinden tek istekte İngilizce ve Japonca başlıklar çekilerek, kullanıcının ayarladığı tercih diline (Romaji / İngilizce / Japonca) göre gösterilmesi sağlandı.
- **İlişki Tipi Çevirileri (`KitsugiTranslations`):** Shikimori ve Jikan ilişkilerindeki "Sequel", "Side story", "Adaptation", "Prequel" gibi ilişki türleri Türkçeye çevrildi. Jikan ilişkilerine AniList'ten İngilizce/Japonca başlık desteği eklendi.
- **Kitsu Karakter & Seslendirmen Paralel İstekleri (`KitsuClient`):** Karakter ve seslendirmen sayfaları sıralı yerine en fazla 3 eşzamanlı istekle paralel çekilerek sayfa açılış hızı büyük oranda artırıldı.
- **Seslendirmen Birleştirme Zaman Aşımı Optimizasyonu (`KitsugiCharacterClient`):** Kitsu ve Shikimori için seslendirmen bekleme süresi 8 saniyeden 5 saniyeye indirilerek sayfa kilitlenmeleri önlendi.

### 🔍 4. Arama & "Tümünü Gör" Devamlılığı
- `SearchViewModel` ve `SourceSearchPage` arasındaki `openSourceSearch` köprüsü Bangumi motorunu da kapsayacak şekilde yenilendi; raflardan "Tümünü Gör"e tıklandığında sorgu, kapsam ve görünen sonuçlar eksiksiz aktarılır.

### 📦 5. Dağıtım
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (`Kitsugi-Beta-v2.4.207-foss.apk`). GMS kesinlikle hariç tutuldu.

---

## 🇬🇧 English (v2.4.207)

### 🎌 1. Full Bangumi (bgm.tv) Platform Integration (7th Source)
- **OAuth 2.0 & Authentication:** Implemented redirect schemes `kitsugi://bangumi-auth` and `aniyomi://bangumi-auth` with `BangumiAuthStore` and `BangumiAuthManager`, enabling 1-click automatic browser login and manual authorization code entry.
- **Library & Smart Sync:** Full support for Bangumi collections (想看/在看/看过/搁置/抛弃 - Wish/Do/Collect/On Hold/Dropped), 0-10 rating scale, and episode progress grid via `BangumiImportManager` and `BangumiSyncManager`.
- **My List 7th Tab:** Added dedicated 🎌 Bangumi tab to My List (`MyListLibraryGrouping`, `MyListScreen`, `MyListComponents`), complete with signature brand color (`#F09199`), item counters, status chips, and in-tab account connection prompt.
- **Explore & Multi-Engine Search:** Integrated Bangumi as the 7th platform in All Sources Explore (`AllSourcesExplore`, `ExploreViewModel`) and Global Search (`SearchViewModel`, `KitsugiBangumiClient`) with anime/manga advanced filtering, tags, years, and character/staff lookups.
- **Brand Assets & Polish:** Added official vector logo (`ic_logo_bangumi.xml`), hero badges, detail screen headers, and gallery chips.

### ⚠️ 2. Cross-Sync Safety Guards & Legal Disclaimers
- **Centralized Disclaimer (`CrossSyncDisclaimer.kt`):** Clear legal notice that Cross-Sync is an experimental beta feature with no 100% match guarantee, disclaiming liability for mismatched lists, progress desyncs, or upstream platform sanctions (bans, suspensions).
- **Surface Visibility:**
  - Added "Experimental ·" tag and summary disclaimer to Account Connections.
  - Injected full disclaimer inside the Cross-Sync settings info card and quick actions section.
  - Embedded disclaimer directly into the synchronization dialog (`KitsugiCrossSyncDialog`).
- **Multi-Candidate Guard (`AuthViewModel`):** Added year compatibility (`yearsCompatible`) and title kinship checks for single-evidence selections when multiple candidate groups exist, preventing erroneous mergers and cross-account data overwrite (`identityReviewRequired = true`).
- **Diagnostic Clarity:** Explicitly labeled synthetic IDs (`300M+` for Kitsu, `100M+` for AniList) in diagnostics to prevent confusion with real MAL IDs.

### ⚡ 3. Jikan Rate Limiting, Shikimori Titles & Kitsu Concurrency
- **Jikan 429 Prevention (`KitsugiApiBase`):** Enforced a rate limit of 3 req/sec and 55 req/min (sliding window) for Jikan API, with automatic backoff cooldown and 3 retries on HTTP 429.
- **Shikimori English/Japanese Titles (`ShikimoriTitleResolver`):** Resolves English and Japanese titles via Shikimori's GraphQL API instead of defaulting to Russian titles, strictly adhering to user's title language preference.
- **Relation Type Translations (`KitsugiTranslations`):** Translated relation types (Sequel, Side story, Adaptation, etc.) to Turkish. Added AniList title fallback for Jikan relations.
- **Kitsu Concurrency (`KitsuClient`):** Characters and voice actors are now fetched concurrently (up to 3 parallel requests), significantly accelerating page loads.
- **Voice Actor Timeout Optimization (`KitsugiCharacterClient`):** Reduced VA fallback timeout from 8s to 5s for Kitsu and Shikimori to prevent UI stalls.

### 🔍 4. Search Shelf Continuity
- Extended `openSourceSearch` handoff between `SearchViewModel` and `SourceSearchPage` to seamlessly support Bangumi without dropping active query, scope, or cached shelf results.

### 📦 5. Release Distribution
- Built strictly as **FOSS Release** (`Kitsugi-Beta-v2.4.207-foss.apk`) per user instruction. No GMS variant generated.

---

## 🇹🇷 Türkçe (v2.4.206)

### 🛡️ 1. Native & Java Çökme Teşhisi, ANR/OOM Raporlama & Cockroach Çözümü
- **Native Çökme Yakalayıcı (C++ - libkitsugi_crash_handler.so):** Java `UncaughtExceptionHandler`'ın yakalayamadığı native çökmeler (SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP) `_Unwind_Backtrace` ile asenkron sinyal güvenliğinde yakalanır; ham geri iz `native_crash.txt` dosyasına yazılır.
- **Sessiz Ölüm Dedektörü (KitsugiSessionSupervisor):** Uygulama temiz çıkış yapamadan kapandığında (LMKD bellek öldürmesi, ANR sonrası sonlandırma, SIGKILL), bir sonraki açılışta logcat taranarak `unclean_exit.txt` raporu üretilir ve kullanıcıya "Beklenmedik Kapanma" penceresi gösterilir.
- **Eylem İzi (Breadcrumbs):** Son 40 kullanıcı hareketi ve son bulunulan ekran anlık kaydedilir; çökme raporlarında hatanın hangi ekranda ve hangi eylem sırasında gerçekleştiği açıkça görülür.
- **Bağımsız Görevde Açılan Çökme Ekranı:** `Thread.sleep(800)` ana thread bloklaması kaldırıldı. Çökme ekranı (`KitsugiCrashActivity`) ayrı görev (`taskAffinity`) ve süreçte açılarak Android görev temizliğinin çökme ekranını yok etmesi engellendi.
- **Cockroach Sonsuz CPU Döngüsü & Kör Hata Yutma Düzeltildi:** `com.lagradost.cloudstream3` hatalarını körlemesine yutup arayüzü kilitli bırakan mantık düzeltildi; `Looper.loop()` sonlandığında oluşan %100 CPU kilitlenmesi (ANR donmaları) giderildi.
- **Erken Kurulum:** Çökme yakalayıcı `attachBaseContext()` aşamasına çekilerek açılış ve provider çökmeleri de kapsama alındı.
- **Genişletilmiş Teşhis Paylaşımı:** "Geliştiriciye Gönder", tüm teşhis kütüklerini (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs` vb.) tek pakette paylaşır.

### 📋 2. Duplicate Lazy Key Liste Çökmeleri Düzeltildi
- `IllegalArgumentException: Key ... was already used` hatasına yol açabilen 7 arayüz listesi düzeltildi: Ana sayfa ızgarası, modern ana sayfa satırları, yayın takvimi (liste ve ızgara), genişletilmiş medya ızgarası, manga eklenti kaynak listesi, özel liste düzenleyici ve oynatıcı hız seçim sayfası.

### 🖼️ 3. Resim İndirme & Bellek Güvenliği (OOM Önleme)
- **Bellek Koruma (Downsampling):** İndirilen görsellerin bildirim önizlemesi tam boy yerine `inSampleSize` ile küçültülerek decode edilir; 48 MB üzeri devasa görseller filtrelenir.
- **Android 10+ MediaStore Kaydı:** Görseller doğrudan `İndirilenler/Kitsugi/Images` altına MediaStore API ile yazılır (Android 11+ dosya yazma engeli aşıldı).
- Galeri ve indirme adımları eylem izine (breadcrumbs) dahil edildi.

### 🎭 4. Karakter Görselleri & Oyuncu Fotoğrafları İyileştirmeleri
- **Akıllı Başlık Adayları:** TMDB içeriklerinde tüm başlık varyantları (Türkçe, Romaji, İngilizce, Japonca, eşanlamlılar) taranarak doğru anime eşleşmesi bulunur.
- **AniList Karakter Araması Yedeği:** Yapım düzeyinde eşleşmeyen karakterler için doğrudan AniList karakter araması yapılır (`realMalId` doğrulamasıyla).
- **Canlı Çekim Kadrolarında Oyuncu Fotoğrafları Korundu:** Live-action yapımlarda oyuncu fotoğrafları silinmez; `isRealMediaRole` bayrağı ile doğrudan TMDB kişi profili açılır.
- **Detay Sayfasında Anında Görsel (Hint Image):** Karakter sayfasına girildiğinde karttaki görsel anında galeriye ve arka plana alınır, sayfa resimsiz kalmaz.
- **Jikan /pictures Optimizasyonu:** Galeri resim isteği yalnızca MyAnimeList kimlik uzayında çalıştırılır (TMDB 404 çağrıları bitti).

### ⚡ 5. Shikimori Detay Sekmeleri & Hız Optimizasyonları
- **Yerel İlişkiler ve Öneriler:** Shikimori `/similar` ve `/related` uç noktalarıyla tek istekte saniyeler içinde yüklenir; kapaklar toplu çözülür.
- **Ekip Sekmesi Boş Kalma Koruması:** Shikimori personel kaydı bulunmayan yapımlarda gerçek MAL ID'si üzerinden Jikan personel listesi devreye girer.
- **Sonsuz Skeleton Koruması (25 sn Zaman Aşımı):** Sekme yüklemelerine 25 saniye (bölümler için 45 saniye) zaman aşımı ve tek-uçuş (single-flight) koruması eklendi.
- **ID İzolasyonu:** Shikimori ID'sinin MAL ID sanılması engellendi; gerçek MAL ID'si önbellekli çözülür.

### 🎬 6. Detay Sayfası Tasarım & "Bilgiler" Kopyalama Butonları Birleştirmesi
- **Ortak Bilgiler Bileşeni (`DetailInfoValueRow`):** Kütüphane girdisi ve API detay ekranlarındaki "Bilgiler" kartı ortak bileşene kavuşturuldu; her iki ekranda da tüm satırlara (Durum, Başlangıç, Bitiş, Stüdyo, Süre, Yayın, Yaş Sınırı, İngilizce, Japonca vb.) kopyalama butonları eklendi.
- **"Diğer Adlar" Tekil Etiketler & Bağımsız Kopyalama:** "Diğer Adlar" satırında tüm isimleri tek metin halinde birleştirip kopyalama sorunu giderildi. Artık her isim kendi etiketinde (chip) ve bağımsız kopyalama butonuyla yer alır; virgül içeren adlar bozulmaz, boş ve tekrar edenler filtrelenir.
- **Kompakt İzle Butonu:** Sol panelde ve özet bilgi alanında tüm genişliği kaplayan devasa buton yerine içerikle uyumlu (`wrapContent`) şık ve kompakt bir "İzle" butonu tasarlandı.

### 🔍 7. Arama & "Tümünü Gör" Raf Tutarlılığı
- **Raf ile Kaynak Sayfası Uyumu:** Arama ekranında bir raftan "Tümünü Gör"e tıklandığında, raftaki sorgu, arama kapsamı (`SearchScope`) ve mevcut sonuçlar doğrudan `SourceSearchPage`'e aktarılır; sonuçlar kaybolmaz.
- **Tümü Rafından Açıldığında Çift Kapsam (Anime + Manga):** "Tümü" rafında hem anime hem manga listelendiği için, kaynak sayfası açıldığında her iki kapsam da birlikte taranır; rafta görünen içerikler sayfada silinmez.
- **Yenileme Başarısız Olsa Bile İçerik Koruma:** Ağ hatasında raftaki mevcut sonuçlar temizlenmez.
- **MAL Alakasız Popülerler Düşüşü Kaldırıldı:** MAL kaynağında spesifik sorgu sonuçsuz kalınca kullanıcıyı yanıltan genel popülerler listesine düşme engellendi.

### 🧾 Ek
- Sürüm adı `v2.4.206` olarak güncellendi.
- Kullanıcı talimatı doğrultusunda **yalnızca `foss` varyantı** derlendi ve yayınlandı (GMS derlenmedi).

---

## 🇬🇧 English (v2.4.206)

### 🛡️ 1. Crash Diagnostics & Reporting Overhaul (No More Silent Deaths)
- **Native Crash Handler (C++ - libkitsugi_crash_handler.so):** Catches SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP using `_Unwind_Backtrace` in an async-signal-safe manner, writing raw backtraces to `native_crash.txt`.
- **Silent-Death Detector (KitsugiSessionSupervisor):** Discovers unhandled terminations (LMKD, post-ANR kills, SIGKILL) by scraping logcat on the subsequent launch, generating `unclean_exit.txt` and alerting the user with an "Unexpected Shutdown" dialog.
- **Action Breadcrumbs:** Tracks the last 40 user interactions and active screen keys to pinpoint the exact failure location.
- **Isolated Crash Screen:** Removed `Thread.sleep(800)` UI freezes. The crash screen (`KitsugiCrashActivity`) opens with its own `taskAffinity` in a dedicated process, protecting it from OS task teardown.
- **Cockroach Loop & Error Swallowing Fix:** Fixed unchecked swallowing of Cloudstream exceptions and eliminated the 100% CPU spinning loop on `Looper.loop()` exit.
- **Early Setup:** Initialized in `attachBaseContext()` to catch startup and ContentProvider faults.
- **Comprehensive Sharing:** "Send to Developer" bundles all diagnostic artifacts (`crash_log`, `crash_history`, `unclean_exit`, `native_crash`, `breadcrumbs`).

### 📋 2. Duplicate Lazy Key List Crashes Fixed
- Resolved `IllegalArgumentException: Key ... was already used` across 7 lists: Home grid, modern home rows, airing calendar (list & grid), expanded media grid dialog, manga extensions, custom list editor, and playback speed picker.

### 🖼️ 3. Image Downloads & Memory Hardening (OOM Prevention)
- **Sampled Decoding:** Notification icons downsample byte streams using `inSampleSize` instead of full-size bitmap decoding; capped downloads at 48 MB.
- **Android 10+ MediaStore Integration:** Saves downloaded images via MediaStore to `Downloads/Kitsugi/Images`, bypassing scoped storage issues on Android 11+.
- Logged gallery and download interactions into breadcrumbs.

### 🎭 4. Character Images & Live-Action Cast Photos
- **Candidate Title Resolver:** Resolves character images across all title variants (Turkish, Romaji, English, Japanese, synonyms).
- **Direct AniList Character Search Fallback:** Unmatched character records fall back to AniList character search with strict `realMalId` verification.
- **Preserved Cast Photos in Live-Action:** Actor photos are preserved, and live-action characters route directly to TMDB person profiles via `isRealMediaRole`.
- **Instant Detail Image:** Re-uses the list card thumbnail as an immediate placeholder on the detail page.
- **MAL-Only Jikan /pictures:** Prevents wasteful 404 network requests with TMDB IDs.

### ⚡ 5. Shikimori Tabs & Speed Optimization
- **Native Relations & Recommendations:** High-speed single-request fetching with batch cover resolution via Shikimori `/similar` and `/related`.
- **Staff Shelf Fallback:** Automatically queries Jikan staff data when Shikimori returns empty staff records.
- **25s Timeout & Single-Flight Protection:** Eliminates perpetual loading skeletons on unstable networks.
- **ID Resolution Safety:** Distinct caching and resolution between Shikimori IDs and MyAnimeList IDs.

### 🎬 6. Detail Screen Polish & Unified Info Row Copy Buttons
- **Unified Info Component (`DetailInfoValueRow`):** Both library entry and API result info cards now share the same row component with individual copy buttons on every row (Status, Start Date, End Date, Studio, Duration, Broadcast, Rating, English, Japanese).
- **Synonyms Chips with Individual Copy:** "Other Names" are displayed as individual chips, each with its own copy button. Prevents comma concatenation issues and ignores null/blank duplicates.
- **Compact Watch Button:** Replaced full-width watch buttons with clean, content-fitting buttons.

### 🔍 7. Search & "See All" Shelf Consistency
- **Handoff from Shelf to Source Page:** Clicking "See All" preserves the shelf's query, search scope (`SearchScope`), and pre-rendered results directly into `SourceSearchPage`.
- **Dual Scope for "All" Shelves:** Searching from "All" carries over both Anime and Manga scopes so items found on the shelf are never wiped upon opening the full source page.
- **Failure Resilience:** Network refresh failures preserve previously visible shelf results.
- **Removed Irrelevant MAL Popular Fallbacks:** Fixed MAL queries returning unrelated popular anime lists when search yielded zero matches.

### 🧾 Extras
- Version bumped to `v2.4.206`.
- Built and published exclusively as the `foss` release variant.

---

## 🇹🇷 Türkçe (v2.4.200)

### 🌍 TMDB & Simkl Başlık Dili Düzeltmesi (Türkçe → İngilizce → Romaji)

**Sorun:** TMDB sekmesindeki "Trend Animeler", "En Yüksek Puanlı Animeler" ve "Yakında Yayında" şeritlerinde, Türkçe başlık dili seçili olmasına rağmen Türkçesi bulunmayan içerikler Japonca/Çince (CJK) başlıklarla görünüyordu.

**Kök neden:** İngilizce yedek başlık isteği üretilirken `language=` parametresi `Regex("language=[^&]+")` ile değiştiriliyordu. Bu regex `with_original_language=ja` parametresinin içindeki `language=ja` kısmını da yakalayıp `with_original_language=en-US` yapıyordu. Sonuç: anime şeritlerinin (hepsi `with_original_language=ja` kullanır) İngilizce yedek isteği **tamamen farklı bir içerik listesi** döndürüyor, ID eşleşmesi tutmuyor ve Japonca başlıklar ekranda kalıyordu.

**Çözüm:**

- **Güvenli URL üretimi (`TmdbUrlUtils`):** `language` parametresi artık yalnızca tam parametre olarak değiştiriliyor; `with_original_language` ve diğer filtreler korunuyor. Yedek istek aynı içerik listesini döndürdüğü için ID eşleşmesi birebir tutuyor.
- **Merkezi başlık çözümleyici (`MediaTitleResolver`):** Türkçe → İngilizce → Romaji zinciri tek yerde tanımlandı. CJK başlıklar ilk üç adımda asla kabul edilmiyor; yalnızca hiçbir Latin alternatif yoksa (son çare) gösteriliyor.
- **Akıllı yedek istek (`TmdbTitleFallback`):** TMDB istenen dilde başlık bulamazsa orijinal (Japonca) başlığı döndürür. Uygulama bunu "yerelleştirilmiş başlık == orijinal başlık" karşılaştırmasıyla ve CJK taramasıyla yakalıyor; yalnızca gerektiğinde tek bir `en-US` isteği yapıp başlıkları birleştiriyor. Gereksiz ağ trafiği yok.
- **Kapsam:** Keşfet şeritleri (trend/popüler/en yüksek puanlı/yakında yayında + "Tümü" ekranları), arama sonuçları, medya detay sayfası (TMDB `alternative_titles` desteğiyle TR/US/JP alternatifleri), bölüm adları, yayın takvimi, yapım şirketi sayfaları, öneriler/ilişkiler ve oyuncu-ekip çalışmaları.
- **Simkl:** Simkl kayıtlarında önce TMDB üzerinden Türkçe başlık denenir; yoksa Simkl'in İngilizce/romaji alanlarına inilir. Simkl `title` alanı CJK geldiğinde ekrana düşmez.
- **Gösterim güvencesi:** TMDB/Simkl kaynaklı öğeler için kart ve liste başlıkları, seçilen başlık dilinden bağımsız olarak daima Latin alfabesinde bir alternatif bulur (Japonca seçilmediği sürece).
- **Önbellek tazeleme:** Bellek içi ve kalıcı keşfet önbelleği anahtarları artık dil + başlık çözümleme sürümü içeriyor. Böylece eski sürümden kalan hatalı (Japonca) kayıtlar servis edilmiyor, düzeltme anında görünür oluyor.

### 🧾 Ek
- Sürüm adı `2.4.200` olarak güncellendi.

---

## 🇬🇧 English (v2.4.200)

### 🌍 TMDB & Simkl Title Language Fix (Turkish → English → Romaji)

**Problem:** In the TMDB tab, shelves such as "Trend Animeler", "En Yüksek Puanlı Animeler" and "Yakında Yayında" showed Japanese/Chinese (CJK) titles for entries that had no Turkish translation, even though Turkish was the selected title language.

**Root cause:** When building the English fallback request, the `language=` parameter was rewritten with `Regex("language=[^&]+")`. That regex also matched the `language=ja` fragment inside `with_original_language=ja` and turned it into `with_original_language=en-US`. As a result, the English fallback request for anime shelves (all of which use `with_original_language=ja`) returned a **completely different result set**, ID matching failed, and raw CJK titles stayed on screen.

**Fix:**

- **Safe URL building (`TmdbUrlUtils`):** the `language` parameter is now replaced only as a standalone parameter; `with_original_language` and other filters are preserved, so the fallback request returns the same list and IDs match one-to-one.
- **Single central resolver (`MediaTitleResolver`):** the Turkish → English → Romaji chain is defined in one place. CJK titles are never accepted in the first three steps and appear only as a last resort when no Latin alternative exists.
- **Smart fallback fetch (`TmdbTitleFallback`):** TMDB returns the original (Japanese) title when it has no translation for the requested language. The app detects this by comparing localised vs. original titles and scanning for CJK, then performs a single `en-US` request only when needed.
- **Coverage:** explore shelves (trending/popular/top-rated/upcoming + "see all" screens), search results, media detail pages (via TMDB `alternative_titles`, TR/US/JP), episode names, airing calendar, production company pages, recommendations/relations and person credits.
- **Simkl:** Simkl entries try a TMDB lookup first for a Turkish title, then fall back to Simkl's English/romaji fields; CJK `title` values never reach the UI.
- **Display guarantee:** for TMDB/Simkl items, card and list titles always resolve to a Latin alternative regardless of the selected title language (unless Japanese is explicitly selected).
- **Cache refresh:** in-memory and persistent explore cache keys now include the language plus the title-resolution version, so stale (Japanese) entries from previous builds are ignored and the fix is visible immediately.

---

## 🇹🇷 Türkçe (v2.4.198)
### 🔞 +18 Bulanıklık & Yetişkin İçerik Güvenliği (Her Koşulda Tam Koruma)
- **46 Arayüz Yüzeyinde Eksiksiz Uygulama:** Ana sayfa, Keşfet şeritleri, Arama sonuçları, Karakter/Personel detayları, Galeri pencereleri ve Android TV arayüzlerinde +18 afiş ve görseller ayara tam uyumlu olarak maskelenir.
- **Eski Android Sürümleri İçin Yedek Motor (API < 31 Fallback):** Android 12 altındaki cihazlarda sistem düzeyindeki `Modifier.blur` sessizce devre dışı kaldığı için afişlerin açık kalması sorunu, Coil tabanlı `BlurTransformation` ve akıllı görsel boru hattı (`KitsugiNsfwImage`) ile çözüldü. Artık her cihazda bulanıklık garantilidir.
- **Platform Başına Doğrulanmış +18 Kuralları:**
  - **Kitsu:** R18 derecelendirmesi, `nsfw=true` veya hentai alt türü (`KitsuAdultFlags`).
  - **Shikimori:** Yalnızca `rx`/`hentai` yapımlar (`r`/`r_plus` ayrıldı).
  - **Simkl:** `adult=true` bayrağı ve resmi sertifikalar (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** `isAdult` ve `adult` alanları keşif takvimleri ve vitrinlere eksiksiz aktarılır.
  - **Manga Eklentileri:** API bayrağı olmayan kaynaklar için etiket ve tür analizi (`hentai`, `doujin`, `xxx`... ecchi hariç tutularak).
- **Kişisel Liste İçe Aktarımı:** Kitsu, Shikimori ve Simkl içe aktarımları `isAdult` bilgisini ilk andan itibaren kaydeder.

### 📌 Kaynak Seçimi Kalıcılığı & Kitsu Kimlik İzolasyonu
- **Keşfet:** Son seçili kaynak (Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) `SettingsDataStore` üzerinde saklanır ve uygulama yeniden açıldığında otomatik geri yüklenir. Otomatik kaynak düşüşleri seçim sayılmaz, kaydedilmez.
- **Arama:** Kaynak motoru + kapsam (sekme, platform, medya türü) birlikte hatırlanır; sekme çipleri ile motor seçicisi senkron kalır ve açılışta gereksiz arama tetiklenmez.
- **Listem:** Kaynak sekmesi `tab_index` ile kalıcıdır; Keşfet/Arama ile aynı davranış sergilemesi korunur.
- **Açılış maliyeti:** Son seçim TMDB/Tümü dışında bir kaynaksa splash sırasındaki 11 istekli TMDB önbellek ön-yüklemesi atlanır.
- **Kitsu Alakasız Ayrıntı Düzeltmesi:** Kitsu içe aktarmasında ham MAL ID'lerinin yazılmasından kaynaklanan sayısal çakışma (ör. Keep Your Hands Off Eizouken!) çözüldü. `KitsuIdNamespace` (`300M+1..399M+1`), stableId çözümleyici ve `KitsuIdentityMigration` eklendi.

### ♾️ Simkl Gerçek Sayfalama & Akış İyileştirmeleri (20'şer Sonsuz Kaydırma)
- **100 Sınırı Kaldırıldı:** Simkl artık statik bir sınırda durmak yerine, diğer kaynaklar gibi aşağı kaydırdıkça 20'şer 20'şer yeni içerik getirmeye devam eder.
- **Resmî Simkl API Endpoint Mimarisi:**
  - **Tür & Popülerlik Listeleri:** `genres/...` endpoint'leri üzerinden `page` ve `limit` parametreleriyle 20 sayfaya kadar (~1200 içerik) gerçek sayfalama.
  - **Yakında Yayında:** `anime/premieres/soon` ve `premieres/new` ile 20'şer içeriklik sayfalı akış.
  - **Trend Listeleri:** Sayfa parametresi bulunmayan Simkl trend JSON'ları için 500'lük Top snapshot (`today_500.json`, `week_500.json`) 1 saat boyunca önbelleğe alınır ve 20'şer dilimler halinde kaydırılır.
  - **Yayında Olanlar (Airing):** Takvim endpoint'inden tek seferde eksiksiz liste çekilir.
- **Arama Tarafında Sayfalama:** Simkl arama ve gelişmiş arama (`search`, `searchAdvanced`) `page` desteğine kavuştu. Hatalı path parçaları dokümandaki `all` standartlarına getirildi.
- **Zorunlu API Parametreleri & User-Agent:** Tüm isteklere `client_id`, `app-name=kitsugi`, `app-version` ve `KitsugiApp/... (Android)` User-Agent başlığı eklendi.
- **Kişisel Listeler:** "İzlemeye Devam" ve "Planladıklarım" listeleri tek seferde yüklendiği için gereksiz yere tekrar çekilmez.

---

## 🇹🇷 Türkçe (v2.4.196)
### 🛡️ Yanlış İçerik Eklenmesine Karşı Kimlik Güvencesi (CrossSyncIdentityGuard)
- **6 Sızıntı Vektörünün Kapatılması:**
  - **V1 (TMDB - ARM):** ARM servisine TMDB kimliği aktarımı kaldırıldı (film/dizi ID çakışması ve tüm franchise'ı kapsayan dizi ID'lerinin yanlış anime MAL ID'si üretmesi engellendi; yalnızca birebir MAL/AniList/Kitsu kimlikleri kullanılır).
  - **V2 (Simkl Anime - TMDB):** Simkl'a anime kayıtları için TMDB kimliği gönderilmesi tamamen engellendi. Simkl ID varsa yalnızca o gönderilir; dizi/film çakışmaları önlendi.
  - **V3 (Simkl Başlık Eşleme):** Güvenli kimliği (Simkl/MAL) olmayan animeler için Simkl'da başlık eşleştirmesi kapatıldı (`titleMatchingAllowed = false`). Benzer adlı film veya dizilerin listeye sızması engellendi.
  - **V4 (Kitsu Başlık Eşleme):** Kitsu başlık aramasında kısmi eşleşmeler (ör. "Oni Chichi" ⊂ "Oni Chichi 2") kapatıldı; birebir alias ve uyumlu yıl şartı getirildi.
  - **V5 & V6 (Tek Kaynak Kimlik Doğrulama):** Yeni `CrossSyncIdentityGuard` ile en az iki bağımsız kaynakça doğrulanmayan veya tek bir sağlayıcı eşlemesinden (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) gelen kimlikler için resmi MAL API / Jikan kataloğundan başlık ve yıl doğrulaması yapılır.
- **"Doğrulanamayan Kimlikle Asla Yeni Kayıt Eklenmez" Kuralı:** Başlık akrabalığı ve yıl kontrolünden geçemeyen şüpheli kimliklerle hiçbir platforma (AniList, MAL, Kitsu, Shikimori, Simkl) yeni kayıt eklenmez; atlanır ve raporda gerekçesiyle gösterilir. Mevcut kayıtların güncellenmesi platformun kendi kimliğiyle yapıldığı için bu kısıtlamadan etkilenmez.

### 📊 Platform Bazlı Eşitleme ve Hata Sayımı Düzeltmeleri
- **Simkl Sayım ve Makbuz İyileştirmesi:** 35'lik grupta tek bir eşleşmeyen öğe yüzünden tüm grubun "200 hata" olarak sayılması engellendi; kayıt bazlı makbuz (`Receipt.unmatched`) ile başarılı olanlar "eklendi", Simkl'de bulunamayanlar "atlandı" olarak işlenir. TV dizi ilerlemesi sınırı ve kısmi puan/geçmiş uyarıları artık grup hatası değil; eşitleme sonunda başlık listesiyle tek bir özet uyarı olarak raporlanır. 400M+ Shikimori kimliklerinin yanlışlıkla Kitsu ID olarak Simkl'e gönderilmesi düzeltildi (`300M..400M` aralık kontrolü).
- **Kitsu Resmi Mappings & 429 Yönetimi:** Resmi Kitsu `/mappings` API uç noktası (MAL ve AniList eşlemeleri) entegre edildi; manga ve ARM'da olmayan animeler için ID çözümleme oranı artırıldı. Kitsu istekleri için merkezi limiter ve 429 durumlarında 3 tekrarlı yeniden deneme mekanizması eklendi; API hata gövdeleri (`KitsuWriteResult`) okunarak ayrıntılı teşhis sağlandı. Kitsu içe aktarımına yayın yılı (`startYear`) eklendi.
- **AniList Kota ve Hız Limiti Optimizasyonu:** İstek aralığı 2100 ms'ye ayarlanarak (~28 req/dk) AniList'in fiili 30/dk hız sınırına tam uyum sağlandı. HTTP 429 durumlarında `Retry-After` başlığına duyarlı en fazla 3 kez otomatik yeniden deneme eklendi. İçe aktarımda zaten var olan `idMal` için her kayıtta atılan gereksiz sorgu kaldırılarak kota tasarrufu yapıldı.
- **MyAnimeList & Shikimori Özet Raporlama:** Çözülemeyen her kayıt için tek tek uyarı basılması yerine platform başına tek satırlık özet uyarı eklendi. Shikimori içe aktarımına `aired_on`/`released_on` yayın yılı ve İngilizce başlık eklendi. AniList içe aktarımında `seasonYear` boşsa `startDate.year` yedeği devreye alındı.

### 🧩 Kimlik Eşleştirme ve Çakışma Yönetimi
- **Aynı İsimli Yapımlar & Çelişen Kimlikler:** Berserk (1997/2016), Hunter x Hunter (1999/2011), Golden Time gibi aynı başlığa sahip yapımların tamamen izole edilip hiçbir yere yazılmaması sorunu çözüldü; artık ayrı gruplar halinde kendi kimlikleriyle yazılır.
- **Sahte "Kimlik Doğrulaması Gerekli" Gevşetmesi:** Farklı platformlardaki alias uyumsuzlukları yalnızca her iki tarafta da yıl biliniyor ve yıl farkı 1'den büyükse incelemeye düşer; aksi halde güvenle birleştirilir.
- **Durum ve İlerleme Doğruluğu:** Simkl'in toplam bölüm (`total`) sayısından "Tamamlandı" çıkarımı yapılması engellendi. Tek kaynaklı gruplarda gereksiz güncellemeler engellendi.
- **Temiz Rapor:** "Eşitleme kısmen tamamlandı" satırı hata yerine bilgi formatına dönüştürüldü.

### 📱 Yenilenen Tam Ekran Çapraz Eşitleme Paneli
- **Tam Ekran Dialog & Çentik Uyumu:** Panel artık `fullScreen = true` olarak açılır; durum çubuğu ve ekran çentikleri (`displayCutoutPadding`) ile kusursuz uyum sağlar.
- **Dikey ve Yatay Yönlendirme:** Dikey modda tek sütunlu akıcı hiyerarşi; yatay modda sol panelde ilerleme/hesaplar/eylemler, sağ panelde filtreler ve canlı günlük gösterilir.
- **Filtre Çipleri & Kompakt Hesap Kartları:** Hata ve uyarı sayıları filtre çiplerine rozet olarak eklendi; platform logolu kartlar ve 40 dp optimize butonlarla daha kompakt bir görünüm sunar.

---

## 🇹🇷 Türkçe (v2.4.195)
### 🌐 Keşfet "Tümü" — Kaynak Bazlı Keşif Alanları & Kategori Gruplaması
- **Kaynaklara Özel Bağımsız Alanlar:** AniList, MyAnimeList, TMDB, Simkl, Kitsu ve Shikimori platformları birbirine karıştırılmadan kendi logolu, renk vurgulu bağımsız başlıklarına kavuştu.
- **Kategori Ayrımı:** Trendler, en iyiler, popülerler, sezon içerikleri ve ilgili platformun desteklediği kategoriler doğrudan kendi kaynağının altında listelenir.
- **Sabit Kaynak & Kategori Çubuğu:** Ekranın üstündeki sabit çubukla doğrudan istenilen platforma veya kategori şeridine anında atlanabilir; uzun sayfalarda sayfa başına dönüş düğmesi eklendi.
- **Bağımsız Daraltma & Dayanıklı Durum:** Her kaynak alanı ayrı ayrı daraltılıp genişletilebilir; daraltma durumları ekran döndürmelerinde veya gezinmede korunur.
- **Akıllı "Tümünü Gör":** Çoklu görünümdeki "Tümünü Gör" butonu doğru kaynağın ve kategorinin devam sayfasını açar.
- **İzole Hata Yönetimi & Yeniden Deneme:** Bir kaynak yüklenemezse veya hata verirse diğer kaynaklar görüntülenmeye devam eder; yalnızca sorunlu kaynak tek dokunuşla yeniden denenebilir.
- **Büyük Ekran & TV Uyumu:** Ortak kaynak alanları mobil, tablet ve Android TV D-pad gezinmesiyle tam uyumlu hale getirildi.

### 📋 Listem "Tümü" Sekmesi, Temsilci Kaynak & Simkl TMDB Detay Geçişi
- **Sola Kaydırma & Sekme Sırası Düzeltmesi:** "Tümü" sekmesi gerçekten ilk sıraya (`index 0`) taşındı. Pager ile üst buton dizilimi eşitlenerek Tümü sayfasından sola kaydıramama sorunu giderildi.
- **Öncelikli Temsilci Kaynak:** Birleşik kütüphanede aynı yapımın farklı servislerdeki kayıtları birleştirilirken öncelik zinciri uygulandı: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Animelerde otomatik olarak AniList öne çıkarken, dizi ve filmlerde doğal olarak Simkl/TMDB temsilci kalır.
- **Hızlı Simkl Detayları (TMDB Entegrasyonu):** Simkl kayıtlarının ayrıntı sayfaları doğrudan TMDB üzerinden açılır (`tmdbId`, ARM ID çözücü veya arama). Simkl'in yavaş API yanıtı detay ekranını geciktirmez.
- **Varsayılan Manuel Kayıt:** Tümü sekmesinde manuel kayıt eklerken varsayılan hedef AniList olarak ayarlandı.

### 🎬 Akış ve Kalite Bilgi Doğruluğu (StreamInfoFix)
- **"400p" Kök Neden Düzeltmesi:** CloudStream eklentilerinin bilinmeyen kalite döndürdüğünde (`Qualities.Unknown = 400`) ekrana "400p" basması sorunu giderildi.
- **Sahte Dil ve Rozet Çıkarmalarının Kaldırılması:** "Subaru" kelimesinden "Altyazılı", "Dubai" kelimesinden "Dublaj" basan hatalı alt dize filtreleri kaldırıldı. "Türkçe eklenti ⇒ Altyazılı" varsayımı ve kanıtsız "🎬 Standart" etiketi temizlendi.
- **Gerçek Ölçüm ve HLS Çözümleme (`StreamProbe`):** HLS master playlist'i üzerinden `#EXT-X-STREAM-INF` (çözünürlük) ve `#EXT-X-MEDIA` (ses/altyazı dilleri) etiketleri incelenerek gerçek veriler rozetlere yansıtılır. Yüklenen altyazılar `CC TR` / `CC EN` olarak gösterilir.
- **Arayüz ve Filtre Uyumu:** Kart rozetleri, alt sayfa akış seçici, filtre çipleri ve TV arayüzü yeni `StreamInfoResolver` verisiyle senkronize edildi.

### 🔐 Shikimori Yetkilendirme & Gradle Bellek Optimizasyonu
- **Shikimori 401 Otomatik Yenileme:** Sunucu tarafında geçersiz kılınan token'larda liste çekimi sırasında otomatik refresh yapılarak kullanıcı oturumunun kopması önlenir.
- **Otomatik RAM Temizliği:** `gradle.properties` içine 60 saniyelik daemon zaman aşımı eklendi; derleme bittikten sonra arkada asılı kalan Gradle/JDK süreçlerinin bellek işgali önlendi.

---

## 🇬🇧 English (v2.4.195)
### 🌐 Explore "All" — Dedicated Source Sections & Grouped Categories
- **Dedicated Platform Sections:** AniList, MyAnimeList, TMDB, Simkl, Kitsu, and Shikimori each receive dedicated branded headers with custom accent colors and descriptions instead of intermixed rails.
- **Source-Scoped Rails:** Trending, top-rated, popular, seasonal, upcoming, and other platform categories stay strictly grouped under their respective provider section.
- **Sticky Platform Navigation:** Sticky header bar allows immediate jumping directly to any provider (AniList, MAL, TMDB, etc.) or category rail, with a quick return-to-top shortcut.
- **Collapsible Sections & Saved State:** Each provider section can be independently collapsed or expanded, preserving state across screen rotations and navigation.
- **Accurate "See All" Routing:** Tapping "See All" navigates to the dedicated category view matching the exact source and filter.
- **Isolated Provider Failures & Retry:** If a single provider fails to load or times out, all other providers remain intact; users can retry individual failed providers independently.
- **TV & Large Screen Support:** Unified source sections fully support Android TV D-pad focus, navigation, and tablet landscape layouts.

### 📋 My List "All" Tab, Representative Hierarchy & Fast Simkl Details
- **Tab Ordering & Swiping Fix:** The unified "All" tab is now index 0. Synchronized pager and pill tabs eliminate the gesture lock where users could not swipe left from the "All" page.
- **Smart Representative Provider Hierarchy:** Multi-provider entries deduplicate using prioritized representative selection: `AniList > MAL > Kitsu > Shikimori > Simkl > TMDB`. Anime defaults to AniList, while TV shows and movies naturally maintain Simkl/TMDB representation.
- **Lightning Fast Simkl Details via TMDB:** Simkl entries now route details through TMDB (`tmdbId`, ARM resolver, or fallback search), removing slow Simkl API bottlenecks.
- **Default Manual Entry:** Manual additions on the "All" tab default to AniList.

### 🎬 Accurate Stream Information (StreamInfoFix)
- **"400p" False Badge Root Fix:** Resolved issue where `Qualities.Unknown = 400` in CloudStream plugins resulted in a "400p" label.
- **Removed Heuristic Audio/Subtitle Inferences:** Fixed substring matching bugs (e.g., "Subaru" triggering Subtitle, "Dubai" triggering Dubbed). Eliminated automatic "Turkish Plugin ⇒ Subtitled" assumption and baseless "🎬 Standard" labels.
- **Real Stream Probing (`StreamProbe`):** Analyzes HLS playlists for actual `#EXT-X-STREAM-INF` resolutions and `#EXT-X-MEDIA` audio/subtitle tracks. Verified subtitle files display as `CC TR` / `CC EN`.
- **Synchronized UI & Filters:** Stream cards, bottom sheets, filter chips, and TV screens are fully wired to the accurate `StreamInfoResolver` output.

### 🔐 Shikimori 401 Silent Token Refresh & Memory Optimizations
- **Shikimori 401 Auto-Recovery:** Automatic token refresh on 401 errors during rate syncing prevents unneeded session expirations.
- **Gradle/JDK Daemon Idle Cleanup:** Added 60s idle timeout to `gradle.properties` and post-build cleanup scripts to stop lingering Java daemons from holding system RAM.

---

## 🇹🇷 Türkçe (v2.4.194)
### ⚡ Çapraz Eşitleme (Cross-Sync) %0 Takılma Düzeltmesi & Gelişmiş Tanılama
- **Aday İndeksleme ve Performans Optimizasyonu:** Eşleştirme aşamasında her yeni içerik için tüm birleştirilmiş kayıtların baştan sona taranması ($O(N^2)$) kaldırıldı. Kimlik ve başlık indeksleriyle (`CrossSyncCandidateIndex`) aday arama daraltıldı; binlerce kayıtta donma ve takılmalar engellendi.
- **Canlı İlerleme Sayacı:** Eşleştirme aşamasında sayaç her 25 kaynak kaydında güncellenir ve coroutine iptal kontrolleri düzenli çalışır.
- **Dinamik Durum ve Duraklama Uyarıları:** Ağ beklerken sahte %0 yerine belirsiz (indeterminate) ilerleme göstergesi sunulur. Ekrana geçen süre, tahmini kalan süre ve uzun süren duraklamalarda uyarı eklendi.
- **Gelişmiş Günlük & Rapor Paylaşımı:** Günlükte Tümü / Sorunlar / Hatalar / Uyarılar filtreleri ve genişletilebilir teknik ayrıntılar yer alır. Eşitlemeyi onaylayarak durdurabilir, sürerken kısmi raporu paylaşabilir veya tamamlanınca raporu dışa aktarabilirsiniz.
- **Otomatik Rapor ve Güvenlik:** Raporlar otomatik olarak `Downloads/Kitsugi/CrossSyncReports` dizinine kaydedilir; cihaz ve ortam bilgileri eklenir, Bearer ve API anahtarları maskelenir (`[REDACTED]`). Hesaplardan içerik silinmez.
- **İstem Dışı Eşitleme Koruması:** "Son Eşitleme Raporu"na dokunulduğunda yanlışlıkla yeni bir eşitleme başlatılması engellendi.

### 🌐 Listem Sayfası Birleşik "Tümü" Görünümü & Dinamik Platform Rozetleri
- **Tüm Kaynakları Tek Ekranda Birleştirme:** Listem sekmesine AniList, MyAnimeList, Simkl, Kitsu ve Shikimori kütüphanelerini tek ekranda toplayan "Tümü" seçeneği eklendi.
- **Tekil Gösterim & Yinelenme Koruması:** Farklı servislerde bulunan aynı yapım yinelenmek yerine tek kartta birleştirilir (`groupMyListEntries`).
- **Dinamik Kaynak Rozetleri:** Kartların altında yapımın hangi platformlarda bulunduğunu gösteren orijinal platform logoları (`FlowRow` ile dar kartlarda taşmadan) sergilenir.
- **Tüm Düzenlerle Uyumlu:** Kompakt, rahat, ayrıntılı, 2 sütunlu grid ve minimalist kart düzenleriyle tam uyumludur.
- **Tek Dokunuşla Yenileme:** Birleşik görünümde aşağı çekip yenileme yapıldığında bağlı tüm aktif sağlayıcılar sırayla eşitlenir.

---

## 🇬🇧 English (v2.4.194)
### ⚡ Cross-Sync 0% Stall Fix & Advanced Diagnostics
- **Candidate Indexing & Performance:** Eliminated $O(N^2)$ quadratic scanning during cross-platform media grouping. Added identity & title candidate index (`CrossSyncCandidateIndex`), speeding up grouping on multi-thousand entry libraries.
- **Live Progress Reporting:** Progress counters update every 25 source records during grouping, with periodic cooperative coroutine cancellation checks.
- **Indeterminate Progress & Stall Warnings:** Displays indeterminate progress indicators during network/verification steps instead of a false 0% determinate bar. Added elapsed time, estimated time, and long-stall warnings.
- **Advanced Log Filtering & Report Sharing:** Logs feature All / Issues / Errors / Warnings filters and expandable technical stacktraces. Allows confirmed cancellation, sharing partial reports while sync is active, and exporting final reports.
- **Auto Report Storage & Credential Redaction:** Diagnostic reports are automatically saved to `Downloads/Kitsugi/CrossSyncReports/` with environment details and redacted credentials (`[REDACTED]`). No items are deleted from upstream accounts.
- **Accidental Sync Trigger Prevention:** Viewing the "Latest Sync Report" no longer accidentally launches a new synchronization.

### 🌐 My List Unified "All" View & Dynamic Platform Badges
- **Unified Library Tab:** Added an "All" option to the My List screen consolidating libraries across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori in one view.
- **Deduplicated Media Grouping:** Identical media titles across multiple connected providers are unified into a single card representation.
- **Dynamic Platform Badges:** Cards display responsive badges (`FlowRow`) indicating all connected platforms hosting the entry.
- **Layout Compatibility:** Fully supported across compact, comfortable, large, 2-column grid, and minimalist card designs.
- **Unified Pull-to-Refresh:** Pulling to refresh in the combined view sequentially syncs all active connected providers.

---

## 🇹🇷 Türkçe (v2.4.193)
### 🔍 Arama Deneyimi (UX) Yenilemesi & "Tümünü Gör" Ayrık Kaynak Sayfası

- **Arama Sayfası Sadeleştirmesi:** Arama çubuğunun altındaki kalabalık kategori çipleri, sıralama seçici, filtreler ve platform bilgi satırı ana sayfadan kaldırılarak sağ üstteki filtre butonunun açtığı `SourceEngineFilterSheet` altında toplandı. Sayfa temizlendi.
- **Kompakt Filtre Özeti:** Varsayılandan farklı bir filtre/kapsam seçildiğinde tek satırlık kompakt özet çipi görüntülenir ve tıklandığında ayar panelini açar.
- **"Tümünü Gör" Artık Ayrık Sayfa (`SourceSearchPage`):** Çoklu arama raflarındaki "Tümünü Gör" artık aynı sayfada sekme değiştirmek yerine geri butonu, başlık ve dikey sonuç listesi içeren bağımsız bir sayfada açılır.
- **İzole ViewModel:** `SourceSearchPage` kendi izole `SearchViewModel` örneğini kullanır; burada yapılan filtre ve arama değişiklikleri ana çoklu arama durumunu etkilemez.
- **Shikimori 1-Tık OAuth Callback Düzeltmesi (`redirect_uri` Uyuşmazlığı):** Shikimori OAuth yetkilendirme isteğindeki callback adresinin uyuşmaması ("The requested redirect uri is malformed or doesn't match client redirect URI") sorunu giderildi. Varsayılan 1-tık girişi resmî `aniyomi://shikimori-auth` callback'ine bağlandı; özel kayıtlı uygulamalar için `kitsugi://shikimori-auth` alternatifi korundu ve regresyon birim testleri eklendi.

---

## 🇬🇧 English (v2.4.193)
### 🔍 Search UX Overhaul, Dedicated "See All" Screen & Shikimori 1-Tap Fix

- **Clean Search Screen:** Cluttered chips, sorting dropdowns, and info banners removed from under the search bar and unified into the top-right `SourceEngineFilterSheet`.
- **Compact Active Filter Summary:** An unobtrusive summary chip appears only when non-default filters or scopes are active.
- **Dedicated "See All" Screen (`SourceSearchPage`):** Tapping "See All" opens a full standalone screen with back navigation, search bar, filters, and vertical results.
- **Isolated ViewModel:** Uses an independent `SearchViewModel` instance so altering filters on source search does not corrupt multi-search state.
- **Shikimori 1-Tap OAuth Callback Fix (`redirect_uri` Mismatch):** Fixed the OAuth authorization rejection ("The requested redirect uri is malformed or doesn't match client redirect URI"). Default 1-tap flow now uses the official registered `aniyomi://shikimori-auth` callback with `kitsugi://shikimori-auth` preserved for custom apps, verified via unit tests.

---

## 🇹🇷 Türkçe (v2.4.194)
### 🔔 Bildirim Altyapısı Denetimi — 5 Kaynak (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Bildirim Teşhisi (yeni):** Bildirimler ekranının sağ üstüne 🪲 butonu eklendi. Tek dokunuşla 5 kaynağa **gerçek istek** atılır; hesap bağlı mı, hangi uç nokta kullanılıyor, **HTTP durum kodu**, dönen kayıt sayısı, süre ve varsa hata + çözüm ipucu gösterilir. Artık "geliyor mu gelmiyor mu" tahmine kalmaz.
- **AniList — sessiz hata yutma kaldırıldı:** İstek başarısız olduğunda istemci boş liste döndürüp "bildirim yok" gösteriyordu; artık GraphQL hataları gerçek mesajla gösterilir. Okunmamış bildirim sayacı eklendi ve sekmede rozet olarak görünür.
- **Shikimori — gerçek bildirim akışı:** Uygulama bugüne kadar **izleme geçmişini** bildirim gibi gösteriyordu. Resmî uçlar (`GET /api/users/:id/messages?type=notifications|news` ve `/unread_messages`) eklendi; `messages` izni yoksa ekran bunu ve nasıl verileceğini açıklar, geçmiş listesine güvenli düşer. OAuth izni artık client_id'ye göre seçilir (kendi uygulamanızı girerseniz `user_rates messages`).
- **Simkl — deprecated takvim yolu düzeltildi (kritik):** Eski kod, **1 Şubat 2027'de güncellenmeyi bırakacak** olan v2 olmayan dosyaları okuyor ve sonucu **ilk 50 öğeyle** kesiyordu; eşleşme bu yüzden neredeyse hiç tutmuyordu. Yeni `SimklCalendarClient` resmî `data.simkl.in/calendar/v2/…` dosyalarını (`{calendar, metadata}`) ve kullanıcının `/sync/all-items/*/watching` listesini kullanır (simkl/tmdb/mal ID eşleşmesi, ±7 gün penceresi).
- **MyAnimeList — takvim haftası tuzağı:** Akış, içinde bulunulan takvim haftasına bağlı olduğu için hafta başında boşalıyor ve `id`'ye göre sıralanıyordu. Artık kayan **son 7 gün** penceresi kullanılır, doğru sıralanır ve listenin ne olduğu (MAL API'sinin kişisel bildirim sunmadığı) bilgi bandında yazılır.
- **Kitsu — sonraki bölüm tarihleri:** Kitsu'nun genel API'sinde bildirim ucu yoktur; buna karşılık anime kaynağındaki `nextRelease` alanı okunup "Bölüm N • yayınlandı/yaklaşan" akışı üretilir.
- **Arka plan işçisi güçlendirildi:** Simkl'de bölüm başına ayrı bildirim (eskiden dizi başına 1), yalnızca son 24 saatte yayınlananlar; MAL için hafta sınırına takılmayan pencere; **Kitsu ve Shikimori için yeni arka plan desteği** ve Ayarlar > Bildirimler'de iki yeni anahtar.
- **Ayrıntılı rapor:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇬🇧 English (v2.4.194)

### 🔔 Notification Infrastructure Audit — All 5 Sources (AniList · MAL · Simkl/TMDB · Kitsu · Shikimori)

- **Notification Diagnostics (new):** a 🪲 action in the Notifications screen fires **real requests** at all five sources and reports connection state, endpoint, **HTTP status**, item count, duration, plus an error and a fix hint. No more guessing whether notifications actually arrive.
- **AniList — silent errors removed:** on failure the client returned an empty list (looking like "no notifications"); GraphQL errors are now surfaced with their real message. The unread notification count is fetched and shown as a tab badge.
- **Shikimori — real notification feed:** the app used to present the **watch history** as notifications. Official endpoints (`GET /api/users/:id/messages?type=notifications|news`, `/unread_messages`) are now used, with a clear in-app explanation and a safe history fallback when the `messages` scope is missing. The OAuth scope is now chosen per client id (use your own app to get `user_rates messages`).
- **Simkl — deprecated calendar path fixed (critical):** the old code read the pre-v2 files that **stop updating on 1 February 2027** and truncated results to the **first 50 items**, which is why matches almost never appeared. The new `SimklCalendarClient` reads the official `data.simkl.in/calendar/v2/…` files (`{calendar, metadata}`) and joins them with the user's `/sync/all-items/*/watching` list (simkl/tmdb/mal ID match, ±7 day window).
- **MyAnimeList — calendar-week trap:** the feed was bound to the current calendar week (empty at the start of a week) and sorted by string id. It now uses a rolling **last 7 days** window, correct ordering, and an info banner explaining that the MAL API provides no personal notifications.
- **Kitsu — next episode dates:** Kitsu's public API has no notification endpoint, but the anime resource's `nextRelease` field is now parsed into an "Episode N • aired/upcoming" feed.
- **Hardened background worker:** per-episode Simkl notifications (previously one per show), last-24-hours only; a week-boundary-free window for MAL; **new background support for Kitsu and Shikimori** with two new toggles in Settings > Notifications.
- **Full report:** `docs/audits/NOTIFICATION_INFRASTRUCTURE_AUDIT_2026-10-07.md`

---

## 🇹🇷 Türkçe (v2.4.194)

### 🛡️ Kitsu Senkronizasyon & Çoklu Kayıt Çökme Koruması (Crash Fix)

- **Kitsu ve Platform Eşitleme Çökmesi Giderildi (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** `MediaEntryRepository.smartImport` içerisinde yer alan katı `require(matches.size <= 1)` koşulu kaldırıldı. Veritabanında aynı yapıma ait birden fazla yerel kayıt veya benzer başlık bulunduğunda uygulamanın `KitsugiCrashActivity` ile çökmesi engellendi; ID, ana başlık ve güncellik sırasına göre en uygun kaydı seçip güvenle güncelleyen akıllı çözümleyici uygulandı.
- **Franchise & Çoklu Film Başlık İzolasyonu:** `MediaIdentity.sameMedia` fonksiyonu güçlendirildi. `Date A Bullet: Dead or Bullet` ve `Date A Bullet: Nightmare or Queen` gibi aynı seriye ait yapımların yalnızca genel Japonca franchise adı (`デート・ア・バレット`) paylaşıldığı için yanlışlıkla aynı yapım sanılması engellendi.
- **Yedek Geri Yükleme ve Çapraz Eşitleme Koruması:** `MediaEntryBackup.mergeAndSyncEntries` ve `AuthViewModel.clusterEntry` adımlarındaki tüm fırlatıcı `require` kontrolleri güvenli eşleme mekanizmasıyla değiştirildi.
- **Hata Yakalama & İzolasyon Güvencesi:** `AuthViewModel` içerisindeki tüm harici platform import ve sunucu doğrulama adımları `runCatching` kalkanına alındı; bir platformda beklenmeyen bir durum oluşsa dahi diğer platformlar ve kullanıcı arayüzü kesintiye uğramadan çalışmaya devam ediyor.

### 🚀 Shikimori OAuth Girişi Kök Neden Düzeltmesi (Yönlendirme + Authorization Header Kaybı)

- **Giriş Başarısızlığının Kök Nedeni Çözüldü:** Shikimori production ortamında diğer domain'ler (örn. `shikimori.one`) `shikimori.io` adresine 301 ile yönlendiriliyor. OkHttp, host değiştiren yönlendirmelerde güvenlik nedeniyle `Authorization` header'ını otomatik SİLİYORDU. Bu yüzden token takası başarılı olup geçerli jeton elde edilmesine rağmen, hemen ardından yapılan `whoami` doğrulaması "oturumsuz" istek olarak ulaşıp `200 + null` döndürüyor ve uygulama **geçerli jetonu çöpe atarak** "jeton geçersiz görünüyor" hatasıyla kullanıcıyı yanıltıyordu (kullanıcı aynı kodla yeniden denediğinde kod tükendiği için `invalid_grant` alıyordu).
- **Başlıklar Korunan Yönlendirme Takibi:** Shikimori istemcisine (`ShikimoriApiClient`) özel bir HTTP istemci eklendi; 301/302/303/307/308 yönlendirmeleri, orijinal `Authorization` header'ı korunarak manuel takip ediliyor (en fazla 3 atlayış, RFC 7231'e uygun POST→GET dönüşümüyle). Artık hangi Shikimori domain'ine istek atılırsa atılsın oturum bilgisi kaybolmuyor.
- **Kanonik Host'a Doğrudan İstek:** Tüm API/token uçları artık production'un resmi host'u olan `shikimori.io` adresine doğrudan gider (`shikimori.one` yedek olarak korunur). Gereksiz yönlendirme atlayışı ortadan kalktı.
- **whoami Artık Girişi Düşürmüyor:** Giriş akışında (hem 1-tık deep-link hem manuel kod) yetki kodu takas edilir edilmez token derhal saklanıyor; profil bakımı (whoami, 3 denemeli) geçici olarak başarısız olsa bile oturum korunuyor ve kullanıcı kimliği ilk senkronizasyon/profil/aktarım işleminde tembel olarak çözümleniyor (`ensureShikimoriUserResolved`). Tekrar tekrar "yeniden giriş" döngüsü ve tüketilmiş kod hataları bitti.
- **Ölü Refresh Token Temizliği:** Sunucu refresh token'ı net biçimde reddediyorsa (400/401 `invalid_grant`/`invalid_client`) oturum sessizce saklanıp 401 döngüsü yerine temizleniyor ve kullanıcı yeniden girişe yönlendiriliyor.

### 📊 Kapsamlı Çapraz Eşitleme Hata Raporu ve Eşleştirme Güvenliği

- **Tam Eşitleme ve Hata Raporlama:** Eşitleme penceresindeki 500 satırlık canlı görünüm sınırından bağımsız olarak, tüm oturum boyunca gerçekleşen işlemler, API hataları, eşleştirme kararları ve platform istatistikleri eksiksiz bir rapora kaydedilir.
- **Otomatik Rapor Dosyası ve Dışa Aktarma:** Android 10+ cihazlarda raporlar otomatik olarak `İndirilenler/Kitsugi/CrossSyncReports/` altına `.txt` formatında kaydedilir. Ayrıca eşitleme penceresine eklenen "Raporu Kaydet" düğmesiyle rapor istenen herhangi bir konuma dışa aktarılabilir.
- **Sağlayıcı Kimlik Çatışması Koruması:** Aynı tür ve başlık için farklı servislerden gelen kimlikler birbiriyle çelişiyorsa (`MediaIdentity.conflictingIdentityKeys`) veya belirsiz birden fazla aday grup çözülemiyorsa kayıt diğer platformlara yazılmadan güvenlik için atlanır ve nedeni detaylarıyla rapora yazılır.
- **Hassas Veri İzolasyonu:** Oluşturulan raporda Bearer token, access/refresh token ve client secret gibi tüm hassas kimlik doğrulama anahtarları otomatik olarak maskelenir (`[REDACTED]`).

---

## 🇬🇧 English (v2.4.194)

### 🛡️ Kitsu Sync & Disambiguation Crash Fix

- **Resolved Fatal Crash in Kitsu & Multi-Platform Sync (`IllegalArgumentException: Belirsiz kitsu eşlemesi...`):** Replaced hard-failing `require(matches.size <= 1)` check in `MediaEntryRepository.smartImport` with graceful disambiguation. If multiple local records exist for an entry (or during complex imports), the system prioritizes exact provider key, exact primary title, English title, and latest update timestamp without throwing unhandled exceptions.
- **Franchise Japanese Title Bleed Prevention:** Enhanced `MediaIdentity.sameMedia` comparison so franchise movies sharing generic Japanese series titles (e.g. `Date A Bullet: Dead or Bullet` vs `Nightmare or Queen`) are not conflated as the same media when primary titles differ.
- **Safe Backup Merge & Cross-Sync Clustering:** Replaced assertion throws in `MediaEntryBackup.mergeAndSyncEntries` and `AuthViewModel.clusterEntry` with deterministic matching fallbacks.
- **Fault-Tolerant Coroutine Safety:** Guarded all platform import invocations in `AuthViewModel` with `runCatching` to prevent background coroutine cancellations from bringing down the UI or interrupting remaining platforms.

### 🚀 Shikimori OAuth Login Root-Cause Fix (Redirect + Authorization Header Loss)

- **Login Failure Root Cause Resolved:** In Shikimori production, other domains (e.g. `shikimori.one`) 301-redirect to `shikimori.io`. OkHttp automatically STRIPS the `Authorization` header on host-changing redirects for security. As a result, the token exchange succeeded and produced a valid token, but the immediately-following `whoami` verification arrived as an unauthenticated request, received `200 + null`, and the app **discarded the valid token** with a misleading "token appears invalid" error (retries with the same code then failed with `invalid_grant` because the code was already consumed).
- **Header-Preserving Redirect Following:** The Shikimori client (`ShikimoriApiClient`) now uses a dedicated HTTP client that manually follows 301/302/303/307/308 redirects while preserving the original `Authorization` header (max 3 hops, RFC 7231-compliant POST→GET conversion). The session no longer gets lost regardless of which Shikimori domain the request lands on.
- **Direct Requests to the Canonical Host:** All API/token endpoints now hit `shikimori.io` (the production canonical host) directly; `shikimori.one` is kept as a fallback. Unnecessary redirect hops are eliminated.
- **whoami No Longer Kills Login:** In both the 1-tap deep-link and manual code flows, the token is saved immediately after the authorization-code exchange. If the profile lookup (whoami, with 3 retries) temporarily fails, the session is preserved and the user identity is resolved lazily on first sync/profile/import (`ensureShikimoriUserResolved`). The repeated re-login loop and consumed-code errors are gone.
- **Dead Refresh Token Cleanup:** When the server explicitly rejects the refresh token (400/401 `invalid_grant`/`invalid_client`), the session is cleared and the user is prompted to reconnect instead of silently looping on 401s.

### 📊 Comprehensive Cross-Sync Diagnostic Report & Matching Safety

- **Full Sync & Error Reporting:** Independent of the live dialog's 500-entry limit, complete session operations, API error chains, disambiguation decisions, and platform statistics are written to an exhaustive diagnostic report.
- **Automatic Report Storage & Export:** On Android 10+, reports are automatically persisted to `Downloads/Kitsugi/CrossSyncReports/` as `.txt` files. A "Save Report" button in the cross-sync dialog also allows saving the report to any user-selected location.
- **Provider Conflict Prevention:** If entries of the same type share titles but have conflicting provider IDs (`MediaIdentity.conflictingIdentityKeys`) or cannot be safely disambiguated across multiple candidates, writing to target platforms is skipped for safety and logged to the report.
- **Credential Redaction:** Bearer headers, access/refresh tokens, and client secrets are automatically redacted (`[REDACTED]`) before reports reach disk.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 MyAnimeList Çapraz Senkronizasyon & Shikimori OAuth Doorkeeper Düzeltmesi

- **MyAnimeList Çapraz Eşitleme URL Hatası Çözüldü (`Illegal character in query at index 193`):** MAL API v2 kullanıcı listesi (`/animelist` ve `/mangalist`) isteklerindeki `fields` parametresi içinde yer alan iç içe alan seçicisi (`list_status{...}`) süslü parantezleri (`%7B` ve `%7D`) olarak URL encode edildi. Böylece `java.net.URI`'nin parantezleri reddedip çapraz eşitlemeyi %0'da durdurması tamamen engellendi.
- **Shikimori Doorkeeper Token İstek Düzeltmesi:** Token takas isteğine (`POST /oauth/token`) `redirect_uri` parametresi geri eklendi; `400 Missing required parameter: redirect_uri` hatası tamamen giderildi.
- **Dinamik Pending Redirect URI Eşleştirmesi:** Tarayıcının açıldığı yönlendirme adresi (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob` veya `aniyomi://`) hafızaya alınarak token takası sırasında sunucuya birebir aynı adres gönderilir.
- **Ham JSON Hatası Engellendi:** Shikimori'nin geçersiz oturumlarda döndürdüğü `null` yanıtının parse edilmesiyle oluşan ham `A JSONObject text must begin with '{'` hatası giderildi; tüm yanıtlar null/HTML kontrolleriyle korumaya alındı.
- **Gelişmiş Giriş Diyaloğu & Alternatif Şema:** Giriş penceresine "Shikimori OAuth Uygulamalarım" yönetim bağlantısı ve eski şemayı kullananlar için "Alternatif Tek Tık (aniyomi://)" butonu eklendi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 MyAnimeList Cross-Sync Query Encoding & Shikimori OAuth Doorkeeper Fix

- **MyAnimeList Cross-Sync Query Parsing Fix (`Illegal character in query at index 193`):** Percent-encoded nested field selector braces (`%7B` and `%7D`) in MAL `/animelist` and `/mangalist` endpoints. Fixed `java.net.URI` query syntax crash that aborted cross-account sync at 0%.
- **Shikimori Doorkeeper Token Payload Fix:** Restored `redirect_uri` parameter in token exchange requests, eliminating `400 Missing required parameter: redirect_uri`.
- **Dynamic Pending Redirect URI Matching:** Pairs the authorization redirect URI (`kitsugi://`, `urn:ietf:wg:oauth:2.0:oob`, or `aniyomi://`) with the token exchange step as required by Doorkeeper OAuth.
- **Guarded JSON Parsing:** Eliminated raw `A JSONObject text must begin with '{'` exceptions when encountering `null` or non-JSON payloads from expired session endpoints.
- **Enhanced Login Dialog & Fallback Flows:** Added direct link to Shikimori OAuth management dashboard and an "Alternative 1-Tap (aniyomi://)" button in the login dialog.

---

---

- **Nuvio Libtorrent P2P Motor Entegrasyonu (libtorrent + Boost.Asio):** Nuvio'nun yüksek performanslı C++ libtorrent ve Boost.Asio çekirdeği (`lib-nuvio-engine-android-0.1.2.aar`) Kitsugi'ye entegre edildi. Artık Debrid üyeliği (RealDebrid, AllDebrid vb.) olmadan da Torrentio ve diğer torrent tabanlı Stremio eklentileri doğrudan cihaz üzerinden eşler arası (P2P) ve yüksek hızla oynatılabilir.
- **Gelişmiş Seeding & Yükleme (Upload) Kontrolleri:** Orijinal Nuvio uygulamasında bulunmayan, arka planda sınırsız çalışan gönderme (seeding) davranışı tamamen kullanıcı denetimine açıldı. Stremio eklentileri ayarlarından seed modu tek tıkla kapatılabilir ya da internet kotasını korumak için 256 KB/s, 512 KB/s, 1 MB/s, 2 MB/s hız sınırları atanabilir.
- **P2P Gizlilik & Swarm Güvenlik Bilgilendirmesi:** P2P motoru ilk kez aktif edildiğinde veya Debrid olmadan bir torrent bağlantısına tıklandığında açılan tek seferlik güvenlik diyaloğu eklendi. IP adresinin torrent swarm havuzunda görünebileceği, telif hakları sorumluluğu ve VPN tavsiyeleri net biçimde sunuldu.
- **Tam Ekran Oynatıcı Canlı Torrent Paneli (TorrentOverlay):** Video oynatıcıya bağlanan canlı gösterge paneli sayesinde anlık indirme hızı, yükleme hızı, bağlı eşler (peers), seed sayısı ve önbellek tampon durumu gerçek zamanlı takip edilebilir. Oynatıcı kapatıldığında veya bölüm değiştiğinde P2P motoru gereksiz arka plan trafiğini önlemek için otomatik sonlandırılır.
- **Simkl Toplu Eşitleme (Batch Sync) & 429 Hız Sınırı Koruması:** Simkl API'sinin saniyede 1 istek kuralına ve `title`/`year` zorunluluklarına tam uyum sağlandı. 35'lik akıllı paketler ve 1.1 saniye gecikme ile yüzlerce animenin tek seferde sorunsuz senkronize edilmesi sağlandı; senkronizasyon sonrası Room DB'ye gerçek sunucu ID'leri işlenerek öğelerin sayfa yenilenince kaybolması engellendi.
- **Simkl Medya Detay Sayfası & 404 Koruması:** Simkl kütüphanesindeki öğelerin üzerine tıklandığında oluşan "Veri Yok" ve 404 hataları giderildi; kimlik çözümleyici Simkl ID'lerini doğru yöne kanalize ederek Jikan ve Simkl arama fallback mekanizmalarıyla güçlendirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Nuvio Libtorrent P2P Streaming Engine, Full Seeding & Upload Controls, Simkl Batch Sync & Media Detail Fixes

- **Nuvio Libtorrent P2P Engine Integration (libtorrent + Boost.Asio):** Embedded Nuvio's high-performance C++ libtorrent and Boost.Asio core (`lib-nuvio-engine-android-0.1.2.aar`) directly into Kitsugi. Users can now stream torrent-based Stremio add-ons (such as Torrentio) entirely for free via peer-to-peer swarms without requiring a Debrid subscription.
- **Full Seeding & Upload Bandwidth Controls:** Addressed a critical privacy and bandwidth shortcoming in the original Nuvio app (which seeded indefinitely with no UI controls). Users can now toggle seeding on/off or limit upload bandwidth to 256 KB/s, 512 KB/s, 1 MB/s, or 2 MB/s directly from the Stremio settings tab.
- **P2P Privacy & Swarm Consent Dialog:** Implemented a one-time transparent consent dialog detailing public swarm IP visibility, copyright compliance responsibilities, and VPN usage recommendations before initiating any P2P torrent stream.
- **Real-Time Video Player Torrent Overlay:** Connected fullscreen player metrics to live engine telemetry, displaying instant download speeds, upload speeds, seeders, peers, and buffering progress, with automatic stream teardown on player exit.
- **Simkl Batch Synchronization & 429 Rate-Limit Prevention:** Adhered strictly to Simkl's 1 request/second policy and mandatory title/year metadata requirements. Synchronizes large libraries in 35-item batches with 1.1s throttling, followed by server-verified Room DB persistence to eliminate phantom entries and list reverts.
- **Simkl Media Detail Navigation & 404 Fallback:** Resolved missing detail screens and 404 errors when opening Simkl entries by prioritizing Simkl external IDs and adding Jikan & Simkl title search fallback strategies.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 İzole Süreç Çökme Ekranı (:crash), Canlı Sistem Teşhisi, Çapraz Senkronizasyon & Kararlılık Güncellemeleri

- **İzole Süreç Tabanlı Çökme & Teşhis Ekranı (Isolated :crash Process):** AndroidManifest'e `:crash` ayrılmış süreci entegre edildi. Ana uygulama süreci beklenmedik bir kritik hatayla veya bellek yetersizliğiyle karşılaşsa dahi, çökme ekranı (`KitsugiCrashActivity`) tamamen izole ve temiz bir süreçte anında açılır. Uygulamanın aniden sessizce kapanması ("pat diye kapanma") önlendi.
- **Hakkında Ekranından Doğrudan Teşhis & Rapor Erişimi:** Ayarlar altındaki Hakkında (About) ekranına yeni "Çökme & Hata Teşhis Raporu" butonu eklendi. Kullanıcılar ve test ekibi istedikleri an geçmiş çökme kayıtlarını, logcat çıktılarını ve cihaz donanım teşhisini inceleyebilir veya tek tıkla panoya kopyalayabilir.
- **Çoklu Platform Çapraz Senkronizasyon (Cross-Sync) Güçlendirmesi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori platformları arasındaki kütüphane ve izleme geçmişi senkronizasyonu geliştirildi; diyalog akışı ve durum göstergeleri optimize edildi.
- **Kitsu Bildirim & Detay Entegrasyonu:** Kitsu bildirimlerine dokunulduğunda anime ve manga detay sayfalarının kesintisiz, doğru kimlik çözümlemesiyle ve önbellekten anında kapak aktarımıyla açılması garantilendi.
- **Shikimori 1-Tık Giriş ve Güvenlik Sertleştirmesi:** Shikimori OAuth2 bağlantısında yaşanan 403 ve token çözümleme problemleri kalıcı olarak giderildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Isolated :crash Process Architecture, Live System Diagnostics, Cross-Sync & Core Stability

- **Isolated Process Crash & Diagnostics Screen (:crash Process):** Dedicated isolated process architecture (`:crash`) wired into AndroidManifest. Even if the primary application process experiences an unhandled fatal error or OutOfMemory condition, `KitsugiCrashActivity` launches reliably within a clean sandbox without hangs or freeze loops, preventing abrupt app disappearance.
- **Direct Diagnostics & Crash Hub Access:** Added a dedicated "Crash & Diagnostics Report" button to the About screen. Users and testers can review past crash logs, live logcat output, and device hardware diagnostics on demand, or copy full reports to clipboard in one tap.
- **Multi-Platform Cross-Sync Enhancements:** Streamlined library cross-synchronization across AniList, MyAnimeList, Simkl, Kitsu, and Shikimori, complete with dialog feedback and state reconciliation.
- **Kitsu Notification & Media Detail Navigation:** Guaranteed smooth detail page transitions when launching from Kitsu push notifications, handling both raw and offset entity identifiers with instant poster caching.
- **Shikimori 1-Tap OAuth & Authorization Polish:** Strengthened Shikimori authentication flow against 403 Forbidden edge cases and token renewal hiccups.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Shikimori 1-Tık Otomatik Giriş Çözümü, Kitsu Bildirim Detay Sayfası Düzeltmesi & Kararlılık Güncellemeleri

- **Shikimori 1-Tık Otomatik Giriş ve Deep Link Desteği:** Shikimori OAuth girişinde yaşanan 403 erişim engelleri ve yetkisiz istemci (invalid_client) sorunları kalıcı olarak çözüldü. Resmi `aniyomi://shikimori-auth` deep link yönlendirmesi entegre edildi. Kullanıcı tarayıcıda tek dokunuşla izin verdiğinde uygulama otomatik açılır ve oturumu başlatır.
- **Alternatif Manuel Giriş ve Otomatik Sıfırlama:** Tarayıcıda doğrulama kodunu doğrudan görüntüleyen `urn:ietf:wg:oauth:2.0:oob` protokolü korundu. Gelişmiş ayarlar menüsüne "Varsayılan Anahtarları Geri Yükle" butonu eklenerek cihaz hafızasında kalmış eski/hatalı anahtarların tek tıkla temizlenmesi sağlandı.
- **Kitsu Bildirimlerinden Medya Detayına Geçiş Sorunu Giderildi:** Bildirimler ekranındaki Kitsu bildirimlerine dokunulduğunda oluşan boş ekran veya çökme problemi düzeltildi. `KitsuExploreClient` ve `KitsugiDetailClient` içerisindeki kimlik çözümleme motoru güncellenerek hem ham Kitsu ID'leri hem de ofsetli (`+300,000,000`) ID'ler eksiksiz tanınır hale getirildi.
- **Görsel Bildirim Geçişleri:** Bildirimlerden detay sayfasına giderken anime/manga başlığı ve kapak görseli önbellekten anında aktarılarak sayfa açılışında görsel süreklilik ve akıcı bir deneyim sunuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Shikimori 1-Tap OAuth & Deep Link Fix, Kitsu Notification Detail Resolution & Stability Improvements

- **Shikimori 1-Tap OAuth & Deep Link Integration:** Fixed 403 Forbidden and invalid_client issues during Shikimori authentication. Configured official `aniyomi://shikimori-auth` deep link callback scheme; tapping "Allow" in browser immediately returns to Kitsugi and authenticates seamlessly.
- **Alternative Out-of-Band Auth & Credential Reset:** Preserved manual code generation via `urn:ietf:wg:oauth:2.0:oob`. Added a "Reset to Default Keys" action in advanced settings to purge any corrupted client keys from app storage.
- **Kitsu Notification Media Detail Page Fix:** Resolved an issue where tapping on Kitsu notifications resulted in empty detail screens or failed navigation. Harmonized ID offset mapping in `KitsuExploreClient` and `KitsugiDetailClient` to reliably resolve both raw numeric IDs and offset stable IDs (`+300,000,000`).
- **Seamless Notification Visual Transition:** Titles and cover posters from incoming notifications are now forwarded directly to detail screens, ensuring instantaneous visual feedback without flicker.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Gelişmiş Arama Filtre Paneli, İzole Arama Motorları, Resmi MAL API Entegrasyonu, Shikimori 1-Tık Giriş & Modern Çökme Raporlama

- **Yeni Nesil Arama Kaynak ve Filtre Seçici Paneli (Source Engine Filter Sheet):** Arama sayfasında motorların durumunu, aktifliklerini ve sonuç sayılarını gösteren, kategorilere (Anime, Dizi, Film, Manga, Roman vb.) göre filtrelenebilen yepyeni bir modal alt panel (`SourceEngineFilterSheet`) eklendi.
- **İzole Edilmiş ve Bağımsız Arama Sağlayıcıları:** Çoklu arama motorları birbirinden tamamen izole edildi; bir sağlayıcıda gecikme veya hata olduğunda diğer motorların (AniList, Kitsu, Shikimori, TMDB, Simkl vb.) arama sonuçları kesintisiz olarak anında ekrana gelir.
- **Resmi MyAnimeList (MAL) API Entegrasyonu:** Eski veya kararsız Jikan uç noktaları yerine MyAnimeList'in resmi OAuth2 API altyapısına geçildi. Arama hızı ve kütüphane güvenilirliği en üst seviyeye çıkarıldı.
- **Shikimori 1-Tık Giriş & 403 Forbidden Çözümü:** Shikimori OAuth girişinde yaşanan 403 erişim engeli ve token sorunları giderildi. Tek tıkla tarayıcı yetkilendirmesiyle sorunsuz profil bağlantısı sağlandı.
- **Kitsu & Shikimori Kapsamlı Profil ve Liste Yönetimi:** Profil ekranında Kitsu ve Shikimori hesapları için kütüphane durumları (İzleniyor/Okunuyor, Tamamlandı, Beklemede, Bırakıldı, Planlandı), puan dağılımı, favoriler ve biyografi eksiksiz görüntülenebilir ve güncellenebilir hale getirildi.
- **Gelişmiş Teşhis ve Çökme Raporlama Ekranı (KitsugiCrashActivity & Logger):** Uygulama beklenmeyen bir hatayla karşılaştığında kapanmak yerine modern bir arayüzle hatanın detayını, cihaz donanım bilgilerini ve bellek durumunu gösterir; logları tek tıkla kopyalama veya uygulamayı güvenle yeniden başlatma imkanı sunar.
- **Ayarlar Ekranı Modüler Hesap Sayfaları:** Ayarlar menüsündeki hesap yönetimi ayrıştırılarak AniList, MAL, Simkl, Kitsu ve Shikimori için özel alt sayfalar (`AccountSettingsSubPages`) oluşturuldu.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Advanced Search Filter Sheet, Isolated Search Engines, Official MAL API, Shikimori 1-Tap OAuth & Modern Crash Activity

- **Next-Gen Source Engine Filter Bottom Sheet:** Introduced a comprehensive modal bottom sheet (`SourceEngineFilterSheet`) on the Search screen with per-source toggles, status indicators, and categories (Anime, Movies, TV Series, Manga, Light Novels).
- **Isolated Multi-Search Provider Pipeline:** Search engines are now completely isolated; network timeouts or throttles on one provider no longer affect parallel search results from others (AniList, Kitsu, Shikimori, TMDB, Simkl).
- **Official MyAnimeList (MAL) API Migration:** Fully migrated search and library operations to official MyAnimeList OAuth2 endpoints, eliminating third-party rate limits and improving result speed.
- **Shikimori 1-Tap OAuth & 403 Forbidden Fix:** Fixed 403 Forbidden errors during Shikimori authorization, enabling an effortless 1-tap browser auth flow.
- **Kitsu & Shikimori Full Profile & Library Management:** Enriched profile screens with full status tracking (Watching/Reading, Completed, On Hold, Dropped, Plan to Watch/Read), score charts, favorites, and profile updates.
- **Advanced Diagnostics & Modern Crash Screen (KitsugiCrashActivity & Logger):** Replaced default unhandled exception crashes with a modern diagnostics screen showing breadcrumbs, system specifications, memory status, stacktrace copying, and 1-tap safe app restart.
- **Modular Account Settings Subpages:** Restructured account management into modular subpages (`AccountSettingsSubPages`) for AniList, MAL, Simkl, Kitsu, and Shikimori with clear connection status and sync actions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Zenginleştirilmiş Sistem Bildirimleri, Kitsu & Shikimori Profilleri, Profil Kaynak Seçici Paneli ve Orijinal Logolar

- **Zengin ve Görsel Sistem Bildirimleri (Drawer & Status Bar):** Android bildirim çekmecesi bildirimleri artık çok daha ayrıntılı ve yüksek çözünürlüklü kapak/avatar görselleriyle (`LargeIcon` & `BigPictureStyle`) donatıldı. Bildirimler artık hangi kaynaktan geldiğini açıkça belirtir (`[AniList]`, `[MyAnimeList]`, `[Simkl]`). Sistem çekmecesi bildirimleri ile uygulama içi bildirim ekranı metin ve biçimlendirme açısından birebir senkronize edildi.
- **Profil Sayfası Açılır Kaynak Seçim Paneli (Profile Source Picker Sheet):** Arama ve Keşfet sayfalarında olduğu gibi profil sayfasına da açılır seçim paneli (`ProfileSourcePickerSheet`) ve orijinal logolu hap butonu eklendi. Tek dokunuşla tüm bağlı/bağlantısız profiller arasında geçiş yapılabilir.
- **Kitsu ve Shikimori Profil Desteği Tamamlandı:** Profil sayfasına eksik olan Kitsu ve Shikimori sekmeleri eklendi. Kullanıcı biyografisi, waifu/husbando, takipçi verileri, anime/manga durum istatistikleri ve kütüphane kayıtları eksiksiz görüntülenir.
- **Orijinal Platform Logoları (Sıfır Emoji Kuralı):** Tüm sayfalardaki kaynak butonları, seçim panelleri ve sekmelerdeki emojiler veya boş kutular kaldırılarak yerini yüksek kaliteli orijinal platform logoları (`KitsugiPlatformLogo`) aldı.
- **Yeni GitHub Hesabı & Depoları Entegrasyonu:** Kod tabanı yeni `f26901964-eng/Kitsugi-Beta` ve `f26901964-eng/Kitsugi-Plugins` depolarına uyarlandı. Eklenti deposundan tüm +18 içerikler temizlenerek güvenli havuz devreye alındı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Rich System Notifications, Kitsu & Shikimori Profiles, Source Picker Sheet & Original Logos

- **Rich Visual System Drawer Notifications:** Android status bar and drawer notifications now include high-resolution poster artwork and avatars via `LargeIcon` and `BigPictureStyle`. Every notification clearly highlights its origin source (`[AniList]`, `[MyAnimeList]`, `[Simkl]`), completely harmonized with the in-app notification center.
- **Profile Source Picker Bottom Sheet:** Added a modal selection sheet and original logo pill button to the Profile screen, matching the Search and Explore user experience.
- **Kitsu & Shikimori Full Profile Integration:** The Profile screen now fully supports Kitsu and Shikimori tabs, displaying bio, social metrics, anime/manga status breakdown charts, score distributions, and library records.
- **Original Platform Logos (Strictly No Emojis):** Replaced all emoji badges and generic colored boxes across buttons, sheets, and tabs with authentic, high-res platform vector logos (`KitsugiPlatformLogo`).
- **New GitHub Ecosystem Integration:** Fully wired the application and plugin repositories to the new `f26901964-eng` GitHub account, with an adult-free safe plugins pool and updated in-app update mechanisms.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Kitsu Düzeltmeleri, Grid/Liste Hatırlama, Yapışkan Bar Filtresi, TMDB İngilizce Fallback, Simkl Dizi/Film Keşfeti ve Çökme Korumaları

- **Grid / Liste Görünümü Kalıcı Olarak Hatırlanıyor:** Popüler, Trend vb. tam ekran medya sayfalarındaki liste veya grid görünüm seçimi artık kaydedilir; sayfadan çıkıp tekrar girildiğinde kullanıcının son seçtiği görünüm modu korunur.
- **Yapışkan Üst Çubukta Filtre Butonu Düzeltildi:** Sayfa aşağı kaydırıldığında beliren kompakt üst çubukta filtre butonunun kaybolması sorunu çözüldü; filtreleme ve sıralama butonu artık her kategoride ve kaydırma durumunda görünür durumdadır.
- **TMDB Keşfet'te Türkçe Başlığı Olmayan İçerikler İçin Otomatik İngilizce Desteği:** TMDB'den çekilen içeriklerde Türkçe başlık bulunmadığında Japonca/Kanji başlık yerine otomatik olarak İngilizce başlık (örn. "Souryo to Majiwaru Shikiyoku no Yoru ni...", "Puella Magi Madoka Magica...") gösterilir.
- **Simkl Keşfet'e Dizi ve Film Desteği Eklendi:** Simkl platformu artık sadece animelerle kısıtlı kalmayıp TMDB gibi Trend Diziler, Trend Filmler, Popüler Diziler, Popüler Filmler ve en yüksek puanlı içerikleri eksiksiz gösterir.
- **Kitsu Anime Ekleme / Düzenleme Sayfası Düzeltildi:** Kitsu üzerinden açılan animelerde "Listeye Ekle / Düzenle" alt sayfasının MyAnimeList olarak açılması ve MAL alanlarının görünmesi sorunu çözüldü. Artık Kitsu renkleri (turuncu), Kitsu kimlik etiketi ve Kitsu'ya özgü liste durumu seçenekleri açılır.
- **Kitsu Karakterler Sekmesi Takılma / Yüklenmeme Sorunu Giderildi:** Anime detay sayfasında "Resimler" sekmesi eklendikten sonra meydana gelen indeks kayması giderildi; "Karakterler" sekmesine tıklandığında artık iskelet ekranda takılı kalmadan tüm karakterler eksiksiz yüklenir.
- **Kitsu Kişi Bilgisi ve Galeri Kaynak Çelişkisi Düzeltildi:** Kitsu'dan açılan yönetmen/seslendirmen/ekip detayında "ANILIST" yerine doğru platform ("Kitsu") etiketi gösterilir; profil fotoğrafı tam ekran açıldığında da "Jikan" ve "🎭 Karakter" yerine doğru kaynak ve "👤 Kişi" kategorisi kullanılır. Kitsu ID ile kişi adı önce doğrudan Kitsu API'den çözümlenerek doğru eşleştirme sağlanır.
- **Rastgele Uygulama Çökmeleri Engellendi (Crash Shield & Looper Koruması):** Sayfa geçişlerinde, video izlerken veya eklentilerden veri gelmesini beklerken arka plan iş parçacıklarında, ağ zaman aşımlarında ya da harici eklenti kodlarında oluşan beklenmedik istisnaların uygulamayı aniden kapatması engellendi. Ana iş parçacığı döngü koruyucusu (Cockroach Looper Recovery) ve `SupervisorJob` takviyesiyle donatıldı.
- **Eklenti Deposu (Kitsugi Plugins) Sorunsuz Yükleme Garantisi:** "Kitsugi Eklentileri (Önerilen)" deposu eklenirken yaşanan ISS engelleri ve ağ zaman aşımları tamamen çözüldü. Hem doğrudan Codeberg bağlantısı hem REST API hem de dahili çevrimdışı katalog yedeği devreye alındı.
- **Shikimori Giriş Akışı Düzeltildi:** "The requested redirect uri is malformed" hatası giderilerek RFC 6749 uyumlu URL-encode OAuth2 giriş akışı sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Grid Persistence, Sticky Filter Button, TMDB English Fallback, Simkl TV/Movies & Crash Shield

- **Grid / List Mode Persistent Memory:** The layout toggle preference on full-screen media grid pages is now saved and remembered across visits.
- **Sticky Top Bar Filter Button Fix:** Fixed the filter and sorting button disappearing from the compact sticky header upon scrolling down; it is now always visible and positioned properly next to the layout toggle.
- **TMDB Explore English Title Fallback:** When Turkish titles are not localized on TMDB, titles automatically fall back to their English/Romaji counterparts instead of Japanese Kanji/Kana.
- **Simkl Explore TV & Movie Expansion:** Simkl explore is no longer limited to anime; it now features full TV show and movie sections (trending, popular, top-rated) matching the TMDB experience.
- **Kitsu Media Editor Sheet Fix:** Fixed Kitsu anime entries opening the edit sheet with "MyAnimeList" branding and MAL status fields. Proper Kitsu branding, theme color, and sync properties are now respected.
- **Kitsu Characters Tab Loading Fix:** Resolved tab index offset bug causing the Characters tab to freeze with skeleton loaders indefinitely; characters now load immediately.
- **Person / Staff Detail & Gallery Attribution Fix:** Resolved source attribution conflicts when opening staff from Kitsu (showing "ANILIST" badge or "Jikan / Character" in fullscreen gallery). Now correctly attributes Kitsu and "Person" category, with pre-resolution against Kitsu's `/people` API.
- **Random Crash Shield & Main Looper Protection:** Prevented random crashes during navigation, video playback, and extension scraping. Installed a resilient main Looper recovery loop and `SupervisorJob` scopes across extension loaders and prefetch coroutines to isolate non-fatal exceptions.
- **Plugin Repository Bulletproof Loading:** Resolved repository manifest loading failures caused by ISP blocks with Codeberg API fallback and bundled 174-plugin offline catalog.
- **Shikimori OAuth Redirect URI Fix:** Fixed "The requested redirect uri is malformed" error with proper RFC 6749 URL encoding and streamlined OAuth flow.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Keşfet Arayüzü, "Tümü" Butonu, Kitsu & Shikimori Girişleri, Bildirim Önizleme ve Çapraz Eşitleme

- **Keşfet Sayfasında Çift Bar Fazlalığı Giderildi:** Keşfet sayfasında hem açılır seçim menüsü hem de alttaki yatay çip çubuğunun oluşturduğu karmaşa giderildi; gereksiz yatay çip barı kaldırılarak tekil, şık ve fonksiyonel kaynak seçici korundu.
- **Listem Sayfası "Tümü" Butonu Alt Bar ile Uyumlu Hale Getirildi:** "Tümü" kategori seçim butonu artık alt gezinti çubuğunun (Bottom Navigation Bar) üzerinde taşma veya örtüşme yapmaz. Diğer sayfalardaki FAB butonlarıyla birebir aynı biçimde, alt çubuğun tam üzerinde hizalanır ve kaydırma hareketleriyle pürüzsüzce animasyonlanır.
- **Kitsu Giriş Hatası ("Client authentication failed") Çözüldü:** Kitsu API OAuth2 token uç noktasına yönelik istemci anahtarları ve OAuth2 standart form-urlencoded yapısı güncellendi. Kullanıcılar artık e-posta/kullanıcı adı ve şifreleriyle doğrudan ve hatasız giriş yapabilir.
- **Shikimori Giriş Akışı Sadeleştirildi:** Kullanıcıdan karmaşık Client ID, Client Secret oluşturması isteme zorunluluğu kaldırıldı. Dahili kimlik bilgileriyle tek dokunuşla tarayıcıda Shikimori onay sayfası açılır, ekrandaki kod yapıştırılarak anında bağlantı kurulur.
- **AniList Bildirimlerine Tıklayınca Doğrudan Aktivite/Beğeni Detayı Açma:** Beğeni veya yorum bildirimlerine tıklandığında yalnızca körü körüne kullanıcı profiline gitmek yerine, tam olarak hangi paylaşımın/içeriğin beğenildiği veya yorumlandığını gösteren detay sayfası ve ilgili anime/dizi bağlantıları açılır.
- **Tüm Hesapları Birbiriyle Eşitle (5 Platform Çapraz Senkronizasyon) Düzeltildi:** AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesapları arasında anime, dizi ve film içerikleri eksiksiz olarak birbirine eşitlenir; her bağlı platformun kütüphanesindeki eksik içerikler tespit edilip hem yerel listelere hem de ilgili servislere sorunsuz aktarılır.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Explore UI, "All" FAB, Kitsu & Shikimori Auth, Activity Notifications & Cross-Sync Matrix

- **Explore Top Bar Deduplication:** Removed the redundant horizontal chip row below the platform selector pill on the Explore screen, keeping a clean and unified interface.
- **MyList "All" FAB Bottom Bar Alignment:** The category button now sits properly above the Material3 navigation bar without overlapping, animating smoothly on scroll just like floating action buttons on other screens.
- **Kitsu Authentication Fix:** Resolved the "Client authentication failed" error by adopting official Kitsu public OAuth2 credentials and urlencoded form specifications.
- **Simplified Shikimori OAuth Flow:** Users no longer need to create developer applications or enter client credentials. A 1-tap browser authorization with built-in keys allows pasting the code directly.
- **Notification Activity Preview:** Clicking activity or like notifications now opens the exact activity/post modal showing what was liked/commented with direct media links, instead of merely navigating to the user profile.
- **Full Cross-Platform Synchronization Matrix:** The "Sync All Accounts" feature now equalizes anime, TV shows, and movies across all connected accounts (AniList, MAL, Simkl, Kitsu, Shikimori), properly updating local tabs and cross-syncing missing library items.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Deposu ve Eklenti Yükleme Sorunları Tamamen Giderildi

- **Codeberg Depo ve Eklenti Yüklenememe Sorunu Çözüldü:** Repo URL doğrulayıcı (`RepoVerifier`), `codeberg.org` alan adını artık varsayılan olarak güvenilir listeye aldı. Ek olarak `.git`, depo kök dizini veya eski GitHub bağlantıları otomatik olarak optimize edilmiş Codeberg manifestine yönlendirildi.
- **"Eklentiler Yüklenemedi" Hatası & Otomatik Yenileme:** Ağ gecikmesi veya ilk açılışta başarısız olan depo isteklerinde eklenti listesinin takılı kalması engellendi; otomatik yeniden deneme ve akıllı önbellek yenileme mekanizması devreye alındı.
- **Ağ Zaman Aşımı & Proxy Desteği:** Yavaş bağlantılarda ve ISS kısıtlamalarında eklenti ve repo indirmelerinin zaman aşımına uğramaması için okuma/bağlantı süreleri 30 saniyeye çıkarıldı, jsDelivr CDN yedeği güçlendirildi.
- **Eski ve Bozuk Depoların Temizliği:** Veritabanındaki eski/çift veya hatalı depo adresleri açılışta otomatik olarak temizlenerek resmi `Kitsugi Plugins (Önerilen)` deposu altında birleştirildi.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Extension Repository & Plugin Loading Fixes

- **Codeberg Domain Trust & Repo Normalization:** `codeberg.org` is now unconditionally trusted in `RepoVerifier`. All repository formats (root URLs, `.git`, legacy GitHub links) now seamlessly normalize to canonical Codeberg endpoints.
- **Automatic Retry for Extension Listings:** Fixed an issue where temporary network failures left repo cards stuck on "Eklentiler yüklenemedi". Repos now auto-retry upon opening or refreshing.
- **Extended Timeouts & CDN Fallback:** Connect and read timeouts increased to 30s to prevent dropouts on throttled networks.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Eklenti Kurulum Hatası Giderildi & Codeberg Eklenti Havuzu Canlıya Alındı

- **Eklenti Kurulumu ve İndirme Hatası Tamamen Çözüldü:** Yeni Codeberg `KitsugiPlugins` deposundaki tüm 174 eklenti (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) artık sorunsuz, doğrudan ve tek tıkla kurulmaktadır. Dosya bütünlüğü ZIP doğrulamasıyla garanti altına alınmıştır.
- **Doğrudan Codeberg URL Koruması:** Eklenti indirme ve repo yenileme sırasında Codeberg URL'lerinin bozulması engellenmiş, eski GitHub bağlantıları otomatik olarak yeni depoya yönlendirilmiştir.
- **Sürüm Güncellemesi:** Önceki derlemelerdeki önbellek çakışmalarını gidermek amacıyla v2.4.194 olarak paketlenmiştir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Plugin Installation Error Fixed & Codeberg Plugin Pool Live

- **Extension Download & Installation Completely Fixed:** All 174 plugins (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime, etc.) in the new Codeberg `KitsugiPlugins` repository now install seamlessly with 1-click installation.
- **Direct Codeberg URL Preservation:** Plugin download URLs are safely preserved and legacy GitHub endpoints are automatically routed to the new repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🚀 Codeberg Entegrasyonu & Eklenti Havuzu Güvenliği (+18 Temizlendi)

- **Codeberg Otomatik Güncelleme Entegrasyonu:** Uygulama içi otomatik güncelleyici (`KitsugiUpdateRepository`) artık birincil güncelleme kaynağı olarak Codeberg Releases API'sini (`BlackDamage/Kitsugi-Beta`) kullanır. Olası bir durumda GitHub API'sine yedek (fallback) olarak bağlanır.
- **Güvenli ve Temiz Eklenti Deposu (+18 / NSFW Kaldırıldı):** Eklenti deposu GitHub'dan Codeberg'e (`BlackDamage/Kitsugi-Plugins`) taşındı. Topluluk ve mağaza kurallarına tam uyum için tüm yetişkin (+18/NSFW), ifşa ve deepfake eklentileri havuzdan kalıcı olarak temizlendi; 170+ popüler dizi, film, anime ve belgesel eklentisi (DiziPal, RecTV, FilmMakinesi, InatBox, TurkAnime vb.) optimize edilerek korundu.
- **Eski Depo URL'lerini Otomatik Yönlendirme:** Uygulama açılışında ve eklenti taramasında eski GitHub bağlantıları (`gameras1010-afk`, `KitsugiBeta-dev`, `Kekik-cloudstream`) otomatik olarak yeni Codeberg deposuna yönlendirilir.

---

## 🇬🇧 English (v2.4.194)

### 🚀 Codeberg Migration & Clean Plugin Repository (+18 Excluded)

- **Codeberg In-App Auto-Updates:** Primary update checking and release asset downloading has been migrated to the Codeberg Releases API (`BlackDamage/Kitsugi-Beta`) with GitHub fallback.
- **Clean & Safe Plugin Repository (+18 Excluded):** Migrated cloudstream plugins to Codeberg (`BlackDamage/Kitsugi-Plugins`), completely removing 116+ NSFW / adult / deepfake plugins to guarantee longevity and platform TOS compliance. All 170+ mainstream movie, anime, TV, and documentary sources remain fully available and updated.
- **Automatic Legacy URL Redirection:** Legacy GitHub URLs for plugins are seamlessly redirected to the new Codeberg repository.

---

## 🇹🇷 Türkçe (v2.4.194)

### 📚 Türkçe Manga & Webtoon Altyapısı Kökten Yenilendi (Keiyoushi V2, 77 TR Kaynağı, MangaDex 3.450 TR Başlık, Hotlink Çözümü)

- **Keiyoushi V2 Repo Şeması & Decoy Koruması:** Keiyoushi'nin modern `index.json` V2 şeması (`extensionList.extensions[]`) tam olarak desteklendi. 2 sahte kayıtlı decoy `index.min.json` otomatik bypass edilerek doğrudan `index.json` üzerinden tüm eklentiler listelenir hale getirildi. 404 veren ölü katalog URL'leri temizlendi.
- **2026 Keiyoushi İmza Anahtarı:** Yeni SHA-256 parmak izi (`9add655a...`) `TrustManager`'a eklendi ve repodan gelen eklentiler için dinamik güven zinciri entegre edildi.
- **Modern Eklenti API'sine Geçiş (extensionLib 1.6+):** 77 Türkçe eklentinin 71'inde detay ve bölüm listelerinin boş gelmesine sebep olan eski deprecated API çağrıları kaldırıldı; modern `getMangaUpdate` suspend API'sine geçildi.
- **MangaDex & Global Katalog Aramaya Dahil Edildi (3.450 TR Başlık):** `MangaSearchCoordinator` filtresindeki mantıksal engel kaldırılarak MangaDex gibi çok dilli global kaynaklar arama adaylarına eklendi. TR kaynaklarda bulunamayan mangalar MangaDex'teki 3.450 Türkçe çeviri üzerinden anında bulunabilir hale geldi.
- **Sayfa Görsellerinde Hotlink Koruması Çözüldü:** Manga-TR, TRManga, WebtoonHattı gibi hotlink korumalı CDN'lerin 403 Forbidden dönmesi engellendi. `MangaPageLoaderV2`, görselleri kaynağın kendi `Referer`, özel User-Agent ve Cloudflare interceptor'ları ile indirip doğrudan disk önbelleğine yazar ve Telephoto okuyucuya sunar.
- **Arama Eşzamanlılık Kapısı (Semaphore 6) & Akıllı Zaman Aşımı:** Aynı anda 70+ kaynağa istek atıp 429 veya bağlantı kopması yaşanmasını engellemek için 6'lı eşzamanlılık kapısı eklendi. Yeni nesil Keiyoushi motoru tanınarak zaman aşımı 25 saniyeye optimize edildi.
- **İki Geçişli Arama & Doujinshi Filtresi:** Arama sonuçlarının ilk turda gereksiz doujinshi veya spin-off'larla dolması engellendi. Önce sıkı skorlama yapılır, sonuç yoksa gevşek skorlama devreye girerek doğru manga ilk sıraya yerleştirilir.
- **Kaynak Sağlık Sınıflandırması İyileştirildi:** Arama teriminin bulunamaması (404) veya geçici DNS hataları ("unable to resolve host") artık kaynakları 2 saatlik "Broken" cezasına sokmaz; "Degraded" seviyesinde tutularak kaynakların kaybolması engellendi.
- **Eksik Host Shim Sınıfları Eklendi:** Eklentilerin ihtiyaç duyduğu `AppInfo`, `UnmeteredSource`, `RateLimitInterceptor` gibi sınıflar shim katmanına eklenerek sınıf yükleme çökmeleri giderildi.

---

## 🇬🇧 English (v2.4.194)

### 📚 Turkish Manga & Webtoon Engine Complete Overhaul (Keiyoushi V2, 77 TR Sources, MangaDex 3,450 TR Titles, Hotlink Bypass)

- **Keiyoushi V2 Schema & Decoy Bypass:** Added full support for the modern Keiyoushi V2 `index.json` schema (`extensionList.extensions[]`). Automatically bypasses the decoy 2-entry `index.min.json` and loads extensions directly from `index.json`. Removed dead 404 catalog URLs.
- **2026 Signing Key Trust:** Added the latest Keiyoushi SHA-256 fingerprint (`9add655a...`) to `TrustManager` and established dynamic signature trust for repository extensions.
- **Modern Extension API (extensionLib 1.6+):** Migrated from deprecated `fetchMangaDetails`/`fetchChapterList` stub methods to the modern `getMangaUpdate` suspend API, restoring detail and chapter lists across 71+ Turkish extensions.
- **MangaDex & Global Catalogs in Search (3,450 TR Titles):** Fixed a boolean logic barrier in `MangaSearchCoordinator`, enabling MangaDex and trusted global catalogs to participate in manga searches alongside Turkish sources.
- **Hotlink Protection Bypass in Image Loader:** Resolved 403 Forbidden errors on hotlink-protected CDNs (Manga-TR, TRManga, WebtoonHattı). `MangaPageLoaderV2` now prioritizes the extension's native `source.getImage()` pipeline with correct `Referer`, User-Agent, and Cloudflare cookies directly into disk cache for the Telephoto reader.
- **Concurrency Gate (Semaphore 6) & Smart Timeouts:** Prevented network congestion and 429 rate limits by bounding concurrent searches to 6 parallel requests. Added modern `KEI_SOURCE` engine detection and increased timeout to 25s for stable scraping on mobile networks.
- **Two-Pass Search Scoring & Doujinshi Guard:** Searches now execute a strict first-pass match before relaxing filters, preventing doujinshi and loose spinoffs from polluting main search results.
- **Softened Health Classifier:** Network DNS errors ("unable to resolve host") and search 404s now mark sources as Degraded rather than Broken, preventing false 2-hour cooldown lockouts.
- **Missing Extension Host Shims:** Added missing `AppInfo`, `UnmeteredSource`, and `RateLimitInterceptor` host shims to prevent ClassNotFound exceptions when loading extensions.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎭 TMDB Kurgusal Karakter Detayları, Seslendirmen Ayrımı, AL Arama ve Kesintisiz Keşfet (Kitsu Fallback)

- **TMDB Kurgusal Karakter Detay Sayfası Düzeltildi:** TMDB yapımlarındaki anime/kurgusal karakterlere (ör. Nobita Nobi, Doraemon, Rudeus Greyrat) tıklandığında seslendirmenin (ör. Megumi Oohara, Wasabi Mizuta) biyografi ve doğum günü yerine doğrudan karakterin kurgusal biyografisi, anime rolleri ve tüm seslendirmenlerinin listelendiği sayfa açılacak şekilde düzeltildi.
- **Karakter Görsel Karışıklığı Kökten Çözüldü:** Mushoku Tensei gibi aynı soyadını ("Greyrat") taşıyan karakterlerin (Rudeus, Former Self, Eris) ve rol varyantlarının yanlış token eşleşmesiyle aynı görseli alması engellendi. Öz isim doğrulaması (given-name guard) ve varyant filtreleri eklendi.
- **AniList (AL) Arama Sorunu Giderildi:** AniList GraphQL sorgusunda eksik olan `$isLicensed: Boolean` tanımı eklenerek sunucudan HTTP 400 Bad Request dönmesi ve aramalarda AL satırının kaybolması düzeltildi.
- **AniList Keşfet Sayfası & Kitsu Otomatik Fallback:** AniList keşfet sayfasındaki genel erişim iyileştirildi; sunucu hatası, hız sınırı veya boş liste durumlarında Kitsu keşfet motorunun otomatik devreye girerek listeleri eksiksiz doldurması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎭 TMDB Fictional Character Details, Voice Actor Separation, AL Search & Seamless Kitsu Fallback

- **Fictional Character Resolution in TMDB:** Tapping anime characters in TMDB entries now reliably opens the character's fictional profile (biography, appearances, and voice actors) via AniList/Jikan rather than displaying the real voice actor's personal biography and birthday.
- **Character Image Pollution Fixed:** Resolved an issue where characters with shared family surnames (e.g., Greyrat in Mushoku Tensei) or role variants ("Former Self") mistakenly received the same image. A robust given-name guard and variant modifier matching algorithm ensures exact individual character mapping.
- **AniList Search Restored:** Fixed an HTTP 400 Bad Request caused by a missing `$isLicensed: Boolean` definition in the AniList GraphQL search query. AniList results now reliably appear in both "All" and "Anime" search tabs.
- **AniList Explore & Seamless Kitsu Fallback:** Restored public AniList explore browsing without token requirements. Whenever AniList returns empty data or encounters issues, Kitsu automatically and seamlessly populates the explore carousels.

---

## 🇹🇷 Türkçe (v2.4.194)

### 🎬 TMDB Film/Dizi ID Eşleme Düzeltmesi & Yorumlardaki Resim Büyüme Sorunu Çözüldü

- **TMDB Anime Filmleri Diziyle Karışma Sorunu Düzeltildi:** TMDB keşfet sayfalarında "Howl's Moving Castle" (Yürüyen Şato) gibi anime filmlerine tıklandığında film yerine aynı TMDB ID numarasına sahip "Roar" adlı TV dizisinin açılması sorunu giderildi. TMDB anime filmlerinin türü doğru şekilde `MediaType.Movie` olarak belirlendi, başlık doğrulaması eklendi ve önbellek film/dizi ayrımıyla güçlendirildi.
- **Yorumlar & Tartışmalardaki Resimlerin Yavaşça Büyümesi Düzeltildi:** Konu detayı ve yorumlarda paylaşılan görsellerin ve GIF'lerin sayfa açıldıkça veya render edildikçe yavaşça kendi kendine büyüyüp genişlemesi (Compose `animateContentSize` döngüsü) durduruldu. Görseller artık anında sabit orantıyla yüklenir, maksimum yükseklik sınırı (240dp) ile kart düzenini bozmaz ve tıklandığında tam ekran galeri açılır.
- **İndirme Ayarları Veri Yönetiminden Ayrıldı:** Veri & Yedekleme içindeki "İndirmeler" sekmesi oradan çıkarıldı; Ayarlar menüsünde doğrudan "İndirme Ayarları" bağımsız bir sayfa olarak yer aldı. Veri & Yedekleme ekranı sadeleştirildi.
- **İndirmeler Ekranı Sağa/Sola Kaydırılabilir Yapıldı:** İndirmeler sayfasındaki Videolar, Altyazılar ve Resimler sekmeleri arasında parmakla sağa ve sola kaydırarak (swipe gesture) geçiş desteği eklendi.
- **Arama Sayfası Yukarı Kaydırma Butonu & Alt Bar Senkronizasyonu:** Arama sayfasında aşağı kaydırırken alt bar ile yukarı kaydırma butonunun (FAB) üst üste binmesi sorunu giderildi. Buton alt barın durumuna göre dinamik olarak barın üzerinde konumlanır, butona tıklandığında alt bar anında geri gelir ve sayfa tepeye ulaştığında alt barın görünür kalması sağlandı.

---

## 🇬🇧 English (v2.4.194)

### 🎬 TMDB Movie/TV ID Mapping Fix, Comment Image Auto-Expansion Resolved & Downloads Navigation

- **TMDB Anime Movie/TV Confusion Resolved:** Fixed an issue where clicking anime movies on TMDB explore (such as *Howl's Moving Castle*) opened a TV show (*Roar*) with the same TMDB ID. Anime movies are now accurately typed as `MediaType.Movie`, verified by title matching, and differentiated in Room cache.
- **Forum & Comment Image Auto-Growing Fixed:** Fixed the issue where inline images and GIFs in discussion threads and comments slowly expanded on their own due to Compose `animateContentSize` layout passes. Inline images now display immediately with fixed bounds (max 240dp height), preserving clean comment layouts.
- **Dedicated Download Settings Subpage:** Removed the download settings tab from inside "Data & Backup". Created a clean, standalone "Download Settings" item in the Settings menu for quick access.
- **Horizontal Swipe for Downloads Screen:** The Downloads screen (Videos, Subtitles, Images) is now horizontally swipeable with smooth paging gestures.
- **Search Screen Scroll-to-Top & Bottom Bar Sync:** Fixed the overlap between the scroll-to-top floating button and the bottom navigation bar on the Search screen. The button dynamically floats above the bottom bar, and tapping it immediately restores the bottom bar while scrolling to the top.
