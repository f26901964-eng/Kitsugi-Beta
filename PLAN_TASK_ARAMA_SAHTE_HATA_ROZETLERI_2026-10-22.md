# PLAN / TASK — Arama Ekranındaki Sahte Kaynak Hata Rozetleri

Tarih: 2026-10-22
Dal: `arena/8b9a84e6-kitsugi-beta`
Sürüm: v2.4.340 (beta)
İlgili rapor: `COZUM_RAPORU_ARAMA_SAHTE_HATA_ROZETLERI_2026-10-22.md`

## İstek ve kapsam

Kullanıcı bildirimi: "kaiju no 8" aramasında AniList, MyAnimeList, Shikimori, Kitsu ve Simkl rafları
sorunsuz doluyor **ama** sonuçların üstünde

- `KTS-COM-1009 · Kayıt bulunamadı (HTTP 404)`
- `KTS-KIT-4001 · Kitsu API adresi değişti`

rozetleri görünüyor. Kullanıcı haklı olarak "Kitsu sorunsuz çalışıyor, bu hatalar niye geliyor?" diyor.

İstenen: rozetlerin kaynağını tespit etmek, yanlış sınıflandırmayı düzeltmek ve hata yoksa rozet
göstermemek.

Kapsam dışı: Arama ekranının görsel/tasarım değişikliği, yeni özellik, kaynak istemcilerinin
(`KitsuExploreClient`, `JikanApiClient` …) sorgu mantığı, hata kodu metinleri (TR/EN string'ler).

## Kök neden özeti

| # | Kök neden | Dosya | Etki |
| --- | --- | --- | --- |
| 1 | Coil görsel yükleyici aynı OkHttp istemcisini kullanıyor; `PlatformFailureInterceptor` her 4xx/5xx'i (poster/kapak/video/altyazı dahil) platform hatası olarak kaydediyor | `KitsugiApplication.kt`, `PlatformFailureInterceptor.kt` | **Asıl neden** — tek bir bozuk poster 404'ü "kaynak çöktü" sanılıyor |
| 2 | `ListPlatform.fromHost()` alan adı son ekiyle eşleşiyor: `media.kitsu.app` → KITSU, `s4.anilist.co` → ANILIST, `image.tmdb.org` → TMDB, `cdn.myanimelist.net` → MAL, `simkl.in` → SIMKL | `ListPlatform.kt` | CDN hataları API hatası gibi görünüyor |
| 3 | Kitsu sınıflandırmasında `t.contains("kitsu.app")` koşulu; ham kayıt her zaman `HTTP 404 · kitsu.app/...` biçiminde olduğu için **her** Kitsu hatası KIT-4001'e düşüyor | `PlatformErrorCatalog.kt` | Yanıltıcı "API adresi değişti" mesajı, 404/429/503 ayrımı kayboluyor |
| 4 | `SearchViewModel` `PlatformFailureRegistry.failuresSince(...)` haritasını süzmeden `sourceErrorCodes`'a yazıyor | `SearchViewModel.kt` | Sonucu gelen kaynak bile "başarısız" görünüyor |
| 5 | Rozet metni yalnızca kodu açıklıyor (`KTS-COM-…`), hangi kaynağa ait olduğu belirsiz | `PlatformErrorCodeChip.kt` | Kullanıcı hangi kaynağın hata verdiğini anlayamıyor |

## Tamamlanan görevler

- [x] `PlatformFailureRegistry.isMediaAssetRequest(host, path)` eklendi:
      bilinen CDN host'ları (`image.tmdb.org`, `api-cdn.myanimelist.net`, `cdn.myanimelist.net`,
      `media.kitsu.app`, `media.kitsu.io`, `simkl.in`, `img.anili.st`, `lain.bgm.tv`),
      ön ekler (`media.`, `img*`, `cdn.`, `static.`, `images.`, `image.`, `assets.`),
      numaralı AniList CDN'i (`^s\d+\.anilist\.co$`) ve statik uzantılar
      (`.jpg .jpeg .png .webp .gif .avif .bmp .svg .ico .mp4 .webm .m3u8 .mpd .vtt .srt .ass`).
      `reportHttp()` bu istekleri **kaydetmiyor**; uzantısız API yolları etkilenmiyor.
- [x] `PlatformFailureInterceptor`: `Accept: image/*` / `video/*` / `audio/*` gönderen istekler
      (Coil) hata kaydına girmiyor — uzantısız CDN adresleri için ek güvence.
- [x] `PlatformErrorCatalog.isKitsuHostMigrationText()` eklendi; KIT-4001 yalnızca gerçek
      taşınma/yönlendirme ifadelerinde (`host changed`, `api adresi değiş`, `adres değiş`,
      `domain değiş`, `moved permanently`, `yönlendir`) **veya** eski host'a ulaşılamamasında
      (`UnknownHost`, `unable to resolve`, `no address associated with hostname`,
      `SSLHandshake`, `certificate`) ve yalnızca `http == null / 301 / 308` iken dönüyor.
- [x] Kitsu hata sırası düzeltildi: 429 → `KIT_4002`, ilişki hatası → `KIT_4004`,
      404 → `COM_1009` (Kayıt bulunamadı), 5xx → `COM_1007` (Sunucu hatası).
- [x] `SearchViewModel.visibleSourceErrorCodes()` eklendi:
      - "Tümü" (raf) aramasında `planAllSourcesForState()` ile planlanan kaynaklardan **sonuç üretmiş**
        olanlar sağlıklı sayılıyor; rozet yalnızca planlandığı hâlde **boş kalan** kaynak için kalıyor.
      - Tek kaynak sekmesinde rozet yalnızca o kaynağın sonucu hiç yoksa gösteriliyor.
      - Karakter/Personel kapsamlarında sonuç varsa rozet yok.
- [x] `PlatformErrorCodeChip`: opsiyonel `platform` parametresi; rozet `Kitsu · Kitsu hız sınırı`
      biçiminde kaynak adını da yazıyor. `SearchScreen` rozetleri kaynak eşlemesiyle besliyor.
- [x] `PlatformFailureRegistryTest`: 5 regresyon testi (Kitsu 404 → COM_1009; 429/503 ayrımı;
      `UnknownHost(kitsu.io)` → KIT_4001; CDN 404'leri kaydedilmiyor; uzantısız API yolu kaydediliyor).

## Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/core/network/PlatformFailureInterceptor.kt
app/src/main/java/com/kitsugi/animelist/data/platform/PlatformErrorCatalog.kt
app/src/main/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistry.kt
app/src/main/java/com/kitsugi/animelist/ui/components/PlatformErrorCodeChip.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt
app/src/test/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistryTest.kt
```

## Doğrulama

- Beklenen davranış: bütün raflar doluyorsa **hiçbir rozet çıkmaz**.
- Gerçek hata varsa rozet **kaynak adı + doğru kod** ile görünür.
- "Kitsu API adresi değişti" yalnızca host gerçekten çözümlenemediğinde görünür.
- Statik denetim yapıldı (parantez/dil yapısı, çağrı yolları, mevcut test beklentileri).
- **Açık madde:** Bu ortamda JDK/Gradle bulunmadığı için `./gradlew :app:testDebugUnitTest` ve
  derleme çalıştırılamadı; CI'da doğrulanmalı.

## Riskler / notlar

- `isMediaAssetRequest` uzantı listesi geniş: `.json`/`.xml` gibi API yolları bilinçli olarak dışarıda.
  İleride bir API uç noktası medya uzantısıyla biterse (`/manifest.mpd` gibi gerçek API) yanlış
  sınıflandırılabilir; bu durumda host bazlı istisna eklenmeli.
- CDN hataları artık hiç kaydedilmiyor; görsel yükleme hataları için ayrı bir telemetri istenirse
  `reportHttp` yerine ayrı bir sayaç/kanal kullanılmalı (platform hata kodu üretmemeli).
- `Accept` başlığına dayalı filtre, görsel indirmeyi `Accept` başlığı olmadan yapan gelecekteki bir
  çağrıyı kapsamaz; host/uzantı filtresi bu durumu zaten karşılıyor.
