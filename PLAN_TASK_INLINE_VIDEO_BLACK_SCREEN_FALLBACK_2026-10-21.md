# Plan / Task — Detay sayfası küçük video oynatıcısında siyah ekran ve 00:00

**Tarih:** 2026-10-21  
**Kapsam:** Detay sayfasındaki fragmanlar ve açılış/kapanış videolarının küçük (inline) oynatıcısı.

## Belirti

Detay sayfasındaki küçük oynatıcı siyah ekranda kalabiliyor ve sayaç `00:00 / 00:00` gösteriyor. Aynı kaynak tam ekran oynatıcıda açılınca oynayabiliyor.

## Kod incelemesinde bulunan fark ve açık

- YouTube kaynaklı küçük oynatıcı, uygulama ayarındaki motor tercihini izliyordu; varsayılan `MPV` ayarında önce MPV deneniyordu. Tam ekran oynatıcı ise Media3 ile başlıyordu. Bu, iki görünümün aynı çıkarılmış YouTube akışını farklı oynatma motorlarına vermesine yol açıyordu.
- Küçük oynatıcıların başlatma bekçisi yalnızca `PlayerEngine.State.READY` durumunu bekliyordu. `READY`, kaynağın hazırlandığını belirtir; oynatımın ilerlediğini kanıtlamaz. Oynatıcı `READY` olup konum `0`'da kalırsa zaman aşımı/yedek motor yolu devreye girmeyebilir.
- Ekran görüntüsü ve kaynak kodu bu iki riski gösteriyor; ancak cihaz logcat'i ve gerçek cihaz testi olmadan, kullanıcının örnek videosunda hangisinin ilk tetikleyici olduğu kesinleştirilemez.

## Yapılan değişiklikler

1. YouTube inline akışını tam ekranla aynı motor önceliğine getirdim: önce Media3, hata/ilerleme zaman aşımında MPV yedeği.
2. Hem YouTube küçük oynatıcıya hem doğrudan tema videosu küçük oynatıcısına gerçek ilerleme bekçisi ekledim. `READY` tek başına başarı sayılmıyor; akış konumu 18 saniye içinde ilerlemezse diğer motor bir kez deneniyor.
3. Uygulama arka plana geçtiğinde bekçi yanlış yedek geçişi yapmıyor.
4. Motor önceliği ve `READY` olup ilerlemeyen akış durumları için birim testleri ekledim.

## Dokunulan dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupport.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupportTest.kt`

## Doğrulama / kalan iş

- `git diff --check`: başarılı.
- `./gradlew :app:testDebugUnitTest --no-daemon`: bu ortamda `JAVA_HOME` ve `java` olmadığı için başlatılamadı.
- Gerçek YouTube/AnimeThemes akışıyla cihaz testi ve logcat doğrulaması bu ortamda mevcut değil; bu nedenle kullanıcı cihazında oynatma doğrulaması yapılmış gibi raporlanmıyor.
