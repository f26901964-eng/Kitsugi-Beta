# KÖK NEDEN — HDFC/FilmMakinesi 404 Sahte Link (v2.4.309)

**Tarih:** 2026-10-19 20:59 log (v2.4.307)
**Semptom:** Her iki eklenti aynı URL’yi veriyor `https://hls8.playmix.uno/hls/filmakinesimp4-f9gx1M12BwC.mp4/master.txt` → `HEAD 404` + `GET 404` → `verified 0` → `false positive → orijinaller döndürülüyor` → `MPV 9001`

## Zincir
1. Embed HTML’i doğru domain’den geliyor (hdfilmcehennemi.mobi, closeload.filmmakinesi.to) — domain fix’i okey.
2. Ama HTML içindeki `dc_` şifreli blok yeni `eval(function(p,a,c,k,e,d){...})` paketlemeyle değişmiş → `decryptLocalUrl Error: bad base-64` / `Native deşifre başarısız`.
3. Eski kod `decrypt == null` → `contentUrl`/`master.txt` regex fallback’ine düşüyor, bayat URL’yi kaynak diye üretiyor (200 OK iken geçerliydi, şimdi CDN’de yok → 404).
4. CsStreamRunner HEAD 404 + GET 404 → deadStreams=1, verified=0 → eski guard `verified empty && streams not empty → streams` → aynı ölü linki oynatıcıya atıyor → MPV 9001.

## Fix (v2.4.309, app içi)
- **KitsugiCdnJwExtractor:** `hasEncryptedBlock = html.contains("dc_") && html.contains("<script")` → decrypt null ise `contentUrl` atlanıyor, sadece açık `.m3u8`/master varsa deneniyor, yoksa boş dön + warn/mirror + CsTrace. Base64 url-safe (`-`→`+`, `_`→`/`, padding) denemesi eklendi.
- **CsStreamRunner:** `verifiedFinal` guard’ı güncellendi: `toCheck.size == deadStreams.size && dead non-empty → trulyDead → verified boş dön`, timeout/belirsizde eski fallback korunuyor.

## Beklenen Davranış
- Eski: 1 stream → HEAD 404 → yine oynatıcıya → 9001 hatası
- Yeni: 1 stream → HEAD 404 → trulyDead → 0 stream → UI “kaynak yok” + log `crypted block çözülemedi` + `Tüm streamler HEAD 404 ile ölü - kaynak yok`

## Açık Nokta
Gerçek `dc_` yeni şifresi için tam `eval/dc_` ham script’i lazım (TANI logundaki kesik satır yetmez). Şu anki fix sahte link üretimini kesiyor; tam decrypt için script geldikçe `decryptNative` güncellenecek.

## Değişenler
- app/src/main/java/com/kitsugi/animelist/data/cloudstream/extractors/KitsugiCdnJwExtractor.kt
- app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt
- app/build.gradle.kts 2.4.308→2.4.309
- RELEASE_NOTES.md v2.4.309 başı
