# Scroll Jank / Kasma Düzeltme Raporu
**Tarih:** 2026-10-10
**Konu:** Tüm kaydırılabilir sayfalarda (Keşfet, Trend Animeler, Arama, Tümünü Gör rafları) belirgin scroll jank / kasma.

## Tespit Edilen Ana Sebepler

1. **Her scroll karesinde tam recomposition**  
   `lazyListState.firstVisibleItemIndex` değeri doğrudan composition içinde okunuyordu.
   Compose bu değeri her frame'de değiştirdiği için tüm sayfa (Hero, sticky header, FAB, kartlar) her kare yeniden compose ediliyordu.

2. **Lazy item key'lerinde index kullanımı**  
   `${source}_${id}_$index` formatındaki key'ler sayesinde liste her filtreleme/sıralama/ekleme
   sonrası tüm item'ların kimliğini kaybediyor, Compose tüm kartları baştan oluşturuyordu.
   Index içermeyen stabil identity key'lere geçildi.

3. **Coil crossfade (150ms) animasyonu**  
   Flung esnasında ekrana giren her yeni kart crossfade animasyonu başlatıyor, bu da
   ana thread üzerinde overdraw + animation churn yaratıyordu. Crossfade kapatıldı.

4. **Mobil "Tümünü Gör" dialog'unda Lazy yerine FlowRow + verticalScroll**  
   Tüm medya kartları (40+) aynı anda compose ediliyordu. LazyVerticalGrid ile değiştirildi.

5. **Kart bileşenlerinde gereksiz nesne üretimi**  
   - `result.getDisplayTitle(...)` her recomp'da tekrar çalışıyor → `remember` ile önbelleklendi.
   - `RoundedCornerShape(...)` her recomp'da yeni nesne → `remember` ile önbelleklendi.
   - `kitsugiNeonGlow` modifier'ı her recomp'da yeni `Gradient`, `Color`, `Brush` üretiyor → remember ile sabitlendi.
   - Shadow elevation 6dp → 4dp (overdraw azaltıldı).
   - KitsugiNsfwImage içinde `onError` lambdası her recomp'da yeni instance üretiyordu, bu Coil'un isteği yeniden enqueue etmesine yol açabiliyordu → remember ile stabilize edildi.

6. **`contentType` eksikliği**  
   LazyList/LazyGrid/LazyRow içinde `contentType` bildirilmediği için Compose görünürlük dışı
   item'lar için composable pooling yapamıyordu. Tüm listelere contentType eklendi.

## Değiştirilen Dosyalar

| Dosya | Yapılanlar |
|---|---|
| `KitsugiApplication.kt` | Coil crossfade kaldırıldı. |
| `ui/components/KitsugiNsfwImage.kt` | OnError lambda stabilize edildi, gereksiz import temizlendi. |
| `ui/components/KitsugiExploreMediaCard.kt` | displayTitle/cardShape remember ile önbelleğe alındı. |
| `ui/components/KitsugiRankingMediaCard.kt` | displayTitle/shape remember ile önbelleğe alındı. |
| `ui/components/DetailedSeasonalMediaCard.kt` | displayTitle remember ile önbelleğe alındı. |
| `ui/components/KitsugiNeonGlowCard.kt` | Gradient/brush/spotColor remember, shadow 6dp→4dp. |
| `ui/components/KitsugiHorizontalMediaSection.kt` | TV + mobil LazyRow key'leri index'ten arındırıldı, contentType eklendi. |
| `ui/components/KitsugiMediaGridDialog.kt` | Mobil versiyonda verticalScroll+FlowRow → LazyVerticalGrid; TV ve mobil key/cType düzeltildi; kullanılmayan import temizlendi. |
| `ui/screens/fullscreen/FullScreenMediaGridPage.kt` | `showFloatingHeader` ve `showScrollToTop` derivedStateOf içine alındı; grid ve list key'leri index-siz sabit identity'ye döndürüldü; contentType eklendi. |
| `ui/screens/explore/ExploreScreen.kt` | Hero `isVisible` değeri `derivedStateOf` içine alındı; hero item'ına sabit key verildi. |
| `ui/screens/search/SearchScreen.kt` | Arama sonuçları ve çoklu platform raf LazyRow'larında key/cType düzeltildi. |
| `ui/screens/search/SearchResultRow.kt` | displayTitle/rowShape remember ile önbelleğe alındı. |

## Beklenen Sonuç

- Scroll sırasında her karede yalnızca görünen item'lar recompose olur.
- Yeni kartlar ekrana girerken crossfade animasyonu çalışmayacağı için fling daha akıcı.
- Tümünü Gör (Trend Animeler vb.) sayfası açılışta ilk katman kadar kartla yetinir, kalanlar kaydırdıkça yüklenir.
- Filtre/sayfalama/sıralama değişimlerinde mevcut kartlar yeniden kullanılır (key'ler stabil).
- Kart başına allocation/graphic işi azaldığı için GPU/CPU yükü düşer.

## Kurulum Sonrası İzlenecekler

Eğer kasma devam ederse:
- Layout Inspector veya `adb shell dumpsys gfxinfo com.kitsugi.animelist` ile janky frame sayısı doğrulanabilir.
- Geliştirici seçeneklerinden "GPU overdraw" ve "Profile GPU rendering" açılarak overdraw yoğun bölgeler tespit edilebilir.
- Özellikle KitsugiHeroSection'da parallax + blur/backdrop katmanı varsa bunlar da düşük uç cihazlarda jank sebebidir; gerekirse blur/backdrop sadece ilk item için aktive edilebilir.
