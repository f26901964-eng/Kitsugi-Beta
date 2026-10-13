# PLAN TASK — Detay Sayfalarında Ortak Yumuşak Animasyonlar

**Tarih:** 2026-10-13  
**Durum:** Uygulandı

## Amaç

Detay sayfalarındaki video önizlemeleri, genişleyen listeler ve kaydırmayla görünen mini üst çubukların aniden belirmesini önlemek. Mevcut renk, ölçü, yerleşim ve bileşen görünümünü koruyarak bu hareketleri ortak bir animasyon mantığında toplamak.

## Kapsam ve uygulama

- `KitsugiAnimations.kt` içinde ortak detay hareket token'ları ve yardımcıları eklendi: içerik aç/kapa geçişi, yükseklik değişimi ve sticky mini başlık geçişi.
- Video detay sekmesindeki fragman ve açılış/kapanış listeleri aşağı doğru yumuşak genişleyip daralacak şekilde düzenlendi. “Daha fazla” ile gelen satırlar da animasyonla görünür.
- Oynatıcı satırı kapanırken oynatıcı motoru hemen bırakılır; yalnızca satırın yerleşim yüksekliği yumuşakça küçülür.
- Karakter, ekip/seslendiren, medya ve stüdyo detaylarındaki kaydırma mini başlıkları ortak fade/slide/expand hareketini kullanır. LazyColumn eşiklerinde görünürlüğün gidip gelmesini önlemek için ortak eşik mantığı kullanılır.
- Wiki açılır bölümleri, sezon akordeonları, uzun açıklamalar ve etiket kartları aynı hareket token'larıyla uyumlu hale getirildi.

## Kabul kriterleri

- Video oynatıcı alanı ve açılır listeler aniden değil, akıcı bir yükseklik/fade geçişiyle görünür ve kaybolur.
- Karakter ve ekip/seslendiren detay sayfalarındaki mini üst çubuk kaydırma sırasında yumuşakça belirir/kaybolur.
- Son durumdaki bileşen görünümü ve yerleşimi korunur; değişiklik hareket davranışıyla sınırlıdır.
- Hareket süreleri tek bir ortak noktadan ayarlanabilir.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiAnimations.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailEpisodesTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailInfoSection.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StudioDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/wiki/CharacterWikiTabContent.kt`
- `PLAN_TASK_DETAY_SMOOTH_ANIMASYON_2026-10-13.md`

## Doğrulama

- `git diff --check`: başarılı.
- Gradle derlemesi çalıştırılamadı; sandbox ortamında Java/JAVA_HOME bulunmuyor.
