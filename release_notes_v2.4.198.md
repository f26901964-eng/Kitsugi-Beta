# Kitsugi Beta v2.4.198 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe (v2.4.198)

### 🔞 1. +18 Bulanıklık & Yetişkin İçerik Güvenliği (Her Koşulda Tam Koruma)
- **46 Arayüz Yüzeyinde Eksiksiz Uygulama:** Ana sayfa, Keşfet şeritleri, Arama sonuçları, Karakter/Personel detayları, Galeri pencereleri ve Android TV arayüzlerinde +18 afiş ve görseller ayara tam uyumlu olarak maskelenir.
- **Eski Android Sürümleri İçin Yedek Motor (API < 31 Fallback):** Android 12 altındaki cihazlarda sistem düzeyindeki `Modifier.blur` sessizce devre dışı kaldığı için afişlerin açık kalması sorunu, Coil tabanlı `BlurTransformation` ve akıllı görsel boru hattı (`KitsugiNsfwImage`) ile çözüldü. Artık her cihazda bulanıklık garantilidir.
- **Platform Başına Doğrulanmış +18 Kuralları:**
  - **Kitsu:** R18 derecelendirmesi, `nsfw=true` veya hentai alt türü (`KitsuAdultFlags`).
  - **Shikimori:** Yalnızca `rx`/`hentai` yapımlar (yanlışlıkla maskelenen `r`/`r_plus` ayrıldı).
  - **Simkl:** `adult=true` bayrağı ve resmi yaş sertifikaları (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** `isAdult` ve `adult` alanları keşif takvimleri ve vitrinlere eksiksiz aktarılır.
  - **Manga Eklentileri:** API bayrağı olmayan kaynaklar için etiket ve tür analizi (`hentai`, `doujin`, `xxx`... ecchi hariç tutularak).
- **Kişisel Liste İçe Aktarımı:** Kitsu, Shikimori ve Simkl içe aktarımları `isAdult` bilgisini ilk andan itibaren kaydeder; "Listem"deki yapımlar bile baştan işaretlenir.

---

### 📌 2. Kaynak Seçimi Kalıcılığı & Kitsu Kimlik İzolasyonu
- **Kalıcı Kaynak Tercihleri (Keşfet / Arama / Listem):**
  - **Keşfet:** Seçilen kaynak (`ExplorePlatform`: Tümü / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) `SettingsDataStore` üzerinde saklanır ve sonraki açılışta hatırlanır. Otomatik geçici düşüşler (fallback) kalıcı seçim sayılmaz.
  - **Arama:** Kaynak motoru, arama kapsamı, sekme, platform ve medya türü senkronize olarak saklanır; açılışta gereksiz arama isteği tetiklemez.
  - **Listem:** Kaynak sekmesi davranışı Keşfet ve Arama ile tam uyumlu hale getirildi.
  - **Performans:** Son kaynak TMDB veya Tümü değilse, açılıştaki 11 istekli TMDB ön-yükleme sorguları atlanarak açılış hızlandırılır.
- **Kitsu Alakasız Ayrıntı Sayfası Düzeltmesi:**
  - Kitsu içe aktarımında ham MAL kimliklerinin Kitsu kimlik alanına yazılması ve sayısal çakışma sonucu bambaşka yapımların açılması (ör. Keep Your Hands Off Eizouken!) kökten çözüldü.
  - Yeni `KitsuIdNamespace` mimarisi (`300M+1..399M+1` aralığı), stableId çözümleyici ve sıkı başlık+yıl kontrolü entegre edildi.
  - `KitsuIdentityMigration`: Mevcut kayıtları ağ trafiği harcamadan yerel eşlemelerle güvenli stableId alanına taşır.

---

### ♾️ 3. Simkl Gerçek Sayfalama & Akış İyileştirmeleri (20'şer Sonsuz Kaydırma)
- **100 Sınırı Kaldırıldı — Kesintisiz Kaydırma:** Simkl artık statik bir sınırda durmak yerine, diğer kaynaklar gibi aşağı kaydırdıkça 20'şer 20'şer yeni içerik getirmeye devam eder.
- **Resmî Simkl API Endpoint Mimarisi:**
  - **Tür & Popülerlik Listeleri:** `genres/...` endpoint'leri üzerinden `page` ve `limit` parametreleriyle 20 sayfaya kadar (~1200 içerik) gerçek sayfalama.
  - **Yakında Yayında:** `anime/premieres/soon` ve `premieres/new` ile 20'şer içeriklik sayfalı akış.
  - **Trend Listeleri:** Sayfa parametresi bulunmayan Simkl trend JSON'ları için 500'lük Top snapshot (`today_500.json`, `week_500.json`) 1 saat boyunca önbelleğe alınır ve istemci tarafında 20'şer dilimler halinde kaydırılır.
  - **Yayında Olanlar (Airing):** Takvim endpoint'inden tek seferde eksiksiz liste çekilir.
- **Arama Tarafında Sayfalama:** Simkl arama ve gelişmiş arama (`search`, `searchAdvanced`) `page` desteğine kavuştu. Hatalı path parçaları (`all-types` vb.) resmi dokümandaki `all` standartlarına getirildi.
- **Zorunlu API Parametreleri & User-Agent:** Tüm isteklere Simkl'in talep ettiği `client_id`, `app-name=kitsugi`, `app-version` ve `KitsugiApp/... (Android)` User-Agent başlığı eklendi.
- **Kişisel Listeler:** "İzlemeye Devam" ve "Planladıklarım" listeleri tek seferde yüklendiği için gereksiz yere tekrar çekilmez.

---
---

## 🇬🇧 English (v2.4.198)

### 🔞 1. +18 Blur & Adult Content Shield (Guaranteed Across All Devices)
- **Comprehensive Coverage Across 46 UI Surfaces:** Complete blur masking for adult posters and fanart across Home rows, Explore strips, Search results, Character/Staff details, image dialogs, and Android TV components.
- **Coil BlurTransformation Fallback (API < 31):** On Android versions below 12 where Compose's `Modifier.blur` is silently ignored by the OS, a robust Coil-based `BlurTransformation` pipeline (`KitsugiNsfwImage`) ensures that adult content is never exposed unblurred.
- **Provider-Verified Adult Flags:**
  - **Kitsu:** R18 ratings, `nsfw=true`, or hentai subgenres via centralized `KitsuAdultFlags`.
  - **Shikimori:** Restricted strictly to `rx`/`hentai` entries (preventing accidental masking of `r`/`r_plus`).
  - **Simkl:** Evaluated via `adult=true` flag and explicit maturity ratings (`NC-17`, `XXX`, `18+`...) (`SimklAdultFlags`).
  - **AniList & TMDB:** Full propagation of `isAdult` and `adult` fields to explore calendars and showcases.
  - **Manga Extensions:** Deep tag heuristics (`hentai`, `doujin`, `xxx`... excluding standard ecchi).
- **Import Preservation:** List imports from Kitsu, Shikimori, and Simkl retain `isAdult` flags from the moment of import.

---

### 📌 2. Persistent Source Selection & Kitsu Identity Isolation
- **Persistent Source Memory (Explore / Search / My List):**
  - **Explore:** Chosen `ExplorePlatform` (All / AniList / MAL / TMDB / Simkl / Kitsu / Shikimori) is stored in `SettingsDataStore` and restored on app launch. Automatic fallback drops are not saved as user choices.
  - **Search:** Search engine, scope, tab, platform, and media type are persisted in sync without triggering premature network queries on startup.
  - **My List:** Verified consistent behavior aligned with Explore and Search tabs.
  - **Startup Performance:** Bypasses 11 splash TMDB pre-warming network requests when the persisted platform is not TMDB or All.
- **Kitsu Mismatched Detail Screen Fix:**
  - Eliminated identity collisions where raw MAL IDs were incorrectly written to Kitsu entries, causing completely unrelated anime (e.g. Keep Your Hands Off Eizouken!) to load in detail views.
  - Introduced `KitsuIdNamespace` (`300M+1..399M+1`), multi-stage fallback resolution, and strict title + year reconciliation.
  - `KitsuIdentityMigration`: Silently repairs existing local entries using offline mapping caches.

---

### ♾️ 3. Simkl True Infinite Pagination & Streamlined Network Engine
- **No More 100-Item Bottleneck:** Simkl explore and search lists now support true infinite scrolling in 20-item increments, matching AniList, MAL, and TMDB.
- **Official Simkl API Architecture:**
  - **Genres Endpoints:** Uses official `genres/...` endpoints with `page` and `limit`, supporting up to 20 pages (~1,200 items) for popular series, top-rated, and popular anime/movies.
  - **Premieres:** Paged integration for `anime/premieres/soon` and `premieres/new`.
  - **Trending Charts:** Finite Top 500 CDN snapshots (`today_500.json`, `week_500.json`) are cached in memory for 1 hour and sliced locally in 20-item batches.
  - **Airing Schedules:** Full schedules fetched efficiently in a single payload.
- **Search Pagination:** Both basic and advanced Simkl search now receive `page` parameters with 20 items per page; fixed legacy query path segments to canonical `all` values.
- **Mandatory App Identification:** Appended mandatory `client_id`, `app-name=kitsugi`, `app-version`, and custom `KitsugiApp/... (Android)` User-Agent headers to all Simkl requests.
- **Personal Watchlists:** Preserved loaded items in "Continue Watching" and "Plan to Watch" without redundant network calls.
