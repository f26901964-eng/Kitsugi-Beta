# PLAN_TASK_SCROLL_JANK_PERFORMANS_FIX_2026-10-10

**Tarih:** 2026-10-10
**Öncelik:** Kritik (UI Donukluğu / Jank)
**Konu:** Tüm kaydırılabilir sayfalarda (Keşfet, Trend Animeler Tümünü Gör, Arama, Çoklu-Kaynak raflar, Tam Ekran Grid) belirgin scroll kasması.
**Kullanıcı Bildirimi:** "kaydırılınabilinir tüm sayfalar kasıyor"

## Hedef
Scroll performansını akıcı hale getirmek; janky frame oranını kabul edilebilir seviyeye düşürmek; LazyList'lerde yalnızca görünür item'ların compose olmasını sağlamak.

## Kök Neden Analizi
1. **Her kare tüm sayfa recompose oluyor**: `lazyListState.firstVisibleItemIndex` composable içinde doğrudan okunuyordu. Bu değer scroll sırasında her frame değiştiği için Hero dahil tüm alt ağaç her kare yeniden çiziliyordu.
2. **Key'ler index içeriyor**: `${source}_${id}_$index` formatı filtre/sıralama/sayfa ekleme durumlarında tüm item identity'lerini bozuyor; Compose bütün kartları yeniden oluşturuyor.
3. **Coil crossfade(150ms)**: Fling esnasında ekrana giren her kart crossfade başlatıyor, ana thread'de animation churn yaratıyor.
4. **FlowRow + verticalScroll (Tümünü Gör dialog)**: Tüm 40+ kart aynı anda compose ediliyor. LazyVerticalGrid kullanılması gerek.
5. **contentType eksikliği**: Lazy listelerde contentType bildirilmediği için Compose view-type pooling yapamıyor.
6. **Kart başı gereksiz allocation**: RoundedCornerShape, getDisplayTitle(), Gradient/Brush, onError lambda her recomp'da yeniden üretiliyor; shadow 6dp fazla.

## Yapılanlar (Değişiklik Listesi)
| Dosya | Değişiklik |
|---|---|
| `KitsugiApplication.kt` | Coil `.crossfade(150)` → `.crossfade(false)` (posterler anında görünür, animasyon yok) |
| `KitsugiNsfwImage.kt` | `onError` lambdasını `remember` ile stabilize et; kullanılmayan `background` import'unu temizle |
| `KitsugiExploreMediaCard.kt` | `displayTitle`, `cardShape` remember ile önbelleğe al |
| `KitsugiRankingMediaCard.kt` | `displayTitle`, `shape` remember ile önbelleğe al |
| `DetailedSeasonalMediaCard.kt` | `displayTitle` remember ile önbelleğe al |
| `SearchResultRow.kt` | `displayTitle`, `rowShape` remember ile önbelleğe al |
| `KitsugiNeonGlowCard.kt` | `gradientColors`, `gradientBrush`, `spotColor` remember; shadow 6dp→4dp; ambient alpha azaltıldı |
| `KitsugiHorizontalMediaSection.kt` | TV + mobil LazyRow key'leri index'siz sabit identity; contentType `"h_media_card"` |
| `KitsugiMediaGridDialog.kt` | Mobil tarafta `verticalScroll + FlowRow` → `LazyVerticalGrid(2 kolon)`; TV key/cType düzelt; kullanılmayan import'lar temizle |
| `FullScreenMediaGridPage.kt` | `showFloatingHeader`, `showScrollToTop` `derivedStateOf` içine al; grid ve list key'leri index'siz sabit identity; contentType eklendi |
| `ExploreScreen.kt` | Hero `isVisible` değeri `derivedStateOf`; hero item'ına sabit key `"explore_hero"` |
| `SearchScreen.kt` | Arama sonuçları + çoklu-platform LazyRow key/cType düzelt |

## Doğrulama
- [ ] Studio Layout Inspector ile recompose sayılarını karşılaştır
- [ ] Düşük uç cihazda Keşfet + Trend Animeler + Arama sayfalarını hızlıca kaydır
- [ ] Filtre/sıralama değişince kartların anında (placeholder olmadan) görünmesi
- [ ] Tümünü Gör dialog'u açılışının daha hızlı olması

## Gelecekte İncelenecekler (bu turda dokunulmadı)
- `KitsugiHeroSection` parallax/backdrop blur katmanı GPU ağır olabilir
- Eklenti (Cloudstream) kaynaklı kartlarda coil çözümleme performansı
- Recomposer count'u kötüyse `Modifier.graphicsLayer { }` ile daha fazla invalidation izolasyonu

## İlgili Rapor
- `SCROLL_JANK_FIX_RAPORU_2026-10-10.md`
