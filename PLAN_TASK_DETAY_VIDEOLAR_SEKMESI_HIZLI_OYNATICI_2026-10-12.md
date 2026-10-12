# PLAN / TASK — Detay Sayfaları Video Sekmesi ve Hızlı Oynatıcı Kapatma

**Tarih:** 2026-10-12  
**Durum:** Kod değişiklikleri tamamlandı; Android derlemesi/cihaz testi ortamda doğrulanamadı.  
**Kapsam:** `MediaEntryDetailPage` ve `ApiResultDetailPage` kullanan standart medya detay sayfaları.

## İstek

Fragman, açılış ve kapanış videoları hızlıca açılıp kapatıldığında eski oynatıcı veya yükleme işlemi yeni videonun başlamasını engellemesin. Bu videolar, resim galerisinden ayrı bir **Videolar** sekmesinde toplansın.

## Yapılanlar

- [x] Resimler sekmesinin yanına yerelleştirilmiş **Videolar** sekmesi eklendi (Türkçe/İngilizce).
- [x] Fragman ile açılış/kapanış videoları Bilgi sekmesinden Videolar sekmesine taşındı.
- [x] Fragman/OP/ED arasında tek bir etkin inline oynatıcı kullanılıyor; video kapatılınca veya seçili sekmeden ayrılınca oynatıcı bileşeni hemen kaldırılıyor.
- [x] Kapatılan veya değiştirilen video için Compose coroutine iptali artık normal bir hata/MPV fallback'i olarak değerlendirilmiyor.
- [x] YouTube çıkarma istekleri iptal edilebilir OkHttp çağrıları kullanıyor; paylaşılan extractor çalışma süresince config/HTTP kaynaklarını yeniden kullanıyor.
- [x] Eski oynatıcıdan gecikmeli gelen callback'lerin yeni oynatıcı state'ini değiştirmesi engellendi.
- [x] Videolar sekmesi eklenirken karakterler, ekip, öneriler, ilişkiler, grafikler, yorumlar ve bölüm verilerinin eski ViewModel indeksleri korunması için UI-indeksi eşlemesi eklendi.
- [x] Sekme indeks eşlemesi için birim testi eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/trailer/InAppYouTubeExtractor.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailTabState.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/components/SharedTrailerOverlay.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/DetailTabIndexMappingTest.kt`

## Kontroller

- [x] Değişen Kotlin dosyaları için sözdizimi kontrolü.
- [x] String kaynaklarının XML ayrıştırma kontrolü.
- [x] `git diff --check`.
- [ ] `:app:compileDebugKotlin` — çalıştırılamadı: ortamda `java`/`JAVA_HOME` yok.
- [ ] Gerçek cihazda hızlı aç/kapat ve video değiştir testleri.
