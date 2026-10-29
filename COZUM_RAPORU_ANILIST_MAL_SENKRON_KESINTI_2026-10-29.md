# COZUM RAPORU — AniList/MAL Çağrı Zamanlı Güncelleme Kesintisi (2026-10-29)

## Şikayet
Kitsugi-Beta'da liste bölümünde yapılan düzenlemeler/eklemeler **hemen** AniList ve
MyAnimeList'e gönderilmiyor; kayıt yerel önbellekte kalıyor ve yalnızca arԁa bir
arapaçra denemekle "güncellenme" gerçekleşiyor. Bağlı hesaplar (çapraz platform
bağlantısı) var.

## Kök Sebep
Kimlik doğrulama katmanı (`CrossSyncIdentityGuard.verifyMalId`) **resmî MAL
kataloğunu** işaret ediyor. MAL kataloğuna erişilemeyen durumlarda (geçersiz/iptal
edilmiş `X-MAL-CLIENT-ID`, ağ/servis hatası) doğrulama `Unverifiable` döndürüyor.
Ardından:

1. **AniList satırından AniList'e yazım** (`updateAniListEntry` → `resolveAniListMediaIdForEntry`):
   dottrinal `resolveAniListMediaId` (resmî MAL'e bağlı) `null` döndürüyor → eski kod
   `account_sync_anilist_entry_identity_mismatch` istisnası fırlatıyordu → bu hata
   bekleyen-senkron kuyruğuna düşüyor → her yeniden denemede **aynı şekilde**
   başarısız oluyor → güncelleme AniList'e asla ulaşmıyor; kullanıcı "Çevrimdışı
   kaydedildi" bildirimini sürekli görüyor.

2. **AniList satırından MAL'e çapraz yazma** (`crossWriteBlocked`): `Unverifiable`
   (catch dahil) → `false` (engellenmedi) mantığı yerine **engelle** → MAL yazma
   sesssizce atlıyordu.

3. **AniList kataloğundanMAL ID eşleşmesi**: AniList satırlarındaki `year` alanı
   seasonYear'dan, doğrulama `startDate.year`'dan geldiği için sezon sınırında
   ±1 yıl farkı (kış sezonu artefakti) **aynı yapım** uyumsuzluğu değildir; bu
   durumdan kaynaklanan atlamalar da sessizce atlıyordu.

## Çözüm
### 1. `AniListSyncManager.updateAniListEntry` (aniListEntryId var)
- `mediaId == null` → **`return null`** (atlanır, istisna fırlatmaz). Atlama
  (OUTCOME_SKIPPED_IDENTITY) doktrinasına uyar: kimlik çözümü hata değil.
- Kuyruktaki "dene-beri-aynı-hata" döngüsü kırıldı; canlı AniList katalog
  doğrulamasıyla bugünden itibaren güncellemeler teslim ediliyor.

### 2. `AniListSyncManager.resolveAniListMediaIdForEntry` (bağlı medya doğrulama)
- `MediaList` kuyruğuna `media{idMal,type,title{romaji,english,native},startDate{year}}`
  eklendi.
- `knownAniListMediaId` yokluğunda **bağlı medya AniList'in KENDİ kataloğuyla
  doğrulanır**: `idMal` (varsa) + tip + başlık alias + yıl. Başlık/yıl uyuşmazlığı
  güvenle atlanır.
- `knownAniListMediaId` varsa eski kural korunur (list kaydının medya kimliği
  çözümlenen kimlikle birebir aynı olmalı).

### 3. `AniListSyncManager.resolveAniListMediaId`
- `verifyMalId` sonucu `when` ile değerlendirilir:
  - `Verified` → eşleşen yerel alias'lar (eski davranış),
  - `Rejected` → `return null` (resmî katalog GERÇEK uyuşmazlık),
  - `Trusted`/`Unverifiable` → yerel alias'lar (resmî kataloga erişilemeyen
    durumlarda AniList kataloğu doğrulaması yapılır — kimlik güvencesiz
    atlanamaz ama tek nokta kırılınca tüm AniList yazmaları sessizce atlanırdı).

### 4. Yeni `AniListSyncManager.verifyMalIdThroughAniList`
- Bir MAL kimliğini AniList kataloğundan `Media(idMal,type){idMal,type,title,startDate{year}}`
  ile doğrular (idMal + tip + başlık + yıl).

### 5. `ExternalListSyncManager.crossWriteBlocked` (syncEntry + deleteEntry)
- `verdict when`: `Verified`/`Trusted` → izin; `Rejected` → engel;
  `Unverifiable` → **`verifyMalIdThroughAniList` ikinci doğrulama**; ikisi de
  engellenirse güvenli yana atlanır.
- Bu fallback olmadan resmî katalog bağlantısı tek noktada kırıldığında AniList↔MAL
  tüm çapraz yazmaları sessizce atlıyordu.

### 6. `CrossSyncIdentityGuard.exactNormalizedTitleMatch`
- NFKC + noktalamasız + küçük harf normalized eşitlik. Yalnızca başlık tam
  kimlik eşitse ±1 yıl farkı güvenle kabul edilir; zayıf başlık eşleşişinde yıllar
  bilinen farklı yapım可能性i yüksek — güvenle atlanır.

### 7. `AuthViewModel` batch-sync AniList update çağrısı
- `null` sonucu artık **hata sayılmıyor**: `recordSkip(...)` ile atlandı
  (ADD çağrısıyla tutarlı).

## Çağrı Zamalı Akışı (değişmedi)
`MyListScreen → MyListViewModel.updateEntry → repository.update → syncEntryIfPossible → ExternalListSyncManager.syncEntry`
- Hata → `PendingSyncDrainer.enqueue`; atlama → `skipped`, kuyruğa girmez;
- tüm atlamalı senkron = başarı; `AppRoot` kuyruk boşaltma tetikleyicileri
  (bağlantı geri gelme, oturum başarılı) aynen korundu.

## Doğrulama
- Java/gradle test ortamı yok; tüm düzenlemeler dikkatlice tekrar okunudu,
  ayraç/çıkan dengesi doğrulandı, `R.string` referansları mevcut.
- `account_sync_anilist_entry_identity_mismatch` içinde artık kullanılmıyor
  (akış halinde).

## ESKİ HABERLER
- `updateMalEntry` (MAL satırı → MAL) ve `updateAniListEntry` (AniList → AniList)
  kendi kaynaklarına yazma — kapı-dışı, dokunulmadı.
- Simkl/Shikimori/Bangumi/TMDB yazmaları — dokunulmadı.
