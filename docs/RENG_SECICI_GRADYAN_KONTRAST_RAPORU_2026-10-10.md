# Renk Seçici (Palet / Özel / Gradyan), Tüm Uygulama Gradyan Entegrasyonu ve Otomatik Siyah-Beyaz Kontrast Raporu

**Tarih:** 10 Ekim 2026

## 1. Sorun Özeti ve Kök Nedenler

1. **`AppRoot.kt` Vurgu Rengi Ezilmesi (Kritik Kök Neden):**
   - `AppRoot.kt:243` ve `735` satırlarında `activeAccentColor = KitsugiAccentForThemeId(appSettings.selectedThemeId)` hesaplanıp `LocalKitsugiAccent` üzerine yazılıyordu. Bu nedenle özel renk veya gradyan seçildiğinde `LocalKitsugiAccent.current` tüm telefon/tablet arayüzünde hazır temanın düz rengine (`mint`) geri dönüyor, `color == LocalKitsugiAccent.current` kontrolleri başarısız oluyordu.
2. **Gradyan Fırçasının (`LocalKitsugiAccentBrush`) Yalnızca Birkaç Bileşende Kullanılması:**
   - Gradyan fırçası yalnızca `SettingsIcon`, `KitsugiUiverseGlowButton`, `KitsugiDetailActionButton`, `KitsugiButton` ve `+` rozetinde okunuyordu.
   - Uygulama genelindeki `Switch` (açılır/kapanır butonlar), `Slider` / `RangeSlider` / `VerticalSlider`, `TabRow` / `ScrollableTabRow`, `FilterChip`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `AppBottomBar` / `AppNavigationRail`, vurgu renkli `Text` ve `Icon` bileşenleri, `.background(accentColor)` / `.background(accentColor.copy(alpha = ...))` ve `.border(..., accentColor, ...)` çağrıları yalnızca düz `color1` rengini çiziyordu.
3. **Renk Seçici Diyaloğunun (`KitsugiAccentColorPickerDialog`) Kendi İçinde Düz Renk Kullanması:**
   - Diyalog içindeki "İki Renkli Gradyan" `Switch`'i, yön ön ayar butonları (`→`, `↘`, vb.), açı `Slider`'ı, sekme alt çizgileri, "Düzenlenen: Başlangıç / Bitiş" aktif çerçevesi ve "Uygula" butonu canlı gradyan yerine düz `color1` kullanıyordu.

---

## 2. Yapılan Mimari Değişiklikler

### 2.1. Tema Kökü ve `KitsugiAccentSupport.kt`
- `LocalKitsugiAccent2` (`Color?`), `LocalKitsugiAccentAngle` (`Float`) ve `LocalInsideAccentSurface` (`Boolean`) `CompositionLocal` değerleri eklendi.
- `Color.isSameAccentRgb(accent: Color)` ve `resolveAccentBrush(color, accent, accent2, angleDegrees)` yardımcıları eklendi:
  - Hem tam opak `accentColor` hem de `accentColor.copy(alpha = ...)` / `KitsugiColors.AccentMuted` gibi yarı saydam vurgu tonları otomatik olarak tespit edilip aynı alfa değerine sahip `AngleLinearGradientBrush` örneklerine dönüştürülür.
- `AppRoot.kt:243` ve `735` üzerindeki `KitsugiAccentForThemeId` ezmesi kaldırılarak `LocalKitsugiAccent.current` doğrudan korundu.
- `SettingsScreen.kt:484` ("Görünüm, Tema ve Düzen" satırı), `KitsugiPreferencesSettingsDialog.kt` ve `TvPlayerActivity.kt` özel vurgu rengi ve gradyan ayarlarını tam aktaracak şekilde güncellendi.

### 2.2. Küresel Gradyan Bileşen Sarmalayıcıları (`com.kitsugi.animelist.ui.theme.gradient.KitsugiGradientMaterial.kt`)
Uygulama genelindeki tüm UI dosyalarında vurgu renginin geçtiği her noktada gradyanın otomatik uygulanması için `com.kitsugi.animelist.ui.theme.gradient` paketinde sıfır-ek-yük (drop-in) bileşenler oluşturuldu:
- **`Modifier.background` & `Modifier.border` (`DrawModifierNode` + `CompositionLocalConsumerModifierNode`):**
  - Non-composable `ModifierNodeElement` mimarisiyle `color` parametresi aktif vurgu rengiyle (veya yarı saydam kopyasıyla) eşleştiğinde otomatik olarak açılı lineer gradyan fırçasıyla çizim yapar.
- **`Text` & `Icon`:**
  - `color` / `tint` değeri tema vurgusuyla eşleştiğinde yazıları `TextStyle.copy(brush = accentBrush)` ile, ikonları ise `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` ile gradyan olarak boyar.
  - Vurgu dolgulu yüzeylerin (`LocalInsideAccentSurface`) içinde veya `KitsugiColors.Background` kontrast rengi verildiğinde otomatik olarak `LocalKitsugiOnAccent.current` (siyah ↔ beyaz smoothstep kontrast) rengini uygular.
- **`Switch` (Açılır/Kapanır Butonlar):**
  - Açık (`checked = true`) konumdayken `52.dp × 32.dp` hap kanalını `LocalKitsugiAccentBrush.current` gradyan fırçasıyla, başlığını (`thumb`) ise `LocalKitsugiOnAccent.current` kontrast rengiyle çizer.
- **`Slider` & `RangeSlider`:**
  - Aktif şeridi (`activeTrack`) ve tutamacı (`thumb`) aktif gradyan fırçasıyla, tutamaç merkez noktasını ise kontrast rengiyle çizer.
- **`RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`:**
  - Seçili/aktif durumlarında tema gradyan fırçasını ve otomatik kontrast rengini kullanır.

### 2.3. Özel Bileşenler ve Renk Seçici Diyaloğu
- **`KitsugiAccentColorPickerDialog.kt`:** Diyalog içeriği canlı düzenlenen `color1`, `color2`, `angle`, `previewBrush` ve `previewOnColor` değerlerini sağlayan `CompositionLocalProvider` ile sarmalandı. Böylece diyalog içindeki `Switch`, yön butonları, açı `Slider`'ı, sekme göstergeleri, hedef renk çerçevesi ve "Uygula" butonu kullanıcı gradyanı değiştirirken anlık olarak gradyanla güncellenir.
- **`AppBottomBar.kt` (`AppBottomBar` & `AppNavigationRail`):** Seçili sekme hapı (`LocalKitsugiAccentBrush`), seçili ikon (`LocalKitsugiOnAccent`), seçili sekme yazısı ve `"Kitsugi"` başlığı gradyan destekli hâle getirildi.
- **Oynatıcı Kontrolleri (`AutoPlaySwitch.kt`, `VerticalSliders.kt`, `PlayerAccentTheme.kt`):** Otomatik oynatma anahtarı ve dikey ses/parlaklık çubukları gradyan fırçasıyla çizilecek şekilde güncellendi.
- **`KitsugiUiverseGlowButton.kt`, `KitsugiSettingsComponents.kt`, `KitsugiNeonGlowCard.kt`, `KitsugiCosmicSearchBar.kt`, `KitsugiPlasmaLoader.kt` ve Profil Başlık Kartları:** Tamamı gradyan fırçası ve `LocalKitsugiOnAccent` kontrast sistemiyle uyumlu hâle getirildi.
