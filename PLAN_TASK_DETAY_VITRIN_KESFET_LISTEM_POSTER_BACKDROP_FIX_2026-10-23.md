# Detay Vitrin: Keşfet / Listem Poster-Backdrop Tutarsızlığı Fix

**Tarih:** 2026-10-23
**Dal:** `arena/a7f97a3d-kitsugi-beta`
**Commit:** `3aeb092` — fix(detail): Keşfet detayında dikey modda poster yerine arka plan göster
**Hedef:** Aynı yapımın detay sayfasında Keşfet'ten girince de Listem'den girince de aynı (doğru) vitrin görselinin gösterilmesi.

## Sorun (Kullanıcı Raporu)
- Keşfet'ten girilen detay sayfası: dikey modda **tam ekran dikey poster** gösteriliyor.
- Listem'den girilen detay sayfası: aynı yapımda **doğru yatay arka plan (backdrop)** gösteriliyor.
- Beklenti: Bu ekran boyutunda/yönünde yatay arka plan gösterilmeli.

Ekran görüntüleri: 1. görsel Keşfet → Kitsu rozeti, tam ekran poster; 2. görsel Listem → AniList rozeti, üstte 16:9 yatay bant.

## Kök Neden
İki detay sayfası iki farklı vitrin bileşeni ve iki farklı seçim kuralı kullanıyordu:

| Sayfa | Bileşen | Eski kural |
|---|---|---|
| Keşfet (`ApiResultDetailPage`) | `KitsugiDetailHero` | `resolveHeroArtworkPreference()`: dikeyde oran (w/h) ≤ 0.75 → **POSTER** |
| Listem (`MediaEntryDetailPage`) | `DetailHero` (`KitsugiDetailHeroSection.kt`) | Sadece yön: dikey → **BACKDROP**, yatay → POSTER |

- `DetailHeroGallerySelection.kt` içindeki `PORTRAIT_TALL_RATIO = 0.75f` eşiği güncel tüm telefonlarda (~0.43–0.50) her zaman POSTER tarafına düşüyordu.
- Keşfet vitrininde yatay adaylar (TMDB/Fanart backdrop + kaynağın kendi backdrop'u, örn. Kitsu `coverImage`) poster adaylarının arkasına sıralandığı için hiç gösterilmiyordu.
- Kutu oranı da poster tercihine kilitliydi: 2:3 kutu + ekran yüksekliği sınırı → poster tam ekran görünüyordu.
- Veri sorunu DEĞİL: Kitsu yatay `coverImage` zaten düzgün geliyordu (`KitsuExploreClient`), seçim kuralı yanlıştı.
- Not: Bu eşik `PLAN_TASK_DETAY_VITRIN_YON_BOYUT_POSTER_BACKDROP_2026-10-12.md` ile bilerek eklenmişti; kullanıcı geri bildirimiyle bu karar geri alındı.

## Çözüm — Tek Ortak Kural
**Yatay → POSTER, Dikey → BACKDROP** (ekran oranından bağımsız).

1. `DetailHeroGallerySelection.kt`
   - `HeroArtworkLayoutRules` / `PORTRAIT_TALL_RATIO` / `LANDSCAPE_TALL_RATIO` kaldırıldı.
   - `resolveHeroArtworkPreference(w, h)` artık sadece `w > h` karşılaştırması yapar.
2. `KitsugiDetailHero.kt` (Keşfet)
   - Yorumlar ortak kurala göre güncellendi.
   - Kutu oranı (`defaultBoxAspect`, `boxAspect.coerceIn`, `bannerCapDp`) artık tercihe değil, o anda **gösterilen gerçek görsele** (`isVerticalArtwork`) göre belirleniyor.
3. `KitsugiDetailHeroSection.kt` (Listem)
   - El yazısı `screenWidthDp > screenHeightDp` kontrolü yerine ortak `resolveHeroArtworkPreference()` kullanılıyor — iki sayfa bir daha ayrışamaz.
   - Kutu oranı aynı şekilde gösterilen gerçek görsele göre belirleniyor.
4. Yedek davranış korundu: Tercih edilen türde aday yoksa diğer türe düşülür; postere düşüldüğünde poster artık 16:9 aralığına sıkıştırılıp kare kırpılmıyor.

## Değişen Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelectionTest.kt`
- `PLAN_TASK_DETAY_VITRIN_KESFET_LISTEM_POSTER_BACKDROP_FIX_2026-10-23.md` (bu dosya)

## Test
- `DetailHeroGallerySelectionTest` güncellendi: dikey telefon (390x844, 360x800, 360x640) → BACKDROP; dikey tablet/kare (800x900, 600x700, 800x800) → BACKDROP; yatay (800x400) → POSTER.
- Doğrulama komutu:
  ```
  ./gradlew :app:testDebugUnitTest --tests "com.kitsugi.animelist.ui.screens.detail.DetailHeroGallerySelectionTest"
  ```
- Not: Arena sandbox'ında JDK olmadığı için test orada çalıştırılamadı; cihazda/yerelde doğrulanmalı.

## Sonuç
- Keşfet ve Listem detay sayfaları dikeyde yatay arka planı, yatayda dikey posteri gösterir — kaynak fark etmeksizin tutarlı.
