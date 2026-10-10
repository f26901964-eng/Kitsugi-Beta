# Keşfet / Arama / Tümünü Gör — Kaydırma Kasması ve Sallantı: Kök Neden Analizi

**Tarih:** 2026-10-10 · **Dal:** `arena/37af8d7b-kitsugi-beta`

## 1. Şikâyet

1. **Keşfet ve Arama:** veriler geldikten sonra, her yöne kaydırmada kasma/donma.
2. **Tümünü Gör** sayfaları: aşağı kaydırmada sallantı, takılma, sayfanın "bugalanması".

Önceki turlarda (`KESFET_PERFORMANS_FIX_RAPORU`, `KESFET_KAYNAK_YUKLEME_PERFORMANS_RAPORU`) bazı
düzeltmeler yapılmış, ancak aşağıdaki kök nedenler bu dalda hâlâ duruyordu.

## 2. Kök nedenler

### A) Kaydırma konumu Compose state'ine yazılıyordu → her karede tam ekran yeniden kompozisyon (ANA NEDEN)

`ui/utils/RetainedLazyListState.kt` — `rememberRetainedLazyListState` (Keşfet **ve** Arama kullanıyor):

```kotlin
var savedIndex by rememberSaveable { mutableIntStateOf(initialIndex) }
var savedOffset by rememberSaveable { mutableIntStateOf(initialOffset) }
val state = rememberLazyListState(savedIndex, savedOffset)   // ← composition'da OKUNUYOR
...
snapshotFlow { ... }.collect { savedIndex = index; savedOffset = offset }  // ← her kaydırma karesinde YAZILIYOR
```

Okuma ve yazma aynı state üzerinde olduğu için: **her piksel kaydırmada çağıran composable
(`ExploreScreen` / `SearchScreen`) geçersiz kılınıp yeniden kompoze ediliyordu.** `ExploreScreen`
gövdesi çok büyük (Tümü modunda 7 kaynak bölümü, `LazyListScope` lambda'sı, filtre
hesapları, `toSet()` dönüşümleri); bu, kaydırmanın her karesinde tekrar çalışıyordu. Bu,
önceki turda `AppViewModel` için düzeltilen döngünün **aynısıydı ama bu yardımcı fonksiyonda kalmıştı**.

**Düzeltme:** Konum tutucusu düz bir nesneye (`RetainedScrollPosition`) taşındı. Değer
yalnızca kaydetme (save-instance-state) anında `listSaver` ile okunuyor; kaydırma sırasında
hiçbir kompozisyon geçersiz kılınmıyor. Geri yükleme davranışı birebir aynı.

### B) Tümünü Gör: `showFloatingHeader` sayfa gövdesinde doğrudan okunuyordu

`ui/screens/fullscreen/FullScreenMediaGridPage.kt`:

```kotlin
val showFloatingHeader = if (isGridView) gridState.firstVisibleItemIndex >= 1 else listState...
```

Her satır/öğe geçişinde tüm sayfa (grid lambda'sı dahil) yeniden kompoze oluyordu. Ayrıca
eşik çevresinde üst overlay (`expandVertically` + `fadeIn`) sürekli açılıp kapanıyordu → **sallantı**.

**Düzeltme:** `derivedStateOf` ile sarıldı; sayfa yalnızca eşik boolean'ı değiştiğinde yeniden kompoze olur.

### C) Yükleme durumu değişince içerik ağacı baştan kuruluyordu (`KitsugiShimmerProvider`)

`ui/components/KitsugiShimmer.kt`:

```kotlin
if (active) { CompositionLocalProvider(LocalShimmerBrush provides sharedBrush, content = content) }
else        { CompositionLocalProvider(LocalShimmerBrush provides staticBrush, content = content) }
```

`active` (`viewModel.isLoading`, arama `isLoading`) her değiştiğinde iki `if` dalı farklı
composition grupları üretiyordu → **`content` (LazyColumn + hero pager + tüm raflar) söküp
yeniden kuruluyordu.** Hero pager'ın durumu sıfırlanıyor, arama sonuçları baştan kompoze oluyordu.
Pull-to-refresh ve arama sırasında yaşanan "bir anda sıçrama" bunun sonucu.

**Düzeltme:** `if` yalnızca fırçayı seçiyor; `CompositionLocalProvider(content)` tek ve sabit
yerde. Statik fırça ayrı `remember` fonksiyonuna taşındı (yükleme yokken `InfiniteTransition` yok).

### D) Hero backdrop çözümleri her biri için ayrı state yazıyordu

`ui/screens/explore/ExploreViewModel.kt` — `enrichHeroBackdrops`: her backdrop sonucunda
`heroBackdropOverrides = heroBackdropCache.toMap()` (tüm harita kopyası + state yazımı).
Yükleme sırasında ~10+ kez Keşfet'i yeniden kompoze ediyor ve hero logo aramasını her seferinde
yeniden başlatıyordu (`LaunchedEffect(items)`).

**Düzeltme:** 250 ms'lik pencerede tek yayın (`scheduleHeroOverridesFlush`).

### E) Hero `isVisible` ham indeksi item içinde okuyordu

`ExploreScreen.kt`: `isVisible = lazyListState.firstVisibleItemIndex == 0` — her satır geçişinde
hero yeniden kompoze oluyordu. **Düzeltme:** `derivedStateOf` ile yalnızca bool değişince tetiklenir.

## 3. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/utils/RetainedLazyListState.kt` | Konum Compose state'inden düz nesneye; `listSaver` ile kaydetme (A) |
| `ui/screens/fullscreen/FullScreenMediaGridPage.kt` | `showFloatingHeader` → `derivedStateOf` (B) |
| `ui/components/KitsugiShimmer.kt` | `KitsugiShimmerProvider` tek sabit ağaç; statik fırça ayrı fonksiyon (C) |
| `ui/screens/explore/ExploreViewModel.kt` | Hero backdrop yayınları toplu (`scheduleHeroOverridesFlush`) (D) |
| `ui/screens/explore/ExploreScreen.kt` | `isHeroVisible` → `derivedStateOf` (E) |

## 4. Bilinen, bilinçli olarak değiştirilmeyen maliyetler

- `KitsugiExploreMediaCard` → `kitsugiNeonGlow` her karta `Modifier.shadow(elevation = 6.dp)`
  uyguluyor. Yüzlerce kartta düşük/orta seviye cihazlarda kaydırma maliyeti yaratabilir. Görsel
  bir tasarım kararı olduğundan bu turda kaldırılmadı; gerekirse "gölgesiz kart" seçeneği
  eklenebilir.
- Tümünü Gör grid anahtarları `"${source}_${malId}_g$idx"` — indeks içeriyor. Sıralama
  değişince anahtarlar değişir; bu kullanıcı tetikli bir durum, kaydırmada sorun yaratmaz.

## 5. Doğrulama notu

Bu sandbox'ta JDK / Android SDK yok, derleme çalıştırılamadı. Tüm değişiklikler şu şekilde
statik olarak doğrulandı: import kontrolleri, `listSaver`/`rememberSaveable` imzaları,
kapsam (değişkenlerin kullanıldığı composable içinde tanımlı olması), `derivedStateOf`
kullanımı. **CI derlemesi ve cihazda Keşfet (Tümü + tek kaynak), Arama ve bir Tümünü Gör
sayfasında kaydırma testi önerilir.**
