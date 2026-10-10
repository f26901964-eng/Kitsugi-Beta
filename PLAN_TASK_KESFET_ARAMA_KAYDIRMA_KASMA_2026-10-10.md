# PLAN_TASK — Keşfet / Arama / Tümünü Gör Kaydırma Kasması ve Sallantı

**Tarih:** 2026-10-10 · **Dal:** `arena/37af8d7b-kitsugi-beta`
**İlgili rapor:** `KESFET_ARAMA_KAYDIRMA_KASMA_KOK_NEDEN_2026-10-10.md`

## Kapsam

- Keşfet (Tümü + tek kaynak): veriler gelirken ve sonrasında kaydırma kasması
- Arama: sonuçlar gelirken / kaydırırken kasma
- Tümünü Gör (grid + liste): aşağı kaydırmada sallantı, takılma, sıçrama

## Plan (kök neden → çözüm)

| # | Kök neden | Dosya | Çözüm |
|---|---|---|---|
| P1 | Kaydırma konumu Compose state'ine yazılıyor ve composable'da okunuyor → her karede Keşfet/Arama ekranı yeniden kompoze | `ui/utils/RetainedLazyListState.kt` | Konum düz nesne (`RetainedScrollPosition`); `listSaver` ile kaydetme |
| P2 | `KitsugiShimmerProvider` `if/else` içinde iki ayrı provider → `isLoading` değişince içerik ağacı söküp yeniden kuruluyor | `ui/components/KitsugiShimmer.kt` | `if` yalnızca fırçayı seçer; içerik sabit; statik fırça ayrı fonksiyon |
| P3 | Tümünü Gör `showFloatingHeader` gövdede doğrudan okunuyor → her sıra geçişinde tüm sayfa yeniden kompoze, overlay eşikte titriyor | `ui/screens/fullscreen/FullScreenMediaGridPage.kt` | `derivedStateOf` |
| P4 | Hero backdrop her çözümde ayrı state yazıyor + tüm harita kopyalanıyor | `ui/screens/explore/ExploreViewModel.kt` | 250 ms pencerede toplu yayın (`scheduleHeroOverridesFlush`) |
| P5 | Hero `isVisible` ham indeksi item içinde okuyor | `ui/screens/explore/ExploreScreen.kt` | `isHeroVisible` `derivedStateOf` |

## Task'lar

- [x] T1 — `RetainedLazyListState`: konum state'ten düz nesneye, `listSaver` ile kaydetme (P1)
- [x] T2 — `KitsugiShimmerProvider`: tek sabit içerik ağacı, statik fırça ayrıldı (P2)
- [x] T3 — `FullScreenMediaGridPage`: `showFloatingHeader` → `derivedStateOf` (P3)
- [x] T4 — `ExploreViewModel`: hero backdrop yayınları toplu (P4)
- [x] T5 — `ExploreScreen`: hero `isVisible` → `derivedStateOf` (P5)
- [x] T6 — Statik doğrulama (import, imza, kapsam, `derivedStateOf`/`listSaver` kullanımı)
- [x] T7 — Rapor + bu plan dosyası
- [ ] T8 — CI derlemesi (`./gradlew assembleDebug`)
- [ ] T9 — Cihazda Keşfet (Tümü + tek kaynak) kaydırma: yükleme sırasında ve sonrasında
- [ ] T10 — Cihazda Arama: sonuç gelirken kaydırma; geri dönünce konum korunuyor mu
- [ ] T11 — Cihazda Tümünü Gör (grid + liste): aşağı kaydırma, üst overlay titremesi yok mu
- [ ] T12 — Keşfet'ten detaya gidip geri dönünce kaydırma konumu geri yükleniyor mu

## Bilinen, bu turda değiştirilmeyenler

- `KitsugiExploreMediaCard` → `kitsugiNeonGlow` her karta `shadow(6dp)`: düşük/orta cihazlarda kaydırma maliyeti olabilir. Tasarım kararı; gerekirse gölgesiz kart seçeneği.

## Değişen dosyalar

```
KESFET_ARAMA_KAYDIRMA_KASMA_KOK_NEDEN_2026-10-10.md
PLAN_TASK_KESFET_ARAMA_KAYDIRMA_KASMA_2026-10-10.md
app/src/main/java/com/kitsugi/animelist/ui/utils/RetainedLazyListState.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiShimmer.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt
```

## Not

Bu sandbox'ta JDK / Android SDK yok; derleme çalıştırılamadı. Tüm değişiklikler statik doğrulandı. T8–T12 cihaz/CI tarafında yapılmalı.
