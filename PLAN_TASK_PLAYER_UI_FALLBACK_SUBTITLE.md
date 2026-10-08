# PLAN / TASK — Medya Oynatıcı: Gösterge Yerleri, Altyazı Paneli, Tema, Yedek Oynatıcı, TR Altyazı Önceliği

Branch: `arena/6835beca-kitsugi-beta` · Commit: `c7e114e`

## Görevler ve Durum

| # | Görev | Durum | Dosyalar |
|---|-------|-------|----------|
| 1 | Parlaklık göstergesi SAĞDA, ses göstergesi SOLDA (sabit). Kaydırma bölgeleri değişmedi. | ✅ | `controls/PlayerControls.kt`, `controls/GestureHandler.kt` (yorum) |
| 2 | Altyazı stil penceresi: yatay/dikey modda ekran dışına taşmıyor, kaydırılabilir, safe-area uyumlu | ✅ | `controls/components/panels/SubtitleSettingsPanel.kt` |
| 3 | Oynatıcı arayüzü (sheet, dialog, panel, slider, buton, seek bar) seçili tema rengiyle uyumlu | ✅ | `PlayerAccentTheme.kt` (yeni), `KitsugiFullscreenPlayerActivity.kt`, `TvPlayerScreen.kt`, `controls/*`, `components/*` |
| 4 | Ana oynatıcı (MEDIA3) oynatma hatası verirse ikinci DAHİLİ oynatıcıya (MPV) geç; harici uygulamaya düşme yok | ✅ | `core/player/engine/PlayerFallbackCoordinator.kt`, `runtime/PlayerErrorRecoveryController.kt`, `KitsugiPlayerViewModel.kt`, `PlayerFallbackCoordinatorTest.kt` |
| 5 | Alt yazı: varsayılan TR; video İÇİ altyazı her zaman harici eklentiden önce; "Türkçe/Turkish/tr/tur/tr-TR" otomatik tanınır | ✅ | `core/player/PlayerSubtitleUtils.kt`, `core/player/engine/Media3PlayerEngine.kt` |

## Yapılan Teknik Değişiklikler

### Gösterge yerleşimi
- Brightness overlay → `Alignment.CenterEnd`, sağdan kayarak girer.
- Volume overlay → `Alignment.CenterStart`, soldan kayarak girer.
- `swipeVolumeBrightnessSides` ayarı artık yalnızca dokunma bölgelerini belirler (gösterge yerleşimini etkilemez).

### Altyazı stil paneli
- ConstraintLayout kaldırıldı; `Box(fillMaxSize, windowInsetsPadding(safeDrawing))` + `Column(fillMaxHeight, widthIn(max), verticalScroll)`.
- Dikey: ortada, max 560dp. Yatay: sağda, max ~500dp.

### Tema
- `PlayerAccentTheme` → Material `primary/secondary/tertiary` = `KitsugiColors.Accent`.
- Aktivite `KitsugiAnimeListTheme`'e `selectedThemeId`, `customAccentColor`, `amoledBlack`, `themeMode` geçiriyor (önceden hiçbiri geçirilmiyordu → mint varsayılanı).
- `PlayerSheet`'in sabit mor paleti ve `Color(0xFF16162A)` / `Color(0xFF1E1E38)` yüzeyleri `playerSurfaceColor()` ile değişti.
- `AccentBlue` / `AccentGreen` kullanımları `Accent`'e çevrildi.

### Kurtarma zinciri
- Eski: MEDIA3 → MPV (yalnızca ayar açıksa) → EXTERNAL.
- Yeni: MEDIA3 ↔ MPV (her bir motora bir kez; `triedEngines`), sonra kaynak-seviyesi kurtarma / fatal.
- MPV paketle gelir (`mpv-android-lib`), `isMpvEnabled = { true }`.
- HTTP/erişim hataları (403/404/5xx) hâlâ motor değiştirmeden kaynak değiştirir.

### Altyazı
- **Bug fix:** `"file://$sub.url"` → `"file://${sub.url}"` (yerel önbellek altyazıları yüklenemiyordu).
- Harici altyazı `SubtitleConfiguration.setId(sub.url)` ile işaretleniyor.
- Harici tespiti: önce track id eşleşmesi, sonra birebir (case-insensitive) etiket eşleşmesi. Eskiden `contains` ile yanlış sınıflandırma olabiliyordu.
- `matchesLanguageCode`: `tr-TR`, `tr_TR` → `tr`.

## Doğrulama Durumu
- ⚠️ Android SDK / Gradle bu ortamda yok → derleme ve cihaz testi YAPILMADI.
- Parantez/süslü parantez dengesi kontrol edildi.
- Birim testi `PlayerFallbackCoordinatorTest` yeni zincire göre güncellendi (çalıştırılmadı).

## Açık Noktalar / Sonraki Adımlar
1. Projeyi derleyip oynatıcıda şunları doğrula: gösterge yerleri, altyazı paneli (yatay/dikey), tema rengi, MEDIA3→MPV geçişi.
2. Media3 `SubtitleConfiguration.setId` ile harici altyazı track id'sinin gerçekten `sub.url` olarak geldiğini doğrula; gelmezse isim eşleşmesi yedek olarak çalışır.
3. İstenirse kaydırma (dokunma) bölgeleri de parlaklık sağda / ses solda olacak şekilde değiştirilebilir (`swipeVolumeBrightnessSides`).
