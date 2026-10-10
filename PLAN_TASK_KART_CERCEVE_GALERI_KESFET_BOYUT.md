# PLAN / TASK: Kart Çerçeveleri, Galeri Adları ve Keşfet Kart Boyutu

Tarih: 2026-10-10
Dal: `arena/e2f4b513-kitsugi-beta`
Commit: `463e5c0`

## Kapsam

### 1. Kart çerçeveleri tek tuşla aç/kapa
- Ayarlar → Görünüm & Tercihler → Tema & Görünüm → **Kart Çerçeveleri** anahtarı.
- Ayar DataStore'da `card_frames_enabled` anahtarıyla saklanır (varsayılan: açık).
- `LocalCardFramesEnabled` (CompositionLocal) `MainActivity` içinde `AppSettings.cardFramesEnabled` değerine bağlanır.
- Kapalıyken `kitsugiNeonGlow` modifier'ı ve `KitsugiNeonGlowCard` renkli kenarlığı (ve `kitsugiNeonGlow` gölgesini) çizmez.
- Etkilenen yerler: Keşfet, Listem, Arama, Profil favorileri ve diğer `kitsugiNeonGlow` kullanan kartlar.
- Kapsam dışı: TV ayar ekranı, `.border(` ile elle çizilen diğer öğeler (ör. "+1 Ekle" butonu).

### 2. Karakter / kişi galerisi adları dile uyumlu
- Sorun: Galeri etiketi (`GalleryItem.description`) ham Japonca ad olarak yazılıyordu; seçili dil İngilizce olsa bile.
- Çözüm: `galleryPersonLabel(...)` (yeni dosya `data/remote/GalleryPersonLabel.kt`).
  - NATIVE / JAPANESE_STAFF → Japonca özgün ad
  - ENGLISH → İngilizce → Romaji → Latin alternatif ad → mevcut ad
  - ROMAJI (varsayılan) → Romaji → İngilizce → Latin alternatif ad → mevcut ad
- `CharacterDetailPage` ve `StaffDetailPage` galeri listesini bu etiketle yeniden eşler.
- Sınır: Kanji/kana'yı romaji'ye çeviren bir kütüphane yok. Latin bir ad hiçbir kaynakta yoksa Japonca ad gösterilir.

### 3. Keşfet kartları aynı boyutta
- `KitsugiExploreMediaCard`:
  - Dikey: metin alanı sabit yükseklik (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT = 168.dp`), `clipToBounds`.
  - Yatay (landscape): satır yüksekliği poster yüksekliğine sabit.
  - TV: metin alanı sabit yükseklik (`EXPLORE_CARD_TV_TEXT_HEIGHT = 76.dp`).
- Bilinen risk: Çok uzun başlık/açıklamada son satır kırpılabilir. Gerekirse sabit yükseklik artırılmalı.

## Değişen dosyalar
- app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt
- app/src/main/java/com/kitsugi/animelist/MainActivity.kt
- app/src/main/java/com/kitsugi/animelist/data/remote/GalleryPersonLabel.kt (yeni)
- app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt
- app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt
- app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiExploreMediaCard.kt
- app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNeonGlowCard.kt
- app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt
- app/src/main/java/com/kitsugi/animelist/ui/theme/KitsugiCardFrames.kt (yeni)

## Doğrulama durumu
- [x] Parantez/süslü parantez dengesi kontrol edildi (13 dosya)
- [ ] Gradle derlemesi (bu ortamda Android SDK ve Maven erişimi yok)
- [ ] Cihazda manuel test:
  - [ ] Kart Çerçeveleri kapalı/açık: Keşfet, Listem, Arama, Profil
  - [ ] Karakter ve seslendirmen galerisi: EN / ROMAJI / NATIVE etiketleri
  - [ ] Keşfet kartları: dikey, yatay ve TV'de aynı boyut

## Açık işler
- TV ayar ekranına Kart Çerçeveleri anahtarı eklenmesi.
- Kanji → romaji dönüşümü (gerekirse ayrı bir kütüphane değerlendirmesi).
