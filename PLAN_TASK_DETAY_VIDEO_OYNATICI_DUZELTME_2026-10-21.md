# Plan / Task — Detay Sayfası Video Oynatıcısı Düzeltmesi

**Tarih:** 2026-10-21  
**Dal:** `arena/878fbfc5-kitsugi-beta`  
**Kapsam:** Detay sayfalarındaki fragman ve açılış/kapanış müzik videolarının uygulama içi oynatılması.

## Kullanıcı belirtisi

Detay sayfasındaki video satırına dokunulduğunda oynatıcı açılıyor ancak video başlamıyor; süre `00:00` kalıyor. Daha önce aynı videolar uygulama içinde hızlıca oynuyordu. Talep, oynatmayı geri getirmek ve video bölümündeki durum/yeniden deneme kullanımını iyileştirmek.

## İncelenen akış

- `KitsugiMediaVideosTab` / `ThemePlayerContainer` video URL'sini alıyor.
- YouTube URL'leri `KitsugiYouTubePlayer` üzerinden `InAppYouTubeExtractor` ile akış URL'sine çevriliyor.
- YouTube video/ses kaynakları Media3 `PlayerMediaSourceFactory` ve `YoutubeChunkedDataSourceFactory` üzerinden oynatılıyor.

## Değişiklikler

- [x] `YoutubeChunkedDataSourceFactory`: video URL'si için tarayıcı benzeri User-Agent ve YouTube Referer başlıklarını ekle; YouTube akış isteğinin bu başlıklar olmadan reddedilmesi/throttle edilmesi riskini azalt.
- [x] `YoutubeChunkedDataSourceFactory`: parça URL'sinde yeni `range` parametresi oluşturmadan önce eski `range` parametresini temizle; yinelenen aralık parametresinin ilk parçada kalma/sıkışma oluşturma riskini gider.
- [x] `KitsugiYouTubePlayer`: yükleme durumunu metinle anlaşılır hale getir.
- [x] `KitsugiYouTubePlayer`: çıkarım/oynatma kaynağı alınamadığında elle yeniden deneme ve YouTube'da açma eylemlerini sun.
- [x] Oynatıcı çıkarımını yeniden deneme sırasında eski playback source'u temizle.

## Değişen uygulama dosyaları

1. `app/src/main/java/com/kitsugi/animelist/data/trailer/YoutubeChunkedDataSourceFactory.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt`

## Doğrulama durumu

- `git diff --check`: başarılı.
- `./gradlew :app:compileDebugKotlin --no-daemon`: bu ortamda Java/JDK olmadığı (`JAVA_HOME` ayarlı değil ve `java` PATH'te bulunmuyor) için çalıştırılamadı.
- Gerçek cihazda video oynatma testi: yapılmadı; cihaz/Android çalıştırma ortamı bu oturumda mevcut değil.

**Not:** Bu paket kaynak değişikliklerini ve bu plan/task raporunu içerir. Cihazda oynatma doğrulaması ayrıca yapılmalıdır.
