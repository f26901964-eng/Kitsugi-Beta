# Kitsugi-Beta v2.4.245 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎨 1. Yenilenmiş Duyarlı Vitrin Göstergesi & Çerçeve Tasarımı
- **Sayısal Rozet Kaldırıldı:** Ana sayfa vitrininde (hero slider) yer alan "8 / 10" formatındaki sayısal sayaç rozeti ve `heroCounterIndex` kaldırıldı; arayüz görsel olarak sadeleştirildi ve recomposition yükü azaltıldı.
- **Dinamik Çerçeve Göstergesi (`drawBehind`):** Hangi vitrin sayfasında olunduğunu belirtmek için aktif çizginin etrafını saran yuvarlatılmış estetik bir çerçeve eklendi. Çerçevenin opaklığı sayfa kaydırma geçişinde (`heroPageVisibility`) doğrudan GPU çizim katmanında akıcı şekilde animasyonlanır.
- **Her Ekrana Uyumlu (Responsive) Çizgiler:** Çizgiler `Modifier.weight(1f)` ile yatay alanı eşit paylaşır. `fillMaxWidth` ve `widthIn(max)` kısıtlamaları sayesinde dikey ve yatay telefonlarda veya tabletlerde ekrandan taşma yapmaz; geniş ekranlarda çizgilerin aşırı uzaması engellenir.
- **Çizim Optimizasyonu:** Renk getter'ları (`indicatorFrameColor`) composition kapsamında yakalanarak `drawBehind` içerisindeki çizim güvenliği ve performansı artırıldı.

---

## 🇬🇧 English

### 🎨 1. Responsive Hero Slider Indicator & Sleek Active Frame
- **Numeric Counter Removal:** Removed the static "8 / 10" badge and `heroCounterIndex` state from the main hero carousel, delivering a clean, modern aesthetic without recomposition overhead.
- **Active Frame Indicator (`drawBehind`):** Introduced a rounded boundary frame wrapping the active page line. Frame opacity animates smoothly based on page scroll offset (`heroPageVisibility`) directly in the draw phase without recomposing layouts.
- **Fully Responsive Bar:** Indicator lines now evenly distribute width using `Modifier.weight(1f)`. Combined with `fillMaxWidth` and responsive `widthIn(max)`, the indicator perfectly scales across portrait/landscape phones and tablets without overflowing or stretching excessively on large screens.
- **Draw Phase Optimization:** Cleanly captures theme color getters (`indicatorFrameColor`) within the composition scope prior to `drawBehind` canvas execution.
