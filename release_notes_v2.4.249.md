# Kitsugi-Beta v2.4.249 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🖼️ 1. Simkl Görsel Logo Mantığı (Vitrin / Hero & Detay Ekranları)

- **Eksik Çapraz Kimlik (Cross-ID) Çözümlemesi:** `SimklApiClient` keşfet şeritlerindeki Simkl öğelerini `malId = simklId, tmdbId = null, realMalId = null` olarak ürettiğinde vitrin ve detay sayfalarında logoların bulunamaması sorunu giderildi. `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo` ve `MediaEntryDetailViewModel.fetchLogo` içine Simkl kimliğinden TMDB / MAL / AniList / Kitsu kimliklerini çözen `KitsugiSimklDetailClient.resolveSimklCrossIds` çağrısı entegre edildi (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` zinciri).
- **Hero Artwork Kimliklerinin Vitrine Aktarımı:** `ExploreScreen.kt` (`displayHeroItems`), `HeroArtwork` üzerinden çözülen 16:9 `backdropUrl` ve `posterUrl` görsellerini öğeye kopyalarken `artwork.resolvedTmdbId` ve `artwork.resolvedMalId` alanlarını da vitrin öğesine aktarır. Ayrıca `KitsugiHeroSection` içindeki `logos` durumu `pagerKey` ile bağlandı ve boş logoların `enrichHeroBackdrops` tamamlandıktan sonra otomatik olarak yeniden denenmesi sağlandı.
- **Romaji Başlık & Çok Sezonlu Yıl Toleransı:** Simkl yalnızca Romaji başlık döndürdüğünde (ör. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026) TMDB kaydı İngilizce `name` + Japonca (CJK) `original_name` ve ilk sezon yılı (2016) taşıdığı için eşleşmenin reddedilmesi sorunu çözüldü. CJK orijinal başlıklı animasyon kayıtları için Romaji başlık uyumluluğu eklendi ve çok sezonlu TV animelerinde ilk sezon yılı ile devam sezonu yılı arasındaki fark için tolerans tanımlandı.
- **Detay Ekranları Çözümleme Zinciri:** Detay sayfası `fetchLogo` akışında Simkl çapraz kimlik çözümlemesi ve `explicitTmdb` eşleşmediğinde kaynak bazlı yedek logo arama zinciri (`TMDB` → `MAL` → `AniList` → `Kitsu`) aktif edildi.

### 📜 2. Kitsu Keşfet ("Tümünü Gör") Sayfalama ve Sonsuz Kaydırma Sorunu

- **Typesense 1-Tabanlı Sayfa Kayması:** Kitsu'nun sunucu arka ucu (`hummingbird-me/kitsu-server`), `filter[...]` parametresi içeren tüm `/anime` ve `/manga` sorgularını (`filter[status]=current`, `filter[status]=upcoming`, `filter[subtype]=movie`, `filter[season]=...`) `TypesenseSearchService` üzerinden çalıştırır. Sunucudaki `_page = (@_offset / @_limit).floor` hesabı `+ 1` eklemediği için 1-tabanlı Typesense'de `page[offset]=0` ve `page[offset]=20` aynı 1. sayfayı döndürüyordu ve liste 20. öğede kilitleniyordu. `KitsuExploreClient.kt` içine `offset > 0` için Typesense 1-tabanlı sayfa kaymasını telafi eden `adjustKitsuTypesenseQuery` (`offset + limit`) eklendi; artık 20. öğe sonrasında yeni sayfalar kesintisiz yükleniyor.
- **Trend Animeler Ucu:** Kitsu'nun `/trending/anime` ucunun sayfalama parametrelerini yok sayıp sabit 10 kayıt döndürmesi problemi çözüldü: ilk sayfada `/trending/anime` sonuçları popüler yayındaki animelerle (`airingAnime` / `topAnime`) 20 öğeye tamamlanır ve sonraki sayfalarda (`offset > 0`) kesintisiz devam eder.
- **KitsuApiHost ve Sonsuz Kaydırma Bekçisi:** Kanonik `https://kitsu.app/api/edge` adresi kalıcı olarak birincil tutuldu; `kitsu.io` yalnızca istek bazında yedek olarak bırakıldı. `FullScreenMediaGridPage.kt` ve `SearchScreen.kt` içinde filtre/sıralama değişimlerinde `autoLoadBudget` ile `lastAutoLoadVisibleCount` sıfırlaması ve `LaunchedEffect` tetikleyici anahtarları düzeltildi.

### 🧪 3. Birim Testleri

- `KitsuPaginationAndSimklLogoTest` ile Kitsu Typesense offset telafisi, kanonik sayfa anahtarları, `KitsuApiHost` önceliği ve Simkl vitrin logo politikası test edilip doğrulandı.

---

## 🇬🇧 English

### 🖼️ 1. Simkl Hero & Detail Visual Logos

- **Cross-ID Resolution:** When `SimklApiClient` outputs shelf items with `malId = simklId, tmdbId = null, realMalId = null`, logos were previously failing to resolve. Integrated `KitsugiSimklDetailClient.resolveSimklCrossIds` across `KitsugiHeroSection.fetchHeroLogo`, `KitsugiHeroArtworkResolver.computeArtworkInternal`, `ApiResultDetailViewModel.fetchLogo`, and `MediaEntryDetailViewModel.fetchLogo` (`tmdbId` → `realMalId` → `aniListId` → `kitsuId` chain).
- **Passing Artwork IDs to Hero:** `ExploreScreen.kt` (`displayHeroItems`) now copies `artwork.resolvedTmdbId` and `artwork.resolvedMalId` from `HeroArtwork` into hero items. Furthermore, `logos` state in `KitsugiHeroSection` is keyed to `pagerKey` and automatically retries blank logos once `enrichHeroBackdrops` completes.
- **Romaji Titles & Multi-Season TV Year Discrepancy:** When Simkl returns Romaji-only titles (e.g. *Re:Zero kara Hajimeru Isekai Seikatsu*, 2026), TMDB records carry English `name` + Japanese CJK `original_name` and the initial season release year (2016). Added CJK animation fallback in `TmdbArtworkIdentity.matches` and multi-season year tolerance in `KitsugiHeroArtworkResolver.kt` so sequels match TMDB entries reliably.
- **Detail Screen Logo Fallbacks:** Full fallback chain (`TMDB` → `MAL` → `AniList` → `Kitsu`) activated in `ApiResultDetailViewModel` and `MediaEntryDetailViewModel` when explicit TMDB entries are absent.

### 📜 2. Kitsu Explore "View All" Pagination & Infinite Scroll

- **Typesense 1-Based Page Offset Shift:** Kitsu's backend (`hummingbird-me/kitsu-server`) routes all `/anime` and `/manga` queries containing `filter[...]` to `TypesenseSearchService`. Due to missing `+ 1` in its offset-to-page calculation, `page[offset]=0` and `page[offset]=20` returned the exact same page 1, causing infinite scroll to stop at item 20. Added `adjustKitsuTypesenseQuery` (`offset + limit`) for `offset > 0` on filtered endpoints in `KitsuExploreClient.kt`, enabling seamless infinite scrolling past item 20.
- **Trending Anime Pagination:** Addressed Kitsu's `/trending/anime` endpoint ignoring pagination by backfilling page 1 with currently airing/popular anime to 20 items and cleanly paging subsequent offsets without interruption.
- **Canonical Host Priority & Scroll Budget Reset:** Preserved `https://kitsu.app/api/edge` as the permanent primary host. Fixed `autoLoadBudget` and `lastAutoLoadVisibleCount` resetting upon filter/sort modifications in `FullScreenMediaGridPage.kt` and `SearchScreen.kt`.

### 🧪 3. Unit Tests

- Added `KitsuPaginationAndSimklLogoTest` validating Typesense query adjustment, canonical query anchors, `KitsuApiHost` priority, and hero logo display policies.
