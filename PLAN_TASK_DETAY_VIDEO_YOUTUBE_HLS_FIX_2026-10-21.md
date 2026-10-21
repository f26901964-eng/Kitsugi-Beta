# Plan / Task — Detay videoları YouTube HLS algılama ve küçük oynatıcı kısayolu

**Tarih:** 2026-10-21  
**Dal:** `arena/560f14dc-kitsugi-beta`  
**Kapsam:** Detay sayfası > Videolar sekmesindeki YouTube fragman/açılış/kapanış videoları

## Belirti

Detay sayfasındaki YouTube videoları uygulama içindeki küçük oynatıcıda başlamıyor; kullanıcı ekranında `00:00 / 00:00` görünüyor.

## Kök neden

`InAppYouTubeExtractor` YouTube için zaman zaman `manifest.googlevideo.com/api/manifest/hls_playlist/...` biçimli HLS adresi döndürüyor. Bu imzalı adres `.m3u8` uzantısı taşımıyor. `PlayerMediaSourceFactory.isHlsUrl()` bu biçimi tanımayınca Media3'e HLS MIME türü verilmiyor; HLS playlist'i yanlışlıkla progressive kaynak gibi ele alınabiliyor ve oynatma başlamıyor.

## Değişiklikler

- [x] `PlayerMediaSourceFactory.isHlsUrl`: YouTube `manifest.googlevideo.com` + `hls_playlist` imzalı URL biçimini HLS olarak tanı.
- [x] `PlayerHlsDetectionTest`: YouTube HLS adresinin HLS, aynı hostun DASH manifestinin HLS olmadığını doğrula.
- [x] YouTube extractor: Kullanılabilir HLS varsa ayrı adaptive video/ses akışı yerine HLS'i öncele; HLS yoksa adaptive/progressive yedekleri koru.
- [x] Küçük oynatıcı: çift dokunma ile 10 sn ileri/geri kısayolu ve görsel geri bildirimi.

## Doğrulama

- `git diff --check`: başarılı.
- `./gradlew :app:testDebugUnitTest --no-daemon`: çalıştırılamadı; çalışma ortamında Java/JDK (`JAVA_HOME`/`java`) kurulu değil.
- Gerçek cihaz/YouTube ağ testi bu ortamda yapılamadı; kullanıcı cihazında doğrulanması gerekiyor.

## Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/trailer/InAppYouTubeExtractor.kt`
3. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt`
4. `app/src/test/java/com/kitsugi/animelist/core/player/PlayerHlsDetectionTest.kt`
5. `PLAN_TASK_DETAY_VIDEO_YOUTUBE_HLS_FIX_2026-10-21.md` (bu dosya)
