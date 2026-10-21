# Kök neden ve düzeltme — Detay sayfası küçük video oynatıcısı

**Tarih:** 2026-10-21  
**Kapsam:** Detay sayfası fragmanları, açılış/kapanış müzik videoları ve bunların küçük oynatıcı/YouTube oynatma hattı.

## Belirti

Video yüzeyi açılıyor ancak görüntü siyah kalıyor ve süre `00:00 / 00:00` gösteriyor. Sorun tek bir video türüne ait değil: YouTube fragmanları akış çıkarımı kullanırken AnimeThemes açılış/kapanışları doğrudan medya URL'si kullanabiliyor; her ikisi de aynı inline motor seçimine ve hazır olma takibine dayanıyor.

## İncelenen çağrı yolları

- **YouTube fragmanı/tema:** `KitsugiYouTubePlayer` → `InAppYouTubeExtractor` → `TrailerPlaybackSource` → kullanıcının seçtiği MPV veya Media3 motoru.
- **Doğrudan tema dosyası (ör. AnimeThemes):** `ThemePlayerContainer` → MPV veya Media3.
- **Fragmanın detay kartındaki ayrı overlay'i:** `SharedTrailerOverlay` → Media3 `ExoPlayer`.

Ayar deposundaki varsayılan tercih MPV olduğundan yalnızca Media3'ün HLS MIME veya data-source düzeltmesi yapmak yeterli değildi.

## Kod incelemesinde doğrulanan kök nedenler

1. **Küçük oynatıcı motoruna istek başlığı taşınmıyordu.** YouTube ve doğrudan OP/ED yolları `prepare()` çağrısında boş başlıklarla açılıyordu. Özellikle varsayılan MPV rotasında Media3'ün YouTube data-source/header katmanı hiç çalışmadığından YouTube `Referer`/`Origin` ve uygun User-Agent garantisi yoktu. Doğrudan video dosyalarında da kaynak başlığı/origin aktarılmıyordu.
2. **Media3'ün YouTube özel data source'u yalnızca ayrı video+ses akışlı kaynakta seçiliyordu.** Extractor'ın tercih ettiği tek URL'li YouTube HLS kaynağı genel data-source yoluna düşüyor, dolayısıyla YouTube'a özgü başlıklar ve HLS için range politikası uygulanmıyordu.
3. **Extractor yalnızca HLS master playlist varyantlarını kabul ediyordu.** YouTube'un zaten seçilmiş bir media playlist döndürdüğü geçerli durumda `#EXT-X-STREAM-INF` bulunmadığı için HLS reddedilip başka, imzalı ve daha kısa ömürlü bir akışa düşülebiliyordu.
4. **Detay kartındaki ayrı fragman overlay'i tek URL'li kaynakta özel factory'yi atlıyordu.** `setMediaItem()` çağrısı, hemen önce oluşturulan YouTube factory'sini kullanmıyor; ayrıca imzasız uzantısız HLS adresine açık HLS MIME türü verilmiyordu.
5. **Hazır olma zaman aşımı/fallback yoktu.** Ağ veya format hatasında oynatıcı süresiz siyah/`00:00` durumda kalabiliyordu.

Bunlar kaynak kodunda doğrulanan hatalı/eksik yollar. Bu oturumda örnek video URL'sine ait cihaz `logcat`/HTTP yanıtı veya gerçek cihaz bulunmadığından, belirtilen videoda hangi hatanın ilk tetikleyici olduğunu tek bir HTTP 403/decoder hatası seviyesinde ayrıştırmak mümkün değil; yapılan düzeltme ortak hattaki tüm doğrulanmış açıkları kapatıyor.

## Yapılan düzeltmeler

- `YouTubeRequestPolicy` eklendi: YouTube/Googlevideo hostlarını sınırları doğru kontrol ederek tanıyor; YouTube header setini ve doğrudan URL için User-Agent/origin başlıklarını ortaklaştırıyor.
- Küçük YouTube ve OP/ED oynatıcıları başlıkları seçili motora ve tam ekran çağrısına iletiyor.
- Media3 YouTube tek ve çift akışlarında ortak özel data source kullanıyor; HLS manifest/segmentlerinde query-range chunking kapanıyor, progressive Googlevideo akışlarında korunuyor.
- HLS tespiti Googlevideo `hls_playlist` ve `hls_variant` biçimlerini kapsıyor. Parser master playlist, media playlist, BOM ve göreli varyant URL'lerini destekliyor; kalite eşitliğinde genişlik manifest nesnesinde tutulup gerçek aday genişlikleriyle karşılaştırılıyor.
- `SharedTrailerOverlay`, tek URL için de özel factory üzerinden MediaSource kuruyor ve YouTube HLS'e MIME türünü açıkça veriyor.
- Küçük oynatıcılar `READY`/`ENDED` bekliyor; 18 saniyede veya oynatma hatasında diğer dahili motora bir kez geçiyor. İkinci deneme de başarısızsa yeniden dene ve kaynağı aç eylemleri gösteriliyor.
- HLS tespiti, request policy, parser, genişlik tie-break'i ve motor yardımcıları için birim testleri eklendi/genişletildi.

## Doğrulama

- `git diff --check`: başarılı (son değişikliklerden sonra tekrar çalıştırıldı).
- `./gradlew :app:testDebugUnitTest --no-daemon`: **çalıştırılamadı**; bu ortamda `JAVA_HOME` tanımlı değil ve PATH'te `java` bulunmuyor.
- Gerçek YouTube/AnimeThemes ağ testi ve cihaz testi: bu oturumda yapılamadı. Testler derlenip, en az bir YouTube HLS fragmanı ve bir doğrudan OP/ED URL'si cihazda denenerek doğrulanmalı.
