# Kitsugi-Beta v2.4.261 — Sürüm Notları / Release Notes

## 🇹🇷 Türkçe

### 🎌 1. Bangumi: Listeye Ekleme / Güncelleme Artık Çalışıyor (HTTP 415 düzeltmesi)
- **Belirti:** Bangumi kaynaklı her "Listeye Ekle", durum/puan güncellemesi ve bölüm ilerlemesi
  `Bangumi yazma hatası (HTTP 415): Unsupported Media Type — request with body must set "content-type" header to "application/json"`
  ile reddediliyordu; arama ve okuma çalışıyordu.
- **Kök neden (uygulama tarafı):** Bangumi sunucusu (`bangumi/server` → `web/req/content_type.go`) `Content-Type` başlığını
  **birebir dize eşitliğiyle** denetler ve yalnızca `application/json` ya da `application/json; charset=UTF-8` kabul eder.
  Kitsugi gövdeyi OkHttp'nin `String.toRequestBody(mediaType)` yoluyla kuruyordu; OkHttp bu yolda tipe kendisi
  `; charset=utf-8` (küçük harf) ekler ve `BridgeInterceptor` elle konan `Content-Type` başlığını gövdenin tipiyle ezer.
  Sunucuya `application/json; charset=utf-8` gidiyordu → 415. Bu ara katman `NeedLogin`/`NeedScope`'tan **önce** çalıştığı
  için token hiç denetlenmiyordu; önceki sürümlerdeki "kapsam / token" teorileri bu yüzden sonuç vermedi.
- **Düzeltme:** `BangumiApiClient.buildJsonRequestBody()` — gövde ham UTF-8 bayt olarak, tip tam `application/json` olacak
  biçimde kuruluyor (Mihon/Aniyomi'nin kullandığı sözleşmenin aynısı). Tüm yazma uçları (`POST/PATCH /v0/users/-/collections/…`,
  `…/episodes`, `PUT …/episodes/{id}`, `/v0/indices*`) ve yazma sondası aynı `send()`'den geçtiği için tek noktadan düzeldi.
- **Tanı:** 415 artık ayrı sınıflandırılıyor — yazma hatası metni (`bangumi_write_failed_unsupported_media`) ve
  "Bağlantıyı ve yazma iznini test et" sonucu (`REQUEST_REJECTED`) bunun bir **uygulama hatası** olduğunu, yeniden
  bağlanmanın çözmeyeceğini açıkça söylüyor.
- **Test:** `BangumiApiClientWriteTest` — gövde tipinin birebir `application/json` olduğunu, charset parametresi
  içermediğini, UTF-8 baytların ve `Content-Length`'in doğru olduğunu ve eski yolun neden reddedildiğini doğrular.

## 🇬🇧 English

### 🎌 1. Bangumi: Add to List / Updates Work Again (HTTP 415 fix)
- **Symptom:** every Bangumi "Add to list", status/score update and episode progress write was rejected with
  `Bangumi write error (HTTP 415): Unsupported Media Type — request with body must set "content-type" header to "application/json"`,
  while search and reads kept working.
- **Root cause (client side):** the Bangumi server (`bangumi/server` → `web/req/content_type.go`) compares the
  `Content-Type` header by **exact string equality** and only accepts `application/json` or `application/json; charset=UTF-8`.
  Kitsugi built the body through OkHttp's `String.toRequestBody(mediaType)`, which appends `; charset=utf-8` (lowercase)
  on its own, and `BridgeInterceptor` overwrites any manually set `Content-Type` with the body's type. The server saw
  `application/json; charset=utf-8` → 415. Because this middleware runs **before** `NeedLogin`/`NeedScope`, the token
  was never evaluated — which is why the earlier "scope / token" theories went nowhere.
- **Fix:** `BangumiApiClient.buildJsonRequestBody()` now builds the body from raw UTF-8 bytes with the media type kept
  exactly `application/json` (the same contract Mihon/Aniyomi use). All write endpoints
  (`POST/PATCH /v0/users/-/collections/…`, `…/episodes`, `PUT …/episodes/{id}`, `/v0/indices*`) and the write probe go
  through the same `send()`, so one change fixes all of them.
- **Diagnostics:** 415 is now classified separately — the write error text (`bangumi_write_failed_unsupported_media`)
  and the "Test connection and write permission" result (`REQUEST_REJECTED`) state clearly that this is an **app bug**
  and reconnecting will not help.
- **Tests:** `BangumiApiClientWriteTest` verifies the body type is exactly `application/json` with no charset parameter,
  that UTF-8 bytes and `Content-Length` are correct, and documents why the legacy path was rejected.
