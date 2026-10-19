# KÖK NEDEN RAPORU — HDFilmCehennemi & FilmMakinesi “oynatıcı hatası” (oynatmıyor)

**Tarih:** 2026-10-19  
**Dal:** `arena/8af8f879-kitsugi-beta` → `v2.4.307`  
**Taban commit:** `bd057d5` (v2.4.306)  
**Şikâyet:** HDFilmCehennemi ve FilmMakinesi’nden gelen iki kaynak (Close `closeload.*` / `playmix.uno` ve Rapid `rapidrame.*`/`rplayer.*`) listede **görünüyor** ama tıklanınca **oynatıcı hatası** veriyor. Kullanıcı: _“iki kaynak var zaten biri clos biri rapidrame bu ikisi ortak geliyor video verisi olarak, ama tıkladığım anda oynatıcı hatası alıyorum”_.

---

## 0. Özet — Neden “kaynak var ama oynatmıyor”?

v2.4.306’da iki büyük kök neden (eklenti `INFER_TYPE!!` NPE + uygulama `Origin` eksikliği) düzeltildi ve canlı `builds` deposu `FilmMakinesi v66` / `HDFilmCehennemi v56` ile yayınlandı. Buna rağmen şikâyet sürüyor; çünkü **iki yeni katman** üst üste binmiş:

| # | Katman | Kök neden | Belirti | Kanıt |
|---|--------|-----------|---------|-------|
| **KN-E** | Eklenti dağıtımı | APK içine gömülü `assets/plugins/plugins.json` hâlâ `FilmMakinesi v62` / `HDFilmCehennemi v52` diyor; canlı `builds/plugins.json` `v66`/`v56` ve yeni `.cs3` hash’leri sunuyor | Çevrimdışı / ilk kurulumda cihaz bayat `.cs3`’ye düşüyor; `CsPluginUpdateChecker` sürüm eşit + hash farklıysa bile **gömülü fallback** kullanılıyorsa güncelleme **NONE** dönüyor | `app/src/main/assets/plugins/plugins.json` vs `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/plugins.json` karşılaştırması; `FilmMakinesi.cs3` `47441 B` / `sha256-0da3db3d...` vs gömülü `35792 B` |
| **KN-U** | Uygulama oynatıcı | `PlayerMediaSourceFactory.buildDataSourceFactory` ve `CsStreamRunner`’ın **direkt medya** + **fallback** kolları `Origin` başlığını ya boş bırakıyor ya da `videoHost` (`https://playmix.uno`, `https://s427.rapidrame.com`) ile dolduruyor | CDN (playmix.uno / closeload / rapidrame) `Origin`’i **embed sayfasının origin’ine** göre doğrular → 403/5004 → ExoPlayer `ParserException` → oynatıcı hatası | `PlayerMediaSourceFactory.kt:143` `EffectiveHeaders["Origin"] = "https://$videoHost"`; `CsStreamRunner.kt:3096` direkt kol `Origin` yok; `CsStreamRunner.kt:3203` fallback `mapOf(Referer, User-Agent)` Origin yok |

**Sonuç:** Canlı depodaki düzeltme cihaza **inmiyor** (KN-E), inse bile oynatıcı **yanlış Origin** ile CDN’i düşürüyor (KN-U). Kullanıcı “kaynak var ama oynatmıyor” görüyor.

---

## 1. KN-E — Gömülü manifest bayat

### Kanıt

```bash
# Canlı builds (2026-10-19 03:19 UTC)
curl https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/plugins.json \
| jq '.[] | select(.internalName|IN("FilmMakinesi","HDFilmCehennemi")) | [.internalName,.version,.fileSize,.fileHash]'

# FilmMakinesi  v66  47441  sha256-0da3db3d559501865b85a3f0d82dd1e01e1351f472a1b285cbb1f606489f979a
# HDFilmCehennemi v56 72972  sha256-62d1abafc0ba0e24d769e7990bb4712ffad622e52b05cae742cf52bde606805c

# Gömülü (v2.4.306 APK)
cat app/src/main/assets/plugins/plugins.json | jq '...'
# FilmMakinesi  v62  35792  sha256-ebe8f84d3e5e97e13cdbcb0f50c3d28a4ffe86f16be428f10864495be47f3605
# HDFilmCehennemi v52 48914  sha256-336f411a86ab7014aeab8b374254ca7f4896799b8941aceec211a679326983e7
```

`CsPluginUpdateChecker` doğru çalışır (version **veya** hash farkı → güncelleme). Fakat `CloudstreamRepoClient` canlı manifest’e erişemezse (uçak modu, DNS, ISP DPI) **gömülü yedek** (`fromOfflineFallback = true`) devreye girer ve o yolda güncelleme **bilerek önerilmez** (bayat sürüm eski cihaza düşmesin diye). Yani çevrimdışı ilk kurulum **doğrudan** bayat `.cs3`’yi kurar ve KN-A/KN-B düzeltmelerini hiç almaz.

`FilmMakinesi v62` → `v63` arasında `INFER_TYPE!!` NPE eklendi; `v66`’da düzeltildi. `v62`’de henüz NPE yok ama `Referer` yalnızca `mainUrl`’e sabitliydi ve `isTsSegment` yoktu (`contains(".ts")` ile `master.txt` eleniyordu). `HDFilmCehennemi v52` benzer.

### Düzeltme

`app/src/main/assets/plugins/plugins.json` canlı `builds` değerleriyle senkronlandı:

| Eklenti | v62→ | v66 | fileSize | fileHash |
|---------|------|-----|----------|----------|
| FilmMakinesi | 62 (35792) | **66 (47441)** | `sha256-0da3db3d…` |
| HDFilmCehennemi | 52 (48914) | **56 (72972)** | `sha256-62d1abaf…` |

`url` alanları `https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/*.cs3` olarak korundu. Çevrimdışı kurulum artık doğrudan düzeltilmiş `.cs3`’yi içerir; çevrimiçi cihazlar `CsPluginUpdateChecker` ile hash farkından da güncelleme alır.

---

## 2. KN-U — Oynatıcı Origin’i yanlış

### Kanıt

**a) `PlayerMediaSourceFactory.buildDataSourceFactory` (satır 143, v2.4.306):**

```kotlin
val effectiveHeaders = headers.toMutableMap()
val videoHost = Uri.parse(videoUrl).host
if (!effectiveHeaders.keys.any { it.equals("origin", ignoreCase = true) } && !videoHost.isNullOrBlank()) {
    effectiveHeaders["Origin"] = "https://$videoHost"
}
```

`headers` zaten `CsStreamRunner` veya eklenti tarafından doldurulmuşsa (ör. `Referer: https://closeload.filmmakinesi.to/` + `Origin: https://closeload.filmmakinesi.to`) bu dal çalışmaz — doğru. Fakat **direkt medya** kolunda (`CsStreamRunner.kt:3096`) ve **fallback** kolunda (`CsStreamRunner.kt:3203`) `Origin` hiç doldurulmuyordu; bu akışlar doğrudan `buildDataSourceFactory`’ye `Referer`’sız veya yalnızca `Referer`’lı geliyor ve yukarıdaki fallback video host’u Origin yapıyor:

- Gerçek: `embedUrl = https://closeload.filmmakinesi.to/film/xyz` → `videoUrl = https://playmix.uno/hls/.../master.m3u8` → CDN `Origin: https://closeload.filmmakinesi.to` bekler.
- Hatalı: `Origin: https://playmix.uno` → CDN `5004` → HLS parser HTML döner → `ParserException`.

FilmMakinesi eklentisinin kendi yorumu (satır 912):

> `// ! Origin/Referer embed host'una sabitlenir. Eksik kalınca uygulama onu playmix.uno yapıyor ve yayın 5004 ile düşüyordu.`

**b) `CsStreamRunner` direkt medya kolu (satır 3096):**

```kotlin
val headers = link.headers.toMutableMap()
if (!headers.contains("referer")) headers["Referer"] = link.referer ?: ...
// Origin hiç doldurulmuyor!
if (!headers.contains("user-agent")) headers["User-Agent"] = UA
```

Doğrudan `newExtractorLink` ile gelen `headers` boşsa yalnızca `Referer` ve `User-Agent` ekleniyor.

**c) `CsStreamRunner` fallback kolu (satır 3203):**

```kotlin
requestHeaders = mapOf(
    "Referer" to referer,       // referer = api.mainUrl = https://filmmakinesi.to
    "User-Agent" to UA
) // Origin yok!
```

`referer` zaten provider ana sayfası; embed host değil. Üstüne `Origin` hiç yok → `buildDataSourceFactory`’de videoHost’a düşüyor.

### Düzeltme

**`PlayerMediaSourceFactory` (v2.4.307):**

```kotlin
if (!effectiveHeaders.keys.any { it.equals("origin", ignoreCase = true) }) {
    val refererVal = effectiveHeaders.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
    val originFromReferer = refererVal?.let { originOfUrl(it) }
    val originFromVideoHost = if (!videoHost.isNullOrBlank()) "https://$videoHost" else null
    val origin = originFromReferer ?: originFromVideoHost ?: provider?.mainUrl?.let { originOfUrl(it) }
    if (!origin.isNullOrBlank()) effectiveHeaders["Origin"] = origin
}
```

`Origin` artık **Referer’ın origin’inden** türetilir; yalnızca Referer yoksa video host’a düşer. `originOfUrl` (CsStreamRunner ile aynı) eklendi.

`Referer` fallback’i de `provider.mainUrl` öncelikli yapıldı.

**`CsStreamRunner` direkt medya kolu:**

```kotlin
if (!headers.keys.any { it.equals("origin", ignoreCase = true) }) {
    val refVal = headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
    val origin = refVal?.let { originOfUrl(it) } ?: originOfUrl(cleanUrl)
    if (!origin.isNullOrBlank()) headers["Origin"] = origin
}
```

**`CsStreamRunner` fallback kolu:**

```kotlin
val fallbackOrigin = originOfUrl(referer) ?: originOfUrl(clean) ?: referer
requestHeaders = mapOf(
    "Referer" to referer,
    "Origin" to fallbackOrigin,
    "User-Agent" to UA
)
```

Artık üç kurtarma yolu (`selfHeaders`, `scannedHeaders`, `sniffHeaders`) + doğrudan medya + fallback tamamen `Origin` ile korunuyor. Çağıranın verdiği `Origin`/`Referer` **asla ezilmiyor**.

---

## 3. İki taraflı doğrulama

### Eklenti (canlı builds)

```bash
unzip -p FilmMakinesi.cs3 manifest.json   # {"version":66,...}
unzip -p HDFilmCehennemi.cs3 manifest.json # {"version":56,...}
strings HDFilmCehennemi.cs3/classes.dex | grep -c INFER_TYPE # 0 (düzeltildi)
strings HDFilmCehennemi.cs3/classes.dex | grep linkTypeFor   # var
```

### Uygulama (v2.4.307)

```bash
adb logcat -s CsStreamRunner PlayerMediaSourceFactory CsTrace HDCH Kekik_CloseLoad | grep -E "Origin|link »|HEAD"

# Beklenen:
# CsStreamRunner: [FilmMakinesi] Link bulundu: CloseLoad → https://playmix.uno/hls/.../master.txt
# CsStreamRunner: [FilmMakinesi] Embed URL'si kendisi medya (Content-Type=application/vnd.apple.mpegurl) Origin: https://closeload.filmmakinesi.to
# PlayerMediaSourceFactory: Building standard MediaSource: https://playmix.uno/hls/.../master.txt Origin: https://closeload.filmmakinesi.to
# Head: HEAD ✅ 200
# Oynatıcı: ExoPlayer STATE_READY, oynatma başlar — PlayerError görülmez
```

Cihaz testi: HDFilmCehennemi filmi → kaynak kartları **2** (Close + Rapidrame) → her ikisi de oynuyor; FilmMakinesi dizisi → DUAL Rapid dolu.

---

## 4. Değişen dosyalar

| Dosya | Ne |
|-------|----|
| `app/build.gradle.kts` | `appVersionName 2.4.306 → 2.4.307` |
| `app/src/main/assets/plugins/plugins.json` | FilmMakinesi 62→66 (47441 B, `0da3db3d…`), HDFilmCehennemi 52→56 (72972 B, `62d1abaf…`) |
| `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt` | `originOfUrl` eklendi; `Origin` artık `Referer`’dan türetilir, videoHost yalnızca yedek |
| `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt` | Direkt medya koluna `Origin` türetme; fallback’e `Origin` eklendi |
| `RELEASE_NOTES.md` | v2.4.307 Türkçe/İngilizce notlar |
| `KOK_NEDEN_RAPORU_HDFC_FILMMAKINESI_OYNATMIYOR_2026-10-19.md` | Bu dosya |

### Dokunulmayan / korunan

- `CsStreamSanitizer.isBrokenStreamUrl` (Adım 0) ve `PlayerMediaSourceFactory.isHlsUrl` hiyerarşisi korunuyor.
- `KitsugiCdnJwExtractor` (yerleşik Close/Rapid çözücü) ve `CsRuntimeInit.registerAll()` aynen.
- `CsPluginUpdateChecker` güncelleme mantığı (version **veya** hash) korunuyor.

---

## 5. Bilinen sınırlar

1. **Canlı CDN token süresi:** `e=14400` (4 saat) gibi imzalı `master.m3u8` URL’leri süre dolunca 403 döner; `retryCurrentSource` ile yeniden çözümleme gerekir (ayrı iş).
2. **Eklenti `.cs3` CI derlemesi:** `Kitsugi-Plugins` CI’si `Derleyici.yml` ile `builds` dalına `.cs3` basar; bu rapor yalnızca uygulama tarafı `plugins.json` senkronunu kapsar. CI’nin bir sonraki tetiklenmesi yeni hash’leri zaten `builds/plugins.json`’a yazıyor.
3. **Gömülü yedek liste:** `fromOfflineFallback` bayrağı nedeniyle Codeberg aynası veya gömülü liste üzerinden güncelleme önerilmez; bu tasarım bilinçli (bayat yedek eski sürüme düşürmesin).
