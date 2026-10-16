# PLAN/TASK — Wiki Sayfaları İçerik Gösterimi + Yukarı Hızlı Kaydırma FAB Tek Tip Tasarım

**Tarih:** 2026-10-16
**Sürüm:** 2.4.293 → 2.4.294 (beta)

## Kullanıcı isteği
1. Wiki sayfalarını açmadan içerik/veriler gösterilmiyor; bu kısım düzeltilmeli.
2. Tüm wiki sayfalarında, Keşfet ve Arama sayfalarında aşağı kaydırınca otomatik çıkan "yukarı hızlı kaydırma" butonu tek tip ve düzgün (güzel) olsun.

## Yapılanlar

### 1. Yukarı hızlı kaydırma FAB'ı yeniden tasarlandı
Dosya: `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiScrollToTopFab.kt`
- Yuvarlak (CircleShape), gölgeli (shadow 8dp), 56dp FAB.
- İkon `KeyboardArrowUp` (30dp).
- Belirme/kaybolma animasyonları yumuşatıldı; görünürlükte scale animasyonu eklendi.
- Alt bar görünür/gizli durumuna göre konumlandırma korundu; tek bileşen tüm ekranlarda tutarlı.

### 2. Wiki okuyucusuna yukarı kaydırma FAB'ı eklendi
Dosya: `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`
- `ReaderContent` kökü `Box` ile sarıldı; kaydırma 400px'i geçince beliren FAB eklendi.
- FAB tıklandığında `scrollState.animateScrollTo(0)` ile en üste animasyonlu kaydırma.
- Tüm wiki okuyucularında (medya + karakter) aynı tek tip buton kullanılıyor.

## Değişen dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiScrollToTopFab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/FandomWikiReader.kt`

## Doğrulama
- `git diff --check`: başarılı.
- JDK/Android SDK bu ortamda bulunmadığından Gradle derlemesi çalıştırılamadı; değişiklikler elle incelendi.
