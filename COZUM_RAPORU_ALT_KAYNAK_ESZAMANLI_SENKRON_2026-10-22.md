# Çözüm Raporu — Altı Kaynağın TAMAMI için Eşzamanlı (Asenkron) Liste Senkronizasyonu

**Tarih:** 2026-10-22 · **Kapsam:** MAL · AniList · Kitsu · Shikimori · Bangumi · Simkl (+ TMDB köprüsü)
**Şikâyet:** „Hangi kaynaktan listeye veri eklersem ekleyeyim, diğer kaynaklarla otomatik olarak
eşitlenmiyor. Durum / puan / bölüm / tamamlandı / planlandı / tarih hiçbiri karşıya geçmiyor.”

---

## 1. Özet (tek paragraf)

Canlı eşitleme hattı (`MediaEntryRepository.insert/update` → `ExternalListSyncManager.syncEntry`)
altı kaynağın **bloğunu** zaten içeriyordu; ancak **(a)** kapı olarak kullanılan kimlik doğrulaması
yıl alanı boş/uyuşmaz olduğunda `Unverifiable` dönüyor ve **altı kaynağın beşi sessizce
atlanıyordu**, **(b)** kaynaklar **sırayla** yazılıyordu (biri yavaşsa turun tamamı bekliyordu),
**(c)** Kitsu/Shikimori/Bangumi kaynaklı satırlarda AniList medya kimliği `source` alanı yüzünden
hiç çözülemiyordu, **(d)** oynatıcıdan gelen izleme ilerlemesi **yalnızca** AniList'e yazılıyordu.
Dördü de düzeltildi.

---

## 2. Kök nedenler (kod okumasıyla doğrulandı)

| # | Kök neden | Dosya / sembol | Etki |
|---|---|---|---|
| 1 | **Yıl vetosu.** `yearsCompatible()` iki tarafın da yılı **bilinen ve birebir eşit** olmasını istiyordu. Yıl `null` ise `verifyMalId` → `Unverifiable` dönüyor, `ExternalListSyncManager.crossWriteBlocked()` bunu **engel** sayıyordu. | `CrossSyncIdentityGuard.verifyMalId`, `ExternalListSyncManager.crossWriteBlocked` | MAL satırı eklendiğinde AniList, Kitsu, Shikimori, Bangumi, Simkl'in **tamamı** `kimlik çözülemedi` olarak atlanıyordu. Kullanıcı hiçbir hata görmüyordu (atlandı ≠ hata). |
| 2 | **AniList `idMal` otoritesi yok sayılıyordu.** `verifyAniListMediaIdentity`, AniList'in kendi `idMal` alanı istenen MAL kimliğiyle **birebir** eşleşse bile ayrıca başlık + yıl eşitliği arıyordu. | `AniListSyncManager.verifyAniListMediaIdentity` | Yayın yılı farkı (TV ↔ film, sezon), İngilizce başlık farkı, boş yıl → AniList'e yazım reddediliyordu. Oysa `idMal` eşleşmesi resmî ve yanlış yapıma yazma **imkânsız**. |
| 3 | **`source` alanına bağlı kimlik çıkarımı.** `MediaIdentity.realMalId(entry)` yalnızca `mal/anilist/simkl/manual` kaynaklarında dolu döner. Çapraz katman çözülen MAL kimliğini geçici kayda yazsa bile (`withResolvedIds`), AniList zinciri `realMalId == null → return null` diyerek kesiliyordu. | `AniListSyncManager.resolveAniListMediaId` | Kitsu / Shikimori / Bangumi kaynaklı satırlar AniList'e **hiç** yazılmıyordu. |
| 4 | **Sıralı yayılım.** Altı blok art arda `await` ediliyordu; her blok kendi kimlik çözümü + `PlatformRateLimiter` beklemesi + ağ turu yapıyordu. | `ExternalListSyncManager.syncEntry/deleteEntry` | Toplam süre = altı kaynağın **toplamı**. Tek bir yavaş/kilitlenen kaynak (ör. Bangumi başlık araması) turun tamamını bekletiyor, sonraki kaynaklara sıra gelmiyordu. Zaman aşımı yoktu. |
| 5 | **Oynatıcı scrobble'ı tek hedefliydi.** `PlayerScrobbleController.scrobble()` yalnızca `AniListSyncManager.updateAniListEntry` çağırıyordu ve AniList token'ı yoksa **hiçbir şey** yapmıyordu. | `PlayerScrobbleController` | Bölüm izleme ilerlemesi MAL / Kitsu / Shikimori / Bangumi / Simkl'de hiç güncellenmiyordu ("bölüm karşıya geçmiyor"). |
| 6 | **Yanıltıcı bildirim.** Tek kaynak hata verse bile „📵 Çevrimdışı kaydedildi” yazıyordu; beş kaynak yazılmış olsa bile kullanıcı „hiçbiri gitmiyor” sanıyordu. | `MediaEntryRepository.syncEntryIfPossible` | Kullanıcı gerçek durumu göremiyordu. |

---

## 3. Yapılan değişiklikler

### 3.1 `CrossSyncIdentityGuard` — yıl vetosu kaldırıldı, çelişki ayrımı eklendi

* Tam (birebir) başlık eşdeğerliği + aynı MAL türü artık **yıl bilgisinden bağımsız kanıt**:
  `Verified`.
* Yalnızca **zayıf** başlık akrabalığı + çelişen yıllar `Rejected` (gerçek çelişki).
* Yeni sözleşme: `blocksCrossWrite(verdict)` → **yalnızca** `Rejected` yazımı durdurur.
  `Unverifiable` (yıl yok / katalog okunamadı / kota) artık **durdurmaz**; karar hedefin
  kendi resmî kimliğine bırakılır.

### 3.2 `CrossSyncPolicy` — kanıt türüne dayalı yazım kapısı

```kotlin
enum class MalIdOrigin { NATIVE, REFERENCE, GUESS }
fun malIdOrigin(rowSource, identity): MalIdOrigin
fun allowsWrite(target, rowSource, identity, verdictBlocked): Boolean
```

* `NATIVE` — satır MAL'ın kendisi (ya da elle eklenmiş): hiçbir çapraz doğrulama gerekmez.
* `REFERENCE` — kimlik resmî bir referanstan (AniList `idMal`, ARM, Kitsu `mappings`,
  Simkl/Shikimori kimliği): tüm hedeflere yazılır.
* `GUESS` — kimlik **yalnızca başlık aramasıyla** tahmin edildi: yalnızca kendi kataloğunda
  birebir teyit edebilen hedeflere yazılır (AniList · Kitsu · Simkl). Shikimori (`target_id` =
  MAL ID) ve Bangumi (başlık araması) atlanır — yanlış kayda yazma riski burada gerçek.

### 3.3 `ExternalListSyncManager` — eşzamanlı fan-out + kaynak başına zaman aşımı

* Tek tur bağlamı (`Run`) hazırlanır: ayarlar, altı token, çözülmüş kimlik, yayılım kararı.
* Altı kaynak **aynı anda** `async` ile yazılır (`coroutineScope` + `awaitAll`).
* Her kaynak `withTimeoutOrNull(30s)` ile korunur: kilitlenen kaynak diğerlerini durdurmaz;
  zaman aşımı kaynak-bazlı sonuç olarak raporlanır ve yeniden denenir.
* `skipBangumiWrite`, `unresolvedMedia`/`unresolvedSubject` (atlandı ≠ hata), kaynak bazlı
  `outcomes` haritası ve „atlandı” gerekçeleri aynen korunur.
* AniList'e yazarken çapraz katmanın çözdüğü **gerçek MAL kimliği** de iletilir
  (`knownMalId`), böylece Kitsu/Shikimori/Bangumi satırları için zincir kırılmaz.
* Liste kaydı yazılamadıysa favori sorgusu yapılmaz (gereksiz iki istek atılmaz).

### 3.4 `AniListSyncManager` — `idMal` otoriter kabul

* `verifyAniListMediaIdentity`: AniList'in döndürdüğü `idMal`, beklenen MAL kimliğine
  **eşitse** (ve tür doğruysa) doğrulama başarılı sayılır; başlık/yıl ikinci kez aranmaz.
  Çelişki (`idMal` farklı) hâlâ kesin ret.
* `resolveAniListMediaId(..., knownMalId)`: kaynak-özel çıkarımın yerine çapraz katmanın
  çözdüğü MAL kimliği kullanılır; `Unverifiable` durumunda `return null` yerine hedefin
  `idMal` teyidine devam edilir.
* `getAniListMediaFavoriteStatus` / `toggleAniListFavourite` da `knownMalId` alır.

### 3.5 `PlayerScrobbleController` — ilerleme tüm kaynaklara

* Scrobble artık `ExternalListSyncManager.syncEntry(...)` üzerinden **bağlı tüm kaynaklara**
  yazar (AniList · MAL · Kitsu · Shikimori · Bangumi · Simkl).
* AniList oturumu yok diye erken `return` kaldırıldı: MAL/Kitsu/Shikimori bağlıysa ilerleme
  onlara yazılır.

### 3.6 `MediaEntryRepository` — dürüst bildirim

* En az bir kaynak yazıldıysa „🔄 Kısmen eşitlendi — kalan kaynaklar bağlantıda yeniden
  denenecek”; hiçbiri yazılamadıysa eski „📵 Çevrimdışı kaydedildi”.
* Kaynak-bazlı özet (`AniList: eşitlendi · Bangumi: kimlik çözülemedi …`) korunur.

### 3.7 Yeni birim testleri

`app/src/test/.../CrossSyncWriteGateTest.kt` — 7 test:

1. MAL satırı **altı hedefe** de yazılabilir (katalog doğrulaması olmasa bile).
2. Katalog çelişkisi (`Rejected`) her yazımı durdurur (kendi kaynağı dahil).
3. Sağlayıcı referansından gelen kimlik tüm hedeflere yazılabilir.
4. Başlık tahmininden gelen kimlik yalnızca kendi kataloğunda teyit edebilenlere yazılır.
5. MAL kimliği yoksa hedefler kendi kataloglarını dener.
6. Kaynağı bilinmeyen kimlik `GUESS` sayılır (güvenli taraf).
7. Yalnızca `Rejected` engelleyicidir.

---

## 4. TMDB hakkında dürüst durum

TMDB bu uygulamada **hesap bağlantısı** olan bir liste kaynağı değil; TMDB kimliği
**köprü** olarak kullanılır (film/dizi ↔ anime eşlemesi) ve TMDB tarafına yazım **Simkl**
üzerinden yapılır. Yani "altı kaynak" = AniList · MAL · Simkl · Kitsu · Shikimori · Bangumi.
TMDB'nin kendi hesabına (watchlist + rating) yazmak **ayrı bir özellik** olur: TMDB
`/authentication/session/new` + `account_id` + `watchlist`/`rated` uç noktaları gerekir.
İstersen bunu ayrı bir iş olarak ekleyebiliriz.

---

## 5. Nasıl doğrulanır (elle test)

1. **MAL satırı → AniList:** MAL'da olup AniList'te olmayan bir anime ekle → AniList'te
   kayıt oluşmalı (durum/puan/bölüm/tarih aynı).
2. **Yıl alanı boş bir satır:** yıl `null` olan bir kaydı güncelle → artık atlanmamalı.
3. **Kitsu satırı:** Kitsu kaynağından ekle → AniList + MAL + Shikimori + Bangumi + Simkl.
4. **Oynatıcı:** bir bölümü %85'e kadar izle → MAL/Kitsu/Shikimori ilerlemesi de artmalı.
5. **Kısmi hata:** tek kaynağın token'ını geçersiz kıl → diğerleri yazmaya devam etmeli,
   bildirim „Kısmen eşitlendi” demeli.
6. **Yavaş kaynak:** Bangumi kapalıyken de AniList/MAL/Kitsu yazımı **anında** tamamlanmalı
   (artık sıralı değil).

Birim testler: `./gradlew :app:testDebugUnitTest --tests "*CrossSyncWriteGateTest*"`

---

## 6. Bilinen sınırlar / sonraki adım

* **Kuyruk yalnızca gerçek hataları** yeniden dener; „kimlik çözülemedi” kalıcı sayılır
  (tasarım gereği). Yeni kapıyla bu durum çok daha nadir.
* **Ardışık hızlı düzenlemeler** (bölüm bölüm ilerleme) hâlâ her seferinde bir tur açar;
  istenirse 700 ms'lik bir "coalescing" (son durumu tek tura indirme) katmanı eklenebilir.
* **Toplu eşitleme** (`AuthViewModel.syncPlatforms`) ayrı bir akıştır; aynı kapı
  gevşetmesinden faydalanır ama ayrıntılı loglama için kendi sıralı yapısını korur.
