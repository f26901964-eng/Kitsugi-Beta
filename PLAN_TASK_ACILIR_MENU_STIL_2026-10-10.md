# Açılır Menü Stil Uyumu Planı — 2026-10-10

## Sorun
Ayarlar ve diğer sayfalardaki açılır menüler (ör. "Anime & Manga Başlık Dili")
Material3'ün varsayılan `DropdownMenu` görünümündeydi. Uygulamanın koyu yüzey
diliyle uyumsuz, animasyonsuz ve seçili satır vurgusu zayıftı.

## Çözüm
1) **`KitsugiDropdownMenu` yeniden yazıldı** (`ui/components/KitsugiSettingsComponents.kt`)
   - Özel `Popup` + `AnimatedVisibility` (fade + hafif scale, üst kenardan)
   - Yüzey: `KitsugiColors.SurfaceStrong`, 20dp köşe, `Border` çerçeve, gölge
   - Konum: çıpanın altına; alta sığmazsa üstüne; ekran kenarına göre sıkıştırma
   - Uzun listeler `verticalScroll` ile kaydırılır
2) **`KitsugiMenuItem` eklendi**: accent yarı saydam seçili zemin, sağda onay
   ikonu (veya özel `trailingIcon`), solda opsiyonel `leadingIcon`, `enabled` desteği
3) **`KitsugiDropdownItem`** yeni `KitsugiMenuItem` üzerine kuruldu (imza aynı)
4) **Doğrudan `DropdownMenu` kullanan ekranlar taşındı:**
   - `KitsugiSeasonalFilterBottomSheet.kt` (sıralama)
   - `EntryDetailTabContents.kt` (galeri kategorisi)
   - `SubtitleDelayPanel.kt` (altyazı gecikmesi tipi)
   - `KitsugiMyListSortMenu.kt` (Listem sıralama, artan/azalan ok)
   - `ProfileActivityComponents.kt` (satır silme menüsü)
   - `SearchFilterDialogs.kt` (yıl/sezon seçimi)
   - `SearchChips.kt` (arama çipi tek seçim menüsü)

## Doğrulama
- Tüm `KitsugiDropdownMenu` çağrılarının imzası korundu (72 kullanım otomatik güncellendi).
- Repoda kalan doğrudan `DropdownMenu(` / `DropdownMenuItem(` kullanımı yok.
- Derleme (`gradle assembleDebug`) ve cihazda görsel kontrol: **henüz yapılmadı**
  (ortamda JDK/Android SDK yok). Açılış/kapanış animasyonu ve konum, gerçek cihazda kontrol edilmeli.

## Kapsam dışı
- `ModalBottomSheet` / `AlertDialog` tabanlı paneller bu işin kapsamında değil.
