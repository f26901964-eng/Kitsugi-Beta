# Karakter / Ekip / Seslendiren Wiki Sekmesi — "Bulunamadı" ve Ölü "Tekrar Dene" Kök Neden Raporu (2026-10-13)

## 1. Kullanıcı bildirimi

- Karakter detayında (ör. **Itsuka Kotori / Date A Live**) Wiki sekmesi "Wiki bulunamadı" diyor;
  oysa Fandom'da sayfa var: `date-a-live.fandom.com/wiki/Kotori_Itsuka`.
- Karakter, ekip ve seslendiren detaylarındaki **"Tekrar dene" düğmesine basınca hiçbir şey
  olmuyor** (ağ isteği bile yok hissi).

## 2. Kök neden 1 — "Tekrar dene" önbellek duvarına çarpıyor (ölü düğme)

Çözümleme üç katmanlı önbellek kullanıyor:

| Katman | Anahtar | Olumsuz TTL |
|---|---|---|
| Karakter sayfası | `fandom:v1:char:v3:<kimlik>:…` | 3 gün (kalıcı DB) |
| Yapım wiki | `fandom:v1:wiki:v2:<mediaKey>:…` | 3 gün (kalıcı DB) |
| Süreç içi "wiki yok" | `missingWikis` (siteinfo) | 30 dk (bellek) |

`FandomCharacterWikiViewModel.retry()` yalnızca `rebind()` yapıyordu: `bindQuery` →
`resolveCharacterPage` → **`readCached` olumsuz kaydı döndürüyor** → ağa çıkılmadan `null` →
`UiState.Unavailable`. Yapım katmanı da kendi olumsuz kaydını döndürdüğü için zincir hiç
canlanmıyordu. Kullanıcıya yansıyan: düğme ölü.

Karşılaştırma: yapım wiki sekmesi (`FandomMediaWikiViewModel.retry`) zaten önce
`FandomRepository.forgetMediaWiki(...)` ile önbelleği silip sonra yeniden çözümlüyordu.
Karakter tarafında bu eşdeğer adım **hiç yoktu** — asimetri bug'ın kendisi.

### Çözüm
- `resolveCharacterPage(context, query, forceRefresh)` ve
  `resolveMediaWiki(context, query, allowNameFallback, forceRefresh)` parametreleri eklendi:
  `forceRefresh=true` iken olumlu/olumsuz önbellek **okunmuyor** (yazma yine yapılır, böylece
  sonuç kaydı tazelenir). Kullanıcının onayladığı wiki kısayolu korunur.
- `FandomCharacterResolver.resolve(query, forceRefresh)` bayrağı yapım katmanına taşır.
- `FandomCharacterWikiViewModel.retry()`: `FandomWikiResolver.forgetMissingWikis()` (süreç içi
  zehir listesi) + `rebind(forceRefresh = true)`. Düğme basılınca iskelet görünür → gerçek istekler
  gider → sonuç (bulunursa içerik, bulunmazsa taze değerlendirme) gelir.

## 3. Kök neden 2 — Geçici kesintinin 3 gün kalıcı "bulunamadı" yazması

`writeCached(persist = !transientFailure)` kuralı var ama `transientFailure` yalnızca
`FandomHttp.failureCount()` farkına bakıyor. Üç açık:

1. **HTTP 403/408 sayılmıyordu.** `services.fandom.com` (Cloudflare) ve `query.wikidata.org`
   (WMF bot politikası) tarayıcı dışı istemciye 403/408 dönebiliyor; bu kodlar "veri yok"
   anlamına gelmediği hâlde sayılmadığı için zincir "sağlıklı bulamadım" sanıp olumsuz kaydı
   **3 gün kalıcı** yazıyordu. (404 bilinçli olarak hâlâ sayılmaz: olmayan wiki gerçekten 404.)
2. **Kimlik köprüsü patlamaları sayılmıyordu.** `FandomWikiResolver.safely {}` (AniList/Kitsu/Simkl
   çapraz kimlik) istisnayı yutuyor, failureCount artmıyordu → aynı 3 gün zehir.
   Artık `safely {}` istisnada `FandomHttp.markFailure()` çağırıyor.
3. **Wikidata karakter yedeği zinciri tek hatada kırıyordu.** `characterArticles()` Action API
   yedeğinde ilk `findQids/getEntities` null dönüşünde `return emptyList()` diyordu; artık
   `continue` ile sıradaki kimliği deniyor.

Ek koruma: kalıcı olumsuz TTL 3 gün → **1 gün** indirildi (sekme yalnızca sayfa açılışında
çözümlendiği için API yükü ihmal edilebilir; bayat zehir penceresi üçte bire iner).

## 4. Neden "wiki var ama bulamıyor" günlerce sürüyordu

Yukarıdaki iki kök neden birleşince: geçici bir kesinti anında üretilen olumsuz kayıt 3 gün
kalıcı oluyor, kullanıcı her girişinde aynı kaydı okuyor, "Tekrar dene" de aynı kaydı okuduğu
için kurtuluş yolu kalmıyordu. Bu paket sonrası:

- Kesinti anında üretilen sonuç kalıcı yazılmaz (10 dk bellek).
- Kalıcı yazılan olumsuz kayıt 1 günde düşer.
- Kullanıcı istediği an "Tekrar dene" ile önbelleksiz taze çözümleme başlatır.

## 5. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/fandom/FandomHttp.kt` | 403/408 geçici hata sayılır (429/5xx zaten). |
| `data/fandom/WikidataClient.kt` | `characterArticles` Action API yedeğinde `return emptyList()` → `continue`. |
| `data/fandom/FandomWikiResolver.kt` | `safely {}` istisnada `markFailure()`; `forgetMissingWikis()` eklendi. |
| `data/fandom/FandomCharacterResolver.kt` | `resolve(query, forceRefresh)`; yapım çözümlemesine taşınır. |
| `data/fandom/FandomRepository.kt` | `resolveMediaWiki`/`resolveCharacterPage` + `forceRefresh`; NEGATIVE_TTL 1 gün; doküman. |
| `ui/.../wiki/FandomCharacterWikiViewModel.kt` | `retry()` = zehir temizliği + `forceRefresh` ile yeniden çözümleme. |
| `app/build.gradle.kts` | 2.4.282. |

## 6. Doğrulama notu

Bu ortamda Android derlemesi ve cihaz testi yapılamadı (Gradle bağımlılıkları ağ politikası
gereği çekilemiyor). Değişiklikler yalnızca imza uyumu ve çağrı noktaları açısından denetlendi:
tüm mevcut çağrılar varsayılan parametreyle uyumlu kalır (`forceRefresh = false` öntanımlı).
Davranış doğrulaması cihazda yapılmalıdır: Wiki sekmesi olmayan bir karakterde "Tekrar dene" →
iskelet → içerik; ayrıca Ayarlar → Fandom Wiki önbellek temizliği yolu eskisi gibi çalışır.
