# Kök Neden Raporu — Liste Ekleme/Güncellemede Seçilen Değerlerin Kaynağına ve Diğer Kaynaklara Yazılmaması (2026-10-20, v2.4.312)

## Şikâyet (kullanıcının tarifi)

> Hangi kaynaktan olursa olsun (ör. Bangumi), liste ekleme/güncellemede durum (ör. **İzleniyor**),
> puan, tarih, bölüm vb. seçtiğim değerler **(1)** o kaynağa doğru yazılmıyor ve
> **(2)** aynı kayıt aynı değerlerle diğer kaynaklara (AniList · MAL · Simkl · Kitsu · Shikimori ·
> Bangumi) eşzamanlı senkronize edilmiyor.

Bu sorun daha önce birden çok kez "düzeltildi" diye işaretlenmiş (bkz.
`CROSS_SYNC_ALTI_KAYNAK_FIX_RAPORU_2026-10-12.md`, `BANGUMI_YAZMA_TESHISI_RAPORU_2026-10-12.md`)
ancak kullanıcı hatayı hâlâ üretiyor. Bu rapor, o düzeltmelerin neden yetmediğini ve bu sürümde
gerçekten giderilen kök nedenleri sıralar.

---

## TL;DR — 9 kök neden, 1 mimari hastalık

Ortak tema: **her kaynağın kendi kimlik uzayı ve kendi başlık dili var; kodun farklı noktaları
bunu tutarlı biçimde dikkate almıyordu.** Özellikle Bangumi kökenli satırlar (Çince görünen
başlık + 500M subject kimliği) her doğrulama basamağında eleniyor, yayılım "sessiz atlama"
olarak raporlanıyordu. Üstüne Bangumi ekleme akışı "önce uzağa yaz" kapısıyla, yazma
başarısız olunca kullanıcının seçimlerini **tümüyle** çöpe atıyordu.

| # | Kök neden | Belirti | Düzeltme |
|---|-----------|---------|----------|
| 1 | Bangumi ekle/düzenle önce Bangumi'ye yazıyordu; başarısızlık = kayıt yerele bile yazılmıyor | Seçimlerin tamamı kayboluyor, hiçbir kaynağa gidilmiyor | Üç çağrı noktası da diğer kaynaklarla aynı "önce yerel, sonra yay" hattına alındı |
| 2 | Kimlik doğrulaması özgün Japonca başlığı kullanmıyordu | Bangumi satırları (Çince başlık) MAL/AniList doğrulamasından hep düşüyor → yayılım atlanıyor | `aliasesOf` artık `titleJapanese` içerir |
| 3 | Çözülen AniList kimliği yazma anında atılıp başlıkla yeniden çözülüyordu | Bangumi köprüsünün bulduğu AniList kaydı yazılamıyordu | `knownAniListMediaId` yazma + favori yoluna taşınıyor (yine doğrulanarak) |
| 4 | Manga erken dönüşü Bangumi köprüsünün önündeydi | Bangumi kitap/manga hiç yayılamıyordu | Köprü erken dönüşten öne alındı |
| 5 | Ham Shikimori/Bangumi/Kitsu kimlikleri MAL kimliği sanılıyordu | MAL çözümü atlanıyor; sahte kimlikle yayılım kapısı açılıyor / yanlış kimlik önbelleğe yazılıyor | `decode` + `cacheKey` kaynak-duyarlı |
| 6 | Simkl kimliği arama koşulu tersineydi; canlı hat `S01E<N>` uyduruyordu; durum önce yazılıyordu | Simkl'e yalnızca durum gidiyor, bölüm/puan düşüyordu; geri yazım satır kimliğini eziyordu | Kimlik hep aranır; 1..N bölüm geçmişi → puan → durum (en son); geri yazım güncel DB satırına |
| 7 | Shikimori yazması MAL kimliği yoksa tamamen atlanıyordu | Shikimori'nin kendi satırı bile güncellenmiyordu | Kendi `target_id` ile yazma yolu açıldı |
| 8 | AniList boş tarih `{0,0,0}` = "tarihi temizle" gönderiyordu; MAL puanı normalize edilmiyordu | Tarihsiz satırdan yayılım AniList tarihlerini siliyordu; 0..100 puan MAL güncellemesinin tamamını 400'lüyordu | Boş tarih gönderilmez; puan `SyncScores.tenPoint` |
| 9 | Düzenleme yalnızca düzenlenen satırı güncelliyordu | Diğer kaynak sekmeleri ve "Tümü" eski değerleri gösteriyordu ("güncelledim ama değişmedi") | Kardeş satırlara yerel yansıtma (ekle/güncelle/sil) |

---

## Ayrıntılar

### 1) Bangumi "önce uzağa yaz" kapısı — toplam kayıp
`AppRoot.onConfirmEdit`, `MyListViewModel.saveBangumiEntry`, `AppViewModel.insertApiSelection`
Bangumi dalı: üçü de önce `BangumiSyncManager.syncEntryToBangumi` çağırıyor, `collectionUpdated`
olmazsa **hiçbir şey** yapmıyordu (ne yerel kayıt ne diğer kaynaklar). Token kapsamı/akış
sorununda kullanıcının editörde seçtiği her şey kayboluyordu. TV tarafı zaten düz
`repository.update` kullanıyordu; telefon tarafındaki bu özel durum anomalinin kendisiydi.
Artık üç nokta da `MediaEntryRepository.insert/update(syncExternal=true)` kullanır:
yerel kayıt her zaman tutulur, `ExternalListSyncManager` Bangumi dahil altı kaynağa yayar,
başarısızlık bekleyen-senkron kuyruğuna girer ve sonuç repository geri çağrısıyla bildirilir.
`skipBangumiWrite=true` kullanan çağrı kalmadı.

### 2) Japonca başlık yokluğu — Bangumi yayılımının ana katili
`CrossSyncIdentityGuard.aliasesOf` yalnızca `title + titleEnglish` döndürüyordu. Bangumi
satırlarında görünen başlık çoğunlukla Çince (`name_cn`), İngilizce başlık çoğunlukla yok;
satırın taşıdığı tek ortak başlık özgün Japonca ad. MAL/AniList doğrulaması bu yüzden
neredeyse her zaman düşüyor, yayılım "kimlik çözülemedi → atlandı" oluyordu.
`titleJapanese` eklendi. Aynı gerekçeyle çözümleyicinin son-çare Jikan araması artık
İngilizce → Japonca → görünen başlık sırasıyla dener.

### 3) Çözülmüş AniList kimliğinin atılması
`ExternalListSyncManager`, `CrossProviderIdentityResolver`'ın çözdüğü `aniListId`'yi
`AniListSyncManager`'a vermiyordu; yazma kendi zinciriyle (yerel kimlik → MAL `idMal` → ARM)
yeniden çözmeye çalışıyor, Bangumi kökenli satırlarda (Çince başlık, sıkı yıl eşitliği) düşüyordu.
`updateAniListEntry`, `getAniListMediaFavoriteStatus`, `toggleAniListFavourite`,
`resolveAniListMediaIdForEntry` artık `knownAniListMediaId` kabul ediyor; kimlik yine
`verifyAniListMediaIdentity` ile doğrulanır, tutmazsa eski zincire düşülür (güvenlik elden
bırakılmadı).

### 4) Bangumi manga/kitap erken dönüşü
`KitsugiIdResolver.resolveIdsUncached` içinde manga erken dönüşü Bangumi köprüsünden **önce**
geliyordu; Bangumi kitapları (`mediaType=Manga`) MAL/AniList kimliği hiç çözemiyordu.
Köprü öne alındı; manga dönüşü köprünün çözdüğü kimlikleri de döndürür.

### 5) Ham kimliklerin MAL sanılması (decode/cacheKey)
`CrossProviderIds.decode`, 100M altı her değeri MAL kimliği sayıyordu; oysa Shikimori içe
aktarması ham `target_id` yazar, eski Kitsu/Bangumi satırları da ham kimlik taşıyabilir.
Sonuçları: (a) Shikimori satırlarında MAL çözümleme adımı "zaten var" sanılıp atlanıyordu;
(b) ham Bangumi satırında subject_id sahte MAL kimliği olarak yayılım kapısını açıyor,
yanlış yapıma yazma riski doğuruyordu; (c) `cacheKey` aynı sayıyı `mal:` uzayına yazıp
bambaşka bir MAL yapımının çözülmüş kimliğini bu satıra yapıştırabiliyordu.
Artık `decode` ve `cacheKey` satır kaynağını dikkate alır (`MediaIdentity` sözleşmesiyle aynı);
AniList/Simkl örtüşen penceresinde de kaynak biliniyorsa ayrım onunla yapılır.
Ek olarak film/dizi satırlarında Bangumi subject kimliği artık korunuyor (eskiden atılıyordu;
Bangumi filmleri Simkl'e yayılamıyordu) ve `allowFanOut` video kimliğini (TMDB/Simkl) de
geçerli sayıyor.

### 6) Simkl canlı hattı
- Arama koşulu `simklId yok && malId yok && tmdbId yok` idi — yani kimlik **varken**
  simklId hiç aranmıyordu. Bölüm ve puan adımları `effectiveSimklId > 0` istediği için
  çapraz eşitlemede Simkl'e fiilen yalnızca durum gidiyordu. Koşul düzeltildi.
- Bulunan simklId, çağıran tarafın geçici kopyası (`remoteEntry` — malId alanı gerçek MAL
  kimliğiyle değiştirilmiş) üzerinden DB'ye yazılıyordu; satırın kendi kaynak kimliği
  (ör. Kitsu 300M) eziliyordu. Geri yazım artık güncel DB satırı üzerinden.
- Sıra ve biçim toplu hatla eşitlendi: anime bölüm geçmişi `historyBatchReceipt` ile
  **1..N** (eski canlı hat, toplu sözleşmenin açıkça yasakladığı `S01E<N>` biçimini
  uyduruyordu), sonra puan, **en son** istenen durum (geçmiş/puan yazımı Simkl'de durumu
  değiştirebildiği için). Dizi (TV) ilerlemesi sezon kataloğu olmadan gönderilmez
  (toplu hatla aynı kural).

### 7) Shikimori kendi kimliğiyle yazma
Shikimori bloğu `realMalId == null` ise tümüyle atlanıyordu — Shikimori'nin **kendi**
satırı bile güncellenmiyordu. Artık çözülmüş `shikimoriId` varsa yazma kendi
`target_id`siyle yapılır.

### 8) AniList tarih temizleme + MAL puan ölçeği
Boş yerel tarih için AniList'e `{0,0,0}` FuzzyDate gönderiliyordu; AniList bunu tarih
temizleme olarak uygular. Tarihsiz bir satırdan (örn. Bangumi — API'de tarih yok) yapılan
her yayılım AniList'teki başlangıç/bitiş tarihlerini sessizce siliyordu. Artık boş tarih
değişkeni hiç gönderilmez. MAL tarafında puan `SyncScores.tenPoint` ile normalize edilir;
eski 0..100 değerler tüm PATCH'i 400'lüyordu.

### 9) Kardeş satırların bayat kalması ("güncelledim ama değişmedi")
Aynı yapımın her kaynakta ayrı satırı vardır; düzenleme yalnızca düzenlenen satırı
güncelliyordu. Diğer kaynak sekmeleri ve "Tümü" (temsili satır) eski değerleri gösterdiği
için kullanıcı senkronun çalışmadığını sanıyordu. `MediaEntryRepository.insert/update`
artık kullanıcı alanlarını (durum/puan/bölüm/cilt/tarih/not/etiket/favori/gizlilik/öncelik/
tekrar) **kimlik kesişimiyle** bulunan kardeş satırlara yerel olarak yansıtır (başlık
benzerliği kullanılmaz; TMDB anime/mangada kanıt sayılmaz — sezonlar tek TMDB dizisinde
toplanır). `deleteById` kardeşleri de yerelden siler.

---

## Değişen dosyalar

- `AppRoot.kt`, `ui/app/AppViewModel.kt`, `ui/screens/mylist/MyListViewModel.kt` — Bangumi dalları birleşik hatta alındı.
- `data/auth/CrossProviderIdentity.kt` — kaynak-duyarlı `decode`/`cacheKey`, video satırında Bangumi subject, `allowFanOut` video kimliği, Jikan başlık sırası, `sharesAnyIdWith`.
- `data/auth/CrossSyncIdentityGuard.kt` — `aliasesOf` Japonca başlık.
- `data/auth/AniListSyncManager.kt` — `knownAniListMediaId` (4 fonksiyon), boş tarih gönderme.
- `data/auth/ExternalListSyncManager.kt` — çözülmüş AniList kimliğini geç; Shikimori kendi kimliği kapısı.
- `data/auth/SimklSyncManager.kt` — kimlik arama koşulu, güvenli geri yazım, toplu-hat sırası/biçimi.
- `data/auth/MalSyncManager.kt` — puan normalizasyonu.
- `data/remote/KitsugiIdResolver.kt` — Bangumi köprüsü manga erken dönüşünden önce.
- `data/local/MediaEntryRepository.kt` — kardeş satır yansıtma (ekle/güncelle/sil).
- `data/local/MyListIdentityCrosswalk.kt` — önbellek anahtarına kaynak.
- Testler: `CrossProviderIdentityTest`, `CrossSyncIdentityGuardTest` genişletildi.

## Doğrulama durumu

- Sandbox'ta JDK/Android SDK yok: **derleme ve test çalıştırılamadı.** Değişen 14 Kotlin
  dosyasının tamamı tree-sitter-kotlin ile sözdizimi denetiminden geçti; tam `git diff`
  satır satır gözden geçirildi; yeni/eski test beklentileri kâğıt üzerinde doğrulandı.
- Cihazda doğrulanması gerekenler: (1) Bangumi'den ekleme → altı kaynakta görünme;
  (2) herhangi bir kaynaktan düzenleme → diğer sekmelerde anında yansıma;
  (3) Simkl'de bölüm+puan+durum; (4) AniList'te tarihlerin korunması.

## Bilinçli olarak yapılmayanlar

- **Bangumi bölüm geri alma (unmark):** İlerleme azaltılırsa işaretli bölümler geri
  alınmıyor. `EpisodeCollectionType.NONE=0` var ancak PATCH'te tip-0 anlamı
  doğrulanamadı; yanlış tahmin kullanıcının işaretlerini bozabilir. Platform davranışı
  olarak belgelendi.
- **Bangumi'ye tarih yazma:** API'de tarih alanı yok (platform sınırı).
- **Hızlı eklemede (editörsüz yol) editör açma:** Bangumi dışı hızlı ekleme editörsüz
  "Planlandı" olarak ekler; editör açılması ayrı bir UX kararı olarak açık bırakıldı.
