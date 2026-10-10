# Vurgu Rengi Seçici, Gradyan Mekaniği & Otomatik Siyah-Beyaz Kontrast Raporu

**Tarih:** 2026-10-10
**Kapsam:** Görünüm & Tercihler → Tema Rengi "+" seçici, gradyan vurgu mekaniği, vurgu zeminlerindeki yazı/ikonlar için otomatik kontrast.

---

## 1. İstenen Değişiklikler

1. **"+" butonu** artık hex kodu sormak yerine **tam bir renk seçici panel** açmalı:
   - Alt bardaki hazır 10 temanın dışında **tüm renkler ve tüm ton varyantları**
   - İsteğe bağlı **gradyan renk mekaniği ve seçeneği**
2. Vurgu (tema) renginin etkilediği **tüm yazılar**; seçilen rengin koyuluğuna/açıklığına göre
   **siyah ↔ beyaz arasında yumuşak geçişli** bir kontrast rengi almalı.

---

## 2. Yeni Renk Seçici (`KitsugiAccentColorPickerDialog.kt`)

Eski `CustomColorPickerDialog` (yalnızca hex alanı) tamamen kaldırıldı; yerine 3 sekmeli panel geldi:

| Sekme | İçerik |
|---|---|
| **Palet** | 19 renk ailesi × 10 ton (50→900) + Siyah/Beyaz = **192 renk**; alt barda olmayan tüm varyantlar |
| **Özel** | Tam **HSV doygunluk-değer karesi + ton (hue) şeridi**; sınırsız renk; isteğe bağlı hex alanı |
| **Gradyan** | **İki renkli açılı lineer gradyan**: açı slider'ı + 8 yön ön ayarı (→ ↘ ↓ ↙ ← ↖ ↑ ↗), canlı şerit önizleme |

- Üstte her zaman **canlı önizleme şeridi** (seçili düz renk veya gradyan + hex değerleri)
- Gradyan açıkken **"Başlangıç / Bitiş"** hedef düğmeleri ile hangi rengin düzenlendiği seçilir
- Alt bardaki **"+" rozeti** artık seçili düz/gradyan rengi gösterir

## 3. Gradyan Mekaniği (Kalıcı Ayarlar)

| Katman | Değişiklik |
|---|---|
| `AppSettings` | `customAccentColor2` (bitiş rengi, 0 = düz), `customAccentGradientAngle` (açı, vars. 135°) |
| `SettingsDataStore` | `custom_accent_color_2`, `custom_accent_gradient_angle` anahtarları + setter'lar |
| `Theme.kt` | `LocalKitsugiAccentBrush` — düz veya `AngleLinearGradientBrush` (açıya göre piksel hassasiyetinde lineer gradyan) |
| Tema taşıyıcı aktiviteler | `MainActivity`, `KitsugiFullscreenPlayerActivity`, `KitsugiStreamActivity`, `DownloadsActivity`, `WatchHistoryActivity` ayarları temaya geçirir |

**Gradyanın uygulandığı yüzeyler:** ayar satırlarındaki ikon daireleri (`SettingsIcon`),
ana aksiyon butonları (`KitsugiUiverseGlowButton`, `KitsugiButton`, `KitsugiDetailActionButton`),
"+"' rozeti ve seçici önizlemeleri.

## 4. Otomatik Siyah-Beyaz Kontrast (`onAccentColor`)

`ui/theme/KitsugiAccentSupport.kt` içinde:

```kotlin
fun onAccentColor(background: Color): Color  // smoothstep siyah↔beyaz geçişi
```

- Zemin **koyulaştıkça metin beyaza**, **açıklaştıkça siyaha** kayar; dar bir geçiş bandında
  ara gri tonlar üretilir (istenilen "siyah-beyaz arası geçiş").
- Gradyanda geçiş rengi iki rengin **orta harmanının** parlaklığına göre hesaplanır.
- `LocalKitsugiOnAccent` ile tema kökünden tüm bileşenlere dağıtılır.

**Uygulanan başlıca noktalar:**

- `SettingsIcon` (tüm ayar ikonları — ekran görüntüsündeki turuncu dairelerdeki beyaz ikonlar artık otomatik)
- `KitsugiUiverseGlowButton` ailesi (İzle/Oku, kaydır-yukarı FAB'ları, tüm dolgulu butonlar)
- Oynatıcı: `PlayerAccentTheme`, `DelayCard`, `PlaybackEndedOverlay`, "Sonraki Bölüm" butonları
- `KitsugiPlayerSettingsDialog` ("Yeni Özel Buton Ekle", "Yeni Dosya Oluştur", "Kaydet")
- TV kitaplık sayaç rozeti, QR giriş "Seç" rozeti, devam-etiketi oynat ikonu
- "Tekrar Dene" butonu

> Not: Video üstü yarı saydam siyah/beyaz katmanlardaki beyaz yazılar bilinçli olarak
> korundu (o zeminler vurgu rengi değil). Fotoğraf üstü avatar seçim ikonu da aynı nedenle beyaz kaldı.

## 5. Dosya Listesi

**Yeni:**
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAccentColorPickerDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/theme/KitsugiAccentSupport.kt`

**Değiştirilen:**
- `data/settings/AppSettings.kt`, `data/settings/SettingsDataStore.kt`
- `ui/theme/Theme.kt`, `ui/theme/KitsugiColors.kt`
- `ui/components/KitsugiPreferencesSettingsDialog.kt`, `KitsugiSettingsComponents.kt`, `KitsugiUiverseGlowButton.kt`
- `ui/screens/settings/SettingsScreen.kt`, `SettingsScreenParameters.kt`
- `AppRootSettingsExtras.kt`, `MainActivity.kt`
- `ui/screens/fullscreen/PlayerAccentTheme.kt`, `KitsugiFullscreenPlayerActivity.kt`, `controls/components/panels/DelayCard.kt`, `components/PlaybackEndedOverlay.kt`, `KitsugiFullscreenPlayerScreen.kt`
- `ui/screens/stream/KitsugiStreamActivity.kt`, `WatchHistoryActivity.kt`, `ui/screens/offline/DownloadsActivity.kt`
- `ui/screens/explore/ExploreScreen.kt`, `ui/screens/search/SearchScreen.kt`, `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `ui/components/KitsugiPlayerSettingsDialog.kt`, `KitsugiTvQrLoginDialog.kt`, `ContinueWatchingProgressLabel.kt`
- `ui/tv/library/TvLibraryScreen.kt`

## 6. Doğrulama

- Tüm değiştirilen dosyalarda parantez/söz dizimi dengesi kontrol edildi.
- Eski `CustomColorPickerDialog` / `colorInputText` referansları temizlendi.
- Vurgu zemini + sabit beyaz yazı kalıpları için kod tabanı geneli tarama yapıldı;
  kalan tek eşleşme bilinçli korunan fotoğraf üstü katmandır.
