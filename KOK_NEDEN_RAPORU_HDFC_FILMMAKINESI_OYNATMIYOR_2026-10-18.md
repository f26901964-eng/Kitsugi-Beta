# KÖK NEDEN RAPORU — HDFilmCehennemi & FilmMakinesi "video verisi oynatılmıyor / hata veriyor"

**Tarih:** 2026-10-18
**Uygulama dalı:** `arena/084e7315-kitsugi-beta` (taban: `9a70fee`, v2.4.304)
**Eklenti deposu:** `f26901964-eng/Kitsugi-Plugins`
**Şikâyet:** Ne HDFilmCehennemi'den ne FilmMakinesi'nden gelen video verisi oynatılmıyor; oynatıcı hata veriyor.

---

## 0. Özet

İki **ayrı** kök neden üst üste binmişti. Birincisi eklenti deposunda, ikincisi uygulamada:

| # | Katman | Kök neden | Belirti |
|---|---|---|---|
| **KN-A** | Eklenti (`.cs3`) | `linkTypeFor()` else dalında `INFER_TYPE!!` → **her zaman NullPointerException** | Eklenti link üretemiyor → 0 kaynak / oynatıcı hata |
| **KN-B** | Uygulama (`CsStreamRunner`) | Kurtarma yolları aday adresi **provider origin'i** ile doğruluyor; CDN ise **embed origin'i** istiyor | Bulunan akış "ölü" sayılıp eleniyor → yine 0 kaynak |

KN-A asıl nedendir. KN-B, KN-A yüzünden devreye geren yedeğin de çalışmamasının nedenidir —
yani uygulama "kendi kendini kurtarma" şansını da kaybediyordu.

---

## 1. KN-A — `INFER_TYPE!!` her zaman NPE fırlatır

### Kanıt

Eklenti kodu (HDFilmCehennemi v53, FilmMakinesi/CloseLoad v63 — **bugün 20:50'de yayınlanan sürümler**):

```kotlin
private fun linkTypeFor(url: String): ExtractorLinkType {
    val lower = url.lowercase().substringBefore("?")
    return when {
        lower.contains(".m3u8") || lower.contains("master.txt") || lower.contains(".m3u") -> ExtractorLinkType.M3U8
        lower.contains(".mp4") || lower.contains(".mkv") || ... -> ExtractorLinkType.VIDEO
        else -> INFER_TYPE!!          // ← BURADA
    }
}
```

Uygulamanın paketlediği CloudStream kütüphanesi
(`repo/com/lagradost/api/library-android/1.0.2-local`, kaynak: `ExtractorApi.kt:447`):

```kotlin
val INFER_TYPE: ExtractorLinkType? = null
```

`INFER_TYPE` **null'dır**. Dolayısıyla `INFER_TYPE!!` o dala düşen her adreste
`NullPointerException` fırlatır. Kütüphanenin kendi extractor'ları bunu doğru kullanır
(`type = INFER_TYPE`, nullable bağlam, `!!` yok) — hata yalnızca bu iki eklentide.

### Neden "oynatmıyor" olarak görünüyor

NPE, eklentinin kendi `try/catch` bloğu tarafından yutuluyor:

```kotlin
} catch (e: Exception) {
    Log.e("Kekik_${this.name}", "Hata: ${e.message}")   // NPE burada kaybolur
}
```

Sonuç: `callback` **hiç çağrılmıyor**. Uygulama tarafında `loadLinks` normal dönüyor ama
`streams` boş kalıyor → kaynak kartı yok / oynatıcı hata veriyor.

### Hangi adresler bu dala düşüyor

Uzantısı `.m3u8` / `master.txt` / `.m3u` / `.mp4` / `.mkv` / `.webm` / `.mov` / `.avi`
**olmayan her gerçek akış**. Bunlar tam olarak bu iki sitenin ürettiği adresler:

- `master.txt` çözülüp içinden seçilen **varyant** adresleri
- JSON gövdeden (`file`/`src`/`url`/`hls`) çözülen uzantısız CDN adresleri
- token'lı `/play`, `/stream`, `index-v1-a1` tarzı uçlar

Yani v53/v63'teki "kör M3U8 düzeltmesi" tam da bu adresleri üretmeye başladığı için
`else` dalı artık **sık sık** çalışıyordu — hata bu sürümlerde görünür hâle geldi.

### Düzeltme (eklenti deposu)

`linkTypeFor` artık `ExtractorLinkType?` döndürüyor ve uzantısız adreste `null` veriyor:

```kotlin
private fun linkTypeFor(url: String): ExtractorLinkType? {
    val lower = url.lowercase().substringBefore('?')
    return when {
        lower.contains(".m3u8") || lower.contains("master.txt") ||
            lower.contains("playlist.txt") || lower.contains(".m3u") -> ExtractorLinkType.M3U8
        lower.endsWith(".mpd") -> ExtractorLinkType.DASH
        lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") ||
            lower.contains(".mov") || lower.contains(".avi") || lower.contains(".m4v") -> ExtractorLinkType.VIDEO
        else -> null      // türü uydurma; newExtractorLink(type = null) adresten çıkarır
    }
}
```

`newExtractorLink(type: ExtractorLinkType? = null)` kütüphanede zaten adres tabanlı
çıkarım yapıyor, bu yüzden `null` güvenli ve doğru davranış.

### İkincil hata — `contains(".ts")` geçerli adresleri eliyordu

HDFilmCehennemi'de çöp-adres filtresi şöyleydi:

```kotlin
if (norm.startsWith("httpshttp://") || norm.contains(".ts")) { ... elendi ... }
```

`".ts"` alt dizesi **`master.txt`**, `tracks.txt`, `playlists` gibi **geçerli** adlarda da
geçer. Yani çalışan kaynaklar sessizce eleniyordu. FilmMakinesi ile aynı davranışa çekildi:
sorgu/fragman atıldıktan sonra gerçek dosya uzantısı kontrol ediliyor
(`.ts` / `.m4s` / `.cmfv` / `.cmfa`) ve çift şema önekleri tek yardımcıda toplanıyor:

```kotlin
private fun isGarbageStreamUrl(url: String?): Boolean
private fun isFragmentUrl(url: String): Boolean
```

---

## 2. KN-B — Uygulama, kurtarma yolunda yanlış origin gönderiyordu

FilmMakinesi eklentisinin kendi kodundaki kayıt bunu açıkça belgeliyor:

> `// ! playmix.uno artık master.txt'yi oynatıcıya HAM vermiyor: liste açılıp içindeki`
> `// ! gerçek .m3u8 adresi seçiliyor. Origin embed sunucusuna sabit; eksik kalınca`
> `// ! **uygulama onu playmix.uno yapıyor ve yayın 5004 ile düşüyordu**.`

Uygulamada `Origin` başlığı hiç doldurulmuyordu; `Referer` ise **provider sitesine**
(hdfilmcehennemi.nl / filmmakinesi.to) sabitleniyordu. Oysa bu CDN'ler isteği
**embed sayfasının origin'ine** göre doğruluyor.

Etkilenen üç kurtarma yolu (`CsStreamRunner.resolveEmbedUrl`):

1. Embed adresinin kendisi medya olduğunda (`isMediaContentType`) → `selfHeaders`
2. HTML derin taraması (`EmbedMediaScanner`) → `scannedHeaders`
3. WebView medya yakalayıcı (son çare) → `sniffHeaders`

Ayrıca aday doğrulaması `verifyMediaCandidate` yalnızca `Referer` gönderiyordu;
CDN 403/5004 dönünce **çalışan** aday "ölü" sayılıp listeden düşüyordu.

### Düzeltme (uygulama)

`CsStreamRunner`'a iki yardımcı eklendi:

```kotlin
internal fun originOfUrl(url: String?): String?
internal fun applyEmbedOriginHeaders(
    headers: MutableMap<String, String>,
    embedPageUrl: String?
): MutableMap<String, String>
```

Kural: çağıranın (eklentinin) açıkça verdiği `Origin` / `Referer` **asla ezilmez** —
CDN token'ı o başlıklara bağlı. Yalnızca eksikse embed sayfasının origin'i ile doldurulur.
`verifyMediaCandidate` de artık `Origin` gönderiyor.

---

## 3. Üçüncü katman — bozuk adres oynatıcıya hiç ulaşmasın

Eklentilerin `substringAfter("https")` kısayolu `httpshttp://…` üretiyordu
(v53/v63 bunu düzeltti ama eski eklenti sürümleri hâlâ kurulu olabilir). Böyle bir adres
oynatıcıya verilirse ExoPlayer şemayı çözemeyip anında hata veriyor: kullanıcı
"kaynak görünüyor ama oynatmıyor" diyor.

`CsStreamSanitizer`'a merkezi bir filtre eklendi (adım 0):

```kotlin
fun isBrokenStreamUrl(url: String?): Boolean
```

Kapsam **kasıtlı olarak dar**: çift/bozuk şema (`httpshttp://`, `httpshttps://`,
`httphttps://`), şemasız adres, host'u çözümlenemeyen adres. Çalışan bir kaynağı
yanlışlıkla elemek kullanıcı için daha büyük kayıp olurdu.

Tüm kaynaklar bozuksa orijinal listeye **dönülmez** — boş liste döner, çünkü bozuk adres
oynatıcıyı yine çökertir.

---

## 4. Değişen dosyalar

### Uygulama (`Kitsugi-Beta`, bu dal)

| Dosya | Ne |
|---|---|
| `data/cloudstream/CsStreamRunner.kt` | `originOfUrl`, `applyEmbedOriginHeaders` (KN-B); `verifyMediaCandidate` + 3 kurtarma yoluna `Origin` |
| `data/cloudstream/CsStreamSanitizer.kt` | `isBrokenStreamUrl` + `sanitize` adım 0 (§3) |
| `app/src/test/.../data/cloudstream/CsEmbedOriginHeadersTest.kt` | YENİ — KN-B regresyonu |
| `app/src/test/.../data/cloudstream/CsStreamSanitizerTest.kt` | YENİ — bozuk adres regresyonu |
| `app/src/test/.../core/player/PlayerHlsDetectionTest.kt` | Gerçek rapidrame / playmix / vmeas adresleri eklendi |
| `KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch` | Eklenti düzeltmesi (KN-A) — uygulanmayı bekliyor |
| `KOK_NEDEN_RAPORU_HDFC_FILMMAKINESI_OYNATMIYOR_2026-10-18.md` | Bu dosya |

### Eklenti deposu (`Kitsugi-Plugins`) — **henüz yayınlanmadı**

| Dosya | Ne |
|---|---|
| `HDFilmCehennemi/.../HDFilmCehennemi.kt` | `linkTypeFor` null-safe; `isGarbageStreamUrl` / `isFragmentUrl`; `contains(".ts")` → gerçek uzantı |
| `HDFilmCehennemi/build.gradle.kts` | `version = 53` → `54` |
| `FilmMakinesi/.../CloseLoadExtractor.kt` | Aynı düzeltmeler |
| `FilmMakinesi/build.gradle.kts` | `version = 63` → `64` |

Commit: `ef99469` — yerel dal `arena/084e7315-kitsugi-plugins` içinde hazır.

---

## 5. ⚠️ Senin yapman gereken tek şey

Bu oturumun GitHub token'ı **yalnızca `Kitsugi-Beta` deposuna yazabiliyor**;
`Kitsugi-Plugins`'e push 403 verdi. Eklenti düzeltmesi bu yüzden yayınlanamadı.
Yayınlanmadan bu iki kaynak **çalışmaya başlamaz** — asıl kök neden orada.

```bash
# 1) Eklenti deposunu al
git clone https://github.com/f26901964-eng/Kitsugi-Plugins.git
cd Kitsugi-Plugins && git checkout main

# 2) Yamayı uygula (dosya: Kitsugi-Beta deposunun kökünde)
git am KITSUGI_PLUGINS_HDFC54_FM64_INFER_TYPE_FIX.patch
#    (git am takılırsa: git apply --3way <patch>)

# 3) Yayınla — CI (Derleyici.yml) .cs3'leri üretip builds dalına basar
git push origin main
```

Push sonrası `CloudStream Derleyici` işi otomatik çalışır ve
`builds/HDFilmCehennemi.cs3` (v54) ile `builds/FilmMakinesi.cs3` (v64) yayınlanır,
`builds/plugins.json` sürüm + `fileHash` + `fileSize` ile güncellenir.
İki eklenti de `enabled_plugins.txt` içinde, yani derleme atlanmaz.

Sonra uygulamada: **Eklentiler → güncelleme denetimi** (veya eklentiyi kaldırıp yeniden kur).
Kurulu eklenti v53/v63 ise v54/v64'e yükseltilmeden düzelme görülmez.

### Doğrulama

```bash
adb logcat -s CsStreamRunner CsTrace Kekik_CloseLoad HDCH PlayerMediaSourceFactory
```

Beklenen:
- `HDCH: link » https://…` / `Kekik_CloseLoad: link » https://…` satırları **görünmeli**
  (v53/v63'te NPE yüzünden bu satır hiç yazılmıyordu)
- `Hata: null` (NPE) satırı **görünmemeli**
- `Sonuç: N doğrulanmış stream` içinde N ≥ 1
- `çöp adres elendi: https://…master.txt…` **görünmemeli** (KN-B/§1 ikincil hata)

---

## 6. Doğrulama durumu

- Uygulama tarafı: statik doğrulama (bu sandbox'ta JDK / Android SDK / Gradle yok,
  `apt` deposu ve `api.adoptium.net` erişilemez → derleme yapılamadı).
- Değişen Kotlin dosyalarında parantez/süslü parantez dengesi **orijinal dosyalarla birebir aynı**
  (aynı ölçüm aracı, aynı sapma → yapı bozulmadı).
- Eklenti tarafı: `git am` ile uygulanabilir yama; dengesi aynı yöntemle doğrulandı.
  Gerçek derleme CI'daki `Derleme Testi` işinde yapılır.
- Cihaz testi: **yapılmadı** — §5'teki adımlar gerekiyor.

Senin makinende:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "*CsEmbedOriginHeadersTest*" \
  --tests "*CsStreamSanitizerTest*" \
  --tests "*PlayerHlsDetectionTest*" \
  --tests "*CsStreamRunnerLogicTest*"
```

---

## 7. Bilinen sınırlar / açık riskler

1. **Eklenti yayınlanana kadar düzelme yok.** KN-A eklenti tarafında; uygulama tek başına
   bu iki kaynağı çalıştırmaz. (§5)
2. **Gömülü `assets/plugins/plugins.json` bayat.** HDFilmCehennemi `version 50`,
   FilmMakinesi `version 60` (canlı `builds/plugins.json` şu an 53/63). Uygulama önce
   uzak manifesti okuduğu için normal akışta sorun değil, ama uzak erişilemezse bu bayat
   liste devreye girer. CI v54/v64'ü bastıktan sonra gömülü listenin `version` + `fileHash`
   + `fileSize` alanları senkronlanmalı.
3. **`builds` dalı tek commit tutuyor.** CI `git commit --amend` + `git push --force`
   yapıyor; bu yüzden `builds` geçmişinden "ne zaman derlendi" okunamıyor. Sürüm doğrulaması
   `.cs3` içindeki `manifest.json` → `version` ile yapılmalı.
4. **CDN tarafı değişken.** rapidrame / playmix.uno / closeload token'lı ve imzalı adres
   veriyor; token süresi dolarsa (ör. HDFC'de `e=14400` = 4 saat) aynı adres tekrar
   oynatılmaz. Bu, kaynak değişimi/yeniden çözümleme ile aşılır — ayrı bir iş.
5. **Çoklu varyant.** Eklenti artık bir master listesinden ilk 2-3 varyantı ayrı link
   olarak veriyor (`… - 1`, `… - 2`, `… - 3`). Kullanıcı bunları ayrı kaynak kartı olarak
   görecek; kalite etiketi sıra numarasından türetiliyor (1080/720/480), gerçek ölçüm değil.
