# Keşfet Sayfası Performans Düzeltmesi — Teşhis & Çözüm

**Tarih:** 2026-10-10 · **Dal:** `arena/784c68d6-kitsugi-beta` · **Commit:** `4247edc`

## 1. Şikâyet
Keşfet sayfası hem **Tümü** modunda hem de **tek kaynak** keşfet sayfalarında
(AniList, MAL, TMDB, Kitsu, Shikimori, Simkl, Bangumi) "feci kasıyor".

## 2. Kök Nedenler

### A) Scroll konumu geri besleme döngüsü (ana neden)
Akış şu şekildeydi:

1. `ExploreScreen`, kaydırmanın **her karesinde** `snapshotFlow` ile
   `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` değerlerini
   `onScrollPositionChange` üzerinden `AppViewModel.updateExploreScrollPosition`a yazar.
2. `exploreScrollIndex` / `exploreScrollOffset` alanları **`mutableIntStateOf`**
   (gözlemlenebilir Compose state) idi.
3. `ExploreTabPage` (AppRootTabPages.kt) bu state'leri OKUYUP
   `initialScrollIndex` / `initialScrollOffset` olarak `ExploreScreen`e geri verir.

Sonuç: kaydırma sırasında **her pikselde tüm Keşfet sekmesi geçersiz kılınıp
yeniden kompoze ediliyordu** — Tümü modunda 7 kaynak × ~15 raf'lık LazyList
DSL'inin (eager `allSourceSections` + `groupBy` + `allSourceHeaderIndices`
hesapları dahil) her karede yeniden inşası + görünür tüm kartların yeniden
kurulması. Aynı döngü Listem sayfası için de geçerliydi
(`myListScrollIndex/Offset`).

### B) Kalıcı sonsuz shimmer animasyonu
`KitsugiShimmerProvider`, Keşfet `LazyColumn`unun tamamını sarıyordu ve
**içerik yüklendikten sonra bile** 1200ms'lik sonsuz shimmer transition'ını
çalıştırmaya devam ediyordu — sayfa boşta dururken bile kare saati sürekli
ayakta kalıyordu (gereksiz CPU/pil + recomposition churn).

## 3. Çözüm

| Dosya | Değişiklik |
|---|---|
| `ui/app/AppViewModel.kt` | `exploreScrollIndex/Offset` ve `myListScrollIndex/Offset` artık **düz değişken** (Compose state değil). Yazma anında hiçbir kompozisyon geçersiz kılınmıyor; değerler yalnızca ekran yeniden kurulurken "ilk değer" olarak okunuyor — davranış birebir aynı. |
| `ui/components/KitsugiShimmer.kt` | `KitsugiShimmerProvider(active: Boolean = true)` eklendi. `active=false` iken sonsuz transition **hiç oluşturulmaz**; tüketiciler için statik fırça sağlanır. |
| `ui/screens/explore/ExploreScreen.kt` | Provider'a `active = viewModel.isLoading` geçiriliyor — animasyon yalnızca gerçekten yükleme varken çalışır. |
| `ui/tv/home/TvAllSourcesHomeContent.kt` | Aynı `active` bağlantısı. |

## 4. Neden güvenli?
- Scroll alanları yalnızca `AppRootTabPages.kt`de **ilk değer** olarak okunuyor
  (Explore: `rememberRetainedLazyListState` başlangıcı; Listem:
  `rememberSaveable` başlangıcı). Gözlemlenebilirlik hiçbir yerde kullanılmıyordu.
- `resetMyListScroll()` çağıran her yol (filtre/sıralama/sekme değişimi) zaten
  başka bir observable state değiştirdiği için ekran o anda yeniden kurulur ve
  güncel 0/0 değerini okur.
- Shimmer tüketicilerinin tamamı (`KitsugiShimmerHeroSection`,
  `KitsugiShimmerMediaRow`, airing placeholder'ları) zaten `isLoading` ile
  kapılıdır; yükleme yokken animasyonlu fırçaya ihtiyaç yoktur.

## 5. Doğrulama notu
Sandbox'ta JDK/Android SDK bulunmadığından derleme yapılamadı; değiştirilen
dosyalar sözdizimi (dengeli parantez/kaşeli) ve tüm kullanım yerleri
(`grep`) düzeyinde doğrulandı. Cihazda derleyip Keşfet'i Tümü + tek kaynak
modlarında kaydırarak test etmeniz yeterli.
