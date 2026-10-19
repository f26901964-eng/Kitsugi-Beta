# TAM DENETİM — HDFC/FilmMakinesi Eklenti Akışı (2026-10-19 v2.4.310)

## Soru
“Başka eksik gedik kaldı mı? Her şeyi tek tek dedirtme.”

## Yöntem
- `domain_fixes.json` 81 domain → `filmmakinesi: https://filmmakinesi.to` ✅, `hdfilmcehennemi: https://www.hdfilmcehennemi.nl` ✅ (log’da kullanılanlarla aynı)
- `app/src/main/assets/plugins/plugins.json` → FM 66 (47441), HDC 56 (72972) = `builds` dalındaki son yayımlı, 58/68 henüz yayımlanmadığı için senkron doğru
- `PlayerMediaSourceFactory.buildDataSourceFactory` → Referer provider.mainUrl fallback ✅, Origin Referer origin’inden türetme ✅, originOfUrl helper var ✅
- `CsStreamRunner` → direkt medya kolu Origin (Referer/clean) ✅, fallback StreamSource Origin ✅, `verifyMediaCandidate` HEAD → Referer+Origin ✅, `tryGetRangeValidation` GET → sadece Referer ❌ → v2.4.310’da düzeltildi (Origin eklendi)
- `KitsugiCdnJwExtractor` → boş HTML warn ✅, hasEncryptedBlock guard (dc_ varsa bayat contentUrl atla) ✅, base64 url-safe/padding ✅, mirrorToAliasTags (Kekik_CloseLoad/HDCH/Rapidrame) ✅, resolveMasterPlaylist warn ✅, reportExtractorError + CsTrace ✅
- `Media3PlayerEngine.onPlayerError` → codeName+httpCode+causeChain+host+hint, CsTrace ✅
- `KitsugiPlayerViewModel.setPlayerError/onFatal` → enrichPlayerError (Kaynak/Host/URL/Hata kodu/HTTP/ipucu) ✅
- `PlayerErrorView` → SelectionContainer + Kopyala + Tanı Raporu notu ✅
- `CsStreamRunner verifiedFinal` → trulyDead ayrımı (toCheck==dead → boş dön, timeout→fallback) ✅
- `MpvTlsPolicyTest` / `KitsugiMpvSurfaceView` → main ile fark var ama plugin akışını etkilemez, not edildi

## Kalan tek eksik (düzeltildi)
`tryGetRangeValidation(url, referer)` sadece `Referer` gönderiyordu. `playmix.uno`/`closeload` Origin’i de doğruladığı için `HEAD 404` doğru olsa bile `GET Range` de 404 dönüp gerçekten canlı linki ölü sanabiliyordu. v2.4.310’da `originOfUrl(referer)` ile Origin eklendi.

## Sonuç
Plugin → extractor → HEAD/GET doğrulama → player → UI hata gösterimi zincirinin tamamı artık Origin’li, bayat-link üretmeyen, kopyalanabilir hatalı. 58/68 yayımlanınca `plugins.json` senkronu tekrar yapılacak; şimdilik 56/66 doğru.

## Değişenler v2.4.310
- CsStreamRunner.tryGetRangeValidation → Origin header
- app/build.gradle.kts 2.4.309→2.4.310
- RELEASE_NOTES v2.4.310 başı
