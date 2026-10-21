# Kök Neden Raporu — Silme ve Eşitlemede Kimlik Çözümü, Kimlik Eşlemesi ve Sonuç Sınıflandırması (2026-10-21)

## Şikâyet (kullanıcının tarifi, ekran görüntüsünden)

"Calling You" (2016 TV, AniList satırı) ayrıntı sayfasından silme:

> **AniList kütüphanesinden silindi • Atlanan: MAL: doğrulanmış medya kimliği yok • Shikimori
> medya kimliği bulunamadı • Bangumi eşleşmesi bulunamadı: Omokage Tayuta • Çevrimdışı kaydedildi,
> bağlantı gelince gönderilecek**

Sonuç özeti:

> **AniList: eşitlendi, MyAnimeList: kimlik çözülemedi, Simkl: hata, Kitsu: hata, Shikimori:
> kimlik çözülemedi, Bangumi: kimlik çözülemedi**

Kullanıcının isteği (özetle): ekleme/güncelleme/silme işlemlerinde **tarihler, puanlar, bölüm
sayısı vb. değerlerin diğer beş kaynaklara düzgün yayılması** — yamayla değil kökünden.

İlgili önceki rapor: `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md` (v2.4.312,
9 kök neden). Bu rapor, onun kalan **silme yolu / kimlik eşlemesi / çıktı sınıflandırma**
katmanını kapatır.

---

## TL;DR — 8 kök neden

Ortak tema: **eşitleme yolu çapraz kimlik katmanıyla (`CrossProviderIdentityResolver`) çalışırken
silme yolu hâlâ "kendi satırına bak, ağa çıkma" kuralındaydı; ayrıca yöneticilerin "kimlik bulunamadı"
sonuçları hata ile atlama arasında gidip geliyordu** — hata sayılan her şey bekleyen-senkron
kuyruğuna ve "Çevrimdışı kaydedildi" bildirimine düşüyor, kullanıcı hatayı sonsuza kadar
görüyordu. Üstelik iki yöneticide kimlik yorumlama kuralı (`<100M` aralığı, `>=300M` eşiği)
**kaynak uzayını** (AniList 100M, Simkl 150M, Kitsu 300M, Shikimori 400M, Bangumi 500M
offset'leri) dikkate almıyordu.

| # | Kök neden | Belirti (ekran görüntüsünde) | Düzeltme |
|---|-----------|------------------------------|----------|
| 1 | Silme kimlik çözümünde **ağa çıkılmıyordu** (`allowNetwork = false`) | Daha önce hiç yayılmamış satır yalnızca kendi kaynağından silinir; diğerlerinde hayalet kayıt + "kimlik çözülemedi" | `allowNetwork = true` + `connected` kaynaklar (güncellemeyle aynı doğrulanmış çözüm zinciri) |
| 2 | "Kimlik bulunamadı" sonuçları **`errors`** listesine yazılıyordu | `needsRetry` = `errors` dolu → kuyruk → her açılışta "Çevrimdışı kaydedildi" + "Simkl: hata, Kitsu: hata" | `unresolvedMedia`/`unresolvedSubject` sözleşmesi: gerekçe `skipped`'e, `errors` yalnızca taşıma/HTTP hatası |
| 3 | Silme yolu yöneticileri kimlik taramasını **kısıtlı** yapıyordu (Kitsu: namespace+MAL önbelleği; Shikimori: yalnız rate önbelleği; Simkl: arama yok) | "Shikimori medya kimliği bulunamadı", "Kitsu: hata", içe aktarılmış kayıtların silinememesi | `knownKitsuMediaId`/`knownMalId` geçişi + yazma yoluyla **aynı** çözüm zinciri; Shikimori'de `fetchAllUserRates` geri araması; bulunamayan kütüphane kaydı = idempotent başarı |
| 4 | Canlı hat `rawKitsuId` için **geniş `>= 300M`** kuralı kullanıyordu (toplu hatta 2026-10-07'de düzeltilmişti) | Shikimori (400M) stableId'leri `id - 300M` ile **sahte Kitsu kimliğine** dönüyor → Simkl her yazma/silmde `not_found` → "Simkl: hata" | `rawKitsuIdOf()`: yalnız `300_000_001..399_999_999` → `-300M`, ya da `source == "kitsu"` ham (<100M) kimlik |
| 5 | `realMalId` = saf `0 < id < 100M` aralığı (canlı Simkl/Shikimori) | İki yönlü: (a) eski Kitsu/Bangumi ham kimlikleri ve TMDB uzay değerleri **MAL sanılıp yanlış yapıma yazabiliyordu**; (b) çapraz eşitlemenin çözdüğü gerçek MAL, `source = "shikimori"` vb. satırın geçici kopyasına yazılıp decode tarafından **ham kimlik sanılıyor ve düşürülüyordu** | `knownMalId` parametresi (kimlik katmanının doğrulanmış sonucu önce gelir) + decode fallback (Simkl sync/sil, Shikimori sync/sil, AuthViewModel toplu Shikimori). Toplu hatta ham satır kuralı **bilinçli** korundu (Shikimori importu MAL uzayındaki `target_id`'yi ham yazar) |
| 6 | AniList silmede `account_sync_anilist_media_id_unresolved` her zaman **FAILED** | Kimlik eksiği bile "hata" → kuyruk | Atlama (skip); `identity_mismatch` yeniden denenebilir kalır |
| 7 | MAL PATCH parsriyel tarih (`"2016"`, `"2016-05"`) ile 400 dönüyordu | Tüm güncelleme tek eksik tarihte "hata" sayılıp kuyruğa düşüyordu | `sendableDate()`: yalnız `yyyy-MM-dd` gönderilir; uymayan alan göndermez (uzak değer korunur) |
| 8 | Bangumi silmede **tüm** hatalar "atlandı" sayılıyordu | Gerçek yazım hataları (ağ/HTTP) kuyruğa hiç girmiyordu | `unresolvedSubject` ayrımı: kimlik eksiği = atlandı, yazım hatası = hata |

---

## Ayrıntılar

### 1) Silme yolu kimlik çözümünde ağa çıkmıyordu
`ExternalListSyncManager.deleteEntry`, `CrossProviderIdentityResolver.resolve(allowNetwork = false)`
ile çağrılıyordu (yorum: "silmede yanlış yapıma dokunmamak için"). Eşitleme yolu ise
`allowNetwork = true` + doğrulanmış eşleşmelerle çalışıyordu. Sonuç: kullanıcı bir kaydı ilk kez
sildiğinde (daha önce hiç yayılmamış içe aktarım satırı gibi) çapraz kimlik hiç çözülemiyor,
**yalnızca satırın kendi kaynağı** silinebiliyor, diğer kaynaklarda hayalet kayıt kalıyordu.
Güvenlik eşitlemeyle aynı: çözümleyici yalnızca doğrulanmış eşleşmeler döndürür; çözülemeyen
alan `null` kalır ve o kaynak atlanır. Artık token/connectedSource hazırlığı kimlik aramasından
**önce** yapılıyor ve `connected` bilgisi aramaya veriliyor (bağlı olmayan kaynak için ağ araması
yapılmaz).

### 2) Kimlik eksiği "hata" sayılıyordu → sonsuz "Çevrimdışı kaydedildi"
`MediaEntryRepository` başarıyı `errors.isEmpty()` ile ölçer, hata görürse kaydı bekleyen-senkron
kuyruğuna yazar ve çevrimdışı bildirimi gösterir. Kuyrukta yeniden denemenin faydası olmayan kimlik
eksiği, eski kodda `errors`a girdiği için **her açılışta aynı hata yeniden üretiliyordu** (şikâyetin
tam kalbi). Artık:

- Yönetici düzeyi: `SyncResult.unresolvedMedia` (Simkl/Kitsu), `unresolvedSubject` (Bangumi) —
  `errors` bu durumda gerekçe metinlerini taşır ama sınıf **atlandı**'dır.
- Orchestrator düzeyi: gerekçe `skipped` listesine, `errors` yalnızca taşıma/HTTP'ye; özet mesajda
  "Atlanan: …" satırı, sonuç haritasında `OUTCOME_SKIPPED_IDENTITY` ("kimlik çözülemedi").
- `skipped asla kuyruğa girmez`; `errors` kuyruğa girer (gerçek hatalar yeniden denensin diye).

Simkl ekleme makbuz ayrıştırımı: `addToListBatchDetailed` yanıtında `transportFailed` (HTTP/ağ) =
hata, `not_found` (katalogda yok) = atlandı. Simkl silme: id arama dönüşü `false` (bulunamadı) =
atlandı; `removeIntoList` Boolean'i bu ayrımı yapamadığı için kimlik arama sonucu ölçüt alındı.

**Ekrandaki sonuç artık:** `Simkl: hata → kimlik çözülemedi/atlandı` (veya kimlik varsa
`eşitlendi`), `Kitsu: hata → kimlik çözülemedi` (veya `eşitlendi`), kuyruk ve "Çevrimdışı
kaydedildi" yalnız gerçek ağ/HTTP hatalarında.

### 3) Silme yolu kimlik taramasını sınırlı yapıyordu
- **Kitsu silme:** yalnız satırın namespace'i + MAL→Kitsu önbelleği. Çapraz kimlik katmanının
  çözdüğü Kitsu medya kimliği yok sayılıyordu. Artık `knownKitsuMediaId = identity.kitsuId` ile
  eklemeyle **aynı** zincir: bilinen kimlik → MAL önbelleği → Kitsu stableId → Kitsu mappings →
  ARM → sıkı başlık araması. Kimlik var ama kütüphane kaydı yoksa = zaten silinmiş (başarı).
- **Shikimori silme:** yalnız rate önbelleği (yalnızca bu uygulama üzerinden oluşturulan kayıtlar).
  Artık: önbellekte yoksa `fetchAllUserRates` → `targetId + targetType` ile eşleşen rate → sil.
  Uzak liste **okunamazsa** bu "yok" değildir → gerçek hata (yeniden dener). Hiç kayıt yoksa =
  idempotent başarı. Hedef hiç çözülemezse atlandı.
- **Simkl silme:** `simklId` yoksa eskiden `account_sync_media_id_not_found` bir **hataydı**;
  artık `unresolvedMedia` (atlandı) ve öncesinde `knownMalId` + kimlik arama üzerinden eşleşme
  denenir.

### 4) Canlı hat Shikimori 400M'yi Kitsu 300M sanıyordu
Eski kural: `entry.source == "kitsu" || malId >= 300_000_000` → `id - 300_000_000`. Shikimori
stableId aralığı (400_000_001..400_099_999) bu eşiğin üstünde olduğu için `id - 300M` ile
**300M+ bir sayı** (gerçek Kitsu id'leri <100K) Simkl'e `ids.kitsu` olarak gidiyor → her yazma ve
silmede `not_found` → "Simkl: hata". Toplu hatta bu hata 2026-10-07'de düzeltilmiş, canlı hatta
kalmıştı. `rawKitsuIdOf()` artık tek sözleşmedir: 300M penceresi → `-300M`; `source == "kitsu"` ve
ham (<100M) → olduğu gibi; aksi halde `null`.

### 5) `realMalId` yorumlaması — iki yönlü hata
Eski kural `0 < id < 100M` idi ve **kaynağa** bakmıyordu:

- (a) Eski Kitsu/Bangumi ham kimlikleri ve film/dizi satırlarındaki TMDB uzay değerleri aynı
  aralığa düşüp MAL sayılıyordu → `ids.mal` olarak yanlış yapıma yazma riski ve `not_found`.
- (b) Tersi: `withResolvedIds` ile çapraz eşitlemenin çözdüğü **gerçek MAL** geçici kopyaya
  (`source = "shikimori"` vb.) yazılıyordu; saf `CrossProviderIds.decode` bunu kaynağın kendi ham
  kimliği sanıp `malId`'den **düşürüyordu** → doğru yazım atlanıyordu.

Çözüm, codebase'in `knownXxxId` desenine uydu: Simkl sync/sil ve Shikimori sync/sil fonksiyonlarına
`knownMalId: Int? = null` parametresi eklendi, çağrılar `identity.malId` geçiriyor (AuthViewModel
toplu Shikimori akışı da `realMalId`'yi geçiriyor). Öncelik: `realMalIdOf(knownMalId)` → decode
fallback. **Toplu Simkl hattı** bilinçli olarak eski aralık kuralında bırakıldı: o hat ham kütüphane
satırları üzerinde çalışır (kopya değil) ve Shikimori importu MAL uzayındaki `target_id`'yi ham
yazar (`<100M`) — decode bunu shikimori saydığı için aralık kuralı orada **doğru** davranır;
dosya içi yorumla belgelendi.

### 6)–(8) Sınıflandırma/alan yamaları
- AniList silmede medya kimliği çözülemedi → atlandı; `identity_mismatch` (olabilir yeniden deneme)
  FAILED kalır.
- MAL tarihleri: `fullIsoDate` (`yyyy-MM-dd`) ile `sendableDate()`; parsriyel tarih gönderilmez,
  PATCH 400'e düşmez. (AniList `scoreRaw` ondalık×10 matematiği, `SyncScores` ve içe aktarma
  ölçekleri denetlendi — **doğru**, "düzeltilmedi".)
- Bangumi silmede `unresolvedSubject` = atlandı, yazım hatası = FAILED (eşitleme yoluyla aynı).

---

## Değişen dosyalar

| Dosya | Değişiklik |
|-------|------------|
| `data/auth/ExternalListSyncManager.kt` | Silme kimliği `allowNetwork=true` + `connected`; Simkl/Kitsu/Shikimori/AniList silme 3'lü sınıflandırma (sync/del Simkl, sync Kitsu/Bangumi zaten öyleydi); `knownKitsuMediaId`/`knownMalId` geçişi; Shikimori hedef kapısı + `account_sync_shikimori_id_not_found` gerekçesi; Bangumi silme `unresolvedSubject` ayrımı; sınıf KDoc'u |
| `data/auth/SimklSyncManager.kt` | `realMalIdOf(entry, knownMalId)`, `rawKitsuIdOf()`; sync adım 3 → `addToListBatchDetailed` makbuz ayrıştırımı; kimlik yoksa/bulunamadıysa `unresolvedMedia`; silme id-miss → `unresolvedMedia`; batch kuralı belge yorumu |
| `data/auth/KitsuSyncManager.kt` | `deleteEntryFromKitsu(…, knownKitsuMediaId)` — eklemeyle aynı çözüm zinciri; null → `unresolvedMedia`; kütüphanede olmayan → idempotent başarı |
| `data/auth/ShikimoriSyncManager.kt` | `shikimoriTargetIdOf(entry, knownMalId)`; sync/sil `knownMalId`; silme: rate önbelleği yoksa `fetchAllUserRates` ile hedefe göre bulma; okuma hatası ≠ bulunamadı; bulunamayan kayıt = idempotent başarı; hedefsiz → `unresolvedMedia` |
| `data/auth/MalSyncManager.kt` | `sendableDate()` — yalnız tam `yyyy-MM-dd` tarih PATCH'e girer |
| `ui/app/AuthViewModel.kt` | Toplu Shikimori çağrılarına `knownMalId = realMalId` |

Dokunulmayan (bilerek): `CrossProviderIdentity.kt` (decode/cacheKey sözleşmesi v2.4.312'de oturdu),
`CrossSyncIdentityGuard`, `CrossSyncReportFormatter`, `PendingSyncDrainer`, `MediaEntryRepository`.

---

## Doğrulama durumu

- **tree-sitter-kotlin** ile 6 değişik `.kt` dosyasının tamamı hatasız ayrıştırıldı (ERROR/MISSING
  yok) — önceki rapordaki yöntem.
- Sandbox'ta `java`/`gradle`/`kotlinc`/`ANDROID_HOME` yok; `scripts/run_sync_*_checks.sh`
  (`$KOTLINC`/`$JSON_JAR` ister) ve Gradle testleri **burada çalıştırılamaz** → CI'da derleme +
  `app/src/test` koşulması gerekiyor.
- API sözleşmeleri kaynak üzerinden doğrulandı: `SimklBatchEntry`/`SimklBatchResponse` alanları,
  `addToListBatchDetailed(token, entries)`, `titleMatchingAllowed`, `fetchAllUserRates`,
  `resolveKitsuMediaId` imzası/dönüşü, string kaynakları (`account_sync_media_id_not_found`
  `%1$s`, `account_sync_shikimori_id_not_found` `%1$s`, `account_sync_anilist_media_id_unresolved`
  argümansız) — çağrı biçimleriyle uyumlu.
- Değişen sembollerin test çağrıları tarandı: yalnız `scripts/tests/fixtures/ExternalListSyncManager.kt`
  (yerel `SyncResult` tanımı, etkilenmez).

## Bilinçli olarak yapılmayanlar

- `PendingSyncDrainer` başarı koşulu gevşetilmedi — kuyruk şişmesinin kaynağı yanlış
  sınıflandırmaydı, kaynaka düzeltildi.
- MAL ilerleme > toplam kırpması yapılmadı (kanıtlanamamış spekülasyon) — istenirse ayrı iş.
- `withResolvedIds` tümüyle yeniden yazılmadı — AniList 100M stableId'lerini null'lar
  `resolveVerifiedMalIdFromAniList`'i kırardı; bunun yerine yöneticilere `known*` kimlikleri geçildi.
- Puan ölçeği/tarih biçimi/"başlık bozulması" iddiaları denetlendi ve **sahte** bulundu
  ("Omokage Tayuta" = "Calling You"nın özgün adı).

## İzleyen işler

1. CI'da `assembleDebug` + `testDebugUnitTest` (özellikle `CrossProviderIdentityTest`,
   `CrossSyncIdentityGuardTest`, rapor biçim testleri).
2. Üretimde izlenecek: silme özetinde `atlandı` gerekçeleri artmalı, "Çevrimdışı kaydedildi" ve
   bekleyen-senkron kuyruğu yalnız gerçek ağ/HTTP hatalarında büyümelidir.
3. (İsteğe bağlı) `rawKitsuIdOf`/`shikimoriTargetIdOf` için saf yardımcı seviyesinde birim test —
   yardımcılar şu an `private`; yalnız test için `internal` yapılmaları tartışılabilir.
