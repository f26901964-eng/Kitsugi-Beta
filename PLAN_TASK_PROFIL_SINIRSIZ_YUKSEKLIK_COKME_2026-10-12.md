# Plan / Görev: Profil Favori Sheet — Sınırsız Yükseklik Çökmesi

**Tarih:** 2026-10-12
**Branch:** arena/e87a8429-kitsugi-beta
**Commit:** 79daaeb

## Hata
- `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`
- Tetik: `LazyVerticalGrid` sınırsız (Infinity) dikey max height ile ölçülüyor.
- Yığın: LazyGrid → LazyLayout → WrapContentNode → FillNode → LazyList item.

## Kök neden (olası)
- `FavoritesExpandedBottomSheet` (ui/screens/profile/ProfileActivityComponents.kt) içindeki grid
  `Modifier.fillMaxWidth().weight(1f, fill = false)` ile Column içine konulmuştu.
- Üst katmanlarda (KitsugiSheetOrDialog / ModalBottomSheet) bounded yükseklik garantisi olmadığında
  grid sonsuz yükseklikle ölçülüyor.

## Yapılan değişiklik
- Grid'in `weight` modifier'ı kaldırıldı.
- Yerine `height((screenHeightDp * 0.7f).dp)` konuldu → her zaman sınırlı yükseklik.
- `androidx.compose.ui.platform.LocalConfiguration` import'u eklendi.

## Doğrulama durumu
- [ ] Android APK derlemesi (SDK sandbox'ta yok — yerelde doğrulanmalı)
- [ ] Profil → "Tümünü gör" (favoriler) açılıyor, kaydırma çalışıyor
- [ ] Farklı ekran boyutlarında (küçük/büyük telefon, yatay mod) kontrol

## Açık kalan işler (bu düzeltme kapsamında DEĞİL)
- [ ] Profil sekmesinde alt bardan başka sekmeye geçip tekrar Profil'e basınca ikinci dokunuşta çökme
- [ ] Uygulama kapanıp açılınca son sekmenin (Profil) açılmaması (`AppRoot.kt` lastUsedTab / defaultTab mantığı)
- [ ] `KitsugiProfileScreen.kt` / `KitsugiUserProfileScreen.kt` içindeki null kontrolsüz `!!` kullanımları
      (activeFavoriteSheet, activeActivityIdForDetail, activeGalleryImages)
- [ ] Crash log tam stack trace'i (uygulama frame'leri) ile tetikleyici composable'ın kesin tespiti

## Değişen dosyalar
- app/src/main/java/com/kitsugi/animelist/ui/screens/profile/ProfileActivityComponents.kt
- diff: fix.diff (bu zip içinde)
