# Kaynak Listesi Bilgi Doğruluğu (Kalite / Dil / Boyut)

> Sorun: Video arama sonuç sayfasındaki rozetler (`400p`, `Altyazılı`, `Dublaj`, `Önbellekte`)
> kaynaktan gelen gerçek veriyi değil, tahminleri gösteriyordu.

## Tespit edilen kök nedenler

| # | Belirti | Kök neden |
|---|---------|-----------|
| 1 | Her kaynakta **"400p"** | CloudStream `Qualities.Unknown.value == 400`. `CsStreamRunner.getQualityString()` bunu `"${quality}p"` ile biçimlendirip "400p" yazıyordu. |
| 2 | Kalite bilinmezken **"1080p (HD)" / "HD" / "720p"** | `parseStreamQuality`, `StreamSorter.parseQualityFromTitle`, `parseStreamTitle` fonksiyonlarının "hiçbir şey bulamazsan şunu yaz" varsayılanları. Ayrıca `"hd"`/`"sd"` gibi belirsiz token'lar 720p/480p sayılıyordu. |
| 3 | Her Türkçe eklentide **"Altyazılı"** | `detectStreamLang` içindeki `isTrAddon` kuralı: "eklenti adı Dizi/Anime içeriyorsa altyazılıdır". |
| 4 | Rastgele **"Dublaj"/"Altyazılı"** | Salt alt-dize eşleşmesi: `text.contains("sub")` → "**Sub**aru", "**Sub**marine"; `text.contains("dub")` → "**Dub**ai". |
| 5 | Torrent olmayan kaynaklarda **"Önbellekte"** | `getCacheState` varsayılanı `CACHED` idi. |
| 6 | Extractor çözemediğinde **"720p" / "HD" (1080)** | `ÖLÜ KANAL` ve ham-URL fallback'lerinde sabit kodlanmış kalite değerleri. |

## Yeni mimari

`data/repository/StreamInfoResolver.kt` — tüm rozet bilgisini **kanıt kaynağıyla birlikte** üretir:

```
StreamInfoOrigin = PROVIDER | MEASURED | FILENAME | NONE
```

Öncelik sırası:

1. **MEASURED** — Kitsugi akışı gerçekten ölçtü (`StreamProbe`)
   * HLS master playlist → `#EXT-X-STREAM-INF ... RESOLUTION=1920x1080` → gerçek varyant çözünürlükleri
   * `#EXT-X-MEDIA:TYPE=AUDIO|SUBTITLES ... LANGUAGE="tr"` → gerçek ses/altyazı dilleri
   * Progressive dosyalar → `HTTP HEAD` `Content-Length` → gerçek dosya boyutu
2. **PROVIDER** — Eklentinin kendi meta verisi
   * CloudStream `ExtractorLink.quality` (yalnızca gerçek çözünürlük değerleri; `Unknown=400` → `null`)
   * CloudStream `DubStatus` (`CsEpisodeMatcher.findDubStatusForEpisodeData`) → `providerAudioKind`
   * `loadLinks` ile gelen gerçek altyazı dosyaları → `CC TR`, `CC EN`
3. **FILENAME** — Dosya/yayın adında **açıkça** yazan bilgi
   * `1080p`, `720 p`, `1920x1080`, `4K`, `UHD`, `FHD`
   * `Türkçe Altyazılı`, `TR Dublaj`, `Dual Audio` (kelime sınırı ile, alt-dize ile değil)
4. **NONE** — Bilgi yok → **rozet basılmaz**; kalite için nötr `Kalite ?` / `Oto (HLS)` gösterilir.

Ölçülmüş/sağlayıcı kaynaklı rozetler arayüzde `✓` ile işaretlenir.

## Davranış değişiklikleri

* `StreamSource.quality` / `qualityValue` artık **bilinmiyorsa `null`**'dır (önceden uydurma değer alıyordu).
* `StreamSorter.parseQualityFromTitle()` ve `parseQualityValue()` nullable döner.
* `getCacheState()` varsayılanı `CACHED` değil `P2P`'dir ve önbellek rozeti yalnızca torrent akışlarında gösterilir.
* `detectStreamLang()` yalnızca kanıt varsa bir tür döndürür; aksi halde `UNKNOWN` (rozetsiz).
* Filtre çipleri (`Altyazı`, `Dublaj`, `1080p`, `720p`) de aynı kanıta dayalı bilgiyi kullanır.

## Testler

`app/src/test/java/com/kitsugi/animelist/data/repository/StreamInfoResolverTest.kt`

* `Qualities.Unknown (400)` → kalite yok
* "Doraemon (2005) 001~400" → kalite yok (bölüm numarası kaliteyle karıştırılmaz)
* "Subaru" / "Dubai" → dil kanıtı sayılmaz
* Türkçe eklenti olması tek başına "Altyazılı" demek değildir
* Ölçülen HLS varyantları sağlayıcı bilgisini ezer

## Notlar / sonraki adım

Ekran görüntülerinde görülen **"RecTV → Stand By Me Doraemon 2"** gibi vakalar bir *eşleştirme*
(title matching) sorunudur, bilgi doğruluğu sorunu değildir; `CsTitleMatcher` tarafında ayrıca
ele alınmalıdır.
