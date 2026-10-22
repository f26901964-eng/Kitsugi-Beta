# Kanıta Dayalı Alan Bazlı Çapraz Eşitleme Raporu

**Tarih:** 22 Ekim 2026
**Dal:** `arena/38590839-kitsugi-beta`
**Commit:** `a331b99` — *"alan bazlı çapraz eşitleme: yalnızca değişen alanlar hedeflere yazılır"*
**Kapsam:** AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl (TMDB kimlik kaynağıdır, yazma ucu yoktur)

---

## 0. Kısa özet

Önceki turlarda altı kaynağa **eşzamanlı fan-out** kurulmuştu (`ExternalListSyncManager`, 6 × `async` + 30 sn timeout). Bu turda kapatılan asıl açık farklıydı ve altı kaynağın **hepsini** ilgilendiriyordu:

> Uygulama, her eşitlemede satırın **tamamını** hedefe yazıyordu. Yerel satırda puan/not/tarih boşken bu, hedefteki değerleri **siliyor ya da eziyordu**. Aynı nedenle, yalnızca ilerleme değiştiğinde bile Kitsu'daki not, MAL'daki puan, Bangumi'deki etiket yeniden yazılıyordu.

Bu turda eşitleme **alan farkı (diff)** üzerine taşındı: `SyncFieldDiff` yalnızca gerçekten değişen alanları üretir, `ProviderCapabilitiesCatalog` her hedefin hangi alanı desteklediğini tanımlar, `ExternalListSyncManager` her sağlayıcıya yalnızca kendi payına düşen alanları verir. Hiçbir alan değişmediyse istek **atılmaz** ve sonuç arayüzde `değişiklik yok` olarak görünür (sessiz atlama yok).

Bunun yanında, kod okumasında çıkan **beş somut veri kaybı hatası** kapatıldı (bölüm 2).

---

## 1. Yöntem: neye dayanarak?

### 1.1 Resmî API sözleşmeleri

| Sağlayıcı | İncelenen sözleşme | Kritik sonuç |
|---|---|---|
| AniList | `SaveMediaListEntry` mutation ([docs.anilist.co/reference/mutation](https://docs.anilist.co/reference/mutation)), `MediaListStatus` | Alanlar: `score`/`scoreRaw`, `progress`, `progressVolumes`, `repeat`, `priority`, `private`, `notes` (≤6000), `startedAt`/`completedAt` = `FuzzyDateInput`. `id` = liste kaydı, `mediaId` = medya. |
| MAL | API v2 `PATCH /{anime\|manga}/{id}/my_list_status` ([myanimelist.net/apiconfig/references/api/v2](https://myanimelist.net/apiconfig/references/api/v2)) | `status`, `score` 0–10 **"0 = puanı sil"**, `num_watched_episodes`/`num_chapters_read`, `num_volumes_read`, `is_rewatching`/`is_rereading`, `num_times_rewatched`/`num_times_reread`, `rewatch_value` 0–5, `priority` 0–2, `tags`, `comments`, `start_date`/`finish_date` (`YYYY-MM-DD`). **Favori ucu yok.** |
| Kitsu | JSON:API `library-entries` ([kitsu.app/api/edge](https://kitsu.app/api/edge/library-entries)) | `ratingTwenty` (2–20), `status` (current/planned/completed/on_hold/dropped), `progress`, `reconsuming`, `reconsumeCount`, `startedAt`/`finishedAt` (ISO), `notes`, `private`. |
| Shikimori | API v2 `user_rates` ([shikimori.one/api/doc/2.0](https://shikimori.one/api/doc/2.0)) | `status` (planned/watching/rewatching/completed/on_hold/dropped), `score` 0–10, `episodes`/`chapters`, `volumes`, `rewatches`, `text`. Yazma scope'u `user_rates`. |
| Bangumi | API v0 `POST/PATCH /v0/users/-/collections/{subject_id}` ([bangumi.github.io/api](https://bangumi.github.io/api/)) | `type` 1–5, `rate` 0–10, `ep_status`, `vol_status`, `comment` (≤380), `private`, `tags` (≤10, her etiket ≥2 karakter, boşluksuz). |
| Simkl | `POST /sync/add-to-list`, `/sync/history`, `/sync/ratings` ([api.simkl.org](https://api.simkl.org/)) | İzinli `to` değerleri: **`watching`, `plantowatch`, `completed`, `notinteresting`, `hold`** — `dropped` **geçersiz**. Tarih, cilt, tekrar sayısı yok. Manga yok. |

### 1.2 Açık kaynak uygulamaların kodu (davranış referansı)

Klone edilip okundu (`/tmp/aniyomi`, `/tmp/malsync` — kalıcı değil, yeniden klonlanabilir):

- **MAL-Sync / MALSync** — `src/_provider/SingleAbstract.ts` (`getStateDiff`, `isValueDirty`), `_provider/AniList/single.ts`, `_provider/MyAnimeList_api/single.ts`, `_provider/Kitsu/single.ts` + `helper.ts`, `_provider/Simkl/single.ts` + `helper.ts`, `_provider/Shikimori/helper.ts`.
  - **Kilit bulgu:** MAL-Sync her yazmada yalnızca *kirli* alanı gönderir (`isValueDirty`); gönderilmeyen alan hedefte değişmez. Bizim eski davranışımız bunun tam tersiydi.
  - `SingleAbstract.fixDates()`: başlangıç boş + durum "izleniyor" + ilerleme > 0 → bugün; bitiş boş + durum "tamamlandı" → bugün.
- **Aniyomi** — `app/src/main/java/eu/kanade/tachiyomi/data/track/{anilist,myanimelist,kitsu,shikimori,bangumi,simkl}/`.
  - `Tracker.kt` yetenek bayrakları: `supportsReadingDates`, `supportsPrivateTracking`.
  - `myanimelist/MyAnimeList.kt`: "Rewatching" **ayrı durum değildir** → `status = watching` + `is_rewatching = true`.

---

## 2. Bulunan somut hatalar (kod kanıtlı)

Aşağıdaki satır numaraları **düzeltme öncesi** `HEAD = 1d33889` durumuna aittir.

### H1 — MAL'da puan/öncelik her eşitlemede sıfırlanıyordu
`MalSyncManager.kt` (eski hâli, `updateMalEntry` gövdesi):

```kotlin
append("&score=${SyncScores.tenPoint(entry.score) ?: 0}")   // entry.score == null → "score=0"
append("&priority=${entry.priority ?: 0}")                  // entry.priority == null → "priority=0"
```

MAL sözleşmesi: *"Set this value to 0 to remove the user's rating."*
Yani **Kitsu'dan yapılan bir ilerleme güncellemesi, MAL'daki puanı siliyordu** — kullanıcının şikâyet ettiği "verilerim kayboluyor" davranışının doğrudan kaynağı.

### H2 — Kitsu'da puan her güncellemede siliniyordu
`KitsuApiClient.updateLibraryEntryDetailed` (eski):

```kotlin
if (ratingTwenty != null && ratingTwenty > 0) put("ratingTwenty", ratingTwenty)
else put("ratingTwenty", JSONObject.NULL)     // ← puan YOKSA da null yazılıyor
```

`null`, "puanı kaldır" demektir. Yerel satırda puan yoksa (ör. AniList'ten eklenmiş, puanı olmayan kayıt) Kitsu'daki puan **her eşitlemede** siliniyordu.

### H3 — Kitsu'ya tekrar (rewatch) bilgisi hiç yazılmıyordu
`KitsuEntryExtras` yalnızca `startedAt, finishedAt, notes, isPrivate` taşıyordu. `reconsuming` / `reconsumeCount` **hiç gönderilmiyordu**; yani "Repeating" durumu Kitsu'da düz "current" olarak kalıyor, tekrar sayısı kayboluyordu. MAL-Sync `_provider/Kitsu/single.ts` bu iki alanı açıkça yazar.

### H4 — Shikimori PATCH yolu not/tekrar/cildi düşürüyordu
- `ShikimoriApiClient.createUserRate` (satır 829): `text`, `rewatches`, `volumes` **var**.
- `ShikimoriApiClient.updateUserRate` (satır ~878, eski): yalnızca `status`, `score`, `episodes/chapters` — **`text`, `rewatches`, `volumes` yok**.

Sonuç: kayıt bir kez oluştuktan sonra not/tekrar/cilt güncellemeleri Shikimori'ye hiç ulaşmıyordu.

### H5 — Simkl'e bırakılan kayıtlar geçersiz değerle gönderiliyordu
`SimklSyncManager.watchStatusToSimkl` (eski):

```kotlin
WatchStatus.Dropped -> "dropped"     // Simkl'de GEÇERSİZ
```

Simkl `to` alanı `notinteresting` bekler. Aynı hata `SimklSyncRepository.kt:316` içindeki ikinci kopyada da vardı. Yani **Simkl'e hiçbir zaman "bırakıldı" olarak yazılamıyordu**; istek reddediliyor, kayıt hiçbir listede görünmüyordu.

### H6 — AniList tarih temizleme sessizce çalışıyordu
`AniListSyncManager` eski davranışı: boş tarih → `{year:0, month:0, day:0}` gönderilmez, fakat `scoreRaw: null` **açıkça** gönderilirse AniList 400 döner (`"The score raw must be an integer."` — seanime #985, 17.10.2026). Ayrıca her eşitlemede `status/progress/notes/repeat` yeniden yazıldığı için AniList web'inde yapılan düzenlemeler eziliyordu.

> Not: `scoreRaw: null` gönderimi bu depoda zaten yoktu; risk **alan atlama** mantığına geçerken null göndermeye kaymaktı. Yeni kod bu yüzden `scoreRaw` için "asla null gönderme" kuralını yorumla sabitliyor.

---

## 3. Yeni mimari: alan farkı → yetenek süzgeci → eşzamanlı fan-out

```
MediaEntryRepository.update()
        │
        ├── previous = dao.getById(entry.id)          ← DB'deki ÖNCEKİ hâl
        ├── changed  = SyncFieldDiff.between(previous, current)   ← yalnız değişenler
        │
        └── ExternalListSyncManager.syncEntry(fields = changed)
                 │
                 ├── Run.fieldsFor("anilist")   → narrowTo("anilist")   ─┐
                 ├── Run.fieldsFor("mal")       → narrowTo("mal")        │  6 × async
                 ├── Run.fieldsFor("kitsu")     → narrowTo("kitsu")      ├─ aynı anda
                 ├── Run.fieldsFor("shikimori") → narrowTo("shikimori")  │  30 sn timeout
                 ├── Run.fieldsFor("bangumi")   → narrowTo("bangumi")    │
                 └── Run.fieldsFor("simkl")     → narrowTo("simkl")     ─┘
                          │
                          └── boş küme ⇒ hedefe İSTEK ATILMAZ, sonuç: "değişiklik yok"
```

### 3.1 `SyncField.kt` (yeni dosya)
- `enum class SyncField` — 13 alan: `STATUS, SCORE, PROGRESS, VOLUME_PROGRESS, START_DATE, END_DATE, NOTES, TAGS, FAVORITE, PRIVATE, REWATCH_COUNT, REPEAT_VALUE, PRIORITY` (satır 22).
- `object SyncFieldDiff.between(previous, current)` (satır 43):
  - `previous == null` (yeni kayıt) → `SyncField.ALL` — ilk yazım tam olmalı, aksi hâlde hedefte kayıt oluşmaz.
  - Alan karşılaştırmaları `null`/`""` ayrımını korur: **boşaltma da bir değişikliktir**.
  - `isRepeating` bayrağı değişirse `REWATCH_COUNT` **ve** `STATUS` kirli sayılır (MAL/Kitsu bayrağı durum yazımıyla taşır).
- `ProviderCapabilities` + `ProviderCapabilitiesCatalog` (satır 157): 6 sağlayıcının desteklediği alan kümesi, puan ölçeği (`HUNDRED`/`TWENTY`/`TEN`), durum sözlüğü ve `rewatchingAsStatus` bayrağı.
- `narrow()`: `rewatchingAsStatus = false` olan hedeflerde (MAL, Kitsu) `REWATCH_COUNT` gelirse `STATUS` de eklenir — böylece tekrar sayısı değişince `is_rewatching` / `reconsuming` bayrağı da yazılır.
- `Set<SyncField>.narrowTo(provider)`: bilinmeyen sağlayıcıda tam yazım korunur (ileriye dönük güvenlik).

### 3.2 `ExternalListSyncManager.kt`
- `Run.fields: Set<SyncField>` + `Run.fieldsFor(provider)` (satır 95–98).
- `syncEntry(..., fields: Set<SyncField> = SyncField.ALL)`; varsayılan yalnızca *yeni kayıt* ve *kuyruk yeniden denemesi* için doğrudur.
- Her sağlayıcı bloğu **kapı kontrollerinden sonra** `if (fields.isEmpty()) return SourceResult(X, OUTCOME_UP_TO_DATE)` yapar (satır 342, 458, 483, 520, 572, 607). Yani:
  - kapalı / bağlı değil / tür desteklenmiyor / kimlik çözülemedi → **eskisi gibi görünür gerekçe**,
  - değişiklik yok → **yeni görünür gerekçe**: `değişiklik yok`.
- Yeni sonuç sabiti `OUTCOME_UP_TO_DATE = "değişiklik yok"` (satır 51); `SyncResult.anySynced`/`needsRetry` bunu hata saymaz → gereksiz yeniden deneme kuyruğu oluşmaz.
- AniList favori sorgusu artık yalnızca `FAVORITE` alanı kirliyse yapılır (2 gereksiz istek tasarrufu).

### 3.3 Sağlayıcı yazıcıları

| Dosya | Değişiklik |
|---|---|
| `MalSyncManager.kt:38` | `fields` parametresi. Gövde yalnız kirli alanları ekler. `score` yalnız `SCORE` kirliyse (`tenPoint ?: 0`). Tarih: tam tarih → aynen; **boşaltıldı** → `0000-00-00`; parsiyel (`2016`, `2016-05`) → **gönderilmez**. `is_rewatching`/`is_rereading` durumla birlikte. Boş gövde → **istek atılmaz**. |
| `AniListSyncManager.kt:22` | `fields`. Var olan kayıtta yalnız kirli alanlar `variables`'a girer. `scoreRaw` **asla `null` gönderilmez**; temizleme `scoreRaw = 0`. Tarih temizleme yalnız diff `START_DATE`/`END_DATE` içeriyorsa `{year:null,month:null,day:null}` ile. Yeni kayıtta tam yazım. |
| `KitsuSyncManager.kt:132` | `fields`. `KitsuEntryExtras`'a `reconsuming` + `reconsumeCount` eklendi. Güncelleme yolu artık **attribute PATCH** (`patchLibraryEntry`) kullanır; gönderilmeyen attribute Kitsu'da korunur. İlerleme uzunluk koruması (`422 cannot exceed length`) korunur. |
| `KitsuApiClient.kt:727` | Yeni `patchLibraryEntry(token, entryId, attributes)` + `patchAttributes`. `updateLibraryEntryDetailed` korunur ama eşitleme yolunda artık kullanılmaz. |
| `ShikimoriSyncManager.kt:72` | `fields`. Rate kimliği biliniyorsa `patchUserRate`; `text`/`rewatches`/`volumes` artık PATCH'te de gider. Puan yalnız `SCORE` kirliyse. `patch` başarısızsa (kayıt yok/eski kimlik) resmî `createUserRate` upsert'üne düşer. |
| `ShikimoriApiClient.kt:883` | Yeni `patchUserRate`; `createUserRate`'in `score` parametresi `Int?` yapıldı (`null` = puana dokunma). |
| `BangumiSyncManager.kt:86` | `fields`. Durum değişmediyse `patchCollection` (PATCH) — kayıt yoksa 404 → `upsertCollection`'a düşer. Not/etiket sanitizasyonu (`sanitizeCommentForWrite`, `sanitizeTagsForWrite`) korunur. |
| `SimklSyncManager.kt:52` | `Dropped → "notinteresting"` (film dalı dâhil). `"notinteresting"` okuma eşlemesi eklendi. `fields`: puan yalnız `SCORE` kirliyse (`removeRating` artık kazara çağrılmıyor), `/sync/history` yalnız `PROGRESS`, `/sync/add-to-list` yalnız `STATUS`. |
| `SimklSyncRepository.kt:316` | Aynı `Dropped → notinteresting` düzeltmesi (ikinci kopya). |
| `MediaEntryRepository.kt:230` | `update()` önce DB'deki önceki satırı okur, `SyncFieldDiff.between` ile farkı çıkarır, `syncEntryIfPossible(..., fields = changedFields)` çağırır. Yeni kayıt (`insert`) yolu varsayılan `ALL` ile çalışır. |
| `PlayerScrobbleController.kt` | Scrobble artık yalnız `{PROGRESS, STATUS}` yazar. Oynatıcı puan/not/tarihe dokunmaz. |

---

## 4. Eşleme tabloları (kanıta dayalı)

### 4.1 Durum

| Kitsugi | AniList | MAL (anime / manga) | Kitsu | Shikimori | Bangumi | Simkl |
|---|---|---|---|---|---|---|
| Watching | `CURRENT` | `watching` / `reading` | `current` | `watching` | `type=3` (在看) | `watching` |
| Completed | `COMPLETED` | `completed` | `completed` | `completed` | `type=2` (看过) | `completed` |
| Planned | `PLANNING` | `plan_to_watch` / `plan_to_read` | `planned` | `planned` | `type=1` (想看) | `plantowatch` |
| Dropped | `DROPPED` | `dropped` | `dropped` | `dropped` | `type=5` (抛弃) | **`notinteresting`** |
| Paused | `PAUSED` | `on_hold` | `on_hold` | `on_hold` | `type=4` (搁置) | `hold` |
| Repeating | `REPEATING` | `watching` + `is_rewatching=true` | `current` + `reconsuming=true` | `rewatching` | (yok) | (yok) |

### 4.2 Puan

| Hedef | Alan | Aralık | "Puan yok" davranışı |
|---|---|---|---|
| AniList | `scoreRaw` (Int) | 0–100 | `0` (alan `null` **gönderilmez**) |
| MAL | `score` | 0–10 | `0` = puanı sil |
| Kitsu | `ratingTwenty` | 2–20 | `null` = puanı sil (yalnız kullanıcı silerse) |
| Shikimori | `score` | 0–10 | `0` = puanı sil |
| Bangumi | `rate` | 0–10 | `0` |
| Simkl | `user_rating` | 1–10 | `removeRating` |

### 4.3 Tarih biçimleri

| Hedef | Biçim | Boşaltma |
|---|---|---|
| AniList | `FuzzyDateInput {year, month, day}` — üçü birlikte | `{null, null, null}` (yalnız diff'te varsa) |
| MAL | `YYYY-MM-DD` | `0000-00-00` |
| Kitsu | `YYYY-MM-DDT00:00:00.000Z` | `null` (yalnız diff'te varsa) |
| Shikimori / Bangumi / Simkl | **desteklenmez** | — |

---

## 5. Asenkronluk ve dayanıklılık

| Gereksinim | Uygulama | Kanıt |
|---|---|---|
| Altı kaynak eşzamanlı | `coroutineScope { listOf(async{…}×6).awaitAll() }` | `ExternalListSyncManager.kt:153–161` |
| Bir kaynak donarsa diğerleri durmaz | her blok `withTimeoutOrNull(30_000)` | `:57`, `:314` |
| Kaynak yoksa görünür gerekçe | `outcomes` haritası: `eşitlendi`, `kimlik çözülemedi`, `tür desteklenmiyor`, `kapalı`, `bağlı değil`, `hata`, `zaman aşımı`, `çağıran yazdı`, **`değişiklik yok`** | `aggregate()` `:898` |
| Yeniden başlatma / çevrimdışı | `SyncResult.needsRetry` yalnız gerçek hatada `true`; `PendingSyncDrainer` kuyruğu boşaltır; `değişiklik yok` kuyruğa **girmez** | `SyncResult:1027` |
| Kaynak ayrıcalıklı değil | `run.allowFanOut` + `CrossSyncPolicy.allowsWrite` her hedef için ayrı; kaynak satırın kendisi "çağıran yazdı" ile işaretlenir | `prepare()` |

---

## 6. Doğrulama

### 6.1 Otomatik (bu ortamda yapılabilen)

```
python3 /tmp/kcheck.py $(git status --porcelain | awk '{print $2}' | grep '\.kt$')
→ files: 14 with errors: 0
```

Tam kaynak taraması (1280 dosya) 6 **önceden var olan** tree-sitter yanlış-pozitifi verir
(`AppRootTabPages.kt`, `LinkedAccountVault.kt`, `BangumiApiClient.kt`, `SimklApiClient.kt`,
`TmdbDiscoverClient.kt`, `KitsugiAccountContent.kt`) — bu değişikliklerle ilgisi yoktur.

### 6.2 Kullanıcının çalıştırması gerekenler (bu ortamda JDK/Android SDK yok)

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests '*SyncFieldDiffTest'
./gradlew :app:testDebugUnitTest --tests '*CrossSync*'
```

Yeni test: `app/src/test/java/com/kitsugi/animelist/data/auth/SyncFieldDiffTest.kt`
(16 test) — sözleşmeyi sabitler:
- `previous yoksa tam yazım`, `hiçbir alan değişmediyse boş küme`
- `sadece puan değişti` → **yalnız** `SCORE`
- `not boşaltıldı` → `NOTES` (hedefte silinmeli)
- `tekrar bayrağı durumu da kirletir` → `STATUS + REWATCH_COUNT`
- `desteklenmeyen alan hedefe yazılmaz` (Simkl'de tarih/cilt yok; Kitsu'da cilt yok)
- `mal tekrar sayısı durumu tetikler`, `anilist tekrar sayısı durumu tetiklemez`
- `altı sağlayıcı tanımlı`

### 6.3 Elle senaryolar (cihazda)

1. **Kitsu'dan ilerleme:** Kitsu listesinde bir animeyi 3 → 4 yap. MAL/AniList/Shikimori/Bangumi/Simkl'de ilerleme 4 olmalı; **MAL puanı ve Kitsu dışındaki notlar değişmemeli**.
2. **MAL'da puan silme:** MAL web'inde puanı 8 yap, uygulamada yerel puanı **boş** bırakıp yalnız ilerlemeyi değiştir → MAL'da puan **8 kalmalı** (eski kodda 0'a düşüyordu).
3. **Kitsu'da puan koruma:** Kitsu'da puanı 8 yap, uygulamada yerel puanı boş bırak, ilerleme değiştir → Kitsu puanı **8 kalmalı**.
4. **Tekrar:** Bir animeyi "Tekrar izleniyor" yap → Kitsu'da `reconsuming=true`, `reconsumeCount=1`; MAL'da `is_rewatching=1`; Shikimori'de `rewatches=1`.
5. **Bırakıldı → Simkl:** Simkl'de kayıt "Not Interested" altında görünmeli (eskiden hiç görünmüyordu).
6. **Hiç değişiklik yok:** Bir satırı aç, değiştirmeden kaydet → sonuç raporunda her hedef için `değişiklik yok`; hiçbirinde `eşitlendi` yazmamalı, ağ isteği olmamalı.
7. **Donan kaynak:** Uçak modunda 3 kaynağı aç, 3'ünü kapat → yanıt 30 sn içinde dönmeli, açık olanlar gerekçeleriyle listelenmeli.

---

## 7. Dürüst sınırlar (gizlenmiyor)

| Konu | Durum |
|---|---|
| **TMDB** | Yazma ucu yok (liste/oturum API'si entegre değil); yalnız kimlik kaynağı. Eşitleme raporunda "hedef" olarak sayılmaz. |
| **Simkl manga** | Simkl manga desteklemez → `tür desteklenmiyor` gerekçesiyle atlanır. |
| **Simkl tarih/tekrar** | API'de yok; sessizce gönderilmez, raporda hedef "eşitlendi" görünür (desteklenen alt küme için). |
| **Bangumi tarih** | Koleksiyon API'sinde yok. |
| **Shikimori tarih** | `user_rates`'te yok. |
| **MAL favori** | MAL'de favori ucu yok → yalnız AniList→MAL yönünde "rapor" mümkün, yazma yok. |
| **Derleme** | Bu ortamda JDK/Android SDK olmadığı için **derleme ve birim test çalıştırılmadı**; doğrulama tree-sitter sözdizimi + kod okuma düzeyinde. `./gradlew :app:compileDebugKotlin` sonucu bekleniyor. |
| **`scoreRaw: null`** | AniList açık `null`'ı reddediyor (seanime #985). Kod bu nedenle puanı temizlerken `0` gönderiyor; alan hiç gönderilmiyor da değil, çünkü kullanıcı puanı sildiyse hedefte de silinmeli. |

---

## 8. Dosya değişiklikleri

```
 yeni  app/src/main/java/com/kitsugi/animelist/data/auth/SyncField.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ExternalListSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/AniListSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/MalSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/KitsuApiClient.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/KitsuSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/BangumiSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/SimklSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/repository/SimklSyncRepository.kt
       app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt
       app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt
 yeni  app/src/test/java/com/kitsugi/animelist/data/auth/SyncFieldDiffTest.kt
```

---

## 9. Kaynak listesi

**Resmî dokümanlar**
1. AniList GraphQL Reference — `SaveMediaListEntry`: https://docs.anilist.co/reference/mutation
2. AniList mutations rehberi: https://docs.anilist.co/guide/graphql/mutations
3. AniList media list sorgusu: https://docs.anilist.co/guide/graphql/queries/media-list
4. MAL API v2 referansı: https://myanimelist.net/apiconfig/references/api/v2
5. Kitsu API (JSON:API): https://kitsu.app/api/edge/library-entries
6. Shikimori API v2: https://shikimori.one/api/doc/2.0
7. Bangumi API v0: https://bangumi.github.io/api/
8. Simkl API: https://api.simkl.org/ (senkronizasyon: `/sync/add-to-list`, `/sync/history`, `/sync/ratings`)

**Açık kaynak kod (klone edilip okundu)**
9. MALSync/MALSync — `src/_provider/SingleAbstract.ts`, `AniList/single.ts`, `MyAnimeList_api/single.ts`, `Kitsu/{single,helper}.ts`, `Simkl/{single,helper}.ts`, `Shikimori/helper.ts`, `pages-sync/syncPage.ts`: https://github.com/MALSync/MALSync
10. aniyomiorg/aniyomi — `data/track/{anilist,myanimelist,kitsu,shikimori,bangumi,simkl}`: https://github.com/aniyomiorg/aniyomi

**Hata raporu**
11. seanime #985 — AniList `scoreRaw: null` → 400 *"The score raw must be an integer"* (17.10.2026).

---

## 10. Ek: ikinci geçişte kapatılan hatalar (commit `1e92d59`)

Alan bazlı yazıma geçtikten sonra kodu tekrar taradım; altı yeni hata daha çıktı:

| # | Hata | Kanıt | Düzeltme |
|---|---|---|---|
| E1 | **Kuyruktan yeniden deneme tam yazım yapıyordu** — çevrimdışıyken kuyruğa giren "ilerleme" işi, bağlantı gelince satırın tamamını yazıp hedeflerdeki puan/notu ezebilirdi. | `PendingSyncDrainer.drain` → `syncEntry(context, entry)` (varsayılan `ALL`) | İşin alan kümesi JSON'a `_syncFields` olarak gömülür; drain yalnız o alanları yazar. Eski kayıtlar (`_syncFields` yok) tam yazım olarak okunur — geriye dönük uyumlu. |
| E2 | **AniList'te tanımsız GraphQL değişkeni** — puan temizlenirken `scoreRaw: 0` gönderiliyordu ama `$scoreRaw` dokümanda tanımlı değildi. | `AniListSyncManager` mutasyon metni `scoreRaw != null` iken kuruluyordu | Puan kararı (`null` / `0` / değer) **doküman kurulmadan önce** verilir; değişken yalnız tanımlıysa gönderilir. `scoreRaw: null` hiçbir durumda gönderilmez. |
| E3 | **MAL "eşitlendi" yalanı** — yazılacak geçerli alan yokken (ör. yalnız parsiyel tarih değişti) istek atılmıyor ama sonuç "eşitlendi" görünüyordu. | `updateMalEntry` `Unit` dönüyordu | `Boolean` döner; `false` → `değişiklik yok`. |
| E4 | **Simkl notu hiç yazılmıyordu** ama yetenek matrisi `NOTES` destekliyor diyordu. | `SimklBatchEntry` alanlarında not yok; `private_memo` gönderilmiyor | `SimklBatchEntry.privateMemo` + `private_memo` gövdesi; not değiştiyse `add-to-list` tetiklenir (not silinirse boş string). |
| E5 | **Bangumi boş gövde** — yalnız not değişip not boşaltıldığında `PATCH {}` 400 üretiyor, ardından gereksiz tam upsert çalışıyordu. Ayrıca not düzenlemesi tüm bölümleri yeniden işaretliyordu. | `hasAnyArg` yok; `setProgress` bloğu koşulsuz | Yazacak argüman yoksa istek atılmaz (`yazılacak değişiklik yok`); bölüm işaretleme yalnız `PROGRESS`/`STATUS` kirliyken. |
| E6 | **MAL gövdesi `&` ile başlıyordu** — tek alan gönderildiğinde gövde `&score=8` oluyordu (eski kodda hep `status` önce geldiği için görünmüyordu). | `buildString` ilk parçayı da `&` ile ekliyor | Gönderim öncesi `trimStart('&')`. |

Ayrıca Simkl'de "hiç istek atılmadı" durumu artık `eşitlendi` değil `değişiklik yok` olarak raporlanıyor (kapasite süzgeci tüm alanları eleyebilir).

---

## 11. Üçüncü geçiş: satır satır denetim (commit `f1198c6`, `ae0f897`, `b0b530a`)

"En ufak kod atlanmasın" talebiyle tüm yazma yolları tek tek okundu. Bulunan **14 ek hata**:

| # | Hata | Neden kritik | Düzeltme |
|---|---|---|---|
| E7 | **Tam yazım / kısmi yazım ayrımı kayboluyordu.** `Run.fieldsFor(provider)` her hedefe *süzülmüş* alt küme verdiği için yazıcı içindeki `fields == ALL` testi asla doğru olmuyordu. | "Tam yazımda boş puan gönderme" koruması hiç devreye girmiyordu → tam yazımda puan/not yine silinebiliyordu. | `Run.isFullWrite` + yazıcılara açık `fullWrite` parametresi. |
| E8 | MAL'da tam yazımda `tags=` ve `comments=` boş gönderiliyordu. | MAL'daki etiket/not silinir. | Boş değer yalnız diff'te gönderiliyor. |
| E9 | AniList'te tam yazımda `notes: ""` ve favori toggle. | Not silinir; yerelde favori yoksa AniList favorisi kaldırılır. | Not yalnız dolu/diff'te; favori tam yazımda **yalnız ekleme** yönünde. |
| E10 | Simkl'de tam yazımda `removeRating`. | Simkl puanı silinir. | `removeRating` yalnız diff'te. |
| E11 | Bangumi: `writeAll` "durum değişti" ile "tam yazım"ı karıştırıyordu; ayrıca yalnız ilerleme değişince koleksiyon yazımı atlanınca **bölüm işaretleme de atlanıyordu**. | Puan/not gereksiz eziliyor; anime ilerlemesi Bangumi'ye hiç gitmiyor. | `fullWrite` / `statusChanged` ayrıldı; koleksiyon atlanırken ilerleme yine yazılıyor. |
| E12 | **Oynatıcı scrobble'ı yerel DB'yi hiç güncellemiyordu.** | Liste eski bölümü gösteriyor; sonraki bir tam yazım eski ilerlemeyi tüm kaynaklara geri yazabiliyordu (ilerleme geri düşer). | Scrobble sonrası yerel satır yazılır (`max(progress)`, Completed/Repeating korunur) + tarih otomasyonu. |
| E13 | MAL'de `Repeating → "watching"`. | Tamamlanmış kayıt MAL'da "İzleniyor"a düşüyor, tamamlanma bozuluyor. | `completed` + `is_rewatching` (MAL'ın kendi modeli). |
| E14 | AniList 429'da `Retry-After` kadar **bloklayan** bekleme (120 sn'ye kadar). | `withTimeoutOrNull(30s)` bloklayan çağrıyı kesemez → tek kayıt tüm yayılımı dakikalarca kilitler. | Bekleme 5 sn ile sınırlandı; geri çekilme sayacı gerçek değerle işaretlenir. |
| E15 | Kuyrukta aynı satır için yeni iş eskisini **alan kümesi birleştirmeden** siliyordu. | Çevrimdışıyken önce ilerleme, sonra not değiştirilirse ilerleme güncellemesi kaybolur. | Alan kümeleri birleştirilir. |
| E16 | Kuyruk boşaltmada başarı ölçütü "mesaj var mı"ydı. | Yazacak şey olmayan işler 5 kez denenip sonsuza kadar kuyrukta kalıyordu. | Ölçüt `!needsRetry` (yalnız gerçek hata). |
| E17 | Başarı yolunda kaynak bazlı sonuç özeti gösterilmiyordu. | "Simkl'e neden yazmadı?" cevabı hiç görünmüyordu. | `eşitlendi` dışında sonuç varsa özet bildirime eklenir. |
| E18 | `hiddenFromStatusLists` (listede gizle) hiç eşitlenmiyordu. | Düzenleme ekranındaki anahtar hiçbir yere gitmiyordu. | `SyncField.HIDDEN_FROM_STATUS_LISTS` + AniList yazımı. |
| E19 | Kitsu tam yazımda puan/not/tarih `null` gönderiyordu. | Kitsu'daki değerler silinir. | Boş değer yalnız diff'te; tam yazımda yalnız dolu değerler. |
| E20 | Değişiklik yokken bile altı kaynağa istek atılıyordu. | Gereksiz ağ yükü ve gürültülü bildirim. | Diff boşsa ağa çıkılmaz. |

### Doğrulama durumu

- Tree-sitter: 1284 Kotlin dosyası tarandı; **6 bilinen yanlış pozitif** (bu değişikliklerle ilgisiz).
- `SyncFieldDiffTest` artık **22 test** (alan farkı, yetenek matrisi, tekrar bayrağı, tarih/puan/öncelik/favori/gizlilik kapsamı).
- **Derleme ve çalışma zamanı testi bu ortamda YAPILAMADI** (JDK/Android SDK yok). Bu rapordaki hiçbir madde "derlendi" garantisi taşımaz; `./gradlew :app:compileDebugKotlin` çalıştırılmalıdır.
