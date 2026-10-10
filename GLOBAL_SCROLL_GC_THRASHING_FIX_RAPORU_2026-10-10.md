# Global Scroll / GC Thrashing Düzeltme Raporu

**Tarih:** 2026-10-10

## Özet

Telefon ölçümleri GPU'nun darboğaz olmadığını; uzun UI kareleriyle eşzamanlı yoğun nesne üretimi ve GC baskısı bulunduğunu gösterdi. Kod incelemesinde global ana thread/diagnostic sorunlarına ek olarak bottom bar state yayılımı ve hero animasyonunun kare başına allocation/layout üretmesi tespit edildi.

## Yapılan Değişiklikler

- Ana thread'deki iç içe `Looper.loop()` kaldırıldı.
- Debug APK'nın bütün logcat'i sürekli dosyaya yazması kaldırıldı.
- Bottom bar görünürlüğü Boolean değer olarak ağır ekran ağacına taşınmıyor.
- State okuması yalnız küçük FAB composable'ına izole edildi.
- Hero animasyonunda kare başına `List` ve `HeroPageLayer` üretimi kaldırıldı.
- Hero offset/alpha/translation hesapları `graphicsLayer` aşamasına taşındı.
- Gösterge genişlik animasyonu layout yerine sabit genişlik + `scaleX` kullanıyor.

## Commitler

- `7959706` — `fix(perf): remove nested main looper and debug logcat writer`
- `682f459` — `fix(perf): isolate scroll state and eliminate hero frame allocations`

## Not

Sandbox ortamında Java/JDK bulunmadığı için Gradle derlemesi çalıştırılamadı. Kaynak diff kontrolü başarılıdır. Kesin performans doğrulaması yeni APK üzerinde aynı gezinme senaryosu ve yeni gfxinfo/logcat/Perfetto ölçümüyle yapılmalıdır.
