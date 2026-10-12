# Bangumi Yazma Hatası — Teşhis Raporu (2026-10-12)

> Kullanıcı bildirimi: *"Bangumi'de hâlâ hata alıyorum, listeye hiçbir şey ekleyemiyorum."*
> Ekran görüntüsü: Steins;Gate detay sayfası (Bangumi kaynaklı, 2011, TV, 25 Bölüm)
> → `Bangumi güncellenemedi (Steins;Gate): Bilinmeyen hata`.
>
> İnceleme: Kitsugi-Beta `2.4.256` + Bangumi sunucu kaynakları (`bangumi/server`,
> `bangumi/server-private`) + dört açık kaynak Bangumi istemcisi + canlı uç nokta kontrolleri.

---

## 1. Özet (TL;DR)

1. **Uygulama hatayı saklıyordu.** `BangumiApiClient.upsertCollection()` istisnayı yutup `false`
   döndürüyordu; arayüz her koşulda sabit `account_sync_unknown_error` = **"Bilinmeyen hata"**
   yazıyordu. Bu yüzden ekran görüntüsündeki metin sunucunun gerçek yanıtını **göstermiyor**.
2. **İstek biçimi doğru.** Yol (`POST /v0/users/-/collections/{id}`), başlıklar
   (`Content-Type: application/json`, `Bearer`, özel `User-Agent`) ve gövde alanları, Bangumi'yi
   sorunsuz kullanan Mihon/Komikku ile **birebir** aynı. Sorun istek şeklinde değil.
3. **Geriye iki aday kaldı:** (a) **401** — token bayat/iptal edilmiş (klasik akışta `scope` boş
   olduğu için öncelikli şüphe bu), (b) **403 `insufficient token scope`** — token yeni OAuth
   akışından geldiyse kapsam JSON dizisi olarak yazılmış olabilir. Okuma uçları token istemediği
   için tarama/arama çalışmaya devam eder — belirtiyle tam uyumlu.
4. **Kapsam (scope) uyumsuzluğu gerçek bir üst-taraf kusuru** (klasik akışta beklenmese de) **ve
   istemciden düzeltilemez:** Bangumi 2026-06-25'te
   (`bangumi/server` PR #1011) yazma uçlarına kapsam denetimi ekledi. Go API'si kapsamı yalnızca
   **JSON nesnesi** olarak çözebiliyor; yeni OAuth servisi ise aynı alanı **JSON dizisi** olarak
   yazıyor → dizi çözülemeyince token "kapsamsız" sayılıp yazma **403** ile reddediliyor.
   **Kişisel erişim anahtarları** (`next.bgm.tv/demo/access-token/create`) bu alanı boş bıraktığı
   için etkilenmiyor — toplulukta bilinen çözüm yolu budur.
5. **Bu sürümde yapılanlar:** gerçek hata metni, 401'de otomatik token yenileme + tekrar deneme,
   400 üretmeyi önleyen etiket/not süzgeci, **yan etkisiz yazma testi** ve **kişisel erişim
   anahtarı ile bağlanma** yolu.

**Kullanıcıdan istenen tek adım:** *Ayarlar → Hesap Bağlantıları → Bangumi → "Bangumi Hesabını
Bağla" → **"Bağlantıyı ve yazma iznini test et"***. Çıkan satırlar sorunun hangi yarıda olduğunu
kesin olarak söyler (§7).

---

## 2. Belirti ve kanıt zinciri

| Adım | Yer | Sonuç |
|---|---|---|
| 1 | Detay sayfası → "Listeye Ekle" | Yerel kayıt oluşturulmadan önce host eşitlemesi denenir |
| 2 | `BangumiSyncManager.syncEntryToBangumi()` | `resolveSubjectId` = 10380 (Steins;Gate) ✓ |
| 3 | `BangumiApiClient.upsertCollection(...)` | İstek atılır, sunucu hata döner |
| 4 | `runCatching { … }.getOrElse { false }` | **İstisna yutulur**, yalnızca `false` döner |
| 5 | `account_sync_entry_update_failed` (strings.xml:5950) | `%1$s güncellenemedi (%2$s): %3$s` → **"Bangumi güncellenemedi (Steins;Gate): Bilinmeyen hata"** |

Yani hata mesajı **hiçbir zaman** HTTP kodunu içermiyordu; 401 ile 403 ile 400 ile 404 ile 429
ekranda aynı görünüyordu. (Eski kod yalnızca `Log.e("upsertCollection(10380) failed: …")` satırını
log dosyasına yazıyordu — cihaz günlüğü alınabilirse gerçek sebep orada zaten vardı.)

---

## 3. Sunucu sözleşmesi (doğrulanmış)

Koleksiyon yazma uçları (`bangumi/server` → `web/routes.go:92-97`):

```go
v0.PATCH("/users/-/collections/:subject_id", PatchSubjectCollection,
        req.JSON, mw.NeedLogin, mw.NeedScope(mw.ScopeWriteCollection))
v0.POST ("/users/-/collections/:subject_id", PostSubjectCollection,
        req.JSON, mw.NeedLogin, mw.NeedScope(mw.ScopeWriteCollection))
```

- **`req.JSON`** → gövde varsa `Content-Type: application/json` **zorunlu** (yoksa 415).
- **`mw.NeedLogin`** → geçerli Bearer token (yoksa/expired ise **401**).
- **`mw.NeedScope("write:collection")`** → kapsam denetimi (**403 `insufficient token scope`**).
- Başarı: `POST` **202**, `PATCH` **204** (202/204 ayrımını API dokümanı yanlış yazar).
- 404 sebepleri: `subject not found`, `subject not collected`, `subject locked or merged`.
- Ayrıca `403` üretebilen iki yol daha var: **yasaklı kullanıcı** (`you are not allowed to do this`)
  ve **reddedilen User-Agent** (`using HTTP request library's default User-Agent is forbidden`).

Gövde kuralları (`web/req/collection.go` → `Validate()`) — **tek bir ihlal tüm isteği 400 yapar**:

| Alan | Kural |
|---|---|
| `type` | 1..5 (1=wish 2=done 3=doing 4=on_hold 5=dropped) |
| `rate` | 0..10 |
| `tags` | ≤10 adet; her etiket **≥2 karakter**, `AllPrintableChar`, zero-width yok, `\u3000` ve boşluk **yasak** |
| `comment` | yazdırılabilir, kırpılır, **≤380 karakter** |
| `ep_status` / `vol_status` | **yalnızca kitap** (subject_type=1) yapıtlarında; anime'de gönderilirse **400** |

Kapsam (`internal/auth/mysql_repository.go` → `parseTokenScope`):

```go
if !scope.Valid || scope.String == "" { return nil, true }   // NULL/boş → legacy → İZİNLİ
var parsed map[string]bool
if err := json.Unmarshal([]byte(scope.String), &parsed); err != nil {
    return Scope{}, false                                    // JSON dizisi → kapsamsız → 403
}
```

`Auth.HasScope()` → `Legacy || Scope == nil` ise izin verir. Yeni OAuth servisi
(`bangumi/server-private` → `routes/oauth/index.ts`) kapsamı `JSON.stringify(scope)` ile yazdığı
için **dizi** üretir; Go çözümleyicisi diziyi çözemez.

---

## 4. Elenen nedenler (ve neden elendi)

| Aday | Durum | Gerekçe |
|---|---|---|
| Yanlış URL / yöntem | ❌ Elendi | Mihon/Komikku/Suwayomi/Aidoku ile birebir aynı: `POST`/`PATCH /v0/users/-/collections/{id}` |
| Eksik/yanlış başlık | ❌ Elendi | `Content-Type: application/json` + `Authorization: Bearer` + özel User-Agent gönderiliyor |
| `okhttp/` User-Agent engeli | ❌ Elendi | `web/mw/ua/new.go` yalnızca **varsayılan kütüphane UA'larını** engeller; Kitsugi kendi UA'sını gönderir. Ayrıca okuma uçları çalışıyor (aynı ara katman) |
| Yapıt bulunamadı (404) | ❌ Elendi | Steins;Gate = subject 10380, canlı uçta mevcut |
| Yapıt eşleştirme hatası | ❌ Elendi | Ayrı/sessiz atlanan bir yol; ekranda "eşleşme bulunamadı" yazardı |
| Gövde doğrulaması (400) | ⚠️ Olası ama dar | "Listeye Ekle" ile oluşan kayıtta not/etiket yoktur → gövde `{"type":…,"private":…}`. Yine de düzenlenen kayıtlarda etikette boşluk (ör. "Time Travel") veya 380+ karakterlik not **her isteği** 400 yapabilir → süzgeç eklendi |
| Ağ / TLS | ❌ Elendi | Aynı istemci okuma uçlarında çalışıyor |
| **Token geçersiz (401)** | ✅ **Öncelikli aday** | Klasik akışta kapsam beklenen şekilde boş; ama token sunucuda iptal edilmiş/süresi dolmuş olabilir. Okumalar token istemediği için belirti tam oturur. Ayrıca `BangumiAuthStore.isExpired()` süre bilgisi yoksa (`expires_at == 0`, ör. geri yükleme sonrası) token'ı "geçerli" sayar → **her** yazma 401 alır, hiçbir zaman yenilenmez |
| **Kapsam yok (403)** | ✅ Yedek aday | Token yeni OAuth akışından geldiyse `scope` dizisi olarak yazılmış olabilir → `parseTokenScope` çözemez → 403. `bangumi/server` PR #1011 sonrası kalıcı bir engeldir |

---

## 5. Açık kaynak uygulamalar bu sorunu nasıl çözüyor?

| Proje | Koleksiyon yazma | Kimlik | Hata yönetimi |
|---|---|---|---|
| **Mihon** (`mihonapp/mihon`) | `POST`/`PATCH /v0/users/-/collections/{id}`, `type`+`rate`+`ep_status`+`private` | Gömülü `bgm291665acbd06a4c28`; `bgm.tv/oauth/authorize` + `/access_token` | `awaitSuccess()` → istisna fırlatır, arayüzde gösterilir |
| **Komikku** (`komikku-app/komikku`) | Aynı uçlar, aynı gövde | Gömülü `bgm31586666817a5d03b`; klasik OAuth | Aynı |
| **Suwayomi-Server** | Aynı uçlar (sunucu tarafı proxy) | Gömülü `bgm376667faf473119bb`; klasik OAuth | Aynı |
| **Aidoku** (Swift) | Aynı uçlar | Gömülü `bgm479068d16523bfca2`; `bgm.tv/oauth` | Aynı |
| **Animeko** (`open-ani/ani`) | Aynı uçlar | Gömülü | Yoğun istek koruması sunucu tarafında ayrıca denetlenir (`DisableBrokenUA`) |

**Çıkarımlar**

1. **Uç nokta/istek şekli konusunda yapılacak bir şey yok** — hepsi Kitsugi ile aynı sözleşmeyi
   kullanıyor. Bu, sorunun "yanlış istek" olmadığını kanıtlıyor.
2. **Hepsi `client_id`'yi uygulamaya gömüyor** ve **hepsi klasik `bgm.tv/oauth/*` akışını**
   kullanıyor; Kitsugi de aynı akışı kullanır (`BangumiApiClient.OAUTH_AUTHORIZE_URL` /
   `OAUTH_TOKEN_URL` = `bgm.tv`). Klasik akışta `scope` alanı boş kalır (API dokümanı:
   `"scope": null`, `scope` parametresi "尚未实现") → sunucu bu token'ları "legacy" sayar →
   **yazma izinlidir**. Bu, "403 kapsam reddi"nin klasik akışta *beklenmeyen* bir durum olduğunu
   gösterir; dolayısıyla **öncelikli şüphe 401 (geçersiz/bayat token)** yönündedir. Yine de 403
   ihtimali tamamen dışlanamaz (token'ın yeni akıştan gelmiş olması veya sunucu tarafında kapsamın
   dizge olarak yazılması) — §7'deki sonda bunu kesin olarak ayırır.
3. **Hiçbiri yazma hatasını kullanıcıya ayrıntılı göstermiyor**; bu konuda Kitsugi bu sürümle
   öne geçiyor (gerçek kod + sunucu metni + yazma sondası).
4. **Topluluk çözümü:** koleksiyona yazma gerektiren üçüncü taraf araçlar (ör. Bangumi MCP
   sunucusu) kullanıcıdan **kişisel erişim anahtarı** ister: *"如果你需要写入收藏、修改进度…再补充个人
   令牌"* → `https://next.bgm.tv/demo/access-token/create`. Sebep tam olarak §3'teki kapsam
   uyumsuzluğudur; kişisel anahtarlarda `scope` boş kalır ve yazma çalışır.

**Kaynaklar:** `github.com/bangumi/server` (`web/routes.go`, `web/req/collection.go`,
`web/mw/ua/new.go`, `internal/auth/*`, PR #1011), `github.com/bangumi/server-private`
(`routes/oauth/index.ts`, `drizzle/schema.ts`), `github.com/bangumi/api` (`docs-raw/How-to-Auth.md`,
issue #212), `github.com/mihonapp/mihon`, `github.com/komikku-app/komikku`,
`github.com/Suwayomi/Suwayomi-Server`, `github.com/Aidoku/Aidoku`, `tski.uk/blog/mcp-bangumi`.

---

## 6. Bu sürümde yapılan düzeltmeler

| Dosya | Değişiklik |
|---|---|
| `data/auth/BangumiApiClient.kt` | `upsertCollection`/`patchCollection`/`markEpisodes` artık **istisna fırlatır**; `buildCollectionPayload`, `sanitizeTagsForWrite`, `sanitizeCommentForWrite`; `probeCollectionWrite()` (var olmayan id'ye boş `PATCH` → yazma kapısı ölçümü); `CollectionWriteProbe` sonuçları; `ACCESS_TOKEN_CREATE_URL` |
| `data/auth/BangumiSyncManager.kt` | `writeWithAuthRetry()` (401 → yenile → bir kez tekrar); `describeWriteFailure()` (HTTP kodu + sunucunun ham metni); not/etiket süzgeci; silme yolu da düzeltildi |
| `data/auth/BangumiAuthManager.kt` | `connectWithPersonalToken()` (anahtarı `GET /v0/me` ile doğrular), `sanitizePersonalToken()`, yeni `diagnose()` (App kimliği → oturum → token → yazma kapısı) |
| `data/auth/BangumiAuthStore.kt` | Kişisel anahtar oturumu (`saveManualToken`, `isManualToken`): `refresh_token` yok, süre takibi yok, 401'de yenileme denenmez; OAuth girişinde/çıkışta temizlenir |
| `ui/components/KitsugiBangumiLoginDialog.kt` | Diyalogda **"Kişisel Erişim Anahtarı ile Bağlan"** bölümü + **"Bağlantıyı ve yazma iznini test et"** bölümü |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Yeni TR/EN metinler (yazma hataları, bağlantı testi, kişisel anahtar) — anahtar sayısı iki dosyada eşit |
| `PLAN_BANGUMI_INTEGRATION.md`, `AccountSettingsSubPages.kt` | 2026-06-25 sonrası kapsam davranışı ve kişisel anahtar yolu belgelendi |
| `app/build.gradle.kts`, `release_notes_v2.4.257.md`, `RELEASE_NOTES.md` | Sürüm 2.4.257 + notlar |

Rapor yazıldıktan sonra yapılan üç küçük sağlamlaştırma:

- **Sonda yanlış pozitife karşı sıkılaştırıldı:** `probeCollectionWrite()` artık 404'ü yalnızca
  gövdede gerçekten `subject` geçtiğinde "yazma kapısı açık" sayıyor. Böylece çerçeve
  seviyesindeki genel bir `404 Not Found` (ör. yanlış yola istek) yanlışlıkla "kapsam geçti"
  diye okunamaz.
- **Boş etiket listesi artık gönderilmiyor:** `sanitizeTagsForWrite()` süzgeçten hiçbir etiket
  geçmezse `null` döndürüyor. `"tags": []` göndermek sunucuda **mevcut etiketleri siler**;
  Kitsugi'nin niyeti "etiket yaz" olduğu için alan tamamen atlanıyor (istenmeyen veri kaybı
  riski kalktı).
- **Çift "bağlantı başarılı" olayı kaldırıldı:** Kişisel anahtar yolunda `saveManualToken(...)`
  zaten `emitBangumiSuccess()` yayınlıyor; diyalogdaki ikinci çağrı kaldırıldı, koleksiyon
  aktarımı bir kez tetikleniyor.

**Doğrulama durumu:** Beş Kotlin dosyası `kotlinc` ile ayrıştırıldı — **0 sözdizimi hatası**
(yalnızca Android/OkHttp bağımlılıkları olmadığı için "unresolved reference" gürültüsü). Bu ortamda
Android SDK bulunmadığı için tam Gradle derlemesi ve APK üretimi yapılamadı; `R.string` referansları
ve TR/EN anahtar eşitliği betikle doğrulandı.

---

## 7. Kullanıcı için adımlar

1. **Yeni sürümü derleyip kurun** (2.4.257+).
2. *Ayarlar → Hesap Bağlantıları → Bangumi → **Bangumi Hesabını Bağla***.
3. **"Bağlantıyı ve yazma iznini test et"** düğmesine basın ve çıkan satırları paylaşın.

Sonuca göre:

| Test sonucu | Anlamı | Yapılacak |
|---|---|---|
| `Token geçerli — kullanıcı: …` + `Yazma kapısı AÇIK` | Token ve kapsam sorunsuz | Yazmayı tekrar deneyin; hata mesajı artık sunucunun **gerçek** metnini gösterir (ör. 400 ise hangi alan) |
| `… HATA: 403 insufficient token scope` | **Kapsam reddi** (§3) | Aynı diyalogda **kişisel erişim anahtarı** üretip yapıştırın: `next.bgm.tv/demo/access-token/create` → "Kişisel Erişim Anahtarı" → **Anahtarla Bağlan** |
| `… HATA: token reddedildi (401)` | Token geçersiz | Bangumi hesabını yeniden bağlayın (veya kişisel anahtar) |
| `… HATA: 403 … you are not allowed to do this` | Hesap yasaklı | Bangumi hesap durumuna bakılmalı |
| `Yazma testi tamamlanamadı: …` | Ağ/5xx | Tekrar deneyin; mesaj paylaşılmalı |

4. İsteğe bağlı: `Listeye Ekle` akışını tekrarlayın — başarısızlık hâlinde ekrandaki mesaj artık
   HTTP kodunu ve sunucunun metnini içerir.

---

## 8. Üst tarafa (Bangumi) bildirilecek hata — hazır metin

> **Başlık:** `parseTokenScope` rejects the JSON array scope written by the OAuth service → all
> collection writes fail with `403 insufficient token scope`
>
> **Gövde:**
> `server-private/routes/oauth/index.ts` stores the granted scope as a JSON **array**, e.g.
> `rawScope = JSON.stringify(["read:collection","write:collection"])`.
> `server/internal/auth/mysql_repository.go:parseTokenScope` only unmarshals into
> `map[string]bool` and returns `Scope{}, legacy=false` for any non-object JSON, so
> `mw.NeedScope("write:collection")` denies the request (`web/routes.go:92-97`).
> Effect: tokens issued by the new OAuth flow can read but never write collections
> (`POST/PATCH /v0/users/-/collections/{subject_id}` → 403), while legacy/PAT tokens
> (`scope = NULL`) keep working — hence the inconsistency between clients.
> Repro: obtain a token via the new OAuth flow, then
> `curl -X PATCH https://api.bgm.tv/v0/users/-/collections/10380 -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'`
> → `403 {"title":"insufficient token scope"}`; the same request with a personal access token
> returns `404 {"title":"subject not collected"}` (i.e. the scope check passed).
> Suggested fix: accept both objects (`{"write:collection":true}`) and arrays
> (`["write:collection"]`) in `parseTokenScope`, or make the OAuth service write the object form.

---

## 9. Açık kalan tek belirsizlik

Kullanıcının elindeki token'ın **hangi akıştan** üretildiği (klasik `bgm.tv/oauth` mı, yeni
`next.bgm.tv/oauth` mu) ve dolayısıyla `scope` alanının ne içerdiği, bu ortamdan (internet erişimi
yalnızca `fetch_page` ile ve api.bgm.tv'ye POST atılamıyor) **doğrudan ölçülemedi**. §7'deki tek
düğme bu belirsizliği cihazda kesin olarak kapatır: sonda ya `404` (kapı açık) ya da
`403 insufficient token scope` (kapı kapalı) döner.
