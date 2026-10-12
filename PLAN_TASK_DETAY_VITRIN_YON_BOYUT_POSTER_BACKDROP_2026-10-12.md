# Detay Vitrin Yön × Boyut Otomatik Poster/Backdrop Seçimi

**Tarih:** 2026-10-12
**Dal:** `arena/b648c3ed-kitsugi-beta`
**Hedef:** Detay sayfası vitrininde görsel tercihi sadece cihaz yönüne değil, **dikey modda ekran oranına** da bakmalı.

## Sorun
Mevcut mantık yalnızca yatay/dikey yöne bakıyordu:
- Yatay mod → dikey poster
- Dikey mod → yatay arka plan

Kullanıcı tarafından raporlanan senaryo: uzun-dar bir telefonda dikey modda
yukarıdaki kural yüzünden yatay arka plan gösteriliyordu. Dikey poster ise
görsel açıdan bu cihazda çok daha kaliteli ve doğal duruyor.

## Çözüm
`HeroArtworkLayoutRules` + `resolveHeroArtworkPreference(...)` helper'ı
(`DetailHeroGallerySelection.kt`) eklendi. Bu helper verilen `screenWidthDp` /
`screenHeightDp` değerlerine bakarak şu sonucu döndürür:

- `HeroArtworkPreference.POSTER` — dikey poster birincil olsun.
- `HeroArtworkPreference.BACKDROP` — yatay arka plan birincil olsun.

### Kurallar
1. **Yatay mod** (`width > height`): neredeyse tüm cihazlarda dikey poster
   (`POSTER`). Aşırı "kare yatay" köşesi için `LANDSCAPE_TALL_RATIO = 0.40`
   koruma eşiği var.
2. **Dikey mod** (`height > width`):
   - Eğer `width / height <= 0.55` (tipik 9:18-9:21 telefonlar) → `POSTER`
   - Daha kare/tablet benzeri cihazlar → `BACKDROP`

`KitsugiDetailHero.kt` içinde eski `isLandscapeScreen` bayrağı tamamen kaldırıldı;
yerine `usePosterArtwork` (`artworkPreference == POSTER`) kullanılıyor.

### Etkilenen noktalar
- `imagePlan` birincil listeyi `usePosterArtwork`'e göre sıralıyor.
- `defaultBoxAspect`: POSTER için 2:3, BACKDROP için 16:9.
- `boxAspect.coerceIn(...)`: POSTER için (0.5, 1.6), BACKDROP için (1.0, 2.2).
- `bannerCapDp`: POSTER için `screenHeightDp` (uzun dikey kutuyu sınırla),
  BACKDROP için `screenHeightDp * 0.62f`.

## Sonuç
- Tüm yatay mod cihazlarda davranış aynı: dikey poster gösterilir.
- Dikey modda uzun-dar telefonlarda dikey poster, daha kare/tablet cihazlarda
  yatay arka plan gösterilir.
- Görsel alanı kutu oranı yine görselin gerçek en-boy oranına kilitlenir, dolayısıyla
  Crop kırpması yine sıfıra yakındır.
- `imageIndex` `imagePlan` değiştiğinde `remember(imagePlan)` ile sıfırlanır;
  yön/boyut değişiminde güvenli geçiş sağlanır.

## Değişen Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailHeroGallerySelection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt`
