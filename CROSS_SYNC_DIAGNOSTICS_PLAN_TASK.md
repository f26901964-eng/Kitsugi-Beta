# Kitsugi Çapraz Eşitleme — Hata/Uyarı Teşhisi ve Düzeltme Planı (Plan & Görev Kaydı)

**Tarih:** 2026-10-07  
**Kapsam:** Çapraz platform eşitleme raporundaki aşırı hata/uyarı sayısının kök neden analizi, platform bazlı düzeltmeler ve eşitleme panelinin tam ekran / yönlendirmeye duyarlı yeniden tasarımı.  
**Dal:** `arena/5fef9c1c-kitsugi-beta` (temel: `5ef3c9f chore(release): bump version to v2.4.194`)

> Not: Teşhis önce ekran görüntüsündeki sayılar ve kaynak kodun satır satır incelenmesiyle yapıldı; ardından kullanıcının Drive üzerinden paylaştığı 5 MB'lık rapor (`Kitsugi_CrossSync_Report_20261007_223112_904.txt`, 493 sorun / 1854 kayıt) örneklenerek doğrulandı (bkz. Bölüm 5 → "Rapor doğrulaması"). Rapor örnekleri teşhisle birebir örtüştü; ek kod değişikliği gerekmedi. **Sürüm 3:** kullanıcının "hiç eklemediğim anime/dizi/filmler hesaplarıma ekleniyor" bildirimi üzerine yanlış içerik ekleme vektörleri denetlendi ve "doğrulanamayan kimlikle asla yeni kayıt eklenmez" kuralı uygulandı (bkz. Bölüm 7).

---

## 1. Kullanıcının bildirdiği sorun

1. Eşitleme raporu yüzlerce hata ve uyarı üretiyor; özellikle Simkl 200 hata, Kitsu 66 hata, AniList 13 hata ve tüm platformlarda yüzlerce "atlandı".
2. "Simkl grubu 1: Simkl 25 öğeden 16 tanesini onayladı; 9 eşleşmedi" gibi **kısmi başarı** satırları ve en sondaki "Eşitleme kısmen tamamlandı…" özeti **hata** olarak işaretleniyor.
3. Eşitleme paneli bottom-sheet olarak açılıyor; tam ekran, dikey/yatay yönlendirmeye uyumlu ve daha kompakt olması isteniyor.

---

## 2. Platform bazlı kök neden teşhisi

### 2.1 Simkl — "200 hata / 297 atlandı"

| Bulgu | Kaynak | Etki |
|---|---|---|
| `SimklBatchResponse.isSuccess` yalnızca **tüm** öğeler onaylanınca true oluyordu; 35'lik grupta tek bir `not_found` bile tüm grubu "hata"ya çeviriyordu. | `SimklApiClient.addToListBatchDetailed` | Kısmi eşleşme → grup hatası |
| `AuthViewModel`, bir grup için herhangi bir hata metni dönünce `simklErrors += chunk.size` yapıyordu. | `AuthViewModel.syncPlatforms` (Simkl toplu bölümü) | 6 grup × ~35 = ~200 "hata" (gerçekte çoğu yazıldı) |
| `historyBatchDetailed` dizi (TV) ilerlemesi için her zaman `false`; `ratingsBatchDetailed` kısmi eşleşmede `false` → ikisi de grup hatası olarak loglanıyordu. | `SimklSyncManager.syncBatchToSimkl` | Aynı grup için 2–3 ayrı hata satırı |
| `not_found` kayıtları başlıkla eşlenmiyordu; rapor "9 eşleşmedi" diyor ama hangileri olduğunu söylemiyordu. | `SimklSyncContract.receipt` yalnızca sayıyordu | Teşhis edilemeyen uyarılar |
| `rawKitsuId = malId > 300M` sınırı Shikimori (400M+) kimliklerini de Kitsu ID olarak gönderiyordu. | `SimklSyncManager` | Yanlış `ids.kitsu` → not_found |

**Düzeltme:** Kayıt bazlı makbuz. `SimklSyncContract.Receipt.unmatched` (`UnmatchedItem`: ids/title/year) eklendi; `SimklApiClient.postSyncEnvelope` ortak POST/429/yeniden deneme katmanı yazıldı; `SimklBatchResponse` artık `notFoundItems`, `transportFailed`, `isPartial` taşıyor. `SimklSyncManager.SyncResult` `failedCount`, `warnings`, `unmatchedTitles`, `unsupportedProgressTitles` ile genişletildi. `AuthViewModel`: `simklAdded += addedCount`, `simklSkipped += notFoundCount`, `simklErrors += failedCount` (yalnızca HTTP/ağ seviyesinde yazılamayanlar). Eşleşmeyen kayıtlar, dizi ilerlemesi sınırı ve kısmi geçmiş/puan uyarıları **eşitleme sonunda birer özet uyarı** olarak (ayrıntıda başlık listesiyle) yazılıyor.

### 2.2 Kitsu — "66 hata / 134 atlandı"

| Bulgu | Kaynak | Etki |
|---|---|---|
| Kimlik çözümleme sırası: önbellek → 300M ofset → ARM (yalnızca anime) → gevşek başlık araması. Resmi Kitsu `mappings` uç noktası hiç kullanılmıyordu. | `KitsuSyncManager.syncEntryToKitsu` | Manga ve ARM'da olmayan anime için "Kitsu ID bulunamadı" hataları |
| `findLibraryEntryId` 429'da `check()` ile istisna fırlatıyordu; her kayıtta 2–4 istek atılırken limiter yalnızca kayıt başına 1 kez çağrılıyordu. | `KitsuApiClient`, `AuthViewModel` | Hız sınırı → istisna → "hata" |
| `createLibraryEntry`/`updateLibraryEntry` HTTP gövdesini yutuyordu; raporda neden görünmüyordu. | `KitsuApiClient` | Teşhis edilemeyen "Kitsu güncellenemedi" |
| Başlık aramasında eşit puanlı adaylar (aynı adlı sezon/remake) ilk gelen kazanıyordu. | `KitsuApiClient.lookupKitsuId` | Yanlış kayda yazma riski |
| Kitsu içe aktarımı `year` taşımıyordu → kimlik doğrulaması yıl olmadan yapılamıyor, "inceleme gerekli" atlamaları artıyordu. | `KitsuImportManager` | Gereksiz atlama |

**Düzeltme:** `KitsuApiClient.lookupKitsuIdByExternalMapping(externalSite, externalId)` (`/mappings?filter[externalSite]=…&filter[externalId]=…&include=item`) eklendi. Çözümleme sırası: bilinen ID → MAL→Kitsu önbelleği → **Kitsu mappings (MAL, sonra AniList)** → ARM → sıkı başlık araması (eşit puanlı belirsizlikte **null**). Her Kitsu isteği öncesi `PlatformRateLimiter.acquire("kitsu")`; `findLibraryEntryId`/create/update 429'da en fazla 3 deneme; `KitsuWriteResult` HTTP kodu + sunucu mesajı döndürüyor. `KitsuSyncManager.SyncResult.unresolvedMedia` eklendi → çözümlenemeyen kayıt **hata değil atlama** (sonda tek özet uyarı). Kitsu içe aktarımı artık `startDate` yılını taşıyor.

### 2.3 AniList — "13 hata / 200 atlandı"

| Bulgu | Kaynak | Etki |
|---|---|---|
| AniList fiili limiti 30 istek/dk; limiter 750 ms (80/dk) ile ayarlıydı. `postAniList` 429'da bekleyip **yine de istisna** fırlatıyordu. | `PlatformRateLimiter`, `AniListSyncManager.postAniList` | 429 → "Eklenemedi/Güncellenemedi" hataları |
| `resolveRealMalId` her AniList-sentetik kayıt için `Media(idMal)` sorgusu atıyordu; içe aktarım `idMal`'ı zaten getirdiğinden sonuç her zaman boş, ama kota tüketiyordu. | `AuthViewModel.resolveRealMalId` | Gereksiz istek → daha fazla 429 |
| `updateAniListEntry` hem "medya çözülemedi" hem "mutasyon id döndürmedi" durumunda `null` dönüyordu; ikisi de hata sayılıyordu. | `AniListSyncManager` | Atlama ile hatanın karışması |

**Düzeltme:** Aralık 2100 ms (~28/dk); `postAniList` 429'da `Retry-After` (yoksa 60 sn) bekleyip aynı isteği en fazla 3 kez yineliyor. Gereksiz `resolveMalIdFromAniList` adımı kaldırıldı. `updateAniListEntry` yalnızca medya çözülemediğinde `null` döner (→ atlama + özet uyarı); mutasyon kimlik döndürmezse açıklayıcı istisna fırlatır (→ gerçek hata).

### 2.4 MyAnimeList / Shikimori — "185 / 393 atlandı"

| Bulgu | Kaynak | Etki |
|---|---|---|
| MAL/Shikimori gerçek MAL ID ister. MAL ID'si çözülemeyen her kayıt için **tek tek uyarı** yazılıyordu. | `AuthViewModel` | Yüzlerce uyarı satırı |
| Shikimori içe aktarımı yalnızca romaji başlık ve yıl olmadan geliyordu; İngilizce başlık/yıl eksikliği kimlik kontrolünde alias uyuşmazlığı üretiyordu. | `ShikimoriApiClient.fetchAllUserRates` | "inceleme gerekli" atlamaları |

**Düzeltme:** Kayıt bazlı atlamalar bilgi satırı (canlı günlüğe girmez) + platform başına **tek özet uyarı** (ayrıntıda başlıklar). Shikimori içe aktarımı `aired_on`/`released_on` yılını ve `english[0]` başlığını taşıyor. AniList içe aktarımı `seasonYear` boşsa `startDate.year` kullanıyor (manga için kritik).

### 2.5 Eşleştirme (tüm platformları etkileyen) — sahte "kimlik doğrulaması gerekli"

| Bulgu | Kaynak | Etki |
|---|---|---|
| `sameTitleIdConflicts`: aynı başlığı taşıyan başka bir grupla sağlayıcı kimliği çelişiyorsa (ör. Berserk 1997 vs 2016, HxH 1999 vs 2011, aynı adlı sezonlar) gelen kayıt **tamamen izole** edilip hiçbir hesaba yazılmıyordu. Oysa `sameMedia` çelişen kimlikleri zaten birleştirmez; kayıt kendi kimliğiyle güvenle eşitlenebilir. | `AuthViewModel.clusterEntry` | Her platformda yüzlerce "atlandı" + uyarı |
| `unrelatedTitleIds`: aynı sağlayıcı kimliği (aynı MAL ID) paylaşıp alias'ları farklı olan kayıtlar (Simkl İngilizce, Shikimori romaji, Kitsu kanonik) "inceleme gerekli" sayılıyordu. | `AuthViewModel.clusterEntry` | Aynı etki |
| `bestStatus`, Simkl'in `total` (yayınlanmış bölüm sayısı) değerinden "Tamamlandı" çıkarıyordu → devam eden diziler bitmiş gibi işaretlenebiliyordu. | `AuthViewModel` Faz 3 | Yanlış durum yazma riski |
| Tek kaynaklı gruplarda kayıt kendi kendine "güncelleniyordu" (yalnızca çıkarım farkı). | `AuthViewModel` Faz 3 | Gereksiz yazma |

**Düzeltme:** Çelişen kimlikli aynı başlıklar normal akışta ayrı grup olur (bilgi notu, canlı günlüğe girmez). Ortak kimlikli alias farkı **yalnızca** iki tarafta da yıl biliniyor ve |Δyıl| > 1 ise (hatalı sağlayıcı eşlemesi şüphesi) incelemeye düşer. "Tamamlandı" çıkarımı için Simkl toplamları yok sayılır. Tek kaynaklı gruplar kendi kaydını güncellemez (eklemeler etkilenmez).

### 2.6 Rapor özeti

- Son "Eşitleme kısmen tamamlandı…" satırı `isError=true` ile yazılıyor, issue sayacını şişiriyor ve kırmızı görünüyordu → artık bilgi satırı; metin "N işlem yazılamadı, M kayıt atlandı" şeklinde sayısal.

---

## 3. Uygulanan görevler

- [x] `SimklSyncContract`: `UnmatchedItem`, `Receipt.unmatched`, `not_found` dizilerinin ayrıştırılması (boş/no-op makbuz reddi korunuyor; `scripts/tests/SyncContractChecks.kt` ile uyumlu). Nesne `internal` → public (API'den açığa çıktığı için).
- [x] `SimklApiClient`: `postSyncEnvelope` (429/Retry-After, geçici hata yeniden denemesi, makbuz hataları tek seferlik), `historyBatchReceipt`, `ratingsBatchReceipt`, `SimklBatchResponse(+notFoundItems, transportFailed, isPartial)`; Boolean sürümler delegasyonla korundu.
- [x] `SimklSyncManager`: kayıt bazlı sonuç (`failedCount`, `warnings`, `unmatchedTitles`, `unsupportedProgressTitles`), `describeUnmatched` ile not_found → başlık eşleme, Kitsu ID aralığı 300M–400M sınırı, manga/eksik kimlik uyarıya dönüştü.
- [x] `KitsuApiClient`: `lookupKitsuIdByExternalMapping`, `KitsuWriteResult`, `createLibraryEntryDetailed`/`updateLibraryEntryDetailed`, 429 güvenli `findLibraryEntryId`, her istekte limiter, belirsiz başlık eşleşmesinde null, `KitsuLibraryEntry.startYear`.
- [x] `KitsuSyncManager`: `resolveKitsuMediaId` (sıralı çözümleyici), `SyncResult.unresolvedMedia/resolvedVia`, HTTP nedenli hata metinleri.
- [x] `AniListSyncManager`: 429 yeniden deneme, `updateAniListEntry` null/istisna ayrımı.
- [x] `PlatformRateLimiter`: anilist 750 → 2100 ms.
- [x] `AuthViewModel`: `clusterEntry` gevşetmeleri (2.5), `recordSkip`/`flushSkipSummaries` ile platform başına özet uyarı, Simkl kayıt bazlı muhasebe + 3 özet uyarı, `bestStatus` Simkl toplamı dışlama, `singleSourceGroup` koruması, gereksiz AniList sorgusunun kaldırılması, Kitsu limiter çağrılarının istemciye taşınması, bilgi niteliğinde son özet.
- [x] İçe aktarımlar: `KitsuImportManager` (yıl), `ShikimoriApiClient`/`ShikimoriImportManager` (yıl + İngilizce başlık), `AniListImportManager` (`startDate.year` yedeği).
- [x] `KitsugiCrossSyncDialog`: `KitsugiSheetOrDialog(fullScreen = true)`; `statusBarsPadding` + `displayCutoutPadding`; dikeyde tek sütun (ilerleme → hesaplar → günlük [kalan yükseklik] → alt eylem çubuğu), yatayda iki sütun (sol: ilerleme/hesaplar/eylemler, dikey kaydırılabilir; sağ: filtreler + günlük); `KitsugiPlatformLogo`'lu kompakt hesap kartları; filtre çiplerinde hata/uyarı sayıları; 40 dp butonlar; uyarı satırları için ayrı ikon/renk; paylaş/kaydet/durdur/yeniden başlat ve iptal onayı korundu.

---

## 4. Güvenlik ve davranış notları

- Eşitleme ekleme/güncelleme odaklı kalır; **silme davranışı eklenmedi**.
- Kimlik gevşetmeleri yalnızca sağlayıcı kimliği paylaşılan ya da kimlikleri çelişen (dolayısıyla zaten birleştirilmeyen) durumları etkiler. Başlık-tabanlı birleştirme kuralları (`MediaIdentity.sameMedia`) değişmedi; yıl uyuşmazlığı hâlâ birleştirmeyi engeller.
- Kitsu başlık araması artık belirsiz eşleşmelerde yazmak yerine atlar (yanlış kayda yazma riski azaltıldı).
- 429 yeniden denemeleri toplam süreyi uzatabilir (AniList'te kayıt başına en fazla ~3 × 60 sn); karşılığında kayıtlar hata yerine başarıyla yazılır.
- "Atlandı" sayaçları (platform kartlarındaki rakamlar) değişmedi; yalnızca kayıt başına uyarı satırları özetlendi. Kullanıcı ayrıntı için özet satırını genişletip tam başlık listesini görebilir.

---

## 5. Doğrulama durumu

- `git diff --check`: temiz.
- Kotlin ayrıştırma (kotlinc 2.0.21, sözdizimi düzeyi): 13 değişen dosyada ayrıştırma hatası yok.
- `SimklSyncContract.kt`: org.json taklit sınıflarıyla anlamsal derleme başarılı.
- Android Gradle derlemesi / cihaz testi: **yapılmadı** (bu ortamda Android SDK/Gradle yok; kullanıcı testi üstlendi).

### Rapor doğrulaması (2026-10-07, Drive'dan okunan gerçek rapor)

Rapor 5.023.375 bayt olduğu için tamamı değil, başlangıç ve dosya içinde farklı konumlardan alınan ~8 örnek (yaklaşık 40 sorun kaydı + 2 Simkl grup başlığı) okundu. Görülen kalıplar ve kodla eşleşmesi:

| Rapor kaydı (örnek) | Kalıp | İlgili düzeltme |
|---|---|---|
| #1 Evangelion: End of Evangelion — Kitsu kaydı `Tür/yıl: Anime / bilinmiyor`, Simkl kaydı 1997; ortak `mal:32`; "başlık alias'ları uyuşmuyor" | Kitsu kayıtlarında yıl yok + ortak kimlikte alias farkı → izole | Kitsu içe aktarımına `startYear`; `unrelatedTitleIds` yalnızca iki yıl da biliniyor ve mutlak yıl farkı > 1 ise |
| #2 Azumanga Daioh, #3 Elf wo Karu Mono-tachi II, #4 Samurai Pizza Cats, #5 Bleach Movie 1, #7 Evangelion 3.0, #8 Toradora! SOS! | Aynı kalıp (Kitsu yıl bilinmiyor vs Simkl/MAL) | Aynı |
| #114 Tokyo Ghoul:re 2nd Season (MAL, 2018) vs Tokyo Ghoul:re 2 (AniList, 2018), ortak `mal:37799` | Yıllar eşit olsa bile alias farkı izole ediyordu | Aynı (yıllar eşit → birleşir) |
| #115 Golden Time `mal:36789` vs Golden Time `mal:17895`; #261/#262 5-toubun no Hanayome ∬ / ∽ vs Go-toubun no Hanayome; #405 Komi-san (Kitsu `mal:48926`) vs Simkl `mal:50631` | "aynı tür/başlık için sağlayıcı kimlikleri çelişiyor" → gelen kayıt hiçbir hesaba yazılmıyordu (MAL kaynaklı kayıtlar dahil) | `sameTitleIdConflicts` artık izole etmez; çelişen kimlikler ayrı grup olarak kendi kimliğiyle yazılır |
| #406 Komi-san 2, #463 Sugar Apple Fairy Tale Part 2, #464 Jigokuraku 2nd Season: "Birden fazla olası grup" — iki aday grup da **aynı** `mal` kimliğini taşıyor (Aday 1 AniList+MAL, Aday 2 Simkl) | Zincirleme etki: Simkl kaydı alias farkı yüzünden kendi grubuna izole edilince üçüncü kaynak iki grup görüyor | Üstteki gevşetmeyle Simkl kaydı birleşir; belirsizlik ortadan kalkar |
| #9 `[HATA] [Kitsu] Oni Chichi (Kitsu kütüphanesine eklenemedi)` — HTTP kodu/gövde yok | Gövde yutuluyordu; muhtemel neden Kitsu'daki mevcut kaydın başka gruba izole edilmesi → POST 422 (yinelenen) ya da 429 | `KitsuWriteResult` HTTP kodu+mesaj; 429'da yeniden deneme; izolasyon azalınca yinelenen POST da azalır |
| #10 `[HATA] [Kitsu] Kitsu ID bulunamadı: Kanojo × Kanojo × Kanojo` (`mal:7411`) | ARM/başlık araması başarısız; `mappings` hiç denenmiyordu | `lookupKitsuIdByExternalMapping`; çözümlenemezse hata değil atlama |
| #479 `[HATA] [Simkl] Grup 2/6: Simkl 35 öğeden 13 tanesini onayladı; 22 eşleşmedi` ve ekrandaki `Grup 6/6: 25 öğeden 16; 9 eşleşmedi` | Kısmi başarı grubun tamamı (35) hata sayılıyor; 6 grup = 5×35+25 = 200 → "200 hata" tamamen bu sayım hatası; HTTP hatası yok | Kayıt bazlı makbuz; eşleşmeyenler atlama + başlık listeli tek özet uyarı |
| Son kayıt "Eşitleme kısmen tamamlandı" `[HATA]` | Özet satırı hata sayılıyordu | Bilgi satırı |

Örneklerde AniList'in 13 hatasına ait satır denk gelmedi (493 kaydın ~%3'ü); kod tarafındaki neden (429 + yeniden deneme yok, 80/dk limiter) değişmedi ve düzeltme yerinde.

### Önerilen test senaryoları (kullanıcı)

1. Aynı 5 hesapla tam eşitleme → Simkl hata sayısının yalnızca gerçek HTTP hatalarını yansıttığını, eşleşmeyenlerin tek özet uyarıda listelendiğini doğrula.
2. Kitsu'da manga içeren bir kütüphane → `mappings` ile çözümlenen kayıtların "Kitsu kimliği kaynağı: Kitsu mappings (MAL)" ayrıntısıyla eklendiğini kontrol et.
3. AniList'te 100+ ekleme → 429 kaynaklı hata olmamalı; süre uzayabilir.
4. Aynı adlı farklı yapımlar (ör. Hunter x Hunter 1999/2011, Berserk 1997/2016) → ayrı gruplar, her biri kendi kimliğiyle yazılmalı; birbirine karışmamalı.
5. Devam eden bir dizi (Simkl'de `total` = yayınlanan bölüm) → "Tamamlandı"ya çekilmemeli.
6. Paneli dikey/yatay çevir → düzen yeniden kurulmalı; yatayda günlük sağda, eylemler solda; çentikli cihazda içerik çentik altında kalmamalı.

---

## 6. Değişen dosyalar (zip içeriği)

1. `app/src/main/java/com/kitsugi/animelist/data/auth/AniListImportManager.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/AniListSyncManager.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/auth/KitsuApiClient.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/auth/KitsuImportManager.kt`
5. `app/src/main/java/com/kitsugi/animelist/data/auth/KitsuSyncManager.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/auth/PlatformRateLimiter.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriImportManager.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/auth/SimklSyncManager.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
11. `app/src/main/java/com/kitsugi/animelist/data/remote/SimklSyncContract.kt`
12. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
13. `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCrossSyncDialog.kt`
14. `app/src/main/java/com/kitsugi/animelist/data/auth/ExternalListSyncManager.kt` (v3: tekil kayıt gönderiminde ARM'a TMDB verilmez)
15. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncIdentityGuard.kt` (v3: **yeni** — ekleme öncesi kimlik doğrulama)
16. `CROSS_SYNC_DIAGNOSTICS_PLAN_TASK.md` (bu dosya)

---

## 7. Yanlış içerik eklenmesine karşı güvence (v3 — "kimlik doğrulanmadan ekleme yok")

### 7.1 Bildirim

Toplu eşitleme sırasında kullanıcının hiç eklemediği anime/dizi/filmler hesaplarına yazılıyor. Bu, hata sayısından daha önemli bir veri bütünlüğü sorunudur: eşitleme yalnızca ekler/günceller (asla silmez), dolayısıyla yanlış eklenen her kayıt kullanıcı tarafından elle temizlenmek zorunda kalır.

### 7.2 Tespit edilen vektörler (kod denetimi)

| # | Vektör | Nerede | Neden yanlış içerik üretir | Durum |
|---|--------|--------|----------------------------|-------|
| V1 | ARM'a **TMDB kimliği** ile MAL/AniList/Kitsu kimliği sorulması | `AuthViewModel.resolveRealMalId`, `AniListSyncManager.resolveAniListMediaId` (3. adım), `KitsuSyncManager.resolveKitsuMediaId` (ARM adımı), `ExternalListSyncManager.syncEntry` | TMDB'de film ve dizi kimlik uzayları çakışır (aynı sayı hem bir filmi hem bir diziyi gösterebilir); tek bir TMDB dizi kimliği franchise'ın tüm sezonlarını kapsar ve ARM dizinin ilk elemanını döndürür. Simkl'dan gelen ve `ids.mal` taşımayan kayıtlar TMDB kimliğiyle sorgulandığında bambaşka bir yapımın MAL ID'si "gerçek kimlik" sanılıp tüm platformlara yeni kayıt olarak yazılıyordu. `ExternalListSyncManager` bu yanlış kimliği yerel veritabanına da kalıcı yazıyordu. **En olası ana neden.** | Kapatıldı: ARM'a yalnızca birebir kimlikler (gerçek MAL / AniList / Kitsu) verilir, TMDB hiç verilmez; bu kimlikler yoksa ARM çağrılmaz. |
| V2 | Simkl isteğinde anime için `tmdb` kimliğinin `mal` ile birlikte gönderilmesi | `SimklApiClient.buildSimklIds` | Anime "shows" zarfıyla gider; film kimliği olan bir TMDB numarası Simkl'da alakasız bir diziye bağlanabilir. | Kapatıldı: Simkl kimliği varsa yalnızca o; anime için TMDB asla gönderilmez; AniList/Kitsu yalnızca MAL yokken eklenir. |
| V3 | Simkl'da anime için **başlıkla eşleştirme** (Simkl/MAL kimliği yokken) | `SimklSyncManager.syncBatchToSimkl`, `SimklApiClient.addToList` | Aynı adlı dizi/film ya da franchise'ın başka sezonu eşleşebilir. | Kapatıldı: Simkl/MAL kimliği olmayan anime kimliksiz gönderilmez (`titleMatchingAllowed=false`); hiç kimliği yoksa "güvenli kimlik yok" uyarısıyla atlanır (hata değil, atlandı). Dizi/film için TMDB/başlık eşleştirmesi (kendi doğru uzayında) korunur. |
| V4 | Kitsu başlık aramasında **kısmi eşleşmenin** (contains, puan 60 ≥ 50) kabul edilmesi | `KitsuApiClient.lookupKitsuId` | "Oni Chichi" → "Oni Chichi 2", "Berserk" → "Berserk: Ougon Jidai-hen" gibi sekans/film seçilebiliyordu. | Daraltıldı: birebir alias eşleşmesi + uyumlu yıl (bilinmiyor ya da ≤ 1 fark) gerekir; kısmi eşleşme yalnızca her iki yıl biliniyor ve birebir aynıysa kabul edilir; eşit puanlı adaylarda null. |
| V5 | **Tek kaynaklı sağlayıcı eşlemesi** (Simkl `ids.mal`, Kitsu `mappings`, AniList `idMal`) ya da ARM/Jikan çıkarımıyla elde edilen MAL ID'nin doğrulanmadan tüm platformlara "ekle" kimliği olarak kullanılması | `AuthViewModel` Faz 3 | Sağlayıcıdaki tek bir hatalı eşleme, yanlış başlığın 4 platforma birden eklenmesine yol açar. | Kapatıldı: yeni `CrossSyncIdentityGuard` ile **ekleme öncesi doğrulama** (7.3). |
| V6 | v2'de gevşetilen "ortak kimlik, farklı alias" birleştirmesi yalnızca yıla bakıyordu | `AuthViewModel.clusterEntry` | Hatalı sağlayıcı eşlemesi aynı yıl içindeyse birleşip yanlış kayda yazılabilirdi. | Daraltıldı: yıl uyumu **ve** başlık akrabalığı (ortak ayırt edici kelime / ön ek / EN-JP alias) birlikte aranır; aksi halde eski güvenli davranış (kimlik doğrulaması gerekli → yazma yok). |

Denetlenip değişiklik gerekmeyenler: Jikan başlık araması (zaten birebir normalize başlık + tür + yıl + tek sonuç şartı), AniList `Media(idMal, type)` sorgusu (türe bağlı), Shikimori `target_type`, Simkl içe aktarımının MAL ID uydurmaması, AniList/Kitsu/Simkl **güncellemelerinin** platformun kendi kayıt kimliğiyle yapılması (`aniListEntryId`, Kitsu library-entry id, `simklId`).

### 7.3 Yeni bileşen: `CrossSyncIdentityGuard` (verify-before-add)

`app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncIdentityGuard.kt`

Faz 3'te her içerik grubu için, **en az bir hedef platformda kayıt eksikse** (yani ekleme yapılacaksa) grubun MAL kimliği şu sırayla değerlendirilir:

1. **Güvenilir (Trusted):** grupta MAL/Shikimori kaynaklı bir kayıt bu kimliği taşıyor (kimlik doğal olarak MAL'dır) **veya** en az iki farklı platform (ör. AniList + Kitsu) bağımsız olarak aynı MAL ID'yi bildiriyor. Ağ isteği yapılmaz.
2. **Doğrulama (Verified / Rejected):** tek bir sağlayıcı eşlemesine ya da ARM/Jikan çıkarımına dayanan kimlik için MAL kataloğundan başlık + alternatif başlıklar + başlangıç yılı çekilir (resmi MAL v2 API, `X-MAL-CLIENT-ID`; yedek: Jikan v4; sonuçlar önbelleklenir, hız sınırlayıcıdan geçer). Yerel grubun tüm alias'ları ile karşılaştırılır:
   - Başlık akrabalığı: normalize eşitlik / ön ek / içerme (CJK dâhil) ya da en az bir ortak ayırt edici kelime (sezon, part, movie, the, no, wa … gibi yapısal kelimeler ve çıplak sayılar sayılmaz). Yıl bilinmiyorsa yalnızca güçlü akrabalık (eşitlik/ön ek/içerme) kabul edilir.
   - Yıl uyumu: ikisi de biliniyorsa mutlak fark ≤ 1.
3. **Doğrulanamadı (Unverifiable):** katalog yanıt vermedi → güvenli taraf.

Sonuç **Rejected** veya **Unverifiable** ise:
- AniList, MyAnimeList, Kitsu, Shikimori ve Simkl için **hiçbir yeni kayıt eklenmez**; her platformda "kimlik doğrulanamadı (MAL #id ↔ yerel başlık/yıl uyuşmuyor; yanlış içerik eklenmesin diye atlandı)" nedeniyle *atlandı* sayılır ve rapor sonunda tek satırda özetlenir.
- Birleşik kayda doğrulanmamış gerçek MAL ID taşınmaz (sentetik AniList/Kitsu kimlikleri korunur).
- Mevcut kayıtların güncellenmesi (durum/ilerleme/puan) etkilenmez; bunlar platformun kendi kayıt kimliğiyle yapılır.
- Rejected için raporda tek bir uyarı satırı: "Şüpheli kimlik, ekleme engellendi: <başlık> → MAL #id" (MAL'daki başlık/yıl ve yerel kayıtlar detayında).

Her grubun "İçerik grubu oluşturuldu" detayına `Kimlik güvencesi: …` satırı eklendi (güvenilir / doğrulandı / REDDEDİLDİ / doğrulanamadı / gerek yok).

### 7.4 Beklenen etki

- Yanlış sağlayıcı eşlemesi ya da ARM/TMDB belirsizliği artık **hiçbir platforma yeni kayıt olarak yansımaz**; en kötü durumda kayıt "atlandı" olarak raporlanır ve kullanıcı elle ekler.
- Maliyet: yalnızca tek kaynaklı kimlik taşıyan ve en az bir platformda eksik olan gruplar için grup başına 1 hafif MAL isteği (~450 ms aralıkla, önbellekli). MAL/Shikimori kaynaklı gruplar ve çok kaynaklı gruplar için ek istek yok.
- Simkl: güvenli kimliği (Simkl/MAL) olmayan animeler artık gönderilmez; bunlar "bulunamadı/atlandı" sayılır, hata değildir.

### 7.5 Kullanıcı için doğrulama önerisi

1. Daha önce yanlış eklenen başlıklardan birkaçının kaynağına bakın (rapordaki "İçerik grubu oluşturuldu" detayı): Simkl kaynaklı ve `ids.mal` olmayan kayıtlar artık `Kimlik güvencesi: …` satırında ya doğrulanmış ya da reddedilmiş görünmeli.
2. Eşitlemeyi çalıştırıp raporda "Şüpheli kimlik, ekleme engellendi" ve "kimlik doğrulanamadı" satırlarını kontrol edin; buradaki başlıklar hesaplara **eklenmemiş** olmalı.
3. Simkl özetinde "güvenli kimlik (Simkl/MAL) olmadığı için gönderilmedi" uyarısı varsa bu kayıtlar kimliksiz başlık eşleştirmesine düşmeden atlanmıştır (beklenen davranış).
