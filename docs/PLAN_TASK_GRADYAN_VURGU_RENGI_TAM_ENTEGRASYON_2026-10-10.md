# PLAN & TASK: Gradyan Vurgu Rengi (Accent Gradient) Tam Uygulama Entegrasyonu

**Tarih:** 10 Ekim 2026  
**Durum:** Tamamlandı ✅

---

## 1. Hedef ve Kapsam
Kullanıcı renk seçiciden **İki Renkli Gradyan** (Başlangıç + Bitiş rengi + Yön/Açı) seçtiğinde, düz vurgu renginin (`KitsugiColors.Accent` / `LocalKitsugiAccent.current`) etki ettiği **tüm** arayüz elemanlarına (butonlar, açılır/kapanır `Switch` anahtarları, `Slider` / `RangeSlider` çubukları, sekmeler, filtre çipleri, ilerleme göstergeleri, alt navigasyon barı, vurgu renkli yazılar, ikonlar, arka planlar ve kenarlıklar) gradyanın eksiksiz ve otomatik kontrastla (`LocalKitsugiOnAccent`) uygulanması.

---

## 2. Kök Neden Analizi (Audit Bulguları)
- [x] **Kök Neden 1 — `AppRoot.kt` Vurgu Rengi Ezilmesi:**
  - `AppRoot.kt:243` ve `735` satırlarında `activeAccentColor = KitsugiAccentForThemeId(appSettings.selectedThemeId)` ile `LocalKitsugiAccent` eziliyordu. Özel renk veya gradyan seçildiğinde `LocalKitsugiAccent.current` hazır temanın (`mint`) renginde kalıyor, `color == LocalKitsugiAccent.current` kontrolleri başarısız oluyordu.
- [x] **Kök Neden 2 — `SettingsScreen.kt` ve `KitsugiPreferencesSettingsDialog.kt` Eksikleri:**
  - `SettingsScreen.kt:484` satırında `iconColor = selectedTheme.color ?: accentColor` kullanıldığı için özel renk/gradyan seçili olsa bile "Görünüm, Tema ve Düzen" ikonu hazır temanın düz rengini gösteriyordu.
  - `KitsugiPreferencesSettingsDialog.kt` içinde hazır tema seçildiğinde `customAccentColor2` sıfırlanmıyordu.
- [x] **Kök Neden 3 — `KitsugiAccentColorPickerDialog.kt` Diyalog İçi Düz Renk Kullanımı:**
  - Diyalog içindeki "İki Renkli Gradyan" `Switch` anahtarı, yön ön ayar butonları (`→`, `↘`, vb.), açı `Slider`'ı, sekme alt çizgileri, "Düzenlenen: Başlangıç / Bitiş" seçili çerçevesi ve "Uygula" butonu canlı gradyan fırçası (`previewBrush`) yerine düz `color1` kullanıyordu.
- [x] **Kök Neden 4 — Material3 ve Foundation Bileşenlerinin Yalnızca `Color` Alması:**
  - Uygulama genelindeki ~300 UI dosyasında kullanılan `Switch`, `Slider`, `RangeSlider`, `RadioButton`, `Checkbox`, `CircularProgressIndicator`, `LinearProgressIndicator`, `TabRow`, `ScrollableTabRow`, `FilterChip`, `Button`, `FloatingActionButton`, `Text`, `Icon`, `Modifier.background(color)` ve `Modifier.border(width, color)` çağrıları yalnızca düz `Color` kabul ettiği için `LocalKitsugiAccentBrush` gradyanını çizmiyordu.

---

## 3. Uygulama Planı ve Görev Listesi (Tasks)

### Faz 1: Tema Kökü ve Kontrast Altyapısı
- [x] **`KitsugiAccentSupport.kt`:**
  - `LocalKitsugiAccent2` (`Color?`), `LocalKitsugiAccentAngle` (`Float`) ve `LocalInsideAccentSurface` (`Boolean`) `CompositionLocal` tanımları eklendi.
  - `Color.isSameAccentRgb(accent: Color)` ve `resolveAccentBrush(color, accent, accent2, angleDegrees)` fonksiyonları eklendi (hem tam opak `accentColor` hem de `accentColor.copy(alpha = ...)` / `AccentMuted` yarı saydam vurgu tonlarını aynı alfa değerinde açılı lineer gradyan fırçasına dönüştürür).
  - `resolveCurrentAccentBrush(color)` ve `accentBrushWithAlpha(alpha)` composable yardımcıları eklendi.
- [x] **`KitsugiColors.kt` & `Theme.kt`:**
  - `KitsugiColors.Accent2`, `KitsugiColors.IsGradient` ve `KitsugiColors.AccentGradientAngle` özellikleri eklendi.
  - `KitsugiAnimeListTheme` içinde `LocalKitsugiAccent2` ve `LocalKitsugiAccentAngle` sağlandı; `MaterialTheme.colorScheme` (`primary`, `onPrimary`, `secondary`, `onSecondary`, `tertiary`, `onTertiary`, `surfaceTint`) aktif vurgu ve `onAccent` kontrast rengine bağlandı.
- [x] **`AppRoot.kt`, `SettingsScreen.kt`, `KitsugiPreferencesSettingsDialog.kt`, `TvPlayerActivity.kt`, `PlayerAccentTheme.kt`:**
  - `AppRoot.kt` içindeki `KitsugiAccentForThemeId` ezmesi kaldırıldı.
  - `SettingsScreen.kt:484` `iconColor = accentColor` olarak güncellendi.
  - `KitsugiPreferencesSettingsDialog.kt` içinde hazır tema seçildiğinde `onCustomAccentColor2Changed(0)` sıfırlaması eklendi.
  - `TvPlayerActivity.kt` ve `PlayerAccentTheme.kt` tam gradyan/kontrast desteğiyle güncellendi.

### Faz 2: Küresel Gradyan Bileşen Sarmalayıcıları (`KitsugiGradientMaterial.kt`)
- [x] **`com.kitsugi.animelist.ui.theme.gradient.KitsugiGradientMaterial.kt` oluşturuldu:**
  - `Modifier.background` & `Modifier.border`: `DrawModifierNode` + `CompositionLocalConsumerModifierNode` tabanlı, non-composable `ModifierNodeElement` mimarisiyle vurgu renkli arka plan ve kenarlıkları otomatik olarak gradyan fırçasıyla çizer.
  - `Text` (`String` & `AnnotatedString`): Vurgu renkli metinleri `style.copy(brush = accentBrush)` ile gradyan çizer; vurgu dolgulu yüzeylerin (`LocalInsideAccentSurface`) içinde veya `KitsugiColors.Background` kontrast rengi verildiğinde otomatik `LocalKitsugiOnAccent.current` kontrast rengini uygular.
  - `Icon` (`ImageVector`, `Painter`, `ImageBitmap`): Vurgu renkli ikonları `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` ile gradyan boyar; vurgu dolgulu yüzeylerde otomatik kontrast rengini uygular.
  - `Switch`: Açık (`checked = true`) konumda `52.dp × 32.dp` hap izini `LocalKitsugiAccentBrush.current` gradyan fırçasıyla, başlığını (`thumb`) `LocalKitsugiOnAccent.current` ile çizer.
  - `Slider` & `RangeSlider`: Aktif şeridi ve tutamaçları gradyan fırçasıyla, tutamaç iç noktasını kontrast rengiyle çizer.
  - `RadioButton` & `Checkbox`: Seçili durumda gradyan fırçası ve kontrast onay işaretiyle çizer.
  - `CircularProgressIndicator` & `LinearProgressIndicator`: Determinate ve indeterminate modlarda gradyan fırçasıyla çizer.
  - `TabRow` & `ScrollableTabRow`: Aktif sekme alt göstergesini gradyan fırçasıyla çizer.
  - `FilterChip`: Seçili durumda yarı saydam gradyan arka plan, gradyan kenarlık ve gradyan metin/ikon uygular.
  - `Button` & `FloatingActionButton`: Vurgu dolgulu butonları gradyan fırçası ve `LocalInsideAccentSurface` kontrastıyla sarmalar.

### Faz 3: Özel Bileşenler ve Tüm UI Dosyalarına Yayılım
- [x] **`KitsugiAccentColorPickerDialog.kt`:** Diyalog içeriği canlı düzenlenen `color1`, `color2`, `angle`, `previewBrush` ve `previewOnColor` değerlerini sağlayan `CompositionLocalProvider` ile sarmalandı; `Switch`, yön butonları, açı `Slider`'ı, sekme göstergeleri, hedef renk çerçevesi ve "Uygula" butonu canlı gradyanla çalışacak şekilde güncellendi.
- [x] **`KitsugiUiverseGlowButton.kt` & `KitsugiSettingsComponents.kt`:** Basılı (`isPressed`) durumda dahi gradyanın korunması ve `LocalInsideAccentSurface` ile tam kontrast sağlanması tamamlandı.
- [x] **`AppBottomBar.kt`, `AutoPlaySwitch.kt`, `VerticalSliders.kt`, `KitsugiNeonGlowCard.kt`, `KitsugiCosmicSearchBar.kt`, `KitsugiPlasmaLoader.kt`, `AlternativeNamesSection.kt`, `KitsugiDetailEpisodesTab.kt`, `ApiResultDetailComponents.kt` ve Profil Başlık Kartları:** Gradyan fırçası ve kontrast sistemiyle güncellendi.
- [x] **Uygulama Genelinde ~300 UI Dosyası:** `com.kitsugi.animelist.ui.theme.gradient.*` import bağlantıları yapılarak tüm ekran, diyalog ve alt sayfalarda gradyan desteği aktif edildi.
