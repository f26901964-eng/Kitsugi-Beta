# PLAN & TASK — Eklenti Sayfaları Tema Uyumu (Keşfet / Tümünü Gör / Arama ile Birebir)

**Tarih:** 2026-10-23  
**Branch:** `arena/3a2a1f6c-kitsugi-beta` (base: `76eff11d`)  
**İstek:** Eklentilerin sayfaları, arama sayfaları ve kutucukları dahil uygulamadaki Keşfet ve Tümünü Gör arama sayfalarına birebir benzemeli — tema, renk, stil, bar, kutucuk, seçim dahil her şey uyumlu olmalı.

---

## 1) Kök Neden
Eklenti yüzeyleri (AddonExplorePage / AddonExploreInline / AddonSearchDialog / PluginPicker / KitsugiAddonDetailDialog) kendi özel arama barları, hero bannerları, kategori satırları ve kart stillerini kullanıyordu:
- Siyah translucent floating bar (42dp, CircleShape) vs ana uygulamadaki `KitsugiCosmicSearchBar` (56dp, 16dp radius, `Surface` + accent focus)
- Özel 130dp/180dp `ResultItemCard` vs `KitsugiExploreMediaCard` / `SearchResultRow` / `KitsugiRankingMediaCard`
- Farklı chip stilleri (20dp, farklı alpha/border) vs `SearchTypeChip` / `GridGenreChipRow` (14dp, accent vs Surface)
- Farklı shimmer, empty, FAB ve pull-to-refresh sözleşmeleri
- Farklı typography (sp sabit) vs `MaterialTheme.typography` + `KitsugiColors` üzerinden tema

Sonuç: Kullanıcı eklenti sayfalarında farklı bir tasarım dili görüyordu.

## 2) Hedef
Tüm eklenti portalı, keşfet ve detay yüzeyleri tek bir tasarım sistemine kilitlenmeli:
- **Arama çubuğu:** `KitsugiCosmicSearchBar` + `CosmicCompanionButton` (56dp, 16dp radius, Surface, accent focus, aynı placeholder/silme/onay davranışı)
- **Vitrine:** `KitsugiHeroSection` (aynı gradient, aynı blur politikası, aynı info tıklaması)
- **Raflar:** `KitsugiHorizontalMediaSection` (aynı başlık `titleLarge Bold`, aynı “Tümünü Gör” TextButton, aynı LazyRow spacing/snap, aynı neon glow kartlar)
- **Kartlar:** `KitsugiExploreMediaCard` (grid), `SearchResultRow` (dikey liste), `KitsugiRankingMediaCard` (sıralı liste) — aynı `KitsugiColors`, aynı shape, aynı `tvClickable`
- **Çipler / seçim:** `SearchTypeChip` sözleşmesi (14dp, seçili=accent zemin + beyaz yazı, seçili değil=Surface + Border)
- **Yardımcılar:** `KitsugiShimmerProvider` / `KitsugiShimmerSearchResultList` / `KitsugiShimmerHeroSection`, `KitsugiEmptyState`, `KitsugiPullToRefreshIndicator`, `KitsugiScrollToTopFab`
- **Detay:** Aynı hero spacing (20dp horizontal), aynı poster card (16dp + neonGlow), aynı tag/episode/selectör stilleri
- **Arka plan & tipografi:** Herwhere `KitsugiColors.Background/Surface/Border/TextPrimary/Secondary/Muted` + `MaterialTheme.typography`, sp sabitleri kaldırıldı

## 3) Değişen Dosyalar (5)

### 3.1 `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonExploreDialog.kt`
**Önce:** Siyah `AddonFloatingSearchBar`, chunked 3'lü `KitsugiExploreMediaCard` grid (forceVertical), özel warning pill (10dp), custom hero/spacer logic.
**Sonra:**
- Floating bar kaldırıldı → LazyColumn içinde `Spacer(28dp)` + başlık (Geri + Extension + api.name `headlineSmall Black` + mainUrl) + `KitsugiCosmicSearchBar` (placeholder “{api.name} içinde ara…”) + `CosmicCompanionButton` (Refresh) — Ana Arama ile birebir.
- `PullToRefreshBox` (state `rememberPullToRefreshState`) + `KitsugiPullToRefreshIndicator` eklendi (Keşfet ile aynı).
- Arama sonuçları: `KitsugiShimmerSearchResultList` loading, `KitsugiEmptyState` (SearchOff) empty, sonuçlar `SearchResultRow` dikey liste (20dp padding, 5dp spacing) — Ana Arama tekli platform sonuçları ile aynı.
- Hero: `KitsugiHeroSection` (isVisible true) + 6dp spacer, shimmer `KitsugiShimmerHeroSection`.
- Raflar: `KitsugiHorizontalMediaSection` (26dp alt spacer) — Keşfet rafları ile aynı.
- Warning pill: 12dp radius, 0.14f alpha, `PlatformErrorCodeHint` ile aynı.
- FAB: `KitsugiScrollToTopFab` (derivedStateOf firstVisibleItemIndex >1, landscape-aware).
- In-memory cache, CF clearance, adult filter sözleşmesi korundu.

### 3.2 `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonExploreInline.kt`
**Önce:** `InlineHeroBanner` (320dp siyah gradient + özel play kutusu), `InlineCategoryRow` (custom title + ChevronRight + `ResultItemCard` LazyRow), custom warning pill (10dp), custom empty/loading.
**Sonra:**
- Hero → `KitsugiHeroSection` (ilk rafın ilk 8 öğesi, adult filtreli, onInfoClick -> stream veya onItemClick) + shimmer hero.
- Category rows → `KitsugiHorizontalMediaSection` (aynı neon glow kartlar, aynı “Tümünü Gör” callback, aynı spacing).
- Warning → 12dp radius, 0.14f alpha, `Text` = `labelMedium`.
- Loading → `KitsugiPlasmaLoader` + `labelMedium`, Empty → `KitsugiEmptyState` (SearchOff) + 20dp horizontal padding.
- `AddonExploreInlineState` load mantığı aynı (CF gate, BoundedCache, skip tracker).

### 3.3 `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonSearchDialog.kt`
**Önce:** Box (56dp, 22dp radius, Border) arama, `ResultItemCard` (130dp/180dp özel Surface kart, quality badge, AsyncImage + manuel Referer header), installed list Box (Surface, Extension, “Keşfet” metni), custom shimmer/empty, Dialog with 16dp padding.
**Sonra:**
- Header → `headlineLarge Bold` + Geri, 18dp spacer — Ana Arama ile aynı.
- Arama → `KitsugiCosmicSearchBar` (placeholder `inline_search_all_installed_extensions_590b4c9`) + `CosmicCompanionButton` (Search), weight 1f, 10dp spacing — Ana Arama ile aynı.
- Loading → `KitsugiPlasmaLoader` + `KitsugiShimmerSearchResultList` (wildcard shimmer).
- Grouped results → Her API için header (24dp icon kutusu 6dp radius accent 0.15f + Extension 14dp + title `titleLarge Bold` + count `labelMedium`) + `LazyRow` (4dp padding, 12dp spacing) + `KitsugiExploreMediaCard` (cardWidth 180dp/260dp landscape, forceVertical false) — `MultiSearchSection` ile aynı.
- Idle → header `bodyMedium`, boşta `KitsugiEmptyState` (Extension), installed list → `Card` (16dp + `kitsugiNeonGlow` + `tvClickable`, `Surface`, 14dp padding) — Ana Arama idle tarihçesi stiliyle aynı.
- LazyColumn horizontal 20dp, 90dp bottom, FAB `KitsugiScrollToTopFab`, `KitsugiShimmerProvider` (isLoading).
- CF cache clear ve adult filtre aynı.

### 3.4 `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt`
**Önce:** Box (16dp, Surface, Border) arama + `BasicTextField` + `TextFieldState`, `PluginTypeChip` (animateColorAsState, Background/Surface, 20dp), `PluginListItem` düz Row (14dp clip, 40dp flag kutusu accent 0.12f, name 15sp SemiBold), `AllPluginsListItem` düz Row, header 20sp Black, count 13sp, divider Border 0.5f, AlphabetRail custom.
**Sonra:**
- Header → `headlineLarge Bold` + Extension 24dp + sayaç `999dp Surface 10/4` pill `labelMedium Bold` — Ana Arama header ile aynı.
- Arama → `KitsugiCosmicSearchBar` (placeholder `eklentilerde_ara_x_eklenti`) + silme `CosmicCompanionButton` (56dp) — Ana Arama ile aynı. `TextFieldState` sync kaldırıldı, direkt `KitsugiCosmicSearchBar` state yönetimi.
- Chip → `PluginTypeChip` SearchTypeChip sözleşmesi ile birebir: 14dp radius, seçili=accent zemin + beyaz `labelLarge Bold`, seçili değil=Surface + Border + `TextPrimary Medium`, `tvClickable`.
- Liste kartları → `Card` (16dp + `kitsugiNeonGlow` + `tvClickable`, `Surface`) — `SearchResultRow` ailesiyle aynı. İçerik: 40dp/10dp flag kutusu accent 0.12f, `titleMedium Bold` + `bodySmall` url, tip rozeti `999dp` accent 0.15f `labelSmall Bold` (NSFW kırmızı).
- Empty → `KitsugiEmptyState` (Extension) + 20dp padding, title `sonuc_bulunamadi`.
- `KitsugiShimmerProvider` (isLoadingActiveApis) + `KitsugiScrollToTopFab` (firstVisible>3, landscape-aware) eklendi. LazyColumn padding start 20 end 34 top 8 bottom 90 spacing 5dp — Ana liste ile aynı.
- AlphabetRail korundu (aynı renkler: accent seçili, TextPrimary 0.9f var, TextMuted 0.25f yok), büyük harf popup aynı.

### 3.5 `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/KitsugiAddonDetailDialog.kt`
**Önce:** Hero Row 16dp padding, poster 12dp Card (elevation 8dp), Extension badge düz Row (14dp icon), tags 6dp Box Surface 8/3 Text 10sp, plot 16sp/14sp 6dp spacer, dub/season pills 20dp Surface/Accent 16/8, episodes header 16sp + start/end/bottom 12dp, episode Card 8dp `SurfaceStrong 0.5f` 16/6 padding, PlayArrow 24dp plain, plot 12sp 16sp lineHeight.
**Sonra:**
- Hero Row 20dp (Keşfet 20dp ile aynı), poster Card 16dp + `kitsugiNeonGlow` (8dp elevation korundu).
- Extension badge → `6dp Surface 0.15f` pill içinde Row + `labelSmall Bold` — platform rozeti ile aynı.
- Tags → `20dp Surface+Border` pill `labelSmall Medium` — stüdyo/detail çipleri ile aynı.
- Plot header `titleMedium Bold` + 8dp spacer + `bodyMedium` 20sp lineHeight — `ApiResultDetailPage` ile aynı.
- Dub/Season pills → 14dp `SearchTypeChip` (accent vs Surface + Border 1dp, 14/8 padding, `labelLarge` Bold/Medium).
- Direct play → 16dp radius `KitsugiButton` 50dp + `titleMedium Bold` — aynı CTA.
- Episodes header → `titleMedium Bold` 20dp/12dp.
- Episode card → `16dp Surface + kitsugiNeonGlow + tvClickable`, 20dp/6dp padding, 36dp accent 0.15f play kutusu 8dp + 18dp icon, `bodyMedium Bold` + `bodySmall`, plot `bodySmall` 16sp lineHeight — `EpisodesTabContent` / `SearchResultRow` kart ailesiyle aynı.
- Background gradient `safeBlur(16dp)` korundu (API31 guard), scrim `Background 0.5f→1f` aynı.
- `kitsugiNeonGlow` import eklendi.

---

## 4) Korunan Davranışlar
- CF clearance (`ensureCfClearance` + `searchWithCfRetry`), `CsPluginStatusTracker` skip/block/errorCode, BoundedCache (12), adult filter (`LocalShowAdultContent` + `isAddonAdultResponse` + `CsStreamRunner.ADULT_PLUGINS_SET`), `safeYear()` reflection, `toAddonSearchResult()` stableId.
- Plugin load: `CsPluginLoader.loadExtension` + `isApiEnabledFor` normalize eşleştirme (KN-D fix).
- Pagination: `AddonFullScreenGridPage` (`hasNext = response.hasNext`, distinctBy url) aynı bırakıldı — zaten birebir.
- Navigation: `DetailScreen.AddonExplore` / `PluginPicker` / `AddonFullScreenGridState` aynı, FAB/scroll retain aynı.

## 5) Tema Sözleşmesi (Birebir Eşleşen Tokenlar)
- Arka plan: `KitsugiColors.Background`
- Yüzey: `KitsugiColors.Surface` (kart), `SurfaceSoft` (shimmer), `SurfaceStrong` (detay kart eski 0.5f yerine Surface)
- Border: `KitsugiColors.Border` (1dp, focus 1.5dp accent)
- Metin: `TextPrimary` (başlık), `TextSecondary` (alt), `TextMuted` (placeholder, count, plot ikincil)
- Accent: `LocalKitsugiAccent.current` (vurgu, chip seçili, FAB, shimmer)
- Shape: arama 16dp, kart 16dp/20dp/24dp, chip 14dp/20dp, rozet 6dp/999dp
- Typography: `headlineLarge Bold`, `titleLarge/titleMedium Bold`, `bodyMedium/bodySmall`, `labelLarge/labelMedium/small Bold`

## 6) Test Planı
- [ ] Keşfet hero ↔ Eklenti hero aynı yükseklik/gradient/tap davranışı
- [ ] Keşfet raf kartı ↔ Eklenti raf kartı aynı neon glow + spacing + shimmer
- [ ] Arama barı focus border (accent 1.5dp) ↔ Eklenti arama barı aynı
- [ ] Arama sonuçları listesi (SearchResultRow) ↔ Eklenti arama sonuçları aynı satır yüksekliği/font/badge
- [ ] Tümünü Gör grid header (Geri + title + badge + FilterList + GridView toggle + chip row + count) ↔ Eklenti Tümünü Gör aynı
- [ ] PluginPortal arama+chip+liste kartı ↔ Ayarlar/listelerdeki aynı kart sözleşmesi
- [ ] Detay hero/poster/tag/plot/seçici/episode kartı ↔ ApiResultDetailPage ile görsel karşılaştırma (pixel diff)
- [ ] Koyu/açık tema, accent değişimi, adult gizle/blur, landscape/portrait, TV D-pad odak ölçekleri

## 7) Risk & Rollback
- Sadece UI katmanı değişti (ViewModel/cache/stream korunduğu için veri riski düşük).
- Rollback: `git revert 7eca56c` veya `git diff origin/main -- 5 dosya` patch'ini geri al.

## 8) Zip İçeriği
- `app/src/.../PluginPickerScreen.kt`
- `app/src/.../AddonExploreDialog.kt`
- `app/src/.../AddonExploreInline.kt`
- `app/src/.../AddonSearchDialog.kt`
- `app/src/.../KitsugiAddonDetailDialog.kt`
- Bu plan dosyası (`PLAN_TASK_EKLENTI_TEMA_UYUMU_KESFET_TUMUNU_GOR_2026-10-23.md`)
- Değişim özeti için `git diff origin/main --stat` ve commit log ektedir.

## 9) Sonraki Adımlar (Opsiyonel)
- `PluginPickerBottomSheet.kt` içindeki `PortalResultItemCard` ve `PluginTypeChip` için aynı birebir güncelleme (şu an full-screen portal zaten birebir; bottom sheet fallback sadece misafir).
- Eklenti detay hero'sunu `DetailPageScaffold` + `DetailSharedComponents` ile tam scaffold'a taşıma (şu an Dialog içinde scaffold benzeri header, bir sonraki fazda tam parite).
