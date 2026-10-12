# PLAN / TASK — Bangumi Yazma: HTTP 415 "Unsupported Media Type" (v2.4.261)

**Tarih:** 2026-10-12
**Dal:** `arena/03a5cb2b-kitsugi-beta`
**Sürüm:** 2.4.260 → **2.4.261**
**Şikâyet:** "Liste oluşturamıyorum, Bangumi'ye hiçbir şey eklenmiyor."
Ekran görüntüsü: Clannad detay sayfası →
`Bangumi güncellenemedi (Clannad: After Story): Bangumi yazma hatası (HTTP 415): Unsupported Media Type —
request with body must set "content-type" header to "application/json" — sunucu: Unsupported Media Type — …`

> Not: 2.4.257'de eklenen "gerçek hata metni" sayesinde bu kez sunucunun **kendi cümlesi** ekrana geldi ve kök
> neden ilk bakışta okunabildi. 2.4.257 raporundaki "istek biçimi doğru, sorun token/kapsam" tezi **yanlıştı** —
> bkz. §1. İlgili raporun başına düzeltme notu eklendi.

---

## 0. Kanıt zinciri (dosya ve satır düzeyinde)

| # | Kaynak | Bulgu |
|---|---|---|
| 1 | `bangumi/server` → `web/req/content_type.go` (`req.JSON`) | `if contentType == echo.MIMEApplicationJSON \|\| contentType == echo.MIMEApplicationJSONCharsetUTF8 { next } else 415` → **tam dize eşitliği**, ön-ek/parametre ayrıştırması yok |
| 2 | `labstack/echo` → `echo.go` | `MIMEApplicationJSON = "application/json"`, `charsetUTF8 = "charset=UTF-8"` → kabul edilen ikinci değer **büyük harfli** `application/json; charset=UTF-8` |
| 3 | `bangumi/server` → `web/routes.go` | `req.JSON` yalnızca **yazma** rotalarında: `POST/PATCH /v0/users/-/collections/:id`, `PATCH …/episodes`, `PUT …/collections/-/episodes/:id`, `/v0/indices*`. Sırası: `req.JSON` → `mw.NeedLogin` → `mw.NeedScope`. `POST /v0/search/subjects` bu ara katmanı **kullanmaz** |
| 4 | `square/okhttp` 4.12.0 → `RequestBody.kt:105-119` | `String.toRequestBody(contentType)`: tipte charset yoksa `finalContentType = "$contentType; charset=utf-8"` (**küçük harf**) |
| 5 | `square/okhttp` 4.12.0 → `BridgeInterceptor.kt:41-46` | `requestBuilder.header("Content-Type", body.contentType().toString())` → elle konan başlık **ezilir** |
| 6 | Kitsugi `BangumiApiClient.send()` (2.4.260) | `(json.toString()).toRequestBody("application/json".toMediaTypeOrNull())` + `.header("Content-Type","application/json")` → telde **`application/json; charset=utf-8`** |
| 7 | `aniyomiorg/aniyomi` ve `mihonapp/mihon` → `BangumiApi.kt` | gövde `.toString().toRequestBody()` (**tipsiz**) + `headersOf("Content-Type", "application/json")` → BridgeInterceptor dokunmaz, telde tam `application/json` → çalışır |

**Sonuç:** 1+2+6 → sunucu `"application/json; charset=utf-8" == "application/json; charset=UTF-8"` karşılaştırmasında
`false` alır → **415**. 3 → arama ve okumalar etkilenmez, yazma hiçbir zaman token aşamasına gelmez.

---

## 1. 2.4.257 teşhisi neden tutmadı?

- Önceki rapor "başlıklar Mihon ile birebir" dedi; kod düzeyinde doğru (ikisi de `Content-Type: application/json`
  **yazar**) ama **telde** farklıydı: Mihon gövdeyi tipsiz kurduğu için başlığı OkHttp değiştirmez, Kitsugi'de değiştirir.
- 401/403 teorileri sunucu ara katman **sırası** nedeniyle test bile edilemezdi: 415, `NeedLogin`'den önce döner.
- 2.4.257'nin yazma sondası (`probeCollectionWrite`) da aynı `send()`'i kullandığı için **o da 415 alıyordu** ve
  sonucu `FAILED` ("yazma testi tamamlanamadı") diye genel bir metne düşürüyordu — kullanıcıya sebep görünmüyordu.

---

## 2. Görevler

- [x] T1 — `BangumiApiClient.buildJsonRequestBody()`: gövde `ByteArray.toRequestBody("application/json".toMediaType())`
      ile kurulur (charset eklenmez); `send()` bunu kullanır. `JSON_CONTENT_TYPE` sabiti + sözleşme KDoc'u
- [x] T2 — `BangumiApiException.isUnsupportedMediaType` (415)
- [x] T3 — `probeCollectionWrite`: 415 → yeni `CollectionWriteProbe.REQUEST_REJECTED` (401/403'ten **önce** değerlendirilir)
- [x] T4 — `BangumiAuthManager.diagnose()`: `REQUEST_REJECTED` satırı (`bangumi_diag_write_request_rejected`)
- [x] T5 — `BangumiSyncManager.describeWriteFailure`: 415 → `bangumi_write_failed_unsupported_media`
- [x] T6 — TR/EN metinler (`values/`, `values-en/`): 2 yeni anahtar; eşlik betiği `check_localization_resource_parity.py` ✓ (6959/6959)
- [x] T7 — Birim testi `BangumiApiClientWriteTest` (6 test): tip birebir `application/json`, charset yok, boş `{}` sonda
      gövdesi, UTF-8 bayt + `Content-Length`, eski yolun `; charset=utf-8` ürettiğinin regresyon kanıtı, sabit kontrolü
- [x] T8 — Sürüm 2.4.261, `release_notes_v2.4.261.md`, `RELEASE_NOTES.md`, `PLAN_BANGUMI_INTEGRATION.md` §5'e sözleşme
      maddesi, 2.4.257 raporuna düzeltme notu
- [ ] T9 — Cihaz testi (kullanıcı): Clannad / Steins;Gate → **Listeye Ekle** → hata yok, bgm.tv profilinde 在看 görünmeli;
      "İzliyorum + 3 bölüm + puan 8" → ep 3 / ★8; Ayarlar → Bangumi → "Bağlantıyı ve yazma iznini test et" → `Yazma kapısı AÇIK`

---

## 3. Değişen dosyalar

| Dosya | Ne |
|---|---|
| `data/auth/BangumiApiClient.kt` | `JSON_CONTENT_TYPE`, `buildJsonRequestBody()`, `send()` düzeltmesi, `isUnsupportedMediaType`, `REQUEST_REJECTED` + sonda dalı, KDoc |
| `data/auth/BangumiSyncManager.kt` | `describeWriteFailure`: 415 dalı |
| `data/auth/BangumiAuthManager.kt` | `diagnose()`: `REQUEST_REJECTED` satırı |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `bangumi_write_failed_unsupported_media`, `bangumi_diag_write_request_rejected` |
| `test/…/data/auth/BangumiApiClientWriteTest.kt` | Yeni birim testi |
| `app/build.gradle.kts` | 2.4.261 |
| `release_notes_v2.4.261.md`, `RELEASE_NOTES.md`, `PLAN_BANGUMI_INTEGRATION.md`, `BANGUMI_YAZMA_TESHISI_RAPORU_2026-10-12.md`, `PLAN_TASK_BANGUMI_YAZMA_IZIN_HATASI_2026-10-12.md` | Belgeler |

---

## 4. Bilinçli olarak dokunulmayan

- **Arama ucu** (`POST /v0/search/subjects`) aynı `send()`'den geçmiyor olsa bile etkilenmezdi (ara katman yok); şimdi
  o da tam `application/json` gönderir — zararsız.
- **OAuth token uçları** form gövdeli (`FormBody`), `bgm.tv/oauth/*`; `req.JSON` kapsamı dışında, değişmedi.
- **Kapsam (scope) notları** (2.4.257): sunucu tarafı dizi/nesne uyumsuzluğu hâlâ gerçek bir üst-taraf konusu olabilir,
  ancak bu kullanıcıda hiç devreye girmedi. PAT yolu ve metinleri korunuyor; 415 düzelince gerçekten 403 alan biri
  olursa artık o mesajı görecek.
- `.header("Content-Type", JSON_CONTENT_TYPE)` satırı `send()`'de **bilgilendirici** olarak bırakıldı; telde belirleyici
  olan `body.contentType()`'tır ve ikisi aynı değerdir.

---

## 5. Doğrulama durumu

- Bu ortamda JDK/Android SDK yok → Gradle derlemesi ve `BangumiApiClientWriteTest` **çalıştırılamadı**; test, OkHttp
  4.12.0 kaynak kodundan okunan davranışa göre yazıldı (`RequestBody.kt`, `BridgeInterceptor.kt`).
- Yerelleştirme eşliği betikle doğrulandı; XML iki dosyada da geçerli.
- Derleme/test komutu (geliştirici makinesi):
  `./gradlew :app:testFossDebugUnitTest --tests '*BangumiApiClientWriteTest*'` ve `./gradlew assembleFossDebug`.
- Canlı doğrulama (herhangi bir makine, PAT ile):
  `curl -i -X PATCH https://api.bgm.tv/v0/users/-/collections/999999999 -H "Authorization: Bearer $TOKEN" -H "User-Agent: test/1.0 (https://example.com)" -H "Content-Type: application/json; charset=utf-8" -d '{}'` → **415**;
  aynı istek `-H "Content-Type: application/json"` ile → **404 subject not found** (kapı açık).
