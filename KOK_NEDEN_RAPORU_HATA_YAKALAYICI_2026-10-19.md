# KÖK NEDEN — Eklenti/Oynatıcı “neden düştüğünü göstermiyor” (hata yakalayıcı eksik)

**Tarih:** 2026-10-19  
**Sürüm:** v2.4.308  
**Şikâyet:** “hata alıyorum ama neyden aldığımı bilemiyorum — sebep ne, niye çalışmıyorlar bilmiyorum”

## Tespit

1. **Extractor yutması:** `KitsugiCdnJwExtractor.getUrl` eski hali `catch (e) { Log.e(TAG, "[name] hata: ${e.message}") }` — stacktrace yok, CsTrace yok, URL/host yok. `resolveMasterPlaylist` aynı. Boş HTML / parse edilemeyen master listesi sadece sessiz `null` dönüyordu.
2. **Oynatıcı tek satırı:** `Media3PlayerEngine.onPlayerError` sadece `errorCode + localizedMessage` ile geçiyor, HTTP 403/5004, cause zinciri, URL/host kayboluyor. Kullanıcı ekranda sadece “oynatıcı hatası” görüyor.
3. **ViewModel tek satır:** `setPlayerError(code, msg)` direkt state’e koyuyor, ipucu/host/URL eklenmiyor. `onFatal` de aynı.
4. **UI kopyalanamıyor:** `PlayerErrorView` mesajı seçilemez, kopyala yok.
5. **Filtre uyumsuzluğu:** Kullanıcı `HDCH / Kekik_CloseLoad / Rapidrame` ile filtreliyor ama app yerleşik extractor `KitsugiCdnJwExtractor` tag’i ile yazıyor — filtre boş kalıyor.

## Çözüm (v2.4.308)

- **KitsugiCdnJwExtractor:**
  - `getUrl` içinde boş HTML → `CsTrace.warn` + `mirrorToAliasTags`
  - `realUrl == null` → warn + alias
  - tüm `catch` → `reportExtractorError(stage, url, referer, origin, e)` : Log.e + CsTrace.error + PlayerDiagnostics + alias tag’ler
  - `resolveMasterPlaylist` boş liste / null varyant için warn, catch için aynı rapor
  - Yeni helper `mirrorToAliasTags` : Close → Kekik_CloseLoad+HDCH, Rapid/Rplayer → Rapidrame+HDCH

- **CsStreamRunner:**
  - `loadExtractor` iki catch + timeout → CsTrace.error/warn + Log.e(alias) + URL

- **Media3PlayerEngine:**
  - `onPlayerError` → `buildDetailedPlaybackMessage` : localizedMessage + errorCodeName + HTTP code regex + causeChain (→ zinciri) + URL + host + hint (403/404/5004/decoder)
  - CsTrace.error(Player, playback) + diagnostics

- **KitsugiPlayerViewModel:**
  - `setPlayerError` → `enrichPlayerError` : host/shortUrl/httpCode/hint → display + details
  - `onFatal` aynı zengin detay, CsTrace.error(fatal)

- **PlayerErrorView:**
  - Mesaj altina SelectionContainer + Kopyala (ClipboardManager + Toast) + “Tanı Raporu’na yazıldı” notu

## Doğrulama
```
adb logcat -s CsTrace Player KitsugiCdnJwExtractor Media3PlayerEngine HDCH Kekik_CloseLoad Rapidrame CS_SEARCH_ERR | grep -E "HATA|extract|master|5004|403|404"
```
Oynatıcı hatası ekranında kopyalanabilir detay + Ayarlar > Tanı Raporu tam log.

## Değişen dosyalar
- app/src/main/java/com/kitsugi/animelist/data/cloudstream/extractors/KitsugiCdnJwExtractor.kt
- app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt
- app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/components/PlayerLoadingViews.kt
- app/build.gradle.kts (2.4.308)
- RELEASE_NOTES.md (v2.4.308 başı)
